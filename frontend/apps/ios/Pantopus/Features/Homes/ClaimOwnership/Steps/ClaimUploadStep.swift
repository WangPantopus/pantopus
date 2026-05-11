//
//  ClaimUploadStep.swift
//  Pantopus
//
//  P20 FrameUpload — two upload tiles + optional reviewer note +
//  inline error banner above the sticky submit shelf. PDF support is
//  TODO(picker); for now only the system Photos picker is wired and
//  the accept hint advertises JPG/PNG only.
//

import PhotosUI
import SwiftUI

struct ClaimUploadStep: View {
    @Bindable var viewModel: ClaimOwnershipWizardViewModel
    @State private var photosPickerSlot: ClaimEvidenceSlot?
    @State private var photosPickerSelection: PhotosPickerItem?

    var body: some View {
        HeadlineBlock("Upload your evidence")
        SubcopyBlock(
            "Uploads stay private and are only seen by the verification team. We'll never share them publicly."
        )
        UploadSlotsBlock(
            slots: ClaimEvidenceSlot.allCases.map { slotDescriptor(for: $0) },
            onPick: { id in
                if let slot = ClaimEvidenceSlot(rawValue: id) {
                    photosPickerSlot = slot
                }
            },
            onRemove: { id in
                if let slot = ClaimEvidenceSlot(rawValue: id) {
                    viewModel.remove(slot)
                }
            }
        )
        ReviewerNoteField(text: Binding(
            get: { viewModel.note },
            set: { viewModel.note = $0 }
        ))
        if let error = viewModel.submitError {
            ErrorBanner(message: error)
        }
        // Hidden anchor to host the PhotosPicker. Driving the sheet
        // directly off `photosPickerSlot` (rather than an intermediate
        // onAppear hop) keeps the picker reachable after a remove +
        // re-tap of the same slot.
        Color.clear.frame(width: 0, height: 0)
            .photosPicker(
                isPresented: Binding(
                    get: { photosPickerSlot != nil },
                    set: { if !$0 { photosPickerSlot = nil } }
                ),
                selection: $photosPickerSelection,
                matching: .images
            )
            .onChange(of: photosPickerSelection) { _, newItem in
                guard let newItem, let slot = photosPickerSlot else { return }
                Task {
                    if let data = try? await newItem.loadTransferable(type: Data.self) {
                        if data.count > CLAIM_FILE_MAX_BYTES {
                            // Client-side guard so the user sees an
                            // inline error instead of a 413 round-trip.
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

    private func slotDescriptor(for slot: ClaimEvidenceSlot) -> UploadSlot {
        UploadSlot(
            id: slot.rawValue,
            title: slot.title,
            acceptHint: slot.acceptHint,
            state: viewState(for: viewModel.slots[slot] ?? .empty)
        )
    }

    private func viewState(for state: ClaimSlotUiState) -> UploadSlotState {
        switch state {
        case .empty: .empty
        case let .picked(file):
            .picked(name: file.filename, sizeBytes: file.sizeBytes)
        case let .uploading(file, fraction):
            .uploading(name: file.filename, fraction: fraction)
        case let .uploaded(file, _):
            .uploaded(name: file.filename, sizeBytes: file.sizeBytes)
        case let .failed(file, message):
            .failed(name: file.filename, message: message)
        }
    }
}

private struct ReviewerNoteField: View {
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("Add a note for the reviewer (optional)")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            TextField(
                "Anything the reviewer should know about your claim…",
                text: $text,
                axis: .vertical
            )
            .lineLimit(4...8)
            .pantopusTextStyle(.body)
            .padding(Spacing.s3)
            .frame(minHeight: 96, alignment: .topLeading)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md)
                    .stroke(text.count > 500 ? Theme.Color.error : Theme.Color.appBorder, lineWidth: 1)
            )
            .accessibilityIdentifier("claimOwnership_note")
            HStack {
                Spacer()
                Text("\(text.count) / 500")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(text.count > 500 ? Theme.Color.error : Theme.Color.appTextSecondary)
            }
        }
    }
}

private struct ErrorBanner: View {
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
        .clipShape(RoundedRectangle(cornerRadius: Radii.md))
        .accessibilityIdentifier("claimOwnership_errorBanner")
    }
}
