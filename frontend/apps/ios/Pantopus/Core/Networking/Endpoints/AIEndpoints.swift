//
//  AIEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/ai.js`. Mounted at `/api/ai`.
//  The chat stream itself (`POST /api/ai/chat`, SSE) is consumed by
//  `AIChatStreamClient` directly — only the JSON routes live here.
//

import Foundation

/// Endpoints under `/api/ai/*` consumed by the Ask-Pantopus thread.
public enum AIEndpoints {
    /// `GET /api/ai/conversations` — the caller's AI conversation list,
    /// newest-updated first (server caps at 50). Summaries only — no
    /// per-conversation messages endpoint exists (message history lives
    /// in the provider's `previous_response_id` state, not our DB).
    /// Route `backend/routes/ai.js:358`.
    public static func conversations() -> Endpoint {
        Endpoint(method: .get, path: "/api/ai/conversations")
    }

    /// `DELETE /api/ai/conversations/:id` — delete one AI conversation.
    /// Route `backend/routes/ai.js:363`.
    public static func deleteConversation(id: String) -> Endpoint {
        Endpoint(method: .delete, path: "/api/ai/conversations/\(id)")
    }
}
