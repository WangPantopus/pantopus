//
//  MailDetailSnapshotTests.swift
//  PantopusTests
//
//  A17.1 — structural snapshots for the generic mail-detail layout.
//  These cover three category accents and the sender / subject / actions
//  slots while the project waits on pixel snapshot support for SwiftUI.
//

import SwiftUI
import XCTest
@testable import Pantopus

@MainActor
final class MailDetailSnapshotTests: XCTestCase {
    func test_notice_generic_detail_renders() {
        assertRenders(makeLayout(category: .notice, title: "Notice of public hearing"))
    }

    func test_package_generic_detail_renders() {
        assertRenders(makeLayout(category: .package, title: "Package delivered to porch"))
    }

    func test_coupon_generic_detail_renders() {
        assertRenders(makeLayout(category: .coupon, title: "Neighborhood bakery coupon"))
    }

    private func makeLayout(
        category: MailItemCategory,
        title: String
    ) -> some View {
        GenericMailDetailLayout(
            content: makeContent(category: category, title: title),
            ackInFlight: false,
            onBack: {},
            onAcknowledge: {},
            onOpenSenderProfile: { _ in },
            onSaveToVault: {}
        )
    }

    private func makeContent(
        category: MailItemCategory,
        title: String
    ) -> MailDetailContent {
        MailDetailContent(
            mailId: "mail-\(category.rawValue)",
            category: category,
            trust: .verified,
            detailTrust: category.detailTrust,
            senderDisplayName: "City of Oakland",
            senderMeta: "@oakland",
            senderTypeLabel: "Verified sender",
            carrierLine: "via Pantopus Mail",
            senderInitials: "CO",
            senderUserId: "sender-1",
            title: title,
            excerpt: "A short preview line keeps the subject block in the same shape as production mail.",
            referenceLabel: "Ref \(category.rawValue.uppercased())-2026",
            createdAtLabel: "Fri May 15, 2026",
            expiresAtLabel: nil,
            readStatusLabel: "Unread",
            bodyParagraphs: [
                "This is the first paragraph of the mail body, rendered in the category body slot.",
                "A second paragraph validates spacing before the sticky action shelf."
            ],
            attachments: ["notice.pdf"],
            aiSummary: nil,
            ackRequired: category == .notice,
            isAcknowledged: false
        )
    }

    private func assertRenders(
        _ view: some View,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let host = UIHostingController(
            rootView: view
                .frame(width: 390, height: 1500)
                .background(Theme.Color.appBg)
        )
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 1500)
        host.view.layoutIfNeeded()
        XCTAssertGreaterThan(host.view.frame.size.width, 0, file: file, line: line)
        XCTAssertGreaterThan(host.view.frame.size.height, 0, file: file, line: line)
    }
}
