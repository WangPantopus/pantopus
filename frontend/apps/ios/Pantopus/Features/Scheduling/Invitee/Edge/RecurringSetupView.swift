//
//  RecurringSetupView.swift
//  Pantopus
//
//  D12 Recurring / Multi-Session Setup (Stream I7). The owner lays out a weekly
//  series — weekday, time, and how many sessions — sees a live preview of the
//  occurrences, and books them in one go. A 409 surfaces the nearest open times
//  via the Foundation SlotTakenSheet. Loading / configuring / confirming / booked
//  / error states. Tokens only.
//

import SwiftUI

struct RecurringSetupView: View {
    @State private var viewModel: RecurringSetupViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: RecurringSetupViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    private let weekdayLabels = ["S", "M", "T", "W", "T", "F", "S"]

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.Color.appBg)
            .navigationTitle("Set up your series")
            .navigationBarTitleDisplayMode(.inline)
            .task { await viewModel.load() }
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .slotTakenSheet(
                item: Binding(get: { viewModel.slotConflict }, set: { viewModel.slotConflict = $0 }),
                tz: TimeZone.current.identifier,
                onSelect: { _ in viewModel.dismissConflict() },
                onPickAnother: { viewModel.dismissConflict() }
            )
            .accessibilityIdentifier("scheduling.recurringSetup")
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            loading
        case .configuring, .confirming:
            configuring
        case let .booked(count):
            booked(count: count)
        case let .error(message):
            EmptyState(
                icon: .repeat2,
                headline: "We couldn't set up your series",
                subcopy: message,
                cta: .init(title: "Try again") { await viewModel.refresh() }
            )
        }
    }

    private var loading: some View {
        VStack(spacing: Spacing.s3) {
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .fill(Theme.Color.appSurfaceSunken)
                .frame(height: 180)
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .fill(Theme.Color.appSurfaceSunken)
                .frame(height: 120)
        }
        .padding(Spacing.s4)
        .frame(maxHeight: .infinity, alignment: .top)
        .accessibilityLabel("Loading")
    }

    // MARK: - Configuring

    private var configuring: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                Text("Book the whole series in one go. We'll find the same time each week and flag any that's taken.")
                    .font(.system(size: 11.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                if viewModel.conflictHint {
                    conflictBanner
                }
                controlsCard
                seriesStrip
                EdgeOverline(text: "All \(viewModel.count) open")
                ForEach(Array(viewModel.sessions.enumerated()), id: \.offset) { _, date in
                    occurrenceRow(date)
                }
                summaryChip
            }
            .padding(Spacing.s4)
        }
        .safeAreaInset(edge: .bottom) {
            EdgeDock {
                PrimaryButton(
                    title: "Review \(viewModel.count) bookings",
                    isLoading: viewModel.state == .confirming
                ) { await viewModel.confirm() }
            }
        }
    }

    private var conflictBanner: some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            Icon(.alertCircle, size: 16, strokeWidth: 2.2, color: Theme.Color.warning)
            Text("Some of those times are taken. Adjust the day, time, or count and try again.")
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundStyle(Theme.Color.warning)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(Spacing.s3)
        .background(Theme.Color.warningBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private var controlsCard: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            field(label: "Repeats") {
                HStack(spacing: Spacing.s2) {
                    Icon(.repeat2, size: 15, color: Theme.Color.primary600)
                    Text("Weekly")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                    Spacer()
                    Icon(.chevronDown, size: 16, color: Theme.Color.appTextMuted)
                }
                .padding(.horizontal, Spacing.s3)
                .frame(height: 42)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
            }
            field(label: "On") { weekdayChips }
            field(label: "At") { timeRow }
            field(label: "How many") { countStepper }
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
    }

    private func field<Inner: View>(label: String, @ViewBuilder content: () -> Inner) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextStrong)
            content()
        }
    }

    private var weekdayChips: some View {
        HStack(spacing: Spacing.s1) {
            ForEach(0..<7, id: \.self) { index in
                let isSelected = viewModel.weekdayIndex == index
                Button { viewModel.setWeekday(index) } label: {
                    Text(weekdayLabels[index])
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(isSelected ? Theme.Color.appTextInverse : Theme.Color.appTextSecondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 32)
                        .background(isSelected ? Theme.Color.primary600 : Theme.Color.appSurface)
                        .overlay(
                            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                                .stroke(isSelected ? Color.clear : Theme.Color.appBorder, lineWidth: 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Calendar(identifier: .gregorian).weekdaySymbols[index])
                .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
            }
        }
    }

    private var timeRow: some View {
        HStack {
            DatePicker(
                "",
                selection: Binding(get: { viewModel.timeOfDay }, set: { viewModel.timeOfDay = $0 }),
                displayedComponents: .hourAndMinute
            )
            .labelsHidden()
            Spacer()
        }
        .accessibilityIdentifier("scheduling.recurringSetup.time")
    }

    private var countStepper: some View {
        HStack {
            Text("for \(viewModel.count) sessions")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
            Spacer()
            HStack(spacing: 0) {
                stepperButton(icon: .minus, enabled: viewModel.count > viewModel.minCount) { viewModel.decrementCount() }
                Text("\(viewModel.count)")
                    .font(.system(size: 13, weight: .bold))
                    .monospacedDigit()
                    .frame(minWidth: 34, minHeight: 34)
                stepperButton(icon: .plus, enabled: viewModel.count < viewModel.maxCount) { viewModel.incrementCount() }
            }
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1.5)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
    }

    private func stepperButton(icon: PantopusIcon, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Icon(icon, size: 14, color: enabled ? Theme.Color.appText : Theme.Color.appTextMuted)
                .frame(width: 34, height: 34)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    // MARK: - Series strip

    private var seriesStrip: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s1) {
                Icon(.calendarDays, size: 13, color: Theme.Color.appTextSecondary)
                Text("Your \(viewModel.count) \(viewModel.weekdayName)s")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextStrong)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.s2) {
                    ForEach(Array(viewModel.sessions.enumerated()), id: \.offset) { _, date in
                        seriesPill(date)
                    }
                }
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private func seriesPill(_ date: Date) -> some View {
        VStack(spacing: Spacing.s1) {
            Text(monthLabel(date))
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(Theme.Color.appTextMuted)
            Text(dayLabel(date))
                .font(.system(size: 12.5, weight: .bold))
                .foregroundStyle(Theme.Color.appTextInverse)
                .frame(width: 34, height: 34)
                .background(Theme.Color.primary600)
                .clipShape(Circle())
            Circle().fill(Theme.Color.primary600).frame(width: 5, height: 5)
        }
        .frame(width: 42)
    }

    // MARK: - Occurrence row

    private func occurrenceRow(_ date: Date) -> some View {
        HStack(spacing: Spacing.s3) {
            Icon(.calendarCheck, size: 16, color: Theme.Color.success)
                .frame(width: 30, height: 30)
                .background(Theme.Color.successBg)
                .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
            VStack(alignment: .leading, spacing: 1) {
                Text(weekdayDateLabel(date))
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text(viewModel.timeLabel)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .monospacedDigit()
            }
            Spacer(minLength: Spacing.s2)
            HStack(spacing: Spacing.s1) {
                Icon(.check, size: 10, strokeWidth: 3, color: Theme.Color.success)
                Text("OPEN")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Theme.Color.success)
            }
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, 2)
            .background(Theme.Color.successBg)
            .clipShape(Capsule())
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    // MARK: - Summary

    private var summaryChip: some View {
        HStack(spacing: Spacing.s3) {
            Icon(.repeat2, size: 15, color: Theme.Color.primary600)
            VStack(alignment: .leading, spacing: 1) {
                Text("\(viewModel.count) sessions · \(viewModel.weekdayName) \(viewModel.timeLabel)")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Theme.Color.primary700)
                Text(summarySubtitle)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .monospacedDigit()
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.primary50)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.primary100, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private var summarySubtitle: String {
        var parts: [String] = []
        if let range = viewModel.rangeLabel { parts.append(range) }
        if let total = viewModel.totalPriceLabel { parts.append("\(total) total") }
        return parts.joined(separator: " · ")
    }

    // MARK: - Booked

    private func booked(count: Int) -> some View {
        VStack(spacing: Spacing.s4) {
            Spacer(minLength: 0)
            EdgeIconHalo(icon: .checkCircle, tone: .success, size: 84)
            VStack(spacing: Spacing.s2) {
                Text("Your series is booked")
                    .font(.system(size: 19, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text("\(count) \(count == 1 ? "session" : "sessions") confirmed. We've sent the details along.")
                    .font(.system(size: 12.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 250)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Spacing.s5)
        .safeAreaInset(edge: .bottom) {
            EdgeDock { PrimaryButton(title: "Done") { dismiss() } }
        }
    }

    // MARK: - Date labels

    private func monthLabel(_ date: Date) -> String { formatted(date, template: "MMM").uppercased() }
    private func dayLabel(_ date: Date) -> String { formatted(date, template: "d") }
    private func weekdayDateLabel(_ date: Date) -> String { formatted(date, template: "EEEMMMd") }

    private func formatted(_ date: Date, template: String) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.timeZone = .current
        formatter.setLocalizedDateFormatFromTemplate(template)
        return formatter.string(from: date)
    }
}

#if DEBUG
#Preview("Configuring") {
    NavigationStack { RecurringSetupView(viewModel: .previewConfiguring()) }
}
#endif
