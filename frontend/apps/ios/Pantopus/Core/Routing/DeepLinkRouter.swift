//
//  DeepLinkRouter.swift
//  Pantopus
//
//  Tiny deep-link router. Receives a URL and publishes the resolved
//  destination so SwiftUI views (or coordinators) can react.
//

// swiftlint:disable cyclomatic_complexity

import Foundation
import Logging

@Observable
@MainActor
final class DeepLinkRouter {
    /// Full routing table from `docs/07-frontend-mobile-app.md §9`.
    /// `home` (singular) keeps the legacy "go to Hub" semantics;
    /// `homeDetail` / `homeDashboard` / `homeMemberRequests` are
    /// the typed variants for `/homes/:id/*`.
    enum Destination: Equatable {
        case feed
        case home
        case notifications
        case supportTrain(id: String)
        /// `pantopus://support-trains/:id/manage` — A13.13 organizer
        /// surface. Reached from the A10.9 detail dock overflow when
        /// the viewer is the organizer, and from back-of-house
        /// shortcut links. Distinct from `supportTrain(id:)`, which
        /// lands on the participant detail (A10.9).
        case supportTrainManage(id: String)
        case post(id: String)
        case gig(id: String)
        case listing(id: String)
        case homeDetail(id: String)
        case homeDashboard(id: String)
        case homeMemberRequests(id: String)
        /// `pantopus://homes/:id/owners/transfer` — A13.4 Transfer Ownership
        /// form. Lands on the populated state; the form owns the Face ID
        /// bottom sheet.
        case homeOwnersTransfer(id: String)
        /// `pantopus://homes/:id/verify-landlord` — opens A12.5 / A12.6.
        case verifyLandlord(id: String)
        /// `pantopus://homes/:id/verify-postcard` — opens the A12.7
        /// sibling status screen directly.
        case postcardVerification(id: String)
        case conversation(id: String)
        case user(id: String)
        case connections
        case discoverHub
        /// `pantopus://businesses/new` — open the A12.10 Create Business
        /// wizard inside the active tab's nav stack.
        case createBusiness
        case invite(token: String)
        /// P4.2 — A13.10 Edit Business Page (owner-only).
        /// `pantopus://businesses/:id/page-editor`.
        case editBusinessPage(businessId: String)
        /// Public business profile reached from a share / push.
        /// `pantopus://businesses/:id`.
        case businessProfile(businessId: String)
        /// `pantopus://auth/reset-password?token=…` — surfaces the hashed
        /// recovery token from the password-reset email. Carries the raw
        /// token; the caller invokes `AuthManager.resetPassword` on submit.
        case resetPassword(token: String)
        /// `pantopus://auth/verify-email?token=…` — surfaces the hashed
        /// Supabase OTP from the verification email. `email` is optional
        /// (the link from the resend / signup flow carries `&email=` so
        /// the screen can render the recipient).
        case verifyEmail(token: String, email: String?)
        /// `pantopus://mailbox/mailday` — the A13.16 My Mail Day editor.
        /// Routed via the mailbox stack so Back returns to the mailbox
        /// root.
        case mailDay
        /// `pantopus://wallet` — A10.10 earnings wallet (distinct from
        /// Settings → Payments; this is the earnings-side surface).
        case wallet
        case unknown(URL)
    }

    static let shared = DeepLinkRouter()

    /// The most recent pending destination. Consumers read this and then call `consume()`.
    private(set) var pending: Destination?

    private let logger = Logger(label: "app.pantopus.ios.DeepLinkRouter")

    private init() {}

    func handle(url: URL) {
        let destination = resolve(url: url)
        logger.info("deeplink", metadata: [
            "url": .string(url.absoluteString),
            "destination": .string("\(destination)")
        ])
        pending = destination
        Observability.shared.track("deeplink.received", properties: [
            "url": url.absoluteString
        ])
    }

    /// Receive a raw path-style link from a notification payload (e.g.
    /// `link` on `NotificationDTO`). Routed through the same
    /// resolver as full URL deep links.
    func handle(path: String) {
        let normalized: String = if path.hasPrefix("pantopus://") || path.hasPrefix("http") {
            path
        } else if path.hasPrefix("/") {
            "pantopus://" + String(path.dropFirst())
        } else {
            "pantopus://" + path
        }
        guard let url = URL(string: normalized) else { return }
        handle(url: url)
    }

    func consume() -> Destination? {
        defer { pending = nil }
        return pending
    }

    // MARK: - URL parsing

    /// Accepts both `pantopus://…` and `https://pantopus.app/…`.
    func resolve(url: URL) -> Destination {
        let segments = routeSegments(for: url)
        let firstSegment = segments.first ?? ""
        let comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let tabQuery = queryValue("tab", in: comps)
        let tokenQuery = queryValue("token", in: comps)
            ?? queryValue("token_hash", in: comps)
            ?? fragmentParam(url.fragment, name: "token")
            ?? fragmentParam(url.fragment, name: "token_hash")
        let emailQuery = queryValue("email", in: comps)
            ?? fragmentParam(url.fragment, name: "email")

        switch firstSegment {
        case "feed":
            return .feed
        case "home":
            return .home
        case "notifications":
            return .notifications
        case "support-trains", "support_train":
            guard let id = segments.dropFirst().first else { return .unknown(url) }
            // `/support-trains/:id/manage` → A13.13 organizer surface.
            if segments.dropFirst(2).first == "manage" {
                return .supportTrainManage(id: id)
            }
            return .supportTrain(id: id)
        case "post", "posts":
            if let id = segments.dropFirst().first { return .post(id: id) }
            return .unknown(url)
        case "gig", "gigs":
            if let id = segments.dropFirst().first { return .gig(id: id) }
            return .unknown(url)
        case "listing", "listings":
            if let id = segments.dropFirst().first { return .listing(id: id) }
            return .unknown(url)
        case "homes":
            return homeDestination(url: url, segments: segments, tabQuery: tabQuery)
        case "businesses", "business":
            // `pantopus://businesses/new` opens the Create Business wizard.
            // `pantopus://businesses/:id/page-editor` opens A13.10 (owner-only).
            // `pantopus://businesses/:id` opens the public business profile.
            guard let id = segments.dropFirst().first else { return .unknown(url) }
            if id == "new" {
                return .createBusiness
            }
            let trailing = Array(segments.dropFirst(2))
            if trailing.first == "page-editor" || trailing.first == "page_editor" {
                return .editBusinessPage(businessId: id)
            }
            return .businessProfile(businessId: id)
        case "chat", "message", "messages", "conversation":
            if let id = segments.dropFirst().first { return .conversation(id: id) }
            return .unknown(url)
        case "user", "users":
            if let id = segments.dropFirst().first { return .user(id: id) }
            return .unknown(url)
        case "connections":
            return .connections
        case "discover-hub", "discover_hub", "discoverhub":
            return .discoverHub
        case "wallet":
            return .wallet
        case "invite":
            if let token = segments.dropFirst().first, !token.isEmpty {
                return .invite(token: token)
            }
            return .unknown(url)
        case "mailbox":
            // `pantopus://mailbox/mailday` — only the mail-day sub-route
            // is wired today. Bare `pantopus://mailbox` falls through to
            // the existing tab-level routing.
            if segments.dropFirst().first == "mailday" {
                return .mailDay
            }
            return .unknown(url)
        case "auth":
            return authDestination(url: url, segments: segments, token: tokenQuery, email: emailQuery)
        case "reset-password", "reset_password":
            // Tolerate the bare `/reset-password?token=…` shape that the
            // backend's older recovery template emits (no `/auth/` prefix).
            return resetPasswordDestination(url: url, token: tokenQuery)
        case "verify-email", "verify_email":
            return verifyEmailDestination(url: url, token: tokenQuery, email: emailQuery)
        default:
            return .unknown(url)
        }
    }

    private func routeSegments(for url: URL) -> [String] {
        var segments = url.pathComponents.filter { $0 != "/" }
        if url.scheme != "http", url.scheme != "https",
           let host = url.host, !host.isEmpty {
            segments.insert(host, at: 0)
        }
        return segments
    }

    private func queryValue(_ name: String, in components: URLComponents?) -> String? {
        components?.queryItems?.first { $0.name == name }?.value
    }

    private func homeDestination(url: URL, segments: [String], tabQuery: String?) -> Destination {
        guard let id = segments.dropFirst().first else { return .unknown(url) }
        let trailing = Array(segments.dropFirst(2))
        if trailing.first == "dashboard" {
            return .homeDashboard(id: id)
        }
        if trailing.first == "members" && tabQuery == "requests" {
            return .homeMemberRequests(id: id)
        }
        if trailing.first == "owners" && trailing.dropFirst().first == "transfer" {
            return .homeOwnersTransfer(id: id)
        }
        if trailing.first == "verify-landlord" || trailing.first == "verify_landlord" {
            return .verifyLandlord(id: id)
        }
        if trailing.first == "verify-postcard" || trailing.first == "verify_postcard" {
            return .postcardVerification(id: id)
        }
        return .homeDetail(id: id)
    }

    private func authDestination(
        url: URL,
        segments: [String],
        token: String?,
        email: String?
    ) -> Destination {
        switch segments.dropFirst().first ?? "" {
        case "reset-password", "reset_password":
            resetPasswordDestination(url: url, token: token)
        case "verify-email", "verify_email":
            verifyEmailDestination(url: url, token: token, email: email)
        default:
            .unknown(url)
        }
    }

    private func resetPasswordDestination(url: URL, token: String?) -> Destination {
        guard let token, !token.isEmpty else { return .unknown(url) }
        return .resetPassword(token: token)
    }

    private func verifyEmailDestination(url: URL, token: String?, email: String?) -> Destination {
        guard let token, !token.isEmpty else { return .unknown(url) }
        return .verifyEmail(token: token, email: email)
    }

    /// Pulls a single key out of a `#` fragment of the form `key=v&k2=v2`.
    /// Supabase auth-callback links sometimes ship the access_token /
    /// token_hash / email in the fragment instead of the query string.
    private func fragmentParam(_ fragment: String?, name: String) -> String? {
        guard let fragment, !fragment.isEmpty else { return nil }
        for pair in fragment.split(separator: "&") {
            let parts = pair.split(separator: "=", maxSplits: 1)
            if parts.count == 2, parts[0] == Substring(name) {
                return String(parts[1])
            }
        }
        return nil
    }
}
