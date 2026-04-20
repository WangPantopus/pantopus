// ============================================================
// HUB ENDPOINT — Aggregation for the Pantopus Hub (Mission Control)
// Endpoints:
//   GET /api/hub              — Aggregated hub payload
//   GET /api/hub/today        — Hub Today context card
//   GET /api/hub/preferences  — Notification preferences
//   PUT /api/hub/preferences  — Update notification preferences
//   GET /api/hub/discovery    — Nearby discovery items (gigs, people, businesses, posts)
// ============================================================

const express = require('express');
const router = express.Router();
const Joi = require('joi');
const supabaseAdmin = require('../config/supabaseAdmin');
const verifyToken = require('../middleware/verifyToken');
const validate = require('../middleware/validate');
const logger = require('../utils/logger');
const { getHubToday } = require('../services/context/providerOrchestrator');

/**
 * GET /api/hub
 * Aggregated hub payload for the authenticated user.
 */
router.get('/', verifyToken, async (req, res) => {
  const userId = req.user.id;

  try {
    // ── Parallel batch 1: User-level data (no dependencies) ──
    const [userResult, walletResult, earningsRpcResult, occupancyResult, bizTeamResult, skillsResult, stripeResult] =
      await Promise.all([
        supabaseAdmin
          .from('User')
          .select('id, email, username, name, first_name, last_name, profile_picture_url, bio, account_type, average_rating, review_count')
          .eq('id', userId)
          .single(),
        Promise.resolve(
          supabaseAdmin
            .from('Wallet')
            .select('balance, lifetime_received')
            .eq('user_id', userId)
            .maybeSingle()
        ).catch(() => ({ data: null })),
        Promise.resolve(
          require('../services/earningsService').getEarningsForUser(userId, null, null)
        ).catch(() => null),
        supabaseAdmin
          .from('HomeOccupancy')
          .select('home_id, role, is_active, home:home_id(id, name, address, city, state, zipcode, latitude, longitude)')
          .eq('user_id', userId)
          .eq('is_active', true),
        // Seat-based business memberships (with fallback to BusinessTeam format)
        (async () => {
          const { getAllSeatsForUser } = require('../utils/seatPermissions');
          const seats = await getAllSeatsForUser(userId);
          if (seats.length > 0) {
            return { data: seats.map(s => ({
              business_user_id: s.business_user_id,
              role_base: s.role_base,
              title: s.display_name,
              business: {
                id: s.business_user_id,
                username: s.business_username,
                name: s.business_name,
                profile_picture_url: null,
              },
            })) };
          }
          return supabaseAdmin
            .from('BusinessTeam')
            .select('business_user_id, role_base, title, business:business_user_id(id, username, name, profile_picture_url)')
            .eq('user_id', userId)
            .eq('is_active', true);
        })(),
        Promise.resolve(
          supabaseAdmin
            .from('UserProfessionalProfile')
            .select('*', { count: 'exact', head: true })
            .eq('user_id', userId)
        ).catch(() => ({ count: 0 })),
        Promise.resolve(
          supabaseAdmin
            .from('StripeAccount')
            .select('id')
            .eq('user_id', userId)
            .maybeSingle()
        ).catch(() => ({ data: null })),
      ]);

    const { data: user, error: userErr } = userResult;
    if (userErr || !user) {
      logger.error('Hub: user lookup failed', { userId, error: userErr?.message });
      return res.status(404).json({ error: 'User not found' });
    }

    const walletBalance = walletResult.data?.balance || 0;
    const lifetimeReceived = walletResult.data?.lifetime_received ?? 0;
    const earningsFromPayments = (earningsRpcResult && typeof earningsRpcResult === 'object' && earningsRpcResult.total_earned != null)
      ? Number(earningsRpcResult.total_earned) || 0
      : 0;
    // Use the higher of: earnings from Payment records, or wallet lifetime_received.
    // lifetime_received covers direct credits, seeder funds, etc. that aren't Payment rows.
    const totalEarnedCents = Math.max(earningsFromPayments, lifetimeReceived);

    const homes = (occupancyResult.data || [])
      .filter((o) => o.home)
      .map((o) => ({
        id: o.home.id,
        name: o.home.name || o.home.address,
        addressShort: [o.home.address, o.home.city].filter(Boolean).join(', '),
        city: o.home.city || null,
        state: o.home.state || null,
        latitude: o.home.latitude || null,
        longitude: o.home.longitude || null,
        isPrimary: false,
        roleBase: o.role || 'member',
      }));

    // Include homes where user is a verified owner (in case occupancy is missing or out of sync after claim approval)
    const occupancyHomeIds = new Set(homes.map((h) => h.id));
    const { data: ownerRows, error: ownerErr } = await supabaseAdmin
      .from('HomeOwner')
      .select('home_id')
      .eq('subject_id', userId)
      .eq('owner_status', 'verified');
    if (ownerErr) {
      logger.warn('Hub: HomeOwner fallback query failed', { userId, error: ownerErr.message });
    }
    const ownerHomeIds = [...new Set((ownerRows || []).map((r) => r.home_id).filter((id) => id && !occupancyHomeIds.has(id)))];
    if (ownerHomeIds.length > 0) {
      const { data: ownerHomes, error: homeErr } = await supabaseAdmin
        .from('Home')
        .select('id, name, address, city, state, zipcode, latitude, longitude')
        .in('id', ownerHomeIds);
      if (homeErr) {
        logger.warn('Hub: Home fetch for owner fallback failed', { ownerHomeIds, error: homeErr.message });
      }
      for (const home of ownerHomes || []) {
        homes.push({
          id: home.id,
          name: home.name || home.address,
          addressShort: [home.address, home.city].filter(Boolean).join(', '),
          city: home.city || null,
          state: home.state || null,
          latitude: home.latitude || null,
          longitude: home.longitude || null,
          isPrimary: false,
          roleBase: 'owner',
        });
      }
    }

    if (homes.length > 0) homes[0].isPrimary = true;
    const primaryHome = homes[0] || null;

    const businesses = (bizTeamResult.data || [])
      .filter((t) => t.business)
      .map((t) => ({
        id: t.business.id,
        name: t.business.name,
        username: t.business.username,
        roleBase: t.role_base,
      }));

    const hasSkills = (skillsResult.count || 0) > 0;
    const hasPayoutMethod = !!stripeResult.data;

    // ── Personal profile completeness ──────────────────────────
    const profileChecks = {
      firstName: !!user.first_name,
      lastName: !!user.last_name,
      photo: !!user.profile_picture_url,
      bio: !!(user.bio && user.bio.length >= 10),
      skills: hasSkills,
    };
    const profileTotal = Object.keys(profileChecks).length;
    const profileDone = Object.values(profileChecks).filter(Boolean).length;
    const profileComplete = profileDone >= profileTotal;
    const profileCompleteness = Math.round((profileDone / profileTotal) * 100);

    const setupSteps = [
      { key: 'complete_profile', done: profileComplete },
      { key: 'home', done: homes.length > 0 },
      { key: 'profile_photo', done: !!user.profile_picture_url },
      { key: 'skills', done: hasSkills },
      { key: 'payout_method', done: hasPayoutMethod },
    ];

    // ── Parallel batch 2: Status counts + card data ──────────
    // Build all independent queries, then run in parallel.
    const now = new Date();
    const weekFromNow = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);

    const batch2 = {
      chatParts: Promise.resolve(
        supabaseAdmin
          .from('ChatParticipant')
          .select('room_id, unread_count')
          .eq('user_id', userId)
          .gt('unread_count', 0)
      ).catch(() => ({ data: null })),
      notifCount: Promise.resolve(
        supabaseAdmin
          .from('Notification')
          .select('id', { count: 'exact', head: true })
          .eq('user_id', userId)
          .eq('is_read', false)
      ).catch(() => ({ count: 0 })),
      personalMail: Promise.resolve(
        supabaseAdmin
          .from('Mail')
          .select('id, type, is_read')
          .eq('recipient_id', userId)
          .eq('status', 'pending')
      ).catch(() => ({ data: null })),
      gigsNearby: Promise.resolve(
        supabaseAdmin
          .from('Gig')
          .select('id', { count: 'exact', head: true })
          .eq('status', 'open')
          .neq('user_id', userId)
      ).catch(() => ({ count: 0 })),
      recentNotifs: Promise.resolve(
        supabaseAdmin
          .from('Notification')
          .select('id, type, title, body, link, metadata, created_at, is_read')
          .eq('user_id', userId)
          .order('created_at', { ascending: false })
          .limit(8)
      ).catch(() => ({ data: null })),
      recentGigs: Promise.resolve(
        supabaseAdmin
          .from('Gig')
          .select('id, title, status, created_at')
          .eq('user_id', userId)
          .order('created_at', { ascending: false })
          .limit(10)
      ).catch(() => ({ data: null })),
    };

    // Home-specific queries (only if user has a home)
    if (primaryHome) {
      batch2.homeMail = Promise.resolve(
        supabaseAdmin
          .from('Mail')
          .select('id', { count: 'exact', head: true })
          .eq('home_id', primaryHome.id)
          .eq('status', 'pending')
      ).catch(() => ({ count: 0 }));
      batch2.dueBills = Promise.resolve(
        supabaseAdmin
          .from('HomeBill')
          .select('id, bill_type, provider_name, amount, due_date, status')
          .eq('home_id', primaryHome.id)
          .not('due_date', 'is', null)
          .lte('due_date', weekFromNow.toISOString())
          .order('due_date', { ascending: true })
          .limit(3)
      ).catch(() => ({ data: null }));
      batch2.dueTasks = Promise.resolve(
        supabaseAdmin
          .from('HomeTask')
          .select('id, title, due_at')
          .eq('home_id', primaryHome.id)
          .not('due_at', 'is', null)
          .lte('due_at', weekFromNow.toISOString())
          .order('due_at', { ascending: true })
          .limit(2)
      ).catch(() => ({ data: null }));
      batch2.memberCount = Promise.resolve(
        supabaseAdmin
          .from('HomeOccupancy')
          .select('id', { count: 'exact', head: true })
          .eq('home_id', primaryHome.id)
          .eq('is_active', true)
      ).catch(() => ({ count: 0 }));

      // Neighbor density: count verified users within ~1 mile via PostGIS
      const homeLat = primaryHome.latitude;
      const homeLng = primaryHome.longitude;
      if (homeLat && homeLng) {
        batch2.neighborDensity = Promise.resolve(
          supabaseAdmin.rpc('count_neighbors_within', {
            center_lat: homeLat,
            center_lng: homeLng,
            radius_meters: 1609, // ~1 mile
          })
        ).catch(() => ({ data: 0 }));

        batch2.densityMilestone = Promise.resolve(
          supabaseAdmin
            .from('HomeOccupancy')
            .select('density_milestone_seen')
            .eq('home_id', primaryHome.id)
            .eq('user_id', userId)
            .eq('is_active', true)
            .maybeSingle()
        ).catch(() => ({ data: null }));
      }
    }

    // Business-specific queries
    if (businesses.length > 0) {
      batch2.bizChats = Promise.resolve(
        supabaseAdmin
          .from('ChatParticipant')
          .select('room_id', { count: 'exact', head: true })
          .eq('user_id', businesses[0].id)
      ).catch(() => ({ count: 0 }));
    }

    // Execute all queries in parallel
    const keys = Object.keys(batch2);
    const results = await Promise.all(Object.values(batch2));
    const b2 = {};
    keys.forEach((k, i) => { b2[k] = results[i]; });

    // ── 5. Build status items from batch results ─────────────
    const statusItems = [];

    const unreadChats = (b2.chatParts.data || []).length;
    if (unreadChats > 0) {
      statusItems.push({
        id: 'chat_unread', type: 'chat_unread', pillar: 'personal',
        title: `${unreadChats} unread chat${unreadChats > 1 ? 's' : ''}`,
        subtitle: 'Tap to view messages', severity: 'info', count: unreadChats,
        route: '/app/chat',
      });
    }

    const notifCount = b2.notifCount.count || 0;
    if (notifCount > 0) {
      statusItems.push({
        id: 'notifications', type: 'system_alert', pillar: 'personal',
        title: `${notifCount} notification${notifCount > 1 ? 's' : ''}`,
        subtitle: 'View updates', severity: 'info', count: notifCount,
        route: '/app/notifications',
      });
    }

    if (walletBalance > 0) {
      statusItems.push({
        id: 'wallet_withdraw', type: 'system_alert', pillar: 'personal',
        title: `$${(walletBalance / 100).toFixed(2)} ready`,
        subtitle: 'Tap to withdraw', severity: 'info', count: 1,
        route: '/app/settings/payments',
      });
    }

    // Home mail
    if (primaryHome) {
      const mailCount = b2.homeMail?.count || 0;
      if (mailCount > 0) {
        statusItems.push({
          id: 'mail_new', type: 'mail_new', pillar: 'home',
          title: `${mailCount} new mail`, subtitle: 'View in mailbox',
          severity: 'info', count: mailCount,
          route: `/app/mailbox?scope=home&homeId=${primaryHome.id}`,
        });
      }
    }

    // Bills due (reuse single query for both status items and card)
    const dueBills = (primaryHome && b2.dueBills?.data) || [];
    for (const bill of dueBills) {
      if (bill.status === 'paid') continue;
      const dueDate = new Date(bill.due_date);
      const isOverdue = dueDate < now;
      const billName = bill.provider_name || bill.bill_type || 'Bill';
      statusItems.push({
        id: `bill_${bill.id}`, type: 'bill_due', pillar: 'home',
        title: isOverdue ? `Bill overdue: ${billName}` : `Bill due soon: ${billName}`,
        subtitle: bill.amount != null ? `$${Number(bill.amount).toFixed(2)}` : undefined,
        severity: isOverdue ? 'critical' : 'warning',
        dueAt: bill.due_date,
        route: `/app/homes/${primaryHome.id}/dashboard?tab=bills`,
        entityRef: { kind: 'bill', id: bill.id },
      });
    }

    // Tasks due (reuse single query)
    const dueTasks = (primaryHome && b2.dueTasks?.data) || [];
    for (const task of dueTasks) {
      statusItems.push({
        id: `task_${task.id}`, type: 'task_due', pillar: 'home',
        title: `Task due: ${task.title}`, subtitle: 'View task details',
        severity: 'warning', dueAt: task.due_at,
        route: `/app/homes/${primaryHome.id}/dashboard?tab=tasks`,
        entityRef: { kind: 'task', id: task.id },
      });
    }

    // Personal inbox
    const mailItems = b2.personalMail.data || [];
    const unreadPersonal = mailItems.filter((m) => !m.is_read).length;
    const offerItems = mailItems.filter((m) => m.type === 'ad' || m.type === 'newsletter');
    if (unreadPersonal > 0) {
      statusItems.push({
        id: 'inbox_personal', type: 'mail_new', pillar: 'personal',
        title: `${unreadPersonal} unread in inbox`, subtitle: 'Open inbox',
        severity: 'info', count: unreadPersonal, route: '/app/mailbox',
      });
    }
    if (offerItems.length > 0) {
      statusItems.push({
        id: 'inbox_offers', type: 'system_alert', pillar: 'personal',
        title: `${offerItems.length} offer${offerItems.length > 1 ? 's' : ''} available`,
        subtitle: 'Earn today', severity: 'info', count: offerItems.length,
        route: '/app/mailbox',
      });
    }

    // Sort status items: critical > warning > info, then by dueAt
    const severityOrder = { critical: 0, warning: 1, info: 2 };
    statusItems.sort((a, b) => {
      const sa = severityOrder[a.severity] ?? 2;
      const sb = severityOrder[b.severity] ?? 2;
      if (sa !== sb) return sa - sb;
      if (a.dueAt && b.dueAt) return new Date(a.dueAt).getTime() - new Date(b.dueAt).getTime();
      if (a.dueAt) return -1;
      if (b.dueAt) return 1;
      return 0;
    });

    // ── 6. Pillar card data (built from batch results) ───────
    const personalCard = {
      unreadChats,
      earnings: totalEarnedCents,
      gigsNearby: b2.gigsNearby.count || 0,
      rating: user.average_rating || 0,
      reviewCount: user.review_count || 0,
    };

    let homeCard = null;
    if (primaryHome) {
      homeCard = {
        newMail: statusItems.find((i) => i.type === 'mail_new')?.count || 0,
        billsDue: dueBills
          .filter((b) => b.status !== 'paid')
          .slice(0, 2)
          .map((b) => ({
            id: b.id,
            name: b.provider_name || b.bill_type || 'Bill',
            amount: Number(b.amount) || 0,
            dueAt: b.due_date,
          })),
        tasksDue: dueTasks.map((t) => ({ id: t.id, title: t.title, dueAt: t.due_at })),
        memberCount: b2.memberCount?.count || 0,
      };
    }

    let businessCard = null;
    if (businesses.length > 0) {
      businessCard = {
        newOrders: 0,
        unreadThreads: b2.bizChats?.count || 0,
        pendingPayout: 0,
      };
    }

    // ── 7. Jump Back In (static for v1) ──────────────────────
    const jumpBackIn = [];
    jumpBackIn.push({ title: 'Post a Task', route: '/gigs/new', icon: 'hammer' });
    jumpBackIn.push({ title: 'Messages', route: '/app/chat', icon: 'chatbubbles' });

    if (primaryHome) {
      jumpBackIn.push({ title: 'Mailbox', route: `/app/mailbox?scope=home&homeId=${primaryHome.id}`, icon: 'mail' });
      jumpBackIn.push({ title: 'My Home', route: `/app/homes/${primaryHome.id}/dashboard`, icon: 'home' });
    }

    if (businesses.length > 0) {
      jumpBackIn.push({ title: `${businesses[0].name}`, route: `/app/businesses/${businesses[0].id}/dashboard`, icon: 'storefront' });
    }

    jumpBackIn.push({ title: 'Explore Map', route: '/app/map', icon: 'map' });

    // ── 8. Activity log (from batch2 results) ────────────────
    let activity = [];
    try {
      for (const n of b2.recentNotifs.data || []) {
        let pillar = 'personal';
        if (n.type?.includes('home') || n.type?.includes('mail') || n.type?.includes('bill') || n.type?.includes('task') || n.type?.includes('residency')) {
          pillar = 'home';
        } else if (n.type?.includes('business') || n.type?.includes('order')) {
          pillar = 'business';
        }

        // Normalize links: /homes/... -> /app/homes/... so in-app routes don't 404
        let route = n.link || '/app/notifications';
        if (route.startsWith('/homes/') && !route.startsWith('/app/homes/')) {
          route = '/app' + route;
        }
        activity.push({
          id: n.id,
          pillar,
          title: n.title || n.body || n.type,
          at: n.created_at,
          read: !!n.is_read,
          route,
        });
      }

      if (activity.length < 10) {
        for (const g of (b2.recentGigs.data || []).slice(0, 10 - activity.length)) {
          activity.push({
            id: `gig_${g.id}`,
            pillar: 'personal',
            title: `Task posted: ${g.title}`,
            at: g.created_at,
            read: true,
            route: `/gigs/${g.id}`,
          });
        }
      }

      activity.sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime());
      activity = activity.slice(0, 10);
    } catch (e) {
      logger.warn('Hub: activity log failed', { error: e.message });
    }

    // ── 8b. Neighbor density + milestone ─────────────────────
    let neighborDensity = null;
    if (primaryHome && b2.neighborDensity !== undefined) {
      const count = typeof b2.neighborDensity.data === 'number'
        ? b2.neighborDensity.data
        : (Number(b2.neighborDensity.data) || 0);
      const milestoneSeen = b2.densityMilestone?.data?.density_milestone_seen || 0;

      const MILESTONES = [500, 200, 100, 50, 25, 10];
      let milestone = null;
      for (const m of MILESTONES) {
        if (count >= m && milestoneSeen < m) {
          milestone = `Your neighborhood just hit ${m} verified members!`;
          break;
        }
      }

      neighborDensity = { count, radiusMiles: 1, milestone };
    }

    // ── 9. Assemble response ─────────────────────────────────
    const payload = {
      user: {
        id: user.id,
        name: user.name || [user.first_name, user.last_name].filter(Boolean).join(' '),
        firstName: user.first_name || (user.name ? user.name.split(' ')[0] : null),
        username: user.username,
        avatarUrl: user.profile_picture_url,
        email: user.email,
      },
      context: {
        activeHomeId: primaryHome?.id || null,
        activePersona: { type: 'personal' },
      },
      availability: {
        hasHome: homes.length > 0,
        hasBusiness: businesses.length > 0,
        hasPayoutMethod,
      },
      homes,
      businesses,
      setup: {
        steps: setupSteps,
        allDone: setupSteps.every((s) => s.done),
        profileCompleteness: {
          score: profileCompleteness,
          checks: profileChecks,
          missingFields: Object.entries(profileChecks)
            .filter(([, done]) => !done)
            .map(([field]) => field),
        },
      },
      statusItems: statusItems.slice(0, 6),
      cards: {
        personal: personalCard,
        ...(homeCard ? { home: homeCard } : {}),
        ...(businessCard ? { business: businessCard } : {}),
      },
      jumpBackIn: jumpBackIn.slice(0, 6),
      activity,
      neighborDensity,
    };

    res.set('Cache-Control', 'no-store, no-cache, must-revalidate');
    res.json(payload);
  } catch (err) {
    logger.error('Hub endpoint error', { error: err.message, stack: err.stack });
    res.status(500).json({ error: 'Failed to load hub data' });
  }
});

// ============================================================
// GET /api/hub/today
// Hub Today context card — weather, AQI, alerts, signals.
// ============================================================
router.get('/today', verifyToken, async (req, res) => {
  try {
    const result = await getHubToday(req.user.id);
    res.set('Cache-Control', 'private, max-age=300');
    res.json(result);
  } catch (err) {
    logger.error('Hub today error', { error: err.message, userId: req.user.id });
    res.json({ today: null, error: 'CONTEXT_UNAVAILABLE' });
  }
});

// ============================================================
// GET /api/hub/preferences
// Returns the user's notification preferences (or defaults).
// ============================================================
router.get('/preferences', verifyToken, async (req, res) => {
  try {
    const { data, error } = await supabaseAdmin
      .from('UserNotificationPreferences')
      .select('*')
      .eq('user_id', req.user.id)
      .maybeSingle();

    if (error) {
      logger.error('Hub preferences read error', { error: error.message, userId: req.user.id });
      return res.status(500).json({ error: 'Failed to fetch preferences' });
    }

    // Return row or defaults
    const prefs = data ? {
      ...data,
      evening_briefing_enabled: data.evening_briefing_enabled ?? true,
      evening_briefing_time_local: data.evening_briefing_time_local || '18:00',
    } : {
      user_id: req.user.id,
      daily_briefing_enabled: false,
      daily_briefing_time_local: '07:30',
      daily_briefing_timezone: 'America/Los_Angeles',
      evening_briefing_enabled: true,
      evening_briefing_time_local: '18:00',
      weather_alerts_enabled: true,
      aqi_alerts_enabled: true,
      mail_summary_enabled: true,
      gig_updates_enabled: true,
      home_reminders_enabled: true,
      quiet_hours_start_local: null,
      quiet_hours_end_local: null,
      location_mode: 'primary_home',
      custom_latitude: null,
      custom_longitude: null,
      custom_label: null,
    };

    res.json({ preferences: prefs });
  } catch (err) {
    logger.error('Hub preferences error', { error: err.message, userId: req.user.id });
    res.status(500).json({ error: 'Failed to fetch preferences' });
  }
});

// ============================================================
// PUT /api/hub/preferences
// Update notification preferences (partial update via upsert).
// ============================================================
const preferencesSchema = Joi.object({
  daily_briefing_enabled: Joi.boolean(),
  daily_briefing_time_local: Joi.string().pattern(/^\d{2}:\d{2}$/),
  daily_briefing_timezone: Joi.string().max(100),
  evening_briefing_enabled: Joi.boolean(),
  evening_briefing_time_local: Joi.string().pattern(/^\d{2}:\d{2}$/),
  weather_alerts_enabled: Joi.boolean(),
  aqi_alerts_enabled: Joi.boolean(),
  mail_summary_enabled: Joi.boolean(),
  gig_updates_enabled: Joi.boolean(),
  home_reminders_enabled: Joi.boolean(),
  quiet_hours_start_local: Joi.string().pattern(/^\d{2}:\d{2}$/).allow(null),
  quiet_hours_end_local: Joi.string().pattern(/^\d{2}:\d{2}$/).allow(null),
  location_mode: Joi.string().valid('viewing_location', 'primary_home', 'device_location', 'custom'),
  custom_latitude: Joi.number().min(-90).max(90).allow(null),
  custom_longitude: Joi.number().min(-180).max(180).allow(null),
  custom_label: Joi.string().max(255).allow(null, ''),
}).min(1);

router.put('/preferences', verifyToken, validate(preferencesSchema), async (req, res) => {
  try {
    const userId = req.user.id;
    const updates = req.body;

    // If location_mode is 'custom', require custom coords
    if (updates.location_mode === 'custom') {
      if (updates.custom_latitude == null || updates.custom_longitude == null) {
        return res.status(400).json({
          error: 'Validation failed',
          message: 'Custom location mode requires custom_latitude and custom_longitude.',
        });
      }
    }

    const { data, error } = await supabaseAdmin
      .from('UserNotificationPreferences')
      .upsert(
        { user_id: userId, ...updates, updated_at: new Date().toISOString() },
        { onConflict: 'user_id' }
      )
      .select('*')
      .single();

    if (error) {
      logger.error('Hub preferences update error', { error: error.message, userId });
      return res.status(500).json({ error: 'Failed to update preferences' });
    }

    res.json({ preferences: data });
  } catch (err) {
    logger.error('Hub preferences update error', { error: err.message, userId: req.user.id });
    res.status(500).json({ error: 'Failed to update preferences' });
  }
});

// ============================================================
// GET /api/hub/discovery
// Returns curated nearby items for the Discovery module.
// Query params: filter (gigs|people|businesses|posts), lat, lng, limit
// ============================================================
router.get('/discovery', verifyToken, async (req, res) => {
  const { filter = 'gigs', lat, lng, limit = 3 } = req.query;
  const userId = req.user.id;
  const parsedLimit = Math.min(parseInt(limit) || 3, 10);

  try {
    let items = [];

    switch (filter) {
      case 'gigs': {
        const { data: gigs } = await supabaseAdmin
          .from('Gig')
          .select('id, title, price, category, status, created_at, description')
          .eq('status', 'open')
          .neq('user_id', userId)
          .order('created_at', { ascending: false })
          .limit(parsedLimit);

        items = (gigs || []).map((g) => ({
          id: g.id,
          type: 'gig',
          title: g.title,
          meta: [
            g.price ? `$${Number(g.price).toFixed(0)}` : null,
            g.category,
          ].filter(Boolean).join(' · '),
          category: g.category || 'General',
          route: `/gigs/${g.id}`,
        }));
        break;
      }

      case 'people': {
        const { data: users } = await supabaseAdmin
          .from('User')
          .select('id, username, name, first_name, last_name, profile_picture_url, average_rating, city, state')
          .eq('account_type', 'personal')
          .neq('id', userId)
          .not('name', 'is', null)
          .order('average_rating', { ascending: false, nullsFirst: false })
          .limit(parsedLimit);

        items = (users || []).map((u) => {
          const displayName = u.name || [u.first_name, u.last_name].filter(Boolean).join(' ') || u.username;
          return {
            id: u.id,
            type: 'person',
            title: displayName,
            meta: [
              u.average_rating ? `${u.average_rating.toFixed(1)} stars` : null,
              u.city,
            ].filter(Boolean).join(' · '),
            avatarUrl: u.profile_picture_url,
            category: 'People',
            route: `/user/${u.id}`,
          };
        });
        break;
      }

      case 'businesses': {
        // Discovery = OTHER users' published businesses near the current user.
        // 1) Get the set of business IDs the current user owns or has a seat in — exclude those.
        // 2) Only include businesses with a published BusinessProfile.
        const [{ data: myTeams }, { data: publishedProfiles }] = await Promise.all([
          supabaseAdmin
            .from('BusinessTeam')
            .select('business_user_id')
            .eq('user_id', userId)
            .eq('is_active', true),
          supabaseAdmin
            .from('BusinessProfile')
            .select('business_user_id')
            .eq('is_published', true),
        ]);

        const myBusinessIds = new Set(
          (myTeams || []).map((t) => t.business_user_id).filter(Boolean)
        );
        const publishedIds = (publishedProfiles || [])
          .map((p) => p.business_user_id)
          .filter((id) => id && !myBusinessIds.has(id));

        if (publishedIds.length === 0) {
          items = [];
          break;
        }

        const { data: bizUsers } = await supabaseAdmin
          .from('User')
          .select('id, username, name, profile_picture_url, average_rating, city, state, bio')
          .eq('account_type', 'business')
          .in('id', publishedIds)
          .order('average_rating', { ascending: false, nullsFirst: false })
          .limit(parsedLimit);

        items = (bizUsers || []).map((b) => ({
          id: b.id,
          type: 'business',
          title: b.name || b.username,
          meta: [
            b.average_rating ? `${b.average_rating.toFixed(1)} stars` : null,
            b.city,
          ].filter(Boolean).join(' · '),
          avatarUrl: b.profile_picture_url,
          category: 'Business',
          route: `/businesses/${b.id}`,
        }));
        break;
      }

      case 'posts': {
        const { data: posts } = await supabaseAdmin
          .from('Post')
          .select('id, post_type, content, author_id, created_at, title')
          .order('created_at', { ascending: false })
          .limit(parsedLimit);

        items = (posts || []).map((p) => ({
          id: p.id,
          type: 'post',
          title: p.title || (p.content || '').slice(0, 60) + ((p.content || '').length > 60 ? '...' : ''),
          meta: p.post_type ? p.post_type.replace(/_/g, ' ') : 'Post',
          category: p.post_type ? p.post_type.replace(/_/g, ' ') : 'Post',
          route: `/posts/${p.id}`,
        }));
        break;
      }

      default:
        return res.status(400).json({ error: 'Invalid filter. Use: gigs, people, businesses, posts' });
    }

    res.json({ filter, items });
  } catch (err) {
    logger.error('Hub discovery error', { error: err.message, stack: err.stack });
    res.status(500).json({ error: 'Discovery failed' });
  }
});

// ============================================================
// POST /api/hub/dismiss-density-milestone
// Marks the current density milestone as seen so it won't show again.
// Body: { homeId: string, milestone: number }
// ============================================================
router.post('/dismiss-density-milestone', verifyToken, async (req, res) => {
  const userId = req.user.id;
  const { homeId, milestone } = req.body;

  if (!homeId || !milestone || typeof milestone !== 'number') {
    return res.status(400).json({ error: 'homeId and numeric milestone required' });
  }

  try {
    const { error } = await supabaseAdmin
      .from('HomeOccupancy')
      .update({ density_milestone_seen: milestone })
      .eq('home_id', homeId)
      .eq('user_id', userId)
      .eq('is_active', true);

    if (error) {
      logger.warn('Failed to dismiss density milestone', { userId, homeId, error: error.message });
      return res.status(500).json({ error: 'Failed to dismiss milestone' });
    }

    res.json({ ok: true });
  } catch (err) {
    logger.error('Dismiss density milestone error', { error: err.message });
    res.status(500).json({ error: 'Failed to dismiss milestone' });
  }
});

module.exports = router;
