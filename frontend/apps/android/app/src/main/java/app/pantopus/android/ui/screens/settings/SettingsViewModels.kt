@file:Suppress("MagicNumber", "PackageNaming", "TooManyFunctions", "LongMethod")

package app.pantopus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.BuildConfig
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.privacy.PrivacyRepository
import app.pantopus.android.ui.components.ChannelGlyph
import app.pantopus.android.ui.components.FuzzStop
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListBanner
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListFuzz
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListGroup
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListRow
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListUiState
import app.pantopus.android.ui.screens.shared.grouped_list.RowControl
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sentinel routes the Settings index can ask its host to push. */
enum class SettingsRoute {
    EditProfile,
    Password,
    Verification,
    Blocks,
    Notifications,
    Privacy,
    IdentityCenter,
    DataExport,
    PaymentsPayouts,
    Help,
    Legal,
    About,
    DidSignOut,

    /**
     * P1.1 — admin-only Review-claims queue. Only surfaced on the
     * Settings index when `auth.state.value.user.isAdmin == true`.
     */
    ReviewClaims,
}

// MARK: - Index

@HiltViewModel
class SettingsIndexViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val privacy: PrivacyRepository,
    ) : ViewModel() {
        val title: String = "Settings"

        private val _state = MutableStateFlow<GroupedListUiState>(GroupedListUiState.Loading)
        val state: StateFlow<GroupedListUiState> = _state.asStateFlow()

        private val _footerCaption = MutableStateFlow<String?>(null)
        val footerCaption: StateFlow<String?> = _footerCaption.asStateFlow()

        private val _navigation = MutableStateFlow<SettingsRoute?>(null)
        val navigation: StateFlow<SettingsRoute?> = _navigation.asStateFlow()

        private var blockCount: Int = 0
        private var verified: Boolean = true
        private var stripeConnected: Boolean? = null
        private var isAdmin: Boolean = false

        fun load() {
            _state.value = GroupedListUiState.Loading
            val state = auth.state.value
            if (state is AuthRepository.State.SignedIn) {
                _footerCaption.value = "${state.user.email} · ID ${state.user.id.take(8)}"
                isAdmin = state.user.isAdmin
            }
            viewModelScope.launch {
                when (val blocks = privacy.blocks()) {
                    is NetworkResult.Success -> blockCount = blocks.data.blocks.size
                    else -> Unit
                }
                rebuild()
            }
        }

        fun consumeNavigation() {
            _navigation.value = null
        }

        fun onRow(rowId: String) {
            when (rowId) {
                "editProfile" -> _navigation.value = SettingsRoute.EditProfile
                "password" -> _navigation.value = SettingsRoute.Password
                "verification" -> _navigation.value = SettingsRoute.Verification
                "blocks" -> _navigation.value = SettingsRoute.Blocks
                "visibility" -> _navigation.value = SettingsRoute.IdentityCenter
                "notificationPreferences" -> _navigation.value = SettingsRoute.Notifications
                "export" -> _navigation.value = SettingsRoute.DataExport
                "paymentsPayouts" -> _navigation.value = SettingsRoute.PaymentsPayouts
                "help" -> _navigation.value = SettingsRoute.Help
                "legal" -> _navigation.value = SettingsRoute.Legal
                "about" -> _navigation.value = SettingsRoute.About
                "reviewClaims" -> _navigation.value = SettingsRoute.ReviewClaims
                "signOut" -> {
                    viewModelScope.launch {
                        auth.signOut()
                        _navigation.value = SettingsRoute.DidSignOut
                    }
                }
            }
        }

        private fun rebuild() {
            val verificationChip: RowControl =
                if (verified) {
                    RowControl.ChipStatus("Verified", RowControl.ChipTone.Success, includesChevron = true)
                } else {
                    RowControl.Chevron
                }
            val stripeChip: RowControl =
                if (stripeConnected == true) {
                    RowControl.ChipStatus("Stripe connected", RowControl.ChipTone.Success, includesChevron = true)
                } else {
                    RowControl.Chevron
                }
            val versionString = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            val groups =
                buildList {
                    add(
                        GroupedListGroup(
                            id = "account",
                            overline = "Account",
                            rows =
                                listOf(
                                    GroupedListRow(id = "editProfile", label = "Edit profile", control = RowControl.Chevron),
                                    GroupedListRow(id = "password", label = "Password", control = RowControl.Chevron),
                                    GroupedListRow(id = "verification", label = "Verification", control = verificationChip),
                                ),
                        ),
                    )
                    add(
                        GroupedListGroup(
                            id = "privacy",
                            overline = "Privacy",
                            rows =
                                listOf(
                                    GroupedListRow(
                                        id = "blocks",
                                        label = "Blocked users",
                                        subtext =
                                            if (blockCount > 0) {
                                                "$blockCount ${if (blockCount == 1) "person" else "people"}"
                                            } else {
                                                null
                                            },
                                        control = RowControl.Chevron,
                                    ),
                                    GroupedListRow(id = "visibility", label = "Profiles & Privacy", control = RowControl.Chevron),
                                    GroupedListRow(id = "export", label = "Data export", control = RowControl.Chevron),
                                ),
                        ),
                    )
                    add(
                        GroupedListGroup(
                            id = "notifications",
                            overline = "Notifications",
                            rows =
                                listOf(
                                    GroupedListRow(
                                        id = "notificationPreferences",
                                        label = "Notification preferences",
                                        subtext = "Push, email, SMS",
                                        control = RowControl.Chevron,
                                    ),
                                ),
                        ),
                    )
                    add(
                        GroupedListGroup(
                            id = "payments",
                            overline = "Payments",
                            rows = listOf(GroupedListRow(id = "paymentsPayouts", label = "Payments & payouts", control = stripeChip)),
                        ),
                    )
                    add(
                        GroupedListGroup(
                            id = "support",
                            overline = "Support",
                            rows =
                                listOf(
                                    GroupedListRow(id = "help", label = "Help", control = RowControl.Chevron),
                                    GroupedListRow(id = "legal", label = "Legal", control = RowControl.Chevron),
                                    GroupedListRow(
                                        id = "about",
                                        label = "About",
                                        subtext = versionString,
                                        control = RowControl.Chevron,
                                    ),
                                ),
                        ),
                    )
                    // P1.1 — admin-only Review-claims entry point. Sits
                    // after the Support group and before the Session
                    // group so the destructive Log-out row stays last.
                    if (isAdmin) {
                        add(
                            GroupedListGroup(
                                id = "admin",
                                overline = "Admin",
                                rows =
                                    listOf(
                                        GroupedListRow(
                                            id = "reviewClaims",
                                            label = "Review claims",
                                            subtext = "Home-ownership claim queue",
                                            control = RowControl.Chevron,
                                        ),
                                    ),
                            ),
                        )
                    }
                    add(
                        GroupedListGroup(
                            id = "session",
                            overline = null,
                            rows =
                                listOf(
                                    GroupedListRow(
                                        id = "signOut",
                                        label = "Log out",
                                        control = RowControl.Chevron,
                                        destructive = true,
                                    ),
                                ),
                        ),
                    )
                }
            _state.value = GroupedListUiState.Loaded(groups = groups)
        }
    }

// MARK: - Notification preferences

/**
 * P7.5 / A14.5 — Notification preferences. Reshaped from the old
 * channel-keyed toggle list into the design's three-channel matrix:
 * five category cards (Tasks · Pulse · Marketplace · Home & Mailbox ·
 * Account & security), each row carrying a [ChannelTriad] (Push /
 * Email / SMS). A Master card on top hosts the Pause-all toggle + a
 * Quiet-hours row; flipping Pause swaps the Master card for the amber
 * [PauseBanner] and dims the category cards to 0.5.
 *
 * Backend persistence is out of scope for P7.5 (mirrors A14.2 Home
 * security) — chips / toggles flip local state only. The helper lines
 * and channel patterns are the parity contract, mirrored word-for-word
 * in the iOS `NotificationSettingsViewModel`.
 */
@HiltViewModel
class NotificationSettingsViewModel
    @Inject
    constructor() : ViewModel() {
        enum class Variant { Populated, Paused }

        val title: String = "Notifications"

        /** Mono legend pinned at the bottom of the scroll. */
        val footerCaption: String = "P · Push   E · Email   S · SMS"

        private var isPaused: Boolean = false
        private val patterns: MutableMap<String, NotificationCatalog.Pattern> =
            NotificationCatalog.seed().toMutableMap()

        private val _state = MutableStateFlow<GroupedListUiState>(GroupedListUiState.Loading)
        val state: StateFlow<GroupedListUiState> = _state.asStateFlow()

        private val _banner = MutableStateFlow<GroupedListBanner?>(null)
        val banner: StateFlow<GroupedListBanner?> = _banner.asStateFlow()

        private val _dimmed = MutableStateFlow(false)
        val dimmed: StateFlow<Boolean> = _dimmed.asStateFlow()

        fun load() {
            rebuild()
        }

        /** Test / preview seam: boot straight into a variant frame. */
        fun setVariant(variant: Variant) {
            isPaused = variant == Variant.Paused
            patterns.clear()
            patterns.putAll(NotificationCatalog.seed())
            rebuild()
        }

        fun onToggle(
            rowId: String,
            isOn: Boolean,
        ) {
            if (rowId != NotificationCatalog.PAUSE_ALL) return
            isPaused = isOn
            rebuild()
        }

        fun onToggleChannel(
            rowId: String,
            channel: ChannelGlyph,
            isOn: Boolean,
        ) {
            val pattern = patterns[rowId] ?: return
            if (NotificationCatalog.lockedFor(rowId).contains(channel)) return
            patterns[rowId] =
                when (channel) {
                    ChannelGlyph.P -> pattern.copy(p = isOn)
                    ChannelGlyph.E -> pattern.copy(e = isOn)
                    ChannelGlyph.S -> pattern.copy(s = isOn)
                }
            rebuild()
        }

        /** Resume — clears the pause; the configured pattern comes back. */
        fun onTapBanner() {
            isPaused = false
            rebuild()
        }

        private fun rebuild() {
            _banner.value = if (isPaused) pauseBanner() else null
            _dimmed.value = isPaused
            _state.value = GroupedListUiState.Loaded(groups())
        }

        private fun groups(): List<GroupedListGroup> =
            buildList {
                if (!isPaused) add(masterGroup())
                NotificationCatalog.categories.forEach { add(categoryGroup(it)) }
            }

        private fun masterGroup(): GroupedListGroup =
            GroupedListGroup(
                id = "master",
                overline = "Master",
                helper = "Pause all silences every channel except emergency alerts. Quiet hours just delays them.",
                rows =
                    listOf(
                        GroupedListRow(
                            id = NotificationCatalog.PAUSE_ALL,
                            label = "Pause all notifications",
                            subtext = "Snooze everything but emergencies",
                            control = RowControl.Toggle(isPaused),
                        ),
                        GroupedListRow(
                            id = NotificationCatalog.QUIET_HOURS,
                            label = "Quiet hours",
                            subtext = "10:00 PM – 7:00 AM · Weekdays",
                            control = RowControl.ChipStatus("On", RowControl.ChipTone.Neutral, includesChevron = true),
                        ),
                    ),
            )

        private fun categoryGroup(category: NotificationCatalog.Category): GroupedListGroup =
            GroupedListGroup(
                id = category.id,
                overline = category.title,
                helper = category.helper,
                showsChannelHeader = true,
                rows =
                    category.rows.map { row ->
                        val pattern = patterns[row.id] ?: row.seed
                        GroupedListRow(
                            id = row.id,
                            label = row.label,
                            subtext = row.sub,
                            control =
                                RowControl.ChannelTriad(
                                    p = pattern.p,
                                    e = pattern.e,
                                    s = pattern.s,
                                    locked = NotificationCatalog.lockedFor(row.id),
                                ),
                        )
                    },
            )

        private fun pauseBanner(): GroupedListBanner =
            GroupedListBanner(
                icon = PantopusIcon.BellOff,
                title = "Paused for 2 hours",
                subtitle = "Resumes 11:42 AM · Emergency alerts still come through",
                actionLabel = "Resume",
            )
    }

/**
 * A14.5 notification catalog — the five category cards, their rows, seed
 * channel patterns, and locked channels. Top-level (mirror of the iOS
 * `NotificationSettingsViewModel` static data) so the view-model stays
 * lean. Copy + patterns here are the parity contract with iOS.
 */
internal object NotificationCatalog {
    const val PAUSE_ALL = "master.pauseAll"
    const val QUIET_HOURS = "master.quietHours"
    const val EMERGENCY = "home.emergency"

    data class Pattern(
        val p: Boolean,
        val e: Boolean,
        val s: Boolean,
    )

    data class RowSpec(
        val id: String,
        val label: String,
        val sub: String?,
        val seed: Pattern,
    )

    data class Category(
        val id: String,
        val title: String,
        val helper: String?,
        val rows: List<RowSpec>,
    )

    /** Channels that can't be muted — Emergency keeps push locked on. */
    fun lockedFor(rowId: String): Set<ChannelGlyph> =
        if (rowId == EMERGENCY) setOf(ChannelGlyph.P) else emptySet()

    fun seed(): Map<String, Pattern> = categories.flatMap { it.rows }.associate { it.id to it.seed }

    private fun spec(
        id: String,
        label: String,
        sub: String?,
        p: Boolean,
        e: Boolean,
        s: Boolean,
    ) = RowSpec(id, label, sub, Pattern(p, e, s))

    val categories: List<Category> =
        listOf(
            Category(
                id = "tasks",
                title = "Tasks",
                helper = "Push only for things that need a fast reply. Receipts go to email so they're searchable.",
                rows =
                    listOf(
                        spec("tasks.bids", "Bids on my tasks", "Within 5 minutes of posting", p = true, e = false, s = false),
                        spec("tasks.messages", "New messages", "From clients & taskers", p = true, e = true, s = false),
                        spec("tasks.status", "Status updates", "Accepted, on the way, done", p = true, e = false, s = false),
                        spec("tasks.receipts", "Payment receipts", null, p = false, e = true, s = false),
                    ),
            ),
            Category(
                id = "pulse",
                title = "Pulse",
                helper = "Pulse is quiet by default. Mentions break through, browsing doesn't.",
                rows =
                    listOf(
                        spec("pulse.replies", "Replies to my posts", null, p = true, e = false, s = false),
                        spec("pulse.mentions", "Mentions", "When a neighbor @s you", p = true, e = false, s = false),
                        spec("pulse.lostFound", "Nearby Lost & Found", "Within 0.5 mi of your address", p = false, e = false, s = false),
                        spec("pulse.digest", "Weekly digest", "Sundays, 8am", p = false, e = true, s = false),
                    ),
            ),
            Category(
                id = "marketplace",
                title = "Marketplace",
                helper = null,
                rows =
                    listOf(
                        spec("marketplace.offers", "Offers on my listings", null, p = true, e = true, s = false),
                        spec("marketplace.buyerMessages", "Buyer messages", null, p = true, e = false, s = false),
                        spec("marketplace.priceDrops", "Price drops on saved items", null, p = false, e = true, s = false),
                        spec("marketplace.expiring", "Listing expiring soon", "48h before auto-pause", p = false, e = true, s = false),
                    ),
            ),
            Category(
                id = "homeMailbox",
                title = "Home & Mailbox",
                helper = "Emergency alerts can't be muted on push.",
                rows =
                    listOf(
                        spec("home.package", "Package arrived", "When carrier scans \"delivered\"", p = true, e = true, s = true),
                        spec("home.member", "Member activity", "Check-ins, new passes, edits", p = true, e = false, s = false),
                        spec("home.civic", "Civic notices", "Permits, service alerts", p = true, e = true, s = false),
                        spec(EMERGENCY, "Emergency alerts", null, p = true, e = true, s = true),
                    ),
            ),
            Category(
                id = "accountSecurity",
                title = "Account & security",
                helper = "Security alerts always come through. You can choose how.",
                rows =
                    listOf(
                        spec("account.signIn", "New sign-in", null, p = true, e = true, s = true),
                        spec("account.verification", "Verification status", null, p = true, e = true, s = false),
                        spec("account.billing", "Billing & receipts", null, p = false, e = true, s = false),
                    ),
            ),
        )
}

// MARK: - Privacy

/**
 * P7.6 / A14.7 — Privacy preferences. Reshaped to the design's
 * full-vocabulary frame: two RadioCards (Profile visibility · Address
 * on profile), a "Map location fuzz" card hosting the `FuzzMap` stepped
 * slider, an Activity toggle card, and a "Your data" card of
 * leading-icon action rows + a detached destructive Delete row. A dark
 * `StealthBanner` rides above the first card in the stealth frame.
 *
 * Backend persistence is out of scope (mirrors A14.2 / A14.5) — the new
 * control set doesn't map onto the existing PrivacySettings fields, so
 * radios / toggles / fuzz flip local state only; data-export + delete
 * open placeholders. Copy is the parity contract, mirrored on iOS.
 */
@HiltViewModel
class PrivacySettingsViewModel
    @Inject
    constructor() : ViewModel() {
        enum class Variant { Populated, Stealth }

        val title: String = "Privacy"

        val footerCaption: String
            get() = if (isStealth) "Stealth · auto-applied May 26, 2026" else "Last updated · Mar 12, 2024"

        private var isStealth: Boolean = false
        private var visibility: String = "verified"
        private var address: String = "street"
        private var fuzz: FuzzStop = FuzzStop.BlockDefault
        private val activity: MutableMap<String, Boolean> = PrivacyCatalog.seedActivity(stealth = false).toMutableMap()

        private val _state = MutableStateFlow<GroupedListUiState>(GroupedListUiState.Loading)
        val state: StateFlow<GroupedListUiState> = _state.asStateFlow()

        private val _banner = MutableStateFlow<GroupedListBanner?>(null)
        val banner: StateFlow<GroupedListBanner?> = _banner.asStateFlow()

        fun load() {
            rebuild()
        }

        /** Test / preview seam: boot straight into a variant frame. */
        fun setVariant(variant: Variant) {
            isStealth = variant == Variant.Stealth
            visibility = if (isStealth) "hidden" else "verified"
            address = if (isStealth) "hidden" else "street"
            fuzz = if (isStealth) FuzzStop.Neighborhood else FuzzStop.BlockDefault
            activity.clear()
            activity.putAll(PrivacyCatalog.seedActivity(isStealth))
            rebuild()
        }

        fun onRadio(rowId: String) {
            when {
                rowId.startsWith("visibility.") -> visibility = rowId.removePrefix("visibility.")
                rowId.startsWith("address.") -> address = rowId.removePrefix("address.")
                else -> return
            }
            rebuild()
        }

        fun onToggle(
            rowId: String,
            isOn: Boolean,
        ) {
            if (!activity.containsKey(rowId)) return
            activity[rowId] = isOn
            rebuild()
        }

        fun onSetFuzz(
            rowId: String,
            stop: FuzzStop,
        ) {
            if (rowId != PrivacyCatalog.FUZZ) return
            fuzz = stop
            rebuild()
        }

        private fun rebuild() {
            _banner.value =
                if (isStealth) {
                    GroupedListBanner(
                        icon = PantopusIcon.EyeOff,
                        title = "Stealth mode is on",
                        subtitle = "Your profile is hidden from search. Existing connections still see you.",
                        style = GroupedListBanner.Style.Stealth,
                    )
                } else {
                    null
                }
            _state.value = GroupedListUiState.Loaded(groups())
        }

        private fun groups(): List<GroupedListGroup> =
            listOf(
                visibilityGroup(),
                addressGroup(),
                fuzzGroup(),
                activityGroup(),
                dataGroup(),
                deleteGroup(),
            )

        private fun visibilityGroup(): GroupedListGroup =
            GroupedListGroup(
                id = "visibility",
                overline = "Profile visibility",
                helper =
                    if (isStealth) {
                        "Hidden — your profile won't show in search or recommendations."
                    } else {
                        "Verified neighbors can find you and start a conversation."
                    },
                rows =
                    PrivacyCatalog.visibilityOptions.map { option ->
                        GroupedListRow(
                            id = "visibility.${option.key}",
                            label = option.label,
                            subtext = option.sub,
                            control = RowControl.Radio(option.key == visibility),
                        )
                    },
            )

        private fun addressGroup(): GroupedListGroup =
            GroupedListGroup(
                id = "address",
                overline = "Address on profile",
                helper =
                    if (isStealth) {
                        "Address hidden everywhere. Deliveries still route correctly."
                    } else {
                        "Street name shows on your profile; full address only to people you hire or sell to."
                    },
                rows =
                    PrivacyCatalog.addressOptions.map { option ->
                        GroupedListRow(
                            id = "address.${option.key}",
                            label = option.label,
                            subtext = option.sub,
                            control = RowControl.Radio(option.key == address),
                        )
                    },
            )

        private fun fuzzGroup(): GroupedListGroup =
            GroupedListGroup(
                id = PrivacyCatalog.FUZZ,
                overline = "Map location fuzz",
                helper =
                    if (isStealth) {
                        "Pins fuzz to your neighborhood — buyers see only \"Park Slope\", never your block."
                    } else {
                        "Pins drop within a block of you. Exact address only shared after a task is accepted."
                    },
                rows = emptyList(),
                fuzz =
                    GroupedListFuzz(
                        leadIn = "How exact your task and listing pins appear on the map.",
                        stop = fuzz,
                    ),
            )

        private fun activityGroup(): GroupedListGroup =
            GroupedListGroup(
                id = "activity",
                overline = "Activity",
                rows =
                    PrivacyCatalog.activitySpecs.map { spec ->
                        GroupedListRow(
                            id = spec.key,
                            label = spec.label,
                            subtext = spec.sub,
                            control = RowControl.Toggle(activity[spec.key] ?: false),
                        )
                    },
            )

        private fun dataGroup(): GroupedListGroup =
            GroupedListGroup(
                id = "data",
                overline = "Your data",
                rows =
                    listOf(
                        GroupedListRow(
                            id = "downloadData",
                            label = "Download your data",
                            subtext = "ZIP of profile, tasks, messages — emailed to you",
                            control = RowControl.Chevron,
                            leadingIcon = PantopusIcon.Download,
                        ),
                        GroupedListRow(
                            id = "whatWeCollect",
                            label = "What we collect",
                            subtext = "Full data policy & current categories",
                            control = RowControl.Chevron,
                            leadingIcon = PantopusIcon.FileText,
                        ),
                    ),
            )

        private fun deleteGroup(): GroupedListGroup =
            GroupedListGroup(
                id = "delete",
                rows =
                    listOf(
                        GroupedListRow(
                            id = "deleteAccount",
                            label = "Delete account",
                            subtext = "Permanent. 30-day grace period.",
                            control = RowControl.Chevron,
                            destructive = true,
                        ),
                    ),
            )
    }

/**
 * A14.7 privacy catalog — the radio options, activity specs, and seeds.
 * Top-level (mirror of the iOS static data) so the view-model stays
 * lean. Copy here is the parity contract with iOS.
 */
internal object PrivacyCatalog {
    const val FUZZ = "fuzz"

    data class Option(
        val key: String,
        val label: String,
        val sub: String?,
    )

    val visibilityOptions: List<Option> =
        listOf(
            Option("public", "Public", "Anyone with the link can see your profile"),
            Option("verified", "Verified neighbors only", "People with a verified address can see you"),
            Option("connections", "Connections only", "Only people you've interacted with"),
            Option("hidden", "Hidden", "Profile not browsable. Existing chats still work"),
        )

    val addressOptions: List<Option> =
        listOf(
            Option("full", "Full address", "14 Elm Park Lane, Brooklyn NY"),
            Option("street", "Street only", "Elm Park Lane, Brooklyn"),
            Option("neighborhood", "Neighborhood", "Park Slope, Brooklyn"),
            Option("hidden", "Hidden", "Verified badge shown, address not"),
        )

    val activitySpecs: List<Option> =
        listOf(
            Option("online", "Show online status", "Green dot when you're active"),
            Option("recent", "Show recent activity", "\"Posted a task 2h ago\" on profile"),
            Option("nearby", "Appear in nearby search", "Neighbors can find you by proximity"),
            Option("ratings", "Show ratings publicly", null),
        )

    fun seedActivity(stealth: Boolean): Map<String, Boolean> = activitySpecs.associate { it.key to !stealth }
}

