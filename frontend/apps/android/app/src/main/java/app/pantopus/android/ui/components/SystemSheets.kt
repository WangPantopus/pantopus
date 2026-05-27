@file:Suppress("MagicNumber", "MatchingDeclarationName", "LongParameterList", "TooManyFunctions")

package app.pantopus.android.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.pantopus.android.ui.screens.shared.list_of_rows.CompactButtonVariant
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import java.io.File

// ─── Invite copy / links ─────────────────────────────────────────
//
// Single source of truth for the invite message + download link shared
// by "Invite to Pantopus" and the post-contact-pick invite. Mirrors iOS
// `InviteLinks`. Swap [DOWNLOAD_URL] for the real Play Store smart-link
// when it ships — every invite surface reads from here.

object InviteLinks {
    const val DOWNLOAD_URL: String = "https://pantopus.app"

    val downloadUri: Uri get() = DOWNLOAD_URL.toUri()

    const val INVITE_MESSAGE: String =
        "Join me on Pantopus — your neighborhood for trusted home help, " +
            "local gigs, and your whole household in one place. $DOWNLOAD_URL"
}

// ─── Share sheet (Intent.ACTION_SEND) ────────────────────────────
//
// Android's native share is an Intent, not a Compose sheet. Iframe-style
// wrap so call sites don't reach into Intent themselves. Mirrors iOS
// `SystemShareSheet` semantics: hand items + optional URL → open the
// system chooser.

/** Fire the system share sheet for plain text (ACTION_SEND). */
fun Context.shareText(
    text: String,
    chooserTitle: String = "Share",
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

/** Fire the system share sheet for a content:// file (ACTION_SEND). */
fun Context.shareFile(
    uri: Uri,
    mimeType: String,
    chooserTitle: String = "Share",
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

/** Resolve a cached [File] to a `content://` URI via the app FileProvider. */
fun fileProviderUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

// ─── Mail compose (Intent.ACTION_SENDTO mailto:) ─────────────────
//
// iOS uses `MFMailComposeViewController` with a `mailto:` fallback. On
// Android the only first-party mail compose path IS `mailto:` — the
// `ACTION_SENDTO` intent with a `mailto:` URI resolves to the user's
// chosen mail app. Mirrors iOS `MailDraft` shape; falls back to plain
// share when no mail app is configured (Pixel emulators in CI).

/**
 * Draft payload for a `mailto:` intent. Recipients / subject / body are
 * URL-encoded into the URI per RFC 6068.
 */
data class MailDraft(
    val subject: String,
    val body: String,
    val recipients: List<String> = emptyList(),
) {
    /** RFC 6068 `mailto:` URI used to launch the system mail picker. */
    val mailtoUri: Uri
        get() {
            val to = recipients.joinToString(",") { Uri.encode(it) }
            val s = Uri.encode(subject)
            val b = Uri.encode(body)
            return "mailto:$to?subject=$s&body=$b".toUri()
        }
}

/**
 * Open the system mail composer (ACTION_SENDTO `mailto:`). Falls back
 * to a plain-text share when no mail app is configured.
 */
fun Context.composeEmail(
    subject: String,
    body: String,
    recipient: String? = null,
) {
    val draft = MailDraft(
        subject = subject,
        body = body,
        recipients = if (recipient != null) listOf(recipient) else emptyList(),
    )
    try {
        startActivity(Intent(Intent.ACTION_SENDTO, draft.mailtoUri))
    } catch (_: ActivityNotFoundException) {
        shareText("$subject\n\n$body")
    }
}

// ─── Contacts picker (ActivityResult contract) ───────────────────
//
// Android exposes contact selection via the system ActivityResult
// contract — no Compose sheet to render. We wrap it so the result
// resolves to the same [PickedContact] shape iOS uses, and a default
// "after-pick" flow that falls through to [shareText] for invite.

/** Mirrors iOS `PickedContact`. */
data class PickedContact(
    val name: String,
    val phone: String?,
    val email: String?,
)

/**
 * `ActivityResultContract` that opens the system contact picker. The
 * launcher result is a `Uri?` pointing at `content://com.android.contacts/...`;
 * resolve to a [PickedContact] via [resolveContact].
 */
class PickContactContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}

/**
 * Resolve a contact URI returned by the picker into a [PickedContact].
 * Reads the DISPLAY_NAME column + the first PHONE / EMAIL row. Returns
 * `null` when the URI is malformed or the contact has no name.
 */
fun resolveContact(context: Context, contactUri: Uri): PickedContact? {
    val resolver = context.contentResolver
    var name: String? = null
    var contactId: String? = null
    resolver
        .query(
            contactUri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                contactId = cursor.getString(0)
                name = cursor.getString(1)
            }
        }
    val id = contactId
    val resolvedName = name
    if (id.isNullOrBlank() || resolvedName.isNullOrBlank()) return null

    val phone = firstField(
        context,
        id,
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    val email = firstField(
        context,
        id,
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        ContactsContract.CommonDataKinds.Email.CONTACT_ID,
        ContactsContract.CommonDataKinds.Email.ADDRESS,
    )
    return PickedContact(name = resolvedName, phone = phone, email = email)
}

private fun firstField(
    context: Context,
    contactId: String,
    contentUri: Uri,
    contactIdColumn: String,
    valueColumn: String,
): String? =
    context.contentResolver
        .query(contentUri, arrayOf(valueColumn), "$contactIdColumn = ?", arrayOf(contactId), null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

/**
 * Composable wrapper that registers a contact picker launcher + auto-
 * resolves the result. Mirrors iOS `findPeopleSheet(isPresented:)`'s
 * pick→invite chain when the caller follows up with [shareText] in the
 * contact callback.
 */
@Composable
fun rememberContactPicker(
    onPicked: (PickedContact?) -> Unit,
): ActivityResultLauncher<Unit> {
    val context = LocalContext.current
    val callback = remember(onPicked) {
        { uri: Uri? ->
            val resolved = uri?.let { resolveContact(context, it) }
            onPicked(resolved)
        }
    }
    return rememberLauncherForActivityResult(contract = PickContactContract()) { uri ->
        callback(uri)
    }
}

// ─── Compose-native bottom sheets ────────────────────────────────
//
// Action / confirmation / picker sheets are Compose-native because
// they're the on-platform analog of iOS `.confirmationDialog` / `.sheet`
// surfaces. Each is a thin slot wrapper over Material3 `ModalBottomSheet`
// so feature code never touches `SheetState` directly.

/** One option in an [ActionSheet]. Mirrors iOS `confirmationDialog` buttons. */
data class ActionSheetOption(
    val label: String,
    val isDestructive: Boolean = false,
    val onSelect: () -> Unit,
)

/**
 * Action sheet — a vertical stack of tappable options. Use for
 * "Edit / Share / Delete" surfaces and any short list of mutually
 * exclusive actions.
 *
 * @param title Optional sheet title.
 * @param options Buttons rendered top-to-bottom.
 * @param onDismiss Called when the user taps outside or back-gestures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    title: String?,
    options: List<ActionSheetOption>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PantopusColors.appSurface,
        modifier = modifier.testTag(ACTION_SHEET_TEST_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = PantopusTextStyle.h3,
                    color = PantopusColors.appText,
                )
            }
            options.forEach { option ->
                ActionRow(option)
            }
        }
    }
}

/**
 * Confirmation sheet — title + body + two CTAs (confirm / cancel).
 * Use for "Are you sure you want to delete X" surfaces.
 *
 * @param title Headline copy.
 * @param confirmLabel Confirm CTA label.
 * @param onConfirm Confirm tap.
 * @param onDismiss Tap-outside or cancel tap.
 * @param body Optional supporting copy.
 * @param cancelLabel Cancel CTA label. Defaults to "Cancel".
 * @param isDestructive Render the confirm CTA in destructive paint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationSheet(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    cancelLabel: String = "Cancel",
    isDestructive: Boolean = false,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PantopusColors.appSurface,
        modifier = modifier.testTag(CONFIRMATION_SHEET_TEST_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(text = title, style = PantopusTextStyle.h3, color = PantopusColors.appText)
            if (body != null) {
                Text(text = body, style = PantopusTextStyle.body, color = PantopusColors.appTextSecondary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                CompactButton(
                    title = cancelLabel,
                    variant = CompactButtonVariant.Ghost,
                    size = CompactButtonSize.Footer,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                CompactButton(
                    title = confirmLabel,
                    variant =
                        if (isDestructive) CompactButtonVariant.Destructive else CompactButtonVariant.Primary,
                    size = CompactButtonSize.Footer,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Picker sheet — generic slot wrapper for any selection UI (date,
 * category, etc.) where the body composable owns rendering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PantopusColors.appSurface,
        modifier = modifier.testTag(PICKER_SHEET_TEST_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(text = title, style = PantopusTextStyle.h3, color = PantopusColors.appText)
            content()
        }
    }
}

@Composable
private fun ActionRow(option: ActionSheetOption) {
    val color =
        if (option.isDestructive) PantopusColors.error else PantopusColors.appText
    Text(
        text = option.label,
        style = PantopusTextStyle.body,
        color = color,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .clickable(onClick = option.onSelect)
                .background(PantopusColors.appSurface)
                .padding(vertical = Spacing.s3, horizontal = Spacing.s2)
                .testTag("action-sheet-option-${option.label}"),
    )
}

internal const val ACTION_SHEET_TEST_TAG = "system-sheet-action"
internal const val CONFIRMATION_SHEET_TEST_TAG = "system-sheet-confirmation"
internal const val PICKER_SHEET_TEST_TAG = "system-sheet-picker"

@Preview(showBackground = true, widthDp = 360, heightDp = 200)
@Composable
private fun SystemSheetsPreviewLabel() {
    Column(modifier = Modifier.padding(Spacing.s4)) {
        Text(
            text = "ActionSheet / ConfirmationSheet / PickerSheet — modal previews are inert",
            style = PantopusTextStyle.caption,
        )
    }
}
