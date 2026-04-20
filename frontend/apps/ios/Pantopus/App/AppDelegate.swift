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
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        requestNotificationPermission()
        return true
    }

    // MARK: - Push notifications

    private func requestNotificationPermission() {
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
        _ application: UIApplication,
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
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        logger.error("APNs registration failed", metadata: ["error": .string(error.localizedDescription)])
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    /// Show banner + play sound even when app is in foreground.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound, .badge]
    }

    /// Handle taps on notifications — route to the relevant deep link.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let userInfo = response.notification.request.content.userInfo
        logger.info("Notification tapped", metadata: ["payload": .string("\(userInfo)")])
        // TODO: Parse `userInfo["deepLink"]` and post to a router.
    }
}
