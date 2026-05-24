//
//  PostGigV1ViewModel.swift
//  Pantopus
//
//  A13.8 — legacy single-screen gig composer. This is intentionally
//  client-side only while the native mobile surface runs without backend
//  create calls in this phase.
//

import Foundation
import Observation

public enum PostGigV1PriceType: String, CaseIterable, Identifiable, Sendable {
    case flat
    case hourly
    case free

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .flat: "Flat"
        case .hourly: "Hourly"
        case .free: "Free"
        }
    }

    public var unitLabel: String? {
        switch self {
        case .flat: "flat"
        case .hourly: "/ hr"
        case .free: nil
        }
    }
}

public enum PostGigV1PhotoTone: String, Sendable {
    case sofa
    case stairs
    case street
    case neutral
}

public struct PostGigV1Photo: Identifiable, Equatable, Sendable {
    public let id: String
    public let tone: PostGigV1PhotoTone

    public init(id: String, tone: PostGigV1PhotoTone) {
        self.id = id
        self.tone = tone
    }
}

public struct PostGigV1Form: Equatable, Sendable {
    public var category: GigsCategory
    public var title: String
    public var description: String
    public var price: String
    public var priceType: PostGigV1PriceType
    public var scheduledAt: Date
    public var location: String
    public var photos: [PostGigV1Photo]

    public init(
        category: GigsCategory = .all,
        title: String = "",
        description: String = "",
        price: String = "",
        priceType: PostGigV1PriceType = .flat,
        scheduledAt: Date = Date().addingTimeInterval(86_400),
        location: String = "",
        photos: [PostGigV1Photo] = []
    ) {
        self.category = category
        self.title = title
        self.description = description
        self.price = price
        self.priceType = priceType
        self.scheduledAt = scheduledAt
        self.location = location
        self.photos = photos
    }

    public var hasAnyInput: Bool {
        category != .all ||
            !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !price.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            priceType != .flat ||
            !location.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !photos.isEmpty
    }
}

public enum PostGigV1Field: String, Sendable {
    case category
    case title
    case description
    case price
    case dateTime
    case location
}

public struct PostGigV1ValidationError: Identifiable, Equatable, Sendable {
    public let field: PostGigV1Field
    public let message: String

    public var id: String { field.rawValue }
}

public enum PostGigV1LoadState: Equatable, Sendable {
    case loading
    case empty
    case ready
    case error(String)
}

public struct PostGigV1State: Equatable, Sendable {
    public var loadState: PostGigV1LoadState
    public var form: PostGigV1Form
    public var validationErrors: [PostGigV1ValidationError]
    public var isSubmitting: Bool
    public var postedGigId: String?

    public init(
        loadState: PostGigV1LoadState = .ready,
        form: PostGigV1Form = PostGigV1Form(),
        validationErrors: [PostGigV1ValidationError] = [],
        isSubmitting: Bool = false,
        postedGigId: String? = nil
    ) {
        self.loadState = loadState
        self.form = form
        self.validationErrors = validationErrors
        self.isSubmitting = isSubmitting
        self.postedGigId = postedGigId
    }
}

@Observable
@MainActor
public final class PostGigV1ViewModel {
    public private(set) var state: PostGigV1State

    private let referenceNow: Date?
    private let idGenerator: @Sendable () -> String

    public init(
        initialState: PostGigV1State = PostGigV1State(),
        referenceNow: Date? = nil,
        idGenerator: @escaping @Sendable () -> String = { "gig-v1-\(UUID().uuidString.prefix(8))" }
    ) {
        state = initialState
        self.referenceNow = referenceNow
        self.idGenerator = idGenerator
    }

    public var isPostEnabled: Bool {
        state.loadState == .ready &&
            !state.isSubmitting &&
            (state.form.hasAnyInput || !state.validationErrors.isEmpty)
    }

    public var canAttemptSubmit: Bool {
        state.loadState == .ready && !state.isSubmitting
    }

    public func retry() {
        state.loadState = .ready
    }

    public func startFromEmpty() {
        state = PostGigV1State(form: PostGigV1Form())
    }

    public func seedFilledSample() {
        state = PostGigV1State(form: PostGigV1SampleData.filledForm)
    }

    public func updateCategory(_ category: GigsCategory) {
        state.form.category = category
    }

    public func updateTitle(_ title: String) {
        state.form.title = title
    }

    public func updateDescription(_ description: String) {
        state.form.description = String(description.prefix(PostGigV1SampleData.descriptionMaxLength))
    }

    public func updatePrice(_ price: String) {
        let filtered = price.filter { $0.isNumber || $0 == "." }
        state.form.price = filtered
    }

    public func updatePriceType(_ priceType: PostGigV1PriceType) {
        state.form.priceType = priceType
        if priceType == .free {
            state.form.price = ""
        }
    }

    public func updateScheduledAt(_ date: Date) {
        state.form.scheduledAt = date
    }

    public func updateLocation(_ location: String) {
        state.form.location = location
    }

    public func addPlaceholderPhoto() {
        guard state.form.photos.count < PostGigV1SampleData.maxPhotos else { return }
        let tones: [PostGigV1PhotoTone] = [.sofa, .stairs, .street, .neutral]
        let index = state.form.photos.count
        state.form.photos.append(PostGigV1Photo(
            id: "photo-\(index + 1)",
            tone: tones[index % tones.count]
        ))
    }

    public func removePhoto(id: String) {
        state.form.photos.removeAll { $0.id == id }
    }

    @discardableResult
    public func submit() -> String? {
        guard state.loadState == .ready else { return nil }
        let errors = validate(form: state.form)
        guard errors.isEmpty else {
            state.validationErrors = errors
            return nil
        }

        let gigId = idGenerator()
        state.validationErrors = []
        state.postedGigId = gigId
        return gigId
    }

    public func error(for field: PostGigV1Field) -> String? {
        state.validationErrors.first { $0.field == field }?.message
    }

    public func dateLabel(for date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "EEE, MMM d · h:mm a"
        return formatter.string(from: date)
    }

    private func validate(form: PostGigV1Form) -> [PostGigV1ValidationError] {
        var errors: [PostGigV1ValidationError] = []
        if form.category == .all {
            errors.append(.init(field: .category, message: "Choose a category."))
        }
        if form.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            errors.append(.init(field: .title, message: "Title is required."))
        }
        if form.description.trimmingCharacters(in: .whitespacesAndNewlines).count < PostGigV1SampleData.descriptionMinLength {
            errors.append(.init(
                field: .description,
                message: "Description must be at least \(PostGigV1SampleData.descriptionMinLength) characters."
            ))
        }
        if form.priceType != .free {
            let trimmed = form.price.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty {
                errors.append(.init(field: .price, message: "Enter a price, or pick Free."))
            } else if (Double(trimmed) ?? 0) <= 0 {
                errors.append(.init(field: .price, message: "Price must be greater than zero."))
            }
        }
        if form.scheduledAt <= (referenceNow ?? Date()) {
            errors.append(.init(field: .dateTime, message: "Date is in the past. Pick a future time."))
        }
        if form.location.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            errors.append(.init(field: .location, message: "Add a pickup or meetup location."))
        }
        return errors
    }
}
