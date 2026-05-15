@file:Suppress("MagicNumber", "PackageNaming", "TooManyFunctions", "LongMethod")

package app.pantopus.android.ui.screens.you.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.BuildConfig
import app.pantopus.android.data.api.models.homes.MyHome
import app.pantopus.android.data.api.models.users.UserProfile
import app.pantopus.android.data.api.models.users.UserStatsDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.profile.ProfileRepository
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Top-level render state for the Me tab. */
sealed interface MeUiState {
    data object Loading : MeUiState

    data class Loaded(
        val personal: MeIdentityContent,
        val home: MeIdentityContent,
        val business: MeIdentityContent,
    ) : MeUiState

    data class Error(val message: String) : MeUiState
}

/** Me tab view-model. */
@HiltViewModel
class MeViewModel
    @Inject
    constructor(
        private val profileRepo: ProfileRepository,
        private val homesRepo: HomesRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<MeUiState>(MeUiState.Loading)
        val state: StateFlow<MeUiState> = _state.asStateFlow()

        private val _activeIdentity = MutableStateFlow(MeIdentity.Personal)
        val activeIdentity: StateFlow<MeIdentity> = _activeIdentity.asStateFlow()

        fun load() {
            if (_state.value is MeUiState.Loaded) return
            fetch()
        }

        fun refresh() = fetch()

        fun selectIdentity(identity: MeIdentity) {
            if (_activeIdentity.value == identity) return
            _activeIdentity.value = identity
        }

        private fun fetch() {
            viewModelScope.launch {
                // Run profile + homes in parallel; their types differ so we
                // keep two `async` handles rather than `awaitAll` on a
                // common-supertype list.
                val profileDeferred = async { profileRepo.ownProfile() }
                val homesDeferred = async { homesRepo.myHomes() }
                val profileResult = profileDeferred.await()
                val homesResult = homesDeferred.await()

                val profile =
                    (profileResult as? NetworkResult.Success)?.data?.user
                        ?: run {
                            val message =
                                (profileResult as? NetworkResult.Failure)
                                    ?.error?.message
                                    ?: "Couldn't load your profile."
                            _state.value = MeUiState.Error(message)
                            return@launch
                        }
                val homes: List<MyHome> =
                    (homesResult as? NetworkResult.Success)?.data?.homes.orEmpty()

                val stats =
                    (profileRepo.stats(profile.id) as? NetworkResult.Success)?.data

                _state.value =
                    MeUiState.Loaded(
                        personal = buildPersonal(profile, stats),
                        home = buildHome(homes, profileLocality = localityOf(profile)),
                        business = buildBusiness(profile),
                    )
            }
        }

        private fun buildPersonal(
            profile: UserProfile,
            stats: UserStatsDto?,
        ): MeIdentityContent {
            val name =
                profile.name.ifEmpty {
                    listOfNotNull(profile.firstName, profile.lastName)
                        .filter { it.isNotEmpty() }
                        .joinToString(" ")
                }
            val displayName = name.ifEmpty { "Pantopus user" }
            return MeIdentityContent(
                identity = MeIdentity.Personal,
                displayName = displayName,
                initials = initials(displayName),
                handle = "@${profile.username}",
                locality = localityOf(profile),
                bio = profile.bio,
                verified = profile.verified,
                stats =
                    listOf(
                        MeStat("posts", "${profile.gigsPosted ?: 0}", "Posts"),
                        MeStat("gigs_done", "${stats?.totalGigsCompleted ?: profile.gigsCompleted ?: 0}", "Gigs done"),
                        MeStat("listings", "${stats?.totalGigsPosted ?: 0}", "Listings"),
                        MeStat("rating", ratingString(stats?.averageRating ?: profile.averageRating ?: 0.0), "Rating"),
                    ),
                actionTiles =
                    listOf(
                        MeActionTile("bids", PantopusIcon.File, "My bids", routeKey = "me.bids"),
                        MeActionTile("gigs", PantopusIcon.Hammer, "My gigs", routeKey = "me.gigs"),
                        MeActionTile("listings", PantopusIcon.ShoppingBag, "My listings", routeKey = "me.listings"),
                        MeActionTile("saved", PantopusIcon.Star, "Saved", routeKey = "me.saved"),
                        MeActionTile("wallet", PantopusIcon.Shield, "Wallet", routeKey = "me.wallet"),
                        MeActionTile("mail", PantopusIcon.Mailbox, "Mail", routeKey = "me.mail"),
                    ),
                sections =
                    withDebug(
                        listOf(
                            MeSection(
                                id = "account",
                                header = "Account",
                                rows =
                                    listOf(
                                        MeSectionRow("edit", PantopusIcon.Edit2, "Edit profile", routeKey = "me.editProfile"),
                                        MeSectionRow("settings", PantopusIcon.Menu, "Settings", routeKey = "me.settings"),
                                        MeSectionRow(
                                            "privacy",
                                            PantopusIcon.Shield,
                                            "Privacy",
                                            value = privacyValue(profile.profileVisibility),
                                            routeKey = "me.privacy",
                                        ),
                                    ),
                            ),
                            MeSection(
                                id = "activity",
                                header = "Activity",
                                rows =
                                    listOf(
                                        MeSectionRow("posts", PantopusIcon.File, "My posts", routeKey = "me.posts"),
                                        MeSectionRow("homes", PantopusIcon.Home, "My homes", routeKey = "me.homes"),
                                        MeSectionRow("businesses", PantopusIcon.ShoppingBag, "My businesses", routeKey = "me.businesses"),
                                    ),
                            ),
                            MeSection(
                                id = "support",
                                header = "Support",
                                rows =
                                    listOf(
                                        MeSectionRow("help", PantopusIcon.HelpCircle, "Help", routeKey = "me.help"),
                                        MeSectionRow("legal", PantopusIcon.File, "Legal", routeKey = "me.legal"),
                                        MeSectionRow("about", PantopusIcon.Info, "About", value = appVersion(), routeKey = "me.about"),
                                    ),
                            ),
                        ),
                    ),
            )
        }

        private fun buildHome(
            homes: List<MyHome>,
            profileLocality: String?,
        ): MeIdentityContent {
            val primary = homes.firstOrNull { it.isPrimaryOwner == true } ?: homes.firstOrNull()
            if (primary == null) {
                return MeIdentityContent(
                    identity = MeIdentity.Home,
                    displayName = "Claim a home",
                    initials = "H",
                    handle = "No home yet",
                    locality = profileLocality,
                    bio = "Add a home from the Hub to unlock household tools.",
                    verified = false,
                    stats =
                        listOf(
                            MeStat("packages", "—", "Packages"),
                            MeStat("bills", "—", "Bills"),
                            MeStat("members", "—", "Members"),
                            MeStat("codes", "—", "Codes"),
                        ),
                    actionTiles = homeActionTiles(),
                    sections = withDebug(homeSections(privacyValue = null)),
                    isUnbound = true,
                )
            }
            val address = primary.address ?: "Your home"
            val displayName = primary.name?.takeIf { it.isNotEmpty() } ?: address
            val locality =
                listOfNotNull(primary.city, primary.state)
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
                    .takeIf { it.isNotEmpty() }
            return MeIdentityContent(
                identity = MeIdentity.Home,
                displayName = displayName,
                initials = initials(displayName),
                handle = "Household · ${homes.size} member${if (homes.size == 1) "" else "s"}",
                locality = locality ?: profileLocality,
                bio = null,
                verified = primary.ownershipStatus == "verified",
                stats =
                    listOf(
                        MeStat("packages", "—", "Packages"),
                        MeStat("bills", "—", "Bills"),
                        MeStat("members", "${homes.size}", "Members"),
                        MeStat("codes", "—", "Codes"),
                    ),
                actionTiles = homeActionTiles(),
                sections = withDebug(homeSections(privacyValue = "Neighbors")),
            )
        }

        private fun buildBusiness(profile: UserProfile): MeIdentityContent =
            MeIdentityContent(
                identity = MeIdentity.Business,
                displayName = "Add a business",
                initials = "B",
                handle = "No business yet",
                locality = localityOf(profile),
                bio = "Business identity is set up in the web app today; mobile read APIs land later.",
                verified = false,
                stats =
                    listOf(
                        MeStat("orders", "—", "Orders"),
                        MeStat("earnings", "—", "Earnings"),
                        MeStat("products", "—", "Products"),
                        MeStat("rating", "—", "Rating"),
                    ),
                actionTiles =
                    listOf(
                        MeActionTile("orders", PantopusIcon.File, "Orders", routeKey = "me.business.orders"),
                        MeActionTile("products", PantopusIcon.ShoppingBag, "Products", routeKey = "me.business.products"),
                        MeActionTile("payouts", PantopusIcon.Shield, "Payouts", routeKey = "me.business.payouts"),
                        MeActionTile("team", PantopusIcon.UserPlus, "Team", routeKey = "me.business.team"),
                        MeActionTile("hours", PantopusIcon.Info, "Hours", routeKey = "me.business.hours"),
                        MeActionTile("promo", PantopusIcon.Megaphone, "Promo", routeKey = "me.business.promo"),
                    ),
                sections =
                    withDebug(
                        listOf(
                            MeSection(
                                id = "account",
                                header = "Business",
                                rows =
                                    listOf(
                                        MeSectionRow("profile", PantopusIcon.Edit2, "Edit business profile", routeKey = "me.business.editProfile"),
                                        MeSectionRow("settings", PantopusIcon.Menu, "Settings", routeKey = "me.business.settings"),
                                    ),
                            ),
                            MeSection(
                                id = "support",
                                header = "Support",
                                rows =
                                    listOf(
                                        MeSectionRow("help", PantopusIcon.HelpCircle, "Help", routeKey = "me.help"),
                                        MeSectionRow("legal", PantopusIcon.File, "Legal", routeKey = "me.legal"),
                                        MeSectionRow("about", PantopusIcon.Info, "About", value = appVersion(), routeKey = "me.about"),
                                    ),
                            ),
                        ),
                    ),
                isUnbound = true,
            )

        private fun homeActionTiles(): List<MeActionTile> =
            listOf(
                MeActionTile("access", PantopusIcon.Lock, "Access", routeKey = "me.home.access"),
                MeActionTile("bills", PantopusIcon.File, "Bills", routeKey = "me.home.bills"),
                MeActionTile("packages", PantopusIcon.ShoppingBag, "Packages", routeKey = "me.home.packages"),
                MeActionTile("members", PantopusIcon.UserPlus, "Members", routeKey = "me.home.members"),
                MeActionTile("docs", PantopusIcon.File, "Docs", routeKey = "me.home.docs"),
                MeActionTile("calendar", PantopusIcon.Calendar, "Calendar", routeKey = "me.home.calendar"),
            )

        private fun homeSections(privacyValue: String?): List<MeSection> =
            listOf(
                MeSection(
                    id = "household",
                    header = "Household",
                    rows =
                        listOf(
                            MeSectionRow("address", PantopusIcon.Home, "Edit address", routeKey = "me.home.editAddress"),
                            MeSectionRow("invite", PantopusIcon.UserPlus, "Invite member", routeKey = "me.home.invite"),
                            MeSectionRow("privacy", PantopusIcon.Shield, "Privacy", value = privacyValue, routeKey = "me.home.privacy"),
                        ),
                ),
                MeSection(
                    id = "activity",
                    header = "Activity",
                    rows =
                        listOf(
                            MeSectionRow("delivery", PantopusIcon.Mailbox, "Delivery log", routeKey = "me.home.deliveryLog"),
                            MeSectionRow("maintenance", PantopusIcon.Hammer, "Maintenance", routeKey = "me.home.maintenance"),
                            MeSectionRow("utilities", PantopusIcon.Info, "Utilities", routeKey = "me.home.utilities"),
                        ),
                ),
                MeSection(
                    id = "support",
                    header = "Support",
                    rows =
                        listOf(
                            MeSectionRow("help", PantopusIcon.HelpCircle, "Help", routeKey = "me.help"),
                            MeSectionRow("about", PantopusIcon.Info, "About", value = appVersion(), routeKey = "me.about"),
                        ),
                ),
            )

        private fun withDebug(sections: List<MeSection>): List<MeSection> {
            if (!BuildConfig.DEBUG) return sections
            return sections +
                MeSection(
                    id = "debug",
                    header = "Debug",
                    rows =
                        listOf(
                            MeSectionRow("openProfile", PantopusIcon.Search, "Open public profile by ID", routeKey = "me.debug.openProfile"),
                            MeSectionRow("openPost", PantopusIcon.Search, "Open Pulse post by ID", routeKey = "me.debug.openPost"),
                            MeSectionRow("openHandshake", PantopusIcon.UserPlus, "Open Privacy Handshake by persona handle", routeKey = "me.debug.openHandshake"),
                            MeSectionRow("openInviteToken", PantopusIcon.Mailbox, "Open invite by token", routeKey = "me.debug.openInviteToken"),
                            MeSectionRow("openCeremonialMail", PantopusIcon.Send, "Open Ceremonial Mail Compose", routeKey = "me.debug.openCeremonialMail"),
                            MeSectionRow("inviteOwner", PantopusIcon.UserPlus, "Invite owner to home by ID", routeKey = "me.debug.inviteOwner"),
                            MeSectionRow("disambiguate", PantopusIcon.Mailbox, "Disambiguate mail by ID", routeKey = "me.debug.disambiguate"),
                        ),
                )
        }

        private fun localityOf(profile: UserProfile): String? {
            val parts = listOfNotNull(profile.city, profile.state).filter { it.isNotEmpty() }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
        }

        private fun initials(name: String): String {
            val parts = name.split(" ").take(2)
            val result = parts.mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
            return result.ifEmpty { "?" }
        }

        private fun ratingString(rating: Double): String = if (rating > 0) "%.1f".format(rating) else "—"

        private fun privacyValue(visibility: String?): String? =
            when (visibility?.lowercase()) {
                "public" -> "Public"
                "registered" -> "Neighbors"
                "private" -> "Strict"
                else -> null
            }

        private fun appVersion(): String = "v${BuildConfig.VERSION_NAME}"
    }
