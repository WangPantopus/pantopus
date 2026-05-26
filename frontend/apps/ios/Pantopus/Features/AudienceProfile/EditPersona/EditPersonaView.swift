//
//  EditPersonaView.swift
//  Pantopus
//
//  A13.12 — Edit persona. The creator-facing editor for a persona / Public
//  Profile. Built on `FormShell` (X + title + @handle subtitle, no
//  top-right action) with a custom persona sticky bar below the scroll.
//
//  Persona accent is sky / `primary600`, flat — see EditPersonaContent.swift
//  for why we mirror the shipped persona surfaces instead of the design
//  source's fuchsia gradient.
//

// swiftlint:disable file_length

import SwiftUI

/// Shared no-op for the editor's placeholder affordances (Preview, Save,
/// Connect, Copy/Share, Add tier). Keeping `action:` a reference rather than
/// an inline `{}` keeps SwiftLint's closure rules satisfied while these
/// stay non-wired in the no-backend build.
@MainActor private let personaNoOp: @Sendable () -> Void = {}

public struct EditPersonaView: View {
    @State private var viewModel: EditPersonaViewModel
    private let onClose: @MainActor () -> Void

    public init(
        viewModel: EditPersonaViewModel,
        onClose: @escaping @MainActor () -> Void = {}
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onClose = onClose
    }

    public var body: some View {
        content
            .background(Theme.Color.appBg)
            .task { await viewModel.load() }
            .accessibilityIdentifier("editPersona")
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            shell(subtitle: nil, isDirty: false, body: { EditPersonaLoadingBody() }, stickyBottom: nil)
        case let .live(loaded):
            shell(
                subtitle: loaded.atHandle,
                isDirty: false,
                body: { EditPersonaEditor(content: loaded, variant: .live) },
                stickyBottom: { AnyView(PersonaStickyBar(variant: .live, onDiscard: onClose)) }
            )
        case let .setup(loaded, done, total):
            shell(
                subtitle: loaded.atHandle,
                isDirty: true,
                body: { EditPersonaEditor(content: loaded, variant: .setup, stepsDone: done, stepsTotal: total) },
                stickyBottom: { AnyView(PersonaStickyBar(variant: .setup, onDiscard: onClose)) }
            )
        case let .error(message):
            shell(
                subtitle: nil,
                isDirty: false,
                body: { EditPersonaErrorBody(message: message) { Task { await viewModel.load() } } },
                stickyBottom: nil
            )
        }
    }

    /// Compose `FormShell` (chrome + scroll + dirty-close confirm) with the
    /// shared `stickyBottom` slot for the persona-aware sticky save bar.
    private func shell(
        subtitle: String?,
        isDirty: Bool,
        @ViewBuilder body: () -> some View,
        stickyBottom: (() -> AnyView)?
    ) -> some View {
        FormShell(
            title: "Edit persona",
            subtitle: subtitle,
            rightActionLabel: nil,
            isValid: true,
            isDirty: isDirty,
            onClose: onClose,
            onCommit: personaNoOp,
            content: { body() },
            stickyBottom: stickyBottom
        )
    }
}

// MARK: - Loading / error bodies

private struct EditPersonaLoadingBody: View {
    var body: some View {
        VStack(spacing: Spacing.s5) {
            Shimmer(height: 120, cornerRadius: Radii.lg)
            Shimmer(height: 160, cornerRadius: Radii.lg)
            Shimmer(height: 200, cornerRadius: Radii.lg)
            Shimmer(height: 120, cornerRadius: Radii.lg)
        }
        .padding(.horizontal, Spacing.s4)
        .accessibilityIdentifier("editPersonaLoading")
    }
}

private struct EditPersonaErrorBody: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s3) {
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load persona")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Text(message)
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button(action: onRetry) {
                Text("Try again")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s5)
                    .frame(height: 44)
                    .background(Theme.Color.primary600)
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("editPersonaRetry")
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s10)
        .accessibilityIdentifier("editPersonaError")
    }
}

// MARK: - Editor body (holds interactive control state)

private struct EditPersonaEditor: View {
    let content: EditPersonaContent
    let variant: EditPersonaVariant
    let stepsDone: Int
    let stepsTotal: Int

    @State private var cap: PersonaCapOption
    @State private var quietHoursOn: Bool
    @State private var analyticsOn: Bool

    init(
        content: EditPersonaContent,
        variant: EditPersonaVariant,
        stepsDone: Int = 0,
        stepsTotal: Int = 0
    ) {
        self.content = content
        self.variant = variant
        self.stepsDone = stepsDone
        self.stepsTotal = stepsTotal
        _cap = State(initialValue: content.cap)
        _quietHoursOn = State(initialValue: content.quietHoursOn)
        _analyticsOn = State(initialValue: content.analyticsOn)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s5) {
            hero
            identitySection
            policySection
            tiersSection
            broadcastSection
            shareSection
            analyticsSection
        }
        .padding(.horizontal, Spacing.s4)
        .accessibilityIdentifier("editPersonaContent")
    }

    @ViewBuilder private var hero: some View {
        switch variant {
        case .live:
            PersonaLiveHero(content: content)
        case .setup:
            PersonaSetupHero(content: content, stepsDone: stepsDone, stepsTotal: stepsTotal)
        }
    }

    // MARK: Identity

    private var identitySection: some View {
        PersonaSection("Identity") {
            VStack(alignment: .leading, spacing: Spacing.s4) {
                VStack(alignment: .leading, spacing: Spacing.s0) {
                    PLabel("Handle", required: true, hint: "lowercase · 3–24 chars")
                    PersonaHandleField(handle: content.handle, status: content.handleStatus)
                    if let note = content.handleNote {
                        HStack(spacing: Spacing.s1) {
                            Icon(.checkCircle, size: 11, color: Theme.Color.success)
                            Text(note)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(Theme.Color.success)
                        }
                        .padding(.top, Spacing.s2)
                    }
                }
                VStack(alignment: .leading, spacing: Spacing.s0) {
                    PLabel("Display name", required: true)
                    PersonaTextDisplay(text: content.displayName, identifier: "editPersonaDisplayName")
                }
                VStack(alignment: .leading, spacing: Spacing.s0) {
                    PLabel("Bio")
                    PersonaTextDisplay(
                        text: content.bio,
                        minHeight: 88,
                        identifier: "editPersonaBio"
                    )
                    Text(content.bioCharCount)
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextMuted)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .padding(.top, Spacing.s1)
                }
            }
        }
    }

    // MARK: Category policy

    private var policySection: some View {
        PersonaSection("Category policy") {
            VStack(alignment: .leading, spacing: Spacing.s2) {
                PersonaPolicyRow(
                    kind: .allow,
                    title: "Allowed on this persona",
                    sub: content.categoriesAllowSub,
                    chips: content.categoriesAllow
                )
                PersonaPolicyRow(
                    kind: .off,
                    title: "Off-topic — blocked auto-suggest",
                    sub: content.categoriesOffSub,
                    chips: content.categoriesOff
                )
                if let note = content.policyNote {
                    Text(note)
                        .font(.system(size: 11))
                        .italic()
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .padding(.top, Spacing.s1)
                }
            }
        }
    }

    // MARK: Tiers

    private var tiersSection: some View {
        PersonaSection("Tiers") {
            VStack(alignment: .leading, spacing: Spacing.s2) {
                PersonaStripeConnectCard(state: content.stripe)
                ForEach(content.tiers) { tier in
                    PersonaTierCardView(tier: tier)
                }
                PersonaAddTierRow(disabled: !content.canAddTier)
            }
        }
    }

    // MARK: Broadcast

    private var broadcastSection: some View {
        PersonaSection("Broadcast") {
            VStack(alignment: .leading, spacing: Spacing.s4) {
                VStack(alignment: .leading, spacing: Spacing.s0) {
                    PLabel("Posts per week", hint: "hard cap, not a target")
                    PersonaCapSelector(selection: $cap)
                }
                PersonaQuietHoursRow(isOn: $quietHoursOn, range: content.quietHoursRange)
            }
        }
    }

    // MARK: Share

    private var shareSection: some View {
        PersonaSection("Share") {
            PersonaShareCardView(url: content.shareUrl, isPublic: content.shareIsPublic)
        }
    }

    // MARK: Analytics

    private var analyticsSection: some View {
        PersonaSection("Analytics") {
            PersonaAnalyticsRow(isOn: $analyticsOn, scope: content.analyticsScope)
        }
    }
}

// MARK: - Section scaffold

private struct PersonaSection<Content: View>: View {
    let overline: String
    let content: Content

    init(_ overline: String, @ViewBuilder content: () -> Content) {
        self.overline = overline
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(overline.uppercased())
                .font(.system(size: 10.5, weight: .bold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .kerning(0.7)
                .accessibilityAddTraits(.isHeader)
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct PLabel: View {
    let text: String
    let required: Bool
    let hint: String?

    init(_ text: String, required: Bool = false, hint: String? = nil) {
        self.text = text
        self.required = required
        self.hint = hint
    }

    var body: some View {
        HStack(spacing: 3) {
            Text(text)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextStrong)
            if required {
                Text("*").font(.system(size: 12, weight: .semibold)).foregroundStyle(Theme.Color.primary600)
            }
            if let hint {
                Text(hint)
                    .font(.system(size: 11, weight: .medium))
                    .italic()
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
            Spacer(minLength: Spacing.s0)
        }
        .padding(.bottom, Spacing.s2)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Live hero (flat sky banner)

private struct PersonaLiveHero: View {
    let content: EditPersonaContent

    var body: some View {
        VStack(spacing: Spacing.s3) {
            HStack(spacing: Spacing.s3) {
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .fill(Theme.Color.appTextInverse.opacity(0.18))
                    .frame(width: 44, height: 44)
                    .overlay { Icon(.radio, size: 19, color: Theme.Color.appTextInverse) }
                VStack(alignment: .leading, spacing: 2) {
                    Text(content.displayName)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                    Text("Live persona · published & broadcasting")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextInverse.opacity(0.8))
                }
                Spacer(minLength: Spacing.s2)
                Text(content.liveBadge.uppercased())
                    .font(.system(size: 9.5, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(Theme.Color.appTextInverse.opacity(0.22))
                    .clipShape(RoundedRectangle(cornerRadius: Radii.xs, style: .continuous))
            }
            HStack(spacing: Spacing.s2) {
                statTile(content.followers, "Followers")
                statTile(content.posts, "Posts · 30d")
                statTile(content.rating, "Avg rating")
            }
        }
        .padding(Spacing.s3)
        .background(Theme.Color.primary600)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "\(content.displayName), live persona. "
                + "\(content.followers) followers, \(content.posts) posts in 30 days, \(content.rating) average rating."
        )
        .accessibilityIdentifier("editPersonaLiveHero")
    }

    private func statTile(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(value)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Theme.Color.appTextInverse)
            Text(label.uppercased())
                .font(.system(size: 9.5, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextInverse.opacity(0.8))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appTextInverse.opacity(0.14))
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }
}

// MARK: - Setup hero (checklist)

private struct PersonaSetupHero: View {
    let content: EditPersonaContent
    let stepsDone: Int
    let stepsTotal: Int

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(spacing: Spacing.s3) {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(Theme.Color.primary600)
                    .frame(width: 40, height: 40)
                    .overlay { Icon(.sparkles, size: 18, color: Theme.Color.appTextInverse) }
                VStack(alignment: .leading, spacing: 1) {
                    Text("Finish your persona")
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(content.checklistSummary)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Theme.Color.primary700)
                }
                Spacer(minLength: Spacing.s2)
                Text("Draft")
                    .font(.system(size: 9.5, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(Theme.Color.primary600)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.xs, style: .continuous))
            }
            HStack(spacing: Spacing.s1) {
                ForEach(0..<max(stepsTotal, 1), id: \.self) { index in
                    Capsule()
                        .fill(index < stepsDone ? Theme.Color.primary600 : Theme.Color.primary100)
                        .frame(height: 5)
                        .frame(maxWidth: .infinity)
                }
            }
            VStack(alignment: .leading, spacing: Spacing.s1) {
                ForEach(content.checklist) { step in
                    checklistRow(step)
                }
            }
        }
        .padding(Spacing.s3)
        .background(Theme.Color.primary50)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.primary200, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityIdentifier("editPersonaSetupHero")
    }

    private func checklistRow(_ step: PersonaChecklistStep) -> some View {
        HStack(spacing: Spacing.s2) {
            if step.done {
                Icon(.checkCircle, size: 12, color: Theme.Color.success)
            } else {
                Circle()
                    .strokeBorder(
                        step.isNext ? Theme.Color.primary600 : Theme.Color.appBorderStrong,
                        lineWidth: 1.5
                    )
                    .background(Circle().fill(step.isNext ? Theme.Color.primary50 : Color.clear))
                    .frame(width: 12, height: 12)
            }
            Text(step.label)
                .font(.system(size: 11.5, weight: step.isNext ? .semibold : .medium))
                .foregroundStyle(stepColor(step))
            Spacer(minLength: Spacing.s0)
            if step.isNext {
                Text("NEXT")
                    .font(.system(size: 9.5, weight: .bold))
                    .foregroundStyle(Theme.Color.primary700)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(step.label)\(step.done ? ", done" : step.isNext ? ", next" : "")")
    }

    private func stepColor(_ step: PersonaChecklistStep) -> Color {
        if step.isNext { return Theme.Color.primary700 }
        return step.done ? Theme.Color.appTextStrong : Theme.Color.appTextSecondary
    }
}

// MARK: - Handle field

private struct PersonaHandleField: View {
    let handle: String
    let status: PersonaHandleStatus

    var body: some View {
        HStack(spacing: Spacing.s1) {
            Text("@")
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundStyle(Theme.Color.primary600)
            Text(handle)
                .font(.system(size: 14, weight: .semibold, design: .monospaced))
                .foregroundStyle(Theme.Color.appText)
            Spacer(minLength: Spacing.s2)
            statusPill
        }
        .padding(.horizontal, Spacing.s3)
        .frame(height: 44)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(borderColor, lineWidth: 1.5)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Handle @\(handle), \(statusLabel)")
        .accessibilityIdentifier("editPersonaHandle")
    }

    @ViewBuilder private var statusPill: some View {
        switch status {
        case .available:
            HStack(spacing: 3) {
                Icon(.checkCircle, size: 13, color: Theme.Color.success)
                Text("Available").font(.system(size: 11, weight: .bold)).foregroundStyle(Theme.Color.success)
            }
        case .reserved:
            HStack(spacing: 3) {
                Icon(.lock, size: 12, color: Theme.Color.primary700)
                Text("Reserved").font(.system(size: 11, weight: .bold)).foregroundStyle(Theme.Color.primary700)
            }
        case .taken:
            HStack(spacing: 3) {
                Icon(.alertCircle, size: 12, color: Theme.Color.error)
                Text("Taken").font(.system(size: 11, weight: .bold)).foregroundStyle(Theme.Color.error)
            }
        }
    }

    private var borderColor: Color {
        switch status {
        case .available: Theme.Color.success
        case .reserved: Theme.Color.primary300
        case .taken: Theme.Color.error
        }
    }

    private var statusLabel: String {
        switch status {
        case .available: "available"
        case .reserved: "reserved"
        case .taken: "taken"
        }
    }
}

private struct PersonaTextDisplay: View {
    let text: String
    var minHeight: CGFloat = 44
    let identifier: String

    var body: some View {
        Text(text)
            .pantopusTextStyle(.small)
            .foregroundStyle(Theme.Color.appText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(minHeight: minHeight, alignment: .topLeading)
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .accessibilityIdentifier(identifier)
    }
}

// MARK: - Category chips + policy rows

private enum PersonaPolicyKind { case allow, off }

private struct PersonaCatChip: View {
    let chip: PersonaCategoryChip
    let kind: PersonaPolicyKind

    var body: some View {
        HStack(spacing: 5) {
            Icon(chip.icon, size: 12, color: foreground)
            Text(chip.label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(foreground)
                .strikethrough(kind == .off, color: Theme.Color.appTextMuted)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, 6)
        .background(background)
        .overlay(Capsule().stroke(border, lineWidth: 1))
        .clipShape(Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(kind == .off ? "\(chip.label), off-topic" : chip.label)
    }

    private var foreground: Color {
        kind == .allow ? Theme.Color.primary700 : Theme.Color.appTextSecondary
    }

    private var background: Color {
        kind == .allow ? Theme.Color.primary50 : Theme.Color.appSurfaceSunken
    }

    private var border: Color {
        kind == .allow ? Theme.Color.primary200 : Theme.Color.appBorder
    }
}

private struct PersonaPolicyRow: View {
    let kind: PersonaPolicyKind
    let title: String
    let sub: String
    let chips: [PersonaCategoryChip]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                Circle()
                    .fill(dotColor)
                    .frame(width: 18, height: 18)
                    .overlay {
                        Icon(kind == .allow ? .check : .x, size: 11, strokeWidth: 3, color: Theme.Color.appTextInverse)
                    }
                Text(title)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(titleColor)
                Spacer(minLength: Spacing.s2)
                Text(sub)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            FilterSheetFlowLayout(spacing: 6) {
                ForEach(chips) { chip in
                    PersonaCatChip(chip: chip, kind: kind)
                }
            }
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(background)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(border, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityIdentifier("editPersonaPolicyRow_\(kind == .allow ? "allow" : "off")")
    }

    private var dotColor: Color {
        kind == .allow ? Theme.Color.success : Theme.Color.appTextMuted
    }

    private var titleColor: Color {
        kind == .allow ? Theme.Color.success : Theme.Color.appTextStrong
    }

    private var background: Color {
        kind == .allow ? Theme.Color.successBg : Theme.Color.appSurfaceSunken
    }

    private var border: Color {
        kind == .allow ? Theme.Color.successLight : Theme.Color.appBorder
    }
}

// MARK: - Tier card (creator-side)

private struct PersonaTierStripeFooter {
    let icon: PantopusIcon
    let text: String
    let color: Color
}

private struct PersonaTierCardView: View {
    let tier: PersonaTierCard

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(alignment: .top, spacing: Spacing.s2) {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(iconBackground)
                    .frame(width: 36, height: 36)
                    .overlay { Icon(iconName, size: 16, color: iconForeground) }
                VStack(alignment: .leading, spacing: 3) {
                    HStack(alignment: .firstTextBaseline, spacing: Spacing.s2) {
                        Text(tier.name)
                            .font(.system(size: 13.5, weight: .bold))
                            .foregroundStyle(Theme.Color.appText)
                        priceLabel
                    }
                    Text(tier.blurb)
                        .font(.system(size: 11.5))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: Spacing.s0)
                if tier.kind != .paidLocked {
                    Icon(.slidersHorizontal, size: 15, color: Theme.Color.appTextMuted)
                }
            }
            if !tier.perks.isEmpty {
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    ForEach(tier.perks, id: \.self) { perk in
                        HStack(spacing: Spacing.s1) {
                            Icon(.check, size: 12, color: Theme.Color.primary600)
                            Text(perk)
                                .font(.system(size: 11.5))
                                .foregroundStyle(Theme.Color.appTextStrong)
                        }
                    }
                }
                .padding(.leading, 46)
            }
            if let footer = stripeFooter {
                Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
                HStack(spacing: 7) {
                    Icon(footer.icon, size: 12, color: footer.color)
                    Text(footer.text)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(footer.color)
                }
            }
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(tier.isFresh ? Theme.Color.primary200 : Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .opacity(tier.kind == .paidLocked ? 0.6 : 1)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityIdentifier("editPersonaTier_\(tier.id)")
    }

    @ViewBuilder private var priceLabel: some View {
        switch tier.kind {
        case .free:
            Text("Always free")
                .font(.system(size: 11))
                .foregroundStyle(Theme.Color.appTextSecondary)
        case .paid, .paidLocked:
            HStack(spacing: Spacing.s0) {
                Text("$\(tier.priceLabel ?? "—")")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundStyle(tier.kind == .paidLocked ? Theme.Color.appTextMuted : Theme.Color.appText)
                if let period = tier.period {
                    Text(" / \(period)")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
        }
    }

    private var iconName: PantopusIcon {
        switch tier.kind {
        case .free: .users
        case .paid: .star
        case .paidLocked: .lock
        }
    }

    private var iconBackground: Color {
        tier.kind == .free ? Theme.Color.appSurfaceSunken : Theme.Color.primary50
    }

    private var iconForeground: Color {
        tier.kind == .free ? Theme.Color.appTextStrong : Theme.Color.primary700
    }

    private var stripeFooter: PersonaTierStripeFooter? {
        switch tier.stripeState {
        case .none:
            nil
        case .ready:
            PersonaTierStripeFooter(
                icon: .shieldCheck,
                text: "Stripe ready · payouts every Friday",
                color: Theme.Color.success
            )
        case .needsStripe:
            PersonaTierStripeFooter(
                icon: .link,
                text: "Connect Stripe to enable paid tiers",
                color: Theme.Color.primary700
            )
        }
    }

    private var accessibilityLabel: String {
        switch tier.kind {
        case .free: "\(tier.name), always free. \(tier.blurb)"
        case .paid: "\(tier.name), $\(tier.priceLabel ?? "") per \(tier.period ?? "month"). \(tier.blurb)"
        case .paidLocked: "\(tier.name), locked until Stripe is connected. \(tier.blurb)"
        }
    }
}

private struct PersonaAddTierRow: View {
    let disabled: Bool

    var body: some View {
        Button(action: personaNoOp) {
            HStack(spacing: Spacing.s2) {
                Icon(.plusCircle, size: 15, color: disabled ? Theme.Color.appTextMuted : Theme.Color.primary700)
                Text("Add paid tier")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(disabled ? Theme.Color.appTextMuted : Theme.Color.primary700)
                Spacer(minLength: Spacing.s0)
                Text("up to 4")
                    .font(.system(size: 10))
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
            .padding(.horizontal, Spacing.s4)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .strokeBorder(
                        disabled ? Theme.Color.appBorder : Theme.Color.primary200,
                        style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])
                    )
            )
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .accessibilityLabel("Add paid tier, up to 4")
        .accessibilityIdentifier("editPersonaAddTier")
    }
}

// MARK: - Stripe connect card

private struct PersonaStripeConnectCard: View {
    let state: PersonaStripeState

    var body: some View {
        switch state {
        case let .connected(account):
            connected(account: account)
        case .notConnected:
            notConnected
        }
    }

    private func connected(account: String) -> some View {
        HStack(spacing: Spacing.s3) {
            stripeBadge
            VStack(alignment: .leading, spacing: 1) {
                Text("Connected · \(account)")
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                HStack(spacing: Spacing.s1) {
                    Icon(.checkCircle, size: 10, color: Theme.Color.success)
                    Text("Charges enabled · payouts enabled")
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.success)
                }
            }
            Spacer(minLength: Spacing.s0)
            Text("Manage")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.Color.primary600)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.successBg)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.successLight, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .accessibilityIdentifier("editPersonaStripeConnected")
    }

    private var notConnected: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(spacing: Spacing.s3) {
                stripeBadge
                VStack(alignment: .leading, spacing: 1) {
                    Text("Connect Stripe to charge for tiers")
                        .font(.system(size: 12.5, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text("~3 min · ID + bank account · we never touch the money.")
                        .font(.system(size: 10.5))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: Spacing.s0)
            }
            Button(action: personaNoOp) {
                HStack(spacing: 7) {
                    Icon(.externalLink, size: 13, color: Theme.Color.appTextInverse)
                    Text("Connect with Stripe")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(Theme.Color.primary600)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Connect with Stripe")
            .accessibilityIdentifier("editPersonaStripeConnect")
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.primary50)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.primary200, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .accessibilityIdentifier("editPersonaStripeCard")
    }

    private var stripeBadge: some View {
        Text("stripe")
            .font(.system(size: 9, weight: .heavy))
            .foregroundStyle(Theme.Color.appTextInverse)
            .frame(width: 32, height: 22)
            .background(Theme.Color.primary600)
            .clipShape(RoundedRectangle(cornerRadius: Radii.xs, style: .continuous))
            .accessibilityHidden(true)
    }
}

// MARK: - Cap selector (segmented)

private struct PersonaCapSelector: View {
    @Binding var selection: PersonaCapOption

    var body: some View {
        HStack(spacing: Spacing.s0) {
            ForEach(PersonaCapOption.allCases) { option in
                let isOn = option == selection
                Button {
                    selection = option
                } label: {
                    Text(option.label)
                        .font(.system(size: 12, weight: isOn ? .bold : .medium))
                        .foregroundStyle(isOn ? Theme.Color.appText : Theme.Color.appTextSecondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 32)
                        .background(isOn ? Theme.Color.appSurface : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isOn ? [.isButton, .isSelected] : .isButton)
                .accessibilityIdentifier("editPersonaCap_\(option.rawValue)")
            }
        }
        .padding(3)
        .background(Theme.Color.appSurfaceSunken)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .accessibilityIdentifier("editPersonaCapSelector")
    }
}

// MARK: - Quiet hours

private struct PersonaQuietHoursRow: View {
    @Binding var isOn: Bool
    let range: String

    var body: some View {
        HStack(spacing: Spacing.s3) {
            Icon(.clock, size: 16, color: Theme.Color.appTextSecondary)
            VStack(alignment: .leading, spacing: 1) {
                Text("Quiet hours")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Text(isOn ? displayRange : "Broadcasts allowed any time")
                    .font(.system(size: 11, design: isOn ? .monospaced : .default))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s2)
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Theme.Color.primary600)
                .accessibilityLabel("Quiet hours")
                .accessibilityIdentifier("editPersonaQuietHoursToggle")
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s3)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }

    private var displayRange: String {
        range.isEmpty ? "10:00 PM → 7:00 AM" : range
    }
}

// MARK: - Share card

private struct PersonaShareCardView: View {
    let url: String
    let isPublic: Bool

    var body: some View {
        HStack(spacing: Spacing.s3) {
            qrStamp
            VStack(alignment: .leading, spacing: Spacing.s2) {
                Text((isPublic ? "Public link · scan to follow" : "Private preview · only you").uppercased())
                    .font(.system(size: 10.5, weight: .bold))
                    .foregroundStyle(isPublic ? Theme.Color.primary700 : Theme.Color.appTextSecondary)
                Text(url)
                    .font(.system(size: 11.5, design: .monospaced))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .padding(.horizontal, Spacing.s2)
                    .padding(.vertical, 6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.Color.appSurfaceMuted)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                            .stroke(Theme.Color.appBorder, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
                HStack(spacing: 6) {
                    shareButton(.copy, "Copy", id: "editPersonaShareCopy")
                    shareButton(.share, "Share", id: "editPersonaShareShare")
                }
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityIdentifier("editPersonaShareCard")
    }

    private var qrStamp: some View {
        ZStack {
            ForEach(Array(qrFinders.enumerated()), id: \.offset) { _, point in
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .stroke(qrColor, lineWidth: 2)
                    .frame(width: 16, height: 16)
                    .position(x: point.x, y: point.y)
            }
            RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                .fill(qrColor)
                .frame(width: 22, height: 22)
                .overlay { Icon(.radio, size: 12, color: Theme.Color.appTextInverse) }
        }
        .frame(width: 72, height: 72)
        .padding(6)
        .background(isPublic ? Theme.Color.appSurface : Theme.Color.appSurfaceSunken)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .accessibilityHidden(true)
    }

    private var qrColor: Color {
        isPublic ? Theme.Color.primary600 : Theme.Color.appTextMuted
    }

    private var qrFinders: [CGPoint] {
        [CGPoint(x: 12, y: 12), CGPoint(x: 60, y: 12), CGPoint(x: 12, y: 60)]
    }

    private func shareButton(_ icon: PantopusIcon, _ label: String, id: String) -> some View {
        Button(action: personaNoOp) {
            HStack(spacing: 5) {
                Icon(icon, size: 12, color: isPublic ? Theme.Color.appText : Theme.Color.appTextMuted)
                Text(label)
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(isPublic ? Theme.Color.appText : Theme.Color.appTextMuted)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 30)
            .background(isPublic ? Theme.Color.appSurface : Theme.Color.appSurfaceSunken)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!isPublic)
        .accessibilityLabel(label)
        .accessibilityIdentifier(id)
    }
}

// MARK: - Analytics row

private struct PersonaAnalyticsRow: View {
    @Binding var isOn: Bool
    let scope: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s3) {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(isOn ? Theme.Color.primary50 : Theme.Color.appSurfaceSunken)
                    .frame(width: 36, height: 36)
                    .overlay {
                        Icon(.arrowUpRight, size: 16, color: isOn ? Theme.Color.primary600 : Theme.Color.appTextMuted)
                    }
                VStack(alignment: .leading, spacing: 2) {
                    Text("Audience analytics")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                    Text("Aggregated reach & growth — never individual followers.")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: Spacing.s2)
                Toggle("", isOn: $isOn)
                    .labelsHidden()
                    .tint(Theme.Color.primary600)
                    .accessibilityLabel("Audience analytics")
                    .accessibilityIdentifier("editPersonaAnalyticsToggle")
            }
            if isOn, !scope.isEmpty {
                FilterSheetFlowLayout(spacing: 6) {
                    ForEach(scope, id: \.self) { item in
                        HStack(spacing: Spacing.s1) {
                            Icon(.check, size: 10, color: Theme.Color.primary700)
                            Text(item).font(.system(size: 10.5, weight: .semibold)).foregroundStyle(Theme.Color.primary700)
                        }
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3)
                        .background(Theme.Color.primary50)
                        .overlay(Capsule().stroke(Theme.Color.primary200, lineWidth: 1))
                        .clipShape(Capsule())
                    }
                }
            }
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityIdentifier("editPersonaAnalyticsRow")
    }
}

// MARK: - Sticky bar

private struct PersonaStickyBar: View {
    let variant: EditPersonaVariant
    let onDiscard: @MainActor () -> Void

    var body: some View {
        VStack(spacing: Spacing.s0) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            Group {
                switch variant {
                case .live: liveBar
                case .setup: setupBar
                }
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s3)
        }
        .background(Theme.Color.appSurface)
    }

    private var liveBar: some View {
        HStack(spacing: Spacing.s2) {
            HStack(spacing: 5) {
                Circle().fill(Theme.Color.success).frame(width: 7, height: 7)
                Text("Live · saved 2m ago")
                    .font(.system(size: 11.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s0)
            Button(action: personaNoOp) {
                HStack(spacing: 5) {
                    Icon(.eye, size: 14, color: Theme.Color.appTextStrong)
                    Text("Preview").font(.system(size: 13.5, weight: .semibold)).foregroundStyle(Theme.Color.appTextStrong)
                }
                .frame(height: 42)
                .padding(.horizontal, Spacing.s3)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Preview")
            .accessibilityIdentifier("editPersonaPreview")
            Text("Save")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextMuted)
                .padding(.horizontal, Spacing.s5)
                .frame(height: 42)
                .background(Theme.Color.appBorder)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                .accessibilityLabel("Save, no changes")
                .accessibilityIdentifier("editPersonaSave")
        }
    }

    private var setupBar: some View {
        VStack(spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                Icon(.info, size: 13, color: Theme.Color.primary700)
                Text("Save anytime — publish unlocks after Stripe + schedule")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.primary700)
                Spacer(minLength: Spacing.s0)
            }
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.Color.primary50)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(Theme.Color.primary200, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            HStack(spacing: Spacing.s2) {
                HStack(spacing: 6) {
                    Circle().fill(Theme.Color.warning).frame(width: 6, height: 6)
                    Text("7 UNSAVED").font(.system(size: 11, weight: .bold)).foregroundStyle(Theme.Color.warning)
                }
                .padding(.horizontal, Spacing.s2)
                .padding(.vertical, Spacing.s1)
                .background(Theme.Color.warningBg)
                .overlay(Capsule().stroke(Theme.Color.warningLight, lineWidth: 1))
                .clipShape(Capsule())
                Spacer(minLength: Spacing.s0)
                Button(action: onDiscard) {
                    Text("Discard")
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.appTextStrong)
                        .frame(height: 42)
                        .padding(.horizontal, Spacing.s3)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Discard draft")
                .accessibilityIdentifier("editPersonaDiscard")
                Button(action: personaNoOp) {
                    HStack(spacing: 6) {
                        Icon(.check, size: 15, color: Theme.Color.appTextInverse)
                        Text("Save draft").font(.system(size: 14, weight: .semibold)).foregroundStyle(Theme.Color.appTextInverse)
                    }
                    .frame(height: 42)
                    .padding(.horizontal, Spacing.s5)
                    .background(Theme.Color.primary600)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Save draft")
                .accessibilityIdentifier("editPersonaSaveDraft")
            }
        }
    }
}

#Preview("Live") {
    EditPersonaView(viewModel: EditPersonaViewModel(personaId: EditPersonaSampleData.personaId, variant: .live))
}

#Preview("Setup") {
    EditPersonaView(viewModel: EditPersonaViewModel(personaId: "persona_sourdough_sat", variant: .setup))
}
