//
//  BookingDetailView.swift
//  Pantopus
//
//  E2 Booking Detail (Stream I8). A ContentDetail-style host screen: custom top
//  bar (back · status pill · overflow), a pillar-accented header, status banners
//  (cancelled refund line / no-show / completed), section cards (requester,
//  location, assigned member, intake answers, status timeline), and a
//  status-contextual sticky dock. Loading / error states wrapped in the offline
//  banner; E3/E4/E5 present locally.
//

import SwiftUI

struct BookingDetailView: View {
    @State var viewModel: BookingDetailViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: BookingDetailViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        @Bindable var viewModel = viewModel
        return VStack(spacing: Spacing.s0) {
            topBar
            content
        }
        .background(Theme.Color.appBg)
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .task { await viewModel.load() }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .sheet(item: $viewModel.activeSheet) { sheet in
            BookingActionSheetView(
                sheet: sheet,
                owner: viewModel.owner,
                eventName: viewModel.eventName,
                onCompleted: { await viewModel.handleSheetCompleted() },
                onSwitchToReschedule: { booking in viewModel.switchToReschedule(booking) }
            )
        }
        .accessibilityIdentifier("scheduling.bookingDetail")
    }

    // MARK: - Top bar

    private var topBar: some View {
        HStack(spacing: Spacing.s2) {
            Button { dismiss() } label: {
                Icon(.chevronLeft, size: 21, color: Theme.Color.appText)
                    .frame(width: 34, height: 34)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            Spacer()
            if viewModel.booking != nil {
                SchedulingStatusPill(viewModel.status)
            }
            if !viewModel.overflowActions.isEmpty {
                Menu {
                    ForEach(viewModel.overflowActions) { action in
                        Button(role: action.isDestructive ? .destructive : nil) {
                            action.handler()
                        } label: {
                            Label { Text(action.title) } icon: { Icon(action.icon, size: 16) }
                        }
                    }
                } label: {
                    Icon(.moreVertical, size: 19, color: Theme.Color.appTextSecondary)
                        .frame(width: 34, height: 34)
                }
                .accessibilityLabel("More actions")
            }
        }
        .padding(.horizontal, Spacing.s2)
        .frame(height: 46)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) { Rectangle().fill(Theme.Color.appBorder).frame(height: 1) }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        switch viewModel.phase {
        case .loading:
            loadingView
        case .error(let message):
            errorView(message)
        case .ready:
            if let booking = viewModel.booking {
                loaded(booking)
            } else {
                errorView("Couldn't load this booking.")
            }
        }
    }

    private func loaded(_ booking: BookingDTO) -> some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s3) {
                    header(booking)
                    if let actionError = viewModel.actionError { banner(actionError, tone: .error) }
                    statusBanner(booking)
                    requesterCard(booking)
                    locationCard
                    assignedMemberCard(booking)
                    intakeCard(booking)
                    timelineCard
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.top, Spacing.s4)
                .padding(.bottom, hasDock ? 110 : Spacing.s8)
            }
            .refreshable { await viewModel.refresh() }
            if hasDock { dockView }
        }
    }

    private var hasDock: Bool {
        switch viewModel.status {
        case .pending, .confirmed, .active: true
        default: false
        }
    }

    // MARK: - Header

    private func header(_ booking: BookingDTO) -> some View {
        let dimmed = viewModel.status == .cancelled || viewModel.status == .declined
        return VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(viewModel.eventName ?? "Booking")
                .font(.system(size: 21, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(viewModel.headerTime)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Theme.Color.appTextSecondary)
            ownerPill(booking.ownerType)
        }
        .opacity(dimmed ? 0.7 : 1)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func ownerPill(_ ownerType: String?) -> some View {
        let accent = BookingsPillar.accent(forType: ownerType)
        return HStack(spacing: Spacing.s1) {
            Circle().fill(accent).frame(width: 7, height: 7)
            Text(BookingsPillar.label(forType: ownerType))
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(accent)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s1)
        .background(BookingsPillar.accentBg(forType: ownerType))
        .clipShape(Capsule())
    }

    // MARK: - Dock

    @ViewBuilder
    private var dockView: some View {
        switch viewModel.status {
        case .pending:
            dockBar {
                dockGhostDanger(title: "Decline", icon: .x) { viewModel.presentDecline() }
                dockPrimary(title: "Approve", icon: .check, color: Theme.Color.primary600) { viewModel.presentReview() }
            }
        case .confirmed, .active:
            dockBar {
                dockGhostDanger(title: "Cancel", icon: .xCircle) { viewModel.presentCancel() }
                dockPrimary(title: "Reschedule", icon: .calendarClock, color: viewModel.accent) { viewModel.presentReschedule() }
            }
        default:
            EmptyView()
        }
    }

    private func dockBar(@ViewBuilder _ content: () -> some View) -> some View {
        HStack(spacing: Spacing.s2) { content() }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s3)
            .padding(.bottom, Spacing.s5)
            .background(Theme.Color.appSurface)
            .overlay(alignment: .top) { Rectangle().fill(Theme.Color.appBorder).frame(height: 1) }
    }

    private func dockGhostDanger(title: String, icon: PantopusIcon, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Spacing.s1) {
                Icon(icon, size: 16, color: Theme.Color.error)
                Text(title).font(.system(size: 14, weight: .bold))
            }
            .foregroundStyle(Theme.Color.error)
            .frame(maxWidth: .infinity, minHeight: 46)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .strokeBorder(Theme.Color.errorLight, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func dockPrimary(title: String, icon: PantopusIcon, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Spacing.s1) {
                Icon(icon, size: 16, color: Theme.Color.appTextInverse)
                Text(title).font(.system(size: 14, weight: .bold))
            }
            .foregroundStyle(Theme.Color.appTextInverse)
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(color)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var loadingView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                Shimmer(width: 200, height: 20)
                Shimmer(width: 150, height: 12)
                Shimmer(width: 90, height: 22, cornerRadius: Radii.pill)
                ForEach(0..<3, id: \.self) { _ in
                    Shimmer(height: 72, cornerRadius: Radii.xl)
                }
            }
            .padding(Spacing.s4)
        }
        .accessibilityLabel("Loading booking")
    }

    private func errorView(_ message: String) -> some View {
        EmptyState(
            icon: .cloudOff,
            headline: "Couldn't load this booking",
            subcopy: message,
            cta: .init(title: "Try again") { await viewModel.refresh() },
            tint: Theme.Color.errorBg,
            accent: Theme.Color.error
        )
    }

}

#if DEBUG
#Preview("Confirmed") {
    NavigationStack { BookingDetailView(viewModel: .preview(status: "confirmed")) }
}

#Preview("Pending · Business") {
    NavigationStack { BookingDetailView(viewModel: .preview(status: "pending", ownerType: "business")) }
}

#Preview("Cancelled") {
    NavigationStack { BookingDetailView(viewModel: .preview(status: "cancelled", paid: true)) }
}
#endif
