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

    /// `POST /api/ai/draft/listing-vision` — Snap & Sell: draft a
    /// listing (title, description, category, condition, price) from
    /// item photos, with an optional comp-range price suggestion.
    /// Route `backend/routes/ai.js:199`.
    public static func draftListingVision(_ body: AIDraftListingVisionRequest) -> Endpoint {
        // 45s: the backend gives the vision model 30s
        // (`DRAFT_TIMEOUT_MS`, `backend/services/ai/agentService.js:30`)
        // and the multi-image upload itself takes a few seconds.
        Endpoint(method: .post, path: "/api/ai/draft/listing-vision", body: body, timeout: 45)
    }
}
