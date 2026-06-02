@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.creator_audience

/**
 * Deterministic sample data for A22.2 "Your audience" previews / snapshot
 * tests. Mirrors the design frames (populated · pending · empty · no-pending).
 */
object YourAudienceSampleData {
    private fun member(
        id: String,
        name: String,
        handle: String,
        rank: Int,
        tierName: String,
        local: Boolean,
        status: String = "active",
        joinedMonth: String? = "2025-01",
    ) = AudienceMember(
        membershipId = id,
        displayName = name,
        handle = handle,
        avatarUrl = null,
        tierRank = rank,
        tierName = tierName,
        verifiedLocal = local,
        status = status,
        joinedMonth = joinedMonth,
        tenureMonths = 4,
    )

    val pending =
        listOf(
            member("m_dana", "Dana Reyes", "@danareyes", 3, "Insiders", local = true, status = "pending", joinedMonth = "2025-05"),
            member("m_marcus", "Marcus Lee", "@marcuslee", 4, "VIP", local = false, status = "pending", joinedMonth = "2025-05"),
        )

    val vipMembers =
        listOf(
            member("m_priya", "Priya Nair", "@priyanair", 4, "VIP", local = true, joinedMonth = "2025-01"),
            member("m_tom", "Tom Becker", "@tombecker", 4, "VIP", local = false, status = "muted", joinedMonth = "2024-11"),
        )

    val insiderMembers =
        listOf(
            member("m_sana", "Sana Ortiz", "@sanaortiz", 3, "Insiders", local = true, joinedMonth = "2025-03"),
            member("m_otis", "Otis Park", "@otispark", 3, "Insiders", local = false, joinedMonth = "2025-04"),
            member("m_lena", "Lena Cho", "@lenacho", 3, "Insiders", local = true, joinedMonth = "2025-05"),
        )

    val counts = AudienceCounts(totalActive = 5, pending = 2, byTier = mapOf(4 to 2, 3 to 3))

    val tierNames = mapOf(4 to "VIP", 3 to "Insiders")

    val populated =
        AudienceLoaded(
            counts = counts,
            pending = pending,
            tierGroups =
                listOf(
                    AudienceTierGroup(rank = 4, name = "VIP", members = vipMembers),
                    AudienceTierGroup(rank = 3, name = "Insiders", members = insiderMembers),
                ),
        )
}
