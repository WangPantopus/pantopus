//
//  BlockOffTimeView.swift
//  Pantopus
//
//  Stream I3 — B9 Block Off Time / Personal busy override (sheet). A short
//  create form: optional private reason, a date, an all-day toggle or a
//  time-range, and a repeat rule. Saving writes a personal busy hold.
//

import SwiftUI

struct BlockOffTimeView: View {
    @State private var viewModel: BlockOffTimeViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: BlockOffTimeViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        FormShell(
            title: "Block off time",
            subtitle: "Personal · Availability",
            leading: .close,
            rightActionLabel: "Save",
            isValid: viewModel.isValid,
            isDirty: true,
            isSaving: viewModel.isSaving,
            onClose: { dismiss() },
            onCommit: { Task { if await viewModel.save() { dismiss() } } }
        ) {
            reasonGroup
            whenGroup
            if let conflict = viewModel.conflict {
                conflictCard(conflict)
            }
            repeatsGroup
            footnote
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .accessibilityIdentifier("scheduling.blockOffTime")
        .alert("Couldn't save", isPresented: saveErrorPresented) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.saveError ?? "")
        }
    }

    private var reasonGroup: some View {
        FormFieldGroup("Reason") {
            TextField("Dentist", text: $viewModel.reason)
                .font(Theme.Font.body)
                .foregroundStyle(Theme.Color.appText)
                .accessibilityIdentifier("scheduling.blockOff.reasonField")
            Text("Optional · only you can see this.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }

    private var whenGroup: some View {
        FormFieldGroup("When") {
            DatePicker("Date", selection: $viewModel.date, displayedComponents: .date)
                .tint(Theme.Color.primary600)
            Divider().background(Theme.Color.appBorderSubtle)
            Toggle(isOn: $viewModel.allDay) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("All day").pantopusTextStyle(.body).foregroundStyle(Theme.Color.appText)
                    Text("Block the whole day")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            .tint(Theme.Color.primary600)
            if !viewModel.allDay {
                HStack(spacing: Spacing.s2) {
                    DatePicker("Start", selection: startBinding, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                        .accessibilityLabel("Start time")
                    Text("–").foregroundStyle(Theme.Color.appTextMuted)
                    DatePicker("End", selection: endBinding, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                        .accessibilityLabel("End time")
                    Spacer()
                }
                if !viewModel.isValid {
                    Text("End must be after start.")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.error)
                }
            }
        }
    }

    private var repeatsGroup: some View {
        FormFieldGroup("Repeats") {
            Picker("Repeats", selection: $viewModel.repeats) {
                ForEach(BlockRepeat.allCases) { option in
                    Text(option.label).tag(option)
                }
            }
            .pickerStyle(.segmented)
            if let caption = viewModel.repeats.caption {
                Text(caption)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }

    private var footnote: some View {
        HStack(alignment: .top, spacing: Spacing.s1) {
            Icon(.lock, size: 12, strokeWidth: 2, color: Theme.Color.appTextMuted)
                .padding(.top, 1)
            Text("This time won't be offered for booking. It's private to you.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.s4)
    }

    // ─── Conflict warning card (chip-led, semantic) ──────────────
    // Mirrors block-time-frames.jsx · ConflictCard: warningBg surface,
    // "Booking overlap" warning chip, body copy, and a "View booking" link.
    private func conflictCard(_ conflict: BlockConflictWarning) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s1) {
                Icon(.triangleAlert, size: 10, strokeWidth: 2.6, color: Theme.Color.appSurface)
                Text("Booking overlap")
                    .font(.system(size: 9, weight: .bold))
                    .textCase(.uppercase)
                    .tracking(0.4)
                    .foregroundStyle(Theme.Color.appSurface)
            }
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, 3)
            .background(Theme.Color.warning)
            .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
            Text("This overlaps a \(conflict.bookingLabel). Blocking won't cancel it.")
                .font(.system(size: 12))
                .foregroundStyle(Theme.Color.appText)
                .fixedSize(horizontal: false, vertical: true)
            Button {
                viewModel.viewConflictingBooking()
            } label: {
                HStack(spacing: 5) {
                    Icon(.arrowUpRight, size: 13, strokeWidth: 2, color: Theme.Color.warning)
                    Text("View booking")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Theme.Color.warning)
                }
            }
            .accessibilityIdentifier("scheduling.blockOff.viewBooking")
        }
        .padding(.horizontal, 13)
        .padding(.vertical, Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.warningBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.warningLight, lineWidth: 1)
        )
        .padding(.horizontal, Spacing.s4)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("scheduling.blockOff.conflictWarning")
    }

    private var startBinding: Binding<Date> {
        Binding(
            get: { viewModel.startTime.referenceDate() },
            set: { viewModel.startTime = TimeOfDay(from: $0) }
        )
    }

    private var endBinding: Binding<Date> {
        Binding(
            get: { viewModel.endTime.referenceDate() },
            set: { viewModel.endTime = TimeOfDay(from: $0) }
        )
    }

    private var saveErrorPresented: Binding<Bool> {
        Binding(get: { viewModel.saveError != nil }, set: { if !$0 { viewModel.saveError = nil } })
    }
}
