//
//  MailDetailContent.swift
//  Pantopus
//

import Foundation

/// Lifecycle state for the generic mail detail screen.
@MainActor
public enum MailDetailState {
    case loading
    case loaded(MailDetailContent)
    case error(message: String)
}

/// Pure projection of the backend mail item into the A17 shell slots.
public struct MailDetailContent: Sendable {
    public let mailId: String
    public let category: MailItemCategory
    public let trust: MailTrust
    public let detailTrust: MailDetailTrust
    public let senderDisplayName: String
    public let senderMeta: String?
    public let senderTypeLabel: String
    public let carrierLine: String
    public let senderInitials: String
    public let senderUserId: String?
    public let title: String
    public let excerpt: String?
    public let referenceLabel: String
    public let createdAtLabel: String?
    public let expiresAtLabel: String?
    public let readStatusLabel: String
    public let bodyParagraphs: [String]
    public let attachments: [String]
    public let aiSummary: String?
    public let ackRequired: Bool
    public let isAcknowledged: Bool
    public let isArchived: Bool
    public let bookletDetail: BookletDetailDTO?
    public let certifiedDetail: CertifiedDetailDTO?
    public let communityDetail: CommunityDetailDTO?
    public let couponDetail: CouponDetailDTO?
    public let gigDetail: GigDetailDTO?
    public let memoryDetail: MemoryDetailDTO?
    public let packageDetail: PackageBodyContent?

    public init(
        mailId: String,
        category: MailItemCategory,
        trust: MailTrust,
        detailTrust: MailDetailTrust,
        senderDisplayName: String,
        senderMeta: String?,
        senderTypeLabel: String,
        carrierLine: String,
        senderInitials: String,
        senderUserId: String?,
        title: String,
        excerpt: String?,
        referenceLabel: String,
        createdAtLabel: String?,
        expiresAtLabel: String?,
        readStatusLabel: String,
        bodyParagraphs: [String],
        attachments: [String],
        aiSummary: String?,
        ackRequired: Bool,
        isAcknowledged: Bool,
        isArchived: Bool = false,
        bookletDetail: BookletDetailDTO? = nil,
        certifiedDetail: CertifiedDetailDTO? = nil,
        communityDetail: CommunityDetailDTO? = nil,
        couponDetail: CouponDetailDTO? = nil,
        gigDetail: GigDetailDTO? = nil,
        memoryDetail: MemoryDetailDTO? = nil,
        packageDetail: PackageBodyContent? = nil
    ) {
        self.mailId = mailId
        self.category = category
        self.trust = trust
        self.detailTrust = detailTrust
        self.senderDisplayName = senderDisplayName
        self.senderMeta = senderMeta
        self.senderTypeLabel = senderTypeLabel
        self.carrierLine = carrierLine
        self.senderInitials = senderInitials
        self.senderUserId = senderUserId
        self.title = title
        self.excerpt = excerpt
        self.referenceLabel = referenceLabel
        self.createdAtLabel = createdAtLabel
        self.expiresAtLabel = expiresAtLabel
        self.readStatusLabel = readStatusLabel
        self.bodyParagraphs = bodyParagraphs
        self.attachments = attachments
        self.aiSummary = aiSummary
        self.ackRequired = ackRequired
        self.isAcknowledged = isAcknowledged
        self.isArchived = isArchived
        self.bookletDetail = bookletDetail
        self.certifiedDetail = certifiedDetail
        self.communityDetail = communityDetail
        self.couponDetail = couponDetail
        self.gigDetail = gigDetail
        self.memoryDetail = memoryDetail
        self.packageDetail = packageDetail
    }

    /// Build a `KeyFactRow` list from the projected fields.
    public func keyFacts() -> [MailDetailKeyFact] {
        var rows: [MailDetailKeyFact] = []
        if let createdAtLabel {
            rows.append(MailDetailKeyFact(icon: .calendar, label: "Received", value: createdAtLabel))
        }
        if let expiresAtLabel {
            rows.append(MailDetailKeyFact(icon: .clock, label: "Expires", value: expiresAtLabel))
        }
        if let senderMeta {
            rows.append(MailDetailKeyFact(icon: .briefcase, label: "From", value: senderMeta))
        }
        rows.append(
            MailDetailKeyFact(icon: category.icon, label: "Category", value: category.label)
        )
        return rows
    }
}

/// Lightweight key/value/icon triple for the generic detail's key facts panel.
public struct MailDetailKeyFact: Identifiable, Sendable, Hashable {
    public let id: String
    public let icon: PantopusIcon
    public let label: String
    public let value: String

    public init(id: String = UUID().uuidString, icon: PantopusIcon, label: String, value: String) {
        self.id = id
        self.icon = icon
        self.label = label
        self.value = value
    }
}
