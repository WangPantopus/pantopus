//
//  CertifiedTermsSheet.swift
//  Pantopus
//
//  Modal sheet shown when the user taps "View terms" on a certified
//  mail item. Renders the terms URL with a primary CTA that opens it
//  in the system browser. Fetching the document body is left for a
//  later prompt — the design says "fetch as plain text or render as
//  in-line markdown if backend returns markdown" but no fetch endpoint
//  exists today.
//

import SwiftUI

@MainActor
public struct CertifiedTermsSheet: View {
    private let termsURL: URL
    private let onDismiss: @MainActor () -> Void
    @Environment(\.openURL) private var openURL

    public init(termsURL: URL, onDismiss: @escaping @MainActor () -> Void) {
        self.termsURL = termsURL
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            HStack {
                Text("Terms of certified delivery")
                    .pantopusTextStyle(.h3)
                    .foregroundStyle(Theme.Color.appText)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Button(action: { onDismiss() }) {
                    Icon(.x, size: 20, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
            }

            Text("Read the full terms before acknowledging this certified document.")
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)

            // TODO(content): fetch body text from `termsURL` and render
            // inline once a backend endpoint exposes the document. For
            // now we surface the URL itself so the user can open it.
            VStack(alignment: .leading, spacing: Spacing.s2) {
                Text("Document URL")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Text(termsURL.absoluteString)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(3)
                    .truncationMode(.middle)
            }
            .padding(Spacing.s3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.Color.appSurfaceSunken)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md))

            PrimaryButton(title: "Open in browser") {
                await MainActor.run {
                    openURL(termsURL)
                    onDismiss()
                }
            }

            Spacer(minLength: 0)
        }
        .padding(Spacing.s4)
        .background(Theme.Color.appBg)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .accessibilityIdentifier("certifiedTermsSheet")
    }
}

#Preview {
    CertifiedTermsSheet(
        termsURL: URL(string: "https://example.com/certified-terms.pdf")!,
        onDismiss: {}
    )
}
