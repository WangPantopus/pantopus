//
//  URLProtocolStub.swift
//  PantopusTests
//
//  Intercepts all requests in tests so URLSession-based clients can be tested
//  without hitting the network. Register with:
//
//      let config = URLSessionConfiguration.ephemeral
//      config.protocolClasses = [URLProtocolStub.self]
//      let session = URLSession(configuration: config)
//

import Foundation

final class URLProtocolStub: URLProtocol {
    struct Response {
        let status: Int
        let body: Data
        let headers: [String: String]

        static func json(_ string: String, status: Int = 200) -> Response {
            Response(
                status: status,
                body: Data(string.utf8),
                headers: ["Content-Type": "application/json"]
            )
        }

        static let empty = Response(status: 204, body: Data(), headers: [:])
    }

    /// Request URL path → canned response. Matched by suffix.
    nonisolated(unsafe) static var stubs: [(pathSuffix: String, response: Response)] = []

    /// Captured requests in the order they were made.
    nonisolated(unsafe) static var capturedRequests: [URLRequest] = []

    static func reset() {
        stubs = []
        capturedRequests = []
    }

    static func stub(path: String, response: Response) {
        stubs.append((path, response))
    }

    // MARK: - URLProtocol

    override class func canInit(with _: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func stopLoading() {}

    override func startLoading() {
        Self.capturedRequests.append(request)

        let path = request.url?.path ?? ""
        let match = Self.stubs.first { path.hasSuffix($0.pathSuffix) }

        let resp: Response
        if let match {
            resp = match.response
        } else {
            resp = Response(status: 404, body: Data("{\"error\":\"no stub\"}".utf8), headers: [:])
        }

        guard let url = request.url,
              let httpResponse = HTTPURLResponse(
                  url: url,
                  statusCode: resp.status,
                  httpVersion: "HTTP/1.1",
                  headerFields: resp.headers
              )
        else { return }

        client?.urlProtocol(self, didReceive: httpResponse, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: resp.body)
        client?.urlProtocolDidFinishLoading(self)
    }
}

enum TestSession {
    /// Build an ephemeral URLSession with URLProtocolStub installed.
    static func make() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [URLProtocolStub.self]
        return URLSession(configuration: config)
    }
}
