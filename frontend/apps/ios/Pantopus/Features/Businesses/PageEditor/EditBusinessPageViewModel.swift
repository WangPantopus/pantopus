//
//  EditBusinessPageViewModel.swift
//  Pantopus
//
//  P4.2 — A13.10 Edit Business Page. View-model for the business-profile
//  editor. Owns the per-field draft state + dirty tracking. Backend
//  endpoints (load draft / save / publish) are stubbed until the
//  `business-page-editor` API lands; the load path hands back one of
//  the two `EditBusinessPageSampleData` payloads so the view can be
//  exercised end-to-end in previews and tests.
//

import Foundation

@Observable
@MainActor
public final class EditBusinessPageViewModel {
    public private(set) var state: EditBusinessPageState
    public var toastMessage: String?

    /// Surface flag — when true, the view stitches a "Discard"
    /// confirmation sheet over the screen. The view-model only flips
    /// the flag; the view owns the presentation.
    public var showsDiscardConfirm = false

    private let businessId: String

    public init(businessId: String, preview: EditBusinessPageContent? = nil) {
        self.businessId = businessId
        if let preview {
            self.state = .loaded(preview)
        } else {
            self.state = .loading
        }
    }

    /// Hydrate the editor. With no backend wired the load path returns
    /// the published-sample payload as a placeholder.
    public func load() async {
        if case .loaded = state { return }
        try? await Task.sleep(nanoseconds: 50_000_000)
        state = .loaded(EditBusinessPageSampleData.publishedRoostCafe)
    }

    public func refresh() async {
        state = .loading
        await load()
    }

    /// Sticky-save Save button (dirty mode). Stub: clears the dirty
    /// count by re-emitting the content with all `current` values
    /// promoted to `original`, then shows a toast.
    public func save() async {
        guard case let .loaded(content) = state else { return }
        let cleaned = promoteCurrentToOriginal(content)
        state = .loaded(cleaned)
        toastMessage = "Saved"
    }

    /// Sticky-save Save draft button (setup mode). Same stub behaviour
    /// as `save()` — both pin the current draft and clear dirtiness.
    public func saveDraft() async {
        guard case let .loaded(content) = state else { return }
        let cleaned = promoteCurrentToOriginal(content)
        state = .loaded(cleaned)
        toastMessage = "Draft saved"
    }

    /// Sticky-save Publish button (setup mode, only enabled when all
    /// sections complete). Stub: pretend the page went live.
    public func publish() async {
        toastMessage = "Published"
    }

    /// Sticky-save Discard button. Flips a flag the view picks up.
    public func discardRequested() {
        showsDiscardConfirm = true
    }

    public func discardConfirmed() async {
        guard case let .loaded(content) = state else { return }
        let reverted = revertToOriginal(content)
        state = .loaded(reverted)
        showsDiscardConfirm = false
        toastMessage = "Edits discarded"
    }

    // MARK: - Helpers

    private func promoteCurrentToOriginal(_ content: EditBusinessPageContent) -> EditBusinessPageContent {
        // Clearing dirtiness — flip each `EditField` so `original == current`.
        EditBusinessPageContent(
            businessId: content.businessId,
            mode: zeroUnsaved(content.mode),
            banner: content.banner.cleaned,
            logo: content.logo,
            name: content.name.cleaned,
            tagline: content.tagline.cleaned,
            category: content.category.cleaned,
            categoryRequired: content.categoryRequired,
            price: content.price.cleaned,
            description: content.description.cleaned,
            hours: content.hours.cleaned,
            services: content.services.cleaned,
            gallery: content.gallery.cleaned,
            phone: content.phone.cleaned,
            email: content.email.cleaned,
            website: content.website.cleaned,
            bookingLink: content.bookingLink?.cleaned,
            location: content.location.cleaned
        )
    }

    private func revertToOriginal(_ content: EditBusinessPageContent) -> EditBusinessPageContent {
        EditBusinessPageContent(
            businessId: content.businessId,
            mode: zeroUnsaved(content.mode),
            banner: content.banner.reverted,
            logo: content.logo,
            name: content.name.reverted,
            tagline: content.tagline.reverted,
            category: content.category.reverted,
            categoryRequired: content.categoryRequired,
            price: content.price.reverted,
            description: content.description.reverted,
            hours: content.hours.reverted,
            services: content.services.reverted,
            gallery: content.gallery.reverted,
            phone: content.phone.reverted,
            email: content.email.reverted,
            website: content.website.reverted,
            bookingLink: content.bookingLink?.reverted,
            location: content.location.reverted
        )
    }

    private func zeroUnsaved(_ mode: EditBusinessPageMode) -> EditBusinessPageMode {
        switch mode {
        case let .published(_, label): .published(unsavedCount: 0, lastPublishedLabel: label)
        case .setup: mode
        }
    }
}

// MARK: - Local cleanup helpers

private extension EditBusinessPageField {
    /// Both originals and currents are the user's last save — used when
    /// the user hits Save. Dirty becomes false.
    var cleaned: EditBusinessPageField {
        EditBusinessPageField(original: current, current: current, placeholder: placeholder)
    }

    /// Restore the original — used when the user hits Discard.
    var reverted: EditBusinessPageField {
        EditBusinessPageField(original: original, current: original, placeholder: placeholder)
    }
}

private extension EditBusinessPageBannerState {
    var cleaned: EditBusinessPageBannerState {
        switch self {
        case .empty: self
        case let .filled(_, palette): .filled(dirty: false, palette: palette)
        }
    }
    var reverted: EditBusinessPageBannerState {
        // Discard the swap — same shape minus the dirty flag.
        cleaned
    }
}

private extension EditBusinessPageDescriptionState {
    var cleaned: EditBusinessPageDescriptionState {
        switch self {
        case let .field(field, limit): .field(field.cleaned, charLimit: limit)
        case .prompt: self
        }
    }
    var reverted: EditBusinessPageDescriptionState {
        switch self {
        case let .field(field, limit): .field(field.reverted, charLimit: limit)
        case .prompt: self
        }
    }
}

private extension EditBusinessPageHoursState {
    var cleaned: EditBusinessPageHoursState {
        switch self {
        case let .rows(rows, hint):
            .rows(rows: rows.map { row in
                EditBusinessPageHoursRow(id: row.id, dayLabel: row.dayLabel, state: row.state, isDirty: false)
            }, footerHint: hint)
        case .quickApply: self
        }
    }
    var reverted: EditBusinessPageHoursState { cleaned }
}

private extension EditBusinessPageServicesState {
    var cleaned: EditBusinessPageServicesState {
        switch self {
        case let .chips(chips):
            .chips(chips: chips.map {
                EditBusinessPageServiceChip(id: $0.id, label: $0.label, iconKey: $0.iconKey, isFresh: false)
            })
        case .prompt: self
        }
    }
    var reverted: EditBusinessPageServicesState { cleaned }
}

private extension EditBusinessPageGalleryState {
    var cleaned: EditBusinessPageGalleryState {
        EditBusinessPageGalleryState(
            tiles: tiles,
            totalSlots: totalSlots,
            freshAddTile: false,
            hintLabel: hintLabel
        )
    }
    var reverted: EditBusinessPageGalleryState { cleaned }
}

private extension EditBusinessPageLocation {
    var cleaned: EditBusinessPageLocation {
        EditBusinessPageLocation(
            address: address.cleaned,
            error: nil,
            mapVerified: mapVerified,
            pinDirty: false,
            hideExactAddress: hideExactAddress
        )
    }
    var reverted: EditBusinessPageLocation {
        EditBusinessPageLocation(
            address: address.reverted,
            error: error,
            mapVerified: mapVerified,
            pinDirty: false,
            hideExactAddress: hideExactAddress
        )
    }
}
