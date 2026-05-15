//
//  NotificationsView.swift
//  Pantopus
//
//  T4.1 Notifications center. Thin wrapper around the shared
//  ListOfRowsView — the only screen-specific work is the top bar
//  (back button + title; the read-all action comes from the data
//  source's `topBarAction`).
//

import SwiftUI

public struct NotificationsView: View {
    @State private var viewModel: NotificationsViewModel
    private let onBack: @MainActor () -> Void

    public init(
        viewModel: NotificationsViewModel,
        onBack: @escaping @MainActor () -> Void = {}
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
    }

    public var body: some View {
        VStack(spacing: 0) {
            topBar
            ListOfRowsView(dataSource: viewModel)
        }
        .background(Theme.Color.appBg)
        .task { await viewModel.load() }
        .accessibilityIdentifier("notifications")
    }

    private var topBar: some View {
        ZStack {
            Text(viewModel.title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            HStack {
                Button(action: onBack) {
                    Icon(.chevronLeft, size: 22, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("notificationsBackButton")
                .accessibilityLabel("Back")
                Spacer()
                if let action = viewModel.topBarAction {
                    Button(action: { action.handler() }) {
                        HStack(spacing: 4) {
                            Icon(action.icon, size: 14, color: Theme.Color.primary600)
                            Text("Mark all read")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Theme.Color.primary600)
                        }
                        .padding(.horizontal, 10)
                        .frame(height: 32)
                        .background(Theme.Color.primary50)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("notificationsMarkAllRead")
                    .accessibilityLabel(action.accessibilityLabel)
                    .padding(.trailing, 12)
                }
            }
        }
        .frame(height: 52)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
    }
}

#Preview {
    NotificationsView(viewModel: NotificationsViewModel())
}
