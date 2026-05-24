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
    @State private var upgradePromptPresented = false
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
            if mode == .fanThread {
                FanMembershipStripe(entitlement: activeFanEntitlement) {
                    upgradePromptPresented = true
                }
            }
            content
            if viewModel.isCounterpartyTyping {
                ChatTypingIndicator(name: viewModel.counterparty.displayName)
            }
            if mode == .fanThread {
                FanQuotaGate(entitlement: activeFanEntitlement) {
                    upgradePromptPresented = true
                }
            }
            ChatComposer(
                text: Binding(
                    get: { viewModel.composerText },
                    set: { viewModel.composerText = $0 }
                ),
                placeholder: composerPlaceholder,
                canSend: viewModel.canSend,
                showsSendCost: mode == .fanThread && !isFanReplyLocked,
                isLockedAction: isFanReplyLocked,
                onAttach: { attachmentsPresented = true },
                onSend: { sendOrPromptForUpgrade() }
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
        .sheet(isPresented: $upgradePromptPresented) {
            FanTierUpgradePromptSheet(entitlement: activeFanEntitlement)
                .presentationDetents([.medium])
        }
        .accessibilityIdentifier("chatConversation")
    }

    private var composerPlaceholder: String {
        if mode == .aiAssistant { return "Ask Pantopus AI…" }
        if mode == .fanThread {
            let first = firstWord(viewModel.counterparty.displayName)
            if let required = activeFanEntitlement.requiredReplyTier {
                return "Upgrade to \(required) to reply…"
            }
            return "Message \(first)… (uses 1 of \(activeFanEntitlement.messageLimit))"
        }
        switch viewModel.counterparty {
        case let .person(name, _, _, _, _): return "Message \(firstWord(name))…"
        case let .group(name, _): return "Message \(firstWord(name))…"
        case .ai: return "Ask Pantopus AI…"
        }
    }

    private var activeFanEntitlement: ChatFanEntitlement {
        viewModel.fanEntitlement ?? ChatFanEntitlement(
            currentTier: "Bronze",
            renewsOn: "Apr 12",
            messagesLeft: 3,
            messageLimit: 5,
            resetCopy: "Resets May 1"
        )
    }

    private var isFanReplyLocked: Bool {
        mode == .fanThread && !activeFanEntitlement.canReply
    }

    private func sendOrPromptForUpgrade() {
        if isFanReplyLocked {
            upgradePromptPresented = true
            return
        }
        Task { await viewModel.send() }
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
        } else if mode == .fanThread {
            fanEmptyFrame
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

    private var fanEmptyFrame: some View {
        ScrollView {
            VStack(spacing: 18) {
                FanAutoWelcomeCard()
                VStack(spacing: 12) {
                    Text("Start a conversation")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(
                        "You can message \(firstWord(viewModel.counterparty.displayName)) directly. " +
                            "Each send uses one of your monthly \(activeFanEntitlement.currentTier) replies."
                    )
                    .font(.system(size: 12.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 280)
                    FanQuotaHero(entitlement: activeFanEntitlement)
                    FanOpeners { label in
                        viewModel.composerText = label
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 8)
            }
            .padding(.horizontal, 14)
            .padding(.top, 14)
            .padding(.bottom, 16)
        }
        .accessibilityIdentifier("chatConversationFanEmpty")
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
                    if mode == .fanThread {
                        FanAutoWelcomeCard()
                            .padding(.bottom, 12)
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
            ChatBubbleRow(
                content: bubble,
                onLockedAction: { upgradePromptPresented = true },
                onRetry: {
                    if let clientId = bubble.id.split(separator: "_").last.map(String.init),
                       bubble.id.hasPrefix("client_") {
                        Task { await viewModel.retry(clientId: "client_\(clientId)") }
                    }
                }
            )
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

// MARK: - Fan thread chrome

private struct FanMembershipStripe: View {
    let entitlement: ChatFanEntitlement
    let onManage: @MainActor () -> Void

    var body: some View {
        HStack(spacing: 8) {
            HStack(spacing: 4) {
                Icon(.crown, size: 10, strokeWidth: 2.6, color: Theme.Color.warning)
                Text(entitlement.currentTier.uppercased())
                    .font(.system(size: 9.5, weight: .bold))
                    .tracking(0.4)
                    .foregroundStyle(Theme.Color.warning)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Theme.Color.warningBg)
            .clipShape(Capsule())

            HStack(spacing: 4) {
                Icon(.calendar, size: 11, color: Theme.Color.appTextMuted)
                Text("renews ")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Text(entitlement.renewsOn)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
            }
            Spacer(minLength: 0)
            Button(action: onManage) {
                HStack(spacing: 3) {
                    Text("Manage")
                    Icon(.chevronRight, size: 11, strokeWidth: 2.5, color: Theme.Color.primary600)
                }
                .font(.system(size: 10.5, weight: .semibold))
                .foregroundStyle(Theme.Color.primary600)
                .frame(minWidth: 44, minHeight: 44)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("chatFanManageMembership")
        }
        .padding(.leading, 14)
        .padding(.trailing, 8)
        .frame(height: 44)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
        .accessibilityIdentifier("chatFanMembershipStripe")
    }
}

private struct FanQuotaGate: View {
    let entitlement: ChatFanEntitlement
    let onUpgrade: @MainActor () -> Void

    var body: some View {
        HStack(spacing: 8) {
            HStack(spacing: 4) {
                Icon(.messageSquare, size: 11, strokeWidth: 2.4, color: gateColor)
                Text("\(entitlement.messagesLeft)")
                    .font(.system(size: 11, weight: .heavy))
                Text("of \(entitlement.messageLimit) left")
                    .font(.system(size: 10.5, weight: .bold))
            }
            .foregroundStyle(gateColor)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(entitlement.canReply ? Theme.Color.infoBg : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                    .stroke(entitlement.canReply ? Theme.Color.infoLight : Theme.Color.warningLight, lineWidth: 1)
            )
            .clipShape(Capsule())

            Text(entitlement.requiredReplyTier.map { "\($0) required" } ?? entitlement.resetCopy)
                .font(.system(size: 10.5, weight: .medium))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .lineLimit(1)
            Spacer(minLength: 0)
            Button(action: onUpgrade) {
                HStack(spacing: 3) {
                    Text("Upgrade")
                    Icon(.arrowUpRight, size: 11, strokeWidth: 2.5, color: Theme.Color.primary600)
                }
                .font(.system(size: 10.5, weight: .bold))
                .foregroundStyle(Theme.Color.primary600)
                .frame(minWidth: 44, minHeight: 44)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("chatFanQuotaUpgrade")
        }
        .padding(.leading, 14)
        .padding(.trailing, 8)
        .frame(height: 50)
        .background(entitlement.canReply ? Theme.Color.appSurface : Theme.Color.warningBg)
        .overlay(alignment: .top) {
            Rectangle().fill(entitlement.canReply ? Theme.Color.appBorder : Theme.Color.warningLight).frame(height: 1)
        }
        .accessibilityIdentifier("chatFanQuotaGate")
    }

    private var gateColor: Color {
        entitlement.canReply ? Theme.Color.primary700 : Theme.Color.warning
    }
}

private struct FanAutoWelcomeCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 4) {
                Icon(.sparkles, size: 9, strokeWidth: 2.8, color: Theme.Color.business)
                Text("AUTO-WELCOME · FREE")
                    .font(.system(size: 9.5, weight: .bold))
                    .tracking(0.6)
                    .foregroundStyle(Theme.Color.business)
            }
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(Theme.Color.businessBg)
            .clipShape(Capsule())

            Text("Welcome to the Diary, Maria.")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(
                "First message is on me — ask anything bread-related, share a bake, " +
                    "or just say hi. I read everything personally on Sunday evenings."
            )
            .font(.system(size: 12.5))
            .lineSpacing(4)
            .foregroundStyle(Theme.Color.appTextStrong)
            Text("— Wynn")
                .font(.system(size: 12, weight: .medium))
                .italic()
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .shadow(color: Theme.Color.appText.opacity(0.06), radius: 6, x: 0, y: 2)
        .accessibilityIdentifier("chatFanAutoWelcome")
    }
}

private struct FanQuotaHero: View {
    let entitlement: ChatFanEntitlement

    var body: some View {
        HStack(spacing: 6) {
            Icon(.messageSquare, size: 13, strokeWidth: 2.5, color: Theme.Color.primary700)
            Text("\(entitlement.messageLimit) messages")
                .font(.system(size: 12, weight: .heavy))
                .foregroundStyle(Theme.Color.primary700)
            Text("this period")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.Color.primary700)
            Text("·")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Theme.Color.primary300)
            Text(entitlement.resetCopy)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Theme.Color.primary600)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Theme.Color.infoBg)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                .stroke(Theme.Color.infoLight, lineWidth: 1)
        )
        .clipShape(Capsule())
        .accessibilityIdentifier("chatFanQuotaHero")
    }
}

private struct FanOpeners: View {
    let onSelect: @MainActor (String) -> Void

    private let openers: [FanOpener] = [
        FanOpener(
            id: "recipe",
            icon: .helpCircle,
            label: "Recipe question",
            title: "Why does my crumb come out tight on day 2?"
        ),
        FanOpener(
            id: "photo",
            icon: .image,
            label: "Share a bake",
            title: "Send a photo for feedback"
        ),
        FanOpener(
            id: "workshop",
            icon: .calendar,
            label: "Workshops",
            title: "When's the next hands-on session?"
        )
    ]

    var body: some View {
        VStack(spacing: 8) {
            ForEach(openers, id: \.id) { opener in
                Button {
                    onSelect(opener.title)
                } label: {
                    HStack(spacing: 10) {
                        Icon(opener.icon, size: 14, color: Theme.Color.business)
                            .frame(width: 30, height: 30)
                            .background(Theme.Color.businessBg)
                            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(opener.label.uppercased())
                                .font(.system(size: 9.5, weight: .bold))
                                .tracking(0.6)
                                .foregroundStyle(Theme.Color.appTextMuted)
                            Text(opener.title)
                                .font(.system(size: 12.5, weight: .medium))
                                .foregroundStyle(Theme.Color.appText)
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)
                        }
                        Spacer(minLength: 0)
                        Icon(.chevronRight, size: 14, color: Theme.Color.appTextMuted)
                    }
                    .padding(.horizontal, 12)
                    .frame(minHeight: 50)
                    .background(Theme.Color.appSurface)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                            .stroke(Theme.Color.appBorder, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("chatFanOpener_\(opener.id)")
            }
        }
        .accessibilityIdentifier("chatFanOpeners")
    }
}

private struct FanOpener: Identifiable {
    let id: String
    let icon: PantopusIcon
    let label: String
    let title: String
}

struct FanTierUpgradePromptSheet: View {
    let entitlement: ChatFanEntitlement

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                Icon(.lock, size: 20, color: Theme.Color.primary600)
                    .frame(width: 44, height: 44)
                    .background(Theme.Color.primary50)
                    .clipShape(Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text("Upgrade to \(entitlement.requiredReplyTier ?? "Silver")")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text("Your \(entitlement.currentTier) support does not unlock this reply yet.")
                        .font(.system(size: 12.5))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                FanUpgradeBenefit(icon: .messageSquare, title: "Read tier-locked replies")
                FanUpgradeBenefit(icon: .arrowUpRight, title: "Reply without the current tier gate")
                FanUpgradeBenefit(icon: .crown, title: "Keep supporting this creator")
            }

            Button {} label: {
                Text("Upgrade membership")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Theme.Color.primary600)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("chatFanUpgradeConfirm")
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 24)
        .background(Theme.Color.appSurface)
        .accessibilityIdentifier("chatFanUpgradePromptSheet")
    }
}

private struct FanUpgradeBenefit: View {
    let icon: PantopusIcon
    let title: String

    var body: some View {
        HStack(spacing: 10) {
            Icon(icon, size: 14, color: Theme.Color.primary600)
                .frame(width: 28, height: 28)
                .background(Theme.Color.primary50)
                .clipShape(Circle())
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextStrong)
        }
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
                    if mode == .fanThread { personaPill }
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
        } else if mode == .fanThread {
            ChatFanPersonaAvatar(initials: fanInitials, size: 32)
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
        if mode == .fanThread {
            HStack(spacing: 0) {
                Icon(.externalLink, size: 18, color: Theme.Color.appText).frame(width: 34, height: 34)
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

    private var personaPill: some View {
        HStack(spacing: 3) {
            Icon(.briefcase, size: 9, strokeWidth: 2.6, color: Theme.Color.business)
            Text("Persona")
                .font(.system(size: 9, weight: .bold))
                .tracking(0.4)
                .foregroundStyle(Theme.Color.business)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background(Theme.Color.businessBg)
        .clipShape(Capsule())
    }

    private var presence: String? {
        if mode == .fanThread {
            switch counterparty {
            case let .person(_, _, locality, _, _):
                return locality.map { "\($0) · creator" } ?? "Creator"
            default:
                return "Creator"
            }
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
        if mode == .fanThread { return true }
        if case let .person(_, _, _, _, online) = counterparty { return online }
        return false
    }

    private var fanInitials: String {
        switch counterparty {
        case let .person(_, initials, _, _, _): initials
        case let .group(name, _): groupInitials(name)
        case .ai: "AI"
        }
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

private struct ChatFanPersonaAvatar: View {
    let initials: String
    let size: CGFloat

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Circle()
                .fill(Theme.Color.business)
                .frame(width: size, height: size)
            Text(initials)
                .font(.system(size: size * 0.35, weight: .bold))
                .foregroundStyle(Theme.Color.appTextInverse)
            Icon(.check, size: max(6, size * 0.22), strokeWidth: 3.5, color: Theme.Color.appTextInverse)
                .frame(width: max(12, size * 0.4), height: max(12, size * 0.4))
                .background(Theme.Color.business)
                .clipShape(Circle())
                .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
                .offset(x: 1, y: 1)
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
    let onLockedAction: @MainActor () -> Void
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
            if let tier = content.sentSupportTier, content.side == .outgoing {
                paidSupportFooter(tier: tier)
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
        return ZStack(alignment: .bottom) {
            inner()
                .padding(.horizontal, 13)
                .padding(.vertical, 9)
                .frame(maxWidth: .infinity, alignment: alignmentToFrameAlignment)
                .background(
                    isOut ? Theme.Color.primary600 : Theme.Color.appSurfaceSunken,
                    in: bubbleShape
                )
                .foregroundStyle(isOut ? Theme.Color.appTextInverse : Theme.Color.appText)
            if let tier = content.lockedTier, content.side == .incoming {
                lockedPaywallOverlay(tier: tier)
            }
        }
        .clipShape(bubbleShape)
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

    private func lockedPaywallOverlay(tier: String) -> some View {
        VStack {
            Spacer(minLength: 12)
            Button(action: onLockedAction) {
                HStack(spacing: 6) {
                    Icon(.lock, size: 12, strokeWidth: 2.6, color: Theme.Color.primary600)
                    Text("Upgrade to read")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Theme.Color.primary600)
                    Text(tier)
                        .font(.system(size: 10.5, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                .padding(.horizontal, 10)
                .frame(minHeight: 44)
                .background(Theme.Color.appSurface)
                .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Upgrade to \(tier) to read")
            .accessibilityIdentifier("chatLockedUpgrade_\(content.id)")
        }
        .frame(maxWidth: 260)
        .background(
            LinearGradient(
                colors: [
                    Theme.Color.appSurface.opacity(0.15),
                    Theme.Color.appSurface.opacity(0.96)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        )
        .accessibilityIdentifier("chatPaywallOverlay_\(content.id)")
    }

    private func paidSupportFooter(tier: String) -> some View {
        HStack(spacing: 5) {
            Icon(.crown, size: 10, strokeWidth: 2.4, color: Theme.Color.warning)
            Text("Sent with \(tier) support")
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(Theme.Color.warning)
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .background(Theme.Color.warningBg)
        .clipShape(Capsule())
        .padding(.top, 4)
        .accessibilityIdentifier("chatPaidSupportFooter_\(content.id)")
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
    let showsSendCost: Bool
    let isLockedAction: Bool
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
                ZStack(alignment: .topTrailing) {
                    Icon(isLockedAction ? .lock : .send, size: 17, color: canSend ? Theme.Color.appTextInverse : Theme.Color.appTextMuted)
                        .frame(width: 38, height: 38)
                        .background(sendBackground)
                        .clipShape(Circle())
                        .shadow(color: canSend ? sendBackground.opacity(0.30) : .clear, radius: 10, x: 0, y: 4)
                    if showsSendCost && canSend {
                        Text("-1")
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundStyle(Theme.Color.primary700)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1)
                            .background(Theme.Color.appSurface)
                            .overlay(Capsule().stroke(Theme.Color.primary600, lineWidth: 1))
                            .clipShape(Capsule())
                            .offset(x: 7, y: -6)
                    }
                }
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

    private var sendBackground: Color {
        guard canSend else { return Theme.Color.appSurfaceSunken }
        return isLockedAction ? Theme.Color.warning : Theme.Color.primary600
    }
}
