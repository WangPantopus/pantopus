//
//  GigDetailViewModel.swift
//  Pantopus
//
//  Fetches `GET /api/gigs/:id` (+ `/:gigId/bids` when the viewer owns
//  the gig) and projects the result into a `ContentDetailContent` for
//  the `ContentDetailShell`. The sticky-dock primary CTA places a bid
//  via `POST /api/gigs/:gigId/bids`.
//

// swiftlint:disable file_length

import Foundation
import Observation

@Observable
@MainActor
public final class GigDetailViewModel {
    public private(set) var state: ContentDetailState = .loading

    /// Set to true when the viewer is the gig owner — `bidsResult`
    /// is then fetched and rendered.
    public private(set) var viewerIsOwner: Bool = false

    /// Cached raw gig used by the place-bid + message flows.
    public private(set) var rawGig: GigDTO?

    /// True when the signed-in viewer is the gig's assigned worker and the
    /// task is `in_progress` — the only state in which the worker can mark
    /// delivery (mirrors the backend `mark-completed` precondition and
    /// MyBids' "Mark complete" gate). Drives the dock's "Mark as delivered"
    /// affordance + which sheet the host presents.
    public private(set) var canMarkDelivered: Bool = false

    /// Block 3D — true when the signed-in viewer is the poster of a completed,
    /// owner-confirmed gig with an assigned worker (the `/tip` preconditions).
    /// Drives the "Send a tip" dock CTA.
    public private(set) var canTip: Bool = false

    /// Transient tip-flow status, surfaced by the view as a toast.
    public enum TipStatus: Sendable, Equatable {
        case idle
        case sending
        case succeeded
        case canceled
        case failed(message: String)
    }

    public private(set) var tipStatus: TipStatus = .idle

    // MARK: - Structured Q&A

    public private(set) var questions: [GigQuestionDTO] = []
    public private(set) var questionsLoading = false
    public var newQuestionText = ""
    public var answeringQuestionId: String?
    public var answerDraftText = ""
    public private(set) var questionSubmitting = false
    public private(set) var answerSubmitting = false

    public var canAskQuestion: Bool {
        Self.currentSignedInUserId() != nil && !viewerIsOwner
    }

    private let gigId: String
    private let api: APIClient
    private let uploader: MultipartUploader
    private let checkout: CheckoutCoordinator
    private let currentUserId: String?

    init(
        gigId: String,
        api: APIClient = .shared,
        uploader: MultipartUploader = .shared,
        checkout: CheckoutCoordinator = CheckoutCoordinator(),
        currentUserId: String? = GigDetailViewModel.currentSignedInUserId()
    ) {
        self.gigId = gigId
        self.api = api
        self.uploader = uploader
        self.checkout = checkout
        self.currentUserId = currentUserId
    }

    /// The signed-in user id, or `nil` when signed out. Mirrors
    /// `ListingDetailViewModel.currentSignedInUserId`.
    static func currentSignedInUserId() -> String? {
        if case let .signedIn(user) = AuthManager.shared.state {
            return user.id
        }
        return nil
    }

    public func load() async {
        state = .loading
        do {
            let detail: GigDetailResponse = try await api.request(GigsEndpoints.detail(id: gigId))
            rawGig = detail.gig
            viewerIsOwner = currentUserId != nil && detail.gig.userId == currentUserId
            canMarkDelivered = Self.viewerCanMarkDelivered(gig: detail.gig, currentUserId: currentUserId)
            canTip = Self.viewerCanTip(gig: detail.gig, viewerIsOwner: viewerIsOwner)
            var bids: [GigBidDTO] = []
            if viewerIsOwner {
                if let bidsResponse: GigBidsResponse = try? await api.request(GigsEndpoints.bids(gigId: gigId)) {
                    bids = bidsResponse.bids
                }
            }
            state = .loaded(Self.project(
                gig: detail.gig,
                bids: bids,
                canMarkDelivered: canMarkDelivered,
                canTip: canTip,
                viewerUserId: currentUserId
            ))
            await loadQuestions()
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load gig."
            state = .error(message: message)
        }
    }

    /// The worker self-completion gate: signed-in viewer is the assigned
    /// worker (`accepted_by`) and the task is `in_progress`.
    static func viewerCanMarkDelivered(gig: GigDTO, currentUserId: String?) -> Bool {
        guard let me = currentUserId, !me.isEmpty,
              let worker = gig.acceptedBy, worker == me else { return false }
        return (gig.status ?? "").lowercased() == "in_progress"
    }

    /// The tip gate (Block 3D): the poster, on a completed + owner-confirmed gig
    /// with an assigned worker. Mirrors the `/tip` route's preconditions.
    static func viewerCanTip(gig: GigDTO, viewerIsOwner: Bool) -> Bool {
        guard viewerIsOwner else { return false }
        guard let worker = gig.acceptedBy, !worker.isEmpty else { return false }
        guard (gig.status ?? "").lowercased() == "completed" else { return false }
        return (gig.ownerConfirmedAt ?? "").isEmpty == false
    }

    /// Send a tip of `amountCents` to the worker: create the tip payment,
    /// present PaymentSheet via the shared `CheckoutCoordinator`, then
    /// best-effort reconcile + refresh the gig. We never mark the tip paid
    /// locally — the refresh-status + webhook reconcile server-side.
    public func sendTip(amountCents: Int) async {
        guard canTip else { return }
        tipStatus = .sending
        do {
            let response: TipResponse = try await api.request(
                PaymentsEndpoints.tip(body: TipRequest(gigId: gigId, amount: amountCents))
            )
            let outcome = await checkout.present(response.sheetParams)
            switch outcome {
            case .paid:
                if let paymentId = response.paymentId {
                    _ = try? await api.request(
                        PaymentsEndpoints.tipRefreshStatus(paymentId: paymentId),
                        as: TipRefreshStatusResponse.self
                    )
                }
                tipStatus = .succeeded
                await load()
            case .canceled:
                tipStatus = .canceled
            case let .declined(message), let .failed(message):
                tipStatus = .failed(message: message)
            }
        } catch {
            tipStatus = .failed(
                message: (error as? APIError)?.errorDescription ?? "Couldn't send the tip."
            )
        }
    }

    /// Clear the tip toast once the view has shown it.
    public func clearTipStatus() {
        tipStatus = .idle
    }

    /// Upload each proof photo via `POST /api/files/upload`, then mark the
    /// task completed with the resulting URLs + the optional note. Returns
    /// `true` so the Delivery Proof sheet can flip to its SUBMITTED
    /// confirmation; refreshes the task (status → completed) on success.
    @discardableResult
    public func submitDeliveryProof(photos: [DeliveryProofPhoto], note: String?) async -> Bool {
        guard let gig = rawGig, !photos.isEmpty else { return false }
        do {
            var urls: [String] = []
            for photo in photos {
                let response = try await uploader.uploadFile(
                    MultipartFile(
                        fieldName: "file",
                        filename: photo.filename,
                        mimeType: photo.mimeType,
                        data: photo.data
                    ),
                    formFields: ["file_type": "gig_completion", "visibility": "private"]
                )
                urls.append(response.file.url)
            }
            _ = try await api.request(
                GigsEndpoints.markCompleted(gigId: gig.id, note: note, photos: urls),
                as: EmptyResponse.self
            )
            await load()
            return true
        } catch {
            return false
        }
    }

    /// Place a bid with the caller-supplied amount + message + proposed
    /// time. Returns `true` on success so the host can dismiss its
    /// bid-entry sheet.
    @discardableResult
    public func placeBid(amount: Double, message: String?, proposedTime: String? = nil) async -> Bool {
        do {
            let _: PlaceBidResponse = try await api.request(
                GigsEndpoints.placeBid(
                    gigId: gigId,
                    body: PlaceBidBody(
                        bidAmount: amount,
                        message: message,
                        proposedTime: proposedTime
                    )
                )
            )
            await load()
            return true
        } catch {
            return false
        }
    }

    // MARK: - Structured Q&A

    func loadQuestions() async {
        questionsLoading = true
        defer { questionsLoading = false }
        do {
            let response: GigQuestionsResponse = try await api.request(GigsEndpoints.questions(gigId: gigId))
            questions = response.questions
        } catch {
            questions = []
        }
    }

    @discardableResult
    func submitQuestion() async -> String? {
        let trimmed = newQuestionText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 5 else { return "Question must be at least 5 characters." }
        guard !questionSubmitting else { return nil }
        questionSubmitting = true
        defer { questionSubmitting = false }
        do {
            _ = try await api.request(
                GigsEndpoints.askQuestion(gigId: gigId, body: AskGigQuestionBody(question: trimmed))
            )
            newQuestionText = ""
            await loadQuestions()
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't post question."
        }
    }

    @discardableResult
    func submitAnswer(questionId: String) async -> String? {
        let trimmed = answerDraftText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "Answer can't be empty." }
        guard !answerSubmitting else { return nil }
        answerSubmitting = true
        defer { answerSubmitting = false }
        do {
            _ = try await api.request(
                GigsEndpoints.answerQuestion(
                    gigId: gigId,
                    questionId: questionId,
                    body: AnswerGigQuestionBody(answer: trimmed)
                )
            )
            answeringQuestionId = nil
            answerDraftText = ""
            await loadQuestions()
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't post answer."
        }
    }

    func beginAnswering(_ questionId: String) {
        answeringQuestionId = questionId
        answerDraftText = ""
    }

    func cancelAnswering() {
        answeringQuestionId = nil
        answerDraftText = ""
    }

    /// Opens (or creates) the gig chat room and returns a conversation
    /// destination for the inbox stack. Mirrors web/RN `getGigChatRoom`.
    public func resolveChatDestination() async -> InboxConversationDestination? {
        guard let gig = rawGig else { return nil }
        let name = gig.creator?.resolvedDisplayName ?? gig.title
        let initials = Self.initialsFromName(name)
        let verified = gig.creator?.resolvedVerified ?? false
        do {
            let response: GigChatRoomResponse = try await api.request(GigsEndpoints.chatRoom(gigId: gigId))
            return InboxConversationDestination(
                mode: .room(id: response.roomId),
                displayName: name,
                initials: initials,
                identityKind: nil,
                verified: verified
            )
        } catch {
            return nil
        }
    }
}

// MARK: - Projection

extension GigDetailViewModel {
    /// Top-level projection. V2 ("Magic Task") is the default surface for
    /// open gigs; legacy V1 is kept only when `is_v2 == false` or when an
    /// awarded terminal state has no explicit V2 flag (sparse awarded UI).
    static func project(
        gig: GigDTO,
        bids: [GigBidDTO],
        canMarkDelivered: Bool = false,
        canTip: Bool = false,
        viewerUserId: String? = nil
    ) -> ContentDetailContent {
        shouldProjectTaskV2(gig: gig)
            ? projectTaskV2(
                gig: gig, bids: bids, canMarkDelivered: canMarkDelivered, canTip: canTip,
                viewerUserId: viewerUserId
            )
            : projectGigV1(gig: gig, bids: bids, canTip: canTip, viewerUserId: viewerUserId)
    }

    /// Poster card for the "Posted By" section — avatar, name, @handle,
    /// and a chat affordance (hidden when the viewer is the poster).
    static func posterCounterparty(gig: GigDTO, viewerUserId: String?) -> ContentDetailCounterparty? {
        guard let posterId = gig.userId, !posterId.isEmpty else { return nil }
        let creator = gig.creator
        let name = creator?.resolvedDisplayName ?? "Neighbor"
        let handle = creator?.resolvedHandle.map { "@\($0)" }
        let showsButton = viewerUserId.map { $0 != posterId } ?? true
        return ContentDetailCounterparty(
            displayName: name,
            initials: initialsFromName(name),
            avatarUrl: creator?.resolvedAvatarURL,
            identityKind: nil,
            verified: creator?.resolvedVerified ?? false,
            rating: nil,
            trailing: handle,
            showsMessageButton: showsButton
        )
    }

    static func initialsFromName(_ name: String) -> String {
        name.split(separator: " ").prefix(2).compactMap { $0.first.map(String.init) }.joined().uppercased()
    }

    /// `is_v2 == false` → V1; `true` → V2; `null` → V2 unless awarded.
    private static func shouldProjectTaskV2(gig: GigDTO) -> Bool {
        if gig.isV2 == false { return false }
        if gig.isV2 == true { return true }
        return !isAwarded(gig)
    }

    /// The dock for the poster on a completed gig — primary becomes "Send a
    /// tip" (Block 3D), keeping "Message" as the secondary.
    static let tipDock = ContentDetailDock(
        secondary: ContentDetailDockButton(label: "Message", icon: .send),
        primary: ContentDetailDockButton(label: "Send a tip", icon: .handCoins)
    )

    /// V2 dock: tip (poster, completed) → mark-delivered (worker, in-progress)
    /// → place-bid. Factored out to keep `projectTaskV2` under the body-length
    /// limit.
    private static func taskV2Dock(canMarkDelivered: Bool, canTip: Bool) -> ContentDetailDock {
        if canTip { return tipDock }
        if canMarkDelivered {
            return ContentDetailDock(
                secondary: ContentDetailDockButton(label: "Message", icon: .send),
                primary: ContentDetailDockButton(label: "Mark as delivered", icon: .checkCheck)
            )
        }
        return ContentDetailDock(
            secondary: ContentDetailDockButton(label: "Message", icon: .send),
            primary: ContentDetailDockButton(label: "Place bid")
        )
    }

    // MARK: V2 (Task) — Magic Task surface

    private static func projectTaskV2(
        gig: GigDTO,
        bids: [GigBidDTO],
        canMarkDelivered: Bool,
        canTip: Bool = false,
        viewerUserId: String? = nil
    ) -> ContentDetailContent {
        let category = GigsCategory.from(backendKey: gig.category)
        let bidCount = gig.bidCount ?? bids.count
        let metaPieces: [String] = [
            distanceLabel(gig.distanceMiles),
            relativeAge(gig.createdAt).map { "posted \($0) ago" }
        ].compactMap { $0 }
        let priceLine = gig.price.map { gigPriceLabel($0, payType: gig.payType) }
        let hero = ContentDetailHero(
            title: gig.title,
            categoryChip: ContentDetailCategoryChip(label: category.label, category: category),
            meta: metaPieces.isEmpty ? nil : metaPieces.joined(separator: " · "),
            priceLine: priceLine,
            priceCaption: gig.price != nil ? "budget · cash or transfer" : nil
        )
        var modules: [ContentDetailModule] = []
        if let body = gig.description, !body.isEmpty {
            modules.append(.description(ContentDetailDescription(title: "What needs doing", icon: .clipboardList, body: body)))
        }
        modules.append(contentsOf: locationModules(gig))
        if let mapModule = locationMapModule(for: gig) {
            modules.append(mapModule)
        }
        if let scheduledStart = gig.scheduledStart, !scheduledStart.isEmpty {
            modules.append(.captionedText(ContentDetailCaptionedText(
                title: "When", icon: .calendar, label: formatScheduledStart(scheduledStart)
            )))
        } else if let deadline = gig.deadline, !deadline.isEmpty {
            modules.append(.captionedText(ContentDetailCaptionedText(
                title: "By", icon: .calendar, label: formatScheduledStart(deadline)
            )))
        }
        modules.append(.capsuleRow(ContentDetailCapsuleRow(capsules: [
            ContentDetailPill(label: "Verified address", icon: .shieldCheck, tone: .info),
            ContentDetailPill(label: "Local Pantopus job", icon: .check, tone: .success)
        ])))
        if bidCount > 0, !bids.isEmpty {
            modules.append(.bids(ContentDetailBidsModule(title: "\(bidCount) bids", bids: bids.map { projectBid($0) })))
        } else {
            modules.append(.callout(ContentDetailCallout(
                identifier: "be-first",
                style: .empty,
                tone: .dashed,
                icon: .handCoins,
                iconTone: .primary,
                title: "Be the first to bid",
                subtitle: "Fresh posts usually get a hire in the first hour. First three bids land at the top of the list.",
                footerPill: "neighbors viewing"
            )))
        }
        // The assigned worker viewing an in-progress task gets the
        // completion affordance (→ Delivery Proof sheet) instead of the
        // bidder dock; everyone else sees the standard "Place bid" path.
        let statusLabel = bidCount > 0 ? "Open · \(bidCount) \(bidCount == 1 ? "bid" : "bids")" : "Open · No bids yet"
        let statusPill = canMarkDelivered
            ? ContentDetailPill(label: "In progress", icon: .circle, tone: .warning)
            : ContentDetailPill(label: statusLabel, icon: .circle, tone: .warning)
        let dock = taskV2Dock(canMarkDelivered: canMarkDelivered, canTip: canTip)
        return ContentDetailContent(
            kind: .gig,
            statusPill: statusPill,
            hero: hero,
            statStrip: statRows(gig),
            counterparty: posterCounterparty(gig: gig, viewerUserId: viewerUserId),
            modules: modules,
            trustCapsules: [],
            dock: dock
        )
    }

    /// Pickup → drop-off two-stop card when both ends are known, else the
    /// single-address "Where" row.
    private static func locationModules(_ gig: GigDTO) -> [ContentDetailModule] {
        if let pickup = gig.pickupAddress, !pickup.isEmpty,
           let dropoff = gig.dropoffAddress, !dropoff.isEmpty {
            let stops = [
                ContentDetailTwoStop.Stop(
                    letter: "A", tone: .primary, address: pickup, distance: distanceLabel(gig.distanceMiles)
                ),
                ContentDetailTwoStop.Stop(letter: "B", tone: .success, address: dropoff, distance: nil)
            ]
            let card = ContentDetailTwoStop(title: "Pickup → drop-off", icon: .mapPin, stops: stops)
            return [.twoStop(card)]
        }
        if let pickup = gig.pickupAddress, !pickup.isEmpty {
            let row = ContentDetailDetailRow(
                title: "Where",
                sectionIcon: .mapPin,
                rowIcon: .mapPin,
                label: pickup,
                trailing: distanceLabel(gig.distanceMiles)
            )
            return [.detailRow(row)]
        }
        return []
    }

    /// Privacy-aware mini map when coordinates are available.
    private static func locationMapModule(for gig: GigDTO) -> ContentDetailModule? {
        guard let coordinate = resolveMapCoordinate(gig) else { return nil }
        let approximate = !(gig.locationUnlocked ?? false)
        let footnote = approximate
            ? "Approximate area — the circle covers ~500m around the actual location. Tap to explore."
            : "Tap to pan and zoom the map."
        let category = GigsCategory.from(backendKey: gig.category)
        return .locationMap(ContentDetailLocationMap(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            isApproximate: approximate,
            footnote: footnote,
            category: category
        ))
    }

    private static func resolveMapCoordinate(_ gig: GigDTO) -> (latitude: Double, longitude: Double)? {
        if let lat = gig.latitude, let lng = gig.longitude { return (lat, lng) }
        if let lat = gig.location?.latitude, let lng = gig.location?.longitude { return (lat, lng) }
        if let lat = gig.approxLocation?.latitude, let lng = gig.approxLocation?.longitude {
            return (lat, lng)
        }
        return nil
    }

    // MARK: V1 (legacy Gig) — sparse + awarded terminal state

    private static func projectGigV1(
        gig: GigDTO,
        bids: [GigBidDTO],
        canTip: Bool = false,
        viewerUserId: String? = nil
    ) -> ContentDetailContent {
        let awarded = isAwarded(gig)
        let bidCount = gig.bidCount ?? bids.count
        let metaPieces: [String] = [
            distanceLabel(gig.distanceMiles),
            gig.scheduledStart.flatMap { $0.isEmpty ? nil : formatScheduledStart($0) }
        ].compactMap { $0 }
        let priceLine = gig.price.map { gigPriceLabel($0, payType: gig.payType) }
        let hero = ContentDetailHero(
            title: gig.title,
            categoryChip: nil,
            meta: metaPieces.isEmpty ? nil : metaPieces.joined(separator: " · "),
            priceLine: priceLine,
            priceCaption: gig.price == nil ? nil : (awarded ? "winning bid" : "budget")
        )
        var modules: [ContentDetailModule] = []
        if awarded {
            let awardTitle = awardWinnerName(gig: gig, bids: bids).map { "Awarded to \($0)" } ?? "Awarded"
            let awardSubtitle = [relativeAge(gig.acceptedAt).map { "\($0) ago" }, "bidding now closed"]
                .compactMap { $0 }
                .joined(separator: " · ")
            modules.append(.callout(ContentDetailCallout(
                identifier: "awarded",
                style: .banner,
                tone: .success,
                icon: .check,
                iconTone: .success,
                title: awardTitle,
                subtitle: awardSubtitle
            )))
        }
        if let body = gig.description, !body.isEmpty {
            modules.append(.description(ContentDetailDescription(title: "Description", icon: nil, body: body)))
        }
        if let mapModule = locationMapModule(for: gig) {
            modules.append(mapModule)
        }
        if !bids.isEmpty {
            modules.append(.bids(ContentDetailBidsModule(
                title: "\(bidCount) bids",
                sub: awarded ? "closed" : nil,
                bids: bids.map { projectBid($0, acceptedBy: awarded ? gig.acceptedBy : nil) }
            )))
        }
        return ContentDetailContent(
            kind: .gig,
            statusPill: awarded
                ? ContentDetailPill(label: "Awarded", icon: .check, tone: .success)
                : ContentDetailPill(label: "Open", icon: .circle, tone: .warning),
            hero: hero,
            statStrip: [],
            counterparty: posterCounterparty(gig: gig, viewerUserId: viewerUserId),
            modules: modules,
            trustCapsules: [],
            dock: canTip
                ? tipDock
                : (awarded
                    ? ContentDetailDock(
                        secondary: ContentDetailDockButton(label: "Message", icon: .send),
                        primary: ContentDetailDockButton(label: "Bidding closed", icon: .lock, enabled: false)
                    )
                    : ContentDetailDock(
                        secondary: ContentDetailDockButton(label: "Message", icon: .send),
                        primary: ContentDetailDockButton(label: "Place bid")
                    ))
        )
    }

    private static func isAwarded(_ gig: GigDTO) -> Bool {
        guard let accepted = gig.acceptedBy, !accepted.isEmpty else { return false }
        switch gig.status {
        case "accepted", "awarded", "completed", "in_progress": return true
        default: return false
        }
    }

    private static func awardWinnerName(gig: GigDTO, bids: [GigBidDTO]) -> String? {
        let winner = bids.first { $0.userId == gig.acceptedBy }
        return winner?.bidder?.resolvedDisplayName
    }

    private static func statRows(_ gig: GigDTO) -> [ContentDetailStat] {
        var out: [ContentDetailStat] = []
        if let scheduled = gig.scheduledStart, !scheduled.isEmpty {
            out.append(ContentDetailStat(top: formatScheduledDate(scheduled), bottom: "fixed date"))
        } else if let schedule = gig.scheduleType, !schedule.isEmpty {
            out.append(ContentDetailStat(top: schedule.replacingOccurrences(of: "_", with: " ").capitalized, bottom: "schedule"))
        }
        if let archetype = gig.taskArchetype, !archetype.isEmpty {
            out.append(ContentDetailStat(top: archetype.replacingOccurrences(of: "_", with: " ").capitalized, bottom: "type"))
        }
        if let engagement = gig.engagementMode, !engagement.isEmpty {
            out.append(ContentDetailStat(top: engagement.replacingOccurrences(of: "_", with: " ").capitalized, bottom: "mode"))
        }
        return Array(out.prefix(3))
    }

    private static func projectBid(_ bid: GigBidDTO, acceptedBy: String? = nil) -> ContentDetailBidRow {
        let name = bid.bidder?.resolvedDisplayName ?? "Bidder"
        let initials = name.split(separator: " ").prefix(2).compactMap { $0.first.map(String.init) }.joined().uppercased()
        let amount = bid.bidAmount ?? bid.amount ?? 0
        let amountLabel = amount.truncatingRemainder(dividingBy: 1) == 0 ? "$\(Int(amount))" : String(format: "$%.2f", amount)
        let won = acceptedBy != nil && bid.userId == acceptedBy
        let dimmed = acceptedBy != nil && !won
        return ContentDetailBidRow(
            id: bid.id,
            initials: initials.isEmpty ? "?" : initials,
            displayName: name,
            avatarColor: "primary",
            ratingLine: "verified neighbor",
            amount: amountLabel,
            verified: bid.bidder?.resolvedVerified ?? false,
            won: won,
            dimmed: dimmed
        )
    }

    private static func gigPriceLabel(_ price: Double, payType: String?) -> String {
        let base = price.truncatingRemainder(dividingBy: 1) == 0 ? "$\(Int(price))" : String(format: "$%.2f", price)
        switch payType {
        case "hourly": return "\(base) / hr"
        case "per_session": return "\(base) / session"
        case "per_walk": return "\(base) / walk"
        case "per_visit": return "\(base) / visit"
        default: return base
        }
    }

    private static func distanceLabel(_ miles: Double?) -> String? {
        guard let miles else { return nil }
        if miles < 0.1 { return "< 0.1 mi" }
        if miles < 10 { return String(format: "%.1f mi", miles) }
        return "\(Int(miles)) mi"
    }

    private static func relativeAge(_ timestamp: String?) -> String? {
        guard let timestamp else { return nil }
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = parser.date(from: timestamp) ?? ISO8601DateFormatter().date(from: timestamp)
        guard let date else { return nil }
        let interval = Date().timeIntervalSince(date)
        if interval < 60 { return "now" }
        if interval < 3600 { return "\(Int(interval / 60))m" }
        if interval < 86400 { return "\(Int(interval / 3600))h" }
        if interval < 604_800 { return "\(Int(interval / 86400))d" }
        return "\(Int(interval / 604_800))w"
    }

    private static func formatScheduledStart(_ iso: String) -> String {
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = parser.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        guard let date else { return iso }
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE MMM d · h:mm a"
        return formatter.string(from: date)
    }

    /// Date-only variant used for the V2 stat strip's first cell.
    private static func formatScheduledDate(_ iso: String) -> String {
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = parser.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        guard let date else { return iso }
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE MMM d"
        return formatter.string(from: date)
    }
}
