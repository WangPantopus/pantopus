//
//  PaymentsDTOs.swift
//  Pantopus
//
//  Decodable models for the Stripe payment-methods surface
//  (`backend/routes/pays.js`, mounted at `/api/payments`). Phase 3 (3A)
//  wires the Settings → Payments methods card: list saved methods, and
//  fetch PaymentSheet (SetupIntent) params for adding a card. Monetary /
//  Connect / payout DTOs land with 3C.
//

import Foundation

/// `GET /api/payments/methods` — route `backend/routes/pays.js:701`.
/// Wraps the saved-method rows from the `PaymentMethod` table.
public struct PaymentMethodsResponse: Decodable, Sendable, Hashable {
    public let paymentMethods: [PaymentMethodDTO]
}

/// One row from the `PaymentMethod` table. Columns are snake_case; a card
/// row carries `card_*`, a bank row carries `bank_*`.
public struct PaymentMethodDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let paymentMethodType: String?
    public let cardBrand: String?
    public let cardLast4: String?
    public let cardExpMonth: Int?
    public let cardExpYear: Int?
    public let bankName: String?
    public let bankLast4: String?
    public let bankAccountType: String?
    public let isDefault: Bool

    private enum CodingKeys: String, CodingKey {
        case id
        case paymentMethodType = "payment_method_type"
        case cardBrand = "card_brand"
        case cardLast4 = "card_last4"
        case cardExpMonth = "card_exp_month"
        case cardExpYear = "card_exp_year"
        case bankName = "bank_name"
        case bankLast4 = "bank_last4"
        case bankAccountType = "bank_account_type"
        case isDefault = "is_default"
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        paymentMethodType = try container.decodeIfPresent(String.self, forKey: .paymentMethodType)
        cardBrand = try container.decodeIfPresent(String.self, forKey: .cardBrand)
        cardLast4 = try container.decodeIfPresent(String.self, forKey: .cardLast4)
        cardExpMonth = try container.decodeIfPresent(Int.self, forKey: .cardExpMonth)
        cardExpYear = try container.decodeIfPresent(Int.self, forKey: .cardExpYear)
        bankName = try container.decodeIfPresent(String.self, forKey: .bankName)
        bankLast4 = try container.decodeIfPresent(String.self, forKey: .bankLast4)
        bankAccountType = try container.decodeIfPresent(String.self, forKey: .bankAccountType)
        isDefault = (try? container.decodeIfPresent(Bool.self, forKey: .isDefault)) ?? false
    }
}

/// `POST /api/payments/payment-sheet-add-card` — route
/// `backend/routes/pays.js:1095`. SetupIntent params for the mobile
/// PaymentSheet "add a card" flow. Keys are already camelCase server-side.
public struct AddCardSheetParams: Decodable, Sendable, Hashable {
    public let setupIntent: String
    public let ephemeralKey: String
    public let customer: String
    public let publishableKey: String?
}
