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
    @State private var showTipSheet = false
    @State private var toast: ToastMessage?
    private let onBack: @MainActor () -> Void
    private let onMessage: (@MainActor (GigDTO) -> Void)?

    /// Block 3D — preset tip amounts in cents.
    private let tipPresets = [500, 1000, 2000]

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
        .sheet(isPresented: $showTipSheet) { tipSheet }
        .overlay(alignment: .bottom) { toastOverlay }
        .overlay(alignment: .top) { tipMarkers }
        .onChange(of: viewModel.tipStatus) { _, status in handleTip(status) }
    }

    // MARK: - Tip (Block 3D)

    private var tipSheet: some View {
        VStack(spacing: Spacing.s4) {
            Icon(.handCoins, size: 32, color: Theme.Color.primary600)
            Text("Send a tip")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text("100% goes to your helper. Charged to your card via Stripe.")
                .font(.system(size: 13))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            HStack(spacing: Spacing.s3) {
                ForEach(tipPresets, id: \.self) { cents in
                    Button { selectTip(cents) } label: {
                        Text("$\(cents / 100)")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(Theme.Color.primary600)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Theme.Color.primary50)
                            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("tip.amount.\(cents)")
                }
            }
            Button("Not now") { showTipSheet = false }
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .buttonStyle(.plain)
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity)
        .presentationDetents([.height(240)])
        .accessibilityIdentifier("tip.amount")
    }

    /// Zero-size anchors so UI tests can assert each tip stage.
    @ViewBuilder private var tipMarkers: some View {
        if viewModel.canTip {
            Color.clear.frame(width: 0, height: 0).accessibilityIdentifier("tip.affordance")
        }
        if viewModel.tipStatus == .sending {
            Color.clear.frame(width: 0, height: 0).accessibilityIdentifier("tip.paymentSheet")
        }
        if viewModel.tipStatus == .succeeded {
            Color.clear.frame(width: 0, height: 0).accessibilityIdentifier("tip.success")
        }
    }

    private func selectTip(_ cents: Int) {
        showTipSheet = false
        Task { await viewModel.sendTip(amountCents: cents) }
    }

    private func handleTip(_ status: GigDetailViewModel.TipStatus) {
        switch status {
        case .idle, .sending:
            break
        case .succeeded:
            // Keep the .succeeded marker live for tests; the toast fires once.
            toast = ToastMessage(text: "Tip sent — thank you!", kind: .success)
        case .canceled:
            viewModel.clearTipStatus()
        case let .failed(message):
            toast = ToastMessage(text: message, kind: .error)
            viewModel.clearTipStatus()
        }
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

    /// Dock primary routes to: the tip sheet when the poster can tip a
    /// completed gig (Block 3D); the Delivery Proof sheet for the assigned
    /// worker on an in-progress task; otherwise the bid sheet.
    private func presentPrimaryAction() {
        if viewModel.canTip {
            showTipSheet = true
        } else if viewModel.canMarkDelivered {
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
