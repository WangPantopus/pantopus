//
//  DeepLinkRouter.swift
//  Pantopus
//
//  Tiny deep-link router. Receives a URL and publishes the resolved
//  destination so SwiftUI views (or coordinators) can react.
//

import Foundation
import Logging

@Observable
@MainActor
final class DeepLinkRouter {
    enum Destination: Equatable {
        case feed
        case home
        case post(id: String)
        case conversation(id: String)
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

    func consume() -> Destination? {
        defer { pending = nil }
        return pending
    }

    // MARK: - URL parsing

    /// Accepts both `pantopus://…` and `https://pantopus.app/…`.
    private func resolve(url: URL) -> Destination {
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

        switch firstSegment {
        case "feed":
            return .feed
        case "home":
            return .home
        case "post", "posts":
            if let id = segments.dropFirst().first { return .post(id: id) }
            return .unknown(url)
        case "message", "messages", "conversation":
            if let id = segments.dropFirst().first { return .conversation(id: id) }
            return .unknown(url)
        default:
            return .unknown(url)
        }
    }
}
