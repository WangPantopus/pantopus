//
//  ChatConversationView.swift
//  Pantopus
//
//  Chat conversation screen (T2.2). One skeleton, three counterparty
//  types (person / group / ai), three frames (populated / empty / AI
//  welcome). Optimistic send with retry, debounced markRead, cursor
//  pagination on scroll-up, socket subscriptions in the VM.
//

// swiftlint:disable file_length

import SwiftUI

/// Chat conversation screen.
public struct ChatConversationView: View {
    @State private var viewModel: ChatConversationViewModel
    @State private var attachmentsPresented = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Presentation mode — drives the AI chrome (avatar, welcome card,
    /// reply bubbles). Default `.dm`.
    private let mode: ChatConversationMode
    private let onBack: @MainActor () -> Void

    public init(
        viewModel: ChatConversationViewModel,
        mode: ChatConversationMode = .dm,
        onBack: @escaping @MainActor () -> Void = {}
    ) {
        _viewModel = State(initialValue: viewModel)
        self.mode = mode
        self.onBack = onBack
    }

    public var body: some View {
        VStack(spacing: 0) {
            ChatConversationHeader(mode: mode, counterparty: viewModel.counterparty, onBack: onBack)
            content
            if viewModel.isCounterpartyTyping {
                ChatTypingIndicator(initials: incomingInitials ?? initials(of: viewModel.counterparty.displayName))
            }
            if !viewModel.queuedAttachments.isEmpty {
                AttachmentStripView(attachments: viewModel.queuedAttachments) { id in
                    viewModel.removeQueuedAttachment(id: id)
                }
            }
            ChatComposer(
                text: Binding(
                    get: { viewModel.composerText },
                    set: { viewModel.composerText = $0 }
                ),
                placeholder: composerPlaceholder,
                canSend: viewModel.canSend,
                onAttach: { attachmentsPresented = true },
                onEmoji: {},
                onSend: { Task { await viewModel.send() } }
            )
        }
        .background(Theme.Color.appSurface)
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .task { await viewModel.load() }
        .onDisappear { viewModel.teardown() }
        .confirmationDialog(
            "Attach",
            isPresented: $attachmentsPresented,
            titleVisibility: .visible
        ) {
            Button("Photo or video") { viewModel.queueSamplePhotoAttachment() }
            Button("Document") { viewModel.queueSampleDocumentAttachment() }
            Button("Listing") {}
            Button("Gig") {}
            Button("Location") {}
            Button("Cancel", role: .cancel) {}
        }
        .accessibilityIdentifier("chatConversation")
    }

    private var composerPlaceholder: String {
        if mode == .aiAssistant { return "Ask Pantopus AI…" }
        switch viewModel.counterparty {
        case let .person(name, _, _, _, _): return "Message \(firstWord(name))…"
        case let .group(name, _): return "Message \(firstWord(name))…"
        case .ai: return "Ask Pantopus AI…"
        }
    }

    private func firstWord(_ raw: String) -> String {
        raw.split(separator: " ").first.map(String.init) ?? raw
    }

    private var incomingInitials: String? {
        guard mode == .dm else { return nil }
        switch viewModel.counterparty {
        case let .person(_, initials, _, _, _): return initials
        case let .group(name, _): return initials(of: name)
        case .ai: return nil
        }
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading: loadingFrame
        case .empty: emptyFrame
        case let .loaded(rows): populatedFrame(rows)
        case let .error(message): errorFrame(message)
        }
    }

    // MARK: - Frames

    private var loadingFrame: some View {
        VStack(spacing: 0) {
            Spacer()
            ProgressView()
                .progressViewStyle(.circular)
                .tint(Theme.Color.primary600)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("chatConversationLoading")
    }

    @ViewBuilder private var emptyFrame: some View {
        if mode == .aiAssistant {
            aiWelcomeFrame
        } else {
            switch viewModel.counterparty {
            case .ai:
                aiWelcomeFrame
            case let .person(name, initials, locality, verified, _):
                personEmptyFrame(name: name, initials: initials, locality: locality, verified: verified)
            case let .group(name, _):
                personEmptyFrame(name: name, initials: initials(of: name), locality: nil, verified: false)
            }
        }
    }

    private func personEmptyFrame(name: String, initials: String, locality: String?, verified: Bool) -> some View {
        VStack(spacing: 18) {
            Spacer()
            ChatPersonAvatar(initials: initials, verified: verified, online: false, size: 64)
            VStack(spacing: 6) {
                Text("Say hi to \(firstWord(name))")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text(
                    locality.map { "You're both verified neighbors on \($0). New conversations stay private." }
                        ?? "You're both verified neighbors. New conversations stay private."
                )
                .font(.system(size: 12.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 240)
            }
            quickChipsRow
            verifiedEncryptionPill
            Spacer()
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("chatConversationEmpty")
    }

    private var quickChipsRow: some View {
        HStack(spacing: 8) {
            ForEach(viewModel.emptyChips) { chip in
                Button {
                    viewModel.composerText = chip.label
                } label: {
                    HStack(spacing: 6) {
                        Icon(chip.icon, size: 13, strokeWidth: 2.2, color: Theme.Color.primary600)
                        Text(chip.label)
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(Theme.Color.appTextStrong)
                    }
                    .padding(.horizontal, 14)
                    .frame(height: 32)
                    .background(Theme.Color.appSurface)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                            .stroke(Theme.Color.appBorder, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("chatQuickChip_\(chip.id)")
            }
        }
    }

    private var verifiedEncryptionPill: some View {
        HStack(spacing: 7) {
            Icon(.shieldCheck, size: 12, color: Theme.Color.primary600)
            Text("DMs end-to-end encrypted between verified addresses")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Theme.Color.appSurfaceMuted)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }
}

// MARK: - AI welcome

extension ChatConversationView {
    private var aiWelcomeFrame: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                aiWelcomeCard
                aiPrivacyRow
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.top, 14)
        }
        .accessibilityIdentifier("chatConversationAI")
    }

    private var aiWelcomeCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                ChatAIAvatar(size: 32)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Hi — I'm Pantopus AI")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text("I can use your verified neighbors, tasks, and mailbox to help.")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            LazyVGrid(
                columns: [GridItem(.flexible(), spacing: 6), GridItem(.flexible(), spacing: 6)],
                spacing: 6
            ) {
                ForEach(viewModel.aiPrompts) { chip in
                    AICapabilityChip(chip: chip) {
                        Task { await viewModel.sendCapabilityPrompt(chip) }
                    }
                }
            }
        }
        .padding(14)
        .background(Theme.Color.magicBgSoft)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.magicBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .accessibilityIdentifier("chatAIWelcomeCard")
    }

    private var aiPrivacyRow: some View {
        HStack(spacing: 6) {
            Icon(.shieldCheck, size: 11, strokeWidth: 2.5, color: Theme.Color.success)
            Text("Private to your account · never shared with neighbors")
                .font(.system(size: 11))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.top, 2)
    }

    // MARK: - Populated + error

    private func populatedFrame(_ rows: [ChatTimelineRow]) -> some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    if viewModel.canLoadOlder {
                        Color.clear
                            .frame(height: 1)
                            .onAppear { Task { await viewModel.loadOlder() } }
                    }
                    ForEach(rows) { row in
                        timelineRowView(row)
                    }
                    Color.clear.frame(height: 4)
                }
                .padding(.horizontal, 14)
                .padding(.top, 12)
            }
            .refreshable { await viewModel.refresh() }
            .accessibilityIdentifier("chatConversationContent")
            .onChange(of: viewModel.pendingScrollTargetId) { _, target in
                guard let target else { return }
                if reduceMotion {
                    proxy.scrollTo(target, anchor: .center)
                } else {
                    withAnimation(.easeOut(duration: 0.2)) {
                        proxy.scrollTo(target, anchor: .center)
                    }
                }
                viewModel.consumePendingScroll()
            }
        }
    }

    @ViewBuilder
    private func timelineRowView(_ row: ChatTimelineRow) -> some View {
        switch row {
        case let .dayDivider(divider):
            ChatDayDividerRow(label: divider.label)
        case let .bubble(bubble):
            ChatBubbleRow(content: bubble, incomingInitials: incomingInitials) {
                if let clientId = bubble.id.split(separator: "_").last.map(String.init),
                   bubble.id.hasPrefix("client_") {
                    Task { await viewModel.retry(clientId: "client_\(clientId)") }
                }
            }
        }
    }

    private func errorFrame(_ message: String) -> some View {
        VStack(spacing: Spacing.s3) {
            Spacer()
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load this conversation")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button { Task { await viewModel.refresh() } } label: {
                Text("Try again")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, 22)
                    .frame(height: 44)
                    .background(Theme.Color.primary600)
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            Spacer()
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("chatConversationError")
    }

    private func initials(of name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        return parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}

extension ChatConversationViewModel {
    var canLoadOlder: Bool {
        if case .loaded = state { return true }
        return false
    }
}

// MARK: - Header

private struct ChatConversationHeader: View {
    let mode: ChatConversationMode
    let counterparty: ChatCounterparty
    let onBack: @MainActor () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            Button(action: onBack) {
                Icon(.chevronLeft, size: 20, color: Theme.Color.appText)
                    .frame(width: 32, height: 32)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            avatar
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 6) {
                    Text(counterparty.displayName)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                        .lineLimit(1)
                    if mode == .aiAssistant { betaPill }
                }
                if let presence {
                    HStack(spacing: 5) {
                        if presenceOnline {
                            Circle().fill(Theme.Color.success).frame(width: 6, height: 6)
                        }
                        Text(presence)
                            .font(.system(size: 10.5, weight: .medium))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            .lineLimit(1)
                    }
                }
            }
            Spacer(minLength: 0)
            trailingActions
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .frame(height: 56)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
        .accessibilityIdentifier("chatConversationHeader")
    }

    @ViewBuilder private var avatar: some View {
        if mode == .aiAssistant {
            ChatAIAvatar(size: 32)
        } else {
            switch counterparty {
            case let .person(_, initials, _, verified, online):
                ChatPersonAvatar(initials: initials, verified: verified, online: online, size: 32)
            case let .group(name, _):
                ChatPersonAvatar(initials: groupInitials(name), verified: false, online: false, size: 32)
            case .ai:
                ChatAIAvatar(size: 32)
            }
        }
    }

    @ViewBuilder private var trailingActions: some View {
        switch counterparty {
        case .person:
            HStack(spacing: 0) {
                Icon(.phone, size: 18, color: Theme.Color.appText).frame(width: 34, height: 34)
                Icon(.video, size: 18, color: Theme.Color.appText).frame(width: 34, height: 34)
                Icon(.moreVertical, size: 18, color: Theme.Color.appText).frame(width: 34, height: 34)
            }
            .accessibilityHidden(true)
        case .ai:
            HStack(spacing: 0) {
                Icon(.history, size: 18, color: Theme.Color.appText).frame(width: 34, height: 34)
                Icon(.moreVertical, size: 18, color: Theme.Color.appText).frame(width: 34, height: 34)
            }
            .accessibilityHidden(true)
        case .group:
            Icon(.moreVertical, size: 18, color: Theme.Color.appText)
                .frame(width: 34, height: 34)
                .accessibilityHidden(true)
        }
    }

    private var betaPill: some View {
        Text("BETA")
            .font(.system(size: 9, weight: .bold))
            .tracking(0.4)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .padding(.horizontal, 6)
            .padding(.vertical, 1)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
    }

    private var presence: String? {
        switch counterparty {
        case let .person(_, _, locality, _, online):
            let prefix = online ? "Active now" : "Verified neighbor"
            if let locality { return "\(prefix) · \(locality)" }
            return prefix
        case let .group(_, memberCount):
            return memberCount.map { "\($0) members" }
        case .ai:
            return "Replies in seconds · powered by Pantopus AI"
        }
    }

    private var presenceOnline: Bool {
        if case let .person(_, _, _, _, online) = counterparty { return online }
        return false
    }

    private func groupInitials(_ name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        return parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}

// MARK: - Avatars

private struct ChatPersonAvatar: View {
    let initials: String
    let verified: Bool
    let online: Bool
    let size: CGFloat

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ZStack {
                Circle().fill(Theme.Color.primary500)
                Text(initials)
                    .font(.system(size: size * 0.4, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
            }
            .frame(width: size, height: size)
            if verified {
                Icon(.check, size: max(6, size * 0.22), strokeWidth: 3.5, color: Theme.Color.appTextInverse)
                    .frame(width: max(12, size * 0.4), height: max(12, size * 0.4))
                    .background(Theme.Color.primary600)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
                    .offset(x: 1, y: 1)
            }
            if online {
                Circle()
                    .fill(Theme.Color.success)
                    .frame(width: 9, height: 9)
                    .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
                    .offset(x: -2, y: -size + 3)
            }
        }
        .frame(width: size + 4, height: size + 4)
    }
}

// MARK: - Day divider + bubble rows

private struct ChatDayDividerRow: View {
    let label: String

    var body: some View {
        HStack(spacing: 10) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
            Text(label.uppercased())
                .font(.system(size: 11, weight: .semibold))
                .tracking(0.4)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
        .padding(.vertical, 6)
    }
}

private struct ChatBubbleRow: View {
    let content: ChatBubbleContent
    let incomingInitials: String?
    let onRetry: @MainActor () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            if content.side == .outgoing {
                Spacer(minLength: 44)
                bubbleStack
            } else {
                if let incomingInitials, showsIncomingAvatarSlot {
                    ChatMiniAvatar(initials: incomingInitials, hidden: content.isContinuation)
                }
                bubbleStack
                Spacer(minLength: 44)
            }
        }
        .frame(maxWidth: .infinity, alignment: alignmentToFrameAlignment)
        .padding(.top, content.isContinuation ? 2 : 8)
        .padding(.bottom, content.stamp == nil ? 3 : 0)
        .accessibilityElement(children: .combine)
    }

    private var bubbleStack: some View {
        VStack(alignment: alignment, spacing: 0) {
            switch content.body {
            case let .text(text):
                bubbleContainer { textBody(text) }
            case let .image(url):
                photoBubble(url)
            case let .attachment(filename, sizeLabel):
                bubbleContainer { attachmentBody(filename: filename, sizeLabel: sizeLabel) }
            case let .systemLink(label, sub, accent):
                systemLinkPill(label: label, sub: sub, accent: accent)
            case let .aiReply(text, estimate):
                aiReplyBubble(text: text, estimate: estimate)
            }
            if let stamp = content.stamp {
                stampView(stamp)
            }
        }
    }

    private func bubbleContainer(@ViewBuilder _ inner: () -> some View) -> some View {
        let isOut = content.side == .outgoing
        return inner()
            .padding(.horizontal, 13)
            .padding(.vertical, 9)
            .background(
                isOut ? Theme.Color.primary600 : Theme.Color.appSurfaceSunken,
                in: bubbleShape
            )
            .foregroundStyle(isOut ? Theme.Color.appTextInverse : Theme.Color.appText)
    }

    private func textBody(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 14))
            .lineSpacing(5)
            .frame(maxWidth: 260, alignment: .leading)
    }

    private func photoBubble(_ url: URL?) -> some View {
        imageBody(url)
            .frame(width: 200, height: 130)
            .background(Theme.Color.appSurfaceSunken)
            .clipShape(bubbleShape)
            .overlay(
                bubbleShape.stroke(
                    content.side == .incoming ? Theme.Color.appBorder : Color.clear,
                    lineWidth: content.side == .incoming ? 1 : 0
                )
            )
            .accessibilityLabel("Photo attachment")
            .accessibilityIdentifier("chatPhotoBubble_\(content.id)")
    }

    @ViewBuilder
    private func imageBody(_ url: URL?) -> some View {
        if let url {
            AsyncImage(url: url) { phase in
                switch phase {
                case let .success(image): image.resizable().scaledToFill()
                default: PhotoBubblePlaceholder()
                }
            }
        } else {
            PhotoBubblePlaceholder()
        }
    }

    private func attachmentBody(filename: String, sizeLabel: String?) -> some View {
        HStack(spacing: 8) {
            Icon(.file, size: 18, color: Theme.Color.primary600)
            VStack(alignment: .leading, spacing: 1) {
                Text(filename)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(content.side == .outgoing ? Theme.Color.appTextInverse : Theme.Color.appText)
                    .lineLimit(1)
                if let sizeLabel {
                    Text(sizeLabel)
                        .font(.system(size: 11))
                        .foregroundStyle(content.side == .outgoing ? Theme.Color.appTextInverse.opacity(0.7) : Theme.Color.appTextSecondary)
                }
            }
        }
    }

    private func systemLinkPill(label: String, sub: String, accent: ChatBubbleContent.SystemLinkAccent) -> some View {
        let fg = accentForeground(accent)
        let bg = accentBackground(accent)
        return HStack(spacing: 8) {
            Icon(.info, size: 12, strokeWidth: 2.4, color: Theme.Color.appTextInverse)
                .frame(width: 24, height: 24)
                .background(fg)
                .clipShape(Circle())
            HStack(spacing: 5) {
                Text(label)
                    .font(.system(size: 11.5, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Text(sub)
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(fg)
                    .lineLimit(1)
            }
            Icon(.chevronRight, size: 13, strokeWidth: 2.4, color: fg)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(bg)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                .stroke(fg.opacity(0.2), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
        .frame(maxWidth: .infinity)
    }

    // MARK: - AI reply

    private func aiReplyBubble(text: String, estimate: ChatEstimate?) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            aiTag
            Text(text)
                .font(.system(size: 14))
                .lineSpacing(5)
                .foregroundStyle(Theme.Color.appText)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let estimate {
                AIEstimateCard(estimate: estimate)
            }
        }
        .padding(.horizontal, 13)
        .padding(.vertical, 10)
        .frame(maxWidth: 300, alignment: .leading)
        .background(Theme.Color.appSurfaceSunken, in: aiBubbleShape)
    }

    private var aiTag: some View {
        HStack(spacing: 4) {
            Icon(.bot, size: 9, strokeWidth: 3, color: Theme.Color.magic)
            Text("PANTOPUS AI")
                .font(.system(size: 9.5, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(Theme.Color.magic)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 1)
        .background(Theme.Color.magicBg)
        .clipShape(Capsule())
    }

    private var aiBubbleShape: some Shape {
        UnevenRoundedRectangle(
            topLeadingRadius: 16,
            bottomLeadingRadius: content.hasTail ? 4 : 16,
            bottomTrailingRadius: 16,
            topTrailingRadius: 16
        )
    }

    private func stampView(_ raw: String) -> some View {
        HStack(spacing: 4) {
            if content.side == .outgoing, content.deliveryState == .read {
                ChatReadReceipt(timestamp: raw)
            } else {
                Text(stampString(raw))
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                if content.side == .outgoing {
                    switch content.deliveryState {
                    case .read:
                        EmptyView()
                    case .delivered:
                        Icon(.check, size: 11, strokeWidth: 2.4, color: Theme.Color.appTextSecondary)
                    case .sending:
                        ProgressView().scaleEffect(0.6).tint(Theme.Color.appTextSecondary)
                    case .failed:
                        Button(action: onRetry) {
                            HStack(spacing: 3) {
                                Icon(.alertCircle, size: 11, color: Theme.Color.error)
                                Text("Retry")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(Theme.Color.error)
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("chatRetry_\(content.id)")
                    case .none:
                        EmptyView()
                    }
                }
            }
        }
        .padding(.top, 2)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: alignmentToFrameAlignment)
    }

    private func stampString(_ raw: String) -> String {
        switch content.deliveryState {
        case .read: "Read \(raw)"
        case .delivered: "\(raw) · Delivered"
        case .sending: "Sending…"
        case .failed: "Couldn't send"
        case .none: raw
        }
    }

    private var alignment: HorizontalAlignment {
        content.side == .outgoing ? .trailing : .leading
    }

    private var alignmentToFrameAlignment: Alignment {
        content.side == .outgoing ? .trailing : .leading
    }

    private var bubbleShape: some Shape {
        UnevenRoundedRectangle(
            topLeadingRadius: 16,
            bottomLeadingRadius: content.side == .incoming && content.hasTail ? 4 : 16,
            bottomTrailingRadius: content.side == .outgoing && content.hasTail ? 4 : 16,
            topTrailingRadius: 16
        )
    }

    private var showsIncomingAvatarSlot: Bool {
        guard content.side == .incoming else { return false }
        if case .systemLink = content.body { return false }
        return true
    }

    private func accentForeground(_ accent: ChatBubbleContent.SystemLinkAccent) -> Color {
        switch accent {
        case .primary: Theme.Color.primary600
        case .success: Theme.Color.success
        case .warning: Theme.Color.warning
        case .error: Theme.Color.error
        }
    }

    private func accentBackground(_ accent: ChatBubbleContent.SystemLinkAccent) -> Color {
        switch accent {
        case .primary: Theme.Color.primary50
        case .success: Theme.Color.successBg
        case .warning: Theme.Color.warningBg
        case .error: Theme.Color.errorBg
        }
    }
}

private struct ChatMiniAvatar: View {
    let initials: String
    var hidden = false

    var body: some View {
        ZStack {
            Circle().fill(Theme.Color.primary500)
            Text(initials)
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(Theme.Color.appTextInverse)
        }
        .frame(width: 26, height: 26)
        .opacity(hidden ? 0 : 1)
        .accessibilityHidden(true)
    }
}

private struct PhotoBubblePlaceholder: View {
    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Theme.Color.appSurfaceSunken
            HStack(spacing: 10) {
                ForEach(0..<6, id: \.self) { index in
                    RoundedRectangle(cornerRadius: Radii.xs, style: .continuous)
                        .fill(Theme.Color.appSurface.opacity(index.isMultiple(of: 2) ? 0.38 : 0.22))
                        .frame(width: 18)
                        .rotationEffect(.degrees(24))
                        .offset(y: CGFloat(index % 3) * 7)
                }
            }
            .padding(.leading, 8)
            .accessibilityHidden(true)
            Text("Photo")
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .padding(.horizontal, 7)
                .padding(.vertical, 2)
                .background(Theme.Color.appSurface.opacity(0.72), in: Capsule())
                .padding(.leading, 10)
                .padding(.bottom, 8)
        }
    }
}

private struct ChatReadReceipt: View {
    let timestamp: String

    var body: some View {
        HStack(spacing: 4) {
            Text("Read \(timestamp)")
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(Theme.Color.appTextSecondary)
            HStack(spacing: -2) {
                Icon(.check, size: 9, strokeWidth: 2.6, color: Theme.Color.primary600)
                Icon(.check, size: 9, strokeWidth: 2.6, color: Theme.Color.primary600)
            }
            .accessibilityHidden(true)
        }
        .accessibilityLabel("Read \(timestamp)")
        .accessibilityIdentifier("chatReadReceipt")
    }
}

// MARK: - Typing indicator

private struct ChatTypingIndicator: View {
    let initials: String
    @State private var isAnimating = false

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            ChatMiniAvatar(initials: initials)
            HStack(spacing: 3) {
                ForEach(0..<3, id: \.self) { i in
                    Circle()
                        .fill(Theme.Color.appTextSecondary)
                        .opacity(isAnimating ? 1.0 : 0.3)
                        .frame(width: 6, height: 6)
                        .animation(
                            .easeInOut(duration: 0.6)
                                .repeatForever(autoreverses: true)
                                .delay(Double(i) * 0.18),
                            value: isAnimating
                        )
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Theme.Color.appSurfaceSunken)
            .overlay(
                UnevenRoundedRectangle(
                    topLeadingRadius: 16,
                    bottomLeadingRadius: 4,
                    bottomTrailingRadius: 16,
                    topTrailingRadius: 16
                )
                .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(
                UnevenRoundedRectangle(
                    topLeadingRadius: 16,
                    bottomLeadingRadius: 4,
                    bottomTrailingRadius: 16,
                    topTrailingRadius: 16
                )
            )
            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.top, 8)
        .padding(.bottom, 6)
        .onAppear {
            isAnimating = true
        }
        .accessibilityLabel("Typing")
        .accessibilityIdentifier("chatTypingIndicator")
    }
}

// MARK: - Attachment strip

private struct AttachmentStripView: View {
    let attachments: [ChatQueuedAttachment]
    let onRemove: @MainActor (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(attachments) { attachment in
                    AttachmentTile(attachment: attachment, onRemove: onRemove)
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 10)
            .padding(.bottom, 6)
        }
        .background(Theme.Color.appSurface)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
        .accessibilityIdentifier("chatAttachmentStrip")
    }
}

private struct AttachmentTile: View {
    let attachment: ChatQueuedAttachment
    let onRemove: @MainActor (String) -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            tileBody
                .frame(width: 64, height: 64)
                .background(Theme.Color.appSurfaceSunken)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            Button { onRemove(attachment.id) } label: {
                ZStack {
                    Circle()
                        .fill(Theme.Color.appText.opacity(0.78))
                        .frame(width: 18, height: 18)
                    Icon(.x, size: 11, strokeWidth: 3, color: Theme.Color.appTextInverse)
                }
                .frame(width: 44, height: 44, alignment: .topTrailing)
                .padding(.top, 3)
                .padding(.trailing, 3)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove \(attachment.filename)")
            .accessibilityIdentifier("chatAttachmentRemove_\(attachment.id)")
        }
        .frame(width: 64, height: 64)
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder private var tileBody: some View {
        switch attachment.kind {
        case .image:
            PhotoBubblePlaceholder()
                .accessibilityLabel("Queued image \(attachment.filename)")
        case .document:
            VStack(spacing: 3) {
                Icon(.fileText, size: 22, color: Theme.Color.primary600)
                Text(attachment.filename)
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .lineLimit(1)
                    .padding(.horizontal, 4)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.Color.appSurface)
            .accessibilityLabel("Queued document \(attachment.filename)")
        }
    }
}

// MARK: - Composer

private struct ChatComposer: View {
    @Binding var text: String
    let placeholder: String
    let canSend: Bool
    let onAttach: @MainActor () -> Void
    let onEmoji: @MainActor () -> Void
    let onSend: @MainActor () -> Void

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            Button(action: onAttach) {
                Icon(.plus, size: 18, strokeWidth: 2.4, color: Theme.Color.appTextStrong)
                    .frame(width: 36, height: 36)
                    .background(Theme.Color.appSurfaceSunken, in: Circle())
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Attach")
            .accessibilityIdentifier("chatComposerAttach")

            HStack(alignment: .bottom, spacing: 8) {
                TextField(placeholder, text: $text, axis: .vertical)
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1...4)
                    .submitLabel(.send)
                    .onSubmit { if canSend { onSend() } }
                Button(action: onEmoji) {
                    Icon(.smile, size: 17, color: Theme.Color.appTextMuted)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Emoji")
                .accessibilityIdentifier("chatComposerEmoji")
            }
            .padding(.leading, 14)
            .padding(.trailing, 4)
            .padding(.vertical, 2)
            .frame(minHeight: 36)
            .background(Theme.Color.appSurfaceSunken)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))

            Button(action: onSend) {
                Icon(.arrowUp, size: 18, strokeWidth: 2.5, color: canSend ? Theme.Color.appTextInverse : Theme.Color.appTextMuted)
                    .frame(width: 36, height: 36)
                    .background(canSend ? Theme.Color.primary600 : Theme.Color.appSurfaceSunken, in: Circle())
                    .shadow(color: canSend ? Theme.Color.primary600.opacity(0.30) : .clear, radius: 10, x: 0, y: 4)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .disabled(!canSend)
            .accessibilityLabel("Send")
            .accessibilityIdentifier("chatComposerSend")
        }
        .padding(.horizontal, 12)
        .padding(.top, 10)
        .padding(.bottom, 24)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
    }
}
