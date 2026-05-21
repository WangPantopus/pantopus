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
    @State private var shareText: ShareTextItem?
    @State private var cardPDF: CardPDFItem?

    init(viewModel: EmergencyInfoViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .accessibilityIdentifier("emergencyInfoList")
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .onAppear { Analytics.track(.screenEmergencyInfoViewed) }
            .onChange(of: viewModel.shareRequested) { _, requested in
                guard requested else { return }
                viewModel.shareRequested = false
                if let text = viewModel.shareSummaryText() {
                    shareText = ShareTextItem(text: text)
                }
            }
            .onChange(of: viewModel.printRequested) { _, requested in
                guard requested else { return }
                viewModel.printRequested = false
                if let card = viewModel.printableCard(), let url = EmergencyCardPDF.render(card) {
                    cardPDF = CardPDFItem(url: url)
                }
            }
            .sheet(item: $shareText) { item in
                SystemShareSheet(items: [item.text])
            }
            .sheet(item: $cardPDF) { item in
                SystemShareSheet(items: [item.url])
            }
    }
}

private struct ShareTextItem: Identifiable {
    let id = UUID()
    let text: String
}

private struct CardPDFItem: Identifiable {
    let id = UUID()
    let url: URL
}

#Preview {
    NavigationStack {
        EmergencyInfoView(viewModel: EmergencyInfoViewModel(homeId: "preview"))
    }
}
