//
//  BookingFollowUpSheet.swift
//  Pantopus
//
//  Stream I9 — E7 Post-Meeting Follow-up. A bottom sheet: outcome chips that
//  swap a smart-default template, a message composer with a "Send rebook link"
//  chip, a separated private host-only note, and a push toggle. Accent follows
//  the owner context. Present via `.sheet(isPresented:)`.
//

import SwiftUI

struct BookingFollowUpSheet: View {
    @State private var viewModel: BookingFollowUpViewModel
    let onClose: () -> Void

    init(viewModel: BookingFollowUpViewModel, onClose: @escaping () -> Void) {
        _viewModel = State(wrappedValue: viewModel)
        self.onClose = onClose
    }

    private var theme: SchedulingIdentityTheme { viewModel.owner.theme }

    var body: some View {
        ZStack {
            sheetBody
            if viewModel.didSend { successOverlay }
        }
        .background(Theme.Color.appSurface)
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .accessibilityIdentifier("scheduling.followUp")
        .task(id: viewModel.didSend) {
            guard viewModel.didSend else { return }
            try? await Task.sleep(for: .seconds(1.3))
            onClose()
        }
    }

    private var sheetBody: some View {
        VStack(spacing: 0) {
            ExtrasSheetGrabber()
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s4) {
                    header
                    outcomeSection
                    composer
                    privateNote
                    if let message = viewModel.errorMessage {
                        ExtrasInlineError(message: message)
                    }
                    pushToggle
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.top, Spacing.s1)
                .padding(.bottom, Spacing.s3)
            }
            footer
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text("Follow up")
                .font(.system(size: 16.5, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(viewModel.headerSubtitle)
                .font(.system(size: 11.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }

    private var outcomeSection: some View {
        VStack(alignment: .leading, spacing: Spacing.s2 + 1) {
            ExtrasOverline(text: "Outcome")
            ExtrasFlowLayout {
                ForEach(FollowUpOutcome.allCases) { outcome in
                    ExtrasPillChip(
                        title: outcome.label,
                        isSelected: outcome == viewModel.outcome,
                        selectedForeground: theme.accent,
                        selectedBackground: theme.accentBg
                    ) {
                        viewModel.select(outcome)
                    }
                }
            }
        }
    }

    private var composer: some View {
        @Bindable var viewModel = viewModel
        return VStack(alignment: .leading, spacing: Spacing.s2) {
            ExtrasOverline(text: "Message to \(viewModel.inviteeName)")
            ExtrasMessageBox(
                text: $viewModel.message,
                placeholder: "Write a message, or pick an outcome above to start from a template.",
                minHeight: 84
            )
            ExtrasChipButton(title: "Send rebook link", icon: .link, accent: theme.accent) {
                Task { await viewModel.appendRebookLink() }
            }
            .disabled(!viewModel.canAppendRebookLink)
            .opacity(viewModel.canAppendRebookLink ? 1 : 0.5)
        }
    }

    private var privateNote: some View {
        @Bindable var viewModel = viewModel
        return VStack(alignment: .leading, spacing: Spacing.s2) {
            Divider().overlay(Theme.Color.appBorder)
                .padding(.bottom, Spacing.s1)
            HStack(spacing: Spacing.s2) {
                Icon(.lock, size: 13, color: Theme.Color.appTextMuted)
                ExtrasOverline(text: "Private note")
            }
            ExtrasMessageBox(
                text: $viewModel.privateNote,
                placeholder: "Outcome notes, next steps…",
                minHeight: 46
            )
            HStack(spacing: Spacing.s1 + 1) {
                Icon(.eyeOff, size: 11, color: Theme.Color.appTextMuted)
                Text("Only you can see this")
                    .font(.system(size: 10))
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
        }
    }

    private var pushToggle: some View {
        @Bindable var viewModel = viewModel
        return ExtrasChannelRow(
            icon: .bell,
            label: "Send via push + message",
            isOn: $viewModel.pushOn,
            accent: theme.accent
        )
    }

    @ViewBuilder private var footer: some View {
        ExtrasStickyFooter {
            if viewModel.primaryIsGhost {
                ExtrasGhostButton(title: viewModel.primaryTitle, icon: viewModel.primaryIcon) {
                    // Private note is not persisted server-side (backend gap); dismiss.
                    onClose()
                }
            } else {
                ExtrasSolidButton(
                    title: viewModel.primaryTitle,
                    icon: viewModel.primaryIcon,
                    accent: theme.accent,
                    isEnabled: viewModel.canSubmit,
                    isBusy: viewModel.isSending
                ) {
                    Task { await viewModel.send() }
                }
            }
        }
    }

    private var successOverlay: some View {
        VStack(spacing: Spacing.s4) {
            ExtrasIconDisc(
                icon: .check,
                background: Theme.Color.successBg,
                foreground: Theme.Color.success,
                diameter: 72
            )
            Text("Follow-up sent")
                .font(.system(size: 16.5, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text("Follow-up sent to \(viewModel.inviteeName)")
                .font(.system(size: 12.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Color.appSurface)
        .transition(.opacity)
    }
}

#if DEBUG
#Preview {
    Color.black.sheet(isPresented: .constant(true)) {
        BookingFollowUpSheet(
            viewModel: BookingFollowUpViewModel(
                owner: .home(homeId: "h1"),
                bookingId: "b1",
                eventTypeId: "et1",
                inviteeName: "Mara",
                headerSubtitle: "Garden walkthrough · Mara Reyes · Jun 9",
                client: .shared
            ),
            onClose: {}
        )
    }
}
#endif
