"""RSS URLs, API endpoints, and per-region source list definitions.

Source priority levels:
  1 = critical  — safety, weather alerts, earthquakes (always surface first)
  2 = core      — local news, events, seasonal tips (the bread and butter)
  3 = enrichment — community feeds, sports (adds variety)
  4 = filler    — fun extras, lowest priority
"""

from __future__ import annotations

# Each source config is a dict with:
#   source_id:    unique identifier (e.g. 'rss:columbian')
#   source_type:  'rss', 'google_news', 'nws_alerts', etc.
#   url:          feed URL, search query, or 'lat,lng' (None for seasonal)
#   category:     seeder category for fetched items
#   display_name: human-readable name for source attribution
#   priority:     1=critical, 2=core, 3=enrichment, 4=filler

CLARK_COUNTY_SOURCES: list[dict] = [
    # --- P1: Critical (safety/weather) ---
    {
        "source_id": "nws_alerts:clark_county",
        "source_type": "nws_alerts",
        "url": "45.6387,-122.6615",
        "category": "weather",
        "display_name": "NWS Weather Alerts",
        "priority": 1,
    },
    # --- P2: Core (local news, events, seasonal) ---
    {
        "source_id": "rss:columbian",
        "source_type": "rss",
        "url": "https://www.columbian.com/feed/",
        "category": "local_news",
        "display_name": "The Columbian",
        "priority": 2,
    },
    {
        "source_id": "google_news:clark_county",
        "source_type": "google_news",
        "url": "Clark County WA local news",
        "category": "local_news",
        "display_name": "Google News (Clark County)",
        "priority": 2,
    },
    {
        "source_id": "rss:camas_washougal",
        "source_type": "rss",
        "url": "https://www.camaspostrecord.com/feed/",
        "category": "local_news",
        "display_name": "Camas-Washougal Post-Record",
        "priority": 2,
    },
    {
        "source_id": "seasonal:pnw",
        "source_type": "seasonal",
        "url": None,
        "category": "seasonal",
        "display_name": "Pantopus Seasonal",
        "priority": 2,
    },
    # --- P3: Enrichment (community, sports) ---
    {
        "source_id": "google_news:trimet",
        "source_type": "google_news",
        "url": "TriMet Portland transit -crime -shooting -election",
        "category": "community_resource",
        "display_name": "TriMet Updates",
        "priority": 3,
    },
    {
        "source_id": "google_news:pdx_airport",
        "source_type": "google_news",
        "url": "PDX airport Portland updates",
        "category": "community_resource",
        "display_name": "PDX Airport Updates",
        "priority": 3,
    },
    {
        "source_id": "google_news:trail_blazers",
        "source_type": "google_news",
        "url": "Portland Trail Blazers -odds -betting -fantasy -player props",
        "category": "sports",
        "display_name": "Trail Blazers News",
        "priority": 3,
    },
    {
        "source_id": "google_news:timbers",
        "source_type": "google_news",
        "url": "Portland Timbers -odds -betting -fantasy",
        "category": "sports",
        "display_name": "Timbers News",
        "priority": 3,
    },
    {
        "source_id": "google_news:thorns",
        "source_type": "google_news",
        "url": "Portland Thorns -odds -betting -fantasy",
        "category": "sports",
        "display_name": "Thorns News",
        "priority": 3,
    },
    {
        "source_id": "google_news:portland_fire",
        "source_type": "google_news",
        "url": "Portland Fire WNBA -wildfire -fire department -odds -betting -fantasy",
        "category": "sports",
        "display_name": "Portland Fire News",
        "priority": 3,
    },
    {
        "source_id": "google_news:winterhawks",
        "source_type": "google_news",
        "url": "Portland Winterhawks -odds -betting -fantasy",
        "category": "sports",
        "display_name": "Winterhawks News",
        "priority": 3,
    },
    {
        "source_id": "google_news:world_cup_portland",
        "source_type": "google_news",
        "url": "FIFA World Cup 2026 Portland Seattle",
        "category": "sports",
        "display_name": "World Cup 2026",
        "priority": 3,
    },
]

PORTLAND_METRO_SOURCES: list[dict] = [
    # --- P1: Critical (safety/weather) ---
    {
        "source_id": "nws_alerts:portland_metro",
        "source_type": "nws_alerts",
        "url": "45.5152,-122.6784",
        "category": "weather",
        "display_name": "NWS Weather Alerts",
        "priority": 1,
    },
    # --- P2: Core (local news, events, seasonal) ---
    {
        "source_id": "rss:oregonlive_local",
        "source_type": "rss",
        "url": "https://www.oregonlive.com/arc/outboundfeeds/rss/category/portland/",
        "category": "local_news",
        "display_name": "OregonLive",
        "priority": 2,
    },
    {
        "source_id": "rss:kgw_local",
        "source_type": "rss",
        "url": "https://www.kgw.com/feeds/syndication/rss/news/local",
        "category": "local_news",
        "display_name": "KGW News",
        "priority": 2,
    },
    {
        "source_id": "rss:opb_news",
        "source_type": "rss",
        "url": "https://www.opb.org/arc/outboundfeeds/rss/?outputType=xml",
        "category": "local_news",
        "display_name": "OPB",
        "priority": 2,
    },
    {
        "source_id": "seasonal:pnw",
        "source_type": "seasonal",
        "url": None,
        "category": "seasonal",
        "display_name": "Pantopus Seasonal",
        "priority": 2,
    },
    # --- P3: Enrichment (community, sports) ---
    {
        "source_id": "google_news:trimet",
        "source_type": "google_news",
        "url": "TriMet Portland transit -crime -shooting -election",
        "category": "community_resource",
        "display_name": "TriMet Updates",
        "priority": 3,
    },
    {
        "source_id": "google_news:pdx_airport",
        "source_type": "google_news",
        "url": "PDX airport Portland updates",
        "category": "community_resource",
        "display_name": "PDX Airport Updates",
        "priority": 3,
    },
    {
        "source_id": "rss:oregon_metro",
        "source_type": "rss",
        "url": "https://www.oregonmetro.gov/metro-rss-feeds",
        "category": "community_resource",
        "display_name": "Oregon Metro",
        "active": False,
        "priority": 3,
    },
    {
        "source_id": "rss:city_portland",
        "source_type": "rss",
        "url": "https://www.portland.gov/news/rss",
        "category": "community_resource",
        "display_name": "City of Portland",
        "priority": 3,
    },
    {
        "source_id": "google_news:trail_blazers",
        "source_type": "google_news",
        "url": "Portland Trail Blazers -odds -betting -fantasy -player props",
        "category": "sports",
        "display_name": "Trail Blazers News",
        "priority": 3,
    },
    {
        "source_id": "google_news:timbers",
        "source_type": "google_news",
        "url": "Portland Timbers -odds -betting -fantasy",
        "category": "sports",
        "display_name": "Timbers News",
        "priority": 3,
    },
    {
        "source_id": "google_news:thorns",
        "source_type": "google_news",
        "url": "Portland Thorns -odds -betting -fantasy",
        "category": "sports",
        "display_name": "Thorns News",
        "priority": 3,
    },
    {
        "source_id": "google_news:portland_fire",
        "source_type": "google_news",
        "url": "Portland Fire WNBA -wildfire -fire department -odds -betting -fantasy",
        "category": "sports",
        "display_name": "Portland Fire News",
        "priority": 3,
    },
    {
        "source_id": "google_news:winterhawks",
        "source_type": "google_news",
        "url": "Portland Winterhawks -odds -betting -fantasy",
        "category": "sports",
        "display_name": "Winterhawks News",
        "priority": 3,
    },
    {
        "source_id": "google_news:seahawks",
        "source_type": "google_news",
        "url": "Seattle Seahawks -odds -betting -fantasy",
        "category": "sports",
        "display_name": "Seahawks News",
        "priority": 3,
    },
    {
        "source_id": "google_news:world_cup_portland",
        "source_type": "google_news",
        "url": "FIFA World Cup 2026 Portland Seattle",
        "category": "sports",
        "display_name": "World Cup 2026",
        "priority": 3,
    },
    {
        "source_id": "rss:kgw_sports",
        "source_type": "rss",
        "url": "https://www.kgw.com/feeds/syndication/rss/sports",
        "category": "sports",
        "display_name": "KGW Sports",
        "priority": 3,
    },
]

_REGION_SOURCES: dict[str, list[dict]] = {
    "clark_county": CLARK_COUNTY_SOURCES,
    "portland_metro": PORTLAND_METRO_SOURCES,
}


def get_sources_for_region(region: str) -> list[dict]:
    """Return source configurations for the given region.

    Returns an empty list for unknown regions.
    """
    return list(_REGION_SOURCES.get(region, []))
