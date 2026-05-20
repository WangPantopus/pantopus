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
    private let onBack: @MainActor () -> Void

    public init(
        viewModel: ChatConversationViewModel,
        onBack: @escaping @MainActor () -> Void = {}
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
    }

    public var body: some View {
        VStack(spacing: 0) {
            ChatConversationHeader(counterparty: viewModel.counterparty, onBack: onBack)
            content
            if viewModel.isCounterpartyTyping {
                ChatTypingIndicator(name: viewModel.counterparty.displayName)
            }
            ChatComposer(
                text: Binding(
                    get: { viewModel.composerText },
                    set: { viewModel.composerText = $0 }
                ),
                placeholder: composerPlaceholder,
                canSend: viewModel.canSend,
                onAttach: { attachmentsPresented = true },
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
            Button("Photo or video") {}
            Button("Listing") {}
            Button("Gig") {}
            Button("Location") {}
            Button("Payment request") {}
            Button("Cancel", role: .cancel) {}
        }
        .accessibilityIdentifier("chatConversation")
    }

    private var composerPlaceholder: String {
        switch viewModel.counterparty {
        case let .person(name, _, _, _, _): "Message \(firstWord(name))…"
        case let .group(name, _): "Message \(firstWord(name))…"
        case .ai: "Ask anything…"
        }
    }

    private func firstWord(_ raw: String) -> String {
        raw.split(separator: " ").first.map(String.init) ?? raw
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
        switch viewModel.counterparty {
        case .ai:
            aiWelcomeFrame
        case let .person(name, initials, locality, verified, _):
            personEmptyFrame(name: name, initials: initials, locality: locality, verified: verified)
        case let .group(name, _):
            personEmptyFrame(name: name, initials: initials(of: name), locality: nil, verified: false)
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

    // MARK: - AI welcome

    private var aiWelcomeFrame: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top, spacing: 10) {
                    ChatAIAvatar(size: 32)
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 5) {
                            Icon(.sparkles, size: 10, strokeWidth: 2.4, color: Theme.Color.primary700)
                            Text("PANTOPUS AI")
                                .font(.system(size: 10, weight: .bold))
                                .tracking(0.4)
                                .foregroundStyle(Theme.Color.primary700)
                        }
                        Text("Hi! I can help you post tasks, find listings, or summarize mail. What can I help with today?")
                            .font(.system(size: 14))
                            .foregroundStyle(Theme.Color.appText)
                    }
                    .padding(12)
                    .background(
                        LinearGradient(
                            colors: [Theme.Color.primary50, Theme.Color.appSurface],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                            .stroke(Theme.Color.primary100, lineWidth: 1)
                    )
                    .clipShape(
                        UnevenRoundedRectangle(
                            topLeadingRadius: 4,
                            bottomLeadingRadius: Radii.lg,
                            bottomTrailingRadius: Radii.lg,
                            topTrailingRadius: Radii.lg
                        )
                    )
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 14)
                VStack(alignment: .leading, spacing: 7) {
                    Text("SUGGESTED")
                        .font(.system(size: 10, weight: .bold))
                        .tracking(0.4)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                    HStack(spacing: 8) {
                        ForEach(viewModel.aiPrompts) { prompt in
                            Button { viewModel.tapAIPrompt(prompt) } label: {
                                HStack(spacing: 6) {
                                    Icon(prompt.icon, size: 13, strokeWidth: 2.2, color: Theme.Color.primary600)
                                    Text(prompt.label)
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
                            .accessibilityIdentifier("chatAIPrompt_\(prompt.id)")
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.leading, 42)
                Spacer(minLength: 24)
            }
            .padding(.top, 18)
        }
        .accessibilityIdentifier("chatConversationAI")
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
            ChatBubbleRow(content: bubble) {
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
                    if case .ai = counterparty { betaPill }
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
        switch counterparty {
        case let .person(_, initials, _, verified, online):
            ChatPersonAvatar(initials: initials, verified: verified, online: online, size: 32)
        case let .group(name, _):
            ChatPersonAvatar(initials: groupInitials(name), verified: false, online: false, size: 32)
        case .ai:
            ChatAIAvatar(size: 32)
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

private struct ChatAIAvatar: View {
    let size: CGFloat

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Theme.Color.primary500, Theme.Color.primary700],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Icon(.sparkles, size: size * 0.5, strokeWidth: 2.4, color: Theme.Color.appTextInverse)
        }
        .frame(width: size, height: size)
        .accessibilityLabel("AI")
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
    let onRetry: @MainActor () -> Void

    var body: some View {
        VStack(alignment: alignment, spacing: 0) {
            switch content.body {
            case let .text(text):
                bubbleContainer { textBody(text) }
            case let .image(url):
                bubbleContainer { imageBody(url) }
            case let .attachment(filename, sizeLabel):
                bubbleContainer { attachmentBody(filename: filename, sizeLabel: sizeLabel) }
            case let .systemLink(label, sub, accent):
                systemLinkPill(label: label, sub: sub, accent: accent)
            }
            if let stamp = content.stamp {
                stampView(stamp)
            }
        }
        .frame(maxWidth: .infinity, alignment: alignmentToFrameAlignment)
        .padding(.bottom, content.stamp == nil ? 3 : 0)
        .accessibilityElement(children: .combine)
    }

    private func bubbleContainer(@ViewBuilder _ inner: () -> some View) -> some View {
        let isOut = content.side == .outgoing
        return inner()
            .padding(.horizontal, 13)
            .padding(.vertical, 9)
            .frame(maxWidth: .infinity, alignment: alignmentToFrameAlignment)
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

    @ViewBuilder
    private func imageBody(_ url: URL?) -> some View {
        if let url {
            AsyncImage(url: url) { phase in
                switch phase {
                case let .success(image): image.resizable().scaledToFill()
                default: Theme.Color.appSurfaceSunken
                }
            }
            .frame(width: 220, height: 140)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        } else {
            Theme.Color.appSurfaceSunken.frame(width: 220, height: 140)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
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

    private func stampView(_ raw: String) -> some View {
        HStack(spacing: 4) {
            Text(stampString(raw))
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(Theme.Color.appTextSecondary)
            if content.side == .outgoing {
                switch content.deliveryState {
                case .read:
                    // Render as a tight pair of single-checks — SF
                    // Symbols ships no native "double check" glyph
                    // (`.checkCheck` maps to `checkmark.circle`,
                    // which we reuse for "completed/paid" status
                    // chips elsewhere). Two `.check`s with -2 spacing
                    // matches the design's `check-check` intent
                    // closely enough that the read indicator reads as
                    // a WhatsApp-style double check.
                    HStack(spacing: -2) {
                        Icon(.check, size: 9, strokeWidth: 2.6, color: Theme.Color.primary600)
                        Icon(.check, size: 9, strokeWidth: 2.6, color: Theme.Color.primary600)
                    }
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
        .padding(.top, 2)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: alignmentToFrameAlignment)
    }

    private func stampString(_ raw: String) -> String {
        switch content.deliveryState {
        case .read: "\(raw) · Read"
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

// MARK: - Typing indicator

private struct ChatTypingIndicator: View {
    let name: String
    @State private var dotPhase: Int = 0

    var body: some View {
        HStack(spacing: 7) {
            HStack(spacing: 3) {
                ForEach(0..<3, id: \.self) { i in
                    Circle()
                        .fill(Theme.Color.appTextSecondary)
                        .opacity(dotPhase == i ? 1.0 : 0.4)
                        .frame(width: 5, height: 5)
                }
            }
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(Theme.Color.appSurfaceSunken)
            .clipShape(Capsule())
            Text("\(name) is typing…")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Theme.Color.appTextSecondary)
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .onAppear {
            Task {
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: 220_000_000)
                    dotPhase = (dotPhase + 1) % 3
                }
            }
        }
        .accessibilityIdentifier("chatTypingIndicator")
    }
}

// MARK: - Composer

private struct ChatComposer: View {
    @Binding var text: String
    let placeholder: String
    let canSend: Bool
    let onAttach: @MainActor () -> Void
    let onSend: @MainActor () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            Button(action: onAttach) {
                Icon(.plus, size: 18, strokeWidth: 2.4, color: Theme.Color.appTextStrong)
                    .frame(width: 38, height: 38)
                    .background(Theme.Color.appSurfaceSunken)
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Attach")
            .accessibilityIdentifier("chatComposerAttach")

            TextField(placeholder, text: $text, axis: .vertical)
                .font(.system(size: 14))
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(1...4)
                .padding(.horizontal, 16)
                .frame(minHeight: 40)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(Capsule())
                .submitLabel(.send)
                .onSubmit { if canSend { onSend() } }

            Button(action: onSend) {
                Icon(.send, size: 17, color: canSend ? Theme.Color.appTextInverse : Theme.Color.appTextMuted)
                    .frame(width: 38, height: 38)
                    .background(canSend ? Theme.Color.primary600 : Theme.Color.appSurfaceSunken)
                    .clipShape(Circle())
                    .shadow(color: canSend ? Theme.Color.primary600.opacity(0.30) : .clear, radius: 10, x: 0, y: 4)
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
