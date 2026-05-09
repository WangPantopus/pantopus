//
//  FormShell.swift
//  Pantopus
//
//  Top-bar (X + title + right action) + scrolling body. Owns the
//  dirty-close confirm sheet so feature screens don't reimplement it.
//

import SwiftUI

/// Scaffold for every Form screen.
///
/// Per the P10 Form archetype: a 44pt top bar with leading X, centered
/// title, and a right-aligned text action button (`Save` / `Send` / `Post`
/// / `Done`). The action button renders in `primary600` when enabled and
/// `fg4` (`appTextMuted`) when disabled.
@MainActor
public struct FormShell<Content: View>: View {
    private let title: String
    private let rightActionLabel: String
    private let isValid: Bool
    private let isDirty: Bool
    private let isSaving: Bool
    private let onClose: () -> Void
    private let onCommit: () -> Void
    private let content: Content

    @State private var showsDiscardConfirm = false

    /// - Parameters:
    ///   - title: Centered top-bar title.
    ///   - rightActionLabel: Text for the trailing action (`Save`, `Send`,
    ///     `Post`, `Done`). Defaults to `"Save"`.
    ///   - isValid: Drives whether the right action is enabled.
    ///   - isDirty: Drives whether the right action is enabled and whether
    ///     close prompts the discard confirm.
    ///   - isSaving: Render a spinner in place of the right-action label
    ///     while a commit is in flight.
    ///   - onClose: Invoked when the user taps X on a clean form, or
    ///     confirms discard on a dirty one.
    ///   - onCommit: Invoked when the user taps the right action.
    ///   - content: Body view builder — typically a stack of
    ///     `FormFieldGroup`s.
    public init(
        title: String,
        rightActionLabel: String = "Save",
        isValid: Bool,
        isDirty: Bool,
        isSaving: Bool = false,
        onClose: @escaping () -> Void,
        onCommit: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.rightActionLabel = rightActionLabel
        self.isValid = isValid
        self.isDirty = isDirty
        self.isSaving = isSaving
        self.onClose = onClose
        self.onCommit = onCommit
        self.content = content()
    }

    public var body: some View {
        VStack(spacing: 0) {
            FormTopBar(
                title: title,
                rightActionLabel: rightActionLabel,
                rightActionEnabled: isValid && isDirty && !isSaving,
                isSaving: isSaving,
                onClose: handleClose,
                onCommit: onCommit
            )
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s5) {
                    content
                }
                .padding(.vertical, Spacing.s4)
            }
            .background(Theme.Color.appBg)
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("formShell")
        .confirmationDialog(
            "Discard changes?",
            isPresented: $showsDiscardConfirm,
            titleVisibility: .visible
        ) {
            Button("Discard", role: .destructive) { onClose() }
            Button("Keep editing", role: .cancel) {}
        } message: {
            Text("You'll lose any unsaved edits.")
        }
    }

    private func handleClose() {
        if isDirty {
            showsDiscardConfirm = true
        } else {
            onClose()
        }
    }
}

/// 44pt top bar: leading X, centered title, trailing action label.
private struct FormTopBar: View {
    let title: String
    let rightActionLabel: String
    let rightActionEnabled: Bool
    let isSaving: Bool
    let onClose: () -> Void
    let onCommit: () -> Void

    var body: some View {
        ZStack {
            Text(title)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            HStack {
                Button(action: onClose) {
                    Icon(.x, size: 22, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Close")
                .accessibilityIdentifier("formCloseButton")
                Spacer()
                Button(action: onCommit) {
                    if isSaving {
                        ProgressView()
                            .frame(width: 60, height: 44)
                    } else {
                        Text(rightActionLabel)
                            .pantopusTextStyle(.body)
                            .foregroundStyle(
                                rightActionEnabled ? Theme.Color.primary600 : Theme.Color.appTextMuted
                            )
                            .frame(minWidth: 60, minHeight: 44)
                    }
                }
                .disabled(!rightActionEnabled)
                .accessibilityLabel(rightActionLabel)
                .accessibilityIdentifier("formCommitButton")
            }
            .padding(.horizontal, Spacing.s2)
        }
        .frame(height: 44)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }
}

/// Error shake modifier — 3 oscillations of 1pt over 240ms. Honors
/// reduced motion.
public struct FirstInvalidShake: ViewModifier {
    let trigger: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public func body(content: Content) -> some View {
        content
            .modifier(Shaker(animatable: CGFloat(trigger), enabled: !reduceMotion))
    }
}

private struct Shaker: GeometryEffect {
    var animatable: CGFloat
    let enabled: Bool

    var animatableData: CGFloat {
        get { animatable }
        set { animatable = newValue }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        guard enabled else { return ProjectionTransform(.identity) }
        let phase = sin(animatable * .pi * 3) * 1
        return ProjectionTransform(CGAffineTransform(translationX: phase, y: 0))
    }
}

public extension View {
    /// Apply a 3-oscillation 240ms 1pt horizontal shake whenever
    /// `trigger` changes. Respects reduced motion.
    func formShakeOnChange(of trigger: Int) -> some View {
        modifier(FirstInvalidShake(trigger: trigger))
            .animation(.easeInOut(duration: 0.24), value: trigger)
    }
}
