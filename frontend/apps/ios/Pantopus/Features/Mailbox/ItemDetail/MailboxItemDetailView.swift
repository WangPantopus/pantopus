//
//  MailboxItemDetailView.swift
//  Pantopus
//
// swiftlint:disable multiple_closures_with_trailing_closure

import SwiftUI

/// Mailbox Item Detail screen. Category-aware; Package renders the
/// concrete body, other categories fall back to `NotYetAvailable`.
/// Identifiable wrapper around `URL` so we can drive a `.sheet(item:)`.
private struct TermsSheetItem: Identifiable {
    let id = UUID()
    let url: URL
}

struct MailboxItemDetailView: View {
    @State private var viewModel: MailboxItemDetailViewModel
    @State private var termsSheet: TermsSheetItem?
    private let onBack: () -> Void
    private let onOpenSenderProfile: (@MainActor (String) -> Void)?

    init(
        mailId: String,
        onBack: @escaping () -> Void,
        onOpenSenderProfile: (@MainActor (String) -> Void)? = nil
    ) {
        _viewModel = State(initialValue: MailboxItemDetailViewModel(mailId: mailId))
        self.onBack = onBack
        self.onOpenSenderProfile = onOpenSenderProfile
    }

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                LoadingLayout(onBack: onBack)
            case let .loaded(content):
                loadedLayout(content)
            case let .error(message):
                ErrorLayout(message: message, onBack: onBack) {
                    Task { await viewModel.refresh() }
                }
            }
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .task { await viewModel.load() }
        .overlay(alignment: .bottom) {
            if let toast = viewModel.ctaFlags.errorToast {
                Text(toast)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s4)
                    .padding(.vertical, Spacing.s2)
                    .background(Theme.Color.error.opacity(0.95))
                    .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
                    .padding(.bottom, 100)
                    .task {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        viewModel.ctaFlags.errorToast = nil
                    }
            }
        }
    }

    private func loadedLayout(_ content: MailboxItemDetailContent) -> some View {
        MailboxItemDetailShell(
            category: content.category,
            trust: content.trust,
            sender: content.sender,
            aiElf: content.aiElf,
            keyFacts: content.keyFacts,
            timeline: content.category == .package ? [] : content.timeline,
            cta: ctaContent(for: content),
            onBack: onBack,
            onAIChip: { kind in
                // AI suggestion chips are shortcuts for the bottom CTAs.
                switch kind {
                case .primary: Task { await viewModel.performPrimaryAction() }
                case .secondary: handleGhost(for: content)
                }
            },
            onPrimary: { Task { await viewModel.performPrimaryAction() } },
            onGhost: { handleGhost(for: content) },
            onSenderAvatarTap: onOpenSenderProfile
        ) { categoryBody(for: content) }
            .sheet(item: $termsSheet) { item in
                CertifiedTermsSheet(termsURL: item.url) { termsSheet = nil }
            }
    }

    /// Ghost CTA dispatcher. For certified mail this surfaces the
    /// View-terms sheet directly; for other categories the VM handles
    /// the action via `performGhostAction`.
    private func handleGhost(for content: MailboxItemDetailContent) {
        if content.category == .certified,
           case let .certified(detail) = content.payload,
           let url = detail.termsURL {
            termsSheet = TermsSheetItem(url: url)
            return
        }
        Task { await viewModel.performGhostAction() }
    }

    @ViewBuilder
    private func categoryBody(for content: MailboxItemDetailContent) -> some View {
        switch (content.category, content.payload) {
        case (.package, _):
            if let pkg = content.packageInfo {
                PackageBody(
                    content: pkg,
                    isReceiveEnabled: content.ctaEnabled && !viewModel.ctaFlags.primaryCompleted,
                    isReceiveLoading: viewModel.ctaFlags.primaryLoading,
                    isReceived: viewModel.ctaFlags.primaryCompleted
                ) {
                    Task { await viewModel.performPrimaryAction() }
                }
            }
        case let (.coupon, .coupon(coupon)):
            CouponBody(coupon: coupon)
        case let (.booklet, .booklet(booklet)):
            BookletBody(booklet: booklet)
        case let (.certified, .certified(certified)):
            CertifiedBody(
                certified: certified,
                isAcknowledged: Binding(
                    get: { viewModel.certifiedAckChecked },
                    set: { viewModel.certifiedAckChecked = $0 }
                )
            ) {
                if let url = certified.termsURL {
                    termsSheet = TermsSheetItem(url: url)
                }
            }
        case let (.gig, .gig(gig)):
            GigBody(gig: gig) {
                Task { await viewModel.acceptGigBid() }
            }
        case let (.memory, .memory(memory)):
            MemoryBody(memory: memory, isSaved: memory.isSaved)
        default:
            MailItemPlaceholderBody(category: content.category)
        }
    }

    private func ctaContent(for content: MailboxItemDetailContent) -> MailboxCTAShelfContent? {
        switch content.category {
        case .package:
            nil
        case .coupon:
            MailboxCTAShelfContent(
                primaryTitle: viewModel.ctaFlags.primaryCompleted ? "Added to wallet ✓" : "Add to wallet",
                ghostTitle: "Save for later",
                primaryLoading: viewModel.ctaFlags.primaryLoading,
                ghostLoading: viewModel.ctaFlags.ghostLoading,
                primaryEnabled: content.ctaEnabled && !viewModel.ctaFlags.primaryCompleted
            )
        case .booklet:
            MailboxCTAShelfContent(
                primaryTitle: "Save to library",
                ghostTitle: nil,
                primaryLoading: viewModel.ctaFlags.primaryLoading,
                ghostLoading: false,
                primaryEnabled: content.ctaEnabled
            )
        case .certified:
            MailboxCTAShelfContent(
                primaryTitle: viewModel.ctaFlags.primaryCompleted
                    ? "Acknowledged ✓"
                    : "Acknowledge receipt",
                ghostTitle: "View terms",
                primaryLoading: viewModel.ctaFlags.primaryLoading,
                ghostLoading: viewModel.ctaFlags.ghostLoading,
                primaryEnabled: content.ctaEnabled
                    && viewModel.certifiedAckChecked
                    && !viewModel.ctaFlags.primaryCompleted
            )
        case .memory:
            memoryCTA(content)
        default:
            nil
        }
    }

    /// Memory CTA shelf — "Save to Vault" flips to a disabled "Saved to
    /// Vault" once kept; "Share" stays available in both states.
    private func memoryCTA(_ content: MailboxItemDetailContent) -> MailboxCTAShelfContent {
        let saved: Bool = if case let .memory(memory) = content.payload {
            memory.isSaved
        } else {
            false
        }
        return MailboxCTAShelfContent(
            primaryTitle: saved ? "Saved to Vault" : "Save to Vault",
            ghostTitle: "Share",
            primaryLoading: viewModel.ctaFlags.primaryLoading,
            ghostLoading: viewModel.ctaFlags.ghostLoading,
            primaryEnabled: !saved
        )
    }
}

private struct LoadingLayout: View {
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 4)
            ContentDetailTopBar(title: nil, onBack: onBack, action: nil)
            VStack(spacing: Spacing.s3) {
                Shimmer(width: 120, height: 22, cornerRadius: Radii.pill)
                Shimmer(height: 56, cornerRadius: Radii.md)
                Shimmer(height: 120, cornerRadius: Radii.lg)
                Shimmer(height: 180, cornerRadius: Radii.lg)
            }
            .padding(Spacing.s4)
            Spacer()
        }
        .background(Theme.Color.appBg)
    }
}

private struct ErrorLayout: View {
    let message: String
    let onBack: () -> Void
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Theme.Color.error).frame(height: 4)
            ContentDetailTopBar(title: nil, onBack: onBack, action: nil)
            EmptyState(
                icon: .alertCircle,
                headline: "Couldn't load this item",
                subcopy: message,
                cta: EmptyState.CTA(title: "Try again") { await MainActor.run { onRetry() } }
            )
            .frame(maxHeight: .infinity)
        }
        .background(Theme.Color.appBg)
    }
}

#Preview {
    MailboxItemDetailView(mailId: "preview") {}
}
