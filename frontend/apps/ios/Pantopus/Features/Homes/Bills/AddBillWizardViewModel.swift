//
//  AddBillWizardViewModel.swift
//  Pantopus
//
//  3-step Wizard for `POST /api/homes/:id/bills` (route
//  `backend/routes/home.js:4539`).
//
//   1. Payee + amount + due date
//   2. Schedule (one-time / recurring monthly) — stored in `details.schedule`
//   3. Review & confirm — POSTs the bill
//
//  Backend caveat: there is no POST/PATCH for `:billId/splits` today, so
//  the design's "Split between household members" step is not modelled
//  here. That decision is tracked in the parity audit and surfaces in
//  the wizard as a note on the review step.
//

import Foundation
import Observation

/// Step identifiers for the Add Bill wizard.
public enum AddBillStep: String, Sendable, CaseIterable {
    case details
    case schedule
    case review
    case success
}

/// Recurrence options exposed on step 2.
public enum AddBillSchedule: String, Sendable, CaseIterable, Identifiable {
    case oneTime
    case monthly
    case quarterly
    case yearly

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .oneTime: "One-time"
        case .monthly: "Recurring monthly"
        case .quarterly: "Recurring quarterly"
        case .yearly: "Recurring yearly"
        }
    }

    public var detailsKey: String {
        switch self {
        case .oneTime: "one_time"
        case .monthly: "monthly"
        case .quarterly: "quarterly"
        case .yearly: "yearly"
        }
    }
}

/// Outbound events the wizard view must react to.
public enum AddBillOutboundEvent: Sendable, Equatable {
    case dismiss
    case created(billId: String)
}

@Observable
@MainActor
final class AddBillWizardViewModel: WizardModel {
    // Step 1 fields
    var payee: String = ""
    var amount: String = ""
    var dueDate: Date?

    // Step 2 fields
    var schedule: AddBillSchedule = .oneTime

    // Lifecycle
    private(set) var currentStep: AddBillStep = .details
    private(set) var isSubmitting: Bool = false
    private(set) var submitError: String?
    private(set) var createdBillId: String?
    var pendingEvent: AddBillOutboundEvent?

    private let homeId: String
    private let api: APIClient

    init(homeId: String, api: APIClient = .shared) {
        self.homeId = homeId
        self.api = api
    }

    // MARK: - WizardModel

    var chrome: WizardChrome {
        switch currentStep {
        case .details:
            WizardChrome(
                title: "Add a bill",
                progressLabel: .stepOf(current: 1, total: 3),
                progressFraction: 1.0 / 3.0,
                leading: .close,
                primaryCTALabel: "Next",
                primaryCTAEnabled: detailsValid,
                isSubmitting: false,
                dirty: isDirty,
                showsProgressBar: true
            )
        case .schedule:
            WizardChrome(
                title: "Schedule",
                progressLabel: .stepOf(current: 2, total: 3),
                progressFraction: 2.0 / 3.0,
                leading: .back,
                primaryCTALabel: "Next",
                primaryCTAEnabled: true,
                isSubmitting: false,
                dirty: isDirty,
                showsProgressBar: true
            )
        case .review:
            WizardChrome(
                title: "Review",
                progressLabel: .stepOf(current: 3, total: 3),
                progressFraction: 3.0 / 3.0,
                leading: .back,
                primaryCTALabel: "Add bill",
                primaryCTAEnabled: !isSubmitting,
                isSubmitting: isSubmitting,
                dirty: isDirty,
                showsProgressBar: true
            )
        case .success:
            WizardChrome(
                title: "Bill added",
                progressLabel: .hidden,
                progressFraction: nil,
                leading: .close,
                primaryCTALabel: "Done",
                primaryCTAEnabled: true,
                secondaryCTA: nil,
                isSubmitting: false,
                dirty: false,
                showsProgressBar: false
            )
        }
    }

    func leadingTapped() {
        switch currentStep {
        case .details:
            pendingEvent = .dismiss
        case .schedule:
            currentStep = .details
        case .review:
            currentStep = .schedule
        case .success:
            pendingEvent = .dismiss
        }
    }

    func discardConfirmed() {
        pendingEvent = .dismiss
    }

    func primaryTapped() {
        switch currentStep {
        case .details:
            currentStep = .schedule
            Analytics.track(.screenAddBillWizardStepViewed(stepNumber: 2, stepName: "schedule"))
        case .schedule:
            currentStep = .review
            Analytics.track(.screenAddBillWizardStepViewed(stepNumber: 3, stepName: "review"))
        case .review:
            Task { await submit() }
        case .success:
            if let id = createdBillId {
                pendingEvent = .created(billId: id)
            } else {
                pendingEvent = .dismiss
            }
        }
    }

    // MARK: - Validation

    var detailsValid: Bool {
        !payee.trimmingCharacters(in: .whitespaces).isEmpty
            && parsedAmount() != nil
    }

    var isDirty: Bool {
        !payee.trimmingCharacters(in: .whitespaces).isEmpty
            || !amount.trimmingCharacters(in: .whitespaces).isEmpty
            || dueDate != nil
            || schedule != .oneTime
    }

    func parsedAmount() -> Decimal? {
        let trimmed = amount.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty,
              let value = Decimal(string: trimmed),
              value > 0
        else {
            return nil
        }
        return value
    }

    // MARK: - Submit

    func submit() async {
        guard let amountValue = parsedAmount(), !isSubmitting else { return }
        if !NetworkMonitor.shared.isOnline {
            submitError = "You're offline. Try again when you're back online."
            return
        }
        isSubmitting = true
        submitError = nil
        defer { isSubmitting = false }

        var details: [String: String] = ["schedule": schedule.detailsKey]
        if schedule != .oneTime {
            details["frequency"] = schedule.detailsKey
        }
        let request = CreateBillRequest(
            billType: "other",
            providerName: payee.trimmingCharacters(in: .whitespaces),
            amount: amountValue,
            dueDate: dueDate.map { AddBillWizardViewModel.formatISODate($0) },
            details: details
        )

        do {
            let response: HomeBillResponse = try await api.request(
                HomesEndpoints.createBill(homeId: homeId, request: request)
            )
            createdBillId = response.bill.id
            currentStep = .success
            Analytics.track(.ctaAddBillSubmit(result: .success))
        } catch {
            submitError = (error as? APIError)?.errorDescription
                ?? "Couldn't add this bill."
            Analytics.track(.ctaAddBillSubmit(result: .error))
        }
    }

    /// `yyyy-MM-dd` formatter built per-call so we don't have to manage a
    /// static `DateFormatter` under Swift 6 strict concurrency.
    private static func formatISODate(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(secondsFromGMT: 0)
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: date)
    }
}
