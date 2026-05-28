//
//  ReviewClaimDetailComponents.swift
//  Pantopus
//
//  P7.2 (A13.3) — reshaped review-claim primitives. Split out of the main
//  view file to stay under SwiftLint's `file_length`.
//
//    • TrustChip / ClaimSummaryTile / ClaimantCard — the claimant header
//      with a 52pt gradient avatar, a "Pending Nd" chip, the claim
//      summary tile, and a row of tone-based trust chips.
//    • EvidenceStrip — a horizontal strip of bespoke, fully-drawn
//      document previews (deed / photo / utility / signed statement).
//    • StatementBlock — the italic, quote-wrapped claim statement.
//    • VerdictBar — Accept (primary, success) on top, Challenge + Reject
//      ghosts below.
//    • ChallengeComposerSheet — the reason-chip + question composer that
//      replaced the old "Request more info" note sheet.
//    • ReviewClaimNoteCaptureSheet — kept for the Reject reason capture.
//

import SwiftUI

// MARK: - Presentation models

/// Tone palette for a `TrustChip`. Drawn entirely from `Theme.Color`.
enum TrustChipTone: Sendable {
    case success
    case warn
    case neutral
}

/// One trust signal rendered as a pill in the claimant card.
struct TrustChipModel: Identifiable, Hashable {
    let icon: PantopusIcon
    let label: String
    let tone: TrustChipTone
    var id: String { label }
}

/// Synthetic preview style for an evidence thumbnail.
enum EvidenceKind: Sendable {
    case deed
    case photo
    case utility
    case signedStatement
}

/// One evidence thumbnail in the strip.
struct EvidenceItemModel: Identifiable, Hashable {
    let id: String
    let kind: EvidenceKind
    let title: String
    let meta: String
    let badge: String?
}

/// Everything the claimant card renders. The view maps the claim DTO into
/// this; snapshots + previews construct it directly to match the design.
struct ClaimantCardModel {
    let name: String
    let email: String?
    let gradient: GradientPair
    let pendingLabel: String?
    let shareValue: String
    let shareDescriptor: String
    let trustChips: [TrustChipModel]
}

// MARK: - Trust chip

struct TrustChip: View {
    let model: TrustChipModel

    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(model.icon, size: 11, color: foreground)
            Text(model.label)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(foreground)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 3)
        .background(background)
        .clipShape(Capsule())
        .overlay(Capsule().stroke(border, lineWidth: 1))
    }

    private var foreground: Color {
        switch model.tone {
        case .success: Theme.Color.success
        case .warn: Theme.Color.warmAmber
        case .neutral: Theme.Color.appTextSecondary
        }
    }

    private var background: Color {
        switch model.tone {
        case .success: Theme.Color.successLight
        case .warn: Theme.Color.warmAmberBg
        case .neutral: Theme.Color.appSurfaceSunken
        }
    }

    private var border: Color {
        switch model.tone {
        case .success: Theme.Color.success.opacity(0.35)
        case .warn: Theme.Color.warningLight
        case .neutral: Theme.Color.appBorder
        }
    }
}

// MARK: - Claim summary tile

struct ClaimSummaryTile: View {
    let value: String
    let descriptor: String

    var body: some View {
        HStack(spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.sm)
                    .fill(Theme.Color.primary50)
                    .frame(width: 28, height: 28)
                Icon(.keyRound, size: 14, color: Theme.Color.primary600)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text("Claiming")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                HStack(alignment: .firstTextBaseline, spacing: Spacing.s1) {
                    Text(value)
                        .font(.system(size: 14, weight: .semibold, design: .monospaced))
                        .foregroundStyle(Theme.Color.primary700)
                    Text(descriptor)
                        .font(.system(size: 13.5, weight: .medium))
                        .foregroundStyle(Theme.Color.appTextStrong)
                }
            }
            Spacer(minLength: Spacing.s0)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, 10)
        .background(Theme.Color.appSurfaceMuted)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
    }
}

// MARK: - Pending chip

private struct PendingChip: View {
    let label: String

    var body: some View {
        HStack(spacing: 3) {
            Icon(.clock, size: 9, color: Theme.Color.warmAmber)
            Text(label.uppercased())
                .font(.system(size: 9.5, weight: .bold))
                .foregroundStyle(Theme.Color.warmAmber)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background(Theme.Color.warmAmberBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xs))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xs)
                .stroke(Theme.Color.warningLight, lineWidth: 1)
        )
    }
}

// MARK: - Claimant card

struct ClaimantCard: View {
    let model: ClaimantCardModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(alignment: .top, spacing: Spacing.s3) {
                avatar
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(model.name)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                            .lineLimit(1)
                        if let pending = model.pendingLabel {
                            PendingChip(label: pending)
                        }
                    }
                    if let email = model.email, !email.isEmpty {
                        HStack(spacing: Spacing.s1) {
                            Icon(.atSign, size: 11, color: Theme.Color.appTextSecondary)
                            Text(email)
                                .pantopusTextStyle(.caption)
                                .foregroundStyle(Theme.Color.appTextSecondary)
                                .lineLimit(1)
                        }
                    }
                }
                Spacer(minLength: Spacing.s0)
            }

            ClaimSummaryTile(value: model.shareValue, descriptor: model.shareDescriptor)

            if !model.trustChips.isEmpty {
                ReviewClaimFlowLayout(spacing: 6) {
                    ForEach(model.trustChips) { chip in
                        TrustChip(model: chip)
                    }
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
        .shadow(color: Theme.Color.appText.opacity(0.04), radius: 3, y: 1)
    }

    private var avatar: some View {
        ZStack {
            Circle()
                .fill(LinearGradient(
                    colors: [model.gradient.start, model.gradient.end],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ))
                .frame(width: 52, height: 52)
            Text(initials)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appTextInverse)
        }
        .shadow(color: model.gradient.end.opacity(0.18), radius: 5, y: 4)
    }

    private var initials: String {
        let parts = model.name.split(separator: " ").prefix(2)
        let letters = parts.compactMap { $0.first }.map(String.init).joined()
        return letters.isEmpty ? "?" : letters.uppercased()
    }
}

// MARK: - Evidence strip

struct EvidenceStrip: View {
    let items: [EvidenceItemModel]
    let extraCount: Int

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 10) {
                ForEach(items) { item in
                    EvidenceThumb(item: item)
                }
                if extraCount > 0 {
                    EvidenceMoreTile(count: extraCount)
                }
            }
        }
    }
}

private struct EvidenceThumb: View {
    let item: EvidenceItemModel

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: Radii.md)
                    .fill(Theme.Color.appSurfaceSunken)
                EvidencePreview(kind: item.kind)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md))
                if let badge = item.badge {
                    Text(badge.uppercased())
                        .font(.system(size: 8.5, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Theme.Color.appText.opacity(0.78))
                        .clipShape(RoundedRectangle(cornerRadius: 3))
                        .padding(5)
                }
            }
            .frame(width: 96, height: 128)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .shadow(color: Theme.Color.appText.opacity(0.06), radius: 2, y: 1)

            VStack(alignment: .leading, spacing: 1) {
                Text(item.title)
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                Text(item.meta)
                    .font(.system(size: 10))
                    .foregroundStyle(Theme.Color.appTextMuted)
                    .lineLimit(1)
            }
            .frame(width: 96, alignment: .leading)
        }
    }
}

private struct EvidenceMoreTile: View {
    let count: Int

    var body: some View {
        VStack(spacing: Spacing.s1) {
            Icon(.plus, size: 18, color: Theme.Color.appTextSecondary)
            Text("+\(count) more")
                .font(.system(size: 10.5, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .frame(width: 96, height: 128)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md)
                .stroke(style: StrokeStyle(lineWidth: 1.5, dash: [4]))
                .foregroundStyle(Theme.Color.appBorderStrong)
        )
    }
}

/// Fully-drawn synthetic document previews — no real imagery. Each kind
/// hand-draws its archetype so the strip reads as documents/photos at a
/// glance without leaking a real claimant's files into the design.
private struct EvidencePreview: View {
    let kind: EvidenceKind

    var body: some View {
        switch kind {
        case .deed: deed
        case .photo: photo
        case .utility: utility
        case .signedStatement: signedStatement
        }
    }

    // White page with title + ruled body lines + a sky "recorded" stamp.
    private var deed: some View {
        VStack(alignment: .leading, spacing: 2.5) {
            line(width: 0.6, color: Theme.Color.appTextStrong, height: 4)
            line(width: 0.85)
            line(width: 0.78)
            line(width: 0.9)
            line(width: 0.4)
            Spacer(minLength: Spacing.s0)
            RoundedRectangle(cornerRadius: 2)
                .fill(Theme.Color.primary50)
                .frame(width: 22, height: 14)
                .overlay(
                    RoundedRectangle(cornerRadius: 2)
                        .stroke(Theme.Color.primary100, lineWidth: 1)
                )
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(6)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Theme.Color.appSurface)
        .padding(Spacing.s2)
    }

    // Warm fall-sunset porch: gradient sky, dark porch, cream door, sun.
    private var photo: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            ZStack {
                LinearGradient(
                    colors: [Theme.Color.warningLight, Theme.Color.handyman, Theme.Color.warmAmber],
                    startPoint: .top,
                    endPoint: .bottom
                )
                Rectangle()
                    .fill(Theme.Color.appText)
                    .frame(width: w * 0.6, height: h * 0.32)
                    .position(x: w * 0.5, y: h * 0.82)
                Rectangle()
                    .fill(Theme.Color.paperCream)
                    .frame(width: w * 0.24, height: h * 0.2)
                    .position(x: w * 0.5, y: h * 0.83)
                Circle()
                    .fill(Theme.Color.appTextInverse)
                    .frame(width: 11, height: 11)
                    .position(x: w * 0.76, y: h * 0.2)
                    .shadow(color: Theme.Color.appTextInverse.opacity(0.7), radius: 6)
            }
        }
    }

    // Utility bill: sky logo bar, ruled lines, a faint "shimmer" line, total.
    private var utility: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                RoundedRectangle(cornerRadius: 1)
                    .fill(Theme.Color.primary600)
                    .frame(width: 16, height: 5)
                Spacer(minLength: Spacing.s0)
                RoundedRectangle(cornerRadius: 1)
                    .fill(Theme.Color.appTextMuted)
                    .frame(width: 10, height: 3)
            }
            .padding(.bottom, 2)
            line(width: 0.7)
            line(width: 0.55)
            RoundedRectangle(cornerRadius: 1)
                .fill(Theme.Color.primary100)
                .frame(width: 38, height: 2)
            Spacer(minLength: Spacing.s0)
            Text("$184.20")
                .font(.system(size: 8, weight: .bold, design: .monospaced))
                .foregroundStyle(Theme.Color.appTextStrong)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Theme.Color.appSurface)
        .padding(Spacing.s2)
    }

    // Signed statement: ruled body + a stylised handwritten signature.
    private var signedStatement: some View {
        VStack(alignment: .leading, spacing: 2.5) {
            line(width: 0.85)
            line(width: 0.7)
            line(width: 0.9)
            line(width: 0.5)
            Spacer(minLength: Spacing.s0)
            SignatureMark()
                .stroke(Theme.Color.primary700, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                .frame(height: 16)
            Rectangle()
                .fill(Theme.Color.appBorderStrong)
                .frame(height: 1)
        }
        .padding(6)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Theme.Color.appSurface)
        .padding(Spacing.s2)
    }

    private func line(width: CGFloat, color: Color = Theme.Color.appBorderStrong, height: CGFloat = 2) -> some View {
        RoundedRectangle(cornerRadius: 1)
            .fill(color)
            .frame(height: height)
            .frame(maxWidth: .infinity, alignment: .leading)
            .scaleEffect(x: width, anchor: .leading)
    }
}

/// A looping cursive-ish stroke that reads as a signature scribble.
private struct SignatureMark: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let h = rect.height
        let w = rect.width
        path.move(to: CGPoint(x: rect.minX, y: rect.maxY * 0.8))
        path.addCurve(
            to: CGPoint(x: w * 0.32, y: h * 0.1),
            control1: CGPoint(x: w * 0.05, y: h * 0.2),
            control2: CGPoint(x: w * 0.2, y: h * 0.0)
        )
        path.addCurve(
            to: CGPoint(x: w * 0.5, y: h * 0.9),
            control1: CGPoint(x: w * 0.42, y: h * 0.2),
            control2: CGPoint(x: w * 0.4, y: h * 0.95)
        )
        path.addCurve(
            to: CGPoint(x: w * 0.78, y: h * 0.25),
            control1: CGPoint(x: w * 0.62, y: h * 0.85),
            control2: CGPoint(x: w * 0.66, y: h * 0.1)
        )
        path.addLine(to: CGPoint(x: w * 0.95, y: h * 0.55))
        return path
    }
}

// MARK: - Statement block

struct StatementBlock: View {
    let statement: String
    let attribution: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("\u{201C}\(statement)\u{201D}")
                .font(.system(size: 13.5).italic())
                .foregroundStyle(Theme.Color.appText)
                .fixedSize(horizontal: false, vertical: true)
            if let attribution {
                HStack(spacing: Spacing.s1) {
                    Icon(.fileSignature, size: 10, color: Theme.Color.appTextMuted)
                    Text(attribution.uppercased())
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
            }
        }
        .padding(.leading, 18)
        .padding(.trailing, Spacing.s4)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurfaceMuted)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 2)
                .fill(Theme.Color.primary600)
                .frame(width: 3)
                .padding(.vertical, 14)
                .padding(.leading, Spacing.s2)
        }
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
    }
}

// MARK: - Verdict bar

struct VerdictBar: View {
    let reviewingAction: AdminClaimReviewAction?
    let onAccept: () -> Void
    let onChallenge: () -> Void
    let onReject: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s2) {
            VerdictButton(
                style: .accept,
                label: reviewingAction == .approve ? "Accepting…" : "Accept claim",
                icon: .checkCircle,
                isLoading: reviewingAction == .approve,
                disabled: reviewingAction != nil,
                action: onAccept
            )
            .accessibilityIdentifier("reviewClaimDetail_accept")
            .accessibilityLabel("Accept claim")

            HStack(spacing: Spacing.s2) {
                VerdictButton(
                    style: .challenge,
                    label: "Challenge",
                    icon: .messageCircle,
                    isLoading: reviewingAction == .challenge,
                    disabled: reviewingAction != nil,
                    action: onChallenge
                )
                .accessibilityIdentifier("reviewClaimDetail_challenge")
                .accessibilityLabel("Challenge claim")

                VerdictButton(
                    style: .reject,
                    label: "Reject",
                    icon: .circleSlash,
                    isLoading: reviewingAction == .reject,
                    disabled: reviewingAction != nil,
                    action: onReject
                )
                .accessibilityIdentifier("reviewClaimDetail_reject")
                .accessibilityLabel("Reject claim")
            }
        }
        .padding(Spacing.s4)
        .frame(maxWidth: .infinity)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl3))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl3)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
        .shadow(color: Theme.Color.appText.opacity(0.08), radius: 12, y: 4)
    }
}

private struct VerdictButton: View {
    enum Style { case accept, challenge, reject }

    let style: Style
    let label: String
    let icon: PantopusIcon
    let isLoading: Bool
    let disabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: style == .accept ? Spacing.s2 : 6) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: foreground))
                } else {
                    Icon(icon, size: style == .accept ? 17 : 15, color: foreground)
                }
                Text(label)
                    .font(.system(size: style == .accept ? 15 : 13.5, weight: .semibold))
                    .foregroundStyle(foreground)
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: style == .accept ? 48 : 44)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(border, lineWidth: border == .clear ? 0 : 1)
            )
            .shadow(color: shadow, radius: style == .accept ? 8 : 0, y: style == .accept ? 6 : 0)
        }
        .buttonStyle(.plain)
        .disabled(disabled)
    }

    private var cornerRadius: CGFloat { style == .accept ? Radii.lg : 10 }

    private var foreground: Color {
        switch style {
        case .accept: Theme.Color.appTextInverse
        case .challenge: Theme.Color.warmAmber
        case .reject: Theme.Color.error
        }
    }

    private var background: Color {
        switch style {
        case .accept: Theme.Color.success
        case .challenge: Theme.Color.warningBg
        case .reject: Theme.Color.appSurface
        }
    }

    private var border: Color {
        switch style {
        case .accept: .clear
        case .challenge: Theme.Color.warningLight
        case .reject: Theme.Color.errorLight
        }
    }

    private var shadow: Color {
        style == .accept ? Theme.Color.primary600.opacity(0.28) : .clear
    }
}

// MARK: - Challenge composer sheet

struct ChallengeComposerSheet: View {
    let claimantFirstName: String
    let coOwnerCount: Int
    @Binding var question: String
    let selectedReasons: Set<ChallengeReason>
    let isSubmitting: Bool
    let canSend: Bool
    let onToggleReason: (ChallengeReason) -> Void
    let onSend: () -> Void
    let onBack: () -> Void

    private let charLimit = 600

    var body: some View {
        VStack(spacing: Spacing.s4) {
            Capsule()
                .fill(Theme.Color.appBorderStrong)
                .frame(width: 38, height: 4)
                .padding(.top, Spacing.s2)

            header
            scrollBody
            actions
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.bottom, Spacing.s6)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.Color.appSurface)
        .presentationDetents([.fraction(0.78), .large])
        .presentationDragIndicator(.hidden)
        .accessibilityIdentifier("reviewClaimDetail_challengeComposer")
    }

    private var header: some View {
        HStack(spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: 9)
                    .fill(Theme.Color.warningBg)
                    .frame(width: 34, height: 34)
                    .overlay(
                        RoundedRectangle(cornerRadius: 9)
                            .stroke(Theme.Color.warningLight, lineWidth: 1)
                    )
                Icon(.messageCircle, size: 17, color: Theme.Color.warmAmber)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text("Challenge this claim")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                    .accessibilityAddTraits(.isHeader)
                Text("\(claimantFirstName) gets your questions and 14 days to respond.")
                    .font(.system(size: 11.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s0)
        }
    }

    private var scrollBody: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: Spacing.s4) {
                VStack(alignment: .leading, spacing: Spacing.s2) {
                    fieldLabel("Reasons (pick any)", required: false)
                    ReviewClaimFlowLayout(spacing: 6) {
                        ForEach(ChallengeReason.allCases) { reason in
                            ReasonChip(
                                label: reason.label,
                                selected: selectedReasons.contains(reason),
                                onTap: { onToggleReason(reason) }
                            )
                            .accessibilityIdentifier("reviewClaimDetail_reason_\(reason.rawValue)")
                        }
                    }
                }

                VStack(alignment: .leading, spacing: Spacing.s2) {
                    fieldLabel("Your questions for \(claimantFirstName)", required: true)
                    questionEditor
                    Text("\(question.count) / \(charLimit)")
                        .font(.system(size: 10.5, weight: .medium, design: .monospaced))
                        .foregroundStyle(Theme.Color.appTextMuted)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }

                visibilityCard
            }
            .padding(.bottom, Spacing.s2)
        }
    }

    private var questionEditor: some View {
        TextEditor(text: $question)
            .font(.system(size: 13.5))
            .foregroundStyle(Theme.Color.appText)
            .scrollContentBackground(.hidden)
            .frame(minHeight: 104)
            .padding(10)
            .background(Theme.Color.appSurfaceMuted)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .accessibilityIdentifier("reviewClaimDetail_challengeQuestion")
    }

    private var visibilityCard: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            visibilityRow(icon: .eye, text: "Sent to \(claimantFirstName) + \(coOwnerCount) co-owners")
            visibilityRow(icon: .clock, text: "14-day window to respond")
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurfaceMuted)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
    }

    private func visibilityRow(icon: PantopusIcon, text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Icon(icon, size: 14, color: Theme.Color.appTextSecondary)
            Text(text)
                .font(.system(size: 11.5))
                .foregroundStyle(Theme.Color.appTextStrong)
            Spacer(minLength: Spacing.s0)
        }
    }

    private var actions: some View {
        HStack(spacing: Spacing.s2) {
            Button(action: onBack) {
                Text("Back")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 46)
                    .background(Theme.Color.appSurface)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.lg)
                            .stroke(Theme.Color.appBorder, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
            .disabled(isSubmitting)

            Button(action: onSend) {
                HStack(spacing: 7) {
                    if isSubmitting {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: Theme.Color.appTextInverse))
                    } else {
                        Icon(.send, size: 15, color: Theme.Color.appTextInverse)
                    }
                    Text(isSubmitting ? "Sending…" : "Send challenge")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 46)
                .background(canSend ? Theme.Color.warmAmber : Theme.Color.appBorderStrong)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
                .shadow(color: canSend ? Theme.Color.warmAmber.opacity(0.28) : .clear, radius: 8, y: 6)
            }
            .buttonStyle(.plain)
            .disabled(!canSend || isSubmitting)
            .accessibilityIdentifier("reviewClaimDetail_sendChallenge")
        }
    }

    private func fieldLabel(_ text: String, required: Bool) -> some View {
        HStack(spacing: 3) {
            Text(text)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextStrong)
            if required {
                Text("*")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.Color.error)
            }
        }
    }
}

private struct ReasonChip: View {
    let label: String
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 5) {
                if selected {
                    Icon(.check, size: 11, strokeWidth: 3, color: Theme.Color.warmAmber)
                }
                Text(label)
                    .font(.system(size: 12, weight: selected ? .semibold : .medium))
                    .foregroundStyle(selected ? Theme.Color.warmAmber : Theme.Color.appTextStrong)
            }
            .padding(.horizontal, 11)
            .padding(.vertical, 7)
            .background(selected ? Theme.Color.warningBg : Theme.Color.appSurface)
            .clipShape(Capsule())
            .overlay(
                Capsule().stroke(selected ? Theme.Color.warningLight : Theme.Color.appBorder, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Flow layout

/// Left-to-right wrapping layout shared by the trust-chip row and the
/// reason-chip picker. Kept local to the feature (mirrors the per-feature
/// `*FlowLayout` convention elsewhere in the app).
struct ReviewClaimFlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var maxRowWidth: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > maxWidth, x > 0 {
                totalHeight += rowHeight + spacing
                maxRowWidth = max(maxRowWidth, x - spacing)
                x = 0
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }

        totalHeight += rowHeight
        maxRowWidth = max(maxRowWidth, x - spacing)
        return CGSize(width: maxRowWidth, height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) {
        let maxWidth = proposal.width ?? bounds.width
        var x: CGFloat = bounds.minX
        var y: CGFloat = bounds.minY
        var rowHeight: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.minX + maxWidth, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

// MARK: - Note capture sheet (reject reason)

struct ReviewClaimNoteCaptureSheet: View {
    let title: String
    let prompt: String
    let placeholder: String
    let primaryTitle: String
    let primaryRole: ButtonRole?
    @Binding var note: String
    let isSubmitting: Bool
    let onPrimary: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            HStack {
                Text(title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Spacer(minLength: Spacing.s0)
                Button(action: onCancel) {
                    Icon(.x, size: 22, color: Theme.Color.appTextSecondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
            }
            Text(prompt)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
            TextEditor(text: $note)
                .frame(minHeight: 120)
                .padding(Spacing.s2)
                .background(Theme.Color.appBg)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .accessibilityIdentifier("reviewClaimDetail_noteEditor")
            Button(role: primaryRole, action: onPrimary) {
                HStack(spacing: Spacing.s2) {
                    if isSubmitting {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: Theme.Color.appTextInverse))
                    }
                    Text(isSubmitting ? "Sending…" : primaryTitle)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 48)
                .background(primaryBackground)
                .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
            }
            .buttonStyle(.plain)
            .disabled(isSubmitting)
            .accessibilityIdentifier("reviewClaimDetail_notePrimary")

            Button(action: onCancel) {
                Text("Cancel")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
            }
            .buttonStyle(.plain)
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.Color.appSurface)
        .presentationDetents([.medium, .large])
    }

    private var primaryBackground: Color {
        primaryRole == .destructive ? Theme.Color.error : Theme.Color.primary600
    }
}
