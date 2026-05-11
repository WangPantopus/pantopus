//
//  MultipartUploader.swift
//  Pantopus
//
//  Single-file multipart/form-data upload helper. Builds the body
//  manually (no extra dependencies) and posts via `URLSession.upload`.
//  Used by P20 to push claim-ownership evidence to
//  `POST /api/files/upload` (`backend/routes/files.js:781`) before
//  registering the resulting URL via the evidence endpoint.
//

import Foundation
import Logging

/// One file part inside a multipart body.
public struct MultipartFile: Sendable {
    public let fieldName: String
    public let filename: String
    public let mimeType: String
    public let data: Data

    public init(fieldName: String, filename: String, mimeType: String, data: Data) {
        self.fieldName = fieldName
        self.filename = filename
        self.mimeType = mimeType
        self.data = data
    }
}

/// Posts `multipart/form-data` to `POST /api/files/upload`. Lives
/// outside `APIClient` because the client is JSON-first; multipart
/// requires a different body assembly path.
public final class MultipartUploader: @unchecked Sendable {
    /// Singleton wired in tests via the same `URLSession` injection
    /// pattern as `APIClient`.
    public static let shared = MultipartUploader()

    private let session: URLSession
    private let environment: AppEnvironment
    private let logger = Logger(label: "app.pantopus.ios.MultipartUploader")

    init(
        environment: AppEnvironment = .current,
        session: URLSession? = nil
    ) {
        self.environment = environment
        if let session {
            self.session = session
        } else {
            let config = URLSessionConfiguration.default
            config.timeoutIntervalForRequest = 60
            config.timeoutIntervalForResource = 120
            self.session = URLSession(configuration: config)
        }
    }

    /// Upload a single file to `POST /api/files/upload`. Optional form
    /// fields are sent alongside the file. Token is read from
    /// `AuthManager.shared`; if nil, the request goes out without an
    /// `Authorization` header so the server's 401 path can handle it
    /// uniformly with the rest of the client.
    public func uploadFile(
        _ file: MultipartFile,
        formFields: [String: String] = [:]
    ) async throws -> FileUploadResponse {
        let token = await AuthManager.shared.accessToken
        let boundary = "PantopusBoundary-\(UUID().uuidString)"
        let url = environment.apiBaseURL.appendingPathComponent("/api/files/upload")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let body = Self.buildBody(boundary: boundary, file: file, fields: formFields)
        let (data, response) = try await session.upload(for: request, from: body)

        guard let http = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        switch http.statusCode {
        case 200..<300:
            do {
                let decoder = JSONDecoder()
                return try decoder.decode(FileUploadResponse.self, from: data)
            } catch {
                logger.error("Multipart upload decode failed: \(error)")
                throw APIError.decoding(underlying: error)
            }
        case 401:
            await AuthManager.shared.handleUnauthorized()
            throw APIError.unauthorized
        case 413:
            throw APIError.clientError(status: 413, message: "File is too large.")
        case 415:
            throw APIError.clientError(status: 415, message: "Unsupported file type.")
        case 400..<500:
            let message = String(data: data, encoding: .utf8)
            throw APIError.clientError(status: http.statusCode, message: message)
        default:
            throw APIError.server(status: http.statusCode, body: String(data: data, encoding: .utf8) ?? "")
        }
    }

    /// Assemble a `multipart/form-data` body. Internal so tests can
    /// assert the exact byte layout without making a network call.
    static func buildBody(
        boundary: String,
        file: MultipartFile,
        fields: [String: String]
    ) -> Data {
        var body = Data()
        for (name, value) in fields.sorted(by: { $0.key < $1.key }) {
            body.append(Data("--\(boundary)\r\n".utf8))
            body.append(Data("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".utf8))
            body.append(Data("\(value)\r\n".utf8))
        }
        body.append(Data("--\(boundary)\r\n".utf8))
        body.append(
            Data("Content-Disposition: form-data; name=\"\(file.fieldName)\"; filename=\"\(file.filename)\"\r\n".utf8)
        )
        body.append(Data("Content-Type: \(file.mimeType)\r\n\r\n".utf8))
        body.append(file.data)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        return body
    }
}
