@file:Suppress("MagicNumber")

package app.pantopus.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for [SystemSheets] pure helpers:
 *
 *   - [InviteLinks] surfaces the canonical download URL + invite copy.
 *   - [MailDraft.mailtoUri] URL-encodes RFC 6068 fields correctly.
 *
 * The Compose-native bottom sheets ([ActionSheet] / [ConfirmationSheet] /
 * [PickerSheet]) live behind `ModalBottomSheet` whose `SheetState` requires
 * a running window manager; their visual contract is covered by
 * UI-integration tests in `androidTest/` and the Paparazzi gallery in
 * `ComponentsSnapshotTest`, not here.
 */
class SystemSheetsTest {
    @Test
    fun inviteLinks_message_contains_download_url() {
        assertTrue(
            "Invite message references the canonical download URL",
            InviteLinks.INVITE_MESSAGE.contains(InviteLinks.DOWNLOAD_URL),
        )
    }

    @Test
    fun mailDraft_encodes_subject_and_body() {
        val draft = MailDraft(subject = "Hello there", body = "A & B = c", recipients = emptyList())
        val uri = draft.mailtoUri.toString()
        assertTrue("URI starts with mailto:", uri.startsWith("mailto:"))
        assertTrue("Subject is URL-encoded", uri.contains("Hello%20there"))
        // '&' and '=' inside body must be URL-encoded so the mail picker
        // doesn't truncate the body at the first '&'.
        assertTrue("Ampersand is URL-encoded", uri.contains("%26"))
        assertTrue("Equals sign is URL-encoded", uri.contains("%3D"))
    }

    @Test
    fun mailDraft_joins_multiple_recipients_with_commas() {
        val draft =
            MailDraft(
                subject = "Hi",
                body = "Body",
                recipients = listOf("a@example.com", "b@example.com"),
            )
        val uri = draft.mailtoUri.toString()
        // Recipients are individually URL-encoded then comma-joined. '@'
        // encodes to %40 in Uri.encode().
        assertTrue("Both recipients appear", uri.contains("a%40example.com"))
        assertTrue("Second recipient appears", uri.contains("b%40example.com"))
        assertTrue("Comma separator preserved", uri.contains(","))
    }

    @Test
    fun mailDraft_empty_recipients_still_produces_valid_uri() {
        val draft = MailDraft(subject = "S", body = "B")
        val uri = draft.mailtoUri.toString()
        // mailto: with no path is valid per RFC 6068 ("blind mail").
        assertTrue(uri.startsWith("mailto:?"))
    }

    @Test
    fun actionSheetOption_carries_destructive_flag() {
        val option = ActionSheetOption(label = "Delete", isDestructive = true, onSelect = {})
        assertEquals("Delete", option.label)
        assertTrue(option.isDestructive)
    }

    @Test
    fun actionSheetOption_default_is_non_destructive() {
        val option = ActionSheetOption(label = "Edit", onSelect = {})
        assertEquals(false, option.isDestructive)
    }

    @Test
    fun pickedContact_holds_full_payload() {
        val contact =
            PickedContact(
                name = "Alex Renault",
                phone = "+1 415 555 0100",
                email = "alex@example.com",
            )
        assertEquals("Alex Renault", contact.name)
        assertEquals("+1 415 555 0100", contact.phone)
        assertEquals("alex@example.com", contact.email)
    }
}
