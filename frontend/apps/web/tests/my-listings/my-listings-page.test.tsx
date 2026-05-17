/**
 * T6.3f / P14 — My listings web page tests. Validates the four states
 * (loading / empty / loaded / error), tab bucketing (Active includes
 * pending_pickup), the chip-meta row (views · offers · status), and
 * the canonical-create FAB CTA.
 */

import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

const mockGetMyListings = jest.fn();

jest.mock('@pantopus/api', () => ({
  getAuthToken: () => 'fake-token',
  listings: {
    getMyListings: (...args: unknown[]) => mockGetMyListings(...args),
  },
}));

import MyListingsPage from '../../src/app/(app)/app/my-listings/page';

beforeEach(() => {
  jest.clearAllMocks();
});

const sampleListings = [
  {
    id: 'l1',
    title: 'Mid-century credenza',
    price: 250,
    is_free: false,
    status: 'active',
    media_urls: ['https://x/y.jpg'],
    view_count: 2400,
    active_offer_count: 5,
    created_at: '2026-05-15T08:00:00Z',
  },
  {
    id: 'l2',
    title: 'Reserved iPad',
    price: 520,
    is_free: false,
    status: 'pending_pickup',
    media_urls: [],
    view_count: 120,
    active_offer_count: 3,
    created_at: '2026-05-14T08:00:00Z',
  },
  {
    id: 'l3',
    title: 'Pyrex bowl set',
    price: 45,
    is_free: false,
    status: 'sold',
    media_urls: ['https://x/p.jpg'],
    view_count: 89,
    active_offer_count: 0,
    created_at: '2026-04-01T08:00:00Z',
  },
];

describe('My listings page (T6.3f)', () => {
  it('renders the empty state when zero listings', async () => {
    mockGetMyListings.mockResolvedValue({ listings: [] });
    render(<MyListingsPage />);
    await waitFor(() => {
      expect(screen.getByText('No active listings')).toBeInTheDocument();
    });
    expect(screen.getAllByRole('button', { name: /List something/i }).length).toBeGreaterThan(0);
  });

  it('renders active + pending_pickup rows on the Active tab with the chip-meta line', async () => {
    mockGetMyListings.mockResolvedValue({ listings: sampleListings });
    render(<MyListingsPage />);
    await waitFor(() => {
      expect(screen.getByText('Mid-century credenza')).toBeInTheDocument();
    });
    expect(screen.getByText('Reserved iPad')).toBeInTheDocument();
    expect(screen.queryByText('Pyrex bowl set')).not.toBeInTheDocument();
    // Chip-meta line is present on the first row.
    expect(screen.getByText('2400 views')).toBeInTheDocument();
    expect(screen.getByText('5 offers')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Pickup pending')).toBeInTheDocument();
  });

  it('switches to the Sold tab and renders only sold rows', async () => {
    mockGetMyListings.mockResolvedValue({ listings: sampleListings });
    render(<MyListingsPage />);
    await waitFor(() => {
      expect(screen.getByText('Mid-century credenza')).toBeInTheDocument();
    });
    const soldTab = screen.getByRole('button', { name: /^Sold/i });
    fireEvent.click(soldTab);
    await waitFor(() => {
      expect(screen.getByText('Pyrex bowl set')).toBeInTheDocument();
    });
    expect(screen.queryByText('Mid-century credenza')).not.toBeInTheDocument();
  });

  it('surfaces the error state when the API throws', async () => {
    mockGetMyListings.mockRejectedValue(new Error('boom'));
    render(<MyListingsPage />);
    await waitFor(() => {
      expect(screen.getByText(/boom/i)).toBeInTheDocument();
    });
  });
});
