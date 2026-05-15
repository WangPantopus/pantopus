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
        case post(id: String)
        case gig(id: String)
        case listing(id: String)
        case homeDetail(id: String)
        case homeDashboard(id: String)
        case homeMemberRequests(id: String)
        case conversation(id: String)
        case user(id: String)
        case connections
        case invite(token: String)
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

        switch firstSegment {
        case "feed":
            return .feed
        case "home":
            return .home
        case "notifications":
            return .notifications
        case "support-trains", "support_train":
            if let id = segments.dropFirst().first { return .supportTrain(id: id) }
            return .unknown(url)
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
        case "invite":
            if let token = segments.dropFirst().first, !token.isEmpty {
                return .invite(token: token)
            }
            return .unknown(url)
        default:
            return .unknown(url)
        }
    }
}
