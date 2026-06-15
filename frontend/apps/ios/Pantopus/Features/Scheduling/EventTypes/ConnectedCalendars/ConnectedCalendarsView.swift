//
//  ConnectedCalendarsView.swift
//  Pantopus
//
//  Stream I2 — B8 Connected Calendars (routed full screen). v1 leads with a
//  calm "coming soon" hero + provider connect rows whose buttons honestly
//  surface the 501; linked accounts (future) render as conflict-check /
//  add-bookings rows. Loading skeleton / error+retry included.
//

import SwiftUI

struct ConnectedCalendarsView: View {
    @State private var viewModel: ConnectedCalendarsViewModel

    init(viewModel: ConnectedCalendarsViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        content
            .background(Theme.Color.appBg)
            .navigationTitle("Connected calendars")
            .navigationBarTitleDisplayMode(.inline)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .task { await viewModel.load() }
            .accessibilityIdentifier("scheduling.connectedCalendars")
            .alert("Coming soon", isPresented: noticePresented) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.notice ?? "")
            }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.phase {
        case .loading:
            loadingSkeleton
        case let .error(message):
            ErrorState(headline: "Couldn't load your calendars", message: message) {
                await viewModel.reload()
            }
        case .ready:
            loaded
        }
    }

    private var loaded: some View {
        ScrollView {
            VStack(spacing: Spacing.s5) {
                if viewModel.isComingSoon { comingSoonHero }
                connectedAccounts
                providerRows
                helper
            }
            .padding(.vertical, Spacing.s4)
        }
    }

    // MARK: Coming-soon hero

    private var comingSoonHero: some View {
        VStack(spacing: Spacing.s3) {
            ZStack {
                Circle().fill(Theme.Color.primary50).frame(width: 72, height: 72)
                Icon(.calendarClock, size: 30, strokeWidth: 1.8, color: Theme.Color.primary600)
            }
            Text("Calendar sync is coming soon")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
                .multilineTextAlignment(.center)
            Text("Soon you'll connect Google, Apple or Outlook so Pantopus checks for conflicts and never double-books you.")
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            HStack(spacing: Spacing.s4) {
                ForEach(viewModel.providers) { provider in
                    Icon(provider.icon, size: 22, color: Theme.Color.appTextMuted)
                }
            }
            .padding(.top, Spacing.s1)
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.s5)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .padding(.horizontal, Spacing.s4)
        .accessibilityIdentifier("scheduling.connectedCalendars.comingSoon")
    }

    // MARK: Provider connect rows

    private var providerRows: some View {
        FormFieldGroup("Add a calendar") {
            ForEach(Array(viewModel.providers.enumerated()), id: \.element.id) { index, provider in
                providerRow(provider)
                if index < viewModel.providers.count - 1 {
                    Divider().background(Theme.Color.appBorderSubtle)
                }
            }
        }
    }

    private func providerRow(_ provider: CalendarProvider) -> some View {
        HStack(spacing: Spacing.s3) {
            Icon(provider.icon, size: 20, color: Theme.Color.appTextSecondary).frame(width: 28)
            Text(provider.name)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
            Spacer()
            if viewModel.connectingId == provider.id {
                Shimmer(width: 72, height: 28, cornerRadius: Radii.sm)
            } else {
                Button { Task { await viewModel.connect(provider) } } label: {
                    Text("Connect")
                        .pantopusTextStyle(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.primary600)
                        .padding(.horizontal, Spacing.s3)
                        .padding(.vertical, Spacing.s2)
                        .overlay(Capsule().stroke(Theme.Color.primary600, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("scheduling.connectedCalendars.connect.\(provider.id)")
            }
        }
    }

    // MARK: Linked accounts (future / defensive)

    @ViewBuilder
    private var connectedAccounts: some View {
        if !viewModel.calendars.isEmpty {
            FormFieldGroup("Connected") {
                ForEach(Array(viewModel.calendars.enumerated()), id: \.element.id) { index, calendar in
                    connectedRow(calendar)
                    if index < viewModel.calendars.count - 1 {
                        Divider().background(Theme.Color.appBorderSubtle)
                    }
                }
            }
        }
    }

    private func connectedRow(_ calendar: ConnectedCalendarDTO) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            HStack(spacing: Spacing.s2) {
                Icon(.calendarCheck, size: 18, color: Theme.Color.success)
                Text(calendar.externalAccount ?? calendar.provider ?? "Calendar")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Spacer()
                StatusChip(calendar.status == "needs_reauth" ? "Reconnect" : "Synced",
                           variant: calendar.status == "needs_reauth" ? .warning : .success)
            }
            if let synced = calendar.lastSyncedAt {
                Text("Last synced \(synced)")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }

    // MARK: Helper

    private var helper: some View {
        Text("Until sync is on, add your bookings to your calendar from each confirmation.")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.s4)
    }

    private var loadingSkeleton: some View {
        ScrollView {
            VStack(spacing: Spacing.s5) {
                Shimmer(height: 180, cornerRadius: Radii.lg).padding(.horizontal, Spacing.s4)
                ForEach(0..<3, id: \.self) { _ in
                    Shimmer(height: 48, cornerRadius: Radii.lg).padding(.horizontal, Spacing.s4)
                }
            }
            .padding(.vertical, Spacing.s4)
        }
    }

    private var noticePresented: Binding<Bool> {
        Binding(get: { viewModel.notice != nil }, set: { if !$0 { viewModel.notice = nil } })
    }
}
