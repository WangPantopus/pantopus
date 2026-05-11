//
//  DisambiguateMailFormViewModelTests.swift
//  PantopusTests
//
//  Selection gating, alias-length validation, the happy path, and the
//  error path for the Disambiguate form.
//

import XCTest
@testable import Pantopus

@MainActor
final class DisambiguateMailFormViewModelTests: XCTestCase {
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

    private func makeVM() -> DisambiguateMailFormViewModel {
        DisambiguateMailFormViewModel(
            mailId: "mail-1",
            ocrRecipient: "MS. ALEX RIVERA\n140 MAIN ST",
            confidence: 0.85,
            envelopeImageURL: nil,
            api: makeAPI()
        )
    }

    func testInitialStateBlocksSubmit() {
        let vm = makeVM()
        XCTAssertNil(vm.selectedChoice)
        XCTAssertFalse(vm.canSubmit)
    }

    func testSelectingChoiceEnablesSubmit() {
        let vm = makeVM()
        vm.select(.home)
        XCTAssertEqual(vm.selectedChoice, .home)
        XCTAssertTrue(vm.canSubmit)
    }

    func testAliasOver255CharsBlocksSubmit() {
        let vm = makeVM()
        vm.select(.personal)
        vm.aliasNotes = String(repeating: "x", count: 256)
        XCTAssertNotNil(vm.aliasError)
        XCTAssertFalse(vm.canSubmit)
    }

    func testSubmitHappyPathSendsDrawerAndAlias() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"message\":\"Routing resolved\",\"drawer\":\"home\"}")
        ]
        let vm = makeVM()
        vm.select(.home)
        vm.aliasNotes = "Mom"
        let ok = await vm.submit()
        XCTAssertTrue(ok)
        XCTAssertEqual(vm.toast?.kind, .success)
        // Verify the captured request body.
        let body = SequencedURLProtocol.capturedRequests.last?.httpBodyData() ?? Data()
        let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any]
        XCTAssertEqual(json?["drawer"] as? String, "home")
        XCTAssertEqual(json?["mailId"] as? String, "mail-1")
        XCTAssertEqual(json?["addAlias"] as? Bool, true)
        XCTAssertEqual(json?["aliasString"] as? String, "Mom")
    }

    func testSubmitWithEmptyAliasOmitsAliasFields() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"message\":\"Routing resolved\",\"drawer\":\"personal\"}")
        ]
        let vm = makeVM()
        vm.select(.personal)
        let ok = await vm.submit()
        XCTAssertTrue(ok)
        let body = SequencedURLProtocol.capturedRequests.last?.httpBodyData() ?? Data()
        let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any]
        XCTAssertNil(json?["addAlias"])
        XCTAssertNil(json?["aliasString"])
    }

    func testSubmitFailureSurfacesErrorToast() async {
        SequencedURLProtocol.sequence = [
            .status(404, body: "{\"error\":\"Mail not found\"}")
        ]
        let vm = makeVM()
        vm.select(.home)
        let ok = await vm.submit()
        XCTAssertFalse(ok)
        XCTAssertEqual(vm.toast?.kind, .error)
    }
}

private extension URLRequest {
    /// Pull the body bytes regardless of whether `httpBody` or
    /// `httpBodyStream` was set by URLSession.
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
