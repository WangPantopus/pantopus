// ============================================================
// IDENTITY FIREWALL TYPES
// Based on backend/database/migrations/20260301000001_identity_firewall_tables.sql
// Tables: BusinessSeat, SeatBinding, UserPrivacySettings, UserProfileBlock
// ============================================================

// ─── Enums ──────────────────────────────────────────────────

export type SeatInviteStatus = 'pending' | 'accepted' | 'declined' | 'expired';

export type SeatBindingMethod = 'invite_accept' | 'owner_bootstrap' | 'migration';

export type SearchVisibilityLevel = 'everyone' | 'mutuals' | 'nobody';

export type ProfileVisibilityLevel = 'public' | 'followers' | 'private';

export type BlockScopeType = 'full' | 'search_only' | 'business_context';

export type NotificationContextType = 'personal' | 'business';

export type BusinessRoleBase = 'owner' | 'admin' | 'editor' | 'staff' | 'viewer';

// ─── BusinessSeat ───────────────────────────────────────────

/** A seat is an opaque business identity — never reveals the real user behind it */
export interface BusinessSeat {
  id: string;
  business_user_id: string;
  display_name: string;
  display_avatar_file_id?: string | null;
  role_base: BusinessRoleBase;
  contact_method?: string | null;
  is_active: boolean;
  invited_by_seat_id?: string | null;
  invite_token_hash?: string | null;
  invite_email?: string | null;
  invite_status: SeatInviteStatus;
  accepted_at?: string | null;
  deactivated_at?: string | null;
  deactivated_reason?: string | null;
  notes?: string | null;
  created_at: string;
  updated_at: string;
}

/** Seat list view — compact fields for the team panel */
export interface SeatListItem {
  id: string;
  business_user_id: string;
  display_name: string;
  display_avatar_file_id?: string | null;
  role_base: BusinessRoleBase;
  title?: string | null;
  contact_method?: string | null;
  is_active: boolean;
  invite_status: SeatInviteStatus;
  invite_email?: string | null;
  accepted_at?: string | null;
  created_at: string;
  updated_at: string;
  /** True if this seat belongs to the viewer */
  is_you?: boolean;
}

/** Seat detail view — all fields */
export interface SeatDetail extends BusinessSeat {
  /** True if this seat belongs to the viewer */
  is_you?: boolean;
}

// ─── MySeat — returned from GET /my-seats ────────────────────

export interface MySeat {
  seat_id: string;
  business_user_id: string;
  business_name: string;
  business_username: string;
  business_logo_file_id?: string | null;
  business_type?: string | null;
  display_name: string;
  display_avatar_file_id?: string | null;
  role_base: BusinessRoleBase;
  contact_method?: string | null;
}

// ─── Invite Details — returned from GET /seats/invite-details ─

export interface InviteDetails {
  seat_id: string;
  business: {
    id: string;
    username?: string;
    name?: string;
  };
  display_name: string;
  role_base: BusinessRoleBase;
  invite_email?: string | null;
  created_at: string;
}

// ─── UserPrivacySettings ────────────────────────────────────

export interface UserPrivacySettings {
  user_id: string;
  search_visibility: SearchVisibilityLevel;
  findable_by_email: boolean;
  findable_by_phone: boolean;
  profile_default_visibility: ProfileVisibilityLevel;
  show_gig_history: ProfileVisibilityLevel;
  show_neighborhood: ProfileVisibilityLevel;
  show_home_affiliation: ProfileVisibilityLevel;
  created_at: string;
  updated_at: string;
}

export interface UpdatePrivacySettingsPayload {
  search_visibility?: SearchVisibilityLevel;
  findable_by_email?: boolean;
  findable_by_phone?: boolean;
  profile_default_visibility?: ProfileVisibilityLevel;
  show_gig_history?: ProfileVisibilityLevel;
  show_neighborhood?: ProfileVisibilityLevel;
  show_home_affiliation?: ProfileVisibilityLevel;
}

// ─── UserProfileBlock ───────────────────────────────────────

export interface UserProfileBlock {
  id: string;
  blocked_user_id: string;
  block_scope: BlockScopeType;
  reason?: string | null;
  created_at: string;
  blocked?: {
    id: string;
    username: string;
    name?: string;
    profile_picture_url?: string | null;
  };
}

export interface CreateBlockPayload {
  blocked_user_id: string;
  block_scope?: BlockScopeType;
  reason?: string;
}

// ─── Seat Invite Payloads ───────────────────────────────────

export interface CreateSeatInvitePayload {
  display_name: string;
  role_base?: BusinessRoleBase;
  invite_email?: string;
  title?: string;
  contact_method?: string;
  notes?: string;
}

export interface AcceptInvitePayload {
  token: string;
  display_name?: string;
}

export interface DeclineInvitePayload {
  token: string;
}

export interface UpdateSeatPayload {
  display_name?: string;
  role_base?: BusinessRoleBase;
  title?: string;
  contact_method?: string;
  notes?: string;
}

// ─── Notification (extended) ────────────────────────────────

export interface NotificationWithContext {
  id: string;
  user_id: string;
  type: string;
  title: string;
  body?: string;
  icon: string;
  link?: string;
  is_read: boolean;
  metadata: Record<string, any>;
  created_at: string;
  context_type?: NotificationContextType;
  context_id?: string | null;
}
