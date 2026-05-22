//
//  GigComposeMagic.swift
//  Pantopus
//
//  B.3 (A12.8) — Magic Task step-1 chrome for the Post-a-Task wizard:
//  the AI-assisted describe path (default) and the manual archetype
//  picker (fallback). Step 1 renders `MagicDescribeStep` or
//  `ManualPickerStep` based on `form.composeMode`.
//

import SwiftUI

// swiftlint:disable file_length

// MARK: - Module prompt model + fixture

/// One JSONB module prompt row in the Magic Task "Task details" card.
struct GigModulePrompt: Identifiable, Hashable {
    let id: String
    let icon: PantopusIcon
    let label: String
    let value: String
    let isFilled: Bool
}

/// Deterministic module-prompt fixture for a detected archetype. Backend
/// JSONB module config is out of scope; this stands in (4 of 5 filled,
/// one nudge — Photos) per the A12.8 populated frame.
func gigMagicModulePrompts(for archetype: GigComposeCategory?) -> [GigModulePrompt] {
    guard archetype != nil else { return [] }
    return [
        GigModulePrompt(
            id: "when",
            icon: .calendar,
            label: "When",
            value: "Sat Oct 18 · Morning (8a–12p)",
            isFilled: true
        ),
        GigModulePrompt(
            id: "where",
            icon: .mapPin,
            label: "Where",
            value: "412 Elm St · Inside, upstairs",
            isFilled: true
        ),
        GigModulePrompt(
            id: "effort",
            icon: .timer,
            label: "Effort",
            value: "~2 hours · 1 tasker",
            isFilled: true
        ),
        GigModulePrompt(
            id: "photos",
            icon: .camera,
            label: "Photos",
            value: "Recommended for better bids",
            isFilled: false
        ),
        GigModulePrompt(
            id: "budget",
            icon: .wallet,
            label: "Budget",
            value: "$80–120 (suggested)",
            isFilled: true
        )
    ]
}

// MARK: - Category accent helper

extension GigComposeCategory {
    /// A12.8 manual path renders the eight concrete archetypes. `Other`
    /// remains valid for restored / backend state, but is not a picker tile.
    static var manualPickerCases: [GigComposeCategory] {
        allCases.filter { $0 != .other }
    }

    /// Accent colour for the manual-picker tile (A12.8 category accents).
    var accent: Color {
        switch self {
        case .handyman: Theme.Color.handyman
        case .cleaning: Theme.Color.cleaning
        case .moving: Theme.Color.moving
        case .petcare: Theme.Color.petCare
        case .childcare: Theme.Color.childCare
        case .tutoring: Theme.Color.tutoring
        case .delivery: Theme.Color.delivery
        case .tech: Theme.Color.tech
        case .other: Theme.Color.appTextSecondary
        }
    }

    var tileIcon: PantopusIcon {
        switch self {
        case .handyman: .hammer
        case .cleaning: .sparkles
        case .moving: .package
        case .petcare: .pawPrint
        case .childcare: .heart
        case .tutoring: .lightbulb
        case .delivery: .send
        case .tech: .laptop
        case .other: .moreHorizontal
        }
    }

    /// Short example list rendered under the tile label.
    var examples: String {
        switch self {
        case .handyman: "Assembly · repairs · install"
        case .cleaning: "Home · move-out · windows"
        case .moving: "Boxes · furniture · loading"
        case .petcare: "Walks · sitting · grooming"
        case .childcare: "Sitting · pickups · tutoring"
        case .tutoring: "Math · music · test prep"
        case .delivery: "Pickups · drops · errands"
        case .tech: "Wifi · setup · troubleshoot"
        case .other: "Anything else"
        }
    }
}

extension GigComposeEngagementMode {
    var label: String {
        switch self {
        case .oneTime: "One-time"
        case .recurring: "Recurring"
        case .openBidding: "Open bidding"
        }
    }

    var subcopy: String {
        switch self {
        case .oneTime: "Done once"
        case .recurring: "Weekly +"
        case .openBidding: "Helpers bid"
        }
    }

    var icon: PantopusIcon {
        switch self {
        case .oneTime: .calendar
        case .recurring: .arrowsRepeat
        case .openBidding: .wallet
        }
    }
}

// MARK: - Identity chip

struct ComposeIdentityChip: View {
    var body: some View {
        HStack(spacing: 4) {
            Icon(.user, size: 11, color: Theme.Color.personal)
            Text("PERSONAL · YOU")
                .font(.system(size: 10.5, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(Theme.Color.personal)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 4)
        .background(Theme.Color.personalBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
        .accessibilityIdentifier("composeGigIdentityChip")
    }
}

// MARK: - Step 1A: Magic describe

struct MagicDescribeStep: View {
    @Bindable var viewModel: GigComposeViewModel

    var body: some View {
        ComposeIdentityChip()
        HeadlineBlock("What do you need done?")
        SubcopyBlock("Describe it in your own words. Pantopus figures out the category, fills in the details, and posts it for bids.")
        MagicDescribeCard(
            text: Binding(
                get: { viewModel.form.describeText },
                set: { viewModel.setDescribeText($0) }
            ),
            isParsed: viewModel.form.detectedArchetype != nil
        )
        if let archetype = viewModel.form.detectedArchetype {
            DetectedArchetypePill(archetype: archetype) {
                viewModel.setComposeMode(.manual)
            }
            ModulePromptsCard(prompts: gigMagicModulePrompts(for: archetype))
        }
        EngagementModeControl(
            selected: viewModel.form.engagementMode
        ) { viewModel.selectEngagementMode($0) }
    }
}

private struct MagicDescribeCard: View {
    @Binding var text: String
    let isParsed: Bool

    var body: some View {
        VStack(spacing: 0) {
            header
            TextEditor(text: $text)
                .font(.system(size: 14.5))
                .foregroundStyle(Theme.Color.appText)
                .frame(minHeight: 96)
                .scrollContentBackground(.hidden)
                .padding(.horizontal, Spacing.s2)
                .padding(.vertical, Spacing.s1)
                .overlay(alignment: .topLeading) {
                    if text.isEmpty {
                        Text("e.g. Need someone to assemble an IKEA desk this Saturday morning…")
                            .font(.system(size: 14.5))
                            .foregroundStyle(Theme.Color.appTextMuted)
                            .padding(.horizontal, Spacing.s3)
                            .padding(.top, Spacing.s2 + 2)
                            .allowsHitTesting(false)
                    }
                }
                .accessibilityIdentifier("composeGigDescribeField")
            toolRow
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
    }

    private var header: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.sparkles, size: 13, strokeWidth: 2.4, color: Theme.Color.appTextInverse)
                .frame(width: 22, height: 22)
                .background(Theme.Color.magic)
                .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
            Text("Magic Task")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Theme.Color.magic)
            Spacer(minLength: 0)
            if isParsed {
                HStack(spacing: 4) {
                    Circle().fill(Theme.Color.success).frame(width: 6, height: 6)
                    Text("PARSED")
                        .font(.system(size: 10, weight: .bold))
                        .tracking(0.4)
                        .foregroundStyle(Theme.Color.success)
                }
            }
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s3)
        .background(Theme.Color.magic.opacity(0.08))
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.magic.opacity(0.18)).frame(height: 1)
        }
    }

    private var toolRow: some View {
        HStack(spacing: Spacing.s2) {
            ForEach([PantopusIcon.image, PantopusIcon.paperclip], id: \.self) { icon in
                Icon(icon, size: 15, color: Theme.Color.appTextSecondary)
                    .frame(width: 32, height: 32)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                            .stroke(Theme.Color.appBorder, lineWidth: 1)
                    )
            }
            Spacer(minLength: 0)
            Text("\(text.count) / \(GigComposeLimits.describeMax)")
                .font(.system(size: 11))
                .foregroundStyle(Theme.Color.appTextMuted)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s2)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }
}

private struct DetectedArchetypePill: View {
    let archetype: GigComposeCategory
    let onChange: () -> Void

    var body: some View {
        HStack(spacing: Spacing.s3) {
            Icon(archetype.tileIcon, size: 18, strokeWidth: 2.2, color: archetype.accent)
                .frame(width: 36, height: 36)
                .background(archetype.accent.opacity(0.14))
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            VStack(alignment: .leading, spacing: 1) {
                Text("DETECTED CATEGORY")
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(0.5)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Text(archetype.label)
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
            }
            Spacer(minLength: 0)
            Button(action: onChange) {
                Text("Change")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.Color.primary600)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("composeGigChangeArchetype")
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Detected category: \(archetype.label)")
        .accessibilityIdentifier("composeGigDetectedArchetype")
    }
}

private struct ModulePromptsCard: View {
    let prompts: [GigModulePrompt]

    private var filledCount: Int {
        prompts.filter(\.isFilled).count
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("TASK DETAILS")
                    .font(.system(size: 10.5, weight: .semibold))
                    .tracking(0.6)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                Text("\(filledCount) of \(prompts.count) filled")
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.success)
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.top, Spacing.s3)
            .padding(.bottom, Spacing.s2)
            ForEach(Array(prompts.enumerated()), id: \.element.id) { index, prompt in
                ModulePromptRow(prompt: prompt)
                if index < prompts.count - 1 {
                    Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
                }
            }
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .accessibilityIdentifier("composeGigModulePrompts")
    }
}

private struct ModulePromptRow: View {
    let prompt: GigModulePrompt

    var body: some View {
        HStack(spacing: Spacing.s3) {
            Icon(prompt.icon, size: 14, strokeWidth: 2.2, color: prompt.isFilled ? Theme.Color.success : Theme.Color.warning)
                .frame(width: 28, height: 28)
                .background((prompt.isFilled ? Theme.Color.success : Theme.Color.warning).opacity(0.14))
                .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
            VStack(alignment: .leading, spacing: 1) {
                Text(prompt.label)
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Text(prompt.value)
                    .font(.system(size: 13, weight: prompt.isFilled ? .semibold : .regular))
                    .foregroundStyle(prompt.isFilled ? Theme.Color.appText : Theme.Color.appTextSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            if prompt.isFilled {
                Icon(.check, size: 14, strokeWidth: 2.6, color: Theme.Color.success)
            } else {
                Text("Add")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.Color.warning)
                    .padding(.horizontal, Spacing.s2)
                    .padding(.vertical, 4)
                    .background(Theme.Color.warning.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
            }
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, 10)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(prompt.label): \(prompt.value)\(prompt.isFilled ? ", filled" : ", needs input")")
    }
}

private struct EngagementModeControl: View {
    let selected: GigComposeEngagementMode
    let onSelect: (GigComposeEngagementMode) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("ENGAGEMENT MODE")
                .font(.system(size: 10.5, weight: .semibold))
                .tracking(0.6)
                .foregroundStyle(Theme.Color.appTextSecondary)
            HStack(spacing: Spacing.s2) {
                ForEach(GigComposeEngagementMode.allCases, id: \.self) { option in
                    let active = option == selected
                    Button { onSelect(option) } label: {
                        VStack(spacing: 4) {
                            Icon(
                                option.icon,
                                size: 16,
                                strokeWidth: 2.2,
                                color: active ? Theme.Color.primary600 : Theme.Color.appTextSecondary
                            )
                            Text(option.label)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(active ? Theme.Color.primary700 : Theme.Color.appText)
                            Text(option.subcopy)
                                .font(.system(size: 10))
                                .foregroundStyle(active ? Theme.Color.primary600 : Theme.Color.appTextSecondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.s2)
                        .background(active ? Theme.Color.primary50 : Theme.Color.appSurface)
                        .overlay(
                            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                                .stroke(active ? Theme.Color.primary600 : Theme.Color.appBorder, lineWidth: active ? 1.5 : 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("composeGigEngagement_\(option.rawValue)")
                    .accessibilityLabel("\(option.label), \(option.subcopy)")
                    .accessibilityAddTraits(active ? [.isButton, .isSelected] : .isButton)
                }
            }
        }
    }
}

// MARK: - Step 1B: Manual picker

struct ManualPickerStep: View {
    @Bindable var viewModel: GigComposeViewModel

    private let columns: [GridItem] = [
        GridItem(.flexible(), spacing: Spacing.s2),
        GridItem(.flexible(), spacing: Spacing.s2)
    ]

    var body: some View {
        BackToMagicBanner { viewModel.setComposeMode(.magic) }
        ComposeIdentityChip()
        HeadlineBlock("Pick a category")
        SubcopyBlock("Skipping the describe step? Pick the archetype directly — we'll ask the questions that matter for it.")
        LazyVGrid(columns: columns, spacing: Spacing.s2) {
            ForEach(GigComposeCategory.manualPickerCases, id: \.self) { category in
                MagicCategoryTile(
                    category: category,
                    isSelected: viewModel.form.category == category
                ) { viewModel.selectCategory(category) }
            }
        }
    }
}

private struct BackToMagicBanner: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Spacing.s3) {
                Icon(.sparkles, size: 14, strokeWidth: 2.4, color: Theme.Color.appTextInverse)
                    .frame(width: 28, height: 28)
                    .background(Theme.Color.magic)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text("Back to Magic Task")
                        .font(.system(size: 12.5, weight: .bold))
                        .foregroundStyle(Theme.Color.magic)
                    Text("Describe it in plain English — faster for most posts.")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                Icon(.arrowLeft, size: 15, color: Theme.Color.magic)
            }
            .padding(Spacing.s3)
            .background(Theme.Color.magic.opacity(0.08))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.magic.opacity(0.25), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("composeGigBackToMagic")
        .accessibilityLabel("Back to Magic Task")
    }
}

private struct MagicCategoryTile: View {
    let category: GigComposeCategory
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: Spacing.s2) {
                Icon(category.tileIcon, size: 17, strokeWidth: 2.2, color: category.accent)
                    .frame(width: 34, height: 34)
                    .background(category.accent.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(category.label)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(category.examples)
                        .font(.system(size: 10.5))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Spacing.s3)
            .background(isSelected ? Theme.Color.primary50 : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(isSelected ? Theme.Color.primary600 : Theme.Color.appBorder, lineWidth: isSelected ? 2 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("composeGig_category_\(category.rawValue)")
        .accessibilityLabel("\(category.label)\(isSelected ? ", selected" : "")")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}
