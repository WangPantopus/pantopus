// ============================================================
// Place — Risk & Readiness detail (C5).
// Flood zone with its plain meaning (FEMA), the deferred hazards as
// "coming soon", and an informational household-readiness checklist
// (device-local, never instructions). Flood reads from the contract;
// the checklist is a self-assessment tool the resident keeps privately.
// ============================================================

'use client';

import type { LucideIcon } from 'lucide-react';
import type { PlaceIntelligence, PlaceFloodData, FloodRiskLevel } from '@pantopus/types';
import {
  Waves,
  ShieldCheck,
  TriangleAlert,
  Activity,
  Flame,
  LifeBuoy,
  Briefcase,
  Phone,
  MapPin,
  Check,
} from 'lucide-react';
import Chip, { type ChipVariant } from '@/components/archetypes/primitives/Chip';
import { SectionCard, DetailHeader, DetailSectionLabel, SourceNote, ComingSoonRow, InfoNote } from '@/components/archetypes/place';
import { findPlaceSection, detailAddress } from './sections';
import { statusToState } from './format';
import { useLocalDraft } from './useLocalDraft';

const FLOOD_CHIP: Record<FloodRiskLevel, { label: string; variant: ChipVariant; icon: LucideIcon }> = {
  minimal: { label: 'Minimal risk', variant: 'success', icon: ShieldCheck },
  moderate: { label: 'Moderate risk', variant: 'warning', icon: TriangleAlert },
  high: { label: 'High risk', variant: 'error', icon: TriangleAlert },
};

function FloodCard({ data }: { data: PlaceFloodData }) {
  const chip = FLOOD_CHIP[data.risk_level] ?? { label: data.zone_label, variant: 'neutral' as ChipVariant, icon: Waves };
  return (
    <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-[18px]">
      <div className="flex items-center gap-3">
        <span className="w-[46px] h-[46px] rounded-[13px] bg-app-home-bg border border-app-success-light flex items-center justify-center shrink-0">
          <Waves size={24} strokeWidth={2} className="text-app-home" />
        </span>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2.5 flex-wrap">
            <span className="text-[20px] font-bold -tracking-[0.015em] text-app-text">{data.zone_label}</span>
            <Chip label={chip.label} variant={chip.variant} icon={chip.icon} />
          </div>
          <div className="text-[13px] text-app-text-secondary mt-0.5">FEMA flood hazard area</div>
        </div>
      </div>
      {data.plain_meaning ? (
        <div className="text-[14px] text-app-text-strong leading-5 mt-[15px] pt-[15px] border-t border-app-border-subtle">
          <span className="font-semibold">What this means:</span> {data.plain_meaning}
        </div>
      ) : null}
    </div>
  );
}

// ── Emergency readiness — informational, device-local checklist ──
interface PlanItem {
  id: string;
  title: string;
  sub: string;
}
interface PlanGroup {
  label: string;
  icon: LucideIcon;
  items: PlanItem[];
}

const PLAN_GROUPS: PlanGroup[] = [
  {
    label: 'Go-bag essentials',
    icon: Briefcase,
    items: [
      { id: 'water', title: 'Water', sub: 'One gallon per person per day, 3-day supply' },
      { id: 'food', title: 'Non-perishable food', sub: '3-day supply that needs no cooking' },
      { id: 'light', title: 'Flashlight & spare batteries', sub: 'One per person' },
      { id: 'aid', title: 'First-aid kit', sub: 'Stocked and in-date' },
      { id: 'meds', title: '7-day medication supply', sub: 'Plus copies of prescriptions' },
      { id: 'docs', title: 'Copies of key documents', sub: 'ID, insurance, deed — sealed in a bag' },
    ],
  },
  {
    label: 'Key contacts',
    icon: Phone,
    items: [
      { id: 'outarea', title: 'Out-of-area contact', sub: 'A friend or relative in another city' },
      { id: 'utility', title: 'Utility shutoff info', sub: 'Gas, water, and electric main locations' },
      { id: 'insure', title: 'Insurance & policy number', sub: 'Saved where you can reach it offline' },
    ],
  },
  {
    label: 'Meeting point',
    icon: MapPin,
    items: [
      { id: 'near', title: 'A spot near home', sub: 'Somewhere just outside, easy to reach' },
      { id: 'far', title: 'A spot outside the neighborhood', sub: 'In case you can’t get back home' },
    ],
  },
];

const ALL_ITEMS = PLAN_GROUPS.flatMap((g) => g.items);

function CheckRow({ item, checked, onToggle, isLast }: { item: PlanItem; checked: boolean; onToggle: () => void; isLast: boolean }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={checked}
      className={`flex items-center gap-3 w-full text-left px-3.5 py-2.5 ${isLast ? '' : 'border-b border-app-border-subtle'} hover:bg-app-hover transition`}
    >
      <span className={`w-6 h-6 rounded-full shrink-0 flex items-center justify-center transition ${checked ? 'bg-app-home border border-app-home' : 'bg-app-surface border-[1.75px] border-app-border-strong'}`}>
        {checked ? <Check size={14} strokeWidth={3} className="text-white" /> : null}
      </span>
      <div className="flex-1 min-w-0">
        <div className={`text-[14.5px] font-semibold -tracking-[0.01em] ${checked ? 'text-app-text' : 'text-app-text-strong'}`}>{item.title}</div>
        <div className="text-[12.5px] text-app-text-muted mt-0.5">{item.sub}</div>
      </div>
    </button>
  );
}

function EmergencyPlan({ homeId }: { homeId: string | null }) {
  const [checked, setChecked, hydrated] = useLocalDraft<Record<string, boolean>>(homeId ? `place:emergency:${homeId}` : null, {});
  const doneCount = ALL_ITEMS.filter((it) => checked[it.id]).length;
  const total = ALL_ITEMS.length;
  const pct = (doneCount / total) * 100;
  const toggle = (id: string) => setChecked({ ...checked, [id]: !checked[id] });

  return (
    <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm overflow-hidden">
      <div className="px-4 pt-4 pb-[15px] border-b border-app-border-subtle">
        <div className="flex items-center justify-between mb-2.5">
          <div className="flex items-center gap-2">
            <LifeBuoy size={19} strokeWidth={2} className="text-app-home" />
            <span className="text-base font-bold -tracking-[0.01em] text-app-text">Your household plan</span>
          </div>
          <span className={`text-[13px] font-semibold ${hydrated && doneCount === total ? 'text-app-success' : 'text-app-text-secondary'}`}>{hydrated ? `${doneCount} of ${total} ready` : `${total} steps`}</span>
        </div>
        <div className="h-[7px] rounded-full bg-app-surface-sunken overflow-hidden">
          <div className="h-full rounded-full bg-app-home transition-[width] duration-200" style={{ width: `${hydrated ? pct : 0}%` }} />
        </div>
      </div>
      {PLAN_GROUPS.map((g, gi) => {
        const GroupIcon = g.icon;
        return (
          <div key={g.label} className={gi === PLAN_GROUPS.length - 1 ? '' : 'border-b border-app-border-subtle'}>
            <div className="flex items-center gap-2 px-3.5 pt-3 pb-1.5">
              <GroupIcon size={14} strokeWidth={2} className="text-app-text-muted" />
              <span className="text-[11.5px] font-bold tracking-[0.06em] uppercase text-app-text-muted">{g.label}</span>
            </div>
            {g.items.map((it, i) => (
              <CheckRow key={it.id} item={it} checked={!!checked[it.id]} onToggle={() => toggle(it.id)} isLast={i === g.items.length - 1} />
            ))}
          </div>
        );
      })}
    </div>
  );
}

export default function RiskDetail({ intelligence, homeId }: { intelligence: PlaceIntelligence; homeId: string | null }) {
  const flood = findPlaceSection(intelligence, 'flood');
  const floodReady = flood && (flood.status === 'ready' || flood.status === 'stale' || flood.status === 'partial') && flood.data;

  return (
    <>
      <DetailHeader title="Risk & readiness" address={detailAddress(intelligence.place)} />
      <div className="px-4 sm:px-5 pt-1 pb-16">
        <DetailSectionLabel>Flood</DetailSectionLabel>
        {floodReady ? (
          <FloodCard data={flood!.data as PlaceFloodData} />
        ) : (
          <SectionCard icon={Waves} title="Flood" state={flood ? statusToState(flood.status) : 'unavailable'} caption={flood?.unavailable_reason ?? undefined} onRetry={() => window.location.reload()} />
        )}
        {flood?.source ? <SourceNote name={flood.source} asOf="as of 2024" /> : null}

        <DetailSectionLabel>Other hazards</DetailSectionLabel>
        <div className="flex flex-col gap-2">
          <ComingSoonRow icon={Activity} title="Earthquake" sub="Seismic zone and liquefaction risk" />
          <ComingSoonRow icon={Flame} title="Wildfire" sub="Wildland-urban interface rating" />
        </div>

        <DetailSectionLabel>Emergency plan</DetailSectionLabel>
        <EmergencyPlan homeId={homeId} />
        <SourceNote name="Recommended items from Ready.gov & American Red Cross" />

        <InfoNote>
          Informational, not emergency instructions. In a real emergency, call 911 and follow guidance from local officials.
        </InfoNote>
      </div>
    </>
  );
}
