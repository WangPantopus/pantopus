//
//  StartSupportTrainWizardViewModel.swift
//  Pantopus
//
//  P2.6 — Start-a-Support-Train wizard view model. Owns the three-step
//  state machine + recipient autocomplete + the launch sequence (create
//  draft → POST each generated slot → publish). On success, emits
//  `.openTrain(trainId:)` so the host stack can pop the wizard and
//  push the new train's review-signups screen.
//

import Foundation
import Observation

@Observable
@MainActor
public final class StartSupportTrainWizardViewModel: WizardModel {
    // MARK: - Public state

    public private(set) var step: StartSupportTrainStep = .whoAndWhy
    public private(set) var pendingEvent: StartSupportTrainEvent?

    // Step 1 — Who & why
    public var beneficiaryQuery: String = ""
    public private(set) var beneficiaryResults: [MailRecipientDTO] = []
    public private(set) var isSearchingBeneficiary: Bool = false
    public private(set) var selectedBeneficiary: MailRecipientDTO?
    public var reason: String = ""

    // Step 2 — What & when
    public var kind: SupportTrainKind = .meals
    public var startDate: Date
    public var endDate: Date
    public var slotDuration: StartSupportTrainSlotDuration = .sixty

    // Step 3 — Review & launch
    public var allowComments: Bool = true
    public var visibility: StartSupportTrainVisibility = .neighbors
    public private(set) var launchError: String?
    public private(set) var publishedTrainId: String?

    // MARK: - Constants

    public static let reasonCharLimit: Int = 500

    // MARK: - Dependencies

    private let api: APIClient
    private var searchTask: Task<Void, Never>?
    private var isSubmittingFlag: Bool = false

    init(
        api: APIClient = .shared,
        startDate: Date = Date(),
        endDate: Date = Date().addingTimeInterval(60 * 60 * 24 * 6)
    ) {
        self.api = api
        let cal = Calendar.current
        self.startDate = cal.startOfDay(for: startDate)
        self.endDate = cal.startOfDay(for: endDate)
    }

    // MARK: - Derived projections

    public var generatedSlots: [StartSupportTrainSlot] {
        StartSupportTrainSlotGenerator.generate(
            startDate: startDate,
            endDate: endDate,
            durationMinutes: slotDuration.rawValue,
            startHour: kind.defaultStartHour
        )
    }

    public var reasonRemainingChars: Int {
        max(0, Self.reasonCharLimit - reason.count)
    }

    public var derivedTitle: String {
        let recipientName = selectedBeneficiary?.name
            ?? selectedBeneficiary?.username
            ?? beneficiaryQuery.trimmingCharacters(in: .whitespaces)
        let name = recipientName.isEmpty ? "a neighbor" : recipientName
        return "\(kind.title) for \(name)"
    }

    // MARK: - Step 1 actions

    public func updateBeneficiaryQuery(_ value: String) {
        beneficiaryQuery = value
        if let current = selectedBeneficiary, value != displayName(current) {
            selectedBeneficiary = nil
        }
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 2 else {
            searchTask?.cancel()
            beneficiaryResults = []
            isSearchingBeneficiary = false
            return
        }
        searchTask?.cancel()
        searchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            await self?.searchBeneficiary(query: trimmed)
        }
    }

    public func selectBeneficiary(_ recipient: MailRecipientDTO) {
        selectedBeneficiary = recipient
        beneficiaryQuery = displayName(recipient)
        beneficiaryResults = []
    }

    public func clearBeneficiary() {
        selectedBeneficiary = nil
    }

    public func updateReason(_ value: String) {
        if value.count > Self.reasonCharLimit {
            reason = String(value.prefix(Self.reasonCharLimit))
        } else {
            reason = value
        }
    }

    // MARK: - Step 2 actions

    public func selectKind(_ value: SupportTrainKind) {
        kind = value
    }

    public func setStartDate(_ value: Date) {
        let cal = Calendar.current
        startDate = cal.startOfDay(for: value)
        if endDate < startDate {
            endDate = startDate
        }
    }

    public func setEndDate(_ value: Date) {
        let cal = Calendar.current
        let day = cal.startOfDay(for: value)
        endDate = day < startDate ? startDate : day
    }

    public func selectSlotDuration(_ value: StartSupportTrainSlotDuration) {
        slotDuration = value
    }

    // MARK: - Step 3 actions

    public func toggleAllowComments(_ value: Bool) {
        allowComments = value
    }

    public func selectVisibility(_ value: StartSupportTrainVisibility) {
        visibility = value
    }

    // MARK: - WizardModel

    public var chrome: WizardChrome {
        WizardChrome(
            title: stepTitle,
            progressLabel: step.stepNumber.map {
                .stepOf(current: $0, total: StartSupportTrainStep.progressTotal)
            } ?? .hidden,
            progressFraction: step.stepNumber.map {
                Double($0) / Double(StartSupportTrainStep.progressTotal)
            },
            leading: step == .whoAndWhy ? .close : .back,
            primaryCTALabel: primaryCTALabel,
            primaryCTAEnabled: primaryCTAEnabled,
            secondaryCTA: step == .success
                ? WizardSecondaryCTA(
                    label: "Back to trains",
                    identifier: "startSupportTrainBackToList"
                )
                : nil,
            isSubmitting: isSubmittingFlag,
            dirty: stepDirty,
            showsProgressBar: step != .success
        )
    }

    public func leadingTapped() {
        switch step {
        case .whoAndWhy:
            pendingEvent = .dismiss
        case .whatAndWhen:
            step = .whoAndWhy
        case .reviewAndLaunch:
            step = .whatAndWhen
        case .success:
            handleSuccessExit()
        }
    }

    public func discardConfirmed() {
        pendingEvent = .dismiss
    }

    public func primaryTapped() {
        switch step {
        case .whoAndWhy:
            guard canAdvanceFromWhoAndWhy else { return }
            step = .whatAndWhen
        case .whatAndWhen:
            guard canAdvanceFromWhatAndWhen else { return }
            step = .reviewAndLaunch
        case .reviewAndLaunch:
            guard !isSubmittingFlag else { return }
            Task { await launch() }
        case .success:
            handleSuccessExit()
        }
    }

    public func secondaryTapped() {
        if step == .success {
            handleSuccessExit()
        }
    }

    public func acknowledgePendingEvent() {
        pendingEvent = nil
    }

    // MARK: - Computed gate state

    public var canAdvanceFromWhoAndWhy: Bool {
        let hasBeneficiary = selectedBeneficiary != nil
            || !beneficiaryQuery.trimmingCharacters(in: .whitespaces).isEmpty
        let hasReason = !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return hasBeneficiary && hasReason
    }

    public var canAdvanceFromWhatAndWhen: Bool {
        endDate >= startDate && !generatedSlots.isEmpty
    }

    private var primaryCTAEnabled: Bool {
        switch step {
        case .whoAndWhy: canAdvanceFromWhoAndWhy
        case .whatAndWhen: canAdvanceFromWhatAndWhen
        case .reviewAndLaunch: !isSubmittingFlag && !generatedSlots.isEmpty
        case .success: true
        }
    }

    private var stepTitle: String {
        switch step {
        case .whoAndWhy: "Who & why"
        case .whatAndWhen: "What & when"
        case .reviewAndLaunch: "Review & launch"
        case .success: "Train launched"
        }
    }

    private var primaryCTALabel: String {
        switch step {
        case .whoAndWhy, .whatAndWhen: "Continue"
        case .reviewAndLaunch: "Launch train"
        case .success: "Open train"
        }
    }

    private var stepDirty: Bool {
        switch step {
        case .whoAndWhy:
            canAdvanceFromWhoAndWhy
                || !beneficiaryQuery.isEmpty
                || !reason.isEmpty
        case .whatAndWhen, .reviewAndLaunch: true
        case .success: false
        }
    }

    // MARK: - Network

    private func searchBeneficiary(query: String) async {
        isSearchingBeneficiary = true
        defer { isSearchingBeneficiary = false }
        do {
            let response: MailComposeRecipientsResponse = try await api.request(
                MailComposeEndpoints.recipients(query: query)
            )
            beneficiaryResults = response.recipients
        } catch {
            beneficiaryResults = []
        }
    }

    private func launch() async {
        isSubmittingFlag = true
        launchError = nil
        defer { isSubmittingFlag = false }
        let trimmedReason = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        let body = CreateSupportTrainBody(
            draftPayload: CreateSupportTrainBody.DraftPayload(story: trimmedReason),
            title: derivedTitle,
            recipientUserId: selectedBeneficiary?.userId,
            sharingMode: visibility.sharingModeWire
        )
        do {
            let created: CreateSupportTrainResponse = try await api.request(
                SupportTrainsEndpoints.create(body: body)
            )
            // Persist the train id immediately — if a follow-up POST
            // fails we still want to drop the organizer into the new
            // train's editor rather than losing the draft.
            publishedTrainId = created.id
            for slot in generatedSlots {
                let slotBody = AddSupportTrainSlotBody(
                    slotDate: slot.dateKey,
                    slotLabel: kind.defaultSlotLabel,
                    supportMode: kind.supportMode,
                    startTime: slot.startTime,
                    endTime: slot.endTime
                )
                _ = try await api.request(
                    SupportTrainsEndpoints.addSlot(
                        supportTrainId: created.id,
                        body: slotBody
                    ),
                    as: EmptyResponse.self
                )
            }
            _ = try await api.request(
                SupportTrainsEndpoints.publish(supportTrainId: created.id),
                as: EmptyResponse.self
            )
            step = .success
        } catch {
            launchError = (error as? APIError)?.errorDescription
                ?? "Couldn't launch the train. Try again."
        }
    }

    // MARK: - Helpers

    private func handleSuccessExit() {
        if let id = publishedTrainId {
            pendingEvent = .openTrain(trainId: id)
        } else {
            pendingEvent = .dismiss
        }
    }

    private func displayName(_ recipient: MailRecipientDTO) -> String {
        recipient.name ?? recipient.username ?? "Recipient"
    }
}
