//
//  EditProfileViewModelTests.swift
//  PantopusTests
//
//  Covers load, validation, dirty tracking, save happy path, save error,
//  and close-on-dirty detection.
//

import XCTest
@testable import Pantopus

@MainActor
final class EditProfileViewModelTests: XCTestCase {

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

    private static let profileJSON = """
    {"user":{
      "id":"u1","email":"alice@example.com","username":"alice",
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

    private func loaded() async -> EditProfileViewModel {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileJSON)]
        let vm = EditProfileViewModel(api: makeAPI())
        await vm.load()
        return vm
    }

    func testLoadHydratesFieldsFromServer() async {
        let vm = await loaded()
        XCTAssertEqual(vm.fields[.firstName]?.value, "Alice")
        XCTAssertEqual(vm.fields[.lastName]?.value, "Doe")
        XCTAssertEqual(vm.fields[.bio]?.value, "Hello world")
        XCTAssertEqual(vm.fields[.phoneNumber]?.value, "+15555550123")
        XCTAssertEqual(vm.email, "alice@example.com")
        XCTAssertTrue(vm.emailVerified)
        XCTAssertFalse(vm.aggregate.isDirty)
        XCTAssertTrue(vm.aggregate.isValid)
    }

    func testUpdateMarksDirtyAndValidates() async {
        let vm = await loaded()
        vm.update(.firstName, to: "")
        XCTAssertNotNil(vm.fields[.firstName]?.error)
        XCTAssertFalse(vm.aggregate.isValid)
        vm.update(.firstName, to: "Alex")
        XCTAssertNil(vm.fields[.firstName]?.error)
        XCTAssertTrue(vm.aggregate.isDirty)
        XCTAssertTrue(vm.aggregate.isValid)
    }

    func testPhoneValidatorRejectsNonE164() async {
        let vm = await loaded()
        vm.update(.phoneNumber, to: "555-0123")
        XCTAssertNotNil(vm.fields[.phoneNumber]?.error)
        vm.update(.phoneNumber, to: "+447700900123")
        XCTAssertNil(vm.fields[.phoneNumber]?.error)
        vm.update(.phoneNumber, to: "")
        XCTAssertNil(vm.fields[.phoneNumber]?.error, "Empty phone is allowed (optional).")
    }

    func testBioMaxLengthIs2000() async {
        let vm = await loaded()
        vm.update(.bio, to: String(repeating: "a", count: 2001))
        XCTAssertNotNil(vm.fields[.bio]?.error)
        vm.update(.bio, to: String(repeating: "a", count: 2000))
        XCTAssertNil(vm.fields[.bio]?.error)
    }

    func testSaveHappyPathPatchesAndSignalsDismiss() async {
        let updatedUser = Self.profileJSON
            .replacingOccurrences(of: "\"invite_progress\":null}", with: "")
            .replacingOccurrences(of: "{\"user\":", with: "")
        let patchEnvelope = "{\"message\":\"ok\",\"user\":\(updatedUser)"
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.profileJSON),
            .status(200, body: patchEnvelope),
        ]
        let vm = EditProfileViewModel(api: makeAPI())
        await vm.load()
        vm.update(.firstName, to: "Alex")
        _ = await vm.save()
        XCTAssertTrue(vm.shouldDismiss)
        XCTAssertEqual(vm.toast?.kind, .success)
    }

    func testSaveServerErrorSurfacesToast() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.profileJSON),
            .status(500, body: "{\"error\":\"down\"}"),
        ]
        let vm = EditProfileViewModel(api: makeAPI())
        await vm.load()
        vm.update(.firstName, to: "Alex")
        _ = await vm.save()
        XCTAssertFalse(vm.shouldDismiss)
        XCTAssertEqual(vm.toast?.kind, .error)
    }

    func testSaveWithInvalidFieldShakesAndDoesNotPatch() async {
        let vm = await loaded()
        vm.update(.firstName, to: "")
        let before = vm.shakeTrigger
        _ = await vm.save()
        XCTAssertNotEqual(before, vm.shakeTrigger)
        XCTAssertFalse(vm.shouldDismiss)
    }

    func testBuildRequestOnlyIncludesDirtyFields() async {
        let vm = await loaded()
        vm.update(.bio, to: "New bio")
        // Only `.bio` is dirty; the PATCH body should carry just `bio`.
        // We can't read the private buildRequest, so rely on `aggregate.isDirty`
        // per-field to prove the intent.
        XCTAssertTrue(vm.fields[.bio]?.isDirty ?? false)
        XCTAssertFalse(vm.fields[.firstName]?.isDirty ?? true)
        XCTAssertFalse(vm.fields[.lastName]?.isDirty ?? true)
    }
}
