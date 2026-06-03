//
//  PostGigV1ViewModel.swift
//  Pantopus
//
//  A13.8 — legacy single-screen gig composer. `submit()` posts the form to
//  `POST /api/gigs` (`GigsEndpoints.create`) — the same create path the V2
//  "Magic" composer (`GigComposeViewModel`) and the gigs feed use. The V2
//  `/api/gigs/magic-*` draft flow is separate and untouched here.
//

import Foundation
import Observation

public enum PostGigV1PriceType: String, CaseIterable, Identifiable, Sendable {
    case flat
    case hourly
    case free

    public var id: String {
        rawValue
    }

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
        scheduledAt: Date = Date().addingTimeInterval(86400),
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

    public var id: String {
        field.rawValue
    }
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

    private let api: APIClient
    private let referenceNow: Date?

    public init(
        api: APIClient = .shared,
        initialState: PostGigV1State = PostGigV1State(),
        referenceNow: Date? = nil
    ) {
        self.api = api
        state = initialState
        self.referenceNow = referenceNow
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

    /// Validate, then create the gig via `POST /api/gigs`
    /// (`GigsEndpoints.create`). On success the backend-issued gig id is
    /// stored in `state.postedGigId` and returned so the caller can route
    /// to the freshly posted task; on failure the message surfaces via
    /// `loadState = .error(_)` and `retry()` returns to the still-filled
    /// form.
    @discardableResult
    public func submit() async -> String? {
        guard state.loadState == .ready, !state.isSubmitting else { return nil }
        let errors = validate(form: state.form)
        guard errors.isEmpty else {
            state.validationErrors = errors
            return nil
        }
        state.validationErrors = []
        state.isSubmitting = true
        defer { state.isSubmitting = false }
        do {
            let response: CreateGigResponse = try await api.request(
                GigsEndpoints.create(buildCreateBody(from: state.form))
            )
            state.postedGigId = response.gig.id
            return response.gig.id
        } catch {
            let message = (error as? APIError)?.errorDescription
                ?? "Couldn't post your task. Please try again."
            state.loadState = .error(message)
            return nil
        }
    }

    /// Map the V1 form onto the `POST /api/gigs` body. The legacy composer
    /// collects a free-text location only, so we send it as the `custom`
    /// location `address` with a `(0, 0)` placeholder coordinate — the same
    /// fallback the V2 composer uses when it has no geocode
    /// (`GigComposeViewModel.fallbackLocation`). Pay-type maps
    /// flat→`fixed`, hourly→`hourly`, free→`offers`; the backend rejects a
    /// non-positive price so free rides on a `1` sentinel (mirrors the V2
    /// open-to-bids handling).
    private func buildCreateBody(from form: PostGigV1Form) -> CreateGigBody {
        let trimmedPrice = Double(form.price.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0
        let payType: String
        let price: Double
        switch form.priceType {
        case .flat:
            payType = "fixed"
            price = trimmedPrice > 0 ? trimmedPrice : 1
        case .hourly:
            payType = "hourly"
            price = trimmedPrice > 0 ? trimmedPrice : 1
        case .free:
            payType = "offers"
            price = 1
        }
        return CreateGigBody(
            title: form.title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: form.description.trimmingCharacters(in: .whitespacesAndNewlines),
            category: form.category == .all ? nil : form.category.rawValue,
            price: price,
            payType: payType,
            scheduleType: "scheduled",
            scheduledStart: ISO8601DateFormatter().string(from: form.scheduledAt),
            taskFormat: nil,
            attachments: nil,
            location: CreateGigLocation(
                mode: "custom",
                latitude: 0,
                longitude: 0,
                address: form.location.trimmingCharacters(in: .whitespacesAndNewlines)
            )
        )
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
