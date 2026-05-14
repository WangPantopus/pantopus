//
//  RelationshipsEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/relationships.js` — connection
//  requests, accept/reject, block/unblock, and the membership listing.
//

import Foundation

/// Endpoints under `/api/relationships/*`.
public enum RelationshipsEndpoints {
    /// `POST /api/relationships/requests` — send a connection request
    /// to another user. Route `backend/routes/relationships.js:67`.
    public static func sendRequest(body: ConnectionRequestBody) -> Endpoint {
        Endpoint(method: .post, path: "/api/relationships/requests", body: body)
    }

    /// `POST /api/relationships/:id/accept` — accept an inbound
    /// connection request. Route `backend/routes/relationships.js:217`.
    public static func accept(id: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/relationships/\(id)/accept")
    }

    /// `POST /api/relationships/:id/reject` — decline an inbound
    /// request. Route `backend/routes/relationships.js:295`.
    public static func reject(id: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/relationships/\(id)/reject")
    }
}

/// `POST /api/relationships/requests` body. Matches the Joi validator at
/// `backend/routes/relationships.js:40-43` (snake_case field names).
public struct ConnectionRequestBody: Encodable, Sendable {
    public let addresseeId: String
    public let message: String?

    public init(addresseeId: String, message: String? = nil) {
        self.addresseeId = addresseeId
        self.message = message
    }

    private enum CodingKeys: String, CodingKey {
        case addresseeId = "addressee_id"
        case message
    }
}

/// `POST /api/relationships/requests` response envelope. The backend
/// returns `{message, relationship}`; we only use `message` for telemetry.
public struct ConnectionRequestResponse: Decodable, Sendable {
    public let message: String?
}
