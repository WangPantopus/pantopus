//
//  Analytics.swift
//  Pantopus
//
//  Typed analytics taxonomy + shim. The taxonomy is closed — any new
//  event must be added to `AnalyticsEvent` so untyped strings never
//  reach the wire. In Debug builds events log to the console; in
//  Release they currently no-op (vendor SDK lands later).
//
//  TODO(analytics): wire `track(_:)` to the real vendor (Amplitude /
//  Mixpanel / PostHog). Today we only mirror events into the Sentry
//  breadcrumb stream via `Observability.shared.track`.
//

import Foundation

/// Closed list of analytics events. Mirrors the P15 taxonomy 1:1; do
/// not invent ad-hoc names — extend the enum instead.
public enum AnalyticsEvent: Sendable, Equatable {
    case screenHubViewed
    case screenMailboxListViewed
    case screenMailboxDrawersViewed
    case screenMyHomesViewed
    case screenHomeDashboardViewed
    case screenMailboxItemDetailViewed(category: String, trustLevel: String)
    case screenEditProfileViewed
    case screenAddHomeWizardStepViewed(stepNumber: Int, stepName: String)
    case screenClaimOwnershipStepViewed(stepName: String)
    case screenMyClaimsViewed
    case screenPetsListViewed
    case screenPetsWizardStepViewed(stepNumber: Int, stepName: String)
    case screenPulseFeedViewed(intent: String)
    case ctaHubActionStripTapped(label: String)
    case ctaHubPillarTapped(pillar: String)
    case ctaMailboxItemLogReceived
    case ctaAddHomeSubmit
    case ctaClaimOwnershipSubmit(result: AnalyticsResult)
    case formEditProfileSubmit(result: AnalyticsResult)
    case formEditProfileValidationError(field: String)

    /// Wire-format event name. Stable across versions — vendor / dashboard
    /// owners depend on these strings.
    public var name: String {
        switch self {
        case .screenHubViewed: "screen.hub.viewed"
        case .screenMailboxListViewed: "screen.mailbox_list.viewed"
        case .screenMailboxDrawersViewed: "screen.mailbox_drawers.viewed"
        case .screenMyHomesViewed: "screen.my_homes.viewed"
        case .screenHomeDashboardViewed: "screen.home_dashboard.viewed"
        case .screenMailboxItemDetailViewed: "screen.mailbox_item_detail.viewed"
        case .screenEditProfileViewed: "screen.edit_profile.viewed"
        case .screenAddHomeWizardStepViewed: "screen.add_home_wizard.step_viewed"
        case .screenClaimOwnershipStepViewed: "screen.claim_ownership_wizard.step_viewed"
        case .screenMyClaimsViewed: "screen.my_claims.viewed"
        case .screenPetsListViewed: "screen.pets_list.viewed"
        case .screenPetsWizardStepViewed: "screen.pets_wizard.step_viewed"
        case .screenPulseFeedViewed: "screen.pulse_feed.viewed"
        case .ctaHubActionStripTapped: "cta.hub.action_strip_tapped"
        case .ctaHubPillarTapped: "cta.hub.pillar_tapped"
        case .ctaMailboxItemLogReceived: "cta.mailbox_item.log_received"
        case .ctaAddHomeSubmit: "cta.add_home.submit"
        case .ctaClaimOwnershipSubmit: "cta.claim_ownership.submit"
        case .formEditProfileSubmit: "form.edit_profile.submit"
        case .formEditProfileValidationError: "form.edit_profile.validation_error"
        }
    }

    /// Per-event property bag. Properties are flat string-keyed so the
    /// vendor schema doesn't need nested types.
    public var properties: [String: String] {
        switch self {
        case let .screenMailboxItemDetailViewed(category, trustLevel):
            ["category": category, "trust_level": trustLevel]
        case let .screenAddHomeWizardStepViewed(stepNumber, stepName):
            ["step_number": "\(stepNumber)", "step_name": stepName]
        case let .screenPetsWizardStepViewed(stepNumber, stepName):
            ["step_number": "\(stepNumber)", "step_name": stepName]
        case let .screenClaimOwnershipStepViewed(stepName):
            ["step_name": stepName]
        case let .ctaHubActionStripTapped(label):
            ["label": label]
        case let .ctaHubPillarTapped(pillar):
            ["pillar": pillar]
        case let .screenPulseFeedViewed(intent):
            ["intent": intent]
        case let .ctaClaimOwnershipSubmit(result):
            ["result": result.rawValue]
        case let .formEditProfileSubmit(result):
            ["result": result.rawValue]
        case let .formEditProfileValidationError(field):
            ["field": field]
        case .screenHubViewed,
             .screenMailboxListViewed,
             .screenMailboxDrawersViewed,
             .screenMyHomesViewed,
             .screenHomeDashboardViewed,
             .screenMyClaimsViewed,
             .screenPetsListViewed,
             .screenEditProfileViewed,
             .ctaMailboxItemLogReceived,
             .ctaAddHomeSubmit:
            [:]
        }
    }
}

/// Standard outcomes for form submissions and other yes/no telemetry.
public enum AnalyticsResult: String, Sendable {
    case success
    case error
}

/// Analytics shim. Feature code calls `Analytics.track(.screenHubViewed)`
/// and we route to the Observability layer. In Debug builds the event
/// also lands in the Xcode console for quick verification.
public enum Analytics {
    public static func track(_ event: AnalyticsEvent) {
        #if DEBUG
        let propsDescription = event.properties.isEmpty
            ? ""
            : " " + event.properties.map { "\($0.key)=\($0.value)" }.joined(separator: " ")
        print("📊 analytics \(event.name)\(propsDescription)")
        #endif
        Task { @MainActor in
            Observability.shared.track(event.name, properties: event.properties)
        }
    }
}
