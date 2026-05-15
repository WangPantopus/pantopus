//
//  GroupedListView.swift
//  Pantopus
//
//  Shell over `GroupedListDataSource`. Renders the three frames in
//  the Settings design: groups[] → rows[], with the right-side control
//  switching on `row.control`. Destructive rows live in their own card
//  with red text. Optimistic toggle / radio / slider mutations flip
//  the local state and call the data source; the VM rolls back on
//  failure by emitting a new state.
//

// swiftlint:disable type_body_length

import SwiftUI

public struct GroupedListView<DataSource: GroupedListDataSource>: View {
    @State private var dataSource: DataSource
    @State private var optimisticOverrides: [String: RowControl] = [:]
    private let onBack: (@MainActor () -> Void)?

    public init(
        dataSource: DataSource,
        onBack: (@MainActor () -> Void)? = nil
    ) {
        _dataSource = State(initialValue: dataSource)
        self.onBack = onBack
    }

    public var body: some View {
        VStack(spacing: 0) {
            topBar
            content
        }
        .background(Theme.Color.appBg)
        .task { await dataSource.load() }
        .accessibilityIdentifier("groupedList")
    }

    private var topBar: some View {
        HStack {
            if let onBack {
                Button(action: onBack) {
                    Icon(.chevronLeft, size: 22, color: Theme.Color.appText)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
                .accessibilityIdentifier("groupedListBackButton")
            } else {
                Spacer().frame(width: 36, height: 36)
            }
            Spacer()
            Text(dataSource.title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            Spacer().frame(width: 36, height: 36)
        }
        .padding(.horizontal, 12)
        .frame(height: 52)
        .background(Theme.Color.appBg)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
    }

    @ViewBuilder private var content: some View {
        switch dataSource.state {
        case .loading:
            loadingFrame
        case let .loaded(groups):
            loadedFrame(groups)
        case let .error(message):
            errorFrame(message: message)
        }
    }

    private var loadingFrame: some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(0..<3, id: \.self) { _ in
                    Shimmer(height: 11, cornerRadius: Radii.xs)
                        .frame(maxWidth: 100)
                        .padding(.horizontal, 16)
                        .padding(.top, 18)
                    VStack(spacing: 0) {
                        ForEach(0..<3, id: \.self) { row in
                            Shimmer(height: 14, cornerRadius: Radii.xs)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 16)
                            if row < 2 {
                                Rectangle()
                                    .fill(Theme.Color.appBorder.opacity(0.5))
                                    .frame(height: 1)
                                    .padding(.leading, 16)
                            }
                        }
                    }
                    .background(Theme.Color.appSurface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(Theme.Color.appBorder, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .padding(.horizontal, 12)
                    .padding(.top, 8)
                }
            }
        }
        .accessibilityIdentifier("groupedListLoading")
    }

    private func loadedFrame(_ groups: [GroupedListGroup]) -> some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(groups) { group in
                    let destructiveRows = group.rows.filter(\.destructive)
                    let regularRows = group.rows.filter { !$0.destructive }
                    if !regularRows.isEmpty {
                        groupCard(group, rows: regularRows)
                    }
                    ForEach(destructiveRows) { row in
                        VStack(spacing: 0) {
                            renderRow(row, isLastInCard: true)
                        }
                        .background(Theme.Color.appSurface)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(Theme.Color.appBorder, lineWidth: 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .padding(.horizontal, 12)
                        .padding(.top, 18)
                        .accessibilityIdentifier("groupedListDestructive_\(row.id)")
                    }
                }
                if let footer = dataSource.footerCaption {
                    Text(footer)
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(Theme.Color.appTextMuted)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                        .padding(.top, 18)
                        .padding(.bottom, 4)
                        .frame(maxWidth: .infinity)
                        .accessibilityIdentifier("groupedListFooter")
                }
            }
            .padding(.bottom, 24)
        }
        .accessibilityIdentifier("groupedListContent")
    }

    @ViewBuilder
    private func groupCard(_ group: GroupedListGroup, rows: [GroupedListRow]) -> some View {
        if let overline = group.overline {
            Text(overline.uppercased())
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .kerning(0.9)
                .padding(.horizontal, 16)
                .padding(.top, 18)
                .padding(.bottom, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier("groupedListOverline_\(group.id)")
        }
        VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                renderRow(row, isLastInCard: index == rows.count - 1)
                if index < rows.count - 1 {
                    Rectangle()
                        .fill(Theme.Color.appBorder.opacity(0.6))
                        .frame(height: 1)
                        .padding(.leading, 16)
                }
            }
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.horizontal, 12)
        if let helper = group.helper {
            Text(helper)
                .font(.system(size: 11.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier("groupedListHelper_\(group.id)")
        }
    }

    @ViewBuilder
    private func renderRow(_ row: GroupedListRow, isLastInCard _: Bool) -> some View {
        let activeControl = optimisticOverrides[row.id] ?? row.control
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(row.label)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(row.destructive ? Theme.Color.error : Theme.Color.appText)
                if let subtext = row.subtext {
                    Text(subtext)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                if case let .slider(stops, index) = activeControl {
                    sliderControl(rowId: row.id, stops: stops, index: index)
                        .padding(.top, 6)
                }
            }
            Spacer(minLength: 0)
            rightControl(rowId: row.id, control: activeControl)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(minHeight: 48)
        .contentShape(Rectangle())
        .onTapGesture { handleTap(rowId: row.id, control: activeControl, destructive: row.destructive) }
        .accessibilityIdentifier("groupedListRow_\(row.id)")
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel(row, control: activeControl))
        .accessibilityAddTraits(accessibilityTraits(control: activeControl))
    }

    @ViewBuilder
    private func rightControl(rowId: String, control: RowControl) -> some View {
        switch control {
        case .chevron:
            Icon(.chevronRight, size: 16, strokeWidth: 2.2, color: Theme.Color.appTextSecondary)
        case let .toggle(isOn):
            Toggle("", isOn: Binding(
                get: { isOn },
                set: { newValue in flipToggle(rowId: rowId, to: newValue, previous: isOn) }
            ))
            .labelsHidden()
            .tint(Theme.Color.primary600)
            .accessibilityIdentifier("groupedListToggle_\(rowId)")
        case let .radio(isSelected):
            radio(isSelected: isSelected)
                .accessibilityIdentifier("groupedListRadio_\(rowId)")
        case let .chipStatus(label, tone, includesChevron):
            HStack(spacing: 8) {
                chipView(label: label, tone: tone)
                    .accessibilityIdentifier("groupedListChip_\(rowId)")
                if includesChevron {
                    Icon(.chevronRight, size: 16, strokeWidth: 2.2, color: Theme.Color.appTextSecondary)
                }
            }
        case .slider:
            EmptyView()
        }
    }

    private func radio(isSelected: Bool) -> some View {
        ZStack {
            Circle()
                .stroke(isSelected ? Theme.Color.primary600 : Theme.Color.appBorder, lineWidth: 1.5)
                .frame(width: 22, height: 22)
            if isSelected {
                Circle()
                    .fill(Theme.Color.primary600)
                    .frame(width: 11, height: 11)
            }
        }
    }

    @ViewBuilder
    private func chipView(label: String, tone: RowControl.ChipTone) -> some View {
        let (bg, fg): (Color, Color) = switch tone {
        case .success: (Theme.Color.successBg, Theme.Color.success)
        case .info: (Theme.Color.primary50, Theme.Color.primary700)
        case .neutral: (Theme.Color.appSurfaceSunken, Theme.Color.appTextStrong)
        case .warning: (Theme.Color.warningBg, Theme.Color.warning)
        }
        Text(label.uppercased())
            .font(.system(size: 10.5, weight: .bold))
            .foregroundStyle(fg)
            .kerning(0.4)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(bg)
            .clipShape(Capsule())
    }

    @ViewBuilder
    private func sliderControl(rowId: String, stops: [String], index: Int) -> some View {
        let count = max(stops.count, 2)
        let active = index.clamped(to: 0...(count - 1))
        VStack(alignment: .leading, spacing: 0) {
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Theme.Color.appBorder)
                        .frame(height: 4)
                    Capsule()
                        .fill(Theme.Color.primary600)
                        .frame(
                            width: proxy.size.width * CGFloat(active) / CGFloat(count - 1),
                            height: 4
                        )
                    ForEach(0..<count, id: \.self) { i in
                        Circle()
                            .fill(i <= active ? Theme.Color.primary600 : Theme.Color.appBorder)
                            .frame(width: 10, height: 10)
                            .offset(
                                x: proxy.size.width * CGFloat(i) / CGFloat(count - 1) - 5
                            )
                    }
                }
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onEnded { value in
                            let fraction = max(0, min(1, value.location.x / proxy.size.width))
                            let newIndex = Int((fraction * CGFloat(count - 1)).rounded())
                            flipSlider(rowId: rowId, newIndex: newIndex, previousIndex: active, stops: stops)
                        }
                )
            }
            .frame(height: 26)
            HStack {
                ForEach(Array(stops.enumerated()), id: \.offset) { i, label in
                    Text(label)
                        .font(.system(size: 11, weight: i == active ? .bold : .medium))
                        .foregroundStyle(i == active ? Theme.Color.appText : Theme.Color.appTextSecondary)
                    if i < stops.count - 1 { Spacer() }
                }
            }
            .padding(.top, 2)
        }
        .accessibilityIdentifier("groupedListSlider_\(rowId)")
    }

    private func errorFrame(message: String) -> some View {
        VStack(spacing: Spacing.s3) {
            Spacer()
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button {
                Task { await dataSource.load() }
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
            .accessibilityIdentifier("groupedListRetry")
            Spacer()
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("groupedListError")
    }

    // MARK: - Interaction

    private func handleTap(rowId: String, control: RowControl, destructive: Bool) {
        switch control {
        case .chevron, .chipStatus:
            Task { await dataSource.tapRow(rowId) }
        case .radio:
            optimisticOverrides[rowId] = .radio(isSelected: true)
            Task { await dataSource.selectRadio(rowId) }
        case .toggle, .slider:
            // Toggle / slider already drive their own callbacks.
            // Chevron / chipStatus rows also handle taps for
            // navigation even when destructive.
            if destructive {
                Task { await dataSource.tapRow(rowId) }
            }
        }
    }

    private func flipToggle(rowId: String, to newValue: Bool, previous: Bool) {
        optimisticOverrides[rowId] = .toggle(isOn: newValue)
        Task {
            await dataSource.toggleRow(rowId, isOn: newValue)
            // The data source either confirms via a new state (which
            // overrides this local one once the view re-reads), or
            // rolls back on failure — same mechanism.
            _ = previous
        }
    }

    private func flipSlider(rowId: String, newIndex: Int, previousIndex: Int, stops: [String]) {
        guard newIndex != previousIndex else { return }
        optimisticOverrides[rowId] = .slider(stops: stops, index: newIndex)
        Task { await dataSource.setSlider(rowId, index: newIndex) }
    }

    private func accessibilityLabel(_ row: GroupedListRow, control: RowControl) -> String {
        var parts = [row.label]
        if let subtext = row.subtext { parts.append(subtext) }
        switch control {
        case let .toggle(isOn):
            parts.append(isOn ? "on" : "off")
        case let .radio(isSelected):
            if isSelected { parts.append("selected") }
        case let .chipStatus(label, _, _):
            parts.append(label)
        case let .slider(stops, index):
            if stops.indices.contains(index) { parts.append("currently \(stops[index])") }
        case .chevron:
            break
        }
        return parts.joined(separator: ", ")
    }

    private func accessibilityTraits(control: RowControl) -> AccessibilityTraits {
        switch control {
        case .toggle, .radio: .isButton
        case .chevron, .chipStatus, .slider: .isButton
        }
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
