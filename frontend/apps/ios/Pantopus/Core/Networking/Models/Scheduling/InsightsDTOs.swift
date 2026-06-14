//
//  InsightsDTOs.swift
//  Pantopus
//
//  DTOs for the bookings summary + insights reports — routes
//  `/api/scheduling/bookings/summary`, `…/bookings/insights/no-shows`,
//  `…/bookings/insights/team`. See `reference/calendarly-backend-api.md`.
//

import Foundation

/// The next upcoming booking, surfaced on the summary card.
public struct NextBookingDTO: Decodable, Sendable, Hashable {
    public let startAt: String?
    public let inviteeName: String?

    enum CodingKeys: String, CodingKey {
        case startAt = "start_at"
        case inviteeName = "invitee_name"
    }
}

/// `GET /bookings/summary` → counts + next booking for the Hub summary card.
public struct SchedulingSummaryDTO: Decodable, Sendable, Hashable {
    public let upcomingCount: Int?
    public let pendingCount: Int?
    public let totalThisMonth: Int?
    public let noShowRate: Double?
    public let nextBooking: NextBookingDTO?
}

/// One per-event-type or per-host no-show row.
public struct NoShowGroupDTO: Decodable, Sendable, Hashable {
    public let eventTypeId: String?
    public let userId: String?
    public let name: String?
    public let count: Int?
    public let rate: Double?

    enum CodingKeys: String, CodingKey {
        case eventTypeId = "event_type_id"
        case userId = "user_id"
        case name
        case count
        case rate
    }
}

/// One recent no-show row.
public struct NoShowRecentDTO: Decodable, Sendable, Hashable {
    public let bookingId: String?
    public let inviteeName: String?
    public let scheduledAt: String?
    public let noShowAt: String?

    enum CodingKeys: String, CodingKey {
        case bookingId = "booking_id"
        case inviteeName = "invitee_name"
        case scheduledAt = "scheduled_at"
        case noShowAt = "no_show_at"
    }
}

/// `GET /bookings/insights/no-shows` → no-show analytics report.
public struct NoShowReportResponse: Decodable, Sendable, Hashable {
    public let noShowCount: Int?
    public let noShowRate: Double?
    public let byEventType: [NoShowGroupDTO]?
    public let byHost: [NoShowGroupDTO]?
    public let recent: [NoShowRecentDTO]?
}

/// One team-member row in the team-performance report.
public struct TeamMemberPerformanceDTO: Decodable, Sendable, Hashable {
    public let userId: String?
    public let name: String?
    public let bookingsCount: Int?
    public let revenue: Double?
    public let noShowRate: Double?
    public let avgDuration: Double?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case name
        case bookingsCount
        case revenue
        case noShowRate
        case avgDuration
    }
}

/// `GET /bookings/insights/team` → business team-performance report.
public struct TeamPerformanceResponse: Decodable, Sendable, Hashable {
    public let teamMembers: [TeamMemberPerformanceDTO]?
    public let totalRevenue: Double?
    public let totalBookings: Int?
    public let avgBookingValue: Double?
}
