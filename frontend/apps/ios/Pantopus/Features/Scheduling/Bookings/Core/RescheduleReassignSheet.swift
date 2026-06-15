//
//  RescheduleReassignSheet.swift
//  Pantopus
//
//  E4 Reschedule / Reassign Sheet (Stream I8). Wraps the Foundation `SlotPicker`
//  for the host: a strikethrough current-time → new-time header, the tz-aware
//  picker, an apply-mode toggle (propose vs reschedule now), a notify row, and —
//  for home/business owners — a reassign-to-an-available-teammate option. A 409
//  `SLOT_CONFLICT` recovers through the Foundation `SlotTakenSheet`. The
//  view-model lives in `RescheduleReassignViewModel.swift`.
//

import SwiftUI

struct RescheduleReassignSheet: View {
    @State private var viewModel: RescheduleReassignViewModel
    @State private var showTimezone = false
    let onCompleted: () async -> Void

    init(viewModel: RescheduleReassignViewModel, onCompleted: @escaping () async -> Void) {
        _viewModel = State(wrappedValue: viewModel)
        self.onCompleted = onCompleted
    }

    var body: some View {
        @Bindable var viewModel = viewModel
        return Group {
            if viewModel.proposalSent {
                proposalSentView
            } else {
                pickerView
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .background(Theme.Color.appSurface)
        .task { await viewModel.load() }
        .sheet(isPresented: $showTimezone) {
            TimezoneSelectorSheet(
                selectedIdentifier: viewModel.timezoneId,
                accent: viewModel.accent,
                onSelect: { id in Task { await viewModel.changeTimezone(id) } },
                onDone: { showTimezone = false }
            )
        }
        .sheet(isPresented: $viewModel.showSlotTaken) {
            SlotTakenSheet(
                mode: viewModel.conflictAlternatives.isEmpty ? .fullyBooked : .alternatives,
                alternatives: viewModel.conflictAlternatives,
                takenTimeLabel: viewModel.newTimeLabel,
                timeZoneIdentifier: viewModel.timezoneId,
                accent: viewModel.accent,
                onSelect: { alt in Task { await viewModel.chooseAlternative(alt) } },
                onPickAnotherTime: { viewModel.dismissSlotTaken() }
            )
            .presentationDetents([.medium, .large])
        }
        .accessibilityIdentifier("scheduling.rescheduleSheet")
    }

    // MARK: - Picker

    private var pickerView: some View {
        VStack(spacing: Spacing.s0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s4) {
                    SheetTitle(text: viewModel.canReassign ? "Reschedule & reassign" : "Pick a new time")
                    currentToNew
                    SlotPicker(
                        state: viewModel.slotPickerState,
                        slots: viewModel.daySlots,
                        timeZoneIdentifier: viewModel.timezoneId,
                        timeZoneLabel: viewModel.timezoneLabel,
                        accent: viewModel.accent,
                        monthAnchor: viewModel.monthAnchor,
                        selectedDate: viewModel.selectedDate,
                        availableDays: viewModel.availableDays,
                        selectedSlotStart: viewModel.selectedSlot?.start,
                        onSelectDate: { viewModel.selectDate($0) },
                        onSelectSlot: { viewModel.selectSlot($0) },
                        onChangeMonth: { delta in Task { await viewModel.changeMonth(delta) } },
                        onTapTimeZone: { showTimezone = true },
                        onJumpNextAvailable: { Task { await viewModel.jumpNextAvailable() } }
                    )
                    authorityToggle
                    if viewModel.canReassign { reassignRow }
                    notifyRow
                    if let error = viewModel.error { inlineError(error) }
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.top, Spacing.s4)
                .padding(.bottom, Spacing.s4)
            }
            footer
        }
    }

    private var currentToNew: some View {
        VStack(spacing: Spacing.s1) {
            HStack(spacing: Spacing.s2) {
                Icon(.calendar, size: 16, color: Theme.Color.appTextMuted)
                Text(viewModel.currentTimeLabel)
                    .font(.system(size: 12.5))
                    .strikethrough()
                    .foregroundStyle(Theme.Color.appTextMuted)
                Spacer(minLength: Spacing.s0)
            }
            .padding(.horizontal, Spacing.s3)
            .frame(minHeight: 40)
            .background(Theme.Color.appSurfaceSunken)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))

            Icon(.arrowDown, size: 16, color: Theme.Color.appTextMuted)

            HStack(spacing: Spacing.s2) {
                Icon(.calendarClock, size: 16, color: viewModel.newTimeLabel == nil ? Theme.Color.appTextMuted : viewModel.accent)
                Text(viewModel.newTimeLabel ?? "New time")
                    .font(.system(size: 12.5, weight: viewModel.newTimeLabel == nil ? .regular : .bold))
                    .foregroundStyle(viewModel.newTimeLabel == nil ? Theme.Color.appTextMuted : Theme.Color.appText)
                Spacer(minLength: Spacing.s0)
            }
            .padding(.horizontal, Spacing.s3)
            .frame(minHeight: 40)
            .background(viewModel.newTimeLabel == nil ? Theme.Color.appSurface : viewModel.owner.theme.accentBg)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .strokeBorder(
                        viewModel.newTimeLabel == nil ? Theme.Color.appBorderStrong : viewModel.accent,
                        style: StrokeStyle(lineWidth: 1.5, dash: viewModel.newTimeLabel == nil ? [4, 4] : [])
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
    }

    private var authorityToggle: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("How to apply")
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.appTextMuted)
            HStack(spacing: 3) {
                ForEach(RescheduleMode.allCases, id: \.self) { option in
                    let isOn = viewModel.mode == option
                    Button { viewModel.mode = option } label: {
                        Text(option.label)
                            .font(.system(size: 11, weight: isOn ? .bold : .semibold))
                            .foregroundStyle(isOn ? viewModel.accent : Theme.Color.appTextMuted)
                            .frame(maxWidth: .infinity, minHeight: 38)
                            .background {
                                if isOn {
                                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                                        .fill(Theme.Color.appSurface)
                                        .pantopusShadow(.sm)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(3)
            .background(Theme.Color.appSurfaceSunken)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            Text(viewModel.mode == .propose
                ? "Propose sends the new time for the invitee to accept."
                : "Reschedule moves the booking immediately and notifies the invitee.")
                .font(.system(size: 10))
                .foregroundStyle(Theme.Color.appTextMuted)
        }
    }

    private var reassignRow: some View {
        @Bindable var viewModel = viewModel
        return Toggle(isOn: $viewModel.reassign) {
            VStack(alignment: .leading, spacing: 1) {
                Text("Reassign to an available teammate")
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Text(viewModel.eligibleHostCount > 0
                    ? "\(viewModel.eligibleHostCount) free at this time"
                    : "No teammates free at the selected time")
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
        }
        .tint(viewModel.accent)
        .disabled(viewModel.eligibleHostCount == 0)
        .padding(Spacing.s3)
        .background(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
        )
        .accessibilityIdentifier("scheduling.reschedule.reassign")
    }

    private var notifyRow: some View {
        @Bindable var viewModel = viewModel
        return Toggle(isOn: $viewModel.notifyInvitee) {
            VStack(alignment: .leading, spacing: 1) {
                Text("Notify invitee")
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Text("Push + message")
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
        }
        .tint(viewModel.accent)
        .padding(Spacing.s3)
        .background(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
        )
    }

    private var footer: some View {
        SheetCTAButton(
            title: viewModel.ctaTitle,
            icon: viewModel.ctaIcon,
            tone: .accent(viewModel.accent),
            isLoading: viewModel.submitting,
            isEnabled: viewModel.canSubmit
        ) {
            if await viewModel.submit() { await onCompleted() }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s2)
        .padding(.bottom, Spacing.s5)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .top) { Rectangle().fill(Theme.Color.appBorder).frame(height: 1) }
    }

    // MARK: - Proposal sent

    private var proposalSentView: some View {
        VStack(spacing: Spacing.s4) {
            ZStack {
                Circle().fill(Theme.Color.successBg).frame(width: 72, height: 72)
                Icon(.send, size: 30, color: Theme.Color.success)
            }
            VStack(spacing: Spacing.s2) {
                Text("Proposal sent")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text(viewModel.proposalSentDetail)
                    .font(.system(size: 12.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .multilineTextAlignment(.center)
            }
            Text("Waiting on \(viewModel.booking.inviteeName ?? "the invitee") to accept")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .padding(.horizontal, Spacing.s4)
                .padding(.vertical, Spacing.s2)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(Capsule())
            PrimaryButton(title: "Done") { await onCompleted() }
                .padding(.top, Spacing.s2)
        }
        .padding(Spacing.s6)
        .frame(maxWidth: .infinity)
    }

    private func inlineError(_ message: String) -> some View {
        HStack(spacing: Spacing.s2) {
            Icon(.alertCircle, size: 16, color: Theme.Color.error)
            Text(message)
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundStyle(Theme.Color.error)
            Spacer(minLength: Spacing.s0)
        }
        .padding(Spacing.s3)
        .background(Theme.Color.errorBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }
}

#if DEBUG
#Preview {
    Color.clear.sheet(isPresented: .constant(true)) {
        RescheduleReassignSheet(
            viewModel: RescheduleReassignViewModel(
                owner: .business(id: "b"),
                booking: .preview(status: "confirmed", ownerType: "business"),
                actions: BookingActions(owner: .business(id: "b")),
                tz: "America/Los_Angeles"
            ),
            onCompleted: {}
        )
    }
}
#endif
