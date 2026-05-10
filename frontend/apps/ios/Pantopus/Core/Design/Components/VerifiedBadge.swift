//
//  VerifiedBadge.swift
//  Pantopus
//
//  Small green-check badge pinned to an avatar corner.
//

import SwiftUI

/// Circular success-tinted check badge.
///
/// - Parameter size: Outer diameter; defaults to 16pt.
@MainActor
public struct VerifiedBadge: View {
    private let size: CGFloat

    public init(size: CGFloat = 16) {
        self.size = size
    }

    public var body: some View {
        ZStack {
            Circle().fill(Theme.Color.success)
            Icon(.check, size: size * 0.6, color: Theme.Color.appTextInverse)
        }
        .frame(width: size, height: size)
        .overlay(
            Circle().stroke(Theme.Color.appSurface, lineWidth: 1.5)
        )
        .accessibilityLabel("Verified")
    }
}

#Preview {
    HStack(spacing: Spacing.s3) {
        VerifiedBadge()
        VerifiedBadge(size: 20)
        VerifiedBadge(size: 28)
    }
    .padding()
}
