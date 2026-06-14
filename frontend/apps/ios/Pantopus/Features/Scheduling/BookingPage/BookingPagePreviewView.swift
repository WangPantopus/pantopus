//
//  BookingPagePreviewView.swift
//  Pantopus
//
//  C2 Public Booking Page Preview · Stream I4. A read-only render of the
//  public /book/:slug page wrapped in dark "preview chrome" with an exit x.
//  The whole render is non-interactive (the "Pick a time" affordance is
//  shown but inert). Honest paused / all-hidden frames. Tokens only.
//

import SwiftUI

public struct BookingPagePreviewView: View {
    @State private var viewModel: BookingPagePreviewViewModel
    @Environment(\.dismiss) private var dismiss

    public init(owner: SchedulingOwner, slug: String) {
        _viewModel = State(initialValue: BookingPagePreviewViewModel(owner: owner, slug: slug))
    }

    init(viewModel: BookingPagePreviewViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        VStack(spacing: Spacing.s0) {
            BookingPreviewBar { dismiss() }
            previewPill
            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Color.appBg.ignoresSafeArea())
        .background(Theme.Color.appText.ignoresSafeArea(edges: .top))
        .toolbar(.hidden, for: .navigationBar)
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .task { await viewModel.load() }
        .accessibilityIdentifier("bookingPagePreview.screen")
    }

    private var previewPill: some View {
        Text("Preview only. Nothing here is bookable.")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextInverse.opacity(0.85))
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s1)
            .background(Theme.Color.appText.opacity(0.85))
            .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
            .padding(.vertical, Spacing.s2)
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            PreviewLoadingView()
        case let .rendered(page, eventTypes):
            publicRender(page: page, eventTypes: eventTypes)
        case let .pageOff(page, status):
            PageOffNotice(page: page, status: status, accent: viewModel.theme.accent)
        case let .allHidden(page):
            AllHiddenNotice(page: page, accent: viewModel.theme.accent)
        case let .error(message):
            PreviewErrorView(message: message) { Task { await viewModel.refresh() } }
        }
    }

    // MARK: - Public render (non-interactive)

    private func publicRender(page: PublicPageView, eventTypes: [PublicEventTypeView]) -> some View {
        ScrollView {
            VStack(spacing: Spacing.s5) {
                PublicHeader(page: page, accent: viewModel.theme.accent)
                VStack(spacing: Spacing.s3) {
                    ForEach(eventTypes) { eventType in
                        PublicEventTypeCard(eventType: eventType, accent: viewModel.theme.accent)
                    }
                }
                InertPickTimeButton(accent: viewModel.theme.accent)
            }
            .padding(Spacing.s4)
            .allowsHitTesting(false) // affordances are inert; the ScrollView still scrolls
        }
        .accessibilityIdentifier("bookingPagePreview.rendered")
    }
}

// MARK: - Preview chrome

private struct BookingPreviewBar: View {
    let onExit: () -> Void

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.eye, size: 16, color: Theme.Color.appTextInverse)
            Text("Previewing your booking page")
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.appTextInverse)
            Spacer()
            Button(action: onExit) {
                Icon(.x, size: 18, color: Theme.Color.appTextInverse)
                    .frame(width: 32, height: 32)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Exit preview")
            .accessibilityIdentifier("bookingPagePreview.exit")
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s3)
        .frame(maxWidth: .infinity)
        .background(Theme.Color.appText)
    }
}

// MARK: - Public header

private struct PublicHeader: View {
    let page: PublicPageView
    let accent: Color

    var body: some View {
        VStack(spacing: Spacing.s2) {
            BookingAvatar(name: page.title ?? "Host", imageURLString: page.avatarURL, size: 72, accent: accent)
            Text(page.title ?? "Host")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
            if let tagline = page.tagline, !tagline.isEmpty {
                Text(tagline)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .multilineTextAlignment(.center)
            }
            if let intro = page.intro, !intro.isEmpty {
                Text(intro)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.s1)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Event-type card

private struct PublicEventTypeCard: View {
    let eventType: PublicEventTypeView
    let accent: Color

    var body: some View {
        BookingCard {
            HStack(spacing: Spacing.s3) {
                Icon(BookingLocationMode.icon(eventType.locationMode), size: 20, color: accent)
                    .frame(width: 40, height: 40)
                    .background(accent.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text(eventType.name)
                        .pantopusTextStyle(.body)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.appText)
                    HStack(spacing: Spacing.s2) {
                        Label(eventType: eventType)
                        LocationChip(mode: eventType.locationMode, accent: accent)
                    }
                }
                Spacer(minLength: Spacing.s2)
                Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
            }
        }
    }

    private struct Label: View {
        let eventType: PublicEventTypeView
        var body: some View {
            Text(BookingDuration.label(eventType.defaultDuration ?? eventType.durations?.first ?? 30))
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }
}

private struct LocationChip: View {
    let mode: String?
    let accent: Color

    var body: some View {
        HStack(spacing: 3) {
            Icon(BookingLocationMode.icon(mode), size: 11, color: accent)
            Text(BookingLocationMode.label(mode))
                .pantopusTextStyle(.caption)
                .foregroundStyle(accent)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 2)
        .background(accent.opacity(0.10))
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
    }
}

private struct InertPickTimeButton: View {
    let accent: Color

    var body: some View {
        Text("Pick a time")
            .pantopusTextStyle(.body)
            .fontWeight(.semibold)
            .foregroundStyle(Theme.Color.appTextInverse)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.s3)
            .background(accent)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }
}

// MARK: - Off / empty / loading / error

private struct PageOffNotice: View {
    let page: PublicPageView
    let status: SchedulingStatus
    let accent: Color

    var body: some View {
        VStack(spacing: Spacing.s3) {
            Spacer()
            ZStack {
                Circle().fill(Theme.Color.appSurfaceSunken).frame(width: 72, height: 72)
                Icon(icon, size: 30, color: Theme.Color.appTextSecondary)
            }
            Text(headline)
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
            Text(caption)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, Spacing.s8)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("bookingPagePreview.pageOff")
    }

    private var icon: PantopusIcon {
        switch status {
        case .expired: .calendarClock
        case .unavailable: .calendarCheck
        default: .pause
        }
    }

    private var headline: String {
        switch status {
        case .expired: "This link has expired"
        case .unavailable: "Fully booked right now"
        default: "Your page is paused"
        }
    }

    private var caption: String {
        switch status {
        case .expired: "Generate a fresh link to keep taking bookings."
        case .unavailable: "There are no open times at the moment."
        default: "Turn it back on in Booking link to take bookings."
        }
    }
}

private struct AllHiddenNotice: View {
    let page: PublicPageView
    let accent: Color

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.s5) {
                PublicHeader(page: page, accent: accent)
                BookingCard {
                    VStack(spacing: Spacing.s2) {
                        Text("No services are visible yet")
                            .pantopusTextStyle(.body)
                            .fontWeight(.semibold)
                            .foregroundStyle(Theme.Color.appText)
                        Text("Turn one on so people see something to book.")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.s4)
                }
            }
            .padding(Spacing.s4)
        }
        .accessibilityIdentifier("bookingPagePreview.allHidden")
    }
}

private struct PreviewLoadingView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.s4) {
                Shimmer(width: 72, height: 72, cornerRadius: Radii.pill)
                Shimmer(width: 160, height: 16)
                Shimmer(width: 120, height: 12)
                ForEach(0..<3, id: \.self) { _ in
                    Shimmer(height: 64, cornerRadius: Radii.lg)
                }
            }
            .padding(Spacing.s4)
        }
        .accessibilityIdentifier("bookingPagePreview.loading")
    }
}

private struct PreviewErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s4) {
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load this page")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            PrimaryButton(title: "Try again") { await MainActor.run { retry() } }
                .frame(maxWidth: 240)
        }
        .padding(Spacing.s6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("bookingPagePreview.error")
    }
}

#if DEBUG
#Preview("Rendered") {
    let viewModel = BookingPagePreviewViewModel(owner: .personal, slug: "maria-k")
    viewModel.setStateForPreview(
        .rendered(page: BookingPageSampleData.publicView.page, eventTypes: BookingPageSampleData.publicView.eventTypes)
    )
    return BookingPagePreviewView(viewModel: viewModel)
}

#Preview("Paused") {
    let viewModel = BookingPagePreviewViewModel(owner: .personal, slug: "maria-k")
    viewModel.setStateForPreview(.pageOff(page: BookingPageSampleData.pausedPublicView.page, status: .paused))
    return BookingPagePreviewView(viewModel: viewModel)
}
#endif
