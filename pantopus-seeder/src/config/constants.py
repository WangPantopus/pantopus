"""Tapering thresholds, cadence rules, category mappings, and other constants."""

from typing import Any

# ---------------------------------------------------------------------------
# Categories
# ---------------------------------------------------------------------------

ALL_CATEGORIES: list[str] = [
    "local_news", "event", "weather", "seasonal", "community_resource", "safety",
    "air_quality", "earthquake", "history", "sports",
]

# ---------------------------------------------------------------------------
# Tapering stages — determines curator cadence based on organic activity
# ---------------------------------------------------------------------------

TAPERING_THRESHOLDS: dict[str, dict[str, int]] = {
    "full": {"organic_posts_per_day": 1, "active_posters_7d": 5},
    "reduced": {"organic_posts_per_day": 2, "active_posters_7d": 10},
    "minimal": {"organic_posts_per_day": 5, "active_posters_7d": 15},
    "dormant": {"organic_posts_per_day": 10, "active_posters_7d": 20},
}

TAPERING_STAGES: dict[str, dict[str, Any]] = {
    "full": {
        "max_slots": 3,
        "allowed_categories": ALL_CATEGORIES,
    },
    "reduced": {
        "max_slots": 2,
        "skip_slots": ["midday"],
        "allowed_categories": ALL_CATEGORIES,
    },
    "minimal": {
        "max_slots": 1,
        "skip_slots": ["midday", "evening"],
        "allowed_categories": ["event", "weather", "seasonal", "safety", "air_quality", "earthquake"],
    },
    "dormant": {
        "max_slots": 0,
        "skip_slots": ["morning", "midday", "evening"],
        "allowed_categories": ["weather", "safety", "earthquake"],
    },
}

# ---------------------------------------------------------------------------
# Posting cadence — time slots in Pacific Time hours
# ---------------------------------------------------------------------------

POSTING_SLOTS: dict[str, int] = {
    "morning": 7,    # ~7:30 AM PT
    "midday": 12,    # ~12:00 PM PT
    "evening": 17,   # ~5:00 PM PT
}

CADENCE_SLOTS: dict[str, list[str]] = {
    "full": ["morning", "midday", "evening"],
    "reduced": ["morning", "evening"],
    "minimal": ["morning"],
    "dormant": [],
}

# ---------------------------------------------------------------------------
# Category → Pantopus API field mappings
# ---------------------------------------------------------------------------

CATEGORY_TO_POST_TYPE: dict[str, str] = {
    "local_news": "local_update",
    "event": "event",
    "weather": "alert",
    "seasonal": "local_update",
    "community_resource": "recommendation",
    "safety": "alert",
    "air_quality": "alert",
    "earthquake": "alert",
    "history": "local_update",
    "sports": "local_update",
}

CATEGORY_TO_PURPOSE: dict[str, str] = {
    "local_news": "local_update",
    "event": "event",
    "weather": "heads_up",
    "seasonal": "local_update",
    "community_resource": "recommend",
    "safety": "heads_up",
    "air_quality": "heads_up",
    "earthquake": "heads_up",
    "history": "local_update",
    "sports": "local_update",
}

# ---------------------------------------------------------------------------
# Humanizer constraints
# ---------------------------------------------------------------------------

MAX_HUMANIZED_LENGTH: int = 500
MAX_EXCLAMATION_MARKS: int = 1

# ---------------------------------------------------------------------------
# Jitter and scheduling
# ---------------------------------------------------------------------------

MAX_JITTER_MINUTES: int = 5

# ---------------------------------------------------------------------------
# Queue hygiene
# ---------------------------------------------------------------------------

QUEUE_STALE_HOURS: int = 48

QUEUE_PURGE_DAYS: dict[str, int] = {
    "filtered_out": 7,
    "skipped": 7,
    "posted": 30,
}

# ---------------------------------------------------------------------------
# Content freshness
# ---------------------------------------------------------------------------

FRESHNESS_HOURS: dict[str, int] = {
    "default": 48,
    "event": 72,
    "history": 24,  # On This Day content only relevant today
}

MAX_SEASONAL_POSTS_PER_WEEK: int = 1

# ---------------------------------------------------------------------------
# Regions — DEPRECATED: regions are now loaded dynamically from seeder_config.
# Kept only for backward compatibility with tests and the setup script.
# New code should use region_registry.load_active_regions() instead.
# ---------------------------------------------------------------------------

REGIONS: dict[str, dict[str, Any]] = {
    "clark_county": {
        "lat": 45.6387,
        "lng": -122.6615,
        "radius_meters": 25000,
        "timezone": "America/Los_Angeles",
    },
    "portland_metro": {
        "lat": 45.5152,
        "lng": -122.6784,
        "radius_meters": 25000,
        "timezone": "America/Los_Angeles",
    },
}

# ---------------------------------------------------------------------------
# Relevance filter blocklist (case-insensitive substring match)
# ---------------------------------------------------------------------------

BLOCKLIST_TERMS: list[str] = [
    # Crime / violence — the filter matches these as whole words, so include
    # common inflections (arrested / arrests) rather than relying on substrings.
    "arrest", "arrested", "arrests",
    "charged", "charges",
    "murder", "murdered", "murders",
    "shooting", "shootings",
    "stabbing", "stabbings",
    "assault", "assaulted",
    "robbery", "robbed",
    "homicide", "homicides",
    "manslaughter",
    "indicted", "indictment",
    "sentenced",
    # Politics
    "republican", "democrat", "election", "ballot", "campaign",
    "partisan", "conservative", "liberal",
    # Paywalled content
    "subscribe to read", "for subscribers", "premium content",
    # Obituaries
    "obituary", "passed away", "in memoriam",
]
