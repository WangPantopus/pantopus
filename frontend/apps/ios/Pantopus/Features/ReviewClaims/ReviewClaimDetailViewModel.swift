//
//  ReviewClaimDetailViewModel.swift
//  Pantopus
//
//  P1.1 — Admin claim-detail view-model. Loads the full claim payload
//  (claimant identity, home record, evidence) for one claim id and
//  exposes Approve / Reject / Request-more-info handlers that hit the
//  `POST /api/admin/claims/:id/review` endpoint.
//

import Foundation
import Observation
import SwiftUI

@MainActor
public enum ReviewClaimDetailState {
    case loading
    case loaded(AdminClaimDetailResponse)
    case error(message: String)
}

@Observable
@MainActor
public final class ReviewClaimDetailViewModel {
    public private(set) var state: ReviewClaimDetailState = .loading
    public private(set) var reviewingAction: AdminClaimReviewAction?
    public private(set) var toast: ToastMessage?

    private let api: APIClient
    private let claimId: String
    private var loadedOnce = false

    init(
        claimId: String,
        api: APIClient = .shared
    ) {
        self.claimId = claimId
        self.api = api
    }

    public func load() async {
        // Same pattern as the queue VM — refetch on every appear so the
        // admin always sees fresh state.
        if !loadedOnce { state = .loading }
        do {
            let response: AdminClaimDetailResponse = try await api.request(
                AdminEndpoints.claimDetail(claimId: claimId)
            )
            state = .loaded(response)
            loadedOnce = true
        } catch {
            state = .error(message: "Couldn't load claim details. Try again.")
        }
    }

    /// Submit the reviewer decision. Surfaces a toast on success + sets
    /// the toast text on failure so the host can show the in-screen
    /// error without spawning a separate state.
    public func review(
        _ action: AdminClaimReviewAction,
        note: String? = nil
    ) async -> Bool {
        guard reviewingAction == nil else { return false }
        reviewingAction = action
        defer { reviewingAction = nil }

        do {
            let _: AdminClaimReviewResponse = try await api.request(
                AdminEndpoints.reviewClaim(
                    claimId: claimId,
                    request: AdminClaimReviewRequest(action: action, note: note)
                )
            )
            toast = ToastMessage(text: Self.successCopy(for: action), kind: .success)
            // Refresh detail so the body shows the new state.
            await load()
            return true
        } catch {
            toast = ToastMessage(text: "Couldn't review this claim. Try again.", kind: .error)
            return false
        }
    }

    public func clearToast() {
        toast = nil
    }

    private static func successCopy(for action: AdminClaimReviewAction) -> String {
        switch action {
        case .approve: "Claim approved. User has been verified."
        case .reject: "Claim rejected. User has been notified."
        case .requestMoreInfo: "More info requested. User has been notified."
        }
    }
}
