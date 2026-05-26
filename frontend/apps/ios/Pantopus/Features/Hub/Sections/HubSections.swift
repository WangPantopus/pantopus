//
//  HubSections.swift
//  Pantopus
//
//  Section composables that make up the hub. T6.2a re-skins each section
//  against `hub-frames.jsx` (per-pixel diff captured in the PR
//  changelog). No structural rebuild — additive data on the state
//  models, surgical visual updates here.
//

// swiftlint:disable file_length

import SwiftUI

// MARK: - Top bar

struct HubTopBar: View {
    let content: TopBarContent
    let onBellTap: () -> Void
    let onMenuTap: () -> Void

    var body: some View {
        HStack(spacing: Spacing.s3) {
            AvatarWithIdentityRing(
                name: content.name,
                identity: content.identity,
                ringProgress: content.ringProgress
            )
            VStack(alignment: .leading, spacing: 1) {
                Text(content.greeting)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Text(content.name)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
            }
            Spacer()
            // Bell + menu — design uses 36pt buttons with 20pt icons.
            ZStack(alignment: .topTrailing) {
                Button(action: onBellTap) {
                    Icon(.bell, size: 20, color: Theme.Color.appText)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Notifications")
                .accessibilityIdentifier("hubBellButton")
                if content.unreadCount > 0 {
                    Circle()
                        .fill(Theme.Color.error)
                        .frame(width: 8, height: 8)
                        .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
                        .offset(x: -8, y: 8)
                }
            }
            Button(action: onMenuTap) {
                Icon(.menu, size: 20, color: Theme.Color.appText)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Menu")
            .accessibilityIdentifier("hubMenuButton")
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }
}

// MARK: - Action strip

struct HubActionStrip: View {
    let chips: [ActionChipContent]
    let onTap: (ActionChipContent.Kind) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.s2) {
                ForEach(chips) { chip in
                    ActionChip(
                        icon: chip.icon,
                        label: chip.label,
                        isActive: chip.active
                    ) { onTap(chip.kind) }
                }
            }
            .padding(.horizontal, Spacing.s4)
        }
        .padding(.vertical, Spacing.s2)
    }
}

// MARK: - Setup banner

struct HubSetupBanner: View {
    let content: SetupBannerContent
    let onStart: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.s2) {
            // Leading 32pt icon disk — design uses warningLight bg + warning fg.
            ZStack {
                RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                    .fill(Theme.Color.warningLight)
                Icon(.shieldCheck, size: 16, color: Theme.Color.warning)
            }
            .frame(width: 32, height: 32)

            VStack(alignment: .leading, spacing: 1) {
                Text(content.title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Text("Unlock gigs + mail receiving.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s0)

            Button(action: onStart) {
                Text(content.ctaTitle)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s3)
                    .padding(.vertical, Spacing.s1)
                    .background(Theme.Color.warning)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(content.ctaTitle) \(content.title)")
            .accessibilityIdentifier("hubSetupBannerStartButton")

            Button(action: onDismiss) {
                Icon(.x, size: 14, color: Theme.Color.appTextSecondary)
                    .frame(width: 24, height: 24)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Dismiss banner")
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s2)
        // The design's H.warningBgSoft maps to our warningBg, and
        // H.warningBg maps to our warningLight (names differ; values match).
        .background(Theme.Color.warningBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.warningLight, lineWidth: 1)
        )
        .padding(.horizontal, Spacing.s4)
        .accessibilityIdentifier("hubSetupBanner")
    }
}

// MARK: - First-run hero (VerifyHero)

struct HubFirstRunHero: View {
    let content: HubState.FirstRunContent
    let onStart: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            // Decorative dashed envelope behind the icon disk.
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .fill(Color.white.opacity(0.10))
                .frame(width: 130, height: 130)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.25), style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                )
                .rotationEffect(.degrees(14))
                .offset(x: 20, y: -20)
                .allowsHitTesting(false)

            // 54pt mail-icon disk (top-right).
            ZStack {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(Color.white.opacity(0.18))
                Icon(.mailbox, size: 26, color: Color.white)
            }
            .frame(width: 54, height: 54)
            .padding(.top, Spacing.s2)
            .padding(.trailing, Spacing.s3)

            VStack(alignment: .leading, spacing: Spacing.s2) {
                HStack(spacing: Spacing.s1) {
                    Icon(.sparkles, size: 10, color: Color.white)
                    Text("GET STARTED")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(Color.white)
                        .tracking(1.0)
                }
                .padding(.horizontal, Spacing.s2)
                .padding(.vertical, 3)
                .background(Color.white.opacity(0.18))
                .clipShape(Capsule())

                Text("Verify your home to unlock Pantopus")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Color.white)
                    .frame(maxWidth: 220, alignment: .leading)
                    .accessibilityAddTraits(.isHeader)

                Text("Takes 4 minutes. Gets you mail, gigs, and neighbor features.")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.white.opacity(0.82))
                    .frame(maxWidth: 240, alignment: .leading)

                Button(action: onStart) {
                    HStack(spacing: Spacing.s1) {
                        Text("Start verification")
                            .font(.system(size: 13, weight: .bold))
                        Icon(.arrowRight, size: 14, color: Theme.Color.primary700)
                    }
                    .foregroundStyle(Theme.Color.primary700)
                    .padding(.horizontal, Spacing.s4)
                    .padding(.vertical, Spacing.s2)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("hubFirstRunStartButton")
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s4)
            .padding(.bottom, Spacing.s4)
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .background(
            LinearGradient(
                colors: [Theme.Color.primary600, Theme.Color.primary700, Theme.Color.primary900],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .padding(.horizontal, Spacing.s4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Verify your home to unlock Pantopus. Takes 4 minutes.")
    }
}

// MARK: - Today

struct HubTodayCard: View {
    let summary: TodaySummary
    var onTap: (() -> Void)?

    var body: some View {
        Button { onTap?() } label: {
            HStack(spacing: Spacing.s3) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .fill(LinearGradient(
                            colors: [Theme.Color.primary100, Theme.Color.primary600],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ))
                    Icon(.sun, size: 20, color: Color.white)
                }
                .frame(width: 40, height: 40)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        if let temp = summary.temperatureFahrenheit {
                            Text("\(temp)°F")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundStyle(Theme.Color.appText)
                        }
                        if let conditions = summary.conditions {
                            Text(conditions)
                                .pantopusTextStyle(.caption)
                                .foregroundStyle(Theme.Color.appTextSecondary)
                        }
                    }
                    HStack(spacing: 6) {
                        if let aqi = summary.aqiLabel {
                            HStack(spacing: Spacing.s1) {
                                Text("AQI")
                                    .pantopusTextStyle(.caption)
                                    .foregroundStyle(Theme.Color.appTextSecondary)
                                Text(aqi)
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(Theme.Color.success)
                            }
                            if summary.commuteLabel != nil {
                                Text("·").pantopusTextStyle(.caption)
                                    .foregroundStyle(Theme.Color.appTextSecondary)
                            }
                        }
                        if let commute = summary.commuteLabel {
                            Text(commute)
                                .pantopusTextStyle(.caption)
                                .foregroundStyle(Theme.Color.appTextSecondary)
                        }
                    }
                }
                Spacer(minLength: Spacing.s0)
                Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s3)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .pantopusShadow(.sm)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, Spacing.s4)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("hubTodayCard")
    }
}

// MARK: - Pillar grid

struct HubPillarGrid: View {
    let tiles: [PillarTile]
    let onTap: (PillarTile.Pillar) -> Void

    private let columns = [
        GridItem(.flexible(), spacing: Spacing.s2),
        GridItem(.flexible(), spacing: Spacing.s2)
    ]

    var body: some View {
        LazyVGrid(columns: columns, spacing: Spacing.s2) {
            ForEach(tiles) { tile in
                Button { onTap(tile.pillar) } label: {
                    PillarTileBody(tile: tile)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(tile.label)\(tile.chip.map { ", \($0)" } ?? "")")
                .accessibilityIdentifier("hub.pillar.\(tile.pillar.rawValue)")
            }
        }
        .padding(.horizontal, Spacing.s4)
    }
}

private struct PillarTileBody: View {
    let tile: PillarTile

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(alignment: .center) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .fill(tile.chipSetupState
                            ? Theme.Color.appSurfaceSunken
                            : tile.tint.backgroundColor)
                    Icon(
                        tile.icon,
                        size: 17,
                        color: tile.chipSetupState
                            ? Theme.Color.appTextMuted
                            : tile.tint.color
                    )
                }
                .frame(width: 32, height: 32)
                Spacer()
                if let chip = tile.chip {
                    PillarChip(label: chip, setupState: tile.chipSetupState, tint: tile.tint)
                }
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(tile.label)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                if let caption = tile.caption {
                    Text(caption)
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(1)
                }
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, minHeight: 94, alignment: .topLeading)
        .background(
            tile.chipSetupState ? Theme.Color.appSurfaceRaised : Theme.Color.appSurface
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .opacity(tile.chipSetupState ? 0.85 : 1)
        .pantopusShadow(.sm)
    }
}

private struct PillarChip: View {
    let label: String
    let setupState: Bool
    let tint: IdentityPillar

    var body: some View {
        Text(setupState ? label.uppercased() : label)
            .font(.system(size: 10, weight: .bold))
            .tracking(setupState ? 0.5 : 0.2)
            .foregroundStyle(setupState ? Theme.Color.appTextSecondary : tint.color)
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, 2)
            .background(setupState ? Theme.Color.appSurfaceSunken : tint.backgroundColor)
            .clipShape(Capsule())
    }
}

// MARK: - Discovery rail

struct HubDiscoveryRail: View {
    let items: [DiscoveryCardContent]
    let onTap: (DiscoveryCardContent) -> Void
    /// Optional `See all` action — pushes to the typed Discover hub
    /// screen (T5.4.1).
    var onSeeAll: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                SectionHeader("Discover nearby")
                Spacer()
                if let onSeeAll {
                    Button(action: onSeeAll) {
                        HStack(spacing: 2) {
                            Text("See all")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(Theme.Color.primary600)
                            Icon(.chevronRight, size: 13, color: Theme.Color.primary600)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("See all discovery")
                    .accessibilityIdentifier("hubDiscoveryRail.seeAll")
                }
            }
            .padding(.horizontal, Spacing.s4)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.s2) {
                    ForEach(items) { item in
                        Button { onTap(item) } label: {
                            DiscoveryCard(item: item)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(item.title), \(item.meta)")
                    }
                }
                .padding(.horizontal, Spacing.s4)
            }
        }
    }
}

private struct DiscoveryCard: View {
    let item: DiscoveryCardContent

    var body: some View {
        VStack(spacing: Spacing.s0) {
            ZStack {
                LinearGradient(
                    colors: [item.tint.backgroundColor, item.tint.color],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .clipped()
                switch item.kind {
                case .person:
                    ZStack {
                        Circle()
                            .fill(Color.white)
                            .overlay(Circle().stroke(Color.white, lineWidth: 3))
                        Text(item.avatarInitials)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(item.tint.color)
                    }
                    .frame(width: 54, height: 54)
                case .business:
                    Icon(.shoppingBag, size: 32, color: Color.white.opacity(0.85))
                case .gig, .post, .unknown:
                    Icon(.shoppingBag, size: 32, color: Color.white.opacity(0.85))
                }
            }
            .frame(height: 80)

            VStack(alignment: .leading, spacing: Spacing.s1) {
                Text(item.title)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                Text(item.meta)
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(2)
                Spacer(minLength: Spacing.s0)
                Text(item.category.uppercased())
                    .font(.system(size: 10, weight: .bold))
                    .tracking(0.3)
                    .foregroundStyle(item.tint.color)
                    .padding(.horizontal, Spacing.s2)
                    .padding(.vertical, 2)
                    .background(item.tint.backgroundColor)
                    .clipShape(Capsule())
            }
            .padding(Spacing.s2)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(width: 140, height: 180)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .pantopusShadow(.sm)
    }
}

// MARK: - Jump back in

struct HubJumpBackIn: View {
    let items: [JumpBackItem]
    let onTap: (JumpBackItem) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            SectionHeader("Jump back in")
                .padding(.horizontal, Spacing.s4)
            HStack(spacing: Spacing.s2) {
                ForEach(items) { item in
                    Button { onTap(item) } label: {
                        JumpBackCard(item: item)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(item.kicker), \(item.title)")
                }
            }
            .padding(.horizontal, Spacing.s4)
        }
    }
}

private struct JumpBackCard: View {
    let item: JumpBackItem

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                    .fill(item.tint.backgroundColor)
                Icon(item.icon, size: 17, color: item.tint.color)
            }
            .frame(width: 34, height: 34)

            VStack(alignment: .leading, spacing: 2) {
                Text(item.kicker.uppercased())
                    .font(.system(size: 10, weight: .bold))
                    .tracking(0.5)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Text(item.title)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }

            Spacer(minLength: Spacing.s0)

            if let fraction = item.progressFraction {
                VStack(alignment: .leading, spacing: 5) {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Theme.Color.appSurfaceSunken)
                            Capsule()
                                .fill(item.tint.color)
                                .frame(width: max(0, geo.size.width * CGFloat(fraction)))
                        }
                    }
                    .frame(height: 4)
                    if let label = item.progressLabel {
                        Text(label)
                            .font(.system(size: 10))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, minHeight: 124, alignment: .topLeading)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .pantopusShadow(.sm)
    }
}

// MARK: - Recent activity

struct HubRecentActivity: View {
    let entries: [ActivityEntry]
    let onSeeAll: @MainActor () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                SectionHeader("Recent activity")
                Spacer()
                Button(action: onSeeAll) {
                    HStack(spacing: 2) {
                        Text("See all")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Theme.Color.primary600)
                        Icon(.chevronRight, size: 13, color: Theme.Color.primary600)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("See all activity")
                .accessibilityIdentifier("hubRecentActivity.seeAll")
            }
            .padding(.horizontal, Spacing.s4)
            VStack(spacing: Spacing.s0) {
                ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                    HStack(spacing: Spacing.s2) {
                        ZStack {
                            RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                                .fill(entry.tint.backgroundColor)
                            Icon(entry.icon, size: 15, color: entry.tint.color)
                        }
                        .frame(width: 30, height: 30)
                        Text(entry.title)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextStrong)
                            .lineLimit(2)
                        Spacer()
                        Text(entry.timeAgo)
                            .font(.system(size: 11))
                            .foregroundStyle(Theme.Color.appTextMuted)
                    }
                    .padding(.horizontal, Spacing.s3)
                    .padding(.vertical, Spacing.s2)
                    .accessibilityElement(children: .combine)
                    if index < entries.count - 1 {
                        Rectangle()
                            .fill(Theme.Color.appBorderSubtle)
                            .frame(height: 1)
                            .padding(.leading, Spacing.s3 + 30 + Spacing.s2)
                    }
                }
            }
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .pantopusShadow(.sm)
            .padding(.horizontal, Spacing.s4)
        }
    }
}

// MARK: - Floating setup-progress bar (first-run)

/// Floating white-surface progress card pinned above the tab bar during
/// first-run. Design's frame 2 — a 36pt conic ring with the percentage
/// inside, a two-line label, and a Continue CTA.
struct HubFloatingProgress: View {
    let fraction: Double
    let stepsDone: Int
    let stepsTotal: Int
    let onContinue: () -> Void

    var body: some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                Circle()
                    .fill(Theme.Color.appSurfaceSunken)
                Circle()
                    .trim(from: 0, to: max(0, min(1, fraction)))
                    .stroke(Theme.Color.primary600, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Circle()
                    .fill(Theme.Color.appSurface)
                    .frame(width: 28, height: 28)
                Text("\(Int((fraction * 100).rounded()))%")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
            }
            .frame(width: 36, height: 36)

            VStack(alignment: .leading, spacing: 2) {
                Text("Complete your setup")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text("\(stepsDone) of \(stepsTotal) steps done · \(max(0, stepsTotal - stepsDone)) left")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s0)
            Button(action: onContinue) {
                Text("Continue")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s3)
                    .padding(.vertical, Spacing.s1)
                    .background(Theme.Color.primary600)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("hubFloatingProgressContinue")
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s3)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .pantopusShadow(.md)
        .padding(.horizontal, Spacing.s4)
    }
}

// MARK: - Skeleton

struct HubSkeleton: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            HStack(spacing: Spacing.s3) {
                Shimmer(width: 40, height: 40, cornerRadius: Radii.xl2)
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Shimmer(width: 80, height: 11)
                    Shimmer(width: 160, height: 16)
                }
                Spacer()
                Shimmer(width: 36, height: 36, cornerRadius: Radii.sm)
                Shimmer(width: 36, height: 36, cornerRadius: Radii.sm)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s3)

            HStack(spacing: Spacing.s2) {
                ForEach(0..<4, id: \.self) { _ in
                    Shimmer(width: 100, height: 36, cornerRadius: Radii.md)
                }
            }
            .padding(.horizontal, Spacing.s4)

            Shimmer(height: 56, cornerRadius: Radii.md)
                .padding(.horizontal, Spacing.s4)

            Shimmer(height: 64, cornerRadius: Radii.lg)
                .padding(.horizontal, Spacing.s4)

            LazyVGrid(
                columns: [
                    GridItem(.flexible(), spacing: Spacing.s2),
                    GridItem(.flexible(), spacing: Spacing.s2)
                ],
                spacing: Spacing.s2
            ) {
                ForEach(0..<4, id: \.self) { _ in
                    Shimmer(height: 94, cornerRadius: Radii.xl)
                }
            }
            .padding(.horizontal, Spacing.s4)

            HStack {
                Shimmer(width: 110, height: 11)
                Spacer()
                Shimmer(width: 44, height: 11)
            }
            .padding(.horizontal, Spacing.s4)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.s2) {
                    ForEach(0..<3, id: \.self) { _ in
                        Shimmer(width: 140, height: 180, cornerRadius: Radii.lg)
                    }
                }
                .padding(.horizontal, Spacing.s4)
            }

            HStack {
                Shimmer(width: 110, height: 11)
                Spacer()
            }
            .padding(.horizontal, Spacing.s4)
            HStack(spacing: Spacing.s2) {
                Shimmer(height: 124, cornerRadius: Radii.lg)
                Shimmer(height: 124, cornerRadius: Radii.lg)
            }
            .padding(.horizontal, Spacing.s4)

            HStack {
                Shimmer(width: 110, height: 11)
                Spacer()
                Shimmer(width: 44, height: 11)
            }
            .padding(.horizontal, Spacing.s4)
            VStack(spacing: Spacing.s0) {
                ForEach(0..<3, id: \.self) { index in
                    HStack(spacing: Spacing.s2) {
                        Shimmer(width: 30, height: 30, cornerRadius: Radii.sm)
                        VStack(alignment: .leading, spacing: 5) {
                            Shimmer(width: 220, height: 10)
                            Shimmer(width: 120, height: 9)
                        }
                        Spacer()
                        Shimmer(width: 22, height: 9)
                    }
                    .padding(.horizontal, Spacing.s3)
                    .padding(.vertical, Spacing.s2)
                    if index < 2 {
                        Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
                    }
                }
            }
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .padding(.horizontal, Spacing.s4)

            Spacer(minLength: Spacing.s10)
        }
    }
}
