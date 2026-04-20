//
//  PantopusApp.swift
//  Pantopus
//
//  App entry point. Boots the environment, auth, and root view.
//

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
                    if !ProcessInfo.processInfo.isUITestSignedInSession {
                        await authManager.restoreSession()
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
        return AuthManager.shared
    }
}

private extension ProcessInfo {
    /// True when the process was launched by a UI test that wants a
    /// seeded signed-in session.
    var isUITestSignedInSession: Bool {
        environment["UI_TESTS_SIGNED_IN"] == "1"
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
