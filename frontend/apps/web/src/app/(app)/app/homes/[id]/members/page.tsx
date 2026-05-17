'use client';

// T6.3a / P9 — Members per-home. Re-skin of the prior custom-card
// layout onto the shared `ListOfRowsShell` with three tabs (Members /
// Guests / Pending), home-green identity, and the same backend wires
// as iOS / Android:
//
//   - GET    /api/homes/:id/occupants  → bucket client-side into the
//                                        three tabs
//   - POST   /api/homes/:id/invite     → invite wizard submit
//   - DELETE /api/homes/:id/members/:userId → remove (member) / cancel
//                                            (pending, when userId is
//                                            resolved)
//
// Backend route refs:
//   backend/routes/home.js:3705   — list occupants
//   backend/routes/home.js:5662   — invite
//   backend/routes/homeIam.js:512 — remove
//
// Parity row added in docs/mobile-parity-audit.md.

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  AlertCircle,
  Clock,
  FileText,
  Home,
  Lock,
  Mailbox,
  Shield,
  ShieldCheck,
  User,
  UserPlus,
  Users,
  X,
} from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import { toast } from '@/components/ui/toast-store';
import { confirmStore } from '@/components/ui/confirm-store';
import { ListOfRowsShell } from '@/components/list-of-rows';
import type {
  ListOfRowsState,
  ListOfRowsTab,
  RowModel,
} from '@/components/list-of-rows';

// ─── Role + bucket helpers ────────────────────────────────────────

type MemberRoleKey =
  | 'owner'
  | 'admin'
  | 'manager'
  | 'member'
  | 'restricted'
  | 'tenant'
  | 'guest';

// Feature-local palette — same documented exception as
// `species-palette.ts` for Pets. Chip background/foreground are raw
// CSS values because the shared `RowCard` consumes them as inline
// styles. Hex values come straight from the design source:
// `members-frames.jsx:57-64` (ROLE block).
const ROLE_META: Record<
  MemberRoleKey,
  { label: string; icon: typeof Home; chipBg: string; chipFg: string }
> = {
  owner: { label: 'Owner', icon: Home, chipBg: '#dcfce7', chipFg: '#15803d' },
  admin: { label: 'Admin', icon: Shield, chipBg: '#f0f9ff', chipFg: '#0369a1' },
  manager: { label: 'Manager', icon: ShieldCheck, chipBg: '#dbeafe', chipFg: '#1d4ed8' },
  member: { label: 'Member', icon: User, chipBg: '#f3f4f6', chipFg: '#374151' },
  restricted: { label: 'Limited', icon: Lock, chipBg: '#fef3c7', chipFg: '#92400e' },
  tenant: { label: 'Tenant', icon: FileText, chipBg: '#dbeafe', chipFg: '#1d4ed8' },
  guest: { label: 'Guest', icon: Clock, chipBg: '#e2e8f0', chipFg: '#334155' },
};

const GUEST_ROLES: Set<MemberRoleKey> = new Set(['guest']);

function parseRole(raw: string | null | undefined): MemberRoleKey {
  switch ((raw || '').toLowerCase()) {
    case 'owner':
      return 'owner';
    case 'admin':
      return 'admin';
    case 'manager':
      return 'manager';
    case 'member':
      return 'member';
    case 'restricted_member':
    case 'restricted':
    case 'limited':
      return 'restricted';
    case 'tenant':
    case 'lease_resident':
      return 'tenant';
    case 'guest':
      return 'guest';
    default:
      return 'member';
  }
}

// ─── Wire shapes (matches backend `GET /:id/occupants`) ───────────

interface OccupantRow {
  id: string;
  user_id: string;
  role?: string | null;
  is_active?: boolean;
  display_name?: string | null;
  username?: string | null;
  avatar_url?: string | null;
  joined_at?: string | null;
  start_at?: string | null;
  created_at?: string | null;
}

interface PendingInviteRow {
  id: string;
  user_id?: string | null;
  role?: string | null;
  email?: string | null;
  name: string;
  invited_by?: string | null;
  created_at?: string | null;
}

interface OccupantsResponse {
  occupants: OccupantRow[];
  pendingInvites: PendingInviteRow[];
}

// ─── Tab + relative-time helpers ──────────────────────────────────

type TabId = 'members' | 'guests' | 'pending';

function formatRelative(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return null;
  const interval = (Date.now() - date.getTime()) / 1000;
  if (interval < 60) return 'just now';
  if (interval < 3600) return `${Math.floor(interval / 60)}m ago`;
  if (interval < 86_400) return `${Math.floor(interval / 3600)}h ago`;
  const days = Math.floor(interval / 86_400);
  if (days === 1) return 'yesterday';
  if (days < 7) return `${days}d ago`;
  if (days < 30) return `${Math.floor(days / 7)}w ago`;
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function displayNameFor(occ: OccupantRow): string {
  if (occ.display_name) return occ.display_name;
  if (occ.username) return `@${occ.username}`;
  return 'Member';
}

// ─── Inline invite modal (slide panel) ─────────────────────────────

type InviteRole = 'member' | 'guest';

function InviteMemberModal({
  open,
  onClose,
  onSubmit,
}: {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: { email: string; role: InviteRole; message: string }) => Promise<void>;
}) {
  const [step, setStep] = useState<'role' | 'identify' | 'review'>('role');
  const [role, setRole] = useState<InviteRole>('member');
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setStep('role');
      setRole('member');
      setEmail('');
      setMessage('');
      setSubmitting(false);
      setError(null);
    }
  }, [open]);

  if (!open) return null;

  const trimmedEmail = email.trim();
  const emailValid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail);

  const handlePrimary = async () => {
    setError(null);
    if (step === 'role') {
      setStep('identify');
      return;
    }
    if (step === 'identify') {
      if (!emailValid) {
        setError('Enter a valid email address.');
        return;
      }
      setStep('review');
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit({ email: trimmedEmail, role, message: message.trim() });
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Couldn't send the invite.";
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const primaryEnabled =
    step === 'role' ||
    (step === 'identify' && emailValid) ||
    (step === 'review' && emailValid && !submitting);

  const primaryLabel = step === 'review' ? (submitting ? 'Sending…' : 'Send invite') : 'Next';

  return (
    <div
      className="fixed inset-0 z-50 bg-black/40 flex items-end sm:items-center justify-center"
      data-testid="inviteMemberModal"
    >
      <div className="w-full sm:max-w-md bg-app-surface rounded-t-2xl sm:rounded-2xl shadow-xl flex flex-col max-h-[90vh]">
        <header className="flex items-center justify-between px-4 py-3 border-b border-app-border">
          <button
            type="button"
            onClick={step === 'role' ? onClose : () => setStep(step === 'review' ? 'identify' : 'role')}
            className="w-9 h-9 inline-flex items-center justify-center rounded-md hover:bg-app-hover"
            aria-label={step === 'role' ? 'Close' : 'Back'}
          >
            <X className="w-5 h-5 text-app-text" />
          </button>
          <h2 className="text-base font-semibold text-app-text">Invite member</h2>
          <span className="text-xs text-app-text-secondary">
            {step === 'role' ? '1 of 3' : step === 'identify' ? '2 of 3' : '3 of 3'}
          </span>
        </header>

        <div className="px-4 py-5 flex-1 overflow-y-auto">
          {step === 'role' && (
            <div className="space-y-3">
              <h3 className="text-lg font-semibold text-app-text">Pick a role</h3>
              <p className="text-sm text-app-text-secondary">
                Members get full access. Guests are short-term — sitters, visitors, contractors.
              </p>
              <div className="space-y-2 mt-2">
                {(['member', 'guest'] as InviteRole[]).map((r) => {
                  const meta = ROLE_META[r];
                  const selected = role === r;
                  const Icon = meta.icon;
                  return (
                    <button
                      key={r}
                      type="button"
                      onClick={() => setRole(r)}
                      className={`w-full flex items-center gap-3 p-3 rounded-xl border transition ${
                        selected
                          ? 'border-app-home ring-1 ring-app-home/40 bg-app-home-bg/30'
                          : 'border-app-border bg-app-surface hover:bg-app-hover'
                      }`}
                      data-testid={`inviteMember_role_${r}`}
                    >
                      <div
                        className="w-11 h-11 rounded-lg flex items-center justify-center"
                        style={{ background: meta.chipBg }}
                      >
                        <Icon className="w-5 h-5" style={{ color: meta.chipFg }} />
                      </div>
                      <div className="flex-1 text-left">
                        <p className="text-sm font-semibold text-app-text">{meta.label}</p>
                        <p className="text-xs text-app-text-secondary mt-0.5">
                          {r === 'member'
                            ? 'Full access — tasks, bills, calendar, codes.'
                            : 'Short-term — sitters, visitors, contractors.'}
                        </p>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {step === 'identify' && (
            <div className="space-y-3">
              <h3 className="text-lg font-semibold text-app-text">Who are you inviting?</h3>
              <p className="text-sm text-app-text-secondary">
                We&apos;ll send them a link to verify their address and join the household.
              </p>
              <label className="block mt-2 space-y-1">
                <span className="text-xs text-app-text-secondary">Email</span>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="name@example.com"
                  data-testid="inviteMember_email"
                  className="w-full px-3 py-2.5 rounded-lg border border-app-border bg-app-surface text-sm text-app-text focus:outline-none focus:border-app-home focus:ring-2 focus:ring-app-home/30"
                />
              </label>
              <label className="block space-y-1">
                <span className="text-xs text-app-text-secondary">Personal note (optional)</span>
                <textarea
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  rows={3}
                  data-testid="inviteMember_message"
                  className="w-full px-3 py-2.5 rounded-lg border border-app-border bg-app-surface text-sm text-app-text focus:outline-none focus:border-app-home focus:ring-2 focus:ring-app-home/30"
                />
              </label>
            </div>
          )}

          {step === 'review' && (
            <div className="space-y-3">
              <h3 className="text-lg font-semibold text-app-text">Send invite</h3>
              <p className="text-sm text-app-text-secondary">
                Confirm the details below. You can resend or cancel later from the Pending tab.
              </p>
              <div className="rounded-xl border border-app-border bg-app-surface divide-y divide-app-border-subtle">
                <div className="flex items-start gap-3 px-3 py-3">
                  <span className="w-20 text-xs text-app-text-secondary">Role</span>
                  <span className="flex-1 text-sm text-app-text">{ROLE_META[role].label}</span>
                </div>
                <div className="flex items-start gap-3 px-3 py-3">
                  <span className="w-20 text-xs text-app-text-secondary">Email</span>
                  <span className="flex-1 text-sm text-app-text break-all">{trimmedEmail}</span>
                </div>
              </div>
              {message.trim() && (
                <div className="rounded-lg bg-app-surface-sunken px-3 py-3">
                  <p className="text-xs text-app-text-secondary mb-1">Personal note</p>
                  <p className="text-sm text-app-text">{message.trim()}</p>
                </div>
              )}
            </div>
          )}

          {error && (
            <div
              className="mt-4 flex items-start gap-2 rounded-lg bg-app-error-bg text-app-error px-3 py-2"
              data-testid="inviteMemberErrorBanner"
            >
              <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
              <p className="text-xs">{error}</p>
            </div>
          )}
        </div>

        <footer className="border-t border-app-border-subtle px-4 py-3">
          <button
            type="button"
            onClick={handlePrimary}
            disabled={!primaryEnabled}
            data-testid="inviteMember_primaryCta"
            className="w-full py-2.5 rounded-lg bg-app-home text-white text-sm font-semibold hover:bg-app-home/90 disabled:opacity-50 disabled:cursor-not-allowed transition"
          >
            {primaryLabel}
          </button>
        </footer>
      </div>
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────

function MembersContent() {
  const router = useRouter();
  const { id: homeId } = useParams<{ id: string }>();

  const [occupants, setOccupants] = useState<OccupantRow[]>([]);
  const [pendingInvites, setPendingInvites] = useState<PendingInviteRow[]>([]);
  const [myAccess, setMyAccess] = useState<{
    isOwner?: boolean;
    role_base?: string;
    permissions?: string[];
  } | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>('members');
  const [showInvite, setShowInvite] = useState(false);

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const fetchData = useCallback(async () => {
    if (!homeId) return;
    setErrorMsg(null);
    const [occRes, accessRes] = await Promise.allSettled([
      api.homes.getHomeOccupants(homeId) as unknown as Promise<OccupantsResponse>,
      api.homeIam.getMyHomeAccess(homeId),
    ]);
    if (occRes.status === 'fulfilled') {
      const value = occRes.value;
      setOccupants((value.occupants || []).filter((o) => o.is_active !== false));
      setPendingInvites(value.pendingInvites || []);
    } else {
      setErrorMsg("Couldn't load members. Try again.");
    }
    if (accessRes.status === 'fulfilled') {
      const v = accessRes.value as { access?: unknown };
      setMyAccess((v?.access ?? accessRes.value) as typeof myAccess);
    }
  }, [homeId]);

  useEffect(() => {
    setLoading(true);
    fetchData().finally(() => setLoading(false));
  }, [fetchData]);

  const canManage = useMemo(
    () =>
      myAccess?.isOwner ||
      myAccess?.permissions?.includes('members.manage') ||
      myAccess?.role_base === 'owner' ||
      myAccess?.role_base === 'admin',
    [myAccess],
  );

  const members = useMemo(
    () => occupants.filter((o) => !GUEST_ROLES.has(parseRole(o.role))),
    [occupants],
  );
  const guests = useMemo(
    () => occupants.filter((o) => GUEST_ROLES.has(parseRole(o.role))),
    [occupants],
  );

  // ─── Mutations ─────────────────────────────────────────────────

  const handleRemove = useCallback(
    async (member: OccupantRow) => {
      if (!homeId) return;
      const name = displayNameFor(member);
      const yes = await confirmStore.open({
        title: 'Remove member?',
        description: `${name} will lose access to this home. They can be re-invited later.`,
        confirmLabel: `Remove ${name}`,
        variant: 'destructive',
      });
      if (!yes) return;
      const previous = occupants;
      setOccupants((prev) => prev.filter((o) => o.user_id !== member.user_id));
      try {
        await api.homeIam.removeMember(homeId, member.user_id);
        toast.success('Member removed');
      } catch (err: unknown) {
        setOccupants(previous);
        toast.error(err instanceof Error ? err.message : 'Failed to remove member');
      }
    },
    [homeId, occupants],
  );

  const handleCancelInvite = useCallback(
    async (invite: PendingInviteRow) => {
      if (!homeId) return;
      const previous = pendingInvites;
      setPendingInvites((prev) => prev.filter((p) => p.id !== invite.id));
      if (!invite.user_id) {
        // Open invite with no resolved user — optimistic drop only.
        toast.success('Invite cancelled');
        return;
      }
      try {
        await api.homeIam.removeMember(homeId, invite.user_id);
        toast.success('Invite cancelled');
      } catch (err: unknown) {
        setPendingInvites(previous);
        toast.error(err instanceof Error ? err.message : 'Failed to cancel invite');
      }
    },
    [homeId, pendingInvites],
  );

  const handleResendInvite = useCallback(
    async (invite: PendingInviteRow) => {
      if (!homeId) return;
      try {
        await api.homes.inviteToHome(homeId, {
          email: invite.email ?? undefined,
          user_id: invite.user_id ?? undefined,
          relationship: invite.role || 'member',
        });
        toast.success('Invite resent');
      } catch (err: unknown) {
        toast.error(err instanceof Error ? err.message : 'Failed to resend invite');
      }
    },
    [homeId],
  );

  const handleSubmitInvite = useCallback(
    async ({ email, role, message }: { email: string; role: InviteRole; message: string }) => {
      if (!homeId) return;
      await api.homes.inviteToHome(homeId, {
        email,
        relationship: role,
        message: message || undefined,
      });
      toast.success('Invite sent');
      await fetchData();
    },
    [homeId, fetchData],
  );

  // ─── Row projections ───────────────────────────────────────────

  function rowForOccupant(occ: OccupantRow): RowModel {
    const role = parseRole(occ.role);
    const meta = ROLE_META[role];
    const name = displayNameFor(occ);
    const joinedRaw = occ.joined_at || occ.start_at || occ.created_at;
    const joined = formatRelative(joinedRaw);
    return {
      id: occ.user_id,
      title: name,
      subtitle: meta.label,
      template: 'avatarKebab',
      leading: {
        kind: 'avatarWithBadge',
        name,
        imageURL: occ.avatar_url ?? null,
        background: { kind: 'gradient', gradient: { start: '#0ea5e9', end: '#0369a1' } },
        size: 'medium',
        verified: true,
      },
      trailing: canManage && role !== 'owner' ? { kind: 'kebab' } : { kind: 'none' },
      body: joined ? `Joined ${joined}` : null,
      onSecondary: canManage && role !== 'owner' ? () => handleRemove(occ) : undefined,
      inlineChip: {
        text: meta.label,
        icon: meta.icon,
        tint: { kind: 'custom', background: meta.chipBg, foreground: meta.chipFg },
      },
    };
  }

  function rowForPending(invite: PendingInviteRow): RowModel {
    const role = parseRole(invite.role);
    const meta = ROLE_META[role];
    const created = formatRelative(invite.created_at);
    return {
      id: invite.id,
      title: invite.name || invite.email || 'Invited user',
      subtitle: meta.label,
      template: 'statusChip',
      leading: {
        kind: 'avatarWithBadge',
        name: invite.name || invite.email || 'I',
        imageURL: null,
        background: { kind: 'gradient', gradient: { start: '#9ca3af', end: '#374151' } },
        size: 'medium',
        verified: false,
      },
      trailing: canManage
        ? {
            kind: 'verticalActions',
            primary: { label: 'Resend', variant: 'primary', onClick: () => handleResendInvite(invite) },
            secondary: { label: 'Cancel', variant: 'ghost', onClick: () => handleCancelInvite(invite) },
          }
        : { kind: 'none' },
      body: `Invited ${created || 'recently'}`,
      inlineChip: {
        text: meta.label,
        icon: meta.icon,
        tint: { kind: 'custom', background: meta.chipBg, foreground: meta.chipFg },
      },
    };
  }

  // ─── Shell state ───────────────────────────────────────────────

  const tabs: ListOfRowsTab[] = [
    { id: 'members', label: 'Members', count: members.length },
    { id: 'guests', label: 'Guests', count: guests.length },
    { id: 'pending', label: 'Pending', count: pendingInvites.length },
  ];

  const shellState: ListOfRowsState = (() => {
    if (loading) return { kind: 'loading' };
    if (errorMsg) return { kind: 'error', message: errorMsg };
    const rows: RowModel[] =
      tab === 'guests'
        ? guests.map(rowForOccupant)
        : tab === 'pending'
          ? pendingInvites.map(rowForPending)
          : members.map(rowForOccupant);
    if (rows.length === 0) {
      const empty = {
        members: {
          icon: Users,
          headline: 'No members yet',
          subcopy:
            'Invite a housemate to share tasks, bills, calendar, and access codes for this home.',
          ctaTitle: 'Invite someone',
        },
        guests: {
          icon: Users,
          headline: 'No active guests',
          subcopy:
            "Add someone short-term — a sitter, visitor, or contractor — to share access while they're around.",
          ctaTitle: 'Add a guest',
        },
        pending: {
          icon: Mailbox,
          headline: 'No pending invites',
          subcopy: 'Invitations you send to housemates appear here until they accept.',
          ctaTitle: 'Send an invite',
        },
      }[tab];
      return {
        kind: 'empty',
        config: {
          ...empty,
          onCta: canManage ? () => setShowInvite(true) : undefined,
        },
      };
    }
    return {
      kind: 'loaded',
      sections: [{ id: tab, rows }],
      hasMore: false,
    };
  })();

  return (
    <>
      <ListOfRowsShell
        title="Members"
        state={shellState}
        onRefresh={fetchData}
        tabs={tabs}
        selectedTab={tab}
        onTabChange={(id) => setTab(id as TabId)}
        fab={
          canManage
            ? {
                icon: UserPlus,
                accessibilityLabel: 'Invite member',
                variant: { kind: 'secondaryCreate' },
                tint: 'home',
                onClick: () => setShowInvite(true),
              }
            : undefined
        }
      />
      <InviteMemberModal
        open={showInvite}
        onClose={() => setShowInvite(false)}
        onSubmit={handleSubmitInvite}
      />
    </>
  );
}

export default function MembersPage() {
  return (
    <Suspense
      fallback={
        <div className="flex items-center justify-center min-h-[50vh]">
          <div className="animate-spin h-8 w-8 border-3 border-app-home border-t-transparent rounded-full" />
        </div>
      }
    >
      <MembersContent />
    </Suspense>
  );
}
