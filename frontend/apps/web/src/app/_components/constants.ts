// ─────────────────────────────────────────────────────────────────────────────
// Landing page constants — Blueprint v3
// ─────────────────────────────────────────────────────────────────────────────

// ── Navigation ───────────────────────────────────────────────────────────────

export const NAV_LINKS = [
  { label: 'Features', href: '#features' },
  { label: 'How It Works', href: '#how-it-works' },
  { label: 'Marketplace', href: '#marketplace' },
  { label: 'Trust & Identity', href: '#trust' },
] as const;

// ── Hero ─────────────────────────────────────────────────────────────────────

export const HERO_JUMP_LINKS = [
  { label: 'First Win', href: '#first-win' },
  { label: 'AI', href: '#ai-reality' },
  { label: 'Marketplace', href: '#marketplace' },
  { label: 'Map', href: '#map' },
  { label: 'Trust', href: '#trust' },
] as const;

// ── First Win ────────────────────────────────────────────────────────────────

export type FirstWinColor = 'blue' | 'emerald' | 'indigo' | 'amber';

export const FIRST_WIN_CARDS = [
  {
    icon: '🙋',
    color: 'blue' as FirstWinColor,
    title: 'Get help today',
    body: 'Post a task, get bids, chat, pay — done.',
    bullets: [
      'Verified people, not anonymous profiles',
      'Clear price and timing upfront',
      'Everything in one thread',
    ],
    cta: 'Post a task',
    href: '/register',
  },
  {
    icon: '💸',
    color: 'emerald' as FirstWinColor,
    title: 'Earn money this week',
    body: 'Offer your skills, accept tasks, build reviews.',
    bullets: [
      'Your skills pay — from yard work to tutoring',
      'Repeat clients who know you',
      'Built-in wallet and payout history',
    ],
    cta: 'Browse tasks',
    href: '/register',
  },
  {
    icon: '🏠',
    color: 'indigo' as FirstWinColor,
    title: 'Organize your home',
    body: 'Tasks, packages, bills, and household notes in one place.',
    bullets: [
      'Invite household members',
      'Private by default',
      'One command center for home chaos',
    ],
    cta: 'Claim your home',
    href: '/register',
  },
  {
    icon: '🏪',
    color: 'amber' as FirstWinColor,
    title: 'Grow your business',
    body: 'A verified business profile with chat, posts, and offers.',
    bullets: [
      'Public page with team roles',
      'Show your work, get inquiries',
      'Convert interest into jobs',
    ],
    cta: 'Create your page',
    href: '/register',
  },
] as const;

// ── Pulse / Feed ─────────────────────────────────────────────────────────────

export const INTENT_TAGS = [
  { label: 'Ask', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300' },
  { label: 'Recommend', color: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300' },
  { label: 'Event', color: 'bg-violet-100 text-violet-700 dark:bg-violet-900/40 dark:text-violet-300' },
  { label: 'Lost & Found', color: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300' },
  { label: 'Announce', color: 'bg-rose-100 text-rose-700 dark:bg-rose-900/40 dark:text-rose-300' },
] as const;

export const FEED_POSTS = [
  {
    intent: 'Ask',
    intentColor: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
    user: 'Maria K.',
    time: '12m ago',
    content: 'Anyone know a reliable plumber for a weekend job? Pipe burst in the basement.',
    replies: 4,
  },
  {
    intent: 'Recommend',
    intentColor: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
    user: 'James T.',
    time: '1h ago',
    content: 'Highly recommend Sunrise Bakery on Main — the sourdough is unreal. Ask for the Thursday batch.',
    replies: 11,
  },
  {
    intent: 'Lost & Found',
    intentColor: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
    user: 'Priya S.',
    time: '3h ago',
    content: 'Found a golden retriever near Elm Park — no collar. Very friendly. DM if yours!',
    replies: 7,
  },
] as const;

// ── AI Reality Skills Tag Cloud ─────────────────────────────────────────────

export const AI_REALITY_SKILLS = [
  'Yard & garden', 'Handyman', 'Moving help', 'Pet care',
  'Tutoring', 'Cleaning', 'Delivery', 'Tech help',
  'Childcare', 'Cooking', 'Event help', 'Senior care',
] as const;

// ── 5-Pillar Features Grid ──────────────────────────────────────────────────

export type PillarTone = 'primary' | 'sky' | 'emerald' | 'violet' | 'indigo' | 'amber' | 'rose';

export const PILLAR_CARDS = [
  {
    emoji: '🌐',
    tone: 'primary' as PillarTone,
    badge: 'Feed',
    title: 'The Pulse — Intent-driven feed',
    tagline: 'Post with purpose: Ask, Recommend, Event, Alert, Announce.',
    bullets: [
      'Audience targeting: Nearby, Followers, Household, Saved Place',
      "Verified signals so you know who's real",
    ],
  },
  {
    emoji: '💼',
    tone: 'emerald' as PillarTone,
    badge: 'Work',
    title: 'Tasks — Full gig lifecycle',
    tagline: 'Post tasks or accept offers with clear scope and timing.',
    bullets: [
      'Bids and quotes that keep pricing transparent',
      'Status tracking from Open → Done, with pay and review built in',
    ],
  },
  {
    emoji: '💬',
    tone: 'violet' as PillarTone,
    badge: 'Messaging',
    title: 'Chat — One thread, everything connected',
    tagline: 'Chat tied to people, with task/offer cards inline.',
    bullets: [
      'Share photos and updates without scattered DMs',
      'Fast handoffs: chat → action → completion',
    ],
  },
  {
    emoji: '🗺️',
    tone: 'sky' as PillarTone,
    badge: 'Discovery',
    title: 'Map — Living activity layer',
    tagline: 'Browse tasks, listings, businesses, and posts on one map.',
    bullets: [
      'Filter by distance, category, and trust level',
      'Tap any pin to act without losing context',
    ],
  },
  {
    emoji: '🏪',
    tone: 'rose' as PillarTone,
    badge: 'Business',
    title: 'Business Profiles — Verified presence',
    tagline: 'Public page with team roles (owner, admin, staff).',
    bullets: [
      'Show your work, get inquiries, convert interest into jobs',
      'Discoverable on map and in-feed',
    ],
  },
] as const;

// ── How It Works ─────────────────────────────────────────────────────────────

export const HOW_IT_WORKS_STEPS = [
  { step: '01', title: 'Verify your identity', line: 'Create an account and verify your address. This is what makes everything else trustworthy.' },
  { step: '02', title: 'Choose what you need', line: 'Post a task, list an item, follow a place, claim your home — start anywhere.' },
  { step: '03', title: 'Get things done', line: 'Find help, earn money, sell stuff, organize home life — with people whose identity is proven.' },
] as const;

// ── Trust & Safety ───────────────────────────────────────────────────────────

export const TRUST_CARDS = [
  { icon: '🔒', title: 'Address verification', body: 'We verify your address through physical mail, property records, or document upload. You can\'t fake a mailing address.' },
  { icon: '🎯', title: 'Granular privacy', body: 'You control exactly what\'s visible — to whom, in what context. Verification builds trust without requiring exposure.' },
  { icon: '⭐', title: 'Reputation that means something', body: 'Ratings, completed transactions, reliability scores — earned through real interactions, not gaming.' },
  { icon: '🛡️', title: 'Moderation & enforcement', body: 'Reporting, incident tracking, blocks, and platform action. With real identity, accountability is real too.' },
] as const;

// ── Footer ───────────────────────────────────────────────────────────────────

export const FOOTER_PRODUCT_LINKS = [
  { label: 'Features', href: '#features' },
  { label: 'How It Works', href: '#how-it-works' },
  { label: 'Marketplace', href: '#marketplace' },
  { label: 'Trust & Identity', href: '#trust' },
] as const;

export const FOOTER_COMPANY_LINKS = [
  { label: 'About', href: '/about' },
  { label: 'Contact', href: '/contact' },
] as const;

export const FOOTER_LEGAL_LINKS = [
  { label: 'Privacy', href: '/privacy' },
  { label: 'Terms', href: '/terms' },
  { label: 'Child Safety', href: '/child-safety' },
] as const;
