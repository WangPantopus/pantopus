//
//  SchedulingError.swift
//  Pantopus
//
//  Typed error surface for Calendarly. `APIClient` throws the app-wide
//  `APIError`; scheduling screens map it through `SchedulingError.from(_:)` to
//  get first-class handling of the booking-specific failure shapes:
//
//    • 409 conflict  → `{ error:'SLOT_TAKEN'|'SLOT_UNAVAILABLE'|'SLOT_FULL',
//                         message, alternatives:[{start,end,startLocal}] }`
//                      — surface the nearest open times, never a dead end.
//    • 400 validation→ `{ error:'Validation failed', details:[{field,message,code}] }`
//    • 501 (connect) → `{ error:'NOT_AVAILABLE', message }` — "coming soon".
//
//  `SchedulingStatus` models the first-class RESPONSE states
//  (paused/secret/unavailable/expired) that public surfaces render honestly —
//  these are NOT errors.
//

import Foundation

/// First-class states a public booking surface can be in. These are decoded
/// from a 200/`status` field — they are NOT errors and must be rendered
/// honestly (a paused page is friendly, not a failure screen).
public enum SchedulingStatus: String, Sendable, Hashable, CaseIterable, Decodable {
    case active
    case paused
    case secret
    case unavailable
    case expired
    /// Forward-compatible fallback for an unrecognised server value.
    case unknown

    public init(from decoder: any Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = SchedulingStatus(rawValue: raw) ?? .unknown
    }
}

/// One nearest-open-time the backend offers when the requested slot is gone.
/// Shape: `{ start, end, startLocal }` (camelCase in the JSON).
public struct SchedulingSlotAlternative: Decodable, Sendable, Hashable {
    /// ISO-8601 UTC.
    public let start: String
    /// ISO-8601 UTC.
    public let end: String
    /// Local ISO rendered in the requested tz (may be absent).
    public let startLocal: String?

    public init(start: String, end: String, startLocal: String? = nil) {
        self.start = start
        self.end = end
        self.startLocal = startLocal
    }
}

/// One field-level validation failure from a `400 { error:'Validation failed',
/// details:[...] }` envelope.
public struct SchedulingValidationDetail: Decodable, Sendable, Hashable {
    public let field: String?
    public let message: String?
    public let code: String?

    public init(field: String? = nil, message: String? = nil, code: String? = nil) {
        self.field = field
        self.message = message
        self.code = code
    }
}

/// The typed failure surface for scheduling calls.
public enum SchedulingError: Error, Sendable, Equatable {
    /// 409 with nearest-open-time `alternatives` (SLOT_TAKEN / SLOT_UNAVAILABLE
    /// / SLOT_FULL / SLOT_CONFLICT). Present the alternatives — never a dead end.
    case slotConflict(code: String, message: String?, alternatives: [SchedulingSlotAlternative])
    /// 409 that is not a slot conflict (e.g. PAGE_PAUSED, LINK_USED,
    /// CANNOT_DELETE_DEFAULT, HAS_UPCOMING_BOOKINGS, ALREADY_*).
    case conflict(code: String, message: String?)
    /// 400 validation failure with per-field `details`.
    case validation(message: String?, details: [SchedulingValidationDetail])
    /// 404 — resource / page / token not found or expired.
    case notFound(message: String?)
    /// 403 — authenticated but not permitted.
    case forbidden(message: String?)
    /// 401 — token missing / expired.
    case unauthorized
    /// 501 — a deferred feature (e.g. connected-calendar connect). Render
    /// "coming soon".
    case notImplemented(message: String?)
    /// 5xx (other than 501) after retries.
    case server(status: Int, message: String?)
    /// Network-layer failure (offline / timeout).
    case transport
    /// Response decoded into an unexpected shape.
    case decoding
    /// Anything else, with the server message if we have one.
    case unknown(message: String?)

    /// Map the app-wide `APIError` to a scheduling-typed error, re-decoding the
    /// 4xx/5xx body for `alternatives` / `details` / status code.
    ///
    /// - Parameters:
    ///   - apiError: the error thrown by `APIClient`.
    ///   - data: optional raw body. `APIError.clientError` already carries the
    ///     body string; pass `data` when you captured it separately (e.g. via
    ///     `requestData`) for 403/404 whose body `APIError` does not retain.
    public static func from(_ apiError: APIError, data: Data? = nil) -> SchedulingError {
        switch apiError {
        case .unauthorized:
            return .unauthorized
        case .forbidden:
            return .forbidden(message: decodeBody(data)?.message)
        case .notFound:
            return .notFound(message: decodeBody(data)?.message)
        case let .clientError(status, message):
            let bodyData = message.map { Data($0.utf8) } ?? data
            return classify(status: status, body: decodeBody(bodyData))
        case let .server(status, body):
            let parsed = decodeBody(Data(body.utf8))
            if status == 501 { return .notImplemented(message: parsed?.message) }
            return .server(status: status, message: parsed?.message)
        case .transport:
            return .transport
        case .decoding:
            return .decoding
        case .invalidURL, .invalidResponse, .retriesExhausted:
            return .unknown(message: apiError.errorDescription)
        }
    }

    // MARK: - Convenience projections

    /// Nearest open times when this is a slot conflict; empty otherwise.
    public var alternatives: [SchedulingSlotAlternative] {
        if case let .slotConflict(_, _, alternatives) = self { return alternatives }
        return []
    }

    /// Per-field validation details when this is a validation error.
    public var validationDetails: [SchedulingValidationDetail] {
        if case let .validation(_, details) = self { return details }
        return []
    }

    /// The backend `error` code when one was supplied (e.g. `SLOT_TAKEN`).
    public var code: String? {
        switch self {
        case let .slotConflict(code, _, _), let .conflict(code, _): code
        default: nil
        }
    }

    /// A best-effort user-facing message.
    public var userMessage: String? {
        switch self {
        case let .slotConflict(_, message, _),
             let .conflict(_, message),
             let .validation(message, _),
             let .notFound(message),
             let .forbidden(message),
             let .notImplemented(message),
             let .server(_, message),
             let .unknown(message):
            message
        case .unauthorized:
            "Your session has expired. Please sign in again."
        case .transport:
            "Can't reach Pantopus. Check your connection."
        case .decoding:
            "Received an unexpected response."
        }
    }

    // MARK: - Body parsing

    private struct ParsedBody {
        let error: String?
        let message: String?
        let status: String?
        let alternatives: [SchedulingSlotAlternative]
        let details: [SchedulingValidationDetail]
    }

    private static func classify(status: Int, body: ParsedBody?) -> SchedulingError {
        let code = body?.error ?? ""
        let message = body?.message
        let alternatives = body?.alternatives ?? []
        let details = body?.details ?? []

        if status == 409 || !alternatives.isEmpty {
            let slotCodes: Set<String> = ["SLOT_TAKEN", "SLOT_UNAVAILABLE", "SLOT_FULL", "SLOT_CONFLICT"]
            if slotCodes.contains(code) || !alternatives.isEmpty {
                return .slotConflict(
                    code: code.isEmpty ? "SLOT_CONFLICT" : code,
                    message: message,
                    alternatives: alternatives
                )
            }
            return .conflict(code: code, message: message)
        }
        if !details.isEmpty || code == "Validation failed" {
            return .validation(message: message, details: details)
        }
        if status == 404 { return .notFound(message: message) }
        if status == 403 { return .forbidden(message: message) }
        return .unknown(message: message ?? (code.isEmpty ? nil : code))
    }

    private static func decodeBody(_ data: Data?) -> ParsedBody? {
        guard let data, !data.isEmpty else { return nil }
        struct Raw: Decodable {
            let error: String?
            let message: String?
            let status: String?
            let alternatives: [SchedulingSlotAlternative]?
            let details: [SchedulingValidationDetail]?
        }
        guard let raw = try? JSONDecoder().decode(Raw.self, from: data) else { return nil }
        return ParsedBody(
            error: raw.error,
            message: raw.message,
            status: raw.status,
            alternatives: raw.alternatives ?? [],
            details: raw.details ?? []
        )
    }
}
