//
//  Observability.swift
//  Pantopus
//
//  Single entry point for crash reporting, error capture, and analytics.
//  Everything else in the app goes through `Observability.shared` so we can
//  swap the backend (Sentry, Firebase, …) without touching call sites.
//

import Foundation
import Logging
import Sentry

@MainActor
final class Observability {
    static let shared = Observability()

    private let logger = Logger(label: "app.pantopus.ios.Observability")
    private(set) var isStarted = false

    private init() {}

    /// Call once from `AppDelegate.didFinishLaunching`. Safe to call again — no-ops after the first invocation.
    func start(environment: AppEnvironment) {
        guard !isStarted else { return }
        isStarted = true

        let dsn = Bundle.main.object(forInfoDictionaryKey: "SentryDSN") as? String ?? ""
        guard !dsn.isEmpty else {
            logger.info("Sentry DSN not set — skipping crash reporting init")
            return
        }

        SentrySDK.start { options in
            options.dsn = dsn
            options.environment = environment.target.rawValue
            options.releaseName = Self.releaseName
            options.enableAutoSessionTracking = true
            options.enableAutoPerformanceTracing = true
            options.tracesSampleRate = environment.target == .production ? 0.1 : 1.0
            options.enableUserInteractionTracing = true
            options.enableNetworkTracking = true
            options.attachViewHierarchy = false
            options.attachScreenshot = false
            options.sendDefaultPii = false
            options.beforeSend = { event in
                // Drop noisy low-signal events here if needed.
                event
            }
        }

        logger.info("Sentry started", metadata: ["env": .string(environment.target.rawValue)])
    }

    // MARK: - Error capture

    func capture(_ error: Error, file: StaticString = #fileID, line: UInt = #line) {
        logger.error("\(error)", metadata: ["file": .string("\(file):\(line)")])
        guard isStarted else { return }
        SentrySDK.capture(error: error)
    }

    func capture(message: String, level: SentryLevel = .warning) {
        logger.warning("\(message)")
        guard isStarted else { return }
        SentrySDK.capture(message: message) { scope in
            scope.setLevel(level)
        }
    }

    // MARK: - User context

    func identify(userId: String?, email: String? = nil) {
        guard isStarted else { return }
        if let userId {
            let user = User(userId: userId)
            user.email = email
            SentrySDK.setUser(user)
        } else {
            SentrySDK.setUser(nil)
        }
    }

    // MARK: - Breadcrumbs & analytics

    /// Lightweight analytics event. For now this is a structured log + Sentry breadcrumb;
    /// wire in a real analytics SDK (Amplitude / Mixpanel / PostHog) by extending this method.
    func track(_ name: String, properties: [String: String] = [:]) {
        logger.info("analytics", metadata: [
            "event": .string(name),
            "props": .string(properties.description),
        ])
        guard isStarted else { return }
        let crumb = Breadcrumb(level: .info, category: "analytics")
        crumb.message = name
        crumb.data = properties
        SentrySDK.addBreadcrumb(crumb)
    }

    // MARK: - Release tag

    private static var releaseName: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.0.0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0"
        return "app.pantopus.ios@\(version)+\(build)"
    }
}
