//
//  LocationFuzzSlider.swift
//  Pantopus
//
//  A14.7 Privacy — the "Map location fuzz" card body: a lead-in line, a
//  five-stop stepped slider (white thumb, primary fill, tick dots), the
//  stop labels, and the live `FuzzMap` preview (P1.3) that grows its
//  concentric ring as the slider drags. The five stops are the
//  `FuzzStop` cases — Exact · Building · Block · Block (default) ·
//  Neighborhood. Lives beside `FuzzMap` so the shared `GroupedListView`
//  can render it without reaching into a feature folder.
//

import SwiftUI

@MainActor
public struct LocationFuzzSlider: View {
    private let leadIn: String
    private let stop: FuzzStop
    private let onChange: (FuzzStop) -> Void

    private let stops = FuzzStop.allCases

    public init(
        leadIn: String,
        stop: FuzzStop,
        onChange: @escaping (FuzzStop) -> Void
    ) {
        self.leadIn = leadIn
        self.stop = stop
        self.onChange = onChange
    }

    private var index: Int { stops.firstIndex(of: stop) ?? 0 }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s0) {
            Text(leadIn)
                .font(.system(size: 13.5, weight: .medium))
                .foregroundStyle(Theme.Color.appTextStrong)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, Spacing.s4)
                .padding(.top, 14)
                .padding(.bottom, Spacing.s1)
                .frame(maxWidth: .infinity, alignment: .leading)

            slider
                .padding(.horizontal, Spacing.s5)
                .padding(.top, 14)
                .padding(.bottom, 18)

            Rectangle()
                .fill(Theme.Color.appBorder.opacity(0.6))
                .frame(height: 1)
                .padding(.leading, Spacing.s4)

            FuzzMap(stop: stop)
                .padding(.horizontal, Spacing.s4)
                .padding(.vertical, 14)
                .frame(maxWidth: .infinity)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("locationFuzzSlider")
    }

    private var slider: some View {
        VStack(spacing: Spacing.s2) {
            GeometryReader { proxy in
                let width = proxy.size.width
                let activeFraction = stops.count > 1 ? CGFloat(index) / CGFloat(stops.count - 1) : 0
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Theme.Color.appBorder)
                        .frame(height: 4)
                    Capsule()
                        .fill(Theme.Color.primary600)
                        .frame(width: width * activeFraction, height: 4)
                    ForEach(Array(stops.enumerated()), id: \.offset) { i, _ in
                        let fraction = stops.count > 1 ? CGFloat(i) / CGFloat(stops.count - 1) : 0
                        Circle()
                            .fill(i <= index ? Theme.Color.appSurface : Theme.Color.appBorderStrong)
                            .frame(width: 6, height: 6)
                            .overlay(
                                Circle().stroke(
                                    i <= index ? Theme.Color.primary600 : Theme.Color.clear,
                                    lineWidth: 1.5
                                )
                            )
                            .offset(x: width * fraction - 3)
                    }
                    Circle()
                        .fill(Theme.Color.appSurface)
                        .frame(width: 24, height: 24)
                        .overlay(Circle().stroke(Theme.Color.primary600, lineWidth: 2))
                        .shadow(color: .black.opacity(0.18), radius: 3, x: 0, y: 2)
                        .offset(x: width * activeFraction - 12)
                }
                .frame(maxHeight: .infinity, alignment: .center)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in updateStop(locationX: value.location.x, width: width) }
                )
            }
            .frame(height: 24)

            HStack(spacing: Spacing.s0) {
                ForEach(Array(stops.enumerated()), id: \.offset) { i, fuzzStop in
                    Text(fuzzStop.label)
                        .font(.system(size: 10.5, weight: i == index ? .bold : .medium))
                        .foregroundStyle(i == index ? Theme.Color.appText : Theme.Color.appTextMuted)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .multilineTextAlignment(.center)
                    if i < stops.count - 1 { Spacer(minLength: Spacing.s1) }
                }
            }
        }
    }

    private func updateStop(locationX: CGFloat, width: CGFloat) {
        guard width > 0, stops.count > 1 else { return }
        let fraction = max(0, min(1, locationX / width))
        let newIndex = Int((fraction * CGFloat(stops.count - 1)).rounded())
        let newStop = stops[newIndex]
        if newStop != stop { onChange(newStop) }
    }
}

#Preview("Location fuzz slider") {
    StatefulPreviewWrapper(FuzzStop.blockDefault) { binding in
        LocationFuzzSlider(
            leadIn: "How exact your task and listing pins appear on the map.",
            stop: binding.wrappedValue,
            onChange: { binding.wrappedValue = $0 }
        )
        .background(Theme.Color.appSurface)
        .padding(Spacing.s3)
        .background(Theme.Color.appBg)
    }
}

/// Tiny preview helper so the slider's `onChange` drives a live `@State`
/// in the Xcode canvas. Test-only — never shipped in a real screen.
private struct StatefulPreviewWrapper<Value, Content: View>: View {
    @State private var value: Value
    private let content: (Binding<Value>) -> Content

    init(_ initial: Value, @ViewBuilder content: @escaping (Binding<Value>) -> Content) {
        _value = State(initialValue: initial)
        self.content = content
    }

    var body: some View { content($value) }
}
