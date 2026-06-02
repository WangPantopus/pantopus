//
//  YourAudienceSampleData.swift
//  Pantopus
//
//  Deterministic sample data for A22.2 "Your audience" previews. Mirrors
//  the four design frames (populated · pending · full-empty · no-pending).
//

import Foundation

enum YourAudienceSampleData {
    static func member(
        id: String,
        name: String,
        handle: String,
        rank: Int,
        tierName: String,
        local: Bool,
        status: String = "active",
        joinedMonth: String? = "2025-01"
    ) -> AudienceMember {
        AudienceMember(
            membershipId: id,
            displayName: name,
            handle: handle,
            avatarURL: nil,
            tierRank: rank,
            tierName: tierName,
            verifiedLocal: local,
            status: status,
            joinedMonth: joinedMonth,
            tenureMonths: 4
        )
    }

    static let pending: [AudienceMember] = [
        member(
            id: "m_dana", name: "Dana Reyes", handle: "@danareyes", rank: 3,
            tierName: "Insiders", local: true, status: "pending", joinedMonth: "2025-05"
        ),
        member(
            id: "m_marcus", name: "Marcus Lee", handle: "@marcuslee", rank: 4,
            tierName: "VIP", local: false, status: "pending", joinedMonth: "2025-05"
        ),
    ]

    static let vipMembers: [AudienceMember] = [
        member(id: "m_priya", name: "Priya Nair", handle: "@priyanair", rank: 4, tierName: "VIP", local: true, joinedMonth: "2025-01"),
        member(
            id: "m_tom", name: "Tom Becker", handle: "@tombecker", rank: 4,
            tierName: "VIP", local: false, status: "muted", joinedMonth: "2024-11"
        ),
    ]

    static let insiderMembers: [AudienceMember] = [
        member(id: "m_sana", name: "Sana Ortiz", handle: "@sanaortiz", rank: 3, tierName: "Insiders", local: true, joinedMonth: "2025-03"),
        member(id: "m_otis", name: "Otis Park", handle: "@otispark", rank: 3, tierName: "Insiders", local: false, joinedMonth: "2025-04"),
        member(id: "m_lena", name: "Lena Cho", handle: "@lenacho", rank: 3, tierName: "Insiders", local: true, joinedMonth: "2025-05"),
    ]

    static var counts: AudienceCounts {
        AudienceCounts(totalActive: 5, pending: 2, byTier: [4: 2, 3: 3])
    }

    static var populatedLoaded: AudienceLoaded {
        AudienceLoaded(
            counts: counts,
            pending: pending,
            tierGroups: [
                AudienceTierGroup(rank: 4, name: "VIP", members: vipMembers),
                AudienceTierGroup(rank: 3, name: "Insiders", members: insiderMembers),
            ]
        )
    }

    static let tierNames: [Int: String] = [4: "VIP", 3: "Insiders"]

    #if DEBUG
    static func populatedViewModel() -> YourAudienceViewModel {
        YourAudienceViewModel.preview(.loaded(populatedLoaded), counts: counts, tierNames: tierNames)
    }

    static func emptyViewModel() -> YourAudienceViewModel {
        YourAudienceViewModel.preview(.empty, counts: .zero)
    }
    #endif
}
