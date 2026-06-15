//
//  GroupRosterView.swift
//  Pantopus
//
//  Stream I9 — E8 Group Event Roster & Seats. Capacity header + Confirmed/
//  Pending/Waitlisted stats, seated + waitlist sections (promote-to-seat), host
//  controls (add attendee · adjust capacity), and a "Message all" FAB. Owner-
//  polymorphic accent. Loading skeleton / empty / error+retry, offline banner.
//

import SwiftUI

struct GroupRosterView: View {
    @State private var viewModel: GroupRosterViewModel
    @State private var showNudge = false
    @State private var showNoShow = false

    init(viewModel: GroupRosterViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    private var theme: SchedulingIdentityTheme { viewModel.owner.theme }

    var body: some View {
        content
            .background(Theme.Color.appBg)
            .navigationTitle("Roster")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("Message all attendees") { showNudge = true }
                        Button("Mark no-show", role: .destructive) { showNoShow = true }
                    } label: {
                        Icon(.moreVertical, size: 19, color: Theme.Color.appTextStrong)
                    }
                    .accessibilityLabel("More actions")
                }
            }
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .task { await viewModel.load() }
            .refreshable { await viewModel.refresh() }
            .sheet(isPresented: $showNudge) { nudgeSheet }
            .overlay { noShowOverlay }
            .accessibilityIdentifier("scheduling.roster")
    }

    @ViewBuilder private var content: some View {
        switch viewModel.phase {
        case .loading:
            loadingSkeleton
        case .empty:
            EmptyState(
                icon: .usersRound,
                headline: "No signups yet",
                subcopy: "Share the booking link to fill seats.",
                cta: .init(title: "Share booking link") { viewModel.openShareLink() },
                tint: theme.accentBg,
                accent: theme.accent
            )
        case let .error(message):
            ErrorState(headline: "Couldn't load the roster", message: message) {
                await viewModel.refresh()
            }
        case .ready:
            loaded
        }
    }

    // MARK: Loaded

    private var loaded: some View {
        ScrollView {
            VStack(spacing: 0) {
                CapacityHeaderCard(
                    filled: viewModel.filled,
                    total: viewModel.seatTotal,
                    waiting: viewModel.waitingCount,
                    showStats: true,
                    confirmed: viewModel.confirmedCount,
                    pending: viewModel.pendingCount,
                    accent: theme.accent
                )
                .padding(.horizontal, Spacing.s4)
                .padding(.top, Spacing.s3)

                if !viewModel.seated.isEmpty {
                    section("Seated · \(viewModel.filled)") {
                        ForEach(viewModel.seated) { person in
                            RosterRow(
                                initials: person.initials,
                                name: person.name,
                                meta: person.meta,
                                verified: true,
                                statusRaw: person.statusRaw,
                                accent: theme.accent,
                                accentBackground: theme.accentBg,
                                onKebab: { showNudge = true }
                            )
                        }
                    }
                }

                if !viewModel.waitlist.isEmpty {
                    section(waitlistOverline) {
                        ForEach(viewModel.waitlist) { person in
                            RosterRow(
                                initials: person.initials,
                                name: person.name,
                                meta: person.meta,
                                accent: theme.accent,
                                accentBackground: theme.accentBg,
                                promote: .init(isEnabled: !viewModel.isFull) {
                                    if let entryId = person.promoteEntryId {
                                        Task { await viewModel.promote(entryId: entryId) }
                                    }
                                }
                            )
                        }
                    }
                }

                hostControls

                if let actionError = viewModel.actionError {
                    ExtrasInlineError(message: actionError)
                        .padding(.horizontal, Spacing.s4)
                        .padding(.top, Spacing.s3)
                }
            }
            .padding(.bottom, 96)
        }
        .overlay(alignment: .bottomTrailing) { messageAllFAB }
    }

    private var waitlistOverline: String {
        let open = max(0, viewModel.seatTotal - viewModel.filled)
        if open > 0 {
            return "Waitlist · \(viewModel.waitingCount) · \(open) seat\(open == 1 ? "" : "s") open"
        }
        return "Waitlist · \(viewModel.waitingCount)"
    }

    private func section<Rows: View>(_ overline: String, @ViewBuilder rows: () -> Rows) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s2 + 1) {
            ExtrasOverline(text: overline)
                .padding(.horizontal, Spacing.s4 + 2)
            VStack(spacing: Spacing.s2 + 1) { rows() }
                .padding(.horizontal, Spacing.s4)
        }
        .padding(.top, Spacing.s4)
    }

    // MARK: Host controls

    @ViewBuilder private var hostControls: some View {
        VStack(spacing: Spacing.s2 + 1) {
            Button { viewModel.openAddAttendee() } label: {
                HStack(spacing: Spacing.s3) {
                    iconTile(.userPlus, background: Theme.Color.primary50, foreground: Theme.Color.primary600)
                    Text("Add or invite attendee")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
                }
                .controlRowStyle()
            }
            .buttonStyle(.plain)

            if viewModel.canAdjustCapacity {
                HStack(spacing: Spacing.s3) {
                    iconTile(.users, background: Theme.Color.appSurfaceSunken, foreground: Theme.Color.appTextStrong)
                    Text("Capacity")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    HStack(spacing: Spacing.s3) {
                        stepperButton(.minus) { Task { await viewModel.adjustCapacity(by: -1) } }
                        Text("\(viewModel.seatTotal)")
                            .font(.system(size: 14, weight: .bold))
                            .monospacedDigit()
                            .foregroundStyle(Theme.Color.appText)
                            .frame(minWidth: 18)
                        stepperButton(.plus) { Task { await viewModel.adjustCapacity(by: 1) } }
                    }
                }
                .controlRowStyle()
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s4 - 1)
    }

    private func iconTile(_ icon: PantopusIcon, background: Color, foreground: Color) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .fill(background)
                .frame(width: 32, height: 32)
            Icon(icon, size: 16, color: foreground)
        }
    }

    private func stepperButton(_ icon: PantopusIcon, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Icon(icon, size: 14, color: Theme.Color.appTextStrong)
                .frame(width: 28, height: 28)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: FAB

    private var messageAllFAB: some View {
        Button { showNudge = true } label: {
            HStack(spacing: Spacing.s2) {
                Icon(.megaphone, size: 16, color: .white)
                Text("Message all")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, Spacing.s5 - 2)
            .frame(height: 46)
            .background(theme.accent)
            .clipShape(Capsule())
            .shadow(color: theme.accent.opacity(0.34), radius: 10, y: 8)
        }
        .buttonStyle(.plain)
        .padding(.trailing, Spacing.s4)
        .padding(.bottom, Spacing.s5)
        .accessibilityIdentifier("scheduling.roster.messageAll")
    }

    // MARK: Loading skeleton

    private var loadingSkeleton: some View {
        ScrollView {
            VStack(spacing: Spacing.s3) {
                Shimmer(height: 118, cornerRadius: Radii.xl)
                    .padding(.top, Spacing.s3)
                ForEach(0 ..< 3, id: \.self) { _ in
                    Shimmer(height: 60, cornerRadius: Radii.lg + 2)
                }
            }
            .padding(.horizontal, Spacing.s4)
        }
    }

    // MARK: Presented sheets / overlays

    private var nudgeSheet: some View {
        SendNudgeSheet(
            viewModel: SendNudgeViewModel(
                owner: viewModel.owner,
                bookingId: viewModel.bookingId,
                eventTitle: "Attendees",
                eventSubtitle: "\(viewModel.filled) attendee\(viewModel.filled == 1 ? "" : "s")",
                counts: viewModel.nudgeCounts,
                client: .shared
            ),
            onClose: { showNudge = false }
        )
    }

    @ViewBuilder private var noShowOverlay: some View {
        if showNoShow {
            MarkNoShowDialog(
                viewModel: MarkNoShowViewModel(
                    owner: viewModel.owner,
                    targets: viewModel.noShowTargets,
                    client: .shared
                ),
                onClose: { showNoShow = false },
                onMarked: { _ in Task { await viewModel.refresh() } }
            )
            .animation(.easeInOut(duration: 0.2), value: showNoShow)
        }
    }
}

private extension View {
    /// White control-row card used by the roster host controls.
    func controlRowStyle() -> some View {
        padding(.horizontal, Spacing.s3 + 1)
            .padding(.vertical, Spacing.s3 - 1)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg + 2, style: .continuous)
                    .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg + 2, style: .continuous))
    }
}
