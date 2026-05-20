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
            // TODO: POST the APNs token to /api/notifications/register
            // with platform=ios. See `docs/push-native-migration.md` for the
            // backend migration away from expo-server-sdk.
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
        // Extract the userInfo dictionary into a Sendable `[String: Any]`
        // snapshot before hopping to the main actor — `UNNotification`
        // isn't `Sendable` so we can't capture `response` directly.
        let deepLink = response.notification.request.content.userInfo["deepLink"] as? String
        logger.info("Notification tapped", metadata: ["deepLink": .string(deepLink ?? "")])
        if let deepLink, let url = URL(string: deepLink) {
            await MainActor.run { DeepLinkRouter.shared.handle(url: url) }
        }
    }
}
