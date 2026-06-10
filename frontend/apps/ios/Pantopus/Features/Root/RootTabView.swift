//
//  RootTabView.swift
//  Pantopus
//
//  The 5-tab bottom bar — Home · Pulse · Tasks · Marketplace · Messages —
//  that sits above every signed-in screen. Home is selected at launch.
//

import Logging
import SwiftUI

/// A tab in the primary bottom bar. Encoded as an enum so call sites never
/// reach for stringly-typed paths.
public enum RootTab: Hashable, CaseIterable {
    case home, pulse, tasks, marketplace, messages

    /// Human-readable label rendered under each tab icon.
    public var label: String {
        switch self {
        case .home: "Home"
        case .pulse: "Pulse"
        case .tasks: "Tasks"
        case .marketplace: "Marketplace"
        case .messages: "Messages"
        }
    }

    /// Stable accessibility / test identifier suffix (`tab.<id>`).
    public var id: String {
        switch self {
        case .home: "home"
        case .pulse: "pulse"
        case .tasks: "tasks"
        case .marketplace: "marketplace"
        case .messages: "messages"
        }
    }

    /// Design-system icon token for the tab.
    public var icon: PantopusIcon {
        switch self {
        case .home: .home
        case .pulse: .rss
        case .tasks: .briefcase
        case .marketplace: .shoppingBag
        case .messages: .messageCircle
        }
    }
}

/// Observable state for the root tab view. Holds the selected tab and a
/// cached count for the Messages badge.
@Observable
@MainActor
public final class RootTabModel {
    /// Currently selected tab. Starts at `.home`.
    public var selected: RootTab = .home
    /// Unread Messages count rendered as the tab badge. Wired to live data in P8.
    public var messagesBadge: Int = 0
    public init() {}
}

/// Root tab container for signed-in users. Each tab hosts its own
/// NavigationStack so deep navigation within a tab survives tab switches.
public struct RootTabView: View {
    @State private var model = RootTabModel()
    private let chatBadgeStore = ChatBadgeStore.shared
    @State private var router = DeepLinkRouter.shared
    @State private var pendingInviteToken: String?
    @State private var showProfile = false

    public init() {}

    public var body: some View {
        TabView(selection: tabBinding) {
            HubTabRoot { showProfile = true }
                .tabItem { tabLabel(.home) }
                .tag(RootTab.home)

            PulseTabRoot()
                .tabItem { tabLabel(.pulse) }
                .tag(RootTab.pulse)

            TasksTabRoot()
                .tabItem { tabLabel(.tasks) }
                .tag(RootTab.tasks)

            MarketplaceTabRoot()
                .tabItem { tabLabel(.marketplace) }
                .tag(RootTab.marketplace)

            InboxTabRoot()
                .tabItem { tabLabel(.messages) }
                .tag(RootTab.messages)
                .badge(model.messagesBadge)
        }
        .tint(Theme.Color.primary600)
        .environment(model)
        .task {
            await chatBadgeStore.start()
            model.messagesBadge = chatBadgeStore.unreadMessages
        }
        .onChange(of: chatBadgeStore.unreadMessages) { _, unread in
            model.messagesBadge = unread
        }
        .onChange(of: router.pending) { _, pending in
            consumeInviteDeepLinkIfNeeded(pending: pending)
        }
        .task {
            consumeInviteDeepLinkIfNeeded(pending: router.pending)
        }
        .fullScreenCover(isPresented: $showProfile) {
            YouTabRoot()
        }
        .fullScreenCover(
            item: Binding<InviteSheetToken?>(
                get: { pendingInviteToken.map(InviteSheetToken.init(token:)) },
                set: { pendingInviteToken = $0?.token }
            )
        ) { item in
            TokenAcceptView(
                viewModel: TokenAcceptViewModel(
                    token: item.token,
                    onAccepted: { _ in pendingInviteToken = nil },
                    onDeclined: { pendingInviteToken = nil }
                )
            )
        }
    }

    private func consumeInviteDeepLinkIfNeeded(pending: DeepLinkRouter.Destination?) {
        guard let pending else { return }
        // Root owns cross-tab dispatch. Concrete drill-down links stay
        // pending so the selected tab can push them into its own
        // NavigationStack.
        switch pending {
        case let .invite(token):
            pendingInviteToken = token
            _ = router.consume()
        case .feed, .post:
            model.selected = .pulse
        case .gig:
            model.selected = .tasks
        case .listing:
            model.selected = .marketplace
        case .supportTrain, .supportTrainManage, .user,
             .connections, .beacons, .discoverHub,
             .homeDetail, .homeDashboard, .homeMemberRequests,
             .homeOwnersTransfer,
             .verifyLandlord, .postcardVerification,
             .notifications, .createBusiness, .businessProfile, .editBusinessPage,
             .vacationHold, .wallet, .mailDay, .paymentsSettings,
             // B1.6 — batch-2 routing seam destinations all resolve inside the
             // Hub tab's stack (deep links open them on the placeholder).
             .stamps, .mailTask, .mailTranslation, .unboxing, .earn,
             .businessOwner, .viewAs, .waitingRoom:
            model.selected = .home
        case .conversation:
            model.selected = .messages
        case .home:
            model.selected = .home
            _ = router.consume()
        case .resetPassword, .verifyEmail, .unknown:
            _ = router.consume()
        }
    }

    private var tabBinding: Binding<RootTab> {
        Binding(
            get: { model.selected },
            set: { model.selected = $0 }
        )
    }

    private func tabLabel(_ tab: RootTab) -> some View {
        Label {
            Text(tab.label)
        } icon: {
            Icon(tab.icon)
                .accessibilityHidden(true)
        }
        .accessibilityLabel(tab.label)
        .accessibilityIdentifier("tab.\(tab.id)")
    }
}

/// `Identifiable` wrapper so `fullScreenCover(item:)` can fire when
/// the token string is non-nil.
private struct InviteSheetToken: Identifiable, Equatable {
    let token: String
    var id: String {
        token
    }
}

/// Root-level unread badge source for the Messages tab. It mirrors the
/// Expo mobile BadgeContext: seed from `/api/chat/stats`, then keep the
/// count warm with `badge:update` socket events and reconnect refreshes.
/// Muted conversation unread is excluded so the badge reflects "notify me"
/// conversations only.
@Observable
@MainActor
final class ChatBadgeStore {
    static let shared = ChatBadgeStore()

    private(set) var unreadMessages: Int = 0

    private let api: APIClient
    private let socket: SocketClient
    private let preferences: ChatConversationPreferences
    private let logger = Logger(label: "app.pantopus.ios.ChatBadge")
    private var badgeTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private var serverTotalUnread: Int = 0
    private var cachedRows: [ConversationRowContent] = []

    init(
        api: APIClient = .shared,
        socket: SocketClient = .shared,
        preferences: ChatConversationPreferences = .shared
    ) {
        self.api = api
        self.socket = socket
        self.preferences = preferences
    }

    func start() async {
        await refresh()
        subscribeIfNeeded()
    }

    func refresh() async {
        async let statsTask: ChatStatsResponse? = optional {
            try await self.api.request(ChatEndpoints.stats())
        }
        async let conversationsTask: UnifiedConversationsResponse? = optional {
            try await self.api.request(ChatEndpoints.unifiedConversations())
        }
        guard let stats = await statsTask else {
            logger.warning("Chat badge refresh failed: stats unavailable")
            return
        }
        serverTotalUnread = stats.stats.totalUnread
        if let conversations = await conversationsTask {
            let mutedKeys = preferences.mutedKeys()
            cachedRows = conversations.conversations.map {
                Self.snapshotRow(from: $0, mutedKeys: mutedKeys)
            }
        }
        applyAdjustedUnread()
    }

    /// Called by the chat list whenever rows or server totals change.
    func applyListSnapshot(totalUnread: Int, rows: [ConversationRowContent]) {
        serverTotalUnread = totalUnread
        cachedRows = rows
        applyAdjustedUnread()
    }

    func stop() {
        badgeTask?.cancel()
        reconnectTask?.cancel()
        badgeTask = nil
        reconnectTask = nil
    }

    private func applyAdjustedUnread() {
        unreadMessages = ChatUnreadBadgeMath.adjustedTotal(
            serverTotal: serverTotalUnread,
            rows: cachedRows,
            mutedKeys: preferences.mutedKeys()
        )
    }

    private func optional<T: Sendable>(_ operation: @Sendable () async throws -> T) async -> T? {
        do {
            return try await operation()
        } catch {
            logger.warning("Chat badge fetch failed: \(error)")
            return nil
        }
    }

    private static func snapshotRow(
        from dto: UnifiedConversation,
        mutedKeys: Set<String>
    ) -> ConversationRowContent {
        let storageKey =
            switch dto.kind {
            case .conversation: ChatConversationPreferences.personKey(dto.id)
            case .room: ChatConversationPreferences.roomKey(dto.id)
            }
        return ConversationRowContent(
            id: dto.id,
            variant: .dm,
            displayName: dto.name ?? dto.id,
            initials: "?",
            avatarURL: nil,
            identityChip: nil,
            verified: false,
            preview: "",
            timeLabel: "",
            unread: dto.totalUnread,
            pinned: false,
            topicKinds: [],
            storageKey: storageKey,
            isMuted: mutedKeys.contains(storageKey)
        )
    }

    private func subscribeIfNeeded() {
        if badgeTask == nil {
            badgeTask = Task { [weak self] in
                guard let self else { return }
                for await update in socket.events(named: "badge:update", as: ChatBadgeUpdate.self) {
                    serverTotalUnread = update.totalUnread
                    applyAdjustedUnread()
                }
            }
        }
        if reconnectTask == nil {
            reconnectTask = Task { [weak self] in
                guard let self else { return }
                for await state in socket.connectionStates() where state == .connected {
                    await refresh()
                }
            }
        }
    }
}

#Preview {
    RootTabView()
        .environment(AuthManager.previewSignedIn)
}
