//
//  HomeDTOs.swift
//  Pantopus
//
//  DTOs for the home endpoints in `backend/routes/home.js`. Because many
//  Home-column fields are untyped in the route response, we capture the
//  stable core (id, address, geography) and expose everything else via
//  `extras: [String: JSONValue]` to avoid inventing field types.
//

import Foundation

/// Stable fields from a Home row, the ones every downstream consumer needs.
/// Route citations per endpoint live on the wrapping response types.
public struct HomeDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let name: String?
    public let address: String?
    public let city: String?
    public let state: String?
    public let zipcode: String?
    public let homeType: String?
    public let visibility: String?
    public let description: String?
    public let createdAt: String?
    public let updatedAt: String?

    private enum CodingKeys: String, CodingKey {
        case id, name, address, city, state, zipcode
        case homeType = "home_type"
        case visibility, description
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }
}

/// Occupancy badge emitted per-home in `my-homes`. Route:
/// `backend/routes/home.js:1464`.
public struct HomeOccupancy: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let role: String
    public let roleBase: String
    public let isActive: Bool
    public let startAt: String?
    public let endAt: String?
    public let verificationStatus: String

    private enum CodingKeys: String, CodingKey {
        case id, role
        case roleBase = "role_base"
        case isActive = "is_active"
        case startAt = "start_at"
        case endAt = "end_at"
        case verificationStatus = "verification_status"
    }
}

/// Entry in the `my-homes` response, composed of the core home +
/// occupancy/ownership flags. Route: `backend/routes/home.js:1464`.
public struct MyHome: Decodable, Sendable, Hashable, Identifiable {
    public let home: HomeDTO
    public let occupancy: HomeOccupancy?
    public let ownershipStatus: String?
    public let verificationTier: String?
    public let isPrimaryOwner: Bool?
    public let pendingClaimId: String?

    public var id: String { home.id }

    // Backend returns these as siblings on the home row, not a nested
    // object; we decode the Home fields via a custom init so the outer
    // response shape stays flat.
    public init(from decoder: Decoder) throws {
        self.home = try HomeDTO(from: decoder)
        let container = try decoder.container(keyedBy: FlatKeys.self)
        self.occupancy = try container.decodeIfPresent(HomeOccupancy.self, forKey: .occupancy)
        self.ownershipStatus = try container.decodeIfPresent(String.self, forKey: .ownershipStatus)
        self.verificationTier = try container.decodeIfPresent(String.self, forKey: .verificationTier)
        self.isPrimaryOwner = try container.decodeIfPresent(Bool.self, forKey: .isPrimaryOwner)
        self.pendingClaimId = try container.decodeIfPresent(String.self, forKey: .pendingClaimId)
    }

    private enum FlatKeys: String, CodingKey {
        case occupancy
        case ownershipStatus = "ownership_status"
        case verificationTier = "verification_tier"
        case isPrimaryOwner = "is_primary_owner"
        case pendingClaimId = "pending_claim_id"
    }
}

/// `GET /api/homes/my-homes` envelope — route `backend/routes/home.js:1464`.
public struct MyHomesResponse: Decodable, Sendable, Hashable {
    public let homes: [MyHome]
    public let message: String?
}

/// `GET /api/homes/:id` envelope — route `backend/routes/home.js:2891`.
public struct HomeDetailResponse: Decodable, Sendable, Hashable {
    public let home: HomeDetail
}

/// Detailed home + owner/occupant graph.
public struct HomeDetail: Decodable, Sendable, Hashable {
    public let base: HomeDTO
    public let owner: HomeUserRef?
    public let occupants: [HomeOccupant]
    public let location: HomeLocation?
    public let isOwner: Bool
    public let isPendingOwner: Bool
    public let pendingClaimId: String?
    public let isOccupant: Bool
    public let owners: [HomeOwnershipRef]
    public let canDeleteHome: Bool

    public init(from decoder: Decoder) throws {
        self.base = try HomeDTO(from: decoder)
        let c = try decoder.container(keyedBy: FlatKeys.self)
        self.owner = try c.decodeIfPresent(HomeUserRef.self, forKey: .owner)
        self.occupants = try c.decodeIfPresent([HomeOccupant].self, forKey: .occupants) ?? []
        self.location = try c.decodeIfPresent(HomeLocation.self, forKey: .location)
        self.isOwner = try c.decodeIfPresent(Bool.self, forKey: .isOwner) ?? false
        self.isPendingOwner = try c.decodeIfPresent(Bool.self, forKey: .isPendingOwner) ?? false
        self.pendingClaimId = try c.decodeIfPresent(String.self, forKey: .pendingClaimId)
        self.isOccupant = try c.decodeIfPresent(Bool.self, forKey: .isOccupant) ?? false
        self.owners = try c.decodeIfPresent([HomeOwnershipRef].self, forKey: .owners) ?? []
        self.canDeleteHome = try c.decodeIfPresent(Bool.self, forKey: .canDeleteHome) ?? false
    }

    private enum FlatKeys: String, CodingKey {
        case owner, occupants, location
        case isOwner, isPendingOwner, pendingClaimId, isOccupant, owners
        case canDeleteHome = "can_delete_home"
    }
}

/// Basic user reference on owner/occupant lists.
public struct HomeUserRef: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let username: String
    public let name: String
}

/// Single occupant row.
public struct HomeOccupant: Decodable, Sendable, Hashable {
    public let userId: String
    public let createdAt: String
    public let user: HomeUserRef

    private enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case createdAt = "created_at"
        case user
    }
}

/// Geographical location (lon, lat).
public struct HomeLocation: Decodable, Sendable, Hashable {
    public let longitude: Double
    public let latitude: Double
}

/// Ownership reference with tier + flags.
public struct HomeOwnershipRef: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let subjectType: String
    public let subjectId: String
    public let ownerStatus: String
    public let isPrimaryOwner: Bool
    public let verificationTier: String

    private enum CodingKeys: String, CodingKey {
        case id
        case subjectType = "subject_type"
        case subjectId = "subject_id"
        case ownerStatus = "owner_status"
        case isPrimaryOwner = "is_primary_owner"
        case verificationTier = "verification_tier"
    }
}

/// `GET /api/homes/:id/public-profile` envelope — route `backend/routes/home.js:2439`.
public struct HomePublicProfileResponse: Decodable, Sendable, Hashable {
    public let home: HomePublicProfile

    public struct HomePublicProfile: Decodable, Sendable, Hashable, Identifiable {
        public let id: String
        public let name: String?
        public let address: String
        public let city: String
        public let state: String
        public let zipcode: String
        public let homeType: String?
        public let visibility: String
        public let description: String?
        public let createdAt: String
        public let hasVerifiedOwner: Bool
        public let verifiedOwner: VerifiedOwner?
        public let userMembershipStatus: String
        public let userResidencyClaim: ResidencyClaim?
        public let memberCount: Int
        public let nearbyGigs: Int

        private enum CodingKeys: String, CodingKey {
            case id, name, address, city, state, zipcode
            case homeType = "home_type"
            case visibility, description
            case createdAt = "created_at"
            case hasVerifiedOwner
            case verifiedOwner
            case userMembershipStatus, userResidencyClaim, memberCount, nearbyGigs
        }

        public struct VerifiedOwner: Decodable, Sendable, Hashable, Identifiable {
            public let id: String
            public let username: String
            public let name: String
            public let firstName: String
            public let lastName: String
            public let profilePictureURL: String?

            private enum CodingKeys: String, CodingKey {
                case id, username, name
                case firstName = "first_name"
                case lastName = "last_name"
                case profilePictureURL = "profile_picture_url"
            }
        }

        public struct ResidencyClaim: Decodable, Sendable, Hashable, Identifiable {
            public let id: String
            public let status: String
            public let createdAt: String

            private enum CodingKeys: String, CodingKey {
                case id, status
                case createdAt = "created_at"
            }
        }
    }
}

/// `POST /api/homes` request. Shape validated by `createHomeSchema` on the
/// server; we expose the commonly-used fields and let callers pass
/// additional ATTOM hints via `attomPropertyDetail` as a pre-built payload.
/// Route: `backend/routes/home.js:677`.
public struct CreateHomeRequest: Encodable, Sendable {
    public let address: String
    public let unitNumber: String?
    public let city: String
    public let state: String
    public let zipCode: String
    public let latitude: Double?
    public let longitude: Double?
    public let homeType: String?
    public let visibility: String?
    public let name: String?
    public let description: String?
    public let attomPropertyDetail: JSONEncodable?

    public init(
        address: String,
        unitNumber: String? = nil,
        city: String,
        state: String,
        zipCode: String,
        latitude: Double? = nil,
        longitude: Double? = nil,
        homeType: String? = nil,
        visibility: String? = nil,
        name: String? = nil,
        description: String? = nil,
        attomPropertyDetail: JSONEncodable? = nil
    ) {
        self.address = address
        self.unitNumber = unitNumber
        self.city = city
        self.state = state
        self.zipCode = zipCode
        self.latitude = latitude
        self.longitude = longitude
        self.homeType = homeType
        self.visibility = visibility
        self.name = name
        self.description = description
        self.attomPropertyDetail = attomPropertyDetail
    }

    private enum CodingKeys: String, CodingKey {
        case address
        case unitNumber = "unit_number"
        case city, state
        case zipCode = "zip_code"
        case latitude, longitude
        case homeType = "home_type"
        case visibility, name, description
        case attomPropertyDetail = "attom_property_detail"
    }
}

/// `POST /api/homes` response envelope — route `backend/routes/home.js:677`.
public struct CreateHomeResponse: Decodable, Sendable, Hashable {
    public let message: String
    public let home: HomeDTO
    public let requiresVerification: Bool
    public let verificationType: String?
    public let role: String

    private enum CodingKeys: String, CodingKey {
        case message, home
        case requiresVerification = "requires_verification"
        case verificationType = "verification_type"
        case role
    }
}

/// `POST /api/homes/property-suggestions` request. Route:
/// `backend/routes/home.js:540`.
public struct PropertySuggestionsRequest: Encodable, Sendable {
    public let address: String
    public let unitNumber: String?
    public let city: String
    public let state: String
    public let zipCode: String
    public let addressId: String?
    public let classification: String?

    public init(
        address: String,
        unitNumber: String? = nil,
        city: String,
        state: String,
        zipCode: String,
        addressId: String? = nil,
        classification: String? = nil
    ) {
        self.address = address
        self.unitNumber = unitNumber
        self.city = city
        self.state = state
        self.zipCode = zipCode
        self.addressId = addressId
        self.classification = classification
    }

    private enum CodingKeys: String, CodingKey {
        case address
        case unitNumber = "unit_number"
        case city, state
        case zipCode = "zip_code"
        case addressId = "address_id"
        case classification
    }
}

/// ATTOM property-suggestions payload. The upstream schema is
/// provider-defined; expose the raw JSON envelope rather than invent a
/// shape. Route: `backend/routes/home.js:540`.
public typealias PropertySuggestionsResponse = JSONValue

/// `POST /api/homes/check-address` request. Route:
/// `backend/routes/home.js:555`.
public struct CheckAddressRequest: Encodable, Sendable {
    public let addressId: String?
    public let address: String
    public let unitNumber: String?
    public let city: String
    public let state: String
    public let zipCode: String
    public let country: String?

    public init(
        addressId: String? = nil,
        address: String,
        unitNumber: String? = nil,
        city: String,
        state: String,
        zipCode: String,
        country: String? = nil
    ) {
        self.addressId = addressId
        self.address = address
        self.unitNumber = unitNumber
        self.city = city
        self.state = state
        self.zipCode = zipCode
        self.country = country
    }

    private enum CodingKeys: String, CodingKey {
        case addressId = "address_id"
        case address
        case unitNumber = "unit_number"
        case city, state
        case zipCode = "zip_code"
        case country
    }
}

/// `POST /api/homes/check-address` response.
public struct CheckAddressResponse: Decodable, Sendable, Hashable {
    public let exists: Bool
    public let homeCount: Int
    public let hasVerifiedMembers: Bool
    public let verdictStatus: String?

    private enum CodingKeys: String, CodingKey {
        case exists, homeCount, hasVerifiedMembers
        case verdictStatus = "verdict_status"
    }
}

/// Type-erased `Encodable` wrapper for request bodies whose schema is
/// defined server-side (e.g. ATTOM property details).
public struct JSONEncodable: Encodable, Sendable {
    private let encodeClosure: @Sendable (Encoder) throws -> Void
    public init<T: Encodable & Sendable>(_ wrapped: T) {
        self.encodeClosure = wrapped.encode
    }
    public func encode(to encoder: Encoder) throws {
        try encodeClosure(encoder)
    }
}
