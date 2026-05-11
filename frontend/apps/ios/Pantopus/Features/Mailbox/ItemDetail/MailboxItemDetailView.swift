//
//  MailboxItemDetailView.swift
//  Pantopus
//

import SwiftUI

/// Mailbox Item Detail screen. Category-aware; Package renders the
/// concrete body, other categories fall back to `NotYetAvailable`.
struct MailboxItemDetailView: View {
    @State private var viewModel: MailboxItemDetailViewModel
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
            timeline: content.timeline,
            cta: ctaContent(for: content),
            onBack: onBack,
            onAIChip: { _ in },
            onPrimary: { Task { await viewModel.performPrimaryAction() } },
            onGhost: { Task { await viewModel.performGhostAction() } },
            onSenderAvatarTap: onOpenSenderProfile,
            body: { categoryBody(for: content) }
        )
    }

    @ViewBuilder
    private func categoryBody(for content: MailboxItemDetailContent) -> some View {
        switch (content.category, content.payload) {
        case (.package, _):
            if let pkg = content.packageInfo {
                PackageBody(carrier: pkg.carrier, etaLine: pkg.etaLine)
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
                ),
                onViewTerms: { Task { await viewModel.performGhostAction() } }
            )
        default:
            MailItemPlaceholderBody(category: content.category)
        }
    }

    private func ctaContent(for content: MailboxItemDetailContent) -> MailboxCTAShelfContent? {
        switch content.category {
        case .package:
            return MailboxCTAShelfContent(
                primaryTitle: viewModel.ctaFlags.primaryCompleted ? "Delivered" : "Log as received",
                ghostTitle: "Not mine",
                primaryLoading: viewModel.ctaFlags.primaryLoading,
                ghostLoading: viewModel.ctaFlags.ghostLoading,
                primaryEnabled: content.ctaEnabled && !viewModel.ctaFlags.primaryCompleted
            )
        case .coupon:
            return MailboxCTAShelfContent(
                primaryTitle: viewModel.ctaFlags.primaryCompleted ? "Added to wallet ✓" : "Add to wallet",
                ghostTitle: "Save for later",
                primaryLoading: viewModel.ctaFlags.primaryLoading,
                ghostLoading: viewModel.ctaFlags.ghostLoading,
                primaryEnabled: content.ctaEnabled && !viewModel.ctaFlags.primaryCompleted
            )
        case .booklet:
            return MailboxCTAShelfContent(
                primaryTitle: "Save to library",
                ghostTitle: nil,
                primaryLoading: viewModel.ctaFlags.primaryLoading,
                ghostLoading: false,
                primaryEnabled: content.ctaEnabled
            )
        case .certified:
            return MailboxCTAShelfContent(
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
        default:
            return nil
        }
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
