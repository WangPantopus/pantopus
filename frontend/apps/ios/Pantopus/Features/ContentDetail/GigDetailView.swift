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
    @State private var tipCustomAmountText = ""
    @State private var toast: ToastMessage?
    private let onBack: @MainActor () -> Void
    private let onOpenChat: (@MainActor (InboxConversationDestination) -> Void)?

    /// Block 3D — preset tip amounts in cents.
    private let tipPresets = [500, 1000, 2000]

    public init(
        viewModel: GigDetailViewModel,
        onBack: @escaping @MainActor () -> Void = {},
        onOpenChat: (@MainActor (InboxConversationDestination) -> Void)? = nil
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
        self.onOpenChat = onOpenChat
    }

    public var body: some View {
        TransactionalDetailShell(
            state: viewModel.state,
            topBarAccessory: bookmarkAccessory,
            onBack: onBack,
            onPrimaryAction: { presentPrimaryAction() },
            onSecondaryAction: { openChat() },
            onRetry: { Task { await viewModel.load() } },
            onMessageCounterparty: { openChat() },
            scrollFooter: {
                if case .loaded = viewModel.state {
                    GigQuestionsSection(viewModel: viewModel) { message in
                        toast = ToastMessage(text: message, kind: .error)
                    }
                }
            }
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

    // MARK: - Save / bookmark (work item C)

    /// Top-bar bookmark toggle. Hidden until the gig is loaded.
    /// Optimistic — the VM flips immediately and reverts on failure,
    /// at which point we surface an error toast.
    private var bookmarkAccessory: AnyView? {
        guard case .loaded = viewModel.state else { return nil }
        return AnyView(
            Button {
                Task {
                    let ok = await viewModel.toggleSave()
                    if !ok {
                        toast = ToastMessage(text: "Couldn't update saved tasks.", kind: .error)
                    }
                }
            } label: {
                Icon(
                    .bookmark,
                    size: 18,
                    strokeWidth: 2,
                    color: viewModel.isSaved ? Theme.Color.primary600 : Theme.Color.appText
                )
                .frame(width: 36, height: 36)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(viewModel.isSaved ? "Saved — tap to unsave" : "Save task")
            .accessibilityIdentifier("gigDetail.save")
        )
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
            VStack(alignment: .leading, spacing: Spacing.s2) {
                Text("Custom amount")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                HStack(spacing: Spacing.s2) {
                    Text("$")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                    TextField("0.00", text: $tipCustomAmountText)
                        .keyboardType(.decimalPad)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                        .accessibilityIdentifier("tip.amount.customInput")
                }
                .padding(.horizontal, Spacing.s3)
                .frame(height: 48)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            }
            Button {
                if let cents = customTipCents {
                    selectTip(cents)
                }
            } label: {
                Text("Send custom tip")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(customTipCents == nil ? Theme.Color.appTextMuted : Theme.Color.appTextInverse)
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .background(customTipCents == nil ? Theme.Color.appSurfaceSunken : Theme.Color.primary600)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            }
            .disabled(customTipCents == nil)
            .buttonStyle(.plain)
            .accessibilityIdentifier("tip.amount.customSubmit")
            Button("Not now") { showTipSheet = false }
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .buttonStyle(.plain)
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity)
        .presentationDetents([.height(410)])
        .accessibilityIdentifier("tip.amount")
    }

    private var customTipCents: Int? {
        let cleaned = tipCustomAmountText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "$", with: "")
            .replacingOccurrences(of: ",", with: "")
        guard let dollars = Double(cleaned), dollars >= 0.5 else { return nil }
        return max(50, Int((dollars * 100).rounded()))
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
        tipCustomAmountText = ""
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
            tipCustomAmountText = ""
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

    private func openChat() {
        Task {
            guard let destination = await viewModel.resolveChatDestination() else { return }
            onOpenChat?(destination)
        }
    }
}
