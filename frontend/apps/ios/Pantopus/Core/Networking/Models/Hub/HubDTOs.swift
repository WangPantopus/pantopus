//
//  HubDTOs.swift
//  Pantopus
//
//  DTOs for the hub endpoints in `backend/routes/hub.js`. All field names
//  are preserved from the route responses (snake_case and camelCase both
//  appear verbatim; the decoder uses per-field coding keys, not
//  convertFromSnakeCase, so surprising case changes never happen).
//

import Foundation

// MARK: - Hub overview (GET /api/hub — backend/routes/hub.js:24)

/// `GET /api/hub` — the hub overview bundle.
public struct HubResponse: Decodable, Sendable, Hashable {
    public let user: HubUser
    public let context: HubContext
    public let availability: HubAvailability
    public let homes: [HubHomeSummary]
    public let businesses: [HubBusinessSummary]
    public let setup: HubSetup
    public let statusItems: [HubStatusItem]
    public let cards: HubCards
    public let jumpBackIn: [HubJumpBackItem]
    public let activity: [HubActivityItem]
    public let neighborDensity: HubNeighborDensity?

    public struct HubUser: Decodable, Sendable, Hashable, Identifiable {
        public let id: String
        public let name: String
        public let firstName: String?
        public let username: String
        public let avatarUrl: String?
        public let email: String
    }

    public struct HubContext: Decodable, Sendable, Hashable {
        public let activeHomeId: String?
        public let activePersona: ActivePersona

        public struct ActivePersona: Decodable, Sendable, Hashable {
            public let type: String
        }
    }

    public struct HubAvailability: Decodable, Sendable, Hashable {
        public let hasHome: Bool
        public let hasBusiness: Bool
        public let hasPayoutMethod: Bool
    }

    public struct HubHomeSummary: Decodable, Sendable, Hashable, Identifiable {
        public let id: String
        public let name: String
        public let addressShort: String
        public let city: String?
        public let state: String?
        public let latitude: Double?
        public let longitude: Double?
        public let isPrimary: Bool
        public let roleBase: String
    }

    public struct HubBusinessSummary: Decodable, Sendable, Hashable, Identifiable {
        public let id: String
        public let name: String
        public let username: String
        public let roleBase: String
    }

    public struct HubSetup: Decodable, Sendable, Hashable {
        public let steps: [HubSetupStep]
        public let allDone: Bool
        public let profileCompleteness: ProfileCompleteness

        public struct HubSetupStep: Decodable, Sendable, Hashable {
            public let key: String
            public let done: Bool
        }

        public struct ProfileCompleteness: Decodable, Sendable, Hashable {
            public let score: Double
            public let checks: Checks
            public let missingFields: [String]

            public struct Checks: Decodable, Sendable, Hashable {
                public let firstName: Bool
                public let lastName: Bool
                public let photo: Bool
                public let bio: Bool
                public let skills: Bool
            }
        }
    }

    public struct HubStatusItem: Decodable, Sendable, Hashable, Identifiable {
        public let id: String
        public let type: String
        public let pillar: String
        public let title: String
        public let subtitle: String?
        public let severity: String
        public let count: Int?
        public let dueAt: String?
        public let route: String
        public let entityRef: EntityRef?

        public struct EntityRef: Decodable, Sendable, Hashable {
            public let kind: String
            public let id: String
        }
    }

    public struct HubCards: Decodable, Sendable, Hashable {
        public let personal: HubPersonalCard
        public let home: HubHomeCard?
        public let business: HubBusinessCard?

        public struct HubPersonalCard: Decodable, Sendable, Hashable {
            public let unreadChats: Int
            public let earnings: Double
            public let gigsNearby: Int
            public let rating: Double
            public let reviewCount: Int
        }

        public struct HubHomeCard: Decodable, Sendable, Hashable {
            public let newMail: Int
            public let billsDue: [HubBill]
            public let tasksDue: [HubTask]
            public let memberCount: Int

            public struct HubBill: Decodable, Sendable, Hashable, Identifiable {
                public let id: String
                public let name: String
                public let amount: Double
                public let dueAt: String
            }

            public struct HubTask: Decodable, Sendable, Hashable, Identifiable {
                public let id: String
                public let title: String
                public let dueAt: String
            }
        }

        public struct HubBusinessCard: Decodable, Sendable, Hashable {
            public let newOrders: Int
            public let unreadThreads: Int
            public let pendingPayout: Double
        }
    }

    public struct HubJumpBackItem: Decodable, Sendable, Hashable {
        public let title: String
        public let route: String
        public let icon: String
    }

    public struct HubActivityItem: Decodable, Sendable, Hashable, Identifiable {
        public let id: String
        public let pillar: String
        public let title: String
        public let at: String
        public let read: Bool
        public let route: String
    }

    public struct HubNeighborDensity: Decodable, Sendable, Hashable {
        public let count: Int
        public let radiusMiles: Double
        public let milestone: String?
    }
}

// MARK: - Hub Today (GET /api/hub/today — backend/routes/hub.js:596)

/// `GET /api/hub/today` — provider-orchestrated response whose precise shape
/// varies by provider (weather, AQI, alerts, signals). We type only the
/// envelope and expose the inner payload as [`JSONValue`](x-source-tag://JSONValue).
public struct HubTodayResponse: Decodable, Sendable, Hashable {
    /// The untyped payload; `nil` when the service reports
    /// `error: CONTEXT_UNAVAILABLE`.
    public let today: JSONValue?
    /// Server-side error signal when context can't be assembled.
    public let error: String?
}

// MARK: - Hub Discovery (GET /api/hub/discovery — backend/routes/hub.js:720)

/// `GET /api/hub/discovery` response envelope.
public struct HubDiscoveryResponse: Decodable, Sendable, Hashable {
    public let items: [Item]

    public struct Item: Decodable, Sendable, Hashable, Identifiable {
        public let id: String
        public let type: String
        public let title: String
        public let meta: String
        public let category: String
        public let avatarUrl: String?
        public let route: String
    }
}
