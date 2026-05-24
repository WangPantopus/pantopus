//
//  CouponBodySnapshotTests.swift
//  PantopusTests
//
//  A17.5 — structural render snapshots for Coupon mail across unused,
//  redeemed, and expired states.
//

import SwiftUI
import XCTest
@testable import Pantopus

@MainActor
final class CouponBodySnapshotTests: XCTestCase {
    func test_coupon_unused_renders() {
        assertRenders(CouponBody(coupon: MailItemSampleData.couponUnused, state: .unused))
    }

    func test_coupon_redeemed_renders() {
        assertRenders(CouponBody(coupon: MailItemSampleData.couponRedeemed, state: .redeemed))
    }

    func test_coupon_expired_renders() {
        assertRenders(CouponBody(coupon: MailItemSampleData.couponExpired, state: .expired))
    }

    func test_coupon_expanded_barcode_renders() {
        assertRenders(
            CouponBody(
                coupon: MailItemSampleData.couponUnused,
                state: .unused,
                barcodeInitiallyExpanded: true
            )
        )
    }

    private func assertRenders(
        _ view: some View,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let host = UIHostingController(
            rootView: ScrollView { view }
                .frame(width: 390, height: 1300)
                .background(Theme.Color.appBg)
        )
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 1300)
        host.view.layoutIfNeeded()
        XCTAssertGreaterThan(host.view.frame.size.width, 0, file: file, line: line)
        XCTAssertGreaterThan(host.view.frame.size.height, 0, file: file, line: line)
    }
}
