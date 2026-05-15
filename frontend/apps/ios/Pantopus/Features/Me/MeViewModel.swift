//
//  MeViewModel.swift
//  Pantopus
//
//  Fetches the three identity bundles in parallel and projects them
//  onto `MeIdentityContent`. The Personal identity reads
//  `GET /api/users/profile` + `GET /api/users/:id/stats`. The Home
//  identity reads `GET /api/homes/my-homes` and uses the primary home
//  (or surfaces an `isUnbound` empty state when no home exists). The
//  Business identity ships an `isUnbound` empty state until business
//  read APIs land in mobile.
//

import Foundation
import Logging
import Observation

/// Top-level Me view-model.
@Observable
@MainActor
public final class MeViewModel {
    /// Current render state.
    public private(set) var state: MeState = .loading

    /// Currently selected identity.
    public private(set) var activeIdentity: MeIdentity = .personal

    /// Transient toast surface.
    public var toastMessage: String?

    private let api: APIClient
    private let logger = Logger(label: "app.pantopus.ios.Me")

    public init(api: APIClient = .shared) {
        self.api = api
    }

    /// First-time load — no-op when we already have content.
    public func load() async {
        if case .loaded = state { return }
        await fetch()
    }

    /// Pull-to-refresh / retry.
    public func refresh() async {
        await fetch()
    }

    /// Switch identity pill — pure UI rebind, no refetch needed.
    public func selectIdentity(_ identity: MeIdentity) {
        guard identity != activeIdentity else { return }
        activeIdentity = identity
    }

    // MARK: - Fetch

    private func fetch() async {
        // Personal profile + stats. Home and Business data run in
        // parallel; either failing degrades the relevant identity into
        // its `isUnbound` empty state rather than failing the whole
        // screen.
        async let profileTask: ProfileResponse? = optional {
            try await self.api.request(UsersEndpoints.profile())
        }
        async let homesTask: MyHomesResponse? = optional {
            try await self.api.request(HomesEndpoints.myHomes())
        }
        guard let profile = await profileTask else {
            state = .error(message: "Couldn't load your profile.")
            return
        }
        let homes = await homesTask
        async let statsTask: UserStatsDTO? = optional {
            try await self.api.request(UsersEndpoints.stats(userId: profile.user.id))
        }
        let stats = await statsTask

        let personal = Self.buildPersonal(profile: profile.user, stats: stats)
        let home = Self.buildHome(homes: homes?.homes ?? [], profileLocality: Self.localityString(profile.user))
        let business = Self.buildBusiness(profile: profile.user)
        state = .loaded(personal: personal, home: home, business: business)
    }

    private func optional<T: Sendable>(_ operation: @Sendable () async throws -> T) async -> T? {
        do {
            return try await operation()
        } catch {
            logger.warning("Me identity fetch failed: \(error)")
            return nil
        }
    }

    // MARK: - Projections

    /// Debug-only deep-link section appended to every identity in
    /// development builds. Preserves the legacy YouTabRoot debug
    /// affordances (open profile / post by ID, etc.) without
    /// re-introducing the List-of-buttons chrome.
    private static var debugSection: MeSection? {
        #if DEBUG
        MeSection(id: "debug", header: "Debug", rows: [
            MeSectionRow(id: "openProfile", icon: .search, label: "Open public profile by ID", routeKey: "me.debug.openProfile"),
            MeSectionRow(id: "openPost", icon: .search, label: "Open Pulse post by ID", routeKey: "me.debug.openPost"),
            MeSectionRow(id: "openHandshake", icon: .userPlus, label: "Open Privacy Handshake by persona handle", routeKey: "me.debug.openHandshake"),
            MeSectionRow(id: "openInviteToken", icon: .mailbox, label: "Open invite by token", routeKey: "me.debug.openInviteToken"),
            MeSectionRow(id: "openStatusWaiting", icon: .checkCircle, label: "Open Status / Waiting", routeKey: "me.debug.openStatusWaiting"),
            MeSectionRow(id: "openCeremonialMail", icon: .send, label: "Open Ceremonial Mail Compose", routeKey: "me.debug.openCeremonialMail"),
            MeSectionRow(id: "openCeremonialMailOpen", icon: .mailbox, label: "Open Ceremonial Mail by ID", routeKey: "me.debug.openCeremonialMailOpen"),
            MeSectionRow(id: "inviteOwner", icon: .userPlus, label: "Invite owner to home by ID", routeKey: "me.debug.inviteOwner"),
            MeSectionRow(id: "disambiguate", icon: .mailbox, label: "Disambiguate mail by ID", routeKey: "me.debug.disambiguate")
        ])
        #else
        nil
        #endif
    }

    private static func withDebug(_ sections: [MeSection]) -> [MeSection] {
        guard let debugSection else { return sections }
        return sections + [debugSection]
    }

    private static func buildPersonal(profile: UserProfile, stats: UserStatsDTO?) -> MeIdentityContent {
        let name = profile.name.isEmpty
            ? [profile.firstName, profile.lastName].filter { !$0.isEmpty }.joined(separator: " ")
            : profile.name
        let displayName = name.isEmpty ? "Pantopus user" : name
        let locality = localityString(profile)
        return MeIdentityContent(
            identity: .personal,
            displayName: displayName,
            initials: initials(from: displayName),
            handle: "@\(profile.username)",
            locality: locality,
            bio: profile.bio,
            verified: profile.verified,
            stats: [
                MeStat(id: "posts", value: "\(profile.gigsPosted ?? 0)", label: "Posts"),
                MeStat(id: "gigs_done", value: "\(stats?.totalGigsCompleted ?? profile.gigsCompleted ?? 0)", label: "Gigs done"),
                MeStat(id: "listings", value: "\(stats?.totalGigsPosted ?? 0)", label: "Listings"),
                MeStat(id: "rating", value: Self.ratingString(stats?.averageRating ?? profile.averageRating ?? 0), label: "Rating")
            ],
            actionTiles: [
                MeActionTile(id: "bids", icon: .file, label: "My bids", badge: nil, routeKey: "me.bids"),
                MeActionTile(id: "gigs", icon: .hammer, label: "My gigs", badge: nil, routeKey: "me.gigs"),
                MeActionTile(id: "listings", icon: .shoppingBag, label: "My listings", badge: nil, routeKey: "me.listings"),
                MeActionTile(id: "saved", icon: .star, label: "Saved", badge: nil, routeKey: "me.saved"),
                MeActionTile(id: "wallet", icon: .shield, label: "Wallet", badge: nil, routeKey: "me.wallet"),
                MeActionTile(id: "mail", icon: .mailbox, label: "Mail", badge: nil, routeKey: "me.mail")
            ],
            sections: withDebug([
                MeSection(id: "account", header: "Account", rows: [
                    MeSectionRow(id: "edit", icon: .edit2, label: "Edit profile", routeKey: "me.editProfile"),
                    MeSectionRow(id: "settings", icon: .menu, label: "Settings", routeKey: "me.settings"),
                    MeSectionRow(id: "privacy", icon: .shield, label: "Privacy",
                                 value: privacyValue(profile.profileVisibility), routeKey: "me.privacy")
                ]),
                MeSection(id: "activity", header: "Activity", rows: [
                    MeSectionRow(id: "posts", icon: .file, label: "My posts", routeKey: "me.posts"),
                    MeSectionRow(id: "homes", icon: .home, label: "My homes", routeKey: "me.homes"),
                    MeSectionRow(id: "businesses", icon: .shoppingBag, label: "My businesses", routeKey: "me.businesses")
                ]),
                MeSection(id: "support", header: "Support", rows: [
                    MeSectionRow(id: "help", icon: .helpCircle, label: "Help", routeKey: "me.help"),
                    MeSectionRow(id: "legal", icon: .file, label: "Legal", routeKey: "me.legal"),
                    MeSectionRow(id: "about", icon: .info, label: "About", value: appVersion(), routeKey: "me.about")
                ])
            ])
        )
    }

    private static func buildHome(homes: [MyHome], profileLocality: String?) -> MeIdentityContent {
        guard let primary = homes.first(where: { $0.isPrimaryOwner == true }) ?? homes.first else {
            return MeIdentityContent(
                identity: .home,
                displayName: "Claim a home",
                initials: "H",
                handle: "No home yet",
                locality: profileLocality,
                bio: "Add a home from the Hub to unlock household tools.",
                verified: false,
                stats: [
                    MeStat(id: "packages", value: "—", label: "Packages"),
                    MeStat(id: "bills", value: "—", label: "Bills"),
                    MeStat(id: "members", value: "—", label: "Members"),
                    MeStat(id: "codes", value: "—", label: "Codes")
                ],
                actionTiles: homeActionTiles(),
                sections: withDebug(homeSections(privacyValue: nil)),
                isUnbound: true
            )
        }
        let home = primary.home
        let address = home.address ?? "Your home"
        let displayName = home.name?.isEmpty == false ? home.name ?? address : address
        let locality = [home.city, home.state].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")
        return MeIdentityContent(
            identity: .home,
            displayName: displayName,
            initials: initials(from: displayName),
            handle: "Household · \(homes.count) member\(homes.count == 1 ? "" : "s")",
            locality: locality.isEmpty ? profileLocality : locality,
            bio: nil,
            verified: primary.ownershipStatus == "verified",
            stats: [
                MeStat(id: "packages", value: "—", label: "Packages"),
                MeStat(id: "bills", value: "—", label: "Bills"),
                MeStat(id: "members", value: "\(homes.count)", label: "Members"),
                MeStat(id: "codes", value: "—", label: "Codes")
            ],
            actionTiles: homeActionTiles(),
            sections: withDebug(homeSections(privacyValue: "Neighbors"))
        )
    }

    private static func buildBusiness(profile: UserProfile) -> MeIdentityContent {
        // Mobile doesn't ship business-read APIs yet; surface a polite
        // empty state so the identity switcher remains a real button
        // and the user understands the destination.
        MeIdentityContent(
            identity: .business,
            displayName: "Add a business",
            initials: "B",
            handle: "No business yet",
            locality: localityString(profile),
            bio: "Business identity is set up in the web app today; mobile read APIs land later.",
            verified: false,
            stats: [
                MeStat(id: "orders", value: "—", label: "Orders"),
                MeStat(id: "earnings", value: "—", label: "Earnings"),
                MeStat(id: "products", value: "—", label: "Products"),
                MeStat(id: "rating", value: "—", label: "Rating")
            ],
            actionTiles: [
                MeActionTile(id: "orders", icon: .file, label: "Orders", routeKey: "me.business.orders"),
                MeActionTile(id: "products", icon: .shoppingBag, label: "Products", routeKey: "me.business.products"),
                MeActionTile(id: "payouts", icon: .shield, label: "Payouts", routeKey: "me.business.payouts"),
                MeActionTile(id: "team", icon: .userPlus, label: "Team", routeKey: "me.business.team"),
                MeActionTile(id: "hours", icon: .info, label: "Hours", routeKey: "me.business.hours"),
                MeActionTile(id: "promo", icon: .megaphone, label: "Promo", routeKey: "me.business.promo")
            ],
            sections: withDebug([
                MeSection(id: "account", header: "Business", rows: [
                    MeSectionRow(id: "profile", icon: .edit2, label: "Edit business profile", routeKey: "me.business.editProfile"),
                    MeSectionRow(id: "settings", icon: .menu, label: "Settings", routeKey: "me.business.settings")
                ]),
                MeSection(id: "support", header: "Support", rows: [
                    MeSectionRow(id: "help", icon: .helpCircle, label: "Help", routeKey: "me.help"),
                    MeSectionRow(id: "legal", icon: .file, label: "Legal", routeKey: "me.legal"),
                    MeSectionRow(id: "about", icon: .info, label: "About", value: appVersion(), routeKey: "me.about")
                ])
            ]),
            isUnbound: true
        )
    }

    private static func homeActionTiles() -> [MeActionTile] {
        [
            MeActionTile(id: "access", icon: .lock, label: "Access", routeKey: "me.home.access"),
            MeActionTile(id: "bills", icon: .file, label: "Bills", routeKey: "me.home.bills"),
            MeActionTile(id: "packages", icon: .shoppingBag, label: "Packages", routeKey: "me.home.packages"),
            MeActionTile(id: "members", icon: .userPlus, label: "Members", routeKey: "me.home.members"),
            MeActionTile(id: "docs", icon: .file, label: "Docs", routeKey: "me.home.docs"),
            MeActionTile(id: "calendar", icon: .calendar, label: "Calendar", routeKey: "me.home.calendar")
        ]
    }

    private static func homeSections(privacyValue: String?) -> [MeSection] {
        [
            MeSection(id: "household", header: "Household", rows: [
                MeSectionRow(id: "address", icon: .home, label: "Edit address", routeKey: "me.home.editAddress"),
                MeSectionRow(id: "invite", icon: .userPlus, label: "Invite member", routeKey: "me.home.invite"),
                MeSectionRow(id: "privacy", icon: .shield, label: "Privacy", value: privacyValue, routeKey: "me.home.privacy")
            ]),
            MeSection(id: "activity", header: "Activity", rows: [
                MeSectionRow(id: "delivery", icon: .mailbox, label: "Delivery log", routeKey: "me.home.deliveryLog"),
                MeSectionRow(id: "maintenance", icon: .hammer, label: "Maintenance", routeKey: "me.home.maintenance"),
                MeSectionRow(id: "utilities", icon: .info, label: "Utilities", routeKey: "me.home.utilities")
            ]),
            MeSection(id: "support", header: "Support", rows: [
                MeSectionRow(id: "help", icon: .helpCircle, label: "Help", routeKey: "me.help"),
                MeSectionRow(id: "about", icon: .info, label: "About", value: appVersion(), routeKey: "me.about")
            ])
        ]
    }

    // MARK: - Helpers

    private static func localityString(_ profile: UserProfile) -> String? {
        let parts = [profile.city, profile.state].compactMap { $0 }.filter { !$0.isEmpty }
        return parts.isEmpty ? nil : parts.joined(separator: ", ")
    }

    private static func initials(from name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        let result = parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
        return result.isEmpty ? "?" : result
    }

    private static func ratingString(_ rating: Double) -> String {
        rating > 0 ? String(format: "%.1f", rating) : "—"
    }

    private static func privacyValue(_ visibility: String?) -> String? {
        switch visibility?.lowercased() {
        case "public": return "Public"
        case "registered": return "Neighbors"
        case "private": return "Strict"
        default: return nil
        }
    }

    private static func appVersion() -> String {
        let dict = Bundle.main.infoDictionary
        let version = dict?["CFBundleShortVersionString"] as? String ?? "—"
        return "v\(version)"
    }
}
