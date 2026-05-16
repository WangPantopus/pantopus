'use client';

// Bills — Homes pillar list screen (T6.0a design-drift refresh).
//
// Built on the shared `<ListOfRowsShell />` archetype. Mirrors the iOS
// `BillsListView` + Android `BillsListScreen`. T6.0a drift from
// T5.2.2:
//   • 8 utility-tinted category tiles (electric / gas / water /
//     internet / hoa / insurance / trash / phone) derived client-side
//     from the payee string — see `./utility-palette.ts`.
//   • 6-status chip palette (added `dueSoon` for due-in-7d and
//     `cancelled` for soft-deleted rows).
//   • Summary banner above the list — 30-day total + overdue count,
//     tinted `home` per the home-pillar identity.
//   • Optional inline "Auto-pay" chip on scheduled rows.
//   • FAB shrunk to 56pt `canonicalCreate` + `home` tint (was 52pt
//     sky `secondaryCreate`); top-bar action removed (the FAB owns the
//     canonical create action).
//   • Detail modal header — category-tinted icon + auto-pay pill +
//     Category / Auto-pay detail rows.
//
// `splitWith` on RowModel is a future-ready field — Bills rows stay
// `splitWith: undefined` today because backend `/api/homes/:id/bills`
// doesn't surface split membership on the list endpoint. Splits remain
// visible on the detail modal via `getHomeBillSplits(...)`.
//
// Endpoints:
//   GET    /api/homes/:id/bills           — route backend/routes/home.js:4506
//   POST   /api/homes/:id/bills           — route backend/routes/home.js:4539
//   PUT    /api/homes/:id/bills/:billId   — route backend/routes/home.js:4585

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  AlertCircle,
  Calendar,
  Check,
  Clock,
  Plus,
  Receipt,
  RefreshCw,
  Trash2,
  Wallet,
  X,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import type { HomeBill } from '@pantopus/api/endpoints/homeProfile';
import ListOfRowsShell from '@/components/list-of-rows/ListOfRowsShell';
import type {
  BannerConfig,
  ListOfRowsState,
  RowChip,
  RowHighlight,
  RowModel,
  RowTrailing,
  StatusChipVariant,
} from '@/components/list-of-rows/types';
import ModalShell from '@/components/ui/ModalShell';
import { toast } from '@/components/ui/toast-store';
import { confirmStore } from '@/components/ui/confirm-store';
import {
  categoryFromPayee,
  utilityVisual,
  type UtilityCategory,
} from './utility-palette';

type BillTab = 'upcoming' | 'paid' | 'all';

/**
 * Canonical 6-state bill chip status, derived from `HomeBill.status` +
 * `due_date`. Discriminated union mirrors iOS `BillChipStatus`.
 */
type ChipStatus = 'paid' | 'overdue' | 'dueSoon' | 'scheduled' | 'due' | 'cancelled';

interface BillRowProjection {
  rowId: string;
  payee: string;
  subtitle: string;
  amount: string;
  chipText: string;
  chipVariant: StatusChipVariant;
  chipIcon: LucideIcon;
  status: ChipStatus;
  category: UtilityCategory;
  inlineChip?: RowChip;
  highlight?: RowHighlight;
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;
const SEVEN_DAYS = 7 * MS_PER_DAY;
const THIRTY_DAYS = 30 * MS_PER_DAY;

function formatCurrency(raw: number | string | null | undefined): string {
  if (raw == null) return '$0.00';
  const num = typeof raw === 'string' ? Number.parseFloat(raw) : raw;
  if (!Number.isFinite(num)) return '$0.00';
  return num.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
}

function formatDateShort(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function billAmount(bill: HomeBill): number {
  if (bill.amount_cents != null) return Number(bill.amount_cents) / 100;
  const raw = bill.amount;
  const num = typeof raw === 'string' ? Number.parseFloat(raw) : raw;
  return Number.isFinite(num) ? Number(num) : 0;
}

/**
 * Derive the chip status per the T6.0a contract:
 *   - `cancelled` when status is "cancelled"
 *   - `paid`      when status is "paid"
 *   - `scheduled` when status is "scheduled"
 *   - `overdue`   when due_date is in the past
 *   - `dueSoon`   when due_date is within the next 7 days
 *   - `due`       otherwise
 */
function deriveChipStatus(bill: HomeBill, now: Date): ChipStatus {
  if (bill.status === 'cancelled') return 'cancelled';
  if (bill.status === 'paid') return 'paid';
  if (bill.status === 'scheduled') return 'scheduled';
  if (bill.due_date) {
    const due = new Date(bill.due_date);
    if (!Number.isNaN(due.getTime())) {
      if (due.getTime() < now.getTime()) return 'overdue';
      if (due.getTime() <= now.getTime() + SEVEN_DAYS) return 'dueSoon';
    }
  }
  return 'due';
}

/**
 * Pure projection of one bill into a row's display fields. Exposed so
 * the detail modal header can reuse the chip / category derivation
 * without rebuilding it.
 */
function projectBill(bill: HomeBill, now: Date): BillRowProjection {
  const chip = deriveChipStatus(bill, now);
  const category = categoryFromPayee(bill.provider_name ?? bill.title ?? null);
  const visual = utilityVisual(category);
  const payee = bill.provider_name || bill.title || visual.label;
  const amount = formatCurrency(billAmount(bill));
  const dueShort = formatDateShort(bill.due_date);
  const paidShort = formatDateShort(bill.paid_at);

  const base = { rowId: bill.id, payee, amount, category };

  switch (chip) {
    case 'paid':
      return {
        ...base,
        subtitle: paidShort ? `Paid ${paidShort}` : 'Paid',
        chipText: 'Paid',
        chipVariant: 'success',
        chipIcon: Check,
        status: chip,
        highlight: undefined,
        inlineChip: undefined,
      };
    case 'cancelled':
      return {
        ...base,
        subtitle: 'Cancelled',
        chipText: 'Cancelled',
        chipVariant: 'neutral',
        chipIcon: X,
        status: chip,
        highlight: 'muted',
        inlineChip: undefined,
      };
    case 'overdue':
      return {
        ...base,
        subtitle: dueShort ? `Overdue · was due ${dueShort}` : 'Overdue',
        chipText: 'Overdue',
        chipVariant: 'error',
        chipIcon: AlertCircle,
        status: chip,
        highlight: undefined,
        inlineChip: undefined,
      };
    case 'dueSoon':
      return {
        ...base,
        subtitle: dueShort ? `Due ${dueShort}` : 'Due soon',
        chipText: 'Due soon',
        chipVariant: 'warning',
        chipIcon: Clock,
        status: chip,
        highlight: undefined,
        inlineChip: undefined,
      };
    case 'scheduled':
      return {
        ...base,
        subtitle: dueShort ? `Auto-pays ${dueShort}` : 'Auto-pay scheduled',
        chipText: 'Scheduled',
        chipVariant: 'info',
        chipIcon: Calendar,
        status: chip,
        inlineChip: {
          text: 'Auto-pay',
          icon: RefreshCw,
          tint: { kind: 'status', variant: 'info' },
        },
        highlight: undefined,
      };
    case 'due':
    default:
      return {
        ...base,
        subtitle: dueShort ? `Due ${dueShort}` : 'No due date',
        chipText: 'Due',
        chipVariant: 'warning',
        chipIcon: Clock,
        status: chip,
        highlight: undefined,
        inlineChip: undefined,
      };
  }
}

function passesTab(bill: HomeBill, tab: BillTab, now: Date): boolean {
  const chip = deriveChipStatus(bill, now);
  switch (tab) {
    case 'upcoming':
      // Upcoming excludes cancelled + paid; everything else
      // (due, dueSoon, overdue, scheduled) is upcoming.
      return chip !== 'cancelled' && chip !== 'paid';
    case 'paid':
      return chip === 'paid';
    case 'all':
      return chip !== 'cancelled';
  }
}

interface BillsBannerSummary {
  totalDueLabel: string | null;
  overdueCount: number;
  nextBillSubtitle: string | null;
}

/**
 * Pure banner summary projection — exported semantics mirror iOS
 * `BillsListViewModel.summarize`. Skips cancelled + paid rows; sums
 * everything due (including overdue) within the next 30 days and any
 * undated upcoming bills.
 */
function summarizeBills(bills: HomeBill[], now: Date): BillsBannerSummary {
  const thirtyOut = now.getTime() + THIRTY_DAYS;
  let totalDue = 0;
  let totalCount = 0;
  let overdueCount = 0;
  let nextDue: { date: Date } | null = null;
  for (const bill of bills) {
    const chip = deriveChipStatus(bill, now);
    if (chip === 'cancelled' || chip === 'paid') continue;
    totalCount += 1;
    const amount = billAmount(bill);
    if (bill.due_date) {
      const due = new Date(bill.due_date);
      if (!Number.isNaN(due.getTime())) {
        if (due.getTime() <= thirtyOut) {
          totalDue += amount;
        }
        if (due.getTime() >= now.getTime()) {
          if (!nextDue || due.getTime() < nextDue.date.getTime()) {
            nextDue = { date: due };
          }
        }
      } else {
        totalDue += amount;
      }
    } else {
      totalDue += amount;
    }
    if (chip === 'overdue') overdueCount += 1;
  }
  const totalDueLabel = totalCount > 0 ? formatCurrency(totalDue) : null;
  let nextBillSubtitle: string | null = null;
  if (nextDue) {
    const days = Math.floor((nextDue.date.getTime() - now.getTime()) / MS_PER_DAY);
    if (days <= 0) nextBillSubtitle = 'Next bill due today';
    else if (days === 1) nextBillSubtitle = 'All current · next bill tomorrow';
    else nextBillSubtitle = `All current · next bill in ${days} days`;
  }
  return { totalDueLabel, overdueCount, nextBillSubtitle };
}

function bannerTitle(summary: BillsBannerSummary): string {
  if (summary.totalDueLabel) {
    return `${summary.totalDueLabel} due in the next 30 days`;
  }
  return 'No upcoming bills';
}

function bannerSubtitle(summary: BillsBannerSummary): string | undefined {
  if (summary.overdueCount > 0) {
    return summary.overdueCount === 1
      ? '1 overdue · pay or schedule today'
      : `${summary.overdueCount} overdue · pay or schedule today`;
  }
  return summary.nextBillSubtitle ?? undefined;
}

function BillsContent() {
  const router = useRouter();
  const { id: homeIdParam } = useParams<{ id: string }>();
  const homeId = homeIdParam;

  const [bills, setBills] = useState<HomeBill[]>([]);
  const [loadKind, setLoadKind] = useState<'loading' | 'loaded' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [tab, setTab] = useState<BillTab>('upcoming');

  // Add bill modal state
  const [showAdd, setShowAdd] = useState(false);
  const [creating, setCreating] = useState(false);
  const [newPayee, setNewPayee] = useState('');
  const [newAmount, setNewAmount] = useState('');
  const [newDueDate, setNewDueDate] = useState('');

  // Bill detail modal state
  const [activeBill, setActiveBill] = useState<HomeBill | null>(null);
  const [savingBill, setSavingBill] = useState(false);

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const fetchBills = useCallback(async () => {
    if (!homeId) return;
    setLoadKind('loading');
    try {
      const res = await api.homeProfile.getHomeBills(homeId);
      setBills(res?.bills ?? []);
      setLoadKind('loaded');
    } catch (err) {
      setErrorMessage(
        err instanceof Error ? err.message : "Couldn't load your bills.",
      );
      setLoadKind('error');
    }
  }, [homeId]);

  useEffect(() => {
    fetchBills();
  }, [fetchBills]);

  // Re-key `now` whenever bills change so chip derivation matches the
  // latest payload without making the memo re-run on every render.
  const now = useMemo(() => new Date(), [bills]);

  const counts = useMemo(() => {
    let upcoming = 0;
    let paid = 0;
    let all = 0;
    for (const b of bills) {
      const chip = deriveChipStatus(b, now);
      if (chip === 'cancelled') continue;
      all += 1;
      if (chip === 'paid') paid += 1;
      else upcoming += 1;
    }
    return { upcoming, paid, all };
  }, [bills, now]);

  const resetAddForm = useCallback(() => {
    setNewPayee('');
    setNewAmount('');
    setNewDueDate('');
  }, []);

  const openAdd = useCallback(() => {
    resetAddForm();
    setShowAdd(true);
  }, [resetAddForm]);

  const handleCreate = useCallback(async () => {
    if (!homeId) return;
    const payee = newPayee.trim();
    const amount = Number.parseFloat(newAmount);
    if (!payee || !Number.isFinite(amount) || amount <= 0) {
      toast.error('Add a payee and a valid amount.');
      return;
    }
    setCreating(true);
    try {
      await api.homeProfile.createHomeBill(homeId, {
        bill_type: 'other',
        provider_name: payee,
        amount,
        due_date: newDueDate || undefined,
      });
      toast.success('Bill added');
      setShowAdd(false);
      resetAddForm();
      await fetchBills();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to add bill');
    } finally {
      setCreating(false);
    }
  }, [homeId, newPayee, newAmount, newDueDate, resetAddForm, fetchBills]);

  const handleMarkPaid = useCallback(async () => {
    if (!homeId || !activeBill) return;
    setSavingBill(true);
    try {
      await api.homeProfile.updateHomeBill(homeId, activeBill.id, {
        status: 'paid',
        paid_at: new Date().toISOString(),
      });
      toast.success('Bill marked paid');
      setActiveBill(null);
      await fetchBills();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to update bill');
    } finally {
      setSavingBill(false);
    }
  }, [homeId, activeBill, fetchBills]);

  const handleDelete = useCallback(async () => {
    if (!homeId || !activeBill) return;
    const yes = await confirmStore.open({
      title: 'Delete bill?',
      description: `${activeBill.provider_name || activeBill.title || 'This bill'} will be cancelled.`,
      confirmLabel: 'Delete',
      variant: 'destructive',
    });
    if (!yes) return;
    setSavingBill(true);
    try {
      // Backend has no DELETE for bills (see parity audit) — soft-delete
      // via `status = 'cancelled'`.
      await api.homeProfile.updateHomeBill(homeId, activeBill.id, {
        status: 'cancelled',
      });
      toast.success('Bill removed');
      setActiveBill(null);
      await fetchBills();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to remove bill');
    } finally {
      setSavingBill(false);
    }
  }, [homeId, activeBill, fetchBills]);

  // Summary banner — only on the Upcoming tab with `loaded` state and
  // at least one upcoming or overdue bill. Nil otherwise (hidden on
  // Paid + All + loading + empty).
  const banner: BannerConfig | undefined = useMemo(() => {
    if (loadKind !== 'loaded' || tab !== 'upcoming') return undefined;
    const summary = summarizeBills(bills, now);
    if (!summary.totalDueLabel && summary.overdueCount === 0) return undefined;
    return {
      icon: Wallet,
      title: bannerTitle(summary),
      subtitle: bannerSubtitle(summary),
      tint: 'home',
    };
  }, [loadKind, tab, bills, now]);

  const state: ListOfRowsState = useMemo(() => {
    if (loadKind === 'loading') return { kind: 'loading' };
    if (loadKind === 'error') {
      return {
        kind: 'error',
        message: errorMessage || "Couldn't load your bills.",
      };
    }
    const active = bills.filter((b) => passesTab(b, tab, now));
    if (active.length === 0) {
      return {
        kind: 'empty',
        config: {
          icon: Receipt,
          headline: 'No bills tracked yet',
          subcopy:
            'Add the utilities, insurance, and HOA dues for this home. Schedule auto-pay or split between household members.',
          ctaTitle: 'Add a bill',
          onCta: openAdd,
        },
      };
    }
    const rows: RowModel[] = active.map((bill) => {
      const projected = projectBill(bill, now);
      const visual = utilityVisual(projected.category);
      const trailing: RowTrailing = {
        kind: 'amountWithChip',
        amount: projected.amount,
        chipText: projected.chipText,
        chipVariant: projected.chipVariant,
        chipIcon: projected.chipIcon,
      };
      return {
        id: projected.rowId,
        title: projected.payee,
        subtitle: projected.subtitle,
        template: 'statusChip',
        leading: {
          kind: 'typeIcon',
          icon: visual.icon,
          background: visual.background,
          foreground: visual.foreground,
        },
        trailing,
        onTap: () => setActiveBill(bill),
        inlineChip: projected.inlineChip,
        highlight: projected.highlight,
        // Future-ready: backend list endpoint doesn't surface splits
        // yet — see file header. Always `undefined` today.
        splitWith: undefined,
      };
    });
    return {
      kind: 'loaded',
      sections: [{ id: 'bills', rows }],
      hasMore: false,
    };
  }, [bills, tab, now, loadKind, errorMessage, openAdd]);

  return (
    <>
      <div data-testid="billsList">
        <ListOfRowsShell
          title="Bills"
          state={state}
          onRefresh={fetchBills}
          tabs={[
            { id: 'upcoming', label: 'Upcoming', count: counts.upcoming },
            { id: 'paid', label: 'Paid', count: counts.paid },
            { id: 'all', label: 'All', count: counts.all },
          ]}
          selectedTab={tab}
          onTabChange={(id) => setTab(id as BillTab)}
          // T6.0a: top-bar action removed. The FAB owns the canonical
          // "Add a bill" action — a duplicate entry point in the top
          // bar would steal taps from the design's primary affordance.
          topBarAction={undefined}
          banner={banner}
          fab={{
            icon: Plus,
            accessibilityLabel: 'Add a bill',
            variant: { kind: 'canonicalCreate' },
            tint: 'home',
            onClick: openAdd,
          }}
        />
      </div>

      <ModalShell
        open={showAdd}
        onClose={() => !creating && setShowAdd(false)}
        icon={Receipt}
        title="Add a bill"
        subtitle="Track a one-time or recurring household bill."
        cancelLabel="Cancel"
        onCancel={() => !creating && setShowAdd(false)}
        cancelDisabled={creating}
        submitLabel="Add bill"
        onSubmit={handleCreate}
        submitDisabled={creating || !newPayee.trim() || !newAmount}
        submitting={creating}
        submitIcon={Plus}
      >
        <div className="space-y-3">
          <label className="block">
            <span className="block text-xs font-medium text-app-text-secondary mb-1">
              Payee
            </span>
            <input
              type="text"
              value={newPayee}
              onChange={(e) => setNewPayee(e.target.value)}
              placeholder="ConEd Electric"
              className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-primary-500"
              data-testid="addBill_payee"
            />
          </label>
          <label className="block">
            <span className="block text-xs font-medium text-app-text-secondary mb-1">
              Amount
            </span>
            <input
              type="text"
              inputMode="decimal"
              value={newAmount}
              onChange={(e) => setNewAmount(e.target.value)}
              placeholder="$ 0.00"
              className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-primary-500"
              data-testid="addBill_amount"
            />
          </label>
          <label className="block">
            <span className="block text-xs font-medium text-app-text-secondary mb-1">
              Due date
            </span>
            <input
              type="date"
              value={newDueDate}
              onChange={(e) => setNewDueDate(e.target.value)}
              className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
              data-testid="addBill_dueDate"
            />
          </label>
        </div>
      </ModalShell>

      {activeBill && (
        <BillDetailModal
          bill={activeBill}
          now={now}
          saving={savingBill}
          onClose={() => !savingBill && setActiveBill(null)}
          onMarkPaid={handleMarkPaid}
          onDelete={handleDelete}
        />
      )}
    </>
  );
}

/**
 * Detail modal — renders the bill's projected header (category-tinted
 * icon + payee + amount + auto-pay pill + chip), a Category /
 * Auto-pay / Status / Due / Paid grid, and a destructive "Remove bill"
 * action. Mirrors iOS `BillDetailView.LoadedShell`.
 */
function BillDetailModal({
  bill,
  now,
  saving,
  onClose,
  onMarkPaid,
  onDelete,
}: {
  bill: HomeBill;
  now: Date;
  saving: boolean;
  onClose: () => void;
  onMarkPaid: () => void;
  onDelete: () => void;
}) {
  const projected = projectBill(bill, now);
  const visual = utilityVisual(projected.category);
  const isPaid = bill.status === 'paid';
  const autoPay = projected.status === 'scheduled';
  const CategoryIcon = visual.icon;
  const ChipIcon = projected.chipIcon;
  return (
    <ModalShell
      open={true}
      onClose={() => !saving && onClose()}
      title="Bill"
      cancelLabel="Close"
      onCancel={() => !saving && onClose()}
      cancelDisabled={saving}
      submitLabel={isPaid ? 'Already paid' : 'Mark paid'}
      onSubmit={onMarkPaid}
      submitDisabled={saving || isPaid}
      submitting={saving}
      submitIcon={Check}
    >
      <div className="space-y-3 text-sm text-app-text">
        {/* Category-tinted header card. */}
        <div className="rounded-xl border border-app-border-subtle bg-app-surface p-4">
          <div className="flex items-start gap-3">
            <div
              className="w-12 h-12 rounded-md flex items-center justify-center shrink-0"
              style={{ background: visual.background }}
            >
              <CategoryIcon className="w-6 h-6" style={{ color: visual.foreground }} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h3 className="text-base font-semibold text-app-text leading-snug line-clamp-2">
                  {projected.payee}
                </h3>
                {autoPay && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-app-info-bg text-app-info text-[10px] font-semibold">
                    <RefreshCw className="w-2.5 h-2.5" />
                    Auto-pay
                  </span>
                )}
              </div>
              <div className="text-sm font-bold text-app-text mt-0.5">
                {projected.amount}
              </div>
            </div>
          </div>
          <div className="mt-2">
            <ChipPill
              text={projected.chipText}
              variant={projected.chipVariant}
              Icon={ChipIcon}
            />
          </div>
        </div>

        {/* Detail rows */}
        <div className="rounded-xl border border-app-border-subtle bg-app-surface overflow-hidden">
          <DetailRow label="Category" value={visual.label} />
          <DetailDivider />
          <DetailRow label="Status" value={capitalize(bill.status)} />
          {bill.status === 'scheduled' && (
            <>
              <DetailDivider />
              <DetailRow label="Auto-pay" value="Scheduled" />
            </>
          )}
          {bill.due_date && (
            <>
              <DetailDivider />
              <DetailRow label="Due" value={formatDateShort(bill.due_date)} />
            </>
          )}
          {bill.paid_at && (
            <>
              <DetailDivider />
              <DetailRow label="Paid on" value={formatDateShort(bill.paid_at)} />
            </>
          )}
          {bill.currency && bill.currency !== 'USD' && (
            <>
              <DetailDivider />
              <DetailRow label="Currency" value={bill.currency} />
            </>
          )}
        </div>

        <button
          type="button"
          onClick={onDelete}
          disabled={saving}
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-app-error hover:underline disabled:opacity-50"
          data-testid="billDetail_delete"
        >
          <Trash2 className="w-3.5 h-3.5" />
          Remove bill
        </button>
      </div>
    </ModalShell>
  );
}

function ChipPill({
  text,
  variant,
  Icon,
}: {
  text: string;
  variant: StatusChipVariant;
  Icon?: LucideIcon;
}) {
  const palette = statusChipClasses(variant);
  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-[3px] rounded-full text-[10.5px] font-semibold ${palette}`}
    >
      {Icon && <Icon className="w-2.5 h-2.5" />}
      {text}
    </span>
  );
}

function statusChipClasses(variant: StatusChipVariant): string {
  switch (variant) {
    case 'success':
      return 'bg-app-success-bg text-app-success';
    case 'warning':
      return 'bg-app-warning-bg text-app-warning';
    case 'error':
      return 'bg-app-error-bg text-app-error';
    case 'info':
      return 'bg-app-info-bg text-app-info';
    case 'personal':
      return 'bg-app-personal-bg text-app-personal';
    case 'home':
      return 'bg-app-home-bg text-app-home';
    case 'business':
      return 'bg-app-business-bg text-app-business';
    case 'neutral':
      return 'bg-app-surface-sunken text-app-text-secondary';
  }
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3 px-4 py-3">
      <span className="text-[11px] text-app-text-secondary">{label}</span>
      <span className="text-sm text-app-text">{value}</span>
    </div>
  );
}

function DetailDivider() {
  return <div className="h-px bg-app-border-subtle" />;
}

function capitalize(s: string): string {
  if (!s) return '';
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export default function BillsPage() {
  return (
    <Suspense>
      <BillsContent />
    </Suspense>
  );
}
