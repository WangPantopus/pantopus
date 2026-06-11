//
//  GigComposeWizardView.swift
//  Pantopus
//
//  P2.2 Post-a-Task wizard. Six-step linear flow + terminal success
//  step, built on the shared `WizardShell`. Mirrors `AddHomeWizardView`'s
//  structure: a `@SceneStorage` mirror of the form, an `onChange`
//  persist hook, and a `pendingEvent` handler that bridges back to the
//  navigation host.
//

// swiftlint:disable file_length

import PhotosUI
import SwiftUI

/// Pushed onto a tab stack from the Gigs FAB (Hub) and the You tab's
/// "Post a task" / "Repost" entry points. On success the host pops the
/// wizard and routes to the new gig's detail via `onOpenGigDetail`.
public struct GigComposeWizardView: View {
    @State private var viewModel: GigComposeViewModel
    @SceneStorage("composeGigWizardForm") private var storedForm: String = ""
    @State private var hasRestored = false
    @Environment(\.dismiss) private var dismiss

    private let preselectedCategory: GigComposeCategory?
    private let onOpenGigDetail: (String) -> Void

    init(
        preselectedCategoryKey: String? = nil,
        viewModel: GigComposeViewModel? = nil,
        onOpenGigDetail: @escaping (String) -> Void
    ) {
        preselectedCategory = GigComposeCategory.from(rawKey: preselectedCategoryKey)
        self.onOpenGigDetail = onOpenGigDetail
        // Always start with `.empty` so the SceneStorage restore can win
        // when the user backgrounds + resumes mid-wizard. The
        // preselected category is applied in `onAppear` only when the
        // restored snapshot is empty (or absent).
        if let viewModel {
            _viewModel = State(initialValue: viewModel)
        } else {
            _viewModel = State(initialValue: GigComposeViewModel(initialState: .empty))
        }
    }

    public var body: some View {
        // `@Bindable` projects a binding to the `@Observable` model's
        // `activePickerSheet` so the E.1 picker sheets present via
        // `.sheet(item:)`.
        @Bindable var bindable = viewModel
        return WizardShell(model: viewModel) {
            stepContent
            if let error = viewModel.errorMessage {
                GigComposeErrorBanner(message: error)
            }
        }
        .onAppear {
            restoreIfNeeded()
            if let stepNumber = viewModel.currentStep.stepNumber {
                Analytics.track(
                    .screenComposeGigWizardStepViewed(
                        stepNumber: stepNumber,
                        stepName: String(describing: viewModel.currentStep)
                    )
                )
            }
        }
        .onChange(of: viewModel.form) { _, _ in persist() }
        .onChange(of: viewModel.pendingEvent) { _, event in handle(event) }
        .accessibilityIdentifier("composeGigWizard")
        .sheet(item: $bindable.activePickerSheet) { sheet in
            GigPickerSheetHost(sheet: sheet, viewModel: viewModel)
        }
    }

    @ViewBuilder
    private var stepContent: some View {
        switch viewModel.currentStep {
        case .category:
            // B.3 — Step 1 renders Magic describe (default) or the manual
            // archetype picker, toggled by `form.composeMode`.
            if viewModel.form.composeMode == .magic {
                MagicDescribeStep(viewModel: viewModel)
            } else {
                ManualPickerStep(viewModel: viewModel)
            }
        case .basics: BasicsStep(viewModel: viewModel)
        case .budget: BudgetStep(viewModel: viewModel)
        case .schedule: ScheduleStep(viewModel: viewModel)
        case .location: LocationStep(viewModel: viewModel)
        case .review: ReviewStep(viewModel: viewModel)
        case .success: SuccessStep()
        }
    }

    private func restoreIfNeeded() {
        guard !hasRestored else { return }
        hasRestored = true
        if let data = storedForm.data(using: .utf8),
           let snapshot = try? JSONDecoder().decode(GigComposeFormState.self, from: data) {
            viewModel.restore(from: snapshot)
        }
        // Apply the route's preselected category last — `restore(from:)`
        // is a no-op if a snapshot already populated the form, so we
        // only seed the category when the user is starting fresh. A
        // preselected category means the user already chose one, so land
        // on the manual picker (tile pre-selected) rather than Magic.
        if let preselected = preselectedCategory, viewModel.form == .empty {
            viewModel.setComposeMode(.manual)
            viewModel.selectCategory(preselected)
        }
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(viewModel.form),
              let json = String(data: data, encoding: .utf8)
        else { return }
        storedForm = json
    }

    private func handle(_ event: GigComposeOutboundEvent?) {
        guard let event else { return }
        switch event {
        case .dismiss:
            storedForm = ""
            dismiss()
        case let .openGigDetail(gigId):
            storedForm = ""
            onOpenGigDetail(gigId)
        }
        viewModel.pendingEvent = nil
    }
}

// MARK: - Step 2: Basics

private struct BasicsStep: View {
    @Bindable var viewModel: GigComposeViewModel
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showsPhotosPicker = false

    var body: some View {
        HeadlineBlock("Describe the task")
        SubcopyBlock("A clear title and a few details help neighbors decide if it's right for them.")
        FormFieldsBlock {
            PantopusTextField(
                "Title",
                text: titleBinding,
                placeholder: "Hang 3 shelves in the living room",
                identifier: "composeGig_title"
            )
            DescriptionField(text: descriptionBinding)
            CharacterCounter(
                current: viewModel.form.description.count,
                min: GigComposeLimits.descriptionMin,
                max: GigComposeLimits.descriptionMax
            )
        }
        // P15.5 — real photo pipeline: PhotosPicker selection → immediate
        // background upload per photo with per-tile state (spinner /
        // tap-to-retry / uploaded). First photo is the cover.
        PhotoSlotsRow(
            attachments: viewModel.attachments,
            max: GigComposeLimits.maxPhotos,
            onAdd: { showsPhotosPicker = true },
            onRetry: { viewModel.retryUpload(id: $0) },
            onRemove: { viewModel.removeAttachment(id: $0) }
        )
        .photosPicker(
            isPresented: $showsPhotosPicker,
            selection: $pickerItems,
            maxSelectionCount: max(1, GigComposeLimits.maxPhotos - viewModel.attachments.count),
            matching: .images
        )
        .onChange(of: pickerItems) { _, newItems in
            handlePicked(newItems)
        }
    }

    private func handlePicked(_ items: [PhotosPickerItem]) {
        guard !items.isEmpty else { return }
        Task {
            for item in items {
                if let data = try? await item.loadTransferable(type: Data.self), !data.isEmpty {
                    viewModel.addPhotoData(data)
                }
            }
            pickerItems = []
        }
    }

    private var titleBinding: Binding<String> {
        Binding(
            get: { viewModel.form.title },
            set: { viewModel.setTitle($0) }
        )
    }

    private var descriptionBinding: Binding<String> {
        Binding(
            get: { viewModel.form.description },
            set: { viewModel.setDescription($0) }
        )
    }
}

private struct DescriptionField: View {
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text("Description")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            TextEditor(text: $text)
                .font(Theme.Font.body)
                .foregroundStyle(Theme.Color.appText)
                .frame(minHeight: 120)
                .scrollContentBackground(.hidden)
                .padding(Spacing.s2)
                .background(Theme.Color.appSurface)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .accessibilityIdentifier("composeGig_description")
        }
    }
}

private struct CharacterCounter: View {
    let current: Int
    let min: Int
    let max: Int

    var body: some View {
        let needsMore = current < min
        Text(needsMore ? "\(min - current) more characters" : "\(current) / \(max)")
            .pantopusTextStyle(.caption)
            .foregroundStyle(needsMore ? Theme.Color.warning : Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .trailing)
            .accessibilityIdentifier("composeGig_descriptionCounter")
    }
}

private struct PhotoSlotsRow: View {
    let attachments: [GigComposeAttachment]
    let max: Int
    let onAdd: () -> Void
    let onRetry: (String) -> Void
    let onRemove: (String) -> Void

    private var hasUploading: Bool {
        attachments.contains { $0.status == .uploading }
    }

    private var hasFailed: Bool {
        attachments.contains { $0.status == .failed }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("Photos (optional, up to \(max))")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            HStack(spacing: Spacing.s2) {
                ForEach(Array(attachments.enumerated()), id: \.element.id) { index, attachment in
                    GigPhotoTile(
                        attachment: attachment,
                        isCover: index == 0,
                        index: index,
                        onRetry: { onRetry(attachment.id) },
                        onRemove: { onRemove(attachment.id) }
                    )
                }
                if attachments.count < max {
                    Button(action: onAdd) {
                        RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                            .stroke(Theme.Color.appBorder, style: StrokeStyle(lineWidth: 1, dash: [4]))
                            .frame(width: 64, height: 64)
                            .overlay(Icon(.plus, size: 22, color: Theme.Color.appTextSecondary))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("composeGig_addPhoto")
                    .accessibilityLabel("Add photo")
                }
                Spacer(minLength: Spacing.s0)
            }
            if hasUploading {
                Text("Uploading photos… you can continue once they finish.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.warning)
                    .accessibilityIdentifier("composeGig_uploadingHint")
            } else if hasFailed {
                Text("A photo failed to upload — tap it to retry, or remove it.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
                    .accessibilityIdentifier("composeGig_uploadFailedHint")
            }
        }
    }
}

/// One 64×64 photo tile — uploading spinner / failed tap-to-retry /
/// uploaded thumbnail (first tile carries the "Cover" badge).
private struct GigPhotoTile: View {
    let attachment: GigComposeAttachment
    let isCover: Bool
    let index: Int
    let onRetry: () -> Void
    let onRemove: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            tileBody
                .frame(width: 64, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            Button(action: onRemove) {
                Icon(.x, size: 12, strokeWidth: 2.6, color: Theme.Color.appTextInverse)
                    .frame(width: 18, height: 18)
                    .background(Circle().fill(Color.black.opacity(0.55)))
            }
            .buttonStyle(.plain)
            .padding(2)
            .accessibilityLabel("Remove photo \(index + 1)")
        }
        .overlay(alignment: .bottomLeading) {
            if isCover, attachment.uploadedURL != nil {
                Text("Cover")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s1)
                    .padding(.vertical, 1)
                    .background(Color.black.opacity(0.55))
                    .clipShape(Capsule())
                    .padding(3)
            }
        }
        .accessibilityIdentifier("composeGig_photo_\(index)")
        .accessibilityLabel(accessibilityText)
    }

    @ViewBuilder private var tileBody: some View {
        switch attachment.status {
        case .uploading:
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .fill(Theme.Color.appSurfaceSunken)
                .overlay(ProgressView().tint(Theme.Color.primary600))
        case .failed:
            Button(action: onRetry) {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(Theme.Color.errorBg)
                    .overlay(
                        VStack(spacing: 2) {
                            Icon(.alertCircle, size: 16, strokeWidth: 2.4, color: Theme.Color.error)
                            Text("Retry")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(Theme.Color.error)
                        }
                    )
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("composeGig_retryPhoto_\(index)")
        case .uploaded:
            if let uiImage = UIImage(data: attachment.imageData) {
                Image(uiImage: uiImage)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 64, height: 64)
            } else {
                // Restored-from-SceneStorage attachments carry no bytes —
                // render the generic photo glyph over the primary tint.
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(Theme.Color.primary50)
                    .overlay(Icon(.image, size: 22, color: Theme.Color.primary600))
            }
        }
    }

    private var accessibilityText: String {
        switch attachment.status {
        case .uploading: "Photo \(index + 1), uploading"
        case .failed: "Photo \(index + 1), upload failed, tap to retry"
        case .uploaded: "Photo \(index + 1)\(isCover ? ", cover" : ""), uploaded"
        }
    }
}

// MARK: - Step 3: Budget

private struct BudgetStep: View {
    @Bindable var viewModel: GigComposeViewModel

    var body: some View {
        HeadlineBlock("Set your budget")
        SubcopyBlock("Pick a price model. Helpers see this on the gig card.")
        VStack(spacing: Spacing.s2) {
            ForEach(GigComposeBudgetType.allCases, id: \.self) { type in
                RadioRow(
                    label: type.label,
                    subcopy: subcopy(for: type),
                    isSelected: viewModel.form.budgetType == type,
                    identifier: "composeGig_budget_\(type.rawValue)"
                ) {
                    viewModel.selectBudgetType(type)
                }
            }
        }
        // G — entering the budget step fetches the nearby price
        // benchmark for the chosen category (silent on failure).
        .task { await viewModel.loadPriceBenchmark() }
        if let selected = viewModel.form.budgetType, selected != .offers {
            BudgetRangeFields(viewModel: viewModel, type: selected)
        }
        if let benchmark = viewModel.priceBenchmark {
            PriceBenchmarkHint(
                benchmark: benchmark,
                categoryLabel: viewModel.form.category?.label.lowercased() ?? "similar"
            )
        }
    }

    private func subcopy(for type: GigComposeBudgetType) -> String {
        switch type {
        case .fixed: "One total price for the whole job."
        case .hourly: "Pay by the hour worked."
        case .offers: "Helpers send their own price and you pick."
        }
    }
}

/// Work item G — "Similar handyman tasks nearby: $40–$120 · median $60"
/// hint under the budget fields, with the benchmark's basis line as a
/// sub-caption. Renders only when a benchmark with comparables landed.
private struct PriceBenchmarkHint: View {
    let benchmark: GigPriceBenchmarkDTO
    let categoryLabel: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(
                "Similar \(categoryLabel) tasks nearby: "
                    + "\(money(benchmark.low))–\(money(benchmark.high))"
                    + " · median \(money(benchmark.median))"
            )
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(Theme.Color.appTextSecondary)
            if let basis = benchmark.basis, !basis.isEmpty {
                Text(basis)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("gigCompose.priceBenchmark")
    }

    private func money(_ value: Double) -> String {
        value.truncatingRemainder(dividingBy: 1) == 0
            ? "$\(Int(value))"
            : String(format: "$%.2f", value)
    }
}

private struct BudgetRangeFields: View {
    @Bindable var viewModel: GigComposeViewModel
    let type: GigComposeBudgetType

    var body: some View {
        let suffix = type == .hourly ? "/ hr" : "total"
        FormFieldsBlock {
            HStack(alignment: .top, spacing: Spacing.s2) {
                PantopusTextField(
                    "Min \(suffix)",
                    text: minBinding,
                    placeholder: "20",
                    keyboardType: .decimalPad,
                    identifier: "composeGig_budgetMin"
                )
                PantopusTextField(
                    "Max \(suffix)",
                    text: maxBinding,
                    placeholder: "Optional",
                    keyboardType: .decimalPad,
                    identifier: "composeGig_budgetMax"
                )
            }
        }
    }

    private var minBinding: Binding<String> {
        Binding(
            get: { viewModel.form.budgetMin },
            set: { viewModel.setBudgetMin($0) }
        )
    }

    private var maxBinding: Binding<String> {
        Binding(
            get: { viewModel.form.budgetMax },
            set: { viewModel.setBudgetMax($0) }
        )
    }
}

// MARK: - Step 4: Schedule

private struct ScheduleStep: View {
    @Bindable var viewModel: GigComposeViewModel

    var body: some View {
        HeadlineBlock("When does it need to happen?")
        SubcopyBlock("Pick one — you can change it later.")
        VStack(spacing: Spacing.s2) {
            ForEach(GigComposeScheduleType.allCases, id: \.self) { type in
                RadioRow(
                    label: type.label,
                    subcopy: subcopy(for: type),
                    isSelected: viewModel.form.scheduleType == type,
                    identifier: "composeGig_schedule_\(type.rawValue)"
                ) {
                    viewModel.selectScheduleType(type)
                }
            }
        }
        if viewModel.form.scheduleType == .oneTime {
            OneTimeDatePicker(viewModel: viewModel)
        }
    }

    private func subcopy(for type: GigComposeScheduleType) -> String {
        switch type {
        case .oneTime: "A single date and time."
        case .recurring: "Repeats on a regular cadence."
        case .flexible: "Whenever works for both of you."
        }
    }
}

private struct OneTimeDatePicker: View {
    @Bindable var viewModel: GigComposeViewModel

    var body: some View {
        FormFieldsBlock {
            DatePicker(
                "When",
                selection: dateBinding,
                in: Date().addingTimeInterval(60)...,
                displayedComponents: [.date, .hourAndMinute]
            )
            .datePickerStyle(.compact)
            .tint(Theme.Color.primary600)
            .accessibilityIdentifier("composeGig_scheduledStart")
        }
    }

    private var dateBinding: Binding<Date> {
        Binding(
            get: {
                if let iso = viewModel.form.scheduledStartISO,
                   let date = ISO8601DateFormatter().date(from: iso) {
                    return date
                }
                // Default to "now + 1 hour" so the picker opens on a
                // plausible value but the form only counts as valid once
                // the user actually taps it (we only mirror the binding
                // setter, never the getter, into the form state).
                return Date().addingTimeInterval(3600)
            },
            set: { viewModel.setScheduledStart($0) }
        )
    }
}

// MARK: - Step 5: Location

private struct LocationStep: View {
    @Bindable var viewModel: GigComposeViewModel

    var body: some View {
        HeadlineBlock("Where does the task happen?")
        SubcopyBlock("Your exact address is shared only after a helper is selected.")
        VStack(spacing: Spacing.s2) {
            ForEach(GigComposeLocationMode.allCases, id: \.self) { mode in
                RadioRow(
                    label: mode.label,
                    subcopy: mode.subcopy,
                    isSelected: viewModel.form.locationMode == mode,
                    identifier: "composeGig_location_\(mode.rawValue)"
                ) {
                    viewModel.selectLocationMode(mode)
                }
            }
        }
        if viewModel.form.locationMode == .aPlace {
            FormFieldsBlock {
                PantopusTextField(
                    "Street",
                    text: line1Binding,
                    placeholder: "123 Main St",
                    contentType: .streetAddressLine1,
                    identifier: "composeGig_place_line1"
                )
                PantopusTextField(
                    "City",
                    text: cityBinding,
                    contentType: .addressCity,
                    identifier: "composeGig_place_city"
                )
                HStack(alignment: .top, spacing: Spacing.s2) {
                    PantopusTextField(
                        "State",
                        text: stateBinding,
                        contentType: .addressState,
                        identifier: "composeGig_place_state"
                    )
                    PantopusTextField(
                        "ZIP",
                        text: zipBinding,
                        keyboardType: .numbersAndPunctuation,
                        contentType: .postalCode,
                        identifier: "composeGig_place_zip"
                    )
                }
            }
        }
    }

    private var line1Binding: Binding<String> {
        Binding(
            get: { viewModel.form.placeAddress.line1 },
            set: { viewModel.updatePlaceAddress(line1: $0) }
        )
    }

    private var cityBinding: Binding<String> {
        Binding(
            get: { viewModel.form.placeAddress.city },
            set: { viewModel.updatePlaceAddress(city: $0) }
        )
    }

    private var stateBinding: Binding<String> {
        Binding(
            get: { viewModel.form.placeAddress.state },
            set: { viewModel.updatePlaceAddress(state: $0) }
        )
    }

    private var zipBinding: Binding<String> {
        Binding(
            get: { viewModel.form.placeAddress.zip },
            set: { viewModel.updatePlaceAddress(zip: $0) }
        )
    }
}

// MARK: - Step 6: Review

private struct ReviewStep: View {
    @Bindable var viewModel: GigComposeViewModel

    var body: some View {
        HeadlineBlock("Review and post")
        SubcopyBlock("Check the details. Helpers see what's below as your gig card.")
        ReviewSummaryBlock([
            ReviewSummaryRow(label: "Category", value: viewModel.form.category?.label ?? "—"),
            ReviewSummaryRow(label: "Title", value: viewModel.form.title.isEmpty ? "—" : viewModel.form.title),
            ReviewSummaryRow(label: "Description", value: condensedDescription),
            ReviewSummaryRow(label: "Photos", value: photosSummary),
            ReviewSummaryRow(label: "Budget", value: budgetSummary),
            ReviewSummaryRow(label: "Schedule", value: scheduleSummary),
            ReviewSummaryRow(label: "Location", value: locationSummary)
        ])
        // E.1 — optional composer fields backed by the picker sheets.
        GigComposeOptionsBlock(viewModel: viewModel)
    }

    private var condensedDescription: String {
        let trimmed = viewModel.form.description.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "—" }
        if trimmed.count > 140 { return String(trimmed.prefix(140)) + "…" }
        return trimmed
    }

    private var photosSummary: String {
        let count = viewModel.form.photoIds.count
        if count == 0 { return "None" }
        return count == 1 ? "1 photo" : "\(count) photos"
    }

    private var budgetSummary: String {
        guard let type = viewModel.form.budgetType else { return "—" }
        switch type {
        case .offers: return "Open to bids"
        case .fixed, .hourly:
            let suffix = type == .hourly ? "/hr" : ""
            let min = viewModel.form.budgetMin
            let max = viewModel.form.budgetMax
            if !max.isEmpty {
                return "$\(min)–$\(max)\(suffix)"
            }
            return "$\(min)\(suffix)"
        }
    }

    private var scheduleSummary: String {
        guard let type = viewModel.form.scheduleType else { return "—" }
        if type == .oneTime, let iso = viewModel.form.scheduledStartISO,
           let date = ISO8601DateFormatter().date(from: iso) {
            let fmt = DateFormatter()
            fmt.dateStyle = .medium
            fmt.timeStyle = .short
            return fmt.string(from: date)
        }
        return type.label
    }

    private var locationSummary: String {
        guard let mode = viewModel.form.locationMode else { return "—" }
        switch mode {
        case .yourAddress: return "Your saved address"
        case .virtual: return "Virtual"
        case .aPlace:
            let addr = viewModel.form.placeAddress
            if addr.isComplete {
                return "\(addr.line1), \(addr.city), \(addr.state) \(addr.zip)"
            }
            return "A place"
        }
    }
}

// MARK: - E.1 Optional details (picker-sheet fields)

/// Tappable field rows on the Review step that open the composer picker
/// sheets and reflect their bound values. Mirrors the design's composer
/// field list (Category · Deadline · Cancellation policy · Urgency · Tags).
private struct GigComposeOptionsBlock: View {
    @Bindable var viewModel: GigComposeViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Text("ADD DETAILS (OPTIONAL)")
                .font(.system(size: 11, weight: .semibold))
                .tracking(0.6)
                .foregroundStyle(Theme.Color.appTextSecondary)
            OptionFieldRow(
                label: "Category",
                value: viewModel.form.category?.label,
                placeholder: "Tap to choose",
                identifier: "gigPicker.row.category"
            ) { viewModel.presentPicker(.category) }
            OptionFieldRow(
                label: "Deadline",
                value: deadlineText,
                placeholder: "Flexible",
                identifier: "gigPicker.row.deadline"
            ) { viewModel.presentPicker(.deadline) }
            OptionFieldRow(
                label: "Cancellation policy",
                value: viewModel.form.cancellationPolicy?.label,
                placeholder: "Standard",
                identifier: "gigPicker.row.policy"
            ) { viewModel.presentPicker(.policy) }
            OptionFieldRow(
                label: "Urgency",
                value: viewModel.form.isUrgent ? "Urgent" : nil,
                placeholder: "Not urgent",
                identifier: "gigPicker.row.urgency"
            ) { viewModel.presentPicker(.urgency) }
            OptionFieldRow(
                label: "Tags",
                value: tagsText,
                placeholder: "Add tags",
                identifier: "gigPicker.row.tags"
            ) { viewModel.presentPicker(.tags) }
        }
    }

    private var deadlineText: String? {
        guard let iso = viewModel.form.deadlineISO,
              let date = ISO8601DateFormatter().date(from: iso)
        else { return nil }
        let fmt = DateFormatter()
        fmt.dateFormat = "EEE, MMM d"
        return fmt.string(from: date)
    }

    private var tagsText: String? {
        let tags = viewModel.form.tags
        guard !tags.isEmpty else { return nil }
        return tags.map { "#\($0)" }.joined(separator: " · ")
    }
}

private struct OptionFieldRow: View {
    let label: String
    let value: String?
    let placeholder: String
    let identifier: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: Spacing.s1 + 1) {
                Text(label)
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                HStack(spacing: Spacing.s2) {
                    Text(value ?? placeholder)
                        .font(.system(size: 13.5))
                        .foregroundStyle(value == nil ? Theme.Color.appTextMuted : Theme.Color.appText)
                        .lineLimit(1)
                    Spacer(minLength: Spacing.s0)
                    Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
                }
                .padding(.horizontal, Spacing.s3)
                .frame(height: 44)
                .frame(maxWidth: .infinity)
                .background(value == nil ? Theme.Color.appSurface : Theme.Color.primary50)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .stroke(value == nil ? Theme.Color.appBorder : Theme.Color.primary600, lineWidth: 1)
                )
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(identifier)
        .accessibilityLabel("\(label): \(value ?? placeholder)")
    }
}

// MARK: - Success

private struct SuccessStep: View {
    var body: some View {
        SuccessHeroBlock(
            headline: "Task posted",
            subcopy: "Helpers can now see it on the Gigs feed. We'll notify you when bids come in."
        )
    }
}

// MARK: - Helpers

private struct RadioRow: View {
    let label: String
    let subcopy: String
    let isSelected: Bool
    let identifier: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: Spacing.s3) {
                ZStack {
                    Circle()
                        .stroke(
                            isSelected ? Theme.Color.primary600 : Theme.Color.appBorder,
                            lineWidth: 2
                        )
                        .frame(width: 22, height: 22)
                    if isSelected {
                        Circle().fill(Theme.Color.primary600).frame(width: 12, height: 12)
                    }
                }
                .padding(.top, 2)
                VStack(alignment: .leading, spacing: 2) {
                    Text(label)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    Text(subcopy)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer()
            }
            .padding(Spacing.s3)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(
                        isSelected ? Theme.Color.primary600 : Theme.Color.appBorder,
                        lineWidth: isSelected ? 2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(identifier)
        .accessibilityLabel("\(label). \(subcopy)")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

private struct GigComposeErrorBanner: View {
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
        .accessibilityIdentifier("composeGigErrorBanner")
    }
}

#Preview {
    GigComposeWizardView(preselectedCategoryKey: "handyman") { _ in }
}

// swiftlint:enable file_length
