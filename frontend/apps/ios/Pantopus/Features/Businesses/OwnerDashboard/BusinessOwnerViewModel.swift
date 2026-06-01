//
//  BusinessOwnerViewModel.swift
//  Pantopus
//
//  A10.7 — View-model for the single-business owner dashboard. In B3.2 the
//  insights, profile strength, and reviews are sample-driven (no analytics
//  or review-reply backend yet); the reply composer commits to local state
//  via `BusinessOwnerContent.applyingReply(_:to:)`. The shared public render
//  reused by "preview as neighbor" is the A10.6 sample (B3.1).
//
//  The screen still ships the four render states (loading shimmer / loaded /
//  not-found / error) and a brief simulated load so the shimmer is exercised
//  in the running app; snapshots render the loaded frames directly off
//  `BusinessOwnerContent`.
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
    private let logger = Logger(label: "app.pantopus.ios.BusinessOwner")

    /// - Parameters:
    ///   - businessId: The owned business id (carried for routing parity).
    ///   - content: Pre-built content for previews / snapshots / tests. When
    ///     `nil` the view-model derives the B3.2 sample.
    public init(businessId: String, content: BusinessOwnerContent? = nil) {
        self.businessId = businessId
        injectedContent = content
    }

    public func load() async {
        state = .loading
        if let injectedContent {
            state = .loaded(injectedContent)
            return
        }
        // B3.2: sample-driven. A short delay exercises the shimmer skeleton
        // in the running app; tests inject content and skip this path.
        try? await Task.sleep(nanoseconds: 350_000_000)
        state = .loaded(resolveSampleContent())
    }

    public func refresh() async {
        if let injectedContent {
            state = .loaded(injectedContent)
            return
        }
        state = .loaded(resolveSampleContent())
    }

    /// Commit a review reply to local state (no backend in B3.2).
    public func submitReply(reviewId: String, text: String) {
        guard case let .loaded(content) = state else { return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        logger.debug("Stubbed review reply for \(reviewId) on business \(businessId)")
        state = .loaded(content.applyingReply(trimmed, to: reviewId))
    }

    private func resolveSampleContent() -> BusinessOwnerContent {
        BusinessOwnerSampleData.marlow
    }
}
