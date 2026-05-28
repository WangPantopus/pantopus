//
//  TransferOwnershipView.swift
//  Pantopus
//
//  A13.4 — Transfer Ownership form. Built on the shared `FormShell` with
//  a bespoke sticky bottom CTA, a custom 1–60% slider with preset chips,
//  the before/after `SplitDiff`, a typed-confirmation field, and a
//  Face ID bottom-sheet confirmation step.
//
// swiftlint:disable file_length

import SwiftUI

public struct TransferOwnershipView: View {
    @State private var viewModel: TransferOwnershipViewModel
    @Environment(\.dismiss) private var dismiss

    public init(viewModel: TransferOwnershipViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            FormShell(
                title: "Transfer ownership",
                leading: .back,
                rightActionLabel: nil,
                isValid: viewModel.isReadyToCommit,
                isDirty: viewModel.isDirty,
                isSaving: false,
                onClose: { dismiss() },
                onCommit: viewModel.presentConfirmSheet,
                content: { TransferOwnershipContent(viewModel: viewModel) },
                stickyBottom: { AnyView(stickyCTA) }
            )
            .accessibilityIdentifier("transferOwnershipForm")
            .toolbar(.hidden, for: .tabBar)

            if viewModel.sheetPhase != .hidden {
                sheetOverlay
                    .transition(.opacity)
            }
        }
        .pantopusAnimation(.componentState, value: viewModel.sheetPhase)
        .overlay(alignment: .bottom) {
            if let toast = viewModel.toast {
                ToastView(message: toast)
                    .padding(.bottom, Spacing.s12)
                    .task {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        viewModel.toast = nil
                    }
                    .transition(.opacity)
                    .accessibilityIdentifier("transferOwnershipToast")
            }
        }
        .pantopusAnimation(.componentState, value: viewModel.toast)
        .onChange(of: viewModel.shouldDismiss) { _, shouldDismiss in
            guard shouldDismiss else { return }
            viewModel.acknowledgeDismiss()
            Task {
                try? await Task.sleep(nanoseconds: 800_000_000)
                dismiss()
            }
        }
    }

    private var stickyCTA: some View {
        VStack(spacing: Spacing.s1 + 2) {
            Button(action: viewModel.presentConfirmSheet) {
                HStack(spacing: Spacing.s2) {
                    Icon(.arrowRightLeft, size: 17, color: Theme.Color.appTextInverse)
                    Text(viewModel.ctaLabel)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity, minHeight: 48)
            }
            .background(viewModel.isReadyToCommit ? Theme.Color.primary600 : Theme.Color.appBorderStrong)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .shadow(
                color: viewModel.isReadyToCommit
                    ? Theme.Color.primary600.opacity(0.28)
                    : .clear,
                radius: 8,
                y: 4
            )
            .disabled(!viewModel.isReadyToCommit)
            .accessibilityIdentifier("transferOwnershipCTA")

            HStack(spacing: Spacing.s1) {
                Icon(.lock, size: 11, color: Theme.Color.appTextSecondary)
                Text("Confirmed with \(viewModel.biometryLabel) after tap")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s3)
        .padding(.bottom, Spacing.s6 + 4)
        .frame(maxWidth: .infinity)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Theme.Color.appBorderSubtle)
                .frame(height: 1)
        }
    }

    private var sheetOverlay: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.5)
                .ignoresSafeArea()
                .onTapGesture {
                    viewModel.dismissConfirmSheet()
                }
                .accessibilityHidden(true)
            VStack(spacing: Spacing.s0) {
                if let message = viewModel.biometricErrorMessage {
                    HStack(spacing: 6) {
                        Icon(.alertCircle, size: 14, color: Theme.Color.error)
                        Text(message)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Theme.Color.error)
                    }
                    .padding(.horizontal, Spacing.s3)
                    .padding(.vertical, Spacing.s2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.Color.errorBg)
                    .accessibilityIdentifier("faceIDConfirmError")
                }
                FaceIDConfirmSheet(
                    parties: viewModel.confirmSheetParties,
                    amount: viewModel.amount,
                    recipientName: viewModel.recipient.name,
                    homeAddress: viewModel.homeContext.address,
                    coOwnerNames: viewModel.homeContext.coOwnerNames,
                    timestamp: viewModel.confirmationTimestamp,
                    biometryLabel: viewModel.biometryLabel,
                    isAuthenticating: viewModel.sheetPhase == .authenticating,
                    onCancel: viewModel.dismissConfirmSheet
                ) {
                    Task { await viewModel.authenticateAndCommit() }
                }
            }
            .background(Theme.Color.appSurface)
            .frame(maxWidth: .infinity)
            .transition(.move(edge: .bottom))
        }
        .ignoresSafeArea(edges: .bottom)
    }
}

// MARK: - Form body

private struct TransferOwnershipContent: View {
    @Bindable var viewModel: TransferOwnershipViewModel

    var body: some View {
        HomeStrip(context: viewModel.homeContext)
            .padding(.horizontal, Spacing.s4)

        FormFieldGroup("Recipient") {
            RecipientSearchField(value: viewModel.recipient.name)
            TransferRecipientCard(recipient: viewModel.recipient)
        }

        FormFieldGroup("Share to transfer · \(viewModel.amount)%") {
            SliderCard(viewModel: viewModel)
            SplitDiff(
                before: viewModel.beforeSegments,
                after: viewModel.afterSegments,
                amount: viewModel.amount,
                recipientName: viewModel.recipient.name
                    .split(separator: " ")
                    .first
                    .map(String.init) ?? viewModel.recipient.name
            )
        }

        FormFieldGroup("Confirmation") {
            ConfirmationField(viewModel: viewModel)
            WarningBlock(text: viewModel.warningCopy)
        }
    }
}

// MARK: - Home strip

private struct HomeStrip: View {
    let context: TransferOwnershipSampleData.HomeContext

    var body: some View {
        HStack(spacing: Spacing.s2 + 2) {
            ZStack {
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [Theme.Color.success, Theme.Color.homeDark],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 32, height: 32)
                Icon(.home, size: 15, color: Theme.Color.appTextInverse)
            }
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: Spacing.s1) {
                    Text(context.title)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                        .lineLimit(1)
                    Text("· \(context.since)")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
                Text("You hold \(context.yourStake)% · \(context.coOwnerNames)")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s0)
            HStack(spacing: 3) {
                Icon(.alertTriangle, size: 9, color: Theme.Color.warning)
                Text("IRREVERSIBLE")
                    .font(.system(size: 9.5, weight: .bold))
                    .tracking(0.6)
                    .foregroundStyle(Theme.Color.warning)
            }
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(Theme.Color.warningLight.opacity(0.7))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.xs, style: .continuous)
                    .stroke(Theme.Color.warningLight, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.xs, style: .continuous))
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s2 + 2)
        .background(Theme.Color.appSurfaceRaised)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md + 2, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md + 2, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(context.address), you hold \(context.yourStake)%, transfer is irreversible")
        .accessibilityIdentifier("transferHomeStrip")
    }
}

// MARK: - Recipient search

private struct RecipientSearchField: View {
    let value: String

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.search, size: 16, color: Theme.Color.appTextSecondary)
            Text(value.isEmpty ? "Search neighbors by name, email, or @handle" : value.lowercased())
                .font(.system(size: 14, weight: value.isEmpty ? .regular : .medium))
                .foregroundStyle(value.isEmpty ? Theme.Color.appTextMuted : Theme.Color.appText)
                .frame(maxWidth: .infinity, alignment: .leading)
                .lineLimit(1)
            if !value.isEmpty {
                ZStack {
                    Circle()
                        .fill(Theme.Color.appSurfaceSunken)
                        .frame(width: 20, height: 20)
                    Icon(.x, size: 12, color: Theme.Color.appTextSecondary)
                }
            }
        }
        .padding(.horizontal, Spacing.s3)
        .frame(height: 44)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md + 2, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md + 2, style: .continuous))
        .accessibilityIdentifier("recipientSearchField")
    }
}

// MARK: - Recipient card

private struct TransferRecipientCard: View {
    let recipient: TransferOwnershipSampleData.RecipientSeed

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(alignment: .top, spacing: Spacing.s3) {
                avatar
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(recipient.name)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                        Text("VERIFIED")
                            .font(.system(size: 10, weight: .bold))
                            .tracking(0.7)
                            .foregroundStyle(Theme.Color.success)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Theme.Color.successBg)
                            .overlay(
                                RoundedRectangle(cornerRadius: Radii.xs, style: .continuous)
                                    .stroke(Theme.Color.successLight, lineWidth: 1)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: Radii.xs, style: .continuous))
                    }
                    HStack(spacing: Spacing.s1) {
                        Icon(.atSign, size: 11, color: Theme.Color.appTextSecondary)
                        Text("\(recipient.handle) · \(recipient.email)")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: Spacing.s0)
                Text("Change")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.Color.primary600)
            }
            metaStrip
        }
        .padding(Spacing.s3 + 2)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.primary600, lineWidth: 1.5)
        )
        .background(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.primary600.opacity(0.10), lineWidth: 4)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Selected recipient \(recipient.name), verified")
        .accessibilityIdentifier("recipientCard")
    }

    private var avatar: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Theme.Color.business, Theme.Color.businessDark],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .shadow(color: Theme.Color.businessDark.opacity(0.18), radius: 6, y: 4)
            Text(recipient.initials)
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(Theme.Color.appTextInverse)
        }
        .frame(width: 48, height: 48)
        .overlay(alignment: .bottomTrailing) {
            ZStack {
                Circle()
                    .fill(Theme.Color.success)
                    .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
                Icon(.check, size: 9, strokeWidth: 4, color: Theme.Color.appTextInverse)
            }
            .frame(width: 17, height: 17)
            .offset(x: 2, y: 2)
        }
    }

    private var metaStrip: some View {
        HStack(spacing: 1) {
            MetaCell(icon: .home, label: "OWNS", value: recipient.owns)
            divider
            MetaCell(icon: .shieldCheck, label: "ON PANTOPUS", value: recipient.onPantopus)
            divider
            MetaCell(icon: .users, label: "MUTUAL", value: recipient.mutual)
        }
        .background(Theme.Color.appBorderSubtle)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }

    private var divider: some View {
        Rectangle().fill(Theme.Color.appBorderSubtle).frame(width: 1)
    }
}

private struct MetaCell: View {
    let icon: PantopusIcon
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 2) {
            HStack(spacing: Spacing.s1) {
                Icon(icon, size: 10, color: Theme.Color.appTextSecondary)
                Text(label)
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(0.4)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Text(value)
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appSurface)
    }
}

// MARK: - Slider card

private struct SliderCard: View {
    @Bindable var viewModel: TransferOwnershipViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(spacing: Spacing.s3 + 2) {
                SharesSlider(
                    value: Binding(
                        get: { viewModel.amount },
                        set: { viewModel.updateAmount($0) }
                    ),
                    range: viewModel.sliderRange,
                    ticks: viewModel.presets
                )
                .frame(maxWidth: .infinity)
                percentPill
            }
            HStack {
                Text("1%")
                    .font(.system(size: 10.5, design: .monospaced))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                Text("Max \(viewModel.maxAmount)% (your stake)")
                    .font(.system(size: 10.5, design: .monospaced))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            presetChips
        }
        .padding(Spacing.s3 + 2)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .shadow(color: .black.opacity(0.04), radius: 3, y: 1)
    }

    private var percentPill: some View {
        Text("\(viewModel.amount)%")
            .font(.system(size: 13, weight: .bold, design: .monospaced))
            .foregroundStyle(Theme.Color.primary700)
            .frame(minWidth: 44)
            .padding(.horizontal, Spacing.s2 + 2)
            .padding(.vertical, Spacing.s1)
            .background(Theme.Color.primary50)
            .clipShape(Capsule())
    }

    private var presetChips: some View {
        HStack(spacing: Spacing.s1 + 2) {
            ForEach(viewModel.presets, id: \.self) { preset in
                Button {
                    viewModel.selectPreset(preset)
                } label: {
                    Text("\(preset)%")
                        .font(.system(size: 12, weight: .semibold, design: .monospaced))
                        .foregroundStyle(preset == viewModel.amount ? Theme.Color.primary700 : Theme.Color.appTextStrong)
                        .frame(maxWidth: .infinity, minHeight: 30)
                }
                .background(preset == viewModel.amount ? Theme.Color.primary50 : Theme.Color.appSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .stroke(preset == viewModel.amount ? Theme.Color.primary100 : Theme.Color.appBorder, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                .accessibilityIdentifier("sharePreset_\(preset)")
            }
        }
    }
}

// MARK: - Confirmation field

private struct ConfirmationField: View {
    @Bindable var viewModel: TransferOwnershipViewModel
    @FocusState private var isFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: 2) {
                Text("Type ")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextStrong)
                Text(viewModel.confirmationPhrase)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(Theme.Color.appText)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1)
                    .background(Theme.Color.appSurfaceSunken)
                    .clipShape(RoundedRectangle(cornerRadius: 3, style: .continuous))
                Text(" to confirm")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextStrong)
                Text("*")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Theme.Color.error)
            }
            HStack(spacing: Spacing.s2) {
                TextField(
                    viewModel.confirmationPhrase,
                    text: Binding(
                        get: { viewModel.confirmationField.value },
                        set: { viewModel.updateConfirmation($0) }
                    )
                )
                .font(.system(size: 14, design: .monospaced))
                .autocorrectionDisabled()
                .textInputAutocapitalization(.characters)
                .focused($isFocused)
                .accessibilityIdentifier("field_confirmationPhrase")
                .accessibilityLabel("Type \(viewModel.confirmationPhrase) to confirm")
                if viewModel.confirmationMatches {
                    Icon(.check, size: 18, color: Theme.Color.success)
                }
            }
            .padding(.horizontal, Spacing.s3)
            .frame(minHeight: 44)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(
                        viewModel.confirmationMatches
                            ? Theme.Color.success
                            : (isFocused ? Theme.Color.primary600 : Theme.Color.appBorder),
                        lineWidth: isFocused || viewModel.confirmationMatches ? 2 : 1
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
    }
}

// MARK: - Warning block

private struct WarningBlock: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            Icon(.info, size: 14, color: Theme.Color.warning)
                .padding(.top, 2)
            Text(text)
                .font(.system(size: 11.5))
                .foregroundStyle(Theme.Color.warning)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s2 + 2)
        .background(Theme.Color.warningBg)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md + 2, style: .continuous)
                .stroke(Theme.Color.warningLight, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md + 2, style: .continuous))
        .accessibilityIdentifier("transferIrreversibleWarning")
    }
}

#Preview("Ready") {
    NavigationStack {
        TransferOwnershipView(
            viewModel: TransferOwnershipViewModel(
                homeId: "preview",
                biometricEvaluator: { _ in .success(()) },
                transferExecutor: {}
            )
        )
    }
}

#Preview("Confirm sheet") {
    let viewModel = TransferOwnershipViewModel(
        homeId: "preview",
        biometricEvaluator: { _ in .success(()) },
        transferExecutor: {}
    )
    viewModel.updateConfirmation(TransferOwnershipSampleData.confirmationPhrase)
    viewModel.presentConfirmSheet()
    return NavigationStack {
        TransferOwnershipView(viewModel: viewModel)
    }
}
