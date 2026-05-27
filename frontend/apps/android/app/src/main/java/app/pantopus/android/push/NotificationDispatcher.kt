@file:Suppress("PackageNaming")

package app.pantopus.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.pantopus.android.MainActivity
import app.pantopus.android.R
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Routes incoming FCM payloads to the right notification channel and,
 * on tap, into the same deep-link router iOS uses.
 *
 * Mirrors iOS `AppDelegate.userNotificationCenter(_:didReceive:)` — the
 * payload's `link` (or `deepLink`) is baked into the PendingIntent so
 * MainActivity's existing `ACTION_VIEW` handler feeds it into
 * `DeepLinkRouter.handle(uri)` on tap. Same trigger point, same
 * destination, both platforms.
 *
 * Android additionally splits the four payload families the backend
 * emits (chat / mail / gig bid / system) onto separate notification
 * channels so users can mute one without losing the others. The category
 * is inferred from the backend `type` field — the canonical list lives
 * in `backend/services/pushService.js`.
 */
@Singleton
class NotificationDispatcher
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
    ) {
        /** Channels match the four top-level families the backend emits. */
        enum class Channel(
            val id: String,
            val nameRes: Int,
            val importance: Int,
        ) {
            CHAT(
                id = "pantopus.chat",
                nameRes = R.string.notif_channel_chat,
                importance = NotificationManager.IMPORTANCE_HIGH,
            ),
            MAIL(
                id = "pantopus.mail",
                nameRes = R.string.notif_channel_mail,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
            ),
            GIG_BID(
                id = "pantopus.gig_bid",
                nameRes = R.string.notif_channel_gig_bid,
                importance = NotificationManager.IMPORTANCE_HIGH,
            ),
            SYSTEM(
                id = "pantopus.system",
                nameRes = R.string.notif_channel_system,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
            ),
        }

        /**
         * Resolved routing for a single FCM message. Exposed as a value so
         * the dispatching logic is unit-testable without RemoteMessage
         * (which can't be instantiated on the JVM).
         */
        data class Routing(
            val channel: Channel,
            val title: String?,
            val body: String?,
            val deepLink: String?,
        )

        /**
         * Hand off a [RemoteMessage] for foreground/background display.
         * Extracts the payload and posts a system notification on the
         * right channel. The deep link is baked into the notification's
         * PendingIntent so it only fires on tap — same trigger point
         * iOS uses in `userNotificationCenter(_:didReceive:)`.
         */
        fun dispatch(message: RemoteMessage) {
            val routing = route(message.data, message.notification?.title, message.notification?.body)
            postNotification(routing)
        }

        /**
         * Pure routing — takes a flat `data` map (as FCM delivers it) plus
         * the optional title/body from the notification block, and returns
         * the resolved [Routing]. Stays JVM-only so tests don't need
         * Robolectric.
         */
        internal fun route(
            data: Map<String, String>,
            notificationTitle: String? = null,
            notificationBody: String? = null,
        ): Routing {
            val channel = channelFor(data["type"])
            val title = notificationTitle ?: data["title"]
            val body = notificationBody ?: data["body"]
            val deepLink = data["link"] ?: data["deepLink"]
            return Routing(channel = channel, title = title, body = body, deepLink = deepLink)
        }

        /**
         * Map a backend notification `type` to one of the four channels.
         * The substring/prefix shapes catch the families enumerated in
         * `backend/services/pushService.js` (e.g. `bid_received`,
         * `gig_started`, `mail_delivered`, `chat_message`).
         */
        internal fun channelFor(type: String?): Channel {
            val key = type?.lowercase() ?: return Channel.SYSTEM
            return when {
                key == "chat" ||
                    key.startsWith("chat_") ||
                    key == "new_message" ||
                    key.endsWith("_message") ||
                    key.contains("message_") -> Channel.CHAT

                key == "mail" ||
                    key.startsWith("mail_") ||
                    key.startsWith("mailbox_") ||
                    key == "mailbox" -> Channel.MAIL

                key.startsWith("bid_") ||
                    key == "first_bid_received" ||
                    key.startsWith("gig_") ||
                    key == "gig_offer" -> Channel.GIG_BID

                else -> Channel.SYSTEM
            }
        }

        private fun postNotification(routing: Routing) {
            ensureChannel(routing.channel)
            val contentIntent = buildContentIntent(routing.deepLink)
            // TODO: replace `ic_launcher` with a dedicated monochrome
            // notification icon (`ic_notification`) before public launch.
            // Status-bar icons must be white-on-transparent per Android
            // 5+; the launcher icon renders as a flat silhouette here.
            val notification =
                NotificationCompat
                    .Builder(appContext, routing.channel.id)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(routing.title ?: appContext.getString(R.string.app_name))
                    .apply { routing.body?.let(::setContentText) }
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .setPriority(routing.channel.toCompatPriority())
                    .build()

            val nm = NotificationManagerCompat.from(appContext)
            // POST_NOTIFICATIONS is requested at runtime in MainActivity.
            // If it's still denied, drop silently — `nm.notify` would
            // throw SecurityException on Android 13+.
            if (!nm.areNotificationsEnabled()) {
                Timber.d("Notifications disabled — skipping system post")
                return
            }
            runCatching {
                nm.notify(Random.nextInt(), notification)
            }.onFailure { Timber.w(it, "Failed to post FCM notification") }
        }

        private fun ensureChannel(channel: Channel) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(channel.id) != null) return
            val nc = NotificationChannel(channel.id, appContext.getString(channel.nameRes), channel.importance)
            manager.createNotificationChannel(nc)
        }

        private fun buildContentIntent(deepLink: String?): PendingIntent {
            val intent =
                Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (!deepLink.isNullOrBlank()) {
                        action = Intent.ACTION_VIEW
                        data = normalizeForIntent(deepLink)
                    }
                }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(appContext, Random.nextInt(), intent, flags)
        }

        /**
         * Notification payloads carry path-style deep links (e.g.
         * `/gig/g_42`); MainActivity's deep-link forwarder only acts on
         * `Intent.ACTION_VIEW` with a non-null `Uri`, so wrap the path
         * in the `pantopus://` custom scheme. Full URLs pass through.
         */
        private fun normalizeForIntent(deepLink: String): Uri {
            val normalized =
                when {
                    deepLink.startsWith("pantopus://") || deepLink.startsWith("http") -> deepLink
                    deepLink.startsWith("/") -> "pantopus://" + deepLink.drop(1)
                    else -> "pantopus://$deepLink"
                }
            return Uri.parse(normalized)
        }

        private fun Channel.toCompatPriority(): Int =
            when (importance) {
                NotificationManager.IMPORTANCE_HIGH -> NotificationCompat.PRIORITY_HIGH
                NotificationManager.IMPORTANCE_LOW -> NotificationCompat.PRIORITY_LOW
                NotificationManager.IMPORTANCE_MIN -> NotificationCompat.PRIORITY_MIN
                else -> NotificationCompat.PRIORITY_DEFAULT
            }
    }
