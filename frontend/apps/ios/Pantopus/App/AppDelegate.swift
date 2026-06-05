//
//  AppDelegate.swift
//  Pantopus
//
//  Minimal UIApplicationDelegate bridge for things SwiftUI doesn't expose:
//  push notification token registration, background task handling.
//

import Logging
import UIKit
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate {
    private let logger = Logger(label: "app.pantopus.ios.AppDelegate")

    func application(
        _: UIApplication,
        didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        MainActor.assumeIsolated {
            Observability.shared.start(environment: AppEnvironment.current)
            // Product analytics (PostHog). No-ops until POSTHOG_API_KEY is set,
            // so dev / CI builds send nothing. Matches Android's vendor + names.
            Analytics.start(environment: AppEnvironment.current)
            // Phase 3 (3A) — configure the Stripe SDK once at launch with the
            // publishable key resolved from Info.plist (xcconfig / .env). The
            // SDK is already linked (project.yml); we only set the key here.
            StripeBootstrap.configure(publishableKey: AppEnvironment.current.stripePublishableKey)
        }
        UNUserNotificationCenter.current().delegate = self
        requestNotificationPermission()
        return true
    }

    // MARK: - Push notifications

    private func requestNotificationPermission() {
        if ProcessInfo.processInfo.environment["UI_TESTS_DISABLE_NOTIFICATIONS"] == "1" {
            return
        }

        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { [weak self] granted, error in
            if let error {
                self?.logger.error("Push permission error", metadata: ["error": .string(error.localizedDescription)])
                return
            }
            guard granted else {
                self?.logger.info("Push permission denied by user")
                return
            }
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    func application(
        _: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let tokenString = deviceToken.map { String(format: "%02x", $0) }.joined()
        logger.info("APNs token received", metadata: ["token": .string(tokenString)])
        Task {
            // POST the APNs device token to /api/notifications/register with
            // platform=ios. The backend stores it as an APNs provider token
            // and delivers via APNs directly (no Expo). See
            // docs/push-native-migration.md.
            await APIClient.shared.registerPushToken(tokenString, platform: "ios")
        }
    }

    func application(
        _: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: any Error
    ) {
        logger.error("APNs registration failed", metadata: ["error": .string(error.localizedDescription)])
        Task { @MainActor in Observability.shared.capture(error) }
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    /// Show banner + play sound even when app is in foreground.
    nonisolated func userNotificationCenter(
        _: UNUserNotificationCenter,
        willPresent _: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound, .badge]
    }

    /// Handle taps on notifications — route to the relevant deep link.
    nonisolated func userNotificationCenter(
        _: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        // The backend attaches the destination under `link` (the unified
        // notification payload key — see backend pushService); older Expo
        // payloads used `deepLink`. Read both so a tap routes regardless of
        // which key the sender used. Only the resolved `String` is captured
        // across the actor hop, so we never smuggle the non-Sendable
        // `UNNotification` onto the main actor.
        let userInfo = response.notification.request.content.userInfo
        let deepLink = (userInfo["link"] as? String) ?? (userInfo["deepLink"] as? String)
        logger.info("Notification tapped", metadata: ["deepLink": .string(deepLink ?? "")])
        if let deepLink, !deepLink.isEmpty {
            // `link` is a path like `/chat/42`; handle(path:) normalises it
            // to the pantopus:// scheme, matching the Android dispatcher.
            await MainActor.run { DeepLinkRouter.shared.handle(path: deepLink) }
        }
    }
}
