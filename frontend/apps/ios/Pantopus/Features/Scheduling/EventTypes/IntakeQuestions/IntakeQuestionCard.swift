//
//  IntakeQuestionCard.swift
//  Pantopus
//
//  Stream I2 — B3 one custom-question card. Collapsed shows the label, type
//  caption and a Required pill; expanded reveals the inline editor (label,
//  type menu, required toggle, options list) plus reorder + delete. Tokens only.
//

import SwiftUI

struct IntakeQuestionCard: View {
    @Binding var question: EditableQuestion
    let isExpanded: Bool
    let isFirst: Bool
    let isLast: Bool
    let onToggle: () -> Void
    let onDelete: () -> Void
    let onMoveUp: () -> Void
    let onMoveDown: () -> Void
    let onTypeChange: () -> Void
    let onAddOption: () -> Void
    let onRemoveOption: (Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            header
            if isExpanded { editor }
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radii.lg).stroke(Theme.Color.appBorder, lineWidth: 1))
    }

    private var header: some View {
        Button(action: onToggle) {
            HStack(spacing: Spacing.s2) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(displayLabel)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(question.label.isEmpty ? Theme.Color.appTextMuted : Theme.Color.appText)
                        .lineLimit(1)
                    Text(question.fieldType.label)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer(minLength: Spacing.s2)
                if question.required {
                    Text("Required".uppercased())
                        .font(.system(size: 9, weight: .bold))
                        .tracking(0.4)
                        .foregroundStyle(Theme.Color.primary700)
                        .padding(.horizontal, Spacing.s2)
                        .padding(.vertical, Spacing.s1)
                        .background(Theme.Color.primary50)
                        .clipShape(Capsule())
                }
                Icon(isExpanded ? .chevronUp : .chevronDown, size: 16, color: Theme.Color.appTextMuted)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var editor: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Divider().background(Theme.Color.appBorderSubtle)
            TextField("What should we cover?", text: $question.label)
                .font(Theme.Font.body)
                .foregroundStyle(Theme.Color.appText)
                .accessibilityIdentifier("scheduling.intake.labelField")
            typeMenu
            if question.fieldType.needsOptions { optionsEditor }
            Toggle(isOn: $question.required) {
                Text("Make this required")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
            }
            .tint(Theme.Color.primary600)
            footer
        }
    }

    private var typeMenu: some View {
        Menu {
            ForEach(QuestionFieldType.allCases) { type in
                Button(type.label) {
                    question.fieldType = type
                    onTypeChange()
                }
            }
        } label: {
            HStack {
                Text("Answer type")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Spacer()
                Text(question.fieldType.label)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Icon(.chevronDown, size: 14, color: Theme.Color.appTextMuted)
            }
            .contentShape(Rectangle())
        }
        .accessibilityIdentifier("scheduling.intake.typeMenu")
    }

    private var optionsEditor: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("Options")
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.appTextStrong)
            ForEach(question.options.indices, id: \.self) { index in
                HStack(spacing: Spacing.s2) {
                    Icon(.gripVertical, size: 14, color: Theme.Color.appTextMuted)
                    TextField("Option \(index + 1)", text: $question.options[index])
                        .font(Theme.Font.body)
                        .foregroundStyle(Theme.Color.appText)
                    Button { onRemoveOption(index) } label: {
                        Icon(.x, size: 14, color: Theme.Color.appTextMuted)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Remove option \(index + 1)")
                }
                .padding(.horizontal, Spacing.s2)
                .padding(.vertical, Spacing.s2)
                .background(Theme.Color.appSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                        .stroke(Theme.Color.appBorder, lineWidth: 1.5)
                )
            }
            Button(action: onAddOption) {
                HStack(spacing: Spacing.s1) {
                    Icon(.plus, size: 14, color: Theme.Color.primary600)
                    Text("Add option")
                        .pantopusTextStyle(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.primary600)
                }
            }
            .buttonStyle(.plain)
        }
    }

    private var footer: some View {
        HStack(spacing: Spacing.s4) {
            Button(action: onMoveUp) {
                Icon(.arrowUp, size: 16, color: isFirst ? Theme.Color.appTextMuted : Theme.Color.appTextSecondary)
            }
            .disabled(isFirst)
            .accessibilityLabel("Move up")
            Button(action: onMoveDown) {
                Icon(.arrowDown, size: 16, color: isLast ? Theme.Color.appTextMuted : Theme.Color.appTextSecondary)
            }
            .disabled(isLast)
            .accessibilityLabel("Move down")
            Spacer()
            Button(action: onDelete) {
                HStack(spacing: Spacing.s1) {
                    Icon(.trash, size: 15, color: Theme.Color.error)
                    Text("Delete")
                        .pantopusTextStyle(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.error)
                }
            }
            .buttonStyle(.plain)
        }
    }

    private var displayLabel: String {
        let trimmed = question.label.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "New question" : trimmed
    }
}
