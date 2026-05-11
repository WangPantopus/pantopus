//
//  BookletBody.swift
//  Pantopus
//
//  Concrete body for the Booklet mailbox category. Replaces the P9
//  placeholder. Hosts the page swiper plus the optional summary copy
//  the design draws below the pager.
//

import SwiftUI

@MainActor
public struct BookletBody: View {
    private let booklet: BookletDetailDTO

    public init(booklet: BookletDetailDTO) {
        self.booklet = booklet
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            BookletPageSwiper(pages: booklet.pages)

            if let summary = booklet.summary, !summary.isEmpty {
                Text(summary)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .padding(.horizontal, Spacing.s4)
                    .accessibilityLabel("Summary: \(summary)")
            }
        }
    }
}

#Preview {
    BookletBody(booklet: BookletDetailDTO(
        pages: [
            URL(string: "https://placehold.co/640x360")!,
            URL(string: "https://placehold.co/640x360/orange/white")!
        ],
        summary: "Spring catalog — 24 pages of new offerings.",
        pageCount: 24
    ))
    .background(Theme.Color.appBg)
}
