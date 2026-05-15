//
//  GigDetailView.swift
//  Pantopus
//
//  T2.6 gig detail. Wraps `ContentDetailShell`. The primary dock
//  action opens a place-bid sheet; the secondary opens a placeholder
//  message thread.
//

import SwiftUI

public struct GigDetailView: View {
    @State private var viewModel: GigDetailViewModel
    @State private var bidSheetVisible = false
    @State private var bidAmount: String = ""
    @State private var bidMessage: String = ""
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
        ContentDetailShell(
            state: viewModel.state,
            onBack: onBack,
            onPrimaryAction: { bidSheetVisible = true },
            onSecondaryAction: { if let gig = viewModel.rawGig { onMessage?(gig) } },
            onRetry: { Task { await viewModel.load() } },
            onMessageCounterparty: { if let gig = viewModel.rawGig { onMessage?(gig) } }
        )
        .task { await viewModel.load() }
        .sheet(isPresented: $bidSheetVisible) {
            bidSheet
        }
    }

    private var bidSheet: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Place a bid")
                .font(.system(size: 18, weight: .bold))
            Text("Tell the poster what you'd charge and add a short message about your approach.")
                .font(.system(size: 13))
                .foregroundStyle(Theme.Color.appTextSecondary)
            TextField("Amount", text: $bidAmount)
                .keyboardType(.decimalPad)
                .textFieldStyle(.roundedBorder)
                .accessibilityLabel("Bid amount")
            TextField("Message (optional)", text: $bidMessage, axis: .vertical)
                .lineLimit(2...4)
                .textFieldStyle(.roundedBorder)
                .accessibilityLabel("Bid message")
            Button {
                guard let amount = Double(bidAmount), amount > 0 else { return }
                Task {
                    let ok = await viewModel.placeBid(amount: amount, message: bidMessage.isEmpty ? nil : bidMessage)
                    if ok {
                        bidSheetVisible = false
                        bidAmount = ""
                        bidMessage = ""
                    }
                }
            } label: {
                Text("Submit bid")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Theme.Color.primary600)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(Double(bidAmount) == nil)
            .accessibilityIdentifier("gigDetailSubmitBid")
        }
        .padding(20)
        .presentationDetents([.medium])
    }
}
