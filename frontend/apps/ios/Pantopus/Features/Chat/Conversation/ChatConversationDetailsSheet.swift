//
//  ChatConversationDetailsSheet.swift
//  Pantopus
//
//  Conversation-details drawer (Phase 3) opened from the person-thread
//  header's info button. Shows the pair's topics and a Safety section
//  with a confirm-to-block action (`POST /api/users/:userId/block`).
//  No "Report" row yet — the backend exposes no chat-report endpoint;
//  that lands in Phase 4.
//

import SwiftUI

struct ChatConversationDetailsSheet: View {
    let viewModel: ChatConversationViewModel
    /// Called after a successful block — the host dismisses the drawer
    /// and pops the thread.
    let onBlocked: @MainActor () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var blockConfirmPresented = false
    @State private var blockFailedPresented = false

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s5) {
                    topicsSection
                    safetySection
                }
                .padding(.top, Spacing.s2)
                .padding(.bottom, Spacing.s6)
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s4)
        .background(Theme.Color.appSurface)
        .alert("Block \(firstName)?", isPresented: $blockConfirmPresented) {
            Button("Cancel", role: .cancel) {}
            Button("Block", role: .destructive) {
                Task {
                    if await viewModel.blockCounterparty() {
                        onBlocked()
                    } else {
                        blockFailedPresented = true
                    }
                }
            }
        } message: {
            Text("\(firstName) won't be able to message you, and new conversations with them are prevented.")
        }
        .alert("Couldn't block \(firstName)", isPresented: $blockFailedPresented) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Please try again.")
        }
        .accessibilityIdentifier("chatConversationDetails")
    }

    private var header: some View {
        HStack {
            Text("Conversation details")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Spacer(minLength: Spacing.s0)
            Button { dismiss() } label: {
                Icon(.x, size: 16, strokeWidth: 2.5, color: Theme.Color.appTextSecondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
            .accessibilityIdentifier("chatDetailsClose")
        }
    }

    // MARK: - Topics

    private var topicsSection: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            sectionLabel("Topics")
            if viewModel.topics.isEmpty {
                Text("No topics yet — share a task or listing to start one.")
                    .font(.system(size: 12.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            } else {
                ForEach(viewModel.topics) { topic in
                    topicRow(topic)
                }
            }
        }
    }

    private func topicRow(_ topic: ChatConversationTopic) -> some View {
        HStack(spacing: Spacing.s3) {
            Icon(icon(for: topic.topicType), size: 14, strokeWidth: 2.2, color: Theme.Color.primary600)
                .frame(width: 30, height: 30)
                .background(Theme.Color.primary50)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            Text(topic.title)
                .font(.system(size: 13.5, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(1)
            Spacer(minLength: Spacing.s0)
            if let status = topic.status, !status.isEmpty {
                Text(status.capitalized)
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .padding(.horizontal, Spacing.s2)
                    .padding(.vertical, 2)
                    .background(Theme.Color.appSurfaceSunken)
                    .clipShape(Capsule())
            }
        }
        .frame(minHeight: 44)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("chatDetailsTopic_\(topic.id)")
    }

    // MARK: - Safety

    private var safetySection: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            sectionLabel("Safety")
            Button {
                blockConfirmPresented = true
            } label: {
                HStack(spacing: Spacing.s3) {
                    Icon(.ban, size: 14, strokeWidth: 2.4, color: Theme.Color.error)
                        .frame(width: 30, height: 30)
                        .background(Theme.Color.errorBg)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                    Text("Block \(firstName)")
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.error)
                    Spacer(minLength: Spacing.s0)
                    if viewModel.isBlocking {
                        ProgressView().tint(Theme.Color.error)
                    }
                }
                .frame(minHeight: 44)
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isBlocking)
            .accessibilityIdentifier("chatDetailsBlock")
            // Phase-4: a "Report <name>" row belongs here once the
            // backend exposes a chat-report endpoint.
        }
    }

    // MARK: - Helpers

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.system(size: 10.5, weight: .bold))
            .tracking(0.5)
            .foregroundStyle(Theme.Color.appTextMuted)
    }

    private var firstName: String {
        let name = viewModel.counterparty.displayName
        return name.split(separator: " ").first.map(String.init) ?? name
    }

    /// Same topicType → icon mapping as the topic strip.
    private func icon(for type: String) -> PantopusIcon {
        switch type {
        case "task": .briefcase
        case "listing": .tag
        case "home": .home
        case "business": .building2
        default: .messageCircle
        }
    }
}
