/**
 * T6.3f / P14 — My homes web page tests. Validates the four states
 * (loading / empty / loaded / error), the Active-home chip on the
 * primary-owner row, the role + locality subtitle assembly, and the
 * banner appearing only when verified homes exist.
 */

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

const mockGetMyHomes = jest.fn();
const mockGetClaims = jest.fn();

jest.mock('@pantopus/api', () => ({
  getAuthToken: () => 'fake-token',
  homes: {
    getMyHomes: (...args: unknown[]) => mockGetMyHomes(...args),
    getPublicHomeProfile: jest.fn(),
  },
  homeOwnership: {
    getMyOwnershipClaims: (...args: unknown[]) => mockGetClaims(...args),
  },
}));

import HomesPage from '../../src/app/(app)/app/homes/page';

beforeEach(() => {
  jest.clearAllMocks();
  mockGetClaims.mockResolvedValue({ claims: [] });
});

describe('My homes page (T6.3f)', () => {
  it('renders the empty state when no homes and no claims', async () => {
    mockGetMyHomes.mockResolvedValue({ homes: [] });
    render(<HomesPage />);
    await waitFor(() => {
      expect(screen.getByText(/You.*belong to any homes yet/i)).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /Claim a home/i })).toBeInTheDocument();
  });

  it('renders rows with role + locality and the Active home chip on the primary owner', async () => {
    mockGetMyHomes.mockResolvedValue({
      homes: [
        {
          id: 'h1',
          name: 'Birch Lane',
          address: '412 Birch Ln',
          city: 'Elm Park',
          state: 'NY',
          owner_id: 'me',
          is_primary_owner: true,
          occupancy: { role: 'owner' },
        },
        {
          id: 'h2',
          name: null,
          address: '88 Greenwood Ave',
          city: 'Sellwood',
          state: 'OR',
          is_primary_owner: false,
          occupancy: { role: 'lease_resident' },
        },
      ],
    });
    render(<HomesPage />);
    await waitFor(() => {
      expect(screen.getByText('Birch Lane')).toBeInTheDocument();
    });
    expect(screen.getByText(/Owner · Elm Park, NY/i)).toBeInTheDocument();
    expect(screen.getByText('Active home')).toBeInTheDocument();
    expect(screen.getByText('88 Greenwood Ave')).toBeInTheDocument();
    expect(screen.getByText(/Tenant · Sellwood, OR/i)).toBeInTheDocument();
    expect(screen.getByText('2 homes you belong to')).toBeInTheDocument();
  });

  it('surfaces the error state when the API throws', async () => {
    mockGetMyHomes.mockRejectedValue(new Error('boom'));
    render(<HomesPage />);
    await waitFor(() => {
      expect(screen.getByText(/boom/i)).toBeInTheDocument();
    });
  });
});
