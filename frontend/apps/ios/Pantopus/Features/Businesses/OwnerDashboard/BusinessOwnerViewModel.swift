//
//  BusinessOwnerViewModel.swift
//  Pantopus
//
//  A10.7 / P1-C — View-model for the single-business owner dashboard. The
//  owner-scoped data is now live:
//    · the shared public render (`publicProfile`) is built by reusing
//      `BusinessProfileViewModel` so the owner frame and "preview as neighbor"
//      describe exactly one business (no projection duplication);
//    · live status + edit recency + the profile-strength checklist come from
//      `GET /:businessId/dashboard`;
//    · the "This week" tiles come from `GET /:businessId/insights`;
//    · the reply composer reads `GET /:businessId/reviews` and commits via
//      `POST /:businessId/reviews/:reviewId/respond` (optimistic + rollback).
//
//  The `BusinessOwnerSampleData` fixture is retained as the preview / snapshot
//  seam — inject `content:` for `#Preview` and tests to skip the network.
//

import Foundation
import Logging
import Observation

/// View-model for the owner dashboard.
@MainActor
@Observable
public final class BusinessOwnerViewModel {
    /// Render state.
    public private(set) var state: BusinessOwnerState = .loading

    private let businessId: String
    private let injectedContent: BusinessOwnerContent?
    private let client: APIClient
    private let logger = Logger(label: "app.pantopus.ios.BusinessOwner")

    /// - Parameters:
    ///   - businessId: The owned business id.
    ///   - client: The API client (injected for tests).
    ///   - content: Pre-built content for previews / snapshots / tests. When
    ///     `nil` the view-model fetches live owner data.
    public init(
        businessId: String,
        client: APIClient = .shared,
        content: BusinessOwnerContent? = nil
    ) {
        self.businessId = businessId
        self.client = client
        injectedContent = content
    }

    public func load() async {
        state = .loading
        if let injectedContent {
            state = .loaded(injectedContent)
            return
        }
        await fetch()
    }

    public func refresh() async {
        if let injectedContent {
            state = .loaded(injectedContent)
            return
        }
        await fetch()
    }

    /// Commit a review reply: optimistic local update, then `POST …/respond`.
    /// On failure the optimistic change is rolled back. Fire-and-forget so the
    /// view's non-async closure stays unchanged.
    public func submitReply(reviewId: String, text: String) {
        guard case let .loaded(content) = state else { return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        // Optimistic update.
        state = .loaded(content.applyingReply(trimmed, to: reviewId))

        Task { [weak self] in
            guard let self else { return }
            do {
                _ = try await client.request(
                    BusinessesEndpoints.respondToReview(
                        businessId: businessId,
                        reviewId: reviewId,
                        response: trimmed
                    )
                )
            } catch {
                logger.warning("Review reply failed for \(reviewId): \(error)")
                // Roll back to the pre-optimistic content.
                if case .loaded = state {
                    state = .loaded(content)
                }
            }
        }
    }

    // MARK: - Fetch

    private func fetch() async {
        // 1. Public render (primary) — reuse the Business Profile projection so
        //    the owner frame reads exactly the public page. This also gives the
        //    not-found / error semantics for the whole screen.
        let profileViewModel = BusinessProfileViewModel(businessId: businessId, client: client)
        await profileViewModel.load()
        let publicProfile: BusinessProfileContent
        switch profileViewModel.state {
        case let .loaded(content):
            publicProfile = content
        case .notFound:
            state = .notFound
            return
        case let .error(message):
            state = .error(message: message)
            return
        case .loading:
            state = .error(message: "Couldn't load your business")
            return
        }

        // 2. Owner-scoped dashboard (required) — publish state + strength.
        let dashboard: BusinessDashboardResponse
        do {
            dashboard = try await client.request(
                BusinessesEndpoints.dashboard(businessId: businessId),
                as: BusinessDashboardResponse.self
            )
        } catch let error as APIError {
            switch error {
            case .forbidden:
                state = .error(message: "You don't have access to this business.")
            case .notFound:
                state = .notFound
            default:
                state = .error(message: "Couldn't load your business")
            }
            return
        } catch {
            state = .error(message: "Couldn't load your business")
            return
        }

        // 3. Tiles + reviews (best-effort overlays; sequential so the request
        //    order stays deterministic for the stubbed-network tests).
        let insights = try? await client.request(
            BusinessesEndpoints.insights(businessId: businessId),
            as: BusinessInsightsResponse.self
        )
        let reviews = (
            try? await client.request(
                BusinessesEndpoints.reviews(businessId: businessId),
                as: BusinessOwnerReviewsResponse.self
            )
        )?.reviews ?? []

        state = .loaded(
            makeContent(
                publicProfile: publicProfile,
                dashboard: dashboard,
                insights: insights,
                reviews: reviews
            )
        )
    }

    // MARK: - Projection (pure; testable)

    /// Compose the owner content from the public render + the owner-scoped
    /// fetches. Exposed `internal` so the projection can be unit-tested
    /// without sequencing six network responses.
    func makeContent(
        publicProfile: BusinessProfileContent,
        dashboard: BusinessDashboardResponse,
        insights: BusinessInsightsResponse?,
        reviews: [BusinessOwnerReviewDTO]
    ) -> BusinessOwnerContent {
        let isLive = dashboard.profile?.isPublished ?? false
        let mappedReviews = reviews.map(ownerReview)
        let pending = mappedReviews.filter { $0.reply == nil }.count
        return BusinessOwnerContent(
            businessId: businessId,
            isLive: isLive,
            editedMeta: editedMeta(updatedAt: dashboard.profile?.updatedAt, isLive: isLive),
            insights: insightTiles(from: insights),
            profileStrength: profileStrength(from: dashboard.onboarding),
            reviewsToReplyLabel: pending > 0 ? "\(pending) to reply" : nil,
            reviews: mappedReviews,
            publicProfile: publicProfile
        )
    }

    private func insightTiles(from insights: BusinessInsightsResponse?) -> [OwnerInsightTile] {
        guard let insights else { return [] }
        return [
            OwnerInsightTile(
                id: "views",
                icon: .eye,
                value: formatCount(insights.views.total),
                label: "Views",
                delta: trendLabel(insights.views.trend)
            ),
            OwnerInsightTile(
                id: "followers",
                icon: .users,
                value: formatCount(insights.followers.total),
                label: "Followers",
                delta: trendLabel(insights.followers.trend)
            ),
            OwnerInsightTile(
                id: "reviews",
                icon: .star,
                value: formatCount(insights.reviews.count),
                label: "Reviews",
                delta: trendLabel(insights.reviews.trend)
            )
        ]
    }

    private func profileStrength(from onboarding: BusinessOnboardingDTO?) -> OwnerProfileStrength {
        guard let onboarding, onboarding.totalCount > 0 else {
            return OwnerProfileStrength(percent: 0, caption: "Finish setting up your page", steps: [])
        }
        let percent = Int((Double(onboarding.completedCount) / Double(onboarding.totalCount) * 100).rounded())
        let remaining = max(0, onboarding.totalCount - onboarding.completedCount)
        let caption = switch remaining {
        case 0: "Your page is complete"
        case 1: "One step from a complete page"
        default: "\(remaining) steps from a complete page"
        }
        let steps = onboarding.checklist.map { item in
            OwnerStrengthStep(id: item.key, label: item.label, done: item.done, ctaLabel: item.done ? nil : "Add")
        }
        return OwnerProfileStrength(percent: percent, caption: caption, steps: steps)
    }

    private func ownerReview(_ dto: BusinessOwnerReviewDTO) -> OwnerReviewItem {
        let meta = [relativeTimestamp(dto.createdAt), dto.gigTitle]
            .compactMap { $0?.isEmpty == false ? $0 : nil }
            .joined(separator: " · ")
        return OwnerReviewItem(
            id: dto.id,
            reviewerName: nonEmpty(dto.reviewerName) ?? "Anonymous",
            reviewerAvatarURL: dto.reviewerAvatar.flatMap(URL.init(string:)),
            meta: meta,
            rating: dto.rating,
            body: dto.comment ?? "",
            reply: nonEmpty(dto.ownerResponse)
        )
    }

    // MARK: - Formatting

    private func editedMeta(updatedAt: String?, isLive: Bool) -> String {
        if let relative = relativeTimestamp(updatedAt), !relative.isEmpty {
            return "Edited \(relative)"
        }
        return isLive ? "Live" : "Draft"
    }

    /// `1234 → "1.2k"`, `84 → "84"`.
    private func formatCount(_ value: Int) -> String {
        guard value >= 1000 else { return "\(value)" }
        let truncated = Double(value) / 1000
        return String(format: "%.1fk", truncated).replacingOccurrences(of: ".0k", with: "k")
    }

    /// Only positive trends render a pill — the tile draws a fixed up-arrow.
    private func trendLabel(_ trend: Int) -> String? {
        trend > 0 ? "\(trend)%" : nil
    }

    private func nonEmpty(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        return value
    }

    private func relativeTimestamp(_ iso: String?) -> String? {
        guard let iso, !iso.isEmpty else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = formatter.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) else {
            return nil
        }
        let elapsed = Date().timeIntervalSince(date)
        switch elapsed {
        case ..<60: return "just now"
        case ..<3600: return "\(Int(elapsed / 60))m ago"
        case ..<86400: return "\(Int(elapsed / 3600))h ago"
        case ..<604_800: return "\(Int(elapsed / 86400))d ago"
        default: return "\(Int(elapsed / 604_800))w ago"
        }
    }
}
