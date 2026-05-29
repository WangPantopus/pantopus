//
//  NotificationSettingsView.swift
//  Pantopus
//
//  P7.5 / A14.5 — Notification preferences. Thin wrapper around
//  `GroupedListView`; `NotificationSettingsViewModel` owns the channel
//  matrix, the Master pause control, the paused banner, and the
//  helper-line copy. Named `NotificationSettings…` (not `Notifications…`)
//  to avoid colliding with the notification-feed `NotificationsView`.
//

import SwiftUI

public struct NotificationSettingsView: View {
    @State private var viewModel: NotificationSettingsViewModel
    private let onBack: @MainActor () -> Void

    public init(
        viewModel: NotificationSettingsViewModel = NotificationSettingsViewModel(),
        onBack: @escaping @MainActor () -> Void
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
    }

    public var body: some View {
        GroupedListView(dataSource: viewModel, onBack: onBack)
            .accessibilityIdentifier("notificationSettings")
    }
}

#Preview("Populated") {
    NotificationSettingsView(viewModel: NotificationSettingsViewModel(variant: .populated)) {}
}

#Preview("Paused") {
    NotificationSettingsView(viewModel: NotificationSettingsViewModel(variant: .paused)) {}
}
