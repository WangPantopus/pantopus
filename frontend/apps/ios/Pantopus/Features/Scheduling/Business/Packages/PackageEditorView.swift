//
//  PackageEditorView.swift
//  Pantopus
//
//  G9 Create / Edit Package (owner) — Stream I15. Reuses the shared `FormShell`
//  (back chevron + title + sticky "Save package" CTA + discard confirm) and
//  fills the body with the design's overline cards. Matches
//  `createpackage-frames.jsx`. Tokens only.
//

import SwiftUI

struct PackageEditorView: View {
    @State private var model: PackageEditorViewModel
    @Environment(\.dismiss) private var dismiss

    init(
        owner: SchedulingOwner,
        packageId: String?,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient = .shared
    ) {
        _model = State(wrappedValue: PackageEditorViewModel(owner: owner, packageId: packageId, push: push, client: client))
    }

    private var title: String { model.isEditing ? "Edit package" : "New package" }

    var body: some View {
        Group {
            switch model.phase {
            case .comingSoon:
                gated
            case let .error(message):
                errored(message)
            case .loading, .ready:
                form
            }
        }
        .task { await model.load() }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .accessibilityIdentifier("scheduling.packageEditor")
    }

    private var form: some View {
        FormShell(
            title: title,
            leading: .back,
            rightActionLabel: nil,
            bottomActionLabel: "Save package",
            isValid: model.isValid && model.phase == .ready,
            isDirty: model.isDirty,
            isSaving: model.saving,
            onClose: { dismiss() },
            onCommit: { Task { await model.save { dismiss() } } },
            content: {
                if model.phase == .loading {
                    loadingBody
                } else {
                    cards
                }
            }
        )
    }

    private var cards: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            if !model.isEditing {
                Text("Set a price and we'll do the per-session math.")
                    .font(.system(size: 11.5)).foregroundStyle(Theme.Color.appTextSecondary)
                    .padding(.horizontal, Spacing.s1)
            }
            detailsCard
            redeemCard
            sessionsCard
            priceCard
            if model.isEditing {
                PkgNote(tone: .info, icon: .info,
                        text: "Changing the price creates a new Stripe price. Current buyers keep their terms.")
            }
            activeCard
            Color.clear.frame(height: Spacing.s8)
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s2)
    }

    // MARK: Cards

    private var detailsCard: some View {
        PkgCard(overline: "Details") {
            PkgTextField(
                label: "Name",
                placeholder: "5-session cleaning",
                text: $model.name,
                error: model.nameError,
                helper: model.nameError ? "Give your package a name" : nil
            )
        }
    }

    private var redeemCard: some View {
        PkgCard(overline: "Redeems against") {
            if model.eventTypes.isEmpty {
                Text("Credits apply to all of your services.")
                    .font(.system(size: 11.5)).foregroundStyle(Theme.Color.appTextSecondary)
            } else {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                    EventTile(
                        title: "All services", duration: "Any event", icon: .layers,
                        selected: model.selectedEventTypeId == nil, accent: model.accent, accentBg: model.theme.accentBg
                    ) { model.selectEventType(nil) }
                    ForEach(model.eventTypes) { type in
                        EventTile(
                            title: type.name, duration: model.tileDuration(type), icon: .calendar,
                            selected: model.selectedEventTypeId == type.id, accent: model.accent, accentBg: model.theme.accentBg
                        ) { model.selectEventType(type.id) }
                    }
                }
            }
        }
    }

    private var sessionsCard: some View {
        PkgCard(overline: "Sessions") {
            HStack {
                Text("Number of sessions").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.Color.appText)
                Spacer()
                PkgStepper(value: $model.sessionsCount)
            }
        }
    }

    private var priceCard: some View {
        PkgCard(overline: "Price") {
            HStack(alignment: .top, spacing: 8) {
                PkgTextField(placeholder: "$0.00", text: $model.priceText, keyboard: .decimalPad)
                Text("USD")
                    .font(.system(size: 12.5, weight: .bold)).foregroundStyle(Theme.Color.appTextStrong)
                    .padding(.horizontal, 12).frame(height: 40)
                    .background(Theme.Color.appSurfaceSunken)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            }
            Text("\(model.perSessionLabel) per session")
                .font(.system(size: 11.5, weight: .bold)).foregroundStyle(model.accent)
        }
    }

    private var activeCard: some View {
        PkgCard {
            PkgToggleRow(
                icon: .power, label: "Active", sub: "Buyers can purchase this package",
                isOn: $model.isActive, accent: model.accent
            )
        }
    }

    // MARK: Loading / gated / error

    private var loadingBody: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            ForEach(0..<4, id: \.self) { _ in
                Shimmer(width: 80, height: 9, cornerRadius: Radii.xs)
                Shimmer(height: 96, cornerRadius: Radii.xl)
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s4)
    }

    private var gated: some View {
        VStack(spacing: Spacing.s0) {
            PkgTopBar(title: title, onBack: { dismiss() })
            PkgComingSoon(title: "Packages")
        }
        .background(Theme.Color.appBg)
        .navigationBarBackButtonHidden(true)
    }

    private func errored(_ message: String) -> some View {
        VStack(spacing: Spacing.s0) {
            PkgTopBar(title: title, onBack: { dismiss() })
            PkgErrorState(message: message) { Task { await model.load() } }
        }
        .background(Theme.Color.appBg)
        .navigationBarBackButtonHidden(true)
    }
}

// MARK: - Event tile

private struct EventTile: View {
    let title: String
    let duration: String
    let icon: PantopusIcon
    let selected: Bool
    let accent: Color
    let accentBg: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Icon(icon, size: 15, color: selected ? accent : Theme.Color.appTextSecondary)
                    Spacer()
                    if selected { Icon(.check, size: 14, strokeWidth: 3, color: accent) }
                }
                Text(title)
                    .font(.system(size: 11.5, weight: .bold)).foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                if !duration.isEmpty {
                    Text(duration).font(.system(size: 10)).foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 11).padding(.vertical, 10)
            .background(selected ? accentBg : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(selected ? accent : Theme.Color.appBorder, lineWidth: selected ? 1.5 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
