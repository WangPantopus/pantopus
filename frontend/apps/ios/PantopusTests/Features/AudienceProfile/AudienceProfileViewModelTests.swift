//
//  AudienceProfileViewModelTests.swift
//  PantopusTests
//
//  Covers the T3.3 Public Profile VM: load() projects header /
//  updates / followers / threads from a parallel bundle of GETs;
//  empty persona transitions to .empty; submitUpdate() POSTs and
//  refreshes; failures roll back composer state.
//

import XCTest
@testable import Pantopus

@MainActor
final class AudienceProfileViewModelTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    private func makeAPI() -> APIClient {
        APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(),
            retryPolicy: .none
        )
    }

    private static let meJSON = """
    {
      "persona": {
        "id": "p_demo",
        "handle": "mayabuilds",
        "displayName": "Maya Builds",
        "avatarUrl": null,
        "bio": "Builder.",
        "category": "creator",
        "audienceLabel": "followers",
        "followerCount": 42,
        "postCount": 7
      },
      "channel": { "id": "ch_demo", "title": "Maya Broadcast", "status": "active" }
    }
    """

    private static let audienceJSON = """
    {
      "persona": null,
      "items": [
        {"membershipId": "m1", "fanHandle": "alex", "fanDisplayName": "Alex",
         "status": "active", "tier": {"rank": 1, "name": "Followers"},
         "verifiedLocal": true, "tenureMonths": 3, "joinedMonth": "2026-02"},
        {"membershipId": "m2", "fanHandle": "billie", "fanDisplayName": "Billie B.",
         "status": "active", "tier": {"rank": 2, "name": "Members"},
         "tenureMonths": 12, "joinedMonth": "2025-05"},
        {"membershipId": "m3", "fanHandle": "cory", "fanDisplayName": "Cory K.",
         "status": "active", "tier": {"rank": 3, "name": "Insiders"},
         "tenureMonths": 1, "joinedMonth": "2026-04"}
      ],
      "counts": {"totalActive": 12, "pending": 3, "byTier": {"1": 8, "2": 3, "3": 1, "4": 0}}
    }
    """

    private static let postsJSON = """
    {"posts":[
      {"id":"u1","body":"New mural going up next week.","created_at":"2026-05-14T18:00:00Z",
       "visibility":"followers","delivered_count":40,"read_count":31},
      {"id":"u2","body":"Workshop seats open.","created_at":"2026-05-13T09:00:00Z",
       "visibility":"tier_or_above","target_tier_rank":2,"delivered_count":3,"read_count":2}
    ]}
    """

    private static let tiersJSON = """
    {"tiers":[
      {"id":"t1","rank":1,"name":"Followers","priceCents":0,"currency":"usd"},
      {"id":"t2","rank":2,"name":"Members","priceCents":500,"currency":"usd"},
      {"id":"t3","rank":3,"name":"Insiders","priceCents":2500,"currency":"usd"}
    ]}
    """

    private static let statsJSON = """
    {"counts":{"followers":8,"members":3,"insiders":1,"direct":0}}
    """

    private static let threadsJSON = """
    {"threads":[
      {"id":"th1","membershipId":"m1","fanHandle":"alex","fanDisplayName":"Alex",
       "tier":{"rank":2,"name":"Members"},
       "lastMessagePreview":"Loved the workshop","lastMessageAt":"2026-05-15T10:00:00Z",
       "unreadCount":2}
    ]}
    """

    private func loadedSequence() -> [SequencedURLProtocol.Response] {
        [
            .status(200, body: Self.meJSON),
            .status(200, body: Self.audienceJSON),
            .status(200, body: Self.postsJSON),
            .status(200, body: Self.tiersJSON),
            .status(200, body: Self.statsJSON),
            .status(200, body: Self.threadsJSON)
        ]
    }

    func testLoadProjectsHeaderUpdatesFollowersThreads() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        guard case let .loaded(loaded) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(loaded.header.displayName, "Maya Builds")
        XCTAssertEqual(loaded.header.handle, "@mayabuilds")
        XCTAssertEqual(loaded.header.followerCount, 12)
        XCTAssertEqual(loaded.header.newThisWeek, 3)
        XCTAssertEqual(loaded.updates.count, 2)
        XCTAssertEqual(loaded.updates.first?.visibility, .followers)
        XCTAssertEqual(loaded.updates.last?.visibility, .tierOrAbove)
        XCTAssertEqual(loaded.updates.last?.targetTierRank, 2)
        XCTAssertEqual(loaded.followers.count, 3)
        XCTAssertEqual(loaded.followers.first?.tierName, "Followers")
        XCTAssertEqual(loaded.followers.first?.tenureMonths, 3)
        XCTAssertEqual(loaded.followers.first?.joinedMonth, "2026-02")
        XCTAssertEqual(loaded.threads.count, 1)
        XCTAssertEqual(loaded.threads.first?.unreadCount, 2)
        XCTAssertEqual(loaded.channelId, "ch_demo")
    }

    func testLoadProjectsAnalyticsCellsAndStackedBar() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        guard case let .loaded(loaded) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        XCTAssertEqual(loaded.analyticsCells.count, 4)
        XCTAssertEqual(loaded.analyticsCells.first { $0.id == "followers" }?.value, "8")
        XCTAssertEqual(loaded.analyticsCells.first { $0.id == "direct" }?.value, "0")
        XCTAssertEqual(loaded.tierBreakdown.total, 12) // 8 + 3 + 1 + 0
        XCTAssertEqual(loaded.tierBreakdown.segments.count, 3) // Three tiers in fixture
        XCTAssertEqual(loaded.tierBreakdown.segments.first?.count, 8)
        // Tier chips include "All" + one per tier.
        XCTAssertEqual(loaded.tierChips.count, 4)
        XCTAssertEqual(loaded.tierChips.first?.id, "all")
        XCTAssertEqual(loaded.tierChips.first?.count, 12)
    }

    func testEmptyPersonaTransitionsToEmpty() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: """
            {"persona": null, "channel": null}
            """)
        ]
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        guard case .empty = vm.state else {
            XCTFail("Expected .empty when persona is null")
            return
        }
    }

    func testLoadFailureTransitionsError() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{}")]
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        guard case .error = vm.state else {
            XCTFail("Expected .error")
            return
        }
    }

    func testSubmitUpdateRequiresNonEmptyBody() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        vm.composer.text = "   "
        XCTAssertFalse(vm.composer.canSubmit)
        await vm.submitUpdate()
        // Composer text should remain (no submission attempted).
        XCTAssertEqual(vm.composer.text, "   ")
    }

    func testSubmitUpdateTierOrAboveRequiresRank() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        vm.composer.text = "Hello tier 2"
        vm.composer.visibility = .tierOrAbove
        vm.composer.targetTierRank = nil
        XCTAssertFalse(vm.composer.canSubmit)
        vm.composer.targetTierRank = 2
        XCTAssertTrue(vm.composer.canSubmit)
    }

    func testSubmitUpdateSuccessClearsComposerAndReloads() async {
        // 6 GETs for initial load + 1 POST + 6 GETs for refresh.
        var seq = loadedSequence()
        seq.append(.status(200, body: """
        {"message":{"id":"new1","body":"Hello","visibility":"followers","created_at":"2026-05-15T12:00:00Z"}}
        """))
        seq.append(contentsOf: loadedSequence())
        SequencedURLProtocol.sequence = seq
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        vm.composer.text = "Hello"
        vm.composer.visibility = .followers
        await vm.submitUpdate()
        XCTAssertEqual(vm.composer.text, "")
        XCTAssertNil(vm.composer.error)
        XCTAssertFalse(vm.composer.isSubmitting)
        // VM should have reloaded — state still .loaded.
        guard case .loaded = vm.state else {
            XCTFail("Expected .loaded after refresh")
            return
        }
    }

    func testSubmitUpdateFailurePopulatesError() async {
        var seq = loadedSequence()
        seq.append(.status(500, body: "{}"))
        SequencedURLProtocol.sequence = seq
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        vm.composer.text = "Hello"
        await vm.submitUpdate()
        XCTAssertNotNil(vm.composer.error)
        XCTAssertFalse(vm.composer.isSubmitting)
        // Composer text preserved so the user can retry.
        XCTAssertEqual(vm.composer.text, "Hello")
    }

    func testTierFilterReducesVisibleFollowers() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        XCTAssertEqual(vm.visibleFollowers.count, 3)
        vm.selectTierFilter(2)
        XCTAssertEqual(vm.visibleFollowers.count, 1)
        XCTAssertEqual(vm.visibleFollowers.first?.tierRank, 2)
        vm.selectTierFilter(nil)
        XCTAssertEqual(vm.visibleFollowers.count, 3)
    }

    func testSearchFiltersByDisplayNameAndHandle() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        vm.followerSearchText = "billie"
        XCTAssertEqual(vm.visibleFollowers.count, 1)
        XCTAssertEqual(vm.visibleFollowers.first?.handle, "@billie")
        // Match on display name (case-insensitive).
        vm.followerSearchText = "CoRy"
        XCTAssertEqual(vm.visibleFollowers.count, 1)
        XCTAssertEqual(vm.visibleFollowers.first?.handle, "@cory")
        // Match on handle prefix without the @.
        vm.followerSearchText = "ale"
        XCTAssertEqual(vm.visibleFollowers.count, 1)
        XCTAssertEqual(vm.visibleFollowers.first?.handle, "@alex")
        // Empty query (whitespace only) returns all.
        vm.followerSearchText = "  "
        XCTAssertEqual(vm.visibleFollowers.count, 3)
        // Non-match yields empty.
        vm.followerSearchText = "zzz"
        XCTAssertEqual(vm.visibleFollowers.count, 0)
    }

    func testSortDefaultsToNewestActiveAndPreservesAPIOrder() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        XCTAssertEqual(vm.followerSort, .newestActive)
        XCTAssertEqual(vm.visibleFollowers.map(\.handle), ["@alex", "@billie", "@cory"])
    }

    func testSortHighestTierOrdersByTierRankDescending() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        vm.selectFollowerSort(.highestTier)
        XCTAssertEqual(vm.visibleFollowers.map(\.handle), ["@cory", "@billie", "@alex"])
    }

    func testSortRecentlyJoinedOrdersByTenureAscending() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        vm.selectFollowerSort(.recentlyJoined)
        // tenureMonths: alex=3, billie=12, cory=1 → cory, alex, billie.
        XCTAssertEqual(vm.visibleFollowers.map(\.handle), ["@cory", "@alex", "@billie"])
    }

    func testSortMostEngagedFavoursHigherTierThenLongerTenure() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        vm.selectFollowerSort(.mostEngaged)
        // rank desc: cory(3) > billie(2) > alex(1). Differs from
        // highest-tier in that ties break on tenure desc — no ties
        // here, so order matches highestTier; the per-state branches
        // are covered by tier-tie unit on the projection function.
        XCTAssertEqual(vm.visibleFollowers.map(\.handle), ["@cory", "@billie", "@alex"])
    }

    func testSortMostEngagedTieBreaksOnLongerTenure() {
        let rows = [
            FollowerRowContent(
                id: "a", displayName: "A", handle: "@a", avatarUrl: nil,
                tierName: "Members", tierRank: 2, tenureLabel: "1 mo.",
                tenureMonths: 1, joinedMonth: nil, verifiedLocal: false
            ),
            FollowerRowContent(
                id: "b", displayName: "B", handle: "@b", avatarUrl: nil,
                tierName: "Members", tierRank: 2, tenureLabel: "9 mo.",
                tenureMonths: 9, joinedMonth: nil, verifiedLocal: false
            )
        ]
        let sorted = AudienceProfileViewModel.sortFollowers(rows, by: .mostEngaged)
        XCTAssertEqual(sorted.map(\.id), ["b", "a"])
    }

    func testSearchAndSortCombineWithTierFilter() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        vm.selectTierFilter(2)
        vm.followerSearchText = "billie"
        vm.selectFollowerSort(.highestTier)
        XCTAssertEqual(vm.visibleFollowers.count, 1)
        XCTAssertEqual(vm.visibleFollowers.first?.handle, "@billie")
    }

    func testActiveTabDefaultsToUpdates() {
        let vm = AudienceProfileViewModel(api: makeAPI())
        XCTAssertEqual(vm.activeTab, .updates)
        vm.selectTab(.followers)
        XCTAssertEqual(vm.activeTab, .followers)
    }

    func testPostVisibilityFollowersMapsCorrectly() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        guard case let .loaded(loaded) = vm.state else { XCTFail("Expected .loaded")
            return
        }
        let followersCard = loaded.updates.first { $0.id == "u1" }
        XCTAssertEqual(followersCard?.visibilityLabel, "Followers")
        let tierCard = loaded.updates.first { $0.id == "u2" }
        XCTAssertEqual(tierCard?.visibilityLabel, "Tier 2+")
    }

    func testTierChipForRank1SurfacesCorrectCount() async {
        SequencedURLProtocol.sequence = loadedSequence()
        let vm = AudienceProfileViewModel(api: makeAPI())
        await vm.load()
        guard case let .loaded(loaded) = vm.state else { XCTFail("Expected .loaded")
            return
        }
        let chip = loaded.tierChips.first { $0.id == "tier_1" }
        XCTAssertEqual(chip?.count, 8)
    }
}
