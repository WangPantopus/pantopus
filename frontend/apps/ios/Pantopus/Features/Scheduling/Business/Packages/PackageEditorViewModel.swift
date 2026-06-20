//
//  PackageEditorViewModel.swift
//  Pantopus
//
//  G9 Create / Edit Package (owner) — Stream I15. One scrolling form with live
//  per-session math: name, eligible event type, sessions, price, active. Wires
//  `POST /packages` (create) and `PUT /packages/:id` (edit). Behind
//  `SchedulingFeatureFlags.paidEnabled`. Matches `createpackage-frames.jsx`.
//
//  Backend note: the packages table has no `description` or `expiry` column
//  (POST /packages accepts name/sessions_count/price_cents/currency/event_type_id/
//  is_active only) and `event_type_id` is a single uuid (null = all services).
//  The design's Description/Expiry cards + multi-select tiles are therefore not
//  persisted and are omitted here (flagged for a backend follow-up). Price 0 is
//  a valid free package server-side, so we don't enforce price > 0.
//

import Foundation
import Observation
import SwiftUI

@Observable
@MainActor
final class PackageEditorViewModel {
    enum Phase: Equatable { case loading, ready, error(String), comingSoon }

    /// Expiry window for purchased credits (design's `Segmented`). View-only
    /// for now — the packages table has no `expiry` column, so the selection is
    /// not sent on save (see the backend note). Persistence is deferred.
    enum Expiry: String, CaseIterable, Equatable {
        case ninetyDays, oneYear, never
        var label: String {
            switch self {
            case .ninetyDays: "90 days"
            case .oneYear: "1 year"
            case .never: "Never"
            }
        }
    }

    // MARK: Inputs

    let owner: SchedulingOwner
    let packageId: String?
    let push: @MainActor (SchedulingRoute) -> Void
    private let client: SchedulingClient

    // MARK: Editable fields

    var name = ""
    /// Free-text "what's included" blurb (design's Description field). View-only:
    /// no `description` column on the packages table, so it is not persisted on
    /// save (deferred to a backend follow-up). Default empty.
    var packageDescription = ""
    var sessionsCount = 5
    var priceText = ""
    /// Single eligible event type (`nil` = all services). The design tiles read
    /// as a multi-select; the wire only carries one `event_type_id`, so this
    /// stays single-select until a backend array lands (deferred).
    var selectedEventTypeId: String?
    /// Credit-expiry window. View-only (no `expiry` column) — see `Expiry`.
    var expiry: Expiry = .oneYear
    var isActive = true

    // MARK: State

    private(set) var phase: Phase = .ready
    private(set) var saving = false
    private(set) var eventTypes: [EventTypeDTO] = []
    private(set) var nameError = false
    private var snapshot = Snapshot()

    var isEditing: Bool { packageId != nil }
    var theme: SchedulingIdentityTheme { owner.theme }
    var accent: Color { theme.accent }
    var accentBg: Color { theme.accentBg }

    var isValid: Bool { !name.trimmingCharacters(in: .whitespaces).isEmpty && sessionsCount >= 1 }

    var isDirty: Bool { Snapshot(self) != snapshot }

    /// Live "$44.00 per session" math for the price card.
    var perSessionLabel: String {
        SchedulingMoney.perSession(totalCents: SchedulingMoney.parseCents(priceText), sessions: sessionsCount)
    }

    init(
        owner: SchedulingOwner,
        packageId: String?,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient
    ) {
        self.owner = owner
        self.packageId = packageId
        self.push = push
        self.client = client
    }

    // MARK: Lifecycle

    func load() async {
        guard SchedulingFeatureFlags.paidEnabled else { phase = .comingSoon; return }
        phase = isEditing ? .loading : .ready
        // Event types power the "redeems against" tiles — best-effort.
        if let types: EventTypesResponse = try? await client.request(SchedulingEndpoints.getEventTypes(owner: owner)) {
            eventTypes = types.eventTypes.filter { $0.isActive != false }
        }
        if let packageId {
            do {
                // No single GET — the owner-scoped list is the source of truth.
                let result: PackagesResponse = try await client.request(SchedulingEndpoints.getPackages(owner: owner))
                guard let package = result.packages.first(where: { $0.id == packageId }) else {
                    phase = .error("That package no longer exists.")
                    return
                }
                seed(from: package)
                phase = .ready
            } catch let error as SchedulingError {
                phase = .error(error.userMessage ?? "Couldn't load that package.")
            } catch {
                phase = .error("Couldn't load that package.")
            }
        }
        snapshot = Snapshot(self)
    }

    private func seed(from package: SchedulingPackageDTO) {
        name = package.name
        sessionsCount = max(1, package.sessionsCount ?? 1)
        if let cents = package.priceCents, cents > 0 {
            priceText = String(format: "%.2f", Double(cents) / 100)
        }
        selectedEventTypeId = package.eventTypeId
        isActive = package.isActive ?? true
    }

    // MARK: Actions

    func selectEventType(_ id: String?) {
        selectedEventTypeId = (selectedEventTypeId == id) ? nil : id
    }

    func tileDuration(_ type: EventTypeDTO) -> String {
        let minutes = type.defaultDuration ?? type.durations.first ?? 0
        return minutes > 0 ? "\(minutes) min" : ""
    }

    /// Create or update, then invoke `onDone` (pop) on success.
    func save(onDone: @escaping () -> Void) async {
        guard isValid else { nameError = name.trimmingCharacters(in: .whitespaces).isEmpty; return }
        nameError = false
        saving = true
        defer { saving = false }
        let priceCents = SchedulingMoney.parseCents(priceText) ?? 0
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        do {
            if let packageId {
                let _: PackageResponse = try await client.request(
                    SchedulingEndpoints.updatePackage(
                        owner: owner,
                        id: packageId,
                        SchedulingUpdatePackageRequest(
                            name: trimmedName,
                            sessionsCount: sessionsCount,
                            priceCents: priceCents,
                            currency: "USD",
                            eventTypeId: selectedEventTypeId,
                            isActive: isActive
                        )
                    )
                )
            } else {
                let _: PackageResponse = try await client.request(
                    SchedulingEndpoints.createPackage(
                        owner: owner,
                        SchedulingCreatePackageRequest(
                            name: trimmedName,
                            sessionsCount: sessionsCount,
                            priceCents: priceCents,
                            currency: "USD",
                            eventTypeId: selectedEventTypeId,
                            isActive: isActive
                        )
                    )
                )
            }
            snapshot = Snapshot(self)
            onDone()
        } catch let error as SchedulingError {
            if case .validation = error, error.validationDetails.contains(where: { $0.field == "name" }) {
                nameError = true
            }
            phase = .error(error.userMessage ?? "Couldn't save the package.")
        } catch {
            phase = .error("Couldn't save the package.")
        }
    }

    // MARK: Dirty tracking

    private struct Snapshot: Equatable {
        var name = ""
        var sessionsCount = 5
        var priceText = ""
        var selectedEventTypeId: String?
        var isActive = true

        init() {}
        @MainActor init(_ vm: PackageEditorViewModel) {
            name = vm.name
            sessionsCount = vm.sessionsCount
            priceText = vm.priceText
            selectedEventTypeId = vm.selectedEventTypeId
            isActive = vm.isActive
        }
    }
}
