@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.audience_profile

import androidx.compose.runtime.Immutable

/** Three tabs on the management surface (mirrors iOS). */
enum class AudienceProfileTab(val key: String, val title: String) {
    Updates("updates", "Updates"),
    Followers("followers", "Followers"),
    Threads("threads", "Threads"),
}

/** Tier visibility for the Updates composer. Matches the backend enum. */
enum class UpdateVisibility(val wire: String, val title: String) {
    Public("public", "Public"),
    Followers("followers", "Followers"),
    TierOrAbove("tier_or_above", "Tier and above"),
    ;

    companion object {
        fun fromWire(value: String?): UpdateVisibility =
            values().firstOrNull { it.wire == value } ?: Followers
    }
}

@Immutable
data class UpdateComposerState(
    val text: String = "",
    val visibility: UpdateVisibility = UpdateVisibility.Followers,
    val targetTierRank: Int? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    /** Composer can submit if non-empty body and tier rank is valid for tierOrAbove. */
    val canSubmit: Boolean
        get() {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return false
            if (visibility == UpdateVisibility.TierOrAbove && targetTierRank == null) return false
            return !isSubmitting
        }
}

@Immutable
data class AudienceHeaderContent(
    val displayName: String,
    val handle: String?,
    val followerCount: Int,
    val newThisWeek: Int,
    val postCount: Int,
)

@Immutable
data class UpdateCardContent(
    val id: String,
    val body: String,
    val timeAgo: String,
    val visibility: UpdateVisibility,
    val targetTierRank: Int?,
    val deliveredCount: Int,
    val readCount: Int,
) {
    val visibilityLabel: String
        get() =
            when (visibility) {
                UpdateVisibility.Public -> "Public"
                UpdateVisibility.Followers -> "Followers"
                UpdateVisibility.TierOrAbove -> targetTierRank?.let { "Tier $it+" } ?: "Tier"
            }
}

@Immutable
data class AnalyticsCellContent(
    val id: String,
    val label: String,
    val value: String,
    val trend: String? = null,
)

@Immutable
data class TierBreakdownContent(
    val total: Int,
    val segments: List<TierSegment>,
) {
    @Immutable
    data class TierSegment(
        val id: String,
        val rank: Int,
        val name: String,
        val count: Int,
    )
}

@Immutable
data class FollowerRowContent(
    val id: String,
    val displayName: String,
    val handle: String,
    val avatarUrl: String?,
    val tierName: String,
    val tierRank: Int,
    val tenureLabel: String?,
    val verifiedLocal: Boolean,
)

@Immutable
data class TierChipContent(
    val id: String,
    val rank: Int?,
    val label: String,
    val count: Int,
)

@Immutable
data class ThreadRowContent(
    val id: String,
    val displayName: String,
    val handle: String,
    val avatarUrl: String?,
    val tierName: String?,
    val preview: String,
    val timeAgo: String,
    val unreadCount: Int,
)

@Immutable
data class AudienceProfileLoaded(
    val header: AudienceHeaderContent,
    val updates: List<UpdateCardContent>,
    val analyticsCells: List<AnalyticsCellContent>,
    val tierBreakdown: TierBreakdownContent,
    val tierChips: List<TierChipContent>,
    val followers: List<FollowerRowContent>,
    val threads: List<ThreadRowContent>,
    val channelId: String?,
)

sealed interface AudienceProfileUiState {
    data object Loading : AudienceProfileUiState

    data class Empty(val message: String) : AudienceProfileUiState

    data class Loaded(val content: AudienceProfileLoaded) : AudienceProfileUiState

    data class Error(val message: String) : AudienceProfileUiState
}
