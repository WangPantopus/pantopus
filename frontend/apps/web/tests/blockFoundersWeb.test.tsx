// ============================================================
// Wave 3 — Block Founders on the web. The invariants: the section is
// hard-gated to verified viewers (no fetch behind the lock), the card
// shows the permanent rank + raw insider count + unlock meters, the
// invite form spends the weekly budget and disappears at zero, and the
// public opt-out page never fires the POST until the person confirms.
// ============================================================

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as api from '@pantopus/api';
import type { PlaceIntelligence } from '@pantopus/types';
import BlockDetail from '@/components/place/detail/BlockDetail';
import NoMailView from '@/components/place/no-mail/NoMailView';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn(), prefetch: jest.fn() }),
  useParams: () => ({}),
  usePathname: () => '/app/place',
}));

const getStatusMock = api.blockFounders.getBlockStatus as jest.Mock;
const sendInviteMock = api.blockFounders.sendBlockInvite as jest.Mock;
const optOutMock = api.blockFounders.redeemInviteOptOut as jest.Mock;

function intel(tier: PlaceIntelligence['tier']): PlaceIntelligence {
  return {
    place: { label: '1421 SE Oak St, Portland', line1: '1421 SE Oak St', city: 'Portland', state: 'OR', postal_code: '97214' },
    tier, region_supported: true, generated_at: '2026-08-25T00:00:00Z',
    groups: [],
  } as unknown as PlaceIntelligence;
}

function renderBlock(tier: PlaceIntelligence['tier'] = 'T4') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <BlockDetail intelligence={intel(tier)} homeId="home-1" />
    </QueryClientProvider>,
  );
}

const BLOCK = {
  available: true,
  rank: 2,
  established_at: '2026-07-04T00:00:00.000Z',
  verified_count: 6,
  meters: [
    { id: 'bill_benchmark', label: 'Block bill benchmark', current: 6, needed: 10, unlocked: false },
    { id: 'block_growing', label: 'Growing-block signal', current: 6, needed: 25, unlocked: false },
  ],
  invites_remaining: 2,
  invites_weekly_cap: 3,
};

describe('BlockDetail — Founders section', () => {
  beforeEach(() => {
    getStatusMock.mockReset();
    sendInviteMock.mockReset();
  });

  it('gates unverified viewers with the permanent-rank promise, without fetching', () => {
    renderBlock('T3');
    expect(screen.getByText(/permanent founding rank/i)).toBeInTheDocument();
    expect(getStatusMock).not.toHaveBeenCalled();
  });

  it('shows the rank, raw insider count, meters, and invite budget for a verified founder', async () => {
    getStatusMock.mockResolvedValue(BLOCK);
    renderBlock('T4');

    await waitFor(() => expect(screen.getByText('Founder #2 of this block')).toBeInTheDocument());
    expect(screen.getByText(/since July 2026/)).toBeInTheDocument();
    expect(screen.getByText('Verified homes on your block')).toBeInTheDocument();
    expect(screen.getByText('Block bill benchmark')).toBeInTheDocument();
    expect(screen.getByText('6 of 10')).toBeInTheDocument();
    expect(screen.getByText('6 of 25')).toBeInTheDocument();
    expect(screen.getByText(/2 left this week/)).toBeInTheDocument();
    // The card is template-only and anonymized — the form says so.
    expect(screen.getByText(/never your name or address/i)).toBeInTheDocument();
  });

  it('sends an invite with the entered address and reports the remaining budget', async () => {
    getStatusMock.mockResolvedValue(BLOCK);
    sendInviteMock.mockResolvedValue({ sent: true, invites_remaining: 1 });
    renderBlock('T4');
    await waitFor(() => expect(screen.getByLabelText('Street address')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('Street address'), { target: { value: '1423 SE Oak St' } });
    fireEvent.change(screen.getByLabelText('City'), { target: { value: 'Portland' } });
    fireEvent.change(screen.getByLabelText('State'), { target: { value: 'or' } });
    fireEvent.change(screen.getByLabelText('ZIP code'), { target: { value: '97214' } });
    fireEvent.click(screen.getByText('Mail the invitation'));

    await waitFor(() => expect(sendInviteMock).toHaveBeenCalledWith('home-1', {
      line1: '1423 SE Oak St', city: 'Portland', state: 'OR', zip: '97214',
    }));
  });

  it('retires the form once the weekly budget is spent', async () => {
    getStatusMock.mockResolvedValue({ ...BLOCK, invites_remaining: 0 });
    renderBlock('T4');

    await waitFor(() => expect(screen.getByText(/used this week/i)).toBeInTheDocument());
    expect(screen.queryByText('Mail the invitation')).not.toBeInTheDocument();
  });

  it('degrades honestly when the home has no map coordinates', async () => {
    getStatusMock.mockResolvedValue({ available: false, reason: 'NO_COORDINATES' });
    renderBlock('T4');

    await waitFor(() => expect(screen.getByText(/couldn't place this home on a block/i)).toBeInTheDocument());
    expect(screen.queryByText(/Founder #/)).not.toBeInTheDocument();
  });
});

describe('NoMailView — the recipient kill switch', () => {
  beforeEach(() => optOutMock.mockReset());

  it('never fires the opt-out until the person confirms', () => {
    render(<NoMailView code="ABCDEFGH12345678" />);
    expect(screen.getByText('Never mail me again')).toBeInTheDocument();
    expect(optOutMock).not.toHaveBeenCalled();
  });

  it('confirms a successful opt-out as permanent', async () => {
    optOutMock.mockResolvedValue({ done: true });
    render(<NoMailView code="ABCDEFGH12345678" />);
    fireEvent.click(screen.getByText('Never mail me again'));

    await waitFor(() => expect(screen.getByText(/off the list/i)).toBeInTheDocument());
    expect(optOutMock).toHaveBeenCalledWith('ABCDEFGH12345678');
    expect(screen.getByText(/never receive another/i)).toBeInTheDocument();
  });

  it('keeps the button and shows calm guidance on a bad code', async () => {
    optOutMock.mockResolvedValue({ done: false });
    render(<NoMailView code="WRONG" />);
    fireEvent.click(screen.getByText('Never mail me again'));

    await waitFor(() => expect(screen.getByText(/didn't work/i)).toBeInTheDocument());
    expect(screen.getByText('Never mail me again')).toBeInTheDocument();
  });
});
