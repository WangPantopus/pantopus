@file:Suppress("MagicNumber")

package app.pantopus.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in the exact accessibility / `contentDescription` strings the
 * five ported components hand to screen readers, so the strings stay
 * verbatim-equal to their iOS `.accessibilityLabel` / `.accessibilityIdentifier`
 * counterparts. A drift on either side fails the matching test on the
 * other.
 *
 * Pin sources (iOS):
 *   - BidderStack:    `Core/Design/Components/BidderStack.swift:70-79`
 *   - Toast:          `Core/Design/Components/Toast.swift:47`
 *   - SystemSheets:   `Core/Design/Components/SystemSheets.swift:31-33`
 *
 * iOS testIDs the `gig-detail-toast` and similar per-screen tags inline
 * at the use site; this file covers ONLY component-owned strings.
 */
class ComponentA11yStringsParityTest {
    @Test
    fun bidderStack_label_strings_match_iOS() {
        assertEquals("No bidders", bidderStackA11yLabel(0))
        assertEquals("1 bidder", bidderStackA11yLabel(1))
        assertEquals("2 bidders", bidderStackA11yLabel(2))
        assertEquals("99 bidders", bidderStackA11yLabel(99))
    }

    @Test
    fun toast_pill_test_tag_matches_contract() {
        // Locked-in test tag — UI integration tests can findByTag("toast-pill").
        // iOS-side `accessibilityIdentifier("…-toast")` is per-screen; the
        // component-owned tag is platform-specific but stable.
        assertEquals("toast-pill", TOAST_TEST_TAG)
    }

    @Test
    fun system_sheets_test_tags_are_stable() {
        // Locked-in test tags for the Compose-native bottom sheets so
        // androidTest / espresso lookups don't drift.
        assertEquals("system-sheet-action", ACTION_SHEET_TEST_TAG)
        assertEquals("system-sheet-confirmation", CONFIRMATION_SHEET_TEST_TAG)
        assertEquals("system-sheet-picker", PICKER_SHEET_TEST_TAG)
    }

    @Test
    fun invite_message_is_verbatim_equal_to_iOS() {
        // iOS `InviteLinks.inviteMessage` — locked here so a marketing
        // copy update flows to both platforms in lockstep.
        val expected =
            "Join me on Pantopus — your neighborhood for trusted home help, " +
                "local gigs, and your whole household in one place. " +
                InviteLinks.DOWNLOAD_URL
        assertEquals(expected, InviteLinks.INVITE_MESSAGE)
    }

    @Test
    fun invite_download_url_is_canonical() {
        assertEquals("https://pantopus.app", InviteLinks.DOWNLOAD_URL)
    }
}
