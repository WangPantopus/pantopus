// ============================================================
// Wave 2b — the rate watch on Money signals. The invariants:
// unverified viewers get the only-the-proven-resident gate, a set
// watch shows both averages with the delta chip, an open refi window
// reads as facts (never "refinance"), and no watch shows the form.
// ============================================================

import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as api from '@pantopus/api';
import type { PlaceIntelligence } from '@pantopus/types';
import MoneyDetail from '@/components/place/detail/MoneyDetail';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn(), prefetch: jest.fn() }),
  useParams: () => ({}),
  usePathname: () => '/app/place',
}));

const getWatchMock = api.recordWatch.getRecordWatch as jest.Mock;

function intel(tier: PlaceIntelligence['tier']): PlaceIntelligence {
  return {
    place: { label: '1421 SE Oak St, Portland', line1: '1421 SE Oak St', city: 'Portland', state: 'OR', postal_code: '97214' },
    tier, region_supported: true, generated_at: '2026-08-25T00:00:00Z',
    groups: [],
  } as unknown as PlaceIntelligence;
}

function renderMoney(tier: PlaceIntelligence['tier'] = 'T4') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MoneyDetail intelligence={intel(tier)} homeId="home-1" />
    </QueryClientProvider>,
  );
}

describe('RateWatchSection', () => {
  beforeEach(() => getWatchMock.mockReset());

  it('gates unverified viewers with the proven-resident promise, without fetching', async () => {
    renderMoney('T3');
    expect(screen.getByText(/only the proven resident can watch a home/i)).toBeInTheDocument();
    expect(getWatchMock).not.toHaveBeenCalled();
  });

  it('shows the form when no watch exists', async () => {
    getWatchMock.mockResolvedValue(null);
    renderMoney('T4');
    await waitFor(() => expect(screen.getByText(/Watch rates against your loan/i)).toBeInTheDocument());
    expect(screen.getByLabelText(/month your loan was recorded/i)).toBeInTheDocument();
    expect(screen.getByText(/not refinancing advice/i)).toBeInTheDocument();
  });

  it('shows both averages and the open-window chip as facts, never advice', async () => {
    getWatchMock.mockResolvedValue({
      id: 'w1', home_id: 'home-1', loan_recorded_month: '2023-03', baseline_rate: 6.6,
      created_at: '2026-08-01T00:00:00.000Z',
      evaluation: { baseline_rate: 6.6, current_rate: 5.7, current_as_of: '2026-08-20', delta_pp: -0.9, refi_window: true },
    });
    renderMoney('T4');

    await waitFor(() => expect(screen.getByText('0.90pp below your month')).toBeInTheDocument());
    expect(screen.getByText('6.60%')).toBeInTheDocument();
    expect(screen.getByText('5.70%')).toBeInTheDocument();
    expect(screen.getAllByText(/March 2023/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/you should refinance/i)).not.toBeInTheDocument();
  });
});
