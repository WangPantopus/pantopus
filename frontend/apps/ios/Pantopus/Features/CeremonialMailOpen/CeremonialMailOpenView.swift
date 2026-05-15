//
//  CeremonialMailOpenView.swift
//  Pantopus
//
//  T3.8 Ceremonial Mail Open — bespoke ContentDetailShell-shaped
//  layout with a four-phase seal-break ceremony. Tapping the sealed
//  envelope animates the seal cracking, then unfurls the letter.
//

import SwiftUI

public struct CeremonialMailOpenView: View {
    @State private var viewModel: CeremonialMailOpenViewModel
    @State private var sealRotation: Double = 0
    @State private var sealScale: Double = 1
    @State private var paperOffset: CGFloat = 24
    @State private var paperOpacity: Double = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let onBack: @MainActor () -> Void
    private let onWriteBack: @MainActor (String) -> Void
    private let onOutcome: @MainActor (CeremonialOutcomeCTA) -> Void

    public init(
        viewModel: CeremonialMailOpenViewModel,
        onBack: @escaping @MainActor () -> Void = {},
        onWriteBack: @escaping @MainActor (String) -> Void = { _ in },
        onOutcome: @escaping @MainActor (CeremonialOutcomeCTA) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
        self.onWriteBack = onWriteBack
        self.onOutcome = onOutcome
    }

    public var body: some View {
        VStack(spacing: 0) {
            topBar
            content
        }
        .background(Theme.Color.appBg)
        .task { await viewModel.load() }
        .accessibilityIdentifier("ceremonialMailOpen")
    }

    private var topBar: some View {
        HStack {
            Button(action: onBack) {
                Icon(.chevronLeft, size: 22, color: Theme.Color.appText)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("ceremonialMailOpenBackButton")
            Spacer()
            Text("Letter")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            Color.clear.frame(width: 36, height: 36)
        }
        .padding(.horizontal, 12)
        .frame(height: 52)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading: loadingFrame
        case let .error(message): errorFrame(message: message)
        case let .loaded(letter, phase):
            ZStack {
                envelopeLayer(letter: letter, phase: phase)
                paperLayer(letter: letter, phase: phase)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .onChange(of: phase) { _, new in
                animateForPhase(new)
            }
        }
    }

    // MARK: - Loading + error

    private var loadingFrame: some View {
        VStack(spacing: 12) {
            Shimmer(height: 140, cornerRadius: 16)
            Shimmer(height: 200, cornerRadius: 16)
        }
        .padding(16)
        .accessibilityIdentifier("ceremonialMailOpenLoading")
    }

    private func errorFrame(message: String) -> some View {
        VStack(spacing: 12) {
            Spacer()
            Icon(.alertCircle, size: 36, color: Theme.Color.error)
            Text("Couldn't open this letter")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .font(.system(size: 13))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button {
                Task { await viewModel.load() }
            } label: {
                Text("Try again")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, 16)
                    .frame(height: 36)
                    .background(Theme.Color.primary600)
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("ceremonialMailOpenRetry")
            Spacer()
        }
        .accessibilityIdentifier("ceremonialMailOpenError")
    }

    // MARK: - Envelope layer

    @ViewBuilder
    private func envelopeLayer(letter: CeremonialMailLetter, phase: CeremonialMailPhase) -> some View {
        if phase == .sealed || phase == .breaking {
            VStack(spacing: 14) {
                envelopeCard(letter: letter)
                if phase == .sealed {
                    sealedHint
                }
            }
            .accessibilityIdentifier("ceremonialMailEnvelope_\(phase.rawValue)")
        }
    }

    private func envelopeCard(letter: CeremonialMailLetter) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(letter.stationery.paperColor)
            sealMedallion(seal: letter.seal)
                .rotationEffect(.degrees(sealRotation))
                .scaleEffect(sealScale)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 280)
        .shadow(color: Color.black.opacity(0.18), radius: 18, y: 6)
        .overlay(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 6) {
                Text("FROM")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(letter.ink.color.opacity(0.7))
                    .kerning(0.6)
                Text(letter.sender.displayName)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(letter.ink.color)
            }
            .padding(16)
        }
        .onTapGesture {
            Task { await viewModel.startBreakingSeal() }
        }
        .accessibilityIdentifier("ceremonialMailSealCard")
        .accessibilityHint("Double-tap to open the letter.")
    }

    private func sealMedallion(seal: CeremonialMailSealTone) -> some View {
        ZStack {
            Circle()
                .fill(seal.color)
                .frame(width: 96, height: 96)
                .shadow(color: seal.color.opacity(0.4), radius: 8)
            Text("✷")
                .font(.system(size: 32, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.85))
        }
        .accessibilityIdentifier("ceremonialMailSealMedallion")
    }

    private var sealedHint: some View {
        HStack(spacing: 8) {
            Icon(.send, size: 14, color: Theme.Color.primary600)
            Text("Tap the seal to open")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.Color.primary700)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Theme.Color.primary50)
        .clipShape(Capsule())
    }

    // MARK: - Paper layer

    @ViewBuilder
    private func paperLayer(letter: CeremonialMailLetter, phase: CeremonialMailPhase) -> some View {
        if phase == .open || phase == .replying {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s4) {
                    senderCard(letter.sender)
                    paperBody(letter)
                    voicePostscriptCard(letter: letter)
                    if phase == .replying {
                        writeBackBanner(letter)
                    }
                    outcomeRow(letter)
                }
                .padding(.vertical, 16)
            }
            .opacity(paperOpacity)
            .offset(y: paperOffset)
            .accessibilityIdentifier("ceremonialMailOpenContent")
        }
    }

    private func senderCard(_ sender: CeremonialSenderCard) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(Theme.Color.primary50).frame(width: 44, height: 44)
                Text(String(sender.displayName.prefix(1)).uppercased())
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Theme.Color.primary700)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(sender.displayName)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                if let handle = sender.handle {
                    Text("@\(handle)")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            Spacer()
            if let trust = sender.trustLabel {
                Text(trust.uppercased())
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(Theme.Color.success)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Theme.Color.successBg)
                    .clipShape(Capsule())
            }
        }
        .padding(12)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityIdentifier("ceremonialMailSenderCard")
    }

    private func paperBody(_ letter: CeremonialMailLetter) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(letter.subject)
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(letter.ink.color)
            ForEach(Array(letter.bodyParagraphs.enumerated()), id: \.offset) { _, paragraph in
                Text(paragraph)
                    .font(.system(size: 14))
                    .foregroundStyle(letter.ink.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(20)
        .background(letter.stationery.paperColor)
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(letter.stationery.paperShadow, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .shadow(color: letter.stationery.paperShadow, radius: 6, y: 2)
        .accessibilityIdentifier("ceremonialMailPaperBody")
    }

    @ViewBuilder
    private func voicePostscriptCard(letter: CeremonialMailLetter) -> some View {
        if let _ = letter.voicePostscriptUri {
            Button {
                viewModel.toggleVoicePlayback()
            } label: {
                HStack(spacing: 12) {
                    ZStack {
                        Circle().fill(Theme.Color.primary600).frame(width: 40, height: 40)
                        Icon(viewModel.isVoicePlaying ? .check : .send, size: 16, color: Theme.Color.appTextInverse)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Voice postscript")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                        Text(viewModel.isVoicePlaying ? "Playing…" : "Tap to listen")
                            .font(.system(size: 11))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    Spacer()
                }
                .padding(12)
                .background(Theme.Color.appSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("ceremonialMailVoicePostscript")
        }
    }

    private func writeBackBanner(_ letter: CeremonialMailLetter) -> some View {
        HStack(spacing: 10) {
            Icon(.send, size: 16, color: Theme.Color.primary600)
            Text("Opening compose for \(letter.sender.displayName)…")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.Color.primary700)
            Spacer()
        }
        .padding(12)
        .background(Theme.Color.primary50)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityIdentifier("ceremonialMailWriteBackBanner")
    }

    private func outcomeRow(_ letter: CeremonialMailLetter) -> some View {
        VStack(spacing: 8) {
            ForEach(letter.outcomeCtas) { cta in
                Button {
                    if cta.id == "write_back" {
                        viewModel.enterReplying()
                        onWriteBack(letter.sender.displayName)
                    }
                    onOutcome(cta)
                } label: {
                    HStack(spacing: 8) {
                        Icon(
                            cta.icon,
                            size: 14,
                            color: cta.style == .primary
                                ? Theme.Color.appTextInverse
                                : Theme.Color.primary600
                        )
                        Text(cta.label)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(
                                cta.style == .primary
                                    ? Theme.Color.appTextInverse
                                    : Theme.Color.appTextStrong
                            )
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(
                        cta.style == .primary
                            ? Theme.Color.primary600
                            : Theme.Color.appSurface
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .stroke(
                                cta.style == .primary
                                    ? Theme.Color.primary600
                                    : Theme.Color.appBorder,
                                lineWidth: 1
                            )
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("ceremonialMailOutcome_\(cta.id)")
            }
        }
    }

    // MARK: - Animation

    private func animateForPhase(_ phase: CeremonialMailPhase) {
        if reduceMotion {
            sealRotation = phase == .breaking ? 25 : 0
            sealScale = phase == .breaking ? 0 : 1
            paperOpacity = phase == .open || phase == .replying ? 1 : 0
            paperOffset = phase == .open || phase == .replying ? 0 : 24
            return
        }
        switch phase {
        case .sealed:
            withAnimation(.easeInOut(duration: 0.25)) {
                sealRotation = 0
                sealScale = 1
                paperOpacity = 0
                paperOffset = 24
            }
        case .breaking:
            withAnimation(.easeIn(duration: 0.55)) {
                sealRotation = 35
                sealScale = 0.05
            }
        case .open, .replying:
            withAnimation(.easeOut(duration: 0.45).delay(0.05)) {
                paperOpacity = 1
                paperOffset = 0
            }
        }
    }
}

#Preview {
    CeremonialMailOpenView(viewModel: CeremonialMailOpenViewModel(mailId: "preview"))
}
