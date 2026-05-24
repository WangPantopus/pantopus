//
//  PackageBodySnapshotTests.swift
//  PantopusTests
//
//  A17.8 - Package mail body. Structural render coverage for the three
//  designed states until real SwiftUI image snapshots land in the iOS test
//  target.
//

import SwiftUI
import XCTest
@testable import Pantopus

@MainActor
final class PackageBodySnapshotTests: XCTestCase {
    func test_package_inTransit_renders() {
        assertRenders(
            PackageBody(
                content: MailItemSampleData.packageInTransit,
                isReceiveEnabled: false
            )
        )
    }

    func test_package_outForDelivery_renders() {
        assertRenders(
            PackageBody(
                content: MailItemSampleData.packageOutForDelivery,
                isReceiveEnabled: false
            )
        )
    }

    func test_package_delivered_renders() {
        assertRenders(
            PackageBody(
                content: MailItemSampleData.packageDelivered,
                isReceiveEnabled: true
            )
        )
    }

    func test_package_fixture_shapes_matchA178() {
        XCTAssertEqual(MailItemSampleData.packageOutForDelivery.trackingSteps.map(\.title), [
            "Shipped", "In transit", "Out for delivery", "Delivered"
        ])
        XCTAssertNil(MailItemSampleData.packageOutForDelivery.deliveryPhoto)
        XCTAssertNotNil(MailItemSampleData.packageDelivered.deliveryPhoto)
        XCTAssertFalse(MailItemSampleData.packageDelivered.handoffSteps.isEmpty)
        XCTAssertEqual(MailItemSampleData.packageDelivered.contents?.items.count, 2)
    }

    private func assertRenders(
        _ view: PackageBody,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let host = UIHostingController(
            rootView: ScrollView { view }
                .frame(width: 390, height: 1800)
                .background(Theme.Color.appBg)
        )
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 1800)
        host.view.layoutIfNeeded()
        XCTAssertGreaterThan(host.view.frame.size.width, 0, file: file, line: line)
        XCTAssertGreaterThan(host.view.frame.size.height, 0, file: file, line: line)
    }
}
