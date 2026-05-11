//
//  UploadSlotsBlock.swift
//  Pantopus
//
//  Wizard content block — vertical stack of upload tiles. Used by P20's
//  claim ownership Step 2 to collect ID + proof-of-ownership documents.
//

import SwiftUI

/// Render-state for one tile.
public enum UploadSlotState: Sendable, Equatable {
    case empty
    case picked(name: String, sizeBytes: Int)
    case uploading(name: String, fraction: Double)
    case uploaded(name: String, sizeBytes: Int)
    case failed(name: String, message: String)
}

/// One upload-tile descriptor passed to `UploadSlotsBlock`.
public struct UploadSlot: Sendable, Equatable, Identifiable {
    public let id: String
    public let title: String
    public let acceptHint: String
    public let state: UploadSlotState

    public init(id: String, title: String, acceptHint: String, state: UploadSlotState) {
        self.id = id
        self.title = title
        self.acceptHint = acceptHint
        self.state = state
    }
}

/// 2-up vertical stack of upload tiles. Each tile is 160 pt tall with
/// a dashed border + sunken background; tap fires `onPick` and the
/// caller drives state transitions through the consumer's view-model.
@MainActor
public struct UploadSlotsBlock: View {
    private let slots: [UploadSlot]
    private let onPick: @MainActor (String) -> Void
    private let onRemove: @MainActor (String) -> Void

    public init(
        slots: [UploadSlot],
        onPick: @escaping @MainActor (String) -> Void,
        onRemove: @escaping @MainActor (String) -> Void = { _ in }
    ) {
        self.slots = slots
        self.onPick = onPick
        self.onRemove = onRemove
    }

    public var body: some View {
        VStack(spacing: Spacing.s3) {
            ForEach(slots) { slot in
                UploadTile(slot: slot, onPick: onPick, onRemove: onRemove)
            }
        }
    }
}

private struct UploadTile: View {
    let slot: UploadSlot
    let onPick: @MainActor (String) -> Void
    let onRemove: @MainActor (String) -> Void

    var body: some View {
        Button(action: tapHandler) {
            content
                .frame(maxWidth: .infinity)
                .frame(height: 160)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
                .overlay(border)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(a11y)
        .accessibilityIdentifier("uploadSlot_\(slot.id)")
        .overlay(alignment: .topTrailing) {
            if showsRemove {
                Button {
                    onRemove(slot.id)
                } label: {
                    Icon(.x, size: 16, color: Theme.Color.appTextInverse)
                        .frame(width: 28, height: 28)
                        .background(Theme.Color.appText.opacity(0.7))
                        .clipShape(Circle())
                        .padding(Spacing.s2)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Remove \(slot.title)")
                .accessibilityIdentifier("uploadSlot_remove_\(slot.id)")
            }
        }
    }

    private func tapHandler() {
        switch slot.state {
        case .empty, .failed:
            onPick(slot.id)
        default:
            break
        }
    }

    private var showsRemove: Bool {
        switch slot.state {
        case .picked, .uploaded, .failed: true
        case .empty, .uploading: false
        }
    }

    @ViewBuilder private var content: some View {
        switch slot.state {
        case .empty:
            emptyState
        case let .picked(name, size), let .uploaded(name, size):
            filledState(name: name, size: size, isUploaded: isUploaded)
        case let .uploading(name, fraction):
            uploadingState(name: name, fraction: fraction)
        case let .failed(name, message):
            failedState(name: name, message: message)
        }
    }

    private var emptyState: some View {
        VStack(spacing: Spacing.s2) {
            Icon(.upload, size: 28, color: Theme.Color.appTextSecondary)
            Text(slot.title)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
            Text("Tap to upload")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Text(slot.acceptHint)
                .font(.system(size: 11))
                .foregroundStyle(Theme.Color.appTextMuted)
        }
        .padding(Spacing.s4)
    }

    private func filledState(name: String, size: Int, isUploaded: Bool) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                Icon(isUploaded ? .checkCircle : .file,
                     size: 22,
                     color: isUploaded ? Theme.Color.success : Theme.Color.primary600)
                VStack(alignment: .leading, spacing: 2) {
                    Text(slot.title)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                    Text(name)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                        .lineLimit(2)
                }
                Spacer()
            }
            Text(formatSize(size))
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .padding(Spacing.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func uploadingState(name: String, fraction: Double) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(spacing: Spacing.s2) {
                ProgressView()
                Text("Uploading \(name)…")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
            }
            ProgressView(value: max(0.05, min(1.0, fraction)))
                .tint(Theme.Color.primary600)
        }
        .padding(Spacing.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func failedState(name: String, message: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                Icon(.alertCircle, size: 22, color: Theme.Color.error)
                Text(name)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
            }
            Text(message)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.error)
            Text("Tap to retry")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.primary600)
        }
        .padding(Spacing.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var border: some View {
        RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
            .strokeBorder(
                style: StrokeStyle(
                    lineWidth: borderWidth,
                    lineCap: .round,
                    dash: borderDash
                )
            )
            .foregroundStyle(borderColor)
    }

    private var borderColor: Color {
        switch slot.state {
        case .empty: Theme.Color.appBorderStrong
        case .picked, .uploading: Theme.Color.primary600
        case .uploaded: Theme.Color.success
        case .failed: Theme.Color.error
        }
    }

    private var borderWidth: CGFloat {
        switch slot.state {
        case .empty, .failed: 1.5
        default: 1
        }
    }

    private var borderDash: [CGFloat] {
        switch slot.state {
        case .empty, .failed: [6, 4]
        default: []
        }
    }

    private var isUploaded: Bool {
        if case .uploaded = slot.state { return true }
        return false
    }

    private var a11y: String {
        switch slot.state {
        case .empty:
            "\(slot.title). Tap to upload. \(slot.acceptHint)"
        case let .picked(name, _):
            "\(slot.title). \(name) selected. Tap remove to reset."
        case let .uploading(name, fraction):
            "\(slot.title). Uploading \(name), \(Int(fraction * 100)) percent."
        case let .uploaded(name, _):
            "\(slot.title). \(name) uploaded."
        case let .failed(name, message):
            "\(slot.title). Failed to upload \(name). \(message). Tap to retry."
        }
    }

    private func formatSize(_ bytes: Int) -> String {
        let mb = Double(bytes) / 1_048_576.0
        if mb >= 1 { return String(format: "%.1f MB", mb) }
        let kb = Double(bytes) / 1_024.0
        return String(format: "%.0f KB", kb)
    }
}

#Preview {
    UploadSlotsBlock(
        slots: [
            UploadSlot(id: "id", title: "Government ID", acceptHint: "JPG, PNG, or PDF up to 10 MB", state: .empty),
            UploadSlot(id: "doc", title: "Proof of ownership", acceptHint: "JPG, PNG, or PDF up to 10 MB", state: .uploaded(name: "Deed.pdf", sizeBytes: 256_000))
        ],
        onPick: { _ in },
        onRemove: { _ in }
    )
    .padding()
    .background(Theme.Color.appBg)
}
