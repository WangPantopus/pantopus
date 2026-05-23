//
//  StartSupportTrainWizardView.swift
//  Pantopus
//
//  P2.6 — Organizer compose flow for a new Support Train. Composes
//  `WizardShell` with three step bodies (Who & why · What & when ·
//  Review & launch) plus a terminal success step that hands the new
//  train's id back to the host stack via `onOpenTrain`.
//

// swiftlint:disable file_length type_body_length

import SwiftUI

public struct StartSupportTrainWizardView: View {
    @State private var viewModel: StartSupportTrainWizardViewModel
    private let onDismiss: @MainActor () -> Void
    private let onOpenTrain: @MainActor (String) -> Void

    init(
        viewModel: StartSupportTrainWizardViewModel = StartSupportTrainWizardViewModel(),
        onDismiss: @escaping @MainActor () -> Void = {},
        onOpenTrain: @escaping @MainActor (String) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onDismiss = onDismiss
        self.onOpenTrain = onOpenTrain
    }

    public var body: some View {
        WizardShell(model: viewModel) {
            stepBody
            if let error = viewModel.launchError {
                StartSupportTrainErrorBanner(message: error)
            }
        }
        .onChange(of: viewModel.pendingEvent) { _, event in
            handle(event)
        }
        .accessibilityIdentifier("startSupportTrainWizard")
    }

    @ViewBuilder
    private var stepBody: some View {
        switch viewModel.step {
        case .whoAndWhy: StartSupportTrainWhoAndWhyStep(viewModel: viewModel)
        case .whatAndWhen: StartSupportTrainWhatAndWhenStep(viewModel: viewModel)
        case .reviewAndLaunch: StartSupportTrainReviewStep(viewModel: viewModel)
        case .success: StartSupportTrainSuccessStep(viewModel: viewModel)
        }
    }

    private func handle(_ event: StartSupportTrainEvent?) {
        guard let event else { return }
        switch event {
        case .dismiss: onDismiss()
        case let .openTrain(trainId): onOpenTrain(trainId)
        }
        viewModel.acknowledgePendingEvent()
    }
}

// MARK: - Shared building blocks

private struct StartSupportTrainErrorBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: 8) {
            Icon(.alertCircle, size: 14, color: Theme.Color.error)
            Text(message)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Theme.Color.error)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Theme.Color.errorBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .accessibilityIdentifier("startSupportTrainLaunchError")
    }
}

private struct OverlineLabel: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(Theme.Color.appTextSecondary)
            .kerning(0.6)
    }
}

private struct LabeledField<Body: View>: View {
    let label: String
    let content: () -> Body

    init(_ label: String, @ViewBuilder content: @escaping () -> Body) {
        self.label = label
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            OverlineLabel(text: label)
            content()
        }
    }
}

// MARK: - Step 1 · Who & why

private struct StartSupportTrainWhoAndWhyStep: View {
    let viewModel: StartSupportTrainWizardViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            SupportTrainChip()
            HeadlineBlock("Who is this for, and why?")
            SubcopyBlock(
                "A support train coordinates meals, rides, and help around someone going through something. Pick the person and the moment."
            )
            beneficiarySection
            reasonPicker
            if viewModel.isInviteRecipientBranch {
                invitePrivacyHint
            } else {
                contextNoteSection
                privacySection
            }
            SupportTrainStepRail(current: 1)
        }
    }

    private var beneficiarySection: some View {
        LabeledField("RECIPIENT") {
            VStack(alignment: .leading, spacing: 6) {
                if viewModel.isInviteRecipientBranch {
                    inviteRecipientCard
                } else if let selected = viewModel.selectedBeneficiary {
                    verifiedRecipientCard(selected)
                } else {
                    searchField
                    if !viewModel.beneficiaryResults.isEmpty {
                        resultList
                    }
                    Text("Search verified neighbors, or type a name to invite them directly.")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
            }
        }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Icon(.search, size: 14, color: Theme.Color.appTextSecondary)
            TextField("Search by name or username", text: Binding(
                get: { viewModel.beneficiaryQuery },
                set: { viewModel.updateBeneficiaryQuery($0) }
            ))
            .font(.system(size: 14))
            .accessibilityIdentifier("startSupportTrainBeneficiaryField")
            if viewModel.isSearchingBeneficiary {
                ProgressView().scaleEffect(0.7)
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 44)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }

    private var resultList: some View {
        VStack(spacing: 0) {
            ForEach(Array(viewModel.beneficiaryResults.enumerated()), id: \.element.id) { index, recipient in
                Button {
                    viewModel.selectBeneficiary(recipient)
                } label: {
                    HStack(spacing: 10) {
                        Icon(.user, size: 16, color: Theme.Color.primary600)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(recipient.name ?? recipient.username ?? "Recipient")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(Theme.Color.appText)
                            if let address = recipient.homeAddress {
                                Text(address)
                                    .font(.system(size: 11))
                                    .foregroundStyle(Theme.Color.appTextSecondary)
                                    .lineLimit(1)
                            }
                        }
                        Spacer()
                        Icon(.chevronRight, size: 14, color: Theme.Color.appTextSecondary)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("startSupportTrainResult_\(recipient.userId)")
                if index < viewModel.beneficiaryResults.count - 1 {
                    Rectangle()
                        .fill(Theme.Color.appBorder)
                        .frame(height: 1)
                        .padding(.leading, 12)
                }
            }
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }

    private func verifiedRecipientCard(_ recipient: MailRecipientDTO) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Theme.Color.personalBg)
                    .frame(width: 48, height: 48)
                Text(initials(for: recipient))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Theme.Color.personal)
                Circle()
                    .fill(Theme.Color.success)
                    .frame(width: 18, height: 18)
                    .overlay(Icon(.check, size: 10, strokeWidth: 3, color: Theme.Color.appTextInverse))
                    .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
                    .offset(x: 16, y: 16)
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(recipient.name ?? recipient.username ?? "Recipient")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    if recipient.isVerified != false {
                        Text("VERIFIED")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(Theme.Color.success)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Theme.Color.successBg)
                            .clipShape(Capsule())
                    }
                }
                Text(recipient.homeAddress ?? "Verified neighbor")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                HStack(spacing: 4) {
                    Icon(.users, size: 10, color: Theme.Color.appTextMuted)
                    Text("3 mutual friends on your block")
                        .font(.system(size: 10))
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
            }
            Spacer()
            Button {
                viewModel.clearBeneficiary()
            } label: {
                Text("Change")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.Color.primary600)
                    .frame(minWidth: 44, minHeight: 44)
            }
            .accessibilityLabel("Change recipient")
            .accessibilityIdentifier("startSupportTrainChangeRecipient")
        }
        .padding(12)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityIdentifier("startSupportTrainSelectedBeneficiary")
    }

    private var inviteRecipientCard: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Icon(.search, size: 14, color: Theme.Color.appTextMuted)
                Text(viewModel.beneficiaryQuery)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Theme.Color.appText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    viewModel.searchAgain()
                } label: {
                    Icon(.x, size: 12, color: Theme.Color.appTextSecondary)
                        .frame(width: 32, height: 32)
                }
                .accessibilityLabel("Clear recipient search")
                .accessibilityIdentifier("startSupportTrainClearInviteSearch")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)

            HStack(alignment: .top, spacing: 10) {
                ZStack {
                    Circle().fill(Theme.Color.warning).frame(width: 28, height: 28)
                    Icon(.search, size: 14, color: Theme.Color.appTextInverse)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("No verified neighbor found")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Theme.Color.warning)
                    Text("We searched verified addresses near yours. You can still start a train and invite them directly.")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.warning)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.Color.warningBg)

            Rectangle().fill(Theme.Color.warningLight).frame(height: 1)

            ForEach(Array(StartSupportTrainInviteMethod.allCases.enumerated()), id: \.element.id) { index, method in
                inviteMethodRow(method)
                if index < StartSupportTrainInviteMethod.allCases.count - 1 {
                    Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
                }
            }
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityIdentifier("startSupportTrainInviteRecipientCard")
    }

    private func inviteMethodRow(_ method: StartSupportTrainInviteMethod) -> some View {
        Button {
            viewModel.selectInviteMethod(method)
        } label: {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(Theme.Color.primary50)
                    .frame(width: 32, height: 32)
                    .overlay(Icon(method.icon, size: 15, color: Theme.Color.primary600))
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(method.title)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Theme.Color.appText)
                        if method == .phone {
                            Text("RECOMMENDED")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(Theme.Color.success)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Theme.Color.successBg)
                                .clipShape(Capsule())
                        }
                    }
                    Text(method.value)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer()
                Icon(
                    viewModel.inviteMethod == method ? .checkCircle : .chevronRight,
                    size: 16,
                    color: viewModel.inviteMethod == method ? Theme.Color.primary600 : Theme.Color.appTextMuted
                )
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("startSupportTrainInviteMethod_\(method.rawValue)")
    }

    private var reasonPicker: some View {
        LabeledField("WHAT'S THE OCCASION?") {
            LazyVGrid(
                columns: [
                    GridItem(.flexible(), spacing: Spacing.s2),
                    GridItem(.flexible(), spacing: Spacing.s2)
                ],
                spacing: Spacing.s2
            ) {
                ForEach(StartSupportTrainReason.allCases) { reason in
                    reasonTile(reason)
                }
            }
        }
    }

    private func reasonTile(_ reason: StartSupportTrainReason) -> some View {
        let isSelected = viewModel.selectedReason == reason
        return Button {
            viewModel.selectReason(reason)
        } label: {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(isSelected ? Theme.Color.warning : Theme.Color.appSurfaceSunken)
                    .frame(width: 28, height: 28)
                    .overlay(
                        Icon(
                            reason.icon,
                            size: 14,
                            color: isSelected ? Theme.Color.appTextInverse : Theme.Color.appTextStrong
                        )
                    )
                Text(reason.title)
                    .font(.system(size: 12, weight: isSelected ? .bold : .semibold))
                    .foregroundStyle(isSelected ? Theme.Color.warning : Theme.Color.appText)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(12)
            .frame(minHeight: 56)
            .background(isSelected ? Theme.Color.warningBg : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(isSelected ? Theme.Color.warning : Theme.Color.appBorder, lineWidth: isSelected ? 1.5 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("startSupportTrainReason_\(reason.rawValue)")
    }

    private var contextNoteSection: some View {
        LabeledField("SHORT NOTE") {
            VStack(alignment: .leading, spacing: 8) {
                ZStack(alignment: .topLeading) {
                    if viewModel.reason.isEmpty {
                        Text("Add a few details so neighbors know what kind of help fits.")
                            .font(.system(size: 13))
                            .foregroundStyle(Theme.Color.appTextMuted)
                            .padding(.top, 8)
                            .padding(.leading, 4)
                    }
                    TextEditor(text: Binding(
                        get: { viewModel.reason },
                        set: { viewModel.updateReason($0) }
                    ))
                    .frame(minHeight: 96)
                    .scrollContentBackground(.hidden)
                    .accessibilityIdentifier("startSupportTrainReasonField")
                }
                HStack(spacing: 4) {
                    Icon(.lock, size: 10, color: Theme.Color.appTextMuted)
                    Text("Shared only with people you invite")
                        .font(.system(size: 10))
                        .foregroundStyle(Theme.Color.appTextMuted)
                    Spacer()
                    Text("\(viewModel.reasonRemainingChars) left")
                        .font(.system(size: 10))
                        .foregroundStyle(Theme.Color.appTextMuted)
                        .accessibilityIdentifier("startSupportTrainReasonRemaining")
                }
            }
            .padding(8)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
    }

    private var privacySection: some View {
        VStack(spacing: 0) {
            privacyToggleRow(
                icon: .usersRound,
                title: "Invite only",
                subtitle: "Only people you add can see and sign up",
                isOn: Binding(
                    get: { viewModel.inviteOnly },
                    set: { viewModel.toggleInviteOnly($0) }
                ),
                identifier: "startSupportTrainInviteOnly"
            )
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            privacyToggleRow(
                icon: .home,
                title: "Block-visible",
                subtitle: "Verified neighbors at 412 Elm can see and offer",
                isOn: Binding(
                    get: { viewModel.blockVisible },
                    set: { viewModel.toggleBlockVisible($0) }
                ),
                identifier: "startSupportTrainBlockVisible"
            )
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private func privacyToggleRow(
        icon: PantopusIcon,
        title: String,
        subtitle: String,
        isOn: Binding<Bool>,
        identifier: String
    ) -> some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .fill(isOn.wrappedValue ? Theme.Color.warningBg : Theme.Color.appSurfaceSunken)
                .frame(width: 30, height: 30)
                .overlay(
                    Icon(
                        icon,
                        size: 14,
                        color: isOn.wrappedValue ? Theme.Color.warning : Theme.Color.appTextSecondary
                    )
                )
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            Toggle("", isOn: isOn)
                .labelsHidden()
                .tint(Theme.Color.warning)
                .accessibilityLabel(title)
                .accessibilityIdentifier(identifier)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
    }

    private var invitePrivacyHint: some View {
        HStack(alignment: .top, spacing: 10) {
            Icon(.shield, size: 14, color: Theme.Color.appTextSecondary)
                .padding(.top, 1)
            Text(
                "Invite-only by default. The train stays private until \(viewModel.beneficiaryQuery) accepts. " +
                    "Other neighbors won't see it on the block."
            )
            .font(.system(size: 11))
            .foregroundStyle(Theme.Color.appTextStrong)
            .fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityIdentifier("startSupportTrainInvitePrivacyHint")
    }

    private func initials(for recipient: MailRecipientDTO) -> String {
        let source = recipient.name ?? recipient.username ?? "Recipient"
        let pieces = source.split(separator: " ")
        let chars = pieces.prefix(2).compactMap(\.first)
        let letters = chars.map(String.init).joined()
        return letters.isEmpty ? "R" : letters.uppercased()
    }
}

private struct SupportTrainChip: View {
    var body: some View {
        HStack(spacing: 5) {
            Icon(.heart, size: 11, color: Theme.Color.warning)
            Text("SUPPORT TRAIN")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(Theme.Color.warning)
                .kerning(0.4)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(Theme.Color.warningBg)
        .clipShape(Capsule())
        .accessibilityIdentifier("startSupportTrainChip")
    }
}

private struct SupportTrainStepRail: View {
    let current: Int

    private let steps: [(Int, String)] = [
        (1, "Recipient"),
        (2, "Type"),
        (3, "Dates"),
        (4, "Invites"),
        (5, "Review")
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            OverlineLabel(text: "YOU'RE ON STEP \(current) OF 5")
            HStack(alignment: .top, spacing: 4) {
                ForEach(Array(steps.enumerated()), id: \.element.0) { index, step in
                    VStack(spacing: 4) {
                        ZStack {
                            Circle()
                                .fill(step.0 <= current ? Theme.Color.warning : Theme.Color.appSurfaceSunken)
                                .frame(width: 22, height: 22)
                            if step.0 < current {
                                Icon(.check, size: 11, strokeWidth: 3, color: Theme.Color.appTextInverse)
                            } else {
                                Text("\(step.0)")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(step.0 == current ? Theme.Color.appTextInverse : Theme.Color.appTextMuted)
                            }
                        }
                        Text(step.1)
                            .font(.system(size: 9, weight: step.0 == current ? .bold : .medium))
                            .foregroundStyle(step.0 == current ? Theme.Color.warning : Theme.Color.appTextMuted)
                            .lineLimit(1)
                    }
                    if index < steps.count - 1 {
                        Rectangle()
                            .fill(step.0 < current ? Theme.Color.warning : Theme.Color.appBorder)
                            .frame(height: 2)
                            .padding(.top, 10)
                            .frame(maxWidth: .infinity)
                    }
                }
            }
            .padding(12)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .accessibilityIdentifier("startSupportTrainStepRail")
        }
    }
}

// MARK: - Step 2 · What & when

private struct StartSupportTrainWhatAndWhenStep: View {
    let viewModel: StartSupportTrainWizardViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            HeadlineBlock("What's the rotation?")
            SubcopyBlock(
                "Pick the kind of help, the dates the train runs, and how long each slot is. We'll generate the calendar next."
            )
            kindSection
            dateRangeSection
            slotDurationSection
        }
    }

    private var kindSection: some View {
        LabeledField("KIND") {
            LazyVGrid(
                columns: [
                    GridItem(.flexible(), spacing: Spacing.s2),
                    GridItem(.flexible(), spacing: Spacing.s2)
                ],
                spacing: Spacing.s2
            ) {
                ForEach(SupportTrainKind.allCases) { kind in
                    kindCell(kind)
                }
            }
        }
    }

    private func kindCell(_ value: SupportTrainKind) -> some View {
        let isSelected = viewModel.kind == value
        return Button {
            viewModel.selectKind(value)
        } label: {
            HStack(spacing: 8) {
                Icon(
                    value.icon,
                    size: 16,
                    color: isSelected ? Theme.Color.primary700 : Theme.Color.appTextSecondary
                )
                Text(value.title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(isSelected ? Theme.Color.primary700 : Theme.Color.appText)
                Spacer()
            }
            .padding(.horizontal, 12)
            .frame(height: 44)
            .background(isSelected ? Theme.Color.primary50 : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(
                        isSelected ? Theme.Color.primary600 : Theme.Color.appBorder,
                        lineWidth: 1
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("startSupportTrainKind_\(value.rawValue)")
    }

    private var dateRangeSection: some View {
        LabeledField("DATES") {
            VStack(spacing: 8) {
                dateRow(
                    label: "Starts",
                    date: Binding(
                        get: { viewModel.startDate },
                        set: { viewModel.setStartDate($0) }
                    ),
                    identifier: "startSupportTrainStartDate"
                )
                dateRow(
                    label: "Ends",
                    date: Binding(
                        get: { viewModel.endDate },
                        set: { viewModel.setEndDate($0) }
                    ),
                    range: viewModel.startDate...,
                    identifier: "startSupportTrainEndDate"
                )
            }
        }
    }

    private func dateRow(
        label: String,
        date: Binding<Date>,
        range: PartialRangeFrom<Date>? = nil,
        identifier: String
    ) -> some View {
        HStack {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
            Spacer()
            Group {
                if let range {
                    DatePicker(
                        "",
                        selection: date,
                        in: range,
                        displayedComponents: .date
                    )
                } else {
                    DatePicker(
                        "",
                        selection: date,
                        displayedComponents: .date
                    )
                }
            }
            .labelsHidden()
            .accessibilityIdentifier(identifier)
        }
        .padding(.horizontal, 12)
        .frame(height: 48)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }

    private var slotDurationSection: some View {
        LabeledField("SLOT LENGTH") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(StartSupportTrainSlotDuration.allCases) { option in
                        let isActive = viewModel.slotDuration == option
                        Button {
                            viewModel.selectSlotDuration(option)
                        } label: {
                            Text(option.title)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(
                                    isActive ? Theme.Color.appTextInverse : Theme.Color.appTextStrong
                                )
                                .padding(.horizontal, 12)
                                .frame(height: 32)
                                .background(isActive ? Theme.Color.primary600 : Theme.Color.appSurface)
                                .overlay(
                                    Capsule().stroke(
                                        isActive ? Theme.Color.primary600 : Theme.Color.appBorder,
                                        lineWidth: 1
                                    )
                                )
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("startSupportTrainSlotDuration_\(option.rawValue)")
                    }
                }
            }
        }
    }
}

// MARK: - Step 3 · Review & launch

private struct StartSupportTrainReviewStep: View {
    let viewModel: StartSupportTrainWizardViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            HeadlineBlock("Look it over")
            SubcopyBlock(
                "Here's the calendar — \(viewModel.generatedSlots.count) slots, one per day. " +
                    "Decide who can see this, then launch."
            )
            summaryCard
            slotGridSection
            optionsSection
        }
    }

    private var summaryCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            reviewLine(
                "Beneficiary",
                value: viewModel.selectedBeneficiary?.name
                    ?? viewModel.selectedBeneficiary?.username
                    ?? viewModel.beneficiaryQuery
            )
            reviewLine("Kind", value: viewModel.kind.title)
            reviewLine("Slot length", value: viewModel.slotDuration.title)
            reviewLine("Slots", value: "\(viewModel.generatedSlots.count)")
            if !viewModel.reason.isEmpty {
                reviewLine("Reason", value: viewModel.reason)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityIdentifier("startSupportTrainReviewSummary")
    }

    private func reviewLine(_ label: String, value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(width: 110, alignment: .leading)
            Text(value.isEmpty ? "—" : value)
                .font(.system(size: 13))
                .foregroundStyle(Theme.Color.appText)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var slotGridSection: some View {
        LabeledField("SLOT GRID") {
            VStack(spacing: 0) {
                if viewModel.generatedSlots.isEmpty {
                    Text("Pick a date range to generate slots.")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                } else {
                    ForEach(Array(viewModel.generatedSlots.enumerated()), id: \.element.id) { index, slot in
                        slotRow(slot)
                        if index < viewModel.generatedSlots.count - 1 {
                            Rectangle()
                                .fill(Theme.Color.appBorderSubtle)
                                .frame(height: 1)
                                .padding(.leading, 14)
                        }
                    }
                }
            }
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .accessibilityIdentifier("startSupportTrainSlotGrid")
        }
    }

    private func slotRow(_ slot: StartSupportTrainSlot) -> some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                    .fill(Theme.Color.primary50)
                    .frame(width: 36, height: 36)
                Icon(.calendar, size: 16, color: Theme.Color.primary600)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(slot.dayLabel)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Text(slot.timeLabel)
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer()
            Text("Open")
                .font(.system(size: 10, weight: .bold))
                .kerning(0.4)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(Capsule())
        }
        .padding(14)
        .accessibilityIdentifier("startSupportTrainSlotRow_\(slot.dateKey)")
    }

    private var optionsSection: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            commentsToggle
            visibilitySection
        }
    }

    private var commentsToggle: some View {
        Toggle(isOn: Binding(
            get: { viewModel.allowComments },
            set: { viewModel.toggleAllowComments($0) }
        )) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Allow comments")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Text("Helpers can leave a note when they sign up.")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
        .tint(Theme.Color.primary600)
        .padding(12)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .accessibilityIdentifier("startSupportTrainAllowComments")
    }

    private var visibilitySection: some View {
        VStack(alignment: .leading, spacing: 6) {
            OverlineLabel(text: "VISIBILITY")
            VStack(spacing: 8) {
                ForEach(StartSupportTrainVisibility.allCases) { option in
                    visibilityRow(option)
                }
            }
        }
    }

    private func visibilityRow(_ option: StartSupportTrainVisibility) -> some View {
        let isSelected = viewModel.visibility == option
        return Button {
            viewModel.selectVisibility(option)
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .stroke(
                            isSelected ? Theme.Color.primary600 : Theme.Color.appBorderStrong,
                            lineWidth: 2
                        )
                        .frame(width: 20, height: 20)
                    if isSelected {
                        Circle().fill(Theme.Color.primary600).frame(width: 10, height: 10)
                    }
                }
                Icon(
                    option.icon,
                    size: 16,
                    color: isSelected ? Theme.Color.primary600 : Theme.Color.appTextSecondary
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text(option.title)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(option.subtitle)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer()
            }
            .padding(12)
            .background(isSelected ? Theme.Color.primary50 : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(
                        isSelected ? Theme.Color.primary600 : Theme.Color.appBorder,
                        lineWidth: 1
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("startSupportTrainVisibility_\(option.rawValue)")
    }
}

// MARK: - Step 4 · Success

private struct StartSupportTrainSuccessStep: View {
    let viewModel: StartSupportTrainWizardViewModel

    var body: some View {
        VStack(spacing: Spacing.s4) {
            ZStack {
                Circle().fill(Theme.Color.successBg).frame(width: 96, height: 96)
                Icon(.checkCircle, size: 56, color: Theme.Color.success)
            }
            .padding(.top, Spacing.s5)
            Text("Train launched")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Text(
                "\(viewModel.generatedSlots.count) slots are open and visible to " +
                    "\(viewModel.visibility.title.lowercased()). " +
                    "Review who signs up from the new train's dashboard."
            )
            .font(.system(size: 13))
            .foregroundStyle(Theme.Color.appTextSecondary)
            .multilineTextAlignment(.center)
            .padding(.horizontal, Spacing.s3)
        }
        .frame(maxWidth: .infinity)
        .accessibilityIdentifier("startSupportTrainSuccess")
    }
}

#Preview {
    StartSupportTrainWizardView()
}
