@file:Suppress("MagicNumber", "PackageNaming", "TooManyFunctions", "LongMethod")

package app.pantopus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.BuildConfig
import app.pantopus.android.data.api.models.settings.PrivacySettingsDto
import app.pantopus.android.data.api.models.settings.PrivacySettingsUpdate
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.privacy.PrivacyRepository
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListGroup
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListRow
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListUiState
import app.pantopus.android.ui.screens.shared.grouped_list.RowControl
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
                        // A14.3 — the design JSX titles the Help/Legal/About
                        // group "About" (the `id` stays "support" so routing +
                        // tests are unaffected).
                        GroupedListGroup(
                            id = "support",
                            overline = "About",
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

@HiltViewModel
class NotificationSettingsViewModel
    @Inject
    constructor(
        private val privacy: PrivacyRepository,
        private val auth: AuthRepository,
    ) : ViewModel() {
        val title: String = "Notifications"

        private val _state = MutableStateFlow<GroupedListUiState>(GroupedListUiState.Loading)
        val state: StateFlow<GroupedListUiState> = _state.asStateFlow()

        private var settings: PrivacySettingsDto? = null

        private val emailAddress: String?
            get() = (auth.state.value as? AuthRepository.State.SignedIn)?.user?.email

        fun load() {
            _state.value = GroupedListUiState.Loading
            viewModelScope.launch {
                when (val result = privacy.settings()) {
                    is NetworkResult.Success -> {
                        settings = result.data.settings
                        rebuild()
                    }
                    is NetworkResult.Failure -> {
                        _state.value = GroupedListUiState.Error("Couldn't load notification settings.")
                    }
                }
            }
        }

        fun onToggle(
            rowId: String,
            isOn: Boolean,
        ) {
            val parts = rowId.split(".")
            if (parts.size != 2) return
            val (channel, category) = parts
            val previous = preferenceValue(channel, category)
            viewModelScope.launch { applyToggle(channel, category, isOn, previous) }
        }

        private suspend fun applyToggle(
            channel: String,
            category: String,
            isOn: Boolean,
            rollbackTo: Boolean,
        ) {
            applyLocal(channel, category, isOn)
            val snapshot = preferenceMap(channel).toMutableMap().apply { put(category, isOn) }
            val update =
                when (channel) {
                    "push" -> PrivacySettingsUpdate(pushPreferences = snapshot)
                    "email" -> PrivacySettingsUpdate(emailPreferences = snapshot)
                    "sms" -> PrivacySettingsUpdate(smsPreferences = snapshot)
                    else -> return
                }
            when (val result = privacy.updateSettings(update)) {
                is NetworkResult.Success -> {
                    settings = result.data.settings
                    rebuild()
                }
                is NetworkResult.Failure -> {
                    applyLocal(channel, category, rollbackTo)
                }
            }
        }

        private fun applyLocal(
            channel: String,
            category: String,
            isOn: Boolean,
        ) {
            val map = preferenceMap(channel).toMutableMap().apply { put(category, isOn) }
            settings = settings?.updating(channel, map)
            rebuild()
        }

        private fun preferenceValue(
            channel: String,
            category: String,
        ): Boolean = preferenceMap(channel)[category] ?: (channel == "push")

        private fun preferenceMap(channel: String): Map<String, Boolean> =
            when (channel) {
                "push" -> settings?.pushPreferences ?: defaults(channel)
                "email" -> settings?.emailPreferences ?: defaults(channel)
                "sms" -> settings?.smsPreferences ?: defaults(channel)
                else -> emptyMap()
            }

        private fun defaults(channel: String): Map<String, Boolean> {
            val defaultOn = channel == "push"
            return Categories.associateWith { defaultOn }
        }

        private fun rebuild() {
            val pushRows =
                Categories.map { category ->
                    GroupedListRow(
                        id = "push.$category",
                        label = category.replaceFirstChar { it.uppercase() },
                        control = RowControl.Toggle(preferenceValue("push", category)),
                    )
                }
            val emailRows =
                Categories.map { category ->
                    GroupedListRow(
                        id = "email.$category",
                        label = category.replaceFirstChar { it.uppercase() },
                        control = RowControl.Toggle(preferenceValue("email", category)),
                    )
                }
            val smsRows =
                Categories.map { category ->
                    GroupedListRow(
                        id = "sms.$category",
                        label = category.replaceFirstChar { it.uppercase() },
                        control = RowControl.Toggle(preferenceValue("sms", category)),
                    )
                }
            val emailHelper =
                emailAddress?.let { "Sent to $it. Digest at 7:30 a.m. local." }
                    ?: "Sent to your account email. Digest at 7:30 a.m. local."
            _state.value =
                GroupedListUiState.Loaded(
                    groups =
                        listOf(
                            GroupedListGroup(
                                id = "push",
                                overline = "Push",
                                helper = "Receive on this device. Sounds and badges follow system settings.",
                                rows = pushRows,
                            ),
                            GroupedListGroup(
                                id = "email",
                                overline = "Email",
                                helper = emailHelper,
                                rows = emailRows,
                            ),
                            GroupedListGroup(
                                id = "sms",
                                overline = "SMS",
                                helper = "Carrier rates may apply.",
                                rows = smsRows,
                            ),
                        ),
                )
        }

        companion object {
            internal val Categories = listOf("messages", "gigs", "listings", "mailbox", "home")
        }
    }

// MARK: - Privacy

@HiltViewModel
class PrivacySettingsViewModel
    @Inject
    constructor(
        private val privacy: PrivacyRepository,
    ) : ViewModel() {
        val title: String = "Privacy"

        private val _state = MutableStateFlow<GroupedListUiState>(GroupedListUiState.Loading)
        val state: StateFlow<GroupedListUiState> = _state.asStateFlow()

        private var settings: PrivacySettingsDto? = null

        fun load() {
            _state.value = GroupedListUiState.Loading
            viewModelScope.launch {
                when (val result = privacy.settings()) {
                    is NetworkResult.Success -> {
                        settings = result.data.settings
                        rebuild()
                    }
                    is NetworkResult.Failure -> {
                        _state.value = GroupedListUiState.Error("Couldn't load privacy settings.")
                    }
                }
            }
        }

        fun onRadio(rowId: String) {
            if (!rowId.startsWith("visibility.")) return
            val value = rowId.removePrefix("visibility.")
            viewModelScope.launch {
                persist(PrivacySettingsUpdate(searchVisibility = value)) { it.copy(searchVisibility = value) }
            }
        }

        fun onSlider(
            rowId: String,
            index: Int,
        ) {
            if (rowId != "addressPrecision" || index !in PrecisionStops.indices) return
            val value = PrecisionStops[index].lowercase()
            viewModelScope.launch {
                persist(PrivacySettingsUpdate(addressPrecision = value)) { it.copy(addressPrecision = value) }
            }
        }

        fun onToggle(
            rowId: String,
            isOn: Boolean,
        ) {
            viewModelScope.launch {
                when (rowId) {
                    "hideFromSearch" ->
                        persist(PrivacySettingsUpdate(hideFromSearch = isOn)) { it.copy(hideFromSearch = isOn) }
                    "showOnlineStatus" ->
                        persist(PrivacySettingsUpdate(showOnlineStatus = isOn)) { it.copy(showOnlineStatus = isOn) }
                    "showLastActive" ->
                        persist(PrivacySettingsUpdate(showLastActive = isOn)) { it.copy(showLastActive = isOn) }
                    "showReadReceipts" ->
                        persist(PrivacySettingsUpdate(showReadReceipts = isOn)) { it.copy(showReadReceipts = isOn) }
                    "shareHomeCheckIns" ->
                        persist(PrivacySettingsUpdate(shareHomeCheckIns = isOn)) { it.copy(shareHomeCheckIns = isOn) }
                }
            }
        }

        private suspend fun persist(
            update: PrivacySettingsUpdate,
            applyLocal: (PrivacySettingsDto) -> PrivacySettingsDto,
        ) {
            val previous = settings
            settings?.let {
                settings = applyLocal(it)
                rebuild()
            }
            when (val result = privacy.updateSettings(update)) {
                is NetworkResult.Success -> {
                    settings = result.data.settings
                    rebuild()
                }
                is NetworkResult.Failure -> {
                    settings = previous
                    rebuild()
                }
            }
        }

        private fun rebuild() {
            val current = settings ?: PrivacySettingsDto()
            val currentVisibility = current.searchVisibility ?: "verified"
            val visibilityRows =
                VisibilityOptions.map { option ->
                    GroupedListRow(
                        id = "visibility.${option.id}",
                        label = option.label,
                        subtext = option.sub,
                        control = RowControl.Radio(option.id == currentVisibility),
                    )
                }
            val precisionValue = current.addressPrecision ?: "street"
            val precisionIndex = PrecisionStops.indexOfFirst { it.lowercase() == precisionValue }.coerceAtLeast(1)
            val precisionRow =
                GroupedListRow(
                    id = "addressPrecision",
                    label = "Precision · ${precisionValue.replaceFirstChar { it.uppercase() }}",
                    subtext = "How precisely Pantopus shares your address with verified connections.",
                    control = RowControl.Slider(PrecisionStops, precisionIndex),
                )
            val hideRow =
                GroupedListRow(
                    id = "hideFromSearch",
                    label = "Hide from search results",
                    subtext = "Your address won't appear in neighbor searches.",
                    control = RowControl.Toggle(current.hideFromSearch ?: false),
                )
            val activityRows =
                listOf(
                    GroupedListRow(
                        id = "showOnlineStatus",
                        label = "Show online status",
                        control = RowControl.Toggle(current.showOnlineStatus ?: true),
                    ),
                    GroupedListRow(
                        id = "showLastActive",
                        label = "Show last active time",
                        control = RowControl.Toggle(current.showLastActive ?: false),
                    ),
                    GroupedListRow(
                        id = "showReadReceipts",
                        label = "Show read receipts",
                        subtext = "In direct messages only",
                        control = RowControl.Toggle(current.showReadReceipts ?: true),
                    ),
                    GroupedListRow(
                        id = "shareHomeCheckIns",
                        label = "Share home check-ins",
                        control = RowControl.Toggle(current.shareHomeCheckIns ?: false),
                    ),
                )
            _state.value =
                GroupedListUiState.Loaded(
                    groups =
                        listOf(
                            GroupedListGroup(
                                id = "visibility",
                                overline = "Profile visibility",
                                helper = "Choose who can find and view your profile.",
                                rows = visibilityRows,
                            ),
                            GroupedListGroup(
                                id = "address",
                                overline = "Address sharing",
                                rows = listOf(precisionRow, hideRow),
                            ),
                            GroupedListGroup(
                                id = "activity",
                                overline = "Activity",
                                helper = "Controls what your verified connections can see about your activity.",
                                rows = activityRows,
                            ),
                        ),
                )
        }

        companion object {
            data class Option(val id: String, val label: String, val sub: String)

            internal val VisibilityOptions =
                listOf(
                    Option("anyone", "Anyone", "Everyone on Pantopus can see your profile."),
                    Option("verified", "Verified connections only", "Only verified neighbors and people you follow."),
                    Option("none", "No one", "Your profile is hidden from search and discovery."),
                )

            internal val PrecisionStops = listOf("Exact", "Street", "Block", "Neighborhood")
        }
    }

// MARK: - Helpers

internal fun PrivacySettingsDto.updating(
    channel: String,
    map: Map<String, Boolean>,
): PrivacySettingsDto =
    when (channel) {
        "push" -> copy(pushPreferences = map)
        "email" -> copy(emailPreferences = map)
        "sms" -> copy(smsPreferences = map)
        else -> this
    }
