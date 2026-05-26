//
//  ProfessionalProfileView.swift
//  Pantopus
//
//  A.5 (A13.11) — the Professional Profile editor (Business pillar). A
//  pushed `FormShell` route with a back chevron, a verification-aware
//  sticky bar, and five sections: Role · Skills · Certifications ·
//  Portfolio · Visibility. Distinct from the Personal `EditProfile` (A13.9).
//

import SwiftUI

public struct ProfessionalProfileView: View {
    @State private var viewModel: ProfessionalProfileViewModel
    private let onBack: @MainActor () -> Void

    public init(
        viewModel: ProfessionalProfileViewModel = ProfessionalProfileViewModel(),
        onBack: @escaping @MainActor () -> Void = {}
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
    }

    public var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                ProfessionalProfileSkeleton()
            case let .verified(content):
                loaded(content, mode: .saved, dirtyCount: 0, pendingCount: content.pendingCount)
            case let .pending(content, dirtyCount, pendingCount):
                loaded(content, mode: .pendingSave, dirtyCount: dirtyCount, pendingCount: pendingCount)
            case let .error(message):
                EmptyState(
                    icon: .alertCircle,
                    headline: "Couldn't load professional profile",
                    subcopy: message,
                    cta: EmptyState.CTA(title: "Try again") { await viewModel.refresh() },
                    tint: Theme.Color.businessBg,
                    accent: Theme.Color.business
                )
            }
        }
        .background(Theme.Color.appBg)
        .task { await viewModel.load() }
        .overlay(alignment: .bottom) {
            if let toast = viewModel.toast {
                ToastView(message: toast)
                    .padding(.bottom, Spacing.s16)
                    .task {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        viewModel.toast = nil
                    }
                    .transition(.opacity)
                    .accessibilityIdentifier("professionalProfileToast")
            }
        }
        .animation(.easeInOut(duration: 0.2), value: viewModel.toast)
        .accessibilityIdentifier("professionalProfile")
    }

    // MARK: - Populated

    private func loaded(
        _ content: ProfessionalProfileContent,
        mode: ProSticky.Mode,
        dirtyCount: Int,
        pendingCount: Int
    ) -> some View {
        FormShell(
            title: "Professional profile",
            leading: .back,
            rightActionLabel: nil,
            isValid: true,
            isDirty: mode == .pendingSave,
            onClose: onBack,
            onCommit: {},
            content: {
                pillarHeader(content)
                roleSection(content)
                skillsSection(content)
                certificationsSection(content)
                portfolioSection(content)
                visibilitySection(content)
            },
            stickyBottom: {
                AnyView(
                    ProSticky(
                        mode: mode,
                        dirtyCount: dirtyCount,
                        pendingCount: pendingCount,
                        onDiscard: { viewModel.discard() },
                        onSaveSubmit: { viewModel.saveAndSubmit() }
                    )
                )
            }
        )
        .accessibilityIdentifier("professionalProfileShell")
    }

    // MARK: - Pillar header

    private func pillarHeader(_ content: ProfessionalProfileContent) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(spacing: Spacing.s3) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .fill(Theme.Color.business)
                        .frame(width: 40, height: 40)
                    Icon(.briefcase, size: 18, color: Theme.Color.appTextInverse)
                }
                .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 1) {
                    Text("\(content.proName) · Pro")
                        .pantopusTextStyle(.small)
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.Color.appText)
                    Text("Separate from your personal & home identities")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.business)
                }
                Spacer(minLength: Spacing.s0)
                Text("Business")
                    .pantopusTextStyle(.overline)
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s2)
                    .padding(.vertical, 3)
                    .background(Theme.Color.business)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.xs, style: .continuous))
            }
            PillarStrip(
                title: "Profile strength",
                percent: content.strength,
                tint: Theme.Color.business,
                caption: content.strengthCaption,
                identifier: "proProfileStrength"
            )
        }
        .padding(Spacing.s3)
        .background(Theme.Color.businessBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.business.opacity(0.2), lineWidth: 1)
        )
        .padding(.horizontal, Spacing.s4)
        .accessibilityIdentifier("proPillarHeader")
    }

    // MARK: - Sections

    private func roleSection(_ content: ProfessionalProfileContent) -> some View {
        proSection("Role") {
            VStack(alignment: .leading, spacing: Spacing.s1) {
                proFieldLabel("Company", dirty: content.company.isDirty)
                CompanyField(company: content.company)
                if let hint = content.company.hint {
                    HStack(alignment: .top, spacing: Spacing.s1) {
                        Icon(.info, size: 11, color: Theme.Color.warning)
                        Text(hint)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.warning)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            proTextField(
                .init(
                    label: "Title",
                    required: true,
                    value: content.title.value,
                    dirty: content.title.isDirty,
                    placeholder: "e.g. Licensed General Handyman",
                    identifier: "proTitleField"
                )
            ) { viewModel.updateTitle($0) }
            proTextField(
                .init(
                    label: "Years in role",
                    required: true,
                    value: content.yearsInRole.value,
                    dirty: content.yearsInRole.isDirty,
                    placeholder: "0",
                    identifier: "proYearsInRoleField",
                    keyboard: .numberPad
                )
            ) { viewModel.updateYearsInRole($0) }
        }
    }

    private func skillsSection(_ content: ProfessionalProfileContent) -> some View {
        proSection("Skills") {
            proFieldLabel("Specialties", dirty: content.skills.contains(where: \.isFresh))
            FilterSheetFlowLayout(spacing: Spacing.s1) {
                ForEach(content.skills) { skill in
                    ProSkillChip(skill: skill) { viewModel.removeSkill(skill.id) }
                }
                AddSkillChip { viewModel.addSkill() }
            }
            .padding(Spacing.s2)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            Text("Match jobs Pantopus shows you. Up to 8.")
                .pantopusTextStyle(.caption)
                .italic()
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }

    private func certificationsSection(_ content: ProfessionalProfileContent) -> some View {
        proSection("Certifications") {
            ForEach(content.certifications) { cert in
                CertCard(cert: cert) { viewModel.removeCertification(cert.id) }
            }
            AddCertButton { viewModel.addCertification() }
        }
    }

    private func portfolioSection(_ content: ProfessionalProfileContent) -> some View {
        proSection("Portfolio") {
            ForEach(content.portfolio) { link in
                LinkCard(link: link)
            }
            AddLinkRow { viewModel.addPortfolioLink() }
        }
    }

    private func visibilitySection(_ content: ProfessionalProfileContent) -> some View {
        proSection("Visibility") {
            VStack(spacing: Spacing.s0) {
                ForEach(Array(content.visibility.enumerated()), id: \.element.id) { index, row in
                    VisRow(row: row) { viewModel.setVisibility(row.id, isOn: $0) }
                    if index < content.visibility.count - 1 {
                        Rectangle()
                            .fill(Theme.Color.appBorderSubtle)
                            .frame(height: 1)
                            .padding(.leading, Spacing.s3)
                    }
                }
            }
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
        }
    }

    // MARK: - Section / field helpers

    private func proSection(
        _ overline: String,
        @ViewBuilder content: () -> some View
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(overline)
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .padding(.horizontal, Spacing.s4)
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: Spacing.s3) {
                content()
            }
            .padding(.horizontal, Spacing.s4)
        }
    }

    private func proFieldLabel(
        _ text: String,
        required: Bool = false,
        optional: Bool = false,
        dirty: Bool = false
    ) -> some View {
        HStack(spacing: Spacing.s1) {
            Text(text)
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.appTextStrong)
            if required {
                Text("*")
                    .pantopusTextStyle(.caption)
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.Color.business)
            }
            if optional {
                Text("(optional)")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
            if dirty { FreshDot() }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            text + (required ? ", required" : "") + (optional ? ", optional" : "") + (dirty ? ", edited" : "")
        )
    }

    private func proTextField(
        _ spec: ProTextFieldSpec,
        onChange: @escaping @MainActor @Sendable (String) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            proFieldLabel(spec.label, required: spec.required, optional: spec.optional, dirty: spec.dirty)
            TextField(spec.placeholder, text: Binding(get: { spec.value }, set: onChange), axis: .vertical)
                .font(Theme.Font.body)
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(1...3)
                .keyboardType(spec.keyboard)
                .padding(.horizontal, Spacing.s3)
                .padding(.vertical, Spacing.s2)
                .frame(minHeight: 44)
                .background(Theme.Color.appSurface)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .accessibilityIdentifier(spec.identifier)
                .accessibilityLabel(spec.label)
        }
    }
}

private struct ProTextFieldSpec {
    let label: String
    var required = false
    var optional = false
    let value: String
    let dirty: Bool
    let placeholder: String
    let identifier: String
    var keyboard: UIKeyboardType = .default
}

// MARK: - Loading skeleton

/// Shimmer placeholder that mirrors the populated geometry: pillar header,
/// a couple of fields, and stacked cards.
@MainActor
struct ProfessionalProfileSkeleton: View {
    var body: some View {
        VStack(spacing: Spacing.s0) {
            // Top bar stand-in.
            HStack {
                Icon(.chevronLeft, size: 22, color: Theme.Color.appTextMuted)
                    .frame(width: 44, height: 44)
                Spacer()
                Text("Professional profile")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Spacer()
                Color.clear.frame(width: 44, height: 44)
            }
            .padding(.horizontal, Spacing.s2)
            .frame(height: 44)
            .background(Theme.Color.appSurface)
            .overlay(alignment: .bottom) {
                Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            }
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s5) {
                    Shimmer(height: 96, cornerRadius: Radii.lg)
                        .padding(.horizontal, Spacing.s4)
                    sectionSkeleton(rows: 2, height: 44)
                    sectionSkeleton(rows: 1, height: 56)
                    sectionSkeleton(rows: 3, height: 64)
                }
                .padding(.vertical, Spacing.s4)
            }
            .background(Theme.Color.appBg)
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("professionalProfileLoading")
    }

    private func sectionSkeleton(rows: Int, height: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Shimmer(width: 90, height: 12, cornerRadius: Radii.xs)
                .padding(.horizontal, Spacing.s4)
            VStack(spacing: Spacing.s2) {
                ForEach(0..<rows, id: \.self) { _ in
                    Shimmer(height: height, cornerRadius: Radii.md)
                }
            }
            .padding(.horizontal, Spacing.s4)
        }
    }
}

#Preview("Verified") {
    ProfessionalProfileView(viewModel: ProfessionalProfileViewModel(seed: ProfessionalProfileSampleData.published))
}

#Preview("Pending") {
    ProfessionalProfileView(viewModel: ProfessionalProfileViewModel(
        seed: ProfessionalProfileSampleData.pendingEdits,
        baseline: ProfessionalProfileSampleData.published
    ))
}

#Preview("Loading") {
    ProfessionalProfileSkeleton()
}
