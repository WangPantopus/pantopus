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

    init(api: APIClient = .shared) {
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
        // Personal profile is the only hard requirement. Home and stats
        // failures degrade their own surface instead of failing the whole
        // screen.
        guard let profile: ProfileResponse = await optional({
            try await self.api.request(UsersEndpoints.profile())
        }) else {
            state = .error(message: "Couldn't load your profile.")
            return
        }

        let homes: MyHomesResponse? = await optional {
            try await self.api.request(HomesEndpoints.myHomes())
        }
        let stats: UserStatsDTO? = await optional {
            try await self.api.request(UsersEndpoints.stats(userId: profile.user.id))
        }

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
            MeSectionRow(
                id: "openHandshake",
                icon: .userPlus,
                label: "Open Privacy Handshake by persona handle",
                routeKey: "me.debug.openHandshake"
            ),
            MeSectionRow(id: "openInviteToken", icon: .mailbox, label: "Open invite by token", routeKey: "me.debug.openInviteToken"),
            MeSectionRow(
                id: "openStatusWaiting",
                icon: .checkCircle,
                label: "Open Status / Waiting",
                routeKey: "me.debug.openStatusWaiting"
            ),
            MeSectionRow(
                id: "openCeremonialMail",
                icon: .send,
                label: "Open Ceremonial Mail Compose",
                routeKey: "me.debug.openCeremonialMail"
            ),
            MeSectionRow(
                id: "openCeremonialMailOpen",
                icon: .mailbox,
                label: "Open Ceremonial Mail by ID",
                routeKey: "me.debug.openCeremonialMailOpen"
            ),
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
        let tagline = (profile.tagline?.isEmpty == false ? profile.tagline : nil) ?? profile.bio
        let activityValue = "\(stats?.totalGigsCompleted ?? profile.gigsCompleted ?? 0)"
        let trustValue = profile.verified ? "Verified" : "Pending"
        let reputationValue = ratingString(stats?.averageRating ?? profile.averageRating ?? 0)
        return MeIdentityContent(
            identity: .personal,
            displayName: displayName,
            initials: initials(from: displayName),
            handle: "@\(profile.username)",
            locality: locality,
            tagline: tagline,
            verified: profile.verified,
            stats: [
                MeStat(id: "activity", value: activityValue, label: "Activity"),
                MeStat(id: "trust", value: trustValue, label: "Trust"),
                MeStat(id: "reputation", value: reputationValue, label: "Reputation")
            ],
            actionTiles: [
                MeActionTile(id: "posts", icon: .file, label: "My posts", routeKey: "me.posts"),
                MeActionTile(id: "bids", icon: .hammer, label: "My bids", routeKey: "me.bids"),
                MeActionTile(id: "gigs", icon: .clipboardList, label: "My tasks", routeKey: "me.gigs"),
                MeActionTile(id: "offers", icon: .handCoins, label: "Offers", routeKey: "me.offers"),
                MeActionTile(id: "listings", icon: .shoppingBag, label: "Listings", routeKey: "me.listings"),
                MeActionTile(id: "connections", icon: .userPlus, label: "Connections", routeKey: "me.connections")
            ],
            sections: withDebug([
                MeSection(id: "profile_privacy", header: "Profile & Privacy", rows: [
                    MeSectionRow(id: "edit", icon: .edit2, label: "Edit profile", routeKey: "me.editProfile"),
                    MeSectionRow(id: "identityCenter", icon: .shield, label: "Identity Center", routeKey: "me.identityCenter"),
                    MeSectionRow(id: "audience", icon: .megaphone, label: "Audience profile", routeKey: "me.audience")
                ]),
                MeSection(id: "activity", header: "Activity", rows: [
                    MeSectionRow(id: "posts", icon: .file, label: "My posts", routeKey: "me.posts"),
                    MeSectionRow(id: "bids", icon: .hammer, label: "My bids", routeKey: "me.bids"),
                    MeSectionRow(id: "gigs", icon: .clipboardList, label: "My tasks", routeKey: "me.gigs"),
                    MeSectionRow(id: "offers", icon: .handCoins, label: "Offers", routeKey: "me.offers"),
                    MeSectionRow(id: "homes", icon: .home, label: "My homes", routeKey: "me.homes"),
                    MeSectionRow(id: "businesses", icon: .shoppingBag, label: "My businesses", routeKey: "me.businesses")
                ]),
                MeSection(id: "help_legal", header: "Help & Legal", rows: [
                    MeSectionRow(id: "help", icon: .helpCircle, label: "Help", routeKey: "me.help"),
                    MeSectionRow(id: "terms", icon: .file, label: "Terms", routeKey: "me.legal"),
                    MeSectionRow(
                        id: "privacy",
                        icon: .shield,
                        label: "Privacy",
                        value: privacyValue(profile.profileVisibility),
                        routeKey: "me.privacy"
                    )
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
                tagline: "Add a home from the Hub to unlock household tools.",
                verified: false,
                stats: [
                    MeStat(id: "bills", value: "—", label: "Bills due"),
                    MeStat(id: "tasks", value: "—", label: "Open tasks"),
                    MeStat(id: "members", value: "—", label: "Members")
                ],
                actionTiles: homeActionTiles(homeId: nil),
                sections: withDebug(homeSections(homeId: nil, homeName: nil, privacyValue: nil)),
                isUnbound: true
            )
        }
        let home = primary.home
        let address = home.address ?? "Your home"
        let displayName = home.name?.isEmpty == false ? home.name ?? address : address
        let locality = [home.city, home.state].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")
        let memberCount = homes.count
        // Only surface the address as a tagline when the display name is
        // a separate household name (e.g. "Cozy Hideout") — otherwise
        // the tagline would just repeat the title.
        let homeTagline: String? = (home.name?.isEmpty == false) ? home.address : nil
        return MeIdentityContent(
            identity: .home,
            displayName: displayName,
            initials: initials(from: displayName),
            handle: "Household · \(memberCount) member\(memberCount == 1 ? "" : "s")",
            locality: locality.isEmpty ? profileLocality : locality,
            tagline: homeTagline,
            verified: primary.ownershipStatus == "verified",
            stats: [
                MeStat(id: "bills", value: "—", label: "Bills due"),
                MeStat(id: "tasks", value: "—", label: "Open tasks"),
                MeStat(id: "members", value: "\(memberCount)", label: "Members")
            ],
            actionTiles: homeActionTiles(homeId: home.id),
            sections: withDebug(homeSections(
                homeId: home.id,
                homeName: displayName,
                privacyValue: "Neighbors"
            ))
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
            tagline: "Business identity is set up in the web app today; mobile read APIs land later.",
            verified: false,
            stats: [
                MeStat(id: "orders", value: "—", label: "Orders"),
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
                MeSection(id: "business", header: "Business", rows: [
                    MeSectionRow(id: "profile", icon: .edit2, label: "Edit business profile", routeKey: "me.business.editProfile"),
                    MeSectionRow(id: "settings", icon: .menu, label: "Settings", routeKey: "me.settings")
                ]),
                MeSection(id: "help_legal", header: "Help & Legal", rows: [
                    MeSectionRow(id: "help", icon: .helpCircle, label: "Help", routeKey: "me.help"),
                    MeSectionRow(id: "terms", icon: .file, label: "Terms", routeKey: "me.legal"),
                    MeSectionRow(id: "privacy", icon: .shield, label: "Privacy", routeKey: "me.privacy")
                ])
            ]),
            isUnbound: true
        )
    }

    private static func homeActionTiles(homeId: String?) -> [MeActionTile] {
        let args = homeId.map { ["homeId": $0] } ?? [:]
        return [
            MeActionTile(id: "bills", icon: .file, label: "Bills", routeKey: "me.bills", routeArgs: args),
            MeActionTile(id: "maintenance", icon: .hammer, label: "Maintenance", routeKey: "me.maintenance", routeArgs: args),
            MeActionTile(id: "pets", icon: .heart, label: "Pets", routeKey: "me.pets", routeArgs: args),
            MeActionTile(id: "members", icon: .userPlus, label: "Members", routeKey: "me.members", routeArgs: args),
            MeActionTile(id: "polls", icon: .checkCircle, label: "Polls", routeKey: "me.polls", routeArgs: args),
            MeActionTile(id: "calendar", icon: .calendar, label: "Calendar", routeKey: "me.calendar", routeArgs: args),
            MeActionTile(id: "docs", icon: .file, label: "Documents", routeKey: "me.docs", routeArgs: args)
        ]
    }

    private static func homeSections(homeId: String?, homeName: String?, privacyValue: String?) -> [MeSection] {
        var args: [String: String] = [:]
        if let homeId, !homeId.isEmpty { args["homeId"] = homeId }
        // homeName carries the access-codes top-bar subtitle so the
        // designed "412 Birch Ln · Maria's household" line renders
        // without a second fetch.
        var accessArgs = args
        if let homeName, !homeName.isEmpty { accessArgs["homeName"] = homeName }
        return [
            MeSection(id: "household", header: "Household", rows: [
                MeSectionRow(id: "members", icon: .userPlus, label: "Members", routeKey: "me.members", routeArgs: args),
                MeSectionRow(id: "owners", icon: .shield, label: "Owners", routeKey: "me.owners", routeArgs: args),
                MeSectionRow(id: "access", icon: .lock, label: "Access codes", routeKey: "me.access", routeArgs: accessArgs)
            ]),
            MeSection(id: "activity", header: "Activity", rows: [
                MeSectionRow(id: "bills", icon: .file, label: "Bills", routeKey: "me.bills", routeArgs: args),
                MeSectionRow(id: "maintenance", icon: .hammer, label: "Maintenance", routeKey: "me.maintenance", routeArgs: args),
                MeSectionRow(id: "tasks", icon: .hammer, label: "Household tasks", routeKey: "me.tasks", routeArgs: args),
                MeSectionRow(id: "packages", icon: .mailbox, label: "Packages", routeKey: "me.packages", routeArgs: args),
                MeSectionRow(id: "emergency", icon: .shield, label: "Emergency info", routeKey: "me.emergency", routeArgs: args)
            ]),
            MeSection(id: "help_legal", header: "Help & Legal", rows: [
                MeSectionRow(id: "help", icon: .helpCircle, label: "Help", routeKey: "me.help"),
                MeSectionRow(id: "terms", icon: .file, label: "Terms", routeKey: "me.legal"),
                MeSectionRow(
                    id: "privacy",
                    icon: .shield,
                    label: "Privacy",
                    value: privacyValue,
                    routeKey: "me.home.privacy",
                    routeArgs: args
                )
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
        case "public": "Public"
        case "registered": "Neighbors"
        case "private": "Strict"
        default: nil
        }
    }
}
