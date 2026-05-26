//
//  CalendarEventDTOs.swift
//  Pantopus
//
//  DTOs for the Home calendar endpoints
//  (`backend/routes/home.js:4793` GET, `:4827` POST, `:4874` PUT,
//  `:4912` DELETE).
//

import Foundation

/// One row from `GET /api/homes/:id/events`.
public struct CalendarEventDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let homeId: String
    public let eventType: String
    public let title: String
    public let description: String?
    /// ISO-8601 timestamp.
    public let startAt: String
    /// ISO-8601 timestamp. Nil for "all-day" / point-in-time events.
    public let endAt: String?
    public let locationNotes: String?
    /// iCal RRULE string ("FREQ=WEEKLY;BYDAY=SU"), nil for one-off events.
    public let recurrenceRule: String?
    /// Backend stores an array of user-ids (assigned household members).
    public let assignedTo: [String]?
    public let alertsEnabled: Bool?
    public let createdBy: String?
    public let createdAt: String?
    public let updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case homeId = "home_id"
        case eventType = "event_type"
        case title
        case description
        case startAt = "start_at"
        case endAt = "end_at"
        case locationNotes = "location_notes"
        case recurrenceRule = "recurrence_rule"
        case assignedTo = "assigned_to"
        case alertsEnabled = "alerts_enabled"
        case createdBy = "created_by"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }

    public init(
        id: String,
        homeId: String,
        eventType: String,
        title: String,
        description: String? = nil,
        startAt: String,
        endAt: String? = nil,
        locationNotes: String? = nil,
        recurrenceRule: String? = nil,
        assignedTo: [String]? = nil,
        alertsEnabled: Bool? = nil,
        createdBy: String? = nil,
        createdAt: String? = nil,
        updatedAt: String? = nil
    ) {
        self.id = id
        self.homeId = homeId
        self.eventType = eventType
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.locationNotes = locationNotes
        self.recurrenceRule = recurrenceRule
        self.assignedTo = assignedTo
        self.alertsEnabled = alertsEnabled
        self.createdBy = createdBy
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

/// Envelope for `GET /api/homes/:id/events` — route
/// `backend/routes/home.js:4793`.
public struct GetHomeEventsResponse: Decodable, Sendable {
    public let events: [CalendarEventDTO]

    public init(events: [CalendarEventDTO]) {
        self.events = events
    }
}

/// Request body for `POST /api/homes/:id/events` — route
/// `backend/routes/home.js:4827`.
public struct CreateHomeEventRequest: Encodable, Sendable {
    public let eventType: String
    public let title: String
    public let description: String?
    public let startAt: String
    public let endAt: String?
    public let locationNotes: String?
    public let recurrenceRule: String?
    public let assignedTo: [String]?
    public let alertsEnabled: Bool?

    enum CodingKeys: String, CodingKey {
        case eventType = "event_type"
        case title
        case description
        case startAt = "start_at"
        case endAt = "end_at"
        case locationNotes = "location_notes"
        case recurrenceRule = "recurrence_rule"
        case assignedTo = "assigned_to"
        case alertsEnabled = "alerts_enabled"
    }

    public init(
        eventType: String,
        title: String,
        startAt: String,
        description: String? = nil,
        endAt: String? = nil,
        locationNotes: String? = nil,
        recurrenceRule: String? = nil,
        assignedTo: [String]? = nil,
        alertsEnabled: Bool? = nil
    ) {
        self.eventType = eventType
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.locationNotes = locationNotes
        self.recurrenceRule = recurrenceRule
        self.assignedTo = assignedTo
        self.alertsEnabled = alertsEnabled
    }
}

/// Request body for `PUT /api/homes/:id/events/:eventId` — route
/// `backend/routes/home.js:5082`. All fields optional; the route picks
/// up only keys present in the body via an allow-list.
public struct UpdateHomeEventRequest: Encodable, Sendable {
    public let eventType: String?
    public let title: String?
    public let description: String?
    public let startAt: String?
    public let endAt: String?
    public let locationNotes: String?
    public let recurrenceRule: String?
    public let assignedTo: [String]?
    public let alertsEnabled: Bool?

    enum CodingKeys: String, CodingKey {
        case eventType = "event_type"
        case title
        case description
        case startAt = "start_at"
        case endAt = "end_at"
        case locationNotes = "location_notes"
        case recurrenceRule = "recurrence_rule"
        case assignedTo = "assigned_to"
        case alertsEnabled = "alerts_enabled"
    }

    public init(
        eventType: String? = nil,
        title: String? = nil,
        description: String? = nil,
        startAt: String? = nil,
        endAt: String? = nil,
        locationNotes: String? = nil,
        recurrenceRule: String? = nil,
        assignedTo: [String]? = nil,
        alertsEnabled: Bool? = nil
    ) {
        self.eventType = eventType
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.locationNotes = locationNotes
        self.recurrenceRule = recurrenceRule
        self.assignedTo = assignedTo
        self.alertsEnabled = alertsEnabled
    }

    public func encode(to encoder: any Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        if let eventType { try c.encode(eventType, forKey: .eventType) }
        if let title { try c.encode(title, forKey: .title) }
        if let description { try c.encode(description, forKey: .description) }
        if let startAt { try c.encode(startAt, forKey: .startAt) }
        // endAt + locationNotes + recurrenceRule + assignedTo are all
        // explicit-nullable to support clearing values on edit.
        if let endAt { try c.encode(endAt, forKey: .endAt) }
        if let locationNotes { try c.encode(locationNotes, forKey: .locationNotes) }
        if let recurrenceRule { try c.encode(recurrenceRule, forKey: .recurrenceRule) }
        if let assignedTo { try c.encode(assignedTo, forKey: .assignedTo) }
        if let alertsEnabled { try c.encode(alertsEnabled, forKey: .alertsEnabled) }
    }
}

/// Envelope for `POST /api/homes/:id/events` and `PUT …/:eventId`.
public struct HomeEventResponse: Decodable, Sendable {
    public let event: CalendarEventDTO
}
