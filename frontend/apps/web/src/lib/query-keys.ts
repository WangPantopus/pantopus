// ============================================================
// QUERY KEY FACTORY
//
// Centralized cache key management for all React Query usage
// outside of mailbox (which keeps its own keys in mailbox-queries.ts).
//
// Convention: each factory returns a readonly tuple so keys are
// structurally typed and auto-completed.
// ============================================================

export const queryKeys = {
  // ── Hub ────────────────────────────────────────────────────
  hub: () => ['hub'] as const,
  hubToday: () => ['hub', 'today'] as const,

  // ── Feed ───────────────────────────────────────────────────
  feed: (surface: string, filter: string) =>
    ['feed', surface, filter] as const,

  // ── Gigs ───────────────────────────────────────────────────
  gigs: (filters: Record<string, unknown>) =>
    ['gigs', filters] as const,
  gigDetail: (id: string) => ['gigs', 'detail', id] as const,

  // ── Chat ───────────────────────────────────────────────────
  conversations: () => ['conversations'] as const,
  chatMessages: (roomId: string) =>
    ['chat', 'messages', roomId] as const,

  // ── Notifications ──────────────────────────────────────────
  notifications: () => ['notifications'] as const,

  // ── My Gigs ────────────────────────────────────────────────
  myGigs: (status?: string) =>
    ['myGigs', status] as const,

  // ── My Bids ────────────────────────────────────────────────
  myBids: (status?: string) =>
    ['myBids', status] as const,

  // ── Discover ───────────────────────────────────────────────
  discover: (scope: string, filters: Record<string, unknown>) =>
    ['discover', scope, filters] as const,

  // ── Relationships ──────────────────────────────────────────
  connections: () => ['connections'] as const,
  connectionRequests: (direction: 'pending' | 'sent') =>
    ['connectionRequests', direction] as const,
  blockedUsers: () => ['blockedUsers'] as const,

  // ── Marketplace ────────────────────────────────────────────
  marketplace: (mode: string, filters: Record<string, unknown>) =>
    ['marketplace', mode, filters] as const,
  listingDetail: (id: string) =>
    ['marketplace', 'listing', id] as const,

  // ── Posts ──────────────────────────────────────────────────
  postDetail: (id: string) => ['posts', 'detail', id] as const,

  // ── Profiles ──────────────────────────────────────────────
  profile: (username: string) =>
    ['profile', username] as const,

  // ── Homes ─────────────────────────────────────────────────
  homeDetail: (id: string) => ['homes', 'detail', id] as const,

  // ── Businesses ────────────────────────────────────────────
  businessDetail: (id: string) =>
    ['businesses', 'detail', id] as const,
} as const;
