//
//  SchedulingOnboardingModel.swift
//  Pantopus
//
//  A6 Scheduling Onboarding view-model — Home (green, 3 steps: Members ·
//  Combine · Share) and Business (violet, 4 steps: Link · Service · Team ·
//  Confirm). On finish: Business claims its slug (PUT booking-page/slug), then
//  both create an event type for the owner (collective/round_robin for Home;
//  one_on_one + requires_approval for Business). Conforms `WizardModel`.
//  Matches `onboarding-home-frames.jsx` / `onboarding-business-frames.jsx`.
//

import Foundation
import Observation
import SwiftUI

@Observable
@MainActor
final class SchedulingOnboardingModel: WizardModel {
    enum Flow { case home, business }

    let flow: Flow
    let owner: SchedulingOwner
    let push: @MainActor (SchedulingRoute) -> Void

    private(set) var stepIndex = 1
    private(set) var isFinished = false
    var pendingShareURL: URL?
    private(set) var isSubmitting = false

    // Home state.
    var selectedMembers: Set<String> = ["you"]
    var combineMode: String = "collective" // collective | round_robin
    var roundRobinRule: String = "balanced" // balanced | priority

    /// Business state.
    var slug = "" {
        didSet { if slug != oldValue { onSlugEdited() } }
    }

    private(set) var slugState: SlugFieldState = .idle
    var serviceType = "consultation"
    var duration = 30
    var priceText = "120"
    var seatedTeam: Set<String> = ["owner"]
    var confirmMode = "approve" // auto | approve

    private let client = SchedulingClient.shared
    private var slugCheckTask: Task<Void, Never>?

    var theme: SchedulingIdentityTheme {
        owner.theme
    }

    var identity: WizardIdentity {
        flow == .home ? .home : .business
    }

    var accent: Color {
        identity.accent
    }

    var accentBg: Color {
        identity.accentBg
    }

    var paidEnabled: Bool {
        SchedulingFeatureFlags.paidEnabled
    }

    let timezoneIdentifier: String

    init(owner: SchedulingOwner, push: @escaping @MainActor (SchedulingRoute) -> Void) {
        self.owner = owner
        self.push = push
        switch owner {
        case .business: flow = .business
        default: flow = .home
        }
        timezoneIdentifier = SchedulingTime.deviceTimeZoneIdentifier
    }

    // MARK: Steps metadata

    var steps: [(Int, String)] {
        switch flow {
        case .home: [(1, "Members"), (2, "Combine"), (3, "Share")]
        case .business: [(1, "Link"), (2, "Service"), (3, "Team"), (4, "Confirm")]
        }
    }

    var totalSteps: Int {
        steps.count
    }

    var isSuccess: Bool {
        stepIndex > totalSteps
    }

    var displayStep: Int {
        min(stepIndex, totalSteps)
    }

    // MARK: Derived

    var shareLink: String {
        let s = slug.isEmpty ? "your-link" : slug
        switch flow {
        case .home: return "pantopus.com/book/family"
        case .business: return "pantopus.com/book/\(s)"
        }
    }

    private var bizStep1Ready: Bool {
        if case .available = slugState { return true }
        return false
    }

    // MARK: WizardModel chrome

    var chrome: WizardChrome {
        let title = flow == .home ? "Family scheduling" : "Business booking"
        if isSuccess {
            return WizardChrome(
                title: title,
                progressLabel: .stepOf(current: totalSteps, total: totalSteps),
                progressFraction: 1,
                leading: .back,
                primaryCTALabel: "Share link",
                primaryCTAEnabled: true,
                primaryCTAIdentifier: "onboardingShare",
                secondaryCTA: WizardSecondaryCTA(
                    label: flow == .home ? "Members" : "Add service",
                    identifier: "onboardingSecondary",
                    icon: flow == .home ? .users : .plus
                ),
                isSubmitting: false,
                dirty: false,
                showsProgressBar: false
            )
        }
        return WizardChrome(
            title: title,
            progressLabel: .stepOf(current: displayStep, total: totalSteps),
            progressFraction: Double(displayStep) / Double(totalSteps),
            // Design: every onboarding step (incl. step 1) leads with a back
            // chevron — no X / discard sheet. `.back` exits via leadingTapped().
            leading: .back,
            primaryCTALabel: primaryLabel,
            primaryCTAEnabled: primaryEnabled,
            primaryCTAIdentifier: "onboardingPrimary",
            secondaryCTA: secondaryCTA,
            isSubmitting: isSubmitting,
            dirty: stepIndex > 1 || !slug.isEmpty || selectedMembers.count > 1,
            showsProgressBar: false
        )
    }

    private var primaryLabel: String {
        switch flow {
        case .home:
            switch stepIndex {
            case 1: "Continue · \(selectedMembers.count) selected"
            default: "Continue"
            }
        case .business:
            switch stepIndex {
            case 1: "Continue · add a service"
            case 4: "Finish setup"
            default: "Continue"
            }
        }
    }

    private var primaryEnabled: Bool {
        if flow == .business && stepIndex == 1 { return bizStep1Ready }
        if flow == .home && stepIndex == 1 { return !selectedMembers.isEmpty }
        return true
    }

    private var secondaryCTA: WizardSecondaryCTA? {
        switch flow {
        case .home where stepIndex == 2:
            WizardSecondaryCTA(label: "Use defaults", identifier: "onboardingDefaults")
        case .business where stepIndex == 2:
            WizardSecondaryCTA(label: "Use defaults", identifier: "onboardingDefaults")
        case .business where stepIndex == 3:
            WizardSecondaryCTA(label: "Skip · just me", identifier: "onboardingSkipTeam")
        default:
            nil
        }
    }

    func leadingTapped() {
        if isSuccess { stepIndex = totalSteps
            return
        }
        if stepIndex == 1 { isFinished = true } else { stepIndex -= 1 }
    }

    func discardConfirmed() {
        isFinished = true
    }

    func primaryTapped() {
        guard !isSubmitting else { return }
        if isSuccess {
            pendingShareURL = URL(string: "https://\(shareLink)")
            return
        }
        if stepIndex < totalSteps {
            if flow == .business && stepIndex == 1 {
                Task { await claimSlug() }
            } else {
                stepIndex += 1
            }
        } else {
            Task { await finishSetup() }
        }
    }

    func secondaryTapped() {
        if isSuccess { isFinished = true
            return
        }
        // Defaults / skip simply advance.
        if stepIndex < totalSteps { stepIndex += 1 } else { Task { await finishSetup() } }
    }

    func finishAfterShare() {
        isFinished = true
    }

    // MARK: Members / team selection

    func toggleMember(_ id: String) {
        if selectedMembers.contains(id) { selectedMembers.remove(id) } else { selectedMembers.insert(id) }
    }

    func toggleSeat(_ id: String) {
        if seatedTeam.contains(id) { seatedTeam.remove(id) } else { seatedTeam.insert(id) }
    }

    func pickSuggestion(_ s: String) {
        slug = s
    }

    // MARK: Slug check (Business)

    private func onSlugEdited() {
        slugCheckTask?.cancel()
        let candidate = slug.trimmingCharacters(in: .whitespaces)
        guard !candidate.isEmpty else { slugState = .idle
            return
        }
        slugState = .checking
        slugCheckTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 450_000_000)
            guard !Task.isCancelled else { return }
            await self?.runSlugCheck(candidate)
        }
    }

    private func runSlugCheck(_ candidate: String) async {
        guard candidate == slug.trimmingCharacters(in: .whitespaces) else { return }
        do {
            let result: CheckSlugResponse = try await client.request(SchedulingEndpoints.checkSlug(owner: owner, slug: candidate))
            guard candidate == slug.trimmingCharacters(in: .whitespaces) else { return }
            slugState = result.available ? .available : .taken(suggestions: result.suggestions ?? [])
        } catch {
            guard candidate == slug.trimmingCharacters(in: .whitespaces) else { return }
            slugState = .idle
        }
    }

    private func claimSlug() async {
        let candidate = slug.trimmingCharacters(in: .whitespaces)
        guard !candidate.isEmpty else { return }
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            _ = try await client.request(SchedulingEndpoints.updateBookingPageSlug(
                owner: owner,
                BookingPageSlugRequest(slug: candidate)
            )) as BookingPageResponse
            stepIndex = 2
        } catch let error as SchedulingError {
            if error.code == "SLUG_TAKEN" { await runSlugCheck(candidate) } else { slugState = .taken(suggestions: []) }
        } catch {
            slugState = .taken(suggestions: [])
        }
    }

    // MARK: Finish — create the owner's event type

    private func finishSetup() async {
        isSubmitting = true
        defer { isSubmitting = false }
        // Seed page timezone (best-effort).
        _ = try? await client.request(SchedulingEndpoints.updateBookingPage(
            owner: owner,
            BookingPageUpdateRequest(timezone: timezoneIdentifier)
        )) as BookingPageResponse

        let assignment = flow == .home ? (combineMode == "round_robin" ? "round_robin" : "collective") : "one_on_one"
        let requiresApproval = flow == .business ? (confirmMode == "approve") : false
        let priceCents: Int? = (flow == .business && paidEnabled) ? (Int(priceText).map { $0 * 100 }) : nil

        let name = flow == .home ? "Household meeting" : "\(serviceTypeLabel)"
        var attempt = 0
        while attempt < 4 {
            let suffix = attempt == 0 ? "" : "-\(attempt + 1)"
            let etSlug = "\(flow == .home ? "household" : serviceType)-meeting\(suffix)"
            let request = CreateEventTypeRequest(
                name: name,
                slug: etSlug,
                durations: [duration],
                defaultDuration: duration,
                locationMode: flow == .business ? "in_person" : "video",
                assignmentMode: assignment,
                requiresApproval: requiresApproval,
                priceCents: priceCents
            )
            do {
                _ = try await client.request(SchedulingEndpoints.createEventType(owner: owner, request)) as EventTypeResponse
                stepIndex = totalSteps + 1
                return
            } catch let error as SchedulingError {
                if error.code == "SLUG_TAKEN" { attempt += 1
                    continue
                }
                stepIndex = totalSteps + 1
                return
            } catch {
                stepIndex = totalSteps + 1
                return
            }
        }
        stepIndex = totalSteps + 1
    }

    private var serviceTypeLabel: String {
        switch serviceType {
        case "quote": "Quote visit"
        case "survey": "Site survey"
        case "service_call": "Service call"
        default: "Consultation"
        }
    }
}
