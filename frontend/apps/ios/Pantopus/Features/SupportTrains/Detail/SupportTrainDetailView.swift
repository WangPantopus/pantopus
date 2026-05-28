//
//  SupportTrainDetailView.swift
//  Pantopus
//
//  A10.9 — Participant-facing Support Train detail screen. Distinct
//  from the organizer review queue (`ReviewSignupsView`). Composes
//  the bespoke recipient + type-dates cards, the shared `SlotCalendar`
//  primitive, two row stacks, and a sticky dock that flips between a
//  single `Sign up for a slot` CTA (populated) and a split
//  `Send a card` / `Join as backup` pair (fully covered).
//

import SwiftUI

@MainActor
public struct SupportTrainDetailView: View {
    @State private var viewModel: SupportTrainDetailViewModel
    private let onBack: @MainActor () -> Void
    private let onOpenManage: (@MainActor () -> Void)?
    private let onShare: (@MainActor () -> Void)?
    private let onSignUp: (@MainActor () -> Void)?
    private let onEditSlot: (@MainActor (SlotRowContent) -> Void)?
    private let onSendCard: (@MainActor () -> Void)?
    private let onJoinAsBackup: (@MainActor () -> Void)?
    private let onMessageHost: (@MainActor () -> Void)?
    private let isOrganizer: Bool

    public init(
        viewModel: SupportTrainDetailViewModel,
        isOrganizer: Bool = false,
        onBack: @escaping @MainActor () -> Void = {},
        onOpenManage: (@MainActor () -> Void)? = nil,
        onShare: (@MainActor () -> Void)? = nil,
        onSignUp: (@MainActor () -> Void)? = nil,
        onEditSlot: (@MainActor (SlotRowContent) -> Void)? = nil,
        onSendCard: (@MainActor () -> Void)? = nil,
        onJoinAsBackup: (@MainActor () -> Void)? = nil,
        onMessageHost: (@MainActor () -> Void)? = nil
    ) {
        _viewModel = State(initialValue: viewModel)
        self.isOrganizer = isOrganizer
        self.onBack = onBack
        self.onOpenManage = onOpenManage
        self.onShare = onShare
        self.onSignUp = onSignUp
        self.onEditSlot = onEditSlot
        self.onSendCard = onSendCard
        self.onJoinAsBackup = onJoinAsBackup
        self.onMessageHost = onMessageHost
    }

    public var body: some View {
        VStack(spacing: Spacing.s0) {
            topBar
            content
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("supportTrainDetail")
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .task { await viewModel.load() }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            loadingShell
        case let .loaded(loaded):
            loadedBody(loaded)
        case let .error(message):
            errorShell(message)
        }
    }

    private func loadedBody(_ content: SupportTrainDetailContent) -> some View {
        VStack(spacing: Spacing.s0) {
            ScrollView(.vertical, showsIndicators: true) {
                LazyVStack(alignment: .leading, spacing: Spacing.s0) {
                    if let banner = content.celebrationBanner {
                        CelebrationBanner(content: banner)
                            .padding(.top, Spacing.s3)
                            .padding(.bottom, Spacing.s1)
                    }

                    overline("For")
                    RecipientCard(content: content.recipient)

                    overline("The train")
                    TypeDatesCard(content: content.typeDates)

                    overline("Slot calendar")
                    calendarCard(days: content.calendarDays)

                    ForEach(content.sections) { section in
                        overline(section.overline, action: section.actionLabel)
                        VStack(spacing: Spacing.s2) {
                            ForEach(section.rows) { row in
                                SlotRow(
                                    content: row,
                                    onSignUp: signUpAction(for: row),
                                    onEdit: editAction(for: row)
                                )
                            }
                        }
                    }

                    HostedByRow(content: content.hostedBy, onMessageHost: onMessageHost)
                        .padding(.top, Spacing.s3)

                    Spacer().frame(height: Spacing.s3)
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.bottom, Spacing.s6)
            }
            .background(Theme.Color.appBg)

            dock(content.dock)
        }
    }

    private func signUpAction(for row: SlotRowContent) -> (@MainActor () -> Void)? {
        guard row.state == .open else { return nil }
        return { onSignUp?() }
    }

    private func editAction(for row: SlotRowContent) -> (@MainActor () -> Void)? {
        guard row.mine else { return nil }
        return { onEditSlot?(row) }
    }

    private func overline(_ label: String, action: String? = nil) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.system(size: 10.5, weight: .bold))
                .textCase(.uppercase)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: Spacing.s2)
            if let action {
                Button {
                    // The "See all" actions all currently surface as the
                    // same in-screen drilldown — defer the navigation
                    // hook to a follow-up. Keep the affordance so the
                    // visual contract stays true to the design.
                } label: {
                    Text(action)
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.primary600)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(action) \(label)")
                .accessibilityIdentifier("supportTrainSeeAll-\(label)")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, Spacing.s4)
        .padding(.bottom, Spacing.s2)
    }

    private func calendarCard(days: [SlotCalendarDay]) -> some View {
        VStack {
            SlotCalendar(days: days) { _ in onSignUp?() }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .center)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .pantopusShadow(.sm)
    }

    private func dock(_ dock: SupportTrainDock) -> some View {
        VStack(spacing: Spacing.s0) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            Group {
                switch dock {
                case let .signUp(label):
                    PrimarySignUpCTA(label: label) { onSignUp?() }
                case .sendCardAndBackup:
                    SplitCoveredDock(onSendCard: onSendCard, onJoinAsBackup: onJoinAsBackup)
                }
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s3)
        }
        .background(Theme.Color.appBg)
    }

    private var loadingShell: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                Shimmer(height: 100, cornerRadius: Radii.lg).padding(.top, Spacing.s3)
                Shimmer(height: 130, cornerRadius: Radii.lg)
                Shimmer(height: 240, cornerRadius: Radii.lg)
                Shimmer(height: 64, cornerRadius: Radii.lg)
                Shimmer(height: 64, cornerRadius: Radii.lg)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.bottom, Spacing.s8)
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("supportTrainDetailLoading")
    }

    private func errorShell(_ message: String) -> some View {
        EmptyState(
            icon: .alertCircle,
            headline: "Couldn't load support train",
            subcopy: message,
            cta: EmptyState.CTA(title: "Try again") { await viewModel.refresh() }
        )
        .accessibilityIdentifier("supportTrainDetailError")
    }

    // MARK: - Top bar

    private var topBar: some View {
        HStack(spacing: Spacing.s2) {
            Button(action: onBack) {
                Icon(.chevronLeft, size: 22, color: Theme.Color.appText)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            .accessibilityIdentifier("supportTrainDetailBackButton")

            Spacer(minLength: Spacing.s0)

            Text("Support train")
                .pantopusTextStyle(.small)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)

            Spacer(minLength: Spacing.s0)

            if let onShare {
                Button(action: onShare) {
                    Icon(.share, size: 20, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Share train")
                .accessibilityIdentifier("supportTrainDetailShareButton")
            }
            Menu {
                if isOrganizer, let onOpenManage {
                    Button {
                        onOpenManage()
                    } label: {
                        Label("Manage signups", systemImage: "list.bullet.rectangle")
                    }
                }
                Button {
                    onMessageHost?()
                } label: {
                    Label("Message the host", systemImage: "message")
                }
                Button(role: .destructive) {
                    // Report sheet wiring is a follow-up — keep the
                    // affordance visible for parity with the design.
                } label: {
                    Label("Report this train", systemImage: "flag")
                }
            } label: {
                Icon(.moreHorizontal, size: 22, color: Theme.Color.appText)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("More options")
            .accessibilityIdentifier("supportTrainDetailMoreButton")
        }
        .padding(.horizontal, Spacing.s2)
        .frame(height: 48)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }
}

// MARK: - Banner

@MainActor
private struct CelebrationBanner: View {
    let content: SupportTrainDetailContent.CelebrationBanner

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.s3) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(Theme.Color.success)
                Icon(.partyPopper, size: 18, strokeWidth: 2.2, color: Theme.Color.appTextInverse)
            }
            .frame(width: 36, height: 36)
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 2) {
                Text(content.title)
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundStyle(Theme.Color.success)
                Text(content.body)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.success)
                    .opacity(0.9)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.successBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.successLight, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(content.title). \(content.body)")
        .accessibilityIdentifier("supportTrainCelebrationBanner")
    }
}

// MARK: - Hosted by

@MainActor
private struct HostedByRow: View {
    let content: HostedByFooter
    let onMessageHost: (@MainActor () -> Void)?

    var body: some View {
        Button {
            onMessageHost?()
        } label: {
            HStack(spacing: Spacing.s2) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [Theme.Color.errorLight, Theme.Color.error],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                    Text(content.organizerInitials)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(width: 24, height: 24)
                .accessibilityHidden(true)

                HStack(spacing: 4) {
                    Text("Hosted by ")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                    Text(content.organizerDisplayName)
                        .pantopusTextStyle(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.appTextStrong)
                    if let hint = content.neighborHint {
                        Text("· \(hint)")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Icon(.messageSquare, size: 14, color: Theme.Color.appTextMuted)
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Hosted by \(content.organizerDisplayName)\(content.neighborHint.map { ", \($0)" } ?? "")")
        .accessibilityIdentifier("supportTrainHostedBy")
    }
}

// MARK: - Dock CTAs

@MainActor
private struct PrimarySignUpCTA: View {
    let label: String
    let onTap: @MainActor () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Spacing.s2) {
                Icon(.calendar, size: 17, color: Theme.Color.appTextInverse)
                Text(label)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(Theme.Color.primary600)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .pantopusShadow(.primary)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityIdentifier("supportTrainSignUpCTA")
    }
}

@MainActor
private struct SplitCoveredDock: View {
    let onSendCard: (@MainActor () -> Void)?
    let onJoinAsBackup: (@MainActor () -> Void)?

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Button(
                action: { onSendCard?() },
                label: {
                    HStack(spacing: Spacing.s1) {
                        Icon(.mail, size: 14, color: Theme.Color.appText)
                        Text("Send a card")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .background(Theme.Color.appSurface)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                            .stroke(Theme.Color.appBorder, lineWidth: 1)
                    )
                }
            )
            .buttonStyle(.plain)
            .accessibilityLabel("Send a card")
            .accessibilityIdentifier("supportTrainSendCardCTA")

            Button(
                action: { onJoinAsBackup?() },
                label: {
                    HStack(spacing: Spacing.s1) {
                        Icon(.userPlus, size: 14, color: Theme.Color.appTextInverse)
                        Text("Join as backup")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Theme.Color.appTextInverse)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .background(Theme.Color.primary600)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                    .pantopusShadow(.primary)
                }
            )
            .buttonStyle(.plain)
            .accessibilityLabel("Join as backup")
            .accessibilityIdentifier("supportTrainJoinBackupCTA")
        }
    }
}

// MARK: - Previews

#Preview("Populated") {
    SupportTrainDetailView(
        viewModel: SupportTrainDetailViewModel(content: SupportTrainDetailSampleData.populated)
    )
}

#Preview("Fully covered") {
    SupportTrainDetailView(
        viewModel: SupportTrainDetailViewModel(content: SupportTrainDetailSampleData.fullyCovered)
    )
}

#Preview("Loading") {
    SupportTrainDetailView(
        viewModel: SupportTrainDetailViewModel(seedState: .loading)
    )
}

#Preview("Error") {
    SupportTrainDetailView(
        viewModel: SupportTrainDetailViewModel(seedState: .error(message: "Network unavailable."))
    )
}
