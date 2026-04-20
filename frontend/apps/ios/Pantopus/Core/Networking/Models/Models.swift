//
//  Models.swift
//  Pantopus
//
//  DTOs that mirror the backend's JSON shapes. Keep these in sync with
//  backend/routes/*. See README's "Migration notes" for the long-term plan
//  to generate these from an OpenAPI spec.
//

import Foundation

// MARK: - Auth

struct LoginRequest: Encodable {
    let email: String
    let password: String
}

struct AuthResponse: Decodable {
    let accessToken: String
    let refreshToken: String?
    let user: UserDTO
}

struct RefreshRequest: Encodable {
    let refreshToken: String
}

// MARK: - User

struct UserDTO: Codable, Identifiable, Hashable {
    let id: String
    let email: String
    let displayName: String?
    let avatarURL: URL?
}

// MARK: - Feed

struct FeedPost: Decodable, Identifiable, Hashable {
    let id: String
    let authorId: String
    let authorName: String?
    let content: String
    let createdAt: Date
    let likeCount: Int
}

struct FeedResponse: Decodable {
    let posts: [FeedPost]
    let nextCursor: String?
}
