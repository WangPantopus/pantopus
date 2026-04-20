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

    init(mailId: String, onBack: @escaping () -> Void) {
        _viewModel = State(initialValue: MailboxItemDetailViewModel(mailId: mailId))
        self.onBack = onBack
    }

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                LoadingLayout(onBack: onBack)
            case .loaded(let content):
                loadedLayout(content)
            case .error(let message):
                ErrorLayout(message: message, onBack: onBack) {
                    Task { await viewModel.refresh() }
                }
            }
        }
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

    @ViewBuilder
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
            onPrimary: { Task { await viewModel.logAsReceived() } },
            onGhost: { Task { await viewModel.markNotMine() } }
        ) {
            if content.category == .package, let pkg = content.packageInfo {
                PackageBody(carrier: pkg.carrier, etaLine: pkg.etaLine)
            } else if content.category != .package {
                MailItemPlaceholderBody(category: content.category)
            }
        }
    }

    private func ctaContent(for content: MailboxItemDetailContent) -> MailboxCTAShelfContent? {
        guard content.category == .package else { return nil }
        return MailboxCTAShelfContent(
            primaryTitle: viewModel.ctaFlags.primaryCompleted ? "Delivered" : "Log as received",
            ghostTitle: "Not mine",
            primaryLoading: viewModel.ctaFlags.primaryLoading,
            ghostLoading: viewModel.ctaFlags.ghostLoading,
            primaryEnabled: content.ctaEnabled && !viewModel.ctaFlags.primaryCompleted
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
    MailboxItemDetailView(mailId: "preview", onBack: {})
}
