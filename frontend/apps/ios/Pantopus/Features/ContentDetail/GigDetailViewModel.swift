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

    private let gigId: String
    private let api: APIClient
    private let currentUserId: String?

    public init(gigId: String, api: APIClient = .shared, currentUserId: String? = nil) {
        self.gigId = gigId
        self.api = api
        self.currentUserId = currentUserId
    }

    public func load() async {
        state = .loading
        do {
            let detail: GigDetailResponse = try await api.request(GigsEndpoints.detail(id: gigId))
            rawGig = detail.gig
            viewerIsOwner = currentUserId != nil && detail.gig.userId == currentUserId
            var bids: [GigBidDTO] = []
            if viewerIsOwner {
                if let bidsResponse: GigBidsResponse = try? await api.request(GigsEndpoints.bids(gigId: gigId)) {
                    bids = bidsResponse.bids
                }
            }
            state = .loaded(Self.project(gig: detail.gig, bids: bids))
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load gig."
            state = .error(message: message)
        }
    }

    /// Place a bid with the caller-supplied amount + message. Returns
    /// `true` on success so the host can dismiss its bid-entry sheet.
    @discardableResult
    public func placeBid(amount: Double, message: String?) async -> Bool {
        do {
            let _: PlaceBidResponse = try await api.request(
                GigsEndpoints.placeBid(
                    gigId: gigId,
                    body: PlaceBidBody(bidAmount: amount, message: message)
                )
            )
            await load()
            return true
        } catch {
            return false
        }
    }

    // MARK: - Projection

    static func project(gig: GigDTO, bids: [GigBidDTO]) -> ContentDetailContent {
        let category = GigsCategory.from(backendKey: gig.category)
        let bidCount = gig.bidCount ?? bids.count
        let statusLabel: String
        let statusTone: ContentDetailPill.Tone
        switch gig.status {
        case "open", nil:
            statusLabel = bidCount > 0 ? "Open · \(bidCount) \(bidCount == 1 ? "bid" : "bids")" : "Open"
            statusTone = .warning
        case "accepted":
            statusLabel = "Accepted"
            statusTone = .info
        case "completed":
            statusLabel = "Completed"
            statusTone = .success
        case "cancelled":
            statusLabel = "Cancelled"
            statusTone = .neutral
        default:
            statusLabel = gig.status?.capitalized ?? "Open"
            statusTone = .neutral
        }
        let metaPieces: [String] = [
            distanceLabel(gig.distanceMiles),
            relativeAge(gig.createdAt).map { "\($0) ago" }
        ].compactMap { $0 }
        let priceLine = gig.price.map { gigPriceLabel($0, payType: gig.payType) }
        let hero = ContentDetailHero(
            title: gig.title,
            categoryChip: ContentDetailCategoryChip(label: category.label, category: category),
            meta: metaPieces.isEmpty ? nil : metaPieces.joined(separator: " · "),
            priceLine: priceLine,
            priceCaption: gig.price != nil ? "budget" : nil
        )
        let stats: [ContentDetailStat] = statRows(gig)
        var modules: [ContentDetailModule] = []
        if let body = gig.description, !body.isEmpty {
            modules.append(.description(ContentDetailDescription(
                title: "What needs doing",
                icon: .file,
                body: body
            )))
        }
        if let pickup = gig.pickupAddress, !pickup.isEmpty {
            modules.append(.detailRow(ContentDetailDetailRow(
                title: "Where",
                sectionIcon: .mapPin,
                rowIcon: .mapPin,
                label: pickup,
                trailing: distanceLabel(gig.distanceMiles)
            )))
        }
        if let scheduledStart = gig.scheduledStart, !scheduledStart.isEmpty {
            modules.append(.captionedText(ContentDetailCaptionedText(
                title: "When",
                icon: .calendar,
                label: formatScheduledStart(scheduledStart)
            )))
        } else if let deadline = gig.deadline, !deadline.isEmpty {
            modules.append(.captionedText(ContentDetailCaptionedText(
                title: "By",
                icon: .calendar,
                label: formatScheduledStart(deadline)
            )))
        }
        if bidCount > 0 && !bids.isEmpty {
            let rows = bids.map(Self.projectBid)
            modules.append(.bids(ContentDetailBidsModule(title: "\(bidCount) bids", bids: rows)))
        }
        let trust: [ContentDetailTrustCapsule] = [
            ContentDetailPill(label: "Verified address", icon: .shieldCheck, tone: .info),
            ContentDetailPill(label: "Local Pantopus job", icon: .check, tone: .success)
        ]
        let dock = ContentDetailDock(
            secondary: ContentDetailDockButton(label: "Message", icon: .send),
            primary: ContentDetailDockButton(label: "Place bid", icon: nil)
        )
        return ContentDetailContent(
            kind: .gig,
            cover: nil,
            statusPill: ContentDetailPill(label: statusLabel, icon: .circle, tone: statusTone),
            hero: hero,
            statStrip: stats,
            counterparty: nil,
            modules: modules,
            trustCapsules: trust,
            dock: dock
        )
    }

    private static func statRows(_ gig: GigDTO) -> [ContentDetailStat] {
        var out: [ContentDetailStat] = []
        if let schedule = gig.scheduleType, !schedule.isEmpty {
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

    private static func projectBid(_ bid: GigBidDTO) -> ContentDetailBidRow {
        let name = bid.bidder?.name ?? bid.bidder?.username ?? "Bidder"
        let initials = name.split(separator: " ").prefix(2).compactMap { $0.first.map(String.init) }.joined().uppercased()
        let amount = bid.bidAmount ?? bid.amount ?? 0
        let amountLabel = amount.truncatingRemainder(dividingBy: 1) == 0 ? "$\(Int(amount))" : String(format: "$%.2f", amount)
        return ContentDetailBidRow(
            id: bid.id,
            initials: initials.isEmpty ? "?" : initials,
            displayName: name,
            avatarColor: "primary",
            ratingLine: "verified neighbor",
            amount: amountLabel,
            verified: bid.bidder?.verified ?? false
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
}
