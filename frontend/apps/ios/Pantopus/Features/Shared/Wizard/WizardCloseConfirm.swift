//
//  WizardCloseConfirm.swift
//  Pantopus
//
//  Reusable "discard your progress?" confirm dialog used by `WizardShell`
//  when the user taps the close X on a dirty step.
//

import SwiftUI

public extension View {
    /// Attach the wizard's discard-confirm action sheet. The shell shows
    /// this when the user taps X on a dirty step.
    func wizardCloseConfirm(
        isPresented: Binding<Bool>,
        onDiscard: @escaping () -> Void
    ) -> some View {
        confirmationDialog(
            "Discard your progress?",
            isPresented: isPresented,
            titleVisibility: .visible
        ) {
            Button("Discard", role: .destructive, action: onDiscard)
            Button("Keep going", role: .cancel) {}
        } message: {
            Text("You'll lose what you've entered so far.")
        }
    }
}
