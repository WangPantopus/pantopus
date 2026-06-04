//
//  PaymentsViewModel.swift
//  Pantopus
//
//  Projects the A14.6 Payments screen into render state. Phase 3 (3A)
//  wires the Payment-methods card to the real backend (`/api/payments`):
//  list saved methods, add a card via Stripe PaymentSheet, set-default
//  and remove (optimistic). The balance hero / Payouts (Stripe Connect) /
//  Activity sections render an honest "not set up yet" scaffold in the
//  live frame — they're wired in 3C. The fixture seam (`init(seed:)`)
//  drives previews + snapshot tests against the full design.
//

import Foundation
import Observation

/// Which sample frame to load. Selecting a seed puts the view-model in
/// fixture mode — `load()` projects the static fixture and the network /
/// PaymentSheet collaborators are never touched. Live mode (the default
/// `init()`) fetches from the backend.
public enum PaymentsSeed: Sendable, Hashable {
    case populated
    case empty
}

@Observable
@MainActor
public final class PaymentsViewModel {
    public private(set) var state: PaymentsState = .loading
    /// Transient, user-facing error from a row action (set-default /
    /// remove / add-card). The view surfaces it as an alert.
    public private(set) var actionError: String?

    private let api: APIClient
    private let sheetPresenter: any PaymentSheetPresenting
    /// Non-`nil` → fixture mode (previews / snapshots / projection tests).
    private let seed: PaymentsSeed?

    /// Live (production) — real backend + Stripe PaymentSheet.
    public convenience init() {
        self.init(api: .shared, sheetPresenter: StripePaymentSheetPresenter())
    }

    /// Live with injected collaborators — used by the live-path unit tests.
    public init(api: APIClient, sheetPresenter: any PaymentSheetPresenting) {
        self.api = api
        self.sheetPresenter = sheetPresenter
        seed = nil
    }

    /// Fixture-driven — previews, snapshot tests, projection tests.
    public init(seed: PaymentsSeed) {
        api = .shared
        sheetPresenter = StripePaymentSheetPresenter()
        self.seed = seed
    }

    // MARK: - Load

    public func load() async {
        if let seed {
            state = .loaded(Self.fixture(for: seed))
            return
        }
        state = .loading
        do {
            let methods = try await fetchMethods()
            state = .loaded(Self.liveFrame(methods: methods))
        } catch {
            state = .error(message: Self.message(for: error))
        }
    }

    public func refresh() async {
        await load()
    }

    // MARK: - Add a card (Stripe PaymentSheet, SetupIntent)

    public func tapAddMethod() async {
        guard seed == nil else { return }
        do {
            let params: AddCardSheetParams = try await api.request(PaymentsEndpoints.addCardSheet())
            let outcome = await sheetPresenter.presentAddCard(
                setupIntentClientSecret: params.setupIntent,
                customer: params.customer,
                ephemeralKey: params.ephemeralKey
            )
            switch outcome {
            case .completed:
                // The attached card is reconciled into the backend by the
                // `payment_method.attached` webhook; re-read the source of
                // truth so the list reflects server state.
                await reloadMethods()
            case .canceled:
                break
            case let .failed(message):
                actionError = message
            }
        } catch {
            actionError = Self.message(for: error)
        }
    }

    // MARK: - Set default / remove (optimistic, then reconcile)

    public func setDefault(_ id: String) async {
        guard seed == nil, case let .loaded(loaded) = state else { return }
        let previous = loaded
        state = .loaded(loaded.markingDefault(id))
        do {
            try await api.request(PaymentsEndpoints.setDefaultMethod(id: id))
            await reloadMethods()
        } catch {
            state = .loaded(previous)
            actionError = "Couldn't update your default payment method. Please try again."
        }
    }

    public func removeMethod(_ id: String) async {
        guard seed == nil, case let .loaded(loaded) = state else { return }
        let previous = loaded
        state = .loaded(loaded.removingMethod(id))
        do {
            try await api.request(PaymentsEndpoints.removeMethod(id: id))
            await reloadMethods()
        } catch {
            state = .loaded(previous)
            actionError = "Couldn't remove that payment method. Please try again."
        }
    }

    public func clearActionError() {
        actionError = nil
    }

    /// Stripe Connect onboarding, payout routing and the destructive
    /// close-account flow land with 3C; these stay no-ops so the view
    /// remains typed.
    public func tapRow(_: String) async {}
    public func tapCloseAccount() async {}

    // MARK: - Backend

    private func fetchMethods() async throws -> [PaymentMethod] {
        let response: PaymentMethodsResponse = try await api.request(PaymentsEndpoints.methods())
        return response.paymentMethods.map(Self.uiMethod(from:))
    }

    private func reloadMethods() async {
        do {
            let methods = try await fetchMethods()
            if case let .loaded(current) = state {
                state = .loaded(current.replacingMethods(methods))
            } else {
                state = .loaded(Self.liveFrame(methods: methods))
            }
        } catch {
            // Keep the optimistic state; surface a soft error.
            actionError = Self.message(for: error)
        }
    }

    private static func message(for error: any Error) -> String {
        (error as? APIError)?.errorDescription ?? "Couldn't load your payment methods. Please try again."
    }

    // MARK: - Projection

    private static func fixture(for seed: PaymentsSeed) -> PaymentsLoaded {
        switch seed {
        case .populated: PaymentsSampleData.populated
        case .empty: PaymentsSampleData.empty
        }
    }

    /// Live frame: real saved methods + an honest "payouts not set up yet"
    /// scaffold. The balance hero / Connect / Activity sections are wired
    /// for real in 3C — until then we never fabricate balances.
    static func liveFrame(methods: [PaymentMethod]) -> PaymentsLoaded {
        PaymentsLoaded(
            balance: nil,
            methods: methods,
            payouts: notConnectedPayouts,
            activity: .empty(
                title: "No transactions yet",
                body: "Hires and sales will appear here."
            ),
            canCloseAccount: false,
            footerCaption: "Payments are processed securely by Stripe."
        )
    }

    private static let notConnectedPayouts = PaymentsPayouts(
        stripe: PaymentsPayoutRow(
            id: "payouts.stripe",
            leadingBrand: .stripe,
            label: "Stripe Connect",
            subtext: "Receive payments from neighbors",
            trailing: .ctaChip(label: "Connect", tone: .primary)
        ),
        payoutMethod: PaymentsPayoutRow(
            id: "payouts.method",
            label: "Payout method",
            subtext: "Available after Stripe connect",
            trailing: .gatedDash
        ),
        payoutSchedule: nil,
        taxInfo: PaymentsPayoutRow(
            id: "payouts.tax",
            label: "Tax info",
            subtext: "Available after Stripe connect",
            trailing: .gatedDash
        ),
        helper: "Required before you can post paid tasks or sell on Marketplace."
    )

    static func uiMethod(from dto: PaymentMethodDTO) -> PaymentMethod {
        let isBank = dto.paymentMethodType == "us_bank_account"
            || (dto.cardBrand == nil && dto.bankLast4 != nil)
        let last4 = isBank ? (dto.bankLast4 ?? "••••") : (dto.cardLast4 ?? "••••")
        let name = isBank ? (dto.bankName ?? "Bank account") : cardName(dto.cardBrand)
        let label = "\(name) •• \(last4)"

        let subtext: String?
        if isBank {
            subtext = dto.bankAccountType.map { "\($0.capitalized) account" }
        } else if let month = dto.cardExpMonth, let year = dto.cardExpYear {
            subtext = String(format: "Expires %02d/%02d", month, year % 100)
        } else {
            subtext = nil
        }

        return PaymentMethod(
            id: dto.id,
            brand: isBank ? .bank : brand(from: dto.cardBrand),
            label: label,
            subtext: subtext,
            chip: dto.isDefault ? PaymentMethodChip(label: "Default", tone: .primary) : nil
        )
    }

    private static func brand(from cardBrand: String?) -> PaymentMethodBrand {
        switch (cardBrand ?? "").lowercased() {
        case "visa": .visa
        case "mastercard": .mastercard
        case "amex", "american_express": .amex
        default: .card
        }
    }

    private static func cardName(_ cardBrand: String?) -> String {
        let raw = (cardBrand ?? "").trimmingCharacters(in: .whitespaces)
        return switch raw.lowercased() {
        case "visa": "Visa"
        case "mastercard": "Mastercard"
        case "amex", "american_express": "Amex"
        case "": "Card"
        default: raw.capitalized
        }
    }
}

// MARK: - PaymentsLoaded transforms

private extension PaymentsLoaded {
    func replacingMethods(_ methods: [PaymentMethod]) -> PaymentsLoaded {
        PaymentsLoaded(
            balance: balance,
            methods: methods,
            payouts: payouts,
            activity: activity,
            canCloseAccount: canCloseAccount,
            footerCaption: footerCaption
        )
    }

    func markingDefault(_ id: String) -> PaymentsLoaded {
        replacingMethods(methods.map { method in
            PaymentMethod(
                id: method.id,
                brand: method.brand,
                label: method.label,
                subtext: method.subtext,
                chip: method.id == id ? PaymentMethodChip(label: "Default", tone: .primary) : nil
            )
        })
    }

    func removingMethod(_ id: String) -> PaymentsLoaded {
        replacingMethods(methods.filter { $0.id != id })
    }
}
