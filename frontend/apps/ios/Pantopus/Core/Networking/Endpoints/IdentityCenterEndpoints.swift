//
//  IdentityCenterEndpoints.swift
//  Pantopus
//
//  Backend route paths use the legacy "identity-center" + "bridges"
//  names — we keep them on the wire (the migration is a breaking
//  change). UI strings the user sees say "Profiles & Privacy" and
//  "Profile links" per `docs/identity-firewall-ui-ux-redesign-2026-05-06.md`.
//

import Foundation

/// Endpoints under `/api/identity-center/*` and the persona surface.
public enum IdentityCenterEndpoints {
    /// `GET /api/identity-center` — every identity surface the
    /// signed-in user owns. Route `backend/routes/identityCenter.js:401`.
    public static let overview = Endpoint(method: .get, path: "/api/identity-center")

    /// `GET /api/identity-center/view-as` — privacy preview window.
    /// Route `backend/routes/identityCenter.js:489`.
    public static let viewAs = Endpoint(method: .get, path: "/api/identity-center/view-as")

    /// `PATCH /api/identity-center/bridges/:personaId` — toggle the
    /// "link these profiles" preferences. Route
    /// `backend/routes/identityCenter.js:516`.
    public static func updateBridges(personaId: String, body: UpdateBridgesBody) -> Endpoint {
        Endpoint(method: .patch, path: "/api/identity-center/bridges/\(personaId)", body: body)
    }
}

/// Body for `PATCH /api/identity-center/bridges/:personaId`. The
/// internal field names retain "persona" / "local" because they're
/// the schema column names; the UI labels them as "Public profile"
/// and "Local Profile".
public struct UpdateBridgesBody: Encodable, Sendable {
    public var showPersonaOnLocal: Bool?
    public var showLocalOnPersona: Bool?

    public init(showPersonaOnLocal: Bool? = nil, showLocalOnPersona: Bool? = nil) {
        self.showPersonaOnLocal = showPersonaOnLocal
        self.showLocalOnPersona = showLocalOnPersona
    }

    enum CodingKeys: String, CodingKey {
        case showPersonaOnLocal = "show_persona_on_local"
        case showLocalOnPersona = "show_local_on_persona"
    }
}
