@file:Suppress("PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.homes.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListGroup
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListRow
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListUiState
import app.pantopus.android.ui.screens.shared.grouped_list.RowControl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Nav key carrying the home id into the per-home Settings stack. */
const val HOME_SETTINGS_HOME_ID_KEY = "homeId"

/**
 * Sentinel routes the per-home Settings index can ask its host to
 * push. Mirrors the iOS `HomeSettingsRoute` enum.
 */
enum class HomeSettingsRoute {
    Address,
    PropertyDetails,
    Photos,
    Documents,
    AccessCodes,
    TrustedNeighbors,
    Security,
    People,
    InviteLink,
    HomeNotifications,
    LeaveHome,
    CancelClaim,
}

@HiltViewModel
class HomeSettingsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val title: String = "Home settings"

        val homeId: String =
            requireNotNull(savedStateHandle[HOME_SETTINGS_HOME_ID_KEY]) {
                "HomeSettingsViewModel requires a '$HOME_SETTINGS_HOME_ID_KEY' nav arg."
            }

        val frame: HomeSettingsSampleData.Frame = HomeSettingsSampleData.frameForHomeId(homeId)

        val identity: HomeSettingsSampleData.Identity = HomeSettingsSampleData.identity(frame)

        val footerCaption: String = HomeSettingsSampleData.footer(frame)

        private val _state = MutableStateFlow<GroupedListUiState>(GroupedListUiState.Loading)
        val state: StateFlow<GroupedListUiState> = _state.asStateFlow()

        private val _navigation = MutableStateFlow<HomeSettingsRoute?>(null)
        val navigation: StateFlow<HomeSettingsRoute?> = _navigation.asStateFlow()

        fun load() {
            _state.value = GroupedListUiState.Loaded(groups())
        }

        fun consumeNavigation() {
            _navigation.value = null
        }

        fun onRow(rowId: String) {
            _navigation.value =
                when (rowId) {
                    "address" -> HomeSettingsRoute.Address
                    "propertyDetails" -> HomeSettingsRoute.PropertyDetails
                    "photos" -> HomeSettingsRoute.Photos
                    "documents" -> HomeSettingsRoute.Documents
                    "accessCodes" -> HomeSettingsRoute.AccessCodes
                    "trustedNeighbors" -> HomeSettingsRoute.TrustedNeighbors
                    "privacy" -> HomeSettingsRoute.Security
                    "people" -> HomeSettingsRoute.People
                    "inviteLink" -> HomeSettingsRoute.InviteLink
                    "homeNotifications" -> HomeSettingsRoute.HomeNotifications
                    "leaveHome" -> HomeSettingsRoute.LeaveHome
                    "cancelClaim" -> HomeSettingsRoute.CancelClaim
                    else -> null
                }
        }

        // Group projection — mirror of iOS `HomeSettingsViewModel.groups()`.

        private fun groups(): List<GroupedListGroup> =
            listOf(
                homeIdentityGroup(),
                accessGroup(),
                membersGroup(),
                notificationsGroup(),
                windDownGroup(),
            )

        private fun homeIdentityGroup(): GroupedListGroup {
            val addressControl =
                RowControl.ChipStatus(
                    label = identity.addressChipLabel,
                    tone = identity.addressChipTone,
                    includesChevron = true,
                )
            val rows =
                when (frame) {
                    HomeSettingsSampleData.Frame.Populated ->
                        listOf(
                            GroupedListRow("address", "Address", subtext = "14 Elm Park Lane", control = addressControl),
                            GroupedListRow(
                                "propertyDetails",
                                "Property details",
                                subtext = "3 bed · 2 bath · Built 1998",
                                control = RowControl.Chevron,
                            ),
                            GroupedListRow(
                                "photos",
                                "Photos",
                                subtext = "Front porch · added Mar 2024",
                                control = RowControl.Chevron,
                            ),
                            GroupedListRow(
                                "documents",
                                "Documents",
                                subtext = "Lease, HOA, Tax",
                                control = RowControl.Chevron,
                            ),
                        )
                    HomeSettingsSampleData.Frame.Pending ->
                        listOf(
                            GroupedListRow("address", "Address", subtext = "42 Magnolia Court", control = addressControl),
                            GroupedListRow(
                                "propertyDetails",
                                "Property details",
                                subtext = "Not set",
                                control = RowControl.Chevron,
                            ),
                            GroupedListRow(
                                "photos",
                                "Photos",
                                subtext = "Add a photo",
                                control = RowControl.Chevron,
                            ),
                            GroupedListRow(
                                "documents",
                                "Documents",
                                subtext = "Available after verification",
                                control = RowControl.Chevron,
                            ),
                        )
                }
            return GroupedListGroup(id = "homeIdentity", overline = "Home identity", rows = rows)
        }

        private fun accessGroup(): GroupedListGroup {
            val rows =
                when (frame) {
                    HomeSettingsSampleData.Frame.Populated ->
                        listOf(
                            GroupedListRow("accessCodes", "Access codes", subtext = "2 active codes", control = RowControl.Chevron),
                            GroupedListRow("trustedNeighbors", "Trusted neighbors", subtext = "3 approved", control = RowControl.Chevron),
                            GroupedListRow("privacy", "Privacy", subtext = "Verified neighbors only", control = RowControl.Chevron),
                        )
                    HomeSettingsSampleData.Frame.Pending ->
                        listOf(
                            GroupedListRow("accessCodes", "Access codes", subtext = "Not set", control = RowControl.Chevron),
                            GroupedListRow(
                                "trustedNeighbors",
                                "Trusted neighbors",
                                subtext = "Available after verification",
                                control = RowControl.Chevron,
                            ),
                            GroupedListRow(
                                "privacy",
                                "Privacy",
                                subtext = "Available after verification",
                                control = RowControl.Chevron,
                            ),
                        )
                }
            return GroupedListGroup(id = "access", overline = "Access", rows = rows)
        }

        private fun membersGroup(): GroupedListGroup {
            val rows =
                when (frame) {
                    HomeSettingsSampleData.Frame.Populated ->
                        listOf(
                            GroupedListRow("people", "People", subtext = "4 members · 1 pending", control = RowControl.Chevron),
                            GroupedListRow(
                                "inviteLink",
                                "Invite link",
                                subtext = "Active · expires in 12 days",
                                control = RowControl.Chevron,
                            ),
                        )
                    HomeSettingsSampleData.Frame.Pending ->
                        listOf(
                            GroupedListRow("people", "People", subtext = "Just you", control = RowControl.Chevron),
                            GroupedListRow(
                                "inviteLink",
                                "Invite link",
                                subtext = "Available after verification",
                                control = RowControl.Chevron,
                            ),
                        )
                }
            return GroupedListGroup(id = "members", overline = "Members", rows = rows)
        }

        private fun notificationsGroup(): GroupedListGroup {
            val sub =
                when (frame) {
                    HomeSettingsSampleData.Frame.Populated -> "Push, email digest"
                    HomeSettingsSampleData.Frame.Pending -> "Default"
                }
            return GroupedListGroup(
                id = "notifications",
                overline = "Notifications",
                rows =
                    listOf(
                        GroupedListRow("homeNotifications", "Home notifications", subtext = sub, control = RowControl.Chevron),
                    ),
            )
        }

        private fun windDownGroup(): GroupedListGroup {
            val row =
                when (frame) {
                    HomeSettingsSampleData.Frame.Populated ->
                        GroupedListRow("leaveHome", "Leave this home", control = RowControl.Chevron, destructive = true)
                    HomeSettingsSampleData.Frame.Pending ->
                        GroupedListRow("cancelClaim", "Cancel claim", control = RowControl.Chevron, destructive = true)
                }
            return GroupedListGroup(id = "windDown", overline = "Wind down", rows = listOf(row))
        }
    }
