//
//  PostGigV1View.swift
//  Pantopus
//
//  A13.8 — legacy V1 single-screen gig composer.
//

import SwiftUI

public struct PostGigV1View: View {
    @State private var viewModel: PostGigV1ViewModel
    private let onClose: @MainActor () -> Void
    private let onPosted: @MainActor (String) -> Void

    public init(
        viewModel: PostGigV1ViewModel = PostGigV1ViewModel(),
        onClose: @escaping @MainActor () -> Void = {},
        onPosted: @escaping @MainActor (String) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onClose = onClose
        self.onPosted = onPosted
    }

    public var body: some View {
        FormShell(
            title: "Post gig",
            leading: .back,
            rightActionLabel: "Post",
            isValid: viewModel.canAttemptSubmit,
            isDirty: viewModel.isPostEnabled,
            isSaving: viewModel.state.isSubmitting,
            onClose: onClose,
            onCommit: commit
        ) {
            content
        }
        .toolbar(.hidden, for: .tabBar)
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("postGigV1")
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state.loadState {
        case .loading:
            PostGigV1LoadingView()
        case .empty:
            PostGigV1EmptyView { viewModel.startFromEmpty() }
        case let .error(message):
            PostGigV1FatalErrorView(message: message) { viewModel.retry() }
        case .ready:
            formContent
        }
    }

    @ViewBuilder private var formContent: some View {
        if !viewModel.state.validationErrors.isEmpty {
            PostGigV1ErrorBanner(errors: viewModel.state.validationErrors)
                .padding(.horizontal, Spacing.s4)
        }

        FormFieldGroup("Category") {
            PostGigV1CategoryField(
                selected: viewModel.state.form.category,
                error: viewModel.error(for: .category),
                onSelect: viewModel.updateCategory
            )
        }

        FormFieldGroup("Details") {
            PantopusTextField(
                "Title",
                text: Binding(
                    get: { viewModel.state.form.title },
                    set: { viewModel.updateTitle($0) }
                ),
                placeholder: "Help moving a sofa up 3 flights",
                state: fieldState(.title),
                isRequired: true,
                identifier: "postGigV1_title"
            )

            PostGigV1DescriptionField(
                text: Binding(
                    get: { viewModel.state.form.description },
                    set: { viewModel.updateDescription($0) }
                ),
                error: viewModel.error(for: .description)
            )
        }

        FormFieldGroup("Pay") {
            PostGigV1PriceField(
                value: Binding(
                    get: { viewModel.state.form.price },
                    set: { viewModel.updatePrice($0) }
                ),
                unit: viewModel.state.form.priceType.unitLabel,
                error: viewModel.error(for: .price)
            )

            PostGigV1RadioRow(
                selected: viewModel.state.form.priceType,
                onSelect: viewModel.updatePriceType
            )
        }

        FormFieldGroup("When") {
            PostGigV1DateField(
                date: Binding(
                    get: { viewModel.state.form.scheduledAt },
                    set: { viewModel.updateScheduledAt($0) }
                ),
                label: viewModel.dateLabel(for: viewModel.state.form.scheduledAt),
                error: viewModel.error(for: .dateTime)
            )
        }

        FormFieldGroup("Photos") {
            PostGigV1PhotosGrid(
                photos: viewModel.state.form.photos,
                canAdd: viewModel.state.form.photos.count < PostGigV1SampleData.maxPhotos,
                onAdd: viewModel.addPlaceholderPhoto,
                onRemove: viewModel.removePhoto
            )
        }

        FormFieldGroup("Location") {
            PantopusTextField(
                "Location",
                text: Binding(
                    get: { viewModel.state.form.location },
                    set: { viewModel.updateLocation($0) }
                ),
                placeholder: "Pearl District · NW 11th & Johnson",
                state: fieldState(.location),
                isRequired: true,
                identifier: "postGigV1_location"
            )
        }

        PostGigV1LegacyStamp()
    }

    private func fieldState(_ field: PostGigV1Field) -> PantopusFieldState {
        if let error = viewModel.error(for: field) {
            return .error(error)
        }
        return .default
    }

    private func commit() {
        Task {
            if let id = await viewModel.submit() {
                onPosted(id)
            }
        }
    }
}

private struct PostGigV1CategoryField: View {
    let selected: GigsCategory
    let error: String?
    let onSelect: (GigsCategory) -> Void

    private var valueLabel: String {
        selected == .moving ? "Moving & hauling" : selected.label
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            PostGigV1FieldLabel("Category", required: true)
            Menu {
                ForEach(GigsCategory.allCases.filter { $0 != .all }, id: \.self) { category in
                    Button(category == .moving ? "Moving & hauling" : category.label) {
                        onSelect(category)
                    }
                }
            } label: {
                HStack(spacing: Spacing.s2) {
                    Text(selected == .all ? "Choose a category" : valueLabel)
                        .pantopusTextStyle(.small)
                        .fontWeight(selected == .all ? .regular : .medium)
                        .foregroundStyle(selected == .all ? Theme.Color.appTextMuted : Theme.Color.appText)
                    Spacer()
                    Icon(.chevronDown, size: 16, color: Theme.Color.appTextSecondary)
                }
                .frame(minHeight: 44)
                .padding(.horizontal, Spacing.s3)
                .background(Theme.Color.appSurface)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .stroke(error == nil ? Theme.Color.appBorder : Theme.Color.error, lineWidth: error == nil ? 1 : 1.5)
                )
            }
            .accessibilityLabel("Category")
            .accessibilityIdentifier("postGigV1_category")
            if let error {
                PostGigV1InlineError(message: error)
            }
        }
    }
}

private struct PostGigV1DescriptionField: View {
    @Binding var text: String
    let error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            PostGigV1FieldLabel("Description", required: true)
            ZStack(alignment: .topLeading) {
                if text.isEmpty {
                    Text("Tell neighbors what to carry, where to meet, and any stairs or timing constraints.")
                        .pantopusTextStyle(.small)
                        .foregroundStyle(Theme.Color.appTextMuted)
                        .padding(.horizontal, Spacing.s3)
                        .padding(.vertical, Spacing.s3)
                        .allowsHitTesting(false)
                }
                TextEditor(text: $text)
                    .font(Theme.Font.small)
                    .foregroundStyle(Theme.Color.appText)
                    .frame(minHeight: 108)
                    .padding(Spacing.s2)
                    .scrollContentBackground(.hidden)
                    .background(Theme.Color.appSurface)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                            .stroke(error == nil ? Theme.Color.appBorder : Theme.Color.error, lineWidth: error == nil ? 1 : 1.5)
                    )
                    .accessibilityLabel(error.map { "Description, error: \($0)" } ?? "Description")
                    .accessibilityIdentifier("postGigV1_description")
            }
            HStack {
                if let error {
                    PostGigV1InlineError(message: error)
                }
                Spacer()
                Text("\(text.count) / \(PostGigV1SampleData.descriptionMaxLength)")
                    .font(.system(size: 11, weight: .regular, design: .monospaced))
                    .foregroundStyle(Theme.Color.appTextMuted)
                    .accessibilityIdentifier("postGigV1_descriptionCount")
            }
        }
    }
}

private struct PostGigV1PriceField: View {
    @Binding var value: String
    let unit: String?
    let error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            PostGigV1FieldLabel("Price", required: true)
            HStack(spacing: Spacing.s2) {
                Text("$")
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(value.isEmpty ? Theme.Color.appTextMuted : Theme.Color.appTextStrong)
                TextField("0", text: $value)
                    .keyboardType(.decimalPad)
                    .font(Theme.Font.body)
                    .fontWeight(value.isEmpty ? .regular : .semibold)
                    .foregroundStyle(Theme.Color.appText)
                    .accessibilityLabel(error.map { "Price, error: \($0)" } ?? "Price")
                    .accessibilityIdentifier("postGigV1_price")
                if let unit {
                    Text(unit)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .padding(.leading, Spacing.s2)
                        .overlay(alignment: .leading) {
                            Rectangle()
                                .fill(Theme.Color.appBorderSubtle)
                                .frame(width: 1)
                        }
                }
            }
            .frame(minHeight: 44)
            .padding(.horizontal, Spacing.s3)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(error == nil ? Theme.Color.appBorder : Theme.Color.error, lineWidth: error == nil ? 1 : 1.5)
            )
            if let error {
                PostGigV1InlineError(message: error)
            }
        }
    }
}

private struct PostGigV1RadioRow: View {
    let selected: PostGigV1PriceType
    let onSelect: (PostGigV1PriceType) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            PostGigV1FieldLabel("Price type", required: false)
            HStack(spacing: Spacing.s5) {
                ForEach(PostGigV1PriceType.allCases) { type in
                    Button {
                        onSelect(type)
                    } label: {
                        HStack(spacing: Spacing.s2) {
                            ZStack {
                                Circle()
                                    .stroke(
                                        type == selected ? Theme.Color.primary600 : Theme.Color.appBorderStrong,
                                        lineWidth: type == selected ? 5 : 1.5
                                    )
                                    .frame(width: 18, height: 18)
                            }
                            Text(type.label)
                                .pantopusTextStyle(.caption)
                                .fontWeight(type == selected ? .semibold : .medium)
                                .foregroundStyle(Theme.Color.appText)
                        }
                        .frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(type.label) price")
                    .accessibilityAddTraits(type == selected ? [.isSelected] : [])
                    .accessibilityIdentifier("postGigV1_priceType_\(type.rawValue)")
                }
            }
        }
    }
}

private struct PostGigV1DateField: View {
    @Binding var date: Date
    let label: String
    let error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            PostGigV1FieldLabel("Date & time", required: true)
            HStack(spacing: Spacing.s3) {
                Icon(.calendar, size: 16, color: Theme.Color.appTextSecondary)
                DatePicker(
                    label,
                    selection: $date,
                    displayedComponents: [.date, .hourAndMinute]
                )
                .labelsHidden()
                .datePickerStyle(.compact)
                .tint(Theme.Color.primary600)
                .frame(maxWidth: .infinity, alignment: .leading)
                Icon(.chevronDown, size: 14, color: Theme.Color.appTextMuted)
            }
            .frame(minHeight: 44)
            .padding(.horizontal, Spacing.s3)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(error == nil ? Theme.Color.appBorder : Theme.Color.error, lineWidth: error == nil ? 1 : 1.5)
            )
            .accessibilityLabel(error.map { "Date and time, error: \($0)" } ?? "Date and time")
            .accessibilityIdentifier("postGigV1_dateTime")
            if let error {
                PostGigV1InlineError(message: error)
            }
        }
    }
}

private struct PostGigV1PhotosGrid: View {
    let photos: [PostGigV1Photo]
    let canAdd: Bool
    let onAdd: () -> Void
    let onRemove: (String) -> Void

    private let columns = Array(repeating: GridItem(.flexible(), spacing: Spacing.s2), count: 4)

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s1) {
                PostGigV1FieldLabel("Photos", required: false)
                Text("(up to \(PostGigV1SampleData.maxPhotos))")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
            LazyVGrid(columns: columns, spacing: Spacing.s2) {
                ForEach(Array(photos.enumerated()), id: \.element.id) { index, photo in
                    PostGigV1PhotoTile(photo: photo, isCover: index == 0) {
                        onRemove(photo.id)
                    }
                }
                if canAdd {
                    Button(action: onAdd) {
                        VStack(spacing: Spacing.s1) {
                            Icon(.plus, size: 18, color: Theme.Color.appTextSecondary)
                            Text("Add")
                                .pantopusTextStyle(.caption)
                                .fontWeight(.semibold)
                                .foregroundStyle(Theme.Color.appTextSecondary)
                        }
                        .frame(maxWidth: .infinity)
                        .aspectRatio(1, contentMode: .fit)
                        .background(Theme.Color.appSurfaceMuted)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                                .stroke(Theme.Color.appBorderStrong, style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Add photo")
                    .accessibilityIdentifier("postGigV1_addPhoto")
                }
            }
            Text(photos.isEmpty ? "Photos help your gig get picked up faster." : "First photo is the cover. Tap remove to delete.")
                .pantopusTextStyle(.caption)
                .italic()
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityIdentifier("postGigV1_photoHint")
        }
    }
}

private struct PostGigV1PhotoTile: View {
    let photo: PostGigV1Photo
    let isCover: Bool
    let onRemove: () -> Void

    private var fill: Color {
        switch photo.tone {
        case .sofa: Theme.Color.primary100
        case .stairs: Theme.Color.homeBg
        case .street: Theme.Color.businessBg
        case .neutral: Theme.Color.appSurfaceSunken
        }
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .fill(fill)
                .overlay {
                    Icon(.image, size: 20, color: Theme.Color.appTextSecondary)
                }
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .aspectRatio(1, contentMode: .fit)

            if isCover {
                Text("Cover")
                    .pantopusTextStyle(.overline)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s1)
                    .padding(.vertical, 2)
                    .background(Theme.Color.appText.opacity(0.78))
                    .clipShape(RoundedRectangle(cornerRadius: Radii.xs, style: .continuous))
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .padding(Spacing.s1)
                    .accessibilityHidden(true)
            }

            Button(action: onRemove) {
                Icon(.x, size: 12, color: Theme.Color.appTextInverse)
                    .frame(width: 24, height: 24)
                    .background(Theme.Color.appText.opacity(0.72))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .padding(5)
            .accessibilityLabel(isCover ? "Remove cover photo" : "Remove photo")
            .accessibilityIdentifier("postGigV1_removePhoto_\(photo.id)")
        }
    }
}

#Preview("Filled") {
    PostGigV1View(viewModel: PostGigV1SampleData.filledViewModel())
}

#Preview("Validation errors") {
    PostGigV1View(viewModel: PostGigV1SampleData.validationErrorViewModel())
}
