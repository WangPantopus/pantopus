//
//  HomeCalendarView.swift
//  Pantopus
//
//  T6.4c / Stream I10 — Home calendar / agenda. The existing month-strip +
//  day-grouped agenda, extended per the I10 design: a "Who's free" entry, a
//  member filter-chip row, per-row assignee avatar stacks, booking-union rows
//  (source:'booking' → badge + status pill, deep-link to Booking Detail E2),
//  and a FAB create-menu that fans out into Add event / Find a time / Book a
//  resource / Schedule a visit. Home pillar green.
//

import SwiftUI

public struct HomeCalendarView: View {
    @State private var viewModel: HomeCalendarViewModel

    public init(viewModel: HomeCalendarViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        @Bindable var bindable = viewModel
        return ZStack(alignment: .bottomTrailing) {
            content
            if showsFab {
                fab
            }
        }
        .navigationTitle("Calendar")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    viewModel.openWhosFree()
                } label: {
                    Icon(.users, size: 20, color: Theme.Color.homeDark)
                }
                .accessibilityLabel("Who's free")
                .accessibilityIdentifier("homeCalendar_whosFree")
            }
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("homeCalendar")
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .onAppear { Analytics.track(.screenHomeCalendarViewed) }
        .task { await viewModel.load() }
        .sheet(isPresented: $bindable.isCreateMenuPresented) {
            HomeCalendarCreateMenu { action in viewModel.selectCreateAction(action) }
        }
        .fullScreenCover(item: $bindable.presentedRoute) { presented in
            HomeSchedulingRouteHost(
                initialRoute: presented.route,
                homeId: viewModel.homeIdForRouting,
                onDismiss: { viewModel.presentedRoute = nil }
            )
        }
    }

    private var showsFab: Bool {
        switch viewModel.state {
        case .loading, .error: false
        default: true
        }
    }

    // MARK: - Content

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            loadingView
        case let .error(message):
            EmptyState(
                icon: .cloudOff,
                headline: "Couldn't load the calendar",
                subcopy: message,
                cta: EmptyState.CTA(title: "Retry") { await viewModel.load() },
                tint: Theme.Color.homeBg,
                accent: Theme.Color.home
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        default:
            VStack(spacing: 0) {
                if let strip = viewModel.monthStrip {
                    MonthStripHeader(
                        state: strip,
                        onSelectDay: { iso in viewModel.selectDay(isoDate: iso) },
                        onPrevMonth: { viewModel.shiftWeek(.previous) },
                        onNextMonth: { viewModel.shiftWeek(.next) }
                    )
                }
                FilterChipRow(
                    chips: viewModel.filterChips,
                    selected: viewModel.memberFilter,
                    onSelect: { viewModel.selectFilter($0) }
                )
                if let empty = viewModel.agendaEmpty {
                    emptyView(empty)
                } else {
                    agendaList
                }
            }
        }
    }

    private var agendaList: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Spacing.s2) {
                ForEach(viewModel.agendaSections) { section in
                    Text(section.header)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .padding(.horizontal, Spacing.s1)
                        .padding(.top, Spacing.s1)
                    ForEach(section.items) { item in
                        HomeAgendaRowCard(item: item) {
                            viewModel.openAgendaItem(item)
                        }
                    }
                }
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.top, Spacing.s2)
            .padding(.bottom, 96)
        }
        .refreshable { await viewModel.refresh() }
    }

    @ViewBuilder private func emptyView(_ empty: HomeCalendarViewModel.AgendaEmpty) -> some View {
        switch empty {
        case .firstRun:
            EmptyState(
                icon: .calendarDays,
                headline: "Nothing scheduled",
                subcopy: "Add your first event and it shows up here for the whole household.",
                cta: EmptyState.CTA(title: "Add an event") {
                    await MainActor.run { viewModel.selectCreateAction(.addEvent) }
                },
                tint: Theme.Color.homeBg,
                accent: Theme.Color.home
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .filteredMember(name):
            FilteredEmpty(
                title: "No events for \(name) this week",
                subcopy: "\(name.capitalized) has nothing scheduled in this range.",
                onClear: { viewModel.clearMemberFilter() }
            )
        case .filteredDay:
            FilteredEmpty(
                title: "Nothing on this day",
                subcopy: "Pick a different day or jump back to today.",
                onClear: { viewModel.jumpToToday() }
            )
        }
    }

    private var loadingView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s2) {
                Shimmer(width: 130, height: 11)
                    .padding(.horizontal, Spacing.s1)
                ForEach(0..<3, id: \.self) { _ in HomeAgendaSkeletonRow() }
                Shimmer(width: 110, height: 11)
                    .padding(.horizontal, Spacing.s1)
                    .padding(.top, Spacing.s2)
                ForEach(0..<2, id: \.self) { _ in HomeAgendaSkeletonRow() }
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s3)
        }
    }

    // MARK: - FAB

    private var fab: some View {
        Button {
            viewModel.openCreateMenu()
        } label: {
            Icon(.plus, size: 24, strokeWidth: 2.4, color: Theme.Color.appTextInverse)
                .frame(width: 52, height: 52)
                .background(Theme.Color.home)
                .clipShape(Circle())
                .pantopusShadow(.primary)
        }
        .buttonStyle(.plain)
        .padding(Spacing.s4)
        .padding(.bottom, Spacing.s2)
        .accessibilityLabel("Create")
        .accessibilityIdentifier("homeCalendar_fab")
    }
}

// MARK: - Filter chip row

private struct FilterChipRow: View {
    let chips: [HomeCalendarViewModel.MemberFilter]
    let selected: HomeCalendarViewModel.MemberFilter
    let onSelect: @MainActor (HomeCalendarViewModel.MemberFilter) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.s2) {
                ForEach(chips, id: \.self) { chip in
                    let isOn = chip == selected
                    Button { onSelect(chip) } label: {
                        Text(label(for: chip))
                            .font(.system(size: 12, weight: isOn ? .bold : .semibold))
                            .foregroundStyle(isOn ? Theme.Color.homeDark : Theme.Color.appTextStrong)
                            .padding(.horizontal, 13)
                            .frame(height: 30)
                            .background(isOn ? Theme.Color.homeBg : Theme.Color.appSurface)
                            .overlay(
                                Capsule().stroke(isOn ? Color.clear : Theme.Color.appBorder, lineWidth: 1)
                            )
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("homeCalendar_filter_\(label(for: chip))")
                    .accessibilityAddTraits(isOn ? [.isButton, .isSelected] : .isButton)
                }
            }
            .padding(.horizontal, Spacing.s3)
        }
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
    }

    private func label(for chip: HomeCalendarViewModel.MemberFilter) -> String {
        switch chip {
        case .all: "All"
        case .mine: "Mine"
        case let .member(_, name): name.split(separator: " ").first.map(String.init) ?? name
        }
    }
}

// MARK: - Filtered empty

private struct FilteredEmpty: View {
    let title: String
    let subcopy: String
    let onClear: @MainActor () -> Void

    var body: some View {
        VStack(spacing: Spacing.s1) {
            Icon(.calendarDays, size: 26, color: Theme.Color.home)
                .frame(width: 56, height: 56)
                .background(Theme.Color.homeBg)
                .clipShape(Circle())
                .padding(.bottom, Spacing.s2)
            Text(title)
                .font(.system(size: 15.5, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .multilineTextAlignment(.center)
            Text(subcopy)
                .font(.system(size: 12.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button(action: onClear) {
                HStack(spacing: 6) {
                    Icon(.x, size: 13, color: Theme.Color.homeDark)
                    Text("Clear filter")
                        .font(.system(size: 12.5, weight: .bold))
                        .foregroundStyle(Theme.Color.homeDark)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 9)
                .background(Theme.Color.homeBg)
                .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .padding(.top, Spacing.s3)
            .accessibilityIdentifier("homeCalendar_clearFilter")
        }
        .padding(.horizontal, Spacing.s6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview {
    NavigationStack {
        HomeCalendarView(
            viewModel: HomeCalendarViewModel(
                homeId: "preview",
                homeSubtitle: "412 Birch Ln"
            )
        )
    }
}
