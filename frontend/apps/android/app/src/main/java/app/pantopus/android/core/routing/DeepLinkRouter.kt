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

        /** `pantopus://wallet` — A10.10 earnings wallet (distinct from
         *  Settings → Payments; this is the earnings-side surface). */
        data object Wallet : Destination

        data class SupportTrain(val id: String) : Destination

        /**
         * `pantopus://support-trains/:id/manage` — organizer-only
         * review queue for a Support Train. Distinct from
         * [SupportTrain], which now lands on the participant detail
         * (A10.9). Owners reach the queue via the detail screen's
         * dock overflow; this deep link is the "land directly on the
         * queue" entry for organizer shortcuts.
         */
        data class SupportTrainManage(val id: String) : Destination

        data class Post(val id: String) : Destination

        data class Gig(val id: String) : Destination

        data class Listing(val id: String) : Destination

        data class HomeDetail(val id: String) : Destination

        data class HomeDashboard(val id: String) : Destination

        data class HomeMemberRequests(val id: String) : Destination

        /**
         * `pantopus://homes/:id/verify-landlord` — opens the A12.5 /
         * A12.6 wizard.
         */
        data class VerifyLandlord(val id: String) : Destination

        /**
         * `pantopus://homes/:id/verify-postcard` — opens the A12.7
         * sibling status screen directly.
         */
        data class PostcardVerification(val id: String) : Destination

        data class Conversation(val id: String) : Destination

        data class User(val id: String) : Destination

        data class Invite(val token: String) : Destination

        /**
         * `pantopus://auth/reset-password?token=…` — hashed recovery
         * token from the password-reset email. The caller invokes
         * `AuthRepository.resetPassword` on submit.
         */
        data class ResetPassword(val token: String) : Destination

        /**
         * `pantopus://auth/verify-email?token=…&email=…` — hashed Supabase
         * OTP from the verification email. `email` is optional but the
         * link from the resend / signup flow carries it so the surface
         * can render the recipient.
         */
        data class VerifyEmail(val token: String, val email: String?) : Destination

        /**
         * `pantopus://businesses/new` — open the A12.10 Create Business
         * wizard inside the active tab's nav stack.
         */
        data object CreateBusiness : Destination

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
        // Auth deep links carry `token` / `token_hash` (Supabase's two
        // recovery-link param names) and an optional `email`. Auth-callback
        // emails sometimes encode params in the fragment instead of the
        // query string, so parse both.
        val fragmentPart =
            rest.substringAfter('#', missingDelimiterValue = "")
        val tokenQuery =
            parseQueryParam(queryPart, "token")
                ?: parseQueryParam(queryPart, "token_hash")
                ?: parseQueryParam(fragmentPart, "token")
                ?: parseQueryParam(fragmentPart, "token_hash")
        val emailQuery =
            parseQueryParam(queryPart, "email") ?: parseQueryParam(fragmentPart, "email")

        return when (segments.first()) {
            "feed" -> Destination.Feed
            "home" -> Destination.Home
            "notifications" -> Destination.Notifications
            "connections" -> Destination.Connections
            "discover-hub", "discover_hub", "discoverhub" -> Destination.DiscoverHub
            "wallet" -> Destination.Wallet
            "support-trains", "support_train" -> {
                val id = segments.getOrNull(1)
                when {
                    id.isNullOrBlank() -> Destination.Unknown(raw)
                    segments.getOrNull(2) == "manage" -> Destination.SupportTrainManage(id)
                    else -> Destination.SupportTrain(id)
                }
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
                when (trailing.firstOrNull()) {
                    "dashboard" -> Destination.HomeDashboard(id)
                    "members" ->
                        if (tabQuery == "requests") {
                            Destination.HomeMemberRequests(id)
                        } else {
                            Destination.HomeDetail(id)
                        }
                    "verify-landlord", "verify_landlord" -> Destination.VerifyLandlord(id)
                    "verify-postcard", "verify_postcard" -> Destination.PostcardVerification(id)
                    else -> Destination.HomeDetail(id)
                }
            }
            "businesses", "business" -> {
                // `pantopus://businesses/new` opens the Create Business wizard.
                // Other `businesses/:id` paths are not yet routed here.
                if (segments.getOrNull(1) == "new") {
                    Destination.CreateBusiness
                } else {
                    Destination.Unknown(raw)
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
            "auth" -> {
                when (segments.getOrNull(1)) {
                    "reset-password", "reset_password" ->
                        if (tokenQuery.isNullOrEmpty()) {
                            Destination.Unknown(raw)
                        } else {
                            Destination.ResetPassword(tokenQuery)
                        }
                    "verify-email", "verify_email" ->
                        if (tokenQuery.isNullOrEmpty()) {
                            Destination.Unknown(raw)
                        } else {
                            Destination.VerifyEmail(token = tokenQuery, email = emailQuery)
                        }
                    else -> Destination.Unknown(raw)
                }
            }
            // Tolerate the bare `/reset-password?token=…` / `/verify-email?token=…`
            // shape that the backend's older recovery template emits (no
            // `/auth/` prefix).
            "reset-password", "reset_password" ->
                if (tokenQuery.isNullOrEmpty()) {
                    Destination.Unknown(raw)
                } else {
                    Destination.ResetPassword(tokenQuery)
                }
            "verify-email", "verify_email" ->
                if (tokenQuery.isNullOrEmpty()) {
                    Destination.Unknown(raw)
                } else {
                    Destination.VerifyEmail(token = tokenQuery, email = emailQuery)
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
