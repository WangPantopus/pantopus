//
//  ResolveRoutingRequest.swift
//  Pantopus
//
//  DTO for `POST /api/mailbox/v2/resolve` — route
//  `backend/routes/mailboxV2.js:555`. Schema:
//  `resolveRoutingSchema` at line 12 of the same file.
//

import Foundation

/// Body for `POST /api/mailbox/v2/resolve`. Mirrors `resolveRoutingSchema`.
public struct ResolveRoutingRequest: Encodable, Sendable, Hashable {
    public let mailId: String
    public let drawer: String
    public let addAlias: Bool?
    public let aliasString: String?

    public init(
        mailId: String,
        drawer: String,
        addAlias: Bool? = nil,
        aliasString: String? = nil
    ) {
        self.mailId = mailId
        self.drawer = drawer
        self.addAlias = addAlias
        self.aliasString = aliasString
    }
}

/// Response for `POST /api/mailbox/v2/resolve` (line 604).
public struct ResolveRoutingResponse: Decodable, Sendable, Hashable {
    public let message: String
    public let drawer: String
}
