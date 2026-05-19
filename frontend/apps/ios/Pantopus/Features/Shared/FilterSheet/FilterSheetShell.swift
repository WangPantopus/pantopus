//
//  FilterSheetShell.swift
//  Pantopus
//
//  Shared bottom-sheet scaffold for filter / sort surfaces. The host
//  wraps this view in `.sheet(isPresented:)` and supplies
//  `.presentationDetents([.medium])` (or accepts the default applied
//  here). The shell owns the working copy of the selection so Reset
//  doesn't leak back to the host until Apply is tapped, and tap-outside
//  dismisses without firing the apply callback.
//

import SwiftUI

/// Scaffold for every filter / sort bottom sheet.
///
/// - The shell maintains an internal working copy of `sections`. Each
///   control mutates that copy without touching the host's bindings.
/// - **Reset** swaps the working copy for `sections.cleared()` — no
///   dismissal.
/// - **Apply** fires `onApply(workingSections)` then `onClose()` so
///   the host can dismiss the sheet.
/// - **Tap-outside** is handled by SwiftUI's `.sheet` binding; the
///   shell never sees it, so `onApply` is never invoked.
@MainActor
public struct FilterSheetShell: View {
    private let title: String
    private let sections: [FilterSection]
    private let applyLabel: String
    private let resetLabel: String
    private let onApply: @MainActor ([FilterSection]) -> Void
    private let onClose: @MainActor () -> Void

    @State private var working: [FilterSection]
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// - Parameters:
    ///   - title: Sheet header label — typically `"Filters"` or `"Sort"`.
    ///   - sections: Initial sections + selection state. The shell
    ///     copies these into its working state on init.
    ///   - applyLabel: Bottom primary button label. Defaults to `"Apply"`.
    ///   - resetLabel: Bottom ghost button label. Defaults to `"Reset"`.
    ///   - onApply: Called with the working sections when the user
    ///     taps the primary button. The shell calls `onClose()` after.
    ///   - onClose: Called whenever the shell wants to dismiss. The
    ///     host toggles its `@State var isPresented = false` here.
    public init(
        title: String = "Filters",
        sections: [FilterSection],
        applyLabel: String = "Apply",
        resetLabel: String = "Reset",
        onApply: @escaping @MainActor ([FilterSection]) -> Void,
        onClose: @escaping @MainActor () -> Void
    ) {
        self.title = title
        self.sections = sections
        self.applyLabel = applyLabel
        self.resetLabel = resetLabel
        self.onApply = onApply
        self.onClose = onClose
        _working = State(initialValue: sections)
    }

    public var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s6) {
                    ForEach(working.indices, id: \.self) { idx in
                        FilterSectionRow(
                            section: working[idx],
                            onUpdate: { updated in
                                update(at: idx, with: updated)
                            }
                        )
                    }
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.top, Spacing.s4)
                .padding(.bottom, Spacing.s5)
            }
            footer
        }
        .background(Theme.Color.appSurface)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
        .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: working)
        .accessibilityIdentifier("filterSheet")
    }

    // MARK: - Chrome

    private var header: some View {
        HStack {
            Text(title)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("filterSheetTitle")
            Spacer()
            Button(action: { onClose() }) {
                Icon(.x, size: 20, color: Theme.Color.appTextSecondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
            .accessibilityIdentifier("filterSheetCloseButton")
        }
        .padding(.leading, Spacing.s4)
        .padding(.trailing, Spacing.s2)
        .frame(height: 56)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }

    private var footer: some View {
        HStack(spacing: Spacing.s3) {
            GhostButton(title: resetLabel) {
                await reset()
            }
            .accessibilityIdentifier("filterSheetResetButton")
            PrimaryButton(title: applyLabel) {
                await apply()
            }
            .accessibilityIdentifier("filterSheetApplyButton")
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s3)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }

    // MARK: - Mutations

    private func update(at index: Int, with section: FilterSection) {
        guard working.indices.contains(index) else { return }
        working[index] = section
    }

    private func reset() async {
        working = working.cleared()
    }

    private func apply() async {
        onApply(working)
        onClose()
    }
}

// MARK: - Section row

@MainActor
struct FilterSectionRow: View {
    let section: FilterSection
    let onUpdate: @MainActor (FilterSection) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Text(section.title)
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("filterSection_\(section.id)_title")
            control
        }
        .accessibilityIdentifier("filterSection_\(section.id)")
    }

    @ViewBuilder private var control: some View {
        switch section.control {
        case let .chipGroup(options, selectedIds):
            FilterChipGroupControl(
                sectionId: section.id,
                options: options,
                selectedIds: selectedIds,
                onChange: { ids in
                    onUpdate(FilterSection(
                        id: section.id,
                        title: section.title,
                        control: .chipGroup(options: options, selectedIds: ids)
                    ))
                }
            )
        case let .radio(options, selectedId):
            FilterRadioControl(
                sectionId: section.id,
                options: options,
                selectedId: selectedId,
                onChange: { id in
                    onUpdate(FilterSection(
                        id: section.id,
                        title: section.title,
                        control: .radio(options: options, selectedId: id)
                    ))
                }
            )
        case let .multiSelect(options, selectedIds):
            FilterMultiSelectControl(
                sectionId: section.id,
                options: options,
                selectedIds: selectedIds,
                onChange: { ids in
                    onUpdate(FilterSection(
                        id: section.id,
                        title: section.title,
                        control: .multiSelect(options: options, selectedIds: ids)
                    ))
                }
            )
        case let .rangeSlider(range):
            FilterRangeSliderControl(
                sectionId: section.id,
                range: range,
                onChange: { newRange in
                    onUpdate(FilterSection(
                        id: section.id,
                        title: section.title,
                        control: .rangeSlider(newRange)
                    ))
                }
            )
        }
    }
}

// MARK: - Chip group

@MainActor
struct FilterChipGroupControl: View {
    let sectionId: String
    let options: [FilterOption]
    let selectedIds: Set<String>
    let onChange: @MainActor (Set<String>) -> Void

    var body: some View {
        FlowLayout(spacing: Spacing.s2) {
            ForEach(options) { option in
                let isOn = selectedIds.contains(option.id)
                Button {
                    var next = selectedIds
                    if isOn { next.remove(option.id) } else { next.insert(option.id) }
                    onChange(next)
                } label: {
                    Text(option.label)
                        .font(.system(size: 14, weight: isOn ? .semibold : .regular))
                        .foregroundStyle(isOn ? Theme.Color.primary600 : Theme.Color.appText)
                        .padding(.horizontal, Spacing.s3)
                        .frame(minHeight: 36)
                        .background(isOn ? Theme.Color.primary50 : Theme.Color.appSurface)
                        .overlay(
                            RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                                .stroke(
                                    isOn ? Theme.Color.primary600 : Theme.Color.appBorder,
                                    lineWidth: isOn ? 1.5 : 1
                                )
                        )
                        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
                }
                .buttonStyle(.plain)
                .frame(minHeight: 44)
                .accessibilityLabel(option.label)
                .accessibilityAddTraits(isOn ? [.isButton, .isSelected] : .isButton)
                .accessibilityIdentifier("filterChip_\(sectionId)_\(option.id)")
            }
        }
    }
}

// MARK: - Radio

@MainActor
struct FilterRadioControl: View {
    let sectionId: String
    let options: [FilterOption]
    let selectedId: String?
    let onChange: @MainActor (String?) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ForEach(options) { option in
                let isOn = selectedId == option.id
                Button {
                    onChange(isOn ? nil : option.id)
                } label: {
                    HStack(spacing: Spacing.s3) {
                        radioGlyph(isOn: isOn)
                        Text(option.label)
                            .font(.system(size: 15, weight: isOn ? .semibold : .regular))
                            .foregroundStyle(Theme.Color.appText)
                        Spacer(minLength: 0)
                    }
                    .padding(.vertical, Spacing.s3)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(option.label)
                .accessibilityValue(isOn ? "Selected" : "Not selected")
                .accessibilityAddTraits(isOn ? [.isButton, .isSelected] : .isButton)
                .accessibilityIdentifier("filterRadio_\(sectionId)_\(option.id)")
                if option.id != options.last?.id {
                    Rectangle()
                        .fill(Theme.Color.appBorderSubtle)
                        .frame(height: 1)
                        .padding(.leading, 32)
                }
            }
        }
    }

    private func radioGlyph(isOn: Bool) -> some View {
        ZStack {
            Circle()
                .stroke(
                    isOn ? Theme.Color.primary600 : Theme.Color.appBorderStrong,
                    lineWidth: 1.5
                )
                .frame(width: 20, height: 20)
            if isOn {
                Circle()
                    .fill(Theme.Color.primary600)
                    .frame(width: 10, height: 10)
            }
        }
    }
}

// MARK: - Multi-select

@MainActor
struct FilterMultiSelectControl: View {
    let sectionId: String
    let options: [FilterOption]
    let selectedIds: Set<String>
    let onChange: @MainActor (Set<String>) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ForEach(options) { option in
                let isOn = selectedIds.contains(option.id)
                Button {
                    var next = selectedIds
                    if isOn { next.remove(option.id) } else { next.insert(option.id) }
                    onChange(next)
                } label: {
                    HStack(spacing: Spacing.s3) {
                        checkboxGlyph(isOn: isOn)
                        Text(option.label)
                            .font(.system(size: 15, weight: .regular))
                            .foregroundStyle(Theme.Color.appText)
                        Spacer(minLength: 0)
                    }
                    .padding(.vertical, Spacing.s3)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(option.label)
                .accessibilityValue(isOn ? "Checked" : "Not checked")
                .accessibilityAddTraits(isOn ? [.isButton, .isSelected] : .isButton)
                .accessibilityIdentifier("filterMulti_\(sectionId)_\(option.id)")
                if option.id != options.last?.id {
                    Rectangle()
                        .fill(Theme.Color.appBorderSubtle)
                        .frame(height: 1)
                        .padding(.leading, 32)
                }
            }
        }
    }

    private func checkboxGlyph(isOn: Bool) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radii.sm)
                .fill(isOn ? Theme.Color.primary600 : Theme.Color.appSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.sm)
                        .stroke(
                            isOn ? Theme.Color.primary600 : Theme.Color.appBorderStrong,
                            lineWidth: 1.5
                        )
                )
                .frame(width: 20, height: 20)
            if isOn {
                Icon(.check, size: 12, color: Theme.Color.appTextInverse)
            }
        }
    }
}

// MARK: - Range slider

@MainActor
struct FilterRangeSliderControl: View {
    let sectionId: String
    let range: FilterRange
    let onChange: @MainActor (FilterRange) -> Void

    private let trackHeight: CGFloat = 4
    private let thumbSize: CGFloat = 24

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            GeometryReader { geo in
                let usableWidth = max(geo.size.width - thumbSize, 1)
                let lowerFraction = fraction(for: range.lower)
                let upperFraction = fraction(for: range.upper)
                let lowerX = lowerFraction * usableWidth
                let upperX = upperFraction * usableWidth

                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Theme.Color.appBorder)
                        .frame(height: trackHeight)
                        .padding(.horizontal, thumbSize / 2)
                    Capsule()
                        .fill(Theme.Color.primary600)
                        .frame(width: max(upperX - lowerX, 0), height: trackHeight)
                        .offset(x: lowerX + thumbSize / 2)
                    thumb()
                        .offset(x: lowerX)
                        .gesture(makeDrag(usableWidth: usableWidth, isUpper: false, coordinateSpaceName: "filterRange_\(sectionId)"))
                        .accessibilityLabel("Minimum")
                        .accessibilityValue("\(Int(range.lower))")
                        .accessibilityIdentifier("filterRangeLower_\(sectionId)")
                    thumb()
                        .offset(x: upperX)
                        .gesture(makeDrag(usableWidth: usableWidth, isUpper: true, coordinateSpaceName: "filterRange_\(sectionId)"))
                        .accessibilityLabel("Maximum")
                        .accessibilityValue("\(Int(range.upper))")
                        .accessibilityIdentifier("filterRangeUpper_\(sectionId)")
                }
                .coordinateSpace(name: "filterRange_\(sectionId)")
                .frame(height: thumbSize)
            }
            .frame(height: thumbSize)
            HStack {
                Text("\(Int(range.lower))")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Spacer()
                Text("\(Int(range.upper))")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
            }
        }
        .accessibilityElement(children: .contain)
    }

    private func thumb() -> some View {
        Circle()
            .fill(Theme.Color.appSurface)
            .overlay(Circle().stroke(Theme.Color.primary600, lineWidth: 2))
            .frame(width: thumbSize, height: thumbSize)
            .pantopusShadow(.sm)
    }

    private func fraction(for value: Double) -> CGFloat {
        let span = range.max - range.min
        guard span > 0 else { return 0 }
        return CGFloat((value - range.min) / span)
    }

    private func value(forFraction fraction: CGFloat) -> Double {
        let span = range.max - range.min
        let raw = range.min + Double(min(max(fraction, 0), 1)) * span
        guard range.step > 0 else { return raw }
        let stepped = (raw - range.min) / range.step
        return range.min + stepped.rounded() * range.step
    }

    private func makeDrag(usableWidth: CGFloat, isUpper: Bool, coordinateSpaceName: String) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named(coordinateSpaceName))
            .onChanged { drag in
                // location is in the track's coordinate space — subtract
                // half the thumb size to map from "track-space x" back
                // to "thumb-leading-edge x", then divide by usable width.
                let normalized = (drag.location.x - thumbSize / 2) / max(usableWidth, 1)
                let newValue = value(forFraction: normalized)
                var next = range
                if isUpper {
                    next.upper = min(max(newValue, range.lower), range.max)
                } else {
                    next.lower = max(min(newValue, range.upper), range.min)
                }
                onChange(next)
            }
    }
}

// MARK: - FlowLayout

/// Simple left-to-right wrapping layout for chip groups.
struct FlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var maxRowWidth: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > maxWidth, x > 0 {
                totalHeight += rowHeight + spacing
                maxRowWidth = max(maxRowWidth, x - spacing)
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        totalHeight += rowHeight
        maxRowWidth = max(maxRowWidth, x - spacing)
        return CGSize(width: maxRowWidth, height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) {
        let maxWidth = proposal.width ?? bounds.width
        var x: CGFloat = bounds.minX
        var y: CGFloat = bounds.minY
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.minX + maxWidth, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

#Preview("Populated") {
    FilterSheetShell(
        title: "Filters",
        sections: [
            FilterSection(
                id: "category",
                title: "Category",
                control: .chipGroup(
                    options: [
                        FilterOption(id: "handyman", label: "Handyman"),
                        FilterOption(id: "cleaning", label: "Cleaning"),
                        FilterOption(id: "moving", label: "Moving"),
                        FilterOption(id: "pets", label: "Pet care"),
                        FilterOption(id: "tutoring", label: "Tutoring")
                    ],
                    selectedIds: ["handyman", "moving"]
                )
            ),
            FilterSection(
                id: "sort",
                title: "Sort by",
                control: .radio(
                    options: [
                        FilterOption(id: "recent", label: "Most recent"),
                        FilterOption(id: "price-asc", label: "Price: low to high"),
                        FilterOption(id: "price-desc", label: "Price: high to low"),
                        FilterOption(id: "distance", label: "Distance")
                    ],
                    selectedId: "recent"
                )
            ),
            FilterSection(
                id: "tags",
                title: "Tags",
                control: .multiSelect(
                    options: [
                        FilterOption(id: "verified", label: "Verified posters"),
                        FilterOption(id: "delivery", label: "Delivery available"),
                        FilterOption(id: "negotiable", label: "Price negotiable")
                    ],
                    selectedIds: ["verified"]
                )
            ),
            FilterSection(
                id: "price",
                title: "Price",
                control: .rangeSlider(
                    FilterRange(min: 0, max: 500, lower: 50, upper: 350, step: 10)
                )
            )
        ],
        onApply: { _ in },
        onClose: {}
    )
    .frame(height: 600)
    .background(Theme.Color.appBg)
}
