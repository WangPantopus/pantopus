//
//  TransferOwnershipViewModel.swift
//  Pantopus
//
//  A13.4 — Backs the Transfer Ownership form. Holds the recipient
//  selection, the live transfer amount (1–60% slider), the typed
//  confirmation field, the bottom-sheet visibility, and the Face ID
//  authentication state machine. The current recipient projection is
//  sample-only, so the live app blocks the backend mutation until recipient
//  selection is backed by real owner data. Tests can opt into the transfer
//  executor seam to keep the post-auth state machine covered.
//

import Foundation
import LocalAuthentication
import Observation
import SwiftUI

/// Visibility states for the bottom confirmation sheet.
public enum ConfirmSheetPhase: Sendable, Equatable {
    case hidden
    case visible
    case authenticating
    case dismissing
}

@Observable
@MainActor
public final class TransferOwnershipViewModel {
    // MARK: - Inputs

    public let homeId: String
    public let homeContext: TransferOwnershipSampleData.HomeContext
    public let recipient: TransferOwnershipSampleData.RecipientSeed
    public let currentUser: TransferOwnershipSampleData.OwnerSeed
    public let coOwners: [TransferOwnershipSampleData.OwnerSeed]
    public let recipientIsBackendBacked: Bool
    public let presets: [Int] = TransferOwnershipSampleData.presets
    public let sliderRange: ClosedRange<Int> = TransferOwnershipSampleData.sliderRange
    public let confirmationPhrase: String = TransferOwnershipSampleData.confirmationPhrase

    // MARK: - Mutable state

    public var amount: Int
    public var confirmationField: FormFieldState
    public private(set) var sheetPhase: ConfirmSheetPhase = .hidden
    public private(set) var biometricErrorMessage: String?
    public var toast: ToastMessage?
    public private(set) var shouldDismiss = false

    // MARK: - Injected boundary

    /// Biometric evaluator. Default uses `LocalAuthentication`; tests can
    /// inject a deterministic stub that returns `.success` / `.failure`
    /// without prompting the user.
    public typealias BiometricEvaluator = @MainActor (_ reason: String) async -> Result<Void, any Error>
    private let biometricEvaluator: BiometricEvaluator

    /// Test seam for the backend round-trip. When `nil` (the default in
    /// the live app) `commitTransfer()` performs
    /// `POST /api/homes/:id/owners/transfer` through `api`; tests inject
    /// a deterministic stub so they never touch the network.
    public typealias TransferExecutor = @MainActor () async throws -> Void
    private let transferExecutor: TransferExecutor?
    private let api: APIClient

    // MARK: - Init

    init(
        homeId: String,
        amount: Int = TransferOwnershipSampleData.defaultAmount,
        api: APIClient = .shared,
        biometricEvaluator: BiometricEvaluator? = nil,
        transferExecutor: TransferExecutor? = nil,
        recipientIsBackendBacked: Bool = false
    ) {
        self.homeId = homeId
        self.api = api
        self.recipientIsBackendBacked = recipientIsBackendBacked
        homeContext = TransferOwnershipSampleData.homeContext(for: homeId)
        recipient = TransferOwnershipSampleData.mayaFortune
        currentUser = TransferOwnershipSampleData.currentUser
        coOwners = TransferOwnershipSampleData.coOwners
        self.amount = max(1, min(amount, currentUser.percent))
        confirmationField = FormFieldState(id: "confirmation", originalValue: "")
        self.biometricEvaluator = biometricEvaluator ?? Self.defaultBiometricEvaluator
        self.transferExecutor = transferExecutor
    }

    // MARK: - Computed projections

    public var maxAmount: Int {
        currentUser.percent
    }

    /// Whether the typed phrase matches the literal "TRANSFER" exactly.
    public var confirmationMatches: Bool {
        confirmationField.value == confirmationPhrase
    }

    /// Whether the sticky CTA is active.
    public var isReadyToCommit: Bool {
        amount > 0 && amount <= maxAmount && confirmationMatches && recipientIsBackendBacked
    }

    /// Whether the host should arm the dirty-close confirm. Any input
    /// touched flips this on.
    public var isDirty: Bool {
        amount != TransferOwnershipSampleData.defaultAmount
            || !confirmationField.value.isEmpty
    }

    /// Field state visual for the typed confirmation input.
    public var confirmationFieldState: PantopusFieldState {
        if confirmationField.value.isEmpty { return .default }
        return confirmationMatches ? .valid : .default
    }

    public var ctaLabel: String {
        if !recipientIsBackendBacked { return "Transfer ownership unavailable" }
        return "Transfer \(amount)% to \(recipient.name.split(separator: " ").first.map(String.init) ?? recipient.name)"
    }

    public var setupBlockMessage: String? {
        recipientIsBackendBacked ? nil : "Recipient selection is not connected to live owner data yet."
    }

    public var warningCopy: String {
        let firstName = recipient.name.split(separator: " ").first.map(String.init) ?? recipient.name
        return "\(coOwners.map(\.displayName).joined(separator: " and ")) " +
            "will be notified after this transfer. You cannot reclaim the \(amount)% without " +
            "\(firstName)'s signed transfer back."
    }

    public var biometryLabel: String {
        Self.biometryLabel
    }

    // MARK: - Diff projection

    public var beforeSegments: [SplitSegment] {
        let you = SplitSegment(
            id: currentUser.id,
            owner: currentUser.displayName,
            percent: currentUser.percent,
            color: currentUser.palette.color
        )
        let others = coOwners.map { owner in
            SplitSegment(
                id: owner.id,
                owner: owner.displayName,
                percent: owner.percent,
                color: owner.palette.color
            )
        }
        return [you] + others
    }

    public var afterSegments: [SplitSegment] {
        let recipientFirstName = recipient.name.split(separator: " ").first.map(String.init) ?? recipient.name
        let you = SplitSegment(
            id: currentUser.id,
            owner: currentUser.displayName,
            percent: max(0, currentUser.percent - amount),
            color: currentUser.palette.color,
            delta: -amount
        )
        let newcomer = SplitSegment(
            id: recipient.id,
            owner: recipientFirstName,
            percent: amount,
            color: TransferOwnershipSampleData.recipientPaletteStart,
            delta: amount,
            isNew: true
        )
        let others = coOwners.map { owner in
            SplitSegment(
                id: owner.id,
                owner: owner.displayName,
                percent: owner.percent,
                color: owner.palette.color
            )
        }
        return [you, newcomer] + others
    }

    public var confirmSheetParties: [ConfirmSheetParty] {
        [
            ConfirmSheetParty(
                id: currentUser.id,
                role: "From",
                name: "You · \(TransferOwnershipSampleData.senderFullName)",
                initials: currentUser.initials,
                avatarStart: currentUser.palette.gradientStart,
                avatarEnd: currentUser.palette.gradientEnd,
                fromPercent: currentUser.percent,
                toPercent: max(0, currentUser.percent - amount)
            ),
            ConfirmSheetParty(
                id: recipient.id,
                role: "To",
                name: recipient.name,
                initials: recipient.initials,
                avatarStart: TransferOwnershipSampleData.recipientPaletteStart,
                avatarEnd: TransferOwnershipSampleData.recipientPaletteEnd,
                fromPercent: 0,
                toPercent: amount,
                verified: recipient.verified
            )
        ]
    }

    /// Stable "HH:mm MMM d" stamp shown in the legal copy. Hardcoded for
    /// snapshot reproducibility.
    public var confirmationTimestamp: String {
        "14:23 May 26"
    }

    // MARK: - Mutations

    public func updateAmount(_ raw: Int) {
        amount = min(max(sliderRange.lowerBound, raw), maxAmount)
    }

    public func selectPreset(_ preset: Int) {
        updateAmount(preset)
    }

    public func updateConfirmation(_ value: String) {
        confirmationField.value = value
        confirmationField.touched = true
    }

    public func presentConfirmSheet() {
        if let setupBlockMessage {
            toast = ToastMessage(text: setupBlockMessage, kind: .error)
            return
        }
        guard isReadyToCommit else { return }
        sheetPhase = .visible
        biometricErrorMessage = nil
    }

    public func dismissConfirmSheet() {
        guard sheetPhase != .authenticating else { return }
        sheetPhase = .hidden
        biometricErrorMessage = nil
    }

    /// Authenticate with biometrics, then run the stubbed transfer
    /// endpoint. On success raises a toast and signals the host should
    /// dismiss. On biometric failure the sheet stays open and surfaces an
    /// inline error so the user can retry.
    public func authenticateAndCommit() async {
        guard sheetPhase == .visible, isReadyToCommit else { return }
        sheetPhase = .authenticating
        biometricErrorMessage = nil
        let reason = "Confirm transfer of \(amount)% to \(recipient.name)"
        let result = await biometricEvaluator(reason)
        switch result {
        case .success:
            await commitTransfer()
        case let .failure(error):
            sheetPhase = .visible
            biometricErrorMessage = (error as? LAError)
                .map { Self.message(for: $0) }
                ?? error.localizedDescription
        }
    }

    public func acknowledgeDismiss() {
        shouldDismiss = false
    }

    private func commitTransfer() async {
        if let setupBlockMessage {
            sheetPhase = .visible
            biometricErrorMessage = setupBlockMessage
            return
        }
        do {
            let successText: String
            if let transferExecutor {
                // Test seam — deterministic stub, no network.
                try await transferExecutor()
                successText = "Transferred \(amount)% to \(recipient.name)"
            } else {
                // Identify the buyer by their account id. `effective_date`
                // is omitted so the transfer takes effect immediately.
                let request = TransferOwnerRequest(buyerUserId: recipient.id)
                let response = try await api.request(
                    HomesEndpoints.transferOwner(homeId: homeId, request: request),
                    as: TransferOwnerResponse.self
                )
                successText = response.requiresQuorum
                    ? "Transfer sent for co-owner approval."
                    : "Transferred \(amount)% to \(recipient.name)"
            }
            sheetPhase = .dismissing
            toast = ToastMessage(text: successText, kind: .success)
            shouldDismiss = true
        } catch {
            sheetPhase = .visible
            biometricErrorMessage = (error as? APIError)?.errorDescription
                ?? "Couldn't complete the transfer. Try again."
        }
    }

    // MARK: - Defaults

    private static let defaultBiometricEvaluator: BiometricEvaluator = { reason in
        let context = LAContext()
        var error: NSError?
        let policy: LAPolicy = .deviceOwnerAuthentication // biometrics with passcode fallback
        guard context.canEvaluatePolicy(policy, error: &error) else {
            return .failure(error ?? LAError(.biometryNotAvailable))
        }
        return await withCheckedContinuation { continuation in
            context.evaluatePolicy(policy, localizedReason: reason) { success, evalError in
                if success {
                    continuation.resume(returning: .success(()))
                } else {
                    continuation.resume(returning: .failure(evalError ?? LAError(.authenticationFailed)))
                }
            }
        }
    }

    private static var biometryLabel: String {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch context.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        case .none: return "Passcode"
        @unknown default: return "Face ID"
        }
    }

    private static func message(for error: LAError) -> String {
        switch error.code {
        case .userCancel, .systemCancel, .appCancel:
            "Authentication was cancelled."
        case .authenticationFailed:
            "Authentication failed. Try again."
        case .passcodeNotSet:
            "Set a device passcode to confirm transfers."
        case .biometryNotAvailable, .biometryNotEnrolled, .biometryLockout:
            "Biometric authentication isn't available right now."
        default:
            error.localizedDescription
        }
    }
}
