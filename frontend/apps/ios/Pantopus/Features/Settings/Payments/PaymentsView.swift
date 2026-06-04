//
//  PaymentsView.swift
//  Pantopus
//
//  P5.2 / A14.6 — Settings → Payments. The payments-OUT surface
//  (cards on file · Stripe Connect setup · payout routing) — distinct
//  from A10.10 Wallet (earnings-in). Three grouped cards under an
//  optional balance hero, with an "Add payment method" blue row as
//  the final item in the Payment methods card (iOS convention) and a
//  destructive Close-account card on the populated frame only.
//
//  Stripe Connect onboarding deep-link and the card-add bottom sheet
//  are out of scope — the rows surface the chrome so the design lands;
//  follow-up work wires the actions through PaymentsViewModel.
//

import SwiftUI

public struct PaymentsView: View {
    @State private var viewModel: PaymentsViewModel
    @State private var actionMethod: PaymentMethod?
    private let onBack: @MainActor () -> Void

    public init(
        viewModel: PaymentsViewModel = PaymentsViewModel(),
        onBack: @escaping @MainActor () -> Void
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
    }

    public var body: some View {
        VStack(spacing: Spacing.s0) {
            SettingsTopBar(title: "Payments", onBack: onBack)
                .accessibilityIdentifier("paymentsTopBar")
            content
        }
        .background(Theme.Color.appBg)
        .task { await viewModel.load() }
        .accessibilityIdentifier("payments")
        .confirmationDialog(
            actionMethod?.label ?? "Payment method",
            isPresented: Binding(
                get: { actionMethod != nil },
                set: { if !$0 { actionMethod = nil } }
            ),
            titleVisibility: .visible
        ) {
            methodActions
        }
        .alert(
            "Something went wrong",
            isPresented: Binding(
                get: { viewModel.actionError != nil },
                set: { if !$0 { viewModel.clearActionError() } }
            )
        ) {
            Button("OK", role: .cancel) { viewModel.clearActionError() }
        } message: {
            Text(viewModel.actionError ?? "")
        }
    }

    @ViewBuilder private var methodActions: some View {
        if let method = actionMethod {
            if method.chip?.tone != .primary {
                Button("Set as Default") {
                    Task { await viewModel.setDefault(method.id) }
                }
                .accessibilityIdentifier("paymentsRow_\(method.id)_setDefault")
            }
            Button("Remove Card", role: .destructive) {
                Task { await viewModel.removeMethod(method.id) }
            }
            .accessibilityIdentifier("paymentsRow_\(method.id)_remove")
            Button("Cancel", role: .cancel) {}
        }
    }
}

private extension PaymentsView {
    @ViewBuilder var content: some View {
        switch viewModel.state {
        case .loading: loadingFrame
        case let .loaded(loaded): loadedFrame(loaded)
        case let .error(message): errorFrame(message: message)
        }
    }

    // MARK: - Loaded

    private func loadedFrame(_ loaded: PaymentsLoaded) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s0) {
                if let balance = loaded.balance {
                    balanceHero(balance)
                        .padding(.horizontal, Spacing.s3)
                        .padding(.top, 14)
                }
                methodsSection(loaded.methods)
                payoutsSection(loaded.payouts)
                activitySection(loaded.activity)
                if loaded.canCloseAccount {
                    destructiveCard
                }
                Text(loaded.footerCaption)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(Theme.Color.appTextMuted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Spacing.s4)
                    .padding(.top, 18)
                    .padding(.bottom, Spacing.s5)
                    .frame(maxWidth: .infinity)
                    .accessibilityIdentifier("paymentsFooter")
            }
            .padding(.bottom, Spacing.s5)
        }
        .accessibilityIdentifier("paymentsContent")
    }

    private func balanceHero(_ balance: PaymentsBalance) -> some View {
        BalanceHero(
            overline: balance.overline,
            amount: balance.amount,
            currencyCode: "USD",
            payoutFooter: BalanceHero.PayoutFooter(
                nextPayoutLabel: balance.nextPayoutLabel,
                frequencyPill: balance.frequencyPill
            )
        )
        .accessibilityIdentifier("paymentsBalanceHero")
    }

    // MARK: - Sections

    private func methodsSection(_ methods: [PaymentMethod]) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s0) {
            sectionOverline("Payment methods", id: "methods")
            card(id: "methods") {
                if methods.isEmpty {
                    inlineEmpty(
                        icon: .creditCard,
                        title: "No payment methods yet",
                        body: "Add a card or bank account to hire neighbors and pay for marketplace listings."
                    )
                    divider
                } else {
                    ForEach(Array(methods.enumerated()), id: \.element.id) { index, method in
                        Button(action: {
                            actionMethod = method
                        }, label: {
                            PaymentMethodRow(
                                brand: method.brand,
                                label: method.label,
                                subtext: method.subtext,
                                chip: method.chip,
                                trailing: .chevron,
                                rowIdentifier: method.id,
                                chipIdentifier: method.chip != nil
                                    ? "paymentsRow_\(method.id)_defaultBadge"
                                    : nil
                            )
                        })
                        .buttonStyle(.plain)
                        if index < methods.count - 1 {
                            divider
                        }
                    }
                    divider
                }
                addMethodRow
            }
        }
    }

    private func payoutsSection(_ payouts: PaymentsPayouts) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s0) {
            sectionOverline("Payouts", id: "payouts")
            card(id: "payouts") {
                payoutRow(payouts.stripe)
                divider
                payoutRow(payouts.payoutMethod)
                if let schedule = payouts.payoutSchedule {
                    divider
                    payoutRow(schedule)
                }
                divider
                payoutRow(payouts.taxInfo)
            }
            if let helper = payouts.helper {
                Text(helper)
                    .font(.system(size: 11.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .padding(.horizontal, Spacing.s4)
                    .padding(.top, Spacing.s2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("paymentsHelper_payouts")
            }
        }
    }

    private func activitySection(_ activity: PaymentsActivity) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s0) {
            sectionOverline("Activity", id: "activity")
            card(id: "activity") {
                switch activity {
                case let .stats(stats):
                    ForEach(Array(stats.enumerated()), id: \.element.id) { index, stat in
                        activityStatRow(stat)
                        if index < stats.count - 1 {
                            divider
                        }
                    }
                case let .empty(title, body):
                    activityEmptyRow(title: title, body: body)
                }
            }
        }
    }

    private var destructiveCard: some View {
        VStack(spacing: Spacing.s0) {
            Button(action: {
                Task { await viewModel.tapCloseAccount() }
            }, label: {
                HStack {
                    Text("Close payment account")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Theme.Color.error)
                    Spacer(minLength: Spacing.s0)
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.vertical, 14)
                .frame(minHeight: 48)
                .contentShape(Rectangle())
            })
            .buttonStyle(.plain)
            .accessibilityIdentifier("paymentsRow_closeAccount")
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .padding(.horizontal, Spacing.s3)
        .padding(.top, 18)
    }

    // MARK: - Row primitives

    private func payoutRow(_ row: PaymentsPayoutRow) -> some View {
        Button(action: {
            Task { await viewModel.tapRow(row.id) }
        }, label: {
            PaymentMethodRow(
                brand: row.leadingBrand,
                label: row.label,
                subtext: row.subtext,
                chip: nil,
                trailing: row.trailing,
                rowIdentifier: row.id
            )
        })
        .buttonStyle(.plain)
        .disabled(row.trailing == .gatedDash)
    }

    private func activityStatRow(_ stat: PaymentsActivityStat) -> some View {
        HStack(spacing: Spacing.s3) {
            VStack(alignment: .leading, spacing: 2) {
                Text(stat.label)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Theme.Color.appText)
                if let subtext = stat.subtext {
                    Text(subtext)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            Spacer(minLength: Spacing.s0)
            Icon(.chevronRight, size: 16, strokeWidth: 2.2, color: Theme.Color.appTextSecondary)
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, 14)
        .frame(minHeight: 48)
        .accessibilityIdentifier("paymentsActivityStat_\(stat.id)")
    }

    private func activityEmptyRow(title: String, body: String) -> some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                Circle().fill(Theme.Color.appSurfaceSunken).frame(width: 32, height: 32)
                Icon(.receipt, size: 16, strokeWidth: 1.75, color: Theme.Color.appTextMuted)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Text(body)
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
            Spacer(minLength: Spacing.s0)
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, 18)
        .accessibilityIdentifier("paymentsActivityEmpty")
    }

    private var addMethodRow: some View {
        Button(action: {
            Task { await viewModel.tapAddMethod() }
        }, label: {
            HStack(spacing: Spacing.s3) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.xs, style: .continuous)
                        .fill(Theme.Color.primary50)
                        .frame(width: 38, height: 26)
                    Icon(.plus, size: 16, strokeWidth: 2.5, color: Theme.Color.primary600)
                }
                Text("Add payment method")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.Color.primary600)
                Spacer(minLength: Spacing.s0)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, 13)
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        })
        .buttonStyle(.plain)
        .accessibilityIdentifier("paymentsAddMethodRow")
    }

    private func inlineEmpty(icon: PantopusIcon, title: String, body: String) -> some View {
        VStack(spacing: Spacing.s2) {
            ZStack {
                Circle().fill(Theme.Color.appSurfaceSunken).frame(width: 48, height: 48)
                Icon(icon, size: 22, strokeWidth: 1.75, color: Theme.Color.appTextMuted)
            }
            .padding(.bottom, 2)
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
            Text(body)
                .font(.system(size: 12.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 240)
                .lineSpacing(2)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Spacing.s5)
        .padding(.top, 28)
        .padding(.bottom, 22)
        .accessibilityIdentifier("paymentsMethodsInlineEmpty")
    }

    // MARK: - Card chrome

    private func card(id: String, @ViewBuilder _ rows: () -> some View) -> some View {
        VStack(spacing: Spacing.s0) {
            rows()
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .padding(.horizontal, Spacing.s3)
        .accessibilityIdentifier("paymentsCard_\(id)")
    }

    private var divider: some View {
        Rectangle()
            .fill(Theme.Color.appBorder.opacity(0.6))
            .frame(height: 1)
            .padding(.leading, Spacing.s4)
    }

    private func sectionOverline(_ text: String, id: String) -> some View {
        Text(text.uppercased())
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(Theme.Color.appTextSecondary)
            .kerning(0.9)
            .padding(.horizontal, Spacing.s4)
            .padding(.top, 18)
            .padding(.bottom, Spacing.s2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("paymentsOverline_\(id)")
    }

    // MARK: - Loading / Error

    private var loadingFrame: some View {
        ScrollView {
            VStack(spacing: Spacing.s3) {
                Shimmer(height: 96, cornerRadius: 18)
                    .padding(.horizontal, Spacing.s3)
                    .padding(.top, 14)
                ForEach(0..<3, id: \.self) { _ in
                    Shimmer(height: 110, cornerRadius: Radii.lg)
                        .padding(.horizontal, Spacing.s3)
                        .padding(.top, Spacing.s3)
                }
            }
        }
        .accessibilityIdentifier("paymentsLoading")
    }

    private func errorFrame(message: String) -> some View {
        VStack(spacing: Spacing.s3) {
            Spacer()
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load Payments")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button {
                Task { await viewModel.refresh() }
            } label: {
                Text("Try again")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, 22)
                    .frame(height: 44)
                    .background(Theme.Color.primary600)
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("paymentsRetry")
            Spacer()
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("paymentsError")
    }
}

#Preview("Populated") {
    PaymentsView(viewModel: PaymentsViewModel(seed: .populated)) {}
}

#Preview("Empty") {
    PaymentsView(viewModel: PaymentsViewModel(seed: .empty)) {}
}
