@file:Suppress("LongMethod")

package app.pantopus.android.ui.screens.root

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pantopus.android.BuildConfig
import app.pantopus.android.core.routing.DeepLinkRouter
import app.pantopus.android.ui.screens._internal.TokenGalleryScreen
import app.pantopus.android.ui.screens.audience_profile.AudienceProfileScreen
import app.pantopus.android.ui.screens.ceremonial_mail.CeremonialMailWizardScreen
import app.pantopus.android.ui.screens.ceremonial_mail_open.CeremonialMailOpenScreen
import app.pantopus.android.ui.screens.connections.ConnectionsChatTarget
import app.pantopus.android.ui.screens.connections.ConnectionsScreen
import app.pantopus.android.ui.screens.contentdetail.GigDetailScreen
import app.pantopus.android.ui.screens.contentdetail.InvoiceDetailScreen
import app.pantopus.android.ui.screens.contentdetail.ListingDetailScreen
import app.pantopus.android.ui.screens.discoverbusinesses.DiscoverBusinessesScreen
import app.pantopus.android.ui.screens.discoverbusinesses.DiscoverBusinessesTarget
import app.pantopus.android.ui.screens.discoverhub.DiscoverHubScreen
import app.pantopus.android.ui.screens.discoverhub.DiscoverHubTarget
import app.pantopus.android.ui.screens.feed.FeedScreen
import app.pantopus.android.ui.screens.feed.pulse.PulseIntent
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.screens.gigs.GigsFeedScreen
import app.pantopus.android.ui.screens.handshake.PrivacyHandshakeScreen
import app.pantopus.android.ui.screens.homes.HOME_DASHBOARD_HOME_ID_KEY
import app.pantopus.android.ui.screens.homes.HomeDashboardScreen
import app.pantopus.android.ui.screens.homes.MyHomesListScreen
import app.pantopus.android.ui.screens.homes.add_home.AddHomeWizardScreen
import app.pantopus.android.ui.screens.homes.bills.ADD_BILL_HOME_ID_KEY
import app.pantopus.android.ui.screens.homes.bills.AddBillWizardScreen
import app.pantopus.android.ui.screens.homes.bills.BILLS_HOME_ID_KEY
import app.pantopus.android.ui.screens.homes.bills.BILL_DETAIL_BILL_ID_KEY
import app.pantopus.android.ui.screens.homes.bills.BILL_DETAIL_HOME_ID_KEY
import app.pantopus.android.ui.screens.homes.bills.BillDetailScreen
import app.pantopus.android.ui.screens.homes.bills.BillsListScreen
import app.pantopus.android.ui.screens.homes.claim_ownership.CLAIM_OWNERSHIP_HOME_ID_KEY
import app.pantopus.android.ui.screens.homes.claim_ownership.ClaimOwnershipWizardScreen
import app.pantopus.android.ui.screens.homes.claims.MyClaimsListScreen
import app.pantopus.android.ui.screens.homes.invite_owner.INVITE_OWNER_CURRENT_EMAIL_KEY
import app.pantopus.android.ui.screens.homes.invite_owner.INVITE_OWNER_HOME_ID_KEY
import app.pantopus.android.ui.screens.homes.invite_owner.InviteOwnerFormScreen
import app.pantopus.android.ui.screens.homes.pets.PETS_LIST_HOME_ID_KEY
import app.pantopus.android.ui.screens.homes.pets.PetsListScreen
import app.pantopus.android.ui.screens.hub.ActionChipContent
import app.pantopus.android.ui.screens.hub.DiscoveryCardContent
import app.pantopus.android.ui.screens.hub.DiscoveryKind
import app.pantopus.android.ui.screens.hub.HubNavigationIntent
import app.pantopus.android.ui.screens.hub.HubScreen
import app.pantopus.android.ui.screens.hub.JumpBackItem
import app.pantopus.android.ui.screens.hub.PillarTile
import app.pantopus.android.ui.screens.identity_center.IdentityCenterScreen
import app.pantopus.android.ui.screens.identity_center.IdentityKind
import app.pantopus.android.ui.screens.inbox.InboxScreen
import app.pantopus.android.ui.screens.inbox.chat.ConversationIdentityChip
import app.pantopus.android.ui.screens.inbox.chat.ConversationRowContent
import app.pantopus.android.ui.screens.inbox.chat.ConversationRowVariant
import app.pantopus.android.ui.screens.inbox.conversation.ChatConversationHost
import app.pantopus.android.ui.screens.inbox.conversation.ChatCounterparty
import app.pantopus.android.ui.screens.inbox.conversation.ChatThreadMode
import app.pantopus.android.ui.screens.listing_offers.ListingOffersScreen
import app.pantopus.android.ui.screens.mailbox.MailboxDrawersScreen
import app.pantopus.android.ui.screens.mailbox.MailboxListScreen
import app.pantopus.android.ui.screens.mailbox.disambiguate.DISAMBIGUATE_MAIL_ID_KEY
import app.pantopus.android.ui.screens.mailbox.disambiguate.DisambiguateMailFormScreen
import app.pantopus.android.ui.screens.mailbox.item_detail.MAILBOX_ITEM_DETAIL_MAIL_ID_KEY
import app.pantopus.android.ui.screens.mailbox.item_detail.MailboxItemDetailScreen
import app.pantopus.android.ui.screens.marketplace.MarketplaceScreen
import app.pantopus.android.ui.screens.my_bids.MyBidsScreen
import app.pantopus.android.ui.screens.my_posts.MyPostsScreen
import app.pantopus.android.ui.screens.my_tasks.MyTasksScreen
import app.pantopus.android.ui.screens.nearby.map.MapEntity
import app.pantopus.android.ui.screens.nearby.map.MapEntityKind
import app.pantopus.android.ui.screens.nearby.map.NearbyMapScreen
import app.pantopus.android.ui.screens.notifications.NotificationsScreen
import app.pantopus.android.ui.screens.offers.OffersScreen
import app.pantopus.android.ui.screens.posts.PULSE_POST_DETAIL_ID_KEY
import app.pantopus.android.ui.screens.posts.PulsePostDetailScreen
import app.pantopus.android.ui.screens.profile.PUBLIC_PROFILE_USER_ID_KEY
import app.pantopus.android.ui.screens.profile.PublicProfileScreen
import app.pantopus.android.ui.screens.settings.NotificationSettingsScreen
import app.pantopus.android.ui.screens.settings.PrivacySettingsScreen
import app.pantopus.android.ui.screens.settings.SettingsIndexScreen
import app.pantopus.android.ui.screens.settings.SettingsRoute
import app.pantopus.android.ui.screens.token_accept.TokenAcceptScreen
import app.pantopus.android.ui.screens.you.YouScreen
import app.pantopus.android.ui.theme.PantopusIcon

/** Non-tab routes reachable from within the Hub stack. */
private object ChildRoutes {
    const val MY_HOMES = "homes/my-homes"
    const val MY_CLAIMS = "homes/my-claims"
    const val ADD_HOME = "homes/add"
    const val CLAIM_OWNERSHIP = "homes/{$CLAIM_OWNERSHIP_HOME_ID_KEY}/claim"
    const val MAILBOX_LIST = "mailbox/list"
    const val MAILBOX_DRAWERS = "mailbox/drawers"
    const val MAILBOX_SEARCH = "mailbox/search"
    const val MAILBOX_ITEM_DETAIL = "mailbox/item/{$MAILBOX_ITEM_DETAIL_MAIL_ID_KEY}"
    const val HOME_DASHBOARD = "homes/{$HOME_DASHBOARD_HOME_ID_KEY}"

    /** Bills list (T5.2.2 / P13). */
    const val HOME_BILLS = "homes/{$BILLS_HOME_ID_KEY}/bills"

    /** Bill detail (read-mostly summary, mark-paid / remove). */
    const val BILL_DETAIL =
        "homes/{$BILL_DETAIL_HOME_ID_KEY}/bills/{$BILL_DETAIL_BILL_ID_KEY}"

    /** Add Bill wizard. */
    const val ADD_BILL = "homes/{$ADD_BILL_HOME_ID_KEY}/bills/new"

    /** Pets list per home (T5.2.1). */
    const val HOME_PETS = "homes/{$PETS_LIST_HOME_ID_KEY}/pets"

    /** Build the concrete path for a home pets list. */
    fun homePets(homeId: String): String = "homes/$homeId/pets"

    const val PUBLIC_PROFILE = "users/{$PUBLIC_PROFILE_USER_ID_KEY}"
    const val PULSE_POST = "posts/{$PULSE_POST_DETAIL_ID_KEY}"
    const val INVITE_OWNER =
        "homes/{$INVITE_OWNER_HOME_ID_KEY}/invite?email={$INVITE_OWNER_CURRENT_EMAIL_KEY}"
    const val DISAMBIGUATE_MAIL = "mailbox/disambiguate/{$DISAMBIGUATE_MAIL_ID_KEY}"

    /** Notifications center (T4.1). Reached from the Hub bell icon. */
    const val NOTIFICATIONS = "notifications"

    /** Connections center (T5.2.3). Reached from the You / Me action grid
     *  or via `pantopus://connections`. */
    const val CONNECTIONS = "connections"

    /** Cross-listing Offers (T5.2.4). Reached from the You tab. */
    const val OFFERS = "offers"

    /** My bids (T5.3.1). Reached from the You tab "My bids" action tile. */
    const val MY_BIDS = "my-bids"

    /** My tasks V2 (T5.3.2). Reached from the You tab "My gigs" action tile. */
    const val MY_TASKS = "my-tasks"

    /** Compose-task placeholder (T5.3.2). Replaced when T2.3 lands the
     *  dedicated composer. */
    const val COMPOSE_TASK = "compose-task"

    /** My posts (T5.3.3). Reached from the You / Me Activity-section row. */
    const val MY_POSTS = "my-posts"

    /** Discover hub (T5.4.1 / P11). Reached from the Hub Discovery rail's
     *  "See all" CTA or via `pantopus://discover-hub`. */
    const val DISCOVER_HUB = "discover-hub"

    /** Discover businesses (T5.4.2 / P12). Until P12 lands, the Discover
     *  hub Businesses "See all" pushes here and renders a placeholder. */
    const val DISCOVER_BUSINESSES = "discover-businesses"

    /** Hub menu icon target. Replaced by Settings in T3.1. */
    const val MENU = "settings"

    /** Notification preferences (T3.1). */
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"

    /** Privacy preferences (T3.1). */
    const val SETTINGS_PRIVACY = "settings/privacy"

    /** Profiles & Privacy / Identity Center (T3.2). */
    const val IDENTITY_CENTER = "identity-center"

    /** Public Profile management / Creator audience dashboard (T3.3). */
    const val AUDIENCE_PROFILE = "audience-profile"

    /** Ceremonial Mail Compose wizard (T3.7). */
    const val CEREMONIAL_MAIL = "mailbox/compose-letter"

    /** Ceremonial Mail Open reader (T3.8). `:id` is the Mail UUID. */
    const val CEREMONIAL_MAIL_OPEN_ID_KEY = "mailId"
    const val CEREMONIAL_MAIL_OPEN = "mailbox/letter/{$CEREMONIAL_MAIL_OPEN_ID_KEY}"

    fun ceremonialMailOpen(mailId: String): String = "mailbox/letter/${java.net.URLEncoder.encode(mailId, "UTF-8")}"

    /** Privacy Handshake wizard (T3.4). `:handle` is the persona being followed. */
    const val PRIVACY_HANDSHAKE_HANDLE_KEY = "personaHandle"
    const val PRIVACY_HANDSHAKE = "handshake/{$PRIVACY_HANDSHAKE_HANDLE_KEY}"

    fun privacyHandshake(handle: String): String = "handshake/${java.net.URLEncoder.encode(handle, "UTF-8")}"

    /** Token / Accept screen (T3.5). Resolves the token then accepts
     *  via the matching backend route. Mirrors iOS DeepLinkRouter's
     *  `.invite(token)` destination. */
    const val TOKEN_ACCEPT_TOKEN_KEY = "token"
    const val TOKEN_ACCEPT = "invite/{$TOKEN_ACCEPT_TOKEN_KEY}"

    fun tokenAccept(token: String): String = "invite/${java.net.URLEncoder.encode(token, "UTF-8")}"

    /**
     * Generic placeholder for intents whose destination hasn't been
     * built yet. `label` is URL-encoded into the path and rendered by
     * the placeholder composable.
     */
    const val PLACEHOLDER_LABEL_KEY = "label"
    const val PLACEHOLDER = "_placeholder/generic?$PLACEHOLDER_LABEL_KEY={$PLACEHOLDER_LABEL_KEY}"

    /** Pulse tab (T1.2). Reached from Hub → pillar(.Pulse). */
    const val PULSE_FEED = "feed/pulse"

    /** Gigs feed (T2.3). Reached from Hub → pillar(.Gigs). */
    const val GIGS_FEED = "gigs/feed"

    /** Gig detail target — placeholder until T2.6 Transactional Detail. */
    const val GIG_DETAIL_ID_KEY = "gigId"
    const val GIG_DETAIL = "gigs/{$GIG_DETAIL_ID_KEY}"

    /** Compose gig target — placeholder until the compose flow ships. */
    const val COMPOSE_GIG_CATEGORY_KEY = "category"
    const val COMPOSE_GIG = "gigs/compose?$COMPOSE_GIG_CATEGORY_KEY={$COMPOSE_GIG_CATEGORY_KEY}"

    /** Nearby map opened from the Gigs feed map-toggle — seeded with the
     *  active category so the same filter applies on the map. */
    const val NEARBY_MAP_FOR_GIGS_CATEGORY_KEY = "category"
    const val NEARBY_MAP_FOR_GIGS =
        "gigs/map?$NEARBY_MAP_FOR_GIGS_CATEGORY_KEY={$NEARBY_MAP_FOR_GIGS_CATEGORY_KEY}"

    /** Marketplace tab (T2.5). Reached from Hub → pillar(.Marketplace). */
    const val MARKETPLACE = "marketplace"

    /** Listing detail target — placeholder until T2.6. */
    const val LISTING_DETAIL_ID_KEY = "listingId"
    const val LISTING_DETAIL = "listings/{$LISTING_DETAIL_ID_KEY}"

    /** Per-listing offers panel (T5.3.4). */
    const val LISTING_OFFERS_ID_KEY = "listingId"
    const val LISTING_OFFERS_TITLE_KEY = "listingTitle"
    const val LISTING_OFFERS =
        "listings/{$LISTING_OFFERS_ID_KEY}/offers?$LISTING_OFFERS_TITLE_KEY={$LISTING_OFFERS_TITLE_KEY}"

    /** Build the listing-offers path with an optional title hint. */
    fun listingOffers(
        listingId: String,
        title: String? = null,
    ): String {
        val encodedTitle = java.net.URLEncoder.encode(title ?: "", "UTF-8")
        return "listings/$listingId/offers?$LISTING_OFFERS_TITLE_KEY=$encodedTitle"
    }

    /** Snap & sell — placeholder until the marketplace compose flow ships. */
    const val COMPOSE_LISTING = "listings/compose"

    /** Invoice detail (T2.6 ContentDetailShell · invoice variant). */
    const val INVOICE_DETAIL_ID_KEY = "invoiceId"
    const val INVOICE_DETAIL = "invoices/{$INVOICE_DETAIL_ID_KEY}"

    /** Chat conversation (T2.2). Reached from Inbox → row tap. */
    const val CHAT_KIND_KEY = "kind"
    const val CHAT_ID_KEY = "id"
    const val CHAT_NAME_KEY = "name"
    const val CHAT_INITIALS_KEY = "initials"
    const val CHAT_VERIFIED_KEY = "verified"
    const val CHAT_IDENTITY_KEY = "identity"
    const val CHAT_LOCALITY_KEY = "locality"
    const val CHAT_ONLINE_KEY = "online"
    const val CHAT_CONVERSATION =
        "chat/{$CHAT_KIND_KEY}/{$CHAT_ID_KEY}?" +
            "$CHAT_NAME_KEY={$CHAT_NAME_KEY}" +
            "&$CHAT_INITIALS_KEY={$CHAT_INITIALS_KEY}" +
            "&$CHAT_VERIFIED_KEY={$CHAT_VERIFIED_KEY}" +
            "&$CHAT_IDENTITY_KEY={$CHAT_IDENTITY_KEY}" +
            "&$CHAT_LOCALITY_KEY={$CHAT_LOCALITY_KEY}" +
            "&$CHAT_ONLINE_KEY={$CHAT_ONLINE_KEY}"

    /** Compose post target — placeholder until the compose flow ships. */
    const val COMPOSE_INTENT_KEY = "intent"
    const val COMPOSE_POST = "feed/compose?$COMPOSE_INTENT_KEY={$COMPOSE_INTENT_KEY}"

    /** Debug-only route reached via 5-tap easter egg on the Hub. */
    const val TOKEN_GALLERY = "_debug/token-gallery"

    /** Build the concrete path for a home dashboard. */
    fun homeDashboard(id: String): String = "homes/$id"

    /** Build the concrete path for the Bills list. */
    fun homeBills(homeId: String): String = "homes/$homeId/bills"

    /** Build the concrete path for a Bill detail. */
    fun billDetail(
        homeId: String,
        billId: String,
    ): String = "homes/$homeId/bills/$billId"

    /** Build the concrete path for the Add Bill wizard. */
    fun addBill(homeId: String): String = "homes/$homeId/bills/new"

    /** Build the concrete path for a mailbox item detail. */
    fun mailboxItemDetail(id: String): String = "mailbox/item/$id"

    /** Build the concrete path for a public profile. */
    fun publicProfile(id: String): String = "users/$id"

    /** Build the concrete path for a Pulse post detail. */
    fun pulsePost(id: String): String = "posts/$id"

    /**
     * Build the invite-owner path. `email` is forwarded so the form
     * can reject self-invites; pass `""` when unknown.
     */
    fun inviteOwner(
        homeId: String,
        currentEmail: String,
    ): String = "homes/$homeId/invite?email=${java.net.URLEncoder.encode(currentEmail, "UTF-8")}"

    /** Build the disambiguate-mail path. */
    fun disambiguateMail(mailId: String): String = "mailbox/disambiguate/$mailId"

    /** Build the claim-ownership wizard path. */
    fun claimOwnership(homeId: String): String = "homes/$homeId/claim"

    /** Build the generic placeholder path with an encoded label. */
    fun placeholder(label: String): String = "_placeholder/generic?$PLACEHOLDER_LABEL_KEY=${java.net.URLEncoder.encode(label, "UTF-8")}"

    /** Build the compose-post path with the pre-fill intent encoded. */
    fun composePost(intent: String): String = "feed/compose?$COMPOSE_INTENT_KEY=${java.net.URLEncoder.encode(intent, "UTF-8")}"

    /** Build the gig-detail path. */
    fun gigDetail(gigId: String): String = "gigs/$gigId"

    /** Build the compose-gig path with the active category pre-fill. */
    fun composeGig(category: String): String = "gigs/compose?$COMPOSE_GIG_CATEGORY_KEY=${java.net.URLEncoder.encode(category, "UTF-8")}"

    /** Build the Nearby-map-for-gigs path with the active category seed. */
    fun nearbyMapForGigs(category: String): String =
        "gigs/map?$NEARBY_MAP_FOR_GIGS_CATEGORY_KEY=${java.net.URLEncoder.encode(category, "UTF-8")}"

    /** Build the listing-detail path. */
    fun listingDetail(listingId: String): String = "listings/$listingId"

    /** Build the invoice-detail path. */
    fun invoiceDetail(invoiceId: String): String = "invoices/$invoiceId"

    /** Build the chat-conversation path with all header context encoded. */
    fun chatConversation(row: ConversationRowContent): String {
        val kind =
            when (row.variant) {
                ConversationRowVariant.AiAssistant -> "ai"
                is ConversationRowVariant.Group -> "room"
                ConversationRowVariant.Dm -> "person"
            }
        val identity =
            when (row.identityChip) {
                ConversationIdentityChip.Business -> "business"
                ConversationIdentityChip.Home -> "home"
                null -> ""
            }

        fun enc(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
        return "chat/$kind/${enc(row.id)}?" +
            "$CHAT_NAME_KEY=${enc(row.displayName)}" +
            "&$CHAT_INITIALS_KEY=${enc(row.initials)}" +
            "&$CHAT_VERIFIED_KEY=${row.verified}" +
            "&$CHAT_IDENTITY_KEY=${enc(identity)}" +
            "&$CHAT_LOCALITY_KEY=" +
            "&$CHAT_ONLINE_KEY=false"
    }
}

/**
 * Signed-in root. Hosts [PantopusBottomBar] + a [NavHost] with the four
 * top-level destinations from [PantopusRoute] plus drill-down destinations
 * wired from the Hub.
 *
 * @param inboxBadgeCount Unread count shown on the Inbox tab. Wired to
 *     live data in Prompt P8.
 */
@Composable
fun RootTabScreen(inboxBadgeCount: Int = 0) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = PantopusRoute.fromPath(backStackEntry?.destination?.route) ?: PantopusRoute.Hub
    val badges: Map<PantopusRoute, Int> =
        if (inboxBadgeCount > 0) mapOf(PantopusRoute.Inbox to inboxBadgeCount) else emptyMap()

    // Consume pending deep links — when the host activity (or a
    // notification tap) routed a URL or path through DeepLinkRouter,
    // dispatch to the matching screen. Mirrors the iOS T4.1 routing
    // table from `docs/07-frontend-mobile-app.md §9`.
    val pendingDeepLink by DeepLinkRouter.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDeepLink) {
        when (val pending = pendingDeepLink) {
            null -> Unit
            is DeepLinkRouter.Destination.Invite -> {
                navController.navigate(ChildRoutes.tokenAccept(pending.token))
                DeepLinkRouter.consume()
            }
            DeepLinkRouter.Destination.Notifications -> {
                navController.navigate(ChildRoutes.NOTIFICATIONS)
                DeepLinkRouter.consume()
            }
            DeepLinkRouter.Destination.Feed -> {
                navController.navigate(ChildRoutes.PULSE_FEED)
                DeepLinkRouter.consume()
            }
            DeepLinkRouter.Destination.Home -> {
                navController.navigate(PantopusRoute.Hub.path)
                DeepLinkRouter.consume()
            }
            DeepLinkRouter.Destination.Connections -> {
                navController.navigate(ChildRoutes.CONNECTIONS)
                DeepLinkRouter.consume()
            }
            DeepLinkRouter.Destination.DiscoverHub -> {
                navController.navigate(ChildRoutes.DISCOVER_HUB)
                DeepLinkRouter.consume()
            }
            is DeepLinkRouter.Destination.Post -> {
                navController.navigate(ChildRoutes.pulsePost(pending.id))
                DeepLinkRouter.consume()
            }
            is DeepLinkRouter.Destination.Conversation -> {
                // Drop the user on the Inbox tab — the chat-conversation
                // route needs counterparty metadata the deep link doesn't
                // carry. Mirrors iOS, which selects the Inbox tab.
                navController.navigate(PantopusRoute.Inbox.path)
                DeepLinkRouter.consume()
            }
            is DeepLinkRouter.Destination.SupportTrain -> {
                navController.navigate(ChildRoutes.placeholder("Support train · ${pending.id}"))
                DeepLinkRouter.consume()
            }
            is DeepLinkRouter.Destination.Gig -> {
                navController.navigate(ChildRoutes.gigDetail(pending.id))
                DeepLinkRouter.consume()
            }
            is DeepLinkRouter.Destination.Listing -> {
                navController.navigate(ChildRoutes.listingDetail(pending.id))
                DeepLinkRouter.consume()
            }
            is DeepLinkRouter.Destination.HomeDetail -> {
                navController.navigate(ChildRoutes.homeDashboard(pending.id))
                DeepLinkRouter.consume()
            }
            is DeepLinkRouter.Destination.HomeDashboard -> {
                navController.navigate(ChildRoutes.homeDashboard(pending.id))
                DeepLinkRouter.consume()
            }
            is DeepLinkRouter.Destination.HomeMemberRequests -> {
                navController.navigate(ChildRoutes.placeholder("Member requests · ${pending.id}"))
                DeepLinkRouter.consume()
            }
            is DeepLinkRouter.Destination.User -> {
                navController.navigate(ChildRoutes.publicProfile(pending.id))
                DeepLinkRouter.consume()
            }
            is DeepLinkRouter.Destination.Unknown -> {
                DeepLinkRouter.consume()
            }
        }
    }

    Scaffold(
        bottomBar = {
            PantopusBottomBar(
                selected = currentRoute,
                badges = badges,
                onSelect = { target ->
                    if (target == currentRoute) return@PantopusBottomBar
                    navController.navigate(target.path) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        NavHost(
            navController = navController,
            startDestination = PantopusRoute.Hub.path,
            modifier = Modifier.padding(padding),
        ) {
            composable(PantopusRoute.Hub.path) {
                HubWithDebugFiveTap(navController = navController) {
                    HubScreen(onIntent = { intent ->
                        when (intent) {
                            HubNavigationIntent.OpenNotifications ->
                                navController.navigate(ChildRoutes.NOTIFICATIONS)
                            HubNavigationIntent.OpenMenu ->
                                navController.navigate(ChildRoutes.MENU)
                            HubNavigationIntent.StartVerification ->
                                navController.navigate(ChildRoutes.ADD_HOME)
                            is HubNavigationIntent.ActionTapped ->
                                when (intent.kind) {
                                    ActionChipContent.Kind.AddHome ->
                                        navController.navigate(ChildRoutes.ADD_HOME)
                                    ActionChipContent.Kind.ScanMail ->
                                        navController.navigate(ChildRoutes.MAILBOX_DRAWERS)
                                    ActionChipContent.Kind.PostTask ->
                                        navController.navigate(ChildRoutes.placeholder("Post a gig"))
                                    ActionChipContent.Kind.SnapAndSell ->
                                        navController.navigate(ChildRoutes.placeholder("Snap & sell"))
                                }
                            is HubNavigationIntent.PillarTapped ->
                                when (intent.pillar) {
                                    PillarTile.Pillar.Mail ->
                                        navController.navigate(ChildRoutes.MAILBOX_LIST)
                                    PillarTile.Pillar.Pulse ->
                                        navController.navigate(ChildRoutes.PULSE_FEED)
                                    PillarTile.Pillar.Marketplace ->
                                        navController.navigate(ChildRoutes.MARKETPLACE)
                                    PillarTile.Pillar.Gigs ->
                                        navController.navigate(ChildRoutes.GIGS_FEED)
                                }
                            is HubNavigationIntent.DiscoveryTapped ->
                                routeForDiscovery(intent.item).also { navController.navigate(it) }
                            HubNavigationIntent.OpenDiscoverHub ->
                                navController.navigate(ChildRoutes.DISCOVER_HUB)
                            is HubNavigationIntent.JumpBackTapped ->
                                routeForJumpBackIn(intent.item).also { navController.navigate(it) }
                        }
                    })
                }
            }
            composable(PantopusRoute.Nearby.path) {
                NearbyMapScreen(
                    onOpenEntity = { entity: MapEntity ->
                        when (entity.kind) {
                            MapEntityKind.Gig -> navController.navigate(ChildRoutes.gigDetail(entity.id))
                            MapEntityKind.Listing -> navController.navigate(ChildRoutes.listingDetail(entity.id))
                        }
                    },
                    onOpenFilters = { navController.navigate(ChildRoutes.placeholder("Map filters")) },
                )
            }
            composable(PantopusRoute.Inbox.path) {
                InboxScreen(
                    onOpenConversation = { row ->
                        navController.navigate(ChildRoutes.chatConversation(row))
                    },
                    onCompose = { navController.navigate(ChildRoutes.placeholder("New message")) },
                    onOpenSearch = { navController.navigate(ChildRoutes.placeholder("Chat search")) },
                )
            }
            composable(PantopusRoute.You.path) {
                YouScreen(
                    onOpenPublicProfile = { userId ->
                        navController.navigate(ChildRoutes.publicProfile(userId))
                    },
                    onOpenPulsePost = { postId ->
                        navController.navigate(ChildRoutes.pulsePost(postId))
                    },
                    onInviteOwner = { homeId, email ->
                        navController.navigate(ChildRoutes.inviteOwner(homeId, email))
                    },
                    onDisambiguateMail = { mailId ->
                        navController.navigate(ChildRoutes.disambiguateMail(mailId))
                    },
                    onOpenPrivacyHandshake = { handle ->
                        navController.navigate(ChildRoutes.privacyHandshake(handle))
                    },
                    onOpenInviteToken = { token ->
                        navController.navigate(ChildRoutes.tokenAccept(token))
                    },
                    onOpenCeremonialMail = {
                        navController.navigate(ChildRoutes.CEREMONIAL_MAIL)
                    },
                    onOpenCeremonialMailOpen = { mailId ->
                        navController.navigate(ChildRoutes.ceremonialMailOpen(mailId))
                    },
                    onOpenMailbox = { navController.navigate(ChildRoutes.MAILBOX_LIST) },
                    onOpenEditProfile = {
                        navController.navigate(ChildRoutes.placeholder("Edit profile"))
                    },
                    onOpenPlaceholder = { label ->
                        navController.navigate(ChildRoutes.placeholder(label))
                    },
                    onOpenSettings = { navController.navigate(ChildRoutes.MENU) },
                    onOpenOffers = { navController.navigate(ChildRoutes.OFFERS) },
                    onOpenMyBids = { navController.navigate(ChildRoutes.MY_BIDS) },
                    onOpenMyTasks = { navController.navigate(ChildRoutes.MY_TASKS) },
                    onOpenMyPosts = { navController.navigate(ChildRoutes.MY_POSTS) },
                )
            }

            composable(ChildRoutes.MY_HOMES) {
                MyHomesListScreen(
                    onOpenHome = { homeId -> navController.navigate(ChildRoutes.homeDashboard(homeId)) },
                    onAddHome = { navController.navigate(ChildRoutes.ADD_HOME) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ChildRoutes.HOME_DASHBOARD,
                arguments = listOf(navArgument(HOME_DASHBOARD_HOME_ID_KEY) { type = NavType.StringType }),
            ) {
                HomeDashboardScreen(
                    onBack = { navController.popBackStack() },
                    onInviteOwner = { homeId ->
                        navController.navigate(ChildRoutes.inviteOwner(homeId, ""))
                    },
                    onClaimOwnership = { homeId ->
                        navController.navigate(ChildRoutes.claimOwnership(homeId))
                    },
                    onOpenClaimsList = { navController.navigate(ChildRoutes.MY_CLAIMS) },
                    onOpenBills = { homeId ->
                        navController.navigate(ChildRoutes.homeBills(homeId))
                    },
                    onOpenPlaceholder = { label ->
                        navController.navigate(ChildRoutes.placeholder(label))
                    },
                    onOpenPets = { homeId ->
                        navController.navigate(ChildRoutes.homePets(homeId))
                    },
                )
            }
            composable(
                route = ChildRoutes.HOME_BILLS,
                arguments = listOf(navArgument(BILLS_HOME_ID_KEY) { type = NavType.StringType }),
            ) { entry ->
                val homeId = entry.arguments?.getString(BILLS_HOME_ID_KEY).orEmpty()
                BillsListScreen(
                    onOpenBill = { billId ->
                        navController.navigate(ChildRoutes.billDetail(homeId, billId))
                    },
                    onAddBill = { navController.navigate(ChildRoutes.addBill(homeId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ChildRoutes.BILL_DETAIL,
                arguments =
                    listOf(
                        navArgument(BILL_DETAIL_HOME_ID_KEY) { type = NavType.StringType },
                        navArgument(BILL_DETAIL_BILL_ID_KEY) { type = NavType.StringType },
                    ),
            ) {
                BillDetailScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ChildRoutes.ADD_BILL,
                arguments = listOf(navArgument(ADD_BILL_HOME_ID_KEY) { type = NavType.StringType }),
            ) { entry ->
                val homeId = entry.arguments?.getString(ADD_BILL_HOME_ID_KEY).orEmpty()
                AddBillWizardScreen(
                    onClose = { navController.popBackStack() },
                    onCreated = { billId ->
                        // Replace the wizard with the bill detail so Back
                        // returns to the Bills list, not the success step.
                        navController.popBackStack()
                        navController.navigate(ChildRoutes.billDetail(homeId, billId))
                    },
                )
            }
            composable(
                route = ChildRoutes.HOME_PETS,
                arguments = listOf(navArgument(PETS_LIST_HOME_ID_KEY) { type = NavType.StringType }),
            ) {
                PetsListScreen(onBack = { navController.popBackStack() })
            }
            composable(ChildRoutes.MAILBOX_LIST) {
                MailboxListScreen(
                    onOpenMail = { mailId ->
                        navController.navigate(ChildRoutes.mailboxItemDetail(mailId))
                    },
                    onOpenSearch = { navController.navigate(ChildRoutes.MAILBOX_SEARCH) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ChildRoutes.MAILBOX_ITEM_DETAIL,
                arguments = listOf(navArgument(MAILBOX_ITEM_DETAIL_MAIL_ID_KEY) { type = NavType.StringType }),
            ) {
                MailboxItemDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSenderProfile = { userId ->
                        navController.navigate(ChildRoutes.publicProfile(userId))
                    },
                )
            }
            composable(
                route = ChildRoutes.PUBLIC_PROFILE,
                arguments = listOf(navArgument(PUBLIC_PROFILE_USER_ID_KEY) { type = NavType.StringType }),
            ) {
                PublicProfileScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMessages = { navController.navigate(ChildRoutes.placeholder("Messages")) },
                    onOpenReport = { navController.navigate(ChildRoutes.placeholder("Report")) },
                )
            }
            composable(
                route = ChildRoutes.PULSE_POST,
                arguments = listOf(navArgument(PULSE_POST_DETAIL_ID_KEY) { type = NavType.StringType }),
            ) {
                PulsePostDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { userId ->
                        navController.navigate(ChildRoutes.publicProfile(userId))
                    },
                )
            }
            composable(
                route = ChildRoutes.INVITE_OWNER,
                arguments =
                    listOf(
                        navArgument(INVITE_OWNER_HOME_ID_KEY) { type = NavType.StringType },
                        navArgument(INVITE_OWNER_CURRENT_EMAIL_KEY) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) {
                InviteOwnerFormScreen(onClose = { navController.popBackStack() })
            }
            composable(
                route = ChildRoutes.DISAMBIGUATE_MAIL,
                arguments = listOf(navArgument(DISAMBIGUATE_MAIL_ID_KEY) { type = NavType.StringType }),
            ) {
                DisambiguateMailFormScreen(onClose = { navController.popBackStack() })
            }
            composable(ChildRoutes.MAILBOX_DRAWERS) {
                MailboxDrawersScreen(
                    onOpenDrawer = { drawer ->
                        navController.navigate(ChildRoutes.placeholder("Drawer · $drawer"))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ChildRoutes.CHAT_CONVERSATION,
                arguments =
                    listOf(
                        navArgument(ChildRoutes.CHAT_KIND_KEY) { type = NavType.StringType },
                        navArgument(ChildRoutes.CHAT_ID_KEY) { type = NavType.StringType },
                        navArgument(ChildRoutes.CHAT_NAME_KEY) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument(ChildRoutes.CHAT_INITIALS_KEY) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument(ChildRoutes.CHAT_VERIFIED_KEY) {
                            type = NavType.StringType
                            defaultValue = "false"
                        },
                        navArgument(ChildRoutes.CHAT_IDENTITY_KEY) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument(ChildRoutes.CHAT_LOCALITY_KEY) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument(ChildRoutes.CHAT_ONLINE_KEY) {
                            type = NavType.StringType
                            defaultValue = "false"
                        },
                    ),
            ) { entry ->
                val args = entry.arguments ?: return@composable
                val kind = args.getString(ChildRoutes.CHAT_KIND_KEY).orEmpty()
                val id = args.getString(ChildRoutes.CHAT_ID_KEY).orEmpty()
                val name = args.getString(ChildRoutes.CHAT_NAME_KEY).orEmpty()
                val initials = args.getString(ChildRoutes.CHAT_INITIALS_KEY).orEmpty()
                val verified = args.getString(ChildRoutes.CHAT_VERIFIED_KEY) == "true"
                val locality = args.getString(ChildRoutes.CHAT_LOCALITY_KEY).orEmpty().takeIf { it.isNotEmpty() }
                val online = args.getString(ChildRoutes.CHAT_ONLINE_KEY) == "true"
                val mode: ChatThreadMode =
                    when (kind) {
                        "ai" -> ChatThreadMode.Ai
                        "room" -> ChatThreadMode.Room(id)
                        else -> ChatThreadMode.Person(otherUserId = id)
                    }
                val counterparty: ChatCounterparty =
                    when (kind) {
                        "ai" -> ChatCounterparty.Ai(displayName = name)
                        "room" -> ChatCounterparty.Group(displayName = name, memberCount = null)
                        else ->
                            ChatCounterparty.Person(
                                displayName = name,
                                initials = initials,
                                locality = locality,
                                verified = verified,
                                online = online,
                            )
                    }
                ChatConversationHost(
                    mode = mode,
                    counterparty = counterparty,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChildRoutes.PULSE_FEED) {
                FeedScreen(
                    onOpenPost = { postId -> navController.navigate(ChildRoutes.pulsePost(postId)) },
                    onCompose = { intent -> navController.navigate(ChildRoutes.composePost(intent.key)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChildRoutes.MARKETPLACE) {
                MarketplaceScreen(
                    onOpenListing = { listingId -> navController.navigate(ChildRoutes.listingDetail(listingId)) },
                    onCompose = { navController.navigate(ChildRoutes.COMPOSE_LISTING) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ChildRoutes.LISTING_DETAIL,
                arguments = listOf(navArgument(ChildRoutes.LISTING_DETAIL_ID_KEY) { type = NavType.StringType }),
            ) {
                ListingDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMessages = { navController.navigate(ChildRoutes.placeholder("Messages")) },
                    onViewOffers = { dto ->
                        navController.navigate(ChildRoutes.listingOffers(dto.id, dto.title))
                    },
                )
            }
            composable(
                route = ChildRoutes.LISTING_OFFERS,
                arguments =
                    listOf(
                        navArgument(ChildRoutes.LISTING_OFFERS_ID_KEY) { type = NavType.StringType },
                        navArgument(ChildRoutes.LISTING_OFFERS_TITLE_KEY) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) {
                ListingOffersScreen(
                    onBack = { navController.popBackStack() },
                    onShareListing = { navController.navigate(ChildRoutes.placeholder("Share listing")) },
                    onOpenBuyer = { navController.navigate(ChildRoutes.placeholder("Buyer profile")) },
                    onOpenTransaction = { navController.navigate(ChildRoutes.placeholder("Transaction detail")) },
                    onSort = { navController.navigate(ChildRoutes.placeholder("Sort offers")) },
                )
            }
            composable(
                route = ChildRoutes.INVOICE_DETAIL,
                arguments = listOf(navArgument(ChildRoutes.INVOICE_DETAIL_ID_KEY) { type = NavType.StringType }),
            ) {
                InvoiceDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(ChildRoutes.COMPOSE_LISTING) {
                NotYetAvailableView(tabName = "Snap & sell", icon = PantopusIcon.Camera)
            }
            composable(ChildRoutes.GIGS_FEED) {
                GigsFeedScreen(
                    onOpenGig = { gigId -> navController.navigate(ChildRoutes.gigDetail(gigId)) },
                    onCompose = { category -> navController.navigate(ChildRoutes.composeGig(category.key)) },
                    onOpenMap = { category -> navController.navigate(ChildRoutes.nearbyMapForGigs(category.key)) },
                    onOpenSearch = { navController.navigate(ChildRoutes.placeholder("Gig search")) },
                    onOpenFilters = { navController.navigate(ChildRoutes.placeholder("Gig filters")) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ChildRoutes.GIG_DETAIL,
                arguments = listOf(navArgument(ChildRoutes.GIG_DETAIL_ID_KEY) { type = NavType.StringType }),
            ) {
                GigDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMessages = { navController.navigate(ChildRoutes.placeholder("Messages")) },
                )
            }
            composable(
                route = ChildRoutes.NEARBY_MAP_FOR_GIGS,
                arguments =
                    listOf(
                        navArgument(ChildRoutes.NEARBY_MAP_FOR_GIGS_CATEGORY_KEY) {
                            type = NavType.StringType
                            defaultValue = GigsCategory.All.key
                        },
                    ),
            ) { entry ->
                val raw =
                    entry.arguments?.getString(ChildRoutes.NEARBY_MAP_FOR_GIGS_CATEGORY_KEY) ?: GigsCategory.All.key
                NearbyMapScreen(
                    onOpenEntity = { entity ->
                        when (entity.kind) {
                            MapEntityKind.Gig -> navController.navigate(ChildRoutes.gigDetail(entity.id))
                            MapEntityKind.Listing -> navController.navigate(ChildRoutes.listingDetail(entity.id))
                        }
                    },
                    onOpenFilters = { navController.navigate(ChildRoutes.placeholder("Map filters")) },
                    onBack = { navController.popBackStack() },
                    initialCategory = GigsCategory.fromBackendKey(raw),
                )
            }
            composable(
                route = ChildRoutes.COMPOSE_GIG,
                arguments =
                    listOf(
                        navArgument(ChildRoutes.COMPOSE_GIG_CATEGORY_KEY) {
                            type = NavType.StringType
                            defaultValue = GigsCategory.All.key
                        },
                    ),
            ) { entry ->
                val raw = entry.arguments?.getString(ChildRoutes.COMPOSE_GIG_CATEGORY_KEY) ?: GigsCategory.All.key
                val label =
                    GigsCategory.entries.firstOrNull { it.key == raw }?.label ?: raw.replaceFirstChar { it.uppercase() }
                NotYetAvailableView(tabName = "Post a task · $label", icon = PantopusIcon.Pencil)
            }
            composable(
                route = ChildRoutes.COMPOSE_POST,
                arguments =
                    listOf(
                        navArgument(ChildRoutes.COMPOSE_INTENT_KEY) {
                            type = NavType.StringType
                            defaultValue = PulseIntent.All.key
                        },
                    ),
            ) { entry ->
                val raw = entry.arguments?.getString(ChildRoutes.COMPOSE_INTENT_KEY) ?: PulseIntent.All.key
                val intent = PulseIntent.fromKey(raw)
                NotYetAvailableView(
                    tabName = "Compose · ${intent.label}",
                    icon = PantopusIcon.Pencil,
                )
            }
            composable(ChildRoutes.NOTIFICATIONS) {
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
            composable(ChildRoutes.CONNECTIONS) {
                ConnectionsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { target: ConnectionsChatTarget ->
                        val row =
                            ConversationRowContent(
                                id = target.userId,
                                variant = ConversationRowVariant.Dm,
                                displayName = target.displayName,
                                initials = target.initials,
                                avatarUrl = null,
                                identityChip = null,
                                verified = target.verified,
                                preview = "",
                                timeLabel = "",
                                unread = 0,
                                pinned = false,
                                topicKinds = emptySet(),
                            )
                        navController.navigate(ChildRoutes.chatConversation(row))
                    },
                    onFindPeople = {
                        navController.navigate(ChildRoutes.placeholder("Find people"))
                    },
                )
            }
            composable(ChildRoutes.DISCOVER_HUB) {
                DiscoverHubScreen(
                    onBack = { navController.popBackStack() },
                    onSelect = { target ->
                        when (target) {
                            is DiscoverHubTarget.Person ->
                                navController.navigate(ChildRoutes.publicProfile(target.userId))
                            is DiscoverHubTarget.Business ->
                                // Per buildout plan F6, the typed business
                                // profile screen lands later — push the
                                // discover-businesses placeholder for now.
                                navController.navigate(ChildRoutes.DISCOVER_BUSINESSES)
                            is DiscoverHubTarget.Gig ->
                                navController.navigate(ChildRoutes.gigDetail(target.gigId))
                            is DiscoverHubTarget.Listing ->
                                navController.navigate(ChildRoutes.listingDetail(target.listingId))
                            DiscoverHubTarget.SeeAllPeople ->
                                navController.navigate(ChildRoutes.CONNECTIONS)
                            DiscoverHubTarget.SeeAllBusinesses ->
                                navController.navigate(ChildRoutes.DISCOVER_BUSINESSES)
                            DiscoverHubTarget.SeeAllGigs ->
                                navController.navigate(ChildRoutes.GIGS_FEED)
                            DiscoverHubTarget.SeeAllListings ->
                                navController.navigate(ChildRoutes.MARKETPLACE)
                            DiscoverHubTarget.OpenFilters ->
                                navController.navigate(ChildRoutes.placeholder("Discovery filters"))
                        }
                    },
                )
            }
            composable(ChildRoutes.DISCOVER_BUSINESSES) {
                DiscoverBusinessesScreen(
                    onBack = { navController.popBackStack() },
                    onSelect = { target ->
                        when (target) {
                            is DiscoverBusinessesTarget.Business ->
                                // The dedicated business-profile screen is
                                // still pending. Use the placeholder until
                                // it lands.
                                navController.navigate(
                                    ChildRoutes.placeholder("Business: ${target.name} (${target.businessId})"),
                                )
                            DiscoverBusinessesTarget.OpenFilters ->
                                navController.navigate(ChildRoutes.placeholder("Business filters"))
                            DiscoverBusinessesTarget.WidenRadius ->
                                navController.navigate(ChildRoutes.placeholder("Set home address"))
                            DiscoverBusinessesTarget.InviteBusiness ->
                                navController.navigate(ChildRoutes.placeholder("Invite a business"))
                        }
                    },
                )
            }
            composable(ChildRoutes.OFFERS) {
                OffersScreen(
                    onBack = { navController.popBackStack() },
                    onOpenOfferDetail = { dto ->
                        val gigId = dto.gigId ?: dto.gig?.id
                        if (!gigId.isNullOrBlank()) {
                            navController.navigate(ChildRoutes.gigDetail(gigId))
                        }
                    },
                    onOpenFilters = { navController.navigate(ChildRoutes.placeholder("Offer filters")) },
                    onBrowseListings = { navController.navigate(ChildRoutes.placeholder("Browse listings")) },
                    onPostTask = { navController.navigate(ChildRoutes.placeholder("Post a task")) },
                )
            }
            composable(ChildRoutes.MY_BIDS) {
                MyBidsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBid = { dto ->
                        val gigId = dto.gigId ?: dto.gig?.id
                        if (!gigId.isNullOrBlank()) {
                            navController.navigate(ChildRoutes.gigDetail(gigId))
                        }
                    },
                    onOpenFilters = { navController.navigate(ChildRoutes.placeholder("Filter bids")) },
                    onBrowseTasks = { navController.navigate(ChildRoutes.GIGS_FEED) },
                    onMessageClient = { dto ->
                        // Push to gig detail; "Message poster" is wired there.
                        val gigId = dto.gigId ?: dto.gig?.id
                        if (!gigId.isNullOrBlank()) {
                            navController.navigate(ChildRoutes.gigDetail(gigId))
                        }
                    },
                    onEditBid = { dto ->
                        val gigId = dto.gigId ?: dto.gig?.id
                        if (!gigId.isNullOrBlank()) {
                            navController.navigate(ChildRoutes.gigDetail(gigId))
                        }
                    },
                    onLeaveReview = { dto ->
                        val gigId = dto.gigId ?: dto.gig?.id
                        if (!gigId.isNullOrBlank()) {
                            navController.navigate(ChildRoutes.gigDetail(gigId))
                        }
                    },
                )
            }
            composable(ChildRoutes.MY_TASKS) {
                MyTasksScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTask = { dto -> navController.navigate(ChildRoutes.gigDetail(dto.id)) },
                    onOpenFilters = { navController.navigate(ChildRoutes.placeholder("Filter tasks")) },
                    onOpenBids = { dto -> navController.navigate(ChildRoutes.gigDetail(dto.id)) },
                    onEditTask = { dto -> navController.navigate(ChildRoutes.gigDetail(dto.id)) },
                    onMessageWorker = { dto -> navController.navigate(ChildRoutes.gigDetail(dto.id)) },
                    onLeaveReview = { dto -> navController.navigate(ChildRoutes.gigDetail(dto.id)) },
                    onPostTask = { navController.navigate(ChildRoutes.COMPOSE_TASK) },
                    onRepost = { navController.navigate(ChildRoutes.COMPOSE_TASK) },
                )
            }
            composable(ChildRoutes.COMPOSE_TASK) {
                NotYetAvailableView(tabName = "Post a task", icon = PantopusIcon.Pencil)
            }
            composable(ChildRoutes.MY_POSTS) {
                MyPostsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPost = { navController.navigate(ChildRoutes.placeholder("Post detail")) },
                    onOpenFilters = { navController.navigate(ChildRoutes.placeholder("Filter posts")) },
                    onCompose = { navController.navigate(ChildRoutes.placeholder("Write a post")) },
                    onEditPost = { navController.navigate(ChildRoutes.placeholder("Edit post")) },
                )
            }
            composable(ChildRoutes.MENU) {
                SettingsIndexScreen(
                    onClose = { navController.popBackStack() },
                    onNavigate = { route ->
                        when (route) {
                            SettingsRoute.Notifications -> navController.navigate(ChildRoutes.SETTINGS_NOTIFICATIONS)
                            SettingsRoute.Privacy -> navController.navigate(ChildRoutes.SETTINGS_PRIVACY)
                            SettingsRoute.IdentityCenter -> navController.navigate(ChildRoutes.IDENTITY_CENTER)
                            SettingsRoute.EditProfile -> navController.navigate(ChildRoutes.placeholder("Edit profile"))
                            SettingsRoute.Password -> navController.navigate(ChildRoutes.placeholder("Password"))
                            SettingsRoute.Verification -> navController.navigate(ChildRoutes.placeholder("Verification"))
                            SettingsRoute.Blocks -> navController.navigate(ChildRoutes.placeholder("Blocked users"))
                            SettingsRoute.DataExport -> navController.navigate(ChildRoutes.placeholder("Data export"))
                            SettingsRoute.PaymentsPayouts -> navController.navigate(ChildRoutes.placeholder("Payments & payouts"))
                            SettingsRoute.Help -> navController.navigate(ChildRoutes.placeholder("Help"))
                            SettingsRoute.Legal -> navController.navigate(ChildRoutes.placeholder("Legal"))
                            SettingsRoute.About -> navController.navigate(ChildRoutes.placeholder("About"))
                            SettingsRoute.DidSignOut -> navController.popBackStack()
                        }
                    },
                )
            }
            composable(ChildRoutes.SETTINGS_NOTIFICATIONS) {
                NotificationSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(ChildRoutes.SETTINGS_PRIVACY) {
                PrivacySettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = ChildRoutes.PRIVACY_HANDSHAKE,
                arguments =
                    listOf(
                        navArgument(ChildRoutes.PRIVACY_HANDSHAKE_HANDLE_KEY) {
                            type = NavType.StringType
                        },
                    ),
            ) {
                PrivacyHandshakeScreen(
                    onDismiss = { navController.popBackStack() },
                )
            }
            composable(
                route = ChildRoutes.TOKEN_ACCEPT,
                arguments =
                    listOf(
                        navArgument(ChildRoutes.TOKEN_ACCEPT_TOKEN_KEY) {
                            type = NavType.StringType
                        },
                    ),
            ) {
                TokenAcceptScreen(
                    onDismiss = { navController.popBackStack() },
                )
            }
            composable(ChildRoutes.CEREMONIAL_MAIL) {
                CeremonialMailWizardScreen(
                    onDismiss = { navController.popBackStack() },
                    onOpenMail = { mailId ->
                        navController.popBackStack()
                        navController.navigate(ChildRoutes.mailboxItemDetail(mailId))
                    },
                )
            }
            composable(
                route = ChildRoutes.CEREMONIAL_MAIL_OPEN,
                arguments =
                    listOf(
                        navArgument(ChildRoutes.CEREMONIAL_MAIL_OPEN_ID_KEY) {
                            type = NavType.StringType
                        },
                    ),
            ) {
                CeremonialMailOpenScreen(
                    onBack = { navController.popBackStack() },
                    onWriteBack = {
                        navController.navigate(ChildRoutes.CEREMONIAL_MAIL)
                    },
                )
            }
            composable(ChildRoutes.AUDIENCE_PROFILE) {
                AudienceProfileScreen(
                    onBack = { navController.popBackStack() },
                    onOpenFollower = { row ->
                        navController.navigate(ChildRoutes.placeholder("Follower · ${row.displayName}"))
                    },
                    onOpenThread = { row ->
                        navController.navigate(ChildRoutes.placeholder("Thread · ${row.displayName}"))
                    },
                    onOpenSetup = {
                        navController.navigate(ChildRoutes.placeholder("Set up Public Profile"))
                    },
                )
            }
            composable(ChildRoutes.IDENTITY_CENTER) {
                IdentityCenterScreen(
                    onBack = { navController.popBackStack() },
                    onOpenIdentity = { card ->
                        when (card.kind) {
                            IdentityKind.PublicProfile ->
                                navController.navigate(ChildRoutes.AUDIENCE_PROFILE)
                            IdentityKind.Local ->
                                navController.navigate(ChildRoutes.placeholder("Local profile"))
                            IdentityKind.Personal ->
                                navController.navigate(ChildRoutes.placeholder("Personal"))
                            IdentityKind.Professional ->
                                navController.navigate(ChildRoutes.placeholder("Professional"))
                        }
                    },
                    onOpenPlaceholder = { label ->
                        navController.navigate(ChildRoutes.placeholder(label))
                    },
                )
            }
            composable(ChildRoutes.MAILBOX_SEARCH) {
                NotYetAvailableView(tabName = "Mail search", icon = PantopusIcon.Search)
            }
            composable(
                route = ChildRoutes.PLACEHOLDER,
                arguments =
                    listOf(
                        navArgument(ChildRoutes.PLACEHOLDER_LABEL_KEY) {
                            type = NavType.StringType
                            defaultValue = "This"
                        },
                    ),
            ) { entry ->
                val label = entry.arguments?.getString(ChildRoutes.PLACEHOLDER_LABEL_KEY) ?: "This"
                NotYetAvailableView(tabName = label, icon = PantopusIcon.Info)
            }
            composable(ChildRoutes.ADD_HOME) {
                AddHomeWizardScreen(
                    onDismiss = { navController.popBackStack() },
                    onOpenHomeDashboard = { homeId ->
                        // Pop the wizard then push the dashboard so Back
                        // returns to MyHomes, not the success screen.
                        navController.popBackStack()
                        navController.navigate(ChildRoutes.homeDashboard(homeId))
                    },
                )
            }
            composable(
                route = ChildRoutes.CLAIM_OWNERSHIP,
                arguments = listOf(navArgument(CLAIM_OWNERSHIP_HOME_ID_KEY) { type = NavType.StringType }),
            ) {
                ClaimOwnershipWizardScreen(
                    onDismiss = { navController.popBackStack() },
                    onOpenClaimsList = {
                        navController.popBackStack()
                        navController.navigate(ChildRoutes.MY_CLAIMS)
                    },
                )
            }
            composable(ChildRoutes.MY_CLAIMS) {
                MyClaimsListScreen(
                    onStartNewClaim = { navController.navigate(ChildRoutes.ADD_HOME) },
                    onOpenClaim = { _ ->
                        navController.navigate(ChildRoutes.placeholder("Claim status"))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            if (BuildConfig.DEBUG) {
                composable(ChildRoutes.TOKEN_GALLERY) { TokenGalleryScreen() }
            }
        }
    }
}

/**
 * Wraps [HubScreen] with a 44dp invisible 5-tap target in the top-leading
 * corner so debug builds can jump to the token gallery — the production
 * hub hides its toolbar so there's no visible title to attach to. No-op in
 * release builds; semantically hidden so TalkBack can't trip it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
@Suppress("ModifierMissing")
private fun HubWithDebugFiveTap(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    if (!BuildConfig.DEBUG) {
        content()
        return
    }
    Box {
        content()
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .size(44.dp)
                    .semantics { invisibleToUser() }
                    .pointerInput(Unit) {
                        var taps = 0
                        var lastTap = 0L
                        detectTapGestures(onTap = {
                            val now = System.currentTimeMillis()
                            taps = if (now - lastTap < FIVE_TAP_WINDOW_MS) taps + 1 else 1
                            lastTap = now
                            if (taps >= 5) {
                                taps = 0
                                navController.navigate(ChildRoutes.TOKEN_GALLERY)
                            }
                        })
                    },
        )
    }
}

private const val FIVE_TAP_WINDOW_MS: Long = 1_500L

/**
 * Dispatch a Hub discovery-card tap to the matching detail route. Items
 * whose detail screen isn't built yet land on the generic placeholder
 * with a labelled title.
 */
private fun routeForDiscovery(item: DiscoveryCardContent): String =
    when (item.kind) {
        DiscoveryKind.Post -> ChildRoutes.pulsePost(item.id)
        DiscoveryKind.Person -> ChildRoutes.publicProfile(item.id)
        DiscoveryKind.Gig -> ChildRoutes.placeholder("Gig detail")
        DiscoveryKind.Business -> ChildRoutes.placeholder("Business")
        DiscoveryKind.Unknown -> ChildRoutes.placeholder(item.title)
    }

/**
 * Backend `jumpBackIn` items carry a canonical web route (e.g.
 * `/app/mailbox?scope=home&homeId=…`, `/app/homes/<id>/dashboard`,
 * `/app/chat`, `/gigs/new`). Map onto a native destination; fall back
 * to a labelled placeholder when nothing matches.
 */
private fun routeForJumpBackIn(item: JumpBackItem): String {
    val path = item.route
    if (path.startsWith("/app/mailbox")) return ChildRoutes.MAILBOX_LIST
    homeIdFromRoute(path)?.let { return ChildRoutes.homeDashboard(it) }
    if (path.startsWith("/app/chat")) return ChildRoutes.placeholder("Messages")
    if (path.startsWith("/gigs/new")) return ChildRoutes.composeGig(GigsCategory.All.key)
    if (path.startsWith("/gigs")) return ChildRoutes.GIGS_FEED
    return ChildRoutes.placeholder(item.title)
}

/** Extracts `<id>` from `/app/homes/<id>/dashboard`. */
private fun homeIdFromRoute(route: String): String? {
    val prefix = "/app/homes/"
    if (!route.startsWith(prefix)) return null
    val segment = route.removePrefix(prefix).substringBefore('/')
    return segment.takeIf { it.isNotEmpty() }
}
