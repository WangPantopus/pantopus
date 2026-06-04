//
//  SavedPlacesView.swift
//  Pantopus
//
//  BLOCK 2E — "Saved places" (places you bookmark from Explore). A pushed
//  sub-route modelled on the Following screen: back chevron, centred title +
//  count line, an optional All · Home · Work · Saved filter-chip row, and the
//  list of saved-place rows. Each row taps through to the place on the map,
//  exposes an overflow action sheet (Open on map / Share place / Remove), and
//  — on iOS — a swipe-to-reveal Open / Remove shortcut. Removal is optimistic
//  and offers an Undo snackbar.
//

import SwiftUI

public struct SavedPlacesView: View {
    @State private var viewModel: SavedPlacesViewModel

    public init(viewModel: SavedPlacesViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        @Bindable var bindable = viewModel
        return VStack(spacing: 0) {
            topBar
            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.Color.appSurfaceMuted)
        .navigationBarHidden(true)
        .accessibilityIdentifier("savedPlaces.screen")
        .task { await viewModel.load() }
        .sheet(item: $bindable.actionTarget) { target in
            SavedPlacesActionSheet(
                target: target,
                onOpenMap: { viewModel.openMap(target) },
                onRemove: { Task { await viewModel.remove(target) } },
                onCancel: { viewModel.closeActions() }
            )
        }
        .overlay(alignment: .bottom) { undoOverlay }
        .overlay(alignment: .bottom) { toastOverlay }
    }

    // MARK: - Top bar

    private var topBar: some View {
        ZStack {
            HStack {
                Button { viewModel.back() } label: {
                    Icon(.chevronLeft, size: 25, strokeWidth: 2.2, color: Theme.Color.appText)
                        .frame(width: 40, height: 40)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
                .accessibilityIdentifier("savedPlaces.back")
                Spacer()
            }
            VStack(spacing: 1) {
                Text("Saved places")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                if let line = countLine {
                    Text(line)
                        .font(.system(size: 11.5))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
        }
        .frame(height: 54)
        .padding(.horizontal, Spacing.s2)
        .background(Theme.Color.appSurfaceMuted)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
    }

    private var countLine: String? {
        switch viewModel.state {
        case let .loaded(_, _, total):
            return "\(total) place\(total == 1 ? "" : "s")"
        case .empty:
            return nil
        case .loading, .error:
            return nil
        }
    }

    // MARK: - Content

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            loadingList
        case let .loaded(rows, filters, _):
            VStack(spacing: 0) {
                if filters.count > 2 {
                    filterChips(filters)
                }
                loadedList(rows)
            }
        case .empty:
            emptyState
        case let .error(message):
            errorState(message)
        }
    }

    // MARK: - Filter chips

    private func filterChips(_ filters: [SavedPlaceFilter]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.s2) {
                ForEach(filters) { filter in
                    let active = filter == viewModel.selectedFilter
                    Button { viewModel.selectFilter(filter) } label: {
                        Text(filter.label)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(active ? Theme.Color.appTextInverse : Theme.Color.appTextStrong)
                            .padding(.horizontal, Spacing.s4)
                            .frame(height: 32)
                            .background(
                                Capsule().fill(active ? Theme.Color.primary600 : Theme.Color.appSurface)
                            )
                            .overlay(
                                Capsule().stroke(
                                    active ? Color.clear : Theme.Color.appBorder,
                                    lineWidth: 1
                                )
                            )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier(filter.accessibilityID)
                }
            }
            .padding(.horizontal, Spacing.s4)
        }
        .padding(.vertical, Spacing.s3)
        .background(Theme.Color.appSurfaceMuted)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
        .accessibilityIdentifier("savedPlaces.filterChips")
    }

    // MARK: - Loaded list

    private func loadedList(_ rows: [SavedPlaceRow]) -> some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                    if index > 0 {
                        Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1).padding(.leading, 70)
                    }
                    SavedPlaceRowView(
                        row: row,
                        onTap: { viewModel.openMap(row.actionTarget) },
                        onOverflow: { viewModel.openActions(for: row) },
                        onSwipeOpen: { viewModel.openMap(row.actionTarget) },
                        onSwipeRemove: { Task { await viewModel.remove(row.actionTarget) } }
                    )
                }
            }
            .background(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous).fill(Theme.Color.appSurface))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.xl, style: .continuous).stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
            .padding(.horizontal, Spacing.s3)
            .padding(.top, Spacing.s3)
            .padding(.bottom, Spacing.s5)
        }
        .refreshable { await viewModel.refresh() }
    }

    // MARK: - Loading

    private var loadingList: some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(0..<6, id: \.self) { _ in
                    SavedPlaceSkeletonRow()
                }
            }
            .padding(Spacing.s3)
            .background(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous).fill(Theme.Color.appSurface))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.xl, style: .continuous).stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .padding(Spacing.s3)
        }
        .disabled(true)
        .accessibilityIdentifier("savedPlaces.loading")
    }

    // MARK: - Empty

    private var emptyState: some View {
        VStack(spacing: Spacing.s3) {
            ZStack {
                Circle().fill(Theme.Color.primary50).frame(width: 76, height: 76)
                Icon(.bookmark, size: 32, strokeWidth: 1.7, color: Theme.Color.primary600)
            }
            Text("No saved places yet")
                .font(.system(size: 20, weight: .bold))
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.Color.appText)
            Text("Save spots you visit often from Explore \u{2014} your home, your go-to coffee shop, the park down the block.")
                .font(.system(size: 13.5))
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(maxWidth: 280)
            Button { viewModel.explore() } label: {
                HStack(spacing: Spacing.s2) {
                    Icon(.compass, size: 16, strokeWidth: 2.4, color: Theme.Color.appTextInverse)
                    Text("Explore nearby")
                        .font(.system(size: 14.5, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .padding(.horizontal, Spacing.s6)
                .frame(height: 46)
                .background(Capsule().fill(Theme.Color.primary600))
            }
            .buttonStyle(.plain)
            .padding(.top, Spacing.s1)
            .accessibilityIdentifier("savedPlaces.exploreNearbyBtn")
        }
        .padding(.horizontal, Spacing.s6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Color.appSurfaceMuted)
        .accessibilityIdentifier("savedPlaces.empty")
    }

    // MARK: - Error

    private func errorState(_ message: String) -> some View {
        VStack(spacing: Spacing.s3) {
            Icon(.alertCircle, size: 34, color: Theme.Color.appTextMuted)
            Text("Couldn\u{2019}t load your saved places")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .font(.system(size: 13.5))
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(maxWidth: 280)
            Button { Task { await viewModel.refresh() } } label: {
                Text("Retry")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s5)
                    .frame(height: 44)
                    .background(Capsule().fill(Theme.Color.primary600))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("savedPlaces.error.retry")
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Color.appSurfaceMuted)
        .accessibilityIdentifier("savedPlaces.error")
    }

    // MARK: - Undo snackbar

    @ViewBuilder private var undoOverlay: some View {
        if let undo = viewModel.undo {
            HStack(spacing: Spacing.s3) {
                Icon(.checkCircle, size: 18, color: Theme.Color.appTextInverse)
                Text("Removed \u{201C}\(undo.dto.label)\u{201D}")
                    .font(.system(size: 13.5, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .lineLimit(1)
                Spacer(minLength: Spacing.s2)
                Button { Task { await viewModel.undoRemove() } } label: {
                    Text("Undo")
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(Theme.Color.primary300)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("savedPlaces.undoSnackbar.undo")
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s3)
            .background(Capsule().fill(Theme.Color.appText.opacity(0.95)))
            .padding(.horizontal, Spacing.s4)
            .padding(.bottom, Spacing.s8)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .task(id: undo) {
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                viewModel.dismissUndo()
            }
            .accessibilityIdentifier("savedPlaces.undoSnackbar")
        }
    }

    // MARK: - Toast

    @ViewBuilder private var toastOverlay: some View {
        if let toast = viewModel.toast {
            ToastView(message: toast)
                .padding(.bottom, Spacing.s12)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .task(id: toast) {
                    try? await Task.sleep(nanoseconds: 2_500_000_000)
                    viewModel.toast = nil
                }
                .accessibilityIdentifier("savedPlaces.toast")
        }
    }
}

// MARK: - Row

struct SavedPlaceRowView: View {
    let row: SavedPlaceRow
    let onTap: @MainActor () -> Void
    let onOverflow: @MainActor () -> Void
    let onSwipeOpen: @MainActor () -> Void
    let onSwipeRemove: @MainActor () -> Void

    /// Two 78pt action buttons revealed by the trailing swipe.
    private let revealWidth: CGFloat = 156
    @State private var offset: CGFloat = 0
    @GestureState private var dragging: CGFloat = 0

    var body: some View {
        ZStack(alignment: .trailing) {
            swipeActions
            rowContent
                .background(Theme.Color.appSurface)
                .offset(x: clampedOffset)
                .highPriorityGesture(swipeGesture)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("savedPlaces.row.\(row.id)")
    }

    private var clampedOffset: CGFloat {
        min(0, max(-revealWidth, offset + dragging))
    }

    // MARK: Main row

    private var rowContent: some View {
        HStack(spacing: Spacing.s3) {
            Button(action: onTap) {
                HStack(spacing: Spacing.s3) {
                    typeTile
                    textColumn
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            overflowButton
        }
        .padding(.leading, 14)
        .padding(.trailing, Spacing.s3)
        .padding(.vertical, 11)
    }

    private var typeTile: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .fill(row.type.tileBackground)
            Icon(row.type.icon, size: 20, color: row.type.tileForeground)
        }
        .frame(width: 44, height: 44)
    }

    private var textColumn: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(row.label)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(1)
            HStack(spacing: Spacing.s2) {
                Text(row.subtitle)
                    .font(.system(size: 11.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(1)
                if let pill = row.type.pillLabel {
                    typePill(pill)
                }
            }
            Text(row.savedCaption)
                .font(.system(size: 11))
                .foregroundStyle(Theme.Color.appTextMuted)
                .lineLimit(1)
                .padding(.top, 1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func typePill(_ label: String) -> some View {
        Text(label)
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(row.type.tileForeground)
            .padding(.horizontal, 6)
            .padding(.vertical, 1)
            .background(Capsule().fill(row.type.tileBackground))
    }

    private var overflowButton: some View {
        Button(action: onOverflow) {
            Icon(.moreHorizontal, size: 18, color: Theme.Color.appTextMuted)
                .frame(width: 28, height: 28)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("More")
        .accessibilityIdentifier("savedPlaces.row.overflow")
    }

    // MARK: Swipe-to-reveal (iOS secondary shortcut)

    private var swipeActions: some View {
        HStack(spacing: 0) {
            swipeButton(
                icon: .map,
                label: "Open",
                tint: Theme.Color.primary600,
                id: "savedPlaces.row.swipeOpen"
            ) {
                reset()
                onSwipeOpen()
            }
            swipeButton(
                icon: .trash2,
                label: "Remove",
                tint: Theme.Color.error,
                id: "savedPlaces.row.swipeRemove"
            ) {
                reset()
                onSwipeRemove()
            }
        }
        .frame(width: revealWidth)
        .opacity(clampedOffset < -2 ? 1 : 0)
    }

    private func swipeButton(
        icon: PantopusIcon,
        label: String,
        tint: Color,
        id: String,
        action: @escaping @MainActor () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 3) {
                Icon(icon, size: 17, color: Theme.Color.appTextInverse)
                Text(label)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextInverse)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(tint)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }

    private var swipeGesture: some Gesture {
        DragGesture(minimumDistance: 14)
            .updating($dragging) { value, state, _ in
                // Only react to predominantly-horizontal drags so the parent
                // ScrollView keeps vertical scrolling.
                if abs(value.translation.width) > abs(value.translation.height) {
                    state = value.translation.width
                }
            }
            .onEnded { value in
                let projected = offset + value.translation.width
                withAnimation(.interpolatingSpring(stiffness: 320, damping: 30)) {
                    offset = projected < -revealWidth / 2 ? -revealWidth : 0
                }
            }
    }

    private func reset() {
        withAnimation(.interpolatingSpring(stiffness: 320, damping: 30)) {
            offset = 0
        }
    }
}

// MARK: - Action sheet

/// BLOCK 2E Frame 3 — the row overflow action sheet: Open on map / Share
/// place / Remove (destructive). Driven by the VM's `actionTarget` binding.
struct SavedPlacesActionSheet: View {
    let target: SavedPlaceActionTarget
    let onOpenMap: @MainActor () -> Void
    let onRemove: @MainActor () -> Void
    let onCancel: @MainActor () -> Void

    /// System-share payload — the place name + an Apple Maps coordinate link.
    private var shareText: String {
        "\(target.label) \u{2014} https://maps.apple.com/?ll=\(target.latitude),\(target.longitude)"
    }

    var body: some View {
        VStack(spacing: Spacing.s2) {
            actionsCard
            cancelCard
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, Spacing.s3)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .background(Theme.Color.appSurfaceMuted)
        .presentationDetents([.height(320)])
        .presentationDragIndicator(.visible)
    }

    private var actionsCard: some View {
        VStack(spacing: 0) {
            contextHeader
            divider
            sheetRow(icon: .map, label: "Open on map", id: "savedPlaces.action.openMap") {
                onOpenMap()
            }
            divider
            ShareLink(item: shareText) {
                HStack(spacing: Spacing.s3) {
                    Icon(.share, size: 20, color: Theme.Color.appText)
                    Text("Share place")
                        .font(.system(size: 15.5, weight: .medium))
                        .foregroundStyle(Theme.Color.appText)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.vertical, Spacing.s3)
                .contentShape(Rectangle())
            }
            .accessibilityIdentifier("savedPlaces.action.share")
            divider
            sheetRow(icon: .trash2, label: "Remove", destructive: true, id: "savedPlaces.action.remove") {
                onRemove()
            }
        }
        .background(card)
        .accessibilityIdentifier("savedPlaces.actionSheet")
    }

    private var contextHeader: some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(target.type.tileBackground)
                Icon(target.type.icon, size: 16, color: target.type.tileForeground)
            }
            .frame(width: 34, height: 34)
            VStack(alignment: .leading, spacing: 1) {
                Text(target.label)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                Text(target.subtitle)
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s3)
    }

    private func sheetRow(
        icon: PantopusIcon,
        label: String,
        destructive: Bool = false,
        id: String,
        action: @escaping @MainActor () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: Spacing.s3) {
                Icon(icon, size: 20, color: destructive ? Theme.Color.error : Theme.Color.appText)
                Text(label)
                    .font(.system(size: 15.5, weight: .medium))
                    .foregroundStyle(destructive ? Theme.Color.error : Theme.Color.appText)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s3)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }

    private var cancelCard: some View {
        Button(action: onCancel) {
            Text("Cancel")
                .font(.system(size: 15.5, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.s4)
                .background(card)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("savedPlaces.actionCancel")
    }

    private var divider: some View {
        Rectangle()
            .fill(Theme.Color.appBorderSubtle)
            .frame(height: 1)
            .padding(.leading, Spacing.s12)
    }

    private var card: some View {
        RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
            .fill(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
    }
}

// MARK: - Skeleton

struct SavedPlaceSkeletonRow: View {
    var body: some View {
        HStack(spacing: Spacing.s3) {
            RoundedRectangle(cornerRadius: Radii.lg).fill(Theme.Color.appSurfaceSunken).frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: Spacing.s2) {
                RoundedRectangle(cornerRadius: Radii.xs).fill(Theme.Color.appSurfaceSunken).frame(width: 140, height: 12)
                RoundedRectangle(cornerRadius: Radii.xs).fill(Theme.Color.appSurfaceSunken).frame(width: 90, height: 10)
                RoundedRectangle(cornerRadius: Radii.xs).fill(Theme.Color.appSurfaceSunken).frame(width: 110, height: 10)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 11)
        .redacted(reason: .placeholder)
    }
}

#if DEBUG
#Preview("Populated") {
    NavigationStack { SavedPlacesView(viewModel: .previewLoaded()) }
}

#Preview("Empty") {
    NavigationStack { SavedPlacesView(viewModel: .previewEmpty()) }
}
#endif
