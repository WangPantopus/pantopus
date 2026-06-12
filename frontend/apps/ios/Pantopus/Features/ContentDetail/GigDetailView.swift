//
//  GigDetailView.swift
//  Pantopus
//
//  T2.6 gig detail. Wraps `TransactionalDetailShell`. The primary dock
//  action opens the shared `EditBidSheetView` in place-bid mode (or tips /
//  delivers / instant-accepts depending on the lifecycle); the Phase 5/5b
//  scroll footer carries the owner bids panel, active-task strip (with
//  running-late badge), changes card, payment card, and review CTA, with
//  counter / report / cancel / no-show / running-late / change-order
//  sheets attached.
//

// swiftlint:disable file_length type_body_length

import SwiftUI

public struct GigDetailView: View {
    @State private var viewModel: GigDetailViewModel
    @State private var bidSheetTarget: EditBidSheetTarget?
    @State private var deliveryTarget: DeliveryProofTarget?
    @State private var showTipSheet = false
    @State private var tipCustomAmountText = ""
    @State private var toast: ToastMessage?
    // Phase 5 — lifecycle sheets
    @State private var counterTarget: GigCounterSheetTarget?
    @State private var rejectCandidate: GigBidDTO?
    @State private var showReportSheet = false
    @State private var showCancelSheet = false
    @State private var cancelPreview: GigCancellationPreview?
    @State private var showNoShowSheet = false
    @State private var reviewTarget: LeaveReviewSheetTarget?
    // Phase 5b — lifecycle completers
    @State private var showRunningLateSheet = false
    @State private var showChangeOrderSheet = false
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
            overflowItems: overflowItems,
            topBarAccessory: topBarAccessories,
            onBack: onBack,
            onPrimaryAction: { presentPrimaryAction() },
            onSecondaryAction: { openChat() },
            onRetry: { Task { await viewModel.load() } },
            onMessageCounterparty: { openChat() },
            scrollFooter: { lifecycleFooter }
        )
        .task {
            await viewModel.load()
            viewModel.startRealtime()
        }
        .onDisappear { viewModel.stopRealtime() }
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
        .modifier(GigLifecycleSheets(
            viewModel: viewModel,
            counterTarget: $counterTarget,
            showReportSheet: $showReportSheet,
            showCancelSheet: $showCancelSheet,
            cancelPreview: $cancelPreview,
            showNoShowSheet: $showNoShowSheet,
            reviewTarget: $reviewTarget,
            showRunningLateSheet: $showRunningLateSheet,
            showChangeOrderSheet: $showChangeOrderSheet,
            toast: $toast
        ))
        .confirmationDialog(
            "Reject this bid?",
            isPresented: Binding(
                get: { rejectCandidate != nil },
                set: { if !$0 { rejectCandidate = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Reject bid", role: .destructive) { confirmReject() }
            Button("Keep bid", role: .cancel) { rejectCandidate = nil }
        } message: {
            Text("The bidder is notified and can't be selected afterwards.")
        }
        .overlay(alignment: .bottom) { toastOverlay }
        .overlay(alignment: .top) { tipMarkers }
        .onChange(of: viewModel.tipStatus) { _, status in handleTip(status) }
    }

    // MARK: - Lifecycle footer (Phase 5 / 5b)

    /// Owner bids panel → active-task panel → changes card → payment
    /// card → review CTA → Q&A.
    @ViewBuilder private var lifecycleFooter: some View {
        if case .loaded = viewModel.state {
            if viewModel.showOwnerBidsPanel {
                GigOwnerBidsPanel(
                    bids: viewModel.ownerBids,
                    inFlightBidId: viewModel.bidActionInFlight,
                    onAccept: { bid in Task { await acceptBid(bid) } },
                    onCounter: { bid in counterTarget = GigCounterSheetTarget(id: bid.id, bid: bid) },
                    onReject: { bid in rejectCandidate = bid }
                )
            }
            if let phase = viewModel.activePhase, viewModel.showActivePanel {
                GigActiveTaskPanel(
                    phase: phase,
                    showWorkerAck: viewModel.showWorkerAck,
                    canStartTask: viewModel.canStartTask,
                    canConfirmCompletion: viewModel.canConfirmCompletion,
                    noShowEligible: viewModel.noShowEligible,
                    runningLateLabel: viewModel.runningLateLabel,
                    canReportRunningLate: viewModel.canReportRunningLate,
                    onWorkerAck: {
                        Task { await runToasting(success: "Told the poster you're on it.") { await viewModel.sendWorkerAck() } }
                    },
                    onStartTask: {
                        Task { await runToasting(success: "Task started.") { await viewModel.startTask() } }
                    },
                    onConfirmCompletion: {
                        Task { await runToasting(success: "Completion confirmed.") { await viewModel.confirmCompletion() } }
                    },
                    onReportNoShow: { showNoShowSheet = true },
                    onRunningLate: { showRunningLateSheet = true }
                )
            }
            if viewModel.showChangesSection {
                GigChangesCard(
                    orders: viewModel.changeOrders,
                    inFlightOrderId: viewModel.changeOrderActionInFlight,
                    isOwnOrder: { viewModel.isOwnChangeOrder($0) },
                    onApprove: { order in
                        Task { await runToasting(success: "Change approved.") { await viewModel.approveChangeOrder(orderId: order.id) } }
                    },
                    onReject: { order in
                        Task { await runToasting(success: "Change rejected.") { await viewModel.rejectChangeOrder(orderId: order.id) } }
                    },
                    onWithdraw: { order in
                        Task { await runToasting(success: "Change withdrawn.") { await viewModel.withdrawChangeOrder(orderId: order.id) } }
                    },
                    onPropose: { showChangeOrderSheet = true }
                )
            }
            if viewModel.showPaymentCard, let payment = viewModel.payment {
                GigPaymentCard(payment: payment, stateInfo: viewModel.paymentStateInfo)
            }
            if viewModel.showReviewSection {
                GigReviewSection(
                    reviewSubmitted: viewModel.reviewSubmitted,
                    revieweeName: viewModel.pendingReview?.revieweeName,
                    onLeaveReview: presentReviewSheet
                )
            }
            GigQuestionsSection(viewModel: viewModel) { message in
                toast = ToastMessage(text: message, kind: .error)
            }
        }
    }

    /// Run a `String?`-error VM action, toasting either way.
    private func runToasting(success: String, _ action: () async -> String?) async {
        if let error = await action() {
            toast = ToastMessage(text: error, kind: .error)
        } else {
            toast = ToastMessage(text: success, kind: .success)
        }
    }

    private func acceptBid(_ bid: GigBidDTO) async {
        switch await viewModel.acceptBid(bidId: bid.id) {
        case .accepted:
            toast = ToastMessage(text: "Bid accepted — task assigned.", kind: .success)
        case .canceled:
            toast = ToastMessage(text: "Payment canceled.", kind: .error)
        case let .failed(message):
            toast = ToastMessage(text: message, kind: .error)
        }
    }

    private func confirmReject() {
        guard let bid = rejectCandidate else { return }
        rejectCandidate = nil
        Task { await runToasting(success: "Bid rejected.") { await viewModel.rejectBid(bidId: bid.id) } }
    }

    private func presentReviewSheet() {
        guard let gig = viewModel.rawGig else { return }
        reviewTarget = LeaveReviewSheetTarget(
            id: "review-\(gig.id)",
            gigId: gig.id,
            revieweeId: viewModel.pendingReview?.revieweeId ?? "",
            gigTitle: gig.title,
            revieweeName: viewModel.pendingReview?.revieweeName
        )
    }

    // MARK: - Top bar (share + bookmark) & overflow

    /// Share (universal link) + bookmark toggle. Hidden until loaded.
    private var topBarAccessories: AnyView? {
        guard case .loaded = viewModel.state else { return nil }
        return AnyView(
            HStack(spacing: Spacing.s1) {
                ShareLink(item: viewModel.shareURL) {
                    Icon(.share, size: 18, strokeWidth: 2, color: Theme.Color.appText)
                        .frame(width: 36, height: 36)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Share task")
                .accessibilityIdentifier("gigDetail.share")
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
            }
        )
    }

    /// Report (everyone) + Cancel task (owner of a live gig).
    private var overflowItems: [ContentDetailOverflowItem] {
        guard case .loaded = viewModel.state else { return [] }
        var items = [
            ContentDetailOverflowItem(label: "Report task", icon: .flag, identifier: "gigDetail.report") {
                showReportSheet = true
            }
        ]
        if viewModel.canCancelTask {
            items.append(
                ContentDetailOverflowItem(
                    label: "Cancel task",
                    icon: .ban,
                    identifier: "gigDetail.cancel",
                    role: .destructive
                ) {
                    Task {
                        cancelPreview = await viewModel.loadCancellationPreview()
                        showCancelSheet = true
                    }
                }
            )
        }
        return items
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

    /// Zero-size anchors so UI tests can assert each tip stage (+ the
    /// instant-accept affordance, whose button is the shared dock primary).
    @ViewBuilder private var tipMarkers: some View {
        if viewModel.canInstantAccept {
            Color.clear.frame(width: 0, height: 0).accessibilityIdentifier("gigDetail.instantAccept")
        }
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
    /// worker on an in-progress task; instant accept on `instant_accept`
    /// open gigs; otherwise the bid sheet.
    private func presentPrimaryAction() {
        if viewModel.canTip {
            tipCustomAmountText = ""
            showTipSheet = true
        } else if viewModel.canMarkDelivered {
            presentDeliveryProof()
        } else if viewModel.canInstantAccept {
            Task { await runToasting(success: "You're on the task — it's yours.") { await viewModel.instantAccept() } }
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

// MARK: - Phase 5 — lifecycle sheets

/// Bundles the counter / report / cancel / no-show / review sheets so
/// `GigDetailView.body` stays readable. All mutations route through the
/// view-model; results surface via the shared toast binding.
private struct GigLifecycleSheets: ViewModifier {
    let viewModel: GigDetailViewModel
    @Binding var counterTarget: GigCounterSheetTarget?
    @Binding var showReportSheet: Bool
    @Binding var showCancelSheet: Bool
    @Binding var cancelPreview: GigCancellationPreview?
    @Binding var showNoShowSheet: Bool
    @Binding var reviewTarget: LeaveReviewSheetTarget?
    @Binding var showRunningLateSheet: Bool
    @Binding var showChangeOrderSheet: Bool
    @Binding var toast: ToastMessage?

    func body(content: Content) -> some View {
        phase5bSheets(phase5Sheets(content))
    }

    /// Phase 5 — counter / report / cancel / no-show / review.
    private func phase5Sheets(_ content: Content) -> some View {
        content
            .sheet(item: $counterTarget) { target in
                GigCounterSheet(
                    target: target,
                    onSubmit: { amount, message in
                        let error = await viewModel.counterBid(
                            bidId: target.bid.id,
                            amount: amount,
                            message: message
                        )
                        if error == nil {
                            toast = ToastMessage(text: "Counter-offer sent.", kind: .success)
                        }
                        return error
                    },
                    onDismiss: { counterTarget = nil }
                )
            }
            .sheet(isPresented: $showReportSheet) {
                GigReportSheet(
                    onSubmit: { reason, details in
                        let result = await viewModel.reportGig(reason: reason, details: details)
                        toast = ToastMessage(text: result.message, kind: result.success ? .success : .error)
                        showReportSheet = false
                    },
                    onDismiss: { showReportSheet = false }
                )
            }
            .sheet(isPresented: $showCancelSheet) {
                GigCancelSheet(
                    preview: cancelPreview,
                    onConfirm: { reason in
                        if let error = await viewModel.cancelTask(reason: reason) {
                            toast = ToastMessage(text: error, kind: .error)
                        } else {
                            toast = ToastMessage(text: "Task cancelled.", kind: .success)
                        }
                        showCancelSheet = false
                    },
                    onDismiss: { showCancelSheet = false }
                )
            }
            .sheet(isPresented: $showNoShowSheet) {
                GigNoShowSheet(
                    counterpartyLabel: viewModel.viewerIsWorker ? "poster" : "worker",
                    onConfirm: { description in
                        if let error = await viewModel.reportNoShow(description: description) {
                            toast = ToastMessage(text: error, kind: .error)
                        } else {
                            toast = ToastMessage(text: "No-show reported. The task was cancelled.", kind: .success)
                        }
                        showNoShowSheet = false
                    },
                    onDismiss: { showNoShowSheet = false }
                )
            }
            .sheet(item: $reviewTarget) { target in
                LeaveReviewSheetView(
                    target: target,
                    onSubmit: { draft in
                        if let error = await viewModel.submitReview(rating: draft.rating, comment: draft.comment) {
                            toast = ToastMessage(text: error, kind: .error)
                            return false
                        }
                        toast = ToastMessage(text: "Review submitted. Thanks!", kind: .success)
                        reviewTarget = nil
                        return true
                    },
                    onCancel: { reviewTarget = nil }
                )
                .presentationDetents([.medium, .large])
            }
    }

    /// Phase 5b — running-late + propose-a-change.
    private func phase5bSheets(_ content: some View) -> some View {
        content
            .sheet(isPresented: $showRunningLateSheet) {
                GigRunningLateSheet(
                    onSubmit: { etaMinutes, note in
                        let error = await viewModel.sendRunningLate(etaMinutes: etaMinutes, note: note)
                        if error == nil {
                            toast = ToastMessage(text: "Told the poster you're running late.", kind: .success)
                        }
                        return error
                    },
                    onDismiss: { showRunningLateSheet = false }
                )
            }
            .sheet(isPresented: $showChangeOrderSheet) {
                GigChangeOrderSheet(
                    onSubmit: { type, description, amountChange, timeChangeMinutes in
                        let error = await viewModel.proposeChangeOrder(
                            type: type,
                            description: description,
                            amountChange: amountChange,
                            timeChangeMinutes: timeChangeMinutes
                        )
                        if error == nil {
                            toast = ToastMessage(text: "Change request sent.", kind: .success)
                        }
                        return error
                    },
                    onDismiss: { showChangeOrderSheet = false }
                )
            }
    }
}
