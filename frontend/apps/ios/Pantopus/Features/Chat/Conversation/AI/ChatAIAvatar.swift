//
//  ChatAIAvatar.swift
//  Pantopus
//
//  A15.3 — the Pantopus AI mark. A flat `business`-purple circle with
//  the `bot` glyph, used wherever the AI assistant appears (chat-list
//  row, conversation header, welcome card). Matches the RN reference's
//  circular AI icon. Flat fill, no gradient — the app shell carries no
//  gradients on the mobile side.
//

import SwiftUI

/// The Pantopus AI avatar: a flat `business` circle with a white `bot`
/// glyph.
public struct ChatAIAvatar: View {
    private let size: CGFloat

    public init(size: CGFloat) {
        self.size = size
    }

    public var body: some View {
        Circle()
            .fill(Theme.Color.business)
            .frame(width: size, height: size)
            .overlay(
                Icon(.bot, size: size * 0.55, strokeWidth: 2.2, color: Theme.Color.appTextInverse)
            )
            .accessibilityElement()
            .accessibilityLabel("Pantopus AI")
    }
}

#Preview {
    HStack(spacing: Spacing.s4) {
        ChatAIAvatar(size: 32)
        ChatAIAvatar(size: 44)
    }
    .padding()
    .background(Theme.Color.appSurface)
}
