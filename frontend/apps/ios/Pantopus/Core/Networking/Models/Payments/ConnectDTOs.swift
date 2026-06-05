//
//  ConnectDTOs.swift
//  Pantopus
//
//  Decodable models for the Stripe Connect (Express) payout surface
//  (`backend/routes/pays.js` `/connect/*`). Block 3C reads onboarding /
//  payouts-enabled state to gate withdraw, and opens Stripe-hosted Account
//  Link + Express dashboard URLs. Bank/identity/KYC fields are intentionally
//  not modelled — Stripe hosts that UI.
//

import Foundation

/// Body for `POST /api/payments/connect/account`. Both fields default
/// server-side (US / individual); the client sends an empty object.
public struct ConnectCreateAccountRequest: Encodable, Sendable, Hashable {
    public let country: String?
    public let businessType: String?

    public init(country: String? = nil, businessType: String? = nil) {
        self.country = country
        self.businessType = businessType
    }
}

/// Body for `POST /api/payments/connect/onboarding`. The return / refresh URLs
/// default to the backend's hosted pages; the client may pass app-aware ones.
public struct ConnectOnboardingRequest: Encodable, Sendable, Hashable {
    public let returnUrl: String?
    public let refreshUrl: String?

    public init(returnUrl: String? = nil, refreshUrl: String? = nil) {
        self.returnUrl = returnUrl
        self.refreshUrl = refreshUrl
    }
}

/// The slim Connect-account projection the client needs to gate payouts.
/// Server keys are snake_case (`StripeAccount` row); only the gating fields
/// are decoded.
public struct ConnectAccountDTO: Decodable, Sendable, Hashable {
    public let stripeAccountId: String?
    public let chargesEnabled: Bool
    public let payoutsEnabled: Bool
    public let detailsSubmitted: Bool

    private enum CodingKeys: String, CodingKey {
        case stripeAccountId = "stripe_account_id"
        case chargesEnabled = "charges_enabled"
        case payoutsEnabled = "payouts_enabled"
        case detailsSubmitted = "details_submitted"
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        stripeAccountId = try container.decodeIfPresent(String.self, forKey: .stripeAccountId)
        chargesEnabled = (try? container.decodeIfPresent(Bool.self, forKey: .chargesEnabled)) ?? false
        payoutsEnabled = (try? container.decodeIfPresent(Bool.self, forKey: .payoutsEnabled)) ?? false
        detailsSubmitted = (try? container.decodeIfPresent(Bool.self, forKey: .detailsSubmitted)) ?? false
    }

    public init(
        stripeAccountId: String? = nil,
        chargesEnabled: Bool = false,
        payoutsEnabled: Bool = false,
        detailsSubmitted: Bool = false
    ) {
        self.stripeAccountId = stripeAccountId
        self.chargesEnabled = chargesEnabled
        self.payoutsEnabled = payoutsEnabled
        self.detailsSubmitted = detailsSubmitted
    }
}

/// `POST /api/payments/connect/account` — route `backend/routes/pays.js:161`.
public struct ConnectCreateAccountResponse: Decodable, Sendable, Hashable {
    public let stripeAccountId: String?
    public let account: ConnectAccountDTO?

    private enum CodingKeys: String, CodingKey {
        case stripeAccountId
        case account
    }
}

/// `GET /api/payments/connect/account` — route `backend/routes/pays.js:243`.
public struct ConnectAccountStatusResponse: Decodable, Sendable, Hashable {
    public let account: ConnectAccountDTO
}

/// `POST /api/payments/connect/onboarding` — route `backend/routes/pays.js:213`.
public struct ConnectOnboardingResponse: Decodable, Sendable, Hashable {
    public let onboardingUrl: String
    public let expiresAt: Int?
}

/// `POST /api/payments/connect/dashboard` — route `backend/routes/pays.js:265`.
public struct ConnectDashboardResponse: Decodable, Sendable, Hashable {
    public let dashboardUrl: String
}
