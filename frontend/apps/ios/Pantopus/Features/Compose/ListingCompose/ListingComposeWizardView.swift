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
            ListingComposePhotosStep(viewModel: viewModel) { photo in
                photoPendingRemoval = photo
            }
        case .titleCategory: ListingComposeTitleCategoryStep(viewModel: viewModel)
        case .conditionDescription: ListingComposeConditionDescriptionStep(viewModel: viewModel)
        case .price: ListingComposePriceStep(viewModel: viewModel)
        case .location: ListingComposeLocationStep(viewModel: viewModel)
        case .review: ListingComposeReviewStep(viewModel: viewModel)
        case .success: ListingComposeSuccessStep()
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
