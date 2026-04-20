//
//  HubSections.swift
//  Pantopus
//
//  The 10 section composables that make up the hub screen. Kept in a
//  single file — they're tightly coupled to `HubView`'s layout and small
//  enough that per-file isolation would add noise without benefit.
//

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
                identity: .personal,
                ringProgress: content.ringProgress
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(content.greeting)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Text(content.name)
                    .pantopusTextStyle(.h3)
                    .foregroundStyle(Theme.Color.appText)
            }
            Spacer()
            ZStack(alignment: .topTrailing) {
                Button(action: onBellTap) {
                    Icon(.bell, size: 22, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Notifications")
                if content.unreadCount > 0 {
                    Circle()
                        .fill(Theme.Color.error)
                        .frame(width: 10, height: 10)
                        .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
                        .offset(x: -8, y: 8)
                }
            }
            Button(action: onMenuTap) {
                Icon(.menu, size: 22, color: Theme.Color.appText)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Menu")
        }
        .padding(.horizontal, Spacing.s4)
        .frame(height: 56)
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
                        isActive: chip.active,
                        action: { onTap(chip.kind) }
                    )
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
        HStack(alignment: .top, spacing: Spacing.s3) {
            Icon(.shieldCheck, size: 22, color: Theme.Color.warning)
            VStack(alignment: .leading, spacing: Spacing.s1) {
                Text(content.title)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Text("Unlock trusted neighborhood features by verifying your address.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Button(action: onStart) {
                    Text(content.ctaTitle)
                        .pantopusTextStyle(.small)
                        .foregroundStyle(Theme.Color.warning)
                }
                .frame(minHeight: 44)
                .accessibilityLabel("\(content.ctaTitle) \(content.title)")
            }
            Spacer(minLength: 0)
            Button(action: onDismiss) {
                Icon(.x, size: 18, color: Theme.Color.warning)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Dismiss banner")
        }
        .padding(Spacing.s3)
        .background(Theme.Color.warningBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.warningLight, lineWidth: 1)
        )
        .padding(.horizontal, Spacing.s4)
    }
}

// MARK: - First-run hero

struct HubFirstRunHero: View {
    let content: HubState.FirstRunContent
    let onStart: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Text("\(content.greeting), \(content.name)")
                .pantopusTextStyle(.h2)
                .foregroundStyle(Theme.Color.appText)
            Text("Verify your home")
                .pantopusTextStyle(.h1)
                .foregroundStyle(Theme.Color.appText)
            Text("Claim your address to unlock your neighborhood pulse, mailbox, and more.")
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appTextSecondary)
            VStack(spacing: Spacing.s2) {
                ForEach(content.steps) { step in
                    HStack(spacing: Spacing.s2) {
                        Icon(
                            step.done ? .checkCircle : .circle,
                            size: 18,
                            color: step.done ? Theme.Color.success : Theme.Color.appTextMuted
                        )
                        Text(step.title)
                            .pantopusTextStyle(.body)
                            .foregroundStyle(step.done ? Theme.Color.appTextSecondary : Theme.Color.appText)
                            .strikethrough(step.done, color: Theme.Color.appTextSecondary)
                    }
                }
            }
            .padding(.top, Spacing.s2)
            PrimaryButton(title: "Verify my home") { await MainActor.run { onStart() } }
        }
        .padding(Spacing.s5)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
        .pantopusShadow(.md)
        .padding(.horizontal, Spacing.s4)
    }
}

// MARK: - Today

struct HubTodayCard: View {
    let summary: TodaySummary

    var body: some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.lg)
                    .fill(LinearGradient(
                        colors: [Theme.Color.warning, Theme.Color.error],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ))
                Icon(.sun, size: 28, color: Theme.Color.appTextInverse)
            }
            .frame(width: 64, height: 64)

            VStack(alignment: .leading, spacing: Spacing.s1) {
                Text("Today")
                    .pantopusTextStyle(.overline)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                if let temp = summary.temperatureFahrenheit {
                    Text("\(temp)°")
                        .pantopusTextStyle(.h2)
                        .foregroundStyle(Theme.Color.appText)
                }
                HStack(spacing: Spacing.s2) {
                    if let conditions = summary.conditions {
                        Text(conditions)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    if let aqi = summary.aqiLabel {
                        StatusChip("AQI \(aqi)", variant: .info)
                    }
                    if let commute = summary.commuteLabel {
                        StatusChip(commute, variant: .neutral)
                    }
                }
            }
            Spacer()
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .pantopusShadow(.sm)
        .padding(.horizontal, Spacing.s4)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Pillar grid

struct HubPillarGrid: View {
    let tiles: [PillarTile]
    let onTap: (PillarTile.Pillar) -> Void

    private let columns = [GridItem(.flexible(), spacing: Spacing.s3), GridItem(.flexible(), spacing: Spacing.s3)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: Spacing.s3) {
            ForEach(tiles) { tile in
                Button { onTap(tile.pillar) } label: {
                    VStack(alignment: .leading, spacing: Spacing.s2) {
                        HStack {
                            ZStack {
                                RoundedRectangle(cornerRadius: Radii.md)
                                    .fill(tile.tint.backgroundColor)
                                Icon(tile.icon, size: 20, color: tile.tint.color)
                            }
                            .frame(width: 40, height: 40)
                            Spacer()
                            if let chip = tile.chip {
                                StatusChip(
                                    chip,
                                    variant: tile.chipSetupState ? .warning : .info
                                )
                            }
                        }
                        Text(tile.label)
                            .pantopusTextStyle(.body)
                            .foregroundStyle(Theme.Color.appText)
                    }
                    .padding(Spacing.s3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.Color.appSurface)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
                    .pantopusShadow(.sm)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(tile.label)\(tile.chip.map { ", \($0)" } ?? "")")
            }
        }
        .padding(.horizontal, Spacing.s4)
    }
}

// MARK: - Discovery rail

struct HubDiscoveryRail: View {
    let items: [DiscoveryCardContent]
    let onTap: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            SectionHeader("Discover nearby")
                .padding(.horizontal, Spacing.s4)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.s3) {
                    ForEach(items) { item in
                        Button { onTap(item.id) } label: {
                            VStack(alignment: .leading, spacing: Spacing.s2) {
                                AvatarWithIdentityRing(
                                    name: item.avatarInitials,
                                    identity: .personal,
                                    ringProgress: 1,
                                    size: 48
                                )
                                Text(item.title)
                                    .pantopusTextStyle(.body)
                                    .foregroundStyle(Theme.Color.appText)
                                    .lineLimit(2)
                                Text(item.meta)
                                    .pantopusTextStyle(.caption)
                                    .foregroundStyle(Theme.Color.appTextSecondary)
                                Spacer(minLength: 0)
                                StatusChip(item.category, variant: .neutral)
                            }
                            .padding(Spacing.s3)
                            .frame(width: 140, height: 180, alignment: .topLeading)
                            .background(Theme.Color.appSurface)
                            .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
                            .pantopusShadow(.sm)
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

// MARK: - Jump back in

struct HubJumpBackIn: View {
    let items: [JumpBackItem]
    let onTap: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            SectionHeader("Jump back in")
                .padding(.horizontal, Spacing.s4)
            HStack(spacing: Spacing.s3) {
                ForEach(items) { item in
                    Button { onTap(item.id) } label: {
                        VStack(alignment: .leading, spacing: Spacing.s2) {
                            ZStack {
                                RoundedRectangle(cornerRadius: Radii.md)
                                    .fill(Theme.Color.primary100)
                                Icon(item.icon, size: 22, color: Theme.Color.primary600)
                            }
                            .frame(width: 40, height: 40)
                            Text(item.title)
                                .pantopusTextStyle(.body)
                                .foregroundStyle(Theme.Color.appText)
                                .lineLimit(2)
                        }
                        .padding(Spacing.s3)
                        .frame(maxWidth: .infinity, minHeight: 124, alignment: .topLeading)
                        .background(Theme.Color.appSurface)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
                        .pantopusShadow(.sm)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(item.title)
                }
            }
            .padding(.horizontal, Spacing.s4)
        }
    }
}

// MARK: - Recent activity

struct HubRecentActivity: View {
    let entries: [ActivityEntry]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            SectionHeader("Recent activity")
                .padding(.horizontal, Spacing.s4)
            VStack(spacing: 0) {
                ForEach(entries) { entry in
                    HStack(spacing: Spacing.s3) {
                        ZStack {
                            RoundedRectangle(cornerRadius: Radii.md)
                                .fill(entry.tint.backgroundColor)
                            Icon(entry.icon, size: 18, color: entry.tint.color)
                        }
                        .frame(width: 36, height: 36)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(entry.title)
                                .pantopusTextStyle(.body)
                                .foregroundStyle(Theme.Color.appText)
                            Text(entry.timeAgo)
                                .pantopusTextStyle(.caption)
                                .foregroundStyle(Theme.Color.appTextSecondary)
                        }
                        Spacer()
                    }
                    .padding(.vertical, Spacing.s2)
                    .padding(.horizontal, Spacing.s3)
                    .accessibilityElement(children: .combine)
                }
            }
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
            .padding(.horizontal, Spacing.s4)
        }
    }
}

// MARK: - Floating progress bar (first-run)

struct HubFloatingProgress: View {
    let fraction: Double

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Text("Profile \(Int(fraction * 100))%")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextInverse)
            SegmentedProgressBar(currentStep: Int((fraction * 4).rounded()), totalSteps: 4)
                .frame(width: 140)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appText.opacity(0.9))
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
        .pantopusShadow(.md)
    }
}

// MARK: - Skeleton

struct HubSkeleton: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            HStack(spacing: Spacing.s3) {
                Shimmer(width: 40, height: 40, cornerRadius: 20)
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Shimmer(width: 80, height: 10)
                    Shimmer(width: 140, height: 14)
                }
                Spacer()
                Shimmer(width: 44, height: 44, cornerRadius: Radii.md)
                Shimmer(width: 44, height: 44, cornerRadius: Radii.md)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s4)
            HStack(spacing: Spacing.s2) {
                ForEach(0..<4, id: \.self) { _ in
                    Shimmer(width: 100, height: 36, cornerRadius: Radii.pill)
                }
            }
            .padding(.horizontal, Spacing.s4)
            Shimmer(height: 96, cornerRadius: Radii.lg)
                .padding(.horizontal, Spacing.s4)
            VStack(spacing: Spacing.s3) {
                ForEach(0..<2, id: \.self) { _ in
                    HStack(spacing: Spacing.s3) {
                        Shimmer(height: 92, cornerRadius: Radii.lg)
                        Shimmer(height: 92, cornerRadius: Radii.lg)
                    }
                }
            }
            .padding(.horizontal, Spacing.s4)
            Shimmer(height: 180, cornerRadius: Radii.lg)
                .padding(.horizontal, Spacing.s4)
        }
    }
}
