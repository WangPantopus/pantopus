//
//  LegalContentView.swift
//  Pantopus
//
//  Static long-form viewer for the legal documents listed in
//  `LegalIndexView`. Each document is a versioned blob bundled with
//  the app — we don't have a CMS yet. The shell is a ContentDetail
//  with the body slot rendering paragraphs + headings.
//

import SwiftUI

public struct LegalContentView: View {
    let document: LegalDocument
    private let onBack: @MainActor () -> Void

    public init(document: LegalDocument, onBack: @escaping @MainActor () -> Void) {
        self.document = document
        self.onBack = onBack
    }

    public var body: some View {
        ContentDetailShell(
            title: document.title,
            onBack: onBack,
            header: { headerView },
            body: { documentBody }
        )
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("legalContent.\(document.rawValue)")
    }

    private var headerView: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(document.title)
                .pantopusTextStyle(.h2)
                .foregroundStyle(Theme.Color.appText)
            Text("Last updated: \(Self.lastUpdated)")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s2)
    }

    private var documentBody: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            ForEach(Array(blocks(for: document).enumerated()), id: \.offset) { _, block in
                switch block {
                case let .heading(text):
                    Text(text)
                        .pantopusTextStyle(.h3)
                        .foregroundStyle(Theme.Color.appText)
                case let .paragraph(text):
                    Text(text)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                case let .bullet(text):
                    HStack(alignment: .top, spacing: Spacing.s2) {
                        Text("•")
                            .foregroundStyle(Theme.Color.appTextSecondary)
                        Text(text)
                            .pantopusTextStyle(.body)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s4)
    }

    private enum Block: Sendable {
        case heading(String)
        case paragraph(String)
        case bullet(String)
    }

    private static let lastUpdated = "2026-05-01"

    private func blocks(for doc: LegalDocument) -> [Block] {
        switch doc {
        case .terms: Self.terms
        case .privacy: Self.privacy
        case .acceptableUse: Self.acceptableUse
        case .cookies: Self.cookies
        case .openSource: Self.openSource
        }
    }

    private static let terms: [Block] = [
        .paragraph("These Terms govern your use of Pantopus. By creating an account you agree to follow them."),
        .heading("1. Using your account"),
        .paragraph("You are responsible for activity on your account. Keep your password private and notify us if it's compromised."),
        .heading("2. Posting content"),
        .paragraph("Anything you post — gigs, listings, replies, mail — must be true to the best of your knowledge. Misleading posts can be removed."),
        .heading("3. Payments"),
        .paragraph("Payments through Pantopus are processed by Stripe. Disputes follow Stripe's resolution flow; Pantopus may intervene at its discretion.")
    ]

    private static let privacy: [Block] = [
        .paragraph("This policy explains what we collect, how we use it, and the controls you have."),
        .heading("What we collect"),
        .bullet("Account: email, display name, optional phone number."),
        .bullet("Activity: posts, messages, gigs, listings you create."),
        .bullet("Location: only when you opt in to neighborhood features."),
        .heading("Who can see it"),
        .paragraph("Default visibility is set in Settings → Privacy. You can tighten or loosen these defaults at any time, and changes apply going forward."),
        .heading("Retention"),
        .paragraph("Deleted content is removed from public surfaces immediately and from backups within 30 days.")
    ]

    private static let acceptableUse: [Block] = [
        .paragraph("Pantopus is a neighborhood platform. Help keep it kind, useful, and honest."),
        .heading("Not allowed"),
        .bullet("Harassment, threats, or hate speech."),
        .bullet("Spam, including unsolicited promotion of unrelated businesses."),
        .bullet("Impersonating a neighbor or a business."),
        .bullet("Posting illegal content or coordinating illegal activity."),
        .heading("Enforcement"),
        .paragraph("Violations may lead to content removal, account suspension, or permanent removal. Repeated abuse can also be reported to the relevant authorities.")
    ]

    private static let cookies: [Block] = [
        .paragraph("On the mobile app we use device storage for: session tokens, push-notification routing, and a small cache of recently loaded content. We don't use third-party advertising cookies."),
        .heading("Web"),
        .paragraph("On the web, we use cookies to keep you signed in and to remember your light/dark mode preference. Analytics is opt-in via Settings → Privacy.")
    ]

    private static let openSource: [Block] = [
        .paragraph("Pantopus is built on shoulders of giants. We owe thanks to the maintainers of every library below. Full license texts ship with each app build."),
        .heading("iOS"),
        .bullet("SwiftUI — Apple"),
        .bullet("Lucide icons — Lucide contributors"),
        .heading("Android"),
        .bullet("Jetpack Compose — Google"),
        .bullet("Hilt — Google"),
        .bullet("Moshi — Square"),
        .heading("Backend"),
        .bullet("Express — OpenJS Foundation"),
        .bullet("Supabase — Supabase Inc."),
        .bullet("Stripe SDK — Stripe Inc.")
    ]
}

#Preview {
    NavigationStack {
        LegalContentView(document: .privacy, onBack: {})
    }
}
