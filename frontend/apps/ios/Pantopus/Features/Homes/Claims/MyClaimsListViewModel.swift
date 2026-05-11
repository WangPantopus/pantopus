//
//  MyClaimsListViewModel.swift
//  Pantopus
//
//  Backs `MyClaimsListView`. Fetches `GET /api/homes/my-ownership-claims`
//  (route `backend/routes/homeOwnership.js:217`) and maps each claim to
//  a `status_chip` row. Backend always returns the opaque
//  `"under_review"` status while a claim is open; once approved /
//  rejected the same field flips. We map those three values to chip
//  variants below.
//
//  TODO(design): a richer claim-status detail view isn't drawn yet —
//  tapping a row currently no-ops with a console log.
//

import Foundation
import Logging
import Observation
import SwiftUI

/// ViewModel for the "My claims" list.
@Observable
@MainActor
final class MyClaimsListViewModel: ListOfRowsDataSource {
    let title = "My claims"
    var topBarAction: TopBarAction? { nil }
    let tabs: [ListOfRowsTab] = []
    var selectedTab: String = ""
    var fab: FABAction? { nil }

    private(set) var state: ListOfRowsState = .loading

    private let api: APIClient
    private let onStartNewClaim: @Sendable () -> Void
    private let logger = Logger(label: "app.pantopus.ios.MyClaimsList")

    init(
        api: APIClient = .shared,
        onStartNewClaim: @escaping @Sendable () -> Void = {}
    ) {
        self.api = api
        self.onStartNewClaim = onStartNewClaim
    }

    func load() async {
        // Unlike MyHomes, we always refetch — claim status changes
        // server-side within hours (per the success-step copy) and the
        // user expects to see status flips on return-nav, not only via
        // pull-to-refresh.
        if case .loading = state {} else {
            state = .loading
        }
        await fetch()
    }

    func refresh() async {
        await fetch()
    }

    /// Endpoint isn't paginated server-side.
    func loadMoreIfNeeded() async {}

    private func fetch() async {
        do {
            let response: MyOwnershipClaimsResponse =
                try await api.request(HomesEndpoints.myOwnershipClaims())
            if response.claims.isEmpty {
                state = .empty(
                    ListOfRowsState.EmptyContent(
                        icon: .shieldCheck,
                        headline: "No claims yet",
                        // Empty-state CTA opens the AddHome wizard
                        // (which kicks off verification when the user
                        // selects "Owner" on the role step). The "Add a
                        // home" copy matches the wizard the CTA actually
                        // routes to, so the user doesn't expect a
                        // claim-existing-home picker that doesn't exist.
                        subcopy: "Submit a claim from a home dashboard. New here? Add a home and pick the Owner role to start.",
                        ctaTitle: "Add a home",
                        onCTA: onStartNewClaim
                    )
                )
            } else {
                let rows = response.claims.map { row(for: $0) }
                state = .loaded(sections: [RowSection(rows: rows)], hasMore: false)
            }
        } catch {
            state = .error(
                message: (error as? APIError)?.errorDescription
                    ?? "Couldn't load your claims."
            )
        }
    }

    private func row(for claim: OwnershipClaimDTO) -> RowModel {
        RowModel(
            id: claim.id,
            title: "Claim \(claim.id.prefix(8))",
            subtitle: subtitle(for: claim),
            template: .statusChip,
            leading: .icon(.shieldCheck, tint: Theme.Color.primary600),
            trailing: .statusChip(
                text: statusText(for: claim.status),
                variant: statusVariant(for: claim.status)
            ),
            onTap: { @Sendable [logger] in
                Task { @MainActor in
                    logger.info("Claim row tapped: \(claim.id) — detail view not yet designed.")
                }
            }
        )
    }

    private func subtitle(for claim: OwnershipClaimDTO) -> String? {
        // Show the friendlier method + the relative submitted date.
        let method = friendlyMethod(claim.method)
        let relative = relativeDate(claim.createdAt)
        return [method, relative].compactMap { $0 }.joined(separator: " · ")
    }

    private func friendlyMethod(_ method: String) -> String? {
        switch method {
        case "doc_upload": return "Document upload"
        case "fast_track": return "Fast-track invite"
        case "id_verification": return "ID verification"
        default: return method.isEmpty ? nil : method
        }
    }

    private func relativeDate(_ iso: String) -> String? {
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = parser.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) else {
            return nil
        }
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return "Submitted \(formatter.localizedString(for: date, relativeTo: Date()))"
    }

    private func statusText(for status: String) -> String {
        switch status {
        case "verified", "approved", "complete": return "Verified"
        case "rejected", "denied": return "Not approved"
        case "under_review", "pending", "submitted": return "Under review"
        default: return status.replacingOccurrences(of: "_", with: " ").capitalized
        }
    }

    private func statusVariant(for status: String) -> StatusChipVariant {
        switch status {
        case "verified", "approved", "complete": return .success
        case "rejected", "denied": return .error
        case "under_review", "pending", "submitted": return .info
        default: return .neutral
        }
    }
}
