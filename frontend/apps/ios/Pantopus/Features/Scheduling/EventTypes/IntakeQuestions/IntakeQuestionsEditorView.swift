//
//  IntakeQuestionsEditorView.swift
//  Pantopus
//
//  Stream I2 — B3 Intake Questions Editor (routed full screen). Name + email
//  show as locked "always asked" rows; custom questions are add / edit / type /
//  required / options / reorder, saved as a whole set on Save. Wrapped in
//  FormShell with the parent owner's pillar.
//

import SwiftUI

struct IntakeQuestionsEditorView: View {
    @State private var viewModel: IntakeQuestionsEditorViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: IntakeQuestionsEditorViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    private var isReady: Bool {
        if case .ready = viewModel.phase { return true }
        return false
    }

    var body: some View {
        content
            .background(Theme.Color.appBg)
            .navigationTitle("Intake questions")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(isReady ? .hidden : .visible, for: .navigationBar)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .task { await viewModel.load() }
            .accessibilityIdentifier("scheduling.intakeQuestions")
            .alert("Couldn't save", isPresented: saveErrorPresented) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.saveError ?? "")
            }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.phase {
        case .loading:
            loadingSkeleton
        case let .error(message):
            ErrorState(headline: "Couldn't load these questions", message: message) {
                await viewModel.reload()
            }
        case .ready:
            editor
        }
    }

    private var editor: some View {
        FormShell(
            title: "Intake questions",
            leading: .back,
            rightActionLabel: "Save",
            isValid: viewModel.isValid,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSaving,
            onClose: { dismiss() },
            onCommit: { Task { if await viewModel.save() { dismiss() } } }
        ) {
            helper
            lockedDefaults
            questionList
            addButton
        }
    }

    private var helper: some View {
        Text("Name and email are always asked. Add anything else you need.")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.s4)
    }

    private var lockedDefaults: some View {
        FormFieldGroup("Always asked") {
            lockedRow("Name")
            Divider().background(Theme.Color.appBorderSubtle)
            lockedRow("Email")
        }
    }

    private func lockedRow(_ title: String) -> some View {
        HStack(spacing: Spacing.s3) {
            Icon(.lock, size: 16, color: Theme.Color.appTextMuted).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Text("Always asked")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer()
        }
    }

    @ViewBuilder
    private var questionList: some View {
        if !viewModel.questions.isEmpty {
            VStack(spacing: Spacing.s3) {
                ForEach($viewModel.questions) { $question in
                    IntakeQuestionCard(
                        question: $question,
                        isExpanded: viewModel.expandedId == question.id,
                        isFirst: viewModel.questions.first?.id == question.id,
                        isLast: viewModel.questions.last?.id == question.id,
                        onToggle: { viewModel.toggleExpanded(question.id) },
                        onDelete: { viewModel.deleteQuestion(question.id) },
                        onMoveUp: { viewModel.move(question.id, by: -1) },
                        onMoveDown: { viewModel.move(question.id, by: 1) },
                        onTypeChange: { viewModel.didChangeType(question.id) },
                        onAddOption: { viewModel.addOption(to: question.id) },
                        onRemoveOption: { viewModel.removeOption(from: question.id, at: $0) }
                    )
                }
            }
            .padding(.horizontal, Spacing.s4)
        }
    }

    private var addButton: some View {
        Button(action: { viewModel.addQuestion() }) {
            HStack(spacing: Spacing.s2) {
                Icon(.plus, size: 16, color: Theme.Color.primary600)
                Text("Add a question")
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.primary600)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.s3)
            .background(Theme.Color.primary50)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, Spacing.s4)
        .accessibilityIdentifier("scheduling.intake.addQuestion")
    }

    private var loadingSkeleton: some View {
        ScrollView {
            VStack(spacing: Spacing.s3) {
                ForEach(0..<3, id: \.self) { _ in
                    Shimmer(height: 64, cornerRadius: Radii.lg).padding(.horizontal, Spacing.s4)
                }
            }
            .padding(.vertical, Spacing.s4)
        }
    }

    private var saveErrorPresented: Binding<Bool> {
        Binding(get: { viewModel.saveError != nil }, set: { if !$0 { viewModel.saveError = nil } })
    }
}
