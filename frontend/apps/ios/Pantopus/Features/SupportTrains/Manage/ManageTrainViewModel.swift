//
//  ManageTrainViewModel.swift
//  Pantopus
//
//  A13.13 — Manage train. Organizer-side surface for an active support
//  train. The VM holds the editable update draft (message body, audience
//  selection, push-to-phones toggle) and the close-train sheet draft
//  (optional thank-you note + presentation state). On `Send update` we
//  clear the draft + flash a toast; on `Close & thank` we flip the
//  train to `.closed` and dismiss the sheet. No backend round-trips —
//  the projection is purely UI state.
//

import Foundation

// MARK: - Content models

/// One audience chip in the Send-an-update form (`All helpers 12` etc).
public struct AudienceChipContent: Sendable, Hashable, Identifiable {
    public let id: String
    public let label: String
    public let count: String

    public init(id: String, label: String, count: String) {
        self.id = id
        self.label = label
        self.count = count
    }
}

/// Visual tone for an Organize-section row's leading icon tile.
public enum OrganizeRowTone: Sendable, Hashable {
    case amber
    case sky
    case green
    case red
}

/// One row in the Organize section card (or the Close-train destructive row).
public struct OrganizeRowContent: Sendable, Hashable, Identifiable {
    public let id: String
    public let icon: PantopusIcon
    public let tone: OrganizeRowTone
    public let label: String
    public let meta: String?
    public let sub: String?
    public let isDestructive: Bool

    public init(
        id: String,
        icon: PantopusIcon,
        tone: OrganizeRowTone,
        label: String,
        meta: String?,
        sub: String?,
        isDestructive: Bool
    ) {
        self.id = id
        self.icon = icon
        self.tone = tone
        self.label = label
        self.meta = meta
        self.sub = sub
        self.isDestructive = isDestructive
    }
}

/// The CloseTrainSheet's static copy. Editable thank-you note lives on the VM.
public struct CloseTrainSheetContent: Sendable, Hashable {
    public let daysEarlyLabel: String
    public let mealsDelivered: String
    public let neighborsHelped: String
    public let coverageDays: String
    public let recipientQuote: String

    public init(
        daysEarlyLabel: String,
        mealsDelivered: String,
        neighborsHelped: String,
        coverageDays: String,
        recipientQuote: String
    ) {
        self.daysEarlyLabel = daysEarlyLabel
        self.mealsDelivered = mealsDelivered
        self.neighborsHelped = neighborsHelped
        self.coverageDays = coverageDays
        self.recipientQuote = recipientQuote
    }
}

/// Static, design-driven content for one Manage Train screen instance.
public struct ManageTrainContent: Sendable, Hashable {
    public let trainId: String
    public let title: String
    public let dateRangeLabel: String
    public let isActive: Bool

    // 4-cell StatCellRow values + tones (tones are derived in the view).
    public let slotFillValue: String
    public let helpersValue: String
    public let daysLeftValue: String
    public let dropoutValue: String

    // SlotPreview mini-fill strip — paints 21 dots in 3 buckets.
    public let slotsFilled: Int
    public let slotsOpen: Int
    public let slotsDropout: Int
    public let slotsTotal: Int
    public let slotFillCaption: String

    // Send-an-update form
    public let draftMessage: String
    public let audienceChips: [AudienceChipContent]
    public let selectedAudienceId: String
    public let pushToPhones: Bool

    // Organize + wind-down rows
    public let organizeRows: [OrganizeRowContent]
    public let closeRow: OrganizeRowContent

    // Close-train sheet
    public let close: CloseTrainSheetContent

    public init(
        trainId: String,
        title: String,
        dateRangeLabel: String,
        isActive: Bool,
        slotFillValue: String,
        helpersValue: String,
        daysLeftValue: String,
        dropoutValue: String,
        slotsFilled: Int,
        slotsOpen: Int,
        slotsDropout: Int,
        slotsTotal: Int,
        slotFillCaption: String,
        draftMessage: String,
        audienceChips: [AudienceChipContent],
        selectedAudienceId: String,
        pushToPhones: Bool,
        organizeRows: [OrganizeRowContent],
        closeRow: OrganizeRowContent,
        close: CloseTrainSheetContent
    ) {
        self.trainId = trainId
        self.title = title
        self.dateRangeLabel = dateRangeLabel
        self.isActive = isActive
        self.slotFillValue = slotFillValue
        self.helpersValue = helpersValue
        self.daysLeftValue = daysLeftValue
        self.dropoutValue = dropoutValue
        self.slotsFilled = slotsFilled
        self.slotsOpen = slotsOpen
        self.slotsDropout = slotsDropout
        self.slotsTotal = slotsTotal
        self.slotFillCaption = slotFillCaption
        self.draftMessage = draftMessage
        self.audienceChips = audienceChips
        self.selectedAudienceId = selectedAudienceId
        self.pushToPhones = pushToPhones
        self.organizeRows = organizeRows
        self.closeRow = closeRow
        self.close = close
    }
}

/// Aggregate UI state.
public enum ManageTrainState: Sendable, Equatable {
    case loading
    case loaded(ManageTrainContent)
    case error(message: String)
}

/// Drives the Close-train confirmation sheet.
public enum ManageTrainSheetMode: Sendable, Hashable {
    case hidden
    case closing
    case closed
}

/// Character cap for the update-message textarea. Mirrors the
/// "168/500" counter in the design source.
public let manageTrainMessageMaxChars: Int = 500

@Observable
@MainActor
public final class ManageTrainViewModel {
    public private(set) var state: ManageTrainState = .loading

    /// Editable draft fields. Initialised from `content.draftMessage` /
    /// `selectedAudienceId` / `pushToPhones` on `load()`.
    public var draftMessage: String = ""
    public var selectedAudienceId: String = ""
    public var pushToPhones: Bool = true

    /// Editable thank-you note typed inside the close sheet.
    public var thankYouNote: String = ""

    /// Close-train sheet presentation state.
    public var sheetMode: ManageTrainSheetMode = .hidden

    /// Transient toast (e.g. "Update sent · 12 helpers").
    public var toast: String?

    private let trainId: String
    private let seed: ManageTrainContent?

    public init(trainId: String, content: ManageTrainContent? = nil) {
        self.trainId = trainId
        seed = content
    }

    public func load() async {
        let content = seed ?? ManageTrainSampleData.active
        state = .loaded(content)
        draftMessage = content.draftMessage
        selectedAudienceId = content.selectedAudienceId
        pushToPhones = content.pushToPhones
        thankYouNote = ""
        sheetMode = .hidden
    }

    public func refresh() async { await load() }

    // MARK: - Send-update form

    public var characterCount: Int { draftMessage.count }
    public var characterCounterLabel: String {
        "\(characterCount) / \(manageTrainMessageMaxChars)"
    }

    /// True when the draft message has at least one non-whitespace
    /// character and is under the cap. Mirrors the design's
    /// `Send update` enable rule (textarea non-empty + valid).
    public var canSendUpdate: Bool {
        let trimmed = draftMessage.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && characterCount <= manageTrainMessageMaxChars
    }

    public func updateDraftMessage(_ value: String) {
        // Hard-clip to the cap so the counter never displays over-limit.
        if value.count > manageTrainMessageMaxChars {
            draftMessage = String(value.prefix(manageTrainMessageMaxChars))
        } else {
            draftMessage = value
        }
    }

    public func selectAudience(_ id: String) {
        guard case let .loaded(content) = state,
              content.audienceChips.contains(where: { $0.id == id }) else { return }
        selectedAudienceId = id
    }

    public func togglePush(_ value: Bool) { pushToPhones = value }

    /// Send the typed update. Clears the draft + flashes a toast so the
    /// helper count surfaces. Real `POST /api/support-trains/:id/updates`
    /// wiring lands when the backend ships.
    public func sendUpdate() {
        guard canSendUpdate, case let .loaded(content) = state else { return }
        let helperCount = content.audienceChips.first { $0.id == selectedAudienceId }?.count
            ?? content.helpersValue
        draftMessage = ""
        toast = "Update sent · \(helperCount) helpers"
    }

    public func acknowledgeToast() { toast = nil }

    // MARK: - Close-train sheet

    public func showCloseSheet() { sheetMode = .closing }
    public func hideCloseSheet() { sheetMode = .hidden }

    public func updateThankYouNote(_ value: String) { thankYouNote = value }

    /// Flip the train to `.closed`. The sheet dismisses and the train's
    /// chip flips from "Active" green to "Closed" neutral.
    public func confirmClose() {
        guard case let .loaded(content) = state else { return }
        let next = ManageTrainContent(
            trainId: content.trainId,
            title: content.title,
            dateRangeLabel: content.dateRangeLabel,
            isActive: false,
            slotFillValue: content.slotFillValue,
            helpersValue: content.helpersValue,
            daysLeftValue: content.daysLeftValue,
            dropoutValue: content.dropoutValue,
            slotsFilled: content.slotsFilled,
            slotsOpen: content.slotsOpen,
            slotsDropout: content.slotsDropout,
            slotsTotal: content.slotsTotal,
            slotFillCaption: content.slotFillCaption,
            draftMessage: content.draftMessage,
            audienceChips: content.audienceChips,
            selectedAudienceId: content.selectedAudienceId,
            pushToPhones: content.pushToPhones,
            organizeRows: content.organizeRows,
            closeRow: content.closeRow,
            close: content.close
        )
        state = .loaded(next)
        sheetMode = .closed
        toast = "Train closed · thanks sent to \(content.helpersValue) helpers"
    }
}
