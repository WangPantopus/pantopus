//
//  DisambiguateMailFormView.swift
//  Pantopus
//
//  P19 FrameDisambiguate — scanned-envelope preview, confidence pill,
//  three drawer-recipient radio rows, optional notes, sticky bottom
//  Confirm CTA.
//
// swiftlint:disable multiple_closures_with_trailing_closure

import SwiftUI

@MainActor
struct DisambiguateMailFormView: View {
    @State private var viewModel: DisambiguateMailFormViewModel
    private let onClose: @MainActor () -> Void

    init(
        mailId: String,
        ocrRecipient: String = "",
        confidence: Double = 0.0,
        envelopeImageURL: URL? = nil,
        api: APIClient = .shared,
        onClose: @escaping @MainActor () -> Void
    ) {
        _viewModel = State(initialValue: DisambiguateMailFormViewModel(
            mailId: mailId,
            ocrRecipient: ocrRecipient,
            confidence: confidence,
            envelopeImageURL: envelopeImageURL,
            api: api
        ))
        self.onClose = onClose
    }

    var body: some View {
        VStack(spacing: Spacing.s0) {
            FormShell(
                title: "Who is this for?",
                rightActionLabel: "",
                isValid: false, // top-bar action is intentionally disabled — sticky CTA owns submit
                isDirty: viewModel.isDirty, // drives discard-confirm on close
                isSaving: false,
                onClose: onClose,
                onCommit: {}
            ) {
                envelopeCard
                    .padding(.horizontal, Spacing.s4)

                FormFieldGroup("Possible recipients") {
                    ForEach(MailRecipientChoice.allCases) { choice in
                        RecipientRow(
                            choice: choice,
                            isSelected: viewModel.selectedChoice == choice
                        ) { viewModel.select(choice) }
                            .accessibilityIdentifier("disambiguateRow_\(choice.rawValue)")
                    }
                }

                FormFieldGroup("Anything else?") {
                    AliasNotesField(
                        text: Binding(
                            get: { viewModel.aliasNotes },
                            set: { viewModel.aliasNotes = $0 }
                        ),
                        error: viewModel.aliasError
                    )
                }

                Color.clear.frame(height: 96) // pad for sticky CTA
            }
            stickyCTA
        }
        .background(Theme.Color.appBg)
        .overlay(alignment: .bottom) { toastOverlay }
        .onChange(of: viewModel.shouldDismiss) { _, dismiss in
            if dismiss {
                viewModel.acknowledgeDismiss()
                onClose()
            }
        }
    }

    private var envelopeCard: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            envelopeImage
            HStack(alignment: .top, spacing: Spacing.s2) {
                Text(viewModel.ocrRecipient.isEmpty ? "—" : viewModel.ocrRecipient)
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .lineLimit(3)
                Spacer(minLength: Spacing.s0)
                ConfidencePill(confidence: viewModel.confidence)
            }
        }
    }

    private var envelopeImage: some View {
        Group {
            if let url = viewModel.envelopeImageURL {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case let .success(image):
                        image.resizable().scaledToFill()
                    case .failure:
                        envelopePlaceholder
                    case .empty:
                        Shimmer(height: 200, cornerRadius: Radii.lg)
                    @unknown default:
                        envelopePlaceholder
                    }
                }
            } else {
                envelopePlaceholder
            }
        }
        .frame(maxWidth: .infinity)
        .aspectRatio(16.0 / 9.0, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .accessibilityLabel("Scanned envelope")
    }

    private var envelopePlaceholder: some View {
        ZStack {
            Theme.Color.appSurfaceSunken
            VStack(spacing: Spacing.s2) {
                Icon(.mailbox, size: 28, color: Theme.Color.appTextMuted)
                Text("Envelope preview unavailable")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }

    private var stickyCTA: some View {
        VStack(spacing: Spacing.s0) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            PrimaryButton(
                title: "Confirm recipient",
                isLoading: viewModel.isSubmitting,
                isEnabled: viewModel.canSubmit
            ) { await viewModel.submit() }
                .padding(Spacing.s4)
        }
        .background(Theme.Color.appSurface)
    }

    @ViewBuilder private var toastOverlay: some View {
        if let toast = viewModel.toast {
            ToastView(message: toast)
                .padding(.bottom, Spacing.s12)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .task(id: toast) {
                    try? await Task.sleep(nanoseconds: 2_500_000_000)
                    viewModel.toast = nil
                }
        }
    }
}

// MARK: - Sub-views

private struct RecipientRow: View {
    let choice: MailRecipientChoice
    let isSelected: Bool
    let onTap: @MainActor () -> Void

    var body: some View {
        Button(action: { onTap() }) {
            HStack(spacing: Spacing.s3) {
                AvatarWithIdentityRing(
                    name: choice.title,
                    identity: choice.identity,
                    ringProgress: 1,
                    size: 36
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text(choice.title)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    Text(choice.subtitle)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer()
                radioMark
            }
            .padding(.vertical, Spacing.s2)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(choice.title), \(choice.subtitle)")
        .accessibilityValue(isSelected ? "Selected" : "Not selected")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }

    private var radioMark: some View {
        ZStack {
            Circle()
                .stroke(
                    isSelected ? Theme.Color.primary600 : Theme.Color.appBorderStrong,
                    lineWidth: isSelected ? 6 : 2
                )
                .frame(width: 22, height: 22)
            if isSelected {
                Circle().fill(Theme.Color.appSurface).frame(width: 8, height: 8)
            }
        }
    }
}

private struct ConfidencePill: View {
    let confidence: Double

    private var percent: Int {
        Int((confidence * 100).rounded())
    }

    private var palette: (background: Color, foreground: Color) {
        switch confidence {
        case ..<0.5: (Theme.Color.errorBg, Theme.Color.error)
        case ..<0.8: (Theme.Color.warningBg, Theme.Color.warning)
        default: (Theme.Color.successBg, Theme.Color.success)
        }
    }

    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(.info, size: 12, color: palette.foreground)
            Text("AI confidence: \(percent)%")
                .pantopusTextStyle(.caption)
                .foregroundStyle(palette.foreground)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, Spacing.s1)
        .background(palette.background)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
        .accessibilityLabel("AI confidence \(percent) percent")
    }
}

private struct AliasNotesField: View {
    @Binding var text: String
    let error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text("Notes")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            TextField("Add a name to remember this routing", text: $text, axis: .vertical)
                .lineLimit(3...6)
                .font(Theme.Font.body)
                .padding(Spacing.s3)
                .frame(minHeight: 80, alignment: .topLeading)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md))
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md)
                        .stroke(error == nil ? Theme.Color.appBorder : Theme.Color.error, lineWidth: 1)
                )
                .accessibilityIdentifier("disambiguateAliasField")
            HStack {
                if let error {
                    Text(error)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.error)
                }
                Spacer()
                Text("\(text.count) / 255")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(text.count > 255 ? Theme.Color.error : Theme.Color.appTextSecondary)
            }
        }
    }
}

#Preview {
    DisambiguateMailFormView(
        mailId: "m-preview",
        ocrRecipient: "MS. ALEX RIVERA\n140 MAIN ST APT 4\nCAMBRIDGE MA 02139",
        confidence: 0.85,
        envelopeImageURL: nil
    ) {}
}
