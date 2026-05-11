//
//  BookletPageSwiper.swift
//  Pantopus
//
//  Horizontal pager + page-indicator dots + pinch-to-zoom for the
//  FrameBooklet body. SwiftUI's `TabView` with `.page` style provides
//  snapping and accessibility actions for free; per-page magnification
//  uses a `MagnificationGesture`.
//

import SwiftUI

@MainActor
public struct BookletPageSwiper: View {
    private let pages: [URL]
    @State private var currentIndex: Int = 0

    public init(pages: [URL]) {
        self.pages = pages
    }

    public var body: some View {
        VStack(spacing: Spacing.s2) {
            TabView(selection: $currentIndex) {
                ForEach(Array(pages.enumerated()), id: \.offset) { idx, url in
                    BookletPage(url: url)
                        .tag(idx)
                        .padding(.horizontal, Spacing.s4)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .frame(height: 320)

            HStack(spacing: Spacing.s1) {
                ForEach(0..<pages.count, id: \.self) { idx in
                    Circle()
                        .fill(idx == currentIndex ? Theme.Color.primary600 : Theme.Color.appBorder)
                        .frame(width: 6, height: 6)
                }
            }
            .accessibilityHidden(true)

            Text("Page \(currentIndex + 1) of \(pages.count)")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityLabel("Page \(currentIndex + 1) of \(pages.count)")
        }
    }
}

private struct BookletPage: View {
    let url: URL
    @State private var scale: CGFloat = 1.0
    @State private var baseScale: CGFloat = 1.0

    var body: some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case let .success(image):
                image
                    .resizable()
                    .aspectRatio(16.0 / 9.0, contentMode: .fit)
                    .scaleEffect(scale)
                    .gesture(
                        // Sustain pinch-to-zoom in [1.0, 3.0]. Double-tap
                        // to reset back to 1.0 (handled below).
                        MagnificationGesture()
                            .onChanged { value in
                                scale = min(max(1.0, baseScale * value), 3.0)
                            }
                            .onEnded { _ in
                                baseScale = scale
                            }
                    )
                    .onTapGesture(count: 2) {
                        withAnimation(.spring()) {
                            scale = 1.0
                            baseScale = 1.0
                        }
                    }
                    .onChange(of: url) { _, _ in
                        scale = 1.0
                        baseScale = 1.0
                    }
            case .failure:
                fallback(icon: .alertCircle, label: "Couldn't load page")
            case .empty:
                fallback(icon: .file, label: "Loading…")
            @unknown default:
                fallback(icon: .file, label: "")
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .accessibilityLabel("Booklet page")
    }

    private func fallback(icon: PantopusIcon, label: String) -> some View {
        ZStack {
            Theme.Color.appSurfaceSunken
            VStack(spacing: Spacing.s2) {
                Icon(icon, size: 24, color: Theme.Color.appTextMuted)
                if !label.isEmpty {
                    Text(label)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
        }
        .aspectRatio(16.0 / 9.0, contentMode: .fit)
    }
}

#Preview {
    BookletPageSwiper(pages: [
        URL(string: "https://placehold.co/640x360")!,
        URL(string: "https://placehold.co/640x360/orange/white")!,
        URL(string: "https://placehold.co/640x360/blue/white")!
    ])
    .padding(.vertical)
    .background(Theme.Color.appBg)
}
