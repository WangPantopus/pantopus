//
//  PulseComposeViewModel.swift
//  Pantopus
//
//  Backs `PulseComposeView`. Holds the intent picker state, per-intent
//  field map, identity + visibility selectors, picked-photo data, and
//  drives the `POST /api/posts` submit. Field shape mirrors
//  `createPostSchema` at `backend/routes/posts.js:196-300`.
//
// swiftlint:disable file_length type_body_length

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
    public var ctaLabel: String {
        "Post"
    }

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
    public var postAs: String {
        rawValue
    }
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

/// Edit-mode prefill state. Surfaces a shimmer in the view while the
/// post is being fetched and a retry CTA when the fetch fails.
public enum PulseComposePrefillState: Sendable, Equatable {
    /// Create mode (no prefill needed) or prefill already resolved.
    case ready
    /// Fetch in flight — view shows shimmer.
    case loading
    /// Fetch failed — view shows error with retry.
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
public let pulseComposeMaxPhotos: Int = 9

/// Backs `PulseComposeView`. Holds intent + identity + visibility,
/// per-field state, picked photos, and a submit pipeline that maps
/// to `POST /api/posts` in create mode or `PATCH /api/posts/:id` in
/// edit mode.
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

    /// Heads Up alert sub-type — required when `postType == alert`.
    public var safetyAlertKind: PulseSafetyAlertKind = .theft

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

    /// Post id when editing an existing post; `nil` in create mode.
    public let editingPostId: String?

    /// Edit-mode prefill state. `.ready` for create mode and after a
    /// successful fetch.
    public private(set) var prefillState: PulseComposePrefillState

    /// Selector + identity baselines. Updated to the prefilled values
    /// after a successful edit-mode fetch so `isDirty` compares against
    /// the post's saved pose, not the create-mode defaults.
    private var baselineIdentity: PulseComposeIdentity = .personal
    private var baselineVisibility: PulseComposeVisibility = .neighbors
    private var baselineLostFoundKind: PulseLostFoundKind = .lost
    private var baselineAnnounceAudience: PulseAnnounceAudience = .neighbors
    private var baselineAskCategory: PulseAskCategory = .handyman
    private var baselineRecommendRating: Int = 5

    private let api: APIClient

    /// Step 1 target — set when entering via the three-step flow.
    public let postingTarget: PulsePostingTarget?

    /// Step 2 purpose — nil for connections-only posts.
    public let composePurpose: PulseComposePurpose?

    init(
        intent: PulseComposeIntent = .ask,
        identity: PulseComposeIdentity = .personal,
        postingTarget: PulsePostingTarget? = nil,
        composePurpose: PulseComposePurpose? = nil,
        postId: String? = nil,
        api: APIClient = .shared
    ) {
        activeIntent = composePurpose?.legacyIntent ?? intent
        self.postingTarget = postingTarget
        self.composePurpose = composePurpose
        if let postingTarget {
            self.identity = PulseComposeIdentity(rawValue: postingTarget.postAs) ?? identity
            visibility = postingTarget.isNetworkTarget ? .connections : .neighbors
        } else {
            self.identity = identity
        }
        editingPostId = postId
        prefillState = postId == nil ? .ready : .loading
        self.api = api
        for field in PulseComposeField.allCases {
            fields[field] = FormFieldState(id: field.rawValue, originalValue: "")
        }
        baselineIdentity = self.identity
        baselineVisibility = visibility
    }

    /// True when the draft screen was reached via target/purpose pickers.
    public var isFlowMode: Bool {
        postingTarget != nil
    }

    /// True iff this view-model is wired to edit an existing post.
    public var isEditing: Bool {
        editingPostId != nil
    }

    /// Top-bar title — "Edit post" in edit mode, "New post" otherwise.
    public var displayTitle: String {
        isEditing ? "Edit post" : "New post"
    }

    /// Right-action label — "Save" in edit mode, intent-driven otherwise.
    public var ctaLabel: String {
        isEditing ? "Save" : activeIntent.ctaLabel
    }

    /// True iff the intent picker is locked (edit mode cannot change
    /// `post_type` without re-baselining the per-intent form).
    public var isIntentLocked: Bool {
        isEditing
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
    /// dirty + valid flags for the new variant's required fields. No-op
    /// in edit mode where `post_type` is fixed.
    public func selectIntent(_ intent: PulseComposeIntent) {
        guard !isIntentLocked, intent != activeIntent else { return }
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
    /// OR any selector has moved off its baseline OR a photo was picked.
    /// In create mode the baseline is the design's defaults; in edit
    /// mode `loadForEdit` rebases the baselines to the post's saved pose.
    public var isDirty: Bool {
        if photos.isNotEmpty { return true }
        if identity != baselineIdentity { return true }
        if visibility != baselineVisibility { return true }
        for field in fieldsActiveForCurrentIntent() where fields[field]?.isDirty ?? false {
            return true
        }
        switch activeIntent {
        case .ask: if askCategory != baselineAskCategory { return true }
        case .recommend: if recommendRating != baselineRecommendRating { return true }
        case .lost: if lostFoundKind != baselineLostFoundKind { return true }
        case .announce: if announceAudience != baselineAnnounceAudience { return true }
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
            .all([.required("Title"), .maxLength(FieldLimits.title)])
        case .body:
            .all([
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
            FormValidator { value in
                let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.isEmpty { return "Add the business name." }
                if trimmed.count > FieldLimits.businessName {
                    return "Must be \(FieldLimits.businessName) characters or fewer."
                }
                return nil
            }
        case .eventDate:
            FormValidator { value in
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
            .all([.required("Location"), .maxLength(FieldLimits.location)])
        case .eventCapacity:
            FormValidator { value in
                let trimmed = value.trimmingCharacters(in: .whitespaces)
                guard !trimmed.isEmpty else { return nil }
                guard let n = Int(trimmed), n > 0 else { return "Capacity must be a positive number." }
                return n > 100_000 ? "Capacity is too large." : nil
            }
        case .lostLastSeenLocation:
            .all([.required("Last seen"), .maxLength(FieldLimits.location)])
        case .lostLastSeenDate:
            .isoDateOrEmpty()
        }
    }

    // swiftlint:enable cyclomatic_complexity

    /// Which fields the active intent's form actually surfaces. Used
    /// by dirty / valid tracking + submit-time validation so untouched
    /// fields from other intents don't gate the CTA.
    func fieldsActiveForCurrentIntent() -> [PulseComposeField] {
        switch activeIntent {
        case .ask:
            [.title, .body]
        case .recommend:
            [.recommendBusiness, .body]
        case .event:
            [.title, .eventDate, .eventLocation, .eventCapacity, .body]
        case .lost:
            [.body, .lostLastSeenLocation, .lostLastSeenDate]
        case .announce:
            [.title, .body]
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

    /// Send the create or update body. Returns true on success.
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
            let postId: String?
            if let editingPostId {
                let request = buildUpdateRequest()
                let response: PostUpdateResponse = try await api.request(
                    PostsEndpoints.updatePost(id: editingPostId, body: request)
                )
                postId = response.postId ?? editingPostId
            } else {
                let request = buildRequest()
                let response: PostCreateResponse = try await api.request(
                    PostsEndpoints.createPost(body: request)
                )
                postId = response.postId
            }
            state = .success(postId: postId)
            toast = ToastMessage(text: isEditing ? "Saved" : "Posted", kind: .success)
            shouldDismiss = true
            PulsePostsRefresh.notifyPostsDidChange()
            Analytics.track(.formPulseComposeSubmit(intent: activeIntent.rawValue, result: .success))
            return true
        } catch {
            let message = (error as? APIError)?.errorDescription ?? (
                isEditing ? "Couldn't save. Try again." : "Couldn't post. Try again."
            )
            state = .error(message)
            toast = ToastMessage(text: message, kind: .error)
            Analytics.track(.formPulseComposeSubmit(intent: activeIntent.rawValue, result: .error))
            return false
        }
    }

    // MARK: - Edit-mode prefill

    /// Fetch the post being edited and seed every field + selector from
    /// the wire payload. Idempotent — safe to call again from the retry
    /// CTA when the first attempt failed.
    public func loadForEdit() async {
        guard let editingPostId else { return }
        prefillState = .loading
        do {
            let response: PostDetailResponse = try await api.request(
                PostsEndpoints.detail(id: editingPostId)
            )
            apply(prefill: response.post)
            prefillState = .ready
        } catch {
            let message = (error as? APIError)?.errorDescription
                ?? "Couldn't load this post. Try again."
            prefillState = .error(message)
        }
    }

    /// Seed every field + selector from the saved post. Re-baselines so
    /// the form starts in a non-dirty pose.
    private func apply(prefill post: PostDetailDTO) {
        let intent = PulseComposeIntent.from(feedIntent: PulseIntent.from(postType: post.postType))
        activeIntent = intent

        // Visibility — fall back to neighbors when the wire value is one
        // of the wider backend enum values we don't expose in the form.
        if let raw = post.visibility, let mapped = PulseComposeVisibility(rawValue: raw) {
            visibility = mapped
        }
        baselineVisibility = visibility

        // Identity is fixed at create time (see updatePostSchema's
        // missing `postAs`). The current creator is the signed-in user,
        // so we keep the form's `identity` selection as-is.
        baselineIdentity = identity

        // Per-intent fields.
        switch intent {
        case .ask:
            seedField(.title, value: post.title ?? "")
            seedField(.body, value: post.content)
            if let raw = post.serviceCategory, let mapped = PulseAskCategory(rawValue: raw) {
                askCategory = mapped
            }
            baselineAskCategory = askCategory
        case .recommend:
            let (stars, body) = unwrapRecommendBody(post.content)
            recommendRating = stars ?? 5
            seedField(.body, value: body)
            seedField(.recommendBusiness, value: post.dealBusinessName ?? "")
            baselineRecommendRating = recommendRating
        case .event:
            seedField(.title, value: post.title ?? "")
            seedField(.body, value: post.content)
            seedField(.eventDate, value: formatEventDateForPicker(post.eventDate))
            seedField(.eventLocation, value: post.eventVenue ?? "")
        case .lost:
            let (location, body) = unwrapLostBody(post.content)
            seedField(.body, value: body)
            seedField(.lostLastSeenLocation, value: location ?? "")
            if let raw = post.lostFoundType, let mapped = PulseLostFoundKind(rawValue: raw) {
                lostFoundKind = mapped
            }
            baselineLostFoundKind = lostFoundKind
        case .announce:
            seedField(.title, value: post.title ?? "")
            seedField(.body, value: post.content)
            if let raw = post.visibility, let mapped = PulseAnnounceAudience(rawValue: raw) {
                announceAudience = mapped
            }
            baselineAnnounceAudience = announceAudience
        }
    }

    private func seedField(_ field: PulseComposeField, value: String) {
        fields[field] = FormFieldState(id: field.rawValue, originalValue: value)
    }

    /// Reverse of `composeRecommendBody`: splits "★★★☆☆\n\n<body>" back
    /// into a star count + body. Falls back to (nil, raw) when the row
    /// is missing — preserves the saved text verbatim.
    private func unwrapRecommendBody(_ raw: String) -> (Int?, String) {
        let firstLine = raw.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false).first.map(String.init) ?? raw
        let filled = firstLine.filter { $0 == "★" }.count
        let empty = firstLine.filter { $0 == "☆" }.count
        if filled + empty == 5, !firstLine.isEmpty {
            let remainder = raw.dropFirst(firstLine.count)
            let body = remainder.drop { $0 == "\n" }
            return (filled, String(body))
        }
        return (nil, raw)
    }

    /// Reverse of `prefixLastSeen`: splits "Last seen: <loc>\n\n<body>"
    /// back into (location, body). Falls back to (nil, raw) when the
    /// prefix is missing.
    private func unwrapLostBody(_ raw: String) -> (String?, String) {
        let prefix = "Last seen: "
        guard raw.hasPrefix(prefix) else { return (nil, raw) }
        let afterPrefix = raw.dropFirst(prefix.count)
        guard let newlineRange = afterPrefix.range(of: "\n") else {
            return (String(afterPrefix), "")
        }
        let location = String(afterPrefix[..<newlineRange.lowerBound])
        let bodyStart = afterPrefix[newlineRange.upperBound...].drop { $0 == "\n" }
        return (location, String(bodyStart))
    }

    /// Convert an ISO-8601 wire date into the `yyyy-MM-dd HH:mm` shape
    /// the Event date picker emits. Returns "" when the wire value is
    /// missing or unparsable so the picker shows "Tap to pick".
    private func formatEventDateForPicker(_ raw: String?) -> String {
        guard let raw, !raw.isEmpty else { return "" }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let parsed = iso.date(from: raw) ?? ISO8601DateFormatter().date(from: raw)
        guard let parsed else { return raw }
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: parsed)
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
        let postType = effectivePostType
        let purposeTag = effectivePurpose
        let vis = effectiveVisibility
        let audience = effectiveAudience
        let postAs = postingTarget?.postAs ?? identity.postAs

        let base: PostCreateRequest
        switch activeIntent {
        case .ask:
            base = PostCreateRequest(
                content: bodyValue,
                title: titleValue.isEmpty ? nil : titleValue,
                postType: postType,
                visibility: vis,
                postAs: postAs,
                serviceCategory: askCategory.rawValue,
                audience: audience,
                purpose: purposeTag
            )
        case .recommend:
            let business = trimmedValue(.recommendBusiness)
            base = PostCreateRequest(
                content: composeRecommendBody(stars: recommendRating, body: bodyValue),
                postType: postType,
                visibility: vis,
                postAs: postAs,
                businessName: business.isEmpty ? nil : business,
                audience: audience,
                purpose: purposeTag
            )
        case .event:
            let venue = trimmedValue(.eventLocation)
            let dateRaw = trimmedValue(.eventDate)
            base = PostCreateRequest(
                content: bodyValue,
                title: titleValue.isEmpty ? nil : titleValue,
                postType: postType,
                visibility: vis,
                postAs: postAs,
                eventDate: dateRaw.isEmpty ? nil : isoDateTime(from: dateRaw),
                eventVenue: venue.isEmpty ? nil : venue,
                audience: audience,
                purpose: purposeTag
            )
        case .lost:
            let lastSeen = trimmedValue(.lostLastSeenLocation)
            base = PostCreateRequest(
                content: prefixLastSeen(body: bodyValue, location: lastSeen),
                postType: postType,
                visibility: vis,
                postAs: postAs,
                lostFoundType: lostFoundKind.rawValue,
                audience: audience,
                purpose: purposeTag
            )
        case .announce:
            let announceVis = postingTarget?.isNetworkTarget == true
                ? vis
                : announceAudience.backendVisibility
            base = PostCreateRequest(
                content: bodyValue,
                title: titleValue.isEmpty ? nil : titleValue,
                postType: postType,
                visibility: announceVis,
                postAs: postAs,
                safetyAlertKind: postType == "alert" ? safetyAlertKind.rawValue : nil,
                audience: audience,
                purpose: purposeTag
            )
        }
        return mergeTargetContext(into: base)
    }

    private var effectivePostType: String {
        if let composePurpose { return composePurpose.postType }
        if postingTarget?.isNetworkTarget == true { return "general" }
        return activeIntent.postType
    }

    private var effectivePurpose: String? {
        if let composePurpose { return composePurpose.apiPurpose }
        if postingTarget?.isNetworkTarget == true { return nil }
        return activeIntent.purpose
    }

    private var effectiveVisibility: String {
        if postingTarget?.isNetworkTarget == true { return PulseComposeVisibility.connections.rawValue }
        return visibility.rawValue
    }

    private var effectiveAudience: String {
        postingTarget?.isNetworkTarget == true ? "connections" : "nearby"
    }

    private func mergeTargetContext(into base: PostCreateRequest) -> PostCreateRequest {
        guard let target = postingTarget else { return base }
        let gps = gpsFields(for: target)
        return PostCreateRequest(
            content: base.content,
            title: base.title,
            postType: base.postType,
            visibility: base.visibility,
            postAs: base.postAs,
            mediaUrls: base.mediaUrls,
            latitude: target.latitude,
            longitude: target.longitude,
            locationName: target.isPlaceTarget ? target.displayLabel : nil,
            homeId: target.homeId,
            businessId: target.businessId,
            tags: base.tags,
            gpsTimestamp: gps.timestamp,
            gpsLatitude: gps.latitude,
            gpsLongitude: gps.longitude,
            crossPostToConnections: base.crossPostToConnections,
            showOnProfile: base.showOnProfile,
            profileVisibilityScope: base.profileVisibilityScope,
            eventDate: base.eventDate,
            eventEndDate: base.eventEndDate,
            eventVenue: base.eventVenue,
            safetyAlertKind: base.safetyAlertKind,
            behaviorDescription: base.behaviorDescription,
            dealExpiresAt: base.dealExpiresAt,
            lostFoundType: base.lostFoundType,
            contactPref: base.contactPref,
            contactPhone: base.contactPhone,
            businessName: base.businessName,
            serviceCategory: base.serviceCategory,
            audience: base.audience,
            purpose: base.purpose
        )
    }

    private struct GPSFields {
        let timestamp: String?
        let latitude: Double?
        let longitude: Double?
    }

    private func gpsFields(for target: PulsePostingTarget) -> GPSFields {
        guard case let .currentLocation(lat, lon, _) = target else {
            return GPSFields(timestamp: nil, latitude: nil, longitude: nil)
        }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return GPSFields(timestamp: formatter.string(from: Date()), latitude: lat, longitude: lon)
    }

    /// Build the `PATCH /api/posts/:id` body from the active intent's
    /// field values + selectors. Sends only the keys `updatePostSchema`
    /// accepts (no `postAs` / `audience` / `purpose` / `businessName`).
    /// Visibility comes from the announce audience for announce posts.
    func buildUpdateRequest() -> PostUpdateRequest {
        let bodyValue = trimmedValue(.body)
        let titleValue = trimmedValue(.title)
        switch activeIntent {
        case .ask:
            return PostUpdateRequest(
                content: bodyValue,
                title: titleValue,
                visibility: visibility.rawValue,
                serviceCategory: askCategory.rawValue
            )
        case .recommend:
            let business = trimmedValue(.recommendBusiness)
            return PostUpdateRequest(
                content: composeRecommendBody(stars: recommendRating, body: bodyValue),
                visibility: visibility.rawValue,
                dealBusinessName: business.isEmpty ? nil : business
            )
        case .event:
            let venue = trimmedValue(.eventLocation)
            let dateRaw = trimmedValue(.eventDate)
            return PostUpdateRequest(
                content: bodyValue,
                title: titleValue,
                visibility: visibility.rawValue,
                eventDate: dateRaw.isEmpty ? nil : isoDateTime(from: dateRaw),
                eventVenue: venue.isEmpty ? nil : venue
            )
        case .lost:
            let lastSeen = trimmedValue(.lostLastSeenLocation)
            return PostUpdateRequest(
                content: prefixLastSeen(body: bodyValue, location: lastSeen),
                visibility: visibility.rawValue,
                lostFoundType: lostFoundKind.rawValue
            )
        case .announce:
            return PostUpdateRequest(
                content: bodyValue,
                title: titleValue,
                visibility: announceAudience.backendVisibility
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
    var isNotEmpty: Bool {
        !isEmpty
    }
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
