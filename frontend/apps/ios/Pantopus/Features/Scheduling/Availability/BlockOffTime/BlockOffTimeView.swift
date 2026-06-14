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
                .font(.system(size: 16))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityIdentifier("scheduling.blockOff.reasonField")
            Text("Optional — only you can see this.")
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
                Text("All day").pantopusTextStyle(.body).foregroundStyle(Theme.Color.appText)
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
        }
    }

    private var footnote: some View {
        Text("This time won't be offered for booking. It's private to you.")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.s4)
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
