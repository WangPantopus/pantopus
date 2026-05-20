package app.pantopus.android.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * P6.6 — system share / mail helpers used by the placeholder sweep:
 * "Share …", "Invite …", and "Find people". Centralising the invite copy
 * + store link here keeps every invite surface on one payload.
 */
object InviteLinks {
    /** Public download landing page — single source of truth. Swap for the
     * real App Store / Play Store smart-link when it ships. */
    const val DOWNLOAD_URL = "https://pantopus.app"

    const val INVITE_MESSAGE =
        "Join me on Pantopus — your neighborhood for trusted home help, " +
            "local gigs, and your whole household in one place. $DOWNLOAD_URL"
}

/** Fire the system share sheet for plain text (ACTION_SEND). */
fun Context.shareText(text: String, chooserTitle: String = "Share") {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

/** Fire the system share sheet for a content:// file (ACTION_SEND). */
fun Context.shareFile(uri: Uri, mimeType: String, chooserTitle: String = "Share") {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

/**
 * Open the system mail composer (ACTION_SENDTO `mailto:`). Falls back to a
 * plain-text share when no mail app is configured.
 */
fun Context.composeEmail(subject: String, body: String, recipient: String? = null) {
    val mailto =
        buildString {
            append("mailto:")
            if (recipient != null) append(Uri.encode(recipient))
            append("?subject=").append(Uri.encode(subject))
            append("&body=").append(Uri.encode(body))
        }
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailto))
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        shareText("$subject\n\n$body")
    }
}
