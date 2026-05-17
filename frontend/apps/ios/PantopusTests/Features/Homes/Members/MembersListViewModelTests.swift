//
//  MembersListViewModelTests.swift
//  PantopusTests
//
//  T6.3a / P9 — Members. Covers:
//    - load → loaded / empty / error transitions
//    - the three tab buckets count and render correctly
//      (Members excludes guests; Guests excludes non-guests; Pending
//      comes from the same payload's `pendingInvites` array)
//    - tab switching mutates the loaded section without a refetch
//    - row mapping for both occupants (Members/Guests) and pending
//      invites
//    - optimistic remove + rollback
//    - optimistic cancel-invite + rollback
//    - handleInvited(_:) folds a new pending invite at top
//    - FAB tint + variant match the design contract
//

import XCTest
@testable import Pantopus

// swiftlint:disable file_length type_body_length

@MainActor
final class MembersListViewModelTests: XCTestCase {
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

    private static let utc = TimeZone(secondsFromGMT: 0) ?? .current
    private static let fixedNow: Date = {
        var components = DateComponents()
        components.year = 2026
        components.month = 5
        components.day = 15
        components.hour = 12
        components.minute = 0
        components.second = 0
        components.timeZone = utc
        return Calendar(identifier: .gregorian).date(from: components)
            ?? Date(timeIntervalSince1970: 1_778_846_400)
    }()
    private static let utcCalendar: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = utc
        return cal
    }()

    private func makeVM(homeId: String = "home_1") -> MembersListViewModel {
        MembersListViewModel(
            homeId: homeId,
            api: makeAPI(),
            now: { Self.fixedNow },
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        )
    }

    // 3 active occupants (owner + admin + guest) + 1 pending invite.
    private static let populatedJSON = """
    {
      "occupants":[
        {"id":"occ_owner","user_id":"u_owner","role":"owner","is_active":true,
         "start_at":"2024-03-01T00:00:00Z",
         "created_at":"2024-03-01T00:00:00Z",
         "display_name":"Maria Kovacs","username":"maria",
         "avatar_url":null,
         "joined_at":"2024-03-01T00:00:00Z"},
        {"id":"occ_admin","user_id":"u_admin","role":"admin","is_active":true,
         "start_at":"2025-01-15T00:00:00Z",
         "created_at":"2025-01-15T00:00:00Z",
         "display_name":"Jamie Patel","username":"jamie",
         "avatar_url":null,
         "joined_at":"2025-01-15T00:00:00Z"},
        {"id":"occ_guest","user_id":"u_guest","role":"guest","is_active":true,
         "start_at":"2026-05-10T00:00:00Z",
         "created_at":"2026-05-10T00:00:00Z",
         "display_name":"Daniel Okafor","username":"danok",
         "avatar_url":null,
         "joined_at":"2026-05-10T00:00:00Z"}
      ],
      "pendingInvites":[
        {"id":"inv_1","user_id":null,"role":"member","is_active":false,
         "email":"newhouse@example.com","name":"newhouse@example.com",
         "invited_by":"Maria","created_at":"2026-05-14T12:00:00Z"}
      ]
    }
    """

    private static let emptyJSON = """
    {"occupants":[],"pendingInvites":[]}
    """

    // MARK: - Lifecycle

    func testLoadEmptyTransitionsToEmptyOnMembersTab() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.emptyJSON)]
        let vm = makeVM()
        await vm.load()
        guard case let .empty(content) = vm.state else {
            XCTFail("Expected .empty, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.headline, "No members yet")
        XCTAssertEqual(content.ctaTitle, "Invite someone")
    }

    func testLoadPopulatedRendersMembersTabByDefault() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.populatedJSON)]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, hasMore) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        // Default tab is Members → excludes the one guest row.
        XCTAssertEqual(sections.count, 1)
        XCTAssertEqual(sections.first?.rows.count, 2)
        XCTAssertEqual(hasMore, false)
        let titles = sections.first?.rows.map(\.title) ?? []
        XCTAssertEqual(Set(titles), ["Maria Kovacs", "Jamie Patel"])
    }

    func testLoadFailureTransitionsToError() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{}")]
        let vm = makeVM()
        await vm.load()
        guard case .error = vm.state else {
            XCTFail("Expected .error, got \(vm.state)")
            return
        }
    }

    func testLoadIsIdempotentAfterLoaded() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.populatedJSON),
            // Second response would only fire if `load()` refetched.
            .status(200, body: Self.emptyJSON)
        ]
        let vm = makeVM()
        await vm.load()
        await vm.load()
        XCTAssertEqual(SequencedURLProtocol.sequence.count, 1)
    }

    // MARK: - Tab buckets

    func testTabCountsExposedOnTabsArray() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.populatedJSON)]
        let vm = makeVM()
        await vm.load()
        let counts = vm.tabs.reduce(into: [String: Int]()) { acc, tab in
            acc[tab.id] = tab.count
        }
        XCTAssertEqual(counts[MembersTab.members], 2)
        XCTAssertEqual(counts[MembersTab.guests], 1)
        XCTAssertEqual(counts[MembersTab.pending], 1)
    }

    func testSwitchingToGuestsTabFiltersToGuestRolesOnly() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.populatedJSON)]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = MembersTab.guests
        guard case let .loaded(sections, _) = vm.state,
              let row = sections.first?.rows.first else {
            XCTFail("Expected one guest row")
            return
        }
        XCTAssertEqual(sections.first?.rows.count, 1)
        XCTAssertEqual(row.title, "Daniel Okafor")
        XCTAssertEqual(row.subtitle, "Guest")
    }

    func testSwitchingToPendingTabSurfacesPendingInvites() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.populatedJSON)]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = MembersTab.pending
        guard case let .loaded(sections, _) = vm.state,
              let row = sections.first?.rows.first else {
            XCTFail("Expected one pending row")
            return
        }
        XCTAssertEqual(row.title, "newhouse@example.com")
        // Pending rows render the stacked Resend / Cancel pair.
        if case let .verticalActions(primary, secondary) = row.trailing {
            XCTAssertEqual(primary.label, "Resend")
            XCTAssertEqual(secondary.label, "Cancel")
        } else {
            XCTFail("Expected .verticalActions on pending row, got \(row.trailing)")
        }
    }

    func testEmptyGuestsTabShowsGuestEmptyState() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.populatedJSON)]
        let vm = makeVM()
        await vm.load()
        // Remove the guest occupant via the same code path the UI uses.
        await vm.remove(userId: "u_guest")
        vm.selectedTab = MembersTab.guests
        guard case let .empty(content) = vm.state else {
            XCTFail("Expected .empty for Guests tab after removal")
            return
        }
        XCTAssertEqual(content.headline, "No active guests")
    }

    func testEmptyPendingTabShowsPendingEmptyState() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.emptyJSON)]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = MembersTab.pending
        guard case let .empty(content) = vm.state else {
            XCTFail("Expected .empty for Pending tab")
            return
        }
        XCTAssertEqual(content.headline, "No pending invites")
    }

    // MARK: - Row mapping

    func testRowMappingOccupantOwnerCarriesHomeChipAndVerifiedAvatar() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.populatedJSON)]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state,
              let row = sections.first?.rows.first(where: { $0.id == "u_owner" }) else {
            XCTFail("Expected owner row")
            return
        }
        XCTAssertEqual(row.title, "Maria Kovacs")
        XCTAssertEqual(row.subtitle, "Owner")
        XCTAssertEqual(row.inlineChip?.text, "Owner")
        XCTAssertEqual(row.inlineChip?.icon, .home)
        if case let .avatarWithBadge(_, _, _, size, verified) = row.leading {
            XCTAssertEqual(size, .medium)
            XCTAssertTrue(verified)
        } else {
            XCTFail("Expected avatarWithBadge leading, got \(row.leading)")
        }
        if case .kebab = row.trailing {
            // OK
        } else {
            XCTFail("Expected kebab trailing on member row, got \(row.trailing)")
        }
    }

    func testRowMappingGuestRoleEmitsGuestChipAndUnverifiedFallback() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.populatedJSON)]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = MembersTab.guests
        guard case let .loaded(sections, _) = vm.state,
              let row = sections.first?.rows.first else {
            XCTFail("Expected guest row")
            return
        }
        XCTAssertEqual(row.inlineChip?.text, "Guest")
        XCTAssertEqual(row.subtitle, "Guest")
    }

    func testRowMappingPendingInviteRendersInvitedSubline() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.populatedJSON)]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = MembersTab.pending
        guard case let .loaded(sections, _) = vm.state,
              let row = sections.first?.rows.first else {
            XCTFail("Expected pending row")
            return
        }
        XCTAssertEqual(row.title, "newhouse@example.com")
        XCTAssertEqual(row.subtitle, "Member")
        XCTAssertNotNil(row.body)
        XCTAssertTrue(row.body?.hasPrefix("Invited") ?? false)
    }

    // MARK: - Mutations

    func testRemoveOptimisticallyRemovesRow() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.populatedJSON),
            .status(200, body: "{\"message\":\"Member removed\"}")
        ]
        let vm = makeVM()
        await vm.load()
        await vm.remove(userId: "u_admin")
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded after remove")
            return
        }
        // Members tab now has just the owner; admin row is gone.
        XCTAssertEqual(sections.first?.rows.count, 1)
        XCTAssertNil(sections.first?.rows.first { $0.id == "u_admin" })
    }

    func testRemoveFailureRollsBack() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.populatedJSON),
            .status(500, body: "{}")
        ]
        let vm = makeVM()
        await vm.load()
        await vm.remove(userId: "u_admin")
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded after rollback")
            return
        }
        XCTAssertEqual(sections.first?.rows.count, 2)
        XCTAssertNotNil(sections.first?.rows.first { $0.id == "u_admin" })
    }

    func testCancelInviteOptimisticallyRemovesPendingRow() async {
        // Pending invite with a resolved user_id so DELETE …/members/:userId fires.
        let json = """
        {"occupants":[],"pendingInvites":[
          {"id":"inv_1","user_id":"u_pending","role":"member","is_active":false,
           "email":"x@y.com","name":"x@y.com","invited_by":null,
           "created_at":"2026-05-14T12:00:00Z"}
        ]}
        """
        SequencedURLProtocol.sequence = [
            .status(200, body: json),
            .status(200, body: "{\"message\":\"Member removed\"}")
        ]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = MembersTab.pending
        await vm.cancelInvite(inviteId: "inv_1")
        guard case .empty = vm.state else {
            XCTFail("Expected empty Pending tab after cancel, got \(vm.state)")
            return
        }
    }

    func testCancelInviteFailureRollsBackWhenUserIdResolved() async {
        let json = """
        {"occupants":[],"pendingInvites":[
          {"id":"inv_1","user_id":"u_pending","role":"member","is_active":false,
           "email":"x@y.com","name":"x@y.com","invited_by":null,
           "created_at":"2026-05-14T12:00:00Z"}
        ]}
        """
        SequencedURLProtocol.sequence = [
            .status(200, body: json),
            .status(500, body: "{}")
        ]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = MembersTab.pending
        await vm.cancelInvite(inviteId: "inv_1")
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected pending row to roll back into .loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(sections.first?.rows.count, 1)
    }

    func testHandleInvitedInsertsAtTopOfPendingBucket() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.emptyJSON)]
        let vm = makeVM()
        await vm.load()
        let invitation = InvitationDTO(
            id: "new_inv",
            homeId: "home_1",
            invitedBy: nil,
            inviteeEmail: "fresh@example.com",
            inviteeUserId: nil,
            proposedRole: "member",
            createdAt: "2026-05-15T11:59:00Z"
        )
        vm.handleInvited(invitation)
        vm.selectedTab = MembersTab.pending
        guard case let .loaded(sections, _) = vm.state,
              let row = sections.first?.rows.first else {
            XCTFail("Expected pending row after handleInvited")
            return
        }
        XCTAssertEqual(row.id, "new_inv")
        XCTAssertEqual(row.title, "fresh@example.com")
    }

    // MARK: - Chrome

    func testFABIsHomeGreenSecondaryCreate() {
        let vm = makeVM()
        guard let fab = vm.fab else {
            XCTFail("Expected FAB")
            return
        }
        XCTAssertEqual(fab.icon, .userPlus)
        XCTAssertEqual(fab.accessibilityLabel, "Invite member")
        if case .secondaryCreate = fab.variant {
            // OK
        } else {
            XCTFail("Expected .secondaryCreate variant")
        }
        XCTAssertEqual(fab.tint, .home)
    }

    func testNoTopBarActionByDesign() {
        let vm = makeVM()
        XCTAssertNil(vm.topBarAction)
    }

    func testThreeTabsByDesign() {
        let vm = makeVM()
        XCTAssertEqual(vm.tabs.count, 3)
        XCTAssertEqual(vm.tabs.map(\.id), [MembersTab.members, MembersTab.guests, MembersTab.pending])
    }

    func testDefaultSelectedTabIsMembers() {
        let vm = makeVM()
        XCTAssertEqual(vm.selectedTab, MembersTab.members)
    }
}
