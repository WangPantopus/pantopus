//
//  BookingLimitsView.swift
//  Pantopus
//
//  Stream I3 — B7 Booking Limits & Notice Rules (sheet). Numeric stepper +
//  segmented controls for the event type's notice/limit fields, with the
//  window-shorter-than-notice conflict state that disables Done.
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
            limitsGroup
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
        FormFieldGroup("Minimum notice") {
            Stepper(value: $viewModel.minNoticeHours, in: 0...168) {
                labelValue("Minimum notice", "\(viewModel.minNoticeHours) \(viewModel.minNoticeHours == 1 ? "hour" : "hours")")
            }
            .accessibilityIdentifier("scheduling.bookingLimits.minNoticeStepper")
            Text("Can't be booked inside this window.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }

    private var windowGroup: some View {
        FormFieldGroup("Booking window") {
            Stepper(value: $viewModel.horizonDays, in: 1...730) {
                labelValue("Book up to", "\(viewModel.horizonDays) days")
            }
            .accessibilityIdentifier("scheduling.bookingLimits.horizonStepper")
            if viewModel.windowConflict {
                Text("Your booking window is shorter than your minimum notice, so no times will show.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
            }
        }
    }

    private var limitsGroup: some View {
        FormFieldGroup("Limits") {
            Toggle(isOn: $viewModel.limitPerDay) {
                Text("Max per day").pantopusTextStyle(.body).foregroundStyle(Theme.Color.appText)
            }
            .tint(Theme.Color.primary600)
            .accessibilityIdentifier("scheduling.bookingLimits.dailyCapToggle")
            if viewModel.limitPerDay {
                Stepper(value: $viewModel.dailyCap, in: 1...50) {
                    labelValue("Bookings per day", "\(viewModel.dailyCap)")
                }
                .accessibilityIdentifier("scheduling.bookingLimits.dailyCapStepper")
            }
            Divider().background(Theme.Color.appBorderSubtle)
            Toggle(isOn: $viewModel.limitPerPerson) {
                Text("Per-person limit").pantopusTextStyle(.body).foregroundStyle(Theme.Color.appText)
            }
            .tint(Theme.Color.primary600)
            .accessibilityIdentifier("scheduling.bookingLimits.perPersonToggle")
            if viewModel.limitPerPerson {
                Stepper(value: $viewModel.perBookerCap, in: 1...20) {
                    labelValue("Bookings per person", "\(viewModel.perBookerCap)")
                }
                .accessibilityIdentifier("scheduling.bookingLimits.perPersonStepper")
            }
        }
    }

    private var startTimesGroup: some View {
        FormFieldGroup("Start times") {
            Picker("Start times", selection: $viewModel.slotInterval) {
                ForEach(SlotInterval.allCases) { interval in
                    Text(interval.label).tag(interval)
                }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("scheduling.bookingLimits.slotInterval")
            Text("How often a booking can start.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }

    private func labelValue(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
            Spacer()
            Text(value)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }

    private var loadingSkeleton: some View {
        ScrollView {
            VStack(spacing: Spacing.s5) {
                ForEach(0..<4, id: \.self) { _ in
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
