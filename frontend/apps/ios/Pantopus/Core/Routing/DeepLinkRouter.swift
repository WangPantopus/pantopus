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
        /// surface. Reached from the A10.9 detail dock overflow.
        case manageTrain(id: String)
        case post(id: String)
        case gig(id: String)
        case listing(id: String)
        case homeDetail(id: String)
        case homeDashboard(id: String)
        case homeMemberRequests(id: String)
        case conversation(id: String)
        case user(id: String)
        case connections
        case discoverHub
        case invite(token: String)
        /// `pantopus://auth/reset-password?token=…` — surfaces the hashed
        /// recovery token from the password-reset email. Carries the raw
        /// token; the caller invokes `AuthManager.resetPassword` on submit.
        case resetPassword(token: String)
        /// `pantopus://auth/verify-email?token=…` — surfaces the hashed
        /// Supabase OTP from the verification email. `email` is optional
        /// (the link from the resend / signup flow carries `&email=` so
        /// the screen can render the recipient).
        case verifyEmail(token: String, email: String?)
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
        // For custom-scheme URLs (`pantopus://messages/conv_42`) the route
        // name lives in the host, not the path. For https URLs the host is
        // the domain and the route is the first path component. Normalize
        // both shapes into one `segments` array so the matcher below
        // doesn't have to branch on scheme.
        var segments = url.pathComponents.filter { $0 != "/" }
        if url.scheme != "http", url.scheme != "https",
           let host = url.host, !host.isEmpty {
            segments.insert(host, at: 0)
        }
        let firstSegment = segments.first ?? ""
        // Parse `?tab=requests` from the query string so the
        // home-member-requests entry routes correctly.
        let comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let tabQuery = comps?.queryItems?.first { $0.name == "tab" }?.value
        // Auth deep links carry `token` / `token_hash` (Supabase's two
        // recovery-link param names) and an optional `email`. Auth-callback
        // emails sometimes encode params in the fragment instead of the
        // query string, so parse both.
        let tokenQuery = comps?.queryItems?.first { $0.name == "token" || $0.name == "token_hash" }?.value
            ?? fragmentParam(url.fragment, name: "token")
            ?? fragmentParam(url.fragment, name: "token_hash")
        let emailQuery = comps?.queryItems?.first { $0.name == "email" }?.value
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
                return .manageTrain(id: id)
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
            guard let id = segments.dropFirst().first else { return .unknown(url) }
            let trailing = Array(segments.dropFirst(2))
            if trailing.first == "dashboard" {
                return .homeDashboard(id: id)
            }
            if trailing.first == "members" && tabQuery == "requests" {
                return .homeMemberRequests(id: id)
            }
            return .homeDetail(id: id)
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
        case "invite":
            if let token = segments.dropFirst().first, !token.isEmpty {
                return .invite(token: token)
            }
            return .unknown(url)
        case "auth":
            // `pantopus://auth/reset-password?token=…` and
            // `pantopus://auth/verify-email?token=…&email=…`.
            let sub = segments.dropFirst().first ?? ""
            switch sub {
            case "reset-password", "reset_password":
                guard let token = tokenQuery, !token.isEmpty else { return .unknown(url) }
                return .resetPassword(token: token)
            case "verify-email", "verify_email":
                guard let token = tokenQuery, !token.isEmpty else { return .unknown(url) }
                return .verifyEmail(token: token, email: emailQuery)
            default:
                return .unknown(url)
            }
        case "reset-password", "reset_password":
            // Tolerate the bare `/reset-password?token=…` shape that the
            // backend's older recovery template emits (no `/auth/` prefix).
            guard let token = tokenQuery, !token.isEmpty else { return .unknown(url) }
            return .resetPassword(token: token)
        case "verify-email", "verify_email":
            guard let token = tokenQuery, !token.isEmpty else { return .unknown(url) }
            return .verifyEmail(token: token, email: emailQuery)
        default:
            return .unknown(url)
        }
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
