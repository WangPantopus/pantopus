/**
 * T5.1 — Notifications V2 web page tests.
 *
 * Covers the 2-tab UI (All / Unread), the date section headers,
 * the unread highlight, and the mark-all-read text-button's
 * enable/disable behaviour. Mocks the API layer + react-query so
 * the page renders deterministically.
 */

import React from 'react';
import {
  render,
  screen,
  fireEvent,
  waitFor,
  within,
} from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// ── Mock next/navigation ────────────────────────────────────
const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

// ── Mock @pantopus/api ──────────────────────────────────────
const mockGetNotifications = jest.fn();
const mockMarkAsRead = jest.fn();
const mockMarkAllAsRead = jest.fn();

jest.mock('@pantopus/api', () => ({
  getAuthToken: () => 'fake-token',
  notifications: {
    getNotifications: (...args: unknown[]) => mockGetNotifications(...args),
    markAsRead: (...args: unknown[]) => mockMarkAsRead(...args),
    markAllAsRead: () => mockMarkAllAsRead(),
  },
}));

// ── Mock socket context ─────────────────────────────────────
jest.mock('@/contexts/SocketContext', () => ({
  useSocket: () => null,
}));

// ── Mock query-keys ─────────────────────────────────────────
jest.mock('@/lib/query-keys', () => ({
  queryKeys: {
    notifications: () => ['notifications'],
  },
}));

import NotificationsPage from '../../src/app/(app)/app/notifications/page';

function renderWithProviders(ui: React.ReactElement) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
    },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

const todayIso = new Date().toISOString();
const yesterdayIso = new Date(Date.now() - 25 * 60 * 60 * 1000).toISOString();

const populatedResponse = {
  notifications: [
    {
      id: 'n1',
      type: 'reply',
      title: 'Maria replied to your gig',
      body: 'Sounds great — can we move it to Saturday?',
      icon: null,
      link: '/post/p_1',
      is_read: false,
      created_at: todayIso,
      user_id: 'u_me',
    },
    {
      id: 'n2',
      type: 'gig',
      title: 'New gig near you',
      body: '$80 gig 0.4mi',
      icon: null,
      link: '/gig/g_1',
      is_read: false,
      created_at: todayIso,
      user_id: 'u_me',
    },
    {
      id: 'n3',
      type: 'listing',
      title: 'Price drop on a saved listing',
      body: 'walnut credenza now $240',
      icon: null,
      link: '/listing/l_1',
      is_read: true,
      created_at: yesterdayIso,
      user_id: 'u_me',
    },
  ],
  unreadCount: 2,
  hasMore: false,
};

const emptyResponse = {
  notifications: [],
  unreadCount: 0,
  hasMore: false,
};

const allReadResponse = {
  notifications: populatedResponse.notifications.map((n) => ({ ...n, is_read: true })),
  unreadCount: 0,
  hasMore: false,
};

describe('NotificationsPage (T5.1)', () => {
  beforeEach(() => {
    mockGetNotifications.mockReset();
    mockMarkAsRead.mockReset();
    mockMarkAllAsRead.mockReset();
    mockPush.mockReset();
  });

  it('renders two tabs with All + Unread counts', async () => {
    mockGetNotifications.mockResolvedValue(populatedResponse);
    renderWithProviders(<NotificationsPage />);
    await waitFor(() => expect(mockGetNotifications).toHaveBeenCalled());
    expect(await screen.findByText('All')).toBeInTheDocument();
    expect(screen.getByText('Unread')).toBeInTheDocument();
    // Counts render alongside the tab labels.
    const allTab = screen.getByTestId('tab.all');
    const unreadTab = screen.getByTestId('tab.unread');
    expect(within(allTab).getByText('3')).toBeInTheDocument();
    expect(within(unreadTab).getByText('2')).toBeInTheDocument();
  });

  it('renders Today + Earlier date section headers (rendered uppercase via CSS)', async () => {
    mockGetNotifications.mockResolvedValue(populatedResponse);
    renderWithProviders(<NotificationsPage />);
    // The shell renders the literal string "Today" with a CSS `uppercase`
    // class — match the underlying text, not the visually-rendered one.
    expect(await screen.findByText('Today')).toBeInTheDocument();
    expect(screen.getByText('Earlier')).toBeInTheDocument();
  });

  it('enables Mark all read when unread > 0', async () => {
    mockGetNotifications.mockResolvedValue(populatedResponse);
    renderWithProviders(<NotificationsPage />);
    const action = await screen.findByTestId('listOfRowsTopBarAction');
    expect(action).toHaveTextContent('Mark all read');
    await waitFor(() => expect(action).not.toBeDisabled());
  });

  it('disables Mark all read when unread == 0', async () => {
    mockGetNotifications.mockResolvedValue(allReadResponse);
    renderWithProviders(<NotificationsPage />);
    const action = await screen.findByTestId('listOfRowsTopBarAction');
    await waitFor(() => expect(action).toBeDisabled());
  });

  it('Mark all read calls the API and clears unread count', async () => {
    mockGetNotifications.mockResolvedValue(populatedResponse);
    mockMarkAllAsRead.mockResolvedValue({ ok: true });
    renderWithProviders(<NotificationsPage />);
    // Wait for the action to flip into enabled state — the page renders
    // with `isEnabled: false` until the first fetch resolves.
    await waitFor(() => {
      const action = screen.getByTestId('listOfRowsTopBarAction');
      expect(action).not.toBeDisabled();
    });
    fireEvent.click(screen.getByTestId('listOfRowsTopBarAction'));
    await waitFor(() => expect(mockMarkAllAsRead).toHaveBeenCalled());
  });

  it('renders an empty state on the Unread tab with a View all notifications CTA', async () => {
    // First call for All tab — populated. Second call after switching to
    // Unread — empty. Third — back to All when CTA fires.
    mockGetNotifications
      .mockResolvedValueOnce(populatedResponse)
      .mockResolvedValueOnce(emptyResponse)
      .mockResolvedValueOnce(populatedResponse);
    renderWithProviders(<NotificationsPage />);
    const unreadTab = await screen.findByTestId('tab.unread');
    fireEvent.click(unreadTab);
    expect(await screen.findByText(/all caught up/i)).toBeInTheDocument();
    expect(screen.getByText('View all notifications')).toBeInTheDocument();
  });
});
