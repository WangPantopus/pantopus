//
//  PostGigV1ViewModelTests.swift
//  PantopusTests
//
//  A13.8 — covers the legacy gig composer's `submit()` now that it posts
//  to `POST /api/gigs` (`GigsEndpoints.create`): a valid form round-trips
//  to the backend-issued id, an invalid form short-circuits before the
//  network, and a server failure surfaces the error chrome with the form
//  preserved for retry.
//

import XCTest
@testable import Pantopus

@MainActor
final class PostGigV1ViewModelTests: XCTestCase {
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

    private func filledViewModel() -> PostGigV1ViewModel {
        PostGigV1ViewModel(
            api: makeAPI(),
            initialState: PostGigV1State(form: PostGigV1SampleData.filledForm),
            referenceNow: PostGigV1SampleData.referenceNow
        )
    }

    func testSubmitValidFormPostsAndReturnsBackendId() async {
        SequencedURLProtocol.sequence = [
            .status(201, body: """
            {"message":"Gig created successfully",
             "gig":{"id":"gig-from-server","title":"Help moving a sofa up 3 flights"}}
            """)
        ]
        let vm = filledViewModel()
        let id = await vm.submit()
        XCTAssertEqual(id, "gig-from-server")
        XCTAssertEqual(vm.state.postedGigId, "gig-from-server")
        XCTAssertFalse(vm.state.isSubmitting)
        XCTAssertTrue(vm.state.validationErrors.isEmpty)
    }

    func testSubmitInvalidFormSurfacesValidationAndSkipsNetwork() async {
        // Empty default form fails category/title/description/price/location.
        let vm = PostGigV1ViewModel(api: makeAPI())
        let id = await vm.submit()
        XCTAssertNil(id)
        XCTAssertFalse(vm.state.validationErrors.isEmpty)
        XCTAssertNil(vm.state.postedGigId)
        XCTAssertTrue(SequencedURLProtocol.capturedRequests.isEmpty, "Validation fails before any request")
    }

    func testSubmitServerErrorSurfacesErrorState() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"boom\"}")]
        let vm = filledViewModel()
        let id = await vm.submit()
        XCTAssertNil(id)
        guard case .error = vm.state.loadState else {
            return XCTFail("Expected error loadState, got \(vm.state.loadState)")
        }
        XCTAssertFalse(vm.state.isSubmitting)
        // Form survives so retry() can re-attempt without re-entry.
        XCTAssertEqual(vm.state.form.title, PostGigV1SampleData.filledForm.title)
    }
}
