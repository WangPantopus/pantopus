//
//  PulseComposeViewModel.swift
//  Pantopus
//
//  Backs `PulseComposeView`. Holds the intent picker state, per-intent
//  field map, identity + visibility selectors, picked-photo data, and
//  drives the `POST /api/posts` submit. Field shape mirrors
//  `createPostSchema` at `backend/routes/posts.js:196-300`.
//
// swiftlint:disable file_length

import Foundation
import Observation

/// Compose-form variants. Mirrors the user-facing intents (every
/// `PulseIntent` case except `.all`, which is a feed-row filter
/// sentinel).
public enum PulseComposeIntent: String, CaseIterable, Sendable, Hashable {
    case ask
    case recommend
    case event
    case lost
    case announce

    /// Human-readable label for the intent chip + form title.
    public var label: String {
        switch self {
        case .ask: "Ask"
        case .recommend: "Recommend"
        case .event: "Event"
        case .lost: "Lost & Found"
        case .announce: "Announce"
        }
    }

    /// Backend `post_type` value sent on `POST /api/posts`.
    public var postType: String {
        switch self {
        case .ask: "ask_local"
        case .recommend: "recommendation"
        case .event: "event"
        case .lost: "lost_found"
        case .announce: "local_update"
        }
    }

    /// Backend `purpose` tag (mirrors postType for v1.2 sortability).
    public var purpose: String {
        switch self {
        case .ask: "ask"
        case .recommend: "recommend"
        case .event: "event"
        case .lost: "lost_found"
        case .announce: "local_update"
        }
    }

    /// Right-action label for the Form top-bar.
    public var ctaLabel: String { "Post" }

    /// Bridges `PulseIntent` (the feed enum) into the compose subset.
    /// `.all` falls back to `.ask`.
    public static func from(feedIntent: PulseIntent) -> PulseComposeIntent {
        switch feedIntent {
        case .all, .ask: .ask
        case .recommend: .recommend
        case .event: .event
        case .lost: .lost
        case .announce: .announce
        }
    }

    /// Parse the string carried by the `HubRoute.composePost` case.
    public static func from(rawValue: String) -> PulseComposeIntent {
        PulseComposeIntent(rawValue: rawValue) ?? .ask
    }

    /// Pantopus icon used inside the intent picker chip.
    public var icon: PantopusIcon {
        switch self {
        case .ask: .helpCircle
        case .recommend: .thumbsUp
        case .event: .calendar
        case .lost: .search
        case .announce: .megaphone
        }
    }
}

/// Stable identifiers for every text field surfaced by the compose form.
/// Selector state (category chip / rating stars / lost-vs-found toggle
/// / announce audience) lives on the view-model directly so the field
/// map only carries values the user can type into.
public enum PulseComposeField: String, CaseIterable, Sendable {
    case title
    case body
    case recommendBusiness
    case eventDate
    case eventLocation
    case eventCapacity
    case lostLastSeenLocation
    case lostLastSeenDate
}

/// Identity the post will be authored under.
public enum PulseComposeIdentity: String, CaseIterable, Sendable, Hashable {
    case personal
    case home
    case business

    public var label: String {
        switch self {
        case .personal: "Personal"
        case .home: "Home"
        case .business: "Business"
        }
    }

    /// Maps to backend `postAs` enum on `createPostSchema`.
    public var postAs: String { rawValue }
}

/// Visibility scope. Mirrors the three options surfaced in the form
/// — backend accepts the wider `public / neighborhood / followers /
/// connections / radius / city / private` set; we expose the three
/// the design calls for.
public enum PulseComposeVisibility: String, CaseIterable, Sendable, Hashable {
    case neighbors = "neighborhood"
    case connections
    case publicFeed = "public"

    public var label: String {
        switch self {
        case .neighbors: "Neighbors"
        case .connections: "Connections"
        case .publicFeed: "Public"
        }
    }
}

/// Lost / Found segmented control state.
public enum PulseLostFoundKind: String, CaseIterable, Sendable, Hashable {
    case lost
    case found
}

/// Announce-audience chip option.
public enum PulseAnnounceAudience: String, CaseIterable, Sendable, Hashable {
    case neighbors
    case followers
    case publicFeed = "public"

    public var label: String {
        switch self {
        case .neighbors: "Neighbors"
        case .followers: "Followers"
        case .publicFeed: "Public"
        }
    }
}

/// Ask-category chip option. Backend stores this in `service_category`.
public enum PulseAskCategory: String, CaseIterable, Sendable, Hashable {
    case handyman
    case cleaning
    case advice
    case other

    public var label: String {
        switch self {
        case .handyman: "Handyman"
        case .cleaning: "Cleaning"
        case .advice: "Advice"
        case .other: "Other"
        }
    }
}

/// One picked photo. Carries the loaded image bytes plus a stable id
/// so SwiftUI can ForEach a removable thumbnail grid.
public struct PulseComposePhoto: Identifiable, Sendable, Hashable {
    public let id: UUID
    public let data: Data

    public init(id: UUID = UUID(), data: Data) {
        self.id = id
        self.data = data
    }
}

/// Render state for `PulseComposeView`.
public enum PulseComposeState: Sendable, Equatable {
    case idle
    case submitting
    case success(postId: String?)
    case error(String)
}

/// Max characters per field — mirrors `createPostSchema` bounds.
private enum FieldLimits {
    static let title: Int = 255
    static let bodyMin: Int = 1
    static let bodyMax: Int = 5000
    static let location: Int = 255
    static let businessName: Int = 255
}

/// Maximum number of photos the picker accepts.
public let pulseComposeMaxPhotos: Int = 4

/// Backs `PulseComposeView`. Holds intent + identity + visibility,
/// per-field state, picked photos, and a submit pipeline that maps
/// to `POST /api/posts`.
@Observable
@MainActor
public final class PulseComposeViewModel {
    public private(set) var state: PulseComposeState = .idle

    /// Active intent — drives which sub-form renders below the picker.
    public var activeIntent: PulseComposeIntent

    /// Author identity. Defaults to `.personal`.
    public var identity: PulseComposeIdentity = .personal

    /// Visibility scope. Defaults to `.neighbors`.
    public var visibility: PulseComposeVisibility = .neighbors

    /// Lost & Found type — drives the segmented control.
    public var lostFoundKind: PulseLostFoundKind = .lost

    /// Announce audience chip selection.
    public var announceAudience: PulseAnnounceAudience = .neighbors

    /// Ask category chip selection.
    public var askCategory: PulseAskCategory = .handyman

    /// Star rating for the Recommend intent (1-5).
    public var recommendRating: Int = 5

    /// Field states keyed by `PulseComposeField`.
    public var fields: [PulseComposeField: FormFieldState] = [:]

    /// Currently selected photos — capped at `pulseComposeMaxPhotos`.
    public private(set) var photos: [PulseComposePhoto] = []

    /// Toast surfaced by the view for errors + validation hints.
    public var toast: ToastMessage?

    /// Shake trigger fired when validation rejects a submit.
    public private(set) var shakeTrigger: Int = 0

    /// Increments + then resets so the view knows when to dismiss.
    public private(set) var shouldDismiss: Bool = false

    private let api: APIClient

    init(
        intent: PulseComposeIntent = .ask,
        identity: PulseComposeIdentity = .personal,
        api: APIClient = .shared
    ) {
        activeIntent = intent
        self.identity = identity
        self.api = api
        for field in PulseComposeField.allCases {
            fields[field] = FormFieldState(id: field.rawValue, originalValue: "")
        }
    }

    // MARK: - Field updates

    public func update(_ field: PulseComposeField, to value: String) {
        guard var snapshot = fields[field] else { return }
        snapshot.value = value
        snapshot.touched = true
        snapshot.error = validator(for: field).validate(value)
        fields[field] = snapshot
    }

    /// Switch active intent without losing draft text — only refreshes
    /// dirty + valid flags for the new variant's required fields.
    public func selectIntent(_ intent: PulseComposeIntent) {
        guard intent != activeIntent else { return }
        activeIntent = intent
    }

    /// Replace the current photo set. Callers must respect
    /// `pulseComposeMaxPhotos`.
    public func setPhotos(_ photos: [PulseComposePhoto]) {
        self.photos = Array(photos.prefix(pulseComposeMaxPhotos))
    }

    /// Append a single photo; drops it silently when at capacity.
    public func append(photo: PulseComposePhoto) {
        guard photos.count < pulseComposeMaxPhotos else { return }
        photos.append(photo)
    }

    public func remove(photo id: UUID) {
        photos.removeAll { $0.id == id }
    }

    // MARK: - Dirty + validity

    /// True iff any user-editable field has diverged from its baseline
    /// OR any selector has moved off its default OR a photo was picked.
    public var isDirty: Bool {
        if photos.isNotEmpty { return true }
        if identity != .personal { return true }
        if visibility != .neighbors { return true }
        for field in fieldsActiveForCurrentIntent() where fields[field]?.isDirty ?? false {
            return true
        }
        // Per-intent selector dirty checks.
        switch activeIntent {
        case .ask: if askCategory != .handyman { return true }
        case .recommend: if recommendRating != 5 { return true }
        case .lost: if lostFoundKind != .lost { return true }
        case .announce: if announceAudience != .neighbors { return true }
        case .event: break
        }
        return false
    }

    /// True iff every active field passes its validator.
    public var isValid: Bool {
        fieldsActiveForCurrentIntent().allSatisfy { (fields[$0]?.error) == nil }
    }

    public var isSubmitting: Bool {
        if case .submitting = state { return true }
        return false
    }

    // swiftlint:disable cyclomatic_complexity

    /// Validators run once per submit; per-field error messages are
    /// also computed live on `update(_:to:)`. The 8-case switch maps
    /// 1:1 to `PulseComposeField` so the complexity is inherent — any
    /// indirection would hide the field-to-rule mapping that mirrors
    /// the iOS / Android validator parity.
    private func validator(for field: PulseComposeField) -> FormValidator {
        switch field {
        case .title:
            return .all([.required("Title"), .maxLength(FieldLimits.title)])
        case .body:
            return .all([
                .required("Description"),
                FormValidator { value in
                    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
                    if trimmed.count < FieldLimits.bodyMin { return "Add a description." }
                    if trimmed.count > FieldLimits.bodyMax {
                        return "Description must be \(FieldLimits.bodyMax) characters or fewer."
                    }
                    return nil
                }
            ])
        case .recommendBusiness:
            return FormValidator { value in
                let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.isEmpty { return "Add the business name." }
                if trimmed.count > FieldLimits.businessName {
                    return "Must be \(FieldLimits.businessName) characters or fewer."
                }
                return nil
            }
        case .eventDate:
            return FormValidator { value in
                let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.isEmpty { return "Event date is required." }
                // Accept either `yyyy-MM-dd` (clear-and-typed) or
                // `yyyy-MM-dd HH:mm` (DatePicker-emitted) shapes — both
                // get normalized to ISO-8601 in `isoDateTime(from:)`.
                let formatter = DateFormatter()
                formatter.calendar = Calendar(identifier: .iso8601)
                formatter.locale = Locale(identifier: "en_US_POSIX")
                formatter.timeZone = TimeZone(secondsFromGMT: 0)
                for shape in ["yyyy-MM-dd HH:mm", "yyyy-MM-dd"] {
                    formatter.dateFormat = shape
                    if formatter.date(from: trimmed) != nil { return nil }
                }
                if trimmed.contains("T"), ISO8601DateFormatter().date(from: trimmed) != nil { return nil }
                return "Use YYYY-MM-DD or pick a date."
            }
        case .eventLocation:
            return .all([.required("Location"), .maxLength(FieldLimits.location)])
        case .eventCapacity:
            return FormValidator { value in
                let trimmed = value.trimmingCharacters(in: .whitespaces)
                guard !trimmed.isEmpty else { return nil }
                guard let n = Int(trimmed), n > 0 else { return "Capacity must be a positive number." }
                return n > 100_000 ? "Capacity is too large." : nil
            }
        case .lostLastSeenLocation:
            return .all([.required("Last seen"), .maxLength(FieldLimits.location)])
        case .lostLastSeenDate:
            return .isoDateOrEmpty()
        }
    }
    // swiftlint:enable cyclomatic_complexity

    /// Which fields the active intent's form actually surfaces. Used
    /// by dirty / valid tracking + submit-time validation so untouched
    /// fields from other intents don't gate the CTA.
    func fieldsActiveForCurrentIntent() -> [PulseComposeField] {
        switch activeIntent {
        case .ask:
            return [.title, .body]
        case .recommend:
            return [.recommendBusiness, .body]
        case .event:
            return [.title, .eventDate, .eventLocation, .eventCapacity, .body]
        case .lost:
            return [.body, .lostLastSeenLocation, .lostLastSeenDate]
        case .announce:
            return [.title, .body]
        }
    }

    /// Touch every active field and return the id of the first that
    /// fails — used by the submit path to shake-highlight invalids.
    @discardableResult
    func validateAll() -> PulseComposeField? {
        var firstInvalid: PulseComposeField?
        for field in fieldsActiveForCurrentIntent() {
            guard var snapshot = fields[field] else { continue }
            let message = validator(for: field).validate(snapshot.value)
            snapshot.touched = true
            snapshot.error = message
            fields[field] = snapshot
            if firstInvalid == nil, message != nil { firstInvalid = field }
        }
        return firstInvalid
    }

    // MARK: - Submit

    /// Send the `POST /api/posts` body. Returns true on success.
    @discardableResult
    public func submit() async -> Bool {
        if case .submitting = state { return false }
        if let invalidField = validateAll() {
            shakeTrigger &+= 1
            toast = ToastMessage(text: "Fix the highlighted field.", kind: .error)
            Analytics.track(.formPulseComposeValidationError(
                intent: activeIntent.rawValue,
                field: invalidField.rawValue
            ))
            return false
        }
        if !NetworkMonitor.shared.isOnline {
            toast = ToastMessage(
                text: "You're offline. Try again when you're back online.",
                kind: .error
            )
            Analytics.track(.formPulseComposeSubmit(intent: activeIntent.rawValue, result: .error))
            return false
        }
        state = .submitting
        do {
            let request = buildRequest()
            let response: PostCreateResponse = try await api.request(
                PostsEndpoints.createPost(body: request)
            )
            state = .success(postId: response.postId)
            toast = ToastMessage(text: "Posted", kind: .success)
            shouldDismiss = true
            Analytics.track(.formPulseComposeSubmit(intent: activeIntent.rawValue, result: .success))
            return true
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't post. Try again."
            state = .error(message)
            toast = ToastMessage(text: message, kind: .error)
            Analytics.track(.formPulseComposeSubmit(intent: activeIntent.rawValue, result: .error))
            return false
        }
    }

    /// Called by the view once the dismissal has been honored.
    public func acknowledgeDismiss() {
        shouldDismiss = false
    }

    // MARK: - Request assembly

    /// Build the `POST /api/posts` body from the active intent's
    /// field values + selectors. Skips empty optional fields so the
    /// backend's Joi schema doesn't trip over `null` / `""`.
    func buildRequest() -> PostCreateRequest {
        let bodyValue = trimmedValue(.body)
        let titleValue = trimmedValue(.title)
        switch activeIntent {
        case .ask:
            return PostCreateRequest(
                content: bodyValue,
                title: titleValue.isEmpty ? nil : titleValue,
                postType: activeIntent.postType,
                visibility: visibility.rawValue,
                postAs: identity.postAs,
                serviceCategory: askCategory.rawValue,
                purpose: activeIntent.purpose
            )
        case .recommend:
            let business = trimmedValue(.recommendBusiness)
            return PostCreateRequest(
                content: composeRecommendBody(stars: recommendRating, body: bodyValue),
                postType: activeIntent.postType,
                visibility: visibility.rawValue,
                postAs: identity.postAs,
                businessName: business.isEmpty ? nil : business,
                purpose: activeIntent.purpose
            )
        case .event:
            let venue = trimmedValue(.eventLocation)
            let dateRaw = trimmedValue(.eventDate)
            return PostCreateRequest(
                content: bodyValue,
                title: titleValue.isEmpty ? nil : titleValue,
                postType: activeIntent.postType,
                visibility: visibility.rawValue,
                postAs: identity.postAs,
                eventDate: dateRaw.isEmpty ? nil : isoDateTime(from: dateRaw),
                eventVenue: venue.isEmpty ? nil : venue,
                purpose: activeIntent.purpose
            )
        case .lost:
            let lastSeen = trimmedValue(.lostLastSeenLocation)
            return PostCreateRequest(
                content: prefixLastSeen(body: bodyValue, location: lastSeen),
                postType: activeIntent.postType,
                visibility: visibility.rawValue,
                postAs: identity.postAs,
                lostFoundType: lostFoundKind.rawValue,
                purpose: activeIntent.purpose
            )
        case .announce:
            return PostCreateRequest(
                content: bodyValue,
                title: titleValue.isEmpty ? nil : titleValue,
                postType: activeIntent.postType,
                visibility: announceAudience.backendVisibility,
                postAs: identity.postAs,
                audience: announceAudience.rawValue,
                purpose: activeIntent.purpose
            )
        }
    }

    private func trimmedValue(_ field: PulseComposeField) -> String {
        (fields[field]?.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func composeRecommendBody(stars: Int, body: String) -> String {
        let clamped = max(1, min(5, stars))
        let row = String(repeating: "★", count: clamped) + String(repeating: "☆", count: 5 - clamped)
        return body.isEmpty ? row : "\(row)\n\n\(body)"
    }

    private func prefixLastSeen(body: String, location: String) -> String {
        guard !location.isEmpty else { return body }
        return "Last seen: \(location)\n\n\(body)"
    }

    /// Normalize an event date string to ISO-8601 (yyyy-MM-dd'T'HH:mm:ssZ).
    /// Accepts `yyyy-MM-dd HH:mm` (DatePicker-emitted) and plain
    /// `yyyy-MM-dd` (treated as 09:00 UTC) so the picker + clear-and-type
    /// paths both round-trip cleanly.
    private func isoDateTime(from raw: String) -> String {
        if raw.contains("T") { return raw }
        let parser = DateFormatter()
        parser.calendar = Calendar(identifier: .iso8601)
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.timeZone = TimeZone(secondsFromGMT: 0)
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        for shape in ["yyyy-MM-dd HH:mm", "yyyy-MM-dd"] {
            parser.dateFormat = shape
            if let parsed = parser.date(from: raw) {
                let bumped = shape == "yyyy-MM-dd" ? parsed.addingTimeInterval(9 * 3600) : parsed
                return formatter.string(from: bumped)
            }
        }
        return raw
    }
}

private extension Array {
    var isNotEmpty: Bool { !isEmpty }
}

private extension PulseAnnounceAudience {
    /// Map the announce-audience chip to a backend visibility value.
    var backendVisibility: String {
        switch self {
        case .neighbors: "neighborhood"
        case .followers: "followers"
        case .publicFeed: "public"
        }
    }
}
