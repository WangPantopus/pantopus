//
//  VisitDetailView.swift
//  Pantopus
//
//  Stream I12 — F14 Visit Detail. Header + derived status + host members +
//  access note, with Reschedule / Edit / Cancel / Book-again actions. The edit
//  sheet writes through the home-event endpoints.
//

import SwiftUI

struct VisitDetailView: View {
    @State private var viewModel: VisitDetailViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: VisitDetailViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                loadingBody
            case .loaded:
                loadedBody
            case let .error(message):
                errorBody(message)
            case .removed:
                removedBody
            }
        }
        .background(Theme.Color.appBg)
        .navigationTitle("Visit")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if case .loaded = viewModel.state, viewModel.lifecycle == .confirmed {
                    Button("Edit") { viewModel.beginEdit() }
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Color.home)
                        .accessibilityIdentifier("scheduling.visitDetail.edit")
                }
            }
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .accessibilityIdentifier("scheduling.visitDetail")
        .task { await viewModel.load() }
        .sheet(isPresented: $viewModel.isEditing) {
            VisitEditSheet(viewModel: viewModel)
        }
        .alert("Couldn't update", isPresented: actionErrorPresented) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.actionError ?? "")
        }
        .confirmationDialog(
            "Cancel this visit?",
            isPresented: $viewModel.showCancelConfirm,
            titleVisibility: .visible
        ) {
            Button("Cancel visit", role: .destructive) {
                Task { if await viewModel.cancelVisit() { dismiss() } }
            }
            Button("Keep", role: .cancel) {}
        } message: {
            Text("This removes the visit from the family calendar.")
        }
    }

    // MARK: Loaded

    private var loadedBody: some View {
        VStack(spacing: Spacing.s0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s3) {
                    headerCard
                    banner
                    hostsCard
                    if viewModel.entryNote != nil {
                        accessCard
                    }
                }
                .padding(Spacing.s3)
                .padding(.bottom, Spacing.s10)
            }
            .refreshable { await viewModel.refresh() }
            footer
        }
    }

    private var headerCard: some View {
        SectionCard {
            HStack(spacing: Spacing.s3) {
                ZStack {
                    Circle().fill(
                        LinearGradient(
                            colors: [Theme.Color.home, Theme.Color.homeDark],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    Text(Self.initials(viewModel.title))
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text(viewModel.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    RuleChip(icon: viewModel.kind.icon, text: viewModel.kind.label, tone: .home)
                }
                Spacer()
            }
            HStack(spacing: Spacing.s2) {
                Icon(.clock, size: 14, color: timeColor)
                Text(viewModel.timeText)
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(timeColor)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appSurfaceSunken)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
    }

    @ViewBuilder private var banner: some View {
        switch viewModel.lifecycle {
        case .confirmed:
            calloutBanner(
                icon: .calendarCheck,
                title: "On the home calendar",
                body: "This visit shows on the family schedule.",
                bg: Theme.Color.homeBg,
                fg: Theme.Color.home
            )
        case .done:
            calloutBanner(
                icon: .check,
                title: "Visit complete",
                body: "This visit has passed.",
                bg: Theme.Color.appSurfaceSunken,
                fg: Theme.Color.appTextSecondary
            )
        }
    }

    private func calloutBanner(icon: PantopusIcon, title: String, body: String, bg: Color, fg: Color) -> some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            Icon(icon, size: 15, color: fg)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(fg)
                Text(body)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextStrong)
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.s3)
        .background(bg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private var hostsCard: some View {
        SectionCard(overline: "Host members") {
            HStack(spacing: Spacing.s2) {
                if viewModel.hostMembers.isEmpty {
                    Icon(.users, size: 18, color: Theme.Color.appTextMuted)
                } else {
                    HomeMemberStack(members: viewModel.hostMembers, size: 30)
                }
                Text(viewModel.hostSummary)
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Spacer(minLength: 0)
            }
        }
    }

    private var accessCard: some View {
        SectionCard {
            HStack(spacing: Spacing.s3) {
                Icon(.keyRound, size: 16, color: Theme.Color.appTextStrong)
                    .frame(width: 32, height: 32)
                    .background(Theme.Color.appSurfaceSunken)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    ResourceOverlineLabel(text: "Entry note")
                    Text(viewModel.entryNote ?? "")
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                }
                Spacer(minLength: 0)
            }
        }
    }

    @ViewBuilder private var footer: some View {
        HStack(spacing: Spacing.s2) {
            switch viewModel.lifecycle {
            case .confirmed:
                HomeSecondaryButton(title: "Cancel", icon: .x, tone: Theme.Color.error) {
                    viewModel.showCancelConfirm = true
                }
                HomePrimaryButton(title: "Reschedule", icon: .calendarClock) {
                    viewModel.beginEdit()
                }
            case .done:
                HomeSecondaryButton(title: "Book again", icon: .arrowsRepeat) {
                    viewModel.bookAgain()
                }
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s2)
        .padding(.bottom, Spacing.s3)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }

    // MARK: Loading / error / removed

    private var loadingBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                Shimmer(height: 110, cornerRadius: Radii.lg)
                Shimmer(height: 60, cornerRadius: Radii.lg)
                Shimmer(height: 70, cornerRadius: Radii.lg)
            }
            .padding(Spacing.s3)
        }
    }

    private func errorBody(_ message: String) -> some View {
        VStack(spacing: Spacing.s3) {
            Icon(.cloudOff, size: 40, color: Theme.Color.error)
            Text("Couldn't load this visit")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            HomePrimaryButton(title: "Retry", icon: .refreshCw) {
                Task { await viewModel.load() }
            }
            .frame(maxWidth: 200)
        }
        .padding(Spacing.s6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var removedBody: some View {
        VStack(spacing: Spacing.s3) {
            Icon(.xCircle, size: 40, color: Theme.Color.appTextMuted)
            Text("This visit was cancelled")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
            Text("It's no longer on the family calendar.")
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            HomeSecondaryButton(title: "Go back", icon: .chevronLeft) { dismiss() }
                .frame(maxWidth: 200)
        }
        .padding(Spacing.s6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var timeColor: Color {
        viewModel.lifecycle == .done ? Theme.Color.appTextSecondary : Theme.Color.home
    }

    private static func initials(_ title: String) -> String {
        let parts = title.split(separator: " ").prefix(2)
        let letters = parts.compactMap { $0.first }.map(String.init).joined()
        return letters.isEmpty ? "V" : letters.uppercased()
    }

    private var actionErrorPresented: Binding<Bool> {
        Binding(get: { viewModel.actionError != nil }, set: { if !$0 { viewModel.actionError = nil } })
    }
}

// MARK: - Edit sheet

/// Local edit/reschedule sheet for a concrete visit (no route — presented from
/// F14). Writes through `PUT …/events/:eventId`.
private struct VisitEditSheet: View {
    @Bindable var viewModel: VisitDetailViewModel

    var body: some View {
        FormShell(
            title: "Edit visit",
            leading: .close,
            rightActionLabel: "Save",
            isValid: viewModel.editValid,
            isDirty: true,
            isSaving: viewModel.isSavingEdit,
            onClose: { viewModel.isEditing = false },
            onCommit: { Task { await viewModel.saveEdit() } }
        ) {
            FormFieldGroup("Details") {
                TextField("Visit title", text: $viewModel.editTitle)
                    .font(Theme.Font.body)
                    .foregroundStyle(Theme.Color.appText)
                Divider().background(Theme.Color.appBorderSubtle)
                Text("Visit type")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextStrong)
                HStack(spacing: Spacing.s2) {
                    ForEach(VisitKind.allCases, id: \.self) { kind in
                        SelectChipIcon(label: kind.label, icon: kind.icon, isOn: kind == viewModel.editKind) {
                            viewModel.editKind = kind
                        }
                    }
                    Spacer()
                }
            }
            FormFieldGroup("Who must be home") {
                ForEach(Array(viewModel.members.enumerated()), id: \.element.id) { index, member in
                    Button { viewModel.toggleEditHost(member.id) } label: {
                        HStack(spacing: Spacing.s2) {
                            HomeMemberAvatar(member: member, size: 30)
                            Text(member.name)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Theme.Color.appText)
                            Spacer()
                            SelectionCheck(isOn: viewModel.editWhoIsHome.contains(member.id))
                        }
                    }
                    .buttonStyle(.plain)
                    if index < viewModel.members.count - 1 {
                        Divider().background(Theme.Color.appBorderSubtle)
                    }
                }
            }
            FormFieldGroup("When") {
                DatePicker("Date", selection: $viewModel.editDate, displayedComponents: .date)
                    .tint(Theme.Color.home)
                Divider().background(Theme.Color.appBorderSubtle)
                DatePicker("Start time", selection: $viewModel.editStart, displayedComponents: .hourAndMinute)
                    .tint(Theme.Color.home)
                Divider().background(Theme.Color.appBorderSubtle)
                CounterRow(label: "Visit length", value: $viewModel.editDuration, unit: "hr", range: 1...12)
            }
            FormFieldGroup("Access") {
                TextField("Entry note for the visitor", text: $viewModel.editNote)
                    .font(Theme.Font.body)
                    .foregroundStyle(Theme.Color.appText)
            }
        }
        .accessibilityIdentifier("scheduling.visitEdit")
    }
}
