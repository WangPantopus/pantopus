// ============================================================
// POST / FEED TYPES
// Shared between web, mobile, and API packages
// ============================================================

export type PostType =
  // Place types
  | 'ask_local' | 'recommendation' | 'event' | 'lost_found'
  | 'alert' | 'deal' | 'local_update' | 'neighborhood_win' | 'visitor_guide'
  // Non-Place types
  | 'general' | 'personal_update' | 'announcement' | 'service_offer'
  | 'resources_howto' | 'progress_wins';

export type PostVisibility = 'public' | 'neighborhood' | 'followers' | 'private' | 'connections';
export type PostFormat = 'standard' | 'quick_pulse' | 'deep_dive' | 'shout_out' | 'show_and_tell';
export type LocationPrecision = 'exact_place' | 'approx_area' | 'neighborhood_only' | 'none';
export type VisibilityScope = 'neighborhood' | 'city' | 'radius' | 'global';
export type SafetyAlertKind =
  | 'theft' | 'vandalism' | 'suspicious' | 'hazard' | 'scam' | 'other'
  // v1.1 alert template kinds
  | 'road_hazard' | 'power_outage' | 'weather_damage' | 'missing_pet' | 'official_notice'
  | 'traffic' | 'infra_outage' | 'weather_env' | 'crime_incident' | 'public_safety';

// v1.1 Feed surfaces
export type FeedSurface = 'place' | 'following' | 'connections';

// v1.1 Distribution targets
export type DistributionTarget = 'place' | 'followers' | 'connections';

export type PostAs = 'personal' | 'business' | 'home';
export type Audience = 'connections' | 'followers' | 'network' | 'nearby' | 'saved_place' | 'household' | 'neighborhood' | 'target_area';
export type FeedScope = 'nearby' | 'following' | 'connections' | 'home' | 'saved-place';
export type TrustLevel = 'verified_resident' | 'verified_business' | 'visitor' | 'incoming_resident' | 'remote_viewer';
export type MapLayerType = 'post' | 'task' | 'offer' | 'business' | 'home';

export interface PostCreator {
  id: string;
  username: string;
  name?: string;
  first_name?: string;
  last_name?: string;
  profile_picture_url?: string;
  city?: string;
  state?: string;
}

export interface PostCommentAttachment {
  id: string;
  comment_id?: string | null;
  file_url: string;
  original_filename: string;
  mime_type: string;
  file_size: number;
  file_type?: string;
  created_at?: string;
}

export interface PostComment {
  id: string;
  post_id: string;
  user_id: string;
  comment: string;
  parent_comment_id?: string | null;
  like_count: number;
  is_edited: boolean;
  is_deleted: boolean;
  created_at: string;
  author?: PostCreator;
  attachments?: PostCommentAttachment[];
  userHasLiked?: boolean;
}

export interface PostingIdentity {
  type: 'personal' | 'business' | 'home';
  id: string;
  name: string;
  role?: string;
  profile_picture_url?: string;
}

export interface Post {
  id: string;
  user_id: string;
  home_id?: string | null;
  title?: string | null;
  content: string;
  media_urls: string[];
  media_types: string[];
  media_thumbnails?: string[];
  media_live_urls?: string[];
  post_type: PostType;
  post_format?: PostFormat;
  visibility: PostVisibility;
  location_precision?: LocationPrecision;
  visibility_scope?: VisibilityScope;
  tags?: string[];
  like_count: number;
  comment_count: number;
  share_count: number;
  save_count?: number;
  is_pinned: boolean;
  is_global_pin?: boolean;
  is_edited: boolean;
  is_archived: boolean;
  created_at: string;
  updated_at: string;
  // Feed redesign fields
  post_as?: PostAs;
  audience?: Audience;
  business_id?: string | null;
  business_author_id?: string | null;
  resolved_at?: string | null;
  archived_at?: string | null;
  archive_reason?: string | null;
  is_story?: boolean;
  story_expires_at?: string | null;
  deal_business_name?: string | null;
  // Location
  latitude?: number | null;
  longitude?: number | null;
  location_name?: string | null;
  location_address?: string | null;
  /** Whether the viewer has access to the exact location (always false for non-authors) */
  locationUnlocked?: boolean;
  distance_meters?: number | null;
  radius_miles?: number | null;
  // Event fields
  event_date?: string | null;
  event_end_date?: string | null;
  event_venue?: string | null;
  // Safety alert fields
  safety_alert_kind?: SafetyAlertKind | null;
  safety_happened_at?: string | null;
  safety_happened_end?: string | null;
  safety_behavior_description?: string | null;
  // Deal fields
  deal_expires_at?: string | null;
  // Lost & found fields
  lost_found_type?: 'lost' | 'found' | null;
  lost_found_contact_pref?: string | null;
  // Service offer fields
  service_category?: string | null;
  // Cross-surface references
  ref_listing_id?: string | null;
  ref_task_id?: string | null;
  // v1.1 distribution
  distribution_targets?: DistributionTarget[];
  gps_timestamp?: string | null;
  // v1.2 social layer fields
  purpose?: string | null;
  utility_score?: number;
  show_on_profile?: boolean;
  profile_visibility_scope?: 'public' | 'followers' | 'connections' | 'local_context' | 'hidden';
  is_visitor_post?: boolean;
  state?: 'open' | 'solved' | null;
  solved_at?: string | null;
  not_helpful_count?: number;
  // Relations
  creator?: PostCreator;
  business_author?: PostCreator | null;
  home?: { id: string; address?: string | null; city: string; state?: string } | null;
  userHasLiked?: boolean;
  userHasSaved?: boolean;
  userHasReposted?: boolean;
  comments?: PostComment[];
  // Organic match fields
  matched_business_ids?: string[] | null;
  matched_businesses_cache?: MatchedBusiness[] | null;
  // Cold-start seeded content
  is_seeded?: boolean;
  metadata?: {
    source?: string;
    cta?: string | null;
    fact_type?: string;
  } | null;
}

export interface MatchedBusiness {
  business_user_id: string;
  username: string;
  name: string;
  profile_picture_url?: string | null;
  categories: string[];
  average_rating: number | null;
  review_count: number;
  completed_gigs?: number;
  distance_miles?: number | null;
  neighbor_count?: number;
  is_new_business?: boolean;
  is_open_now?: boolean | null;
  cached_at?: string;
}
