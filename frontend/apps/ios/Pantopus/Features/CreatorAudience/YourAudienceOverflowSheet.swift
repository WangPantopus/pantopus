//
//  YourAudienceOverflowSheet.swift
//  Pantopus
//
//  A22.2 "Your audience" — the per-member overflow (•••) action sheet:
//  Message · Change tier · Remove (destructive). Remove maps to
//  `PATCH /me/audience/:membershipId { action: "remove" }`.
//

import SwiftUI

struct YourAudienceOverflowSheet: View {
    let member: AudienceMember
    let onMessage: () -> Void
    let onChangeTier: () -> Void
    let onRemove: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            VStack(alignment: .leading, spacing: Spacing.s1) {
                Text(member.displayName)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Text(member.handle)
                    .font(.system(size: 12.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            .padding(.bottom, Spacing.s2)

            actionRow(
                icon: .messageCircle,
                title: "Message",
                tint: Theme.Color.appText,
                id: "audienceOverflow.message"
            ) {
                onMessage()
                dismiss()
            }

            actionRow(
                icon: .crown,
                title: "Change tier",
                tint: Theme.Color.appText,
                id: "audienceOverflow.changeTier"
            ) {
                onChangeTier()
                dismiss()
            }

            actionRow(
                icon: .userMinus,
                title: "Remove",
                tint: Theme.Color.error,
                id: "audienceOverflow.remove"
            ) {
                onRemove()
                dismiss()
            }
        }
        .padding(Spacing.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityIdentifier("audienceOverflowSheet")
    }

    private func actionRow(
        icon: PantopusIcon,
        title: String,
        tint: Color,
        id: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: Spacing.s3) {
                Icon(icon, size: 18, color: tint)
                    .frame(width: 24)
                Text(title)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(tint)
                Spacer()
            }
            .padding(.vertical, Spacing.s3)
            .padding(.horizontal, Spacing.s3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Radii.md)
                    .fill(Theme.Color.appSurfaceSunken)
            )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }
}
