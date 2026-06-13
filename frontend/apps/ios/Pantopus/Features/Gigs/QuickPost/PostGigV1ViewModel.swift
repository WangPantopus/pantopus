//
//  PostGigV1ViewModel.swift
//  Pantopus
//
//  A13.8 — legacy single-screen gig composer. `submit()` posts the form to
//  `POST /api/gigs` (`GigsEndpoints.create`) — the same create path the V2
//  "Magic" composer (`GigComposeViewModel`) and the gigs feed use. The V2
//  `/api/gigs/magic-*` draft flow is separate and untouched here.
//
//  Phase 4 (A13.8 polish): photos ride the real `POST /api/files/upload`
//  pipeline (per-tile uploading / failed-retry / uploaded states, same
//  mechanism as the V2 wizard's P15.5 handling), and an optional
//  `editGigId` flips the screen into edit mode — `GET /api/gigs/:id`
//  prefills every field and submit goes to `PATCH /api/gigs/:id`.
//

import Foundation
import Observation

// swiftlint:disable file_length

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

/// One photo tile riding the real upload pipeline
/// (`POST /api/files/upload`, field `file`, `file_type: "gig_photo"`).
/// The raw bytes back the grid thumbnail; `status` drives the per-tile
/// spinner / retry / uploaded chrome. Edit-mode prefill rehydrates tiles
/// from the gig's stored `attachments` (URL only, no bytes).
public struct PostGigV1Photo: Identifiable, Equatable, Sendable {
    public enum Status: Equatable, Sendable {
        case uploading
        case failed
        case uploaded(url: String)
    }

    public let id: String
    public let imageData: Data
    public var status: Status

    public init(id: String, imageData: Data = Data(), status: Status) {
        self.id = id
        self.imageData = imageData
        self.status = status
    }

    public var uploadedURL: String? {
        if case let .uploaded(url) = status { return url }
        return nil
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
    private let uploader: MultipartUploader
    private let referenceNow: Date?

    /// When set the screen is the "Edit gig" surface: `load()` prefills
    /// from `GET /api/gigs/:id` and `submit()` goes to `PATCH`.
    public let editGigId: String?

    /// One-shot guard around the edit-mode prefill fetch.
    private var editLoaded = false

    /// Exact coordinates captured at edit-load. PATCHing `location` with
    /// the V1 free-text address rides these so the stored point survives
    /// (the backend requires lat/lng on the nested location object).
    private var editOrigin: (latitude: Double, longitude: Double)?

    /// In-flight photo uploads keyed by photo id.
    private var uploadTasks: [String: Task<Void, Never>] = [:]

    /// Public entry point — carries no `APIClient` (the client and `.shared`
    /// are module-internal) so views / previews / sample data construct the
    /// composer without referencing it.
    public convenience init(
        initialState: PostGigV1State = PostGigV1State(),
        referenceNow: Date? = nil,
        editGigId: String? = nil
    ) {
        self.init(
            api: .shared,
            initialState: initialState,
            referenceNow: referenceNow,
            editGigId: editGigId
        )
    }

    /// Designated init — module-internal because `APIClient` is. Tests
    /// inject a stubbed client + uploader here.
    init(
        api: APIClient,
        uploader: MultipartUploader = .shared,
        initialState: PostGigV1State = PostGigV1State(),
        referenceNow: Date? = nil,
        editGigId: String? = nil
    ) {
        self.api = api
        self.uploader = uploader
        state = initialState
        self.referenceNow = referenceNow
        self.editGigId = editGigId
        if editGigId != nil {
            state.loadState = .loading
        }
    }

    public var isEditMode: Bool {
        editGigId != nil
    }

    /// Top-bar title — "Post gig" for create, "Edit gig" for edit.
    public var screenTitle: String {
        isEditMode ? "Edit gig" : "Post gig"
    }

    /// Right-action CTA label — "Post" for create, "Save" for edit.
    public var commitLabel: String {
        isEditMode ? "Save" : "Post"
    }

    /// True while any photo upload is still in flight — gates the
    /// Post / Save CTA so a half-uploaded gig can't ship.
    public var hasUploadsInFlight: Bool {
        state.form.photos.contains { $0.status == .uploading }
    }

    public var isPostEnabled: Bool {
        canAttemptSubmit && (state.form.hasAnyInput || !state.validationErrors.isEmpty)
    }

    public var canAttemptSubmit: Bool {
        state.loadState == .ready && !state.isSubmitting && !hasUploadsInFlight
    }

    /// Edit mode: fetch the gig and prefill every field. Create mode:
    /// no-op (the form starts ready).
    public func load() async {
        guard let editGigId, !editLoaded else { return }
        state.loadState = .loading
        do {
            let response: GigDetailResponse = try await api.request(GigsEndpoints.detail(id: editGigId))
            prefill(from: response.gig)
            editLoaded = true
            state.loadState = .ready
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load this gig. Please try again."
            state.loadState = .error(message)
        }
    }

    public func retry() {
        if isEditMode, !editLoaded {
            // The edit prefill never landed — refetch instead of showing
            // an empty form against a PATCH submit.
            Task { await load() }
        } else {
            state.loadState = .ready
        }
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

    // MARK: - Photo uploads (same pipeline as the V2 wizard's P15.5)

    /// Add a picked photo and immediately upload it in the background.
    /// Caps the grid at `PostGigV1SampleData.maxPhotos` — extra calls
    /// are ignored. The first photo is the gig's cover.
    public func addPhotoData(_ data: Data) {
        guard state.form.photos.count < PostGigV1SampleData.maxPhotos, !data.isEmpty else { return }
        let photo = PostGigV1Photo(id: UUID().uuidString, imageData: data, status: .uploading)
        state.form.photos.append(photo)
        startUpload(photoId: photo.id)
    }

    /// Tap-to-retry on a failed tile.
    public func retryUpload(id: String) {
        guard let index = state.form.photos.firstIndex(where: { $0.id == id }),
              state.form.photos[index].status == .failed else { return }
        state.form.photos[index].status = .uploading
        startUpload(photoId: id)
    }

    /// Remove a photo (any state). Cancels an in-flight upload.
    public func removePhoto(id: String) {
        uploadTasks[id]?.cancel()
        uploadTasks[id] = nil
        state.form.photos.removeAll { $0.id == id }
    }

    private func startUpload(photoId: String) {
        uploadTasks[photoId] = Task { [weak self] in
            await self?.performUpload(photoId: photoId)
        }
    }

    /// Push one photo through `POST /api/files/upload` (field `file`,
    /// `file_type: "gig_photo"` — the V2 wizard's exact mechanism). The
    /// resulting URL rides the create/update body's `attachments`.
    func performUpload(photoId: String) async {
        guard let photo = state.form.photos.first(where: { $0.id == photoId }) else { return }
        do {
            let response = try await uploader.uploadFile(
                MultipartFile(
                    fieldName: "file",
                    filename: "gig-\(photoId.prefix(6)).jpg",
                    mimeType: "image/jpeg",
                    data: photo.imageData
                ),
                formFields: ["file_type": "gig_photo"]
            )
            guard let index = state.form.photos.firstIndex(where: { $0.id == photoId }) else { return }
            state.form.photos[index].status = .uploaded(url: response.file.url)
        } catch {
            guard let index = state.form.photos.firstIndex(where: { $0.id == photoId }) else { return }
            state.form.photos[index].status = .failed
        }
    }

    #if DEBUG
    /// Test hook — wait for every kicked upload task to settle.
    func awaitUploadsForTesting() async {
        for task in uploadTasks.values {
            await task.value
        }
    }
    #endif

    // MARK: - Submit

    /// Validate, then create (`POST /api/gigs`) or — in edit mode —
    /// update (`PATCH /api/gigs/:id`) the gig. On success the
    /// backend-issued gig id is stored in `state.postedGigId` and
    /// returned so the caller can route to the task; on failure the
    /// message surfaces via `loadState = .error(_)` and `retry()`
    /// returns to the still-filled form.
    @discardableResult
    public func submit() async -> String? {
        guard canAttemptSubmit else { return nil }
        let errors = validate(form: state.form)
        guard errors.isEmpty else {
            state.validationErrors = errors
            return nil
        }
        state.validationErrors = []
        state.isSubmitting = true
        defer { state.isSubmitting = false }
        do {
            let response: CreateGigResponse = if let editGigId {
                try await api.request(
                    GigsEndpoints.update(id: editGigId, body: buildUpdateBody(from: state.form))
                )
            } else {
                try await api.request(
                    GigsEndpoints.create(buildCreateBody(from: state.form))
                )
            }
            state.postedGigId = response.gig.id
            return response.gig.id
        } catch {
            let fallback = isEditMode
                ? "Couldn't save your changes. Please try again."
                : "Couldn't post your task. Please try again."
            let message = (error as? APIError)?.errorDescription ?? fallback
            state.loadState = .error(message)
            return nil
        }
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
}

// MARK: - Body building, validation, edit prefill

extension PostGigV1ViewModel {
    /// Pay-type maps flat→`fixed`, hourly→`hourly`, free→`offers` with a
    /// true `price: 0` — the backend schema accepts zero
    /// (`Joi.number().min(0)`, `backend/routes/gigs.js:428`).
    private func payTypeAndPrice(from form: PostGigV1Form) -> (payType: String, price: Double) {
        let trimmedPrice = Double(form.price.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0
        switch form.priceType {
        case .flat:
            return ("fixed", trimmedPrice > 0 ? trimmedPrice : 1)
        case .hourly:
            return ("hourly", trimmedPrice > 0 ? trimmedPrice : 1)
        case .free:
            return ("offers", 0)
        }
    }

    private func uploadedAttachmentURLs(from form: PostGigV1Form) -> [String] {
        form.photos.compactMap(\.uploadedURL)
    }

    /// Map the V1 form onto the `POST /api/gigs` body. The legacy composer
    /// collects a free-text location only, so we send it as the `custom`
    /// location `address` with a `(0, 0)` placeholder coordinate — the same
    /// fallback the V2 composer uses when it has no geocode
    /// (`GigComposeViewModel.fallbackLocation`).
    private func buildCreateBody(from form: PostGigV1Form) -> CreateGigBody {
        let pay = payTypeAndPrice(from: form)
        let attachments = uploadedAttachmentURLs(from: form)
        return CreateGigBody(
            title: form.title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: form.description.trimmingCharacters(in: .whitespacesAndNewlines),
            category: form.category == .all ? nil : form.category.rawValue,
            price: pay.price,
            payType: pay.payType,
            scheduleType: "scheduled",
            scheduledStart: ISO8601DateFormatter().string(from: form.scheduledAt),
            taskFormat: nil,
            attachments: attachments.isEmpty ? nil : attachments,
            location: CreateGigLocation(
                mode: "custom",
                latitude: 0,
                longitude: 0,
                address: form.location.trimmingCharacters(in: .whitespacesAndNewlines)
            )
        )
    }

    /// Map the V1 form onto the `PATCH /api/gigs/:id` body — same field
    /// names as create. `attachments` always rides (an empty array
    /// clears removed photos); `location` only rides when edit-load
    /// captured real coordinates, so the stored point is preserved.
    private func buildUpdateBody(from form: PostGigV1Form) -> UpdateGigBody {
        let pay = payTypeAndPrice(from: form)
        var location: CreateGigLocation?
        if let editOrigin {
            location = CreateGigLocation(
                mode: "custom",
                latitude: editOrigin.latitude,
                longitude: editOrigin.longitude,
                address: form.location.trimmingCharacters(in: .whitespacesAndNewlines)
            )
        }
        return UpdateGigBody(
            title: form.title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: form.description.trimmingCharacters(in: .whitespacesAndNewlines),
            category: form.category == .all ? nil : form.category.rawValue,
            price: pay.price,
            payType: pay.payType,
            scheduleType: "scheduled",
            scheduledStart: ISO8601DateFormatter().string(from: form.scheduledAt),
            attachments: uploadedAttachmentURLs(from: form),
            location: location
        )
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

    // MARK: - Edit-mode prefill

    private func prefill(from gig: GigDTO) {
        var form = PostGigV1Form()
        form.category = GigsCategory(rawValue: gig.category ?? "") ?? .all
        form.title = gig.title
        form.description = String((gig.description ?? "").prefix(PostGigV1SampleData.descriptionMaxLength))
        switch gig.payType {
        case "offers":
            form.priceType = .free
            form.price = ""
        case "hourly":
            form.priceType = .hourly
            form.price = Self.priceText(gig.price)
        default:
            form.priceType = .flat
            form.price = Self.priceText(gig.price)
        }
        if let scheduledStart = gig.scheduledStart, let date = Self.parseISO(scheduledStart) {
            form.scheduledAt = date
        }
        form.location = gig.exactAddress ?? gig.pickupAddress ?? ""
        form.photos = (gig.attachments ?? [])
            .prefix(PostGigV1SampleData.maxPhotos)
            .map { PostGigV1Photo(id: $0, status: .uploaded(url: $0)) }
        if let latitude = gig.location?.latitude ?? gig.latitude,
           let longitude = gig.location?.longitude ?? gig.longitude {
            editOrigin = (latitude, longitude)
        }
        state.form = form
        state.validationErrors = []
    }

    private static func priceText(_ price: Double?) -> String {
        guard let price, price > 0 else { return "" }
        if price.truncatingRemainder(dividingBy: 1) == 0 {
            return String(Int(price))
        }
        return String(price)
    }

    private static func parseISO(_ string: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: string) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: string)
    }
}
