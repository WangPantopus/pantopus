//
//  SequencedURLProtocol.swift
//  PantopusTests
//
//  URLProtocol stub that pops one canned response per request from a FIFO
//  sequence — designed for retry tests where the client sees a different
//  response on each attempt.
//

import Foundation

final class SequencedURLProtocol: URLProtocol {
    struct Response {
        let status: Int
        let body: Data
        let headers: [String: String]

        static func status(_ code: Int, body: String, headers: [String: String] = [:]) -> Response {
            Response(status: code, body: Data(body.utf8), headers: headers)
        }
    }

    nonisolated(unsafe) static var sequence: [Response] = []
    nonisolated(unsafe) static var routeResponses: [String: [Response]] = [:]
    nonisolated(unsafe) static var capturedRequests: [URLRequest] = []

    private static let lock = NSLock()

    static func reset() {
        lock.lock()
        defer { lock.unlock() }
        sequence = []
        routeResponses = [:]
        capturedRequests = []
    }

    static func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [SequencedURLProtocol.self]
        config.urlCache = nil
        return URLSession(configuration: config)
    }

    override static func canInit(with _: URLRequest) -> Bool {
        true
    }

    override static func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func stopLoading() {}

    override func startLoading() {
        let response = Self.nextResponse(for: request)

        guard let url = request.url,
              let http = HTTPURLResponse(
                  url: url,
                  statusCode: response.status,
                  httpVersion: "HTTP/1.1",
                  headerFields: response.headers
              )
        else { return }
        client?.urlProtocol(self, didReceive: http, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: response.body)
        client?.urlProtocolDidFinishLoading(self)
    }

    private static func nextResponse(for request: URLRequest) -> Response {
        lock.lock()
        defer { lock.unlock() }
        capturedRequests.append(request)
        if let path = request.url?.path,
           var responses = routeResponses[path],
           !responses.isEmpty {
            let response = responses.removeFirst()
            routeResponses[path] = responses
            return response
        }
        if sequence.isEmpty {
            return Response.status(599, body: "{\"error\":\"no stubbed response\"}")
        }
        return sequence.removeFirst()
    }
}
