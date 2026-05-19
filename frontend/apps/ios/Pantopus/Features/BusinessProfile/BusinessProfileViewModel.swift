//
//  BusinessProfileViewModel.swift
//  Pantopus
//
//  P1.6 — View-model for the typed Business Profile screen. Loads
//  `GET /api/businesses/:businessId` (the authenticated detail) and,
//  best-effort, the `/public/:username` payload + the public-profile
//  endpoint to populate the Overview hours, Services tab, and Reviews
//  tab. Tab state lives on the VM so switching doesn't refetch.
//

import Foundation
import Logging
import Observation

/// In-flight state for the Save action.
public enum BusinessProfileActionState: Sendable, Equatable {
    case idle
    case inFlight
    case saved
    case failed(message: String)
}

/// View-model for the Business Profile screen.
@MainActor
@Observable
public final class BusinessProfileViewModel {
    /// Render state.
    public private(set) var state: BusinessProfileState = .loading

    /// Currently selected tab. Switching is local — no refetch.
    public var selectedTab: BusinessProfileTab = .overview

    /// In-flight state for the "Save" affordance.
    public private(set) var saveState: BusinessProfileActionState = .idle

    /// Drives the kebab-overflow action sheet.
    public var showOverflow: Bool = false

    /// Transient toast surface.
    public var toastMessage: String?

    private let businessId: String
    private let client: APIClient
    private let logger = Logger(label: "app.pantopus.ios.BusinessProfile")

    init(businessId: String, client: APIClient = .shared) {
        self.businessId = businessId
        self.client = client
    }

    public func load() async {
        state = .loading
        await fetch()
    }

    public func refresh() async {
        await fetch()
    }

    /// Pop the "Save" toast. P1.6 wires the optimistic UX but the
    /// backend follow endpoint for businesses ships later; until then
    /// we just emit the toast so the affordance reads as live.
    public func save() async {
        guard saveState != .inFlight, saveState != .saved else { return }
        saveState = .inFlight
        // Optimistic: pretend the follow request succeeded. When the
        // real endpoint lands this is the single integration site.
        try? await Task.sleep(nanoseconds: 200_000_000)
        saveState = .saved
        toastMessage = "Saved"
    }

    private func fetch() async {
        do {
            let detail = try await client.request(
                BusinessesEndpoints.business(businessId: businessId),
                as: BusinessDetailResponse.self
            )
            // Side-quests run after the primary fetch resolves so we
            // can route to .notFound on a 404 without the rest of the
            // requests masking it. The username may be nil for legacy
            // rows; we skip the public fetch in that case.
            let publicResponse = await loadPublic(username: detail.business.username)
            let reviewsResponse = await loadReviewsAndStats()
            let content = build(
                from: detail,
                publicResponse: publicResponse,
                reviewsResponse: reviewsResponse
            )
            state = .loaded(content)
        } catch let error as APIError {
            switch error {
            case .notFound:
                logger.info("Business not found: \(self.businessId)")
                state = .notFound
            default:
                logger.warning("Business detail load failed: \(error)")
                state = .error(message: friendlyMessage(for: error))
            }
        } catch {
            logger.warning("Business detail load failed: \(error)")
            state = .error(message: "Something went wrong")
        }
    }

    private func loadPublic(username: String?) async -> BusinessPublicResponse? {
        guard let username, !username.isEmpty else { return nil }
        do {
            return try await client.request(
                BusinessesEndpoints.publicBusiness(username: username),
                as: BusinessPublicResponse.self
            )
        } catch {
            logger.debug("Public business fetch skipped (unpublished or error): \(error)")
            return nil
        }
    }

    private func loadReviewsAndStats() async -> PublicProfile? {
        do {
            return try await client.request(
                PublicProfileEndpoints.profile(id: businessId),
                as: PublicProfile.self
            )
        } catch {
            logger.debug("Public-profile fallback skipped for business: \(error)")
            return nil
        }
    }

    fileprivate func friendlyMessage(for error: APIError) -> String {
        switch error {
        case .notFound: "We couldn't find this business."
        case .forbidden: "This business profile is private."
        case .transport: "Check your connection and try again."
        default: "Something went wrong. Try again."
        }
    }
}

// MARK: - Projection

extension BusinessProfileViewModel {
    fileprivate func build(
        from detail: BusinessDetailResponse,
        publicResponse: BusinessPublicResponse?,
        reviewsResponse: PublicProfile?
    ) -> BusinessProfileContent {
        let business = detail.business
        let profile = detail.profile
        let primaryLocation = profile?.primaryLocation
            ?? detail.locations.first { $0.isPrimary == true }
            ?? detail.locations.first

        let resolvedDisplayName: String = {
            if let name = business.name, !name.isEmpty {
                return name
            }
            if let username = business.username {
                return "@\(username)"
            }
            return "Business"
        }()

        let header = BusinessProfileHeader(
            displayName: resolvedDisplayName,
            handle: business.username,
            locality: localityString(business: business, location: primaryLocation),
            logoURL: business.profilePictureURL.flatMap(URL.init(string:)),
            isVerified: business.verified ?? (profile?.verificationStatus.map { $0 != "unverified" } ?? false),
            categoryChips: Array((profile?.categories ?? []).prefix(4))
        )

        let stats = buildStats(
            business: business,
            reviewsResponse: reviewsResponse
        )

        let about = profile?.description?.isEmpty == false
            ? profile?.description
            : (business.bio?.isEmpty == false ? business.bio : business.tagline)

        let hours = buildHours(
            from: publicResponse?.hours ?? [],
            primaryLocationId: primaryLocation?.id
        )

        let address = primaryLocation.flatMap { buildAddress(location: $0) }

        let contact = buildContact(profile: profile, location: primaryLocation)

        let services = (publicResponse?.catalog ?? []).map { buildService($0) }

        let reviewCards = (reviewsResponse?.reviews ?? []).map(buildReview)

        let websiteURL = normalizedWebsite(profile?.website)

        return BusinessProfileContent(
            businessId: business.id,
            header: header,
            stats: stats,
            about: about,
            hours: hours,
            address: address,
            contact: contact,
            services: services,
            reviews: reviewCards,
            websiteURL: websiteURL,
            viewerIsOwner: detail.access?.isOwner ?? false
        )
    }

    private func buildStats(
        business: BusinessUserDetailDTO,
        reviewsResponse: PublicProfile?
    ) -> [BusinessStatCell] {
        let followers = business.followersCount ?? reviewsResponse?.followersCount ?? 0
        let reviews = business.reviewCount ?? reviewsResponse?.reviewCount ?? 0
        let years = yearsOnPantopus(business.createdAt)
        return [
            BusinessStatCell(id: "followers", value: formatStat(followers), label: "Followers"),
            BusinessStatCell(id: "reviews", value: formatStat(reviews), label: "Reviews"),
            BusinessStatCell(id: "years", value: years, label: years == "1" ? "Year" : "Years")
        ]
    }

    private func formatStat(_ value: Int) -> String {
        if value >= 1000 {
            let truncated = Double(value) / 1000
            return String(format: "%.1fK", truncated).replacingOccurrences(of: ".0K", with: "K")
        }
        return "\(value)"
    }

    private func yearsOnPantopus(_ iso: String?) -> String {
        guard let iso else { return "—" }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let created = formatter.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        guard let created else { return "—" }
        let seconds = Date().timeIntervalSince(created)
        let years = Int(seconds / (365.25 * 24 * 3600))
        return years < 1 ? "<1" : "\(years)"
    }

    private func localityString(
        business: BusinessUserDetailDTO,
        location: BusinessLocationDTO?
    ) -> String? {
        if let city = location?.city, let state = location?.state, !city.isEmpty, !state.isEmpty {
            return "\(city), \(state)"
        }
        if let city = business.city, let state = business.state, !city.isEmpty, !state.isEmpty {
            return "\(city), \(state)"
        }
        return business.city ?? business.state
    }

    private func buildHours(
        from rows: [BusinessHoursDTO],
        primaryLocationId: String?
    ) -> [BusinessHoursRow] {
        let scoped = primaryLocationId.map { id in
            rows.filter { $0.locationId == id }
        } ?? rows
        let dayNames = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
        let sorted = scoped.sorted { $0.dayOfWeek < $1.dayOfWeek }
        return sorted.map { row in
            let dayIndex = max(0, min(6, row.dayOfWeek))
            let label = dayNames[dayIndex]
            let isClosed = row.isClosed == true
            let time: String
            if isClosed {
                time = "Closed"
            } else if let open = row.openTime, let close = row.closeTime {
                time = "\(formatTime(open)) – \(formatTime(close))"
            } else {
                time = "—"
            }
            return BusinessHoursRow(id: row.id, dayLabel: label, timeLabel: time, isClosed: isClosed)
        }
    }

    private func formatTime(_ raw: String) -> String {
        // Backend stores "HH:MM:SS" or "HH:MM". Strip seconds and
        // normalise to 12-hour for the body.
        let parts = raw.split(separator: ":")
        guard parts.count >= 2,
              let hour = Int(parts[0]),
              let minute = Int(parts[1]) else { return raw }
        let suffix = hour >= 12 ? "PM" : "AM"
        let normalisedHour = hour % 12 == 0 ? 12 : hour % 12
        if minute == 0 {
            return "\(normalisedHour) \(suffix)"
        }
        return String(format: "%d:%02d %@", normalisedHour, minute, suffix)
    }

    private func buildAddress(location: BusinessLocationDTO) -> BusinessAddress {
        var lines: [String] = []
        if let address = location.address, !address.isEmpty {
            lines.append(address)
            if let address2 = location.address2, !address2.isEmpty {
                lines.append(address2)
            }
        }
        let cityLine = [location.city, location.state, location.zipcode]
            .compactMap { $0?.isEmpty == false ? $0 : nil }
            .joined(separator: ", ")
        if !cityLine.isEmpty {
            lines.append(cityLine)
        }
        return BusinessAddress(
            lines: lines,
            latitude: location.location?.lat,
            longitude: location.location?.lng
        )
    }

    private func buildContact(
        profile: BusinessProfileDetailDTO?,
        location: BusinessLocationDTO?
    ) -> [BusinessContactRow] {
        var rows: [BusinessContactRow] = []
        if let phone = (profile?.publicPhone ?? location?.phone), !phone.isEmpty {
            let url = URL(string: "tel:\(phone.filter { $0.isNumber || $0 == "+" })")
            rows.append(BusinessContactRow(id: "phone", kind: .phone, value: phone, actionURL: url))
        }
        if let email = (profile?.publicEmail ?? location?.email), !email.isEmpty {
            let url = URL(string: "mailto:\(email)")
            rows.append(BusinessContactRow(id: "email", kind: .email, value: email, actionURL: url))
        }
        if let website = profile?.website, !website.isEmpty {
            rows.append(
                BusinessContactRow(
                    id: "website",
                    kind: .website,
                    value: prettyHost(for: website) ?? website,
                    actionURL: normalizedWebsite(website)
                )
            )
        }
        return rows
    }

    private func buildService(_ item: BusinessCatalogItemDTO) -> BusinessServiceRow {
        BusinessServiceRow(
            id: item.id,
            name: item.name,
            detail: item.description,
            priceLabel: priceLabel(for: item)
        )
    }

    private func priceLabel(for item: BusinessCatalogItemDTO) -> String {
        let currency = item.currency?.uppercased() ?? "USD"
        let symbol = currency == "USD" ? "$" : ""
        let formatDollars: (Int) -> String = { cents in
            let value = Double(cents) / 100
            if value == value.rounded() {
                return String(format: "%@%.0f", symbol, value)
            }
            return String(format: "%@%.2f", symbol, value)
        }
        switch (item.priceCents, item.priceMaxCents) {
        case let (min?, max?) where max > min:
            return "\(formatDollars(min)) – \(formatDollars(max))"
        case let (price?, _):
            let unitSuffix = item.priceUnit.map { "/\($0)" } ?? ""
            return "\(formatDollars(price))\(unitSuffix)"
        default:
            if item.kind == "donation" {
                return "Suggested"
            }
            return "Contact"
        }
    }

    private func buildReview(_ raw: PublicProfileReview) -> BusinessReviewCard {
        BusinessReviewCard(
            id: raw.id ?? UUID().uuidString,
            reviewerName: raw.reviewerName ?? "Anonymous",
            reviewerAvatarURL: raw.reviewerAvatar.flatMap(URL.init(string:)),
            rating: raw.rating,
            body: raw.content ?? "",
            timestamp: relativeTimestamp(raw.createdAt)
        )
    }

    private func relativeTimestamp(_ iso: String?) -> String {
        guard let iso else { return "" }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = formatter.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) ?? Date()
        let elapsed = Date().timeIntervalSince(date)
        switch elapsed {
        case ..<60: return "Just now"
        case ..<3600: return "\(Int(elapsed / 60))m ago"
        case ..<86400: return "\(Int(elapsed / 3600))h ago"
        case ..<604_800: return "\(Int(elapsed / 86400))d ago"
        default:
            let display = DateFormatter()
            display.dateStyle = .medium
            display.timeStyle = .none
            return display.string(from: date)
        }
    }

    private func normalizedWebsite(_ raw: String?) -> URL? {
        guard let raw, !raw.isEmpty else { return nil }
        if let url = URL(string: raw), url.scheme != nil {
            return url
        }
        return URL(string: "https://\(raw)")
    }

    private func prettyHost(for raw: String) -> String? {
        guard let url = normalizedWebsite(raw) else { return raw }
        return url.host?.replacingOccurrences(of: "www.", with: "")
    }
}
