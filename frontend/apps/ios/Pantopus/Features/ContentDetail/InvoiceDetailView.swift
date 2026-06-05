//
//  InvoiceDetailView.swift
//  Pantopus
//
//  T2.6 invoice detail. Wraps `TransactionalDetailShell` with the same
//  vocabulary as gig + listing. Block 3B wires the "Pay" CTA to the real
//  Stripe PaymentSheet via the view-model's `CheckoutCoordinator`:
//  PaymentSheet (presented by the SDK over the current screen) collects the
//  card + handles SCA/3-D Secure, and the result drives a success / declined /
//  canceled toast. We never mark the invoice paid here — the VM re-reads
//  server state on success.
//

import SwiftUI

public struct InvoiceDetailView: View {
    @State private var viewModel: InvoiceDetailViewModel
    @State private var toast: ToastMessage?
    @State private var isPaying = false
    private let onBack: @MainActor () -> Void

    public init(
        viewModel: InvoiceDetailViewModel,
        onBack: @escaping @MainActor () -> Void = {}
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
    }

    public var body: some View {
        TransactionalDetailShell(
            state: viewModel.state,
            onBack: onBack,
            onPrimaryAction: { pay() },
            onSecondaryAction: nil,
            onRetry: { Task { await viewModel.load() } },
            onMessageCounterparty: nil
        )
        .task { await viewModel.load() }
        // Marks the surface as checkout-enabled (the Stripe sheet itself is
        // presented by the SDK, not a SwiftUI sheet we own).
        .accessibilityIdentifier("checkout.paymentSheet")
        .overlay(alignment: .bottom) { toastOverlay }
        .onChange(of: viewModel.paymentStatus) { _, status in
            handle(status)
        }
    }

    private func pay() {
        guard !isPaying else { return }
        isPaying = true
        Task {
            await viewModel.payNow()
            isPaying = false
        }
    }

    private func handle(_ status: InvoicePaymentStatus) {
        switch status {
        case .idle, .paying:
            break
        case .paid:
            toast = ToastMessage(text: "Payment complete.", kind: .success)
            viewModel.clearPaymentStatus()
        case .canceled:
            toast = ToastMessage(text: "Payment canceled.", kind: .neutral)
            viewModel.clearPaymentStatus()
        case let .declined(message):
            toast = ToastMessage(text: message, kind: .error)
            viewModel.clearPaymentStatus()
        }
    }

    @ViewBuilder private var toastOverlay: some View {
        if let toast {
            ToastView(message: toast)
                .padding(.bottom, Spacing.s8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .accessibilityIdentifier(identifier(for: toast.kind))
                .task(id: toast) {
                    try? await Task.sleep(nanoseconds: 2_500_000_000)
                    self.toast = nil
                }
        }
    }

    private func identifier(for kind: ToastKind) -> String {
        switch kind {
        case .success: "checkout.paySuccess"
        case .error: "checkout.payDeclined"
        case .neutral: "checkout.cancel"
        }
    }
}
