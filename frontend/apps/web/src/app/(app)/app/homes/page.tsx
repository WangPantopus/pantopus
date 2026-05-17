// T6.3f / P14 — My homes (web). Refactored onto `<ListOfRowsShell />`
// so the iOS / Android avatar-first row roster has identity parity on
// web. Pending ownership claims still surface as a separate "Verification
// in progress" section above the verified-homes list; the per-row
// delete / leave actions move onto the home dashboard since the new
// shell has no row-level kebab on web.
//
// Backend: `GET /api/homes/my-homes` — `backend/routes/home.js:1464`.
//          `GET /api/homes/my-ownership-claims` — `backend/routes/homeOwnership.js:217`.

'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Home as HomeIcon, Plus } from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import type { Home } from '@pantopus/types';
import ListOfRowsShell from '@/components/list-of-rows/ListOfRowsShell';
import type {
  ListOfRowsState,
  RowModel,
  RowSection,
} from '@/components/list-of-rows/types';

type PendingClaim = {
  claim: { id: string; home_id: string; status: string };
  addressLine: string;
  cityLine: string;
};

function claimInProgress(status: string) {
  return status === 'under_review';
}

function roleLabel(home: Home): string {
  const occ = (home as { occupancy?: { role?: string } }).occupancy?.role;
  if ((home as { owner_id?: string | null }).owner_id) return 'Owner';
  switch (occ) {
    case 'owner':
      return 'Owner';
    case 'lease_resident':
      return 'Tenant';
    case 'household_member':
      return 'Housemate';
    case 'guest':
      return 'Guest';
    case 'admin':
    case 'manager':
      return 'Manager';
    default:
      return 'Member';
  }
}

export default function HomesPage() {
  const router = useRouter();
  const [homes, setHomes] = useState<Home[]>([]);
  const [pendingClaims, setPendingClaims] = useState<PendingClaim[]>([]);
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
      const [homesRes, claimsRes] = await Promise.all([
        api.homes.getMyHomes(),
        api.homeOwnership.getMyOwnershipClaims(),
      ]);
      const list = ((homesRes as Record<string, unknown>)?.homes as Home[]) ?? [];
      setHomes(list);
      const homeIds = new Set(list.map((h) => h.id));
      const claims = (claimsRes as { claims?: Array<{ id: string; home_id: string; status: string }> })?.claims ?? [];
      const pending = claims.filter(
        (c) => claimInProgress(c.status) && c.home_id && !homeIds.has(c.home_id),
      );
      const enriched: PendingClaim[] = await Promise.all(
        pending.map(async (claim) => {
          try {
            const prof = await api.homes.getPublicHomeProfile(claim.home_id);
            const h = prof.home;
            const addressLine = h.name || h.address || 'Home';
            const cityLine = [h.city, h.state, h.zipcode].filter(Boolean).join(', ');
            return { claim, addressLine, cityLine };
          } catch {
            return { claim, addressLine: 'Home', cityLine: 'Ownership verification in progress' };
          }
        }),
      );
      setPendingClaims(enriched);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to load homes');
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
    if (homes.length === 0 && pendingClaims.length === 0) {
      return {
        kind: 'empty',
        config: {
          icon: HomeIcon,
          headline: 'You don’t belong to any homes yet',
          subcopy:
            'Claim or join a verified home to unlock packages, bills, tasks, and member chat.',
          ctaTitle: 'Claim a home',
          onCta: () => router.push('/app/homes/new'),
        },
      };
    }

    const sections: RowSection[] = [];

    if (pendingClaims.length > 0) {
      const pendingRows: RowModel[] = pendingClaims.map((p) => ({
        id: `claim-${p.claim.id}`,
        title: p.addressLine,
        subtitle: p.cityLine,
        template: 'avatarKebab',
        leading: {
          kind: 'avatar',
          name: p.addressLine,
          identity: 'home',
          ringProgress: 0.3,
        },
        trailing: { kind: 'chevron' },
        chips: [
          {
            text: 'Under review',
            tint: { kind: 'status', variant: 'warning' },
          },
        ],
        onTap: () =>
          router.push(
            `/app/homes/${p.claim.home_id}/claim-owner/evidence?claimId=${encodeURIComponent(p.claim.id)}`,
          ),
      }));
      sections.push({
        id: 'pending',
        header: 'Verification in progress',
        rows: pendingRows,
      });
    }

    if (homes.length > 0) {
      const verifiedRows: RowModel[] = homes.map((h) => {
        const title = h.name || h.address || 'Unnamed home';
        const locality = [h.city, h.state].filter(Boolean).join(', ');
        const role = roleLabel(h);
        const subtitle = [role, locality].filter(Boolean).join(' · ');
        const isPrimary = !!(h as { is_primary_owner?: boolean }).is_primary_owner;
        return {
          id: h.id,
          title,
          subtitle,
          template: 'avatarKebab',
          leading: {
            kind: 'avatar',
            name: title,
            identity: 'home',
            ringProgress: 1.0,
          },
          trailing: { kind: 'chevron' },
          chips: isPrimary
            ? [
                {
                  text: 'Active home',
                  icon: HomeIcon,
                  tint: { kind: 'status', variant: 'home' },
                },
              ]
            : undefined,
          onTap: () => router.push(`/app/homes/${h.id}/dashboard`),
        };
      });
      sections.push({
        id: 'verified',
        header: pendingClaims.length > 0 ? 'Your homes' : undefined,
        rows: verifiedRows,
      });
    }

    return { kind: 'loaded', sections };
  }, [loading, error, homes, pendingClaims, router]);

  const banner = useMemo(() => {
    if (homes.length === 0) return undefined;
    return {
      icon: HomeIcon,
      title: homes.length === 1 ? '1 home you belong to' : `${homes.length} homes you belong to`,
      subtitle: 'Tap any home to jump into that household',
      tint: 'home' as const,
    };
  }, [homes.length]);

  return (
    <ListOfRowsShell
      title="My homes"
      state={state}
      onRefresh={load}
      banner={banner}
      fab={{
        icon: Plus,
        accessibilityLabel: 'Claim a home',
        variant: { kind: 'secondaryCreate' },
        tint: 'home',
        onClick: () => router.push('/app/homes/new'),
      }}
    />
  );
}
