//
//  APIClient.swift
//  Pantopus
//
//  Async/await HTTP client with typed errors, ETag-aware response caching,
//  and exponential-backoff retry for idempotent GETs. Every feature
//  accesses the backend through this — no direct `URLSession.shared` in
//  feature code.
//

import Foundation
import Logging

/// Pantopus's HTTP client. Owns a dedicated `URLSession` with on-disk
/// caching; emits typed `APIError` values; retries transient failures on
/// idempotent methods.
@Observable
final class APIClient: @unchecked Sendable {
    /// Singleton for the live app. Unit tests construct their own instance.
    static let shared = APIClient()

    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder
    private let logger = Logger(label: "app.pantopus.ios.APIClient")
    private let environment: AppEnvironment
    private let retryPolicy: RetryPolicy

    /// - Parameters:
    ///   - environment: API target + base URL.
    ///   - session: Inject a custom session for tests. Defaults to a
    ///     URLCache-backed session sized 10MB / 50MB (memory / disk).
    ///   - retryPolicy: Retry configuration.
    init(
        environment: AppEnvironment = .current,
        session: URLSession? = nil,
        retryPolicy: RetryPolicy = .default
    ) {
        self.environment = environment
        self.retryPolicy = retryPolicy

        if let session {
            self.session = session
        } else {
            let config = URLSessionConfiguration.default
            // ETag-aware cache — URLSession honours Cache-Control, ETag, and
            // If-None-Match automatically when the policy allows it. Feature
            // code can override per-request via `Endpoint.cachePolicy`.
            let cache = URLCache(
                memoryCapacity: 10 * 1024 * 1024,
                diskCapacity: 50 * 1024 * 1024,
                diskPath: "pantopus-http"
            )
            config.urlCache = cache
            config.requestCachePolicy = .useProtocolCachePolicy
            config.timeoutIntervalForRequest = 20
            config.timeoutIntervalForResource = 60
            self.session = URLSession(configuration: config)
        }

        let decoder = JSONDecoder()
        // We do NOT set `convertFromSnakeCase` globally — many DTOs mix
        // snake_case and camelCase in the same response, so per-field
        // `CodingKeys` are the source of truth.
        decoder.dateDecodingStrategy = .iso8601
        self.decoder = decoder

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        self.encoder = encoder
    }

    // MARK: - Public API

    /// Perform a request and decode the response body as `Response`.
    func request<Response: Decodable>(
        _ endpoint: Endpoint,
        as _: Response.Type = Response.self
    ) async throws -> Response {
        let data = try await executeWithRetry(endpoint)
        if Response.self == EmptyResponse.self, data.isEmpty {
            // swiftlint:disable:next force_cast
            return EmptyResponse() as! Response
        }
        do {
            return try decoder.decode(Response.self, from: data)
        } catch {
            logger.error(
                "Decode error for \(endpoint.path)",
                metadata: ["error": .string("\(error)")]
            )
            await Observability.shared.capture(error)
            throw APIError.decoding(underlying: error)
        }
    }

    /// Void convenience. Throws on non-2xx; returns `EmptyResponse` otherwise.
    @discardableResult
    func request(_ endpoint: Endpoint) async throws -> EmptyResponse {
        try await request(endpoint, as: EmptyResponse.self)
    }

    /// `Result`-returning variant for call sites that prefer explicit
    /// handling over `try`.
    func perform<Response: Decodable>(
        _ endpoint: Endpoint,
        as type: Response.Type = Response.self
    ) async -> APIResult<Response> {
        do {
            return .success(try await request(endpoint, as: type))
        } catch let error as APIError {
            return .failure(error)
        } catch {
            return .failure(.decoding(underlying: error))
        }
    }

    // MARK: - Push token (unchanged from Prompt P1)

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
            logger.warning(
                "Push token registration failed",
                metadata: ["error": .string("\(error)")]
            )
        }
    }

    // MARK: - Retry loop

    private func executeWithRetry(_ endpoint: Endpoint) async throws -> Data {
        let request = try await buildRequest(for: endpoint)
        let shouldRetry = endpoint.method.isIdempotent
        var attempt = 0
        var lastError: APIError = .retriesExhausted
        while true {
            do {
                return try await executeOnce(request, endpoint: endpoint)
            } catch let error as APIError {
                lastError = error
                if !shouldRetry || !error.isTransient || attempt >= retryPolicy.maxRetries {
                    throw error
                }
                attempt += 1
                let delay = retryPolicy.delay(forAttempt: attempt)
                logger.info(
                    "Retry \(attempt)/\(self.retryPolicy.maxRetries) after \(Int(delay * 1000))ms for \(endpoint.path)"
                )
                try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            }
        }
        throw lastError
    }

    private func executeOnce(_ request: URLRequest, endpoint: Endpoint) async throws -> Data {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError {
            throw APIError.transport(underlying: error)
        } catch {
            throw APIError.invalidResponse
        }
        guard let http = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        logger.debug("API \(endpoint.method.rawValue) \(endpoint.path) -> \(http.statusCode)")

        switch http.statusCode {
        case 200..<300, 304:
            return data
        case 401:
            await AuthManager.shared.handleUnauthorized()
            throw APIError.unauthorized
        case 403: throw APIError.forbidden
        case 404: throw APIError.notFound
        case 400..<500:
            let message = String(data: data, encoding: .utf8)
            throw APIError.clientError(status: http.statusCode, message: message)
        default:
            await Observability.shared.capture(
                message: "API \(endpoint.method.rawValue) \(endpoint.path) -> \(http.statusCode)",
                level: .error
            )
            let body = String(data: data, encoding: .utf8) ?? ""
            throw APIError.server(status: http.statusCode, body: body)
        }
    }

    // MARK: - Building requests

    private func buildRequest(for endpoint: Endpoint) async throws -> URLRequest {
        guard var components = URLComponents(
            url: environment.apiBaseURL.appendingPathComponent(endpoint.path),
            resolvingAgainstBaseURL: false
        ) else {
            throw APIError.invalidURL
        }
        if !endpoint.query.isEmpty {
            components.queryItems = endpoint.query
                .sorted { $0.key < $1.key }
                .map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = components.url else { throw APIError.invalidURL }

        var request = URLRequest(url: url, cachePolicy: endpoint.cachePolicy)
        request.httpMethod = endpoint.method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(
            "ios-\(Bundle.main.appVersion)",
            forHTTPHeaderField: "X-Client-Platform"
        )
        for (key, value) in endpoint.headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        if endpoint.authenticated, let token = await AuthManager.shared.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        if let body = endpoint.body {
            request.httpBody = try encoder.encode(AnyEncodable(body))
        }

        return request
    }
}

// MARK: - Endpoint

/// A fully-described outbound HTTP call. Prefer the feature-scoped helpers
/// in `Networking/Endpoints/` over constructing this by hand.
public struct Endpoint: Sendable {
    public enum Method: String, Sendable {
        case get = "GET"
        case post = "POST"
        case put = "PUT"
        case patch = "PATCH"
        case delete = "DELETE"

        /// Only idempotent methods are retried by the client.
        public var isIdempotent: Bool {
            self == .get || self == .put || self == .delete
        }
    }

    public let method: Method
    public let path: String
    public let query: [String: String]
    public let body: (any Encodable & Sendable)?
    public let headers: [String: String]
    public let authenticated: Bool
    public let cachePolicy: URLRequest.CachePolicy

    public init(
        method: Method,
        path: String,
        query: [String: String] = [:],
        body: (any Encodable & Sendable)? = nil,
        headers: [String: String] = [:],
        authenticated: Bool = true,
        cachePolicy: URLRequest.CachePolicy = .useProtocolCachePolicy
    ) {
        self.method = method
        self.path = path
        self.query = query
        self.body = body
        self.headers = headers
        self.authenticated = authenticated
        self.cachePolicy = cachePolicy
    }
}

// MARK: - Retry policy

/// Exponential-backoff retry policy with jitter.
public struct RetryPolicy: Sendable {
    public let maxRetries: Int
    public let baseDelay: TimeInterval
    public let maxDelay: TimeInterval

    public init(maxRetries: Int, baseDelay: TimeInterval, maxDelay: TimeInterval) {
        self.maxRetries = maxRetries
        self.baseDelay = baseDelay
        self.maxDelay = maxDelay
    }

    /// 2 retries, 300ms base → ~300ms + 900ms (both with ±20% jitter).
    public static let `default` = RetryPolicy(
        maxRetries: 2,
        baseDelay: 0.300,
        maxDelay: 5.0
    )

    /// No retries; used from tests that want to assert single-shot behaviour.
    public static let none = RetryPolicy(maxRetries: 0, baseDelay: 0, maxDelay: 0)

    /// Compute the delay before attempt `attempt` (1-indexed).
    public func delay(forAttempt attempt: Int) -> TimeInterval {
        let exponential = baseDelay * pow(3.0, Double(attempt - 1))
        let capped = min(exponential, maxDelay)
        let jitter = Double.random(in: 0.8...1.2)
        return capped * jitter
    }
}

// MARK: - Helpers

/// Empty response sentinel for endpoints that return no body.
public struct EmptyResponse: Decodable, Sendable {
    public init() {}
}

/// Erases any `Encodable` into something JSONEncoder can handle when we
/// only know the concrete type at the call site.
struct AnyEncodable: Encodable, @unchecked Sendable {
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
