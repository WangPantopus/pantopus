//
//  ProfessionalProfileMappingTests.swift
//  PantopusTests
//
//  P1-F — covers the live wiring of the Professional Profile editor:
//    - pure ProfessionalProfileDTO → content projection (overlapping fields),
//    - verification-status + category mapping,
//    - the null-profile (professional mode off) path,
//    - the live load() path via a stubbed APIClient.
//

import XCTest
@testable import Pantopus

@MainActor
final class ProfessionalProfileMappingTests: XCTestCase {
    private func dto(
        headline: String? = "Licensed General Handyman",
        bio: String? = "20 years of trade work.",
        categories: [String]? = ["handyman", "carpentry"],
        isPublic: Bool? = true,
        isActive: Bool? = false,
        status: String? = "verified"
    ) -> ProfessionalProfileDTO {
        ProfessionalProfileDTO(
            headline: headline,
            bio: bio,
            categories: categories,
            serviceArea: .init(city: "Elm Park", state: "NY"),
            pricingMeta: nil,
            isPublic: isPublic,
            isActive: isActive,
            verificationTier: 2,
            verificationStatus: status
        )
    }

    // MARK: - Projection

    func testMakeContentMapsOverlappingFields() {
        let content = ProfessionalProfileViewModel.makeContent(
            from: dto(),
            verification: nil,
            proName: "Maria K."
        )
        XCTAssertEqual(content.proName, "Maria K.")
        XCTAssertEqual(content.title.value, "Licensed General Handyman")
        XCTAssertEqual(content.skills.map(\.label), ["Handyman", "Carpentry"])
        XCTAssertEqual(content.company.locality, "Elm Park, NY")
        XCTAssertEqual(content.company.status, .verified)
        // Backend doesn't store these on profile/me.
        XCTAssertTrue(content.certifications.isEmpty)
        XCTAssertTrue(content.portfolio.isEmpty)
        // Visibility reflects the live flags.
        XCTAssertEqual(content.visibility.first { $0.id == "publicProfile" }?.isOn, true)
        XCTAssertEqual(content.visibility.first { $0.id == "activeForHire" }?.isOn, false)
        XCTAssertEqual(content.dirtyCount, 0, "Freshly loaded content is clean")
    }

    func testNullProfileProducesEmptyCleanContent() {
        let content = ProfessionalProfileViewModel.makeContent(
            from: nil,
            verification: nil,
            proName: ""
        )
        XCTAssertEqual(content.title.value, "")
        XCTAssertTrue(content.skills.isEmpty)
        XCTAssertEqual(content.company.status, .unverified)
        XCTAssertEqual(content.strength, 0)
        XCTAssertEqual(content.dirtyCount, 0)
    }

    func testVerificationStatusMapping() {
        XCTAssertEqual(ProfessionalProfileViewModel.verificationStatus("verified"), .verified)
        XCTAssertEqual(ProfessionalProfileViewModel.verificationStatus("pending"), .pending)
        XCTAssertEqual(ProfessionalProfileViewModel.verificationStatus(nil), .unverified)
        XCTAssertEqual(ProfessionalProfileViewModel.verificationStatus("rejected"), .unverified)
    }

    func testCategoryLabelHumanizes() {
        XCTAssertEqual(ProfessionalProfileViewModel.categoryLabel("pet_care"), "Pet Care")
        XCTAssertEqual(ProfessionalProfileViewModel.categoryLabel("handyman"), "Handyman")
    }

    func testStrengthHeuristicRewardsCompleteness() {
        let bare = ProfessionalProfileViewModel.strength(
            for: dto(headline: "", bio: "", categories: [], status: "unverified")
        )
        let full = ProfessionalProfileViewModel.strength(for: dto())
        XCTAssertEqual(bare, 40)
        XCTAssertGreaterThan(full, bare)
        XCTAssertLessThanOrEqual(full, 100)
    }

    // MARK: - Live load() path

    func testLiveLoadHydratesFromProfileMe() async {
        SequencedURLProtocol.reset()
        defer { SequencedURLProtocol.reset() }
        let session = SequencedURLProtocol.makeSession(routeResponses: [
            "/api/professional/profile/me": [
                .status(200, body: """
                {"profile":{"headline":"Licensed Handyman","bio":"Trades.",\
                "categories":["handyman","carpentry"],"is_public":true,"is_active":false,\
                "verification_status":"verified","service_area":{"city":"Elm Park","state":"NY"}}}
                """)
            ],
            "/api/professional/verification/status": [
                .status(200, body: """
                {"tier":2,"status":"verified","submitted_at":null,"completed_at":null}
                """)
            ]
        ])
        let vm = ProfessionalProfileViewModel(api: APIClient(session: session, retryPolicy: .none))
        await vm.load()
        guard case let .verified(content) = vm.state else {
            return XCTFail("Expected verified (clean) after load, got \(vm.state)")
        }
        XCTAssertEqual(content.title.value, "Licensed Handyman")
        XCTAssertEqual(content.skills.count, 2)
        XCTAssertEqual(content.company.status, .verified)
    }

    func testLiveLoadErrorSurfacesErrorState() async {
        SequencedURLProtocol.reset()
        defer { SequencedURLProtocol.reset() }
        let session = SequencedURLProtocol.makeSession(routeResponses: [
            "/api/professional/profile/me": [.status(500, body: "{\"error\":\"boom\"}")]
        ])
        let vm = ProfessionalProfileViewModel(api: APIClient(session: session, retryPolicy: .none))
        await vm.load()
        guard case .error = vm.state else {
            return XCTFail("Expected error, got \(vm.state)")
        }
    }
}
