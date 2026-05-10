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
    case ctaHubActionStripTapped(label: String)
    case ctaHubPillarTapped(pillar: String)
    case ctaMailboxItemLogReceived
    case ctaAddHomeSubmit
    case formEditProfileSubmit(result: AnalyticsResult)
    case formEditProfileValidationError(field: String)

    /// Wire-format event name. Stable across versions — vendor / dashboard
    /// owners depend on these strings.
    public var name: String {
        switch self {
        case .screenHubViewed: return "screen.hub.viewed"
        case .screenMailboxListViewed: return "screen.mailbox_list.viewed"
        case .screenMailboxDrawersViewed: return "screen.mailbox_drawers.viewed"
        case .screenMyHomesViewed: return "screen.my_homes.viewed"
        case .screenHomeDashboardViewed: return "screen.home_dashboard.viewed"
        case .screenMailboxItemDetailViewed: return "screen.mailbox_item_detail.viewed"
        case .screenEditProfileViewed: return "screen.edit_profile.viewed"
        case .screenAddHomeWizardStepViewed: return "screen.add_home_wizard.step_viewed"
        case .ctaHubActionStripTapped: return "cta.hub.action_strip_tapped"
        case .ctaHubPillarTapped: return "cta.hub.pillar_tapped"
        case .ctaMailboxItemLogReceived: return "cta.mailbox_item.log_received"
        case .ctaAddHomeSubmit: return "cta.add_home.submit"
        case .formEditProfileSubmit: return "form.edit_profile.submit"
        case .formEditProfileValidationError: return "form.edit_profile.validation_error"
        }
    }

    /// Per-event property bag. Properties are flat string-keyed so the
    /// vendor schema doesn't need nested types.
    public var properties: [String: String] {
        switch self {
        case .screenMailboxItemDetailViewed(let category, let trustLevel):
            return ["category": category, "trust_level": trustLevel]
        case .screenAddHomeWizardStepViewed(let stepNumber, let stepName):
            return ["step_number": "\(stepNumber)", "step_name": stepName]
        case .ctaHubActionStripTapped(let label):
            return ["label": label]
        case .ctaHubPillarTapped(let pillar):
            return ["pillar": pillar]
        case .formEditProfileSubmit(let result):
            return ["result": result.rawValue]
        case .formEditProfileValidationError(let field):
            return ["field": field]
        case .screenHubViewed,
             .screenMailboxListViewed,
             .screenMailboxDrawersViewed,
             .screenMyHomesViewed,
             .screenHomeDashboardViewed,
             .screenEditProfileViewed,
             .ctaMailboxItemLogReceived,
             .ctaAddHomeSubmit:
            return [:]
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
