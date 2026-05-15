//
//  SubmitClaimRequest.swift
//  Pantopus
//
//  Request + response for `POST /api/homes/:id/ownership-claims` —
//  route `backend/routes/homeOwnership.js:251`. Mirrors
//  `submitClaimSchema` (`backend/routes/homeOwnership.js:33`):
//  `claim_type` enum (default `owner`) + `method` enum (required).
//  Note: no `note` field server-side — the wizard's optional textarea
//  is wired into evidence metadata instead. See PR description.
//

import Foundation

/// `POST /api/homes/:id/ownership-claims` body.
public struct SubmitClaimRequest: Encodable, Sendable, Hashable {
    public let claimType: String
    public let method: String

    public init(claimType: String = "owner", method: String) {
        self.claimType = claimType
        self.method = method
    }

    private enum CodingKeys: String, CodingKey {
        case claimType = "claim_type"
        case method
    }
}

/// Envelope for `POST /api/homes/:id/ownership-claims`. Backend keeps
/// the internal state opaque — `claim.status` is always
/// `"under_review"` per the handler at `:430`.
public struct SubmitClaimResponse: Decodable, Sendable, Hashable {
    public let message: String
    public let claim: ClaimEnvelope
    public let nextStep: String?

    public struct ClaimEnvelope: Decodable, Sendable, Hashable {
        public let id: String?
        public let status: String
    }

    private enum CodingKeys: String, CodingKey {
        case message, claim
        case nextStep = "next_step"
    }
}

/// `POST /api/homes/:id/ownership-claims/:claimId/evidence` body.
/// Mirrors `uploadEvidenceSchema` (`backend/routes/homeOwnership.js:43`).
public struct UploadEvidenceRequest: Encodable, Sendable, Hashable {
    public let evidenceType: String
    public let provider: String
    public let storageRef: String?
    public let metadata: [String: String]?

    public init(
        evidenceType: String,
        provider: String = "manual",
        storageRef: String? = nil,
        metadata: [String: String]? = nil
    ) {
        self.evidenceType = evidenceType
        self.provider = provider
        self.storageRef = storageRef
        self.metadata = metadata
    }

    public func encode(to encoder: any Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(evidenceType, forKey: .evidenceType)
        try c.encode(provider, forKey: .provider)
        try c.encodeIfPresent(storageRef, forKey: .storageRef)
        try c.encodeIfPresent(metadata, forKey: .metadata)
    }

    private enum CodingKeys: String, CodingKey {
        case evidenceType = "evidence_type"
        case provider
        case storageRef = "storage_ref"
        case metadata
    }
}

/// Response envelope for the evidence endpoint.
/// Backend returns `{ evidence: <row>, verification_tier: <object> }`
/// — both fields are loosely shaped, modelled as JSONValue.
public struct UploadEvidenceResponse: Decodable, Sendable, Hashable {
    public let evidence: JSONValue
    public let verificationTier: JSONValue?

    private enum CodingKeys: String, CodingKey {
        case evidence
        case verificationTier = "verification_tier"
    }
}

/// Response envelope for `POST /api/files/upload` —
/// `backend/routes/files.js:781`.
public struct FileUploadResponse: Decodable, Sendable, Hashable {
    public let message: String
    public let file: FileRef

    public struct FileRef: Decodable, Sendable, Hashable {
        public let id: String
        public let url: String
    }
}
