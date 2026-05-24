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
    private let creatorContext: ChatCreatorThreadContext?
    private let onOpenAudienceProfile: @MainActor () -> Void
    private let onBack: @MainActor () -> Void

    public init(
        viewModel: ChatConversationViewModel,
        mode: ChatConversationMode = .dm,
        creatorContext: ChatCreatorThreadContext? = nil,
        onOpenAudienceProfile: @escaping @MainActor () -> Void = {},
        onBack: @escaping @MainActor () -> Void = {}
    ) {
        _viewModel = State(initialValue: viewModel)
        self.mode = mode
        self.creatorContext = creatorContext
        self.onOpenAudienceProfile = onOpenAudienceProfile
        self.onBack = onBack
    }

    public var body: some View {
        VStack(spacing: 0) {
            ChatConversationHeader(
                mode: mode,
                counterparty: viewModel.counterparty,
                creatorContext: resolvedCreatorContext,
                onBack: onBack
            )
            if mode == .creatorThread {
                ChatCreatorAudienceStrip(
                    context: resolvedCreatorContext,
                    onOpenAudienceProfile: onOpenAudienceProfile
                )
                ChatCreatorQuotaMeter(quota: resolvedCreatorContext.quota)
            }
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

    private var resolvedCreatorContext: ChatCreatorThreadContext {
        creatorContext ?? .defaults()
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
        case let .broadcastReference(reference):
            ChatBroadcastReferenceCard(reference: reference)
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
    let mode: ChatConversationMode
    let counterparty: ChatCounterparty
    let creatorContext: ChatCreatorThreadContext
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
                    if mode == .creatorThread {
                        CreatorTierChip(
                            name: creatorContext.fanTierName,
                            rank: creatorContext.fanTierRank
                        )
                    }
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
        .frame(height: mode == .creatorThread ? 64 : 56)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
        .accessibilityIdentifier("chatConversationHeader")
    }

    @ViewBuilder private var avatar: some View {
        if mode == .aiAssistant {
            ChatAIAvatar(size: 32)
        } else if mode == .creatorThread {
            switch counterparty {
            case let .person(_, initials, _, verified, online):
                ChatPersonAvatar(
                    initials: initials,
                    verified: verified,
                    online: online,
                    size: 32,
                    ringColor: creatorTierColor(rank: creatorContext.fanTierRank)
                )
            case let .group(name, _):
                ChatPersonAvatar(
                    initials: groupInitials(name),
                    verified: false,
                    online: false,
                    size: 32,
                    ringColor: creatorTierColor(rank: creatorContext.fanTierRank)
                )
            case .ai:
                ChatAIAvatar(size: 32)
            }
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
        if mode == .creatorThread {
            HStack(spacing: 0) {
                Icon(.user, size: 18, color: Theme.Color.appText).frame(width: 34, height: 34)
                Icon(.moreHorizontal, size: 18, color: Theme.Color.appText).frame(width: 34, height: 34)
            }
            .accessibilityHidden(true)
        } else {
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
        if mode == .creatorThread {
            return creatorContext.fanSubtitle
        }
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
        if mode == .creatorThread {
            return creatorContext.fanTierRank > 1
        }
        if case let .person(_, _, _, _, online) = counterparty { return online }
        return false
    }

    private func groupInitials(_ name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        return parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}

// MARK: - Creator chrome

private struct ChatCreatorAudienceStrip: View {
    let context: ChatCreatorThreadContext
    let onOpenAudienceProfile: @MainActor () -> Void

    var body: some View {
        Button(action: onOpenAudienceProfile) {
            HStack(spacing: 10) {
                Icon(.users, size: 15, strokeWidth: 2.4, color: Theme.Color.appTextInverse)
                    .frame(width: 28, height: 28)
                    .background(Theme.Color.business)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text("Creator inbox · \(context.personaName)")
                        .font(.system(size: 11.5, weight: .bold))
                        .tracking(0.4)
                        .foregroundStyle(Theme.Color.business)
                        .textCase(.uppercase)
                        .lineLimit(1)
                    Text(context.audienceSummary)
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                Icon(.chevronRight, size: 16, strokeWidth: 2.4, color: Theme.Color.business)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(Theme.Color.businessBg)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.business.opacity(0.18), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .padding(.horizontal, 12)
            .padding(.top, 10)
            .padding(.bottom, 0)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Open audience profile. \(context.audienceSummary)")
        .accessibilityIdentifier("chatCreatorAudienceStrip")
    }
}

private struct ChatCreatorQuotaMeter: View {
    let quota: ChatCreatorQuota

    private var progress: CGFloat {
        guard quota.total > 0 else { return 0 }
        return min(1, max(0, CGFloat(quota.used) / CGFloat(quota.total)))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                HStack(spacing: 5) {
                    Icon(.messageSquare, size: 11, strokeWidth: 2.5, color: Theme.Color.business)
                    Text("Replies this week")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer(minLength: 0)
                Text("\(quota.used) of \(quota.total) replies this week")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(Theme.Color.appSurfaceSunken)
                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(Theme.Color.primary600)
                        .frame(width: proxy.size.width * progress)
                }
            }
            .frame(height: 4)
            HStack(spacing: 4) {
                Icon(.refreshCw, size: 10, strokeWidth: 2.4, color: Theme.Color.appTextMuted)
                Text(quota.resetCopy)
                    .font(.system(size: 10))
                    .foregroundStyle(Theme.Color.appTextMuted)
                Spacer(minLength: 0)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(quota.used) of \(quota.total) replies this week. \(quota.resetCopy)")
        .accessibilityIdentifier("chatCreatorQuotaMeter")
    }
}

private struct CreatorTierChip: View {
    let name: String
    let rank: Int

    var body: some View {
        HStack(spacing: 3) {
            if let icon = tierIcon {
                Icon(icon, size: 9, strokeWidth: 2.4, color: creatorTierColor(rank: rank))
            }
            Text(name.uppercased())
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(creatorTierColor(rank: rank))
                .lineLimit(1)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background(creatorTierBgColor(rank: rank))
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
        .accessibilityLabel("\(name) tier")
        .accessibilityIdentifier("chatCreatorTierChip")
    }

    private var tierIcon: PantopusIcon? {
        if rank >= 4 { return .crown }
        if rank >= 2 { return .shield }
        return nil
    }
}

private func creatorTierColor(rank: Int) -> Color {
    switch rank {
    case 1: Theme.Color.appTextSecondary
    case 2: Theme.Color.warning
    case 3: Theme.Color.appTextStrong
    case 4: Theme.Color.warning
    default: Theme.Color.appTextSecondary
    }
}

private func creatorTierBgColor(rank: Int) -> Color {
    switch rank {
    case 1: Theme.Color.appSurfaceSunken
    case 2: Theme.Color.warningBg
    case 3: Theme.Color.appSurfaceSunken
    case 4: Theme.Color.warningLight
    default: Theme.Color.appSurfaceSunken
    }
}

// MARK: - Avatars

private struct ChatPersonAvatar: View {
    let initials: String
    let verified: Bool
    let online: Bool
    let size: CGFloat
    var ringColor: Color?

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ZStack {
                Circle().fill(Theme.Color.primary500)
                Text(initials)
                    .font(.system(size: size * 0.4, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
            }
            .frame(width: size, height: size)
            .overlay {
                if let ringColor {
                    Circle()
                        .stroke(Theme.Color.appSurface, lineWidth: 4)
                    Circle()
                        .stroke(ringColor, lineWidth: 2)
                        .padding(1)
                }
            }
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

private struct ChatBroadcastReferenceCard: View {
    let reference: ChatBroadcastReference

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Icon(.radioTower, size: 15, strokeWidth: 2.5, color: Theme.Color.business)
                .frame(width: 30, height: 30)
                .background(Theme.Color.businessBg)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            VStack(alignment: .leading, spacing: 4) {
                Text("Broadcast referenced")
                    .font(.system(size: 10, weight: .bold))
                    .tracking(0.4)
                    .foregroundStyle(Theme.Color.business)
                    .textCase(.uppercase)
                Text(reference.title)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                Text(reference.subtitle)
                    .font(.system(size: 11.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(2)
                Text(reference.metric)
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextStrong)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.business.opacity(0.18), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("chatBroadcastReference_\(reference.id)")
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
            case let .aiReply(text, estimate):
                aiReplyBubble(text: text, estimate: estimate)
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
