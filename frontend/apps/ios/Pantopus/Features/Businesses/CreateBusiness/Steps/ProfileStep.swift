//
//  ProfileStep.swift
//  Pantopus
//
//  Create Business step 3 — Location Form + Hours. Both sections may be
//  skipped. Composed with Wizard + Form tokens (no design frames).
//

import SwiftUI

struct ProfileStep: View {
    @Bindable var viewModel: CreateBusinessWizardViewModel

    var body: some View {
        BusinessIdentityChip()
        HeadlineBlock(
            "Location & hours",
            subtitle: "Add a primary address and weekly hours, or skip for now."
        )

        locationSection
        hoursSection

        if let submitError = viewModel.submitError, viewModel.currentStep == .profile {
            Text(submitError)
                .font(.system(size: 13))
                .foregroundStyle(Theme.Color.error)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier("createBusinessSubmitError")
        }
    }

    @ViewBuilder
    private var locationSection: some View {
        if viewModel.locationSkipped {
            skippedCard(
                icon: .mapPin,
                label: "Location skipped",
                subcopy: "You can add a location later from the dashboard.",
                actionLabel: "Add a location",
                action: viewModel.unskipLocation
            )
        } else {
            FormFieldsBlock {
                PantopusTextField(
                    "Address",
                    text: Binding(
                        get: { viewModel.address },
                        set: { viewModel.setAddress($0) }
                    ),
                    placeholder: "123 Main St",
                    identifier: "createBusiness_address"
                )
                PantopusTextField(
                    "City",
                    text: Binding(
                        get: { viewModel.city },
                        set: { viewModel.setCity($0) }
                    ),
                    placeholder: "Vancouver",
                    identifier: "createBusiness_city"
                )
                PantopusTextField(
                    "State",
                    text: Binding(
                        get: { viewModel.state },
                        set: { viewModel.setState($0) }
                    ),
                    placeholder: "WA",
                    identifier: "createBusiness_state"
                )
                PantopusTextField(
                    "ZIP code",
                    text: Binding(
                        get: { viewModel.zip },
                        set: { viewModel.setZip($0) }
                    ),
                    placeholder: "98660",
                    keyboardType: .numberPad,
                    identifier: "createBusiness_zip"
                )
                Button {
                    viewModel.skipLocation()
                } label: {
                    Text("Skip location for now")
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("createBusiness_skipLocation")
            }
        }
    }

    @ViewBuilder
    private var hoursSection: some View {
        if viewModel.locationSkipped {
            EmptyView()
        } else if viewModel.hoursSkipped {
            skippedCard(
                icon: .clock,
                label: "Hours skipped",
                subcopy: "You can set hours later from the dashboard.",
                actionLabel: "Set hours",
                action: viewModel.unskipHours
            )
        } else if !viewModel.hasLocation && viewModel.address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            Text("Add an address and city to configure hours, or skip location above.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            VStack(alignment: .leading, spacing: Spacing.s2) {
                Text("Hours")
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appText)
                VStack(spacing: Spacing.s0) {
                    ForEach(viewModel.hours) { day in
                        hoursRow(day)
                        if day.dayOfWeek < 6 {
                            Rectangle()
                                .fill(Theme.Color.appBorderSubtle)
                                .frame(height: 1)
                        }
                    }
                }
                .padding(.horizontal, Spacing.s3)
                .background(Theme.Color.appSurface)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))

                Button {
                    viewModel.skipHours()
                } label: {
                    Text("Skip hours for now")
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("createBusiness_skipHours")
            }
        }
    }

    private func hoursRow(_ day: BusinessHoursDay) -> some View {
        HStack(spacing: Spacing.s2) {
            Button {
                viewModel.toggleDayClosed(day.dayOfWeek)
            } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.xs, style: .continuous)
                        .strokeBorder(
                            day.isClosed ? Theme.Color.appBorder : Theme.Color.business,
                            lineWidth: 1.5
                        )
                        .background(
                            RoundedRectangle(cornerRadius: Radii.xs, style: .continuous)
                                .fill(day.isClosed ? Theme.Color.appSurfaceSunken : Theme.Color.businessBg)
                        )
                        .frame(width: 28, height: 28)
                    if !day.isClosed {
                        Icon(.check, size: 14, color: Theme.Color.business)
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("createBusiness_hoursDay_\(day.dayOfWeek)")

            Text(day.shortLabel)
                .pantopusTextStyle(.body)
                .foregroundStyle(day.isClosed ? Theme.Color.appTextSecondary : Theme.Color.appText)
                .frame(width: 40, alignment: .leading)

            if day.isClosed {
                Text("Closed")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .italic()
            } else {
                Text("\(day.openTime) – \(day.closeTime)")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appText)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, Spacing.s2)
    }

    private func skippedCard(
        icon: PantopusIcon,
        label: String,
        subcopy: String,
        actionLabel: String,
        action: @escaping () -> Void
    ) -> some View {
        VStack(spacing: Spacing.s3) {
            ZStack {
                Circle().fill(Theme.Color.businessBg).frame(width: 48, height: 48)
                Icon(icon, size: 22, strokeWidth: 2, color: Theme.Color.business)
            }
            Text(label)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
            Text(subcopy)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button(action: action) {
                Text(actionLabel)
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.business)
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }
}
