// ============================================================
// Place — Money Signals detail (C7). Everything informational, never
// advice. Bill benchmark vs similar homes (peer-relative), DSIRE
// incentives you may qualify for, the HUD rent band with your own
// private rent, and property tax as a post-v1 "coming soon" row.
// Bill benchmark / incentives / rent read from the contract.
// ============================================================

'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from '@pantopus/api';
import type { RecordWatch } from '@pantopus/api';
import { toast } from '@/components/ui/toast-store';
import { queryKeys } from '@/lib/query-keys';
import type {
  PlaceIntelligence,
  PlaceBillBenchmarkData,
  PlaceIncentivesData,
  PlaceIncentive,
  PlaceRentBandData,
  PlaceExemptionCheckData,
  IncentiveLevel,
  IncentiveType,
  BenchmarkComparison,
  BillUtilityKind,
} from '@pantopus/types';
import { Zap, BadgePercent, Building2, Landmark, PlusCircle, Lock, BadgeCheck, CircleAlert, CircleHelp, TrendingDown, Loader2, Ban } from 'lucide-react';
import Chip, { type ChipVariant } from '@/components/archetypes/primitives/Chip';
import { SectionCard, DetailHeader, DetailSectionLabel, SourceNote, ComingSoonRow, InfoNote } from '@/components/archetypes/place';
import { findPlaceSection, detailAddress } from './sections';
import { usd, statusToState } from './format';
import { MoneyField, PrivacyNote, SkyButton, parseMoney, groupDigits } from './fields';
import { useLocalDraft } from './useLocalDraft';

const clampPct = (n: number) => Math.min(100, Math.max(0, n));

const BILL_VERDICT: Record<BenchmarkComparison, { text: string; color: string }> = {
  lower: { text: 'Lower than typical', color: 'text-app-success' },
  typical: { text: 'Typical for your area', color: 'text-app-text-secondary' },
  higher: { text: 'Higher than typical', color: 'text-app-warning' },
};

// Typed on the union, not `string`, so widening BillUtilityKind is a
// compile error here rather than a silent "Utility" heading above a summary
// line that correctly says "internet".
const UTILITY_LABEL: Record<BillUtilityKind, string> = {
  electric: 'Electric',
  gas: 'Gas',
  water: 'Water',
  sewer: 'Sewer',
  trash: 'Trash',
  internet: 'Internet',
  cable: 'Cable',
};

function BillBenchmark({ data }: { data: PlaceBillBenchmarkData }) {
  const your = data.your_amount;
  const band = data.band_high - data.band_low;
  const trackMin = Math.min(your ?? data.band_low, data.band_low) - band;
  const trackMax = Math.max(your ?? data.band_high, data.band_high) + band;
  const span = Math.max(1, trackMax - trackMin);
  const pos = (v: number) => clampPct(((v - trackMin) / span) * 100);
  const verdict = BILL_VERDICT[data.comparison];

  return (
    <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-[18px]">
      <div className="flex items-center gap-3">
        <span className="w-11 h-11 rounded-xl bg-app-home-bg border border-app-success-light flex items-center justify-center shrink-0">
          <Zap size={22} strokeWidth={2} className="text-app-home" />
        </span>
        <div className="flex-1 min-w-0">
          <div className="text-[13px] font-semibold text-app-text-secondary">{UTILITY_LABEL[data.utility] ?? 'Utility'} · monthly average</div>
          <div className="flex items-baseline gap-2.5 mt-0.5">
            <span className="text-[30px] font-bold -tracking-[0.02em] text-app-text">{your != null ? usd(your) : `${usd(data.band_low)}–${usd(data.band_high)}`}</span>
            <span className={`text-[14.5px] font-semibold ${verdict.color}`}>{verdict.text}</span>
          </div>
        </div>
      </div>

      <div className="relative h-3.5 mt-5 mb-2.5">
        <div className="absolute top-[3px] left-0 right-0 h-2 rounded-full bg-app-surface-sunken overflow-hidden">
          <div className="absolute inset-y-0 bg-app-success-light" style={{ left: `${pos(data.band_low)}%`, width: `${pos(data.band_high) - pos(data.band_low)}%` }} />
        </div>
        {your != null ? (
          <div className="absolute top-0 w-3.5 h-3.5 rounded-full bg-app-surface border-[3px] border-app-home shadow" style={{ left: `${pos(your)}%`, transform: 'translateX(-50%)' }} />
        ) : null}
      </div>
      <div className="flex justify-between text-[11px] font-semibold uppercase tracking-[0.02em] text-app-text-muted">
        <span>Lower</span>
        <span>Typical for your area</span>
        <span>Higher</span>
      </div>

      {data.summary ? (
        <div className="text-[14px] text-app-text-strong leading-5 mt-[15px] pt-[15px] border-t border-app-border-subtle">
          <span className="font-semibold">What this means:</span> {data.summary}
        </div>
      ) : null}
    </div>
  );
}

const LEVEL_LABEL: Record<IncentiveLevel, string> = { federal: 'Federal', state: 'State', utility: 'Utility', local: 'Local' };
const TYPE_LABEL: Record<IncentiveType, string> = { tax_credit: 'tax credit', rebate: 'rebate', discount: 'discount', loan: 'loan' };

function IncentiveRow({ item, isLast }: { item: PlaceIncentive; isLast: boolean }) {
  return (
    <div className={`flex items-start gap-3 px-3.5 py-3 ${isLast ? '' : 'border-b border-app-border-subtle'}`}>
      <span className="w-9 h-9 rounded-[9px] bg-app-home-bg flex items-center justify-center shrink-0 mt-0.5">
        <BadgePercent size={18} strokeWidth={2} className="text-app-home" />
      </span>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-[14.5px] font-semibold text-app-text -tracking-[0.01em]">{item.name}</span>
          <Chip label="You may be eligible" variant="info" />
        </div>
        <div className="text-[12.5px] text-app-text-muted mt-0.5">{LEVEL_LABEL[item.level]} · {TYPE_LABEL[item.incentive_type]}</div>
        {item.summary ? <div className="text-[13px] text-app-text-strong leading-[19px] mt-1">{item.summary}</div> : null}
      </div>
    </div>
  );
}

function IncentivesList({ data }: { data: PlaceIncentivesData }) {
  if (!data.programs || data.programs.length === 0) {
    return <SectionCard icon={BadgePercent} title="Incentives" state="empty" caption="No programs matched your address yet." />;
  }
  return (
    <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm overflow-hidden">
      {data.programs.map((p, i) => (
        <IncentiveRow key={p.id} item={p} isLast={i === data.programs.length - 1} />
      ))}
      <div className="flex items-start gap-2 px-3.5 py-3 bg-app-surface-muted border-t border-app-border-subtle">
        <Lock size={14} strokeWidth={2} className="mt-0.5 shrink-0 text-app-text-muted" />
        <span className="text-[12.5px] leading-[18px] text-app-text-secondary">Eligibility is an estimate based on your address and home. Verify the details and amounts with each provider before counting on them.</span>
      </div>
    </div>
  );
}

function RentBand({ data, homeId }: { data: PlaceRentBandData; homeId: string | null }) {
  const [rent, setRent, hydrated] = useLocalDraft(homeId ? `place:rent:${homeId}` : null, '');
  const [editing, setEditing] = useState(false);

  const span = Math.max(1, data.market_high - data.market_low);
  const pos = (v: number) => clampPct(((v - data.market_low) / span) * 100);
  const rnum = parseMoney(rent);
  const showMarker = hydrated && rnum > 0 && !editing;

  const verdict: { text: string; variant: ChipVariant } =
    rnum >= data.band_low && rnum <= data.band_high
      ? { text: 'Within the band', variant: 'success' }
      : rnum < data.band_low
        ? { text: 'Below the band', variant: 'info' }
        : { text: 'Above the band', variant: 'warning' };

  return (
    <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-[18px]">
      <div className="flex items-center gap-3 mb-1">
        <span className="w-11 h-11 rounded-xl bg-app-home-bg border border-app-success-light flex items-center justify-center shrink-0">
          <Building2 size={22} strokeWidth={2} className="text-app-home" />
        </span>
        <div className="flex-1 min-w-0">
          <div className="text-[13px] font-semibold text-app-text-secondary">{data.bedrooms}-bedroom market band</div>
          <div className="text-2xl font-bold -tracking-[0.02em] text-app-text mt-0.5">{usd(data.band_low)} – {usd(data.band_high)}</div>
        </div>
      </div>
      <div className="text-[13px] text-app-text-muted">Typical asking rent for your area</div>

      <div className="relative h-3.5 mt-[18px] mb-2.5">
        <div className="absolute top-[3px] left-0 right-0 h-2 rounded-full bg-app-surface-sunken overflow-hidden">
          <div className="absolute inset-y-0 bg-app-success-light" style={{ left: `${pos(data.band_low)}%`, width: `${pos(data.band_high) - pos(data.band_low)}%` }} />
        </div>
        {showMarker ? (
          <div className="absolute top-0 w-3.5 h-3.5 rounded-full bg-app-surface border-[3px] border-primary-600 shadow" style={{ left: `${pos(rnum)}%`, transform: 'translateX(-50%)' }} />
        ) : null}
      </div>
      <div className="flex justify-between text-[11px] font-semibold text-app-text-muted">
        <span>{usd(data.market_low)}</span>
        <span>Market band</span>
        <span>{usd(data.market_high)}</span>
      </div>

      {showMarker ? (
        <div className="flex items-center justify-between mt-4 pt-[15px] border-t border-app-border-subtle">
          <div>
            <div className="flex items-center gap-1.5 text-[12.5px] font-semibold text-app-text-secondary">
              <Lock size={12} strokeWidth={2} /> Your rent
            </div>
            <div className="text-xl font-bold text-app-text mt-0.5">{usd(rnum)}<span className="text-[13px] font-medium text-app-text-secondary"> /mo</span></div>
          </div>
          <div className="flex flex-col items-end gap-1.5">
            <Chip label={verdict.text} variant={verdict.variant} />
            <button type="button" onClick={() => setEditing(true)} className="text-[13px] font-semibold text-primary-600 hover:text-primary-700 transition">Edit</button>
          </div>
        </div>
      ) : editing || (hydrated && rnum > 0) ? (
        <div className="mt-4 pt-[15px] border-t border-app-border-subtle">
          <MoneyField label="Your monthly rent" prefix="$" value={rent} onChange={(v) => setRent(groupDigits(v))} placeholder="0" />
          <div className="mt-3">
            <SkyButton onClick={() => setEditing(false)}>Show where I fall</SkyButton>
          </div>
          <PrivacyNote>Saved on this device only — never shown to neighbors or on your public place.</PrivacyNote>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setEditing(true)}
          className="w-full flex items-center gap-3 mt-4 bg-app-surface-muted border border-app-border-subtle rounded-xl px-3 py-3 text-left hover:bg-app-hover transition"
        >
          <PlusCircle size={19} strokeWidth={2} className="text-primary-600 shrink-0" />
          <span className="flex-1 text-[14px] font-semibold text-app-text-strong">Add your rent to see where you fall</span>
          <span className="inline-flex items-center gap-1 text-[12px] font-medium text-app-text-muted shrink-0"><Lock size={12} strokeWidth={2} />Private</span>
        </button>
      )}
    </div>
  );
}

// ── Exemption check (Wave 2) — the honesty ladder as a card ──
// on_file (green, nothing to do) · none_on_file (amber — THE hook:
// exemptions aren't automatic) · unknown (neutral — the county feed
// doesn't report exemption status; never dressed as either).

const EXEMPTION_STATES: Record<PlaceExemptionCheckData['filing_status'], {
  chip: string; variant: ChipVariant; icon: typeof BadgeCheck; lead: (d: PlaceExemptionCheckData) => string;
}> = {
  on_file: {
    chip: 'On file',
    variant: 'success',
    icon: BadgeCheck,
    lead: (d) => `The county's record shows ${d.exemptions.join(', ')} on this parcel — nothing to chase.`,
  },
  none_on_file: {
    chip: 'Nothing on file',
    variant: 'warning',
    icon: CircleAlert,
    lead: () => 'No exemption appears on the county’s record for this parcel. Exemptions usually aren’t automatic — if this is your primary residence, it’s worth checking whether one applies.',
  },
  unknown: {
    chip: 'Not reported',
    variant: 'neutral',
    icon: CircleHelp,
    lead: () => 'This county’s assessor feed doesn’t report exemption status to our data provider, so we can’t tell what’s on file. Your tax bill or the assessor’s site will say.',
  },
};

function ExemptionCard({ data }: { data: PlaceExemptionCheckData }) {
  const state = EXEMPTION_STATES[data.filing_status] ?? EXEMPTION_STATES.unknown;
  const Icon = state.icon;
  return (
    <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-[18px]">
      <div className="flex items-center gap-3">
        <span className="w-11 h-11 rounded-xl bg-app-home-bg flex items-center justify-center shrink-0">
          <Icon size={22} strokeWidth={2} className="text-app-home" />
        </span>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[15.5px] font-semibold text-app-text -tracking-[0.01em]">Homestead exemption</span>
            <Chip label={state.chip} variant={state.variant} />
          </div>
          <div className="text-[12.5px] text-app-text-muted mt-0.5">From the county assessor&apos;s parcel record</div>
        </div>
      </div>
      <div className="text-[13.5px] text-app-text-strong leading-[20px] mt-3 pt-3 border-t border-app-border-subtle">
        {state.lead(data)}
      </div>
      <div className="mt-2.5 px-3.5 py-3 bg-app-surface-muted border border-app-border-subtle rounded-[11px]">
        <div className="text-[12.5px] font-semibold text-app-text">{data.state_program.label}</div>
        <div className="text-[12.5px] text-app-text-secondary leading-[18px] mt-0.5">{data.state_program.note}</div>
      </div>
      {data.assessment_signal && (
        <div className="mt-2.5 pt-2.5 border-t border-app-border-subtle">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[12px] font-semibold tracking-[0.04em] uppercase text-app-text-muted">Assessment vs county market value</span>
            {data.assessment_signal.stance === 'above' && <Chip label={`${data.assessment_signal.ratio_pct}% above`} variant="warning" />}
            {data.assessment_signal.stance === 'near' && <Chip label="Within 5%" variant="neutral" />}
            {data.assessment_signal.stance === 'below' && <Chip label={`${Math.abs(data.assessment_signal.ratio_pct)}% below`} variant="success" />}
          </div>
          <div className="text-[13px] text-app-text-strong leading-[19px] mt-1.5">
            Assessed at ${data.assessment_signal.assessed_value.toLocaleString()} against the county&apos;s own ${data.assessment_signal.market_value.toLocaleString()} market value.
            {data.assessment_signal.stance === 'above'
              ? ' An assessment meaningfully above the county’s market value is the usual basis for an appeal — appeals are filed with your county, typically in a window after assessment notices mail.'
              : ' Nothing here suggests the usual basis for an appeal.'}
          </div>
        </div>
      )}
    </div>
  );
}

// ── Rate watch (Wave 2b) — Home Record Watch's free half ─────
// One user-entered fact (the month the loan was recorded) held against
// Freddie Mac's weekly PMMS average. Averages and deltas only — the
// copy never says "refinance".

function RateWatchCard({ watch, homeId }: { watch: RecordWatch; homeId: string }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const ev = watch.evaluation;
  const monthLabel = new Date(`${watch.loan_recorded_month}-01T00:00:00Z`)
    .toLocaleDateString('en-US', { month: 'long', year: 'numeric', timeZone: 'UTC' });

  const removeMutation = useMutation({
    mutationFn: () => api.recordWatch.deleteRecordWatch(homeId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.recordWatch(homeId) });
      toast.success('Watch removed.');
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : 'Could not remove the watch.'),
  });

  if (editing) {
    return <RateWatchForm homeId={homeId} initialMonth={watch.loan_recorded_month} onDone={() => setEditing(false)} />;
  }

  return (
    <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-[18px]">
      <div className="flex items-center gap-3">
        <span className="w-11 h-11 rounded-xl bg-app-home-bg flex items-center justify-center shrink-0">
          <TrendingDown size={22} strokeWidth={2} className="text-app-home" />
        </span>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[15.5px] font-semibold text-app-text -tracking-[0.01em]">Rate watch</span>
            {ev && (ev.refi_window
              ? <Chip label={`${Math.abs(ev.delta_pp).toFixed(2)}pp below your month`} variant="success" />
              : <Chip label={`${ev.delta_pp > 0 ? '+' : ''}${ev.delta_pp.toFixed(2)}pp vs your month`} variant="neutral" />)}
          </div>
          <div className="text-[12.5px] text-app-text-muted mt-0.5">Watching against {monthLabel}, when your loan was recorded</div>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-3 mt-3 pt-3 border-t border-app-border-subtle">
        <div>
          <div className="text-[11px] font-semibold tracking-[0.04em] uppercase text-app-text-muted mb-0.5">{monthLabel} average</div>
          <div className="text-[17px] font-bold text-app-text">{watch.baseline_rate.toFixed(2)}%</div>
        </div>
        <div>
          <div className="text-[11px] font-semibold tracking-[0.04em] uppercase text-app-text-muted mb-0.5">This week</div>
          <div className="text-[17px] font-bold text-app-text">{ev ? `${ev.current_rate.toFixed(2)}%` : '—'}</div>
        </div>
      </div>
      <div className="text-[12.5px] text-app-text-muted leading-[18px] mt-2.5">
        {ev?.refi_window
          ? 'The market average is meaningfully below your loan month’s average — the comparison lenders start from. We’ll nudge you when it moves further.'
          : 'We check the weekly market average against your month and nudge you if it falls meaningfully below — before the mail offers do.'}
      </div>
      <div className="flex items-center gap-2 mt-3 pt-3 border-t border-app-border-subtle">
        <button
          type="button"
          onClick={() => setEditing(true)}
          className="flex-1 h-10 rounded-[10px] border-[1.5px] border-app-border bg-app-surface text-app-text text-[13.5px] font-semibold hover:bg-app-hover transition"
        >
          Change month
        </button>
        <button
          type="button"
          onClick={() => removeMutation.mutate()}
          disabled={removeMutation.isPending}
          className="h-10 px-3.5 rounded-[10px] border-[1.5px] border-app-border bg-app-surface text-app-error text-[13.5px] font-semibold flex items-center justify-center gap-1.5 hover:bg-app-error-light/40 transition disabled:opacity-50"
        >
          <Ban size={15} strokeWidth={2} /> Remove
        </button>
      </div>
    </div>
  );
}

function RateWatchForm({ homeId, initialMonth, onDone }: { homeId: string; initialMonth?: string; onDone?: () => void }) {
  const [month, setMonth] = useState(initialMonth ?? '');
  const queryClient = useQueryClient();

  const setMutation = useMutation({
    mutationFn: () => api.recordWatch.setRecordWatch(homeId, month),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.recordWatch(homeId) });
      toast.success('Watch set — we’ll compare the weekly average against your month.');
      onDone?.();
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : 'Could not save the watch.'),
  });

  return (
    <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-[18px]">
      <div className="flex items-center gap-3">
        <span className="w-11 h-11 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
          <TrendingDown size={22} strokeWidth={2} className="text-primary-600" />
        </span>
        <div className="flex-1 min-w-0">
          <div className="text-[15.5px] font-semibold text-app-text -tracking-[0.01em]">Watch rates against your loan</div>
          <div className="text-[12.5px] text-app-text-muted mt-0.5">Hear it from your dashboard before the refi mailers find you</div>
        </div>
      </div>
      <label htmlFor="rate-watch-month" className="block text-[12.5px] font-semibold text-app-text-secondary mt-3.5 mb-1.5">
        The month your loan was recorded
      </label>
      <input
        id="rate-watch-month"
        type="month"
        value={month}
        onChange={(e) => setMonth(e.target.value)}
        className="w-full h-[46px] px-3.5 text-[15px] text-app-text bg-app-surface border-[1.5px] border-app-border rounded-[10px] outline-none transition focus:border-primary-600 focus:ring-4 focus:ring-primary-600/10"
      />
      <button
        type="button"
        onClick={() => setMutation.mutate()}
        disabled={setMutation.isPending || !month}
        className="w-full h-11 mt-3 rounded-xl bg-primary-600 text-white text-[14.5px] font-semibold flex items-center justify-center gap-2 hover:bg-primary-700 transition disabled:opacity-60"
      >
        {setMutation.isPending ? <Loader2 size={17} className="animate-spin" /> : <TrendingDown size={17} strokeWidth={2.25} />}
        {initialMonth ? 'Update watch' : 'Start watching'}
      </button>
      <div className="text-[12px] text-app-text-muted leading-[17px] mt-2">
        We compare Freddie Mac&apos;s weekly 30-year survey average with the average for your month — facts about the market, not refinancing advice. Only you can see this.
      </div>
    </div>
  );
}

function RateWatchSection({ homeId, verified }: { homeId: string; verified: boolean }) {
  const watchQuery = useQuery({
    queryKey: queryKeys.recordWatch(homeId),
    queryFn: () => api.recordWatch.getRecordWatch(homeId),
    enabled: verified,
  });

  if (!verified) {
    return (
      <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-4 flex items-center gap-3.5">
        <span className="w-11 h-11 rounded-xl bg-app-surface-sunken flex items-center justify-center shrink-0">
          <Lock size={20} strokeWidth={2} className="text-app-text-muted" />
        </span>
        <div className="flex-1 min-w-0">
          <div className="text-[14.5px] font-semibold text-app-text">Rate watch</div>
          <div className="text-[12.5px] text-app-text-muted mt-0.5">Verify your address to watch the market against the month your loan was recorded — only the proven resident can watch a home.</div>
        </div>
      </div>
    );
  }
  if (watchQuery.isLoading) {
    return <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-4 text-[13.5px] text-app-text-muted">Loading your watch…</div>;
  }
  // A failed read must never impersonate "no watch" — the create form it
  // rendered would quietly overwrite the real watch's month on submit.
  if (watchQuery.isError) {
    return (
      <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-4">
        <div className="text-[13.5px] text-app-text-strong">Couldn&apos;t load your rate watch.</div>
        <button
          type="button"
          onClick={() => watchQuery.refetch()}
          className="mt-2 h-9 px-3.5 rounded-[10px] border-[1.5px] border-app-border bg-app-surface text-app-text text-[13px] font-semibold hover:bg-app-hover transition"
        >
          Try again
        </button>
      </div>
    );
  }
  return watchQuery.data
    ? <RateWatchCard watch={watchQuery.data} homeId={homeId} />
    : <RateWatchForm homeId={homeId} />;
}

export default function MoneyDetail({ intelligence, homeId }: { intelligence: PlaceIntelligence; homeId: string | null }) {
  const bill = findPlaceSection(intelligence, 'bill_benchmark');
  const incentives = findPlaceSection(intelligence, 'incentives');
  const rent = findPlaceSection(intelligence, 'rent_band');
  const exemption = findPlaceSection(intelligence, 'exemption_check');

  const billReady = bill && (bill.status === 'ready' || bill.status === 'stale' || bill.status === 'partial') && bill.data;
  const incReady = incentives && (incentives.status === 'ready' || incentives.status === 'stale' || incentives.status === 'partial') && incentives.data;
  const rentReady = rent && (rent.status === 'ready' || rent.status === 'stale' || rent.status === 'partial') && rent.data;
  const exemptionReady = exemption && exemption.access === 'available'
    && (exemption.status === 'ready' || exemption.status === 'stale') && exemption.data;

  return (
    <>
      <DetailHeader title="Money signals" address={detailAddress(intelligence.place)} />
      <div className="px-4 sm:px-5 pt-1 pb-16">
        <DetailSectionLabel>Bill benchmark</DetailSectionLabel>
        {billReady ? (
          <BillBenchmark data={bill!.data as PlaceBillBenchmarkData} />
        ) : (
          <SectionCard icon={Zap} title="Bill benchmark" state={bill ? statusToState(bill.status) : 'unavailable'} caption={bill?.unavailable_reason ?? undefined} onRetry={() => window.location.reload()} />
        )}
        {bill?.source ? <SourceNote name={bill.source} asOf="12-month average" /> : null}

        <DetailSectionLabel>Incentives you may qualify for</DetailSectionLabel>
        {incReady ? (
          <IncentivesList data={incentives!.data as PlaceIncentivesData} />
        ) : (
          <SectionCard icon={BadgePercent} title="Incentives" state={incentives ? statusToState(incentives.status) : 'unavailable'} caption={incentives?.unavailable_reason ?? undefined} onRetry={() => window.location.reload()} />
        )}
        {incentives?.source ? <SourceNote name={incentives.source} /> : null}

        <DetailSectionLabel>Rent</DetailSectionLabel>
        {rentReady ? (
          <RentBand data={rent!.data as PlaceRentBandData} homeId={homeId} />
        ) : (
          <SectionCard icon={Building2} title="Rent band" state={rent ? statusToState(rent.status) : 'unavailable'} caption={rent?.unavailable_reason ?? undefined} onRetry={() => window.location.reload()} />
        )}
        {rent?.source ? <SourceNote name={rent.source} asOf="FY 2026" /> : null}

        <DetailSectionLabel>Property-tax exemption</DetailSectionLabel>
        {exemptionReady ? (
          <ExemptionCard data={exemption!.data as PlaceExemptionCheckData} />
        ) : (
          <SectionCard
            icon={Landmark}
            title="Homestead exemption"
            state={exemption && exemption.access === 'available' ? statusToState(exemption.status) : 'unavailable'}
            caption={exemption?.unavailable_reason ?? undefined}
            onRetry={() => window.location.reload()}
          />
        )}
        {exemption?.source && exemptionReady ? <SourceNote name={exemption.source} /> : null}

        <DetailSectionLabel>Rate watch</DetailSectionLabel>
        {homeId ? (
          <RateWatchSection homeId={homeId} verified={intelligence.tier === 'T4'} />
        ) : (
          <ComingSoonRow icon={TrendingDown} title="Rate watch" sub="Claim your place to watch the market against your loan month" />
        )}
        <SourceNote name="Freddie Mac Primary Mortgage Market Survey" asOf="weekly" />
        <ComingSoonRow icon={Landmark} title="Deed & lien alerts" sub="Know within days if anyone records against your home — only you can watch it" />

        <DetailSectionLabel>Property tax</DetailSectionLabel>
        <ComingSoonRow icon={Landmark} title="Property tax check" sub="Your assessment vs nearby comps + how appeals work" />

        <InfoNote>
          Everything here is informational, drawn from public data and your own entries. It isn&apos;t financial, tax, or legal advice, and amounts aren&apos;t guarantees.
        </InfoNote>
      </div>
    </>
  );
}
