//
//  ClaimUploadStep.swift
//  Pantopus
//
//  A12.4 — Claim ownership · Evidence (wizard step 2). Home chip + headline,
//  two evidence `UploadSlot`s with per-file address-match confirmations, an
//  optional `ClaimStatement`, and the encryption footer. PDF support is
//  TODO(picker); the system Photos picker is wired and the accept hint
//  advertises JPG/PNG only.
//

import PhotosUI
import SwiftUI

/// Copy shared with the Android screen — keep both platforms word-for-word.
enum ClaimUploadCopy {
    static let statementPlaceholder =
        "Add a short statement to help the reviewer (e.g. how long you've owned, anyone else on title)…"
    static let encryptionFooter =
        "Encrypted in transit. Visible only to the reviewer assigned to your claim."
}

/// One slot's display descriptor, assembled from the view model (or from
/// sample fixtures in snapshot tests).
struct ClaimUploadSlotModel: Identifiable, Equatable {
    let id: String
    let label: String
    let required: Bool
    let hint: String
    let state: UploadSlotState
}

// MARK: - Pure content (snapshot-testable)

/// The Evidence step body as a pure function of its state. `ClaimUploadStep`
/// builds this from the view model; snapshot tests render it from fixtures.
struct ClaimUploadStepContent: View {
    let homeLabel: String
    let slots: [ClaimUploadSlotModel]
    @Binding var statement: String
    var submitError: String?
    var onPick: (String) -> Void = { _ in }
    var onRemove: (String) -> Void = { _ in }

    private var attachedCount: Int {
        slots.filter(\.state.isAttached).count
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            ClaimHomeChip(label: homeLabel)

            HeadlineBlock(
                "Upload your evidence",
                subtitle: "Two documents help us verify you own \(homeLabel). " +
                    "We auto-check the address against your account."
            )

            VStack(alignment: .leading, spacing: Spacing.s3) {
                Text("Documents · \(attachedCount) of \(slots.count) attached")
                    .pantopusTextStyle(.overline)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                ForEach(slots) { slot in
                    UploadSlot(
                        id: slot.id,
                        label: slot.label,
                        required: slot.required,
                        hint: slot.hint,
                        state: slot.state,
                        onPick: { onPick(slot.id) },
                        onRemove: { onRemove(slot.id) }
                    )
                }
            }

            ClaimStatement(text: $statement, placeholder: ClaimUploadCopy.statementPlaceholder)

            if let submitError {
                ClaimUploadErrorBanner(message: submitError)
            }

            EncryptionFooter()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - View-model-bound step

struct ClaimUploadStep: View {
    @Bindable var viewModel: ClaimOwnershipWizardViewModel
    @State private var photosPickerSlot: ClaimEvidenceSlot?
    @State private var photosPickerSelection: PhotosPickerItem?

    var body: some View {
        ClaimUploadStepContent(
            homeLabel: viewModel.startContent.homeLabel,
            slots: ClaimEvidenceSlot.allCases.map(slotModel(for:)),
            statement: $viewModel.note,
            submitError: viewModel.submitError,
            onPick: { id in
                if let slot = ClaimEvidenceSlot(rawValue: id) { photosPickerSlot = slot }
            },
            onRemove: { id in
                if let slot = ClaimEvidenceSlot(rawValue: id) { viewModel.remove(slot) }
            }
        )
        // Driving the sheet directly off `photosPickerSlot` (rather than an
        // intermediate onAppear hop) keeps the picker reachable after a
        // remove + re-tap of the same slot.
        .photosPicker(
            isPresented: Binding(
                get: { photosPickerSlot != nil },
                set: { if !$0 { photosPickerSlot = nil } }
            ),
            selection: $photosPickerSelection,
            matching: .images
        )
        .onChange(of: photosPickerSelection) { _, newItem in
            handlePicked(newItem)
        }
    }

    private func slotModel(for slot: ClaimEvidenceSlot) -> ClaimUploadSlotModel {
        ClaimUploadSlotModel(
            id: slot.rawValue,
            label: slot.title,
            required: true,
            hint: slot.acceptHint,
            state: viewState(for: slot)
        )
    }

    private func viewState(for slot: ClaimEvidenceSlot) -> UploadSlotState {
        switch viewModel.slots[slot] ?? .empty {
        case .empty:
            return .empty
        case let .uploading(file, fraction):
            return .uploading(file: displayFile(file), progress: fraction)
        case .picked, .uploaded, .failed:
            guard let file = viewModel.slots[slot]?.pickedFile else { return .empty }
            let verdict = viewModel.addressMatches[slot]
                ?? ClaimOwnershipSampleData.addressMatch(
                    forFilename: file.filename,
                    homeLabel: viewModel.startContent.homeLabel
                )
            switch verdict {
            case let .matches(detail):
                return .done(file: displayFile(file), detail: detail)
            case let .differs(detail):
                return .warn(file: displayFile(file), detail: detail)
            }
        }
    }

    private func displayFile(_ file: ClaimPickedFile) -> UploadSlotFile {
        let isPDF = file.mimeType == "application/pdf"
            || file.filename.lowercased().hasSuffix(".pdf")
        return UploadSlotFile(
            name: file.filename,
            sizeLabel: formatClaimFileSize(file.sizeBytes),
            pageCount: nil,
            kind: isPDF ? .pdf : .image
        )
    }

    private func handlePicked(_ newItem: PhotosPickerItem?) {
        guard let newItem, let slot = photosPickerSlot else { return }
        Task {
            if let data = try? await newItem.loadTransferable(type: Data.self) {
                if data.count > CLAIM_FILE_MAX_BYTES {
                    // Client-side guard so the user sees an inline error
                    // instead of a 413 round-trip.
                    viewModel.fileTooLarge(for: slot)
                } else {
                    let filename = "\(slot.rawValue)-\(UUID().uuidString.prefix(6)).jpg"
                    viewModel.picked(slot, file: ClaimPickedFile(
                        filename: filename,
                        mimeType: "image/jpeg",
                        data: data
                    ))
                }
            }
            photosPickerSelection = nil
            photosPickerSlot = nil
        }
    }
}

// MARK: - Footer + error banner

private struct EncryptionFooter: View {
    var body: some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            Icon(.lock, size: 12, color: Theme.Color.appTextSecondary)
            Text(ClaimUploadCopy.encryptionFooter)
                .font(.system(size: 11.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("claimOwnership_encryptionFooter")
    }
}

private struct ClaimUploadErrorBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.alertCircle, size: 18, color: Theme.Color.error)
            Text(message)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.error)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(Spacing.s3)
        .background(Theme.Color.errorBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .accessibilityIdentifier("claimOwnership_errorBanner")
    }
}

/// Human-readable file size, e.g. "1.4 MB" / "820 KB".
func formatClaimFileSize(_ bytes: Int) -> String {
    let mb = Double(bytes) / 1_048_576.0
    if mb >= 1 { return String(format: "%.1f MB", mb) }
    let kb = Double(bytes) / 1024.0
    return String(format: "%.0f KB", kb)
}
