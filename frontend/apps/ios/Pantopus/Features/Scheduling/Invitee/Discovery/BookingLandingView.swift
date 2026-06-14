//
//  BookingLandingView.swift
//  Pantopus
//
//  C5 Booking Landing / Booker Profile (Stream I5). Public, full-screen invitee
//  landing: host header + a list of bookable event-type cards, themed to the
//  host's pillar. Renders the four fetch states (loading skeleton / loaded /
//  empty / error+Retry) plus the first-class `paused` state, wrapped in the
//  offline banner. Tapping an event type hands off to C6.
//
//  The web design's dismissible "Open in app" banner is intentionally omitted:
//  it is a web→app smart-routing affordance and is meaningless once the booker
//  is already inside the native app.
//

import SwiftUI

struct BookingLandingView: View {
    @State private var viewModel: BookingLandingViewModel

    init(viewModel: BookingLandingViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        content
            .background(Theme.Color.appBg)
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .task { await viewModel.load() }
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .accessibilityIdentifier("scheduling.bookingLanding")
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            loadingSkeleton
        case let .loaded(view):
            loadedScroll(view, eventTypes: view.eventTypes)
        case let .paused(view):
            loadedScroll(view, eventTypes: [], stateCard: pausedCard)
        case let .empty(view):
            loadedScroll(view, eventTypes: [], stateCard: emptyCard)
        case let .error(message):
            errorState(message)
        }
    }

    // MARK: - Loaded

    private func loadedScroll(
        _ view: PublicBookView,
        eventTypes: [PublicEventTypeView],
        @ViewBuilder stateCard: () -> some View = { EmptyView() }
    ) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s5) {
                hostHeader(view.page)
                if !eventTypes.isEmpty {
                    eventTypeSection(eventTypes, accent: viewModel.accent)
                }
                stateCard()
                footer
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s4)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func hostHeader(_ page: PublicPageView) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(spacing: Spacing.s3) {
                HostAvatar(urlString: page.avatarURL, accent: viewModel.accent)
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text(page.title ?? "Booking")
                        .pantopusTextStyle(.h2)
                        .foregroundStyle(Theme.Color.appText)
                    if let tagline = page.tagline, !tagline.isEmpty {
                        Text(tagline)
                            .pantopusTextStyle(.small)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
            }
            if let intro = page.intro, !intro.isEmpty {
                Text(intro)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func eventTypeSection(_ eventTypes: [PublicEventTypeView], accent: Color) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Text("Pick a time that works for you.")
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
            VStack(spacing: Spacing.s3) {
                ForEach(eventTypes) { eventType in
                    EventTypeCardRow(eventType: eventType, accent: accent) {
                        viewModel.selectEventType(eventType)
                    }
                }
            }
            if eventTypes.count == 1 {
                Text("Going straight to pick a time.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
        }
    }

    // MARK: - Calm state cards

    private func pausedCard() -> some View {
        DiscoveryNotice(
            icon: .calendarClock,
            title: "This page isn't taking bookings right now",
            message: "Check back soon — the host can reopen it at any time.",
            accent: viewModel.accent
        )
    }

    private func emptyCard() -> some View {
        DiscoveryNotice(
            icon: .calendar,
            title: "No times are set up yet",
            message: "The host hasn't opened any booking types here.",
            accent: viewModel.accent
        )
    }

    private func errorState(_ message: String) -> some View {
        VStack {
            Spacer(minLength: 0)
            EmptyState(
                icon: .link,
                headline: message,
                subcopy: "It may have been turned off or moved.",
                cta: .init(title: "Try again") { await viewModel.refresh() }
            )
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Loading

    private var loadingSkeleton: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s5) {
                HStack(spacing: Spacing.s3) {
                    Circle().fill(Theme.Color.appSurfaceSunken).frame(width: 56, height: 56)
                    VStack(alignment: .leading, spacing: Spacing.s2) {
                        Shimmer(width: 160, height: 16)
                        Shimmer(width: 110, height: 12)
                    }
                }
                VStack(spacing: Spacing.s3) {
                    ForEach(0..<3, id: \.self) { _ in
                        Shimmer(height: 64, cornerRadius: Radii.xl)
                    }
                }
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s4)
        }
        .accessibilityLabel("Loading booking page")
    }

    // MARK: - Footer

    private var footer: some View {
        HStack(spacing: Spacing.s1) {
            Spacer(minLength: 0)
            Text("Powered by")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextMuted)
            Text("Pantopus")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Spacer(minLength: 0)
        }
        .padding(.top, Spacing.s4)
    }
}

// MARK: - Host avatar

private struct HostAvatar: View {
    let urlString: String?
    let accent: Color

    var body: some View {
        ZStack {
            Circle().fill(accent.opacity(0.12))
            if let urlString, let url = URL(string: urlString) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Icon(.user, size: 26, color: accent)
                }
                .clipShape(Circle())
            } else {
                Icon(.user, size: 26, color: accent)
            }
        }
        .frame(width: 56, height: 56)
    }
}

// MARK: - Event-type card row

struct EventTypeCardRow: View {
    let eventType: PublicEventTypeView
    let accent: Color
    let action: () -> Void

    private var durationLabel: String? {
        let minutes = eventType.defaultDuration ?? eventType.durations?.first
        return minutes.map { "\($0) min" }
    }

    private var locationLabel: String? {
        DiscoveryLocation.label(mode: eventType.locationMode, detail: eventType.locationDetail)
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.s3) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .fill(accent.opacity(0.12))
                        .frame(width: 40, height: 40)
                    Icon(DiscoveryLocation.icon(mode: eventType.locationMode), size: 20, color: accent)
                }
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text(eventType.name)
                        .pantopusTextStyle(.body)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.appText)
                        .multilineTextAlignment(.leading)
                    HStack(spacing: Spacing.s2) {
                        if let durationLabel {
                            metaChip(text: durationLabel, icon: .clock)
                        }
                        if let locationLabel {
                            metaChip(text: locationLabel, icon: DiscoveryLocation.icon(mode: eventType.locationMode))
                        }
                    }
                }
                Spacer(minLength: Spacing.s2)
                Icon(.chevronRight, size: 18, color: Theme.Color.appTextMuted)
            }
            .padding(Spacing.s4)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityIdentifier("scheduling.bookingLanding.eventType")
    }

    private func metaChip(text: String, icon: PantopusIcon) -> some View {
        HStack(spacing: Spacing.s1) {
            Icon(icon, size: 13, color: Theme.Color.appTextMuted)
            Text(text)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }

    private var accessibilityLabel: String {
        [eventType.name, durationLabel, locationLabel].compactMap { $0 }.joined(separator: ", ")
    }
}

// MARK: - Calm notice card

struct DiscoveryNotice: View {
    let icon: PantopusIcon
    let title: String
    let message: String
    let accent: Color

    var body: some View {
        VStack(spacing: Spacing.s3) {
            ZStack {
                Circle().fill(accent.opacity(0.12)).frame(width: 56, height: 56)
                Icon(icon, size: 26, color: accent)
            }
            Text(title)
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
                .multilineTextAlignment(.center)
            Text(message)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.s6)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
    }
}

#if DEBUG
#Preview("Loaded") {
    NavigationStack {
        BookingLandingView(viewModel: .previewLoaded())
    }
}

#Preview("Paused") {
    NavigationStack {
        BookingLandingView(viewModel: .previewPaused())
    }
}
#endif
