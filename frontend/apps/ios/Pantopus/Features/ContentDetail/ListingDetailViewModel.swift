//
//  ListingDetailViewModel.swift
//  Pantopus
//
//  Fetches `GET /api/listings/:id`, projects into `ContentDetailContent`
//  for the shared `ContentDetailShell`, and exposes `messageSeller` /
//  `makeOffer` actions for the sticky dock.
//

import Foundation
import Observation

@Observable
@MainActor
public final class ListingDetailViewModel {
    public private(set) var state: ContentDetailState = .loading
    public private(set) var rawListing: ListingDTO?

    private let listingId: String
    private let api: APIClient
    private let currentUserId: @MainActor () -> String?

    init(
        listingId: String,
        api: APIClient = .shared,
        currentUserId: @escaping @MainActor () -> String? = ListingDetailViewModel.currentSignedInUserId
    ) {
        self.listingId = listingId
        self.api = api
        self.currentUserId = currentUserId
    }

    /// True when the loaded listing is owned by the currently signed-in
    /// user. Drives the dock's "Make offer" → "View offers" swap on the
    /// seller's own listing.
    public var isOwnedByMe: Bool {
        guard let ownerId = rawListing?.userId, !ownerId.isEmpty,
              let me = currentUserId(), !me.isEmpty
        else { return false }
        return ownerId == me
    }

    @MainActor
    private static func currentSignedInUserId() -> String? {
        if case let .signedIn(user) = AuthManager.shared.state {
            return user.id
        }
        return nil
    }

    public func load() async {
        state = .loading
        do {
            let detail: ListingDetailResponse = try await api.request(ListingsEndpoints.detail(id: listingId))
            rawListing = detail.listing
            let viewerId = currentUserId()
            state = .loaded(Self.project(detail.listing, viewerUserId: viewerId))
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load listing."
            state = .error(message: message)
        }
    }

    /// Send the seller a message — optionally with an offer amount.
    /// Returns `true` on success so the host can dismiss its sheet.
    @discardableResult
    public func sendMessage(text: String, offerAmount: Double? = nil) async -> Bool {
        do {
            let _: MessageListingResponse = try await api.request(
                ListingsEndpoints.messageListing(
                    id: listingId,
                    body: MessageListingBody(message: text, offerAmount: offerAmount)
                )
            )
            return true
        } catch {
            return false
        }
    }

    // MARK: - Projection

    static func project(_ listing: ListingDTO, viewerUserId: String? = nil) -> ContentDetailContent {
        let isViewerOwner: Bool = {
            guard let owner = listing.userId, !owner.isEmpty,
                  let viewer = viewerUserId, !viewer.isEmpty
            else { return false }
            return owner == viewer
        }()
        return ContentDetailContent(
            kind: .listing,
            cover: cover(for: listing),
            statusPill: nil,
            hero: ContentDetailHero(
                title: listing.title ?? "Listing",
                categoryChip: nil,
                meta: nil,
                priceLine: priceLine(for: listing),
                priceCaption: listing.layer == "rentals" ? "per week" : nil
            ),
            statStrip: [],
            counterparty: counterparty(for: listing),
            modules: modules(for: listing),
            trustCapsules: trustCapsules(for: listing),
            dock: dock(isViewerOwner: isViewerOwner)
        )
    }

    private static func priceLine(for listing: ListingDTO) -> String {
        if listing.isFree ?? false { return "Free" }
        guard let price = listing.price else { return "—" }
        return price.truncatingRemainder(dividingBy: 1) == 0
            ? "$\(Int(price))"
            : String(format: "$%.2f", price)
    }

    private static func cover(for listing: ListingDTO) -> ContentDetailCover {
        ContentDetailCover(
            imageUrl: (listing.firstImage ?? listing.mediaUrls?.first).flatMap(URL.init(string:)),
            gradient: ListingGradient.from(id: listing.id),
            placeholderIcon: placeholderIcon(category: listing.category, layer: listing.layer),
            pageCount: max(listing.mediaUrls?.count ?? 1, 1),
            activePage: 0
        )
    }

    private static func trustCapsules(for listing: ListingDTO) -> [ContentDetailTrustCapsule] {
        var trust: [ContentDetailTrustCapsule] = []
        if let condition = conditionLabel(listing.condition) {
            trust.append(ContentDetailPill(label: condition, icon: .star, tone: .success))
        }
        if listing.layer == "rentals" {
            trust.append(ContentDetailPill(label: "Rental", icon: .calendar, tone: .business))
        } else if listing.isFree ?? false {
            trust.append(ContentDetailPill(label: "Free", icon: .heart, tone: .success))
        } else {
            trust.append(ContentDetailPill(label: "Pickup", icon: .mapPin, tone: .neutral))
        }
        return trust
    }

    private static func counterparty(for listing: ListingDTO) -> ContentDetailCounterparty {
        ContentDetailCounterparty(
            displayName: "Seller",
            initials: "S",
            identityKind: "personal",
            verified: true,
            rating: nil,
            trailing: listing.locationName,
            showsMessageButton: true
        )
    }

    private static func modules(for listing: ListingDTO) -> [ContentDetailModule] {
        var modules: [ContentDetailModule] = []
        if let body = listing.description, !body.isEmpty {
            modules.append(.description(ContentDetailDescription(
                title: "Description",
                icon: .file,
                body: body
            )))
        }
        if let where_ = listing.locationName, !where_.isEmpty {
            modules.append(.detailRow(ContentDetailDetailRow(
                title: "Where",
                sectionIcon: .mapPin,
                rowIcon: .mapPin,
                label: where_,
                trailing: distanceLabel(listing.distanceMeters)
            )))
        }
        return modules
    }

    private static func dock(isViewerOwner: Bool) -> ContentDetailDock {
        ContentDetailDock(
            secondary: ContentDetailDockButton(label: "Message", icon: .send),
            primary: ContentDetailDockButton(label: isViewerOwner ? "View offers" : "Make offer", icon: nil)
        )
    }

    private static func placeholderIcon(category: String?, layer: String?) -> PantopusIcon {
        if layer == "vehicles" { return .send }
        if layer == "rentals" { return .calendar }
        switch category ?? "" {
        case "furniture": return .home
        case "electronics": return .lightbulb
        case "clothing": return .shoppingBag
        case "tools": return .hammer
        case "books_media": return .file
        case "free_stuff": return .heart
        default: return .shoppingBag
        }
    }

    private static func conditionLabel(_ condition: String?) -> String? {
        guard let condition else { return nil }
        switch condition {
        case "new": return "New"
        case "like_new": return "Like new"
        case "good": return "Good"
        case "fair": return "Fair"
        case "for_parts": return "For parts"
        default: return condition.replacingOccurrences(of: "_", with: " ").capitalized
        }
    }

    private static func distanceLabel(_ meters: Double?) -> String? {
        guard let meters else { return nil }
        let miles = meters / 1609.344
        if miles < 0.1 { return "< 0.1 mi" }
        if miles < 10 { return String(format: "%.1f mi", miles) }
        return "\(Int(miles)) mi"
    }
}
