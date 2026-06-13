//
//  GigDetailViewModel.swift
//  Pantopus
//
//  Fetches `GET /api/gigs/:id` (+ `/:gigId/bids` when the viewer owns
//  the gig) and projects the result into a `ContentDetailContent` for
//  the `ContentDetailShell`. The sticky-dock primary CTA places a bid
//  via `POST /api/gigs/:gigId/bids`.
//

// swiftlint:disable file_length type_body_length

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

    /// Bookmark state for the top-bar toggle. Seeded from
    /// `saved_by_user` on load and flipped optimistically by
    /// `toggleSave()`.
    public private(set) var isSaved: Bool = false

    /// True while a save/unsave request is in flight — debounces taps.
    public private(set) var isSaveInFlight: Bool = false

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

    // MARK: - Phase 5 — lifecycle state

    /// True when the signed-in viewer is the gig's assigned worker.
    public private(set) var viewerIsWorker: Bool = false

    /// Raw bids for the owner's interactive bids panel (status open).
    public private(set) var ownerBids: [GigBidDTO] = []

    /// Bid id with an owner action (accept / counter / reject) in flight.
    public private(set) var bidActionInFlight: String?

    /// Instant-accept gate: `engagement_mode == "instant_accept"`, gig
    /// open, signed-in viewer who isn't the poster (gigsV2.js:64).
    public private(set) var canInstantAccept: Bool = false

    /// Lifecycle phase for the active-task strip (assigned → confirmed).
    public private(set) var activePhase: GigActivePhase?

    /// No-show affordance for either party (owner reports the worker,
    /// worker reports an unresponsive owner), gated by
    /// `GET /:gigId/no-show-check`.
    public private(set) var noShowEligible: Bool = false

    // MARK: - Phase 5b — lifecycle completers

    /// Owner's payment summary (`GET /:gigId/payment`) — `nil` hides the
    /// Payment card (no linked payment, 404, or any fetch failure).
    public private(set) var payment: GigPaymentDTO?

    /// Status chip metadata riding the payment envelope.
    public private(set) var paymentStateInfo: GigPaymentStateInfo?

    /// Change orders on an assigned / in-progress gig (newest first).
    public private(set) var changeOrders: [GigChangeOrderDTO] = []

    /// Change-order id with an approve / reject / withdraw in flight.
    public private(set) var changeOrderActionInFlight: String?

    /// Set when `GET /api/reviews/my-pending` says this gig still wants a
    /// review from the viewer. Drives the "Leave a review" CTA.
    public private(set) var pendingReview: PendingReviewDTO?

    /// Flips after a successful review POST (or a 409 "already reviewed")
    /// so the CTA renders as "Reviewed ✓".
    public private(set) var reviewSubmitted: Bool = false

    /// Realtime room subscription tasks (`gig:<eventType>` refetch loop).
    private var realtimeTasks: [Task<Void, Never>] = []
    private let roomEvents: @MainActor (String) -> AsyncStream<GigRoomEvent>
    private let emitRoom: @MainActor (_ event: String, _ gigId: String) -> Void

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
    /// Phase 6b — lock-screen Live Activity driver. The default real
    /// controller no-ops in tests / previews; tests inject a recorder.
    private let liveActivity: any GigLiveActivityControlling

    init(
        gigId: String,
        api: APIClient = .shared,
        uploader: MultipartUploader = .shared,
        checkout: CheckoutCoordinator = CheckoutCoordinator(),
        currentUserId: String? = GigDetailViewModel.currentSignedInUserId(),
        liveActivity: any GigLiveActivityControlling = GigLiveActivityController.shared,
        roomEvents: @escaping @MainActor (String) -> AsyncStream<GigRoomEvent> = { name in
            SocketClient.shared.events(named: name, as: GigRoomEvent.self)
        },
        emitRoom: @escaping @MainActor (String, String) -> Void = { event, gigId in
            SocketClient.shared.emit(event, payload: ["gigId": gigId])
        }
    ) {
        self.gigId = gigId
        self.api = api
        self.uploader = uploader
        self.checkout = checkout
        self.currentUserId = currentUserId
        self.liveActivity = liveActivity
        self.roomEvents = roomEvents
        self.emitRoom = emitRoom
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
        await fetch(silently: false)
    }

    /// Realtime / post-mutation refetch — keeps the current frame on
    /// screen (no skeleton) and swallows errors.
    public func refreshSilently() async {
        await fetch(silently: true)
    }

    private func fetch(silently: Bool) async {
        do {
            let detail: GigDetailResponse = try await api.request(GigsEndpoints.detail(id: gigId))
            rawGig = detail.gig
            isSaved = detail.gig.savedByUser ?? false
            viewerIsOwner = currentUserId != nil && detail.gig.userId == currentUserId
            viewerIsWorker = currentUserId != nil && detail.gig.acceptedBy == currentUserId
            canMarkDelivered = Self.viewerCanMarkDelivered(gig: detail.gig, currentUserId: currentUserId)
            canTip = Self.viewerCanTip(gig: detail.gig, viewerIsOwner: viewerIsOwner)
            canInstantAccept = Self.viewerCanInstantAccept(
                gig: detail.gig,
                viewerIsOwner: viewerIsOwner,
                signedIn: currentUserId != nil
            )
            activePhase = Self.activePhase(for: detail.gig)
            var bids: [GigBidDTO] = []
            if viewerIsOwner {
                if let bidsResponse: GigBidsResponse = try? await api.request(GigsEndpoints.bids(gigId: gigId)) {
                    bids = bidsResponse.bids
                }
            }
            ownerBids = viewerIsOwner ? bids : []
            // Phase 6b — reconcile the lock-screen Live Activity on every
            // fetch (load, post-mutation refresh, gig:* room events): start
            // once assigned, update on phase changes, end on terminal states.
            liveActivity.sync(
                gig: detail.gig,
                isParticipant: viewerIsOwner || viewerIsWorker,
                workerName: bids.first { $0.userId == detail.gig.acceptedBy }?.bidder?.resolvedDisplayName
            )
            state = .loaded(Self.project(
                gig: detail.gig,
                bids: bids,
                canMarkDelivered: canMarkDelivered,
                canTip: canTip,
                viewerUserId: currentUserId,
                canInstantAccept: canInstantAccept,
                suppressBidsModule: Self.ownerPanelHandlesBids(gig: detail.gig, viewerIsOwner: viewerIsOwner)
            ))
            await loadQuestions()
            await loadLifecycleExtras(gig: detail.gig)
        } catch {
            guard !silently else { return }
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load gig."
            state = .error(message: message)
        }
    }

    /// Conditional follow-up fetches: either party's no-show eligibility
    /// on an active task, the owner's payment summary once assigned+,
    /// the change-order list, and the viewer's pending-review row once
    /// the gig completes. All best-effort — failures just hide
    /// affordances.
    private func loadLifecycleExtras(gig: GigDTO) async {
        let status = (gig.status ?? "").lowercased()
        // The backend gates `/report-no-show` for both parties: poster →
        // worker no-show, worker → unresponsive poster (gigs.js:7722).
        if viewerIsOwner || viewerIsWorker, ["assigned", "in_progress"].contains(status) {
            let check: NoShowCheckResponse? = try? await api.request(GigsEndpoints.noShowCheck(gigId: gigId))
            noShowEligible = check?.canReport ?? false
        } else {
            noShowEligible = false
        }
        await loadPayment(gig: gig, status: status)
        await loadChangeOrders(status: status)
        if status == "completed", viewerIsOwner || viewerIsWorker, !reviewSubmitted {
            if let response: MyPendingReviewsResponse = try? await api.request(ReviewsEndpoints.myPending()) {
                pendingReview = response.pending.first { $0.gigId == gigId }
                // The gig is completed but no longer pending → the viewer
                // already reviewed it.
                if pendingReview == nil { reviewSubmitted = true }
            }
        }
    }

    /// Owner's Payment card data — fetched once a bid was accepted /
    /// the gig is assigned+ (gigs.js:8440). Silent-hide on 404/failure.
    private func loadPayment(gig: GigDTO, status: String) async {
        let assignedPlus = ["assigned", "in_progress", "completed"].contains(status)
            || !(gig.acceptedBy ?? "").isEmpty
        guard viewerIsOwner, assignedPlus else {
            payment = nil
            paymentStateInfo = nil
            return
        }
        let response: GigPaymentResponse? = try? await api.request(GigsEndpoints.payment(gigId: gigId))
        payment = response?.payment
        paymentStateInfo = response?.stateInfo
    }

    /// Change orders — both roles, while the gig is assigned /
    /// in_progress (the create route's precondition, gigs.js:6691).
    private func loadChangeOrders(status: String) async {
        guard viewerIsOwner || viewerIsWorker, ["assigned", "in_progress"].contains(status) else {
            changeOrders = []
            return
        }
        let response: GigChangeOrdersResponse? = try? await api.request(GigsEndpoints.changeOrders(gigId: gigId))
        changeOrders = response?.changeOrders ?? []
    }

    /// Bookmark toggle (work item C). Optimistic: flip `isSaved`
    /// immediately, then `POST /api/gigs/:id/save` (or `DELETE` to
    /// unsave). On failure the flip reverts and the method returns
    /// `false` so the view can toast. Taps are debounced while a
    /// request is in flight.
    @discardableResult
    public func toggleSave() async -> Bool {
        guard !isSaveInFlight, let gig = rawGig else { return true }
        isSaveInFlight = true
        defer { isSaveInFlight = false }
        let target = !isSaved
        isSaved = target
        do {
            let endpoint = target
                ? GigsEndpoints.save(id: gig.id)
                : GigsEndpoints.unsave(id: gig.id)
            _ = try await api.request(endpoint, as: EmptyResponse.self)
            return true
        } catch {
            isSaved = !target
            return false
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

    /// The instant-accept gate: `engagement_mode == "instant_accept"`,
    /// gig still `open`, signed-in viewer ≠ owner (gigsV2.js:64).
    static func viewerCanInstantAccept(gig: GigDTO, viewerIsOwner: Bool, signedIn: Bool) -> Bool {
        guard signedIn, !viewerIsOwner else { return false }
        guard gig.engagementMode == "instant_accept" else { return false }
        return (gig.status ?? "").lowercased() == "open"
    }

    /// Lifecycle phase for the active-task strip. `nil` outside the
    /// assigned → confirmed window (open / cancelled gigs).
    static func activePhase(for gig: GigDTO) -> GigActivePhase? {
        switch (gig.status ?? "").lowercased() {
        case "assigned": .assigned
        case "in_progress": .inProgress
        case "completed":
            (gig.ownerConfirmedAt ?? "").isEmpty ? .markedDone : .confirmed
        default: nil
        }
    }

    /// True when the owner's interactive bids panel replaces the
    /// read-only `.bids` module (open gigs only — awarded states keep
    /// the winner/dimmed module).
    static func ownerPanelHandlesBids(gig: GigDTO, viewerIsOwner: Bool) -> Bool {
        viewerIsOwner && (gig.status ?? "").lowercased() == "open"
    }

    /// "I'm on it" gate — assigned worker, before ack and before start
    /// (`/worker-ack` preconditions, gigs.js:5840).
    public var showWorkerAck: Bool {
        guard viewerIsWorker, let gig = rawGig else { return false }
        return (gig.status ?? "").lowercased() == "assigned"
            && (gig.workerAckStatus ?? "").isEmpty
            && (gig.startedAt ?? "").isEmpty
    }

    /// "Running late" gate — assigned worker, before start, not already
    /// flagged late. Unlike `showWorkerAck` it stays available after an
    /// "I'm on it" (the backend lets the worker re-ack, gigs.js:5838).
    public var canReportRunningLate: Bool {
        guard viewerIsWorker, let gig = rawGig else { return false }
        return (gig.status ?? "").lowercased() == "assigned"
            && (gig.startedAt ?? "").isEmpty
            && gig.workerAckStatus != "running_late"
    }

    /// "Running ~X min late" badge copy for the phase strip — shown to
    /// both roles while the late ack stands on an active task.
    public var runningLateLabel: String? {
        guard let gig = rawGig, gig.workerAckStatus == "running_late" else { return nil }
        guard ["assigned", "in_progress"].contains((gig.status ?? "").lowercased()) else { return nil }
        if let eta = gig.workerAckEtaMinutes, eta > 0 {
            return "Running ~\(eta) min late"
        }
        return "Running late"
    }

    /// Payment card gate — owner only (the worker's payout view lives in
    /// the wallet); data presence implies the assigned+ fetch succeeded.
    public var showPaymentCard: Bool {
        viewerIsOwner && payment != nil
    }

    /// Changes card gate — either party on an assigned / in-progress
    /// gig (mirrors the create route's precondition).
    public var showChangesSection: Bool {
        guard viewerIsOwner || viewerIsWorker, let gig = rawGig else { return false }
        return ["assigned", "in_progress"].contains((gig.status ?? "").lowercased())
    }

    /// True when the signed-in viewer proposed this change order —
    /// drives Withdraw (proposer) vs Approve / Reject (counterparty).
    public func isOwnChangeOrder(_ order: GigChangeOrderDTO) -> Bool {
        guard let me = currentUserId, !me.isEmpty else { return false }
        return order.requestedBy == me
    }

    /// "Start task" gate — the **assigned worker** transitions
    /// assigned → in_progress (`/start`, gigs.js:5503).
    public var canStartTask: Bool {
        guard viewerIsWorker, let gig = rawGig else { return false }
        return (gig.status ?? "").lowercased() == "assigned"
    }

    /// "Confirm completion" gate — the poster, once the worker marked
    /// done (status `completed`, not yet owner-confirmed).
    public var canConfirmCompletion: Bool {
        guard viewerIsOwner, let gig = rawGig else { return false }
        return (gig.status ?? "").lowercased() == "completed"
            && (gig.ownerConfirmedAt ?? "").isEmpty
    }

    /// "Cancel task" overflow gate — the poster on a live gig.
    public var canCancelTask: Bool {
        guard viewerIsOwner, let gig = rawGig else { return false }
        return ["open", "assigned", "in_progress"].contains((gig.status ?? "").lowercased())
    }

    /// Universal-link share URL — `DeepLinkRouter` resolves
    /// `https://pantopus.app/gigs/<id>` back to this detail.
    public var shareURL: URL {
        URL(string: "https://pantopus.app/gigs/\(gigId)") ?? AppEnvironment.current.apiBaseURL
    }

    /// The interactive owner bids panel renders below the modules while
    /// the owned gig is open.
    public var showOwnerBidsPanel: Bool {
        guard let gig = rawGig else { return false }
        return Self.ownerPanelHandlesBids(gig: gig, viewerIsOwner: viewerIsOwner)
    }

    /// Active-task panel — assigned → confirmed window, participant only.
    public var showActivePanel: Bool {
        activePhase != nil && (viewerIsOwner || viewerIsWorker)
    }

    /// Review section — completed gig, viewer is a participant, and we
    /// either know a review is pending or one was just submitted.
    public var showReviewSection: Bool {
        guard (rawGig?.status ?? "").lowercased() == "completed" else { return false }
        guard viewerIsOwner || viewerIsWorker else { return false }
        return pendingReview != nil || reviewSubmitted
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

/// Lifecycle phase shown in the active-task strip:
/// Assigned → In progress → Marked done → Confirmed.
public enum GigActivePhase: Int, Sendable, Comparable, CaseIterable {
    case assigned = 0
    case inProgress = 1
    case markedDone = 2
    case confirmed = 3

    public var label: String {
        switch self {
        case .assigned: "Assigned"
        case .inProgress: "In progress"
        case .markedDone: "Marked done"
        case .confirmed: "Confirmed"
        }
    }

    public static func < (lhs: GigActivePhase, rhs: GigActivePhase) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}

// MARK: - Phase 5 — lifecycle actions

public extension GigDetailViewModel {
    /// Outcome of the owner accepting a bid (PaymentSheet path for paid
    /// gigs). Mirrors the Mailbox A17.6 accept flow.
    enum BidAcceptOutcome: Sendable, Equatable {
        case accepted
        case canceled
        case failed(message: String)
    }

    /// Poster accepts a bid: `POST .../bids/:bidId/accept`; paid gigs
    /// return PaymentSheet params → present → `finalize-accept` (or
    /// `abort-accept` on cancel/decline). Refreshes the gig on success.
    func acceptBid(bidId: String) async -> BidAcceptOutcome {
        guard bidActionInFlight == nil else { return .canceled }
        bidActionInFlight = bidId
        defer { bidActionInFlight = nil }
        do {
            let response: GigBidAcceptResponse = try await api.request(
                GigsEndpoints.acceptBid(gigId: gigId, bidId: bidId)
            )
            let requiresPayment = response.requiresPaymentSetup == true
                || response.sheetParams.clientSecret != nil
            if requiresPayment {
                let outcome = await checkout.present(response.sheetParams)
                switch outcome {
                case .paid:
                    let _: GigBidAcceptResponse = try await api.request(
                        GigsEndpoints.finalizeAcceptBid(gigId: gigId, bidId: bidId)
                    )
                case .canceled:
                    _ = try? await api.request(
                        GigsEndpoints.abortAcceptBid(gigId: gigId, bidId: bidId),
                        as: GigBidAcceptResponse.self
                    )
                    return .canceled
                case let .declined(message), let .failed(message):
                    _ = try? await api.request(
                        GigsEndpoints.abortAcceptBid(gigId: gigId, bidId: bidId),
                        as: GigBidAcceptResponse.self
                    )
                    return .failed(message: message)
                }
            }
            await refreshSilently()
            return .accepted
        } catch {
            return .failed(message: (error as? APIError)?.errorDescription ?? "Couldn't accept this bid.")
        }
    }

    /// Poster counters a pending bid. Returns an error string for the
    /// sheet, or `nil` on success (row flips to "Countered $X" locally).
    @discardableResult
    func counterBid(bidId: String, amount: Double, message: String?) async -> String? {
        guard amount > 0 else { return "Enter a counter amount." }
        guard bidActionInFlight == nil else { return nil }
        bidActionInFlight = bidId
        defer { bidActionInFlight = nil }
        do {
            let _: PlaceBidResponse = try await api.request(
                GigsEndpoints.counterBid(gigId: gigId, bidId: bidId, body: CounterBidBody(amount: amount, message: message))
            )
            if let index = ownerBids.firstIndex(where: { $0.id == bidId }) {
                ownerBids[index] = Self.bidCopy(of: ownerBids[index], status: "countered", counterAmount: amount)
            }
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't send counter-offer."
        }
    }

    /// Poster rejects a bid — the row dims in place.
    @discardableResult
    func rejectBid(bidId: String) async -> String? {
        guard bidActionInFlight == nil else { return nil }
        bidActionInFlight = bidId
        defer { bidActionInFlight = nil }
        do {
            _ = try await api.request(GigsEndpoints.rejectBid(gigId: gigId, bidId: bidId), as: EmptyResponse.self)
            if let index = ownerBids.firstIndex(where: { $0.id == bidId }) {
                ownerBids[index] = Self.bidCopy(of: ownerBids[index], status: "rejected")
            }
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't reject this bid."
        }
    }

    /// Helper instantly claims an `instant_accept` task. The backend
    /// assigns atomically; any payment authorization happens on the
    /// poster's side, so we just refresh to the assigned state.
    @discardableResult
    func instantAccept() async -> String? {
        guard canInstantAccept else { return "This task can't be instantly accepted." }
        do {
            let _: GigInstantAcceptResponse = try await api.request(GigsEndpoints.instantAccept(gigId: gigId))
            await refreshSilently()
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't accept this task."
        }
    }

    /// Worker's "I'm on it" — `worker-ack` with `starting_now`.
    @discardableResult
    func sendWorkerAck() async -> String? {
        guard showWorkerAck else { return nil }
        do {
            let _: WorkerAckResponse = try await api.request(
                GigsEndpoints.workerAck(gigId: gigId, body: WorkerAckBody(status: "starting_now"))
            )
            await refreshSilently()
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't send the update."
        }
    }

    /// Worker's "Running late" — `worker-ack` with `running_late` plus
    /// an ETA (1–480 min) and optional note (gigs.js:5838).
    @discardableResult
    func sendRunningLate(etaMinutes: Int, note: String?) async -> String? {
        guard canReportRunningLate else { return nil }
        do {
            let _: WorkerAckResponse = try await api.request(
                GigsEndpoints.workerAck(
                    gigId: gigId,
                    body: WorkerAckBody(status: "running_late", etaMinutes: etaMinutes, note: note)
                )
            )
            await refreshSilently()
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't send the update."
        }
    }

    // MARK: - Phase 5b — change orders

    /// Either party proposes a change (gigs.js:6691). Returns an error
    /// string for the sheet, or `nil` on success (row prepends locally —
    /// the list is newest-first).
    @discardableResult
    func proposeChangeOrder(
        type: GigChangeOrderType,
        description: String,
        amountChange: Double?,
        timeChangeMinutes: Int?
    ) async -> String? {
        let trimmed = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 5 else { return "Describe the change in at least 5 characters." }
        guard changeOrderActionInFlight == nil else { return nil }
        changeOrderActionInFlight = "new"
        defer { changeOrderActionInFlight = nil }
        do {
            let response: GigChangeOrderMutationResponse = try await api.request(
                GigsEndpoints.createChangeOrder(
                    gigId: gigId,
                    body: CreateChangeOrderBody(
                        type: type,
                        description: trimmed,
                        amountChange: amountChange,
                        timeChangeMinutes: timeChangeMinutes
                    )
                )
            )
            if let order = response.changeOrder {
                changeOrders.insert(order, at: 0)
            }
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't send the change request."
        }
    }

    /// Counterparty approves — price deltas apply server-side, so the
    /// gig refreshes silently after the row flips.
    @discardableResult
    func approveChangeOrder(orderId: String) async -> String? {
        await mutateChangeOrder(
            orderId: orderId,
            endpoint: GigsEndpoints.approveChangeOrder(gigId: gigId, orderId: orderId),
            status: "approved",
            refreshGig: true,
            failure: "Couldn't approve the change."
        )
    }

    /// Counterparty declines the pending change.
    @discardableResult
    func rejectChangeOrder(orderId: String) async -> String? {
        await mutateChangeOrder(
            orderId: orderId,
            endpoint: GigsEndpoints.rejectChangeOrder(gigId: gigId, orderId: orderId),
            status: "rejected",
            refreshGig: false,
            failure: "Couldn't reject the change."
        )
    }

    /// Proposer withdraws their own pending change.
    @discardableResult
    func withdrawChangeOrder(orderId: String) async -> String? {
        await mutateChangeOrder(
            orderId: orderId,
            endpoint: GigsEndpoints.withdrawChangeOrder(gigId: gigId, orderId: orderId),
            status: "withdrawn",
            refreshGig: false,
            failure: "Couldn't withdraw the change."
        )
    }

    /// Shared approve / reject / withdraw plumbing: POST, flip the local
    /// row to `status` (the mutation responses drop the requester join),
    /// optionally refresh the gig (approve may change the price).
    private func mutateChangeOrder(
        orderId: String,
        endpoint: Endpoint,
        status: String,
        refreshGig: Bool,
        failure: String
    ) async -> String? {
        guard changeOrderActionInFlight == nil else { return nil }
        changeOrderActionInFlight = orderId
        defer { changeOrderActionInFlight = nil }
        do {
            _ = try await api.request(endpoint, as: GigChangeOrderMutationResponse.self)
            if let index = changeOrders.firstIndex(where: { $0.id == orderId }) {
                changeOrders[index] = Self.changeOrderCopy(of: changeOrders[index], status: status)
            }
            if refreshGig { await refreshSilently() }
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? failure
        }
    }

    /// Copy a change order with a new status for the optimistic row flip.
    internal static func changeOrderCopy(of order: GigChangeOrderDTO, status: String) -> GigChangeOrderDTO {
        GigChangeOrderDTO(
            id: order.id,
            gigId: order.gigId,
            requestedBy: order.requestedBy,
            type: order.type,
            description: order.description,
            amountChange: order.amountChange,
            timeChangeMinutes: order.timeChangeMinutes,
            status: status,
            reviewedBy: order.reviewedBy,
            reviewedAt: order.reviewedAt,
            rejectionReason: order.rejectionReason,
            createdAt: order.createdAt,
            requester: order.requester,
            reviewer: order.reviewer
        )
    }

    /// Worker starts the task (assigned → in_progress).
    @discardableResult
    func startTask() async -> String? {
        guard canStartTask else { return nil }
        do {
            _ = try await api.request(GigsEndpoints.startGig(gigId: gigId), as: EmptyResponse.self)
            await refreshSilently()
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't start the task."
        }
    }

    /// Poster confirms completion (`/complete`) — releases payment and
    /// unlocks the tip affordance on refresh.
    @discardableResult
    func confirmCompletion() async -> String? {
        guard canConfirmCompletion else { return nil }
        do {
            _ = try await api.request(GigsEndpoints.completeGigAsPoster(gigId: gigId), as: EmptyResponse.self)
            await refreshSilently()
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't confirm completion."
        }
    }

    /// Either party reports the other as a no-show (owner → worker,
    /// worker → unresponsive poster) — cancels the gig server-side.
    @discardableResult
    func reportNoShow(description: String?) async -> String? {
        do {
            _ = try await api.request(
                GigsEndpoints.reportNoShow(gigId: gigId, body: ReportNoShowBody(description: description)),
                as: EmptyResponse.self
            )
            await refreshSilently()
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't report the no-show."
        }
    }

    /// Submit the post-completion review. A 409 means the viewer already
    /// reviewed this gig — treat it as success so the CTA settles into
    /// "Reviewed ✓".
    @discardableResult
    func submitReview(rating: Int, comment: String?) async -> String? {
        guard let revieweeId = pendingReview?.revieweeId ?? fallbackRevieweeId() else {
            return "Couldn't work out who to review."
        }
        do {
            _ = try await api.request(
                ReviewsEndpoints.create(body: CreateReviewBody(
                    gigId: gigId,
                    revieweeId: revieweeId,
                    rating: rating,
                    comment: comment
                )),
                as: EmptyResponse.self
            )
            reviewSubmitted = true
            pendingReview = nil
            return nil
        } catch {
            if case let .clientError(status, _)? = error as? APIError, status == 409 {
                reviewSubmitted = true
                pendingReview = nil
                return nil
            }
            return (error as? APIError)?.errorDescription ?? "Couldn't submit review."
        }
    }

    /// Owner reviews the worker; worker reviews the owner.
    private func fallbackRevieweeId() -> String? {
        guard let gig = rawGig else { return nil }
        return viewerIsOwner ? gig.acceptedBy : gig.userId
    }

    /// Report the gig for moderation. Both arms carry a toastable
    /// message; `success` picks the toast kind.
    func reportGig(reason: GigReportReason, details: String?) async -> (success: Bool, message: String) {
        do {
            let response: GigReportResponse = try await api.request(
                GigsEndpoints.reportGig(gigId: gigId, body: ReportGigBody(reason: reason, details: details))
            )
            return (true, response.message ?? "Reported. We'll take a look.")
        } catch {
            return (false, (error as? APIError)?.errorDescription ?? "Couldn't report this task.")
        }
    }

    /// Fetch the zone / fee preview shown in the cancel sheet.
    func loadCancellationPreview() async -> GigCancellationPreview? {
        try? await api.request(GigsEndpoints.cancellationPreview(gigId: gigId))
    }

    /// Cancel the gig with a structured reason.
    @discardableResult
    func cancelTask(reason: CancelGigReason?) async -> String? {
        do {
            _ = try await api.request(GigsEndpoints.cancelGig(gigId: gigId, reason: reason), as: EmptyResponse.self)
            await refreshSilently()
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't cancel the task."
        }
    }

    // MARK: - Phase 6b — reschedule

    /// Reschedule gate — poster on an `assigned` gig (the `/reschedule`
    /// route's preconditions, gigs.js:6405). The cancel sheet additionally
    /// requires the preview's `can_reschedule` (zone <= 1) before showing
    /// the affordance.
    var canRescheduleTask: Bool {
        guard viewerIsOwner, let gig = rawGig else { return false }
        return (gig.status ?? "").lowercased() == "assigned"
    }

    /// Poster moves the assigned gig to a new future start instead of
    /// cancelling. The backend resets the worker's on-my-way ack, notifies
    /// them, and fires `gig:rescheduled`; we refresh silently for the new
    /// `scheduled_start`.
    @discardableResult
    func rescheduleTask(scheduledStart: Date, note: String?) async -> String? {
        guard canRescheduleTask else { return nil }
        do {
            let _: GigRescheduleResponse = try await api.request(
                GigsEndpoints.reschedule(
                    gigId: gigId,
                    body: RescheduleGigBody(
                        scheduledStart: ISO8601DateFormatter().string(from: scheduledStart),
                        note: note
                    )
                )
            )
            await refreshSilently()
            return nil
        } catch {
            return (error as? APIError)?.errorDescription ?? "Couldn't reschedule the task."
        }
    }

    // MARK: - Realtime (gig:<eventType> room)

    /// Room event suffixes `emitGigUpdate` fires (gigs.js / gigsV2.js):
    /// any of them just triggers a silent refetch.
    internal static let roomEventNames = [
        "gig:bid-update",
        "gig:status-change",
        "gig:worker-ack",
        "gig:completion-update",
        "gig:payment-update",
        "gig:rescheduled"
    ]

    /// Join the `gig:<id>` room and refetch on any room event for this
    /// gig. Call from the view's `.task`; paired with `stopRealtime()`.
    func startRealtime() {
        guard realtimeTasks.isEmpty else { return }
        emitRoom("gig:join", gigId)
        for name in Self.roomEventNames {
            let stream = roomEvents(name)
            realtimeTasks.append(Task { [weak self] in
                for await event in stream {
                    guard let self, !Task.isCancelled else { return }
                    guard event.gigId == nil || event.gigId == gigId else { continue }
                    await refreshSilently()
                }
            })
        }
    }

    /// Leave the room and tear down the listeners.
    func stopRealtime() {
        guard !realtimeTasks.isEmpty else { return }
        for task in realtimeTasks {
            task.cancel()
        }
        realtimeTasks = []
        emitRoom("gig:leave", gigId)
    }

    /// Copy a bid with a new status (+ optional counter amount) for the
    /// optimistic owner-panel updates.
    internal static func bidCopy(of bid: GigBidDTO, status: String, counterAmount: Double? = nil) -> GigBidDTO {
        GigBidDTO(
            id: bid.id,
            userId: bid.userId,
            bidAmount: bid.bidAmount,
            amount: bid.amount,
            status: status,
            message: bid.message,
            createdAt: bid.createdAt,
            bidder: bid.bidder,
            counterAmount: counterAmount ?? bid.counterAmount,
            counterStatus: counterAmount != nil ? "pending" : bid.counterStatus
        )
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
        viewerUserId: String? = nil,
        canInstantAccept: Bool = false,
        suppressBidsModule: Bool = false
    ) -> ContentDetailContent {
        shouldProjectTaskV2(gig: gig)
            ? projectTaskV2(
                gig: gig,
                bids: bids,
                canMarkDelivered: canMarkDelivered,
                canTip: canTip,
                viewerUserId: viewerUserId,
                canInstantAccept: canInstantAccept,
                suppressBidsModule: suppressBidsModule
            )
            : projectGigV1(
                gig: gig,
                bids: bids,
                canTip: canTip,
                viewerUserId: viewerUserId,
                suppressBidsModule: suppressBidsModule
            )
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
    /// → instant-accept (open `instant_accept` gig, non-owner) → place-bid.
    /// Factored out to keep `projectTaskV2` under the body-length limit.
    private static func taskV2Dock(
        canMarkDelivered: Bool,
        canTip: Bool,
        canInstantAccept: Bool = false
    ) -> ContentDetailDock {
        if canTip { return tipDock }
        if canMarkDelivered {
            return ContentDetailDock(
                secondary: ContentDetailDockButton(label: "Message", icon: .send),
                primary: ContentDetailDockButton(label: "Mark as delivered", icon: .checkCheck)
            )
        }
        if canInstantAccept {
            return ContentDetailDock(
                secondary: ContentDetailDockButton(label: "Message", icon: .send),
                primary: ContentDetailDockButton(label: "Accept this task", icon: .zap)
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
        viewerUserId: String? = nil,
        canInstantAccept: Bool = false,
        suppressBidsModule: Bool = false
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
        // The interactive owner panel (scroll footer) supersedes the
        // read-only module on open gigs the viewer owns.
        if suppressBidsModule {
            // no-op — `gigDetail.bids` renders below the modules.
        } else if bidCount > 0, !bids.isEmpty {
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
        let dock = taskV2Dock(
            canMarkDelivered: canMarkDelivered,
            canTip: canTip,
            canInstantAccept: canInstantAccept
        )
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
        viewerUserId: String? = nil,
        suppressBidsModule: Bool = false
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
        if !bids.isEmpty, !suppressBidsModule {
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
