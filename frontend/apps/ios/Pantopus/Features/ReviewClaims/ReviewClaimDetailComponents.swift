//
//  ReviewClaimDetailComponents.swift
//  Pantopus
//
//  P1.1 — Supporting views for `ReviewClaimDetailView`. Split out of the
//  main file to keep each file under SwiftLint's `file_length` threshold.
//

import SwiftUI

// MARK: - Evidence list

struct ReviewClaimEvidenceList: View {
    let evidence: [AdminClaimEvidenceDTO]

    var body: some View {
        if evidence.isEmpty {
            HStack(spacing: Spacing.s2) {
                Icon(.alertCircle, size: 20, color: Theme.Color.warning)
                Text("No documents uploaded yet")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.Color.warning)
                Spacer(minLength: 0)
            }
            .padding(Spacing.s3)
            .background(Theme.Color.warningBg)
            .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
        } else {
            VStack(spacing: Spacing.s2) {
                ForEach(evidence) { item in
                    ReviewClaimEvidenceRow(item: item)
                }
            }
        }
    }
}

private struct ReviewClaimEvidenceRow: View {
    let item: AdminClaimEvidenceDTO

    var body: some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.md)
                    .fill(Theme.Color.businessBg)
                    .frame(width: 40, height: 40)
                Icon(isImage ? .file : .fileText, size: 20, color: Theme.Color.business)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(AdminClaimEvidenceLabel.display(for: item.evidenceType))
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                if let name = item.fileName, !name.isEmpty {
                    Text(name)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(1)
                }
                Text(metaLine)
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
            Spacer(minLength: 0)
            if let urlString = item.fileURL, let url = URL(string: urlString) {
                Link(destination: url) {
                    Text("View")
                        .font(.system(size: 12, weight: .semibold))
                        .padding(.horizontal, Spacing.s3)
                        .frame(height: 32)
                        .foregroundStyle(Theme.Color.primary600)
                        .background(Theme.Color.primary50)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.md))
                }
                .accessibilityLabel("View \(AdminClaimEvidenceLabel.display(for: item.evidenceType))")
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
    }

    private var isImage: Bool {
        item.mimeType?.hasPrefix("image/") == true
    }

    private var metaLine: String {
        var parts: [String] = []
        if let size = item.fileSize, size > 0 {
            parts.append("\(size / 1024) KB")
        }
        parts.append(AdminClaimTimeFormat.longDate(item.createdAt))
        return parts.joined(separator: " · ")
    }
}

// MARK: - Action footer

struct ReviewClaimActionFooter: View {
    let reviewingAction: AdminClaimReviewAction?
    let onApprove: () -> Void
    let onReject: () -> Void
    let onRequestInfo: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s2) {
            Button(action: onApprove) {
                HStack(spacing: Spacing.s2) {
                    if reviewingAction == .approve {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: Theme.Color.appTextInverse))
                    } else {
                        Icon(.checkCircle, size: 18, color: Theme.Color.appTextInverse)
                    }
                    Text(reviewingAction == .approve ? "Approving…" : "Approve")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 48)
                .background(Theme.Color.success)
                .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
            }
            .buttonStyle(.plain)
            .disabled(reviewingAction != nil)
            .accessibilityIdentifier("reviewClaimDetail_approve")
            .accessibilityLabel("Approve claim")

            HStack(spacing: Spacing.s2) {
                Button(action: onReject) {
                    HStack(spacing: Spacing.s1) {
                        Icon(.circleSlash, size: 16, color: Theme.Color.error)
                        Text("Reject")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.Color.error)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
                    .background(Theme.Color.errorBg)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.xl)
                            .stroke(Theme.Color.error.opacity(0.5), lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
                }
                .buttonStyle(.plain)
                .disabled(reviewingAction != nil)
                .accessibilityIdentifier("reviewClaimDetail_reject")
                .accessibilityLabel("Reject claim")

                Button(action: onRequestInfo) {
                    HStack(spacing: Spacing.s1) {
                        Icon(.helpCircle, size: 16, color: Theme.Color.warning)
                        Text("Request info")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.Color.warning)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
                    .background(Theme.Color.warningBg)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.xl)
                            .stroke(Theme.Color.warning.opacity(0.5), lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
                }
                .buttonStyle(.plain)
                .disabled(reviewingAction != nil)
                .accessibilityIdentifier("reviewClaimDetail_requestInfo")
                .accessibilityLabel("Request more info")
            }
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .top) {
            Divider().background(Theme.Color.appBorderSubtle)
        }
    }
}

// MARK: - Note capture sheet (reject / request info)

struct ReviewClaimNoteCaptureSheet: View {
    let title: String
    let prompt: String
    let placeholder: String
    let primaryTitle: String
    let primaryRole: ButtonRole?
    @Binding var note: String
    let isSubmitting: Bool
    let onPrimary: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            HStack {
                Text(title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Spacer(minLength: 0)
                Button(action: onCancel) {
                    Icon(.x, size: 22, color: Theme.Color.appTextSecondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
            }
            Text(prompt)
                .font(.system(size: 14))
                .foregroundStyle(Theme.Color.appTextSecondary)
            TextEditor(text: $note)
                .frame(minHeight: 120)
                .padding(Spacing.s2)
                .background(Theme.Color.appBg)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .accessibilityIdentifier("reviewClaimDetail_noteEditor")
            Button(role: primaryRole, action: onPrimary) {
                HStack(spacing: Spacing.s2) {
                    if isSubmitting {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: Theme.Color.appTextInverse))
                    }
                    Text(isSubmitting ? "Sending…" : primaryTitle)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 48)
                .background(primaryBackground)
                .clipShape(RoundedRectangle(cornerRadius: Radii.xl))
            }
            .buttonStyle(.plain)
            .disabled(isSubmitting)
            .accessibilityIdentifier("reviewClaimDetail_notePrimary")

            Button(action: onCancel) {
                Text("Cancel")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
            }
            .buttonStyle(.plain)
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.Color.appSurface)
        .presentationDetents([.medium, .large])
    }

    private var primaryBackground: Color {
        primaryRole == .destructive ? Theme.Color.error : Theme.Color.primary600
    }
}
