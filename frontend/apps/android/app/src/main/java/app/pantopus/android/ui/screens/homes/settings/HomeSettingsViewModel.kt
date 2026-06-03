@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.HomeDetail
import app.pantopus.android.data.api.models.homes.OccupantsResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomeMembersRepository
import app.pantopus.android.data.homes.HomesRepository
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

/**
 * P5.1 / A14.1 / Block 2A — per-home Settings index. A NAVIGATION index
 * (chevron rows routing to Address / Photos / People / … sub-screens),
 * not a settings form — there is no settings `PATCH`.
 *
 * Wiring: fetches the real home (`GET /:id`) so the identity card shows
 * `home.name` + a verification chip derived from the claim state, and
 * the People row's subtext reflects the real member / pending counts
 * from the same `GET /:id/occupants` the Members screen uses. Rows with
 * no backend source are left bare rather than faked. Sample frames in
 * [HomeSettingsSampleData] back the previews + Paparazzi baselines.
 */
@HiltViewModel
class HomeSettingsViewModel
    @Inject
    constructor(
        private val homesRepository: HomesRepository,
        private val homeMembersRepository: HomeMembersRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val title: String = "Home settings"

        val homeId: String =
            requireNotNull(savedStateHandle[HOME_SETTINGS_HOME_ID_KEY]) {
                "HomeSettingsViewModel requires a '$HOME_SETTINGS_HOME_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<GroupedListUiState>(GroupedListUiState.Loading)
        val state: StateFlow<GroupedListUiState> = _state.asStateFlow()

        private val _identity =
            MutableStateFlow(HomeSettingsSampleData.identity(HomeSettingsSampleData.Frame.Populated))
        val identity: StateFlow<HomeSettingsSampleData.Identity> = _identity.asStateFlow()

        private val _footerCaption = MutableStateFlow<String?>(null)
        val footerCaption: StateFlow<String?> = _footerCaption.asStateFlow()

        private val _navigation = MutableStateFlow<HomeSettingsRoute?>(null)
        val navigation: StateFlow<HomeSettingsRoute?> = _navigation.asStateFlow()

        private var frame: HomeSettingsSampleData.Frame = HomeSettingsSampleData.Frame.Populated
        private var subtexts = RowSubtexts()
        private var loadedOnce = false

        fun load() {
            if (loadedOnce) return
            reload()
        }

        fun refresh() = reload()

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

        private fun reload() {
            _state.value = GroupedListUiState.Loading
            viewModelScope.launch {
                when (val result = homesRepository.detail(homeId)) {
                    is NetworkResult.Success -> {
                        // Member counts are best-effort — a roster failure
                        // still lets the identity card + navigation render.
                        val occupants =
                            (homeMembersRepository.listOccupants(homeId) as? NetworkResult.Success)?.data
                        apply(result.data.home, occupants)
                        loadedOnce = true
                        _state.value = GroupedListUiState.Loaded(groups())
                    }
                    is NetworkResult.Failure -> {
                        _state.value = GroupedListUiState.Error(result.error.message)
                    }
                }
            }
        }

        private fun apply(
            detail: HomeDetail,
            occupants: OccupantsResponse?,
        ) {
            val isPending = detail.isPendingOwner || detail.pendingClaimId != null
            frame = if (isPending) HomeSettingsSampleData.Frame.Pending else HomeSettingsSampleData.Frame.Populated

            val homeName =
                detail.name?.takeIf { it.isNotBlank() }
                    ?: detail.address?.takeIf { it.isNotBlank() }
                    ?: "This home"
            _identity.value =
                HomeSettingsSampleData.Identity(
                    homeName = homeName,
                    addressChipLabel = if (isPending) "Verifying" else "Verified",
                    addressChipTone = if (isPending) RowControl.ChipTone.Warning else RowControl.ChipTone.Success,
                )
            _footerCaption.value = "$homeName · ${if (isPending) "Claim pending" else "Owner"}"
            subtexts =
                RowSubtexts(
                    address = addressLine(detail),
                    propertyDetails = humanizedHomeType(detail.homeType),
                    people = peopleSubtext(occupants),
                )
        }

        private fun addressLine(detail: HomeDetail): String? {
            val street = detail.address?.takeIf { it.isNotBlank() } ?: return null
            val city = detail.city?.takeIf { it.isNotBlank() }
            return if (city != null) "$street, $city" else street
        }

        private fun humanizedHomeType(raw: String?): String? {
            val value = raw?.takeIf { it.isNotBlank() } ?: return null
            return value
                .replace('_', ' ')
                .replace('-', ' ')
                .split(' ')
                .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        }

        private fun peopleSubtext(occupants: OccupantsResponse?): String? {
            occupants ?: return null
            val members = occupants.occupants.size
            val pending = occupants.pendingInvites.size
            val memberLabel = if (members == 1) "1 member" else "$members members"
            if (pending == 0) return memberLabel
            val pendingLabel = if (pending == 1) "1 pending" else "$pending pending"
            return "$memberLabel · $pendingLabel"
        }

        // Group projection — structure mirrors iOS `groups()`; subtexts are
        // resolved from live data (or left null when no endpoint backs them).

        private fun groups(): List<GroupedListGroup> =
            listOf(
                homeIdentityGroup(),
                accessGroup(),
                membersGroup(),
                notificationsGroup(),
                windDownGroup(),
            )

        private fun homeIdentityGroup(): GroupedListGroup {
            val identity = _identity.value
            val addressControl =
                RowControl.ChipStatus(
                    label = identity.addressChipLabel,
                    tone = identity.addressChipTone,
                    includesChevron = true,
                )
            return GroupedListGroup(
                id = "homeIdentity",
                overline = "Home identity",
                rows =
                    listOf(
                        GroupedListRow("address", "Address", subtext = subtexts.address, control = addressControl),
                        GroupedListRow("propertyDetails", "Property details", subtext = subtexts.propertyDetails, control = RowControl.Chevron),
                        GroupedListRow("photos", "Photos", subtext = subtexts.photos, control = RowControl.Chevron),
                        GroupedListRow("documents", "Documents", subtext = subtexts.documents, control = RowControl.Chevron),
                    ),
            )
        }

        private fun accessGroup(): GroupedListGroup =
            GroupedListGroup(
                id = "access",
                overline = "Access",
                rows =
                    listOf(
                        GroupedListRow("accessCodes", "Access codes", subtext = subtexts.accessCodes, control = RowControl.Chevron),
                        GroupedListRow("trustedNeighbors", "Trusted neighbors", subtext = subtexts.trustedNeighbors, control = RowControl.Chevron),
                        GroupedListRow("privacy", "Privacy", subtext = subtexts.privacy, control = RowControl.Chevron),
                    ),
            )

        private fun membersGroup(): GroupedListGroup =
            GroupedListGroup(
                id = "members",
                overline = "Members",
                rows =
                    listOf(
                        GroupedListRow("people", "People", subtext = subtexts.people, control = RowControl.Chevron),
                        GroupedListRow("inviteLink", "Invite link", subtext = subtexts.inviteLink, control = RowControl.Chevron),
                    ),
            )

        private fun notificationsGroup(): GroupedListGroup =
            GroupedListGroup(
                id = "notifications",
                overline = "Notifications",
                rows =
                    listOf(
                        GroupedListRow("homeNotifications", "Home notifications", subtext = subtexts.notifications, control = RowControl.Chevron),
                    ),
            )

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

/**
 * Row subtexts resolved for the active frame. Live data fills only the
 * slots a real endpoint backs (address, property type, people counts);
 * the rest stay null so the row renders bare rather than faked.
 */
private data class RowSubtexts(
    val address: String? = null,
    val propertyDetails: String? = null,
    val photos: String? = null,
    val documents: String? = null,
    val accessCodes: String? = null,
    val trustedNeighbors: String? = null,
    val privacy: String? = null,
    val people: String? = null,
    val inviteLink: String? = null,
    val notifications: String? = null,
)
