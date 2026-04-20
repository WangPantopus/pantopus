package app.pantopus.android.data.api.models.hub

import app.pantopus.android.data.api.models.common.JsonValue
import com.squareup.moshi.JsonClass

/** `GET /api/hub` — route `backend/routes/hub.js:24`. */
@JsonClass(generateAdapter = true)
data class HubResponse(
    val user: HubUser,
    val context: HubContext,
    val availability: HubAvailability,
    val homes: List<HubHomeSummary>,
    val businesses: List<HubBusinessSummary>,
    val setup: HubSetup,
    val statusItems: List<HubStatusItem>,
    val cards: HubCards,
    val jumpBackIn: List<HubJumpBackItem>,
    val activity: List<HubActivityItem>,
    val neighborDensity: HubNeighborDensity?,
)

@JsonClass(generateAdapter = true)
data class HubUser(
    val id: String,
    val name: String,
    val firstName: String?,
    val username: String,
    val avatarUrl: String?,
    val email: String,
)

@JsonClass(generateAdapter = true)
data class HubContext(
    val activeHomeId: String?,
    val activePersona: ActivePersona,
) {
    @JsonClass(generateAdapter = true)
    data class ActivePersona(val type: String)
}

@JsonClass(generateAdapter = true)
data class HubAvailability(
    val hasHome: Boolean,
    val hasBusiness: Boolean,
    val hasPayoutMethod: Boolean,
)

@JsonClass(generateAdapter = true)
data class HubHomeSummary(
    val id: String,
    val name: String,
    val addressShort: String,
    val city: String?,
    val state: String?,
    val latitude: Double?,
    val longitude: Double?,
    val isPrimary: Boolean,
    val roleBase: String,
)

@JsonClass(generateAdapter = true)
data class HubBusinessSummary(
    val id: String,
    val name: String,
    val username: String,
    val roleBase: String,
)

@JsonClass(generateAdapter = true)
data class HubSetup(
    val steps: List<Step>,
    val allDone: Boolean,
    val profileCompleteness: ProfileCompleteness,
) {
    @JsonClass(generateAdapter = true)
    data class Step(val key: String, val done: Boolean)

    @JsonClass(generateAdapter = true)
    data class ProfileCompleteness(
        val score: Double,
        val checks: Checks,
        val missingFields: List<String>,
    ) {
        @JsonClass(generateAdapter = true)
        data class Checks(
            val firstName: Boolean,
            val lastName: Boolean,
            val photo: Boolean,
            val bio: Boolean,
            val skills: Boolean,
        )
    }
}

@JsonClass(generateAdapter = true)
data class HubStatusItem(
    val id: String,
    val type: String,
    val pillar: String,
    val title: String,
    val subtitle: String?,
    val severity: String,
    val count: Int?,
    val dueAt: String?,
    val route: String,
    val entityRef: EntityRef?,
) {
    @JsonClass(generateAdapter = true)
    data class EntityRef(val kind: String, val id: String)
}

@JsonClass(generateAdapter = true)
data class HubCards(
    val personal: HubPersonalCard,
    val home: HubHomeCard?,
    val business: HubBusinessCard?,
)

@JsonClass(generateAdapter = true)
data class HubPersonalCard(
    val unreadChats: Int,
    val earnings: Double,
    val gigsNearby: Int,
    val rating: Double,
    val reviewCount: Int,
)

@JsonClass(generateAdapter = true)
data class HubHomeCard(
    val newMail: Int,
    val billsDue: List<HubBill>,
    val tasksDue: List<HubTask>,
    val memberCount: Int,
) {
    @JsonClass(generateAdapter = true)
    data class HubBill(
        val id: String,
        val name: String,
        val amount: Double,
        val dueAt: String,
    )

    @JsonClass(generateAdapter = true)
    data class HubTask(
        val id: String,
        val title: String,
        val dueAt: String,
    )
}

@JsonClass(generateAdapter = true)
data class HubBusinessCard(
    val newOrders: Int,
    val unreadThreads: Int,
    val pendingPayout: Double,
)

@JsonClass(generateAdapter = true)
data class HubJumpBackItem(
    val title: String,
    val route: String,
    val icon: String,
)

@JsonClass(generateAdapter = true)
data class HubActivityItem(
    val id: String,
    val pillar: String,
    val title: String,
    val at: String,
    val read: Boolean,
    val route: String,
)

@JsonClass(generateAdapter = true)
data class HubNeighborDensity(
    val count: Int,
    val radiusMiles: Double,
    val milestone: String?,
)

/**
 * `GET /api/hub/today` — provider-orchestrated, shape varies. Exposed as
 * untyped JSON until the provider bundle stabilises. Route:
 * `backend/routes/hub.js:596`.
 */
@JsonClass(generateAdapter = true)
data class HubTodayResponse(
    val today: JsonValue?,
    val error: String?,
)

/** `GET /api/hub/discovery` — route `backend/routes/hub.js:720`. */
@JsonClass(generateAdapter = true)
data class HubDiscoveryResponse(
    val items: List<DiscoveryItem>,
)

@JsonClass(generateAdapter = true)
data class DiscoveryItem(
    val id: String,
    val type: String,
    val title: String,
    val meta: String,
    val category: String,
    val avatarUrl: String?,
    val route: String,
)
