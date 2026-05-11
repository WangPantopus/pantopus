//
//  ClaimOwnershipWizardViewModel.swift
//  Pantopus
//
//  3-step claim-ownership wizard. Backend flow:
//   1. POST /api/homes/:id/ownership-claims  (claim_type=owner, method=doc_upload)
//   2. For each evidence file:
//        a. POST /api/files/upload (multipart) → file URL
//        b. POST /api/homes/:id/ownership-claims/:claimId/evidence with storage_ref
//   3. On all-success: advance to .success
//   4. On any failure: stay on .upload, mark the failing slot, preserve files
//
//  Backend reality vs P20 spec — flagged in the PR description:
//  - submitClaimSchema does NOT accept a `note` field. The textarea
//    value is sent as `metadata.note` on the FIRST evidence upload.
//  - The evidence endpoint takes JSON `storage_ref`, not multipart.
//    Real bytes go through `/api/files/upload` first.
//

import Foundation
import Logging
import Observation

/// Outbound events the wizard view must react to.
public enum ClaimOwnershipOutboundEvent: Sendable, Equatable {
    case dismiss
    case openClaimsList
}

/// ViewModel backing `ClaimOwnershipWizardView`.
@Observable
@MainActor
final class ClaimOwnershipWizardViewModel: WizardModel {
    // MARK: - Published state

    private(set) var currentStep: ClaimOwnershipStep = .start
    var slots: [ClaimEvidenceSlot: ClaimSlotUiState] = [:]
    var note: String = ""
    private(set) var isSubmitting: Bool = false
    private(set) var submitError: String?
    var pendingEvent: ClaimOwnershipOutboundEvent?

    // MARK: - Init

    private let homeId: String
    private let api: APIClient
    private let uploader: MultipartUploader
    private let logger = Logger(label: "app.pantopus.ios.ClaimOwnershipWizard")

    init(
        homeId: String,
        api: APIClient = .shared,
        uploader: MultipartUploader = .shared
    ) {
        self.homeId = homeId
        self.api = api
        self.uploader = uploader
        for slot in ClaimEvidenceSlot.allCases {
            slots[slot] = .empty
        }
    }

    // MARK: - WizardModel

    var chrome: WizardChrome {
        switch currentStep {
        case .start:
            return WizardChrome(
                title: "Claim ownership",
                progressLabel: .stepOf(current: 1, total: 3),
                progressFraction: 1.0 / 3.0,
                leading: .close,
                primaryCTALabel: "Start claim",
                primaryCTAEnabled: true,
                secondaryCTA: nil,
                isSubmitting: false,
                dirty: false,
                showsProgressBar: true
            )
        case .upload:
            return WizardChrome(
                title: "Claim ownership",
                progressLabel: .stepOf(current: 2, total: 3),
                progressFraction: 2.0 / 3.0,
                leading: .back,
                primaryCTALabel: "Submit claim",
                primaryCTAEnabled: bothSlotsHaveFiles && !isSubmitting,
                secondaryCTA: nil,
                isSubmitting: isSubmitting,
                dirty: anySlotHasFile || !note.isEmpty,
                showsProgressBar: true
            )
        case .success:
            return WizardChrome(
                title: "Claim ownership",
                progressLabel: .hidden,
                progressFraction: nil,
                leading: .close,
                primaryCTALabel: "View status",
                primaryCTAEnabled: true,
                secondaryCTA: WizardSecondaryCTA(label: "Back to home", identifier: "claimOwnership_backToHome"),
                isSubmitting: false,
                dirty: false,
                showsProgressBar: false
            )
        }
    }

    func leadingTapped() {
        switch currentStep {
        case .start:
            pendingEvent = .dismiss
        case .upload:
            currentStep = .start
        case .success:
            pendingEvent = .dismiss
        }
    }

    func discardConfirmed() {
        pendingEvent = .dismiss
    }

    func primaryTapped() {
        switch currentStep {
        case .start:
            currentStep = .upload
        case .upload:
            Task { await submit() }
        case .success:
            pendingEvent = .openClaimsList
        }
    }

    func secondaryTapped() {
        // Only fires on success — "Back to home".
        pendingEvent = .dismiss
    }

    // MARK: - Slot management

    func picked(_ slot: ClaimEvidenceSlot, file: ClaimPickedFile) {
        slots[slot] = .picked(file: file)
        submitError = nil
    }

    func remove(_ slot: ClaimEvidenceSlot) {
        slots[slot] = .empty
    }

    var bothSlotsHaveFiles: Bool {
        slots.values.allSatisfy { $0.hasFile }
    }

    var anySlotHasFile: Bool {
        slots.values.contains { $0.hasFile }
    }

    // MARK: - Submit

    func submit() async {
        guard bothSlotsHaveFiles, !isSubmitting else { return }
        if !NetworkMonitor.shared.isOnline {
            submitError = "You're offline. Try again when you're back online."
            return
        }
        isSubmitting = true
        submitError = nil
        defer { isSubmitting = false }

        // Step 1: create the claim.
        let claimResponse: SubmitClaimResponse
        do {
            claimResponse = try await api.request(
                HomesEndpoints.submitClaim(
                    homeId: homeId,
                    request: SubmitClaimRequest(method: "doc_upload")
                )
            )
        } catch {
            submitError = "Couldn't submit. Retry."
            logger.warning("Claim submit failed: \(error)")
            return
        }
        guard let claimId = claimResponse.claim.id else {
            // Opaque-handshake path can return nil claim id when a
            // duplicate exists. Surface a friendly message rather than
            // failing silently.
            submitError = "We're already working on a claim for this home."
            return
        }

        // Step 2: upload each slot's file then register evidence.
        for (index, slot) in ClaimEvidenceSlot.allCases.enumerated() {
            guard let file = slots[slot]?.pickedFile else { continue }
            slots[slot] = .uploading(file: file, fraction: 0.1)
            let metadata: [String: String]? =
                index == 0 && !note.trimmingCharacters(in: .whitespaces).isEmpty
                    ? ["note": note] : nil
            do {
                slots[slot] = .uploading(file: file, fraction: 0.4)
                let upload = try await uploader.uploadFile(
                    MultipartFile(
                        fieldName: "file",
                        filename: file.filename,
                        mimeType: file.mimeType,
                        data: file.data
                    ),
                    formFields: ["file_type": "claim_evidence", "visibility": "private"]
                )
                slots[slot] = .uploading(file: file, fraction: 0.8)
                _ = try await api.request(
                    HomesEndpoints.uploadEvidence(
                        homeId: homeId,
                        claimId: claimId,
                        request: UploadEvidenceRequest(
                            evidenceType: slot.backendType,
                            storageRef: upload.file.url,
                            metadata: metadata
                        )
                    )
                ) as UploadEvidenceResponse
                slots[slot] = .uploaded(file: file, fileURL: upload.file.url)
            } catch {
                logger.warning("Evidence upload failed for slot \(slot.rawValue): \(error)")
                slots[slot] = .failed(file: file, message: "Upload failed")
                submitError = "Couldn't submit. Retry."
                return
            }
        }

        // All uploads succeeded — advance to success.
        currentStep = .success
    }

    func acknowledgePendingEvent() {
        pendingEvent = nil
    }
}
