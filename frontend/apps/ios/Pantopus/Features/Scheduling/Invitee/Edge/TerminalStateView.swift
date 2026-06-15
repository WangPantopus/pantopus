//
//  TerminalStateView.swift
//  Pantopus
//
//  D7 Unavailable / Expired / Paused / Secret (Stream I7). The calm, full-screen
//  terminal state for an invitee link that can't be booked right now. Each status
//  is rendered honestly (never alarm styling): a soft halo, a plain-language
//  headline + body, and a contextual affordance. Loading / error states wrap in
//  the offline banner. Tokens only.
//

import SwiftUI

struct TerminalStateView: View {
    @State private var viewModel: TerminalStateViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: TerminalStateViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.Color.appBg)
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .task { await viewModel.load() }
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .accessibilityIdentifier("scheduling.terminalState")
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            loading
        case let .resolved(kind, hostName):
            resolved(kind: kind, hostName: hostName)
        case let .error(message):
            EmptyState(
                icon: .wifiOff,
                headline: message,
                subcopy: "Check your connection and try again.",
                cta: .init(title: "Try again") { await viewModel.refresh() }
            )
        }
    }

    private var loading: some View {
        VStack(spacing: Spacing.s4) {
            Circle().fill(Theme.Color.appSurfaceSunken).frame(width: 84, height: 84)
            Shimmer(width: 180, height: 18)
            Shimmer(width: 220, height: 12)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel("Loading")
    }

    private func resolved(kind: TerminalKind, hostName: String?) -> some View {
        let copy = TerminalCopy.copy(for: kind, hostName: hostName)
        return VStack(spacing: Spacing.s4) {
            Spacer(minLength: 0)
            EdgeIconHalo(icon: copy.icon, tone: copy.tone, size: 84)
            VStack(spacing: Spacing.s2) {
                Text(copy.title)
                    .font(.system(size: 19, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                    .multilineTextAlignment(.center)
                Text(copy.body)
                    .font(.system(size: 12.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(2)
                    .frame(maxWidth: 250)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .safeAreaInset(edge: .bottom) { dock(kind: kind) }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(copy.title). \(copy.body)")
    }

    @ViewBuilder
    private func dock(kind: TerminalKind) -> some View {
        EdgeDock {
            if kind == .cancelled, viewModel.slug?.isEmpty == false {
                PrimaryButton(title: "Book again") { viewModel.openBookingPage() }
            } else if (kind == .paused || kind == .fullyBooked), viewModel.slug?.isEmpty == false {
                GhostButton(title: "Back to this page") { viewModel.openBookingPage() }
            }
            GhostButton(title: "Go back") { dismiss() }
        }
    }
}

// MARK: - Per-state copy

private enum TerminalCopy {
    struct Copy {
        let icon: PantopusIcon
        let tone: EdgeTone
        let title: String
        let body: String
    }

    static func copy(for kind: TerminalKind, hostName: String?) -> Copy {
        let host = hostName ?? "This host"
        switch kind {
        case .notFound:
            return Copy(
                icon: .searchX,
                tone: .neutral,
                title: "We can't find that page",
                body: "The link may be mistyped, or this page no longer exists."
            )
        case .privateLink:
            return Copy(
                icon: .lock,
                tone: .neutral,
                title: "This is a private link",
                body: "Ask the host for the right link to book a time."
            )
        case .expired:
            return Copy(
                icon: .clock,
                tone: .neutral,
                title: "This link has expired",
                body: "For your security, these links stop working after a while."
            )
        case .paused:
            return Copy(
                icon: .pauseCircle,
                tone: .warning,
                title: "Bookings are paused",
                body: "\(host) isn't taking new bookings at the moment."
            )
        case .fullyBooked:
            return Copy(
                icon: .calendarX,
                tone: .neutral,
                title: "No times are open right now",
                body: "Every slot is taken for now — new times open up regularly."
            )
        case .cancelled:
            return Copy(
                icon: .xCircle,
                tone: .neutral,
                title: "This booking was cancelled",
                body: "The slot was released. Nothing further is owed."
            )
        }
    }
}

#if DEBUG
#Preview("Paused") {
    NavigationStack { TerminalStateView(viewModel: .preview(.paused)) }
}

#Preview("Expired") {
    NavigationStack { TerminalStateView(viewModel: .preview(.expired, hostName: nil)) }
}
#endif
