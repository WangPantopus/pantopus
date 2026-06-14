//
//  DateOverridesView.swift
//  Pantopus
//
//  Stream I3 — B6 Date Overrides & Holidays (sheet). A month calendar +
//  Unavailable/Custom-hours composer, a date-range block, the list of saved
//  overrides, and a US-public-holiday bulk import. Presented locally from the
//  weekly-hours editor; also routable.
//

import SwiftUI

struct DateOverridesView: View {
    @State private var viewModel: DateOverridesViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: DateOverridesViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        VStack(spacing: Spacing.s0) {
            header
            content
        }
        .background(Theme.Color.appSurface)
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .task { await viewModel.load() }
        .accessibilityIdentifier("scheduling.dateOverrides")
        .alert("Couldn't save", isPresented: errorPresented) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }

    private var header: some View {
        HStack {
            Text("Date overrides")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
            Spacer()
            Button("Done") { dismiss() }
                .font(Theme.Font.body)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.primary600)
        }
        .padding(Spacing.s4)
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.phase {
        case .loading:
            loadingSkeleton
        case let .error(message):
            ErrorState(headline: "Couldn't load overrides", message: message) {
                await viewModel.reload()
            }
        case .ready:
            ScrollView {
                VStack(spacing: Spacing.s4) {
                    composerCard
                    overridesSection
                    holidayCard
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.bottom, Spacing.s8)
            }
            .background(Theme.Color.appBg)
        }
    }

    // MARK: Composer

    private var composerCard: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            DatePicker(
                "Pick a date",
                selection: $viewModel.selectedDate,
                displayedComponents: .date
            )
            .datePickerStyle(.graphical)
            .tint(Theme.Color.primary600)
            .accessibilityIdentifier("scheduling.dateOverrides.calendar")

            Toggle(isOn: $viewModel.isRange) {
                Text("Block a date range")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
            }
            .tint(Theme.Color.primary600)

            if viewModel.isRange {
                DatePicker(
                    "Until",
                    selection: $viewModel.rangeEndDate,
                    in: viewModel.selectedDate...viewModel.maxRangeEnd,
                    displayedComponents: .date
                )
                .tint(Theme.Color.primary600)
            } else {
                Picker("What happens", selection: $viewModel.mode) {
                    ForEach(OverrideMode.allCases) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
                .pickerStyle(.segmented)

                if viewModel.mode == .customHours {
                    customHoursRow
                }
            }

            PrimaryButton(title: "Add", isEnabled: viewModel.canAddCustom) {
                await viewModel.addOverride()
            }
        }
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private var customHoursRow: some View {
        HStack(spacing: Spacing.s2) {
            DatePicker("Start", selection: customStartBinding, displayedComponents: .hourAndMinute)
                .labelsHidden()
                .accessibilityLabel("Custom start time")
            Text("–")
                .foregroundStyle(Theme.Color.appTextMuted)
            DatePicker("End", selection: customEndBinding, displayedComponents: .hourAndMinute)
                .labelsHidden()
                .accessibilityLabel("Custom end time")
            Spacer()
        }
    }

    // MARK: Overrides list

    @ViewBuilder
    private var overridesSection: some View {
        if viewModel.overrides.isEmpty {
            Text("No date overrides yet. Pick a date to add one.")
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Spacing.s4)
        } else {
            VStack(spacing: Spacing.s0) {
                ForEach(Array(viewModel.overrides.enumerated()), id: \.element.id) { index, entry in
                    overrideRow(entry)
                    if index < viewModel.overrides.count - 1 {
                        Divider().background(Theme.Color.appBorder)
                    }
                }
            }
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
    }

    private func overrideRow(_ entry: OverrideEntry) -> some View {
        HStack(spacing: Spacing.s3) {
            VStack(alignment: .leading, spacing: 2) {
                Text(OverrideFormatting.displayDate(entry.date))
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Text(Self.summary(entry))
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s2)
            Button {
                Task { await viewModel.removeOverride(entry.date) }
            } label: {
                Icon(.trash, size: 18, color: Theme.Color.error)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Delete override for \(OverrideFormatting.displayDate(entry.date))")
        }
        .padding(Spacing.s3)
    }

    private static func summary(_ entry: OverrideEntry) -> String {
        if entry.isUnavailable { return "Unavailable" }
        if let start = entry.start, let end = entry.end {
            return TimeRange(start: start, end: end).display
        }
        return "Custom hours"
    }

    // MARK: Holidays

    private var holidayCard: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Toggle(isOn: holidaysBinding) {
                Text("US public holidays")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
            }
            .tint(Theme.Color.primary600)
            Text("Adds \(viewModel.holidayCount) days off this year.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    // MARK: Loading

    private var loadingSkeleton: some View {
        VStack(spacing: Spacing.s4) {
            Shimmer(height: 240, cornerRadius: Radii.lg)
            ForEach(0..<3, id: \.self) { _ in
                Shimmer(height: 56, cornerRadius: Radii.lg)
            }
            Spacer()
        }
        .padding(Spacing.s4)
    }

    // MARK: Bindings

    private var customStartBinding: Binding<Date> {
        Binding(
            get: { viewModel.customStart.referenceDate() },
            set: { viewModel.customStart = TimeOfDay(from: $0) }
        )
    }

    private var customEndBinding: Binding<Date> {
        Binding(
            get: { viewModel.customEnd.referenceDate() },
            set: { viewModel.customEnd = TimeOfDay(from: $0) }
        )
    }

    private var holidaysBinding: Binding<Bool> {
        Binding(
            get: { viewModel.holidaysEnabled },
            set: { enable in Task { await viewModel.toggleHolidays(enable) } }
        )
    }

    private var errorPresented: Binding<Bool> {
        Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
    }
}
