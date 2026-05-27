//
//  DeepLinkRouterTests.swift
//  PantopusTests
//

import XCTest
@testable import Pantopus

@MainActor
final class DeepLinkRouterTests: XCTestCase {
    override func setUp() {
        super.setUp()
        _ = DeepLinkRouter.shared.consume() // clear any leftover state
    }

    func testCustomSchemeFeed() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://feed"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .feed)
    }

    func testHTTPSHost() throws {
        let url = try XCTUnwrap(URL(string: "https://pantopus.app/home"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .home)
    }

    func testPostIDExtracted() throws {
        let url = try XCTUnwrap(URL(string: "https://pantopus.app/posts/abc-123"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .post(id: "abc-123"))
    }

    func testConversationIDExtracted() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://messages/conv_42"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .conversation(id: "conv_42"))
    }

    func testUnknownPathFallsBack() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://wat"))
        DeepLinkRouter.shared.handle(url: url)
        if case let .unknown(captured) = DeepLinkRouter.shared.pending {
            XCTAssertEqual(captured, url)
        } else {
            XCTFail("Expected .unknown")
        }
    }

    func testConsumeClearsPending() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://feed"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertNotNil(DeepLinkRouter.shared.consume())
        XCTAssertNil(DeepLinkRouter.shared.pending)
        XCTAssertNil(DeepLinkRouter.shared.consume())
    }

    func testInviteTokenCustomScheme() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://invite/abc-123"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .invite(token: "abc-123"))
    }

    func testInviteTokenHTTPSHost() throws {
        let url = try XCTUnwrap(URL(string: "https://pantopus.app/invite/xyz789"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .invite(token: "xyz789"))
    }

    func testInviteWithoutTokenFallsBack() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://invite"))
        DeepLinkRouter.shared.handle(url: url)
        if case .unknown = DeepLinkRouter.shared.pending {
            // ok
        } else {
            XCTFail("Expected .unknown when /invite is missing the token")
        }
    }

    // MARK: - T4.1 routing table (docs/07-frontend-mobile-app.md §9)

    func testSupportTrainRoute() throws {
        try DeepLinkRouter.shared.handle(url: XCTUnwrap(URL(string: "https://pantopus.app/support-trains/st_1")))
        XCTAssertEqual(DeepLinkRouter.shared.pending, .supportTrain(id: "st_1"))
    }

    func testGigRoute() throws {
        try DeepLinkRouter.shared.handle(url: XCTUnwrap(URL(string: "pantopus://gig/g_42")))
        XCTAssertEqual(DeepLinkRouter.shared.pending, .gig(id: "g_42"))
    }

    func testListingRoute() throws {
        try DeepLinkRouter.shared.handle(url: XCTUnwrap(URL(string: "https://pantopus.app/listing/l_99")))
        XCTAssertEqual(DeepLinkRouter.shared.pending, .listing(id: "l_99"))
    }

    func testHomeDetailRoute() throws {
        try DeepLinkRouter.shared.handle(url: XCTUnwrap(URL(string: "https://pantopus.app/homes/h_1")))
        XCTAssertEqual(DeepLinkRouter.shared.pending, .homeDetail(id: "h_1"))
    }

    func testHomeDashboardRoute() throws {
        try DeepLinkRouter.shared.handle(url: XCTUnwrap(URL(string: "https://pantopus.app/homes/h_1/dashboard")))
        XCTAssertEqual(DeepLinkRouter.shared.pending, .homeDashboard(id: "h_1"))
    }

    func testHomeMemberRequestsRoute() throws {
        try DeepLinkRouter.shared.handle(
            url: XCTUnwrap(URL(string: "https://pantopus.app/homes/h_1/members?tab=requests"))
        )
        XCTAssertEqual(DeepLinkRouter.shared.pending, .homeMemberRequests(id: "h_1"))
    }

    func testHomeMembersWithoutRequestsTabFallsBackToDetail() throws {
        try DeepLinkRouter.shared.handle(
            url: XCTUnwrap(URL(string: "https://pantopus.app/homes/h_1/members"))
        )
        XCTAssertEqual(DeepLinkRouter.shared.pending, .homeDetail(id: "h_1"))
    }

    func testBusinessProfileRoute() throws {
        try DeepLinkRouter.shared.handle(url: XCTUnwrap(URL(string: "https://pantopus.app/businesses/biz_42")))
        XCTAssertEqual(DeepLinkRouter.shared.pending, .businessProfile(businessId: "biz_42"))
    }

    func testEditBusinessPageRoute() throws {
        try DeepLinkRouter.shared.handle(
            url: XCTUnwrap(URL(string: "pantopus://businesses/biz_42/page-editor"))
        )
        XCTAssertEqual(DeepLinkRouter.shared.pending, .editBusinessPage(businessId: "biz_42"))
    }

    func testChatRouteUsesConversationCase() throws {
        try DeepLinkRouter.shared.handle(url: XCTUnwrap(URL(string: "pantopus://chat/room_1")))
        XCTAssertEqual(DeepLinkRouter.shared.pending, .conversation(id: "room_1"))
    }

    func testUserRoute() throws {
        try DeepLinkRouter.shared.handle(url: XCTUnwrap(URL(string: "https://pantopus.app/user/u_demo")))
        XCTAssertEqual(DeepLinkRouter.shared.pending, .user(id: "u_demo"))
    }

    func testConnectionsRoute() throws {
        try DeepLinkRouter.shared.handle(url: XCTUnwrap(URL(string: "pantopus://connections")))
        XCTAssertEqual(DeepLinkRouter.shared.pending, .connections)
    }

    func testNotificationsRoute() throws {
        try DeepLinkRouter.shared.handle(url: XCTUnwrap(URL(string: "pantopus://notifications")))
        XCTAssertEqual(DeepLinkRouter.shared.pending, .notifications)
    }

    // MARK: - T6.1c P5 — auth deep links

    func testResetPasswordCustomScheme() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://auth/reset-password?token=hashed-recovery"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .resetPassword(token: "hashed-recovery"))
    }

    func testResetPasswordHTTPSHost() throws {
        let url = try XCTUnwrap(URL(string: "https://pantopus.app/auth/reset-password?token=abc-123"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .resetPassword(token: "abc-123"))
    }

    func testResetPasswordWithoutTokenFallsBack() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://auth/reset-password"))
        DeepLinkRouter.shared.handle(url: url)
        if case .unknown = DeepLinkRouter.shared.pending {
            // ok
        } else {
            XCTFail("Expected .unknown when /auth/reset-password is missing the token")
        }
    }

    func testResetPasswordAcceptsBareShape() throws {
        // The backend's older recovery template emits `/reset-password?token=…`
        // without the `/auth/` prefix — must still route.
        let url = try XCTUnwrap(URL(string: "pantopus://reset-password?token=bare-shape-tok"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .resetPassword(token: "bare-shape-tok"))
    }

    func testResetPasswordAcceptsTokenHashParam() throws {
        // Supabase's older email template uses `token_hash` as the param
        // name; we accept both shapes.
        let url = try XCTUnwrap(URL(string: "pantopus://auth/reset-password?token_hash=hash-shape"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .resetPassword(token: "hash-shape"))
    }

    func testVerifyEmailCustomScheme() throws {
        let url = try XCTUnwrap(
            URL(string: "pantopus://auth/verify-email?token=hashed-otp&email=alice%40example.com")
        )
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(
            DeepLinkRouter.shared.pending,
            .verifyEmail(token: "hashed-otp", email: "alice@example.com")
        )
    }

    func testVerifyEmailHTTPSHost() throws {
        let url = try XCTUnwrap(URL(string: "https://pantopus.app/auth/verify-email?token=tok"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .verifyEmail(token: "tok", email: nil))
    }

    func testVerifyEmailWithoutTokenFallsBack() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://auth/verify-email"))
        DeepLinkRouter.shared.handle(url: url)
        if case .unknown = DeepLinkRouter.shared.pending {
            // ok
        } else {
            XCTFail("Expected .unknown when /auth/verify-email is missing the token")
        }
    }

    // MARK: - Path entry point (notification payload `link` field)

    func testHandlePathBoxesAbsolutePathIntoRouter() {
        DeepLinkRouter.shared.handle(path: "/post/abc-123")
        XCTAssertEqual(DeepLinkRouter.shared.pending, .post(id: "abc-123"))
    }

    func testHandlePathBoxesRelativeIntoRouter() {
        DeepLinkRouter.shared.handle(path: "gig/g_5")
        XCTAssertEqual(DeepLinkRouter.shared.pending, .gig(id: "g_5"))
    }

    func testHandlePathPassesThroughFullURLs() {
        DeepLinkRouter.shared.handle(path: "https://pantopus.app/user/u_1")
        XCTAssertEqual(DeepLinkRouter.shared.pending, .user(id: "u_1"))
    }
}
