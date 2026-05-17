//
//  EmergencyInfoView.swift
//  Pantopus
//
//  T6.4b / P17 — Concrete List-of-Rows screen backed by
//  `EmergencyInfoViewModel`. Wired to
//  `GET /api/homes/:id/emergencies` (route `backend/routes/home.js:5406`).
//

import SwiftUI

struct EmergencyInfoView: View {
    @State private var viewModel: EmergencyInfoViewModel

    init(viewModel: EmergencyInfoViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .accessibilityIdentifier("emergencyInfoList")
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .onAppear { Analytics.track(.screenEmergencyInfoViewed) }
    }
}

#Preview {
    NavigationStack {
        EmergencyInfoView(viewModel: EmergencyInfoViewModel(homeId: "preview"))
    }
}
