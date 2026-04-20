//
//  Headers.swift
//  Pantopus
//
//  Header slots for the Content Detail shell. `HomeHeroHeader` is the
//  only concrete implementation today; the others ship as
//  `NotYetAvailable` placeholders so screens can already reference the
//  types.
//

import SwiftUI

// MARK: - Home hero

/// A single stat cell inside `HomeHeroHeader`.
public struct HomeHeroStat: Sendable, Identifiable {
    public let id: String
    public let value: String
    public let label: String

    public init(id: String = UUID().uuidString, value: String, label: String) {
        self.id = id
        self.value = value
        self.label = label
    }
}

/// Gradient primary card with "VERIFIED HOME" overline, bold address, and
/// a 3-stat row.
public struct HomeHeroHeader: View {
    private let address: String
    private let verified: Bool
    private let stats: [HomeHeroStat]

    public init(address: String, verified: Bool, stats: [HomeHeroStat]) {
        self.address = address
        self.verified = verified
        self.stats = stats
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(spacing: Spacing.s2) {
                Icon(.shieldCheck, size: 14, color: Theme.Color.appTextInverse)
                Text(verified ? "VERIFIED HOME" : "UNVERIFIED HOME")
                    .pantopusTextStyle(.overline)
                    .foregroundStyle(Theme.Color.appTextInverse.opacity(0.85))
            }
            Text(address)
                .pantopusTextStyle(.h2)
                .foregroundStyle(Theme.Color.appTextInverse)
                .lineLimit(3)

            HStack(spacing: Spacing.s4) {
                ForEach(stats) { stat in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(stat.value)
                            .pantopusTextStyle(.h3)
                            .foregroundStyle(Theme.Color.appTextInverse)
                        Text(stat.label.uppercased())
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextInverse.opacity(0.85))
                    }
                    if stat.id != stats.last?.id {
                        Spacer()
                    }
                }
            }
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(
                colors: [Theme.Color.primary600, Theme.Color.primary800],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl2, style: .continuous))
        .pantopusShadow(.primary)
        .padding(.horizontal, Spacing.s4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(verified ? "Verified home" : "Unverified home"), \(address)")
    }
}

// MARK: - Stubs (implemented in later prompts)

/// Placeholder for a future profile-header pattern.
public struct ProfileHeaderStub: View {
    public init() {}
    public var body: some View {
        NotYetAvailableView(tabName: "Profile header", icon: .user)
            .frame(height: 240)
            .padding(.horizontal, Spacing.s4)
    }
}

/// Placeholder for a future post-author header.
public struct PostAuthorHeaderStub: View {
    public init() {}
    public var body: some View {
        NotYetAvailableView(tabName: "Post header", icon: .megaphone)
            .frame(height: 240)
            .padding(.horizontal, Spacing.s4)
    }
}

/// Placeholder for a future business header.
public struct BusinessHeaderStub: View {
    public init() {}
    public var body: some View {
        NotYetAvailableView(tabName: "Business header", icon: .shoppingBag)
            .frame(height: 240)
            .padding(.horizontal, Spacing.s4)
    }
}

/// Placeholder for a future wallet hero.
public struct WalletHeroStub: View {
    public init() {}
    public var body: some View {
        NotYetAvailableView(tabName: "Wallet header", icon: .shield)
            .frame(height: 240)
            .padding(.horizontal, Spacing.s4)
    }
}
