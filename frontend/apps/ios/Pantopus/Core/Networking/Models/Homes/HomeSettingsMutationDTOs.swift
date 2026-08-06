//
//  HomeSettingsMutationDTOs.swift
//  Pantopus
//
//  Response DTOs for home-settings mutations (leave home / cancel claim).
//

import Foundation

/// `POST /api/homes/:id/move-out` — route `backend/routes/home.js:3391`.
public struct MoveOutResponse: Decodable, Sendable, Hashable {
    public let message: String
    public let homeId: String?

    private enum CodingKeys: String, CodingKey {
        case message
        case homeId
    }
}

/// `DELETE /api/homes/:id/ownership-claims/:claimId` — route
/// `backend/routes/homeOwnership.js:603`.
public struct DeleteOwnershipClaimResponse: Decodable, Sendable, Hashable {
    public let ok: Bool
    public let deleted: Bool

    public init(ok: Bool = true, deleted: Bool = true) {
        self.ok = ok
        self.deleted = deleted
    }

    private enum CodingKeys: String, CodingKey {
        case ok, deleted
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        ok = try container.decodeIfPresent(Bool.self, forKey: .ok) ?? true
        deleted = try container.decodeIfPresent(Bool.self, forKey: .deleted) ?? true
    }
}
