@file:Suppress("PackageNaming", "LongParameterList", "MagicNumber")

package app.pantopus.android.data.api.models.mailbox.v2

import app.pantopus.android.data.api.models.common.JsonValue

/** HOA/neighborhood-group seal rendered in the badge card. */
data class CommunityGroupInfo(
    val name: String,
    val tagline: String?,
    val founded: String?,
    val role: String?,
    val membershipSince: String?,
    val memberCount: Int?,
    val isVerified: Boolean,
)

/** When/where/bring chunk per the A17.4 design's Event details card. */
data class CommunityEventInfo(
    val dayLabel: String?,
    val dateLabel: String?,
    val timeRange: String?,
    val location: String?,
    val locationNote: String?,
    val distanceLabel: String?,
    val bringItems: List<String>,
    val weatherSummary: String?,
    val weatherTemperatureF: Int?,
)

/** One attendee rendered in the strip. */
data class CommunityAttendee(
    val id: String,
    val displayName: String,
    val initials: String,
    val blockLabel: String?,
    val isVerified: Boolean,
)

/** Cross-link card pointing at the related Pulse thread. */
data class CommunityPulseThread(
    val threadId: String,
    val title: String,
    val replyCount: Int,
    val lastReplyAuthor: String?,
    val lastReplyPreview: String?,
    val lastReplyAge: String?,
)

/** Tri-state RSVP — mirrors the design's chip row. */
enum class CommunityRsvpStatus(val wire: String) {
    Going("going"),
    Maybe("maybe"),
    NotGoing("not_going"),
    Undecided("undecided"),
    ;

    companion object {
        fun fromWire(value: String?): CommunityRsvpStatus =
            when (value?.lowercase()) {
                "going", "will_attend" -> Going
                "maybe" -> Maybe
                "not_going", "declined" -> NotGoing
                else -> Undecided
            }
    }
}

/**
 * Community detail sub-payload decoded from `mail.object_payload` when
 * `mail_type == "community"`. Backend stores this as untyped JSON shaped
 * by the `CommunityMailItem` table (`backend/database/schema.sql:5350`).
 * The RSVP-going state flows back from
 * `POST /api/mailbox/v2/community/rsvp`
 * (`backend/routes/mailboxV2Phase3.js:746`).
 * [decodeFromObjectPayload] returns null when the payload doesn't carry
 * a community item id.
 */
data class CommunityDetailDto(
    val communityItemId: String,
    val group: CommunityGroupInfo,
    val event: CommunityEventInfo?,
    val attendees: List<CommunityAttendee>,
    val attendeeCount: Int,
    val attendeesFromBlock: Int?,
    val pulseThread: CommunityPulseThread?,
    val rsvp: CommunityRsvpStatus,
) {
    companion object {
        @Suppress("UNCHECKED_CAST", "LongMethod", "CyclomaticComplexMethod")
        fun decodeFromObjectPayload(payload: JsonValue?): CommunityDetailDto? {
            if (payload == null) return null
            val itemId =
                (payload["community_item_id"] as? String)
                    ?: (payload["id"] as? String) ?: return null
            if (itemId.isEmpty()) return null
            val groupRaw =
                (payload["group"] as? Map<String, Any?>)
                    ?: (payload["community"] as? Map<String, Any?>)
                    ?: emptyMap<String, Any?>()
            val group =
                CommunityGroupInfo(
                    name = (groupRaw["name"] as? String) ?: "Neighborhood group",
                    tagline = groupRaw["tagline"] as? String,
                    founded = groupRaw["founded"] as? String,
                    role =
                        (groupRaw["role"] as? String)
                            ?: (groupRaw["membership_role"] as? String),
                    membershipSince =
                        (groupRaw["membership_since"] as? String)
                            ?: (groupRaw["since"] as? String),
                    memberCount = (groupRaw["member_count"] as? Number)?.toInt(),
                    isVerified = (groupRaw["verified"] as? Boolean) ?: false,
                )
            val eventRaw = payload["event"] as? Map<String, Any?>
            val event =
                eventRaw?.let { evt ->
                    val weather = evt["weather"] as? Map<String, Any?>
                    val whenMap = evt["when"] as? Map<String, Any?>
                    val whereStr =
                        (evt["location"] as? String)
                            ?: (evt["where"] as? String)
                    val bring =
                        (evt["bring"] as? List<*>)
                            ?.mapNotNull { it as? String } ?: emptyList()
                    CommunityEventInfo(
                        dayLabel =
                            (evt["day_label"] as? String)
                                ?: (whenMap?.get("day") as? String),
                        dateLabel =
                            (evt["date_label"] as? String)
                                ?: (whenMap?.get("date") as? String),
                        timeRange =
                            (evt["time_range"] as? String)
                                ?: (whenMap?.get("range") as? String),
                        location = whereStr,
                        locationNote =
                            (evt["location_note"] as? String)
                                ?: (evt["where_note"] as? String),
                        distanceLabel = evt["distance_label"] as? String,
                        bringItems = bring,
                        weatherSummary = weather?.get("summary") as? String,
                        weatherTemperatureF =
                            (weather?.get("temperature_f") as? Number)?.toInt()
                                ?: (weather?.get("temp") as? Number)?.toInt(),
                    )
                }
            val attendeesRaw = payload["attendees"] as? List<*> ?: emptyList<Any?>()
            val attendees =
                attendeesRaw.mapNotNull { entry ->
                    val map = entry as? Map<String, Any?> ?: return@mapNotNull null
                    val name =
                        (map["display_name"] as? String)
                            ?: (map["name"] as? String) ?: return@mapNotNull null
                    if (name.isEmpty()) return@mapNotNull null
                    val initials = (map["initials"] as? String) ?: makeInitials(name)
                    CommunityAttendee(
                        id = (map["id"] as? String) ?: java.util.UUID.randomUUID().toString(),
                        displayName = name,
                        initials = initials,
                        blockLabel =
                            (map["block_label"] as? String)
                                ?: (map["block"] as? String),
                        isVerified = (map["verified"] as? Boolean) ?: true,
                    )
                }
            val attendeeCount =
                (payload["attendee_count"] as? Number)?.toInt()
                    ?: (payload["rsvp_count"] as? Number)?.toInt()
                    ?: attendees.size
            val attendeesFromBlock = (payload["attendees_from_block"] as? Number)?.toInt()
            val threadRaw = payload["pulse_thread"] as? Map<String, Any?>
            val pulseThread =
                threadRaw?.let { td ->
                    val threadId =
                        (td["thread_id"] as? String)
                            ?: (td["id"] as? String) ?: return@let null
                    val title = td["title"] as? String ?: return@let null
                    val last = td["last_reply"] as? Map<String, Any?>
                    CommunityPulseThread(
                        threadId = threadId,
                        title = title,
                        replyCount =
                            (td["reply_count"] as? Number)?.toInt()
                                ?: (td["count"] as? Number)?.toInt() ?: 0,
                        lastReplyAuthor =
                            (last?.get("author") as? String)
                                ?: (last?.get("who") as? String),
                        lastReplyPreview = last?.get("preview") as? String,
                        lastReplyAge =
                            (last?.get("age") as? String)
                                ?: (last?.get("when") as? String),
                    )
                }
            return CommunityDetailDto(
                communityItemId = itemId,
                group = group,
                event = event,
                attendees = attendees,
                attendeeCount = attendeeCount,
                attendeesFromBlock = attendeesFromBlock,
                pulseThread = pulseThread,
                rsvp = CommunityRsvpStatus.fromWire(payload["rsvp_status"] as? String),
            )
        }

        private fun makeInitials(name: String): String {
            val parts = name.split(" ").take(2)
            return parts.mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                .joinToString("")
                .ifEmpty { "·" }
        }
    }
}
