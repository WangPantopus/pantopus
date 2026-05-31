//
//  StampsView.swift
//  Pantopus
//
//  A17.11 — Stamps (postage wallet). A standalone mailbox screen reusing
//  the A17 archetype chrome: a top nav with a teal category dot, a white
//  card stack (book hero · sheet · wallet rail · usage history · issuer),
//  the sky-gradient "Elf" AI strip, and a sticky "Buy more" dock.
//
//  Ports `docs/designs/A17/stamps.jsx` (`MailStampsScreen`). Four render
//  states: loading (shimmer), loaded (the populated wallet), empty ("No
//  stamps yet" + starter book), error.
//

// swiftlint:disable file_length

import SwiftUI

public struct StampsView: View {
    @State private var viewModel: StampsViewModel

    public init(viewModel: StampsViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        VStack(spacing: Spacing.s0) {
            StampsNav(onBack: { viewModel.tapBack() })
            stateBody
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("stamps")
        .task {
            await viewModel.load()
            Analytics.track(.screenStampsViewed(state: viewModel.state.analyticsTag))
        }
    }

    @ViewBuilder private var stateBody: some View {
        switch viewModel.state {
        case .loading:
            StampsLoadingBody()
        case let .loaded(content):
            StampsPopulatedBody(content: content, onBuyMore: { viewModel.buyMore() })
        case let .empty(content):
            StampsEmptyBody(content: content, onBuy: { viewModel.purchaseStarterBook() })
        case let .error(message):
            StampsErrorBody(message: message, onRetry: { Task { await viewModel.refresh() } })
        }
    }
}

// MARK: - Top nav

/// Bespoke A17 nav — back-to-Mailbox + centered teal dot eyebrow + the
/// gift / overflow action cluster. Mirrors `StampsNav` in `stamps.jsx`.
private struct StampsNav: View {
    let onBack: () -> Void

    var body: some View {
        ZStack {
            HStack(spacing: Spacing.s1) {
                Circle()
                    .fill(StampInk.local.color)
                    .frame(width: 8, height: 8)
                Text("STAMPS")
                    .font(.system(size: 12, weight: .bold))
                    .tracking(0.6)
                    .foregroundStyle(Theme.Color.appTextStrong)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("stampsNavEyebrow")

            HStack(spacing: Spacing.s0) {
                Button(action: onBack) {
                    HStack(spacing: Spacing.s0) {
                        Icon(.chevronLeft, size: 22, color: Theme.Color.primary600)
                        Text("Mailbox")
                            .font(.system(size: 15))
                            .foregroundStyle(Theme.Color.primary600)
                    }
                    .padding(.horizontal, Spacing.s1)
                    .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back to Mailbox")
                .accessibilityIdentifier("stampsNavBack")

                Spacer()

                HStack(spacing: 2) {
                    navIcon(.gift, label: "Gift a stamp", id: "stampsNavGift")
                    navIcon(.moreHorizontal, label: "More actions", id: "stampsNavMore")
                }
            }
            .padding(.horizontal, Spacing.s2)
        }
        .frame(height: 44)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }

    private func navIcon(_ icon: PantopusIcon, label: String, id: String) -> some View {
        Button(action: {}) {
            Icon(icon, size: 18, color: Theme.Color.appTextStrong)
                .frame(width: 34, height: 34)
                .background(Circle().fill(Theme.Color.appSurfaceSunken))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityIdentifier(id)
    }
}

// MARK: - Populated body

private struct StampsPopulatedBody: View {
    let content: StampsContent
    let onBuyMore: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                StampsItemHeader(category: content.categoryLabel, time: content.timeLabel)
                StampBookHero(book: content.book)
                AIElfStripView(content: elf)
                StampSheet(book: content.book)
                WalletRail(stamps: content.wallet, summary: content.walletSummary)
                UsageHistoryCard(usage: content.usage, window: content.usageWindow)
                StampsIssuerCard(issuer: content.issuer)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s3)
            .padding(.bottom, Spacing.s4)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(Theme.Color.appBg)
        .safeAreaInset(edge: .bottom) {
            StampsDock(onBuyMore: onBuyMore)
        }
    }

    private var elf: AIElfStripContent {
        AIElfStripContent(
            headline: content.elfHeadline,
            summary: content.elfSummary,
            bullets: content.insights.map {
                AIElfBullet(id: $0.id, icon: $0.icon, label: $0.label, text: $0.text)
            }
        )
    }
}

// MARK: - Item header (trust · category · time)

/// The received-item header row — same vocabulary as the other A17
/// variants: a verified trust chip + the Stamps category chip + a
/// relative-time string.
private struct StampsItemHeader: View {
    let category: String
    let time: String

    var body: some View {
        HStack(spacing: Spacing.s1) {
            StampsTrustChip()
            StampsCategoryChip(label: category)
            Spacer(minLength: Spacing.s0)
            Text(time)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .padding(.horizontal, 2)
        .accessibilityIdentifier("stampsItemHeader")
    }
}

private struct StampsTrustChip: View {
    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(.shieldCheck, size: 11, color: Theme.Color.success)
            Text("Verified")
                .font(.system(size: 10, weight: .bold))
        }
        .foregroundStyle(Theme.Color.success)
        .padding(.leading, 7)
        .padding(.trailing, Spacing.s2)
        .padding(.vertical, 3)
        .background(Theme.Color.successBg)
        .clipShape(Capsule())
    }
}

private struct StampsCategoryChip: View {
    let label: String

    var body: some View {
        HStack(spacing: Spacing.s1) {
            Circle()
                .fill(StampInk.local.color)
                .frame(width: 6, height: 6)
            Text(label)
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextStrong)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 3)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(Capsule())
    }
}

// MARK: - Issuer card

/// "From" — the Pantopus Post issuer card with a verified badge.
private struct StampsIssuerCard: View {
    let issuer: StampIssuer

    var body: some View {
        StampCard {
            StampSectionLabel("From") { EmptyView() }
            HStack(spacing: Spacing.s3) {
                avatar
                VStack(alignment: .leading, spacing: 1) {
                    Text(issuer.name)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(issuer.dept)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                    HStack(spacing: Spacing.s1) {
                        chip(icon: .stamp, text: issuer.kindLabel, tint: StampInk.local.color)
                        proofChip
                    }
                    .padding(.top, Spacing.s1)
                }
                Spacer(minLength: Spacing.s0)
                Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("From \(issuer.name). \(issuer.dept). \(issuer.kindLabel).")
        .accessibilityIdentifier("stampsIssuer")
    }

    private var avatar: some View {
        ZStack(alignment: .bottomTrailing) {
            Text(issuer.initials)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Color.white)
                .frame(width: 44, height: 44)
                .background(
                    LinearGradient(
                        colors: [StampInk.local.color, StampPalette.issuerDeep],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            Icon(.check, size: 9, color: Color.white)
                .frame(width: 16, height: 16)
                .background(Circle().fill(Theme.Color.success))
                .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
                .offset(x: 3, y: 3)
        }
    }

    private var proofChip: some View {
        Text(issuer.proofLabel)
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(Theme.Color.success)
            .padding(.horizontal, Spacing.s1)
            .padding(.vertical, 2)
            .background(Theme.Color.successBg)
            .clipShape(Capsule())
    }

    private func chip(icon: PantopusIcon, text: String, tint: Color) -> some View {
        HStack(spacing: 3) {
            Icon(icon, size: 9, color: tint)
            Text(text)
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(tint)
        }
        .padding(.horizontal, Spacing.s1)
        .padding(.vertical, 2)
        .background(tint.opacity(0.12))
        .clipShape(Capsule())
    }
}

// MARK: - Sticky dock

/// "Buy more stamps" primary CTA + the quick-action chip row. Pinned to
/// the bottom safe area in the populated state.
private struct StampsDock: View {
    let onBuyMore: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            Button(action: onBuyMore) {
                HStack(spacing: Spacing.s2) {
                    Icon(.plus, size: 16, color: Color.white)
                    Text("Buy more stamps")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Color.white)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Theme.Color.primary600)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                .shadow(color: Theme.Color.primary600.opacity(0.3), radius: 8, x: 0, y: 4)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("stampsBuyMore")

            HStack(spacing: Spacing.s2) {
                StampActionChip(icon: .arrowsRepeat, label: "Auto-refill")
                StampActionChip(icon: .gift, label: "Gift")
                StampActionChip(icon: .send, label: "Send mail")
                StampActionChip(icon: .archive, label: "Archive")
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s3)
        .padding(.bottom, Spacing.s2)
        .background(
            Theme.Color.appSurface
                .overlay(alignment: .top) {
                    Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
                }
                .ignoresSafeArea(edges: .bottom)
        )
    }
}

private struct StampActionChip: View {
    let icon: PantopusIcon
    let label: String

    var body: some View {
        Button(action: {}) {
            VStack(spacing: Spacing.s1) {
                Icon(icon, size: 17, color: Theme.Color.appTextSecondary)
                Text(label)
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("stampsAction.\(label)")
    }
}

// MARK: - Empty body

private struct StampsEmptyBody: View {
    let content: StampsEmptyContent
    let onBuy: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.s4) {
                hero
                StarterBookCard(book: content.starterBook, onGetBook: onBuy)
                howItWorks
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s4)
            .padding(.bottom, Spacing.s8)
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("stampsEmpty")
    }

    private var hero: some View {
        VStack(spacing: Spacing.s0) {
            ZStack(alignment: .bottomTrailing) {
                PerforatedStamp(ink: StampInk.local.color, width: 108, height: 138)
                    .shadow(color: StampInk.local.color.opacity(0.22), radius: 14, x: 0, y: 10)
                Icon(.plus, size: 18, color: Theme.Color.appTextMuted)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(Theme.Color.appSurface))
                    .overlay(Circle().stroke(Theme.Color.appBorder, lineWidth: 1))
                    .shadow(color: Color.black.opacity(0.12), radius: 6, x: 0, y: 3)
                    .offset(x: 10, y: 8)
            }
            .padding(.top, Spacing.s6)
            .padding(.bottom, Spacing.s5)

            Text(content.headline)
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .padding(.bottom, Spacing.s1)
            Text(content.body)
                .font(.system(size: 13))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
                .lineSpacing(2)
                .frame(maxWidth: 280)
                .padding(.bottom, Spacing.s5)

            Button(action: onBuy) {
                HStack(spacing: Spacing.s2) {
                    Text(content.buyLabel)
                        .font(.system(size: 14, weight: .bold))
                    Icon(.arrowRight, size: 15, color: Color.white)
                }
                .foregroundStyle(Color.white)
                .padding(.horizontal, Spacing.s5)
                .padding(.vertical, Spacing.s3)
                .background(Theme.Color.primary600)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                .shadow(color: Theme.Color.primary600.opacity(0.3), radius: 8, x: 0, y: 4)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("stampsEmptyBuy")
        }
        .frame(maxWidth: .infinity)
    }

    private var howItWorks: some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            Icon(.info, size: 15, color: Theme.Color.primary700)
                .frame(width: 28, height: 28)
                .background(Theme.Color.primary50)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(content.howItWorksTitle)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text(content.howItWorksBody)
                    .font(.system(size: 11.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: Spacing.s0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }
}

/// The previewed starter-book offer on the empty state.
private struct StarterBookCard: View {
    let book: StampStarterBook
    let onGetBook: () -> Void

    var body: some View {
        StampCard(noPad: true) {
            HStack(spacing: 14) {
                PerforatedStamp(ink: StampInk.local.color, width: 58, height: 74, toothRadius: 3, toothGap: 9)
                VStack(alignment: .leading, spacing: 2) {
                    Text(book.title)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(book.detail)
                        .font(.system(size: 11.5))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineSpacing(1)
                }
                Spacer(minLength: Spacing.s0)
                VStack(alignment: .trailing, spacing: Spacing.s1) {
                    Text(book.priceLabel)
                        .font(.system(size: 16, weight: .heavy))
                        .foregroundStyle(Theme.Color.appText)
                    Button(action: onGetBook) {
                        Text("Get book")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Color.white)
                            .padding(.horizontal, Spacing.s3)
                            .padding(.vertical, 5)
                            .background(StampInk.local.color)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("stampsStarterGetBook")
                }
            }
            .padding(14)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("stampsStarterBook")
    }
}

// MARK: - Loading body

/// Shimmer skeleton mirroring the loaded geometry — never a spinner.
private struct StampsLoadingBody: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Shimmer(width: 160, height: 22, cornerRadius: Radii.pill)
                Shimmer(height: 160, cornerRadius: Radii.xl)
                Shimmer(height: 120, cornerRadius: Radii.xl)
                Shimmer(height: 220, cornerRadius: Radii.xl)
                Shimmer(height: 150, cornerRadius: Radii.xl)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s3)
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("stampsLoading")
    }
}

// MARK: - Error body

private struct StampsErrorBody: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s3) {
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load your stamps")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .font(.system(size: 13))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button(action: onRetry) {
                Text("Try again")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color.white)
                    .padding(.horizontal, Spacing.s5)
                    .padding(.vertical, Spacing.s3)
                    .background(Theme.Color.primary600)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("stampsRetry")
        }
        .padding(.horizontal, Spacing.s6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("stampsError")
    }
}

// MARK: - Shared card chrome

/// White rounded card with a hairline border — the A17 card-stack unit.
struct StampCard<Content: View>: View {
    var noPad = false
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s0) {
            content()
        }
        .padding(noPad ? 0 : 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .shadow(color: Color.black.opacity(0.03), radius: 1, x: 0, y: 1)
    }
}

/// Uppercase overline used at the head of a card, with a trailing slot.
struct StampSectionLabel<Trailing: View>: View {
    let title: String
    @ViewBuilder let trailing: () -> Trailing

    init(_ title: String, @ViewBuilder trailing: @escaping () -> Trailing) {
        self.title = title
        self.trailing = trailing
    }

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Text(title.uppercased())
                .font(.system(size: 11, weight: .bold))
                .tracking(0.7)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Spacer(minLength: Spacing.s0)
            trailing()
        }
        .padding(.bottom, Spacing.s3)
    }
}

// MARK: - VM-free frames (snapshots / previews)

/// Populated frame without a view-model — for snapshot tests + previews.
struct StampsPopulatedFrame: View {
    let content: StampsContent

    var body: some View {
        VStack(spacing: Spacing.s0) {
            StampsNav(onBack: {})
            StampsPopulatedBody(content: content, onBuyMore: {})
        }
        .background(Theme.Color.appBg)
    }
}

/// Empty frame without a view-model — for snapshot tests + previews.
struct StampsEmptyFrame: View {
    let content: StampsEmptyContent

    var body: some View {
        VStack(spacing: Spacing.s0) {
            StampsNav(onBack: {})
            StampsEmptyBody(content: content, onBuy: {})
        }
        .background(Theme.Color.appBg)
    }
}

// MARK: - State analytics

private extension StampsState {
    var analyticsTag: String {
        switch self {
        case .loading: "loading"
        case .loaded: "populated"
        case .empty: "empty"
        case .error: "error"
        }
    }
}

// MARK: - Previews

#if DEBUG
#Preview("A17.11 · populated") {
    StampsPopulatedFrame(content: StampsSampleData.populated)
}

#Preview("A17.11 · empty") {
    StampsEmptyFrame(content: StampsSampleData.empty)
}
#endif
