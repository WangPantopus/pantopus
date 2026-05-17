// T6.3f / P14 — My businesses (web). Refactored onto `<ListOfRowsShell />`
// so the iOS / Android avatar-first business roster has identity parity
// on web. Row tap drills into the business dashboard; the FAB pushes
// to the existing create flow at `/app/businesses/new`.
//
// Backend: `GET /api/businesses/my-businesses` —
//          `backend/routes/businesses.js:682`.

'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Building2 } from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import ListOfRowsShell from '@/components/list-of-rows/ListOfRowsShell';
import type {
  ListOfRowsState,
  RowModel,
} from '@/components/list-of-rows/types';

type BusinessUser = {
  id: string;
  username?: string | null;
  name?: string | null;
  profile_picture_url?: string | null;
  city?: string | null;
  state?: string | null;
};

type BusinessProfile = {
  business_type?: string | null;
  categories?: string[] | null;
  is_published?: boolean | null;
  description?: string | null;
};

type Membership = {
  id: string;
  role_base?: string | null;
  business_user_id: string;
  business: BusinessUser;
  profile?: BusinessProfile | null;
};

function categoryLabel(profile?: BusinessProfile | null): string | null {
  const cats = profile?.categories;
  if (cats && cats.length > 0 && cats[0]) {
    return cats[0].replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase());
  }
  if (profile?.business_type) {
    return profile.business_type.replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase());
  }
  return null;
}

function roleLabel(roleBase?: string | null): string | null {
  if (!roleBase) return null;
  switch (roleBase) {
    case 'owner':
      return 'Owner';
    case 'admin':
      return 'Admin';
    case 'manager':
      return 'Manager';
    case 'staff':
      return 'Staff';
    case 'viewer':
      return 'Viewer';
    case 'editor':
      return 'Editor';
    default:
      return roleBase.charAt(0).toUpperCase() + roleBase.slice(1);
  }
}

export default function BusinessesPage() {
  const router = useRouter();
  const [businesses, setBusinesses] = useState<Membership[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const token = getAuthToken();
      if (!token) {
        router.push('/login');
        return;
      }
      const res = (await api.businesses.getMyBusinesses()) as { businesses?: Membership[] };
      setBusinesses(res.businesses ?? []);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to load businesses');
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    load();
  }, [load]);

  const state = useMemo<ListOfRowsState>(() => {
    if (loading) return { kind: 'loading' };
    if (error) return { kind: 'error', message: error };
    if (businesses.length === 0) {
      return {
        kind: 'empty',
        config: {
          icon: Building2,
          headline: 'No businesses yet',
          subcopy:
            'Create a business profile to take quotes inside Pantopus and earn the violet verified mark.',
          ctaTitle: 'Register a business',
          onCta: () => router.push('/app/businesses/new'),
        },
      };
    }
    const rows: RowModel[] = businesses.map((m) => {
      const title = m.business.name || m.business.username || 'Untitled business';
      const category = categoryLabel(m.profile);
      const role = roleLabel(m.role_base);
      const subtitle = [category, role].filter(Boolean).join(' · ');
      const locality = [m.business.city, m.business.state].filter(Boolean).join(', ');
      const body = locality || 'Online only';
      return {
        id: m.business_user_id,
        title,
        subtitle: subtitle || null,
        template: 'avatarKebab',
        leading: {
          kind: 'avatarWithBadge',
          name: title,
          imageURL: m.business.profile_picture_url ?? null,
          background: {
            kind: 'gradient',
            gradient: { start: '#7c3aed', end: '#6d28d9' },
          },
          size: 'large',
          verified: m.profile?.is_published === true,
        },
        trailing: { kind: 'chevron' },
        body,
        onTap: () => router.push(`/app/businesses/${m.business_user_id}/dashboard`),
      };
    });
    return { kind: 'loaded', sections: [{ id: 'my-businesses', rows }] };
  }, [loading, error, businesses, router]);

  const banner = useMemo(() => {
    if (businesses.length === 0) return undefined;
    return {
      icon: Building2,
      title:
        businesses.length === 1
          ? '1 verified business'
          : `${businesses.length} verified businesses`,
      subtitle: 'Tap any business to manage its inbox, gigs, and reviews',
      tint: 'business' as const,
    };
  }, [businesses.length]);

  return (
    <ListOfRowsShell
      title="My businesses"
      state={state}
      onRefresh={load}
      banner={banner}
      fab={{
        icon: Building2,
        accessibilityLabel: 'Register a business',
        variant: { kind: 'secondaryCreate' },
        tint: 'business',
        onClick: () => router.push('/app/businesses/new'),
      }}
    />
  );
}
