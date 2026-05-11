//
//  PostAuthorHeader.swift
//  Pantopus
//
//  `post_author` slot for the Pulse post detail. 44pt avatar + verified
//  badge, two-line identity stack, right-aligned intent chip.
//
// swiftlint:disable multiple_closures_with_trailing_closure

import SwiftUI

/// One of the five intent buckets the design draws for a post. Source:
/// `Pantopus_pages/content-detail-frames.jsx::FramePost`. The backend's
/// `purpose` enum is wider (13 values); the view-model collapses those
/// onto these five for display.
public enum PostIntent: String, Sendable, CaseIterable, Identifiable {
    case ask
    case offer
    case event
    case share
    case alert

    public var id: String {
        rawValue
    }

    /// Display label.
    public var label: String {
        switch self {
        case .ask: "Ask"
        case .offer: "Offer"
        case .event: "Event"
        case .share: "Share"
        case .alert: "Alert"
        }
    }

    /// Maps to a semantic `StatusChip` variant.
    public var chipVariant: StatusChipVariant {
        switch self {
        case .ask: .info
        case .offer: .success
        case .event: .personal
        case .share: .neutral
        case .alert: .warning
        }
    }

    /// Convert a backend `Post.purpose` / `Post.post_type` token into the
    /// nearest UI intent. Returns `.share` for unknown / generic values.
    public static func from(purpose: String?, postType: String?) -> PostIntent {
        let needle = (purpose ?? postType ?? "").lowercased()
        switch needle {
        case "ask": return .ask
        case "offer": return .offer
        case "event": return .event
        case "alert", "safety", "heads_up": return .alert
        case "deal", "recommend", "share", "showcase", "story",
             "neighborhood_win", "visitor_guide", "local_update", "learn",
             "lost_found":
            return .share
        default: return .share
        }
    }
}

/// Pulse post header. Renders the author identity row + intent chip used
/// in the design's `FramePost` slot.
@MainActor
public struct PostAuthorHeader: View {
    private let displayName: String
    private let avatarURL: URL?
    private let isVerified: Bool
    private let identity: IdentityPillar
    private let timeAndLocality: String
    private let intent: PostIntent
    private let onAvatarTap: (@MainActor () -> Void)?

    public init(
        displayName: String,
        avatarURL: URL?,
        isVerified: Bool,
        identity: IdentityPillar,
        timeAndLocality: String,
        intent: PostIntent,
        onAvatarTap: (@MainActor () -> Void)? = nil
    ) {
        self.displayName = displayName
        self.avatarURL = avatarURL
        self.isVerified = isVerified
        self.identity = identity
        self.timeAndLocality = timeAndLocality
        self.intent = intent
        self.onAvatarTap = onAvatarTap
    }

    public var body: some View {
        HStack(alignment: .top, spacing: Spacing.s3) {
            avatarTile
            VStack(alignment: .leading, spacing: 2) {
                Text(displayName)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Text(timeAndLocality)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer()
            StatusChip(intent.label, variant: intent.chipVariant)
                .accessibilityLabel("\(intent.label) post")
        }
        .padding(.horizontal, Spacing.s4)
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder private var avatarTile: some View {
        if let onAvatarTap {
            Button(action: { onAvatarTap() }) {
                avatarStack
            }
            .buttonStyle(.plain)
            .frame(minWidth: 44, minHeight: 44)
            .accessibilityLabel("Open \(displayName)'s profile")
        } else {
            avatarStack
        }
    }

    private var avatarStack: some View {
        ZStack(alignment: .bottomTrailing) {
            AvatarWithIdentityRing(
                name: displayName,
                imageURL: avatarURL,
                identity: identity,
                ringProgress: 1,
                size: 44
            )
            if isVerified {
                VerifiedBadge(size: 16)
                    .offset(x: 2, y: 2)
            }
        }
        .frame(width: 48, height: 48, alignment: .topLeading)
    }
}

#Preview("All intents") {
    VStack(alignment: .leading, spacing: Spacing.s4) {
        ForEach(PostIntent.allCases) { intent in
            PostAuthorHeader(
                displayName: "Alex Rivera",
                avatarURL: nil,
                isVerified: intent == .ask || intent == .offer,
                identity: .personal,
                timeAndLocality: "12m ago · Cambridge, MA",
                intent: intent
            )
        }
    }
    .padding(.vertical, Spacing.s4)
    .background(Theme.Color.appBg)
}
