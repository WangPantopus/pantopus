//
//  JSONValue.swift
//  Pantopus
//
//  Escape hatch for response fields whose shape is provider-dependent or
//  untyped at the backend (e.g. the `today` payload from the hub/today
//  endpoint, ATTOM property bundles, S3-sourced mail object payloads).
//  Prefer a typed DTO whenever the route commits to a stable shape.
//

import Foundation

/// A dynamic JSON value we can losslessly round-trip through `Codable`.
public indirect enum JSONValue: Decodable, Sendable, Hashable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: JSONValue])
    case array([JSONValue])
    case null

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let value = try? container.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? container.decode(Double.self) {
            self = .number(value)
        } else if let value = try? container.decode(String.self) {
            self = .string(value)
        } else if let value = try? container.decode([String: JSONValue].self) {
            self = .object(value)
        } else if let value = try? container.decode([JSONValue].self) {
            self = .array(value)
        } else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unknown JSON value"
            )
        }
    }

    /// String value if this case is `.string`, otherwise nil.
    public var stringValue: String? { if case .string(let v) = self { return v } else { return nil } }
    /// Numeric value if this case is `.number`, otherwise nil.
    public var numberValue: Double? { if case .number(let v) = self { return v } else { return nil } }
    /// Boolean value if this case is `.bool`, otherwise nil.
    public var boolValue: Bool? { if case .bool(let v) = self { return v } else { return nil } }
}
