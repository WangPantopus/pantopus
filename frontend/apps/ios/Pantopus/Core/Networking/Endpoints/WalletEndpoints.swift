//
//  WalletEndpoints.swift
//  Pantopus
//
//  Read-path endpoint builders for `backend/routes/wallet.js`. P1-F wires
//  the READ surface only — balance, transaction history, and the
//  pending-release breakdown. `POST /api/wallet/withdraw` and any payout
//  action are intentionally NOT modelled here; they land with the Stripe
//  Connect integration in Phase 3.
//

import Foundation

public enum WalletEndpoints {
    /// `GET /api/wallet` — route `backend/routes/wallet.js:55`. Current
    /// balance + lifetime summary. Server creates the wallet on first
    /// access. Balance + frozen flag drive the available-balance hero.
    public static func balance() -> Endpoint {
        Endpoint(method: .get, path: "/api/wallet")
    }

    /// `GET /api/wallet/transactions` — route `backend/routes/wallet.js:124`.
    /// Paginated history; the Wallet screen reads the first page for the
    /// Recent-activity feed.
    public static func transactions(limit: Int = 50, offset: Int = 0) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/wallet/transactions",
            query: ["limit": String(limit), "offset": String(offset)]
        )
    }

    /// `GET /api/wallet/pending-release` — route `backend/routes/wallet.js:160`.
    /// In-review + releasing-soon escrow breakdown that explains why the
    /// available balance trails total earnings.
    public static func pendingRelease() -> Endpoint {
        Endpoint(method: .get, path: "/api/wallet/pending-release")
    }
}
