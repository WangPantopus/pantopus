//
//  FormShell.swift
//  Pantopus
//
//  Top-bar (X + title + Save) + scrolling body + optional sticky CTA.
//  Handles the dirty-close confirm and the first-invalid shake on submit.
//

import SwiftUI

/// Close-button behavior.
public enum FormCloseIntent: Sendable {
    /// Form is clean — dismiss immediately.
    case dismiss
    /// Form is dirty — confirm discard before dismissing.
    case confirmDiscard
}

/// Scaffold for every Form screen.
@MainActor
public struct FormShell<Content: View>: View {
    private let title: String
    private let aggregate: FormAggregate
    private let isSaving: Bool
    private let onClose: (FormCloseIntent) -> Void
    private let onSave: () -> Void
    private let content: Content

    public init(
        title: String,
        aggregate: FormAggregate,
        isSaving: Bool = false,
        onClose: @escaping (FormCloseIntent) -> Void,
        onSave: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.aggregate = aggregate
        self.isSaving = isSaving
        self.onClose = onClose
        self.onSave = onSave
        self.content = content()
    }

    public var body: some View {
        VStack(spacing: 0) {
            FormTopBar(
                title: title,
                saveEnabled: aggregate.isDirty && aggregate.isValid && !isSaving,
                isSaving: isSaving,
                onClose: { onClose(aggregate.isDirty ? .confirmDiscard : .dismiss) },
                onSave: onSave
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
    }
}

/// 44pt top bar: leading X, centered title, trailing Save.
private struct FormTopBar: View {
    let title: String
    let saveEnabled: Bool
    let isSaving: Bool
    let onClose: () -> Void
    let onSave: () -> Void

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
                Button(action: onSave) {
                    if isSaving {
                        ProgressView()
                            .frame(width: 60, height: 44)
                    } else {
                        Text("Save")
                            .pantopusTextStyle(.body)
                            .foregroundStyle(saveEnabled ? Theme.Color.primary600 : Theme.Color.appTextMuted)
                            .frame(minWidth: 60, minHeight: 44)
                    }
                }
                .disabled(!saveEnabled)
                .accessibilityLabel("Save")
                .accessibilityIdentifier("formSaveButton")
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
