//
//  BookletDetailDTO.swift
//  Pantopus
//
//  Booklet sub-payload decoded from `mail.object_payload` when
//  `mail_type == "booklet"`. Backend stores this as untyped JSON in S3
//  (route handler at `backend/routes/mailboxV2.js:412`); the DTO is
//  defensive and `decode(from:)` returns nil if the payload doesn't
//  carry a single page URL.
//

import Foundation

/// Booklet detail payload — drives the FrameBooklet swiper + facts.
public struct BookletDetailDTO: Sendable, Hashable {
    public let pages: [URL]
    public let summary: String?
    public let pageCount: Int

    public init(pages: [URL], summary: String?, pageCount: Int) {
        self.pages = pages
        self.summary = summary
        self.pageCount = pageCount
    }

    public static func decode(from value: JSONValue?) -> BookletDetailDTO? {
        guard let dict = value?.dictValue else { return nil }
        let pageURLs: [URL] = (dict["pages"]?.arrayValue ?? [])
            .compactMap(\.stringValue)
            .compactMap(URL.init(string:))
        guard !pageURLs.isEmpty else { return nil }
        return BookletDetailDTO(
            pages: pageURLs,
            summary: dict["summary"]?.stringValue,
            pageCount: dict["page_count"]?.numberValue.map { Int($0) } ?? pageURLs.count
        )
    }
}
