//
//  BookletPager.swift
//  Pantopus
//
//  T6.5c (P21) — Booklet variant of the A17 shell's body slot.
//
//  Two render modes per `booklet.jsx`:
//    - `.page` — full-width `TabView` swiping through the page images,
//      with an indicator strip below ("Page N of M" + sky scrubber +
//      grid-mode toggle button).
//    - `.grid` — 3-column thumbnail grid; tap a thumbnail to jump
//      straight back to `.page` mode at that page.
//
//  Lives in the feature folder per the P21 brief — the swipeable
//  paper-page geometry doesn't generalise to other mail variants.
//  Community attachments (P22) use a different shape.
//

import SwiftUI

/// Rendering mode for the pager. Exposed at the view init so the variant
/// view can persist the mode in its caller (e.g. across re-renders).
public enum BookletPagerMode: Sendable, Hashable {
    case page
    case grid
}

@MainActor
public struct BookletPager: View {
    private let pages: [URL]
    @State private var currentIndex: Int
    @State private var mode: BookletPagerMode

    public init(
        pages: [URL],
        initialPage: Int = 0,
        initialMode: BookletPagerMode = .page
    ) {
        self.pages = pages
        _currentIndex = State(initialValue: max(0, min(initialPage, max(0, pages.count - 1))))
        _mode = State(initialValue: initialMode)
    }

    public var body: some View {
        VStack(spacing: Spacing.s2) {
            switch mode {
            case .page:
                pageMode
            case .grid:
                gridMode
            }
        }
        .accessibilityIdentifier("bookletPager")
    }

    // MARK: - Page mode

    private var pageMode: some View {
        VStack(spacing: Spacing.s2) {
            TabView(selection: $currentIndex) {
                ForEach(Array(pages.enumerated()), id: \.offset) { idx, url in
                    BookletPageImage(url: url)
                        .tag(idx)
                        .padding(.horizontal, Spacing.s4)
                        .accessibilityIdentifier("bookletPager_page_\(idx)")
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .frame(height: 360)
            pageIndicator
        }
    }

    private var pageIndicator: some View {
        VStack(spacing: Spacing.s2) {
            HStack(alignment: .center, spacing: Spacing.s2) {
                Button {
                    currentIndex = max(0, currentIndex - 1)
                } label: {
                    Icon(.chevronLeft, size: 14, color: Theme.Color.appTextStrong)
                        .frame(width: 32, height: 32)
                        .background(Theme.Color.appSurfaceSunken)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .disabled(currentIndex == 0)
                .accessibilityLabel("Previous page")
                .accessibilityIdentifier("bookletPager_prev")
                VStack(alignment: .center, spacing: 1) {
                    HStack(spacing: 4) {
                        Text("Page \(currentIndex + 1)")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Theme.Color.appText)
                        Text("of \(pages.count)")
                            .font(.system(size: 13))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    .accessibilityLabel("Page \(currentIndex + 1) of \(pages.count)")
                    .accessibilityIdentifier("bookletPager_pageLabel")
                }
                .frame(maxWidth: .infinity)
                Button {
                    currentIndex = min(pages.count - 1, currentIndex + 1)
                } label: {
                    Icon(.chevronRight, size: 14, color: Theme.Color.appTextStrong)
                        .frame(width: 32, height: 32)
                        .background(Theme.Color.appSurfaceSunken)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .disabled(currentIndex >= pages.count - 1)
                .accessibilityLabel("Next page")
                .accessibilityIdentifier("bookletPager_next")
            }
            scrubber
            Button {
                mode = .grid
            } label: {
                HStack(spacing: Spacing.s1) {
                    Icon(.fileType, size: 12, color: Theme.Color.primary600)
                    Text("View all pages")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Theme.Color.primary600)
                }
                .padding(.horizontal, Spacing.s3)
                .padding(.vertical, Spacing.s1)
                .background(Theme.Color.primary50)
                .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("bookletPager_toggleGrid")
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
    }

    private var scrubber: some View {
        GeometryReader { proxy in
            let totalSegments = max(1, pages.count - 1)
            let filled = pages.count <= 1
                ? proxy.size.width
                : proxy.size.width * CGFloat(currentIndex) / CGFloat(totalSegments)
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Theme.Color.appSurfaceSunken)
                    .frame(height: 4)
                Capsule()
                    .fill(Theme.Color.primary600)
                    .frame(width: max(4, filled), height: 4)
            }
        }
        .frame(height: 4)
        .accessibilityHidden(true)
    }

    // MARK: - Grid mode

    private var gridMode: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: Spacing.s2) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("ALL PAGES")
                        .font(.system(size: 11, weight: .bold))
                        .tracking(0.5)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .accessibilityAddTraits(.isHeader)
                    Text("Tap a thumbnail to jump there")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer(minLength: 0)
                Text("\(pages.count) pages")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .padding(.horizontal, Spacing.s2)
                    .padding(.vertical, 4)
                    .background(Theme.Color.appSurfaceSunken)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            LazyVGrid(
                columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.s2), count: 3),
                spacing: Spacing.s2
            ) {
                ForEach(Array(pages.enumerated()), id: \.offset) { idx, url in
                    Button {
                        currentIndex = idx
                        mode = .page
                    } label: {
                        ThumbnailCell(url: url, page: idx + 1, isCurrent: idx == currentIndex)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Jump to page \(idx + 1)")
                    .accessibilityIdentifier("bookletPager_thumb_\(idx)")
                }
            }
            .padding(Spacing.s3)
            HStack {
                Spacer()
                Button {
                    mode = .page
                } label: {
                    HStack(spacing: Spacing.s1) {
                        Icon(.chevronLeft, size: 12, color: Theme.Color.primary600)
                        Text("Back to reader")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(Theme.Color.primary600)
                    }
                    .padding(.horizontal, Spacing.s3)
                    .padding(.vertical, Spacing.s1)
                    .background(Theme.Color.primary50)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("bookletPager_togglePage")
                Spacer()
            }
            .padding(.bottom, Spacing.s3)
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
    }
}

private struct BookletPageImage: View {
    let url: URL

    var body: some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case let .success(image):
                image
                    .resizable()
                    .aspectRatio(3.0 / 4.0, contentMode: .fit)
            case .failure:
                fallback(icon: .alertCircle, label: "Couldn't load page")
            case .empty:
                fallback(icon: .fileType, label: "Loading…")
            @unknown default:
                fallback(icon: .fileType, label: "")
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
    }

    private func fallback(icon: PantopusIcon, label: String) -> some View {
        ZStack {
            Theme.Color.appSurfaceSunken
            VStack(spacing: Spacing.s2) {
                Icon(icon, size: 24, color: Theme.Color.appTextMuted)
                if !label.isEmpty {
                    Text(label)
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
        }
        .aspectRatio(3.0 / 4.0, contentMode: .fit)
    }
}

private struct ThumbnailCell: View {
    let url: URL
    let page: Int
    let isCurrent: Bool

    var body: some View {
        ZStack(alignment: .topTrailing) {
            AsyncImage(url: url) { phase in
                switch phase {
                case let .success(image):
                    image
                        .resizable()
                        .aspectRatio(3.0 / 4.0, contentMode: .fit)
                case .failure, .empty:
                    Rectangle()
                        .fill(Theme.Color.appSurfaceSunken)
                        .aspectRatio(3.0 / 4.0, contentMode: .fit)
                @unknown default:
                    Rectangle()
                        .fill(Theme.Color.appSurfaceSunken)
                        .aspectRatio(3.0 / 4.0, contentMode: .fit)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(
                        isCurrent ? Theme.Color.primary600 : Theme.Color.appBorder,
                        lineWidth: isCurrent ? 2.5 : 1
                    )
            )
            VStack(alignment: .trailing, spacing: 0) {
                if isCurrent {
                    Icon(.eye, size: 10, color: Theme.Color.appTextInverse)
                        .frame(width: 18, height: 18)
                        .background(Theme.Color.primary600)
                        .clipShape(Circle())
                        .padding(4)
                }
                Spacer(minLength: 0)
                Text("\(page)")
                    .font(.system(size: 10, weight: .bold, design: .serif))
                    .italic()
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .padding(.horizontal, 4)
                    .padding(.bottom, 4)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
        }
    }
}

#Preview {
    BookletPager(pages: [
        URL(string: "https://placehold.co/360x480")!,
        URL(string: "https://placehold.co/360x480/orange/white")!,
        URL(string: "https://placehold.co/360x480/blue/white")!,
        URL(string: "https://placehold.co/360x480/green/white")!,
        URL(string: "https://placehold.co/360x480/red/white")!,
        URL(string: "https://placehold.co/360x480/purple/white")!
    ])
    .padding()
    .background(Theme.Color.appBg)
}
