//
//  ActionRowCTA.swift
//  Pantopus
//
//  `action_row` CTA slot for the Public profile detail. The action
//  buttons sit in the overlapping stats strip in the body slot, so this
//  CTA is intentionally empty.
//

import SwiftUI

/// Empty CTA shelf for the Public profile detail. The Message / Connect
/// / overflow row lives in `StatsTabsBody`; no sticky footer is needed.
public struct ActionRowCTA: View {
    public init() {}

    public var body: some View {
        EmptyView()
    }
}
