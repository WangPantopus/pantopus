//
//  AppEnvironment.swift
//  Pantopus
//
//  Centralised configuration. Values come from Info.plist build settings
//  or a local .env file (loaded in Debug only via BuildSettings).
//

import Foundation

@Observable
final class AppEnvironment {
    enum Target: String, CaseIterable {
        case local
        case staging
        case production

        /// Selected from scheme env variable `PANTOPUS_API_ENV` first.
        /// When unset (release builds installed from TestFlight / App
        /// Store don't carry scheme env) we fall back per build
        /// configuration: Debug → `.local`, Release → `.production`.
        static var current: Target {
            if let raw = ProcessInfo.processInfo.environment["PANTOPUS_API_ENV"],
               let target = Target(rawValue: raw) {
                return target
            }
            #if DEBUG
            return .local
            #else
            return .production
            #endif
        }
    }

    let target: Target
    let apiBaseURL: URL
    let socketURL: URL
    let stripePublishableKey: String

    static let current = AppEnvironment()

    private init() {
        let target = Target.current
        self.target = target

        switch target {
        case .local:
            self.apiBaseURL = Self.mustURL("http://localhost:8000")
            self.socketURL = Self.mustURL("http://localhost:8000")
        case .staging:
            self.apiBaseURL = Self.mustURL("https://staging.api.pantopus.app")
            self.socketURL = Self.mustURL("https://staging.api.pantopus.app")
        case .production:
            self.apiBaseURL = Self.mustURL("https://api.pantopus.app")
            self.socketURL = Self.mustURL("https://api.pantopus.app")
        }

        // Stripe publishable key — read from Info.plist so it can be injected
        // per-configuration via xcconfig. Never commit a real key here.
        self.stripePublishableKey = Bundle.main.object(forInfoDictionaryKey: "StripePublishableKey") as? String ?? ""
    }

    private static func mustURL(_ string: String) -> URL {
        guard let url = URL(string: string) else {
            preconditionFailure("Invalid URL literal: \(string)")
        }
        return url
    }
}
