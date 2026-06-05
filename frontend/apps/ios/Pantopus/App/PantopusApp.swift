//
//  PantopusApp.swift
//  Pantopus
//
//  App entry point. Boots the environment, auth, and root view.
//

import Foundation
import SwiftUI

@main
struct PantopusApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    // App-wide singletons — injected via environment.
    @State private var environment = AppEnvironment.current
    @State private var authManager = Self.bootAuthManager()
    @State private var apiClient = APIClient.shared
    @State private var socketClient = SocketClient.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(environment)
                .environment(authManager)
                .environment(apiClient)
                .environment(socketClient)
                .task {
                    if !ProcessInfo.processInfo.isUITestSeededAuthSession {
                        await authManager.restoreSession()
                    }
                }
                // Universal links (`https://pantopus.app/…`) and custom-scheme
                // URLs (`pantopus://…`). Both funnel into the same router the
                // notification-tap path uses; `RootTabView` observes
                // `DeepLinkRouter.shared.pending` and dispatches to the right
                // tab/stack, so cold-start links resolve once the root appears.
                .onOpenURL { url in
                    DeepLinkRouter.shared.handle(url: url)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        DeepLinkRouter.shared.handle(url: url)
                    }
                }
        }
    }

    /// Pick the AuthManager for this launch. Under `UI_TESTS_SIGNED_IN=1`
    /// we boot into an in-memory signed-in session so UI tests can exercise
    /// the root tab view without a real backend.
    private static func bootAuthManager() -> AuthManager {
        if ProcessInfo.processInfo.isUITestSignedInSession {
            return AuthManager.previewSignedIn
        }
        if ProcessInfo.processInfo.isUITestSignedOutSession {
            return AuthManager.previewSignedOut
        }
        return AuthManager.shared
    }
}

private extension ProcessInfo {
    /// True when the process was launched by a UI test that wants a
    /// seeded signed-in session.
    var isUITestSignedInSession: Bool {
        environment["UI_TESTS_SIGNED_IN"] == "1"
    }

    var isUITestSignedOutSession: Bool {
        environment["UI_TESTS_SIGNED_OUT"] == "1"
    }

    var isUITestSeededAuthSession: Bool {
        isUITestSignedInSession || isUITestSignedOutSession
    }
}

/// Top-level router — shows auth or main content based on session state.
struct RootView: View {
    @Environment(AuthManager.self) private var auth

    var body: some View {
        Group {
            switch auth.state {
            case .unknown:
                SplashView()
            case .signedOut:
                LoginView()
            case .signedIn:
                RootTabView()
            }
        }
        .animation(.easeInOut(duration: 0.25), value: auth.state)
        .transition(.opacity)
    }
}

/// Launch-time splash while we hydrate the session from the keychain.
private struct SplashView: View {
    var body: some View {
        ZStack {
            Theme.Color.appBg.ignoresSafeArea()
            VStack(spacing: Spacing.s4) {
                Icon(.home, size: 64, color: Theme.Color.primary600)
                ProgressView()
            }
        }
        .accessibilityLabel("Loading Pantopus")
    }
}

#Preview("Signed in") {
    RootView()
        .environment(AppEnvironment.current)
        .environment(AuthManager.previewSignedIn)
        .environment(APIClient.shared)
        .environment(SocketClient.shared)
}
