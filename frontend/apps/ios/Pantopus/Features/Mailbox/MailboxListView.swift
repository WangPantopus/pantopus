//
//  MailboxListView.swift
//  Pantopus
//

import SwiftUI

/// `GET /api/mailbox` wrapped in the List-of-Rows archetype with
/// All / Unread / Starred tabs.
struct MailboxListView: View {
    @State private var viewModel: MailboxListViewModel

    init(viewModel: MailboxListViewModel = MailboxListViewModel()) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .overlay(alignment: .bottom) {
                if let toast = viewModel.toast {
                    ToastBanner(message: toast)
                        .padding(.bottom, Spacing.s10)
                        .task {
                            try? await Task.sleep(nanoseconds: 1_800_000_000)
                            viewModel.toast = nil
                        }
                        .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.2), value: viewModel.toast)
    }
}

private struct ToastBanner: View {
    let message: String

    var body: some View {
        Text(message)
            .pantopusTextStyle(.small)
            .foregroundStyle(Theme.Color.appTextInverse)
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appText.opacity(0.9))
            .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
            .accessibilityLabel(message)
    }
}

#Preview {
    NavigationStack { MailboxListView() }
}
