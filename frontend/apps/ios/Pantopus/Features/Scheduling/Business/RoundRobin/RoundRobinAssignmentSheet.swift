//
//  RoundRobinAssignmentSheet.swift
//  Pantopus
//
//  G1 Round-Robin Assignment (Stream I13) — bottom sheet on the Business violet
//  pillar. Matches `roundrobin-frames.jsx` (default / loading / none-selected /
//  single-member). CTA stays product sky. Tokens only.
//

import SwiftUI

struct RoundRobinAssignmentSheet: View {
    @State private var model: RoundRobinAssignmentViewModel
    let onClose: () -> Void

    init(owner: SchedulingOwner, eventTypeId: String, client: SchedulingClient = .shared, onClose: @escaping () -> Void) {
        _model = State(wrappedValue: RoundRobinAssignmentViewModel(owner: owner, eventTypeId: eventTypeId, client: client))
        self.onClose = onClose
    }

    var body: some View {
        VStack(spacing: Spacing.s0) {
            BizSheetHeader(title: "Assign bookings", subhead: "New bookings rotate across the members you pick.", onClose: onClose)
            content
            if case .ready = model.phase {
                BizSheetFooter {
                    BizPrimaryButton(
                        title: model.isSaving ? "Saving" : "Done",
                        isSaving: model.isSaving,
                        isDisabled: model.doneDisabled,
                        action: { Task { if await model.save() { onClose() } } }
                    )
                }
            }
        }
        .background(Theme.Color.appBg)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .task { await model.load() }
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .loading: loadingBody
        case .ready: readyBody
        case let .error(message): errorBody(message)
        }
    }

    private var readyBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s2) {
                ruleCard(.balanced, name: "Balanced", desc: "Spread bookings by weight", icon: .gauge)
                ruleCard(.priority, name: "Priority order", desc: "Fill the top of the list first", icon: .listChecks)
                ruleCard(.strict, name: "Strict round-robin", desc: "One each, strictly in turn", icon: .arrowsRepeat)

                BizOverline(text: "Bookable members", color: Theme.Color.appTextSecondary)
                    .padding(.top, Spacing.s2)

                if model.checkedCount == 0 {
                    BizNote(tone: .warning, icon: .alertTriangle, text: "Pick at least one member to take bookings.")
                }

                BizCard {
                    VStack(spacing: Spacing.s0) {
                        ForEach(Array(model.picks.enumerated()), id: \.element.id) { idx, pick in
                            seatRow(pick)
                            if idx < model.picks.count - 1 { BizRowDivider() }
                        }
                    }
                }

                if model.isSingleMember {
                    BizNote(tone: .biz, icon: .info,
                            text: "Rotation needs two or more members. Bookings go to \(model.firstCheckedName ?? "this member") for now.")
                } else if model.checkedCount >= 2 {
                    BizNote(tone: .biz, icon: .arrowsRepeat,
                            text: "New bookings rotate across \(model.checkedCount) members, weighted by your settings.")
                }

                if let saveError = model.saveError {
                    BizNote(tone: .error, icon: .alertTriangle, text: saveError)
                }

                Color.clear.frame(height: Spacing.s2)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s1)
        }
    }

    private func ruleCard(_ rule: RoundRobinAssignmentViewModel.Rule, name: String, desc: String, icon: PantopusIcon) -> some View {
        let selected = model.selectedRule == rule
        return Button { model.selectRule(rule) } label: {
            HStack(spacing: 11) {
                ZStack {
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(selected ? Theme.Color.appSurface : Theme.Color.appSurfaceSunken)
                    Icon(icon, size: 16, color: selected ? Theme.Color.business : Theme.Color.appTextSecondary)
                }
                .frame(width: 32, height: 32)
                VStack(alignment: .leading, spacing: 1) {
                    Text(name).font(.system(size: 13, weight: .bold)).foregroundStyle(Theme.Color.appText)
                    Text(desc).font(.system(size: 11)).foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer(minLength: Spacing.s2)
                ZStack {
                    Circle().stroke(selected ? Color.clear : Theme.Color.appBorderStrong, lineWidth: 1.5)
                    if selected {
                        Circle().fill(Theme.Color.business)
                        Icon(.check, size: 12, strokeWidth: 3.2, color: Theme.Color.appTextInverse)
                    }
                }
                .frame(width: 20, height: 20)
            }
            .padding(.horizontal, Spacing.s3).padding(.vertical, 11)
            .background(selected ? Theme.Color.businessBg : Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                    .stroke(selected ? Theme.Color.business : Theme.Color.appBorder, lineWidth: selected ? 1.5 : 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }

    private func seatRow(_ pick: RoundRobinAssignmentViewModel.Pick) -> some View {
        HStack(spacing: 11) {
            Button { model.toggle(pick.id) } label: {
                BizCheckbox(on: pick.checked)
            }
            .buttonStyle(.plain)
            BizAvatar(name: pick.name, imageURL: pick.avatarURL, size: 34)
            VStack(alignment: .leading, spacing: 1) {
                Text(pick.name).font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.Color.appText).lineLimit(1)
                Text(pick.role ?? "Uses personal availability")
                    .font(.system(size: 10.5)).foregroundStyle(Theme.Color.appTextSecondary).lineLimit(1)
            }
            Spacer(minLength: Spacing.s2)
            trailing(pick)
        }
        .padding(.vertical, Spacing.s3)
        .opacity(pick.checked ? 1 : 0.55)
    }

    @ViewBuilder
    private func trailing(_ pick: RoundRobinAssignmentViewModel.Pick) -> some View {
        if pick.checked {
            switch model.selectedRule {
            case .balanced:
                BizStepper(
                    value: pick.weight,
                    accent: Theme.Color.business,
                    prefix: "×",
                    canDecrement: pick.weight > 1,
                    canIncrement: pick.weight < 9,
                    onDecrement: { model.decrementWeight(pick.id) },
                    onIncrement: { model.incrementWeight(pick.id) }
                )
            case .priority:
                Menu {
                    Button("Move up") { model.moveUp(pick.id) }
                    Button("Move down") { model.moveDown(pick.id) }
                } label: {
                    Icon(.gripVertical, size: 20, color: Theme.Color.appTextMuted)
                }
                .accessibilityLabel("Reorder \(pick.name)")
            case .strict:
                EmptyView()
            }
        } else {
            EmptyView()
        }
    }

    private var loadingBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s2) {
                ForEach(0..<3, id: \.self) { _ in Shimmer(height: 56, cornerRadius: Radii.xl) }
                BizOverline(text: "Bookable members", color: Theme.Color.appTextSecondary).padding(.top, Spacing.s2)
                BizCard {
                    VStack(spacing: Spacing.s0) {
                        ForEach(0..<4, id: \.self) { idx in
                            BizShimmerRow(showCheckbox: true, showTrailingPill: true)
                            if idx < 3 { BizRowDivider() }
                        }
                    }
                }
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s1)
        }
    }

    private func errorBody(_ message: String) -> some View {
        VStack(spacing: Spacing.s3) {
            Spacer()
            Icon(.cloudOff, size: 28, strokeWidth: 1.8, color: Theme.Color.appTextSecondary)
            Text(message).font(.system(size: 13.5)).foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center).frame(maxWidth: 240)
            Button { Task { await model.load() } } label: {
                Text("Try again").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.Color.appTextStrong)
                    .padding(.horizontal, Spacing.s4).padding(.vertical, Spacing.s2)
                    .overlay(Capsule().stroke(Theme.Color.appBorder, lineWidth: 1))
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, Spacing.s8)
    }
}
