//
//  MailDetailView.swift
//  Pantopus
//
//  T6.5b (P20) — Generic mail item detail (A17.1). Sits on the shared
//  `MailItemDetailShell` (P19) and wires every slot from the mail item
//  DTO. P21-P23 will compose the same shell with variant-specific
//  slot views for package / coupon / booklet / certified.
//

import SwiftUI

// swiftlint:disable file_length multiple_closures_with_trailing_closure trailing_closure

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
        .animation(.easeInOut(duration: 0.2), value: viewModel.toast)
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
        // T6.5c (P21) — dispatch to variant layouts when the projected
        // content carries decoded variant payloads. Variants sit on the
        // same `MailItemDetailShell` and override only the slots their
        // design diverges on; generic A17.1 falls through.
        if content.category == .booklet, let booklet = content.bookletDetail {
            BookletDetailLayout(
                content: content,
                booklet: booklet,
                ackInFlight: viewModel.ackInFlight,
                onBack: { onBack() },
                onAcknowledge: { Task { await viewModel.acknowledge() } },
                onOpenSenderProfile: onOpenSenderProfile,
                onSaveToVault: { Task { await viewModel.openSaveToVaultPicker() } }
            )
        } else if content.category == .certified, let certified = content.certifiedDetail {
            CertifiedDetailLayout(
                content: content,
                certified: certified,
                ackInFlight: viewModel.ackInFlight,
                onBack: { onBack() },
                onAcknowledge: { Task { await viewModel.acknowledge() } },
                onOpenSenderProfile: onOpenSenderProfile,
                onSaveToVault: { Task { await viewModel.openSaveToVaultPicker() } }
            )
        } else if content.category == .community, let community = content.communityDetail {
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
            MailItemDetailShell(
                topBar: makeTopBar(for: content),
                aiElf: makeAIElf(for: content),
                attachments: makeAttachments(for: content),
                hero: { HeroCard(content: content) },
                keyFacts: { KeyFactsCard(rows: content.keyFacts()) },
                body: { BodyCard(paragraphs: content.bodyParagraphs) },
                sender: { SenderCard(content: content, onOpenProfile: onOpenSenderProfile) },
                actions: {
                    ActionsRow(
                        content: content,
                        ackInFlight: viewModel.ackInFlight,
                        onAck: { Task { await viewModel.acknowledge() } }
                    )
                }
            )
        }
    }

    private func makeTopBar(for content: MailDetailContent) -> MailTopBarConfig {
        MailTopBarConfig(
            eyebrow: content.category.label,
            trust: content.detailTrust,
            onBack: { @Sendable in
                Task { @MainActor in onBack() }
            },
            trailingAction: nil,
            overflowItems: [
                MailOverflowItem(id: "forward", icon: .send, label: "Forward") {},
                MailOverflowItem(id: "saveToVault", icon: .bookmark, label: "Save to vault") { @Sendable in
                    Task { @MainActor in await viewModel.openSaveToVaultPicker() }
                },
                MailOverflowItem(id: "archive", icon: .archive, label: "Archive") {},
                MailOverflowItem(id: "unread", icon: .bell, label: "Mark unread") {},
                MailOverflowItem(
                    id: "delete",
                    icon: .trash2,
                    label: "Delete",
                    isDestructive: true
                ) {},
                MailOverflowItem(id: "report", icon: .info, label: "Report") {}
            ]
        )
    }

    private func makeAIElf(for content: MailDetailContent) -> AIElfStripContent? {
        guard let summary = content.aiSummary, !summary.isEmpty else { return nil }
        return AIElfStripContent(summary: summary)
    }

    private func makeAttachments(for content: MailDetailContent) -> AttachmentsRowContent? {
        guard !content.attachments.isEmpty else { return nil }
        let items = content.attachments.enumerated().map { index, name in
            AttachmentItem(
                id: "att-\(index)",
                kind: Self.guessKind(for: name),
                name: name
            )
        }
        return AttachmentsRowContent(items: items)
    }

    /// Cheap heuristic from filename extension. Backend will eventually
    /// expose `kind` per-attachment and this helper retires.
    private static func guessKind(for name: String) -> AttachmentKind {
        let lower = name.lowercased()
        if lower.hasSuffix(".pdf") { return .pdf }
        if lower.hasSuffix(".jpg") || lower.hasSuffix(".jpeg") ||
            lower.hasSuffix(".png") || lower.hasSuffix(".heic") || lower.hasSuffix(".webp") {
            return .image
        }
        if lower.hasSuffix(".mp4") || lower.hasSuffix(".mov") { return .video }
        if lower.hasSuffix(".mp3") || lower.hasSuffix(".m4a") { return .audio }
        if lower.hasPrefix("http://") || lower.hasPrefix("https://") { return .link }
        return .other
    }
}

// MARK: - Slot subviews

private struct HeroCard: View {
    let content: MailDetailContent

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(alignment: .center, spacing: Spacing.s1) {
                CategoryBadge(category: content.category)
                Spacer()
                if let received = content.createdAtLabel {
                    Text(received)
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            Text(content.senderDisplayName.uppercased())
                .font(.system(size: 11, weight: .semibold))
                .tracking(0.6)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Text(content.title)
                .font(.system(size: 19, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .fixedSize(horizontal: false, vertical: true)
            if let excerpt = content.excerpt, !excerpt.isEmpty {
                Text(excerpt)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .leading) {
            Rectangle()
                .fill(content.category.accent)
                .frame(width: 4)
        }
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
    }
}

private struct CategoryBadge: View {
    let category: MailItemCategory

    var body: some View {
        HStack(spacing: 4) {
            Icon(category.icon, size: 11, color: category.accent)
            Text(category.label)
                .font(.system(size: 10, weight: .bold))
                .tracking(0.4)
                .foregroundStyle(category.accent)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 3)
        .background(category.rowBackground)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.pill)
                .stroke(category.rowBackground, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
    }
}

private struct KeyFactsCard: View {
    let rows: [MailDetailKeyFact]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("KEY FACTS")
                .font(.system(size: 11, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Spacing.s3)
                .padding(.top, Spacing.s2)
                .padding(.bottom, Spacing.s2)
                .accessibilityAddTraits(.isHeader)
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                HStack(alignment: .top, spacing: Spacing.s3) {
                    Icon(row.icon, size: 13, color: Theme.Color.appTextStrong)
                        .frame(width: 24, height: 24)
                        .background(Theme.Color.appSurfaceSunken)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                    VStack(alignment: .leading, spacing: 1) {
                        Text(row.label.uppercased())
                            .font(.system(size: 11, weight: .semibold))
                            .tracking(0.4)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                        Text(row.value)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, Spacing.s3)
                .padding(.vertical, Spacing.s2)
                if index < rows.count - 1 {
                    Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
                }
            }
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
    }
}

private struct BodyCard: View {
    let paragraphs: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("NOTICE TEXT")
                .font(.system(size: 11, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityAddTraits(.isHeader)
            ForEach(Array(paragraphs.enumerated()), id: \.offset) { _, paragraph in
                Text(paragraph)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
    }
}

private struct SenderCard: View {
    let content: MailDetailContent
    let onOpenProfile: (@MainActor (String) -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("SENDER")
                .font(.system(size: 11, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityAddTraits(.isHeader)
            row
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
    }

    private var row: some View {
        HStack(spacing: Spacing.s3) {
            avatar
            VStack(alignment: .leading, spacing: 2) {
                Text(content.senderDisplayName)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                if let meta = content.senderMeta {
                    Text(meta)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                HStack(spacing: 4) {
                    Icon(content.trust.icon, size: 11, color: content.trust.foreground)
                    Text(content.trust.label)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(content.trust.foreground)
                }
                .padding(.top, 2)
            }
            Spacer(minLength: 0)
            if onOpenProfile != nil, content.senderUserId != nil {
                Icon(.chevronRight, size: 14, color: Theme.Color.appTextMuted)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            if let onOpenProfile, let userId = content.senderUserId {
                onOpenProfile(userId)
            }
        }
    }

    private var avatar: some View {
        Text(content.senderInitials)
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(Theme.Color.appTextInverse)
            .frame(width: 44, height: 44)
            .background(content.category.accent)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct ActionsRow: View {
    let content: MailDetailContent
    let ackInFlight: Bool
    let onAck: @MainActor () -> Void

    var body: some View {
        VStack(spacing: Spacing.s2) {
            if content.ackRequired || content.isAcknowledged {
                acknowledgeButton
            }
            secondaryRow
        }
    }

    private var acknowledgeButton: some View {
        Button(action: { onAck() }) {
            HStack(spacing: Spacing.s2) {
                Icon(
                    content.isAcknowledged ? .checkCircle : .check,
                    size: 16,
                    color: content.isAcknowledged ? Theme.Color.success : Theme.Color.appTextInverse
                )
                Text(content.isAcknowledged ? "Acknowledged · Tap to undo" : "Acknowledge receipt")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(
                        content.isAcknowledged ? Theme.Color.success : Theme.Color.appTextInverse
                    )
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                content.isAcknowledged ? Theme.Color.appSurface : Theme.Color.primary600
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(
                        content.isAcknowledged ? Theme.Color.successLight : Color.clear,
                        lineWidth: 1.5
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .opacity(ackInFlight ? 0.6 : 1)
        }
        .buttonStyle(.plain)
        .disabled(ackInFlight)
        .accessibilityIdentifier("mailDetail_acknowledge")
    }

    private var secondaryRow: some View {
        HStack(spacing: Spacing.s2) {
            secondaryTile(icon: .send, label: "Reply")
            secondaryTile(icon: .arrowRight, label: "Forward")
            secondaryTile(icon: .archive, label: "Archive")
        }
    }

    private func secondaryTile(icon: PantopusIcon, label: String) -> some View {
        Button(action: {}) {
            VStack(spacing: 4) {
                Icon(icon, size: 17, color: Theme.Color.appTextStrong)
                Text(label)
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextStrong)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

private struct LoadingLayout: View {
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
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
        VStack(spacing: 0) {
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
