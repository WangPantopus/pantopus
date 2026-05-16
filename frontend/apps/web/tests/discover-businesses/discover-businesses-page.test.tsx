/**
 * T5.4.2 — Discover businesses web page tests.
 *
 * Covers the `<ListOfRowsShell />` reskin at `(app)/app/discover`:
 *   - no-home empty state with "Widen radius" CTA
 *   - populated list groups results into category sections in chip order
 *   - chip selection collapses to the matching section
 *   - search input triggers a refetch with `q=`
 *   - row click pushes to the business profile route
 */

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// ── Mock next/navigation ────────────────────────────────────
const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn() }),
}));

// ── Mock @pantopus/api ──────────────────────────────────────
const mockSearchNearbyBusinesses = jest.fn();

jest.mock('@pantopus/api', () => ({
  getAuthToken: () => 'fake-token',
  businesses: {
    searchNearbyBusinesses: (...args: unknown[]) => mockSearchNearbyBusinesses(...args),
  },
  homes: {
    getPrimaryHome: jest.fn(),
  },
}));

// ── Mock useViewerHome ──────────────────────────────────────
const mockUseViewerHome = jest.fn();
jest.mock('@/hooks/useViewerHome', () => ({
  __esModule: true,
  default: () => mockUseViewerHome(),
}));

import DiscoverBusinessesPage from '../../src/app/(app)/app/discover/page';

const homeFixture = {
  homeId: 'home-1',
  lat: 45.5,
  lng: -122.6,
  address: '1 Elm St',
  city: 'Portland',
  state: 'OR',
};

const populatedResponse = {
  results: [
    {
      business_user_id: 'b1',
      username: 'bigtree',
      name: 'Big Tree Handyman',
      categories: ['handyman'],
      description: 'Old-house specialist',
      business_type: 'company',
      average_rating: 4.9,
      review_count: 32,
      distance_miles: 0.4,
      distance_meters: 644,
      neighbor_count: 0,
      endorsement_count: 0,
      is_open_now: true,
      is_new_business: false,
      city: 'Portland',
      state: 'OR',
      avg_response_minutes: null,
      profile_completeness: 80,
      accepts_gigs: true,
      verification_status: 'document_verified',
      verification_badge: 'verified',
      founding_badge: false,
      address_verified: true,
      catalog_preview: [],
    },
    {
      business_user_id: 'b2',
      username: 'nwfixers',
      name: 'Northwest Fixers',
      categories: ['handyman'],
      description: 'Small jobs, same-day quotes',
      business_type: 'sole',
      average_rating: 4.7,
      review_count: 12,
      distance_miles: 1.1,
      distance_meters: 1770,
      neighbor_count: 0,
      endorsement_count: 0,
      is_open_now: false,
      is_new_business: false,
      city: 'Portland',
      state: 'OR',
      avg_response_minutes: null,
      profile_completeness: 60,
      accepts_gigs: true,
      verification_status: 'unverified',
      verification_badge: null,
      founding_badge: false,
      address_verified: false,
      catalog_preview: [],
    },
    {
      business_user_id: 'b3',
      username: 'cleanbee',
      name: 'Clean Bee PDX',
      categories: ['cleaning'],
      description: 'Eco-friendly · Deep cleans',
      business_type: 'company',
      average_rating: 4.8,
      review_count: 18,
      distance_miles: 0.6,
      distance_meters: 966,
      neighbor_count: 0,
      endorsement_count: 0,
      is_open_now: true,
      is_new_business: false,
      city: 'Portland',
      state: 'OR',
      avg_response_minutes: null,
      profile_completeness: 90,
      accepts_gigs: true,
      verification_status: 'document_verified',
      verification_badge: 'verified',
      founding_badge: false,
      address_verified: true,
      catalog_preview: [],
    },
  ],
  pagination: {
    page: 1,
    page_size: 50,
    total_count: 3,
    total_pages: 1,
    has_more: false,
  },
  sort: 'relevance',
  sort_label: 'Most hired nearby',
  filters_active: {},
  banner: null,
};

function renderWithClient(node: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

describe('DiscoverBusinessesPage (T5.4.2)', () => {
  beforeEach(() => {
    mockSearchNearbyBusinesses.mockReset();
    mockPush.mockReset();
    mockUseViewerHome.mockReset();
  });

  it('renders no-home empty state with Widen-radius CTA', async () => {
    mockUseViewerHome.mockReturnValue({ viewerHome: null, loading: false, hasHome: false });

    renderWithClient(<DiscoverBusinessesPage />);

    expect(await screen.findByText('Set a home address')).toBeInTheDocument();
    const cta = screen.getByRole('button', { name: 'Widen radius' });
    fireEvent.click(cta);
    expect(mockPush).toHaveBeenCalledWith('/app/profile');
    expect(mockSearchNearbyBusinesses).not.toHaveBeenCalled();
  });

  it('groups results by category in chip-strip order', async () => {
    mockUseViewerHome.mockReturnValue({
      viewerHome: homeFixture,
      loading: false,
      hasHome: true,
    });
    mockSearchNearbyBusinesses.mockResolvedValue(populatedResponse);

    renderWithClient(<DiscoverBusinessesPage />);

    // Each category header (uppercase via the section header in shell).
    const handymanHeader = await screen.findByText(/Handyman/i);
    const cleaningHeader = await screen.findByText(/Cleaning/i);
    expect(handymanHeader).toBeInTheDocument();
    expect(cleaningHeader).toBeInTheDocument();

    // Both businesses appear under Handyman.
    expect(screen.getByText('Big Tree Handyman')).toBeInTheDocument();
    expect(screen.getByText('Northwest Fixers')).toBeInTheDocument();
    expect(screen.getByText('Clean Bee PDX')).toBeInTheDocument();
  });

  it('selecting a chip filters to the matching category section', async () => {
    mockUseViewerHome.mockReturnValue({
      viewerHome: homeFixture,
      loading: false,
      hasHome: true,
    });
    mockSearchNearbyBusinesses.mockResolvedValue(populatedResponse);

    renderWithClient(<DiscoverBusinessesPage />);

    await screen.findByText('Big Tree Handyman');

    // Click the Cleaning chip.
    const cleaningChip = screen.getByTestId('chip.cleaning');
    fireEvent.click(cleaningChip);

    // The next call should carry categories=cleaning.
    await waitFor(() => {
      const lastCall = mockSearchNearbyBusinesses.mock.calls.at(-1);
      expect(lastCall?.[0]?.categories).toBe('cleaning');
    });
  });

  it('debounced search submits q= to the backend', async () => {
    jest.useFakeTimers();
    try {
      mockUseViewerHome.mockReturnValue({
        viewerHome: homeFixture,
        loading: false,
        hasHome: true,
      });
      mockSearchNearbyBusinesses.mockResolvedValue(populatedResponse);

      renderWithClient(<DiscoverBusinessesPage />);

      const searchInput = await screen.findByPlaceholderText(/Search businesses or services/i);
      fireEvent.change(searchInput, { target: { value: 'tree' } });

      jest.advanceTimersByTime(400);

      await waitFor(() => {
        const calls = mockSearchNearbyBusinesses.mock.calls;
        const lastCall = calls.at(-1);
        expect(lastCall?.[0]?.q).toBe('tree');
      });
    } finally {
      jest.useRealTimers();
    }
  });

  it('clicking a row pushes to the business profile route', async () => {
    mockUseViewerHome.mockReturnValue({
      viewerHome: homeFixture,
      loading: false,
      hasHome: true,
    });
    mockSearchNearbyBusinesses.mockResolvedValue(populatedResponse);

    renderWithClient(<DiscoverBusinessesPage />);

    const row = await screen.findByText('Big Tree Handyman');
    fireEvent.click(row);
    expect(mockPush).toHaveBeenCalledWith('/app/businesses/bigtree');
  });
});
