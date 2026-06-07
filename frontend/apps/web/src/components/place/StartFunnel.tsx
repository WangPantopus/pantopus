// ============================================================
// StartFunnel — the signed-out Place funnel at /start.
//
// A2 hero (sentence-case, "free, no account" on the demonstration only)
// → address autocomplete (public api.geo) → teaser-then-wall: a
// one-shot preview with the free Band-A subset live and everything
// recurring/exact as LockedCards → the wall ("Create a free account to
// save this place and get daily updates") → /register.
//
// Anti-leak (§4): the daily layer (weather/AQI/alerts) is LOCKED here;
// the free taste is flood + density bucket + area teaser. The preview
// persists nothing — the resolved address is only stashed (sessionStorage)
// to save once the account exists.
// ============================================================

'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import type { LucideIcon } from 'lucide-react';
import {
  MapPinned,
  Globe,
  Lock as LockIcon,
  ArrowRight,
  ShieldCheck,
  Smartphone,
  CloudSun,
  Home,
  FlaskConical,
  Zap,
  Landmark,
  Waves,
  House,
} from 'lucide-react';
import * as api from '@pantopus/api';
import type { PlacePreview, PlacePreviewLockedSection } from '@pantopus/api';
import { Group, SectionCard, LockedCard, DensityCard, PlaceHeader, TextButton } from '@/components/archetypes/place';
import { ShimmerBlock } from '@/components/ui/Shimmer';
import { getStoreDownloadCta } from '@/lib/publicShare';
import { stashPendingPlace } from './pendingPlace';
import AddressAutocomplete, { type SelectedAddress } from './AddressAutocomplete';

const REGISTER_HREF = '/register?redirectTo=%2Fapp%2Fplace';

// ── flood zone → qualitative chip (the preview free.flood gives a zone) ──
function floodChip(zone?: string): { label: string; variant: 'success' | 'warning' | 'error' } {
  const z = (zone || '').toUpperCase();
  if (z.startsWith('A') || z.startsWith('V')) return { label: 'High risk', variant: 'error' };
  if (z.includes('0.2') || z.includes('X500') || z.includes('SHADED')) return { label: 'Moderate risk', variant: 'warning' };
  return { label: 'Minimal risk', variant: 'success' };
}

const LOCKED_ICON: Record<string, LucideIcon> = {
  today: CloudSun,
  your_home: Home,
  health_environment: FlaskConical,
  money_signals: Zap,
  civic: Landmark,
};

function money(n?: number | null): string | null {
  if (n == null || !Number.isFinite(n)) return null;
  return `$${Math.round(n).toLocaleString('en-US')}`;
}

// ── Brand lockup + static region pill ───────────────────────
function TopBar() {
  return (
    <div className="flex items-center justify-between pt-2">
      <div className="flex items-center gap-2.5">
        <span className="inline-flex items-center justify-center w-[30px] h-[30px] rounded-[9px] bg-app-home shadow-sm">
          <MapPinned size={17} strokeWidth={2.25} className="text-white" />
        </span>
        <span className="text-lg font-bold -tracking-[0.02em] text-app-text">Pantopus</span>
      </div>
      <span className="inline-flex items-center gap-1.5 rounded-full border border-app-border bg-app-surface px-2.5 py-1 shadow-sm">
        <Globe size={14} strokeWidth={2} className="text-app-text-secondary" />
        <span className="text-[12.5px] font-semibold text-app-text-strong -tracking-[0.01em]">United States</span>
      </span>
    </div>
  );
}

// ── The hero step ───────────────────────────────────────────
function HeroStep({
  onSelect,
  onClear,
  onSubmit,
  canSubmit,
  onBrowse,
}: {
  onSelect: (p: SelectedAddress) => void;
  onClear: () => void;
  onSubmit: () => void;
  canSubmit: boolean;
  onBrowse: () => void;
}) {
  return (
    <div className="flex flex-col min-h-screen pb-8">
      <TopBar />
      <div className="flex-1 flex flex-col justify-center">
        <h1 className="text-[31px] leading-[37px] font-bold -tracking-[0.028em] text-app-text">
          See what&apos;s true about your address.
        </h1>
        <p className="mt-3.5 text-[15.5px] leading-[23px] text-app-text-secondary -tracking-[0.005em]">
          Public records, local risks, and who&apos;s verified nearby — free, no account. Save your place and get
          daily updates when you sign up.
        </p>

        <div className="mt-6 flex flex-col gap-2.5">
          <AddressAutocomplete onSelect={onSelect} onClear={onClear} onSubmit={onSubmit} autoFocus />
          <button
            type="button"
            onClick={onSubmit}
            disabled={!canSubmit}
            className="h-[54px] w-full flex items-center justify-center gap-1.5 rounded-2xl bg-primary-600 text-white text-base font-semibold -tracking-[0.01em] shadow-[0_6px_16px_rgba(2,132,199,0.2)] enabled:hover:bg-primary-700 disabled:opacity-50 transition-colors"
          >
            See your place
            <ArrowRight size={17} strokeWidth={2.5} />
          </button>
        </div>

        <div className="mt-4 flex items-start justify-center gap-1.5 px-2">
          <LockIcon size={13} strokeWidth={2} className="mt-px text-app-text-muted shrink-0" />
          <span className="text-[12.5px] leading-[17px] text-app-text-secondary text-center">
            Private by default. Verification builds trust, not exposure.
          </span>
        </div>
      </div>

      <div className="flex justify-center">
        <button
          type="button"
          onClick={onBrowse}
          className="inline-flex items-center gap-1.5 py-2 px-1 text-[13.5px] font-medium text-app-text-secondary -tracking-[0.005em]"
        >
          Just here to follow someone or browse?
          <ArrowRight size={14} strokeWidth={2.25} className="text-primary-600" />
        </button>
      </div>
    </div>
  );
}

// ── Preview hero card ("here's what's public…") ─────────────
function PreviewHeroCard() {
  return (
    <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-4">
      <div className="flex items-center justify-between mb-3">
        <span className="text-[11px] font-bold uppercase tracking-[0.07em] text-app-text-secondary">Public preview</span>
        <span className="inline-flex items-center gap-1 rounded-full bg-app-home-bg text-app-home text-[11px] font-semibold px-2 py-0.5">
          <ShieldCheck size={12} strokeWidth={2.25} />
          Free · one-time look
        </span>
      </div>
      <div className="flex items-start gap-3">
        <span className="inline-flex items-center justify-center shrink-0 w-[42px] h-[42px] rounded-xl bg-app-home-bg text-app-home">
          <MapPinned size={22} strokeWidth={2} />
        </span>
        <div className="min-w-0">
          <p className="text-[17px] font-semibold text-app-text leading-[23px] -tracking-[0.012em]">
            Here&apos;s what&apos;s public about your address — a free, one-time look.
          </p>
          <p className="text-[13.5px] text-app-text-secondary leading-[19px] mt-1.5">
            Create an account to save this place and keep it updated every day.
          </p>
        </div>
      </div>
    </div>
  );
}

// ── Free + locked sections from the preview payload ─────────
function PreviewBody({ preview, onWall }: { preview: PlacePreview; onWall: () => void }) {
  const free = preview.free;
  const locked = preview.locked ?? [];

  return (
    <div className="mt-5">
      {free ? (
        <>
          <Group label="Risk & readiness">
            {free.flood.status === 'ready' ? (
              <SectionCard
                icon={Waves}
                title="Flood"
                value={`Zone ${free.flood.zone} — ${floodChip(free.flood.zone).label.toLowerCase()}`}
                chip={floodChip(free.flood.zone)}
                caption="FEMA flood zone, area-level"
              />
            ) : (
              <SectionCard icon={Waves} title="Flood" state="unavailable" />
            )}
          </Group>

          <Group label="Your block">
            <DensityCard bucket={free.density.bucket} onCta={onWall} />
            {free.area.status === 'ready' ? (
              <SectionCard
                icon={House}
                title="Homes here"
                value={
                  free.area.median_year_built
                    ? `Median built ${free.area.median_year_built}`
                    : money(free.area.median_home_value)
                      ? `Typical value ${money(free.area.median_home_value)}`
                      : 'Area facts'
                }
                caption="Census, area-level — not your home"
              />
            ) : (
              <SectionCard icon={House} title="Homes here" state="unavailable" />
            )}
          </Group>
        </>
      ) : null}

      {locked.length > 0 ? (
        <Group label="More with a free account">
          {locked.map((s: PlacePreviewLockedSection) => (
            <LockedCard
              key={s.id}
              icon={LOCKED_ICON[s.group] ?? LockIcon}
              title={s.title}
              reason={s.reason}
              cta="Create account"
              onCta={onWall}
            />
          ))}
        </Group>
      ) : null}
    </div>
  );
}

// ── App-download link (platform-aware; the QR-scanner's other path) ──
// The store URLs ship with real fallbacks, so this is never a dead link;
// computed on the client from the user agent to point at the right store.
function AppDownloadLink() {
  const [cta, setCta] = useState<{ href: string; label: string } | null>(null);
  useEffect(() => {
    try {
      setCta(getStoreDownloadCta(navigator.userAgent));
    } catch {
      setCta(null);
    }
  }, []);
  if (!cta) return null;
  return (
    <a
      href={cta.href}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-1.5 text-[13px] font-semibold text-primary-600 hover:text-primary-700"
    >
      <Smartphone size={14} strokeWidth={2} />
      Prefer the app? Get it
      <ArrowRight size={13} strokeWidth={2.25} />
    </a>
  );
}

// ── Sticky wall bar ─────────────────────────────────────────
function WallBar({ onWall }: { onWall: () => void }) {
  return (
    <div className="sticky bottom-0 left-0 right-0 -mx-5 px-5 py-3.5 bg-app-surface/95 backdrop-blur border-t border-app-border">
      <div className="flex items-center gap-3.5">
        <div className="flex-1 min-w-0">
          <p className="text-[14.5px] font-semibold text-app-text leading-[19px] -tracking-[0.01em]">
            Create a free account to save this place and get daily updates
          </p>
          <p className="text-[12.5px] text-app-text-secondary mt-0.5">Free. Takes a minute.</p>
        </div>
        <button
          type="button"
          onClick={onWall}
          className="shrink-0 rounded-xl bg-primary-600 text-white px-4 py-3 text-[15px] font-semibold -tracking-[0.01em] shadow-[0_6px_16px_rgba(2,132,199,0.18)] hover:bg-primary-700 transition-colors whitespace-nowrap"
        >
          Create account
        </button>
      </div>
      <div className="mt-2 flex justify-center">
        <AppDownloadLink />
      </div>
    </div>
  );
}

// ── Preview loading skeleton ────────────────────────────────
function PreviewSkeleton() {
  return (
    <div aria-hidden="true">
      <div className="mt-2 flex items-start justify-between gap-3">
        <div className="flex flex-col gap-2">
          <ShimmerBlock className="h-7 w-36" />
          <ShimmerBlock className="h-4 w-52" />
        </div>
        <ShimmerBlock className="h-5 w-12" />
      </div>
      <div className="mt-4 bg-app-surface border border-app-border rounded-2xl shadow-sm p-4">
        <ShimmerBlock className="h-4 w-11/12 mb-2" />
        <ShimmerBlock className="h-4 w-2/3" />
      </div>
      <div className="mt-5 flex flex-col gap-2">
        {[0, 1, 2].map((i) => (
          <div key={i} className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-4">
            <div className="flex items-center gap-3 mb-3">
              <ShimmerBlock className="w-[34px] h-[34px] rounded-[9px]" />
              <ShimmerBlock className="h-[15px] w-28" />
            </div>
            <ShimmerBlock className="h-[15px] w-3/5" />
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Unsupported region (non-US) ─────────────────────────────
function UnsupportedRegion({ onBrowse }: { onBrowse: () => void }) {
  return (
    <div className="mt-10 flex flex-col items-center text-center px-2">
      <span className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-app-home-bg text-app-home mb-5">
        <MapPinned size={30} strokeWidth={2} />
      </span>
      <h2 className="text-xl font-bold -tracking-[0.02em] text-app-text">Home features are U.S.-only for now</h2>
      <p className="mt-2 text-sm text-app-text-secondary leading-relaxed max-w-sm">
        Records, risks, and home details come from U.S. public-data sources. Following, fanning, and messaging
        verified people work everywhere today.
      </p>
      <div className="mt-5">
        <TextButton onClick={onBrowse}>Create a free account to follow people and places</TextButton>
      </div>
    </div>
  );
}

// ── The funnel ──────────────────────────────────────────────
export default function StartFunnel() {
  const router = useRouter();
  const [selected, setSelected] = useState<SelectedAddress | null>(null);
  const [submitted, setSubmitted] = useState<string | null>(null);

  const previewQuery = useQuery({
    queryKey: ['place', 'public-preview', submitted],
    queryFn: () => api.place.getPublicPlacePreview(submitted as string),
    enabled: !!submitted,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  const goBrowse = () => router.push(REGISTER_HREF);

  const goWall = () => {
    if (selected) {
      const place = previewQuery.data?.place;
      stashPendingPlace({
        label: selected.label,
        latitude: selected.latitude,
        longitude: selected.longitude,
        city: place?.city ?? null,
        state: place?.state ?? null,
      });
    }
    router.push(REGISTER_HREF);
  };

  const submit = () => {
    if (selected) setSubmitted(selected.label);
  };

  // Hero step.
  if (!submitted) {
    return (
      <div className="min-h-screen bg-app-bg">
        <div className="mx-auto w-full max-w-[480px] px-5">
          <HeroStep
            onSelect={setSelected}
            onClear={() => setSelected(null)}
            onSubmit={submit}
            canSubmit={!!selected}
            onBrowse={goBrowse}
          />
        </div>
      </div>
    );
  }

  // Preview step.
  const preview = previewQuery.data;
  const addressLabel = preview?.place?.address
    ? [preview.place.address, preview.place.city].filter(Boolean).join(', ')
    : selected?.label ?? 'Your address';

  return (
    <div className="min-h-screen bg-app-bg">
      <div className="mx-auto w-full max-w-[480px] px-5 pt-3 flex flex-col min-h-screen">
        {previewQuery.isPending ? (
          <PreviewSkeleton />
        ) : previewQuery.isError ? (
          <div className="mt-10 text-center">
            <p className="text-sm text-app-text-secondary">We couldn&apos;t look up that address. Try again.</p>
            <div className="mt-3 flex justify-center">
              <TextButton arrow={false} onClick={() => previewQuery.refetch()}>Try again</TextButton>
            </div>
          </div>
        ) : preview && preview.status === 'unsupported_region' ? (
          <UnsupportedRegion onBrowse={goBrowse} />
        ) : preview ? (
          <>
            <div className="flex-1">
              <PlaceHeader
                address={addressLabel}
                status="none"
                rightSlot={
                  <button
                    type="button"
                    onClick={() => router.push('/login')}
                    className="text-sm font-semibold text-primary-600 hover:text-primary-700"
                  >
                    Sign in
                  </button>
                }
              />
              <div className="mt-4">
                <PreviewHeroCard />
              </div>
              <PreviewBody preview={preview} onWall={goWall} />
              <div className="h-4" />
            </div>
            <WallBar onWall={goWall} />
          </>
        ) : null}
      </div>
    </div>
  );
}
