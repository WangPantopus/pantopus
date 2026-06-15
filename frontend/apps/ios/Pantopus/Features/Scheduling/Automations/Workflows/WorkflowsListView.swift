//
//  WorkflowsListView.swift
//  Pantopus
//
//  Stream I16 — H2 Workflows List (full screen). Mirrors `workflows-list-frames.jsx`:
//  a top bar + A08 scope tab strip (Global / This event type), a pinned "Default
//  reminders" card that opens the H1 sheet, a "Your workflows" group of rows
//  (trigger glyph, plain-English trigger, action + channel, status pill, iOS
//  toggle), and a create FAB. Frames: populated · empty (pinned card + "Add a
//  follow-up") · loading shimmer · error retry · permission-gated. Accent follows
//  the owner pillar; functional chrome stays product sky.
//

import SwiftUI

struct WorkflowsListView: View {
    @State private var model: WorkflowsListViewModel
    @Environment(\.dismiss) private var dismiss

    init(
        owner: SchedulingOwner,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient = .shared
    ) {
        _model = State(wrappedValue: WorkflowsListViewModel(owner: owner, push: push, client: client))
    }

    var body: some View {
        VStack(spacing: Spacing.s0) {
            AutoTopBar(
                title: "Workflows",
                leading: .back,
                onLeading: { dismiss() },
                trailing: AnyView(
                    Button { model.createWorkflow() } label: {
                        Icon(.plus, size: 19, strokeWidth: 2.2, color: Theme.Color.primary600)
                    }
                    .accessibilityLabel("New workflow")
                )
            )
            AutoUnderlineTabs(
                tabs: ["Global", "This event type"],
                selectedIndex: model.scope.rawValue,
                accent: model.accent,
                onSelect: { model.selectScope($0) }
            )
            content
        }
        .background(Theme.Color.appBg)
        .navigationBarBackButtonHidden(true)
        .overlay(alignment: .bottomTrailing) { fab }
        .task { await model.load() }
        .onAppear { if case .loaded = model.phase { Task { await model.refresh() } } }
        .refreshable { await model.refresh() }
        .sheet(isPresented: $model.showRemindersSheet, onDismiss: { model.remindersSheetDismissed() }) {
            DefaultRemindersView(owner: model.owner, onClose: { model.showRemindersSheet = false })
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .alert("Heads up", isPresented: actionErrorPresented) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(model.actionError ?? "")
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .accessibilityIdentifier("scheduling.workflows.list")
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .loading:
            loadingBody
        case .loaded:
            loadedBody
        case let .error(message):
            AutoErrorView(headline: "Couldn't load workflows", message: message) {
                Task { await model.load() }
            }
        }
    }

    // MARK: Loaded

    private var loadedBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if model.isGated { gatedBanner }
                if !model.isGated { remindersGroup }
                workflowsGroup
                Color.clear.frame(height: 80)
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.top, Spacing.s3)
        }
    }

    private var remindersGroup: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            AutoOverline(text: "Reminders").padding(.horizontal, 2)
            AutoCard(padding: EdgeInsets(top: 4, leading: 13, bottom: 4, trailing: 13)) {
                Button { model.openDefaultReminders() } label: {
                    HStack(spacing: 11) {
                        autoIconTile(.bell, bg: model.accentBg, fg: model.accent)
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Default reminders").font(.system(size: 13, weight: .bold)).foregroundStyle(Theme.Color.appText)
                            Text(model.remindersSummary).font(.system(size: 11)).foregroundStyle(Theme.Color.appTextSecondary).lineLimit(1)
                        }
                        Spacer(minLength: Spacing.s2)
                        Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
                    }
                    .padding(.vertical, 11)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("workflows.defaultRemindersCard")
            }
        }
    }

    private var workflowsGroup: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            AutoOverline(text: "Your workflows").padding(.horizontal, 2)
            if model.visibleWorkflows.isEmpty {
                emptyPrompt
            } else {
                AutoCard {
                    VStack(spacing: Spacing.s0) {
                        ForEach(Array(model.visibleWorkflows.enumerated()), id: \.element.id) { idx, workflow in
                            workflowRow(workflow)
                            if idx < model.visibleWorkflows.count - 1 { AutoRowDivider() }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var emptyPrompt: some View {
        if model.scope == .global {
            AutoInlineEmpty(
                icon: .zap,
                headline: "No follow-ups yet",
                subcopy: "Reminders are handled. Add a thank-you or a review request to run automatically.",
                accent: model.accent,
                accentBg: model.accentBg,
                ctaTitle: model.isGated ? nil : "Add a follow-up",
                onCTA: model.isGated ? nil : { model.createWorkflow() }
            )
        } else {
            AutoInlineEmpty(
                icon: .zap,
                headline: "No event-type workflows",
                subcopy: "Workflows scoped to a single event type show up here. Add one from its editor.",
                accent: model.accent,
                accentBg: model.accentBg
            )
        }
    }

    private func workflowRow(_ workflow: WorkflowDTO) -> some View {
        let trigger = WorkflowTrigger(wire: workflow.trigger)
        let channel = WorkflowChannel(wire: workflow.action)
        return HStack(spacing: 11) {
            Button { model.openWorkflow(workflow) } label: {
                HStack(spacing: 11) {
                    autoIconTile(trigger.icon, bg: Theme.Color.appSurfaceSunken, fg: Theme.Color.appTextStrong)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(trigger.summary(offsetMinutes: workflow.offsetMinutes ?? 0))
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                            .lineLimit(1)
                        HStack(spacing: 5) {
                            Icon(channel.icon, size: 12, color: Theme.Color.appTextMuted)
                            Text(workflowActionLabel(workflow, channel: channel))
                                .font(.system(size: 10.5))
                                .foregroundStyle(Theme.Color.appTextSecondary)
                                .lineLimit(1)
                        }
                        SchedulingStatusPill(status: model.statusKey(workflow))
                    }
                    Spacer(minLength: Spacing.s2)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if !model.isGated {
                Toggle("", isOn: Binding(
                    get: { model.isActive(workflow) },
                    set: { _ in Task { await model.toggleActive(workflow) } }
                ))
                .labelsHidden()
                .tint(model.accent)
                .accessibilityLabel("\(workflow.name) active")
            }
        }
        .padding(.vertical, 11)
        .opacity(model.isGated ? 0.5 : 1)
        .accessibilityIdentifier("workflows.row.\(workflow.id)")
    }

    /// Prefer the workflow's own name when set; fall back to the channel's
    /// implied action ("Email attendees").
    private func workflowActionLabel(_ workflow: WorkflowDTO, channel: WorkflowChannel) -> String {
        let name = workflow.name.trimmingCharacters(in: .whitespaces)
        return name.isEmpty ? channel.actionSummary : name
    }

    private var gatedBanner: some View {
        AutoNote(tone: .warning, icon: .lock, text: "Only admins can edit \(ownerWord) workflows.")
    }

    private var ownerWord: String {
        switch model.owner {
        case .home: "Home"
        case .business: "Business"
        case .personal: "these"
        }
    }

    // MARK: FAB

    @ViewBuilder
    private var fab: some View {
        if case .loaded = model.phase, !model.isGated {
            AutoFAB(accent: model.accent, shadow: model.theme.ctaShadow, accessibilityLabel: "New workflow") {
                model.createWorkflow()
            }
            .padding(.trailing, Spacing.s4)
            .padding(.bottom, Spacing.s6)
        }
    }

    // MARK: Loading

    private var loadingBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                AutoCard {
                    VStack(spacing: Spacing.s0) {
                        ForEach(0..<4, id: \.self) { idx in
                            AutoSkeletonRow()
                            if idx < 3 { AutoRowDivider() }
                        }
                    }
                }
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.top, Spacing.s4)
        }
    }

    private var actionErrorPresented: Binding<Bool> {
        Binding(get: { model.actionError != nil }, set: { if !$0 { model.actionError = nil } })
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        WorkflowsListView(owner: .personal, push: { _ in })
    }
}
#endif
