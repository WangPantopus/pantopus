//
//  BookingPageSupport.swift
//  Pantopus
//
//  Shared building blocks for Stream I4 (Booking page & sharing): the
//  public booking-link URL builder, the system share/messages/email
//  actions, an avatar, and a couple of small token-styled card primitives
//  reused by C1 (management), C2 (preview), C4 (one-off generator) and
//  H16 (zero-state).
//
//  Tokens only — no hardcoded colors/spacing. Owner context, DTOs, endpoints
//  and SharedUI come from Foundation and are consumed read-only.
//

import SwiftUI
import UIKit

// MARK: - Public booking-link URL builder

/// Builds the human-facing booking link shown in management, share and
/// one-off surfaces. The backend serves the public page JSON at
/// `/api/public/book/:slug`; the *shareable* page lives at `<web>/book/:slug`
/// and one-off `path`s arrive as `/book/o/:token`. There is no web-origin in
/// `AppEnvironment` yet, so the origin is centralised here (matches the
/// design's `pantopus.com`). A shared `publicBookingBaseURL` in Foundation/
/// config would be the better long-term home — flagged in the I4 PR.
enum BookingLinkURL {
    /// Human-facing origin without a scheme, e.g. `pantopus.com`.
    static let displayOrigin = "pantopus.com"
    /// Scheme used for tappable/QR links.
    static let scheme = "https"

    /// `pantopus.com/book/<slug>` — for monospace display.
    static func display(slug: String) -> String {
        "\(displayOrigin)/book/\(slug)"
    }

    /// `https://pantopus.com/book/<slug>` — for share/QR/open.
    static func shareable(slug: String) -> String {
        "\(scheme)://\(display(slug: slug))"
    }

    /// A backend one-off `path` (e.g. `/book/o/<token>`) → `pantopus.com/book/o/<token>`.
    static func display(path: String) -> String {
        "\(displayOrigin)\(normalized(path))"
    }

    /// A backend one-off `path` → `https://pantopus.com/book/o/<token>`.
    static func shareable(path: String) -> String {
        "\(scheme)://\(display(path: path))"
    }

    private static func normalized(_ path: String) -> String {
        path.hasPrefix("/") ? path : "/\(path)"
    }
}

// MARK: - Share / messages / email actions

/// Centralises the share-target side effects the C3 `ShareLinkSheet` and the
/// C4 generated-link card invoke. The `ShareLinkSheet` copies to the
/// pasteboard itself; `copy` here is for our own footer buttons.
@MainActor
enum BookingLinkActions {
    static func copy(_ string: String) {
        UIPasteboard.general.string = string
    }

    /// `sms:&body=<link>` deep link into Messages. No-op if unconstructable.
    static func openMessages(with link: String, openURL: OpenURLAction) {
        let body = link.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? link
        guard let url = URL(string: "sms:&body=\(body)") else { return }
        openURL(url)
    }

    /// `mailto:` with a booking-link subject + body.
    static func openEmail(with link: String, openURL: OpenURLAction) {
        let subject = "Book a time with me".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let body = link.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? link
        guard let url = URL(string: "mailto:?subject=\(subject)&body=\(body)") else { return }
        openURL(url)
    }

    /// Presents the system `UIActivityViewController`. Uses UIKit topmost-VC
    /// presentation so it works when invoked from inside another SwiftUI
    /// sheet (ShareLinkSheet / one-off generated card), where stacking a
    /// second `.sheet` would be fragile.
    static func presentShare(_ items: [Any]) {
        guard let scene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene,
            let root = scene.keyWindow?.rootViewController
        else { return }
        var top = root
        while let presented = top.presentedViewController { top = presented }
        let activity = UIActivityViewController(activityItems: items, applicationActivities: nil)
        activity.popoverPresentationController?.sourceView = top.view
        top.present(activity, animated: true)
    }
}

// MARK: - Avatar

/// A simple round avatar: remote image with an accent-tinted initials
/// fallback. Lighter than `AvatarWithIdentityRing` (no progress ring) and
/// reused by C1's header and C2's preview.
struct BookingAvatar: View {
    let name: String
    var imageURLString: String?
    var size: CGFloat = 48
    var accent: Color = Theme.Color.primary600

    var body: some View {
        Group {
            if let imageURLString, let url = URL(string: imageURLString) {
                AsyncImage(url: url) { phase in
                    if case let .success(image) = phase {
                        image.resizable().scaledToFill()
                    } else {
                        initialsCircle
                    }
                }
            } else {
                initialsCircle
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var initialsCircle: some View {
        ZStack {
            Circle().fill(accent.opacity(0.15))
            Text(initials)
                .font(.system(size: size * 0.36, weight: .bold))
                .foregroundStyle(accent)
        }
    }

    private var initials: String {
        let parts = name.split(separator: " ").prefix(2).compactMap(\.first)
        return parts.isEmpty ? "?" : String(parts).uppercased()
    }
}

// MARK: - Card primitives

/// White rounded card with a hairline border + small shadow — the booking
/// surface card chrome used across I4 (1px `appBorder`, 16px radius, `sm`).
struct BookingCard<Content: View>: View {
    var padding: CGFloat = Spacing.s4
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(padding)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .pantopusShadow(.sm)
    }
}

/// 11px uppercase secondary overline used above grouped cards/fields.
struct CardOverline: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 11, weight: .bold))
            .tracking(0.8)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .accessibilityAddTraits(.isHeader)
    }
}

/// An inline note banner ("Turn on at least one service…", draft hints).
struct InlineNote: View {
    enum Tone { case warning, info, error }
    let tone: Tone
    let text: String
    var icon: PantopusIcon = .info

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            Icon(icon, size: 15, color: foreground)
            Text(text)
                .pantopusTextStyle(.caption)
                .foregroundStyle(foreground)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(Spacing.s3)
        .background(background)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private var foreground: Color {
        switch tone {
        case .warning: Theme.Color.warning
        case .info: Theme.Color.info
        case .error: Theme.Color.error
        }
    }

    private var background: Color {
        switch tone {
        case .warning: Theme.Color.warningBg
        case .info: Theme.Color.infoBg
        case .error: Theme.Color.errorBg
        }
    }
}

// MARK: - Location-mode display

/// Maps an event-type `location_mode` to a label + glyph for service rows and
/// preview cards. Falls back gracefully for unknown/custom values.
enum BookingLocationMode {
    static func label(_ mode: String?) -> String {
        switch (mode ?? "").lowercased() {
        case "video": "Video call"
        case "phone": "Phone call"
        case "in_person": "In person"
        case "custom": "Custom"
        case "ask": "They choose"
        default: "Video call"
        }
    }

    static func icon(_ mode: String?) -> PantopusIcon {
        switch (mode ?? "").lowercased() {
        case "video": .video
        case "phone": .phone
        case "in_person": .mapPin
        case "ask": .messageCircle
        default: .video
        }
    }
}

// MARK: - Duration formatting

enum BookingDuration {
    /// `30` → "30 min"; `60` → "1 hr"; `90` → "1 hr 30 min".
    static func label(_ minutes: Int) -> String {
        if minutes < 60 { return "\(minutes) min" }
        let hours = minutes / 60
        let rem = minutes % 60
        if rem == 0 { return hours == 1 ? "1 hr" : "\(hours) hr" }
        return "\(hours) hr \(rem) min"
    }
}
