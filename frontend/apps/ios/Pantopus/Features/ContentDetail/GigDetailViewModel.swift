//
//  GigDetailViewModel.swift
//  Pantopus
//
//  Fetches `GET /api/gigs/:id` (+ `/:gigId/bids` when the viewer owns
//  the gig) and projects the result into a `ContentDetailContent` for
//  the `ContentDetailShell`. The sticky-dock primary CTA places a bid
//  via `POST /api/gigs/:gigId/bids`.
//

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
                gig: detail.gig, bids: bids, canMarkDelivered: canMarkDelivered, canTip: canTip
            ))
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
}

// MARK: - Projection

extension GigDetailViewModel {
    /// Top-level projection. Splits on the explicit `is_v2` discriminator
    /// (`GigDTO.isV2`): V2 ("Magic Task") gets the rich surface, legacy V1
    /// gets the sparse layout (which also carries the awarded terminal
    /// state). The full design-spec V2 frame — 3-photo strip, trust
    /// capsules with ratings, per-bid tags — is rendered from
    /// `GigDetailSampleData` until the Magic Task JSONB is wired through
    /// the backend (out of scope per P8.2).
    static func project(
        gig: GigDTO,
        bids: [GigBidDTO],
        canMarkDelivered: Bool = false,
        canTip: Bool = false
    ) -> ContentDetailContent {
        (gig.isV2 == true)
            ? projectTaskV2(gig: gig, bids: bids, canMarkDelivered: canMarkDelivered, canTip: canTip)
            : projectGigV1(gig: gig, bids: bids, canTip: canTip)
    }

    /// The dock for the poster on a completed gig — primary becomes "Send a
    /// tip" (Block 3D), keeping "Message" as the secondary.
    static let tipDock = ContentDetailDock(
        secondary: ContentDetailDockButton(label: "Message", icon: .send),
        primary: ContentDetailDockButton(label: "Send a tip", icon: .handCoins)
    )

    // MARK: V2 (Task) — Magic Task surface

    private static func projectTaskV2(
        gig: GigDTO,
        bids: [GigBidDTO],
        canMarkDelivered: Bool,
        canTip: Bool = false
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
            priceCaption: gig.price != nil ? "budget" : nil
        )
        var modules: [ContentDetailModule] = []
        if let body = gig.description, !body.isEmpty {
            modules.append(.description(ContentDetailDescription(title: "What needs doing", icon: .clipboardList, body: body)))
        }
        modules.append(contentsOf: locationModules(gig))
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
        let dock: ContentDetailDock = if canTip {
            tipDock
        } else if canMarkDelivered {
            ContentDetailDock(
                secondary: ContentDetailDockButton(label: "Message", icon: .send),
                primary: ContentDetailDockButton(label: "Mark as delivered", icon: .checkCheck)
            )
        } else {
            ContentDetailDock(
                secondary: ContentDetailDockButton(label: "Message", icon: .send),
                primary: ContentDetailDockButton(label: "Place bid")
            )
        }
        return ContentDetailContent(
            kind: .gig,
            statusPill: statusPill,
            hero: hero,
            statStrip: statRows(gig),
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

    // MARK: V1 (legacy Gig) — sparse + awarded terminal state

    private static func projectGigV1(gig: GigDTO, bids: [GigBidDTO], canTip: Bool = false) -> ContentDetailContent {
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
        if let poster = gig.creator?.name ?? gig.creator?.username {
            let posted = relativeAge(gig.createdAt).map { " · \($0) ago" } ?? ""
            modules.append(.captionedText(ContentDetailCaptionedText(title: "Posted by", icon: nil, label: "\(poster)\(posted)")))
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
        return winner?.bidder?.name ?? winner?.bidder?.username
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
        let name = bid.bidder?.name ?? bid.bidder?.username ?? "Bidder"
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
            verified: bid.bidder?.verified ?? false,
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
