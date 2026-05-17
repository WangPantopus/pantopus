//
//  HomeCalendarView.swift
//  Pantopus
//
//  T6.4c (P18) — concrete Home calendar screen. Reuses the shared
//  `ListOfRowsView` shell and threads the feature-local
//  `MonthStripHeader` through the additive `customHeader` slot.
//

import SwiftUI

public struct HomeCalendarView: View {
    @State private var viewModel: HomeCalendarViewModel

    public init(viewModel: HomeCalendarViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        ListOfRowsView(dataSource: viewModel) {
            if let strip = viewModel.monthStrip {
                MonthStripHeader(
                    state: strip,
                    onSelectDay: { iso in viewModel.selectDay(isoDate: iso) },
                    onPrevMonth: { viewModel.shiftWeek(.previous) },
                    onNextMonth: { viewModel.shiftWeek(.next) }
                )
            }
        }
        .accessibilityIdentifier("homeCalendar")
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .onAppear { Analytics.track(.screenHomeCalendarViewed) }
    }
}

#Preview {
    NavigationStack {
        HomeCalendarView(
            viewModel: HomeCalendarViewModel(
                homeId: "preview",
                homeSubtitle: "412 Birch Ln"
            )
        )
    }
}
