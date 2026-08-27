// ============================================================
// SCOUT on the web (Wave 5) — "before you sign".
//
// The one surface where the reader is NOT the person the data is about:
// they are considering an address somebody else currently lives at. So
// every assertion here is written against a specific way the page could
// mislead, not against "does it render":
//
//   * the two non-ready answers are DIFFERENT answers — collapsing
//     "we could not place that" into "you are not in the U.S." once told
//     every US user during a geocoder outage that the product was not
//     for them;
//   * the rent verdict never appears without the unit size it judged,
//     because "below band" against the wrong band reads as a good deal;
//   * `because` and `scope_note` render in full, never clamped — a
//     question without its reason is a checklist off the internet, and
//     the scope note is the page's account of where the address went;
//   * the ask list is data-driven, so a question added server-side can
//     never silently vanish;
//   * every fact block degrades independently;
//   * the typed address never enters a URL and never survives a remount.
// ============================================================

import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import * as api from '@pantopus/api';
import type { ScoutReport } from '@pantopus/api';
import Scout from '@/components/place/scout/Scout';

const push = jest.fn();
const replace = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace, prefetch: jest.fn() }),
  useParams: () => ({}),
  usePathname: () => '/app/place/scout',
}));

const getScoutReport = api.scout.getScoutReport as jest.Mock;
const getAuthToken = api.getAuthToken as jest.Mock;

// The client rejects with a PLAIN OBJECT, never an Error (client.ts does
// `Promise.reject({…})`), so a test that rejects with `new Error(...)`
// would pass against a handler that drops the server message entirely.
function rejection(message: string, code?: string, statusCode?: number) {
  return { message, code, statusCode };
}

const ADDRESS = '1421 SE Oak St, Portland, OR';

const FULL: ScoutReport = {
  place: { address: '1421 SE Oak St', city: 'Portland', state: 'OR', zipcode: '97214' },
  flood: { zone: 'AE', in_sfha: true },
  flood_cost: {
    premium_p25: 480,
    premium_median: 760,
    premium_p75: 1240,
    policy_count: 128,
    scope: 'census tract',
    note: 'Real policies near this address. A benchmark, not a quote.',
  },
  environment: {
    radon: { radon_zone: 1, lead_paint_risk: 'likely', year_built: 1961 },
    water: {
      utility_name: 'Portland Water Bureau', pws_id: 'OR41000', violation_count: 2, recent_health_violations: true,
    },
  },
  rent: {
    band_low: 1600,
    band_high: 1920,
    period: 'FY 2026',
    asking_rent: 2400,
    bedrooms: 2,
    bedrooms_stated: true,
    position: 'above_band',
    scope: 'county',
  },
  ask_before_you_sign: [
    {
      id: 'flood_insurance_required',
      question: 'Who pays for flood insurance here, and what does it cost this year?',
      because: 'This address sits in FEMA flood zone AE, where a federally backed mortgage requires flood insurance.',
      source: 'FEMA National Flood Hazard Layer',
    },
    {
      id: 'whats_changed',
      question: 'What has been repaired or replaced in the last five years, and is there paperwork?',
      because: 'Roof, heating, water heater and electrical panel are the expensive four, and their age predicts what you will spend.',
      source: null,
    },
  ],
  scope_note:
    'Everything here describes the property and the area from public records. '
    + 'Nothing about the people who live there is shown, and nobody at the address is told you looked. '
    + 'Answering means looking the address up with our mapping provider, then asking public agencies '
    + '— FEMA, the Census Bureau and the EPA — what they publish about that location.',
};

function report(overrides: Partial<ScoutReport> = {}): ScoutReport {
  return { ...FULL, ...overrides };
}

async function runLookup(extra?: () => void) {
  render(<Scout />);
  fireEvent.change(screen.getByLabelText(/the address you are considering/i), { target: { value: ADDRESS } });
  extra?.();
  fireEvent.click(screen.getByRole('button', { name: /show me what to ask/i }));
}

beforeEach(() => {
  jest.clearAllMocks();
  getAuthToken.mockReturnValue('test-token');
});

// ── The two non-ready answers ───────────────────────────────

describe('an address we could not place is not a geographic denial', () => {
  it('says what would help, and never that the reader is outside the U.S.', async () => {
    getScoutReport.mockResolvedValue({
      status: 'could_not_place',
      message: 'We could not find that address — try adding the city and state',
    });
    await runLookup();

    expect(await screen.findByText(/could not find that address/i)).toBeInTheDocument();
    // Appears in both the server's message and our follow-up line.
    expect(screen.getAllByText(/city and state/i).length).toBeGreaterThan(0);
    // The exact laundering that shipped on the sibling surface.
    expect(screen.queryByText(/U\.S\.-only/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/outside the U\.S\./i)).not.toBeInTheDocument();
  });

  it('an address genuinely outside the U.S. still gets the geographic answer', async () => {
    getScoutReport.mockResolvedValue({ status: 'unsupported_region', message: 'Scout is U.S.-only for now' });
    await runLookup();

    expect(await screen.findByText(/U\.S\.-only for now/i)).toBeInTheDocument();
    expect(screen.queryByText(/city and state/i)).not.toBeInTheDocument();
  });
});

// ── The question list is the product ────────────────────────

describe('the question list', () => {
  it('renders a question the client has never seen before', async () => {
    // Switching on the ids this build knows is how a question added
    // server-side silently disappears.
    getScoutReport.mockResolvedValue({
      status: 'ready',
      scout: report({
        ask_before_you_sign: [{
          id: 'a_brand_new_ask_id',
          question: 'Is there a working carbon-monoxide alarm on every floor?',
          because: 'A fact we invented for this test, which the client cannot possibly know about.',
          source: 'Some registry',
        }],
      }),
    });
    await runLookup();

    expect(await screen.findByText(/carbon-monoxide alarm/i)).toBeInTheDocument();
    expect(screen.getByText(/a fact we invented for this test/i)).toBeInTheDocument();
  });

  it('renders `because` in full, never clamped', async () => {
    getScoutReport.mockResolvedValue({ status: 'ready', scout: report() });
    await runLookup();

    // The same sentence appears in the flood card too, so scope to the
    // ask row — the flood card's copy is not what this pins.
    const askRow = (await screen.findByText(/who pays for flood insurance/i)).closest('li') as HTMLElement;
    const because = within(askRow).getByText(/federally backed mortgage requires flood insurance/i);
    expect(because.className).not.toMatch(/line-clamp|truncate|max-h-/);
  });

  it('a null source renders no source element, and never the string "null"', async () => {
    getScoutReport.mockResolvedValue({ status: 'ready', scout: report() });
    await runLookup();

    const row = (await screen.findByText(/what has been repaired or replaced/i)).closest('li') as HTMLElement;
    expect(within(row).queryByText('null')).not.toBeInTheDocument();
    expect(row.textContent).not.toMatch(/\bnull\b/);
    // The sibling row DOES have a source, so this is a real distinction.
    const sourced = (await screen.findByText(/who pays for flood insurance/i)).closest('li') as HTMLElement;
    expect(within(sourced).getByText('FEMA National Flood Hazard Layer')).toBeInTheDocument();
  });
});

// ── The rent verdict ────────────────────────────────────────

describe('the rent verdict never stands alone', () => {
  it('names the unit size it judged', async () => {
    getScoutReport.mockResolvedValue({ status: 'ready', scout: report() });
    await runLookup();

    expect(await screen.findByText(/above the band/i)).toBeInTheDocument();
    expect(screen.getByText(/2-bedroom/)).toBeInTheDocument();
  });

  it('a studio is never described as a 2-bedroom', async () => {
    // The backend bug this guards was a studio judged against the
    // county's 2-bedroom band. If the count ever stops travelling with
    // the verdict, this is what catches it on the client.
    getScoutReport.mockResolvedValue({
      status: 'ready',
      scout: report({
        rent: {
          band_low: 1200, band_high: 1440, period: 'FY 2026', asking_rent: 1400,
          bedrooms: 0, bedrooms_stated: true, position: 'in_band', scope: 'county',
        },
      }),
    });
    await runLookup();

    // Scope to the rent card. A bare findByText(/studio/i) matches the
    // form's own <option>Studio</option> on the first poll, resolves
    // against it, and then fails `toBeInTheDocument` once the form
    // unmounts — passing or failing for reasons unrelated to the card.
    const rentCard = (await screen.findByText(/HUD fair market rent/i)).closest('div.bg-app-surface') as HTMLElement;
    expect(rentCard.textContent).toMatch(/studio/i);
    expect(rentCard.textContent).not.toMatch(/2-bedroom/);
  });

  it('says so when the bedroom count was ours rather than the reader’s', async () => {
    getScoutReport.mockResolvedValue({
      status: 'ready',
      scout: report({ rent: { ...FULL.rent!, bedrooms_stated: false } }),
    });
    await runLookup();

    expect(await screen.findByText(/we assumed 2-bedroom because you did not say/i)).toBeInTheDocument();
  });
});

// ── Independent degradation ─────────────────────────────────

describe('every fact block degrades on its own', () => {
  it('renders the flood cost even when the flood zone is missing', async () => {
    // FEMA NFHL down, tract benchmark warm — a real, reachable state.
    // Gating the cost on the zone would hide a genuine dollar figure.
    getScoutReport.mockResolvedValue({ status: 'ready', scout: report({ flood: null }) });
    await runLookup();

    expect(await screen.findByText(/what flood insurance costs here/i)).toBeInTheDocument();
    expect(screen.queryByText(/FEMA zone/i)).not.toBeInTheDocument();
  });

  it('with every fact block null, renders the questions and the note and nothing else', async () => {
    getScoutReport.mockResolvedValue({
      status: 'ready',
      scout: report({
        flood: null, flood_cost: null, rent: null, environment: { radon: null, water: null },
      }),
    });
    await runLookup();

    expect(await screen.findByText(/what has been repaired or replaced/i)).toBeInTheDocument();
    expect(screen.getByTestId('scout-scope-note')).toBeInTheDocument();
    // No empty cards, no zeroes standing in for absent facts.
    expect(screen.queryByText(/^Rent$/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Environment$/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/\$0\b/)).not.toBeInTheDocument();
  });
});

// ── The scope note ──────────────────────────────────────────

describe('the scope note', () => {
  it('renders verbatim and unclamped', async () => {
    getScoutReport.mockResolvedValue({ status: 'ready', scout: report() });
    await runLookup();

    const note = await screen.findByTestId('scout-scope-note');
    expect(note.textContent).toContain(FULL.scope_note);
    expect(note.innerHTML).not.toMatch(/line-clamp|truncate|max-h-|sr-only/);
    expect(note.closest('details')).toBeNull();
  });
});

// ── What must never happen to the typed address ─────────────

describe('the typed address stays out of the URL and out of any cache', () => {
  it('is never pushed into a route', async () => {
    getScoutReport.mockResolvedValue({ status: 'ready', scout: report() });
    await runLookup();
    await screen.findByText(/who pays for flood insurance/i);

    for (const call of push.mock.calls) {
      expect(String(call[0])).not.toContain('Oak St');
    }
    for (const anchor of Array.from(document.querySelectorAll('a[href]'))) {
      expect(anchor.getAttribute('href')).not.toContain('Oak');
    }
  });

  it('does not survive a remount', async () => {
    // The assertion that fails if someone moves this container onto
    // react-query: a cache key on a typed address is itself a record of
    // the lookup, which is what this surface promises not to keep.
    getScoutReport.mockResolvedValue({ status: 'ready', scout: report() });
    const { unmount } = render(<Scout />);
    fireEvent.change(screen.getByLabelText(/the address you are considering/i), { target: { value: ADDRESS } });
    fireEvent.click(screen.getByRole('button', { name: /show me what to ask/i }));
    await screen.findByText(/who pays for flood insurance/i);
    expect(getScoutReport).toHaveBeenCalledTimes(1);

    unmount();
    jest.clearAllMocks();
    render(<Scout />);

    // Back to the empty form, and nothing replayed from a cache.
    expect(screen.getByLabelText(/the address you are considering/i)).toHaveValue('');
    expect(screen.queryByText(/who pays for flood insurance/i)).not.toBeInTheDocument();
    expect(getScoutReport).not.toHaveBeenCalled();
  });
});

// ── Failure modes say something true ────────────────────────

describe('when the request fails', () => {
  it('a 429 talks about the limit, not the connection', async () => {
    getScoutReport.mockRejectedValue(rejection('Too many requests', 'AI_RATE_LIMITED', 429));
    await runLookup();

    expect(await screen.findByText(/available this hour/i)).toBeInTheDocument();
    expect(screen.queryByText(/check the address/i)).not.toBeInTheDocument();
  });

  it('any other failure offers the retry that actually applies', async () => {
    getScoutReport.mockRejectedValue(rejection('boom', undefined, 500));
    await runLookup();

    expect(await screen.findByText(/couldn’t put that report together|couldn't put that report together/i)).toBeInTheDocument();
  });
});

// ── The auth gate ───────────────────────────────────────────

describe('the auth gate', () => {
  it('sends a signed-out visitor to login with a way back', async () => {
    getAuthToken.mockReturnValue(null);
    render(<Scout />);
    await waitFor(() => expect(replace).toHaveBeenCalled());
    expect(String(replace.mock.calls[0][0])).toContain('/login?redirectTo=');
  });
});
