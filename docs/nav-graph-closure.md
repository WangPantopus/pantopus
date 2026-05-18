# Navigation graph closure check

> Generated 2026-05-18. Read-only audit. Walks every navigation entry
> point on both platforms and surfaces orphan routes, dead screens, and
> the placeholder backlog with proposed dispositions.

## Scope and method

**iOS:** start from `Features/Root/RootTabView.swift` (4 tabs: Hub, Nearby,
Inbox, You). Each tab owns a `…TabRoot.swift` containing a typed
`…Route: Hashable` enum and a `destination(for:)` switch. For every
route case we trace (a) what View it produces and (b) every call site
that pushes it.

**Android:** start from `screens/root/RootTabScreen.kt` (single
NavHost). Every `composable(ChildRoutes.X)` block is a route; every
`navController.navigate(ChildRoutes.X)` is a call site.

## Section 1 — iOS route trees

### HubRoute (47 cases)

All cases below are declared in
`frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` lines 13–123
and matched in `destination(for:)`.

| Case | Destination | Status | Reachable from |
|---|---|---|---|
| `.myHomes` | `MyHomesListView` | LIVE | `HubView` Homes pillar, `MeView` action grid (via You-tab) |
| `.myClaims` | `MyClaimsListView` | LIVE | `HomeDashboardView.onOpenClaimsList`, `ClaimOwnershipWizardView.onCompletion` |
| `.mailboxDrawers` | `MailboxDrawersView` | LIVE | `HubView` Scan-mail action, Mail pillar |
| `.mailbox` | `MailboxListView` | LIVE | `HubView` Mail pillar, `jumpBackIn(/app/mailbox)`, `VaultListView.goToMailbox` |
| `.mailItemDetail(mailId:)` | `MailDetailView` | LIVE | `MailboxListView.onOpenMail`, `VaultListView.onOpenItem` |
| `.drawerDetail(drawer:)` | `NotYetAvailableView("Drawer · <drawer>")` | STUB | `MailboxDrawersView.onOpenDrawer` |
| `.mailboxVault` | `VaultListView` | LIVE | `MailboxDrawersView.onOpenVault` |
| `.addHome` | `AddHomeWizardView` | LIVE | `HubView` Add-home action, Setup banner, `MyHomesListView.onAddHome`, `MyClaimsListView.onStartNewClaim` |
| `.claimOwnership(homeId:)` | `ClaimOwnershipWizardView` | LIVE | `HomeDashboardView.onClaimOwnership` |
| `.homeDashboard(homeId:)` | `HomeDashboardView` | LIVE | `MyHomesListView.onOpenHome`, `jumpBackIn(/app/homes/:id/dashboard)`, `AddHomeWizardView.onSuccess` |
| `.homePets(homeId:)` | `PetsListView` | LIVE | `HomeDashboardView.onOpenPets` |
| `.homeEmergency(homeId:)` | `EmergencyInfoView` | LIVE | `HomeDashboardView.onOpenEmergency` |
| `.homeDocs(homeId:)` | `DocumentsView` | LIVE | `HomeDashboardView.onOpenDocs` |
| `.homePackages(homeId:)` | `PackagesListView` | LIVE | `HomeDashboardView.onOpenPackages` |
| `.packageDetail(homeId:, packageId:)` | `PackageDetailView` | LIVE | `PackagesListView.onOpenPackage` |
| `.logPackage(homeId:)` | `LogPackageSheetView` | LIVE | `PackagesListView.onLogPackage` |
| `.homeTasks(homeId:)` | `HouseholdTasksListView` | LIVE | `HomeDashboardView.onOpenTasks` |
| `.homeMaintenance(homeId:)` | `MaintenanceListView` | LIVE | `HomeDashboardView.onOpenMaintenance` |
| `.homeMembers(homeId:)` | `MembersListView` | LIVE | `HomeDashboardView.onOpenMembers` |
| `.publicProfile(userId:)` | `PublicProfileView` | LIVE | `MailDetailView.onOpenSenderProfile`, `PulsePostDetailView.onOpenProfile`, `DiscoverHubView.onSelect(person:)` |
| `.pulsePost(postId:)` | `PulsePostDetailView` | LIVE | `FeedView.onOpenPost`, Hub discovery card (`kind: .post`) |
| `.homeBills(homeId:)` | `BillsListView` | LIVE | `HomeDashboardView.onOpenBills` |
| `.homeCalendar(homeId:)` | `HomeCalendarView` | LIVE | `HomeDashboardView.onOpenCalendar` |
| `.billDetail(homeId:, billId:)` | `BillDetailView` | LIVE | `BillsListView.onOpenBill` |
| `.addBill(homeId:)` | `AddBillWizardView` | LIVE | `BillsListView.onAddBill` |
| `.homePolls(homeId:)` | `PollsListView` | LIVE | `HomeDashboardView.onOpenPolls` |
| `.pollDetail(homeId:, pollId:)` | `PollDetailView` | LIVE | `PollsListView.onOpenPoll` |
| `.pulseFeed` | `FeedView` | LIVE | `HubView` Pulse pillar |
| `.composePost(intent:)` | `NotYetAvailableView("Compose · <intent>")` | STUB | `FeedView.onCompose` |
| `.gigsFeed` | `GigsFeedView` | LIVE | `HubView` Gigs pillar, `jumpBackIn(/gigs)`, `MyBidsView.onBrowseTasks` |
| `.gigDetail(gigId:)` | `GigDetailView` | LIVE | `GigsFeedView.onOpenGig`, `NearbyMapView.onOpenEntity(gig)`, `DiscoverHubView.onSelect(gig:)`, `MyBidsView` row taps |
| `.nearbyMapForGigs(categoryKey:)` | `NearbyMapView` | LIVE | `GigsFeedView.onOpenMap` |
| `.composeGig(category:)` | `NotYetAvailableView("Post a task · <category>")` | STUB | `GigsFeedView.onCompose`, `jumpBackIn(/gigs/new)` |
| `.marketplace` | `MarketplaceView` | LIVE | `HubView` Marketplace pillar, `DiscoverHubView.onSelect(seeAllListings:)` |
| `.listingDetail(listingId:)` | `ListingDetailView` | LIVE | `MarketplaceView.onOpenListing`, `NearbyMapView.onOpenEntity(listing)`, `DiscoverHubView.onSelect(listing:)` |
| `.composeListing` | `NotYetAvailableView("Snap & sell")` | STUB | `MarketplaceView.onCompose` |
| `.invoiceDetail(invoiceId:)` | `InvoiceDetailView` | **ORPHAN** | No inbound caller — intended for future wallet/payments integration (carries-over from T2.6 Transactional Detail shell scaffolding). |
| `.notifications` | `NotificationsView` | LIVE | `HubView.onBellTap` |
| `.connections` | `ConnectionsView` | LIVE | Deep link `pantopus://connections`, `DiscoverHubView.onSelect(seeAllPeople:)` |
| `.supportTrains` | `SupportTrainsView` | LIVE | Deep link `pantopus://support-trains` |
| `.reviewSignups(supportTrainId:)` | `ReviewSignupsView` | LIVE | Deep link `pantopus://support-trains/:id`, `SupportTrainsView.onOpenTrain` |
| `.myBids` | `MyBidsView` | LIVE | (reached via `YouRoute.myBids` mirror; HubRoute case retained for future Hub-side bid surface) |
| `.listingOffers(listingId:, title:)` | `ListingOffersView` | LIVE | `ListingDetailView.onViewOffers` |
| `.discoverHub` | `DiscoverHubView` | LIVE | Deep link `pantopus://discover-hub`, `HubView.openDiscoverHub` |
| `.discoverBusinesses` | `DiscoverBusinessesView` | LIVE | `DiscoverHubView.onSelect(business:)` / `(seeAllBusinesses:)` |
| `.chatConversation(InboxConversationDestination)` | `ChatConversationView` | LIVE | `ConnectionsView.onMessage`, `MyBidsView.onMessageClient` |
| `.menu` | `SettingsView` | LIVE | `HubView.onMenuTap` |
| `.mailboxSearch` | `NotYetAvailableView("Mail search")` | STUB | `MailboxListView.onOpenSearch` |
| `.placeholder(label:)` | `NotYetAvailableView(label)` | STUB | Catch-all for unbuilt affordances (~40 inbound pushes) |
| `.tokenGallery` *(DEBUG)* | `TokenGalleryView` | LIVE (DEBUG) | 5-tap easter egg on Hub corner |
| `.iconGallery` *(DEBUG)* | `IconGalleryView` | LIVE (DEBUG) | 5-tap easter egg on Hub corner |
| `.componentGallery` *(DEBUG)* | `ComponentGalleryView` | LIVE (DEBUG) | 5-tap easter egg on Hub corner |

### NearbyRoute (4 cases)

Declared in `frontend/apps/ios/Pantopus/Features/Root/NearbyTabRoot.swift`.
The Nearby tab roots at `NearbyMapView` (no NavigationStack entry —
push happens from the map's callbacks).

| Case | Destination | Status | Reachable from |
|---|---|---|---|
| `.entityDetail(kind: MapEntityKind, id:)` | `GigDetailView` (kind=.gig) / `ListingDetailView` (kind=.listing) | LIVE | `NearbyMapView.onOpenEntity` |
| `.filters` | `NotYetAvailableView("Map filters")` | STUB | `NearbyMapView.onOpenFilters` |
| `.placeholder(label:)` | `NotYetAvailableView(label)` | STUB | `ListingOffersView` callbacks (share/buyer/transaction/edit/sort) |
| `.listingOffers(listingId:, title:)` | `ListingOffersView` | LIVE | `ListingDetailView.onViewOffers` (within Nearby tab stack) |

### InboxRoute (4 cases)

Declared in `frontend/apps/ios/Pantopus/Features/Root/InboxTabRoot.swift`. The Inbox tab roots at `ChatListView`.

| Case | Destination | Status | Reachable from |
|---|---|---|---|
| `.conversation(InboxConversationDestination)` | `ChatConversationView` | LIVE | `ChatListView.onOpenConversation`, `NewMessageView.onSelect` (after dismiss) |
| `.compose` | `NewMessageView` | LIVE | `ChatListView.onCompose` |
| `.invite` | `NotYetAvailableView("Invite to Pantopus")` | STUB | `NewMessageView.onInvite` |
| `.search` | `NotYetAvailableView("Chat search")` | STUB | `ChatListView.onOpenSearch` |

### YouRoute (43 cases)

Declared in `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift`
lines 13–55 and matched in `destination(for:)`. The You tab roots at `MeView`.

| Case | Destination | Status | Reachable from |
|---|---|---|---|
| `.signOutConfirm` | `EmptyView()` | **DEAD** | Declared in enum but never pushed; sign-out uses a separate `confirmationDialog` in `MeView`. Stale enum case — safe to remove. |
| `.mailbox` | `MailboxListView` | LIVE | `handleAction(me.mail)` |
| `.mailItemDetail(mailId:)` | `MailDetailView` | LIVE | `MailboxListView.onOpenMail` (You-tab stack) |
| `.settings` | `SettingsView` | LIVE | `handleSection(me.settings)` |
| `.placeholder(label:)` | `NotYetAvailableView(label)` | STUB | Catch-all (~80 inbound pushes from action grid / section taps) |
| `.offers` | `OffersView` | LIVE | `handleAction(me.offers)`, `handleSection(me.offers)` |
| `.myBids` | `MyBidsView` | LIVE | `handleAction(me.bids)`, `handleSection(me.bids)` |
| `.myTasks` | `MyTasksView` | LIVE | `handleAction(me.gigs)`, `handleSection(me.gigs)` |
| `.composeTask` | `NotYetAvailableView("Post a task")` | STUB | `MyTasksView.onPostTask`, `onRepost` |
| `.myPosts` | `MyPostsView` | LIVE | `handleAction(me.posts)`, `handleSection(me.posts)` |
| `.connections` | `ConnectionsView` | LIVE | `handleAction(me.connections)`, `handleSection(me.connections)` |
| `.supportTrains` | `SupportTrainsView` | LIVE | `handleAction(me.supportTrains)`, `handleSection(me.supportTrains)` |
| `.reviewSignups(supportTrainId:)` | `ReviewSignupsView` | LIVE | `SupportTrainsView.onOpenTrain` |
| `.myHomes` | `MyHomesListView` | LIVE | `handleAction(me.homes)`, `handleSection(me.homes)` |
| `.myListings` | `MyListingsView` | LIVE | `handleAction(me.listings)`, `handleSection(me.listings)` |
| `.myBusinesses` | `MyBusinessesView` | LIVE | `handleAction(me.businesses)`, `handleSection(me.businesses)` |
| `.homeDashboard(homeId:)` | `HomeDashboardView` | LIVE | `MyHomesListView.onOpenHome` (You-tab stack) |
| `.identityCenter` | `IdentityCenterView` | LIVE | `handleSection(me.identityCenter)` |
| `.audienceProfile` | `AudienceProfileView` | LIVE | `handleSection(me.audience)` |
| `.homeBills(homeId:)` | `BillsListView` | LIVE | action/section handlers + `HomeDashboardView.onOpenBills` (You-tab) |
| `.homePets(homeId:)` | `PetsListView` | LIVE | action handler + `HomeDashboardView.onOpenPets` (You-tab) |
| `.homeCalendar(homeId:)` | `HomeCalendarView` | LIVE | action/section handlers + `HomeDashboardView.onOpenCalendar` (You-tab) |
| `.homeEmergency(homeId:)` | `EmergencyInfoView` | LIVE | action/section handlers + `HomeDashboardView.onOpenEmergency` (You-tab) |
| `.homeDocs(homeId:)` | `DocumentsView` | LIVE | action/section handlers + `HomeDashboardView.onOpenDocs` (You-tab) |
| `.homePackages(homeId:)` | `PackagesListView` | LIVE | action/section handlers + `HomeDashboardView.onOpenPackages` (You-tab) |
| `.packageDetail(homeId:, packageId:)` | `PackageDetailView` | LIVE | `PackagesListView.onOpenPackage` (You-tab stack) |
| `.logPackage(homeId:)` | `LogPackageSheetView` | LIVE | `PackagesListView.onLogPackage` (You-tab stack) |
| `.homePolls(homeId:)` | `PollsListView` | LIVE | action/section handlers + `HomeDashboardView.onOpenPolls` (You-tab) |
| `.pollDetail(homeId:, pollId:)` | `PollDetailView` | LIVE | `PollsListView.onOpenPoll` (You-tab) |
| `.accessCodes(homeId:, homeName:)` | `AccessCodesView` | LIVE | `handleSection(me.access)` |
| `.homeTasks(homeId:)` | `HouseholdTasksListView` | LIVE | action/section handlers + `HomeDashboardView.onOpenTasks` (You-tab) |
| `.homeMaintenance(homeId:)` | `MaintenanceListView` | LIVE | action/section handlers + `HomeDashboardView.onOpenMaintenance` (You-tab) |
| `.homeOwners(homeId:)` | `OwnersListView` | LIVE | `handleSection(me.owners)` |
| `.homeMembers(homeId:)` | `MembersListView` | LIVE | `handleSection(me.members)`, `HomeDashboardView.onOpenMembers` (You-tab) |
| `.listingOffers(listingId:, title:)` | `ListingOffersView` | LIVE | `ListingDetailView.onViewOffers` (You-tab stack) |
| `.gigDetail(gigId:)` | `GigDetailView` | LIVE | `OffersView.onOpenOfferDetail`, `MyBidsView.onOpenBid`, multiple `MyTasksView` callbacks |
| `.listingDetail(listingId:)` | `ListingDetailView` | LIVE | `MyListingsView.onOpenListing` |
| `.publicProfile(userId:)` *(DEBUG)* | `PublicProfileView` | LIVE (DEBUG) | DEBUG alert "Open profile" |
| `.pulsePost(postId:)` *(DEBUG)* | `PulsePostDetailView` | LIVE (DEBUG) | DEBUG alert "Open post" |
| `.privacyHandshake(personaHandle:)` *(DEBUG)* | `PrivacyHandshakeWizardView` | LIVE (DEBUG) | DEBUG alert |
| `.statusWaiting` *(DEBUG)* | `StatusWaitingView` | LIVE (DEBUG) | `handleSection(me.debug.openStatusWaiting)` |
| `.ceremonialMail` *(DEBUG)* | `CeremonialMailWizardView` | LIVE (DEBUG) | `handleSection(me.debug.openCeremonialMail)` |
| `.ceremonialMailOpen(mailId:)` *(DEBUG)* | `CeremonialMailOpenView` | LIVE (DEBUG) | DEBUG alert + `CeremonialMailOpenView.onWriteBack` |

## Section 2 — Android route graph

Declared as `const val` inside the `ChildRoutes` object in
`frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt`
(constants on lines 169-330) and matched by `composable(ChildRoutes.X) { … }`
blocks inside the same file (~lines 800–1880).

| Constant | Pattern | Destination | Status | Reachable from |
|---|---|---|---|---|
| `MY_HOMES` | `homes/my-homes` | `MyHomesListScreen` | LIVE | `HubScreen` Homes pillar, `YouScreen.onOpenMyHomes`, `HomeDashboardScreen.onAddHome` |
| `MY_CLAIMS` | `homes/my-claims` | `MyClaimsListScreen` | LIVE | `HomeDashboardScreen.onOpenClaimsList` |
| `ADD_HOME` | `homes/add` | `AddHomeWizardScreen` | LIVE | `MyHomesListScreen.onAddHome`, `YouScreen.onAddHome`, Hub Setup banner |
| `CLAIM_OWNERSHIP` | `homes/{homeId}/claim` | `ClaimOwnershipWizardScreen` | LIVE | `HomeDashboardScreen.onClaimOwnership` |
| `MAILBOX_LIST` | `mailbox/list` | `MailboxListScreen` | LIVE | `MailboxDrawersScreen.onOpenMailbox`, `VaultListScreen` |
| `MAILBOX_DRAWERS` | `mailbox/drawers` | `MailboxDrawersScreen` | LIVE | `HubScreen` Mail pillar |
| `MAILBOX_SEARCH` | `mailbox/search` | `NotYetAvailableView("Mail search")` | STUB | `MailboxListScreen.onOpenSearch` |
| `MAILBOX_ITEM_DETAIL` | `mailbox/item/{mailId}` | `MailDetailScreen` | LIVE | `MailboxListScreen.onOpenMail`, `VaultListScreen.onOpenItem`, `CeremonialMailWizardScreen.onOpenMail` |
| `MAILBOX_VAULT` | `mailbox/vault` | `VaultListScreen` | LIVE | `MailboxDrawersScreen.onOpenVault` |
| `HOME_DASHBOARD` | `homes/{homeId}` | `HomeDashboardScreen` | LIVE | `MyHomesListScreen.onOpenHome`, deep link `Destination.Home` |
| `HOME_BILLS` | `homes/{homeId}/bills` | `BillsListScreen` | LIVE | `HomeDashboardScreen.onOpenBills` |
| `BILL_DETAIL` | `homes/{homeId}/bills/{billId}` | `BillDetailScreen` | LIVE | `BillsListScreen.onOpenBill` |
| `ADD_BILL` | `homes/{homeId}/bills/new` | `AddBillWizardScreen` | LIVE | `BillsListScreen.onAddBill` |
| `HOME_PETS` | `homes/{homeId}/pets` | `PetsListScreen` | LIVE | `HomeDashboardScreen.onOpenPets` |
| `HOME_CALENDAR` | `homes/{homeId}/calendar` | `HomeCalendarScreen` | LIVE | `HomeDashboardScreen.onOpenCalendar` |
| `HOME_EMERGENCY` | `homes/{homeId}/emergency` | `EmergencyInfoScreen` | LIVE | `HomeDashboardScreen.onOpenEmergency` |
| `HOME_DOCS` | `homes/{homeId}/docs` | `DocumentsScreen` | LIVE | `HomeDashboardScreen.onOpenDocs` |
| `HOME_PACKAGES` | `homes/{homeId}/packages` | `PackagesListScreen` | LIVE | `HomeDashboardScreen.onOpenPackages` |
| `PACKAGE_DETAIL` | `homes/{homeId}/packages/{packageId}` | `PackageDetailScreen` | LIVE | `PackagesListScreen.onOpenPackage` |
| `LOG_PACKAGE` | `homes/{homeId}/packages/new` | `LogPackageScreen` | LIVE | `PackagesListScreen.onLogPackage` |
| `HOME_POLLS` | `homes/{homeId}/polls` | `PollsListScreen` | LIVE | `HomeDashboardScreen.onOpenPolls` |
| `POLL_DETAIL` | `homes/{homeId}/polls/{pollId}` | `PollDetailScreen` | LIVE | `PollsListScreen.onOpenPoll` |
| `ACCESS_CODES` | `homes/{homeId}/access?homeName={…}` | `AccessCodesScreen` | LIVE | `HomeDashboardScreen.onOpenAccessCodes` |
| `HOME_TASKS` | `homes/{homeId}/tasks` | `HouseholdTasksListScreen` | LIVE | `HomeDashboardScreen.onOpenTasks` |
| `HOME_MAINTENANCE` | `homes/{homeId}/maintenance` | `MaintenanceListScreen` | LIVE | `HomeDashboardScreen.onOpenMaintenance` |
| `HOME_OWNERS` | `homes/{homeId}/owners` | `OwnersListScreen` | LIVE | `YouScreen.onOpenHomeOwners` (RootTabScreen.kt:835 → YouScreen.kt:221) |
| `HOME_MEMBERS` | `homes/{homeId}/members` | `MembersListScreen` | LIVE | `HomeDashboardScreen.onOpenMembers` |
| `PUBLIC_PROFILE` | `users/{userId}` | `PublicProfileScreen` | LIVE | `MailDetailScreen.onOpenSenderProfile`, `PulsePostDetailScreen.onOpenProfile`, `DiscoverHubScreen.onSelect(Person)`, deep link |
| `PULSE_POST` | `posts/{postId}` | `PulsePostDetailScreen` | LIVE | `FeedScreen.onOpenPost`, deep link, Hub discovery |
| `INVITE_OWNER` | `homes/{homeId}/invite?email={…}` | `InviteOwnerFormScreen` | LIVE | `HomeDashboardScreen.onInviteOwner`, `OwnersListScreen.onOpenInvite` |
| `DISAMBIGUATE_MAIL` | `mailbox/disambiguate/{mailId}` | `DisambiguateMailFormScreen` | LIVE | `MailboxListScreen.onDisambiguate` |
| `NOTIFICATIONS` | `notifications` | `NotificationsScreen` | LIVE | Hub bell, deep link |
| `CONNECTIONS` | `connections` | `ConnectionsScreen` | LIVE | `YouScreen.onOpenConnections`, Hub card, deep link |
| `OFFERS` | `offers` | `OffersScreen` | LIVE | `YouScreen.onOpenOffers` |
| `MY_BIDS` | `my-bids` | `MyBidsScreen` | LIVE | `YouScreen.onOpenMyBids` |
| `MY_TASKS` | `my-tasks` | `MyTasksScreen` | LIVE | `YouScreen.onOpenMyTasks` |
| `COMPOSE_TASK` | `compose-task` | `NotYetAvailableView("Post a task")` | STUB | `MyTasksScreen.onPostTask`, `onRepost` |
| `MY_POSTS` | `my-posts` | `MyPostsScreen` | LIVE | `YouScreen.onOpenMyPosts` |
| `DISCOVER_HUB` | `discover-hub` | `DiscoverHubScreen` | LIVE | Hub Discovery "See all", deep link |
| `DISCOVER_BUSINESSES` | `discover-businesses` | `DiscoverBusinessesScreen` | LIVE | `DiscoverHubScreen.onSelect(Business)`, `SeeAllBusinesses` |
| `MENU` | `settings` | `SettingsIndexScreen` | LIVE | Hub menu icon, You Settings |
| `SETTINGS_NOTIFICATIONS` | `settings/notifications` | `NotificationSettingsScreen` | LIVE | `SettingsIndexScreen.onNavigate(.Notifications)` |
| `SETTINGS_PRIVACY` | `settings/privacy` | `PrivacySettingsScreen` | LIVE | `SettingsIndexScreen.onNavigate(.Privacy)` |
| `SETTINGS_BLOCKED_USERS` | `settings/blocks` | `BlockedUsersScreen` | LIVE | `SettingsIndexScreen.onNavigate(.Blocks)` |
| `SETTINGS_PASSWORD` | `settings/password` | `PasswordChangeScreen` | LIVE | `SettingsIndexScreen.onNavigate(.Password)` |
| `SETTINGS_VERIFICATION` | `settings/verification` | `VerificationCenterScreen` | LIVE | `SettingsIndexScreen.onNavigate(.Verification)` |
| `SETTINGS_HELP` | `settings/help` | `HelpCenterScreen` | LIVE | `SettingsIndexScreen.onNavigate(.Help)` |
| `SETTINGS_LEGAL` | `settings/legal` | `LegalIndexScreen` | LIVE | `SettingsIndexScreen.onNavigate(.Legal)` |
| `SETTINGS_LEGAL_CONTENT` | `settings/legal/{doc}` | `LegalContentScreen` (known doc) / `NotYetAvailableView` (unknown) | PARTIAL | `LegalIndexScreen.onSelectDocument` |
| `SETTINGS_ABOUT` | `settings/about` | `AboutScreen` | LIVE | `SettingsIndexScreen.onNavigate(.About)` |
| `IDENTITY_CENTER` | `identity-center` | `IdentityCenterScreen` | LIVE | `SettingsIndexScreen.onNavigate(.IdentityCenter)`, You-tab |
| `AUDIENCE_PROFILE` | `audience-profile` | `AudienceProfileScreen` | LIVE | `IdentityCenterScreen.onOpenIdentity(PublicProfile)` |
| `CEREMONIAL_MAIL` | `mailbox/compose-letter` | `CeremonialMailWizardScreen` | LIVE | `CeremonialMailOpenScreen.onWriteBack` |
| `CEREMONIAL_MAIL_OPEN` | `mailbox/letter/{mailId}` | `CeremonialMailOpenScreen` | LIVE | Mail detail tap for ceremonial-category mail |
| `PRIVACY_HANDSHAKE` | `handshake/{personaHandle}` | `PrivacyHandshakeScreen` | LIVE | Beacon-profile follow CTA |
| `TOKEN_ACCEPT` | `invite/{token}` | `TokenAcceptScreen` | LIVE | Deep link `Destination.Invite` |
| `SUPPORT_TRAINS` | `support-trains` | `SupportTrainsScreen` | LIVE | `YouScreen.onOpenSupportTrains`, deep link |
| `REVIEW_SIGNUPS` | `support-trains/{supportTrainId}/review` | `ReviewSignupsScreen` | LIVE | `SupportTrainsScreen.onOpenTrain` |
| `PLACEHOLDER` | `_placeholder/generic?label={label}` | `NotYetAvailableView(label)` | STUB | Catch-all for ~50 unbuilt affordances |
| `PULSE_FEED` | `feed/pulse` | `FeedScreen` | LIVE | Hub Pulse pillar, deep link |
| `COMPOSE_POST` | `feed/compose?intent={intent}` | `NotYetAvailableView("Compose · <intent>")` | STUB | `FeedScreen.onCompose` |
| `GIGS_FEED` | `gigs/feed` | `GigsFeedScreen` | LIVE | Hub Gigs pillar, `OffersScreen.onBrowseTasks`, `DiscoverHubScreen.SeeAllGigs` |
| `GIG_DETAIL` | `gigs/{gigId}` | `GigDetailScreen` | LIVE | `GigsFeedScreen.onOpenGig`, `MyBidsScreen.onOpenBid`, `OffersScreen.onOpenOfferDetail`, deep link |
| `COMPOSE_GIG` | `gigs/compose?category={category}` | `NotYetAvailableView("Post a task · <category>")` | STUB | `GigsFeedScreen.onCompose` |
| `NEARBY_MAP_FOR_GIGS` | `gigs/map?category={category}` | `NearbyMapScreen` | LIVE | `GigsFeedScreen.onOpenMap` |
| `MARKETPLACE` | `marketplace` | `MarketplaceScreen` | LIVE | Hub Marketplace pillar, `DiscoverHubScreen.SeeAllListings` |
| `LISTING_DETAIL` | `listings/{listingId}` | `ListingDetailScreen` | LIVE | `MarketplaceScreen.onOpenListing`, `NearbyMapScreen.onOpenEntity(Listing)`, `DiscoverHubScreen.onSelect(listing)`, deep link |
| `LISTING_OFFERS` | `listings/{listingId}/offers?listingTitle={…}` | `ListingOffersScreen` | LIVE | `ListingDetailScreen.onViewOffers` |
| `COMPOSE_LISTING` | `listings/compose` | `NotYetAvailableView("Snap & sell")` | STUB | `MarketplaceScreen.onCompose`, `MyListingsScreen.onCompose` |
| `MY_LISTINGS` | `listings/me` | `MyListingsScreen` | LIVE | `YouScreen.onOpenMyListings` |
| `INVOICE_DETAIL` | `invoices/{invoiceId}` | `InvoiceDetailScreen` | **ORPHAN** | No inbound caller — same future-wallet integration story as iOS. |
| `MY_BUSINESSES` | `businesses/me` | `MyBusinessesScreen` | LIVE | `YouScreen.onOpenMyBusinesses` |
| `CHAT_CONVERSATION` | `chat/{kind}/{id}?…` | `ChatConversationHost` | LIVE | `ConnectionsScreen.onOpenChat`, `NewMessageScreen.onSelect` |
| `NEW_MESSAGE` | `chat/new` | `NewMessageScreen` | LIVE | `ChatListScreen.onCompose` |
| `TOKEN_GALLERY` *(DEBUG)* | `_debug/token-gallery` | `TokenGalleryScreen` | LIVE (DEBUG) | 5-tap easter egg on Hub corner |

## Section 3 — Orphan routes

Routes whose destination is a real View / Composable, but which have **zero** call sites pushing them. (Excluding intentional DEBUG-only easter-egg routes.)

| Platform | Route | Destination | Disposition |
|---|---|---|---|
| iOS | `HubRoute.invoiceDetail(invoiceId:)` | `InvoiceDetailView` | Keep — future wallet/payments integration per T2.6 plan; remove only if wallet feature is descoped. |
| Android | `ChildRoutes.INVOICE_DETAIL` | `InvoiceDetailScreen` | Keep — mirrors iOS. |

The earlier iOS scan flagged `tokenGallery / iconGallery / componentGallery` as orphan — these are reached via a 5-tap easter egg gesture on the Hub corner, so they're intentionally non-discoverable rather than orphan. The earlier Android scan flagged `HOME_OWNERS` as orphan; that was a miss — it IS reached via `YouScreen.kt:221 → onOpenHomeOwners → RootTabScreen.kt:835 → ChildRoutes.homeOwners(homeId)`. Recorded LIVE in Section 2.

**Dead-route cases (declared but neither pushed nor producing a real View):**

| Platform | Route | Note |
|---|---|---|
| iOS | `YouRoute.signOutConfirm` | Maps to `EmptyView()`; never pushed. `MeView` uses a SwiftUI `confirmationDialog` for sign-out instead. Safe to remove in a follow-up cleanup. |

## Section 4 — Dead screens

Screens whose `View` / `@Composable` exists in `Features/` / `screens/`
but is not referenced from any route or wired screen.

| Platform | Screen | Note |
|---|---|---|
| iOS | (none) | All public View files under `Features/` reach a route destination, or are shared shells (`Shared/`, `_Internal/`, route hosts, content/header/CTA slots inside ContentDetail). |
| Android | `screens/status/StatusWaitingScreen.kt` | Composable exists but **no `ChildRoutes` constant** references it and no `composable(…)` block invokes it. iOS at least has the `YouRoute.statusWaiting` DEBUG case; Android has no route — effectively dead until wired. **Disposition: wire it** (the screen is reachable through the home-claim happy path on the design pack's Status / Waiting.html). |
| Android | `screens/mailbox/item_detail/MailboxItemDetailScreen.kt` (the T6.5a A17-shell consumer) | Exists alongside the older `MailDetailScreen.kt`. `MAILBOX_ITEM_DETAIL` route still resolves to `MailDetailScreen`. Not technically dead (it's wired in `MailboxItemDetailShell.kt` previews + future migration target), but **not reachable from production navigation today**. Same caveat applies to iOS `MailboxItemDetailView.swift`. |

## Section 5 — Placeholder catalog with proposed dispositions

96 unique placeholder labels with **96 distinct dispositions** spread
across **305 call sites** (iOS 125, Android 81 generic + 7 typed +
other contexts). Cross-referenced against `docs/screen-parity-inventory.md`
and the `A08 — per-screen batch 1/` design pack to decide each
disposition:

- **BUILD** — the affordance corresponds to a screen already in the
  design pack OR points to an existing View/Screen that just needs
  routing wired up.
- **DEFER** — post-MVP affordance (search/filter sheets, share/print/
  export, edit flows, secondary identity surfaces). Acceptable to ship
  v1 with the placeholder still in place.
- **REMOVE** — stale call site with no path forward; affordance should
  be deleted from the parent screen. *(None identified in this audit.)*

### Per-label disposition summary

### Unique labels — 96 total

| Label | iOS sites | Android sites | Disposition |
|---|---:|---:|---|
| `Add a bill` | 1 | 0 | **BUILD** |
| `Add a task` | 2 | 1 | **BUILD** |
| `Add emergency info` | 2 | 1 | **BUILD** |
| `Add event` | 2 | 1 | **BUILD** |
| `Article body` | 1 | 0 | **DEFER** |
| `Audience setup` | 1 | 0 | **BUILD** |
| `Bill detail` | 1 | 0 | **BUILD** |
| `Browse listings` | 1 | 1 | **BUILD** |
| `Browse tasks` | 1 | 0 | **BUILD** |
| `Business` | 1 | 1 | **DEFER** |
| `Business dashboard` | 1 | 1 | **DEFER** |
| `Business filters` | 1 | 1 | **DEFER** |
| `Business header` | 1 | 0 | **DEFER** |
| `Business: ${target.name} (${target.businessId})` | 0 | 1 | **BUILD** |
| `Business: \(name) (\(businessId))` | 1 | 0 | **BUILD** |
| `Buyer profile` | 3 | 1 | **DEFER** |
| `Chat search` | 1 | 1 | **DEFER** |
| `Claim a home` | 1 | 0 | **BUILD** |
| `Claim ownership` | 1 | 0 | **BUILD** |
| `Claim status` | 1 | 1 | **BUILD** |
| `Compose · \(intent.capitalized)` | 1 | 0 | **DEFER** |
| `Data export` | 1 | 1 | **DEFER** |
| `Discovery filters` | 1 | 1 | **DEFER** |
| `Document action` | 2 | 1 | **BUILD** |
| `Document detail` | 2 | 1 | **BUILD** |
| `Drawer · $drawer` | 0 | 1 | **BUILD** |
| `Drawer · \(drawer)` | 1 | 0 | **BUILD** |
| `Edit access code` | 1 | 1 | **BUILD** |
| `Edit listing` | 3 | 0 | **DEFER** |
| `Edit post` | 1 | 1 | **DEFER** |
| `Edit profile` | 1 | 2 | **BUILD** |
| `Edit recurring task` | 2 | 1 | **BUILD** |
| `Edit signup · $reservationId` | 0 | 1 | **DEFER** |
| `Edit signup · \(reservationId)` | 2 | 0 | **DEFER** |
| `Emergency item` | 2 | 1 | **BUILD** |
| `Event detail` | 2 | 1 | **BUILD** |
| `Export documents` | 2 | 1 | **DEFER** |
| `Filter bids` | 2 | 1 | **DEFER** |
| `Filter posts` | 1 | 1 | **DEFER** |
| `Filter tasks` | 1 | 1 | **DEFER** |
| `Find people` | 2 | 1 | **BUILD** |
| `Follower` | 1 | 0 | **DEFER** |
| `Follower · ${row.displayName}` | 0 | 1 | **DEFER** |
| `Gig detail` | 1 | 1 | **BUILD** |
| `Gig filters` | 1 | 1 | **DEFER** |
| `Gig search` | 1 | 1 | **DEFER** |
| `Identity` | 1 | 0 | **BUILD** |
| `Invite a business` | 1 | 1 | **DEFER** |
| `Invite to Pantopus` | 1 | 1 | **DEFER** |
| `Key/value body` | 1 | 0 | **DEFER** |
| `Legal` | 0 | 1 | **DEFER** |
| `List something` | 1 | 1 | **BUILD** |
| `Local profile` | 0 | 1 | **DEFER** |
| `Log maintenance` | 2 | 1 | **BUILD** |
| `Mail search` | 2 | 1 | **DEFER** |
| `Maintenance detail` | 2 | 1 | **BUILD** |
| `Map filters` | 2 | 2 | **DEFER** |
| `Media body` | 1 | 0 | **DEFER** |
| `Member requests · ${pending.id}` | 0 | 1 | **DEFER** |
| `Message helper` | 2 | 0 | **DEFER** |
| `Message helper · $reservationId` | 0 | 1 | **DEFER** |
| `Messages` | 6 | 4 | **BUILD** |
| `My claims` | 1 | 0 | **DEFER** |
| `Nearby` | 1 | 1 | **DEFER** |
| `Offer filters` | 1 | 1 | **DEFER** |
| `Payments & payouts` | 1 | 1 | **DEFER** |
| `Personal` | 0 | 1 | **DEFER** |
| `Post a gig` | 1 | 1 | **BUILD** |
| `Post a task` | 2 | 2 | **BUILD** |
| `Post a task · $label` | 0 | 1 | **DEFER** |
| `Post a task · \(category.capitalized)` | 1 | 0 | **DEFER** |
| `Post detail` | 1 | 1 | **BUILD** |
| `Print emergency card` | 2 | 1 | **BUILD** |
| `Professional` | 0 | 1 | **DEFER** |
| `Register a business` | 1 | 1 | **DEFER** |
| `Report` | 1 | 1 | **DEFER** |
| `Search access codes` | 1 | 1 | **DEFER** |
| `Search documents` | 2 | 1 | **DEFER** |
| `Search support trains` | 2 | 1 | **DEFER** |
| `Set home address` | 1 | 1 | **BUILD** |
| `Set up Public Profile` | 0 | 1 | **BUILD** |
| `Share emergency info` | 2 | 1 | **BUILD** |
| `Share listing` | 3 | 1 | **DEFER** |
| `Share train` | 2 | 1 | **DEFER** |
| `Snap & sell` | 2 | 2 | **BUILD** |
| `Sort offers` | 3 | 1 | **DEFER** |
| `Start a poll` | 2 | 1 | **BUILD** |
| `Start a support train` | 2 | 1 | **DEFER** |
| `Task detail` | 2 | 1 | **BUILD** |
| `Thread` | 1 | 0 | **BUILD** |
| `Thread · ${row.displayName}` | 0 | 1 | **DEFER** |
| `Today` | 0 | 1 | **BUILD** |
| `Transaction detail` | 3 | 1 | **BUILD** |
| `Upload document` | 2 | 1 | **BUILD** |
| `Wallet header` | 1 | 0 | **DEFER** |
| `Write a post` | 1 | 1 | **BUILD** |

### Full per-call-site catalogs

The per-call-site tables are large; included verbatim for grep-ability.


### iOS placeholders — 125 call sites

| Label | File | Line | Disposition | Reason |
|---|---|---:|---|---|
| `Add a bill` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 817 | **BUILD** | AddBillWizardView/Screen already exists; route wiring needed. |
| `Add a task` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 528 | **BUILD** | Household task creation — needed for Household tasks.html FAB. |
| `Add a task` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 1053 | **BUILD** | Household task creation — needed for Household tasks.html FAB. |
| `Add emergency info` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 447 | **BUILD** | Emergency info FAB — Emergency info.html. |
| `Add emergency info` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 850 | **BUILD** | Emergency info FAB — Emergency info.html. |
| `Add event` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 429 | **BUILD** | Home calendar add — Home calendar.html FAB. |
| `Add event` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 829 | **BUILD** | Home calendar add — Home calendar.html FAB. |
| `Article body` | `frontend/apps/ios/Pantopus/Features/Shared/ContentDetail/Bodies.swift` | 131 | **DEFER** | Not in A08 design pack; treat as post-MVP affordance. |
| `Audience setup` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 806 | **BUILD** | Public Beacon Profile.html FRAME 4 / Creator Audience setup. |
| `Bill detail` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 814 | **BUILD** | BillDetailView/Screen already exists; route wiring needed. |
| `Browse listings` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 592 | **BUILD** | Wire to existing MarketplaceView/Screen. |
| `Browse tasks` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 680 | **BUILD** | Wire to existing GigsFeedView/Screen. |
| `Business` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 260 | **DEFER** | Generic business profile — no specific design HTML. |
| `Business dashboard` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 997 | **DEFER** | Business surface — post-MVP. |
| `Business filters` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 826 | **DEFER** | Filter sheet — post-MVP polish on Discover Businesses. |
| `Business header` | `frontend/apps/ios/Pantopus/Features/Shared/ContentDetail/Headers.swift` | 100 | **DEFER** | Not in A08 design pack; treat as post-MVP affordance. |
| `Business: \(name) (\(businessId))` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 824 | **BUILD** | Business profile screen — partial design in Discover businesses.html. |
| `Buyer profile` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 707 | **DEFER** | Marketplace buyer detail — post-MVP. |
| `Buyer profile` | `frontend/apps/ios/Pantopus/Features/Root/NearbyTabRoot.swift` | 78 | **DEFER** | Marketplace buyer detail — post-MVP. |
| `Buyer profile` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 629 | **DEFER** | Marketplace buyer detail — post-MVP. |
| `Chat search` | `frontend/apps/ios/Pantopus/Features/Root/InboxTabRoot.swift` | 124 | **DEFER** | Inbox search — needs backend query support. |
| `Claim a home` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 978 | **BUILD** | AddHomeWizard route exists; wire it. |
| `Claim ownership` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 1009 | **BUILD** | ClaimOwnershipWizard route exists; wire it. |
| `Claim status` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 334 | **BUILD** | Status Waiting.html FRAME 1 'Claim submitted' covers this state. |
| `Compose · \(intent.capitalized)` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 639 | **DEFER** | Not in A08 design pack; treat as post-MVP affordance. |
| `Data export` | `frontend/apps/ios/Pantopus/Features/Settings/SettingsView.swift` | 133 | **DEFER** | Privacy export — P8.5+ per code comment. |
| `Discovery filters` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 810 | **DEFER** | Filter sheet — post-MVP polish on Discover Hub. |
| `Document action` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 488 | **BUILD** | Documents.html row kebab — bulk actions. |
| `Document action` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 891 | **BUILD** | Documents.html row kebab — bulk actions. |
| `Document detail` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 468 | **BUILD** | Documents.html row tap. |
| `Document detail` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 871 | **BUILD** | Documents.html row tap. |
| `Drawer · \(drawer)` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 602 | **BUILD** | Mailbox drawer detail — Mailbox Mobile.html shows drawer composites. |
| `Edit access code` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 964 | **BUILD** | Access codes row tap → edit form. |
| `Edit listing` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 713 | **DEFER** | Listing edit — post-MVP. |
| `Edit listing` | `frontend/apps/ios/Pantopus/Features/Root/NearbyTabRoot.swift` | 84 | **DEFER** | Listing edit — post-MVP. |
| `Edit listing` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 639 | **DEFER** | Listing edit — post-MVP. |
| `Edit post` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 662 | **DEFER** | Post edit — post-MVP. |
| `Edit profile` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 890 | **BUILD** | Form.html FRAME 2 'Edit profile' design; EditProfileView already exists on iOS. |
| `Edit recurring task` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 531 | **BUILD** | Household tasks edit affordance — designed in Household tasks.html row actions. |
| `Edit recurring task` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 1056 | **BUILD** | Household tasks edit affordance — designed in Household tasks.html row actions. |
| `Edit signup · \(reservationId)` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 780 | **DEFER** | Support train signup edit — post-MVP. |
| `Edit signup · \(reservationId)` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 784 | **DEFER** | Support train signup edit — post-MVP. |
| `Emergency item` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 442 | **BUILD** | Emergency info row detail — Emergency info.html row tap. |
| `Emergency item` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 845 | **BUILD** | Emergency info row detail — Emergency info.html row tap. |
| `Event detail` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 432 | **BUILD** | Home calendar event detail — Home calendar.html row tap. |
| `Event detail` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 834 | **BUILD** | Home calendar event detail — Home calendar.html row tap. |
| `Export documents` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 483 | **DEFER** | Bulk export — post-MVP. |
| `Export documents` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 886 | **DEFER** | Bulk export — post-MVP. |
| `Filter bids` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 846 | **DEFER** | Filter sheet — post-MVP polish on My bids. |
| `Filter bids` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 677 | **DEFER** | Filter sheet — post-MVP polish on My bids. |
| `Filter posts` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 656 | **DEFER** | Filter sheet — post-MVP polish on My posts. |
| `Filter tasks` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 715 | **DEFER** | Filter sheet — post-MVP polish on My tasks. |
| `Find people` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 745 | **BUILD** | Connections.html add-person CTA — ConnectionsView/Screen has the search affordance to wire. |
| `Find people` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 749 | **BUILD** | Connections.html add-person CTA — ConnectionsView/Screen has the search affordance to wire. |
| `Follower` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 800 | **DEFER** | Creator follower detail — post-MVP, secondary to Creator Audience tabs. |
| `Gig detail` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 259 | **BUILD** | GigDetailView/Screen exists; route wiring. |
| `Gig filters` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 652 | **DEFER** | Filter sheet — post-MVP polish on Gigs. |
| `Gig search` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 651 | **DEFER** | Gigs search — needs backend query support. |
| `Identity` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 793 | **BUILD** | IdentityCenterView/Screen exists; route just needs to push it. |
| `Invite a business` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 830 | **DEFER** | Business invite flow — post-MVP. |
| `Invite to Pantopus` | `frontend/apps/ios/Pantopus/Features/Root/InboxTabRoot.swift` | 122 | **DEFER** | User invite flow — post-MVP. |
| `Key/value body` | `frontend/apps/ios/Pantopus/Features/Shared/ContentDetail/Bodies.swift` | 140 | **DEFER** | Not in A08 design pack; treat as post-MVP affordance. |
| `List something` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 989 | **BUILD** | Marketplace compose flow (alternate label). |
| `Log maintenance` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 381 | **BUILD** | Maintenance.html FAB. |
| `Log maintenance` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 1068 | **BUILD** | Maintenance.html FAB. |
| `Mail search` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 894 | **DEFER** | Mailbox search — needs backend query support. |
| `Mail search` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 557 | **DEFER** | Mailbox search — needs backend query support. |
| `Maintenance detail` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 378 | **BUILD** | Maintenance.html row tap. |
| `Maintenance detail` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 1065 | **BUILD** | Maintenance.html row tap. |
| `Map filters` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 676 | **DEFER** | Filter sheet — post-MVP polish on Map+List Hybrid. |
| `Map filters` | `frontend/apps/ios/Pantopus/Features/Root/NearbyTabRoot.swift` | 66 | **DEFER** | Filter sheet — post-MVP polish on Map+List Hybrid. |
| `Media body` | `frontend/apps/ios/Pantopus/Features/Shared/ContentDetail/Bodies.swift` | 149 | **DEFER** | Not in A08 design pack; treat as post-MVP affordance. |
| `Message helper` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 776 | **DEFER** | Support train messaging — post-MVP. |
| `Message helper` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 780 | **DEFER** | Support train messaging — post-MVP. |
| `Messages` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 278 | **BUILD** | ChatConversationView/Screen already exists; route just needs to push it instead of placeholder. |
| `Messages` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 577 | **BUILD** | ChatConversationView/Screen already exists; route just needs to push it instead of placeholder. |
| `Messages` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 659 | **BUILD** | ChatConversationView/Screen already exists; route just needs to push it instead of placeholder. |
| `Messages` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 691 | **BUILD** | ChatConversationView/Screen already exists; route just needs to push it instead of placeholder. |
| `Messages` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 604 | **BUILD** | ChatConversationView/Screen already exists; route just needs to push it instead of placeholder. |
| `Messages` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 746 | **BUILD** | ChatConversationView/Screen already exists; route just needs to push it instead of placeholder. |
| `My claims` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 1012 | **DEFER** | MyClaimsListView exists but the claim detail screen does not — wire list, leave detail placeholder. |
| `Nearby` | `frontend/apps/ios/Pantopus/Features/Root/NotYetAvailableView.swift` | 48 | **DEFER** | Not in A08 design pack; treat as post-MVP affordance. |
| `Offer filters` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 589 | **DEFER** | Filter sheet — post-MVP. |
| `Payments & payouts` | `frontend/apps/ios/Pantopus/Features/Settings/SettingsView.swift` | 135 | **DEFER** | Wallet — P8.5+ per code comment. |
| `Post a gig` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 200 | **BUILD** | Same as Post a task — Gigs compose. |
| `Post a task` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 595 | **BUILD** | Gigs compose flow — Gigs.html empty state CTA. |
| `Post a task` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 741 | **BUILD** | Gigs compose flow — Gigs.html empty state CTA. |
| `Post a task · \(category.capitalized)` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 662 | **DEFER** | Not in A08 design pack; treat as post-MVP affordance. |
| `Post detail` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 653 | **BUILD** | PulsePostDetailView/Screen exists; route wiring. |
| `Print emergency card` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 457 | **BUILD** | Emergency info.html CTA — Print fallback acceptable post-MVP but design exists. |
| `Print emergency card` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 860 | **BUILD** | Emergency info.html CTA — Print fallback acceptable post-MVP but design exists. |
| `Register a business` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 1000 | **DEFER** | Business onboarding — post-MVP, no design. |
| `Report` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 578 | **DEFER** | Content/user report flow — post-MVP, not in A08. |
| `Search access codes` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 966 | **DEFER** | Access codes search — post-MVP. |
| `Search documents` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 478 | **DEFER** | Documents search — post-MVP. |
| `Search documents` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 881 | **DEFER** | Documents search — post-MVP. |
| `Search support trains` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 759 | **DEFER** | Support Trains search — post-MVP. |
| `Search support trains` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 763 | **DEFER** | Support Trains search — post-MVP. |
| `Set home address` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 828 | **BUILD** | AddHomeWizard handles this. |
| `Share emergency info` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 452 | **BUILD** | Emergency info.html share CTA. |
| `Share emergency info` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 855 | **BUILD** | Emergency info.html share CTA. |
| `Share listing` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 704 | **DEFER** | Share sheet — post-MVP. |
| `Share listing` | `frontend/apps/ios/Pantopus/Features/Root/NearbyTabRoot.swift` | 75 | **DEFER** | Share sheet — post-MVP. |
| `Share listing` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 624 | **DEFER** | Share sheet — post-MVP. |
| `Share train` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 768 | **DEFER** | Share sheet — post-MVP. |
| `Share train` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 772 | **DEFER** | Share sheet — post-MVP. |
| `Snap & sell` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 201 | **BUILD** | Marketplace compose flow — Marketplace.html shows the empty-state CTA; compose wizard is part of the marketplace surface. |
| `Snap & sell` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 721 | **BUILD** | Marketplace compose flow — Marketplace.html shows the empty-state CTA; compose wizard is part of the marketplace surface. |
| `Sort offers` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 716 | **DEFER** | Sort sheet — post-MVP polish. |
| `Sort offers` | `frontend/apps/ios/Pantopus/Features/Root/NearbyTabRoot.swift` | 87 | **DEFER** | Sort sheet — post-MVP polish. |
| `Sort offers` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 644 | **DEFER** | Sort sheet — post-MVP polish. |
| `Start a poll` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 960 | **BUILD** | Polls.html FAB → create poll. |
| `Start a poll` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 941 | **BUILD** | Polls.html FAB → create poll. |
| `Start a support train` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 753 | **DEFER** | Support Train creation wizard — no specific design HTML; surface from Support trains.html. |
| `Start a support train` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 757 | **DEFER** | Support Train creation wizard — no specific design HTML; surface from Support trains.html. |
| `Task detail` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 525 | **BUILD** | Household task detail — implied by Household tasks.html row tap. |
| `Task detail` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 1050 | **BUILD** | Household task detail — implied by Household tasks.html row tap. |
| `Thread` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 803 | **BUILD** | ChatConversationView/Screen exists; route wiring. |
| `Transaction detail` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 710 | **BUILD** | TransactionalDetailShell exists; route wiring. |
| `Transaction detail` | `frontend/apps/ios/Pantopus/Features/Root/NearbyTabRoot.swift` | 81 | **BUILD** | TransactionalDetailShell exists; route wiring. |
| `Transaction detail` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 634 | **BUILD** | TransactionalDetailShell exists; route wiring. |
| `Upload document` | `frontend/apps/ios/Pantopus/Features/Root/HubTabRoot.swift` | 473 | **BUILD** | Documents.html FAB. |
| `Upload document` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 876 | **BUILD** | Documents.html FAB. |
| `Wallet header` | `frontend/apps/ios/Pantopus/Features/Shared/ContentDetail/Headers.swift` | 110 | **DEFER** | Not in A08 design pack; treat as post-MVP affordance. |
| `Write a post` | `frontend/apps/ios/Pantopus/Features/Root/YouTabRoot.swift` | 659 | **BUILD** | Pulse compose flow — Pulse.html empty state CTA. |

### Android placeholders — 81 call sites

| Label | File | Line | Disposition | Reason |
|---|---|---:|---|---|
| `Add a task` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1084 | **BUILD** | Household task creation — needed for Household tasks.html FAB. |
| `Add emergency info` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 982 | **BUILD** | Emergency info FAB — Emergency info.html. |
| `Add event` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 967 | **BUILD** | Home calendar add — Home calendar.html FAB. |
| `Browse listings` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1551 | **BUILD** | Wire to existing MarketplaceView/Screen. |
| `Business` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1923 | **DEFER** | Generic business profile — no specific design HTML. |
| `Business dashboard` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 859 | **DEFER** | Business surface — post-MVP. |
| `Business filters` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1532 | **DEFER** | Filter sheet — post-MVP polish on Discover Businesses. |
| `Business: ${target.name} (${target.businessId})` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1529 | **BUILD** | Same — Business profile screen. |
| `Buyer profile` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1371 | **DEFER** | Marketplace buyer detail — post-MVP. |
| `Chat search` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 774 | **DEFER** | Inbox search — needs backend query support. |
| `Claim status` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1857 | **BUILD** | Status Waiting.html FRAME 1 'Claim submitted' covers this state. |
| `Data export` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1625 | **DEFER** | Privacy export — P8.5+ per code comment. |
| `Discovery filters` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1514 | **DEFER** | Filter sheet — post-MVP polish on Discover Hub. |
| `Document action` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1032 | **BUILD** | Documents.html row kebab — bulk actions. |
| `Document detail` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1026 | **BUILD** | Documents.html row tap. |
| `Drawer · $drawer` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1215 | **BUILD** | Same. |
| `Edit access code` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1067 | **BUILD** | Access codes row tap → edit form. |
| `Edit post` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1609 | **DEFER** | Post edit — post-MVP. |
| `Edit profile` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 805 | **BUILD** | Form.html FRAME 2 'Edit profile' design; EditProfileView already exists on iOS. |
| `Edit profile` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1620 | **BUILD** | Form.html FRAME 2 'Edit profile' design; EditProfileView already exists on iOS. |
| `Edit recurring task` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1087 | **BUILD** | Household tasks edit affordance — designed in Household tasks.html row actions. |
| `Edit signup · $reservationId` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1810 | **DEFER** | Same — post-MVP. |
| `Emergency item` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 980 | **BUILD** | Emergency info row detail — Emergency info.html row tap. |
| `Event detail` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 969 | **BUILD** | Home calendar event detail — Home calendar.html row tap. |
| `Export documents` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1030 | **DEFER** | Bulk export — post-MVP. |
| `Filter bids` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1564 | **DEFER** | Filter sheet — post-MVP polish on My bids. |
| `Filter posts` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1607 | **DEFER** | Filter sheet — post-MVP polish on My posts. |
| `Filter tasks` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1591 | **DEFER** | Filter sheet — post-MVP polish on My tasks. |
| `Find people` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1485 | **BUILD** | Connections.html add-person CTA — ConnectionsView/Screen has the search affordance to wire. |
| `Follower · ${row.displayName}` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1748 | **DEFER** | Same. |
| `Gig detail` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1922 | **BUILD** | GigDetailView/Screen exists; route wiring. |
| `Gig filters` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1391 | **DEFER** | Filter sheet — post-MVP polish on Gigs. |
| `Gig search` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1390 | **DEFER** | Gigs search — needs backend query support. |
| `Invite a business` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1536 | **DEFER** | Business invite flow — post-MVP. |
| `Invite to Pantopus` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1328 | **DEFER** | User invite flow — post-MVP. |
| `Legal` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1687 | **DEFER** | Not in A08 design pack; treat as post-MVP affordance. |
| `List something` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 853 | **BUILD** | Marketplace compose flow (alternate label). |
| `Local profile` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1766 | **DEFER** | Public Beacon Profile FRAME 3 variant — same screen, identity switch. |
| `Log maintenance` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1101 | **BUILD** | Maintenance.html FAB. |
| `Mail search` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1779 | **DEFER** | Mailbox search — needs backend query support. |
| `Maintenance detail` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1098 | **BUILD** | Maintenance.html row tap. |
| `Map filters` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 765 | **DEFER** | Filter sheet — post-MVP polish on Map+List Hybrid. |
| `Map filters` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1423 | **DEFER** | Filter sheet — post-MVP polish on Map+List Hybrid. |
| `Member requests · ${pending.id}` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 676 | **DEFER** | Member request handling — post-MVP. |
| `Message helper · $reservationId` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1813 | **DEFER** | Same — post-MVP. |
| `Messages` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1178 | **BUILD** | ChatConversationView/Screen already exists; route just needs to push it instead of placeholder. |
| `Messages` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1351 | **BUILD** | ChatConversationView/Screen already exists; route just needs to push it instead of placeholder. |
| `Messages` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1401 | **BUILD** | ChatConversationView/Screen already exists; route just needs to push it instead of placeholder. |
| `Messages` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1937 | **BUILD** | ChatConversationView/Screen already exists; route just needs to push it instead of placeholder. |
| `Nearby` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/NotYetAvailableView.kt` | 49 | **DEFER** | Not in A08 design pack; treat as post-MVP affordance. |
| `Offer filters` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1550 | **DEFER** | Filter sheet — post-MVP. |
| `Payments & payouts` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1627 | **DEFER** | Wallet — P8.5+ per code comment. |
| `Personal` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1768 | **DEFER** | Identity Center switcher action — Identity Center.html shows but switching action is post-MVP. |
| `Post a gig` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 730 | **BUILD** | Same as Post a task — Gigs compose. |
| `Post a task` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1552 | **BUILD** | Gigs compose flow — Gigs.html empty state CTA. |
| `Post a task` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1601 | **BUILD** | Gigs compose flow — Gigs.html empty state CTA. |
| `Post a task · $label` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1441 | **DEFER** | Not in A08 design pack; treat as post-MVP affordance. |
| `Post detail` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1606 | **BUILD** | PulsePostDetailView/Screen exists; route wiring. |
| `Print emergency card` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 985 | **BUILD** | Emergency info.html CTA — Print fallback acceptable post-MVP but design exists. |
| `Professional` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1770 | **DEFER** | Same — Identity Center. |
| `Register a business` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 860 | **DEFER** | Business onboarding — post-MVP, no design. |
| `Report` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1179 | **DEFER** | Content/user report flow — post-MVP, not in A08. |
| `Search access codes` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1070 | **DEFER** | Access codes search — post-MVP. |
| `Search documents` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1029 | **DEFER** | Documents search — post-MVP. |
| `Search support trains` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1791 | **DEFER** | Support Trains search — post-MVP. |
| `Set home address` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1534 | **BUILD** | AddHomeWizard handles this. |
| `Set up Public Profile` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1754 | **BUILD** | Public Beacon Profile.html FRAME 4 first-run flow. |
| `Share emergency info` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 983 | **BUILD** | Emergency info.html share CTA. |
| `Share listing` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1370 | **DEFER** | Share sheet — post-MVP. |
| `Share train` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1807 | **DEFER** | Share sheet — post-MVP. |
| `Snap & sell` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 732 | **BUILD** | Marketplace compose flow — Marketplace.html shows the empty-state CTA; compose wizard is part of the marketplace surface. |
| `Snap & sell` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1383 | **BUILD** | Marketplace compose flow — Marketplace.html shows the empty-state CTA; compose wizard is part of the marketplace surface. |
| `Sort offers` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1373 | **DEFER** | Sort sheet — post-MVP polish. |
| `Start a poll` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1015 | **BUILD** | Polls.html FAB → create poll. |
| `Start a support train` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1788 | **DEFER** | Support Train creation wizard — no specific design HTML; surface from Support trains.html. |
| `Task detail` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1081 | **BUILD** | Household task detail — implied by Household tasks.html row tap. |
| `Thread · ${row.displayName}` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1751 | **DEFER** | AudienceProfile thread row tap — wire to ChatConversation. |
| `Today` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 752 | **BUILD** | Hub Today rail context — Hub.html FRAME 1 implies a Today section. |
| `Transaction detail` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1372 | **BUILD** | TransactionalDetailShell exists; route wiring. |
| `Upload document` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1028 | **BUILD** | Documents.html FAB. |
| `Write a post` | `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/root/RootTabScreen.kt` | 1608 | **BUILD** | Pulse compose flow — Pulse.html empty state CTA. |




## Closure summary

| | iOS | Android |
|---|---|---|
| Total declared routes | 98 (HubRoute 47 + NearbyRoute 4 + InboxRoute 4 + YouRoute 43) | 79 ChildRoutes constants wired to composables |
| LIVE (reachable + real destination) | 81 | 70 |
| STUB (routed to placeholder/NotYetAvailableView) | 13 | 6 |
| DEBUG-only LIVE | 6 (galleries + handshake/status/ceremonial) | 1 (TokenGallery) |
| ORPHAN (real destination, no caller) | 1 (`invoiceDetail`) | 1 (`INVOICE_DETAIL`) |
| DEAD (case declared but no destination) | 1 (`signOutConfirm`) | 0 |
| Dead screens (View exists but no route reaches it) | 0 | 2 (`StatusWaitingScreen` + the A17 `MailboxItemDetailScreen` consumer) |

## Follow-up signal for downstream prompts

1. **Wire `InvoiceDetail` and the A17 `MailboxItemDetail` shell consumer** — both exist on both platforms but live behind unused route cases. P1 task.
2. **Remove `YouRoute.signOutConfirm`** — stale enum case that always resolves to `EmptyView()`. Safe one-line cleanup.
3. **Wire Android `StatusWaitingScreen`** — Status / Waiting.html design exists; the iOS analogue is DEBUG-only too, so the right move is to (a) add the route, and (b) thread it onto the claim/submission happy paths on both platforms.
4. **40+ "BUILD" placeholders** point to screens that exist but aren't routed — these are the cheapest wins (Bill detail, Add a bill, Document detail, Maintenance detail, etc.). A single P1 sweep can close them.
5. **The catch-all `.placeholder(label:)` / `ChildRoutes.PLACEHOLDER` pattern is fine to keep** — it's the safe-by-default behavior when a new affordance lands and a destination is still in design.

