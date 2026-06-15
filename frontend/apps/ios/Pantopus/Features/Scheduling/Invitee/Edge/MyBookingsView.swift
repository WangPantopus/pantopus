//
//  MyBookingsView.swift
//  Pantopus
//
//  D11 My Bookings — customer (Stream I7). The signed-in user's bookings in one
//  place: an Upcoming / Past segmented control over time-bucketed groups, each
//  row carrying the host pillar, the local time and an honest status pill.
//  Loading skeleton / empty / loaded / error states, wrapped in the offline
//  banner. Tokens only.
//

import SwiftUI

struct MyBookingsView: View {
    @State private var viewModel: MyBookingsViewModel

    init(viewModel: MyBookingsViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.Color.appBg)
            .navigationTitle("My bookings")
            .navigationBarTitleDisplayMode(.inline)
            .task { await viewModel.load() }
            .refreshable { await viewModel.refresh() }
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .accessibilityIdentifier("scheduling.myBookings")
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            loading
        case .empty:
            emptyState
        case .error(let message):
            EmptyState(
                icon: .calendar,
                headline: "We couldn't load your bookings",
                subcopy: message,
                cta: .init(title: "Try again") { await viewModel.refresh() }
            )
        case .loaded:
            loaded
        }
    }

    // MARK: - Loaded

    private var loaded: some View {
        ScrollView {
            VStack(spacing: Spacing.s3) {
                segmented
                Text("Everything you've booked, in one place.")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                if currentGroups.isEmpty {
                    segmentEmpty
                } else {
                    ForEach(currentGroups) { group in
                        VStack(alignment: .leading, spacing: Spacing.s2) {
                            EdgeOverline(text: group.title, alert: group.attention)
                            ForEach(group.bookings) { booking in
                                bookingRow(booking)
                            }
                        }
                    }
                }
            }
            .padding(Spacing.s4)
        }
    }

    private var currentGroups: [BookingGroup] {
        viewModel.segment == .upcoming ? viewModel.upcomingGroups : viewModel.pastGroups
    }

    private var segmented: some View {
        HStack(spacing: 3) {
            segmentButton(title: "Upcoming", value: .upcoming)
            segmentButton(title: "Past", value: .past)
        }
        .padding(3)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private func segmentButton(title: String, value: MyBookingsViewModel.Segment) -> some View {
        let isSelected = viewModel.segment == value
        return Button { viewModel.segment = value } label: {
            Text(title)
                .font(.system(size: 11.5, weight: isSelected ? .bold : .semibold))
                .foregroundStyle(isSelected ? Theme.Color.primary700 : Theme.Color.appTextSecondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.s2)
                .background(isSelected ? Theme.Color.appSurface : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
                .edgeShadow(isSelected ? .sm : nil)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("scheduling.myBookings.segment.\(title)")
    }

    private func bookingRow(_ booking: BookingDTO) -> some View {
        let isPast = viewModel.segment == .past
        let tz = booking.inviteeTimezone ?? SchedulingTime.deviceTimeZoneIdentifier
        return HStack(spacing: Spacing.s3) {
            EdgePillarAvatar(name: booking.inviteeName, ownerType: booking.ownerType, size: 42)
            VStack(alignment: .leading, spacing: 2) {
                Text(EdgeFormat.dayTime(booking.startAt, tz: tz) ?? "Booking")
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                Text(ownerLabel(booking.ownerType))
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: Spacing.s2)
            SchedulingStatusPill(status: booking.status)
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .opacity(isPast ? 0.7 : 1)
        .accessibilityElement(children: .combine)
    }

    private func ownerLabel(_ ownerType: String?) -> String {
        "\(EdgeOwnerTheme.owner(forOwnerType: ownerType).theme.title) booking"
    }

    // MARK: - States

    private var loading: some View {
        ScrollView {
            VStack(spacing: Spacing.s3) {
                segmented
                ForEach(0..<4, id: \.self) { _ in
                    HStack(spacing: Spacing.s3) {
                        Circle().fill(Theme.Color.appSurfaceSunken).frame(width: 42, height: 42)
                        VStack(alignment: .leading, spacing: 6) {
                            Shimmer(width: 160, height: 12)
                            Shimmer(width: 110, height: 10)
                        }
                        Spacer()
                        Shimmer(width: 56, height: 18)
                    }
                    .padding(Spacing.s3)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                            .stroke(Theme.Color.appBorder, lineWidth: 1)
                    )
                }
            }
            .padding(Spacing.s4)
        }
        .accessibilityLabel("Loading your bookings")
    }

    private var emptyState: some View {
        EmptyState(
            icon: .calendar,
            headline: "You haven't booked anything yet",
            subcopy: "Bookings you make show up here — everything in one place."
        )
    }

    private var segmentEmpty: some View {
        VStack(spacing: Spacing.s2) {
            Icon(.calendar, size: 26, color: Theme.Color.appTextMuted)
            Text(viewModel.segment == .upcoming ? "No upcoming bookings" : "No past bookings")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.s10)
    }
}

#if DEBUG
#Preview("Loaded") {
    NavigationStack { MyBookingsView(viewModel: .previewLoaded()) }
}

#Preview("Empty") {
    NavigationStack { MyBookingsView(viewModel: .previewEmpty()) }
}
#endif
