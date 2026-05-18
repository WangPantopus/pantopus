//
//  VaultDTOs.swift
//  Pantopus
//
//  T6.5e (P19.5) — Wire models for the Mailbox Vault endpoints in
//  `backend/routes/mailboxV2Phase2.js`. The backend treats vault as
//  the personal-pillar "keep pile" — folder-grouped, drawer-scoped.
//

import Foundation

/// `GET /api/mailbox/v2/p2/vault/folders` envelope — route
/// `backend/routes/mailboxV2Phase2.js:952`. Returns both a flat
/// `folders` array and a `grouped` map keyed by drawer; we read the
/// flat array since the UI filters client-side after fetch.
public struct VaultFoldersResponse: Decodable, Sendable, Hashable {
    public let folders: [VaultFolderDTO]

    public init(folders: [VaultFolderDTO]) {
        self.folders = folders
    }
}

/// Wire shape for a single vault folder row.
public struct VaultFolderDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let userId: String?
    public let drawer: String
    public let label: String
    public let icon: String?
    public let color: String?
    public let system: Bool?
    public let itemCount: Int?
    public let sortOrder: Int?
    public let createdAt: String?

    public init(
        id: String,
        userId: String?,
        drawer: String,
        label: String,
        icon: String?,
        color: String?,
        system: Bool?,
        itemCount: Int?,
        sortOrder: Int?,
        createdAt: String?
    ) {
        self.id = id
        self.userId = userId
        self.drawer = drawer
        self.label = label
        self.icon = icon
        self.color = color
        self.system = system
        self.itemCount = itemCount
        self.sortOrder = sortOrder
        self.createdAt = createdAt
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case drawer
        case label
        case icon
        case color
        case system
        case itemCount = "item_count"
        case sortOrder = "sort_order"
        case createdAt = "created_at"
    }
}

/// `POST /api/mailbox/v2/p2/vault/folder` envelope — `{ folder: ... }`.
public struct VaultFolderResponse: Decodable, Sendable, Hashable {
    public let folder: VaultFolderDTO?
}

/// `GET /api/mailbox/v2/p2/vault/folder/:id/items` envelope.
public struct VaultFolderItemsResponse: Decodable, Sendable, Hashable {
    public let items: [VaultMailItemDTO]
    public let total: Int?
}

/// Wire shape for a single vault-filed mail row. Mirrors a thin slice
/// of the `Mail` row — only what the vault row needs.
public struct VaultMailItemDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let mailType: String?
    public let type: String?
    public let subject: String?
    public let displayTitle: String?
    public let previewText: String?
    public let senderAddress: String?
    public let senderBusinessName: String?
    public let createdAt: String?
    public let lifecycle: String?
    public let viewedAt: String?
    public let attachments: [String]?
    public let vaultFolderId: String?

    public init(
        id: String,
        mailType: String?,
        type: String?,
        subject: String?,
        displayTitle: String?,
        previewText: String?,
        senderAddress: String?,
        senderBusinessName: String?,
        createdAt: String?,
        lifecycle: String?,
        viewedAt: String?,
        attachments: [String]?,
        vaultFolderId: String?
    ) {
        self.id = id
        self.mailType = mailType
        self.type = type
        self.subject = subject
        self.displayTitle = displayTitle
        self.previewText = previewText
        self.senderAddress = senderAddress
        self.senderBusinessName = senderBusinessName
        self.createdAt = createdAt
        self.lifecycle = lifecycle
        self.viewedAt = viewedAt
        self.attachments = attachments
        self.vaultFolderId = vaultFolderId
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case mailType = "mail_type"
        case type
        case subject
        case displayTitle = "display_title"
        case previewText = "preview_text"
        case senderAddress = "sender_address"
        case senderBusinessName = "sender_business_name"
        case createdAt = "created_at"
        case lifecycle
        case viewedAt = "viewed_at"
        case attachments
        case vaultFolderId = "vault_folder_id"
    }
}

/// `POST /api/mailbox/v2/p2/vault/file` envelope —
/// `{ message, folderId }`.
public struct FileToVaultResponse: Decodable, Sendable, Hashable {
    public let message: String?
    public let folderId: String?

    private enum CodingKeys: String, CodingKey {
        case message
        case folderId
    }
}
