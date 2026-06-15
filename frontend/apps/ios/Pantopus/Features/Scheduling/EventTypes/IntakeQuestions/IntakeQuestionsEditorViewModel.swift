//
//  IntakeQuestionsEditorViewModel.swift
//  Pantopus
//
//  Stream I2 — B3 Intake Questions Editor. Loads the event type's custom
//  questions (`GET /event-types/:id`), edits them locally (add / edit / type /
//  required / options / reorder), and replaces the whole set on save
//  (`PUT /event-types/:id/questions`). Name + email are always asked and shown
//  as locked rows. Inherits the parent event type's owner pillar.
//

import Observation
import SwiftUI

/// The intake-question field types the editor exposes. Mirrors the backend
/// `field_type` enum.
enum QuestionFieldType: String, CaseIterable, Identifiable, Sendable {
    case text
    case textarea
    case select
    case multiselect
    case checkbox
    case phone

    var id: String { rawValue }

    var label: String {
        switch self {
        case .text: "Short text"
        case .textarea: "Paragraph"
        case .select: "Dropdown"
        case .multiselect: "Multi-select"
        case .checkbox: "Checkbox"
        case .phone: "Phone"
        }
    }

    var needsOptions: Bool { self == .select || self == .multiselect }

    static func from(_ raw: String?) -> QuestionFieldType {
        QuestionFieldType(rawValue: raw ?? "text") ?? .text
    }
}

/// A locally-editable intake question.
struct EditableQuestion: Identifiable, Hashable {
    let id = UUID()
    var label: String
    var fieldType: QuestionFieldType
    var options: [String]
    var required: Bool

    var isValid: Bool {
        guard !label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        if fieldType.needsOptions {
            return options.contains { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        }
        return true
    }
}

@Observable
@MainActor
final class IntakeQuestionsEditorViewModel {
    enum Phase: Equatable {
        case loading
        case ready
        case error(message: String)
    }

    private(set) var phase: Phase = .loading

    var questions: [EditableQuestion] = []
    /// Id of the question whose inline editor is expanded.
    var expandedId: UUID?
    var saveError: String?
    private(set) var isSaving = false

    let owner: SchedulingOwner
    let eventTypeId: String
    private let client: SchedulingClient
    private var baselineSignature = ""

    init(
        owner: SchedulingOwner,
        eventTypeId: String,
        client: SchedulingClient = .shared
    ) {
        self.owner = owner
        self.eventTypeId = eventTypeId
        self.client = client
    }

    var isValid: Bool { questions.allSatisfy(\.isValid) }
    var isDirty: Bool { signature() != baselineSignature }

    // MARK: Load

    func load() async {
        if case .ready = phase { return }
        await fetch()
    }

    func reload() async { await fetch() }

    private func fetch() async {
        phase = .loading
        do {
            let response: EventTypeDetailResponse = try await client.request(
                SchedulingEndpoints.getEventType(owner: owner, id: eventTypeId)
            )
            questions = (response.questions ?? [])
                .sorted { ($0.sortOrder ?? 0) < ($1.sortOrder ?? 0) }
                .map { dto in
                    EditableQuestion(
                        label: dto.label,
                        fieldType: QuestionFieldType.from(dto.fieldType),
                        options: dto.options ?? [],
                        required: dto.required ?? false
                    )
                }
            baselineSignature = signature()
            phase = .ready
        } catch let error as SchedulingError {
            phase = .error(message: error.userMessage ?? "Couldn't load these questions.")
        } catch {
            phase = .error(message: "Couldn't load these questions.")
        }
    }

    // MARK: Editing

    func addQuestion() {
        let new = EditableQuestion(label: "", fieldType: .text, options: [], required: false)
        questions.append(new)
        expandedId = new.id
    }

    func deleteQuestion(_ id: UUID) {
        questions.removeAll { $0.id == id }
        if expandedId == id { expandedId = nil }
    }

    func toggleExpanded(_ id: UUID) {
        expandedId = expandedId == id ? nil : id
    }

    func move(_ id: UUID, by offset: Int) {
        guard let index = questions.firstIndex(where: { $0.id == id }) else { return }
        let target = index + offset
        guard questions.indices.contains(target) else { return }
        questions.swapAt(index, target)
    }

    func addOption(to id: UUID) {
        guard let index = questions.firstIndex(where: { $0.id == id }) else { return }
        questions[index].options.append("")
    }

    func removeOption(from id: UUID, at optionIndex: Int) {
        guard let index = questions.firstIndex(where: { $0.id == id }),
              questions[index].options.indices.contains(optionIndex) else { return }
        questions[index].options.remove(at: optionIndex)
    }

    /// When a question type stops needing options, drop them so the payload
    /// stays clean.
    func didChangeType(_ id: UUID) {
        guard let index = questions.firstIndex(where: { $0.id == id }) else { return }
        if !questions[index].fieldType.needsOptions {
            questions[index].options = []
        } else if questions[index].options.isEmpty {
            questions[index].options = [""]
        }
    }
}

// MARK: - Save / signature

extension IntakeQuestionsEditorViewModel {
    func signature() -> String {
        questions.map { question in
            let opts = question.options.joined(separator: ",")
            return "\(question.label)|\(question.fieldType.rawValue)|\(opts)|\(question.required)"
        }.joined(separator: "~")
    }

    /// Returns `true` when the save succeeded so the caller can pop the editor.
    func save() async -> Bool {
        guard !isSaving, isValid else { return false }
        isSaving = true
        defer { isSaving = false }
        let payload = questions.enumerated().map { index, question in
            QuestionsRequest.Question(
                label: question.label.trimmingCharacters(in: .whitespacesAndNewlines),
                fieldType: question.fieldType.rawValue,
                options: question.fieldType.needsOptions ? cleanedOptions(question.options) : [],
                required: question.required,
                sortOrder: index
            )
        }
        do {
            _ = try await client.request(
                SchedulingEndpoints.setEventTypeQuestions(owner: owner, id: eventTypeId, QuestionsRequest(questions: payload)),
                as: QuestionsResponse.self
            )
            baselineSignature = signature()
            return true
        } catch let error as SchedulingError {
            saveError = error.userMessage ?? "Couldn't save your questions."
            return false
        } catch {
            saveError = "Couldn't save your questions."
            return false
        }
    }

    private func cleanedOptions(_ options: [String]) -> [String] {
        options
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }
}
