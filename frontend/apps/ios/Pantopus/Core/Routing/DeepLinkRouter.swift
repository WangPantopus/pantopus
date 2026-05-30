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
        /// `pantopus://beacons` — A03.2 Beacon Updates feed (`surface=personas`).
        case beacons
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
        /// A14.8 — `pantopus://mailbox/vacation` opens the Vacation
        /// hold screen (scheduling or active variant depending on
        /// server state once the persistence layer lands; today the
        /// view-model seeds the scheduling form).
        case vacationHold
        /// `pantopus://mailbox/mailday` — the A13.16 My Mail Day editor.
        /// Routed via the mailbox stack so Back returns to the mailbox
        /// root.
        case mailDay
        /// `pantopus://wallet` — A10.10 earnings wallet (distinct from
        /// Settings → Payments; this is the earnings-side surface).
        case wallet
        /// `pantopus://settings/payments` — A14.6 Settings → Payments.
        /// Distinct from `pantopus://wallet` (earnings-in surface).
        /// Consumed by the active tab's deep-link router which pushes
        /// `.menu` then forwards into the Payments stack route.
        case paymentsSettings
        // MARK: - B1.6 batch-2 routing seam
        // Pre-registered for the batch-2 screens (B2–B5). Each resolves to
        // the NotYetAvailableView placeholder today; the screen prompts swap
        // in their real destinations without editing the route files.
        /// `pantopus://mailbox/stamps` — A17.11 Stamps / postage wallet.
        case stamps
        /// `pantopus://mailbox/tasks/:id` — A17.12 mail-derived task detail.
        case mailTask(taskId: String)
        /// `pantopus://mailbox/translation?id=` — A17.13 auto-translated mail.
        case mailTranslation(mailId: String)
        /// `pantopus://mailbox/unboxing` — A17.14 scan-first capture flow. The
        /// optional `?id=` seeds the originating mail item when present.
        case unboxing(mailId: String?)
        /// `pantopus://mailbox/earn` — A10.11 Earn dashboard (Wallet sibling).
        case earn
        /// `pantopus://businesses/:id` — A10.7 Business owner view. The public
        /// profile (A10.6) lives at the singular `pantopus://business/:username`.
        case businessOwner(businessId: String)
        /// `pantopus://identity/preview` — A18.5 "View as" identity preview.
        case viewAs
        /// `pantopus://homes/:id/waiting-room` — A18.4 persistent waiting room.
        case waitingRoom(id: String)
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
        // B1.6 — `?id=` seeds the translation / unboxing mailbox sub-screens.
        let idQuery = queryValue("id", in: comps)
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
        case "businesses":
            return businessesDestination(url: url, segments: segments)
        case "business":
            // Singular `business/:username` is the A10.6 public profile.
            guard let id = segments.dropFirst().first else { return .unknown(url) }
            return .businessProfile(businessId: id)
        case "identity":
            // `pantopus://identity/preview` — A18.5 "View as" preview.
            if segments.dropFirst().first == "preview" { return .viewAs }
            return .unknown(url)
        case "chat", "message", "messages", "conversation":
            if let id = segments.dropFirst().first { return .conversation(id: id) }
            return .unknown(url)
        case "user", "users":
            if let id = segments.dropFirst().first { return .user(id: id) }
            return .unknown(url)
        case "connections":
            return .connections
        case "beacons", "beacon-updates", "beacon_updates":
            return .beacons
        case "discover-hub", "discover_hub", "discoverhub":
            return .discoverHub
        case "mailbox":
            return mailboxDestination(url: url, segments: segments, idQuery: idQuery)
        case "wallet":
            return .wallet
        case "invite":
            if let token = segments.dropFirst().first, !token.isEmpty {
                return .invite(token: token)
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
        case "settings":
            // `pantopus://settings/payments` — A14.6. Other settings
            // sub-routes aren't deep-linkable yet; the bare host
            // `pantopus://settings` falls through to `.unknown`.
            if segments.dropFirst().first == "payments" {
                return .paymentsSettings
            }
            return .unknown(url)
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
        // B1.6 — `pantopus://homes/:id/waiting-room` opens the A18.4 room.
        if trailing.first == "waiting-room" || trailing.first == "waiting_room" {
            return .waitingRoom(id: id)
        }
        return .homeDetail(id: id)
    }

    /// Plural `businesses/*` is the owner-side surface family.
    /// `…/new` opens the Create Business wizard, `…/:id/page-editor` opens
    /// A13.10 (owner-only), and `…/:id` opens the A10.7 Business owner view.
    /// The singular `business/:username` (A10.6 public profile) is handled
    /// separately in `resolve`.
    private func businessesDestination(url: URL, segments: [String]) -> Destination {
        guard let id = segments.dropFirst().first else { return .unknown(url) }
        if id == "new" {
            return .createBusiness
        }
        let trailing = Array(segments.dropFirst(2))
        if trailing.first == "page-editor" || trailing.first == "page_editor" {
            return .editBusinessPage(businessId: id)
        }
        return .businessOwner(businessId: id)
    }

    private func mailboxDestination(url: URL, segments: [String], idQuery: String?) -> Destination {
        // `pantopus://mailbox/vacation` opens A14.8; `pantopus://mailbox/mailday`
        // opens the A13.16 My Mail Day editor. B1.6 adds the batch-2 mailbox
        // sub-screens (stamps / tasks / translation / unboxing / earn). Other
        // mailbox paths fall through to `.unknown` until they have routes.
        switch segments.dropFirst().first {
        case "vacation": .vacationHold
        case "mailday": .mailDay
        case "stamps": .stamps
        case "earn": .earn
        case "unboxing": .unboxing(mailId: idQuery)
        case "translation": .mailTranslation(mailId: idQuery ?? "")
        case "tasks": mailTaskDestination(url: url, segments: segments)
        default: .unknown(url)
        }
    }

    /// `pantopus://mailbox/tasks/:id` — A17.12 mail-derived task detail. The
    /// task id rides as the third path segment.
    private func mailTaskDestination(url: URL, segments: [String]) -> Destination {
        guard let taskId = segments.dropFirst(2).first, !taskId.isEmpty else { return .unknown(url) }
        return .mailTask(taskId: taskId)
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
