//
//  MailboxDTOs.swift
//  Pantopus
//
//  DTOs for the V1 mailbox endpoints in `backend/routes/mailbox.js`.
//

import Foundation

/// Core mail row — shared shape between list, detail, and V2 envelopes.
/// Route citations: `backend/routes/mailbox.js:1306` (list),
/// `backend/routes/mailbox.js:1466` (detail).
public struct MailItem: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let recipientUserId: String?
    public let recipientHomeId: String?
    public let deliveryTargetType: String?
    public let deliveryTargetId: String?
    public let addressHomeId: String?
    public let attnUserId: String?
    public let attnLabel: String?
    public let deliveryVisibility: String?
    public let mailType: String?
    public let displayTitle: String?
    public let previewText: String?
    public let primaryAction: String?
    public let actionRequired: Bool?
    public let ackRequired: Bool?
    public let ackStatus: String?
    public let type: String
    public let subject: String?
    public let content: String?
    public let senderUserId: String?
    public let senderBusinessName: String?
    public let senderAddress: String?
    public let viewed: Bool
    public let viewedAt: String?
    public let archived: Bool
    public let starred: Bool
    public let payoutAmount: Double?
    public let payoutStatus: String?
    public let category: String?
    public let tags: [String]
    public let priority: String
    public let attachments: [String]?
    public let expiresAt: String?
    public let createdAt: String

    private enum CodingKeys: String, CodingKey {
        case id
        case recipientUserId = "recipient_user_id"
        case recipientHomeId = "recipient_home_id"
        case deliveryTargetType = "delivery_target_type"
        case deliveryTargetId = "delivery_target_id"
        case addressHomeId = "address_home_id"
        case attnUserId = "attn_user_id"
        case attnLabel = "attn_label"
        case deliveryVisibility = "delivery_visibility"
        case mailType = "mail_type"
        case displayTitle = "display_title"
        case previewText = "preview_text"
        case primaryAction = "primary_action"
        case actionRequired = "action_required"
        case ackRequired = "ack_required"
        case ackStatus = "ack_status"
        case type, subject, content
        case senderUserId = "sender_user_id"
        case senderBusinessName = "sender_business_name"
        case senderAddress = "sender_address"
        case viewed
        case viewedAt = "viewed_at"
        case archived, starred
        case payoutAmount = "payout_amount"
        case payoutStatus = "payout_status"
        case category, tags, priority, attachments
        case expiresAt = "expires_at"
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.recipientUserId = try c.decodeIfPresent(String.self, forKey: .recipientUserId)
        self.recipientHomeId = try c.decodeIfPresent(String.self, forKey: .recipientHomeId)
        self.deliveryTargetType = try c.decodeIfPresent(String.self, forKey: .deliveryTargetType)
        self.deliveryTargetId = try c.decodeIfPresent(String.self, forKey: .deliveryTargetId)
        self.addressHomeId = try c.decodeIfPresent(String.self, forKey: .addressHomeId)
        self.attnUserId = try c.decodeIfPresent(String.self, forKey: .attnUserId)
        self.attnLabel = try c.decodeIfPresent(String.self, forKey: .attnLabel)
        self.deliveryVisibility = try c.decodeIfPresent(String.self, forKey: .deliveryVisibility)
        self.mailType = try c.decodeIfPresent(String.self, forKey: .mailType)
        self.displayTitle = try c.decodeIfPresent(String.self, forKey: .displayTitle)
        self.previewText = try c.decodeIfPresent(String.self, forKey: .previewText)
        self.primaryAction = try c.decodeIfPresent(String.self, forKey: .primaryAction)
        self.actionRequired = try c.decodeIfPresent(Bool.self, forKey: .actionRequired)
        self.ackRequired = try c.decodeIfPresent(Bool.self, forKey: .ackRequired)
        self.ackStatus = try c.decodeIfPresent(String.self, forKey: .ackStatus)
        self.type = try c.decode(String.self, forKey: .type)
        self.subject = try c.decodeIfPresent(String.self, forKey: .subject)
        self.content = try c.decodeIfPresent(String.self, forKey: .content)
        self.senderUserId = try c.decodeIfPresent(String.self, forKey: .senderUserId)
        self.senderBusinessName = try c.decodeIfPresent(String.self, forKey: .senderBusinessName)
        self.senderAddress = try c.decodeIfPresent(String.self, forKey: .senderAddress)
        self.viewed = try c.decodeIfPresent(Bool.self, forKey: .viewed) ?? false
        self.viewedAt = try c.decodeIfPresent(String.self, forKey: .viewedAt)
        self.archived = try c.decodeIfPresent(Bool.self, forKey: .archived) ?? false
        self.starred = try c.decodeIfPresent(Bool.self, forKey: .starred) ?? false
        self.payoutAmount = try c.decodeIfPresent(Double.self, forKey: .payoutAmount)
        self.payoutStatus = try c.decodeIfPresent(String.self, forKey: .payoutStatus)
        self.category = try c.decodeIfPresent(String.self, forKey: .category)
        self.tags = try c.decodeIfPresent([String].self, forKey: .tags) ?? []
        self.priority = try c.decodeIfPresent(String.self, forKey: .priority) ?? "normal"
        self.attachments = try c.decodeIfPresent([String].self, forKey: .attachments)
        self.expiresAt = try c.decodeIfPresent(String.self, forKey: .expiresAt)
        self.createdAt = try c.decode(String.self, forKey: .createdAt)
    }
}

/// `GET /api/mailbox` envelope — route `backend/routes/mailbox.js:1306`.
public struct MailboxListResponse: Decodable, Sendable, Hashable {
    public let mail: [MailItem]
    public let count: Int
}

/// `GET /api/mailbox/:id` envelope — route `backend/routes/mailbox.js:1466`.
public struct MailDetailResponse: Decodable, Sendable, Hashable {
    public let mail: MailDetail

    public struct MailDetail: Decodable, Sendable, Hashable, Identifiable {
        public let item: MailItem
        public let sender: Sender?
        public let object: JSONValue?
        public let contentFormat: String?
        public let links: [JSONValue]

        public var id: String { item.id }

        public init(from decoder: Decoder) throws {
            self.item = try MailItem(from: decoder)
            let c = try decoder.container(keyedBy: Keys.self)
            self.sender = try c.decodeIfPresent(Sender.self, forKey: .sender)
            self.object = try c.decodeIfPresent(JSONValue.self, forKey: .object)
            self.contentFormat = try c.decodeIfPresent(String.self, forKey: .contentFormat)
            self.links = try c.decodeIfPresent([JSONValue].self, forKey: .links) ?? []
        }

        public struct Sender: Decodable, Sendable, Hashable, Identifiable {
            public let id: String
            public let username: String
            public let name: String
        }

        private enum Keys: String, CodingKey {
            case sender, object
            case contentFormat = "content_format"
            case links
        }
    }
}

/// `PATCH /api/mailbox/:id/ack` response — route `backend/routes/mailbox.js:2702`.
public struct AckResponse: Decodable, Sendable, Hashable {
    public let message: String
    public let ackStatus: String
}
