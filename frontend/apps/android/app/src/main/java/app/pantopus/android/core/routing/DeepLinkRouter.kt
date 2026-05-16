@file:Suppress("PackageNaming")

package app.pantopus.android.core.routing

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mirrors iOS `Core/Routing/DeepLinkRouter.swift`. The host activity
 * pushes Uri instances in via [handle]; observers collect [pending]
 * and call [consume] when they've routed it.
 *
 * The router accepts both `pantopus://…` and `https://pantopus.app/…`
 * URLs. The first segment after the host (or the host itself for the
 * custom scheme) names the route.
 *
 * Full routing table from `docs/07-frontend-mobile-app.md §9`.
 * `Home` (singular) keeps the legacy "go to Hub" semantics; the typed
 * `HomeDetail` / `HomeDashboard` / `HomeMemberRequests` variants
 * cover `/homes/:id/[*]`.
 */
object DeepLinkRouter {
    sealed interface Destination {
        data object Feed : Destination

        data object Home : Destination

        data object Notifications : Destination

        data object Connections : Destination

        data object DiscoverHub : Destination

        data class SupportTrain(val id: String) : Destination

        data class Post(val id: String) : Destination

        data class Gig(val id: String) : Destination

        data class Listing(val id: String) : Destination

        data class HomeDetail(val id: String) : Destination

        data class HomeDashboard(val id: String) : Destination

        data class HomeMemberRequests(val id: String) : Destination

        data class Conversation(val id: String) : Destination

        data class User(val id: String) : Destination

        data class Invite(val token: String) : Destination

        data class Unknown(val uri: String) : Destination
    }

    private val _pending = MutableStateFlow<Destination?>(null)
    val pending: StateFlow<Destination?> = _pending.asStateFlow()

    fun handle(uri: Uri) {
        _pending.value = resolve(uri)
    }

    /**
     * Receive a raw path-style link from a notification payload (e.g.
     * `link` on `NotificationDto`). Routed through the same resolver as
     * full URL deep links.
     */
    fun handle(path: String) {
        val normalized =
            when {
                path.startsWith("pantopus://") || path.startsWith("http") -> path
                path.startsWith("/") -> "pantopus://" + path.drop(1)
                else -> "pantopus://$path"
            }
        _pending.value = resolveString(normalized)
    }

    fun consume(): Destination? {
        val current = _pending.value
        _pending.value = null
        return current
    }

    internal fun resolve(uri: Uri): Destination = resolveString(uri.toString())

    /**
     * Pure-string resolver — works on JVM unit tests without
     * Robolectric (`android.net.Uri` is a stub there).
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount", "LongMethod")
    internal fun resolveString(raw: String): Destination {
        if (raw.isBlank()) return Destination.Unknown(raw)
        val schemeEnd = raw.indexOf("://")
        val scheme: String
        val rest: String
        if (schemeEnd >= 0) {
            scheme = raw.substring(0, schemeEnd)
            rest = raw.substring(schemeEnd + 3)
        } else {
            scheme = ""
            rest = raw
        }
        // Split off the query / fragment so the segment match below sees
        // just the path components.
        val pathPart = rest.substringBefore('?').substringBefore('#')
        val queryPart =
            rest.substringAfter('?', missingDelimiterValue = "")
                .substringBefore('#')
        val parts = pathPart.split('/').filter { it.isNotBlank() }
        val segments: List<String> =
            if (scheme == "http" || scheme == "https") {
                if (parts.size <= 1) emptyList() else parts.drop(1)
            } else {
                parts
            }
        if (segments.isEmpty()) return Destination.Unknown(raw)
        val tabQuery = parseQueryParam(queryPart, "tab")

        return when (segments.first()) {
            "feed" -> Destination.Feed
            "home" -> Destination.Home
            "notifications" -> Destination.Notifications
            "connections" -> Destination.Connections
            "discover-hub", "discover_hub", "discoverhub" -> Destination.DiscoverHub
            "support-trains", "support_train" -> {
                val id = segments.getOrNull(1)
                if (id.isNullOrBlank()) Destination.Unknown(raw) else Destination.SupportTrain(id)
            }
            "post", "posts" -> {
                val id = segments.getOrNull(1)
                if (id.isNullOrBlank()) Destination.Unknown(raw) else Destination.Post(id)
            }
            "gig", "gigs" -> {
                val id = segments.getOrNull(1)
                if (id.isNullOrBlank()) Destination.Unknown(raw) else Destination.Gig(id)
            }
            "listing", "listings" -> {
                val id = segments.getOrNull(1)
                if (id.isNullOrBlank()) Destination.Unknown(raw) else Destination.Listing(id)
            }
            "homes" -> {
                val id = segments.getOrNull(1)
                if (id.isNullOrBlank()) return Destination.Unknown(raw)
                val trailing = segments.drop(2)
                when {
                    trailing.firstOrNull() == "dashboard" -> Destination.HomeDashboard(id)
                    trailing.firstOrNull() == "members" && tabQuery == "requests" ->
                        Destination.HomeMemberRequests(id)
                    else -> Destination.HomeDetail(id)
                }
            }
            "chat", "message", "messages", "conversation" -> {
                val id = segments.getOrNull(1)
                if (id.isNullOrBlank()) Destination.Unknown(raw) else Destination.Conversation(id)
            }
            "user", "users" -> {
                val id = segments.getOrNull(1)
                if (id.isNullOrBlank()) Destination.Unknown(raw) else Destination.User(id)
            }
            "invite" -> {
                val token = segments.getOrNull(1)
                if (token.isNullOrBlank()) Destination.Unknown(raw) else Destination.Invite(token)
            }
            else -> Destination.Unknown(raw)
        }
    }

    private fun parseQueryParam(
        query: String,
        key: String,
    ): String? {
        if (query.isBlank()) return null
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            if (eq < 0) continue
            val k = pair.substring(0, eq)
            if (k == key) return pair.substring(eq + 1)
        }
        return null
    }
}
