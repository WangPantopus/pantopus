//
//  DrawerMenuButton.swift
//  Pantopus
//
//  The shared top-left hamburger that opens the global navigation drawer.
//  Every primary tab (Your Place / Pulse / Tasks / Marketplace / Messages)
//  renders this at the leading edge of its header, so the side menu is
//  reachable from anywhere. Carries the cross-platform `navMenuButton` tag
//  (mirrored on Android as `Modifier.testTag("navMenuButton")`).
//

import SwiftUI

/// Leading-edge menu button. Tapping it asks the host to present the
/// global `NavigationDrawerView`.
struct DrawerMenuButton: View {
    let action: @MainActor () -> Void

    var body: some View {
        Button(action: action) {
            Icon(.menu, size: 22, color: Theme.Color.appText)
                .frame(width: 36, height: 36)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Open menu")
        .accessibilityIdentifier("navMenuButton")
    }
}

#Preview {
    DrawerMenuButton(action: {})
}
