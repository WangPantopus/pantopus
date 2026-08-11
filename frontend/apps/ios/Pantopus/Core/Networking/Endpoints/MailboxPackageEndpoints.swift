//
//  MailboxPackageEndpoints.swift
//  Pantopus
//
//  Endpoint builders for the Phase-2 package/unboxing writes in
//  `backend/routes/mailboxV2Phase2.js` (mounted at `/api/mailbox/v2/p2`,
//  `backend/app.js:316`). These back the A17.14 Unboxing screen, which
//  used to be a pure in-memory fixture — every action now persists.
//

import Foundation

public enum MailboxPackageEndpoints {
    /// `POST /api/mailbox/v2/p2/package/:mailId/unboxing` — route
    /// `backend/routes/mailboxV2Phase2.js:1217`. Records the condition
    /// photo (and/or unboxing video) on the `MailPackage` row and marks
    /// the unboxing complete.
    public static func recordUnboxing(mailId: String, request: PackageUnboxingRequest) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/mailbox/v2/p2/package/\(mailId)/unboxing",
            body: request
        )
    }

    /// `POST /api/mailbox/v2/p2/package/:mailId/save-warranty` — route
    /// `backend/routes/mailboxV2Phase2.js:1246`. Flips
    /// `warranty_saved` / `manual_saved` and auto-files the document to
    /// the caller's Home › Warranties vault folder.
    public static func saveWarranty(mailId: String, type: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/mailbox/v2/p2/package/\(mailId)/save-warranty",
            body: PackageSaveWarrantyRequest(type: type)
        )
    }

    /// `POST /api/mailbox/v2/p2/package/:mailId/gig` — route
    /// `backend/routes/mailboxV2Phase2.js:1280`. Posts the help gig for
    /// this package; `gigType` is `hold / inside / sign / custom /
    /// assembly` and the backend picks pre- vs post-delivery copy from
    /// the package status.
    public static func createPackageGig(mailId: String, request: PackageGigRequest) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/mailbox/v2/p2/package/\(mailId)/gig",
            body: request
        )
    }
}
