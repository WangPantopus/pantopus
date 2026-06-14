//
//  NoAvailabilityView.swift
//  Pantopus
//
//  C8 Slot / No-Availability State (Stream I5). The dedicated calm full screen
//  for "nothing open" — never alarm styling. Offers next-horizon paging and,
//  when a later month has times, hands off to C6 so the booker is never
//  dead-ended.
//

import SwiftUI

struct NoAvailabilityView: View {
    @State private var viewModel: NoAvailabilityViewModel

    init(viewModel: NoAvailabilityViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.Color.appBg)
            .navigationTitle("Pick a time")
            .navigationBarTitleDisplayMode(.inline)
            .task { await viewModel.load() }
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .accessibilityIdentifier("scheduling.noAvailability")
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            loading
        case let .noTimes(monthName):
            EmptyState(
                icon: .calendar,
                headline: "No open times in \(monthName)",
                subcopy: "Availability changes often. Try a later month.",
                cta: .init(title: "See next month") { await viewModel.seeNextMonth() }
            )
        case let .found(monthName):
            EmptyState(
                icon: .calendarClock,
                headline: "Times open up in \(monthName)",
                subcopy: "Open times are available — pick one that works for you.",
                cta: .init(title: "See open times") { viewModel.openPicker() }
            )
        case .paused:
            DiscoveryNotice(
                icon: .calendarClock,
                title: "This page isn't taking bookings right now",
                message: "Check back soon — the host can reopen it at any time.",
                accent: viewModel.accent
            )
            .padding(.horizontal, Spacing.s4)
        case let .error(message):
            EmptyState(
                icon: .link,
                headline: message,
                subcopy: "It may have been turned off or moved.",
                cta: .init(title: "Try again") { await viewModel.refresh() }
            )
        }
    }

    private var loading: some View {
        VStack(spacing: Spacing.s3) {
            ForEach(0..<4, id: \.self) { _ in
                SchedulingSlotRowSkeleton()
            }
        }
        .padding(.horizontal, Spacing.s4)
        .accessibilityLabel("Checking for open times")
    }
}

#if DEBUG
#Preview("No availability") {
    NavigationStack {
        NoAvailabilityView(viewModel: .previewNoTimes())
    }
}
#endif
