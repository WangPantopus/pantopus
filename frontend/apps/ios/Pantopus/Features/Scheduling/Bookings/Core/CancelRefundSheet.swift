//
//  CancelRefundSheet.swift
//  Pantopus
//
//  E5 Cancel & Refund Sheet (Stream I8). A destructive bottom sheet off the
//  inbox/detail: reason chips, an optional note to the other party, a notify
//  toggle, and — for paid bookings behind the paid flag — a refund-policy note
//  (the backend applies refund logic from the cancellation policy; the cancel
//  endpoint takes no amount, and the booking carries no price, so the concrete
//  figure / preset UI awaits the pricing surfaces). On success it surfaces
//  `refund_issued`; ALREADY_CANCELLED / PAST_DEADLINE / REFUND_FAILED handled.
//

import SwiftUI

@Observable
@MainActor
final class CancelRefundViewModel {
    let owner: SchedulingOwner
    let booking: BookingDTO
    let eventName: String?
    private let actions: BookingActions

    var reason: CancelReason?
    var otherDetail = ""
    var note = ""
    var notifyInvitee = true

    private(set) var submitting = false
    private(set) var succeeded = false
    private(set) var refundIssued: Bool?
    private(set) var refundFailed = false
    var error: String?

    init(owner: SchedulingOwner, booking: BookingDTO, eventName: String?, actions: BookingActions) {
        self.owner = owner
        self.booking = booking
        self.eventName = eventName
        self.actions = actions
    }

    /// Paid surfaces stay behind the flag + Stripe TEST mode.
    var isPaid: Bool { booking.paymentId != nil && SchedulingFeatureFlags.paidEnabled }

    /// Already-terminal bookings render the read-only summary frame.
    var alreadyCancelled: Bool {
        switch SchedulingPillStatus(backend: booking.status) {
        case .cancelled, .declined: true
        default: false
        }
    }

    var subtitle: String {
        [eventName, booking.inviteeName, BookingsTime.shortWhen(startUTC: booking.startAt)]
            .compactMap { $0 }
            .joined(separator: " · ")
    }

    var confirmTitle: String {
        if refundFailed { return "Retry refund" }
        return isPaid ? "Cancel & refund" : "Cancel booking"
    }

    var confirmIcon: PantopusIcon { refundFailed ? .refreshCw : .xCircle }

    func cancel() async {
        submitting = true
        error = nil
        refundFailed = false
        do {
            let updated = try await actions.cancel(id: booking.id, reason: composedReason())
            refundIssued = updated.refundIssued
            succeeded = true
        } catch let scheduling as SchedulingError {
            handle(scheduling)
        } catch {
            self.error = "Couldn't cancel — try again."
        }
        submitting = false
    }

    private func composedReason() -> String? {
        let detail = reason == .other ? otherDetail : nil
        let parts = [reason?.label, detail, note]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return parts.isEmpty ? nil : parts.joined(separator: " — ")
    }

    private func handle(_ scheduling: SchedulingError) {
        switch scheduling {
        case let .conflict(code, message):
            switch code {
            case "ALREADY_CANCELLED": error = "This booking was already cancelled."
            case "PAST_DEADLINE": error = "It's past the cancellation cutoff for this booking."
            case "REFUND_FAILED":
                refundFailed = true
                error = "The refund couldn't be processed — try again or contact support."
            default: error = message ?? "Couldn't cancel — try again."
            }
        default:
            error = scheduling.userMessage ?? "Couldn't cancel — try again."
        }
    }
}

struct CancelRefundSheet: View {
    @State private var viewModel: CancelRefundViewModel
    let onCompleted: () async -> Void

    init(viewModel: CancelRefundViewModel, onCompleted: @escaping () async -> Void) {
        _viewModel = State(wrappedValue: viewModel)
        self.onCompleted = onCompleted
    }

    var body: some View {
        Group {
            if viewModel.alreadyCancelled {
                terminalView
            } else if viewModel.succeeded {
                cancelledView
            } else {
                formView
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .background(Theme.Color.appSurface)
        .accessibilityIdentifier("scheduling.cancelSheet")
    }

    // MARK: - Form

    private var formView: some View {
        @Bindable var viewModel = viewModel
        return VStack(spacing: Spacing.s0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s4) {
                    VStack(alignment: .leading, spacing: Spacing.s1) {
                        Text("Cancel this booking?")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(Theme.Color.appText)
                        Text(viewModel.subtitle)
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.Color.appTextMuted)
                    }
                    reasonSection($viewModel.reason, otherDetail: $viewModel.otherDetail)
                    BookingNoteField(
                        placeholder: "Note to the other party (optional)",
                        text: $viewModel.note,
                        accessibilityID: "scheduling.cancel.note"
                    )
                    if viewModel.isPaid { refundCard }
                    notifyRow($viewModel.notifyInvitee)
                    if let error = viewModel.error { inlineError(error) }
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.top, Spacing.s4)
                .padding(.bottom, Spacing.s4)
            }
            footer
        }
    }

    private func reasonSection(_ selection: Binding<CancelReason?>, otherDetail: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("Reason")
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.appTextMuted)
            ReasonChipRow(reasons: CancelReason.allCases, label: { $0.label }, selected: selection)
            if selection.wrappedValue == .other {
                BookingNoteField(
                    placeholder: "Tell us what happened",
                    text: otherDetail,
                    accessibilityID: "scheduling.cancel.otherDetail"
                )
            }
        }
    }

    private var refundCard: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            BookingOverline(icon: .receipt, text: "Refund")
            HStack(alignment: .top, spacing: Spacing.s2) {
                Icon(.info, size: 14, color: Theme.Color.appTextSecondary)
                Text("Any refund is issued automatically to the card per your cancellation policy.")
                    .font(.system(size: 11.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
        )
    }

    private func notifyRow(_ notify: Binding<Bool>) -> some View {
        Toggle(isOn: notify) {
            Text("Notify invitee")
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
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
            title: viewModel.confirmTitle,
            icon: viewModel.confirmIcon,
            tone: .destructive,
            isLoading: viewModel.submitting,
            isEnabled: !viewModel.submitting
        ) {
            // On success the body re-renders to the cancelled confirmation
            // (which surfaces refund_issued); Done there calls onCompleted.
            await viewModel.cancel()
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s2)
        .padding(.bottom, Spacing.s5)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .top) { Rectangle().fill(Theme.Color.appBorder).frame(height: 1) }
        .accessibilityIdentifier("scheduling.cancel.submit")
    }

    // MARK: - Cancelled confirmation (surfaces refund_issued)

    private var cancelledView: some View {
        terminalScaffold(
            icon: .circleSlash,
            iconTint: Theme.Color.appTextSecondary,
            iconBg: Theme.Color.appSurfaceSunken,
            title: "Booking cancelled",
            body: refundOutcomeCopy
        )
    }

    private var refundOutcomeCopy: String {
        if viewModel.isPaid {
            if viewModel.refundIssued == true {
                return "A refund was issued to the card. The invitee has been notified."
            }
            return "No refund was due per your cancellation policy. The invitee has been notified."
        }
        return "The invitee has been notified."
    }

    // MARK: - Already cancelled (read-only)

    private var terminalView: some View {
        terminalScaffold(
            icon: .circleSlash,
            iconTint: Theme.Color.appTextSecondary,
            iconBg: Theme.Color.appSurfaceSunken,
            title: "Already cancelled",
            body: "This booking is no longer active."
        )
    }

    private func terminalScaffold(
        icon: PantopusIcon, iconTint: Color, iconBg: Color, title: String, body: String
    ) -> some View {
        VStack(spacing: Spacing.s4) {
            ZStack {
                Circle().fill(iconBg).frame(width: 64, height: 64)
                Icon(icon, size: 28, color: iconTint)
            }
            VStack(spacing: Spacing.s2) {
                Text(title)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text(body)
                    .font(.system(size: 12.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .multilineTextAlignment(.center)
            }
            PrimaryButton(title: "Done") { await onCompleted() }
                .padding(.top, Spacing.s2)
        }
        .padding(Spacing.s6)
        .frame(maxWidth: .infinity)
    }

    private func inlineError(_ message: String) -> some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            Icon(.alertCircle, size: 16, color: Theme.Color.error)
            Text(message)
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundStyle(Theme.Color.error)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: Spacing.s0)
        }
        .padding(Spacing.s3)
        .background(Theme.Color.errorBg)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .strokeBorder(Theme.Color.errorLight, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }
}

private extension CancelRefundViewModel {
    var accent: Color { owner.theme.accent }
}

#if DEBUG
#Preview("Cancel") {
    Color.clear.sheet(isPresented: .constant(true)) {
        CancelRefundSheet(
            viewModel: CancelRefundViewModel(
                owner: .personal,
                booking: .preview(status: "confirmed", ownerType: "user"),
                eventName: "30-min intro call",
                actions: BookingActions(owner: .personal)
            ),
            onCompleted: {}
        )
    }
}
#endif
