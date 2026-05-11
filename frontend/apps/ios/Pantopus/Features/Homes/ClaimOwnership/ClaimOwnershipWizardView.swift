//
//  ClaimOwnershipWizardView.swift
//  Pantopus
//
//  Wraps `WizardShell` with the three claim-ownership steps.
//

import SwiftUI

@MainActor
public struct ClaimOwnershipWizardView: View {
    @State private var viewModel: ClaimOwnershipWizardViewModel
    private let onClose: @MainActor () -> Void
    private let onOpenClaimsList: @MainActor () -> Void

    public init(
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
        .accessibilityIdentifier("claimOwnershipWizard")
    }

    @ViewBuilder
    private var stepContent: some View {
        switch viewModel.currentStep {
        case .start:
            ClaimStartStep()
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
