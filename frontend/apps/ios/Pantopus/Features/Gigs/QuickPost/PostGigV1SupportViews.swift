//
//  PostGigV1SupportViews.swift
//  Pantopus
//
//  Shared support states for the A13.8 V1 single-screen gig composer.
//

import SwiftUI

struct PostGigV1ErrorBanner: View {
    let errors: [PostGigV1ValidationError]

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.s3) {
            Icon(.alertTriangle, size: 14, color: Theme.Color.appTextInverse)
                .frame(width: 24, height: 24)
                .background(Theme.Color.error)
                .clipShape(Circle())
                .padding(.top, 1)
            VStack(alignment: .leading, spacing: Spacing.s1) {
                Text("\(errors.count) problems - please fix")
                    .pantopusTextStyle(.small)
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.Color.error)
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(errors) { error in
                        Text("• \(error.message)")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.error)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s2 + 2)
        .background(Theme.Color.errorBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.errorLight, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(errors.count) problems. \(errors.map(\.message).joined(separator: " "))")
        .accessibilityIdentifier("postGigV1_errorBanner")
    }
}

struct PostGigV1InlineError: View {
    let message: String

    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(.alertCircle, size: 11, color: Theme.Color.error)
            Text(message)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.error)
        }
        .accessibilityElement(children: .combine)
    }
}

struct PostGigV1FieldLabel: View {
    let title: String
    let required: Bool

    init(_ title: String, required: Bool) {
        self.title = title
        self.required = required
    }

    var body: some View {
        HStack(spacing: 2) {
            Text(title)
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.appTextStrong)
            if required {
                Text("*")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
                    .accessibilityHidden(true)
            }
        }
        .accessibilityLabel(required ? "\(title), required" : title)
    }
}

struct PostGigV1LegacyStamp: View {
    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(.info, size: 11, color: Theme.Color.appTextMuted)
            Text("gig composer · v1.4.2")
                .font(.system(size: 10.5, weight: .regular, design: .monospaced))
                .foregroundStyle(Theme.Color.appTextMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.bottom, Spacing.s2)
        .accessibilityIdentifier("postGigV1_legacyStamp")
    }
}

struct PostGigV1LoadingView: View {
    var body: some View {
        ForEach(["Category", "Details", "Pay", "When", "Photos"], id: \.self) { title in
            FormFieldGroup(title) {
                VStack(spacing: Spacing.s3) {
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .fill(Theme.Color.appSurfaceSunken)
                        .frame(height: 44)
                    if title == "Details" {
                        RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                            .fill(Theme.Color.appSurfaceSunken)
                            .frame(height: 108)
                    }
                }
                .accessibilityHidden(true)
            }
        }
    }
}

struct PostGigV1EmptyView: View {
    let onStart: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s3) {
            Icon(.briefcase, size: 30, color: Theme.Color.primary600)
                .frame(width: 72, height: 72)
                .background(Theme.Color.primary50)
                .clipShape(Circle())
            Text("No quick-post draft")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
            Text("Start with the V1 form when you already know the title, price, and time.")
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button(action: onStart) {
                Text("Start quick post")
                    .pantopusTextStyle(.small)
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s5)
                    .frame(minHeight: 44)
                    .background(Theme.Color.primary600)
                    .clipShape(Capsule())
            }
            .accessibilityIdentifier("postGigV1_emptyStart")
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.s6)
        .accessibilityIdentifier("postGigV1_empty")
    }
}

struct PostGigV1FatalErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s3) {
            Icon(.alertTriangle, size: 28, color: Theme.Color.error)
                .frame(width: 64, height: 64)
                .background(Theme.Color.errorBg)
                .clipShape(Circle())
            Text("Quick post unavailable")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button("Retry", action: onRetry)
                .foregroundStyle(Theme.Color.primary600)
                .frame(minHeight: 44)
                .accessibilityIdentifier("postGigV1_retry")
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.s6)
        .accessibilityIdentifier("postGigV1_error")
    }
}
