"""Keyword/category-based relevance filtering for fetched content items."""

from __future__ import annotations

import logging
import re
from datetime import datetime, timezone
from functools import lru_cache

from src.config.constants import BLOCKLIST_TERMS, FRESHNESS_HOURS
from src.models.queue_item import RawContentItem

log = logging.getLogger("seeder.pipeline.relevance_filter")

_LOW_SIGNAL_SPORTS_PATTERNS = (
    "best bets",
    "betting",
    "moneyline",
    "player props",
    "point spread",
    "spread pick",
    "fantasy",
    "game thread",
    "open thread",
    "live discussion",
)

# Sports coverage uses ordinary English that overlaps with crime/politics terms
# ("shooting percentage", "charged up", "championship campaign"). Skip these
# ambiguous blocklist terms when evaluating a sports item so legit coverage
# isn't silently dropped.
_SPORTS_BLOCKLIST_ALLOWLIST = frozenset({
    "shooting", "shootings",
    "charged", "charges",
    "campaign",
    "assault", "assaulted",
    "sentenced",
    "robbed",  # "robbed of victory" is a common sports idiom
})


@lru_cache(maxsize=None)
def _blocklist_pattern(term: str) -> re.Pattern[str]:
    """Compile a word-boundary regex for a blocklist term.

    Multi-word phrases are matched as a whole ("subscribe to read"); single
    words use \\b boundaries so "shooting" won't match "shooting percentage"
    being part of the term but WILL match the standalone word.
    """
    return re.compile(rf"(?<!\w){re.escape(term.lower())}(?!\w)", re.IGNORECASE)


def filter_item(item: RawContentItem) -> tuple[bool, str | None]:
    """Check if a content item passes relevance filtering.

    Returns (True, None) if the item passes all checks.
    Returns (False, reason_string) if the item fails any check.
    """
    # Pass 1 — Content blocklist (word-boundary match to avoid false positives
    # like "shooting percentage" for basketball coverage).
    title_text = item.title.lower()
    body_text = (item.body or "").lower()

    for term in BLOCKLIST_TERMS:
        # Skip ambiguous terms for sports coverage where they have innocuous
        # secondary meanings in sports writing.
        if item.category == "sports" and term.lower() in _SPORTS_BLOCKLIST_ALLOWLIST:
            continue
        pattern = _blocklist_pattern(term)
        if pattern.search(title_text) or pattern.search(body_text):
            log.debug("Rejected (blocklist:%s): %s", term, item.title)
            return False, f"blocklist:{term}"

    text_to_check = title_text
    if body_text:
        text_to_check += " " + body_text

    # Pass 2 — Freshness
    if item.published_at is not None:
        now = datetime.now(timezone.utc)
        pub = item.published_at if item.published_at.tzinfo else item.published_at.replace(tzinfo=timezone.utc)
        age_hours = (now - pub).total_seconds() / 3600
        limit = FRESHNESS_HOURS.get(item.category, FRESHNESS_HOURS["default"])
        if age_hours > limit:
            log.debug("Rejected (stale:%.0fh): %s", age_hours, item.title)
            return False, f"stale:{int(age_hours)}h"

    # Pass 3 — Minimum content quality
    if len(item.title) < 10:
        log.debug("Rejected (quality:title_too_short): %s", item.title)
        return False, "quality:title_too_short"

    if item.title == item.title.upper() and item.title != item.title.lower():
        log.debug("Rejected (quality:all_caps_title): %s", item.title)
        return False, "quality:all_caps_title"

    if item.body is not None:
        combined_len = len(item.title) + len(item.body)
        if combined_len < 20:
            log.debug("Rejected (quality:content_too_short): %s", item.title)
            return False, "quality:content_too_short"

    # Pass 4 — Reject generic/listing content
    title_lower = item.title.lower()
    generic_patterns = [
        "tv listings", "tv schedule", "scores are available",
        "standings, fixtures", "full highlights |",
    ]
    for pat in generic_patterns:
        if pat in title_lower:
            log.debug("Rejected (quality:generic_listing): %s", item.title)
            return False, "quality:generic_listing"

    # Pass 5 — reject betting previews and thread-like sports content
    if item.category == "sports":
        for pat in _LOW_SIGNAL_SPORTS_PATTERNS:
            if pat in text_to_check:
                log.debug("Rejected (quality:sports_low_signal): %s", item.title)
                return False, "quality:sports_low_signal"

    return True, None
