//
//  HomesEndpoints.swift
//  Pantopus
//

import Foundation

/// Endpoint builders for `backend/routes/home.js`.
public enum HomesEndpoints {
    /// `GET /api/homes/my-homes` — route `backend/routes/home.js:1464`.
    public static func myHomes() -> Endpoint {
        Endpoint(method: .get, path: "/api/homes/my-homes")
    }

    /// `GET /api/homes/:id` — route `backend/routes/home.js:2891`.
    public static func detail(homeId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/homes/\(homeId)")
    }

    /// `GET /api/homes/:id/public-profile` — route `backend/routes/home.js:2439`.
    public static func publicProfile(homeId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/homes/\(homeId)/public-profile")
    }

    /// `POST /api/homes` — route `backend/routes/home.js:677`.
    public static func create(_ request: CreateHomeRequest) -> Endpoint {
        Endpoint(method: .post, path: "/api/homes", body: request)
    }

    /// `POST /api/homes/property-suggestions` — route `backend/routes/home.js:540`.
    public static func propertySuggestions(_ request: PropertySuggestionsRequest) -> Endpoint {
        Endpoint(method: .post, path: "/api/homes/property-suggestions", body: request)
    }

    /// `POST /api/homes/check-address` — route `backend/routes/home.js:555`.
    public static func checkAddress(_ request: CheckAddressRequest) -> Endpoint {
        Endpoint(method: .post, path: "/api/homes/check-address", body: request)
    }

    /// `POST /api/homes/:id/owners/invite` — route
    /// `backend/routes/homeOwnership.js:1376`.
    public static func inviteOwner(homeId: String, request: InviteOwnerRequest) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/homes/\(homeId)/owners/invite",
            body: request
        )
    }

    /// `POST /api/homes/:id/ownership-claims` — route
    /// `backend/routes/homeOwnership.js:251`.
    public static func submitClaim(homeId: String, request: SubmitClaimRequest) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/homes/\(homeId)/ownership-claims",
            body: request
        )
    }

    /// `POST /api/homes/:id/ownership-claims/:claimId/evidence` — route
    /// `backend/routes/homeOwnership.js:886`.
    public static func uploadEvidence(
        homeId: String,
        claimId: String,
        request: UploadEvidenceRequest
    ) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/homes/\(homeId)/ownership-claims/\(claimId)/evidence",
            body: request
        )
    }

    /// `GET /api/homes/my-ownership-claims` — route
    /// `backend/routes/homeOwnership.js:217`.
    public static func myOwnershipClaims() -> Endpoint {
        Endpoint(method: .get, path: "/api/homes/my-ownership-claims")
    }

    /// `GET /api/homes/:id/bills` — route `backend/routes/home.js:4506`.
    public static func bills(homeId: String, status: String? = nil) -> Endpoint {
        var query: [String: String] = [:]
        if let status { query["status"] = status }
        return Endpoint(
            method: .get,
            path: "/api/homes/\(homeId)/bills",
            query: query
        )
    }

    /// `POST /api/homes/:id/bills` — route `backend/routes/home.js:4539`.
    public static func createBill(homeId: String, request: CreateBillRequest) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/homes/\(homeId)/bills",
            body: request
        )
    }

    /// `PUT /api/homes/:id/bills/:billId` — route `backend/routes/home.js:4585`.
    public static func updateBill(
        homeId: String,
        billId: String,
        request: UpdateBillRequest
    ) -> Endpoint {
        Endpoint(
            method: .put,
            path: "/api/homes/\(homeId)/bills/\(billId)",
            body: request
        )
    }

    /// `GET /api/homes/:id/bills/:billId/splits` — route
    /// `backend/routes/home.js:4627`. Backend has no POST/PATCH/DELETE
    /// for splits; the detail view treats them as read-only until a
    /// follow-up PR ships the write side.
    public static func billSplits(homeId: String, billId: String) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/homes/\(homeId)/bills/\(billId)/splits"
        )
    }

    // MARK: - Pets (T5.2.1)

    /// `GET /api/homes/:id/pets` — route `backend/routes/home.js:6789`.
    public static func listPets(homeId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/homes/\(homeId)/pets")
    }

    /// `POST /api/homes/:id/pets` — route `backend/routes/home.js:6826`.
    public static func createPet(homeId: String, request: CreatePetRequest) -> Endpoint {
        Endpoint(method: .post, path: "/api/homes/\(homeId)/pets", body: request)
    }

    /// `PUT /api/homes/:id/pets/:petId` — route `backend/routes/home.js:6880`.
    public static func updatePet(
        homeId: String,
        petId: String,
        request: UpdatePetRequest
    ) -> Endpoint {
        Endpoint(method: .put, path: "/api/homes/\(homeId)/pets/\(petId)", body: request)
    }

    /// `DELETE /api/homes/:id/pets/:petId` — route `backend/routes/home.js:6926`.
    public static func deletePet(homeId: String, petId: String) -> Endpoint {
        Endpoint(method: .delete, path: "/api/homes/\(homeId)/pets/\(petId)")
    }

    // MARK: - Polls (T6.3e / P13)

    /// `GET /api/homes/:id/polls` — route `backend/routes/home.js:6984`.
    /// Response is enriched server-side with `vote_count`, `option_counts`
    /// (per-option breakdown keyed by `PollOptionDTO.id`), and `my_vote`.
    public static func listPolls(homeId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/homes/\(homeId)/polls")
    }

    /// `POST /api/homes/:id/polls` — route `backend/routes/home.js:7058`.
    public static func createPoll(homeId: String, request: CreatePollRequest) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/homes/\(homeId)/polls",
            body: request
        )
    }

    /// `POST /api/homes/:id/polls/:pollId/vote` — route
    /// `backend/routes/home.js:7100`. Upserts the viewer's vote (changing
    /// a vote is a re-call with new `selectedOptions`).
    public static func castPollVote(
        homeId: String,
        pollId: String,
        request: CastVoteRequest
    ) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/homes/\(homeId)/polls/\(pollId)/vote",
            body: request
        )
    }

    /// `PUT /api/homes/:id/polls/:pollId` — route `backend/routes/home.js:7159`.
    /// Used to close a poll (`status: "closed"`) or edit metadata.
    public static func updatePoll(
        homeId: String,
        pollId: String,
        request: UpdatePollRequest
    ) -> Endpoint {
        Endpoint(
            method: .put,
            path: "/api/homes/\(homeId)/polls/\(pollId)",
            body: request
        )
    }

    // MARK: - Members (T6.3a / P9)

    /// `GET /api/homes/:id/occupants` — route `backend/routes/home.js:3705`.
    /// Returns `{ occupants, pendingInvites }`; the Members screen
    /// buckets client-side into the Members / Guests / Pending tabs.
    public static func listOccupants(homeId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/homes/\(homeId)/occupants")
    }

    /// `POST /api/homes/:id/invite` — route `backend/routes/home.js:5662`.
    /// Body shape: `InviteMemberRequest`.
    public static func inviteMember(
        homeId: String,
        request: InviteMemberRequest
    ) -> Endpoint {
        Endpoint(method: .post, path: "/api/homes/\(homeId)/invite", body: request)
    }

    /// `DELETE /api/homes/:id/members/:userId` — route
    /// `backend/routes/homeIam.js:512`. Removes the membership; if the
    /// caller is the target it acts as a self-leave.
    public static func removeMember(homeId: String, userId: String) -> Endpoint {
        Endpoint(method: .delete, path: "/api/homes/\(homeId)/members/\(userId)")
    }
}
