//
//  GigSearchSnapshotTests.swift
//  PantopusTests
//
//  P4.4 — render lockfile for the Gig Search surface. Mirrors the
//  `PulseComposeSnapshotTests` harness: each phase is hosted in a
//  `UIHostingController` so layout runs once and the view-tree
//  materialises. Covers every shell phase the screen produces —
//  idle/recent, typing-shimmer, populated results, no-matches empty, and
//  the error-flavoured empty — plus the VM-backed screen itself.
//

import SwiftUI
import UIKit
import XCTest
@testable import Pantopus

@MainActor
final class GigSearchSnapshotTests: XCTestCase {
    func test_gig_search_idle_recent_renders() {
        assertRenders(shell(query: "", results: [], isLoading: false))
    }

    func test_gig_search_typing_shimmer_renders() {
        assertRenders(shell(query: "shel", results: [], isLoading: true))
    }

    func test_gig_search_results_renders() {
        assertRenders(shell(query: "clean", results: Self.rows, isLoading: false))
    }

    func test_gig_search_empty_renders() {
        assertRenders(
            shell(
                query: "zzzzzz",
                results: [],
                isLoading: false,
                empty: EmptyStateContent(
                    icon: .search,
                    headline: "No matches",
                    subcopy: "Try a different keyword or category."
                )
            )
        )
    }

    func test_gig_search_error_renders() {
        assertRenders(
            shell(
                query: "shelf",
                results: [],
                isLoading: false,
                empty: EmptyStateContent(
                    icon: .alertCircle,
                    headline: "Couldn't search",
                    subcopy: "Network unavailable. Check your connection."
                )
            )
        )
    }

    func test_gig_search_view_default_renders() {
        assertRenders(GigSearchView())
    }

    // MARK: - Builders

    private func shell(
        query: String,
        results: [GigCardContent],
        isLoading: Bool,
        empty: EmptyStateContent = EmptyStateContent(
            icon: .search,
            headline: "No matches",
            subcopy: "Try a different keyword or category."
        )
    ) -> some View {
        SearchListShell(
            placeholder: "Search gigs, skills, neighborhoods…",
            query: .constant(query),
            results: results,
            isLoading: isLoading,
            filters: AnyView(
                GigsCategoryChipRow(active: .cleaning) { _ in }
            ),
            emptyState: empty,
            row: { content in
                GigRow(content: content)
                    .padding(.horizontal, Spacing.s3)
                    .padding(.vertical, Spacing.s1)
            },
            onCancel: {}
        )
    }

    private func assertRenders(
        _ view: some View,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let host = UIHostingController(rootView: view.frame(width: 390, height: 844))
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        host.view.layoutIfNeeded()
        XCTAssertGreaterThan(host.view.frame.size.width, 0, file: file, line: line)
        XCTAssertGreaterThan(host.view.frame.size.height, 0, file: file, line: line)
    }

    private static let rows: [GigCardContent] = [
        GigCardContent(
            id: "g1",
            category: .handyman,
            metaLine: "0.2mi · 2h ago",
            title: "Hang 3 floating shelves in living room",
            body: "Need 3 IKEA Lack shelves mounted on drywall.",
            price: "$60",
            bidCount: 4,
            distanceLabel: "0.2mi"
        ),
        GigCardContent(
            id: "g2",
            category: .cleaning,
            metaLine: "0.5mi · 5h ago",
            title: "Deep clean 2BR apartment before move-out",
            body: "Kitchen, bath, baseboards, inside oven.",
            price: "$180",
            bidCount: 0,
            distanceLabel: "0.5mi"
        ),
        GigCardContent(
            id: "g3",
            category: .petcare,
            metaLine: "0.3mi · 1d ago",
            title: "Midday dog walks Tue/Thu",
            body: "20-min loop, ongoing.",
            price: "$22 / walk",
            bidCount: 2,
            distanceLabel: "0.3mi"
        )
    ]
}
