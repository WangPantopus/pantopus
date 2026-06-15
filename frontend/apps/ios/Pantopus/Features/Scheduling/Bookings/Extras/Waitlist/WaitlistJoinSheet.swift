//
//  WaitlistJoinSheet.swift
//  Pantopus
//
//  Stream I9 — E13 Waitlist (invitee join). A host-branded bottom sheet shown
//  when an event type / slot is full: a "Fully booked" pill, the requested
//  window, contact fields, and a "Join waitlist" CTA that swaps to a joined
//  confirmation. Presented by the public booking flow (I5/I7) when full.
//  Accent follows the host's pillar.
//

import SwiftUI

struct WaitlistJoinSheet: View {
    @State private var viewModel: WaitlistJoinViewModel
    var accent: Color
    let onClose: () -> Void

    init(viewModel: WaitlistJoinViewModel, accent: Color, onClose: @escaping () -> Void) {
        _viewModel = State(wrappedValue: viewModel)
        self.accent = accent
        self.onClose = onClose
    }

    var body: some View {
        VStack(spacing: 0) {
            ExtrasSheetGrabber()
            if viewModel.didJoin {
                joinedConfirmation
            } else {
                form
                footer
            }
        }
        .background(Theme.Color.appSurface)
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .accessibilityIdentifier("scheduling.waitlistJoin")
    }

    // MARK: Form

    private var form: some View {
        @Bindable var viewModel = viewModel
        return ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                fullyBookedPill
                Text(viewModel.windowLabel)
                    .font(.system(size: 16.5, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text("Join the waitlist and we'll email you the moment a spot opens.")
                    .font(.system(size: 12.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineSpacing(2)

                tzChip

                field(label: "Your name", icon: .user, placeholder: "Full name", text: $viewModel.name)
                field(
                    label: "Email",
                    icon: .mail,
                    placeholder: "For a message when a spot opens",
                    text: $viewModel.email,
                    isEmail: true
                )
                field(label: "Preferred time", icon: nil, placeholder: "Any morning works (optional)", text: $viewModel.preferredTime)

                if let message = viewModel.errorMessage {
                    ExtrasInlineError(message: message)
                }
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s2)
            .padding(.bottom, Spacing.s3)
        }
    }

    private var fullyBookedPill: some View {
        HStack(spacing: Spacing.s2 - 2) {
            Icon(.usersRound, size: 13, color: Theme.Color.warning)
            Text("Fully booked")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Theme.Color.warning)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s1 + 1)
        .background(Theme.Color.warningBg)
        .overlay(Capsule().strokeBorder(Theme.Color.warningLight, lineWidth: 1))
        .clipShape(Capsule())
    }

    private var tzChip: some View {
        HStack(spacing: Spacing.s2 - 2) {
            Icon(.globe, size: 13, color: Theme.Color.appTextStrong)
            Text("Times in \(viewModel.timeZoneLabel)")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextStrong)
        }
        .padding(.horizontal, Spacing.s3)
        .frame(height: 28)
        .overlay(Capsule().strokeBorder(Theme.Color.appBorder, lineWidth: 1))
        .clipShape(Capsule())
    }

    private func field(
        label: String,
        icon: PantopusIcon?,
        placeholder: String,
        text: Binding<String>,
        isEmail: Bool = false
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s2 - 2) {
            ExtrasOverline(text: label)
            HStack(spacing: Spacing.s2 + 1) {
                if let icon { Icon(icon, size: 16, color: Theme.Color.appTextMuted) }
                TextField(placeholder, text: text)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.Color.appText)
                    .keyboardType(isEmail ? .emailAddress : .default)
                    .textInputAutocapitalization(isEmail ? .never : .words)
                    .autocorrectionDisabled(isEmail)
            }
            .padding(.horizontal, Spacing.s3)
            .frame(height: 44)
            .background(Theme.Color.appSurfaceSunken)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
    }

    private var footer: some View {
        ExtrasStickyFooter {
            ExtrasSolidButton(
                title: "Join waitlist",
                icon: .userPlus,
                accent: accent,
                isEnabled: viewModel.canJoin,
                isBusy: viewModel.isJoining
            ) {
                Task { await viewModel.join() }
            }
        }
    }

    // MARK: Joined

    private var joinedConfirmation: some View {
        VStack(spacing: Spacing.s4) {
            Spacer(minLength: Spacing.s8)
            ExtrasIconDisc(
                icon: .check,
                background: Theme.Color.successBg,
                foreground: Theme.Color.success,
                diameter: 74
            )
            VStack(spacing: Spacing.s2) {
                Text("You're on the waitlist")
                    .font(.system(size: 17.5, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text("We'll email you the moment a spot opens.")
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .multilineTextAlignment(.center)
            }
            Spacer()
            ExtrasGhostButton(title: "Done") { onClose() }
                .padding(.horizontal, Spacing.s4)
                .padding(.bottom, Spacing.s5)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, Spacing.s5)
    }
}

#if DEBUG
#Preview {
    Color.black.sheet(isPresented: .constant(true)) {
        WaitlistJoinSheet(
            viewModel: WaitlistJoinViewModel(
                slug: "acme-studio",
                eventTypeSlug: "group-class",
                windowLabel: "Sat, Jun 14 · 10:00 AM",
                timeZoneLabel: "Pacific Time",
                client: .shared
            ),
            accent: Theme.Color.business
        ) {}
    }
}
#endif
