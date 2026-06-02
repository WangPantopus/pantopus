//
//  GigDetailView.swift
//  Pantopus
//
//  T2.6 gig detail. Wraps `TransactionalDetailShell`. The primary dock
//  action opens the shared `EditBidSheetView` in place-bid mode; the
//  secondary opens a placeholder message thread.
//

import SwiftUI

public struct GigDetailView: View {
    @State private var viewModel: GigDetailViewModel
    @State private var bidSheetTarget: EditBidSheetTarget?
    @State private var deliveryTarget: DeliveryProofTarget?
    @State private var toast: ToastMessage?
    private let onBack: @MainActor () -> Void
    private let onMessage: (@MainActor (GigDTO) -> Void)?

    public init(
        viewModel: GigDetailViewModel,
        onBack: @escaping @MainActor () -> Void = {},
        onMessage: (@MainActor (GigDTO) -> Void)? = nil
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
        self.onMessage = onMessage
    }

    public var body: some View {
        TransactionalDetailShell(
            state: viewModel.state,
            onBack: onBack,
            onPrimaryAction: { presentPrimaryAction() },
            onSecondaryAction: { if let gig = viewModel.rawGig { onMessage?(gig) } },
            onRetry: { Task { await viewModel.load() } },
            onMessageCounterparty: { if let gig = viewModel.rawGig { onMessage?(gig) } }
        )
        .task { await viewModel.load() }
        .sheet(item: $bidSheetTarget) { target in
            EditBidSheetView(
                target: target,
                onSubmit: { draft in
                    let ok = await viewModel.placeBid(
                        amount: draft.amount,
                        message: draft.message,
                        proposedTime: draft.proposedTime
                    )
                    if ok {
                        toast = ToastMessage(text: "Bid submitted.", kind: .success)
                    }
                    return ok
                },
                onCancel: { bidSheetTarget = nil }
            )
            .presentationDetents([.large])
        }
        .sheet(item: $deliveryTarget) { target in
            DeliveryProofSheetView(
                target: target,
                onSubmit: { photos, note in
                    await viewModel.submitDeliveryProof(photos: photos, note: note)
                },
                onDismiss: { deliveryTarget = nil }
            )
        }
        .overlay(alignment: .bottom) { toastOverlay }
    }

    @ViewBuilder private var toastOverlay: some View {
        if let toast {
            ToastView(message: toast)
                .padding(.bottom, Spacing.s8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .task(id: toast) {
                    try? await Task.sleep(nanoseconds: 2_500_000_000)
                    self.toast = nil
                }
                .accessibilityIdentifier("gig-detail-toast")
        }
    }

    /// Dock primary routes to the Delivery Proof sheet when the viewer is
    /// the assigned worker on an in-progress task, otherwise the bid sheet.
    private func presentPrimaryAction() {
        if viewModel.canMarkDelivered {
            presentDeliveryProof()
        } else {
            presentBidSheet()
        }
    }

    private func presentBidSheet() {
        guard let gig = viewModel.rawGig else { return }
        bidSheetTarget = EditBidSheetTarget(
            id: "new-bid-\(gig.id)",
            gigId: gig.id,
            gigTitle: gig.title,
            bidId: nil
        )
    }

    private func presentDeliveryProof() {
        guard let gig = viewModel.rawGig else { return }
        deliveryTarget = DeliveryProofTarget(
            id: "deliver-\(gig.id)",
            gigId: gig.id,
            gigTitle: gig.title
        )
    }
}
