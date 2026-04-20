//
//  ContentDetailShell.swift
//  Pantopus
//
//  Reusable detail scaffold. Concrete screens supply three slots — a
//  header, a body, and an optional CTA — and the shell handles the top
//  bar, scroll container, and back-chevron chrome.
//

import SwiftUI

/// Top-bar trailing action payload for the content-detail shell.
public struct ContentDetailTopBarAction: Sendable {
    public let icon: PantopusIcon
    public let accessibilityLabel: String
    public let handler: @Sendable () -> Void

    public init(icon: PantopusIcon, accessibilityLabel: String, handler: @escaping @Sendable () -> Void) {
        self.icon = icon
        self.accessibilityLabel = accessibilityLabel
        self.handler = handler
    }
}

/// Generic content-detail shell with pluggable header / body / cta slots.
///
/// Rather than wiring three type parameters through the view hierarchy
/// (`Shell<H, B, C>`), we keep the shell ergonomic by taking three
/// `@ViewBuilder` closures — each screen inlines whichever concrete
/// header / body / CTA it uses.
@MainActor
public struct ContentDetailShell<HeaderView: View, BodyView: View, CTAView: View>: View {
    private let title: String?
    private let onBack: (() -> Void)?
    private let topBarAction: ContentDetailTopBarAction?
    private let headerContent: HeaderView
    private let bodyContent: BodyView
    private let ctaContent: CTAView

    public init(
        title: String? = nil,
        onBack: (() -> Void)? = nil,
        topBarAction: ContentDetailTopBarAction? = nil,
        @ViewBuilder header: () -> HeaderView,
        @ViewBuilder body: () -> BodyView,
        @ViewBuilder cta: () -> CTAView = { EmptyView() }
    ) {
        self.title = title
        self.onBack = onBack
        self.topBarAction = topBarAction
        self.headerContent = header()
        self.bodyContent = body()
        self.ctaContent = cta()
    }

    public var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                ContentDetailTopBar(title: title, onBack: onBack, action: topBarAction)
                ScrollView {
                    VStack(alignment: .leading, spacing: Spacing.s4) {
                        headerContent
                        bodyContent
                        Spacer(minLength: Spacing.s10)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, Spacing.s4)
                }
                .background(Theme.Color.appBg)
            }
            ctaContent
                .padding(Spacing.s4)
        }
        .background(Theme.Color.appBg)
    }
}

/// 44pt top bar used by the content-detail shell.
public struct ContentDetailTopBar: View {
    let title: String?
    let onBack: (() -> Void)?
    let action: ContentDetailTopBarAction?

    public var body: some View {
        HStack(spacing: Spacing.s2) {
            if let onBack {
                Button(action: onBack) {
                    Icon(.chevronLeft, size: 22, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
            } else {
                Spacer().frame(width: 44, height: 44)
            }
            Spacer()
            if let title {
                Text(title)
                    .pantopusTextStyle(.h3)
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                    .accessibilityAddTraits(.isHeader)
            }
            Spacer()
            if let action {
                Button(action: action.handler) {
                    Icon(action.icon, size: 22, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(action.accessibilityLabel)
            } else {
                Spacer().frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal, Spacing.s2)
        .frame(height: 44)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }
}
