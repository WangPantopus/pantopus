//
//  PantopusWidgetsBundle.swift
//  PantopusWidgets
//
//  Phase 6b — widget-extension entry point. Currently hosts only the
//  active-task Live Activity.
//

import SwiftUI
import WidgetKit

@main
struct PantopusWidgetsBundle: WidgetBundle {
    var body: some Widget {
        GigActivityWidget()
    }
}
