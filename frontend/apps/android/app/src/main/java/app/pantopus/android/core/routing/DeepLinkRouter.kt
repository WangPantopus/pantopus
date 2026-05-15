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
 */
object DeepLinkRouter {
    sealed interface Destination {
        data object Feed : Destination

        data object Home : Destination

        data class Post(val id: String) : Destination

        data class Conversation(val id: String) : Destination

        data class Invite(val token: String) : Destination

        data class Unknown(val uri: String) : Destination
    }

    private val _pending = MutableStateFlow<Destination?>(null)
    val pending: StateFlow<Destination?> = _pending.asStateFlow()

    fun handle(uri: Uri) {
        _pending.value = resolve(uri)
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
        // Drop query / fragment from the matchable section.
        val pathOnly = rest.substringBefore('?').substringBefore('#')
        // Custom scheme: host segment is the first route token.
        // https://host/path/path: skip the host, route starts after.
        val parts = pathOnly.split('/').filter { it.isNotBlank() }
        val segments: List<String> =
            if (scheme == "http" || scheme == "https") {
                if (parts.size <= 1) emptyList() else parts.drop(1)
            } else {
                parts
            }
        if (segments.isEmpty()) return Destination.Unknown(raw)
        return when (segments.first()) {
            "feed" -> Destination.Feed
            "home" -> Destination.Home
            "post", "posts" -> {
                val id = segments.getOrNull(1)
                if (id.isNullOrBlank()) Destination.Unknown(raw) else Destination.Post(id)
            }
            "message", "messages", "conversation" -> {
                val id = segments.getOrNull(1)
                if (id.isNullOrBlank()) Destination.Unknown(raw) else Destination.Conversation(id)
            }
            "invite" -> {
                val token = segments.getOrNull(1)
                if (token.isNullOrBlank()) Destination.Unknown(raw) else Destination.Invite(token)
            }
            else -> Destination.Unknown(raw)
        }
    }
}
