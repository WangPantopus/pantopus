//
//  UnboxingView.swift
//  Pantopus
//
//  A17.14 — Unboxing scan-capture flow. A scan-first surface in the A17
//  archetype: a custom nav (back · "Unboxing" eyebrow · gallery/overflow),
//  a status-chip header row, and a phase-dependent body over a sticky
//  action shelf.
//
//    `.capture` — `CaptureFilmstrip` (CameraScanner viewfinder + filmstrip)
//      · AI elf · `DrawerSuggestionCard` · `OcrFactsList` (editable) ·
//      Confirm shelf.
//    `.filed`   — `FiledSummary` (banner + photo summary) · AI elf ·
//      `OcrFactsList` (locked) · `ScanNextCard` · View-in-drawer shelf.
//
//  The `CameraScanner` viewfinder falls back to a static placeholder under
//  the simulator / when camera access is off, and the scan-line honors
//  Reduce Motion — so snapshots are deterministic. Mirrors `UnboxingScreen`
//  on Android.
//

import SwiftUI

public struct UnboxingView: View {
    @State private var viewModel: UnboxingViewModel
    private let reduceMotionOverride: Bool?
    private let onBack: @MainActor () -> Void

    private let accent = Theme.Color.categoryUnboxing
    private let accentDark = Theme.Color.categoryUnboxingDark
    private let accentBg = Theme.Color.categoryUnboxingBg

    /// Split init (see GigsFeedView): a defaulted `= UnboxingViewModel()`
    /// tripped a Swift 6.1.2 / Xcode 16.4 SILGen crash in the default-argument
    /// generator. Constructing the view-model in the convenience init's body
    /// avoids that path; behaviour is unchanged.
    public init(
        viewModel: UnboxingViewModel,
        reduceMotionOverride: Bool? = nil,
        onBack: @escaping @MainActor () -> Void = {}
    ) {
        _viewModel = State(initialValue: viewModel)
        self.reduceMotionOverride = reduceMotionOverride
        self.onBack = onBack
    }

    public init(
        reduceMotionOverride: Bool? = nil,
        onBack: @escaping @MainActor () -> Void = {}
    ) {
        self.init(
            viewModel: UnboxingViewModel(),
            reduceMotionOverride: reduceMotionOverride,
            onBack: onBack
        )
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: Spacing.s0) {
                UnboxNav(accent: accent, onBack: onBack)
                ScrollView {
                    scrollBody
                        .padding(.horizontal, Spacing.s4)
                        .padding(.top, Spacing.s3)
                        .padding(.bottom, 150)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .background(Theme.Color.appBg)
            }
            actionShelf
        }
        .background(Theme.Color.appBg)
        // The screen paints its own nav + status header; hide the system bar
        // so there's no double chrome (matches GigSearchView).
        .toolbar(.hidden, for: .navigationBar)
        .accessibilityIdentifier("unboxing")
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .task { await viewModel.load() }
    }

    // MARK: - Body per phase

    @ViewBuilder
    private var scrollBody: some View {
        switch viewModel.state {
        case let .capture(content):
            captureBody(content)
        case let .filed(content):
            filedBody(content)
        }
    }

    private func captureBody(_ content: UnboxingContent) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            headerRow(content: content, filed: false)
            CaptureFilmstrip(
                accent: accent,
                shots: viewModel.shots,
                reduceMotionOverride: reduceMotionOverride,
                onCapture: { viewModel.capture() },
                onAddShot: { viewModel.capture() }
            )
            AIElfStripView(content: content.classifyElf)
                .accessibilityIdentifier("unboxing_elf")
            DrawerSuggestionCard(
                accent: accent,
                accentDark: accentDark,
                accentBg: accentBg,
                suggestion: content.suggestion,
                alternates: content.alternates,
                onSelectAlternate: { _ in },
                onChooseAnother: {}
            )
            OcrFactsList(
                title: "Read from your scans",
                status: OcrFactsStatus(icon: .scanLine, text: "Tap to edit", tone: .neutral),
                facts: content.facts
            )
            .accessibilityIdentifier("unboxing_facts")
        }
    }

    private func filedBody(_ content: UnboxingContent) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            headerRow(content: content, filed: true)
            FiledSummary(
                filedTo: content.filedTo,
                filedSubtitle: content.filedSubtitle,
                shots: viewModel.shots,
                photosSavedLabel: content.photosSavedLabel,
                onUndo: { viewModel.undo() },
                onViewPhotos: {}
            )
            AIElfStripView(content: content.filedElf)
                .accessibilityIdentifier("unboxing_elf")
            OcrFactsList(
                title: "Read from your scans",
                status: OcrFactsStatus(icon: .lock, text: "Saved", tone: .success),
                facts: content.facts
            )
            .accessibilityIdentifier("unboxing_facts")
            ScanNextCard(
                accent: accent,
                accentDark: accentDark,
                accentBg: accentBg
            ) { viewModel.scanNext() }
        }
    }

    // MARK: - Header row

    private func headerRow(content: UnboxingContent, filed: Bool) -> some View {
        HStack(spacing: Spacing.s1) {
            if filed {
                StateChip(icon: .checkCircle, label: "Filed", filed: true)
            } else {
                StateChip(icon: .scanLine, label: "New capture", filed: false)
            }
            categoryChip(label: content.category)
            Spacer(minLength: Spacing.s2)
            Text(content.timeLabel)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }

    private func categoryChip(label: String) -> some View {
        HStack(spacing: Spacing.s1) {
            Circle().fill(accent).frame(width: 6, height: 6)
            Text(label)
                .font(.system(size: 11, weight: .bold))
                .tracking(0.4)
                .foregroundStyle(Theme.Color.appTextStrong)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 3)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(Capsule())
    }

    // MARK: - Action shelf

    private var actionShelf: some View {
        Group {
            switch viewModel.state {
            case .capture:
                UnboxActions(accent: accent) { viewModel.confirm() }
            case .filed:
                FiledActions { viewModel.openDrawer() }
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s3)
        .padding(.bottom, Spacing.s4)
        .frame(maxWidth: .infinity)
        .background(
            Theme.Color.appSurface
                .overlay(alignment: .top) {
                    Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
                }
                .ignoresSafeArea(edges: .bottom)
        )
    }
}

// MARK: - Top nav

private struct UnboxNav: View {
    let accent: Color
    let onBack: @MainActor () -> Void

    var body: some View {
        HStack(spacing: Spacing.s1) {
            Button {
                onBack()
            } label: {
                HStack(spacing: Spacing.s0) {
                    Icon(.chevronLeft, size: 22, color: Theme.Color.primary600)
                    Text("Mailbox")
                        .font(.system(size: 15, weight: .regular))
                        .foregroundStyle(Theme.Color.primary600)
                }
                .padding(.horizontal, Spacing.s1)
                .frame(minHeight: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back to Mailbox")
            .accessibilityIdentifier("unboxing_back")

            Spacer()

            HStack(spacing: Spacing.s1) {
                Circle().fill(accent).frame(width: 8, height: 8)
                Text("UNBOXING")
                    .font(.system(size: 12, weight: .bold))
                    .tracking(0.5)
                    .foregroundStyle(Theme.Color.appTextStrong)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("unboxing_eyebrow")

            Spacer()

            HStack(spacing: 2) {
                navIcon(.image, label: "Photo library")
                navIcon(.moreHorizontal, label: "More actions")
            }
        }
        .padding(.horizontal, Spacing.s2)
        .frame(height: 44)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }

    private func navIcon(_ icon: PantopusIcon, label: String) -> some View {
        Button {} label: {
            Icon(icon, size: 18, color: Theme.Color.appTextStrong)
                .frame(width: 34, height: 34)
                .background(Circle().fill(Theme.Color.appSurfaceSunken))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// MARK: - State chip

private struct StateChip: View {
    let icon: PantopusIcon
    let label: String
    let filed: Bool

    private var foreground: Color {
        filed ? Theme.Color.success : Theme.Color.categoryUnboxingDark
    }

    private var background: Color {
        filed ? Theme.Color.successBg : Theme.Color.categoryUnboxingBg
    }

    private var border: Color {
        filed ? Theme.Color.successLight : Theme.Color.categoryUnboxingBorder
    }

    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(icon, size: 11, strokeWidth: 2, color: foreground)
            Text(label)
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(foreground)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 3)
        .background(background)
        .clipShape(Capsule())
        .overlay(Capsule().stroke(border, lineWidth: 1))
        .accessibilityIdentifier("unboxing_stateChip")
    }
}

// MARK: - Action bars

private struct UnboxActions: View {
    let accent: Color
    let onConfirm: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s2) {
            PrimaryActionButton(
                icon: .checkCheck,
                label: "Confirm — file to Home",
                background: accent,
                action: onConfirm
            )
            .accessibilityIdentifier("unboxing_confirm")
            HStack(spacing: Spacing.s2) {
                UbChip(icon: .arrowsRepeat, label: "Retake") {}
                UbChip(icon: .pencil, label: "Edit facts") {}
                UbChip(icon: .messageSquare, label: "Add note") {}
                UbChip(icon: .trash2, label: "Discard") {}
            }
        }
    }
}

private struct FiledActions: View {
    let onOpenDrawer: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s2) {
            PrimaryActionButton(
                icon: .folderLock,
                label: "View in Home drawer",
                background: Theme.Color.primary600,
                action: onOpenDrawer
            )
            .accessibilityIdentifier("unboxing_viewInDrawer")
            HStack(spacing: Spacing.s2) {
                UbChip(icon: .package, label: "Open record") {}
                UbChip(icon: .share, label: "Share") {}
                UbChip(icon: .bell, label: "Reminders") {}
                UbChip(icon: .archive, label: "Archive") {}
            }
        }
    }
}

private struct PrimaryActionButton: View {
    let icon: PantopusIcon
    let label: String
    let background: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.s2) {
                Icon(icon, size: 17, strokeWidth: 2.4, color: .white)
                Text(label)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .shadow(color: background.opacity(0.22), radius: 12, x: 0, y: 6)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

private struct UbChip: View {
    let icon: PantopusIcon
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: Spacing.s1) {
                Icon(icon, size: 17, strokeWidth: 2, color: Theme.Color.appTextSecondary)
                Text(label)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.s1)
            .padding(.vertical, 10)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

#if DEBUG
#Preview("Capture") {
    NavigationStack {
        UnboxingView(viewModel: UnboxingViewModel(phase: .capture), reduceMotionOverride: true)
    }
}

#Preview("Filed") {
    NavigationStack {
        UnboxingView(viewModel: UnboxingViewModel(phase: .filed), reduceMotionOverride: true)
    }
}
#endif
