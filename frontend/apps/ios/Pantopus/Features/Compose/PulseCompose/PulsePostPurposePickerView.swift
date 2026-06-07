//
//  PulsePostPurposePickerView.swift
//  Pantopus
//
//  Step 2 — "What is this post for?" Grid of purpose chips filtered
//  by the selected posting target.
//

import SwiftUI

public struct PulsePostPurposePickerView: View {
    private let target: PulsePostingTarget
    private let onSelect: @MainActor (PulseComposePurpose) -> Void
    private let onBack: @MainActor () -> Void

    private var purposes: [PulseComposePurpose] {
        PulseComposePurpose.allowed(for: target)
    }

    public init(
        target: PulsePostingTarget,
        onSelect: @escaping @MainActor (PulseComposePurpose) -> Void,
        onBack: @escaping @MainActor () -> Void
    ) {
        self.target = target
        self.onSelect = onSelect
        self.onBack = onBack
    }

    public var body: some View {
        FormShell(
            title: "New Post",
            leading: .back,
            rightActionLabel: nil,
            isValid: false,
            isDirty: false,
            onClose: onBack,
            onCommit: {},
            content: {
                VStack(alignment: .leading, spacing: Spacing.s3) {
                    Text("What is this post for?")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)

                    LazyVGrid(
                        columns: [GridItem(.flexible(), spacing: Spacing.s2), GridItem(.flexible(), spacing: Spacing.s2)],
                        spacing: Spacing.s2
                    ) {
                        ForEach(purposes) { purpose in
                            purposeChip(purpose)
                        }
                    }
                }
            }
        )
        .accessibilityIdentifier("pulsePostPurposePicker")
    }

    private func purposeChip(_ purpose: PulseComposePurpose) -> some View {
        Button { onSelect(purpose) } label: {
            HStack(spacing: Spacing.s2) {
                Icon(purposeIcon(purpose), size: 18, strokeWidth: 2, color: purposeAccent(purpose))
                Text(purpose.label)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(purposeAccent(purpose))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .padding(.horizontal, Spacing.s3)
            .background(purposeBackground(purpose))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(purposeAccent(purpose).opacity(0.25), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("pulsePurpose-\(purpose.rawValue)")
    }

    private func purposeIcon(_ purpose: PulseComposePurpose) -> PantopusIcon {
        switch purpose {
        case .ask: .helpCircle
        case .headsUp: .megaphone
        case .recommend: .star
        case .lostFound: .search
        case .localUpdate: .fileText
        case .neighborhoodWin: .crown
        case .visitorGuide: .compass
        case .event: .calendar
        case .deal: .tag
        }
    }

    private func purposeAccent(_ purpose: PulseComposePurpose) -> Color {
        switch purpose {
        case .ask: Theme.Color.primary600
        case .headsUp: Theme.Color.error
        case .recommend: Theme.Color.warmAmber
        case .lostFound: Theme.Color.warning
        case .localUpdate: Theme.Color.primary700
        case .neighborhoodWin: Theme.Color.success
        case .visitorGuide: Theme.Color.home
        case .event: Theme.Color.primary600
        case .deal: Theme.Color.success
        }
    }

    private func purposeBackground(_ purpose: PulseComposePurpose) -> Color {
        switch purpose {
        case .ask: Theme.Color.primary50
        case .headsUp: Theme.Color.errorBg
        case .recommend: Theme.Color.warmAmberBg
        case .lostFound: Theme.Color.warningBg
        case .localUpdate: Theme.Color.primary50
        case .neighborhoodWin: Theme.Color.successBg
        case .visitorGuide: Theme.Color.homeBg
        case .event: Theme.Color.primary50
        case .deal: Theme.Color.successBg
        }
    }
}
