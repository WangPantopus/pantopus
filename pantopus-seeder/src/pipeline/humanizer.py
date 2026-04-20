"""OpenAI API integration for rewriting raw content into curator tone."""

from __future__ import annotations

import logging
import re
from datetime import date

try:
    import openai
except ImportError:
    openai = None  # type: ignore[assignment]

from src.config.constants import MAX_HUMANIZED_LENGTH

HUMANIZER_MODEL = "gpt-5.4-mini"
from src.sources.seasonal import SEASONAL_CALENDAR, get_active_seasons

log = logging.getLogger("seeder.pipeline.humanizer")

def _build_system_prompt(region_display_name: str = "") -> str:
    """Build the system prompt, optionally tailored to a specific region."""
    area = region_display_name if region_display_name else "a local community"
    return (
        f"You are a community content curator for a neighborhood platform in {area}. "
        "Your job has TWO parts:\n\n"
        "PART 1 — QUALITY GATE:\n"
        "First, decide if this content is worth sharing with local residents. "
        "Reply with exactly \"SKIP\" (nothing else) if the content is:\n"
        "- Not relevant or useful to people living in the area\n"
        "- Generic national/international news with no local angle\n"
        "- A simple listing, schedule, or standings page with no story\n"
        "- Clickbait, spam, or extremely low quality\n"
        "- About a city/region far away from the target area\n"
        "- Would make users think the app is buggy or low-effort\n\n"
        "PART 2 — WRITE THE POST (only if content passes the quality gate):\n"
        "Write a brief, engaging community update.\n\n"
        "Rules:\n"
        "- 2-3 sentences of factual content, slightly warm tone\n"
        "- Lead with the most actionable information (date, time, location, what to do)\n"
        "- Include specific details from the source\n"
        "- No exclamation marks unless genuinely exciting\n"
        '- No greetings ("Hey neighbors", "Good morning everyone")\n'
        "- No hashtags, no emoji\n"
        "- Never use first-person singular (I, my, me)\n"
        "- Do not editorialize or add information not present in the source\n\n"
        "ENGAGEMENT: After the main content, add a short casual question on a new line "
        "to invite locals to share their experience or opinion. Keep it natural and "
        "relevant to the topic — not forced or generic. Examples:\n"
        '  "Anyone else notice this?" / "Have you tried this spot?" / '
        '"How are you preparing?" / "What route are you taking instead?"\n\n'
        'End with a source attribution on its own line: "Source: [name]"\n'
        "Do NOT include URLs in the source line — just the source name."
    )


# Keep a default for backward compatibility and tests
SYSTEM_PROMPT = _build_system_prompt()

_GREETING_PATTERNS = re.compile(
    r"^\s*(?:hey|hello|hi|good morning|good evening|greetings)\b",
    re.IGNORECASE,
)

# Match first-person "I" followed by a verb/contraction, with both straight
# and curly (Unicode \u2019) apostrophes. The negative lookbehind prevents
# matching words like "AQI" or "AI".
_FIRST_PERSON_PATTERNS = re.compile(
    r"(?<![A-Za-z])"
    r"I"
    r"(?:\s|['\u2019]m|['\u2019]ve|['\u2019]d)"
)


_PNW_KEYWORDS = {"portland", "vancouver", "seattle", "tacoma", "eugene", "salem",
                  "olympia", "bend", "spokane", "boise", "clark_county",
                  "portland_metro", "wa", "or", "id"}


def _is_pnw_region(region: str) -> bool:
    """Check if a region slug likely refers to a Pacific Northwest area."""
    parts = set(region.lower().replace("-", "_").split("_"))
    return bool(parts & _PNW_KEYWORDS)


def _get_current_seasons() -> list[str]:
    """Return names of currently active PNW seasons."""
    today = date.today()
    return [s["name"] for s in get_active_seasons(today.month, today.day)]


_NO_CONTENT_PATTERNS = re.compile(
    r"no\s+(local|relevant|available|update|information|content|news|sports)",
    re.IGNORECASE,
)


def _validate_output(text: str) -> str | None:
    """Validate humanized text. Returns None if valid, or a rejection reason."""
    if not text:
        return "empty"

    if len(text) > MAX_HUMANIZED_LENGTH:
        return f"too_long:{len(text)}"

    if text.count("!") > 1:
        return "too_many_exclamations"

    greeting_match = _GREETING_PATTERNS.match(text)
    if greeting_match:
        return f"greeting:{greeting_match.group().strip().lower()}"

    if _FIRST_PERSON_PATTERNS.search(text):
        return "first_person"

    if _NO_CONTENT_PATTERNS.search(text):
        return "no_content_placeholder"

    return None


def humanize(
    raw_title: str,
    raw_body: str | None,
    source_url: str | None,
    source_display_name: str,
    category: str,
    region: str,
    openai_api_key: str,
    region_display_name: str = "",
) -> tuple[str | None, str | None]:
    """Rewrite raw content into a 2-4 sentence community update post.

    Returns (humanized_text, error_reason). On success error_reason is None.
    On failure humanized_text is None and error_reason explains why.
    """
    if openai is None:
        return None, "api_error:openai package not installed"

    today_str = date.today().isoformat()

    # Include seasonal context only for PNW regions
    season_line = ""
    if _is_pnw_region(region):
        seasons = _get_current_seasons()
        season_str = ", ".join(seasons) if seasons else "none"
        season_line = f"Current PNW season: {season_str}\n"

    display = region_display_name or region
    user_message = (
        f"Category: {category}\n"
        f"Region: {display}\n"
        f"{season_line}"
        f"Today's date: {today_str}\n\n"
        f"Title: {raw_title}\n"
        f"Body: {raw_body or 'Not available'}\n"
        f"Source URL: {source_url or 'Not available'}\n"
        f"Source name: {source_display_name}"
    )

    system_prompt = _build_system_prompt(region_display_name)

    try:
        client = openai.OpenAI(api_key=openai_api_key)
    except Exception as exc:
        return None, f"api_error:{exc}"

    messages = [{"role": "user", "content": user_message}]

    # First attempt
    text, api_err = _call_api(client, messages, system_prompt)
    if api_err:
        return None, api_err

    # AI quality gate — model returns "SKIP" for low-quality content
    if text and text.strip().upper() == "SKIP":
        log.info("AI quality gate rejected: %s", raw_title[:60])
        return None, "ai_quality_gate:skipped"

    reason = _validate_output(text)
    if reason is None:
        return text, None

    # Retry once
    log.info("Humanizer validation failed (%s), retrying: %s", reason, raw_title[:60])
    messages.append({"role": "assistant", "content": text})
    messages.append({
        "role": "user",
        "content": (
            f"The previous output was rejected: {reason}. Please rewrite more concisely, "
            "following all rules strictly. 2-3 sentences maximum, no greetings, no first person."
        ),
    })

    text2, api_err2 = _call_api(client, messages, system_prompt)
    if api_err2:
        return None, api_err2

    reason2 = _validate_output(text2)
    if reason2 is None:
        return text2, None

    return None, f"validation_failed:{reason2}"


def _call_api(client, messages: list[dict], system_prompt: str = "") -> tuple[str | None, str | None]:
    """Call OpenAI and return (text, error). error is None on success."""
    try:
        # Prepend system message to the conversation
        full_messages = [{"role": "system", "content": system_prompt or SYSTEM_PROMPT}]
        full_messages.extend(messages)

        response = client.chat.completions.create(
            model=HUMANIZER_MODEL,
            max_completion_tokens=300,
            messages=full_messages,
        )
        text = (response.choices[0].message.content or "").strip()
        if not text:
            return None, "empty_response"
        return text, None
    except Exception as exc:
        log.warning("OpenAI API error: %s", exc, exc_info=True)
        return None, f"api_error:{exc}"
