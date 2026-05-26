//
//  PublicProfileChrome.swift
//  Pantopus
//
//  P6.5 — Persona vs Local chrome components for the Public Profile
//  screen. Lives in the Profile feature folder because the components
//  are screen-private (no other surface reuses them today). Includes:
//
//  - `PublicProfileBanner`: flat 100pt banner tinted per kind.
//  - `PublicProfileBroadcastCard`: persona broadcast with the visibility
//    chip and the optional "Subscribe to unlock" paywall overlay.
//  - `PublicProfileLocalPostCard`: Pulse-style neighborhood post with
//    the optional intent chip.
//
//  All colors come from the token set — never raw hex.
//

import SwiftUI

// MARK: - Banner

/// Flat, kind-tinted banner sitting above the profile header. The design
/// references a sky/green gradient for visual richness, but the spec
/// pins us to flat surfaces on mobile (the only gradient in the app is
/// the marketing landing hero). Persona uses `primary50` with a
/// `primary600` trim; Local uses `homeBg` with a `home` trim.
@MainActor
struct PublicProfileBanner: View {
    let kind: PublicProfileKind

    var body: some View {
        VStack(spacing: Spacing.s0) {
            Rectangle()
                .fill(fillColor)
                .frame(height: Spacing.s16)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(trimColor).frame(height: 2)
                }
        }
        .accessibilityIdentifier(
            kind == .persona
                ? "publicProfilePersonaBanner"
                : "publicProfileLocalBanner"
        )
        .accessibilityHidden(true)
    }

    private var fillColor: Color {
        switch kind {
        case .persona: Theme.Color.primary50
        case .local: Theme.Color.homeBg
        }
    }

    private var trimColor: Color {
        switch kind {
        case .persona: Theme.Color.primary600
        case .local: Theme.Color.home
        }
    }
}

// MARK: - Persona broadcast card

/// Persona-broadcast card surface with visibility chip + locked-paywall
/// overlay. The card mirrors the design's `BroadcastCard`: meta row
/// (timeAgo + visibility), body, optional reactions/replies footer. When
/// `post.isLocked` is true, the body is suppressed visually and an
/// overlay invites the visitor to subscribe to the gating tier.
@MainActor
struct PublicProfileBroadcastCard: View {
    let post: PublicProfilePost
    let onUnlock: @MainActor () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            metaRow
            if post.isLocked {
                lockedContent
            } else {
                Text(post.body)
                    .font(.system(size: PantopusTextStyle.small.size))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(3)
                    .multilineTextAlignment(.leading)
                reactionsRow
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("publicProfileBroadcastCard_\(post.id)")
        .accessibilityLabel(accessibilitySummary)
    }

    private var metaRow: some View {
        HStack(spacing: Spacing.s2) {
            Text(post.timeAgo)
                .font(.system(size: PantopusTextStyle.caption.size))
                .foregroundStyle(Theme.Color.appTextSecondary)
            Text("·")
                .font(.system(size: PantopusTextStyle.caption.size))
                .foregroundStyle(Theme.Color.appTextMuted)
            if let visibility = post.visibility {
                VisibilityChip(visibility: visibility)
            }
            Spacer(minLength: Spacing.s0)
        }
    }

    private var reactionsRow: some View {
        HStack(spacing: Spacing.s3) {
            HStack(spacing: Spacing.s1) {
                Icon(.heart, size: 13, color: Theme.Color.appTextSecondary)
                Text("\(post.reactions)")
                    .font(.system(size: PantopusTextStyle.caption.size))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            HStack(spacing: Spacing.s1) {
                Icon(.messageCircle, size: 13, color: Theme.Color.appTextSecondary)
                Text("\(post.replies)")
                    .font(.system(size: PantopusTextStyle.caption.size))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s0)
            Icon(.bookmark, size: 13, color: Theme.Color.appTextSecondary)
        }
        .padding(.top, Spacing.s1)
    }

    /// Locked broadcast body — paywall surface that swaps out the body
    /// text + reactions row for a tinted "Subscribe to unlock" CTA.
    private var lockedContent: some View {
        VStack(spacing: Spacing.s2) {
            ZStack {
                Circle()
                    .fill(Theme.Color.warningBg)
                    .frame(width: 40, height: 40)
                Icon(.lock, size: 18, color: Theme.Color.warning)
            }
            Text("Subscribe to \(unlockTierLabel) to unlock")
                .font(.system(size: PantopusTextStyle.small.size, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .multilineTextAlignment(.center)
            Button(action: onUnlock) {
                Text("Subscribe to unlock")
                    .font(.system(size: PantopusTextStyle.caption.size, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s4)
                    .frame(minHeight: 32)
                    .background(Theme.Color.warning)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("publicProfileBroadcastUnlock_\(post.id)")
            .accessibilityLabel("Subscribe to unlock \(unlockTierLabel) broadcast")
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.s3)
        .padding(.horizontal, Spacing.s4)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private var unlockTierLabel: String {
        switch post.visibility ?? .bronze {
        case .free: "Free"
        case .bronze: "Bronze"
        case .silver: "Silver"
        case .gold: "Gold"
        }
    }

    private var accessibilitySummary: String {
        let visibilityPart = post.visibility.map { " (\($0.rawValue))" } ?? ""
        if post.isLocked {
            return "Locked broadcast\(visibilityPart). \(post.timeAgo). Subscribe to unlock."
        }
        return "Broadcast\(visibilityPart). \(post.body). \(post.timeAgo). \(post.reactions) reactions, \(post.replies) replies."
    }
}

@MainActor
private struct VisibilityChip: View {
    let visibility: PublicProfilePost.Visibility

    var body: some View {
        HStack(spacing: 3) {
            Icon(icon, size: 10, color: foreground)
            Text(label)
                .font(.system(size: 10, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(foreground)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 2)
        .background(background)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
    }

    private var label: String {
        switch visibility {
        case .free: "FREE"
        case .bronze: "BRONZE+"
        case .silver: "SILVER+"
        case .gold: "GOLD+"
        }
    }

    private var icon: PantopusIcon {
        switch visibility {
        case .free: .globe
        default: .lock
        }
    }

    private var foreground: Color {
        switch visibility {
        case .free: Theme.Color.success
        default: Theme.Color.warning
        }
    }

    private var background: Color {
        switch visibility {
        case .free: Theme.Color.successBg
        default: Theme.Color.warningBg
        }
    }
}

// MARK: - Local Pulse-style post card

/// Local-post card surface — Pulse styling without tier chips or
/// paywall. Renders meta row (timeAgo · locality · intent chip), body,
/// reactions/replies/share row.
@MainActor
struct PublicProfileLocalPostCard: View {
    let post: PublicProfilePost

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            metaRow
            Text(post.body)
                .font(.system(size: PantopusTextStyle.small.size))
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(3)
                .multilineTextAlignment(.leading)
            reactionsRow
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("publicProfileLocalPostCard_\(post.id)")
        .accessibilityLabel(accessibilitySummary)
    }

    private var metaRow: some View {
        HStack(spacing: Spacing.s2) {
            Text(post.timeAgo)
                .font(.system(size: PantopusTextStyle.caption.size))
                .foregroundStyle(Theme.Color.appTextSecondary)
            if let locality = post.locality, !locality.isEmpty {
                Text("·")
                    .font(.system(size: PantopusTextStyle.caption.size))
                    .foregroundStyle(Theme.Color.appTextMuted)
                HStack(spacing: 3) {
                    Icon(.mapPin, size: 11, color: Theme.Color.appTextSecondary)
                    Text(locality)
                        .font(.system(size: PantopusTextStyle.caption.size))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            Spacer(minLength: Spacing.s0)
            if let intent = post.intent {
                IntentChip(intent: intent)
            }
        }
    }

    private var reactionsRow: some View {
        HStack(spacing: Spacing.s3) {
            HStack(spacing: Spacing.s1) {
                Icon(.lightbulb, size: 13, color: Theme.Color.appTextSecondary)
                Text("\(post.reactions)")
                    .font(.system(size: PantopusTextStyle.caption.size))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            HStack(spacing: Spacing.s1) {
                Icon(.messageCircle, size: 13, color: Theme.Color.appTextSecondary)
                Text("\(post.replies)")
                    .font(.system(size: PantopusTextStyle.caption.size))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s0)
            Icon(.share, size: 13, color: Theme.Color.appTextSecondary)
        }
        .padding(.top, Spacing.s1)
    }

    private var accessibilitySummary: String {
        let intentPart = post.intent.map { " \($0.rawValue) post." } ?? " Post."
        let localityPart = post.locality.map { " in \($0)." } ?? ""
        return "\(intentPart)\(localityPart) \(post.body). \(post.timeAgo). \(post.reactions) reactions, \(post.replies) replies."
    }
}

@MainActor
private struct IntentChip: View {
    let intent: PublicProfilePost.Intent

    var body: some View {
        HStack(spacing: 3) {
            Icon(icon, size: 10, color: foreground)
            Text(label)
                .font(.system(size: 10, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(foreground)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 2)
        .background(background)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
    }

    private var label: String {
        switch intent {
        case .offer: "OFFER"
        case .alert: "ALERT"
        case .event: "EVENT"
        case .ask: "ASK"
        }
    }

    private var icon: PantopusIcon {
        switch intent {
        case .offer: .hand
        case .alert: .alertTriangle
        case .event: .calendar
        case .ask: .helpCircle
        }
    }

    private var foreground: Color {
        switch intent {
        case .offer: Theme.Color.home
        case .alert: Theme.Color.warning
        case .event: Theme.Color.personal
        case .ask: Theme.Color.primary700
        }
    }

    private var background: Color {
        switch intent {
        case .offer: Theme.Color.homeBg
        case .alert: Theme.Color.warningBg
        case .event: Theme.Color.personalBg
        case .ask: Theme.Color.primary50
        }
    }
}

// MARK: - Posts feed wrapper

/// Container that renders either persona broadcasts or local posts
/// beneath the stats/tabs body. Empty state is a single-line caption so
/// it doesn't fight with the tab's own empty UI (Reviews/Gigs tabs ship
/// their own `EmptyState`).
@MainActor
struct PublicProfilePostsFeed: View {
    let kind: PublicProfileKind
    let posts: [PublicProfilePost]
    let onUnlock: @MainActor (PublicProfilePost) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Text(headerLabel)
                .font(.system(size: 11, weight: .semibold))
                .tracking(0.6)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .textCase(.uppercase)
                .accessibilityAddTraits(.isHeader)

            if posts.isEmpty {
                Text(emptyCopy)
                    .font(.system(size: PantopusTextStyle.small.size))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, Spacing.s3)
                    .accessibilityIdentifier("publicProfilePostsEmpty")
            } else {
                ForEach(posts) { post in
                    switch kind {
                    case .persona:
                        PublicProfileBroadcastCard(post: post) {
                            onUnlock(post)
                        }
                    case .local:
                        PublicProfileLocalPostCard(post: post)
                    }
                }
            }
        }
        .padding(.horizontal, Spacing.s4)
    }

    private var headerLabel: String {
        kind == .persona ? "Recent broadcasts" : "Recent posts"
    }

    private var emptyCopy: String {
        kind == .persona
            ? "No broadcasts yet — check back soon."
            : "No posts from this neighbor yet."
    }
}
