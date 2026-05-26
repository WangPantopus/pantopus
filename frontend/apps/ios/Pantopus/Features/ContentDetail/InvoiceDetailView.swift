//
//  InvoiceDetailView.swift
//  Pantopus
//
//  T2.6 invoice detail. Wraps `TransactionalDetailShell` with the same
//  vocabulary as gig + listing. The "Pay" CTA hands off to a Stripe
//  payment sheet once the backend integration lands; until then it
//  surfaces a placeholder.
//

import SwiftUI

public struct InvoiceDetailView: View {
    @State private var viewModel: InvoiceDetailViewModel
    @State private var paySheetVisible = false
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
            onPrimaryAction: { paySheetVisible = true },
            onSecondaryAction: nil,
            onRetry: { Task { await viewModel.load() } },
            onMessageCounterparty: nil
        )
        .task { await viewModel.load() }
        .sheet(isPresented: $paySheetVisible) {
            paySheet
        }
    }

    private var paySheet: some View {
        VStack(spacing: 14) {
            Icon(.shieldCheck, size: 36, color: Theme.Color.primary600)
            Text("Stripe payment sheet")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text("The real payment flow hooks the existing two-intent + sensitive-action-guard plumbing. Stub for now.")
                .font(.system(size: 13))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button {
                paySheetVisible = false
            } label: {
                Text("Got it")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Theme.Color.primary600)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("invoiceDetailDismiss")
        }
        .padding(Spacing.s5)
        .presentationDetents([.fraction(0.4)])
    }
}
