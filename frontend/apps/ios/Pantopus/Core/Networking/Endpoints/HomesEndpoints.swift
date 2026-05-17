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

    // MARK: - Maintenance (T6.3b / P10)

    /// `GET /api/homes/:id/maintenance` — route `backend/routes/home.js`
    /// (added in T6.3b / P10).
    public static func maintenance(
        homeId: String,
        status: String? = nil
    ) -> Endpoint {
        var query: [String: String] = [:]
        if let status { query["status"] = status }
        return Endpoint(
            method: .get,
            path: "/api/homes/\(homeId)/maintenance",
            query: query
        )
    }

    /// `POST /api/homes/:id/maintenance` — route `backend/routes/home.js`.
    public static func createMaintenance(
        homeId: String,
        request: CreateMaintenanceRequest
    ) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/homes/\(homeId)/maintenance",
            body: request
        )
    }

    /// `PUT /api/homes/:id/maintenance/:taskId` — route
    /// `backend/routes/home.js`.
    public static func updateMaintenance(
        homeId: String,
        taskId: String,
        request: UpdateMaintenanceRequest
    ) -> Endpoint {
        Endpoint(
            method: .put,
            path: "/api/homes/\(homeId)/maintenance/\(taskId)",
            body: request
        )
    }

    /// `DELETE /api/homes/:id/maintenance/:taskId` — route
    /// `backend/routes/home.js`.
    public static func deleteMaintenance(
        homeId: String,
        taskId: String
    ) -> Endpoint {
        Endpoint(
            method: .delete,
            path: "/api/homes/\(homeId)/maintenance/\(taskId)"
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
}
