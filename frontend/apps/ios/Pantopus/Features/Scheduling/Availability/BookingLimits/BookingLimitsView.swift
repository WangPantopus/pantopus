//
//  BookingLimitsView.swift
//  Pantopus
//
//  Stream I3 — B7 Booking Limits & Notice Rules (sheet). One white card per
//  rule: a label, a one-line caption explaining the effect, and a numeric
//  stepper with a unit suffix (or a segmented control). The window-shorter-
//  than-notice conflict outlines in error red and disables Done.
//

import SwiftUI

struct BookingLimitsView: View {
    @State private var viewModel: BookingLimitsViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: BookingLimitsViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        content
            .background(Theme.Color.appBg)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .task { await viewModel.load() }
            .accessibilityIdentifier("scheduling.bookingLimits")
            .alert("Couldn't save", isPresented: saveErrorPresented) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.saveError ?? "")
            }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.phase {
        case .loading:
            loadingSkeleton
        case let .error(message):
            ErrorState(headline: "Couldn't load limits", message: message) {
                await viewModel.reload()
            }
        case .ready:
            form
        }
    }

    private var form: some View {
        FormShell(
            title: "Booking limits",
            leading: .close,
            rightActionLabel: "Done",
            isValid: viewModel.isValid,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSaving,
            onClose: { dismiss() },
            onCommit: { Task { if await viewModel.save() { dismiss() } } }
        ) {
            helper
            noticeGroup
            windowGroup
            dailyCapGroup
            perPersonGroup
            startTimesGroup
        }
    }

    private var helper: some View {
        Text("Sensible defaults are set, so you usually don't need to touch these.")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.s4)
    }

    private var noticeGroup: some View {
        ruleCard("Minimum notice", caption: "Can't be booked inside this window.") {
            Stepper(value: $viewModel.minNoticeHours, in: 0...168) {
                valueLabel("\(viewModel.minNoticeHours) \(viewModel.minNoticeHours == 1 ? "hour" : "hours")")
            }
            .accessibilityIdentifier("scheduling.bookingLimits.minNoticeStepper")
        }
    }

    private var windowGroup: some View {
        ruleCard(
            "Book up to",
            caption: "How far ahead people can book.",
            error: viewModel.windowConflict
                ? "Your booking window is shorter than your minimum notice, so no times will show."
                : nil
        ) {
            Stepper(value: $viewModel.horizonDays, in: 1...730) {
                valueLabel("\(viewModel.horizonDays) days")
            }
            .accessibilityIdentifier("scheduling.bookingLimits.horizonStepper")
        }
    }

    private var dailyCapGroup: some View {
        ruleCard("Max per day", caption: "Most bookings you'll take in a day.") {
            Stepper(value: $viewModel.dailyCap, in: 1...50) {
                valueLabel("\(viewModel.dailyCap)")
            }
            .accessibilityIdentifier("scheduling.bookingLimits.dailyCapStepper")
        }
    }

    private var perPersonGroup: some View {
        ruleCard("Per-person limit", caption: "How many one person can hold at once.") {
            Stepper(value: $viewModel.perBookerCap, in: 1...20) {
                valueLabel("\(viewModel.perBookerCap) \(viewModel.perBookerCap == 1 ? "booking" : "bookings")")
            }
            .accessibilityIdentifier("scheduling.bookingLimits.perPersonStepper")
        }
    }

    private var startTimesGroup: some View {
        ruleCard("Start times", caption: "Where bookings can start within the hour.") {
            Picker("Start times", selection: $viewModel.slotInterval) {
                ForEach(SlotInterval.allCases) { interval in
                    Text(interval.label).tag(interval)
                }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("scheduling.bookingLimits.slotInterval")
        }
    }

    // MARK: Building blocks

    private func ruleCard(
        _ label: String,
        caption: String,
        error: String? = nil,
        @ViewBuilder control: () -> some View
    ) -> some View {
        FormFieldGroup(label) {
            control()
            if let error {
                HStack(alignment: .top, spacing: Spacing.s1) {
                    Icon(.circleAlert, size: 12, strokeWidth: 2, color: Theme.Color.error)
                    Text(error)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.error)
                }
            } else {
                Text(caption)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }

    private func valueLabel(_ value: String) -> some View {
        Text(value)
            .pantopusTextStyle(.body)
            .foregroundStyle(Theme.Color.appText)
    }

    private var loadingSkeleton: some View {
        ScrollView {
            VStack(spacing: Spacing.s5) {
                ForEach(0..<5, id: \.self) { _ in
                    Shimmer(height: 64, cornerRadius: Radii.lg)
                        .padding(.horizontal, Spacing.s4)
                }
            }
            .padding(.vertical, Spacing.s4)
        }
    }

    private var saveErrorPresented: Binding<Bool> {
        Binding(get: { viewModel.saveError != nil }, set: { if !$0 { viewModel.saveError = nil } })
    }
}
