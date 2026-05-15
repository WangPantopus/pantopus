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

    init(listingId: String, api: APIClient = .shared) {
        self.listingId = listingId
        self.api = api
    }

    public func load() async {
        state = .loading
        do {
            let detail: ListingDetailResponse = try await api.request(ListingsEndpoints.detail(id: listingId))
            rawListing = detail.listing
            state = .loaded(Self.project(detail.listing))
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

    static func project(_ listing: ListingDTO) -> ContentDetailContent {
        let isFree = listing.isFree ?? false
        let priceLine: String = if isFree {
            "Free"
        } else if let price = listing.price {
            price.truncatingRemainder(dividingBy: 1) == 0
                ? "$\(Int(price))"
                : String(format: "$%.2f", price)
        } else {
            "—"
        }
        let imageUrl = (listing.firstImage ?? listing.mediaUrls?.first).flatMap(URL.init(string:))
        let cover = ContentDetailCover(
            imageUrl: imageUrl,
            gradient: ListingGradient.from(id: listing.id),
            placeholderIcon: placeholderIcon(category: listing.category, layer: listing.layer),
            pageCount: max(listing.mediaUrls?.count ?? 1, 1),
            activePage: 0
        )
        let condition = conditionLabel(listing.condition)
        var trust: [ContentDetailTrustCapsule] = []
        if let condition {
            trust.append(ContentDetailPill(label: condition, icon: .star, tone: .success))
        }
        if listing.layer == "rentals" {
            trust.append(ContentDetailPill(label: "Rental", icon: .calendar, tone: .business))
        } else if isFree {
            trust.append(ContentDetailPill(label: "Free", icon: .heart, tone: .success))
        } else {
            trust.append(ContentDetailPill(label: "Pickup", icon: .mapPin, tone: .neutral))
        }
        let counterparty = ContentDetailCounterparty(
            displayName: "Seller",
            initials: "S",
            identityKind: "personal",
            verified: true,
            rating: nil,
            trailing: listing.locationName,
            showsMessageButton: true
        )
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
        let dock = ContentDetailDock(
            secondary: ContentDetailDockButton(label: "Message", icon: .send),
            primary: ContentDetailDockButton(label: "Make offer", icon: nil)
        )
        return ContentDetailContent(
            kind: .listing,
            cover: cover,
            statusPill: nil,
            hero: ContentDetailHero(
                title: listing.title ?? "Listing",
                categoryChip: nil,
                meta: nil,
                priceLine: priceLine,
                priceCaption: listing.layer == "rentals" ? "per week" : nil
            ),
            statStrip: [],
            counterparty: counterparty,
            modules: modules,
            trustCapsules: trust,
            dock: dock
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
