//
//  UITestStubProtocol.swift
//  Pantopus
//
//  In-process URL stub used only by XCUITests. Activated when the host
//  app is launched with `UI_TESTS_STUB_API=1` (see `APIClient.shared`).
//  Reads canned response bodies from the launch environment so each test
//  can dictate what the network looks like without a real backend.
//

#if DEBUG
import Foundation

/// `URLProtocol` subclass that intercepts every request on the stubbed
/// `URLSession` and returns canned JSON.
///
/// Environment keys (all optional — sensible fallbacks are provided):
///
/// - `UI_TESTS_PROFILE_GET_BODY` — JSON body for `GET /api/users/profile`.
/// - `UI_TESTS_PROFILE_PATCH_BODY` — JSON body for `PATCH /api/users/profile`.
/// - `UI_TESTS_PROFILE_GET_STATUS` / `UI_TESTS_PROFILE_PATCH_STATUS` —
///    override status codes (default 200).
/// - `UI_TESTS_HOMES_SUGGEST_BODY` — JSON body for
///   `POST /api/homes/property-suggestions`.
/// - `UI_TESTS_HOMES_CHECK_BODY` — JSON body for
///   `POST /api/homes/check-address`.
/// - `UI_TESTS_HOMES_CREATE_BODY` — JSON body for `POST /api/homes`.
/// - `UI_TESTS_HOMES_*_STATUS` — override the corresponding status code.
///
/// The protocol is single-process: each XCUITest spawns the app fresh,
/// so there is no need for cross-test isolation.
final class UITestStubProtocol: URLProtocol {
    override static func canInit(with request: URLRequest) -> Bool {
        request.url?.path.hasPrefix("/api/") ?? false
    }

    override static func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func stopLoading() {}

    override func startLoading() {
        guard let url = request.url else {
            finishWith(status: 400, body: Data())
            return
        }

        let path = url.path
        let method = request.httpMethod?.uppercased() ?? "GET"
        let env = ProcessInfo.processInfo.environment

        switch (method, path) {
        case ("GET", "/api/users/profile"):
            let body = env["UI_TESTS_PROFILE_GET_BODY"] ?? Self.defaultProfileResponseJSON
            let status = Int(env["UI_TESTS_PROFILE_GET_STATUS"] ?? "200") ?? 200
            finishWith(status: status, body: Data(body.utf8))

        case ("PATCH", "/api/users/profile"):
            let body = env["UI_TESTS_PROFILE_PATCH_BODY"] ?? Self.defaultProfilePatchResponseJSON
            let status = Int(env["UI_TESTS_PROFILE_PATCH_STATUS"] ?? "200") ?? 200
            finishWith(status: status, body: Data(body.utf8))

        case ("POST", "/api/homes/property-suggestions"):
            let body = env["UI_TESTS_HOMES_SUGGEST_BODY"] ?? Self.defaultPropertySuggestionsJSON
            let status = Int(env["UI_TESTS_HOMES_SUGGEST_STATUS"] ?? "200") ?? 200
            finishWith(status: status, body: Data(body.utf8))

        case ("POST", "/api/homes/check-address"):
            let body = env["UI_TESTS_HOMES_CHECK_BODY"] ?? Self.defaultCheckAddressJSON
            let status = Int(env["UI_TESTS_HOMES_CHECK_STATUS"] ?? "200") ?? 200
            finishWith(status: status, body: Data(body.utf8))

        case ("POST", "/api/homes"):
            let body = env["UI_TESTS_HOMES_CREATE_BODY"] ?? Self.defaultCreateHomeJSON
            let status = Int(env["UI_TESTS_HOMES_CREATE_STATUS"] ?? "200") ?? 200
            finishWith(status: status, body: Data(body.utf8))

        case let ("GET", path) where path.hasPrefix("/api/homes/") && path.hasSuffix("/public-profile"):
            let body = env["UI_TESTS_HOMES_PUBLIC_BODY"] ?? Self.defaultHomePublicProfileJSON
            finishWith(status: 200, body: Data(body.utf8))

        case let ("GET", path) where path.hasPrefix("/api/homes/") && !path.contains("my-homes"):
            let body = env["UI_TESTS_HOMES_DETAIL_BODY"] ?? Self.defaultHomeDetailJSON
            finishWith(status: 200, body: Data(body.utf8))

        case ("GET", "/api/homes/my-homes"):
            let body = env["UI_TESTS_HOMES_MYHOMES_BODY"] ?? Self.defaultMyHomesJSON
            finishWith(status: 200, body: Data(body.utf8))

        case ("GET", "/api/gigs"):
            let body = env["UI_TESTS_GIGS_LIST_BODY"] ?? Self.defaultGigsListJSON
            finishWith(status: 200, body: Data(body.utf8))

        case let ("GET", path)
            where path.hasPrefix("/api/gigs/")
            && !path.hasSuffix("/bids")
            && !path.contains("/in-bounds")
            && !path.contains("/nearby")
            && !path.contains("/browse")
            && !path.contains("/categories"):
            let body = env["UI_TESTS_GIG_DETAIL_BODY"] ?? Self.defaultGigDetailJSON
            finishWith(status: 200, body: Data(body.utf8))

        case let ("GET", path) where path.hasPrefix("/api/gigs/") && path.hasSuffix("/bids"):
            // Owner-only on real backend; the stub returns an empty
            // list so the detail screen renders the trust capsules
            // without a bids module under the UI test.
            finishWith(status: 200, body: Data("{\"bids\":[]}".utf8))

        case ("GET", "/api/identity-center"):
            let body = env["UI_TESTS_IDENTITY_CENTER_BODY"] ?? Self.defaultIdentityCenterJSON
            finishWith(status: 200, body: Data(body.utf8))

        case ("GET", "/api/privacy/blocks"):
            finishWith(status: 200, body: Data("{\"blocks\":[]}".utf8))

        default:
            // Unknown endpoint under test — surface a recognizable 599
            // so test failures point clearly at a missing stub.
            finishWith(
                status: 599,
                body: Data("{\"error\":\"UITestStubProtocol: unmocked \(method) \(path)\"}".utf8)
            )
        }
    }

    private func finishWith(status: Int, body: Data) {
        guard let url = request.url,
              let response = HTTPURLResponse(
                  url: url,
                  statusCode: status,
                  httpVersion: "HTTP/1.1",
                  headerFields: ["Content-Type": "application/json"]
              )
        else { return }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: body)
        client?.urlProtocolDidFinishLoading(self)
    }

    /// Minimal user profile that decodes cleanly into `ProfileResponse`.
    /// Tests that need different data should override
    /// `UI_TESTS_PROFILE_GET_BODY`.
    static let defaultProfileResponseJSON = """
    {"user":{
      "id":"u_test","email":"alice@example.com","username":"alice",
      "firstName":"Alice","middleName":null,"lastName":"Doe","name":"Alice Doe",
      "phoneNumber":"+15555550123","dateOfBirth":null,
      "address":null,"city":null,"state":null,"zipcode":null,
      "accountType":"personal","role":"user","verified":true,
      "residency":null,"avatar_url":null,"profile_picture_url":null,"profilePicture":null,
      "bio":"Hello world","tagline":null,"socialLinks":null,"skills":[],
      "followers_count":0,"average_rating":0,"gigs_posted":0,"gigs_completed":0,
      "profileVisibility":"public","createdAt":"2025-01-01T00:00:00Z","updatedAt":"2025-01-01T00:00:00Z"
    },"invite_progress":null}
    """

    /// Successful PATCH envelope shaped like `ProfileUpdateResponse`.
    static let defaultProfilePatchResponseJSON = """
    {"message":"ok","user":{
      "id":"u_test","email":"alice@example.com","username":"alice",
      "firstName":"Alice","middleName":null,"lastName":"Doe","name":"Alice Doe",
      "phoneNumber":"+15555550123","dateOfBirth":null,
      "address":null,"city":null,"state":null,"zipcode":null,
      "accountType":"personal","role":"user","verified":true,
      "residency":null,"avatar_url":null,"profile_picture_url":null,"profilePicture":null,
      "bio":"Hello world","tagline":null,"socialLinks":null,"skills":[],
      "followers_count":0,"average_rating":0,"gigs_posted":0,"gigs_completed":0,
      "profileVisibility":"public","createdAt":"2025-01-01T00:00:00Z","updatedAt":"2025-01-01T00:00:00Z"
    }}
    """

    /// ATTOM property suggestions envelope — matches `JSONValue` with one
    /// candidate. Tests can override via `UI_TESTS_HOMES_SUGGEST_BODY`.
    static let defaultPropertySuggestionsJSON = """
    {"results":[{"address":"412 Elm St","city":"Portland","state":"OR","zipCode":"97214"}]}
    """

    /// `CheckAddressResponse` — exists=false so the wizard's verdict row
    /// renders the "looks good" path.
    static let defaultCheckAddressJSON = """
    {"exists":false,"homeCount":0,"hasVerifiedMembers":false,"verdictStatus":null}
    """

    /// `CreateHomeResponse` envelope. The wizard's "View home" CTA uses
    /// the returned `home.id` to push the dashboard route.
    static let defaultCreateHomeJSON = """
    {"message":"ok","home":{
      "id":"home_test","name":"412 Elm St","address":"412 Elm St",
      "city":"Portland","state":"OR","zipcode":"97214",
      "home_type":null,"visibility":"public","description":null,
      "created_at":"2025-01-01T00:00:00Z","updated_at":"2025-01-01T00:00:00Z"
    },"requires_verification":false,"verification_type":null,"role":"owner"}
    """

    /// Minimal `MyHomesResponse` for tests that need to land on the
    /// MyHomes list before triggering the wizard.
    static let defaultMyHomesJSON = """
    {"homes":[],"message":"ok"}
    """

    /// Minimal `HomeDetailResponse` for the dashboard route.
    static let defaultHomeDetailJSON = """
    {"home":{
      "id":"home_test","name":"412 Elm St","address":"412 Elm St",
      "city":"Portland","state":"OR","zipcode":"97214",
      "home_type":null,"visibility":"public","description":null,
      "created_at":"2025-01-01T00:00:00Z","updated_at":"2025-01-01T00:00:00Z",
      "owner":null,"occupants":[],"location":null,
      "isOwner":true,"isPendingOwner":false,"pendingClaimId":null,
      "isOccupant":true,"owners":[],"can_delete_home":true
    }}
    """

    /// Minimal `HomePublicProfileResponse`.
    static let defaultHomePublicProfileJSON = """
    {"home":{
      "id":"home_test","name":"412 Elm St","address":"412 Elm St",
      "city":"Portland","state":"OR","zipcode":"97214",
      "home_type":null,"visibility":"public","description":null,
      "created_at":"2025-01-01T00:00:00Z",
      "hasVerifiedOwner":false,"verifiedOwner":null,
      "userMembershipStatus":"member","userResidencyClaim":null,
      "memberCount":1,"nearbyGigs":0
    }}
    """

    /// Single-row `GigsListResponse` for the Gigs feed + the matching
    /// detail row for the T2.6 ContentDetailShell capture.
    static let defaultGigsListJSON = """
    {"gigs":[{
      "id":"g_demo",
      "title":"Hang 3 shelves",
      "description":"Three IKEA Lack shelves on drywall — studs already located.",
      "price":60,
      "category":"handyman",
      "status":"open",
      "created_at":"2025-01-01T00:00:00Z",
      "user_id":"u_demo",
      "bid_count":4,
      "distance_miles":0.2,
      "pickup_address":"Rose Court, Unit 4B"
    }],"total":1}
    """

    static let defaultGigDetailJSON = """
    {"gig":{
      "id":"g_demo",
      "title":"Hang 3 shelves",
      "description":"Three IKEA Lack shelves on drywall — studs already located.",
      "price":60,
      "category":"handyman",
      "status":"open",
      "created_at":"2025-01-01T00:00:00Z",
      "user_id":"u_demo",
      "bid_count":4,
      "distance_miles":0.2,
      "pickup_address":"Rose Court, Unit 4B"
    }}
    """

    /// Identity Center overview — all four identities populated so the
    /// 14_IdentityCenter screenshot captures the loaded state with
    /// Profile-links toggles, blocked counts, and the Live chip on the
    /// Public profile card.
    static let defaultIdentityCenterJSON = """
    {
      "private_account": {
        "id": "u_demo", "email": "alice@example.com", "name": "Alice Doe",
        "verified": true
      },
      "local_profile": {
        "id": "lp_demo", "handle": "alice.d", "display_name": "Alice D.",
        "post_count": 47, "connection_count": 23, "verified": true
      },
      "audience_profile": {
        "id": "ap_demo", "handle": "aliceonline", "display_name": "Alice Online",
        "follower_count": 1247, "post_cadence": "weekly", "status": "live"
      },
      "bridges": {"show_persona_on_local": true, "show_local_on_persona": false},
      "homes": [{"id": "home_demo", "name": "412 Elm St"}],
      "business_profiles": [
        {"id": "biz_demo", "display_name": "Alice Masonry", "is_active": true}
      ],
      "persona_count": 1,
      "block_counts": {"personal": 2, "audience": 5}
    }
    """
}
#endif
