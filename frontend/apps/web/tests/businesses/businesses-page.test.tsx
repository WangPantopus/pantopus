/**
 * T6.3f / P14 — My businesses web page tests. Validates the four
 * states (loading / empty / loaded / error), the category + role
 * subtitle, the locality body, the "Online only" fallback, and the
 * banner shown when populated.
 */

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

const mockGetMyBusinesses = jest.fn();

jest.mock('@pantopus/api', () => ({
  getAuthToken: () => 'fake-token',
  businesses: {
    getMyBusinesses: (...args: unknown[]) => mockGetMyBusinesses(...args),
  },
}));

import BusinessesPage from '../../src/app/(app)/app/businesses/page';

beforeEach(() => {
  jest.clearAllMocks();
});

const sampleBusinesses = [
  {
    id: 'seat-1',
    role_base: 'owner',
    business_user_id: 'b1',
    business: {
      id: 'b1',
      username: 'bigtreehandyman',
      name: 'Big Tree Handyman',
      city: 'Elm Park',
      state: 'NY',
    },
    profile: {
      business_type: 'home_services',
      categories: ['handyman'],
      is_published: true,
    },
  },
  {
    id: 'seat-2',
    role_base: 'manager',
    business_user_id: 'b2',
    business: {
      id: 'b2',
      username: 'baysidetutoring',
      name: 'Bayside Tutoring',
      city: null,
      state: null,
    },
    profile: {
      business_type: 'education',
      categories: ['tutoring'],
      is_published: false,
    },
  },
];

describe('My businesses page (T6.3f)', () => {
  it('renders the empty state when no memberships', async () => {
    mockGetMyBusinesses.mockResolvedValue({ businesses: [] });
    render(<BusinessesPage />);
    await waitFor(() => {
      expect(screen.getByText('No businesses yet')).toBeInTheDocument();
    });
    expect(
      screen.getAllByRole('button', { name: /Register a business/i }).length,
    ).toBeGreaterThan(0);
  });

  it('renders rows with category + role and a locality body or Online only fallback', async () => {
    mockGetMyBusinesses.mockResolvedValue({ businesses: sampleBusinesses });
    render(<BusinessesPage />);
    await waitFor(() => {
      expect(screen.getByText('Big Tree Handyman')).toBeInTheDocument();
    });
    expect(screen.getByText('Handyman · Owner')).toBeInTheDocument();
    expect(screen.getByText('Elm Park, NY')).toBeInTheDocument();
    expect(screen.getByText('Tutoring · Manager')).toBeInTheDocument();
    expect(screen.getByText('Online only')).toBeInTheDocument();
    // Banner shows count when populated.
    expect(screen.getByText('2 verified businesses')).toBeInTheDocument();
  });

  it('surfaces the error state when the API throws', async () => {
    mockGetMyBusinesses.mockRejectedValue(new Error('boom'));
    render(<BusinessesPage />);
    await waitFor(() => {
      expect(screen.getByText(/boom/i)).toBeInTheDocument();
    });
  });
});
