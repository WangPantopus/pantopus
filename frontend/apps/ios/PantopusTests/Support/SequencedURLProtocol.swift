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
    nonisolated(unsafe) static var capturedRequests: [URLRequest] = []

    static func reset() {
        sequence = []
        capturedRequests = []
    }

    static func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [SequencedURLProtocol.self]
        config.urlCache = nil
        return URLSession(configuration: config)
    }

    override class func canInit(with _: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func stopLoading() {}

    override func startLoading() {
        Self.capturedRequests.append(request)
        let response: Response
        if Self.sequence.isEmpty {
            response = Response.status(599, body: "{\"error\":\"no stubbed response\"}")
        } else {
            response = Self.sequence.removeFirst()
        }

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
}
