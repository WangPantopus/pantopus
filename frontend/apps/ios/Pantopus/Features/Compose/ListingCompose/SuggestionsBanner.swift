//
//  SuggestionsBanner.swift
//  Pantopus
//
//  A12.9 Snap-and-Sell review components: photo strip, AI suggestions
//  banner, editable suggested fields, comp-range track, condition
//  segmented control, and pickup / delivery toggles.
//

import SwiftUI

// swiftlint:disable file_length

struct ListingComposeSnapReviewStep: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        ListingComposeIdentityChip()
        HeadlineBlock("Review your listing")
        SubcopyBlock(
            "We pulled title, category, and price from your photos. Edit anything that looks off."
        )
        ListingComposePhotoStrip(photos: viewModel.form.photos)
        SuggestionsBanner()
        SuggestedTitleField(viewModel: viewModel)
        SuggestedCategoryField(viewModel: viewModel)
        SuggestedPriceField(viewModel: viewModel)
        SuggestedConditionControl(viewModel: viewModel)
        PickupDeliveryPanel(viewModel: viewModel)
    }
}

private struct ListingComposeIdentityChip: View {
    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(.user, size: 11, color: Theme.Color.personal)
            Text("Personal · You")
        }
        .font(.system(size: 10.5, weight: .bold))
        .foregroundStyle(Theme.Color.personal)
        .textCase(.uppercase)
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s1)
        .background(Theme.Color.personalBg)
        .clipShape(Capsule())
        .accessibilityIdentifier("listingComposeIdentityChip")
    }
}

private struct ListingComposePhotoStrip: View {
    let photos: [ListingComposePhoto]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack {
                Text("Photos · \(photos.count) of \(ListingComposeFormState.maxPhotos)")
                    .pantopusTextStyle(.overline)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                HStack(spacing: Spacing.s1) {
                    Icon(.sparkles, size: 11, color: Theme.Color.success)
                    Text("Good lighting")
                        .font(.system(size: 10.5, weight: .semibold))
                }
                .foregroundStyle(Theme.Color.success)
                .padding(.horizontal, Spacing.s2)
                .padding(.vertical, 3)
                .background(Theme.Color.successLight)
                .clipShape(Capsule())
            }
            PhotoStripGrid(photos: photos)
        }
        .accessibilityIdentifier("listingComposePhotoStrip")
    }
}

private struct PhotoStripGrid: View {
    let photos: [ListingComposePhoto]

    var body: some View {
        GeometryReader { proxy in
            let gap: CGFloat = 6
            let smallWidth = (proxy.size.width - gap * 3) / 4
            let heroWidth = smallWidth * 2 + gap
            HStack(spacing: gap) {
                PhotoStripTile(index: 0, isFilled: !photos.isEmpty, isHero: true)
                    .frame(width: heroWidth, height: 168)
                VStack(spacing: gap) {
                    HStack(spacing: gap) {
                        PhotoStripTile(index: 1, isFilled: photos.count > 1)
                        PhotoStripTile(index: 2, isFilled: photos.count > 2)
                    }
                    HStack(spacing: gap) {
                        PhotoStripTile(index: 3, isFilled: photos.count > 3)
                        AddMorePhotoTile()
                    }
                }
                .frame(width: heroWidth, height: 168)
            }
        }
        .frame(height: 168)
    }
}

private struct PhotoStripTile: View {
    let index: Int
    let isFilled: Bool
    var isHero = false

    var body: some View {
        RoundedRectangle(cornerRadius: isHero ? Radii.xl : Radii.lg, style: .continuous)
            .fill(
                isFilled
                    ? LinearGradient(
                        colors: [
                            Color(red: 0.53, green: 0.66, blue: 0.55),
                            Color(red: 0.28, green: 0.39, blue: 0.31)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    : LinearGradient(
                        colors: [Theme.Color.appSurfaceMuted, Theme.Color.appSurfaceMuted],
                        startPoint: .top,
                        endPoint: .bottom
                    )
            )
            .overlay {
                if isFilled {
                    SofaThumbMark()
                        .padding(isHero ? Spacing.s5 : Spacing.s3)
                } else {
                    Icon(.image, size: isHero ? 26 : 20, color: Theme.Color.appTextSecondary)
                }
            }
            .overlay(alignment: .topLeading) {
                if isHero && isFilled {
                    Text("Cover")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(.white)
                        .textCase(.uppercase)
                        .padding(.horizontal, Spacing.s2)
                        .padding(.vertical, 3)
                        .background(.black.opacity(0.56))
                        .clipShape(Capsule())
                        .padding(Spacing.s2)
                }
            }
            .overlay(
                RoundedRectangle(cornerRadius: isHero ? Radii.xl : Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .accessibilityIdentifier("listingComposeSnapPhoto_\(index)")
    }
}

private struct SofaThumbMark: View {
    var body: some View {
        VStack(spacing: Spacing.s0) {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(.white.opacity(0.24))
                .frame(height: 26)
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(.white.opacity(0.18))
                .frame(height: 44)
                .offset(y: -6)
        }
    }
}

private struct AddMorePhotoTile: View {
    var body: some View {
        RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
            .fill(Theme.Color.appSurfaceRaised)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorderStrong, style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
            )
            .overlay {
                VStack(spacing: 2) {
                    Icon(.plus, size: 18, color: Theme.Color.appTextSecondary)
                    Text("Add photo")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
    }
}

struct SuggestionsBanner: View {
    var body: some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(Color(red: 0.49, green: 0.23, blue: 0.93))
                    .frame(width: 28, height: 28)
                Icon(.sparkles, size: 14, strokeWidth: 2.4, color: .white)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("Snap-and-sell suggested everything below")
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(Color(red: 0.49, green: 0.23, blue: 0.93))
                Text("Tap any field to edit. Based on 47 similar comps within 3 mi.")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s0)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, 10)
        .background(Color(red: 0.96, green: 0.95, blue: 1.0))
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Color(red: 0.87, green: 0.84, blue: 1.0), lineWidth: 1)
        )
        .accessibilityIdentifier("listingComposeSuggestionsBanner")
    }
}

private struct SuggestedTitleField: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        SuggestedFieldShell(
            label: "Title",
            hint: "Snap-and-sell pulled this from the photos"
        ) {
            TextField(
                "Sage green velvet sofa, 3-seater",
                text: Binding(get: { viewModel.form.title }, set: { viewModel.setTitle($0) })
            )
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(Theme.Color.appText)
            Icon(.pencil, size: 14, color: Theme.Color.appTextMuted)
        }
        .accessibilityIdentifier("listingComposeSnapTitle")
    }
}

private struct SuggestedCategoryField: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        SuggestedFieldShell(label: "Category") {
            HStack(spacing: Spacing.s2) {
                ForEach(ListingComposeCategory.allCases, id: \.self) { category in
                    CategoryChip(
                        category: category,
                        isSelected: viewModel.form.category == category
                    ) {
                        viewModel.setCategory(category)
                    }
                }
            }
        }
        .accessibilityIdentifier("listingComposeSnapCategory")
    }
}

private struct CategoryChip: View {
    let category: ListingComposeCategory
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(category.label)
                .font(.system(size: 11.5, weight: .bold))
                .foregroundStyle(isSelected ? Color(red: 0.49, green: 0.23, blue: 0.93) : Theme.Color.appTextSecondary)
                .padding(.horizontal, Spacing.s2)
                .padding(.vertical, 7)
                .background(isSelected ? Color(red: 0.96, green: 0.95, blue: 1.0) : Theme.Color.appSurfaceRaised)
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(isSelected ? Color(red: 0.49, green: 0.23, blue: 0.93) : Theme.Color.appBorder, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("listingComposeSnapCategory_\(category.rawValue)")
    }
}

private struct SuggestedPriceField: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            SuggestedLabel(label: "Price")
            VStack(alignment: .leading, spacing: Spacing.s3) {
                HStack(alignment: .firstTextBaseline, spacing: Spacing.s1) {
                    Text("$")
                        .font(.system(size: 22, weight: .bold))
                    TextField(
                        "280",
                        text: Binding(get: { viewModel.form.priceAmount }, set: { viewModel.setPriceAmount($0) })
                    )
                    .font(.system(size: 28, weight: .bold))
                    .keyboardType(.decimalPad)
                    Spacer()
                    Text("USD · firm")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                PriceCompRangeTrack()
            }
            .padding(Spacing.s3)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
        }
        .accessibilityIdentifier("listingComposeSnapPrice")
    }
}

private struct PriceCompRangeTrack: View {
    var body: some View {
        VStack(spacing: Spacing.s2) {
            GeometryReader { proxy in
                let width = proxy.size.width
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Theme.Color.appSurfaceSunken)
                        .frame(height: 6)
                    Capsule()
                        .fill(Theme.Color.successLight)
                        .frame(width: width * 0.46, height: 6)
                        .offset(x: width * 0.22)
                    Circle()
                        .fill(Theme.Color.primary600)
                        .frame(width: 12, height: 12)
                        .overlay(Circle().stroke(.white, lineWidth: 2))
                        .shadow(color: .black.opacity(0.18), radius: 4, x: 0, y: 1)
                        .offset(x: width * 0.52 - 6)
                }
            }
            .frame(height: 12)
            HStack {
                Text("$180 low")
                Spacer()
                Text("$240–$320 typical")
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.success)
                Spacer()
                Text("$420 high")
            }
            .font(.system(size: 10.5))
            .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .accessibilityIdentifier("listingComposeCompRange")
    }
}

private struct SuggestedConditionControl: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("Condition")
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.appTextSecondary)
            HStack(spacing: Spacing.s1) {
                ForEach(ListingComposeCondition.allCases, id: \.self) { condition in
                    Button {
                        viewModel.setCondition(condition)
                    } label: {
                        Text(shortLabel(for: condition))
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundStyle(viewModel.form.condition == condition ? Theme.Color.primary700 : Theme.Color.appTextStrong)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(viewModel.form.condition == condition ? Theme.Color.primary50 : Theme.Color.appSurface)
                            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                                    .stroke(
                                        viewModel.form.condition == condition ? Theme.Color.primary600 : Theme.Color.appBorder,
                                        lineWidth: viewModel.form.condition == condition ? 1.5 : 1
                                    )
                            )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("listingComposeSnapCondition_\(condition.rawValue)")
                }
            }
            Text("Light wear on one cushion · minor sun fade. Add notes in description.")
                .font(.system(size: 11))
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }

    private func shortLabel(for condition: ListingComposeCondition) -> String {
        switch condition {
        case .new: "New"
        case .likeNew: "Like new"
        case .good: "Good"
        case .fair: "Fair"
        case .forParts: "Parts"
        }
    }
}

private struct PickupDeliveryPanel: View {
    @Bindable var viewModel: ListingComposeWizardViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("Pickup & delivery")
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.appTextSecondary)
            VStack(spacing: Spacing.s0) {
                HStack(spacing: Spacing.s3) {
                    ZStack {
                        RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                            .fill(Theme.Color.primary50)
                            .frame(width: 28, height: 28)
                        Icon(.mapPin, size: 14, color: Theme.Color.primary600)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text("412 Elm St · West Loop")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                        Text("Shown as approximate location to buyers")
                            .font(.system(size: 11))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    Spacer()
                    Icon(.chevronRight, size: 14, color: Theme.Color.appTextMuted)
                }
                .padding(Spacing.s3)
                Divider()
                VStack(spacing: Spacing.s2) {
                    FulfillmentToggleRow(
                        icon: .handCoins,
                        title: "Local pickup",
                        subtitle: "Buyers come to you",
                        isOn: viewModel.form.fulfillment == .pickup
                    ) {
                        viewModel.setFulfillment(.pickup)
                    }
                    FulfillmentToggleRow(
                        icon: .package,
                        title: "Local delivery",
                        subtitle: "Up to 3 mi · $40 fee",
                        isOn: viewModel.form.deliveryEnabled
                    ) {
                        viewModel.setDeliveryEnabled(!viewModel.form.deliveryEnabled)
                    }
                    FulfillmentToggleRow(
                        icon: .package,
                        title: "Ship nationwide",
                        subtitle: "Too large to ship",
                        isOn: false,
                        isDisabled: true
                    ) {}
                }
                .padding(Spacing.s3)
            }
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
        }
        .accessibilityIdentifier("listingComposePickupDelivery")
    }
}

private struct FulfillmentToggleRow: View {
    let icon: PantopusIcon
    let title: String
    let subtitle: String
    let isOn: Bool
    var isDisabled = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.s3) {
                Icon(icon, size: 15, color: Theme.Color.appTextSecondary)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title)
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(subtitle)
                        .font(.system(size: 10.5))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer()
                TogglePill(isOn: isOn)
            }
            .opacity(isDisabled ? 0.55 : 1)
        }
        .disabled(isDisabled)
        .buttonStyle(.plain)
    }
}

private struct TogglePill: View {
    let isOn: Bool

    var body: some View {
        Capsule()
            .fill(isOn ? Theme.Color.primary600 : Theme.Color.appBorderStrong)
            .frame(width: 32, height: 18)
            .overlay(alignment: isOn ? .trailing : .leading) {
                Circle()
                    .fill(.white)
                    .frame(width: 14, height: 14)
                    .padding(2)
                    .shadow(color: .black.opacity(0.18), radius: 2, x: 0, y: 1)
            }
    }
}

private struct SuggestedFieldShell<Content: View>: View {
    let label: String
    var hint: String?
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            SuggestedLabel(label: label)
            HStack(spacing: Spacing.s2) {
                content
            }
            .padding(.horizontal, Spacing.s3)
            .frame(minHeight: 44)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            if let hint {
                Text(hint)
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }
}

private struct SuggestedLabel: View {
    let label: String

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.system(size: 10.5, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .textCase(.uppercase)
            Spacer()
            HStack(spacing: 3) {
                Icon(.sparkles, size: 10, color: Color(red: 0.49, green: 0.23, blue: 0.93))
                Text("AI suggested")
                    .font(.system(size: 10, weight: .semibold))
            }
            .foregroundStyle(Color(red: 0.49, green: 0.23, blue: 0.93))
        }
    }
}
