@file:Suppress("PackageNaming", "TooManyFunctions", "LongMethod")

package app.pantopus.android.ui.screens.homes.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.InvitationDto
import app.pantopus.android.data.api.models.homes.InviteMemberRequest
import app.pantopus.android.data.api.models.homes.OccupantDto
import app.pantopus.android.data.api.models.homes.PendingInviteDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomeMembersRepository
import app.pantopus.android.ui.screens.shared.list_of_rows.AvatarBackground
import app.pantopus.android.ui.screens.shared.list_of_rows.AvatarBadgeSize
import app.pantopus.android.ui.screens.shared.list_of_rows.CompactButtonVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.FabAction
import app.pantopus.android.ui.screens.shared.list_of_rows.FabTint
import app.pantopus.android.ui.screens.shared.list_of_rows.FabVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsTab
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowChip
import app.pantopus.android.ui.screens.shared.list_of_rows.RowLeading
import app.pantopus.android.ui.screens.shared.list_of_rows.RowModel
import app.pantopus.android.ui.screens.shared.list_of_rows.RowSection
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTemplate
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTrailing
import app.pantopus.android.ui.screens.shared.list_of_rows.VerticalAction
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

/** Nav arg key for the home id consumed via [SavedStateHandle]. */
const val MEMBERS_LIST_HOME_ID_KEY = "homeId"

/** Stable tab ids — exposed for the screen + tests. */
object MembersTab {
    const val MEMBERS = "members"
    const val GUESTS = "guests"
    const val PENDING = "pending"
}

/**
 * Surfaced to the screen so it can present sheets / confirms in
 * response to row interactions without the VM holding view state.
 */
sealed interface MembersListEvent {
    data object OpenInvite : MembersListEvent

    /** A13.1 — open the Add Guest form from the Guests tab. */
    data object OpenAddGuest : MembersListEvent

    data class ConfirmRemove(
        val userId: String,
        val name: String,
    ) : MembersListEvent
}

/**
 * Drives the T6.3a / P9 Members per-home roster. Reads
 * `GET /api/homes/:id/occupants` (members + pending invites in one
 * payload), buckets client-side into Members / Guests / Pending tabs,
 * and projects rows via the shared `ListOfRows` archetype.
 *
 * Mirrors iOS `MembersListViewModel` exactly — same tab ids, same
 * row mapping, same optimistic remove + cancel-invite rollback.
 */
@HiltViewModel
class MembersListViewModel
    @Inject
    constructor(
        private val repo: HomeMembersRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val homeId: String = savedStateHandle[MEMBERS_LIST_HOME_ID_KEY] ?: ""

        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private val _selectedTab = MutableStateFlow(MembersTab.MEMBERS)
        val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

        private var occupants: List<OccupantDto> = emptyList()
        private var pendingInvites: List<PendingInviteDto> = emptyList()
        private var loadedOnce = false

        private val _tabs = MutableStateFlow(makeTabs())
        val tabs: StateFlow<List<ListOfRowsTab>> = _tabs.asStateFlow()

        private val _pendingEvent = MutableStateFlow<MembersListEvent?>(null)
        val pendingEvent: StateFlow<MembersListEvent?> = _pendingEvent.asStateFlow()

        /** 52dp home-green secondary-create FAB. Contextual on Guests:
         *  issue a guest pass; otherwise invite a household member. */
        val fab: FabAction
            get() =
                if (_selectedTab.value == MembersTab.GUESTS) {
                    FabAction(
                        icon = PantopusIcon.UserPlus,
                        contentDescription = "Add guest",
                        variant = FabVariant.SecondaryCreate,
                        tint = FabTint.Home,
                        onClick = ::requestAddGuest,
                    )
                } else {
                    FabAction(
                        icon = PantopusIcon.UserPlus,
                        contentDescription = "Invite member",
                        variant = FabVariant.SecondaryCreate,
                        tint = FabTint.Home,
                        onClick = ::requestInvite,
                    )
                }

        /** Idempotent — re-running won't refetch once content is loaded. */
        fun load() {
            if (loadedOnce) return
            reload()
        }

        /** Pull-to-refresh / retry. */
        fun refresh() = reload()

        /** Backend doesn't paginate /occupants. */
        fun loadMoreIfNeeded() = Unit

        /** Tab switch — re-segment over cached state. */
        fun selectTab(id: String) {
            if (_selectedTab.value == id) return
            _selectedTab.value = id
            applyState()
        }

        /** Screen calls this after dispatching a pending event. */
        fun acknowledgeEvent() {
            _pendingEvent.value = null
        }

        /** Fired by the FAB / empty-state CTA. */
        fun requestInvite() {
            _pendingEvent.value = MembersListEvent.OpenInvite
        }

        /** Fired by the Guests-tab FAB / empty-state CTA. */
        fun requestAddGuest() {
            _pendingEvent.value = MembersListEvent.OpenAddGuest
        }

        /**
         * Fold a freshly-created invite into the Pending bucket so the
         * user sees the new row without waiting for a refetch.
         */
        fun handleInvited(invitation: InvitationDto) {
            val invite =
                PendingInviteDto(
                    id = invitation.id,
                    userId = invitation.inviteeUserId,
                    role = invitation.proposedRole,
                    email = invitation.inviteeEmail,
                    name = invitation.inviteeEmail ?: "Invited user",
                    invitedBy = null,
                    createdAt = invitation.createdAt,
                )
            pendingInvites = listOf(invite) + pendingInvites
            applyState()
        }

        /**
         * Optimistic remove with rollback on failure. The confirm dialog
         * has already fired by the time this is invoked.
         */
        fun remove(userId: String) {
            val previous = occupants
            occupants = previous.filterNot { it.userId == userId }
            applyState()
            viewModelScope.launch {
                when (repo.remove(homeId, userId)) {
                    is NetworkResult.Success -> Unit
                    is NetworkResult.Failure -> {
                        occupants = previous
                        applyState()
                    }
                }
            }
        }

        /**
         * Optimistic cancel-invite. The backend lacks a dedicated cancel
         * endpoint today, so for invites with a resolved `user_id` we
         * use the same DELETE …/members/:userId route; for open invites
         * (no user id) we just drop the row optimistically and let the
         * backend reconcile via expiry.
         */
        fun cancelInvite(inviteId: String) {
            val invite = pendingInvites.firstOrNull { it.id == inviteId } ?: return
            val previous = pendingInvites
            pendingInvites = previous.filterNot { it.id == inviteId }
            applyState()
            val userId = invite.userId ?: return
            viewModelScope.launch {
                when (repo.remove(homeId, userId)) {
                    is NetworkResult.Success -> Unit
                    is NetworkResult.Failure -> {
                        pendingInvites = previous
                        applyState()
                    }
                }
            }
        }

        /**
         * Re-issues the invite via POST /:id/invite with the same email
         * + role. Optimistic — no state change locally.
         */
        fun resendInvite(inviteId: String) {
            val invite = pendingInvites.firstOrNull { it.id == inviteId } ?: return
            val request =
                InviteMemberRequest(
                    email = invite.email,
                    userId = invite.userId,
                    relationship = invite.role ?: MemberRole.Member.wire,
                    message = null,
                )
            viewModelScope.launch {
                // Fire-and-forget — we don't surface success/failure to UI
                // until the design adds a toast/snackbar slot for the
                // Members screen.
                repo.invite(homeId, request)
            }
        }

        private fun reload() {
            _state.value = ListOfRowsUiState.Loading
            viewModelScope.launch {
                when (val result = repo.listOccupants(homeId)) {
                    is NetworkResult.Success -> {
                        occupants = result.data.occupants.filter { it.isActive }
                        pendingInvites = result.data.pendingInvites
                        loadedOnce = true
                        applyState()
                    }
                    is NetworkResult.Failure -> {
                        _state.value = ListOfRowsUiState.Error(result.error.message)
                    }
                }
            }
        }

        // ─── Buckets ──────────────────────────────────────────────

        private fun membersBucket(): List<OccupantDto> = occupants.filter { MemberRole.parse(it.role) !in MemberRole.guestRoles }

        private fun guestsBucket(): List<OccupantDto> = occupants.filter { MemberRole.parse(it.role) in MemberRole.guestRoles }

        private fun makeTabs(): List<ListOfRowsTab> =
            listOf(
                ListOfRowsTab(id = MembersTab.MEMBERS, label = "Members", count = membersBucket().size),
                ListOfRowsTab(id = MembersTab.GUESTS, label = "Guests", count = guestsBucket().size),
                ListOfRowsTab(id = MembersTab.PENDING, label = "Pending", count = pendingInvites.size),
            )

        // ─── State projection ─────────────────────────────────────

        private fun applyState() {
            _tabs.value = makeTabs()
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val rows: List<RowModel> =
                when (_selectedTab.value) {
                    MembersTab.GUESTS -> guestsBucket().map { rowForOccupant(it, now, zone) }
                    MembersTab.PENDING -> pendingInvites.map { rowForPending(it, now, zone) }
                    else -> membersBucket().map { rowForOccupant(it, now, zone) }
                }
            if (rows.isEmpty()) {
                _state.value = emptyState(_selectedTab.value)
                return
            }
            val section = RowSection(id = _selectedTab.value, rows = rows)
            _state.value = ListOfRowsUiState.Loaded(sections = listOf(section), hasMore = false)
        }

        private fun emptyState(tab: String): ListOfRowsUiState.Empty =
            when (tab) {
                MembersTab.GUESTS ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Users,
                        headline = "No active guests",
                        subcopy = "Add someone short-term — a sitter, visitor, or contractor — to share access while they're around.",
                        ctaTitle = "Add a guest",
                        onCta = ::requestAddGuest,
                    )
                MembersTab.PENDING ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Mailbox,
                        headline = "No pending invites",
                        subcopy = "Invitations you send to housemates appear here until they accept.",
                        ctaTitle = "Send an invite",
                        onCta = ::requestInvite,
                    )
                else ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Users,
                        headline = "No members yet",
                        subcopy = "Invite a housemate to share tasks, bills, calendar, and access codes for this home.",
                        ctaTitle = "Invite someone",
                        onCta = ::requestInvite,
                    )
            }

        // ─── Row mapping ──────────────────────────────────────────

        internal fun rowForOccupant(
            occ: OccupantDto,
            now: Instant,
            zone: ZoneId,
        ): RowModel {
            val role = MemberRole.parse(occ.role)
            val palette = role.palette
            val name = displayName(occ)
            val userId = occ.userId
            val joined =
                relativeText(
                    occ.joinedAt ?: occ.startAt ?: occ.createdAt,
                    now = now,
                    zone = zone,
                )?.let { "Joined $it" }
            return RowModel(
                id = occ.userId,
                title = name,
                subtitle = role.label,
                template = RowTemplate.AvatarKebab,
                leading =
                    RowLeading.AvatarWithBadge(
                        name = name,
                        imageUrl = occ.avatarUrl,
                        background = AvatarBackground.Gradient(MemberAvatarTone.toneFor(occ.userId).gradient),
                        size = AvatarBadgeSize.Medium,
                        verified = true,
                    ),
                trailing = RowTrailing.Kebab,
                onSecondary = {
                    _pendingEvent.value = MembersListEvent.ConfirmRemove(userId = userId, name = name)
                },
                body = joined,
                subtitleIcon = role.icon,
                bodyIcon = joined?.let { PantopusIcon.Clock },
                inlineChip =
                    RowChip(
                        text = role.label,
                        icon = role.icon,
                        tint =
                            RowChip.Tint.Custom(
                                background = palette.background,
                                foreground = palette.foreground,
                            ),
                    ),
            )
        }

        internal fun rowForPending(
            invite: PendingInviteDto,
            now: Instant,
            zone: ZoneId,
        ): RowModel {
            val role = MemberRole.parse(invite.role)
            val palette = role.palette
            val name = invite.name
            val inviteId = invite.id
            val invitedText =
                "Invited " + (relativeText(invite.createdAt, now = now, zone = zone) ?: "recently")
            return RowModel(
                id = invite.id,
                title = name,
                subtitle = role.label,
                template = RowTemplate.StatusChip,
                leading =
                    RowLeading.AvatarWithBadge(
                        name = name,
                        imageUrl = null,
                        background = AvatarBackground.Gradient(MemberAvatarTone.toneFor(invite.id).gradient),
                        size = AvatarBadgeSize.Medium,
                        verified = false,
                    ),
                trailing =
                    RowTrailing.VerticalActions(
                        primary =
                            VerticalAction(
                                label = "Resend",
                                variant = CompactButtonVariant.Primary,
                                onClick = { resendInvite(inviteId) },
                            ),
                        secondary =
                            VerticalAction(
                                label = "Cancel",
                                variant = CompactButtonVariant.Ghost,
                                onClick = { cancelInvite(inviteId) },
                            ),
                    ),
                body = invitedText,
                subtitleIcon = role.icon,
                bodyIcon = PantopusIcon.Mailbox,
                inlineChip =
                    RowChip(
                        text = role.label,
                        icon = role.icon,
                        tint =
                            RowChip.Tint.Custom(
                                background = palette.background,
                                foreground = palette.foreground,
                            ),
                    ),
            )
        }

        // ─── Helpers ───────────────────────────────────────────────

        companion object {
            private const val SECONDS_PER_MINUTE = 60L
            private const val SECONDS_PER_HOUR = 3_600L
            private const val SECONDS_PER_DAY = 86_400L
            private const val ONE_DAY = 1L
            private const val DAYS_IN_WEEK = 7L
            private const val DAYS_IN_MONTH = 30L

            fun displayName(occ: OccupantDto): String {
                if (!occ.displayName.isNullOrEmpty()) return occ.displayName
                if (!occ.username.isNullOrEmpty()) return "@${occ.username}"
                return "Member"
            }

            private val iso8601: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

            private fun parseInstant(raw: String?): Instant? {
                if (raw.isNullOrEmpty()) return null
                return runCatching {
                    Instant.parse(raw)
                }.getOrElse {
                    runCatching {
                        iso8601.parse(raw, Instant::from)
                    }.getOrNull()
                }
            }

            internal fun relativeText(
                raw: String?,
                now: Instant,
                zone: ZoneId,
            ): String? {
                val instant = parseInstant(raw) ?: return null
                val interval = now.epochSecond - instant.epochSecond
                val startOfNow = now.atZone(zone).toLocalDate()
                val startOfDate = instant.atZone(zone).toLocalDate()
                val dayDelta = ChronoUnit.DAYS.between(startOfDate, startOfNow)
                val formatter =
                    DateTimeFormatter
                        .ofPattern("MMM d", Locale.US)
                        .withZone(zone)
                return when {
                    interval < SECONDS_PER_MINUTE -> "just now"
                    interval < SECONDS_PER_HOUR -> "${interval / SECONDS_PER_MINUTE}m ago"
                    interval < SECONDS_PER_DAY -> "${interval / SECONDS_PER_HOUR}h ago"
                    dayDelta == ONE_DAY -> "yesterday"
                    dayDelta < DAYS_IN_WEEK -> "${dayDelta}d ago"
                    dayDelta < DAYS_IN_MONTH -> "${dayDelta / DAYS_IN_WEEK}w ago"
                    else -> formatter.format(instant)
                }
            }
        }
    }
