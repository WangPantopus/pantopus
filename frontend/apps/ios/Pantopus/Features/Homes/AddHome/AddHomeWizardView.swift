//
//  AddHomeWizardView.swift
//  Pantopus
//
//  Concrete Add-Home wizard. Composes `WizardShell` with five step bodies
//  and persists in-progress state via `@SceneStorage` so the wizard
//  survives process death (acceptance criterion #5).
//

import SwiftUI

/// Pushed onto the Hub stack from the MyHomes FAB / empty-CTA. On
/// success, signals the parent stack to pop the wizard and route to the
/// new home's dashboard via `onOpenHomeDashboard`.
public struct AddHomeWizardView: View {
    @State private var viewModel: AddHomeWizardViewModel
    @SceneStorage("addHomeWizardForm") private var storedForm: String = ""
    @State private var hasRestored = false
    @Environment(\.dismiss) private var dismiss

    private let onOpenHomeDashboard: (String) -> Void

    public init(
        onOpenHomeDashboard: @escaping (String) -> Void
    ) {
        _viewModel = State(initialValue: AddHomeWizardViewModel())
        self.onOpenHomeDashboard = onOpenHomeDashboard
    }

    init(
        viewModel: AddHomeWizardViewModel,
        onOpenHomeDashboard: @escaping (String) -> Void
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onOpenHomeDashboard = onOpenHomeDashboard
    }

    public var body: some View {
        WizardShell(model: viewModel) {
            stepContent
            if let error = viewModel.errorMessage {
                AddHomeErrorBanner(message: error)
            }
        }
        .onAppear {
            restoreIfNeeded()
            // Fire the initial step view event since transitions only
            // fire on user-driven step changes after this point.
            if let stepNumber = viewModel.currentStep.stepNumber {
                Analytics.track(
                    .screenAddHomeWizardStepViewed(
                        stepNumber: stepNumber,
                        stepName: String(describing: viewModel.currentStep)
                    )
                )
            }
        }
        .onChange(of: viewModel.form) { _, _ in persist() }
        .onChange(of: viewModel.pendingEvent) { _, event in
            handle(event)
        }
        .accessibilityIdentifier("addHomeWizard")
    }

    @ViewBuilder
    private var stepContent: some View {
        switch viewModel.currentStep {
        case .address: AddressStep(viewModel: viewModel)
        case .confirm: ConfirmStep(viewModel: viewModel)
        case .role: RoleStep(viewModel: viewModel)
        case .review: ReviewStep(viewModel: viewModel)
        case .success: SuccessStep()
        }
    }

    private func restoreIfNeeded() {
        guard !hasRestored else { return }
        hasRestored = true
        guard let data = storedForm.data(using: .utf8),
              let snapshot = try? JSONDecoder().decode(AddHomeFormState.self, from: data)
        else { return }
        viewModel.restore(from: snapshot)
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(viewModel.form),
              let json = String(data: data, encoding: .utf8)
        else { return }
        storedForm = json
    }

    private func handle(_ event: AddHomeOutboundEvent?) {
        guard let event else { return }
        switch event {
        case .dismiss:
            storedForm = ""
            dismiss()
        case let .openHomeDashboard(homeId):
            storedForm = ""
            onOpenHomeDashboard(homeId)
        }
        viewModel.pendingEvent = nil
    }
}

// MARK: - Step 2: Confirm

private struct ConfirmStep: View {
    @Bindable var viewModel: AddHomeWizardViewModel

    var body: some View {
        HeadlineBlock("Confirm the property")
        SubcopyBlock(
            "We checked this address against our property records. Review the details before continuing."
        )
        VStack(alignment: .leading, spacing: Spacing.s3) {
            ReviewSummaryBlock([
                ReviewSummaryRow(label: "Street", value: viewModel.form.address.street),
                ReviewSummaryRow(label: "City", value: viewModel.form.address.city),
                ReviewSummaryRow(label: "State", value: viewModel.form.address.state),
                ReviewSummaryRow(label: "ZIP", value: viewModel.form.address.zipCode)
            ])
            if let check = viewModel.addressCheck {
                AddressVerdictRow(check: check)
            }
            PrimaryHomeToggle(isPrimary: viewModel.form.isPrimary) {
                viewModel.setPrimaryHome($0)
            }
        }
    }
}

// MARK: - Step 3: Role

private struct RoleStep: View {
    @Bindable var viewModel: AddHomeWizardViewModel

    var body: some View {
        HeadlineBlock("What's your role?")
        SubcopyBlock("This determines what verification we'll ask for next.")
        VStack(spacing: Spacing.s2) {
            ForEach(AddHomeRole.allCases, id: \.self) { role in
                RoleRow(role: role, isSelected: viewModel.form.role == role) {
                    viewModel.selectRole(role)
                }
            }
        }
    }
}

// MARK: - Step 4: Review

private struct ReviewStep: View {
    @Bindable var viewModel: AddHomeWizardViewModel

    var body: some View {
        HeadlineBlock("Review and submit")
        SubcopyBlock("Make sure everything below looks right before submitting.")
        ReviewSummaryBlock([
            ReviewSummaryRow(
                label: "Address",
                value: composedAddress(viewModel.form.address)
            ),
            ReviewSummaryRow(
                label: "Role",
                value: viewModel.form.role?.label ?? "—"
            ),
            ReviewSummaryRow(
                label: "Primary",
                value: viewModel.form.isPrimary ? "Yes" : "No"
            )
        ])
    }

    private func composedAddress(_ fields: AddHomeAddressFields) -> String {
        var parts: [String] = [fields.street]
        if !fields.unit.isEmpty { parts.append(fields.unit) }
        parts.append(fields.city)
        parts.append("\(fields.state) \(fields.zipCode)")
        return parts.joined(separator: ", ")
    }
}

// MARK: - Step 5: Success

private struct SuccessStep: View {
    var body: some View {
        // Re-use the T3.6 Status / Waiting screen so the home-added
        // terminal shares its chrome with the claim-submitted and
        // check-your-email frames.
        StatusWaitingView(
            content: .claimSubmitted(homeName: nil)
                .withHeadline("Home added")
                .withSubcopy("We'll email you when verification completes.")
        )
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private extension StatusWaitingContent {
    func withHeadline(_ headline: String) -> StatusWaitingContent {
        StatusWaitingContent(
            illustration: illustration,
            headline: headline,
            subcopy: subcopy,
            timeline: timeline,
            currentStageId: currentStageId,
            etaChip: etaChip,
            actionCards: actionCards,
            explainerBullets: explainerBullets,
            primaryCta: primaryCta,
            secondaryCta: secondaryCta
        )
    }

    func withSubcopy(_ subcopy: String) -> StatusWaitingContent {
        StatusWaitingContent(
            illustration: illustration,
            headline: headline,
            subcopy: subcopy,
            timeline: timeline,
            currentStageId: currentStageId,
            etaChip: etaChip,
            actionCards: actionCards,
            explainerBullets: explainerBullets,
            primaryCta: primaryCta,
            secondaryCta: secondaryCta
        )
    }
}

private struct AddressVerdictRow: View {
    let check: CheckAddressResponse

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Icon(verdictIcon, size: 20, color: verdictColor)
            VStack(alignment: .leading, spacing: 2) {
                Text(verdictHeadline)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Text(verdictSubcopy)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer()
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurfaceMuted)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(verdictHeadline). \(verdictSubcopy)")
    }

    private var verdictIcon: PantopusIcon {
        check.exists ? .alertCircle : .checkCircle
    }

    private var verdictColor: Color {
        check.exists ? Theme.Color.warning : Theme.Color.success
    }

    private var verdictHeadline: String {
        check.exists
            ? "Already on Pantopus"
            : "Looks good"
    }

    private var verdictSubcopy: String {
        if check.exists {
            return "Another household already has this address. We'll route you to a join flow next."
        }
        return "We'll create a new household for this address."
    }
}

private struct PrimaryHomeToggle: View {
    let isPrimary: Bool
    let onChange: @MainActor @Sendable (Bool) -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("This is my primary home")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Text("Use this home for default mail and notifications.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer()
            Toggle(
                "",
                isOn: Binding(get: { isPrimary }, set: onChange)
            )
            .labelsHidden()
            .tint(Theme.Color.primary600)
            .accessibilityIdentifier("addHome_primaryToggle")
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }
}

private struct RoleRow: View {
    let role: AddHomeRole
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                ZStack {
                    Circle()
                        .stroke(
                            isSelected ? Theme.Color.primary600 : Theme.Color.appBorder,
                            lineWidth: 2
                        )
                        .frame(width: 22, height: 22)
                    if isSelected {
                        Circle().fill(Theme.Color.primary600).frame(width: 12, height: 12)
                    }
                }
                Text(role.label)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Spacer()
            }
            .padding(Spacing.s3)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(
                        isSelected ? Theme.Color.primary600 : Theme.Color.appBorder,
                        lineWidth: isSelected ? 2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("addHome_role_\(role.rawValue)")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

private struct AddHomeErrorBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.alertCircle, size: 18, color: Theme.Color.error)
            Text(message)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.error)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(Spacing.s3)
        .background(Theme.Color.errorBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .accessibilityIdentifier("addHomeErrorBanner")
    }
}

#Preview {
    AddHomeWizardView { _ in }
}
