//
//  ShareLinkSheet.swift
//  Pantopus
//
//  Foundation (I0b) — C3 Share your link. A system share sheet for the owner's
//  booking link: a context overline + pillar dot, a copyable URL with a sky
//  Copy button, share targets, a QR (real, CoreImage), two settings toggles,
//  and a quiet Regenerate. Identity (overline/dot/QR) follows the link's pillar;
//  functional controls stay product sky. A draft banner leads when not live.
//

import CoreImage.CIFilterBuiltins
import SwiftUI
import UIKit

/// Owner booking-link share sheet. Persistence (toggles), system share, and
/// regeneration are wired by the presenting stream via callbacks.
public struct ShareLinkSheet: View {
    private let url: String
    private let theme: SchedulingIdentityTheme
    private let isLive: Bool
    private let showOnProfile: Bool
    private let addToSignature: Bool
    private let onCopy: () -> Void
    private let onShare: () -> Void
    private let onMessages: () -> Void
    private let onEmail: () -> Void
    private let onToggleShowOnProfile: @Sendable (Bool) -> Void
    private let onToggleSignature: @Sendable (Bool) -> Void
    private let onRegenerate: () -> Void
    private let onTurnOnPage: (() -> Void)?

    @State private var copied = false
    @State private var showQR = false
    @State private var confirmRegenerate = false

    public init(
        url: String,
        theme: SchedulingIdentityTheme,
        isLive: Bool,
        showOnProfile: Bool,
        addToSignature: Bool,
        onCopy: @escaping () -> Void,
        onShare: @escaping () -> Void,
        onMessages: @escaping () -> Void,
        onEmail: @escaping () -> Void,
        onToggleShowOnProfile: @escaping @Sendable (Bool) -> Void,
        onToggleSignature: @escaping @Sendable (Bool) -> Void,
        onRegenerate: @escaping () -> Void,
        onTurnOnPage: (() -> Void)? = nil
    ) {
        self.url = url
        self.theme = theme
        self.isLive = isLive
        self.showOnProfile = showOnProfile
        self.addToSignature = addToSignature
        self.onCopy = onCopy
        self.onShare = onShare
        self.onMessages = onMessages
        self.onEmail = onEmail
        self.onToggleShowOnProfile = onToggleShowOnProfile
        self.onToggleSignature = onToggleSignature
        self.onRegenerate = onRegenerate
        self.onTurnOnPage = onTurnOnPage
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s4) {
                if !isLive { draftBanner }
                overline
                urlCard
                shareTargets
                qrCard
                toggles
                regenerate
            }
            .padding(Spacing.s5)
        }
        .background(Theme.Color.appSurface)
        .accessibilityIdentifier("scheduling.shareLinkSheet")
        .fullScreenCover(isPresented: $showQR) { qrFullScreen }
        .alert("Regenerate this link?", isPresented: $confirmRegenerate) {
            Button("Cancel", role: .cancel) {}
            Button("Regenerate", role: .destructive, action: onRegenerate)
        } message: {
            Text("The old link stops working.")
        }
    }

    // MARK: - Pieces

    private var draftBanner: some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            Icon(.info, size: 16, color: Theme.Color.warning)
            VStack(alignment: .leading, spacing: Spacing.s1) {
                Text("This page isn't live yet. People can't book until you turn it on.")
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appText)
                if let onTurnOnPage {
                    Button("Turn on", action: onTurnOnPage)
                        .font(Theme.Font.small)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.primary600)
                }
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.warningBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }

    private var overline: some View {
        HStack(spacing: Spacing.s2) {
            Circle().fill(theme.accent).frame(width: 8, height: 8)
            Text("\(theme.title) booking link")
                .pantopusTextStyle(.overline)
                .foregroundStyle(theme.accent)
        }
    }

    private var urlCard: some View {
        HStack(spacing: Spacing.s3) {
            Text(url)
                .font(.system(size: 14, design: .monospaced))
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(1)
                .truncationMode(.middle)
            Spacer(minLength: Spacing.s2)
            Button {
                UIPasteboard.general.string = url
                copied = true
                onCopy()
            } label: {
                HStack(spacing: Spacing.s1) {
                    Icon(copied ? .check : .copy, size: 14, color: copied ? Theme.Color.success : Theme.Color.appTextInverse)
                    Text(copied ? "Copied" : "Copy")
                        .font(Theme.Font.small)
                        .fontWeight(.semibold)
                        .foregroundStyle(copied ? Theme.Color.success : Theme.Color.appTextInverse)
                }
                .padding(.horizontal, Spacing.s3)
                .padding(.vertical, Spacing.s2)
                .background(copied ? Theme.Color.successBg : Theme.Color.primary600)
                .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(copied ? "Link copied" : "Copy link")
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }

    private var shareTargets: some View {
        HStack(spacing: Spacing.s3) {
            shareTile(icon: .share, label: "Share", action: onShare)
            shareTile(icon: .scanLine, label: "QR code") { showQR = true }
            shareTile(icon: .messageCircle, label: "Messages", action: onMessages)
            shareTile(icon: .mail, label: "Email", action: onEmail)
        }
    }

    private func shareTile(icon: PantopusIcon, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: Spacing.s2) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .fill(Theme.Color.primary50)
                        .frame(height: 52)
                    Icon(icon, size: 22, color: Theme.Color.primary600)
                }
                Text(label)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity)
        .accessibilityLabel(label)
    }

    private var qrCard: some View {
        Button { showQR = true } label: {
            HStack(spacing: Spacing.s3) {
                qrImage(url, size: 44)
                    .frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Show QR")
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    Text("Scan to book")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
                Spacer(minLength: Spacing.s2)
                Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
            }
            .padding(Spacing.s3)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var toggles: some View {
        VStack(spacing: 0) {
            toggleRow("Show on my profile", isOn: showOnProfile, onChange: onToggleShowOnProfile)
            Divider().background(Theme.Color.appBorderSubtle)
            toggleRow("Add to email signature", isOn: addToSignature, onChange: onToggleSignature)
        }
        .padding(.horizontal, Spacing.s3)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }

    private func toggleRow(_ label: String, isOn: Bool, onChange: @escaping @Sendable (Bool) -> Void) -> some View {
        Toggle(isOn: Binding(get: { isOn }, set: onChange)) {
            Text(label)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
        }
        .tint(Theme.Color.primary600)
        .padding(.vertical, Spacing.s2)
    }

    private var regenerate: some View {
        Button("Regenerate link") { confirmRegenerate = true }
            .font(Theme.Font.small)
            .foregroundStyle(Theme.Color.error)
            .frame(maxWidth: .infinity)
            .padding(.top, Spacing.s2)
    }

    // MARK: - QR fullscreen

    private var qrFullScreen: some View {
        VStack(spacing: Spacing.s5) {
            HStack {
                Spacer()
                Button("Done") { showQR = false }
                    .font(Theme.Font.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.primary600)
            }
            Spacer()
            qrImage(url, size: 240)
                .frame(width: 240, height: 240)
                .padding(Spacing.s5)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            Text(url)
                .font(.system(size: 13, design: .monospaced))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Spacer()
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Color.appBg)
    }

    @ViewBuilder
    private func qrImage(_ string: String, size: CGFloat) -> some View {
        if let cg = Self.qrCGImage(string, size: size) {
            Image(decorative: cg, scale: 1)
                .interpolation(.none)
                .resizable()
                .scaledToFit()
        } else {
            RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                .fill(Theme.Color.appSurfaceSunken)
        }
    }

    private static func qrCGImage(_ string: String, size: CGFloat) -> CGImage? {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let ciImage = filter.outputImage, ciImage.extent.width > 0 else { return nil }
        let scale = max(size / ciImage.extent.width, 1)
        let scaled = ciImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        return context.createCGImage(scaled, from: scaled.extent)
    }
}

#if DEBUG
#Preview {
    ShareLinkSheet(
        url: "pantopus.com/book/alexkim",
        theme: SchedulingIdentityTheme(.personal),
        isLive: false,
        showOnProfile: true,
        addToSignature: false,
        onCopy: {},
        onShare: {},
        onMessages: {},
        onEmail: {},
        onToggleShowOnProfile: { _ in },
        onToggleSignature: { _ in },
        onRegenerate: {},
        onTurnOnPage: {}
    )
}
#endif
