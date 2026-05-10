//
//  RowModel.swift
//  Pantopus
//
//  Template-agnostic row payload. Every list-of-rows screen maps its
//  backend DTOs into one of these.
//

import SwiftUI

/// Visual template for a list row.
public enum RowTemplate: Sendable {
    /// Title + optional subtitle + trailing status chip.
    case statusChip
    /// Leading icon + title + trailing chevron — for drill-down lists.
    case fileChevron
    /// Leading avatar + title + trailing kebab (more-horizontal) menu.
    case avatarKebab
}

/// Optional leading visual.
public enum RowLeading: Sendable {
    case icon(PantopusIcon, tint: Color = Theme.Color.primary600)
    case avatar(name: String, imageURL: URL?, identity: IdentityPillar, ringProgress: Double)
    case none
}

/// Trailing payload — rendered according to the chosen `RowTemplate`.
public enum RowTrailing: Sendable {
    case statusChip(text: String, variant: StatusChipVariant)
    case chevron
    case kebab
    case none
}

/// A single row. Call sites construct these from DTOs in their ViewModel.
public struct RowModel: Identifiable, Sendable {
    public let id: String
    public let title: String
    public let subtitle: String?
    public let template: RowTemplate
    public let leading: RowLeading
    public let trailing: RowTrailing
    /// Invoked when the row is tapped.
    public let onTap: @Sendable () -> Void
    /// Invoked when the kebab menu is tapped (if any).
    public let onSecondary: (@Sendable () -> Void)?

    public init(
        id: String,
        title: String,
        subtitle: String? = nil,
        template: RowTemplate,
        leading: RowLeading = .none,
        trailing: RowTrailing = .none,
        onTap: @escaping @Sendable () -> Void = {},
        onSecondary: (@Sendable () -> Void)? = nil
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.template = template
        self.leading = leading
        self.trailing = trailing
        self.onTap = onTap
        self.onSecondary = onSecondary
    }
}

/// Optional grouping for the list body.
public struct RowSection: Identifiable, Sendable {
    public let id: String
    public let header: String?
    public let rows: [RowModel]

    public init(id: String = UUID().uuidString, header: String? = nil, rows: [RowModel]) {
        self.id = id
        self.header = header
        self.rows = rows
    }
}
