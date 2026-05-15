@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.you.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi snapshots for the Me tab (T1.3). One baseline per identity
 * binding — Personal, Home, and unbound-Business — proves the
 * identity-rebind chrome stays put while header gradient, action
 * accent, stats, and section labels swap.
 */
class MeSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2400,
                    softButtons = false,
                ),
        )

    @Test
    fun me_personal() {
        paparazzi.snapshot {
            Frame { PopulatedFrame(active = personalContent(), onSwitch = {}, onAction = {}, onSection = {}, onDestructive = {}) }
        }
    }

    @Test
    fun me_home() {
        paparazzi.snapshot {
            Frame { PopulatedFrame(active = homeContent(), onSwitch = {}, onAction = {}, onSection = {}, onDestructive = {}) }
        }
    }

    @Test
    fun me_business_unbound() {
        paparazzi.snapshot {
            Frame { PopulatedFrame(active = businessUnbound(), onSwitch = {}, onAction = {}, onSection = {}, onDestructive = {}) }
        }
    }

    private fun personalContent(): MeIdentityContent =
        MeIdentityContent(
            identity = MeIdentity.Personal,
            displayName = "Maria K.",
            initials = "MK",
            handle = "@maria",
            locality = "Elm Park",
            bio = "Neighborhood cleanup organiser; coffee enthusiast.",
            verified = true,
            stats =
                listOf(
                    MeStat(id = "posts", value = "24", label = "Posts"),
                    MeStat(id = "helpful", value = "108", label = "Helpful"),
                    MeStat(id = "saved", value = "12", label = "Saved"),
                ),
            actionTiles =
                listOf(
                    MeActionTile(id = "mail", icon = PantopusIcon.Mailbox, label = "Mail", routeKey = "me.mail", badge = 3),
                    MeActionTile(id = "posts", icon = PantopusIcon.Star, label = "My posts", routeKey = "me.posts"),
                    MeActionTile(id = "saved", icon = PantopusIcon.Heart, label = "Saved", routeKey = "me.saved"),
                    MeActionTile(id = "wallet", icon = PantopusIcon.ShoppingBag, label = "Wallet", routeKey = "me.wallet"),
                    MeActionTile(id = "verify", icon = PantopusIcon.ShieldCheck, label = "Verify", routeKey = "me.verify"),
                    MeActionTile(id = "settings", icon = PantopusIcon.MoreHorizontal, label = "Settings", routeKey = "me.settings"),
                ),
            sections =
                listOf(
                    MeSection(
                        id = "account",
                        header = "Account",
                        rows =
                            listOf(
                                MeSectionRow(id = "editProfile", icon = PantopusIcon.User, label = "Edit profile", routeKey = "me.editProfile"),
                                MeSectionRow(id = "notifications", icon = PantopusIcon.Bell, label = "Notifications", routeKey = "me.notifications"),
                            ),
                    ),
                ),
        )

    private fun homeContent(): MeIdentityContent =
        MeIdentityContent(
            identity = MeIdentity.Home,
            displayName = "12 Rose Court",
            initials = "RC",
            handle = "Rose Court, Unit 4B",
            locality = "Elm Park",
            bio = "Two-person household. Hosts the block potluck.",
            verified = true,
            stats =
                listOf(
                    MeStat(id = "members", value = "2", label = "Members"),
                    MeStat(id = "mail", value = "7", label = "Mail"),
                    MeStat(id = "claims", value = "1", label = "Claim"),
                ),
            actionTiles =
                listOf(
                    MeActionTile(id = "dashboard", icon = PantopusIcon.Home, label = "Dashboard", routeKey = "home.dashboard"),
                    MeActionTile(id = "mail", icon = PantopusIcon.Mailbox, label = "Mail", routeKey = "home.mail", badge = 7),
                    MeActionTile(id = "members", icon = PantopusIcon.UserPlus, label = "Members", routeKey = "home.members"),
                    MeActionTile(id = "invite", icon = PantopusIcon.Send, label = "Invite owner", routeKey = "home.invite"),
                    MeActionTile(id = "claims", icon = PantopusIcon.ShieldCheck, label = "Claims", routeKey = "home.claims"),
                    MeActionTile(id = "settings", icon = PantopusIcon.MoreHorizontal, label = "Settings", routeKey = "home.settings"),
                ),
            sections =
                listOf(
                    MeSection(
                        id = "household",
                        header = "Household",
                        rows =
                            listOf(
                                MeSectionRow(id = "address", icon = PantopusIcon.MapPin, label = "Address", value = "Verified", routeKey = "home.address"),
                            ),
                    ),
                ),
        )

    private fun businessUnbound(): MeIdentityContent =
        MeIdentityContent(
            identity = MeIdentity.Business,
            displayName = "No business yet",
            initials = "B",
            handle = "Tap to register your business",
            locality = null,
            bio = null,
            verified = false,
            stats = emptyList(),
            actionTiles =
                listOf(
                    MeActionTile(id = "register", icon = PantopusIcon.PlusSquare, label = "Register", routeKey = "business.register"),
                ),
            sections = emptyList(),
            isUnbound = true,
        )

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) { content() }
        }
    }
}
