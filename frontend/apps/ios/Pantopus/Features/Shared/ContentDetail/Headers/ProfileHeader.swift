//
//  ProfileHeader.swift
//  Pantopus
//
//  `profile_header` slot for the Public profile detail. Centered 72pt
//  avatar with 28pt VerifiedBadge overlay, name + handle/locality row,
//  identity-pillar chip row.
//

import SwiftUI

/// Verification state for one of the three identity pillars.
public enum IdentityPillarVerificationState: Sendable, Equatable {
    case verified
    case unverified
}

/// Lightweight tuple — pillar + its verification state.
public struct IdentityPillarBadge: Sendable, Identifiable, Hashable {
    public let pillar: IdentityPillar
    public let state: IdentityPillarVerificationState

    public init(pillar: IdentityPillar, state: IdentityPillarVerificationState) {
        self.pillar = pillar
        self.state = state
    }

    public var id: String {
        switch pillar {
        case .personal: "personal"
        case .home: "home"
        case .business: "business"
        }
    }

    var label: String {
        switch pillar {
        case .personal: "Personal"
        case .home: "Home"
        case .business: "Business"
        }
    }

    var chipVariant: StatusChipVariant {
        switch (pillar, state) {
        case (.personal, .verified): .personal
        case (.home, .verified): .home
        case (.business, .verified): .business
        case (_, .unverified): .neutral
        }
    }

    var leadingIcon: PantopusIcon {
        state == .verified ? .check : .circle
    }
}

/// Centered profile header: 72pt avatar + verified-badge overlay, name,
/// handle/locality, identity-pillar chip row.
@MainActor
public struct ProfileHeader: View {
    private let displayName: String
    private let handle: String?
    private let locality: String?
    private let avatarURL: URL?
    private let isVerified: Bool
    private let identityBadges: [IdentityPillarBadge]
    /// `nil` renders the badges as non-tappable status chips. Pass a
    /// real handler to make them open an identity-detail surface.
    private let onBadgeTap: (@MainActor (IdentityPillar) -> Void)?

    public init(
        displayName: String,
        handle: String?,
        locality: String?,
        avatarURL: URL?,
        isVerified: Bool,
        identityBadges: [IdentityPillarBadge],
        onBadgeTap: (@MainActor (IdentityPillar) -> Void)? = nil
    ) {
        self.displayName = displayName
        self.handle = handle
        self.locality = locality
        self.avatarURL = avatarURL
        self.isVerified = isVerified
        self.identityBadges = identityBadges
        self.onBadgeTap = onBadgeTap
    }

    public var body: some View {
        VStack(spacing: Spacing.s3) {
            ZStack(alignment: .bottomTrailing) {
                AvatarWithIdentityRing(
                    name: displayName,
                    imageURL: avatarURL,
                    identity: .personal,
                    ringProgress: 1,
                    size: 72
                )
                if isVerified {
                    VerifiedBadge(size: 28).offset(x: 4, y: 4)
                }
            }
            .frame(width: 80, height: 80)

            Text(displayName)
                .font(.system(size: PantopusTextStyle.h3.size, weight: .bold))
                .tracking(-0.25)
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)

            if handle != nil || locality != nil {
                Text(handleAndLocality)
                    .font(.system(size: PantopusTextStyle.caption.size, weight: .regular))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(1)
            }

            if !identityBadges.isEmpty {
                HStack(spacing: Spacing.s2) {
                    ForEach(identityBadges) { badge in
                        if let onBadgeTap {
                            Button { onBadgeTap(badge.pillar) } label: {
                                StatusChip(
                                    badge.label,
                                    variant: badge.chipVariant,
                                    icon: badge.leadingIcon
                                )
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(
                                "\(badge.label) identity, \(badge.state == .verified ? "verified" : "not verified")"
                            )
                        } else {
                            StatusChip(
                                badge.label,
                                variant: badge.chipVariant,
                                icon: badge.leadingIcon
                            )
                            .accessibilityElement()
                            .accessibilityLabel(
                                "\(badge.label) identity, \(badge.state == .verified ? "verified" : "not verified")"
                            )
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s4)
        .padding(.bottom, Spacing.s5)
        .accessibilityElement(children: .contain)
    }

    private var handleAndLocality: String {
        switch (handle, locality) {
        case let (h?, l?) where !h.isEmpty && !l.isEmpty: "@\(h) · \(l)"
        case let (h?, _) where !h.isEmpty: "@\(h)"
        case let (_, l?) where !l.isEmpty: l
        default: ""
        }
    }
}

#Preview("Verified + all badges") {
    ProfileHeader(
        displayName: "Alex Rivera",
        handle: "alex",
        locality: "Cambridge, MA",
        avatarURL: nil,
        isVerified: true,
        identityBadges: [
            IdentityPillarBadge(pillar: .personal, state: .verified),
            IdentityPillarBadge(pillar: .home, state: .verified),
            IdentityPillarBadge(pillar: .business, state: .unverified)
        ]
    )
    .padding(.vertical, Spacing.s4)
    .background(Theme.Color.appBg)
}
