"""Tests for pipeline: relevance filter and deduplication."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock

import pytest

from src.models.queue_item import RawContentItem
from src.pipeline.dedup import compute_dedup_hash, is_duplicate
from src.pipeline.relevance_filter import filter_item


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _item(**overrides) -> RawContentItem:
    base = {
        "title": "New community garden opens in Vancouver",
        "body": "The city announced a new community garden project.",
        "source_url": "https://example.com/article/123",
        "category": "local_news",
        "source_id": "rss:columbian",
        "region": "clark_county",
        "published_at": datetime.now(timezone.utc) - timedelta(hours=1),
    }
    base.update(overrides)
    return RawContentItem(**base)


# ===========================================================================
# Relevance Filter Tests
# ===========================================================================

class TestRelevanceFilterPass:
    def test_clean_item_passes(self):
        passed, reason = filter_item(_item())
        assert passed is True
        assert reason is None

    def test_item_with_no_published_at_passes(self):
        passed, reason = filter_item(_item(published_at=None))
        assert passed is True
        assert reason is None

    def test_normal_mixed_case_title_passes(self):
        passed, reason = filter_item(_item(title="Vancouver Parks and Rec Update"))
        assert passed is True


class TestBlocklist:
    def test_blocklist_term_in_title(self):
        passed, reason = filter_item(_item(title="Man arrested for robbery downtown"))
        assert passed is False
        assert reason is not None
        assert reason.startswith("blocklist:")

    def test_blocklist_term_in_body_not_title(self):
        passed, reason = filter_item(_item(
            title="Downtown incident report released",
            body="Police say the suspect was arrested after a brief chase.",
        ))
        assert passed is False
        assert "blocklist:" in reason

    def test_blocklist_case_insensitive(self):
        passed, reason = filter_item(_item(title="SHOOTING reported near park"))
        assert passed is False
        assert "blocklist:shooting" in reason

    def test_politics_blocklist(self):
        passed, reason = filter_item(_item(title="Democrat wins local election by large margin"))
        assert passed is False
        assert "blocklist:" in reason

    def test_paywall_blocklist(self):
        passed, reason = filter_item(_item(title="Big story — subscribe to read the full article"))
        assert passed is False
        assert "blocklist:" in reason

    def test_obituary_blocklist(self):
        passed, reason = filter_item(_item(title="Longtime resident John Smith passed away at 85"))
        assert passed is False
        assert "blocklist:" in reason

    def test_word_boundary_no_false_positive(self):
        """Blocklist uses word boundaries: 'arrested' shouldn't match inside 'arrestedly'."""
        passed, reason = filter_item(_item(
            title="Charleston restaurants shine this spring",
            body="Charleston is charming in April.",
        ))
        # 'charged' should not match inside 'Charleston' / 'charming'
        assert passed is True
        assert reason is None

    def test_sports_shooting_percentage_passes(self):
        """Sports coverage often mentions 'shooting' (percentage/guard) — must not be blocked."""
        passed, reason = filter_item(_item(
            category="sports",
            title="Trail Blazers improve shooting percentage in second half",
            body="The team's shooting guard led scoring with a career high.",
        ))
        assert passed is True
        assert reason is None

    def test_sports_campaign_passes(self):
        """'Campaign' is normal sports vocabulary ('championship campaign')."""
        passed, reason = filter_item(_item(
            category="sports",
            title="Timbers look back on a memorable campaign",
            body="The squad reflects on their championship campaign.",
        ))
        assert passed is True
        assert reason is None

    def test_sports_still_blocks_real_crime(self):
        """Crime terms unrelated to sports vocabulary still block sports items."""
        passed, reason = filter_item(_item(
            category="sports",
            title="Player indicted on federal tax charges",
            body="Authorities announced the indictment on Monday.",
        ))
        assert passed is False
        assert reason is not None and reason.startswith("blocklist:")

    def test_sports_blocks_non_allowlisted_crime(self):
        """A crime term not in the sports allowlist still blocks sports items."""
        # "homicide" is a crime term with no innocuous sports meaning.
        passed, reason = filter_item(_item(
            category="sports",
            title="Former player linked to homicide investigation",
            body="Team officials declined to comment on the ongoing case.",
        ))
        assert passed is False
        assert reason == "blocklist:homicide"

    def test_sports_robbed_idiom_passes(self):
        """'Robbed of victory' is a common sports idiom, not a crime report."""
        passed, reason = filter_item(_item(
            category="sports",
            title="Timbers robbed of victory by late penalty call",
            body="The squad felt robbed after a controversial stoppage-time call.",
        ))
        assert passed is True
        assert reason is None

    def test_non_sports_robbed_still_blocks(self):
        """'Robbed' remains a blocklist term for non-sports coverage."""
        passed, reason = filter_item(_item(
            category="local_news",
            title="Downtown store robbed overnight",
            body="Police are reviewing surveillance footage.",
        ))
        assert passed is False
        assert reason == "blocklist:robbed"


class TestFreshness:
    def test_stale_local_news_rejected(self):
        old = datetime.now(timezone.utc) - timedelta(hours=100)
        passed, reason = filter_item(_item(published_at=old, category="local_news"))
        assert passed is False
        assert reason.startswith("stale:")

    def test_stale_event_rejected(self):
        old = datetime.now(timezone.utc) - timedelta(hours=100)
        passed, reason = filter_item(_item(published_at=old, category="event"))
        assert passed is False
        assert reason.startswith("stale:")

    def test_event_within_72h_passes(self):
        recent = datetime.now(timezone.utc) - timedelta(hours=50)
        passed, reason = filter_item(_item(published_at=recent, category="event"))
        assert passed is True

    def test_no_published_at_passes_freshness(self):
        passed, reason = filter_item(_item(published_at=None))
        assert passed is True


class TestContentQuality:
    def test_short_title_rejected(self):
        passed, reason = filter_item(_item(title="Hey"))
        assert passed is False
        assert reason == "quality:title_too_short"

    def test_all_caps_title_rejected(self):
        passed, reason = filter_item(_item(title="BREAKING NEWS FROM DOWNTOWN VANCOUVER"))
        assert passed is False
        assert reason == "quality:all_caps_title"

    def test_all_caps_non_alpha_passes(self):
        """Strings with no case distinction (e.g. all numbers) should pass."""
        passed, reason = filter_item(_item(title="2026-04-04 12:00:00"))
        assert passed is True

    def test_mixed_case_title_passes(self):
        passed, reason = filter_item(_item(title="Vancouver City Council Meets Tuesday"))
        assert passed is True

    def test_sports_betting_headline_rejected(self):
        passed, reason = filter_item(_item(
            category="sports",
            title="Trail Blazers best bets and player props for tonight",
            body="Latest odds, player props and betting preview.",
        ))
        assert passed is False
        assert reason == "quality:sports_low_signal"

    def test_regular_sports_update_passes(self):
        passed, reason = filter_item(_item(
            category="sports",
            title="Portland Thorns announce matchday transit partnership",
            body="TriMet rides are included with digital tickets for the home opener.",
        ))
        assert passed is True
        assert reason is None


# ===========================================================================
# Dedup Hash Tests
# ===========================================================================

class TestComputeDedupHash:
    def test_same_inputs_same_hash(self):
        a = _item()
        b = _item()
        assert compute_dedup_hash(a) == compute_dedup_hash(b)

    def test_different_url_different_hash(self):
        a = _item(source_url="https://example.com/1")
        b = _item(source_url="https://example.com/2")
        assert compute_dedup_hash(a) != compute_dedup_hash(b)

    def test_same_url_different_date_different_hash(self):
        a = _item(published_at=datetime(2026, 4, 1, tzinfo=timezone.utc))
        b = _item(published_at=datetime(2026, 4, 2, tzinfo=timezone.utc))
        assert compute_dedup_hash(a) != compute_dedup_hash(b)

    def test_no_url_uses_title(self):
        a = _item(source_url=None, title="Unique title A")
        b = _item(source_url=None, title="Unique title B")
        assert compute_dedup_hash(a) != compute_dedup_hash(b)

    def test_no_url_same_title_same_hash(self):
        a = _item(source_url=None, title="Same title here")
        b = _item(source_url=None, title="Same title here")
        assert compute_dedup_hash(a) == compute_dedup_hash(b)

    def test_hash_is_hex_string(self):
        h = compute_dedup_hash(_item())
        assert isinstance(h, str)
        assert len(h) == 64  # SHA-256 hex digest


# ===========================================================================
# is_duplicate Tests
# ===========================================================================

class TestIsDuplicate:
    def _mock_client(self, data=None, error=False):
        client = MagicMock()
        table = client.table.return_value
        select = table.select.return_value
        eq = select.eq.return_value
        filtered = eq.filter.return_value
        limit = filtered.limit.return_value

        if error:
            limit.execute.side_effect = Exception("DB error")
        else:
            result = MagicMock()
            result.data = data or []
            limit.execute.return_value = result

        return client

    def test_returns_true_when_hash_exists(self):
        client = self._mock_client(data=[{"id": "some-uuid"}])
        assert is_duplicate("abc123", client) is True

    def test_returns_false_when_hash_not_found(self):
        client = self._mock_client(data=[])
        assert is_duplicate("abc123", client) is False

    def test_returns_false_on_error(self):
        client = self._mock_client(error=True)
        assert is_duplicate("abc123", client) is False
