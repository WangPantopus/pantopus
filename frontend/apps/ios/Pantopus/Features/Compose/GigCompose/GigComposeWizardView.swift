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
        self.preselectedCategory = GigComposeCategory.from(rawKey: preselectedCategoryKey)
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
        WizardShell(model: viewModel) {
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
    }

    @ViewBuilder
    private var stepContent: some View {
        switch viewModel.currentStep {
        case .category: CategoryStep(viewModel: viewModel)
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
        // only seed the category when the user is starting fresh.
        if let preselected = preselectedCategory, viewModel.form == .empty {
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

// MARK: - Step 1: Category

private struct CategoryStep: View {
    @Bindable var viewModel: GigComposeViewModel

    private let columns: [GridItem] = [
        GridItem(.flexible(), spacing: Spacing.s2),
        GridItem(.flexible(), spacing: Spacing.s2),
        GridItem(.flexible(), spacing: Spacing.s2)
    ]

    var body: some View {
        HeadlineBlock("What kind of help do you need?")
        SubcopyBlock("Pick the closest match. You can refine it later.")
        LazyVGrid(columns: columns, spacing: Spacing.s2) {
            ForEach(GigComposeCategory.allCases, id: \.self) { category in
                CategoryTile(
                    category: category,
                    isSelected: viewModel.form.category == category
                ) {
                    viewModel.selectCategory(category)
                }
            }
        }
    }
}

private struct CategoryTile: View {
    let category: GigComposeCategory
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: Spacing.s2) {
                Icon(icon, size: 22, color: iconColor)
                Text(category.label)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appText)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity, minHeight: 88)
            .padding(Spacing.s3)
            .background(isSelected ? Theme.Color.primary50 : Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(
                        isSelected ? Theme.Color.primary600 : Theme.Color.appBorder,
                        lineWidth: isSelected ? 2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("composeGig_category_\(category.rawValue)")
        .accessibilityLabel("\(category.label)\(isSelected ? ", selected" : "")")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }

    private var icon: PantopusIcon {
        switch category {
        case .handyman: .hammer
        case .cleaning: .sparkles
        case .moving: .package
        case .petcare: .pawPrint
        case .childcare: .heart
        case .tutoring: .lightbulb
        case .delivery: .send
        case .tech: .zap
        case .other: .moreHorizontal
        }
    }

    private var iconColor: Color {
        isSelected ? Theme.Color.primary600 : Theme.Color.appTextSecondary
    }
}

// MARK: - Step 2: Basics

private struct BasicsStep: View {
    @Bindable var viewModel: GigComposeViewModel

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
        PhotoSlotsRow(
            count: viewModel.form.photoIds.count,
            max: GigComposeLimits.maxPhotos,
            onAddPlaceholder: {
                // Real upload pipeline lands in P15.5. Until then this
                // mints a placeholder URL so the cap + count behaviour
                // are exercisable. The wire field is omitted from the
                // POST body when empty (see `buildCreateBody`).
                viewModel.addPhoto("placeholder://photo/\(UUID().uuidString)")
            },
            onRemove: viewModel.removePhoto(at:)
        )
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
    let count: Int
    let max: Int
    let onAddPlaceholder: () -> Void
    let onRemove: (Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("Photos (optional, up to \(max))")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            HStack(spacing: Spacing.s2) {
                ForEach(0 ..< count, id: \.self) { index in
                    Button {
                        onRemove(index)
                    } label: {
                        ZStack(alignment: .topTrailing) {
                            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                                .fill(Theme.Color.primary50)
                                .frame(width: 64, height: 64)
                                .overlay(
                                    Icon(.camera, size: 22, color: Theme.Color.primary600)
                                )
                            Icon(.x, size: 14, color: Theme.Color.appText)
                                .padding(4)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("composeGig_photo_\(index)")
                    .accessibilityLabel("Remove photo \(index + 1)")
                }
                if count < max {
                    Button(action: onAddPlaceholder) {
                        RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                            .stroke(Theme.Color.appBorder, style: StrokeStyle(lineWidth: 1, dash: [4]))
                            .frame(width: 64, height: 64)
                            .overlay(Icon(.plus, size: 22, color: Theme.Color.appTextSecondary))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("composeGig_addPhoto")
                    .accessibilityLabel("Add photo")
                }
                Spacer(minLength: 0)
            }
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
        if let selected = viewModel.form.budgetType, selected != .offers {
            BudgetRangeFields(viewModel: viewModel, type: selected)
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
