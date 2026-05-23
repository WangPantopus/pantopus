//
//  ClaimOwnershipWizardView.swift
//  Pantopus
//
//  Wraps `WizardShell` with the three claim-ownership steps.
//

import SwiftUI

/// Concrete claim-ownership wizard view. Mirrors `AddHomeWizardView` —
/// owns a `ClaimOwnershipWizardViewModel` instance, renders the active
/// step inside the shared `WizardShell`, and dispatches outbound events
/// (`dismiss` / `openClaimsList`) to the host nav stack.
@MainActor
public struct ClaimOwnershipWizardView: View {
    @State private var viewModel: ClaimOwnershipWizardViewModel
    private let onClose: @MainActor () -> Void
    private let onOpenClaimsList: @MainActor () -> Void

    init(
        homeId: String,
        api: APIClient = .shared,
        uploader: MultipartUploader = .shared,
        onClose: @escaping @MainActor () -> Void,
        onOpenClaimsList: @escaping @MainActor () -> Void
    ) {
        _viewModel = State(initialValue: ClaimOwnershipWizardViewModel(
            homeId: homeId,
            api: api,
            uploader: uploader
        ))
        self.onClose = onClose
        self.onOpenClaimsList = onOpenClaimsList
    }

    public var body: some View {
        WizardShell(model: viewModel) {
            stepContent
        }
        .onChange(of: viewModel.pendingEvent) { _, event in
            handle(event)
        }
        .onChange(of: viewModel.currentStep) { _, step in
            Analytics.track(.screenClaimOwnershipStepViewed(stepName: step.rawValue))
        }
        .onAppear {
            Analytics.track(.screenClaimOwnershipStepViewed(stepName: viewModel.currentStep.rawValue))
        }
        .accessibilityIdentifier("claimOwnershipWizard")
    }

    @ViewBuilder
    private var stepContent: some View {
        switch viewModel.currentStep {
        case .start:
            ClaimStartStep(content: viewModel.startContent)
        case .upload:
            ClaimUploadStep(viewModel: viewModel)
        case .success:
            ClaimSuccessStep()
        }
    }

    private func handle(_ event: ClaimOwnershipOutboundEvent?) {
        guard let event else { return }
        switch event {
        case .dismiss:
            onClose()
        case .openClaimsList:
            onOpenClaimsList()
        }
        viewModel.acknowledgePendingEvent()
    }
}

#Preview {
    ClaimOwnershipWizardView(
        homeId: "home-preview",
        onClose: {},
        onOpenClaimsList: {}
    )
}
