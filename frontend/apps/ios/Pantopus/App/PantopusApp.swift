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
    @State private var authManager = AuthManager.shared
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
                    await authManager.restoreSession()
                }
        }
    }
}

/// Top-level router — shows auth or main content based on session state.
struct RootView: View {
    @Environment(AuthManager.self) private var auth

    var body: some View {
        Group {
            switch auth.state {
            case .unknown:
                ProgressView()
                    .progressViewStyle(.circular)
            case .signedOut:
                LoginView()
            case .signedIn:
                MainTabView()
            }
        }
        .animation(.easeInOut(duration: 0.2), value: auth.state)
    }
}

/// Main tab scaffold — Feed / Home / Profile.
struct MainTabView: View {
    var body: some View {
        TabView {
            FeedView()
                .tabItem {
                    Label("Feed", systemImage: "square.grid.2x2")
                }

            HomeView()
                .tabItem {
                    Label("Home", systemImage: "house")
                }

            Text("Profile — coming soon")
                .tabItem {
                    Label("Profile", systemImage: "person.crop.circle")
                }
        }
    }
}

#Preview {
    RootView()
        .environment(AppEnvironment.current)
        .environment(AuthManager.previewSignedIn)
        .environment(APIClient.shared)
        .environment(SocketClient.shared)
}
