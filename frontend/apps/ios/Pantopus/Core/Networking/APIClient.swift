//
//  APIClient.swift
//  Pantopus
//
//  Thin URLSession client with async/await. Adds auth headers, decodes
//  JSON, surfaces APIError. Designed to be extended one endpoint at a time.
//

import Foundation
import Logging

@Observable
final class APIClient {
    static let shared = APIClient()

    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder
    private let logger = Logger(label: "app.pantopus.ios.APIClient")
    private let environment: AppEnvironment

    init(environment: AppEnvironment = .current, session: URLSession = .shared) {
        self.environment = environment
        self.session = session

        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .iso8601
        self.decoder = decoder

        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        encoder.dateEncodingStrategy = .iso8601
        self.encoder = encoder
    }

    // MARK: - Core request

    func request<Response: Decodable>(
        _ endpoint: Endpoint,
        as _: Response.Type = Response.self
    ) async throws -> Response {
        let request = try buildRequest(for: endpoint)
        let (data, response) = try await session.data(for: request)

        guard let http = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        logger.debug("API \(endpoint.method.rawValue) \(endpoint.path) -> \(http.statusCode)")

        guard (200..<300).contains(http.statusCode) else {
            let message = String(data: data, encoding: .utf8) ?? "Unknown error"
            if http.statusCode == 401 {
                // Token expired / invalid — nudge the auth manager.
                await AuthManager.shared.handleUnauthorized()
                throw APIError.unauthorized
            }
            throw APIError.server(status: http.statusCode, body: message)
        }

        if Response.self == EmptyResponse.self, data.isEmpty {
            // swiftlint:disable:next force_cast
            return EmptyResponse() as! Response
        }

        do {
            return try decoder.decode(Response.self, from: data)
        } catch {
            logger.error("Decode error for \(endpoint.path)", metadata: ["error": .string("\(error)")])
            throw APIError.decoding(underlying: error)
        }
    }

    /// Convenience for endpoints that return no body.
    @discardableResult
    func request(_ endpoint: Endpoint) async throws -> EmptyResponse {
        try await request(endpoint, as: EmptyResponse.self)
    }

    // MARK: - Push token registration

    func registerPushToken(_ token: String, platform: String) async {
        do {
            try await request(
                Endpoint(
                    method: .post,
                    path: "/api/notifications/register",
                    body: ["token": token, "platform": platform]
                )
            )
        } catch {
            logger.warning("Push token registration failed", metadata: ["error": .string("\(error)")])
        }
    }

    // MARK: - Building requests

    private func buildRequest(for endpoint: Endpoint) throws -> URLRequest {
        guard var components = URLComponents(
            url: environment.apiBaseURL.appendingPathComponent(endpoint.path),
            resolvingAgainstBaseURL: false
        ) else {
            throw APIError.invalidURL
        }
        if !endpoint.query.isEmpty {
            components.queryItems = endpoint.query.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = components.url else { throw APIError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = endpoint.method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("ios-\(Bundle.main.appVersion)", forHTTPHeaderField: "X-Client-Platform")

        if endpoint.authenticated, let token = AuthManager.shared.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        if let body = endpoint.body {
            request.httpBody = try encoder.encode(AnyEncodable(body))
        }

        return request
    }
}

// MARK: - Endpoint

struct Endpoint {
    enum Method: String {
        case get = "GET"
        case post = "POST"
        case put = "PUT"
        case patch = "PATCH"
        case delete = "DELETE"
    }

    let method: Method
    let path: String
    var query: [String: String] = [:]
    var body: (any Encodable)?
    var authenticated: Bool = true

    init(
        method: Method,
        path: String,
        query: [String: String] = [:],
        body: (any Encodable)? = nil,
        authenticated: Bool = true
    ) {
        self.method = method
        self.path = path
        self.query = query
        self.body = body
        self.authenticated = authenticated
    }
}

// MARK: - Errors

enum APIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case unauthorized
    case server(status: Int, body: String)
    case decoding(underlying: Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL: "Could not build request URL."
        case .invalidResponse: "Invalid response from server."
        case .unauthorized: "Your session has expired. Please sign in again."
        case .server(let status, let body): "Server error \(status): \(body)"
        case .decoding(let error): "Could not parse response: \(error.localizedDescription)"
        }
    }
}

// MARK: - Helpers

struct EmptyResponse: Decodable {}

/// Erases any Encodable into something JSONEncoder can handle when we only
/// know the concrete type at the call site.
private struct AnyEncodable: Encodable {
    private let encodeClosure: (Encoder) throws -> Void
    init<T: Encodable>(_ wrapped: T) {
        self.encodeClosure = wrapped.encode
    }
    func encode(to encoder: Encoder) throws {
        try encodeClosure(encoder)
    }
}

private extension Bundle {
    var appVersion: String {
        (infoDictionary?["CFBundleShortVersionString"] as? String) ?? "0.0.0"
    }
}
