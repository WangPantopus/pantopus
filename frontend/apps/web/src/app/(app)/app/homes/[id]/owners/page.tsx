'use client';

// Pantopus — P15 / T6.3g Owners screen on the shared `<ListOfRowsShell />`.
// Re-skin of the legacy bespoke owners list onto the shared shell. The
// page only owns the data fetch (via `@pantopus/api`) + the row
// projection; the shell renders the chrome. Each owner row renders as
// the avatar-first shape (40px AvatarWithBadge leading + name + role
// subtitle + verbose proof body + optional "You" chip + kebab
// trailing). Mirrors iOS `OwnersListView` and Android
// `OwnersListScreen` exactly.
//
// FAB routes to the existing `/app/homes/[id]/owners/invite` page
// (already wired to `homeOwnership.inviteCoOwner`); transfer
// ownership remains reachable from the home dashboard.

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  Clock,
  File as FileIcon,
  FileText,
  Shield,
  ShieldCheck,
  UserPlus,
} from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import { colors } from '@pantopus/theme';
import ListOfRowsShell from '@/components/list-of-rows/ListOfRowsShell';
import type {
  ListOfRowsState,
  RowChip,
  RowModel,
} from '@/components/list-of-rows/types';
import { toast } from '@/components/ui/toast-store';
import { confirmStore } from '@/components/ui/confirm-store';

// ─── Proof palette ──────────────────────────────────────────────

// Same proof bucket vocabulary as iOS `OwnerProofPalette.swift` and
// Android `OwnerProofPalette.kt`. Status precedence wins — `pending`
// always reads as Pending regardless of the verification tier the
// claim carries.
type OwnerProof = 'deed' | 'title' | 'document' | 'pending';

interface ProofTone {
  label: string;
  bodyLabel: string;
  Icon: typeof ShieldCheck;
  bg: string;
  fg: string;
}

const PROOF_TONES: Record<OwnerProof, ProofTone> = {
  deed: {
    label: 'Deed',
    bodyLabel: 'Deed on file',
    Icon: ShieldCheck,
    bg: colors.identity.home.bg,
    fg: colors.identity.home.color,
  },
  title: {
    label: 'Title',
    bodyLabel: 'Title on file',
    Icon: FileIcon,
    bg: colors.primary[50],
    fg: colors.primary[700],
  },
  document: {
    label: 'Document',
    bodyLabel: 'Document on file',
    Icon: FileText,
    bg: colors.semantic.warningBg,
    fg: colors.semantic.warning,
  },
  pending: {
    label: 'Pending',
    bodyLabel: 'Pending review',
    Icon: Clock,
    bg: colors.surface.sunken,
    fg: colors.text.strong,
  },
};

export function resolveProof(
  ownerStatus: string,
  verificationTier: string,
): OwnerProof {
  const status = ownerStatus.toLowerCase();
  if (status === 'pending') return 'pending';
  if (status === 'disputed' || status === 'revoked') return 'document';
  switch (verificationTier.toLowerCase()) {
    case 'legal':
    case 'strong':
      return 'deed';
    case 'standard':
      return 'title';
    default:
      return 'document';
  }
}

// Per-position avatar tone palette. Home-green for owner 1 (matches
// the screen's home identity), sky for owner 2, amber for owner 3,
// business-violet for owner 4. Beyond index 3 we wrap.
const OWNER_TONES: Array<{ start: string; end: string }> = [
  { start: colors.identity.home.color, end: colors.semantic.successBg },
  { start: colors.primary[500], end: colors.primary[700] },
  { start: colors.semantic.warning, end: colors.semantic.warningLight },
  { start: colors.identity.business.color, end: colors.identity.business.bg },
];

function toneAt(index: number): { start: string; end: string } {
  const len = OWNER_TONES.length;
  return OWNER_TONES[((index % len) + len) % len];
}

// ─── Page ──────────────────────────────────────────────────────

interface OwnerRecord {
  id: string;
  subject_type: 'user' | 'business' | 'trust';
  subject_id: string;
  owner_status: string;
  is_primary_owner: boolean;
  added_via?: string;
  verification_tier: string;
  created_at?: string;
  updated_at?: string;
  user?: {
    id: string;
    username?: string;
    name?: string;
    profile_picture_url?: string | null;
  } | null;
}

function displayName(owner: OwnerRecord): string {
  const name = owner.user?.name?.trim();
  if (name) return name;
  const username = owner.user?.username?.trim();
  if (username) return `@${username}`;
  const suffix = owner.subject_id.slice(-4);
  switch (owner.subject_type) {
    case 'business':
      return `Business · ${suffix}`;
    case 'trust':
      return `Trust · ${suffix}`;
    default:
      return `Owner · ${suffix}`;
  }
}

function roleSubtitle(args: {
  isPrimary: boolean;
  total: number;
  isPending: boolean;
}): string {
  if (args.isPending) return 'Invited · awaiting verification';
  if (args.total <= 1) return 'Sole owner';
  return args.isPrimary ? 'Primary owner' : 'Co-owner';
}

function OwnersContent() {
  const router = useRouter();
  const { id: homeId } = useParams<{ id: string }>();

  const [owners, setOwners] = useState<OwnerRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  // Best-effort: resolve the viewer's id so the row mapper can surface
  // the "You" chip. Skipped silently if the call fails — the screen
  // still renders, just without the "You" badge.
  useEffect(() => {
    void (async () => {
      try {
        const profile = await api.users.getMyProfile();
        const id = (profile as { user?: { id?: string } } | undefined)?.user?.id;
        if (id) setCurrentUserId(id);
      } catch {
        // Non-fatal — screen still renders.
      }
    })();
  }, []);

  const fetchOwners = useCallback(async () => {
    if (!homeId) return;
    setErrorMessage(null);
    try {
      const res = await api.homeOwnership.getHomeOwners(homeId);
      setOwners(((res as { owners?: OwnerRecord[] } | undefined)?.owners) || []);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load owners';
      setErrorMessage(message);
      toast.error('Failed to load owners');
    }
  }, [homeId]);

  useEffect(() => {
    setLoading(true);
    fetchOwners().finally(() => setLoading(false));
  }, [fetchOwners]);

  const handleRemove = useCallback(
    async (owner: OwnerRecord) => {
      const name = displayName(owner);
      const yes = await confirmStore.open({
        title: 'Remove owner?',
        description:
          `${name} will lose owner privileges. If other owners exist, ` +
          'removal may need quorum approval.',
        confirmLabel: 'Remove',
        variant: 'destructive',
      });
      if (!yes || !homeId) return;
      const previous = owners;
      setOwners((rows) => rows.filter((o) => o.id !== owner.id));
      try {
        const result = await api.homeOwnership.removeOwner(homeId, owner.id);
        if ((result as { quorum_action_id?: string }).quorum_action_id) {
          toast.success('Removal pending — needs co-owner approval.');
        } else {
          toast.success('Owner removed');
        }
      } catch {
        setOwners(previous);
        toast.error('Failed to remove owner');
      }
    },
    [homeId, owners],
  );

  const handleInvite = useCallback(() => {
    if (!homeId) return;
    router.push(`/app/homes/${homeId}/owners/invite`);
  }, [homeId, router]);

  const rows = useMemo<RowModel[]>(() => {
    const total = owners.length;
    return owners.map((owner, position) => {
      const proof = resolveProof(owner.owner_status, owner.verification_tier);
      const tone = toneAt(position);
      const tonePalette = PROOF_TONES[proof];
      const isYou = currentUserId !== null && currentUserId === owner.subject_id;
      const youChip: RowChip | undefined = isYou
        ? {
            text: 'You',
            tint: {
              kind: 'custom',
              background: colors.primary[50],
              foreground: colors.primary[700],
            },
          }
        : undefined;
      return {
        id: owner.id,
        title: displayName(owner),
        subtitle: roleSubtitle({
          isPrimary: owner.is_primary_owner,
          total,
          isPending: owner.owner_status.toLowerCase() === 'pending',
        }),
        template: 'avatarKebab',
        leading: {
          kind: 'avatarWithBadge',
          name: displayName(owner),
          imageURL: owner.user?.profile_picture_url || null,
          background: {
            kind: 'gradient',
            gradient: { start: tone.start, end: tone.end },
          },
          size: 'medium',
          verified: proof !== 'pending',
        },
        trailing: { kind: 'kebab' },
        onSecondary: () => void handleRemove(owner),
        body: tonePalette.bodyLabel,
        inlineChip: youChip,
      } satisfies RowModel;
    });
  }, [owners, currentUserId, handleRemove]);

  const state: ListOfRowsState = useMemo(() => {
    if (loading) return { kind: 'loading' };
    if (errorMessage) return { kind: 'error', message: errorMessage };
    if (rows.length === 0) {
      return {
        kind: 'empty',
        config: {
          icon: Shield,
          headline: 'No owners yet',
          subcopy:
            "Invite a spouse, sibling, or co-investor who's on the deed. " +
            "They'll upload proof and split the share with you.",
          ctaTitle: 'Invite an owner',
          onCta: handleInvite,
        },
      };
    }
    return {
      kind: 'loaded',
      sections: [{ id: 'owners', rows }],
      hasMore: false,
    };
  }, [loading, errorMessage, rows, handleInvite]);

  if (!homeId) return null;

  return (
    <ListOfRowsShell
      title="Owners"
      state={state}
      onRefresh={() => {
        void fetchOwners();
      }}
      fab={{
        icon: UserPlus,
        accessibilityLabel: 'Invite an owner',
        variant: { kind: 'secondaryCreate' },
        tint: 'home',
        onClick: handleInvite,
      }}
    />
  );
}

export default function OwnersPage() {
  return (
    <Suspense>
      <OwnersContent />
    </Suspense>
  );
}
