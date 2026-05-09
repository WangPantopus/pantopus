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
///
/// The protocol is single-process: each XCUITest spawns the app fresh,
/// so there is no need for cross-test isolation.
final class UITestStubProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.path.hasPrefix("/api/") ?? false
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
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
}
#endif
