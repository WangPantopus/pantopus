//
//  ResetPasswordViewModelTests.swift
//  PantopusTests
//
//  T6.1c P5 — Covers token parsing (carried as an init arg from the
//  deep-link route), submit gating (passwords match + strength), the
//  AuthManager.resetPassword call with the right body, and error
//  rollback.
//

import XCTest
@testable import Pantopus

@MainActor
final class ResetPasswordViewModelTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    override func tearDown() {
        SequencedURLProtocol.reset()
        super.tearDown()
    }

    private func makeAuth() -> AuthManager {
        let client = APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(),
            retryPolicy: .none
        )
        return AuthManager(store: InMemorySecureStore(), apiClient: client)
    }

    // MARK: - Token parsing from deep-link

    func test_token_is_captured_from_init() {
        let vm = ResetPasswordViewModel(token: "deep-link-hashed-token")
        XCTAssertEqual(vm.token, "deep-link-hashed-token")
    }

    // MARK: - Gating

    func test_canSubmit_requires_strength_and_match_and_token() {
        let vm = ResetPasswordViewModel(token: "tok")
        XCTAssertFalse(vm.canSubmit)
        vm.password = "weak"
        vm.confirmPassword = "weak"
        XCTAssertFalse(vm.canSubmit, "weak password should fail strength check")
        vm.password = "strongpass1"
        vm.confirmPassword = "different1"
        XCTAssertFalse(vm.canSubmit, "mismatched confirm should disable submit")
        vm.confirmPassword = "strongpass1"
        XCTAssertTrue(vm.canSubmit)
    }

    func test_canSubmit_false_with_empty_token() {
        let vm = ResetPasswordViewModel(token: "")
        vm.password = "strongpass1"
        vm.confirmPassword = "strongpass1"
        XCTAssertFalse(vm.canSubmit, "missing token blocks submission")
    }

    // MARK: - Submit success

    func test_submit_calls_resetPassword_with_the_token_and_password() async {
        SequencedURLProtocol.routeResponses["/api/users/reset-password"] = [
            .status(200, body: "{\"message\":\"Password reset successful.\"}")
        ]
        let auth = makeAuth()
        let vm = ResetPasswordViewModel(token: "deep-tok")
        vm.password = "strongpass1"
        vm.confirmPassword = "strongpass1"

        await vm.submit(using: auth)

        XCTAssertEqual(vm.phase, .reset)
        XCTAssertNil(vm.errorMessage)
        XCTAssertFalse(vm.isLoading)

        let body = SequencedURLProtocol.capturedRequests.last?.httpBodyData()
            .flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
        XCTAssertEqual(body?["token"] as? String, "deep-tok")
        XCTAssertEqual(body?["newPassword"] as? String, "strongpass1")
    }

    // MARK: - Error rollback

    func test_submit_invalid_token_rolls_back_loading_state() async {
        SequencedURLProtocol.routeResponses["/api/users/reset-password"] = [
            .status(400, body: "{\"error\":\"Invalid or expired reset token\"}")
        ]
        let auth = makeAuth()
        let vm = ResetPasswordViewModel(token: "stale-tok")
        vm.password = "strongpass1"
        vm.confirmPassword = "strongpass1"

        await vm.submit(using: auth)

        if case let .serverError(message) = vm.errorMessage {
            XCTAssertEqual(message, "Invalid or expired reset token")
        } else {
            XCTFail("Expected .serverError, got \(String(describing: vm.errorMessage))")
        }
        XCTAssertEqual(vm.phase, .form, "phase stays on form when submit fails")
        XCTAssertFalse(vm.isLoading)
    }

    func test_submit_blocked_when_invalid_does_not_hit_network() async {
        let auth = makeAuth()
        let vm = ResetPasswordViewModel(token: "tok")
        // password missing — gate blocks submission
        await vm.submit(using: auth)
        XCTAssertEqual(SequencedURLProtocol.capturedRequests.count, 0)
    }
}

private extension URLRequest {
    func httpBodyData() -> Data? {
        if let direct = httpBody { return direct }
        guard let stream = httpBodyStream else { return nil }
        stream.open()
        defer { stream.close() }

        var data = Data()
        let bufferSize = 4096
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }

        while stream.hasBytesAvailable {
            let read = stream.read(buffer, maxLength: bufferSize)
            if read <= 0 { break }
            data.append(buffer, count: read)
        }
        return data
    }
}
