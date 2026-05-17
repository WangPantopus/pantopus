//
//  CommunityDetailDTO.swift
//  Pantopus
//
//  T6.5d (P22) — Community (A17.4) sub-payload decoded from
//  `mail.object_payload` when `mail_type == "community"`. Backend
//  stores this as untyped JSON shaped by the `CommunityMailItem`
//  table (schema at `backend/database/schema.sql:5350`). The
//  RSVP-going state flows back from the existing
//  `POST /api/mailbox/v2/community/rsvp` route
//  (`backend/routes/mailboxV2Phase3.js:746`) — the decoder is
//  defensive and `decode(from:)` returns nil when the payload
//  doesn't carry a community item id.
//

import Foundation

/// HOA/neighborhood-group seal rendered in the badge card.
public struct CommunityGroupInfo: Sendable, Hashable {
    public let name: String
    public let tagline: String?
    public let founded: String?
    public let role: String?
    public let membershipSince: String?
    public let memberCount: Int?
    public let isVerified: Bool

    public init(
        name: String,
        tagline: String?,
        founded: String?,
        role: String?,
        membershipSince: String?,
        memberCount: Int?,
        isVerified: Bool
    ) {
        self.name = name
        self.tagline = tagline
        self.founded = founded
        self.role = role
        self.membershipSince = membershipSince
        self.memberCount = memberCount
        self.isVerified = isVerified
    }
}

/// When/where/bring chunk per the A17.4 design's Event details card.
public struct CommunityEventInfo: Sendable, Hashable {
    public let dayLabel: String?
    public let dateLabel: String?
    public let timeRange: String?
    public let location: String?
    public let locationNote: String?
    public let distanceLabel: String?
    public let bringItems: [String]
    public let weatherSummary: String?
    public let weatherTemperatureF: Int?

    public init(
        dayLabel: String?,
        dateLabel: String?,
        timeRange: String?,
        location: String?,
        locationNote: String?,
        distanceLabel: String?,
        bringItems: [String],
        weatherSummary: String?,
        weatherTemperatureF: Int?
    ) {
        self.dayLabel = dayLabel
        self.dateLabel = dateLabel
        self.timeRange = timeRange
        self.location = location
        self.locationNote = locationNote
        self.distanceLabel = distanceLabel
        self.bringItems = bringItems
        self.weatherSummary = weatherSummary
        self.weatherTemperatureF = weatherTemperatureF
    }
}

/// One attendee rendered in the strip. Backend wire shape is loose;
/// `displayName` is always present, the rest are best-effort.
public struct CommunityAttendee: Sendable, Hashable, Identifiable {
    public let id: String
    public let displayName: String
    public let initials: String
    public let blockLabel: String?
    public let isVerified: Bool

    public init(id: String, displayName: String, initials: String, blockLabel: String?, isVerified: Bool) {
        self.id = id
        self.displayName = displayName
        self.initials = initials
        self.blockLabel = blockLabel
        self.isVerified = isVerified
    }
}

/// Cross-link card pointing at the related Pulse thread.
public struct CommunityPulseThread: Sendable, Hashable {
    public let threadId: String
    public let title: String
    public let replyCount: Int
    public let lastReplyAuthor: String?
    public let lastReplyPreview: String?
    public let lastReplyAge: String?

    public init(
        threadId: String,
        title: String,
        replyCount: Int,
        lastReplyAuthor: String?,
        lastReplyPreview: String?,
        lastReplyAge: String?
    ) {
        self.threadId = threadId
        self.title = title
        self.replyCount = replyCount
        self.lastReplyAuthor = lastReplyAuthor
        self.lastReplyPreview = lastReplyPreview
        self.lastReplyAge = lastReplyAge
    }
}

/// Tri-state RSVP — mirrors the design's Going / Maybe / Can't make it
/// chip row. Wire string is lowercase per the backend convention.
public enum CommunityRsvpStatus: String, Sendable, Hashable {
    case going
    case maybe
    case notGoing = "not_going"
    case undecided

    public init(wire: String?) {
        switch wire?.lowercased() {
        case "going", "will_attend": self = .going
        case "maybe": self = .maybe
        case "not_going", "declined": self = .notGoing
        default: self = .undecided
        }
    }
}

/// Community detail payload — drives the A17.4 variant body.
public struct CommunityDetailDTO: Sendable, Hashable {
    /// PK of the `CommunityMailItem` row. Required so the RSVP mutation
    /// can find the right item.
    public let communityItemId: String
    public let group: CommunityGroupInfo
    public let event: CommunityEventInfo?
    public let attendees: [CommunityAttendee]
    public let attendeeCount: Int
    public let attendeesFromBlock: Int?
    public let pulseThread: CommunityPulseThread?
    public let rsvp: CommunityRsvpStatus

    public init(
        communityItemId: String,
        group: CommunityGroupInfo,
        event: CommunityEventInfo?,
        attendees: [CommunityAttendee],
        attendeeCount: Int,
        attendeesFromBlock: Int?,
        pulseThread: CommunityPulseThread?,
        rsvp: CommunityRsvpStatus
    ) {
        self.communityItemId = communityItemId
        self.group = group
        self.event = event
        self.attendees = attendees
        self.attendeeCount = attendeeCount
        self.attendeesFromBlock = attendeesFromBlock
        self.pulseThread = pulseThread
        self.rsvp = rsvp
    }

    public static func decode(from value: JSONValue?) -> CommunityDetailDTO? {
        guard let dict = value?.dictValue else { return nil }
        guard let itemId = dict["community_item_id"]?.stringValue
            ?? dict["id"]?.stringValue, !itemId.isEmpty else {
            return nil
        }
        let groupDict = dict["group"]?.dictValue ?? dict["community"]?.dictValue ?? [:]
        let group = CommunityGroupInfo(
            name: groupDict["name"]?.stringValue ?? "Neighborhood group",
            tagline: groupDict["tagline"]?.stringValue,
            founded: groupDict["founded"]?.stringValue,
            role: groupDict["role"]?.stringValue ?? groupDict["membership_role"]?.stringValue,
            membershipSince: groupDict["membership_since"]?.stringValue
                ?? groupDict["since"]?.stringValue,
            memberCount: groupDict["member_count"]?.numberValue.map { Int($0) },
            isVerified: groupDict["verified"]?.boolValue ?? false
        )
        let eventDict = dict["event"]?.dictValue
        let event = eventDict.map { evt -> CommunityEventInfo in
            let weatherDict = evt["weather"]?.dictValue
            let bring = (evt["bring"]?.arrayValue ?? []).compactMap(\.stringValue)
            return CommunityEventInfo(
                dayLabel: evt["day_label"]?.stringValue
                    ?? evt["when"]?.dictValue?["day"]?.stringValue,
                dateLabel: evt["date_label"]?.stringValue
                    ?? evt["when"]?.dictValue?["date"]?.stringValue,
                timeRange: evt["time_range"]?.stringValue
                    ?? evt["when"]?.dictValue?["range"]?.stringValue,
                location: evt["location"]?.stringValue ?? evt["where"]?.stringValue,
                locationNote: evt["location_note"]?.stringValue
                    ?? evt["where_note"]?.stringValue,
                distanceLabel: evt["distance_label"]?.stringValue,
                bringItems: bring,
                weatherSummary: weatherDict?["summary"]?.stringValue,
                weatherTemperatureF: weatherDict?["temperature_f"]?.numberValue.map { Int($0) }
                    ?? weatherDict?["temp"]?.numberValue.map { Int($0) }
            )
        }
        let attendees: [CommunityAttendee] = (dict["attendees"]?.arrayValue ?? [])
            .compactMap { attn -> CommunityAttendee? in
                guard let a = attn.dictValue,
                      let name = a["display_name"]?.stringValue ?? a["name"]?.stringValue,
                      !name.isEmpty else { return nil }
                let id = a["id"]?.stringValue ?? UUID().uuidString
                let initials = a["initials"]?.stringValue
                    ?? Self.makeInitials(from: name)
                return CommunityAttendee(
                    id: id,
                    displayName: name,
                    initials: initials,
                    blockLabel: a["block_label"]?.stringValue ?? a["block"]?.stringValue,
                    isVerified: a["verified"]?.boolValue ?? true
                )
            }
        let attendeeCount = dict["attendee_count"]?.numberValue.map { Int($0) }
            ?? dict["rsvp_count"]?.numberValue.map { Int($0) }
            ?? attendees.count
        let attendeesFromBlock = dict["attendees_from_block"]?.numberValue.map { Int($0) }
        let threadDict = dict["pulse_thread"]?.dictValue
        let pulseThread = threadDict.flatMap { td -> CommunityPulseThread? in
            guard let threadId = td["thread_id"]?.stringValue ?? td["id"]?.stringValue,
                  let title = td["title"]?.stringValue else { return nil }
            let lastReply = td["last_reply"]?.dictValue
            return CommunityPulseThread(
                threadId: threadId,
                title: title,
                replyCount: td["reply_count"]?.numberValue.map { Int($0) }
                    ?? td["count"]?.numberValue.map { Int($0) }
                    ?? 0,
                lastReplyAuthor: lastReply?["author"]?.stringValue
                    ?? lastReply?["who"]?.stringValue,
                lastReplyPreview: lastReply?["preview"]?.stringValue,
                lastReplyAge: lastReply?["age"]?.stringValue
                    ?? lastReply?["when"]?.stringValue
            )
        }
        return CommunityDetailDTO(
            communityItemId: itemId,
            group: group,
            event: event,
            attendees: attendees,
            attendeeCount: attendeeCount,
            attendeesFromBlock: attendeesFromBlock,
            pulseThread: pulseThread,
            rsvp: CommunityRsvpStatus(wire: dict["rsvp_status"]?.stringValue)
        )
    }

    private static func makeInitials(from name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        let result = parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
        return result.isEmpty ? "·" : result
    }
}
