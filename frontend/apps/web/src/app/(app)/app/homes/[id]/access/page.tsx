'use client';

/**
 * T6.4a — Access codes (per-home roster). Mirrors the iOS + Android
 * Access codes screen design at `access-frames.jsx`. Categories are
 * Wi-Fi / Alarm / Gate / Lockbox / Garage / Smart lock; values are
 * masked by default and revealed on tap. Copy fires a toast.
 *
 * Backend: GET /api/homes/:id/access — `backend/routes/home.js:5487`.
 */

import { Suspense, useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft,
  Building2,
  Copy,
  KeyRound,
  List,
  Lock,
  MoreHorizontal,
  Plus,
  Search,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Wifi,
} from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import { toast } from '@/components/ui/toast-store';

// ─── Types ──────────────────────────────────────────────────────

interface HomeAccessSecret {
  id: string;
  home_id: string;
  access_type: string;
  label: string;
  secret_value: string;
  notes?: string | null;
  visibility?: string | null;
  created_at?: string | null;
  updated_at?: string | null;
}

type AccessCategoryKey =
  | 'wifi'
  | 'alarm'
  | 'gate'
  | 'lockbox'
  | 'garage'
  | 'smart_lock';

interface CategoryMeta {
  key: AccessCategoryKey;
  label: string;
  icon: typeof Wifi;
  bg: string;
  fg: string;
}

// ─── Category palette (lifted from access-frames.jsx) ──────────

const CATEGORY_META: Record<AccessCategoryKey, CategoryMeta> = {
  wifi: { key: 'wifi', label: 'Wi-Fi', icon: Wifi, bg: '#DBEAFE', fg: '#1D4ED8' },
  alarm: { key: 'alarm', label: 'Alarm', icon: ShieldAlert, bg: '#FEE2E2', fg: '#B91C1C' },
  gate: { key: 'gate', label: 'Gate', icon: Shield, bg: '#E0E7FF', fg: '#4338CA' },
  lockbox: { key: 'lockbox', label: 'Lockbox', icon: Lock, bg: '#FEF3C7', fg: '#92400E' },
  garage: { key: 'garage', label: 'Garage', icon: Building2, bg: '#E2E8F0', fg: '#334155' },
  smart_lock: {
    key: 'smart_lock',
    label: 'Smart lock',
    icon: ShieldCheck,
    bg: '#CCFBF1',
    fg: '#0F766E',
  },
};

const DISPLAY_ORDER: AccessCategoryKey[] = [
  'wifi',
  'alarm',
  'gate',
  'lockbox',
  'garage',
  'smart_lock',
];

function categoryFor(accessType: string | undefined | null): AccessCategoryKey {
  if (!accessType) return 'lockbox';
  const key = accessType.toLowerCase();
  if (DISPLAY_ORDER.includes(key as AccessCategoryKey)) {
    return key as AccessCategoryKey;
  }
  if (key.includes('wifi') || key.includes('network')) return 'wifi';
  if (key.includes('alarm') || key.includes('siren')) return 'alarm';
  if (key.includes('gate') || key.includes('fence')) return 'gate';
  if (key.includes('garage') || key.includes('opener')) return 'garage';
  if (key.includes('smart') || key.includes('digital')) return 'smart_lock';
  return 'lockbox';
}

function maskValue(value: string): string {
  const length = Math.max(1, Math.min(12, value?.length || 0));
  return '•'.repeat(Math.max(length, 4));
}

// ─── Page ───────────────────────────────────────────────────────

function AccessContent() {
  const router = useRouter();
  const { id: homeId } = useParams<{ id: string }>();
  const [selectedChip, setSelectedChip] = useState<'all' | AccessCategoryKey>('all');
  const [revealed, setRevealed] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const secretsQuery = useQuery({
    queryKey: ['home-access-secrets', homeId],
    queryFn: async (): Promise<HomeAccessSecret[]> => {
      const res = (await api.homeProfile.getHomeAccessSecrets(homeId)) as {
        secrets?: HomeAccessSecret[];
      };
      return res?.secrets ?? [];
    },
    enabled: Boolean(homeId),
  });

  const homesQuery = useQuery({
    queryKey: ['my-homes-subtitle'],
    queryFn: async () => api.homes.getMyHomes(),
    staleTime: 60_000,
  });

  const homeName = useMemo(() => {
    if (!homesQuery.data || !homeId) return undefined;
    const match = homesQuery.data.homes.find((h: any) => h.id === homeId);
    if (!match) return undefined;
    const trimmed = (match.name ?? '').trim();
    return trimmed.length > 0 ? trimmed : match.address ?? undefined;
  }, [homesQuery.data, homeId]);

  const secrets = secretsQuery.data ?? [];

  const countsByCategory = useMemo(() => {
    const counts: Record<AccessCategoryKey, number> = {
      wifi: 0,
      alarm: 0,
      gate: 0,
      lockbox: 0,
      garage: 0,
      smart_lock: 0,
    };
    for (const secret of secrets) {
      counts[categoryFor(secret.access_type)] += 1;
    }
    return counts;
  }, [secrets]);

  const sectionsToRender: AccessCategoryKey[] =
    selectedChip === 'all' ? DISPLAY_ORDER : [selectedChip];

  const visibleSections = sectionsToRender
    .map((cat) => ({
      cat,
      rows: secrets.filter((s) => categoryFor(s.access_type) === cat),
    }))
    .filter((s) => s.rows.length > 0);

  function toggleReveal(id: string) {
    setRevealed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function copyValue(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      toast.success('Code copied');
    } catch {
      toast.error('Failed to copy');
    }
  }

  function openKebab(_secretId: string) {
    toast.info('Edit / delete coming soon');
  }

  function openAdd(category?: AccessCategoryKey) {
    const label = category ? CATEGORY_META[category].label : 'Add access code';
    toast.info(`${label} composer coming soon`);
  }

  function openSearch() {
    toast.info('Search coming soon');
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-6" data-testid="accessCodes_screen">
      {/* Top bar — 2-line title with home subtitle */}
      <div className="flex items-start gap-3 mb-4">
        <button
          onClick={() => router.back()}
          className="p-1.5 hover:bg-app-hover rounded-lg transition mt-1"
          aria-label="Back"
        >
          <ArrowLeft className="w-5 h-5 text-app-text" />
        </button>
        <div className="flex-1 min-w-0">
          <h1 className="text-xl font-bold text-app-text leading-tight">Access codes</h1>
          {homeName && (
            <p className="text-xs text-app-text-secondary mt-0.5 truncate">{homeName}</p>
          )}
        </div>
        <button
          onClick={openSearch}
          className="p-1.5 hover:bg-app-hover rounded-lg transition mt-1"
          aria-label="Search access codes"
        >
          <Search className="w-5 h-5 text-app-text" />
        </button>
        <button
          onClick={() => openAdd()}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-semibold transition"
        >
          <Plus className="w-4 h-4" />
          Add code
        </button>
      </div>

      {/* Chip strip */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-3 mb-4 border-b border-app-border">
        <ChipButton
          icon={List}
          label={`All (${secrets.length})`}
          active={selectedChip === 'all'}
          onClick={() => setSelectedChip('all')}
        />
        {DISPLAY_ORDER.map((cat) => {
          const meta = CATEGORY_META[cat];
          return (
            <ChipButton
              key={cat}
              icon={meta.icon}
              label={`${meta.label} (${countsByCategory[cat]})`}
              active={selectedChip === cat}
              onClick={() => setSelectedChip(cat)}
            />
          );
        })}
      </div>

      {/* States */}
      {secretsQuery.isPending && <LoadingState />}
      {secretsQuery.isError && (
        <ErrorState onRetry={() => void secretsQuery.refetch()} />
      )}
      {secretsQuery.isSuccess && visibleSections.length === 0 && (
        <EmptyState selectedChip={selectedChip} onAdd={openAdd} />
      )}
      {secretsQuery.isSuccess && visibleSections.length > 0 && (
        <div className="space-y-6">
          {visibleSections.map(({ cat, rows }) => (
            <CategorySection
              key={cat}
              category={cat}
              rows={rows}
              revealed={revealed}
              onToggleReveal={toggleReveal}
              onCopy={(v) => void copyValue(v)}
              onKebab={openKebab}
              onEdit={() => openAdd(cat)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ─── Pieces ─────────────────────────────────────────────────────

function ChipButton({
  icon: Icon,
  label,
  active,
  onClick,
}: {
  icon: typeof Wifi;
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap flex-shrink-0 transition ${
        active
          ? 'bg-emerald-600 text-white'
          : 'bg-app-surface-sunken text-app-text-secondary hover:bg-app-hover'
      }`}
    >
      <Icon className="w-3.5 h-3.5" />
      {label}
    </button>
  );
}

function CategorySection({
  category,
  rows,
  revealed,
  onToggleReveal,
  onCopy,
  onKebab,
  onEdit,
}: {
  category: AccessCategoryKey;
  rows: HomeAccessSecret[];
  revealed: Set<string>;
  onToggleReveal: (id: string) => void;
  onCopy: (value: string) => void;
  onKebab: (id: string) => void;
  onEdit: () => void;
}) {
  const meta = CATEGORY_META[category];
  const CatIcon = meta.icon;
  return (
    <section data-testid={`accessCodes_section_${category}`}>
      <header className="flex items-center gap-2 mb-2 px-1">
        <span
          className="w-6 h-6 rounded-md flex items-center justify-center flex-shrink-0"
          style={{ backgroundColor: meta.bg }}
        >
          <CatIcon className="w-3.5 h-3.5" style={{ color: meta.fg }} />
        </span>
        <h2 className="text-[11px] font-bold uppercase tracking-wider text-app-text-strong flex-1">
          {meta.label}
        </h2>
        <span className="text-[10px] font-semibold text-app-text-muted bg-app-surface-sunken px-2 py-0.5 rounded-full">
          {rows.length}
        </span>
        <button
          onClick={onEdit}
          className="text-xs font-semibold text-emerald-700 hover:text-emerald-800 px-1"
        >
          Edit
        </button>
      </header>
      <div className="bg-app-surface border border-app-border rounded-xl divide-y divide-app-border">
        {rows.map((secret) => (
          <AccessCodeRow
            key={secret.id}
            secret={secret}
            revealed={revealed.has(secret.id)}
            onTap={() => onToggleReveal(secret.id)}
            onCopy={() => onCopy(secret.secret_value)}
            onKebab={() => onKebab(secret.id)}
          />
        ))}
      </div>
    </section>
  );
}

function AccessCodeRow({
  secret,
  revealed,
  onTap,
  onCopy,
  onKebab,
}: {
  secret: HomeAccessSecret;
  revealed: boolean;
  onTap: () => void;
  onCopy: () => void;
  onKebab: () => void;
}) {
  const category = categoryFor(secret.access_type);
  const meta = CATEGORY_META[category];
  const CatIcon = meta.icon;
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onTap}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onTap();
        }
      }}
      className="flex items-start gap-3 p-4 cursor-pointer hover:bg-app-surface-sunken/40 transition"
      data-testid="accessCodes_row"
    >
      <span
        className="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0"
        style={{ backgroundColor: meta.bg }}
      >
        <CatIcon className="w-5 h-5" style={{ color: meta.fg }} />
      </span>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-app-text">{secret.label}</p>
        <p
          className={`text-base font-semibold font-mono mt-1 text-app-text ${
            revealed ? 'tracking-normal' : 'tracking-widest'
          }`}
        >
          {revealed ? secret.secret_value : maskValue(secret.secret_value)}
        </p>
        {secret.notes && (
          <p className="text-xs text-app-text-secondary mt-1.5">{secret.notes}</p>
        )}
      </div>
      <div className="flex items-center gap-1.5 flex-shrink-0">
        <IconAction
          icon={Copy}
          ariaLabel={`Copy ${secret.label}`}
          testId="accessCodes_copyAction"
          onClick={(e) => {
            e.stopPropagation();
            onCopy();
          }}
        />
        <IconAction
          icon={MoreHorizontal}
          ariaLabel={`More actions for ${secret.label}`}
          testId="accessCodes_kebabAction"
          onClick={(e) => {
            e.stopPropagation();
            onKebab();
          }}
        />
      </div>
    </div>
  );
}

function IconAction({
  icon: Icon,
  ariaLabel,
  testId,
  onClick,
}: {
  icon: typeof Copy;
  ariaLabel: string;
  testId: string;
  onClick: (e: React.MouseEvent) => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={ariaLabel}
      data-testid={testId}
      className="w-8 h-8 rounded-md bg-app-surface-sunken text-app-text-secondary hover:bg-app-hover flex items-center justify-center transition"
    >
      <Icon className="w-4 h-4" />
    </button>
  );
}

function LoadingState() {
  return (
    <div className="space-y-2" aria-busy="true">
      {[0, 1, 2].map((i) => (
        <div
          key={i}
          className="h-20 bg-app-surface border border-app-border rounded-xl animate-pulse"
        />
      ))}
    </div>
  );
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="text-center py-12 bg-app-surface border border-app-border rounded-xl">
      <p className="text-sm font-semibold text-app-text mb-1">Couldn't load access codes</p>
      <p className="text-xs text-app-text-secondary mb-4">Try again in a moment.</p>
      <button
        onClick={onRetry}
        className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-semibold transition"
      >
        Try again
      </button>
    </div>
  );
}

function EmptyState({
  selectedChip,
  onAdd,
}: {
  selectedChip: 'all' | AccessCategoryKey;
  onAdd: (category?: AccessCategoryKey) => void;
}) {
  const isFiltered = selectedChip !== 'all';
  const meta = isFiltered ? CATEGORY_META[selectedChip as AccessCategoryKey] : undefined;
  const labelLower = meta?.label.toLowerCase();
  const headline = isFiltered
    ? `No ${labelLower} codes yet`
    : 'No access codes yet';
  const subcopy = isFiltered
    ? `Add a ${labelLower} code so household members can find it when they need it.`
    : 'One vault for every code at this address. Codes are encrypted, masked by default, and only shared with members you choose.';
  const ctaLabel = isFiltered ? `Add ${meta!.label} code` : 'Add your first code';
  return (
    <div className="text-center py-16 px-6">
      <div className="w-24 h-24 mx-auto rounded-full bg-emerald-50 flex items-center justify-center mb-5">
        <KeyRound className="w-10 h-10 text-emerald-600" />
      </div>
      <p className="text-xl font-semibold text-app-text mb-2">{headline}</p>
      <p className="text-sm text-app-text-secondary max-w-sm mx-auto mb-5">{subcopy}</p>
      <button
        onClick={() => onAdd(isFiltered ? (selectedChip as AccessCategoryKey) : undefined)}
        className="inline-flex items-center gap-2 px-5 py-3 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-semibold transition"
      >
        <Plus className="w-4 h-4" />
        {ctaLabel}
      </button>
    </div>
  );
}

export default function AccessPage() {
  return (
    <Suspense>
      <AccessContent />
    </Suspense>
  );
}
