'use client';

// Pantopus — `<MailItemDetailShell />` preview canvas.
//
// T6.5a (P19) — Designer sanity check for the A17 archetype shell.
// Shows one example of every slot configuration so the designer can
// confirm spacing, top bar trust dot, AI elf strip, attachments tiles,
// and the sticky actions shelf land per the design files. Not linked
// from the production navigation — accessed via `/mail-item-detail-preview`.

import type { ReactNode } from 'react';
import {
  Archive,
  BadgeCheck,
  Bell,
  Bookmark,
  Calendar,
  CheckCircle2,
  ChevronRight,
  Forward,
  Landmark,
  MapPin,
  Pencil,
  Send,
  Trash2,
} from 'lucide-react';
import {
  MailItemDetailShell,
  type AIElfStripContent,
  type AttachmentsRowContent,
  type MailTopBarConfig,
} from '@/components/mail-item-detail';

export default function MailItemDetailPreviewPage() {
  return (
    <div className="min-h-screen bg-slate-100 px-4 py-8">
      <div className="mx-auto max-w-7xl">
        <header className="mb-6">
          <h1 className="text-2xl font-bold text-app-text">MailItemDetailShell preview</h1>
          <p className="mt-1 text-sm text-app-text-secondary">
            P19 / T6.5a — every slot configuration of the A17 archetype shell. P20–P23 variants
            (Generic, Booklet, Certified, Community, Ceremonial) sit on top of this scaffold.
          </p>
        </header>

        <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
          <PreviewFrame title="A — every slot supplied" subtitle="Generic mail · full layout">
            <MailItemDetailShell {...EVERY_SLOT_PROPS} />
          </PreviewFrame>

          <PreviewFrame title="B — required slots only" subtitle="Top bar + hero only">
            <MailItemDetailShell
              topBar={REQUIRED_ONLY_TOP_BAR}
              hero={
                <div className="rounded-2xl border border-app-border bg-app-surface p-4 shadow-sm">
                  <div className="mb-2 text-[11px] font-bold uppercase tracking-wide text-app-text-secondary">
                    Booklet · League of Women Voters
                  </div>
                  <div className="text-[19px] font-bold leading-snug text-app-text">
                    June 2026 primary voter guide
                  </div>
                  <div className="mt-1 font-mono text-[11px] text-app-text-secondary">
                    Vol. 47 · 28 pages
                  </div>
                </div>
              }
            />
          </PreviewFrame>

          <PreviewFrame title="C — nil aiElf + attachments" subtitle="Optional payloads skipped">
            <MailItemDetailShell
              topBar={REQUIRED_ONLY_TOP_BAR}
              aiElf={null}
              attachments={null}
              hero={
                <div className="rounded-2xl border border-app-border bg-app-surface p-4 shadow-sm">
                  <div className="mb-2 text-[11px] font-bold uppercase tracking-wide text-app-text-secondary">
                    Coupon · Acme Bakery
                  </div>
                  <div className="text-[19px] font-bold leading-snug text-app-text">
                    20% off any loaf — this Saturday only
                  </div>
                </div>
              }
              keyFacts={
                <div className="rounded-2xl border border-app-border bg-app-surface p-3.5 shadow-sm">
                  <h3 className="mb-2 text-[11px] font-bold uppercase tracking-wide text-app-text-secondary">
                    Coupon
                  </h3>
                  <div className="grid grid-cols-2 gap-2 text-[12px] text-app-text">
                    <div>
                      <div className="text-app-text-secondary">Expires</div>
                      <div className="font-semibold">Sat, May 24</div>
                    </div>
                    <div>
                      <div className="text-app-text-secondary">Code</div>
                      <div className="font-mono font-semibold">LOAF20</div>
                    </div>
                  </div>
                </div>
              }
              body={
                <div className="rounded-2xl border border-app-border bg-app-surface p-3.5 shadow-sm">
                  <p className="text-[13px] leading-relaxed text-app-text-strong">
                    Bring this coupon (or just show this page) to Acme Bakery between 9 AM and
                    closing on Saturday for 20% off any loaf.
                  </p>
                </div>
              }
              sender={
                <div className="flex items-center gap-3 rounded-2xl border border-app-border bg-app-surface p-3.5 shadow-sm">
                  <div className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl bg-amber-500 text-[14px] font-bold text-white">
                    AB
                  </div>
                  <div className="flex-1">
                    <div className="text-[14px] font-bold text-app-text">Acme Bakery</div>
                    <div className="text-[12px] text-app-text-secondary">Local · 0.4mi away</div>
                  </div>
                  <ChevronRight className="h-4 w-4 text-app-text-muted" aria-hidden />
                </div>
              }
              actions={
                <button
                  type="button"
                  className="flex w-full items-center justify-center gap-2 rounded-xl bg-sky-600 px-4 py-3.5 text-[15px] font-bold text-white shadow-sm"
                >
                  <Bookmark className="h-4 w-4" aria-hidden />
                  Save coupon
                </button>
              }
            />
          </PreviewFrame>
        </div>
      </div>
    </div>
  );
}

function PreviewFrame({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
}) {
  return (
    <section>
      <div className="mb-2">
        <div className="text-[15px] font-bold text-app-text">{title}</div>
        <div className="text-[12px] text-app-text-secondary">{subtitle}</div>
      </div>
      <div className="relative h-[820px] w-[360px] overflow-hidden rounded-[40px] border border-slate-200 bg-app-bg shadow-xl ring-1 ring-black/5">
        <div className="h-full w-full overflow-hidden">{children}</div>
      </div>
    </section>
  );
}

// ─── Fixtures ────────────────────────────────────────────────

const REQUIRED_ONLY_TOP_BAR: MailTopBarConfig = {
  eyebrow: 'Booklet',
  trust: 'verified',
  onBack: () => {},
};

const FULL_TOP_BAR: MailTopBarConfig = {
  eyebrow: 'Certified',
  trust: 'verified',
  onBack: () => {},
  trailingAction: {
    icon: Bookmark,
    accessibilityLabel: 'Save to vault',
    isActive: false,
    onClick: () => {},
  },
  overflowItems: [
    { id: 'forward', icon: Forward, label: 'Forward', onSelect: () => {} },
    { id: 'archive', icon: Archive, label: 'Archive', onSelect: () => {} },
    { id: 'unread', icon: Bell, label: 'Mark unread', onSelect: () => {} },
    { id: 'delete', icon: Trash2, label: 'Delete', isDestructive: true, onSelect: () => {} },
  ],
};

const AI_ELF: AIElfStripContent = {
  headline: 'Pantopus read this for you',
  summary:
    'Your neighbor at 412 Elm wants a 2-foot rear-yard setback variance to extend their garage. ' +
    'The city is holding a public hearing June 3 — you can comment in writing or show up in person.',
  bullets: [
    { id: '1', icon: MapPin, label: 'Affects 412 Elm St', text: 'next door to you' },
    { id: '2', icon: Calendar, label: 'Hearing Tue Jun 3, 6:00 PM', text: 'City Hall, Room 1' },
    { id: '3', icon: Pencil, label: 'Written comment by May 30', text: 'optional' },
  ],
  trailingBadge: '2 min summary',
  onRedo: () => {},
};

const ATTACHMENTS: AttachmentsRowContent = {
  items: [
    { id: 'a1', kind: 'pdf', name: 'Public notice ZA-2026-0188.pdf', meta: '2 pages · 84 KB' },
    { id: 'a2', kind: 'image', name: 'Site plan and elevation.jpg', meta: '1.2 MB · J. Reyes' },
    { id: 'a3', kind: 'video', name: 'Hearing audio briefing.mp4', meta: '1m 22s' },
    { id: 'a4', kind: 'link', name: 'oaklandca.gov/planning-hearings', meta: 'External link' },
  ],
};

const EVERY_SLOT_PROPS = {
  topBar: FULL_TOP_BAR,
  aiElf: AI_ELF,
  attachments: ATTACHMENTS,
  hero: (
    <div className="relative overflow-hidden rounded-2xl border border-app-border bg-app-surface p-3.5 shadow-sm">
      <div className="absolute inset-y-0 left-0 w-1 bg-orange-500" aria-hidden />
      <div className="pl-2">
        <div className="mb-2 flex items-center gap-1.5 text-[11px] font-bold">
          <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-emerald-700">VERIFIED</span>
          <span className="rounded-full bg-orange-100 px-2 py-0.5 text-orange-700">CERTIFIED</span>
          <span className="ml-auto font-medium text-app-text-secondary">1h ago</span>
        </div>
        <div className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-app-text-secondary">
          City of Oakland · Planning
        </div>
        <div className="text-[19px] font-bold leading-snug text-app-text">
          Notice of public hearing — Zoning variance ZA-2026-0188
        </div>
        <div className="mt-1.5 font-mono text-[11px] text-app-text-secondary">
          Case ZA-2026-0188 · Cert # 7014-2026-0411
        </div>
      </div>
    </div>
  ),
  keyFacts: (
    <div className="overflow-hidden rounded-2xl border border-app-border bg-app-surface shadow-sm">
      <div className="border-b border-app-border-subtle px-3.5 py-2 text-[11px] font-bold uppercase tracking-wide text-app-text-secondary">
        Key facts
      </div>
      <ul>
        {[
          ['Hearing date', 'Tue, Jun 3, 2026 · 6:00 PM'],
          ['Location', 'Oakland City Hall, Room 1'],
          ['Subject property', '412 Elm St'],
          ['Comment deadline', 'Fri, May 30, 2026'],
        ].map(([label, value], i) => (
          <li
            key={label}
            className={
              'flex justify-between px-3.5 py-2 text-[13px] ' +
              (i < 3 ? 'border-b border-app-border-subtle' : '')
            }
          >
            <span className="text-app-text-secondary">{label}</span>
            <span className="font-semibold text-app-text">{value}</span>
          </li>
        ))}
      </ul>
    </div>
  ),
  body: (
    <div className="rounded-2xl border border-app-border bg-app-surface p-3.5 shadow-sm">
      <h3 className="mb-2 text-[11px] font-bold uppercase tracking-wide text-app-text-secondary">
        Notice text
      </h3>
      <p className="text-[13px] leading-relaxed text-app-text-strong">
        NOTICE IS HEREBY GIVEN that the Oakland Planning Commission will hold a public hearing on
        Tuesday, June 3, 2026, at 6:00 PM in Room 1 of City Hall.
      </p>
    </div>
  ),
  sender: (
    <div className="flex items-center gap-3 rounded-2xl border border-app-border bg-app-surface p-3.5 shadow-sm">
      <div className="relative flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl bg-blue-700 text-[14px] font-bold text-white">
        CO
        <span className="absolute -bottom-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full border-2 border-white bg-emerald-500 text-white">
          <BadgeCheck className="h-2.5 w-2.5" aria-hidden />
        </span>
      </div>
      <div className="flex-1">
        <div className="text-[14px] font-bold text-app-text">City of Oakland</div>
        <div className="text-[12px] text-app-text-secondary">Bureau of Planning · Hearings</div>
        <div className="mt-1.5 flex flex-wrap gap-1">
          <span className="inline-flex items-center gap-1 rounded-full bg-blue-100 px-1.5 py-0.5 text-[10px] font-bold text-blue-800">
            <Landmark className="h-2.5 w-2.5" aria-hidden />
            Verified government
          </span>
          <span className="rounded-full bg-emerald-100 px-1.5 py-0.5 text-[10px] font-bold text-emerald-700">
            Sender domain checked
          </span>
        </div>
      </div>
      <ChevronRight className="h-4 w-4 text-app-text-muted" aria-hidden />
    </div>
  ),
  actions: (
    <div className="flex flex-col gap-2.5">
      <button
        type="button"
        className="flex w-full items-center justify-center gap-2 rounded-xl bg-sky-600 px-4 py-3.5 text-[15px] font-bold text-white shadow-sm"
      >
        <CheckCircle2 className="h-4 w-4" aria-hidden />
        Acknowledge receipt
      </button>
      <div className="grid grid-cols-4 gap-2">
        {[
          { icon: Calendar, label: 'Calendar' },
          { icon: Send, label: 'Reply' },
          { icon: Forward, label: 'Forward' },
          { icon: Archive, label: 'Archive' },
        ].map((s) => (
          <button
            key={s.label}
            type="button"
            className="flex flex-col items-center gap-1 rounded-xl border border-app-border bg-app-surface px-1 py-2.5 text-[10.5px] font-semibold text-app-text-strong"
          >
            <s.icon className="h-4 w-4" aria-hidden />
            {s.label}
          </button>
        ))}
      </div>
    </div>
  ),
};
