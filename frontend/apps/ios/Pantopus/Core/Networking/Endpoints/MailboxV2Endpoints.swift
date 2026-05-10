//
//  MailboxV2Endpoints.swift
//  Pantopus
//

import Foundation

/// Endpoint builders for the V2 mailbox routes in `backend/routes/mailboxV2.js`.
public enum MailboxV2Endpoints {
    /// `GET /api/mailbox/v2/drawers` — route `backend/routes/mailboxV2.js:214`.
    public static func drawers() -> Endpoint {
        Endpoint(method: .get, path: "/api/mailbox/v2/drawers")
    }

    /// `GET /api/mailbox/v2/drawer/:drawer` — route `backend/routes/mailboxV2.js:280`.
    public static func drawer(
        _ drawer: String,
        tab: String? = nil,
        limit: Int = 50,
        offset: Int = 0,
        homeId: String? = nil
    ) -> Endpoint {
        var query: [String: String] = ["limit": String(limit), "offset": String(offset)]
        if let tab { query["tab"] = tab }
        if let homeId { query["homeId"] = homeId }
        return Endpoint(method: .get, path: "/api/mailbox/v2/drawer/\(drawer)", query: query)
    }

    /// `GET /api/mailbox/v2/item/:id` — route `backend/routes/mailboxV2.js:366`.
    public static func item(mailId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/mailbox/v2/item/\(mailId)")
    }

    /// `POST /api/mailbox/v2/item/:id/action` — route `backend/routes/mailboxV2.js:459`.
    public static func itemAction(
        mailId: String,
        action: String
    ) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/mailbox/v2/item/\(mailId)/action",
            body: MailboxItemActionRequest(action: action)
        )
    }

    /// `GET /api/mailbox/v2/package/:mailId` — route `backend/routes/mailboxV2.js:634`.
    public static func package(mailId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/mailbox/v2/package/\(mailId)")
    }

    /// `PATCH /api/mailbox/v2/package/:mailId/status` — route `backend/routes/mailboxV2.js:670`.
    public static func packageStatusUpdate(
        mailId: String,
        request: PackageStatusUpdateRequest
    ) -> Endpoint {
        Endpoint(
            method: .patch,
            path: "/api/mailbox/v2/package/\(mailId)/status",
            body: request
        )
    }
}
