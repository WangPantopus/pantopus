//
//  MailboxCategoryPayload.swift
//  Pantopus
//
//  Discriminated union of category-specific sub-payloads decoded from
//  `mail.object_payload`. The view-model resolves this once per item
//  load; bodies switch on it to render the right surface.
//

import Foundation

/// Category-tagged payload for a mailbox item.
public enum MailboxCategoryPayload: Sendable, Hashable {
    case coupon(CouponDetailDTO)
    case booklet(BookletDetailDTO)
    case certified(CertifiedDetailDTO)
    case gig(GigDetailDTO)
    /// No category-specific decoder applies (Package, Bill, Notice, …).
    /// Bodies fall back to the generic placeholder layout.
    case other

    /// Resolve a payload from a category + raw object_payload JSON. Falls
    /// back to `.other` when the category isn't one of the three P18
    /// shapes or when decoding fails.
    public static func resolve(
        category: MailItemCategory,
        objectPayload: JSONValue?
    ) -> MailboxCategoryPayload {
        switch category {
        case .coupon:
            if let dto = CouponDetailDTO.decode(from: objectPayload) {
                return .coupon(dto)
            }
        case .booklet:
            if let dto = BookletDetailDTO.decode(from: objectPayload) {
                return .booklet(dto)
            }
        case .certified:
            if let dto = CertifiedDetailDTO.decode(from: objectPayload) {
                return .certified(dto)
            }
        case .gig:
            if let dto = GigDetailDTO.decode(from: objectPayload) {
                return .gig(dto)
            }
        default:
            break
        }
        return .other
    }
}
