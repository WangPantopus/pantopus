//
//  MailDetailView.swift
//  Pantopus
//
//  Mail item detail screen — dispatches to the bespoke ceremonial layout
//  for each category (A17.1–A17.8). Every variant composes the shared
//  `MailItemDetailShell` (P19) and lives under `Variants/`.
//

import SwiftUI

// swiftlint:disable trailing_closure

public struct MailDetailView: View {
    @State private var viewModel: MailDetailViewModel
    private let onBack: () -> Void
    private let onOpenSenderProfile: (@MainActor (String) -> Void)?

    public init(
        mailId: String,
        onBack: @escaping () -> Void,
        onOpenSenderProfile: (@MainActor (String) -> Void)? = nil
    ) {
        _viewModel = State(initialValue: MailDetailViewModel(mailId: mailId))
        self.onBack = onBack
        self.onOpenSenderProfile = onOpenSenderProfile
    }

    public var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                LoadingLayout(onBack: onBack)
            case let .loaded(content):
                loaded(content)
            case let .error(message):
                ErrorLayout(message: message, onBack: onBack) {
                    Task { await viewModel.refresh() }
                }
            }
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .accessibilityIdentifier("mailDetail")
        .task { await viewModel.load() }
        .overlay(alignment: .bottom) {
            if let toast = viewModel.toast {
                ToastBanner(message: toast)
                    .padding(.bottom, 110)
                    .task {
                        try? await Task.sleep(nanoseconds: 1_800_000_000)
                        viewModel.toast = nil
                    }
                    .transition(.opacity)
            }
        }
        .pantopusAnimation(.componentState, value: viewModel.toast)
        .confirmationDialog(
            "Save to vault",
            isPresented: Binding(
                get: { viewModel.showsSaveToVaultPicker },
                set: { viewModel.showsSaveToVaultPicker = $0 }
            ),
            titleVisibility: .visible
        ) {
            ForEach(viewModel.saveToVaultFolders) { folder in
                Button(folder.label) {
                    Task { await viewModel.saveToVault(folderId: folder.id) }
                }
                .accessibilityIdentifier("mailDetail_saveToVault_\(folder.id)")
            }
            Button("Cancel", role: .cancel) {
                viewModel.showsSaveToVaultPicker = false
            }
        } message: {
            Text("Pick a folder to keep this mail in.")
        }
    }

    @ViewBuilder
    private func loaded(_ content: MailDetailContent) -> some View {
        switch content.category {
        case .booklet:
            booklet(content)
        case .certified:
            certified(content)
        case .community:
            community(content)
        case .coupon:
            coupon(content)
        case .gig:
            gig(content)
        case .memory:
            memory(content)
        case .package:
            package(content)
        case .records:
            records(content)
        default:
            generic(content)
        }
    }

    @ViewBuilder
    private func booklet(_ content: MailDetailContent) -> some View {
        if let booklet = content.bookletDetail {
            BookletDetailLayout(
                content: content,
                booklet: booklet,
                ackInFlight: viewModel.ackInFlight,
                onBack: { onBack() },
                onAcknowledge: { Task { await viewModel.acknowledge() } },
                onOpenSenderProfile: onOpenSenderProfile,
                onSaveToVault: { Task { await viewModel.openSaveToVaultPicker() } }
            )
        } else {
            generic(content)
        }
    }

    @ViewBuilder
    private func certified(_ content: MailDetailContent) -> some View {
        if let certified = content.certifiedDetail {
            CertifiedDetailLayout(
                content: content,
                certified: certified,
                ackInFlight: viewModel.ackInFlight,
                onBack: { onBack() },
                onAcknowledge: { Task { await viewModel.acknowledge() } },
                onOpenSenderProfile: onOpenSenderProfile,
                onSaveToVault: { Task { await viewModel.openSaveToVaultPicker() } }
            )
        } else {
            generic(content)
        }
    }

    @ViewBuilder
    private func community(_ content: MailDetailContent) -> some View {
        if let community = content.communityDetail {
            CommunityDetailLayout(
                content: content,
                community: community,
                rsvpInFlight: viewModel.rsvpInFlight,
                onBack: { onBack() },
                onRsvp: { status in Task { await viewModel.setRsvp(status) } },
                onOpenSenderProfile: onOpenSenderProfile,
                onSaveToVault: { Task { await viewModel.openSaveToVaultPicker() } }
            )
        } else {
            generic(content)
        }
    }

    @ViewBuilder
    private func coupon(_ content: MailDetailContent) -> some View {
        if let coupon = content.couponDetail {
            CouponDetailLayout(
                content: content,
                coupon: coupon,
                redeemInFlight: viewModel.couponRedeemInFlight,
                onBack: { onBack() },
                onRedeem: { Task { await viewModel.redeemCoupon() } },
                onOpenSenderProfile: onOpenSenderProfile,
                onSaveToVault: { Task { await viewModel.openSaveToVaultPicker() } }
            )
        } else {
            generic(content)
        }
    }

    @ViewBuilder
    private func gig(_ content: MailDetailContent) -> some View {
        if let gig = content.gigDetail {
            GigDetailLayout(
                content: content,
                gig: gig,
                bidInFlight: viewModel.gigBidInFlight,
                onBack: { onBack() },
                onAccept: { Task { await viewModel.acceptGigBid() } },
                onOpenSenderProfile: onOpenSenderProfile,
                onSaveToVault: { Task { await viewModel.openSaveToVaultPicker() } }
            )
        } else {
            generic(content)
        }
    }

    @ViewBuilder
    private func memory(_ content: MailDetailContent) -> some View {
        if let memory = content.memoryDetail {
            MemoryDetailLayout(
                content: content,
                memory: memory,
                saveInFlight: viewModel.saveToVaultInFlight,
                onBack: { onBack() },
                onSaveMemory: { Task { await viewModel.saveMemoryToVault() } },
                onOpenSenderProfile: onOpenSenderProfile,
                onSaveToVault: { Task { await viewModel.openSaveToVaultPicker() } }
            )
        } else {
            generic(content)
        }
    }

    @ViewBuilder
    private func package(_ content: MailDetailContent) -> some View {
        if let package = content.packageDetail {
            PackageDetailLayout(
                content: content,
                package: package,
                ackInFlight: viewModel.ackInFlight,
                onBack: { onBack() },
                onAcknowledgeDelivery: { Task { await viewModel.acknowledge() } },
                onOpenSenderProfile: onOpenSenderProfile,
                onSaveToVault: { Task { await viewModel.openSaveToVaultPicker() } }
            )
        } else {
            generic(content)
        }
    }

    @ViewBuilder
    private func records(_ content: MailDetailContent) -> some View {
        if let records = content.recordsDetail {
            RecordsDetailLayout(
                content: content,
                records: records,
                fileInFlight: viewModel.recordsFileInFlight,
                onBack: { onBack() },
                onFileInVault: { Task { await viewModel.fileRecordToVault() } },
                onOpenSenderProfile: onOpenSenderProfile,
                onSaveToVault: { Task { await viewModel.openSaveToVaultPicker() } }
            )
        } else {
            generic(content)
        }
    }

    private func generic(_ content: MailDetailContent) -> some View {
        GenericMailDetailLayout(
            content: content,
            ackInFlight: viewModel.ackInFlight,
            onBack: { onBack() },
            onAcknowledge: { Task { await viewModel.acknowledge() } },
            onOpenSenderProfile: onOpenSenderProfile,
            onSaveToVault: { Task { await viewModel.openSaveToVaultPicker() } }
        )
    }
}

// MARK: - Loading / Error / Toast

private struct LoadingLayout: View {
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s0) {
            MailItemDetailTopBar(
                config: MailTopBarConfig(
                    eyebrow: nil,
                    trust: .neutral,
                    onBack: { @Sendable in Task { @MainActor in onBack() } }
                )
            )
            VStack(alignment: .leading, spacing: Spacing.s3) {
                Shimmer(height: 100, cornerRadius: Radii.lg)
                Shimmer(height: 80, cornerRadius: Radii.lg)
                Shimmer(height: 160, cornerRadius: Radii.lg)
            }
            .padding(Spacing.s4)
            Spacer()
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("mailDetail_loading")
    }
}

private struct ErrorLayout: View {
    let message: String
    let onBack: () -> Void
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s0) {
            MailItemDetailTopBar(
                config: MailTopBarConfig(
                    eyebrow: nil,
                    trust: .warning,
                    onBack: { @Sendable in Task { @MainActor in onBack() } }
                )
            )
            EmptyState(
                icon: .alertCircle,
                headline: "Couldn't load this item",
                subcopy: message,
                cta: EmptyState.CTA(title: "Try again") { await MainActor.run { onRetry() } }
            )
            .frame(maxHeight: .infinity)
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("mailDetail_error")
    }
}

private struct ToastBanner: View {
    let message: String

    var body: some View {
        Text(message)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(Theme.Color.appTextInverse)
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appText.opacity(0.9))
            .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
            .accessibilityLabel(message)
    }
}

#Preview {
    NavigationStack {
        MailDetailView(mailId: "preview", onBack: {})
    }
}
