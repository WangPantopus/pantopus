//
//  TodayDetailView.swift
//  Pantopus
//
//  P6.6 — Full "Today" detail behind the Hub's Today card. Weather + AQI +
//  commute from the Hub today summary, plus today's calendar events.
//

import SwiftUI

struct TodayDetailView: View {
    @State private var viewModel: TodayDetailViewModel
    private let onBack: () -> Void

    init(
        viewModel: TodayDetailViewModel = TodayDetailViewModel(),
        onBack: @escaping () -> Void = {}
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
    }

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                loadingShell
            case .empty:
                emptyShell
            case let .loaded(content):
                loadedShell(content)
            case let .error(message):
                errorShell(message)
            }
        }
        .accessibilityIdentifier("todayDetail")
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .task { await viewModel.load() }
    }

    // MARK: - States

    private var loadingShell: some View {
        ContentDetailShell(
            title: "Today",
            onBack: onBack,
            header: {
                Shimmer(height: 120, cornerRadius: Radii.lg)
                    .padding(.horizontal, Spacing.s4)
            },
            body: {
                VStack(spacing: Spacing.s3) {
                    Shimmer(height: 72, cornerRadius: Radii.md)
                    Shimmer(height: 72, cornerRadius: Radii.md)
                    Shimmer(height: 56, cornerRadius: Radii.md)
                }
                .padding(.horizontal, Spacing.s4)
            }
        )
    }

    private var emptyShell: some View {
        ContentDetailShell(
            title: "Today",
            onBack: onBack,
            header: { EmptyView() },
            body: {
                EmptyState(
                    icon: .sun,
                    headline: "Nothing to show yet",
                    subcopy: "Add a home address to see weather, air quality, and your day at a glance.",
                    tint: Theme.Color.personalBg
                )
                .frame(height: 360)
            }
        )
    }

    private func errorShell(_ message: String) -> some View {
        ContentDetailShell(
            title: "Today",
            onBack: onBack,
            header: { EmptyView() },
            body: {
                EmptyState(
                    icon: .alertCircle,
                    headline: "Couldn't load today",
                    subcopy: message,
                    cta: EmptyState.CTA(title: "Try again") { await viewModel.refresh() }
                )
                .frame(height: 360)
            }
        )
    }

    private func loadedShell(_ content: TodayDetailContent) -> some View {
        ContentDetailShell(
            title: "Today",
            onBack: onBack,
            header: {
                if content.hasWeather {
                    WeatherHero(content: content)
                        .padding(.horizontal, Spacing.s4)
                }
            },
            body: {
                VStack(alignment: .leading, spacing: Spacing.s4) {
                    if let aqi = content.aqiLabel {
                        StatCard(
                            icon: .leaf,
                            title: "Air quality",
                            value: content.aqiValue.map { "\($0) · \(aqi)" } ?? aqi,
                            identifier: "todayDetailAQI"
                        )
                        .padding(.horizontal, Spacing.s4)
                    }
                    if let commute = content.commute {
                        StatCard(
                            icon: .navigation,
                            title: "Commute",
                            value: commute,
                            identifier: "todayDetailCommute"
                        )
                        .padding(.horizontal, Spacing.s4)
                    }
                    EventsSection(events: content.events)
                        .padding(.horizontal, Spacing.s4)
                }
            }
        )
    }
}

// MARK: - Weather hero

private struct WeatherHero: View {
    let content: TodayDetailContent

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.s3) {
            ZStack {
                Circle().fill(Theme.Color.personalBg).frame(width: 56, height: 56)
                Icon(.sun, size: 28, color: Theme.Color.primary600)
            }
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.s1) {
                if let temp = content.temperatureF {
                    Text("\(temp)°")
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                }
                if let conditions = content.conditions {
                    Text(conditions)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("todayDetailWeather")
    }
}

// MARK: - Stat card

private struct StatCard: View {
    let icon: PantopusIcon
    let title: String
    let value: String
    let identifier: String

    var body: some View {
        HStack(spacing: Spacing.s3) {
            Icon(icon, size: 20, color: Theme.Color.primary600)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Text(value)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title) \(value)")
        .accessibilityIdentifier(identifier)
    }
}

// MARK: - Events

private struct EventsSection: View {
    let events: [TodayEventRow]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("TODAY'S EVENTS")
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityAddTraits(.isHeader)
            if events.isEmpty {
                Text("No events scheduled for today.")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(Spacing.s4)
                    .background(Theme.Color.appSurface)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.lg)
                            .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
                    )
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(events.enumerated()), id: \.element.id) { index, event in
                        if index > 0 {
                            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
                        }
                        EventRow(event: event)
                    }
                }
                .background(Theme.Color.appSurface)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.lg)
                        .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
                )
            }
        }
        .accessibilityIdentifier("todayDetailEvents")
    }
}

private struct EventRow: View {
    let event: TodayEventRow

    var body: some View {
        HStack(spacing: Spacing.s3) {
            Icon(event.icon, size: 18, color: Theme.Color.home)
            VStack(alignment: .leading, spacing: 2) {
                Text(event.title)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Text("\(event.timeLabel) · \(event.typeLabel)")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.s4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(event.title), \(event.timeLabel), \(event.typeLabel)")
    }
}

#Preview {
    TodayDetailView()
}
