//
//  PostcardVerificationViewModel.swift
//  Pantopus
//
//  A12.7 — sibling status screen for the verify-landlord flow. Tracks
//  the postcard's USPS state (mailed / in transit / delivered) and the
//  user's 6-char code input. The submit state machine is the same
//  contract shared with the wizard VM: idle → submitting → submitted /
//  error.
//

import Foundation
import Observation

/// Lifecycle state of the physical postcard.
public enum PostcardDeliveryStage: Sendable, Equatable {
    case mailed
    case inTransit
    case delivered
}

/// Full payload describing the postcard verification surface.
public struct PostcardVerificationContent: Sendable, Equatable {
    public let recipientName: String
    public let street: String
    public let cityZip: String
    public let trackingNumber: String
    public let mailedOn: String
    public let inTransitOn: String?
    public let deliveredOn: String?
    public let resendAvailableOn: String

    public init(
        recipientName: String,
        street: String,
        cityZip: String,
        trackingNumber: String,
        mailedOn: String,
        inTransitOn: String?,
        deliveredOn: String?,
        resendAvailableOn: String
    ) {
        self.recipientName = recipientName
        self.street = street
        self.cityZip = cityZip
        self.trackingNumber = trackingNumber
        self.mailedOn = mailedOn
        self.inTransitOn = inTransitOn
        self.deliveredOn = deliveredOn
        self.resendAvailableOn = resendAvailableOn
    }
}

/// Deterministic sample data — used by previews + snapshot tests, and
/// as the default seed when a homeId pattern doesn't match a stored
/// postcard.
public enum PostcardVerificationSampleData {
    public static let deliveredContent = PostcardVerificationContent(
        recipientName: "Mira Patel",
        street: "412 Elm St, Apt 3B",
        cityZip: "San Francisco, CA 94114",
        trackingNumber: "#9405 5036 …8421",
        mailedOn: "Oct 9",
        inTransitOn: "Oct 11",
        deliveredOn: "Oct 12",
        resendAvailableOn: "Oct 15"
    )

    public static let inTransitContent = PostcardVerificationContent(
        recipientName: "Mira Patel",
        street: "412 Elm St, Apt 3B",
        cityZip: "San Francisco, CA 94114",
        trackingNumber: "#9405 5036 …8421",
        mailedOn: "Oct 9",
        inTransitOn: "Oct 11",
        deliveredOn: nil,
        resendAvailableOn: "Oct 15"
    )

    public static func content(for stage: PostcardDeliveryStage) -> PostcardVerificationContent {
        stage == .delivered ? deliveredContent : inTransitContent
    }

    public static func stage(for homeId: String) -> PostcardDeliveryStage {
        if homeId.localizedCaseInsensitiveContains("delivered") {
            return .delivered
        }
        return .inTransit
    }
}

/// Outbound events the host nav stack acts on.
public enum PostcardVerificationOutboundEvent: Sendable, Equatable {
    case dismiss
    /// Verify pressed and the code matched — caller should pop the
    /// screen and route to the verified-home success surface. The
    /// homeId is forwarded so callers can refresh that home's
    /// verification status.
    case verified(homeId: String)
}

/// View model for A12.7. Holds the current delivery stage, the 6-char
/// code the user is typing, and a `submitState` machine identical in
/// shape to the wizard's `VerifyLandlordSubmitState`.
@Observable
@MainActor
final class PostcardVerificationViewModel {
    // MARK: - Published state

    private(set) var stage: PostcardDeliveryStage
    private(set) var content: PostcardVerificationContent
    var codeInput: String = ""
    private(set) var submitState: VerifyLandlordSubmitState = .idle
    var pendingEvent: PostcardVerificationOutboundEvent?

    // MARK: - Init

    private let homeId: String
    private let submitDelayNanos: UInt64
    private let api: APIClient
    /// Offline/preview/test seam. When non-nil, `verify()` checks the
    /// typed code against this value locally instead of calling the
    /// backend — used by previews and unit/snapshot tests. Production
    /// passes `nil`, so the code is validated by
    /// `POST /api/homes/:id/verify-postcard`.
    private let expectedCode: String?

    init(
        homeId: String,
        stage: PostcardDeliveryStage? = nil,
        content: PostcardVerificationContent? = nil,
        expectedCode: String? = nil,
        api: APIClient = .shared,
        submitDelayNanos: UInt64 = 800_000_000
    ) {
        let resolvedStage = stage ?? PostcardVerificationSampleData.stage(for: homeId)
        self.stage = resolvedStage
        self.content = content
            ?? PostcardVerificationSampleData.content(for: resolvedStage)
        self.homeId = homeId
        self.expectedCode = expectedCode
        self.api = api
        self.submitDelayNanos = submitDelayNanos
    }

    // MARK: - Derived state

    /// Whether the user can type into the 6-char field. Locked while
    /// in transit so a wrong-code submit can't happen before delivery.
    var isCodeInputUnlocked: Bool {
        stage == .delivered
    }

    var isSubmitting: Bool {
        if case .submitting = submitState { return true }
        return false
    }

    /// Whether the primary CTA fires. Mirrors the design's disabled
    /// state on the in-transit frame.
    var primaryCTAEnabled: Bool {
        stage == .delivered && codeInput.count == 6 && !isSubmitting
    }

    var primaryCTALabel: String {
        isSubmitting ? "Verifying…" : "Verify code"
    }

    // MARK: - Mutations

    func updateCode(_ raw: String) {
        codeInput = String(raw.uppercased().prefix(6))
    }

    /// Resend the postcard — reissues a new code via
    /// `POST /api/homes/:id/request-postcard`. The offline/test seam
    /// (`expectedCode != nil`) just clears the input.
    func resendPostcard() {
        codeInput = ""
        guard expectedCode == nil else { return }
        Task { [api, homeId] in
            _ = try? await api.request(
                HomesEndpoints.requestPostcard(homeId: homeId),
                as: RequestPostcardResponse.self
            )
        }
    }

    /// Used by the debug / preview tooling and the snapshot tests to
    /// flip between the in-transit and delivered frames without
    /// waiting on the simulated USPS clock.
    func setStage(_ next: PostcardDeliveryStage) {
        stage = next
        content = PostcardVerificationSampleData.content(for: next)
        if next != .delivered {
            codeInput = ""
        }
    }

    func verifyTapped() {
        guard primaryCTAEnabled else { return }
        Task { await verify() }
    }

    func dismissTapped() {
        pendingEvent = .dismiss
    }

    func acknowledgePendingEvent() {
        pendingEvent = nil
    }

    // MARK: - Submit

    private func verify() async {
        submitState = .submitting
        if let expectedCode {
            // Offline/test seam — compare locally, no network.
            try? await Task.sleep(nanoseconds: submitDelayNanos)
            if codeInput == expectedCode {
                submitState = .submitted
                pendingEvent = .verified(homeId: homeId)
            } else {
                submitState = .error(message: "That code didn't match. Double-check the postcard.")
                codeInput = ""
            }
            return
        }
        do {
            _ = try await api.request(
                HomesEndpoints.verifyPostcard(
                    homeId: homeId,
                    request: VerifyPostcardRequest(code: codeInput)
                ),
                as: VerifyPostcardResponse.self
            )
            submitState = .submitted
            pendingEvent = .verified(homeId: homeId)
        } catch {
            if case .transport = (error as? APIError) {
                submitState = .error(message: "You're offline. Try again when you're back online.")
            } else {
                submitState = .error(message: "That code didn't match. Double-check the postcard.")
                codeInput = ""
            }
        }
    }
}
