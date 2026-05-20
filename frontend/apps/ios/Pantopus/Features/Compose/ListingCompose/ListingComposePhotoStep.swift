//
//  ListingComposePhotoStep.swift
//  Pantopus
//
//  Photo picker step components for the Snap & Sell listing wizard.
//

import SwiftUI

struct ListingComposePhotosStep: View {
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
