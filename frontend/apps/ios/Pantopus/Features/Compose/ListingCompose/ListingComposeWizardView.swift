//
//  ListingComposeWizardView.swift
//  Pantopus
//
//  Concrete Snap & Sell listing wizard. Composes `WizardShell` with six
//  step bodies and persists in-progress state via `@SceneStorage` so the
//  wizard survives process death.
//

import SwiftUI

/// Pushed onto the Hub stack from the Marketplace FAB. On success,
/// signals the parent stack to pop the wizard and route to the new
/// listing's detail via `onOpenListingDetail`.
public struct ListingComposeWizardView: View {
    @State private var viewModel = ListingComposeWizardViewModel()
    @SceneStorage("listingComposeWizardForm") private var storedForm: String = ""
    @State private var hasRestored = false
    @State private var photoPendingRemoval: ListingComposePhoto?
    @Environment(\.dismiss) private var dismiss

    private let onOpenListingDetail: (String) -> Void

    public init(onOpenListingDetail: @escaping (String) -> Void) {
        self.onOpenListingDetail = onOpenListingDetail
    }

    public var body: some View {
        WizardShell(model: viewModel) {
            stepContent
            if let error = viewModel.errorMessage {
                ListingComposeErrorBanner(message: error)
            }
        }
        .onAppear {
            restoreIfNeeded()
            if let stepNumber = viewModel.currentStep.stepNumber {
                Analytics.track(
                    .screenListingComposeWizardStepViewed(
                        stepNumber: stepNumber,
                        stepName: String(describing: viewModel.currentStep)
                    )
                )
            }
        }
        .onChange(of: viewModel.form) { _, _ in persist() }
        .onChange(of: viewModel.pendingEvent) { _, event in handle(event) }
        .confirmationDialog(
            "Remove this photo?",
            isPresented: Binding(
                get: { photoPendingRemoval != nil },
                set: { if !$0 { photoPendingRemoval = nil } }
            ),
            titleVisibility: .visible,
            presenting: photoPendingRemoval
        ) { photo in
            Button("Remove photo", role: .destructive) {
                viewModel.removePhoto(id: photo.id)
                photoPendingRemoval = nil
            }
            .accessibilityIdentifier("listingCompose_removePhotoConfirm")
            Button("Cancel", role: .cancel) { photoPendingRemoval = nil }
        }
        .accessibilityIdentifier("listingComposeWizard")
    }

    @ViewBuilder
    private var stepContent: some View {
        switch viewModel.currentStep {
        case .photos:
            PhotosStep(
                viewModel: viewModel,
                onRequestRemove: { photo in photoPendingRemoval = photo }
            )
        case .titleCategory: TitleCategoryStep(viewModel: viewModel)
        case .conditionDescription: ConditionDescriptionStep(viewModel: viewModel)
        case .price: PriceStep(viewModel: viewModel)
        case .location: LocationStep(viewModel: viewModel)
        case .review: ReviewStep(viewModel: viewModel)
        case .success: SuccessStep()
        }
    }

    private func restoreIfNeeded() {
        guard !hasRestored else { return }
        hasRestored = true
        guard let data = storedForm.data(using: .utf8),
              let snapshot = try? JSONDecoder().decode(ListingComposeFormState.self, from: data)
        else { return }
        viewModel.restore(from: snapshot)
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(viewModel.form),
              let json = String(data: data, encoding: .utf8)
        else { return }
        storedForm = json
    }

    private func handle(_ event: ListingComposeOutboundEvent?) {
        guard let event else { return }
        switch event {
        case .dismiss:
            storedForm = ""
            dismiss()
        case let .openListingDetail(listingId):
            storedForm = ""
            onOpenListingDetail(listingId)
        }
        viewModel.pendingEvent = nil
    }
}

// MARK: - Step 1: Photos

private struct PhotosStep: View {
    @Bindable var viewModel: ListingComposeWizardViewModel
    let onRequestRemove: (ListingComposePhoto) -> Void

    private let columns: [GridItem] = [
        GridItem(.flexible(), spacing: Spacing.s3),
        GridItem(.flexible(), spacing: Spacing.s3)
    ]

    var body: some View {
        HeadlineBlock("Add photos")
        SubcopyBlock(
            "Show your item in good light. The first photo becomes the hero — long-press a tile to reorder, tap to remove."
        )
        LazyVGrid(columns: columns, spacing: Spacing.s3) {
            ForEach(Array(viewModel.form.photos.enumerated()), id: \.element.id) { index, photo in
                PhotoTile(
                    index: index,
                    photo: photo,
                    onTap: { onRequestRemove(photo) },
                    onMoveUp: index > 0 ? { viewModel.movePhoto(from: index, to: index - 1) } : nil,
                    onMoveDown: index < viewModel.form.photos.count - 1
                        ? { viewModel.movePhoto(from: index, to: index + 1) }
                        : nil,
                    onMakeHero: index > 0 ? { viewModel.makeHero(id: photo.id) } : nil
                )
            }
            if viewModel.form.photos.count < ListingComposeFormState.maxPhotos {
                AddPhotoTile { viewModel.addPhoto() }
            }
        }
        PhotoCountLabel(count: viewModel.form.photos.count)
    }
}

private struct PhotoTile: View {
    let index: Int
    let photo: ListingComposePhoto
    let onTap: () -> Void
    let onMoveUp: (() -> Void)?
    let onMoveDown: (() -> Void)?
    let onMakeHero: (() -> Void)?

    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .fill(Theme.Color.appSurfaceMuted)
                    .aspectRatio(1, contentMode: .fit)
                    .overlay(
                        Icon(.image, size: 32, color: Theme.Color.appTextSecondary)
                    )
                if index == 0 {
                    HeroChip()
                        .padding(Spacing.s2)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("listingCompose_photo_\(index)")
        .accessibilityLabel(
            index == 0
                ? "Photo \(index + 1) of grid. Hero photo. Tap to remove."
                : "Photo \(index + 1) of grid. Tap to remove."
        )
        .accessibilityAddTraits(.isButton)
        .contextMenu {
            if let onMakeHero {
                Button("Make hero", action: onMakeHero)
                    .accessibilityIdentifier("listingCompose_makeHero_\(index)")
            }
            if let onMoveUp {
                Button("Move up", action: onMoveUp)
                    .accessibilityIdentifier("listingCompose_moveUp_\(index)")
            }
            if let onMoveDown {
                Button("Move down", action: onMoveDown)
                    .accessibilityIdentifier("listingCompose_moveDown_\(index)")
            }
        }
    }
}

private struct HeroChip: View {
    var body: some View {
        Text("HERO")
            .pantopusTextStyle(.overline)
            .foregroundStyle(Theme.Color.appTextInverse)
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, Spacing.s1)
            .background(Theme.Color.primary600)
            .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
    }
}

private struct AddPhotoTile: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(
                    Theme.Color.appBorder,
                    style: StrokeStyle(lineWidth: 1, dash: [4, 4])
                )
                .aspectRatio(1, contentMode: .fit)
                .overlay(
                    VStack(spacing: Spacing.s1) {
                        Icon(.camera, size: 28, color: Theme.Color.appTextSecondary)
                        Text("Add photo")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("listingCompose_addPhoto")
        .accessibilityLabel("Add photo")
        .accessibilityAddTraits(.isButton)
    }
}

private struct PhotoCountLabel: View {
    let count: Int

    var body: some View {
        Text("\(count) of \(ListingComposeFormState.maxPhotos) photos")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("listingCompose_photoCount")
    }
}

// MARK: - Step 2: Title + Category

private struct TitleCategoryStep: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        HeadlineBlock("Name it & pick a category")
        SubcopyBlock("Keep the title short and specific — buyers scan in a glance.")
        FormFieldsBlock {
            PantopusTextField(
                "Title",
                text: titleBinding,
                placeholder: "Moving boxes — bundle of 18",
                state: titleFieldState,
                identifier: "listingCompose_title"
            )
            TitleCountLabel(length: viewModel.trimmedTitle.count)
        }
        Text("Category")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
        VStack(spacing: Spacing.s2) {
            ForEach(ListingComposeCategory.allCases, id: \.self) { category in
                CategoryRow(
                    category: category,
                    isSelected: viewModel.form.category == category
                ) {
                    viewModel.setCategory(category)
                }
            }
        }
    }

    private var titleBinding: Binding<String> {
        Binding(
            get: { viewModel.form.title },
            set: { viewModel.setTitle($0) }
        )
    }

    private var titleFieldState: PantopusFieldState {
        let length = viewModel.trimmedTitle.count
        if length == 0 { return .default }
        if length < ListingComposeFormState.titleMinLength {
            return .error("Title must be at least \(ListingComposeFormState.titleMinLength) characters.")
        }
        if length > ListingComposeFormState.titleMaxLength {
            return .error("Title must be at most \(ListingComposeFormState.titleMaxLength) characters.")
        }
        return .valid
    }
}

private struct TitleCountLabel: View {
    let length: Int

    var body: some View {
        Text("\(length)/\(ListingComposeFormState.titleMaxLength)")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .trailing)
    }
}

private struct CategoryRow: View {
    let category: ListingComposeCategory
    let isSelected: Bool
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
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text(category.label)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    Text(category.subtitle)
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
        .accessibilityIdentifier("listingCompose_category_\(category.rawValue)")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

// MARK: - Step 3: Condition + Description

private struct ConditionDescriptionStep: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        HeadlineBlock("Condition & details")
        SubcopyBlock("Buyers want to know what they're getting before they message you.")
        if let category = viewModel.form.category, category.requiresCondition {
            Text("Condition")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)
            VStack(spacing: Spacing.s2) {
                ForEach(ListingComposeCondition.allCases, id: \.self) { condition in
                    ConditionRow(
                        condition: condition,
                        isSelected: viewModel.form.condition == condition
                    ) {
                        viewModel.setCondition(condition)
                    }
                }
            }
        }
        Text("Description")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
        VStack(alignment: .leading, spacing: Spacing.s1) {
            TextEditor(text: bodyBinding)
                .frame(minHeight: 128)
                .padding(Spacing.s2)
                .background(Theme.Color.appSurface)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .stroke(descriptionBorderColor, lineWidth: 1)
                )
                .accessibilityIdentifier("listingCompose_description")
            DescriptionCountLabel(length: viewModel.trimmedDescription.count)
        }
    }

    private var bodyBinding: Binding<String> {
        Binding(
            get: { viewModel.form.bodyText },
            set: { viewModel.setBody($0) }
        )
    }

    private var descriptionBorderColor: Color {
        let length = viewModel.trimmedDescription.count
        if length == 0 { return Theme.Color.appBorder }
        if length < ListingComposeFormState.descriptionMinLength { return Theme.Color.error }
        if length > ListingComposeFormState.descriptionMaxLength { return Theme.Color.error }
        return Theme.Color.appBorder
    }
}

private struct ConditionRow: View {
    let condition: ListingComposeCondition
    let isSelected: Bool
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
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text(condition.label)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    Text(condition.subtitle)
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
        .accessibilityIdentifier("listingCompose_condition_\(condition.rawValue)")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

private struct DescriptionCountLabel: View {
    let length: Int

    var body: some View {
        let bounds = "\(length)/\(ListingComposeFormState.descriptionMaxLength)"
        let needsMore = length > 0 && length < ListingComposeFormState.descriptionMinLength
        HStack {
            if needsMore {
                Text("At least \(ListingComposeFormState.descriptionMinLength) characters")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
            }
            Spacer()
            Text(bounds)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }
}

// MARK: - Step 4: Price

private struct PriceStep: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        HeadlineBlock("Pricing & fulfillment")
        SubcopyBlock("Choose how to price it and how the buyer will receive it.")
        Text("Pricing")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
        VStack(spacing: Spacing.s2) {
            ForEach(ListingComposePriceKind.allCases, id: \.self) { kind in
                PriceKindRow(
                    kind: kind,
                    isSelected: viewModel.form.priceKind == kind
                ) {
                    viewModel.setPriceKind(kind)
                }
            }
        }
        if viewModel.form.priceKind == .fixed || viewModel.form.priceKind == .negotiable {
            FormFieldsBlock {
                PantopusTextField(
                    "Amount (USD)",
                    text: amountBinding,
                    placeholder: "0.00",
                    state: amountFieldState,
                    keyboardType: .decimalPad,
                    identifier: "listingCompose_priceAmount"
                )
            }
        }
        Text("Fulfillment")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
        VStack(spacing: Spacing.s2) {
            ForEach(ListingComposeFulfillment.allCases, id: \.self) { kind in
                FulfillmentRow(
                    kind: kind,
                    isSelected: viewModel.form.fulfillment == kind
                ) {
                    viewModel.setFulfillment(kind)
                }
            }
        }
    }

    private var amountBinding: Binding<String> {
        Binding(
            get: { viewModel.form.priceAmount },
            set: { viewModel.setPriceAmount($0) }
        )
    }

    private var amountFieldState: PantopusFieldState {
        if viewModel.form.priceAmount.isEmpty { return .default }
        guard let value = viewModel.parsedPrice, value > 0 else {
            return .error("Enter an amount greater than zero.")
        }
        return .valid
    }
}

private struct PriceKindRow: View {
    let kind: ListingComposePriceKind
    let isSelected: Bool
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
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text(kind.label)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    Text(kind.subtitle)
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
        .accessibilityIdentifier("listingCompose_priceKind_\(kind.rawValue)")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

private struct FulfillmentRow: View {
    let kind: ListingComposeFulfillment
    let isSelected: Bool
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
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text(kind.label)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    Text(kind.subtitle)
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
        .accessibilityIdentifier("listingCompose_fulfillment_\(kind.rawValue)")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

// MARK: - Step 5: Location

private struct LocationStep: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        HeadlineBlock("Where will the handoff happen?")
        SubcopyBlock(
            "Your exact address is only shared with the buyer after both sides commit."
        )
        VStack(spacing: Spacing.s2) {
            ForEach(ListingComposeLocationKind.allCases, id: \.self) { kind in
                LocationKindRow(
                    kind: kind,
                    isSelected: viewModel.form.locationKind == kind
                ) {
                    viewModel.setLocationKind(kind)
                }
            }
        }
        if viewModel.form.locationKind == .meetPoint {
            FormFieldsBlock {
                PantopusTextField(
                    "Meet point name",
                    text: locationLabelBinding,
                    placeholder: "Lincoln Park bandshell",
                    identifier: "listingCompose_locationLabel"
                )
            }
        }
    }

    private var locationLabelBinding: Binding<String> {
        Binding(
            get: { viewModel.form.locationLabel },
            set: { viewModel.setLocationLabel($0) }
        )
    }
}

private struct LocationKindRow: View {
    let kind: ListingComposeLocationKind
    let isSelected: Bool
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
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text(kind.label)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    Text(kind.subtitle)
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
        .accessibilityIdentifier("listingCompose_locationKind_\(kind.rawValue)")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

// MARK: - Step 6: Review

private struct ReviewStep: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        HeadlineBlock("Review & list")
        SubcopyBlock("Take one last look — you can edit after listing.")
        ReviewSummaryBlock(rows)
    }

    private var rows: [ReviewSummaryRow] {
        var summary: [ReviewSummaryRow] = [
            ReviewSummaryRow(label: "Photos", value: photoSummary),
            ReviewSummaryRow(label: "Title", value: viewModel.trimmedTitle),
            ReviewSummaryRow(label: "Category", value: viewModel.form.category?.label ?? "—")
        ]
        if let condition = viewModel.form.condition {
            summary.append(ReviewSummaryRow(label: "Condition", value: condition.label))
        }
        summary.append(
            ReviewSummaryRow(
                label: "Description",
                value: viewModel.trimmedDescription
            )
        )
        summary.append(ReviewSummaryRow(label: "Price", value: priceSummary))
        summary.append(ReviewSummaryRow(label: "Fulfillment", value: viewModel.form.fulfillment.label))
        summary.append(ReviewSummaryRow(label: "Location", value: locationSummary))
        return summary
    }

    private var photoSummary: String {
        let count = viewModel.form.photos.count
        if count == 0 { return "0 photos" }
        return "\(count) photo\(count == 1 ? "" : "s") (hero first)"
    }

    private var priceSummary: String {
        guard let kind = viewModel.form.priceKind else { return "—" }
        switch kind {
        case .free: return "Free"
        case .fixed:
            return viewModel.form.priceAmount.isEmpty ? "—" : "$\(viewModel.form.priceAmount)"
        case .negotiable:
            return viewModel.form.priceAmount.isEmpty
                ? "Open to offers"
                : "$\(viewModel.form.priceAmount) · open to offers"
        }
    }

    private var locationSummary: String {
        guard let kind = viewModel.form.locationKind else { return "—" }
        switch kind {
        case .savedAddress: return kind.label
        case .meetPoint:
            return viewModel.form.locationLabel.isEmpty
                ? kind.label
                : "\(kind.label) · \(viewModel.form.locationLabel)"
        }
    }
}

// MARK: - Success

private struct SuccessStep: View {
    var body: some View {
        SuccessHeroBlock(
            headline: "Your listing is live",
            subcopy: "Neighbors can find it in Marketplace now. We'll notify you when an offer comes in."
        )
    }
}

// MARK: - Error banner

private struct ListingComposeErrorBanner: View {
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
        .accessibilityIdentifier("listingComposeErrorBanner")
    }
}

#Preview {
    ListingComposeWizardView { _ in }
}
