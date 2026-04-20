/**
 * feedService.js — Unified feed query logic for list and map views.
 *
 * All feed surfaces (place, following, connections) share the same
 * query pipeline: author resolution → post query → normalize →
 * mute/hide/block → politics filter → sort/trim → enrich.
 */

const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');
const { applyLocationPrecision, leastPrecise } = require('../utils/locationPrivacy');
const s3 = require('./s3Service');

// ---------------------------------------------------------------------------
// In-memory cache for mute/hide filters (60-second TTL)
// ---------------------------------------------------------------------------

const _filterCache = new Map(); // key: userId, value: { data, expires }
const FILTER_CACHE_TTL = 60_000; // 60 seconds

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const FEED_SURFACES = ['place', 'following', 'connections'];

// ---------------------------------------------------------------------------
// Global pins — posts that appear at the top of every feed for every user,
// bypassing location and social-graph filters.
// Short TTL cache so the query doesn't run on every single feed request.
// ---------------------------------------------------------------------------

let _globalPinsCache = { data: null, expires: 0 };
const GLOBAL_PINS_TTL = 60_000; // 60 seconds

async function getGlobalPins() {
  const now = Date.now();
  if (_globalPinsCache.data && now < _globalPinsCache.expires) {
    return _globalPinsCache.data;
  }

  const { data, error } = await supabaseAdmin
    .from('Post')
    .select(FEED_POST_SELECT)
    .eq('is_global_pin', true)
    .is('archived_at', null)
    .order('created_at', { ascending: false })
    .limit(10);

  if (error) {
    logger.error('Failed to fetch global pins', { error: error.message });
    return [];
  }

  const pins = (data || []).map(r => normalizeFeedPostRow(r));
  _globalPinsCache = { data: pins, expires: now + GLOBAL_PINS_TTL };
  return pins;
}

const CREATOR_SELECT = `id, username, name, first_name, last_name, profile_picture_url, city, state, account_type`;

const FEED_POST_SELECT = `
  id, user_id, title, content, media_urls, media_types, media_live_urls, post_type, post_format,
  visibility, visibility_scope, location_precision, tags,
  like_count, comment_count, share_count, save_count, is_pinned, is_global_pin, is_edited, created_at, updated_at,
  home_id, latitude, longitude, effective_latitude, effective_longitude, location_name, location_address,
  post_as, audience, business_id, business_author_id, target_place_id,
  resolved_at, archived_at, archive_reason, is_story, story_expires_at,
  distribution_targets,
  event_date, event_end_date, event_venue,
  safety_alert_kind, safety_happened_at, safety_behavior_description,
  deal_expires_at, deal_business_name,
  lost_found_type, lost_found_contact_pref,
  service_category, ref_listing_id, ref_task_id,
  purpose, utility_score, show_on_profile, profile_visibility_scope,
  is_visitor_post, state, solved_at, not_helpful_count,
  creator:user_id (${CREATOR_SELECT}),
  business_author:business_author_id (${CREATOR_SELECT}),
  home:home_id (id, address, city)
`;

// ---------------------------------------------------------------------------
// Media URL helpers
// ---------------------------------------------------------------------------

function resolveStoredMediaUrl(url) {
  const cleanUrl = typeof url === 'string' ? url.trim() : '';
  if (!cleanUrl) return '';
  if (/^(https?:)?\/\//i.test(cleanUrl)) return cleanUrl;
  if (/^(data:|blob:)/i.test(cleanUrl)) return cleanUrl;
  return s3.getPublicUrl(cleanUrl.replace(/^\/+/, ''));
}

function normalizeMediaUrls(mediaUrls) {
  if (!Array.isArray(mediaUrls)) return [];
  return mediaUrls.map(resolveStoredMediaUrl).filter(Boolean);
}

// ---------------------------------------------------------------------------
// Row normalizer (direct-query rows → frontend shape)
// ---------------------------------------------------------------------------

function normalizeFeedPostRow(row, likedPostIds = new Set(), savedPostIds = new Set()) {
  return {
    id: row.id,
    user_id: row.user_id,
    title: row.title || null,
    content: row.content,
    media_urls: normalizeMediaUrls(row.media_urls),
    media_types: row.media_types || [],
    media_live_urls: normalizeMediaUrls(row.media_live_urls),
    post_type: row.post_type || 'general',
    post_format: row.post_format || 'standard',
    visibility: row.visibility || 'neighborhood',
    visibility_scope: row.visibility_scope || 'neighborhood',
    location_precision: row.location_precision || 'approx_area',
    tags: row.tags || [],
    like_count: row.like_count || 0,
    comment_count: row.comment_count || 0,
    share_count: row.share_count || 0,
    save_count: row.save_count || 0,
    is_pinned: row.is_pinned || false,
    is_global_pin: row.is_global_pin || false,
    is_edited: row.is_edited || false,
    is_archived: !!row.archived_at,
    created_at: row.created_at,
    updated_at: row.updated_at || row.created_at,
    userHasLiked: likedPostIds.has(row.id),
    userHasSaved: savedPostIds.has(row.id),
    userHasReposted: false,
    home_id: row.home_id || null,
    latitude: row.latitude || null,
    longitude: row.longitude || null,
    effective_latitude: row.effective_latitude || null,
    effective_longitude: row.effective_longitude || null,
    location_name: row.location_name || null,
    distance_meters: null,
    post_as: row.post_as || 'personal',
    audience: row.audience || 'nearby',
    business_id: row.business_id || null,
    target_place_id: row.target_place_id || null,
    resolved_at: row.resolved_at || null,
    archived_at: row.archived_at || null,
    archive_reason: row.archive_reason || null,
    is_story: row.is_story || false,
    story_expires_at: row.story_expires_at || null,
    distribution_targets: row.distribution_targets || [],
    event_date: row.event_date || null,
    event_end_date: row.event_end_date || null,
    event_venue: row.event_venue || null,
    safety_alert_kind: row.safety_alert_kind || null,
    safety_happened_at: row.safety_happened_at || null,
    safety_behavior_description: row.safety_behavior_description || null,
    deal_expires_at: row.deal_expires_at || null,
    deal_business_name: row.deal_business_name || null,
    lost_found_type: row.lost_found_type || null,
    lost_found_contact_pref: row.lost_found_contact_pref || null,
    service_category: row.service_category || null,
    ref_listing_id: row.ref_listing_id || null,
    ref_task_id: row.ref_task_id || null,
    purpose: row.purpose || null,
    utility_score: row.utility_score || 0,
    show_on_profile: row.show_on_profile !== false,
    profile_visibility_scope: row.profile_visibility_scope || 'public',
    is_visitor_post: row.is_visitor_post || false,
    state: row.state || 'open',
    solved_at: row.solved_at || null,
    not_helpful_count: row.not_helpful_count || 0,
    creator: row.creator || null,
    home: row.home || null,
  };
}

function applyPostLocationPrivacy(post, viewerUserId) {
  if (!post) return post;

  const isAuthor = post.user_id === viewerUserId;
  const effectivePrecision = isAuthor
    ? (post.location_precision || 'approx_area')
    : leastPrecise(post.location_precision || 'approx_area', 'approx_area');

  applyLocationPrecision(post, effectivePrecision, isAuthor);

  if (!isAuthor) {
    post.locationUnlocked = false;
    if (post.home) {
      post.home = {
        ...post.home,
        address: null,
      };
    }
  }

  return post;
}

function applyPostLocationPrivacyBatch(posts, viewerUserId) {
  return posts.map((post) => applyPostLocationPrivacy(post, viewerUserId));
}

// ---------------------------------------------------------------------------
// Mute / hide / block filters
// ---------------------------------------------------------------------------

async function getMuteAndHideFilters(userId) {
  const cached = _filterCache.get(userId);
  if (cached && cached.expires > Date.now()) {
    return cached.data;
  }

  const [{ data: mutes }, { data: hides }, { data: blocks }, { data: feedPrefs }] = await Promise.all([
    supabaseAdmin.from('PostMute').select('muted_entity_id, muted_entity_type, surface').eq('user_id', userId),
    supabaseAdmin.from('PostHide').select('post_id').eq('user_id', userId),
    supabaseAdmin.from('Relationship')
      .select('requester_id, addressee_id')
      .eq('status', 'blocked')
      .or(`requester_id.eq.${userId},addressee_id.eq.${userId}`),
    supabaseAdmin.from('UserFeedPreference').select('*').eq('user_id', userId).maybeSingle(),
  ]);

  const blockedUserIds = new Set();
  if (blocks) {
    for (const b of blocks) {
      if (b.requester_id === userId) blockedUserIds.add(b.addressee_id);
      else blockedUserIds.add(b.requester_id);
    }
  }

  // Build surface-scoped topic mute sets
  const topicMutes = (mutes || []).filter(m => m.muted_entity_type === 'topic');
  const globalMutedTopics = new Set(topicMutes.filter(m => !m.surface).map(m => m.muted_entity_id));
  const mutedTopicsBySurface = new Map();
  for (const m of topicMutes) {
    if (m.surface) {
      if (!mutedTopicsBySurface.has(m.surface)) mutedTopicsBySurface.set(m.surface, new Set());
      mutedTopicsBySurface.get(m.surface).add(m.muted_entity_id);
    }
  }

  const result = {
    mutedUserIds: new Set((mutes || []).filter(m => m.muted_entity_type === 'user').map(m => m.muted_entity_id)),
    mutedBusinessIds: new Set((mutes || []).filter(m => m.muted_entity_type === 'business').map(m => m.muted_entity_id)),
    hiddenPostIds: new Set((hides || []).map(h => h.post_id)),
    blockedUserIds,
    globalMutedTopics,
    mutedTopicsBySurface,
    feedPreferences: feedPrefs || {
      hide_deals_place: false, hide_alerts_place: false,
      show_politics_following: false, show_politics_connections: false, show_politics_place: false,
    },
  };

  _filterCache.set(userId, { data: result, expires: Date.now() + FILTER_CACHE_TTL });
  return result;
}

function invalidateFilterCache(userId) {
  _filterCache.delete(userId);
}

function applyMuteHideFilters(posts, filters, surface, viewerUserId = null) {
  const dealTypes = ['deal'];
  const alertTypes = ['alert'];
  const isPlace = surface === 'place';

  return posts.filter(p => {
    const isOwnPost = viewerUserId != null && p.user_id === viewerUserId;
    if (filters.hiddenPostIds.has(p.id)) return false;
    if (!isOwnPost) {
      if (filters.mutedUserIds.has(p.user_id)) return false;
      if (p.business_id && filters.mutedBusinessIds.has(p.business_id)) return false;
      if (filters.blockedUserIds && filters.blockedUserIds.has(p.user_id)) return false;
      if (filters.globalMutedTopics && filters.globalMutedTopics.has(p.post_type)) return false;
      if (filters.mutedTopicsBySurface) {
        const surfaceTopics = filters.mutedTopicsBySurface.get(surface);
        if (surfaceTopics && surfaceTopics.has(p.post_type)) return false;
      }
      // Place-specific preference hiding — only apply on the Place surface
      if (isPlace && filters.feedPreferences?.hide_deals_place && dealTypes.includes(p.post_type)) return false;
      if (isPlace && filters.feedPreferences?.hide_alerts_place && alertTypes.includes(p.post_type)) return false;
    }
    return true;
  });
}

// ---------------------------------------------------------------------------
// Enrichment (like / save status)
// ---------------------------------------------------------------------------

// NOTE: This function applies location privacy via applyPostLocationPrivacy.
// Callers should NOT also call applyPostLocationPrivacy/applyPostLocationPrivacyBatch.
async function enrichWithUserStatus(posts, userId) {
  if (!posts.length) return posts;
  const postIds = posts.map(p => p.id);
  const [{ data: likes }, { data: saves }, { data: reposts }] = await Promise.all([
    supabaseAdmin.from('PostLike').select('post_id').eq('user_id', userId).in('post_id', postIds),
    supabaseAdmin.from('PostSave').select('post_id').eq('user_id', userId).in('post_id', postIds),
    supabaseAdmin.from('PostShare').select('post_id').eq('user_id', userId).eq('share_type', 'repost').in('post_id', postIds),
  ]);
  const likedSet = new Set((likes || []).map(r => r.post_id));
  const savedSet = new Set((saves || []).map(r => r.post_id));
  const repostSet = new Set((reposts || []).map(r => r.post_id));
  return posts.map(p => {
    applyPostLocationPrivacy(p, userId);
    return {
      ...p,
      userHasLiked: likedSet.has(p.id),
      userHasSaved: savedSet.has(p.id),
      userHasReposted: repostSet.has(p.id),
    };
  });
}

// ---------------------------------------------------------------------------
// Politics filter
// ---------------------------------------------------------------------------

function isPoliticsPost(post) {
  return (post.tags || []).some(t => ['politics', 'political'].includes(t.toLowerCase()))
    || (post.purpose === 'story' && (post.tags || []).includes('politics'));
}

// ---------------------------------------------------------------------------
// Cursor pagination helpers
// ---------------------------------------------------------------------------

function applyCursorCondition(query, cursorCreatedAt, cursorId) {
  if (!cursorCreatedAt || !cursorId) return query;
  return query.or(
    `created_at.lt.${cursorCreatedAt},and(created_at.eq.${cursorCreatedAt},id.lt.${cursorId})`
  );
}

function applyPinnedCursorCondition(query, cursorCreatedAt, cursorId, cursorIsPinned) {
  if (!cursorCreatedAt || !cursorId || cursorIsPinned == null) {
    return applyCursorCondition(query, cursorCreatedAt, cursorId);
  }

  if (cursorIsPinned) {
    return query.or(
      [
        'is_pinned.eq.false',
        `and(is_pinned.eq.true,created_at.lt.${cursorCreatedAt})`,
        `and(is_pinned.eq.true,created_at.eq.${cursorCreatedAt},id.lt.${cursorId})`,
      ].join(',')
    );
  }

  return query.or(
    [
      `and(is_pinned.eq.false,created_at.lt.${cursorCreatedAt})`,
      `and(is_pinned.eq.false,created_at.eq.${cursorCreatedAt},id.lt.${cursorId})`,
    ].join(',')
  );
}

async function getCursorPinState(cursorId) {
  if (!cursorId) return null;
  const { data, error } = await supabaseAdmin
    .from('Post')
    .select('id, is_pinned')
    .eq('id', cursorId)
    .maybeSingle();

  if (error || !data) return null;
  return !!data.is_pinned;
}

function buildCursorPagination(posts, limit, preSliceCount) {
  // If preSliceCount is provided, use it to determine hasMore (the count
  // of posts that survived filtering before trimming to limit).
  // Otherwise fall back to comparing the returned array length.
  const hasMore = preSliceCount != null ? preSliceCount > limit : posts.length >= limit;
  const lastPost = posts.length > 0 ? posts[posts.length - 1] : null;
  return {
    nextCursor: lastPost ? { createdAt: lastPost.created_at, id: lastPost.id } : null,
    hasMore,
  };
}

// ---------------------------------------------------------------------------
// Author-graph loaders (Following / Connections)
// ---------------------------------------------------------------------------

async function loadFollowingIds(userId) {
  const { data: follows, error } = await supabaseAdmin
    .from('UserFollow')
    .select('following_id')
    .eq('follower_id', userId);

  if (error) {
    throw new Error(`Failed to load following graph: ${error.message}`);
  }
  return (follows || []).map(f => f.following_id).filter(Boolean);
}

async function loadConnectionIds(userId) {
  const { data: rels, error } = await supabaseAdmin
    .from('Relationship')
    .select('requester_id, addressee_id')
    .eq('status', 'accepted')
    .or(`requester_id.eq.${userId},addressee_id.eq.${userId}`);

  if (error) {
    throw new Error(`Failed to load connection graph: ${error.message}`);
  }
  return (rels || []).map(r =>
    r.requester_id === userId ? r.addressee_id : r.requester_id
  ).filter(Boolean);
}

// ---------------------------------------------------------------------------
// Bounding-box helper
// ---------------------------------------------------------------------------

function boundingBoxFromCenter(lat, lng, radiusMeters) {
  const latDelta = radiusMeters / 111000;
  const lngDelta = radiusMeters / (111000 * Math.cos(lat * Math.PI / 180));
  return {
    south: lat - latDelta,
    north: lat + latDelta,
    west: lng - lngDelta,
    east: lng + lngDelta,
  };
}

/**
 * Haversine distance in meters between two lat/lng points.
 */
function haversineMeters(lat1, lng1, lat2, lng2) {
  const R = 6_371_000; // Earth radius in meters
  const toRad = (d) => d * Math.PI / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

// ---------------------------------------------------------------------------
// Surface-specific politics filter key
// ---------------------------------------------------------------------------

function politicsPrefKey(surface) {
  if (surface === 'following') return 'show_politics_following';
  if (surface === 'connections') return 'show_politics_connections';
  return 'show_politics_place';
}

// ---------------------------------------------------------------------------
// getListFeed
// ---------------------------------------------------------------------------

/**
 * Unified list-feed query.
 *
 * @param {Object} opts
 * @param {string} opts.userId
 * @param {string} opts.surface        – 'place' | 'following' | 'connections'
 * @param {number} [opts.latitude]
 * @param {number} [opts.longitude]
 * @param {number} [opts.radiusMeters] – defaults to 16 000 m
 * @param {string} [opts.postType]
 * @param {string} [opts.cursorCreatedAt]
 * @param {string} [opts.cursorId]
 * @param {number} [opts.limit]        – defaults to 20
 * @param {string[]} [opts.tags]
 * @returns {Promise<{ posts: object[], pagination: object }>}
 */
async function getListFeed({
  userId,
  surface,
  latitude,
  longitude,
  radiusMeters = 16000,
  postType,
  cursorCreatedAt,
  cursorId,
  limit = 20,
  tags,
}) {
  // 1. Validate surface
  if (!FEED_SURFACES.includes(surface)) {
    throw new Error(`Invalid feed surface: ${surface}`);
  }

  const parsedLimit = Number(limit) || 20;
  const overFetchLimit = parsedLimit + 20;
  const initialCursorPinned = await getCursorPinState(cursorId);

  // 2–4. Resolve author IDs and distribution target per surface
  let authorIds = null;
  let distributionTarget;

  if (surface === 'place') {
    distributionTarget = 'place';
    if (latitude == null || longitude == null) {
      return {
        posts: [],
        pagination: { nextCursor: null, hasMore: false },
        requiresViewingLocation: true,
        message: 'Set an area to see local posts.',
      };
    }
  } else if (surface === 'following') {
    distributionTarget = 'followers';
    authorIds = await loadFollowingIds(userId);
    if (!authorIds.length) {
      return { posts: [], pagination: { nextCursor: null, hasMore: false }, emptyGraph: true };
    }
  } else {
    // connections
    distributionTarget = 'connections';
    authorIds = await loadConnectionIds(userId);
    if (!authorIds.length) {
      return { posts: [], pagination: { nextCursor: null, hasMore: false }, emptyGraph: true };
    }
  }

  // Pre-compute filters once and reuse across fetch iterations
  const filters = await getMuteAndHideFilters(userId);
  const filterPolitics = !filters.feedPreferences?.[politicsPrefKey(surface)];

  // Pre-compute bounding box and center for place surface
  let box, centerLat, centerLng;
  if (surface === 'place') {
    centerLat = parseFloat(latitude);
    centerLng = parseFloat(longitude);
    box = boundingBoxFromCenter(centerLat, centerLng, radiusMeters);
  }

  // Fetch loop: keep fetching until we have enough visible posts or the
  // DB is exhausted. This prevents premature "all caught up" when many
  // consecutive posts are filtered by mute/hide/politics rules.
  const MAX_ROUNDS = 5;
  let accumulated = [];
  let loopCursorCreatedAt = cursorCreatedAt;
  let loopCursorId = cursorId;
  let loopCursorPinned = initialCursorPinned;
  let dbExhausted = false;

  for (let round = 0; round < MAX_ROUNDS && accumulated.length < parsedLimit && !dbExhausted; round++) {
    // 5. Build base query
    let query = supabaseAdmin
      .from('Post')
      .select(FEED_POST_SELECT)
      .is('archived_at', null)
      .order('is_pinned', { ascending: false })
      .order('created_at', { ascending: false })
      .order('id', { ascending: false })
      .limit(overFetchLimit);

    // Distribution target filter
    if (surface === 'place') {
      query = query.contains('distribution_targets', ['place']);
    } else {
      query = query
        .in('user_id', authorIds)
        .overlaps('distribution_targets', [distributionTarget]);
    }

    // Place bounding-box filter
    // Use OR to fall back to latitude/longitude when effective_* columns are NULL
    // (can happen if PostgREST schema cache is stale after migration 20260308130000)
    if (surface === 'place') {
      query = query.or(
        `and(effective_latitude.gte.${box.south},effective_latitude.lte.${box.north},effective_longitude.gte.${box.west},effective_longitude.lte.${box.east}),` +
        `and(effective_latitude.is.null,latitude.gte.${box.south},latitude.lte.${box.north},longitude.gte.${box.west},longitude.lte.${box.east})`
      );
    }

    // 6. postType filter
    if (postType) query = query.eq('post_type', postType);

    // 7. tags filter
    if (tags?.length) query = query.overlaps('tags', tags);

    // Cursor pagination
    query = applyPinnedCursorCondition(query, loopCursorCreatedAt, loopCursorId, loopCursorPinned);

    const { data, error } = await query;
    if (error) {
      logger.error('Feed query failed', { error: error.message, userId, surface });
      throw new Error(`Feed query failed: ${error.message}`);
    }

    const rawRows = data || [];
    if (rawRows.length < overFetchLimit) {
      dbExhausted = true;
    }

    // 8. Normalize
    let posts = rawRows.map(r => normalizeFeedPostRow(r, new Set(), new Set()));

    // 8b. Haversine distance filter for place surface (bounding box is a
    // square approximation; this removes posts outside the true radius)
    if (surface === 'place') {
      posts = posts.filter(p => {
        const lat = p.latitude ?? p.effective_latitude;
        const lng = p.longitude ?? p.effective_longitude;
        if (lat == null || lng == null) return true;
        const dist = haversineMeters(centerLat, centerLng, lat, lng);
        p.distance_meters = Math.round(dist);
        return dist <= radiusMeters;
      });
    }

    // 9. Mute / hide / block
    posts = applyMuteHideFilters(posts, filters, surface, userId);

    // 10. Politics filtering
    if (filterPolitics) {
      posts = posts.filter(p => !isPoliticsPost(p));
    }

    accumulated.push(...posts);

    // Advance cursor to the last raw row for the next round
    if (rawRows.length > 0) {
      const lastRaw = rawRows[rawRows.length - 1];
      loopCursorCreatedAt = lastRaw.created_at;
      loopCursorId = lastRaw.id;
      loopCursorPinned = !!lastRaw.is_pinned;
    }
  }

  // 11. Trim to limit. Query order already matches the display order:
  // pinned first, then newest-first within each pin bucket.
  const hasMore = !dbExhausted || accumulated.length > parsedLimit;
  let posts = accumulated.slice(0, parsedLimit);

  // 12. Prepend global pins on the first page (no cursor = first page).
  // These bypass all surface/location/author filters.
  const isFirstPage = !cursorCreatedAt && !cursorId;
  if (isFirstPage) {
    const globalPins = await getGlobalPins();
    if (globalPins.length) {
      // Deduplicate — a global pin might also match the regular query
      const regularIds = new Set(posts.map(p => p.id));
      const uniquePins = globalPins.filter(p => !regularIds.has(p.id));
      posts = [...uniquePins, ...posts];
    }
  }

  // 13. Enrich with like/save status
  const enriched = await enrichWithUserStatus(posts, userId);

  // 14–15. Build pagination and return
  const lastPost = enriched.length > 0 ? enriched[enriched.length - 1] : null;
  return {
    posts: enriched,
    pagination: {
      nextCursor: lastPost ? { createdAt: lastPost.created_at, id: lastPost.id } : null,
      hasMore,
    },
  };
}

// ---------------------------------------------------------------------------
// getMapFeed
// ---------------------------------------------------------------------------

/**
 * Map-view feed query — posts within a bounding box.
 *
 * @param {Object} opts
 * @param {string} opts.userId
 * @param {string} [opts.surface]      – defaults to 'place'
 * @param {number} opts.south
 * @param {number} opts.west
 * @param {number} opts.north
 * @param {number} opts.east
 * @param {string} [opts.postType]
 * @param {number} [opts.limit]        – defaults to 50
 * @returns {Promise<object[]>}
 */
async function getMapFeed({
  userId,
  surface = 'place',
  south,
  west,
  north,
  east,
  postType,
  limit = 50,
}) {
  const normalizedSurface = FEED_SURFACES.includes(surface) ? surface : 'place';
  const queryLimit = Math.max(Number(limit) || 50, 1);

  // 1. Resolve author IDs and distribution target
  let authorIds = null;
  let distributionTarget = 'place';

  if (normalizedSurface === 'following') {
    authorIds = await loadFollowingIds(userId);
    distributionTarget = 'followers';
  } else if (normalizedSurface === 'connections') {
    authorIds = await loadConnectionIds(userId);
    distributionTarget = 'connections';
  }

  if (authorIds && authorIds.length === 0) {
    return [];
  }

  // 2. Build query with bounding box (fall back to latitude/longitude when effective_* is NULL)
  let query = supabaseAdmin
    .from('Post')
    .select(FEED_POST_SELECT)
    .is('archived_at', null)
    .or(
      `and(effective_latitude.gte.${south},effective_latitude.lte.${north},effective_longitude.gte.${west},effective_longitude.lte.${east}),` +
      `and(effective_latitude.is.null,latitude.gte.${south},latitude.lte.${north},longitude.gte.${west},longitude.lte.${east})`
    )
    .overlaps('distribution_targets', [distributionTarget])
    .order('created_at', { ascending: false })
    .limit(queryLimit + 40);

  if (postType) query = query.eq('post_type', postType);
  if (authorIds) query = query.in('user_id', authorIds);

  const { data, error } = await query;
  if (error) {
    throw new Error(`Failed to load map posts: ${error.message}`);
  }

  // 3–4. Normalize and apply mute/hide/block
  const filters = await getMuteAndHideFilters(userId);
  let posts = applyMuteHideFilters(
    (data || []).map(row => normalizeFeedPostRow(row, new Set(), new Set())),
    filters,
    normalizedSurface,
    userId
  );

  // 5. Politics filtering
  if (!filters.feedPreferences?.[politicsPrefKey(normalizedSurface)]) {
    posts = posts.filter(p => !isPoliticsPost(p));
  }

  // 6. Trim
  posts = posts.slice(0, queryLimit);

  // 7. Enrich
  return enrichWithUserStatus(posts, userId);
}

// ---------------------------------------------------------------------------
// Exports
// ---------------------------------------------------------------------------

module.exports = {
  getListFeed,
  getMapFeed,
  // Exported for use by posts.js during migration (these still exist in posts.js too)
  normalizeMediaUrls,
  normalizeFeedPostRow,
  getMuteAndHideFilters,
  applyMuteHideFilters,
  enrichWithUserStatus,
  applyPostLocationPrivacy,
  applyPostLocationPrivacyBatch,
  applyCursorCondition,
  buildCursorPagination,
  applyPinnedCursorCondition,
  isPoliticsPost,
  invalidateFilterCache,
  FEED_POST_SELECT,
  CREATOR_SELECT,
};
