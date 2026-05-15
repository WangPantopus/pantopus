//
//  StatusWaitingContent.swift
//  Pantopus
//
//  Render-only model for the T3.6 Status / Waiting screen. The view
//  itself is purely presentational — the caller builds the content
//  and passes it in. Three canonical frames are exposed as static
//  factories on `StatusWaitingContent` so call sites can compose by
//  intent ("claim submitted") rather than slot.
//

import Foundation

/// Hero illustration tone. The view picks an icon + tint per case.
public enum StatusIllustration: String, Sendable, Hashable, CaseIterable {
    /// Big success check, green halo. "Claim submitted" / "Home added".
    case success
    /// Spinning clock, amber halo. "Under review".
    case waiting
    /// Mailbox icon, primary halo. "Check your email".
    case email
}

/// One stage in the optional bottom timeline. Mirrors the existing
/// `TimelineBlockStage` shape so the view can hand the array straight
/// through.
public struct StatusTimelineStage: Sendable, Hashable, Identifiable {
    public let id: String
    public let label: String

    public init(id: String, label: String) {
        self.id = id
        self.label = label
    }
}

/// One row in the action-cards stack.
public struct StatusActionCard: Sendable, Hashable, Identifiable {
    public let id: String
    public let icon: PantopusIcon
    public let title: String
    public let subtitle: String?

    public init(id: String, icon: PantopusIcon, title: String, subtitle: String? = nil) {
        self.id = id
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
    }
}

/// CTA at the sticky bottom row. Carries a label + an opaque key so
/// tests can verify "the right CTA fired" without inspecting closure
/// identity (closures aren't Hashable in Swift).
public struct StatusCTA: Sendable, Hashable {
    public let label: String
    public let actionKey: String

    public init(label: String, actionKey: String) {
        self.label = label
        self.actionKey = actionKey
    }
}

/// Snapshot the view renders. Every slot the prompt's DoD lists is
/// represented; optional fields hide their section when nil/empty.
public struct StatusWaitingContent: Sendable, Hashable {
    public let illustration: StatusIllustration
    public let headline: String
    public let subcopy: String
    public let timeline: [StatusTimelineStage]
    public let currentStageId: String?
    public let etaChip: String?
    public let actionCards: [StatusActionCard]
    public let explainerBullets: [String]
    public let primaryCta: StatusCTA?
    public let secondaryCta: StatusCTA?

    public init(
        illustration: StatusIllustration,
        headline: String,
        subcopy: String,
        timeline: [StatusTimelineStage] = [],
        currentStageId: String? = nil,
        etaChip: String? = nil,
        actionCards: [StatusActionCard] = [],
        explainerBullets: [String] = [],
        primaryCta: StatusCTA? = nil,
        secondaryCta: StatusCTA? = nil
    ) {
        self.illustration = illustration
        self.headline = headline
        self.subcopy = subcopy
        self.timeline = timeline
        self.currentStageId = currentStageId
        self.etaChip = etaChip
        self.actionCards = actionCards
        self.explainerBullets = explainerBullets
        self.primaryCta = primaryCta
        self.secondaryCta = secondaryCta
    }
}

// MARK: - Presets (the three design frames)

public extension StatusWaitingContent {
    /// Three-stage progress used by every Homes flow. Stages are
    /// `submitted / review / complete` — homes wizards highlight one
    /// of them depending on where the claim is.
    static let homesClaimTimeline: [StatusTimelineStage] = [
        StatusTimelineStage(id: "submitted", label: "Submitted"),
        StatusTimelineStage(id: "review", label: "Under review"),
        StatusTimelineStage(id: "complete", label: "Complete")
    ]

    /// Frame 1 — Success. Right after the user POSTs a claim or
    /// finishes the Add-Home wizard.
    static func claimSubmitted(
        homeName: String?,
        eta: String? = "2–3 days",
        onOpenClaim: @escaping () -> Void = {},
        onBackToHub: @escaping () -> Void = {}
    ) -> StatusWaitingContent {
        let venue = (homeName?.isEmpty == false) ? homeName! : "this home"
        return StatusWaitingContent(
            illustration: .success,
            headline: "Claim submitted",
            subcopy:
                "We'll review your evidence and email you when \(venue) is verified.",
            timeline: homesClaimTimeline,
            currentStageId: "submitted",
            etaChip: eta,
            actionCards: [
                StatusActionCard(
                    id: "checkInbox",
                    icon: .mailbox,
                    title: "Check your inbox",
                    subtitle: "We'll send a verification email shortly."
                ),
                StatusActionCard(
                    id: "viewClaim",
                    icon: .file,
                    title: "View claim details",
                    subtitle: "See what you submitted and add more evidence."
                )
            ],
            explainerBullets: [
                "We compare your evidence against any prior claims.",
                "If we need more, we'll ask via email — never SMS.",
                "Most claims resolve in 2–3 days."
            ],
            primaryCta: StatusCTA(label: "Back to Hub", actionKey: "back_to_hub"),
            secondaryCta: StatusCTA(label: "View claim", actionKey: "view_claim")
        )
    }

    /// Frame 2 — Under review. Reached from MyClaimsList or a deep
    /// link while the backend is still processing.
    static func underReview(
        homeName: String?,
        submittedAgo: String? = nil
    ) -> StatusWaitingContent {
        let venue = (homeName?.isEmpty == false) ? homeName! : "your claim"
        var subcopy = "We're reviewing your evidence. We'll email you when \(venue) is verified."
        if let submittedAgo, !submittedAgo.isEmpty {
            subcopy += " Submitted \(submittedAgo)."
        }
        return StatusWaitingContent(
            illustration: .waiting,
            headline: "Under review",
            subcopy: subcopy,
            timeline: homesClaimTimeline,
            currentStageId: "review",
            etaChip: "Usually resolved in 2–3 days",
            actionCards: [
                StatusActionCard(
                    id: "addEvidence",
                    icon: .upload,
                    title: "Add more evidence",
                    subtitle: "Strengthen your claim with extra documents."
                ),
                StatusActionCard(
                    id: "contactSupport",
                    icon: .mailbox,
                    title: "Contact support",
                    subtitle: "Stuck for more than 3 days? Reach out."
                )
            ],
            explainerBullets: [
                "Pantopus never asks for payment to speed up a review.",
                "You can keep using the rest of the app while we verify.",
                "We'll notify you in-app AND by email when it resolves."
            ],
            primaryCta: StatusCTA(label: "Back to Hub", actionKey: "back_to_hub"),
            secondaryCta: StatusCTA(label: "View claim", actionKey: "view_claim")
        )
    }

    /// Frame 3 — Check your email. Email-verification frame fed from
    /// `/api/users/me` (or the response from a /resend call).
    static func checkYourEmail(
        email: String?,
        onResend: @escaping () -> Void = {},
        onChangeEmail: @escaping () -> Void = {}
    ) -> StatusWaitingContent {
        let recipient = email.map { "We just sent a link to \($0)." }
            ?? "We just sent a verification link to your email."
        return StatusWaitingContent(
            illustration: .email,
            headline: "Check your email",
            subcopy: recipient + " Tap the link to finish setting up your account.",
            timeline: [],
            currentStageId: nil,
            etaChip: "Should arrive in under a minute",
            actionCards: [
                StatusActionCard(
                    id: "openMail",
                    icon: .mailbox,
                    title: "Open Mail",
                    subtitle: "Jump straight to your inbox."
                ),
                StatusActionCard(
                    id: "resendEmail",
                    icon: .send,
                    title: "Resend verification",
                    subtitle: "Didn't get it? We'll send another."
                )
            ],
            explainerBullets: [
                "Links expire 30 minutes after they're sent.",
                "Check your spam folder if it doesn't arrive.",
                "You can change your email below if it was wrong."
            ],
            primaryCta: StatusCTA(label: "Resend email", actionKey: "resend_email"),
            secondaryCta: StatusCTA(label: "Change email", actionKey: "change_email")
        )
    }
}
