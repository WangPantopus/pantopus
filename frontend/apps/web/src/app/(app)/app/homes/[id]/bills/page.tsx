'use client';

// Bills — Homes pillar list screen (T5.2.2 / P13).
//
// Reskinned onto the shared `<ListOfRowsShell />` archetype. Mirrors the
// iOS `BillsListView` + Android `BillsListScreen`: 3 tabs
// (Upcoming / Paid / All), 40pt receipt-icon leading, payee + date
// subtitle, amount-with-chip trailing, FAB → Add bill.
//
// Endpoints:
//   GET    /api/homes/:id/bills           — route backend/routes/home.js:4506
//   POST   /api/homes/:id/bills           — route backend/routes/home.js:4539
//   PUT    /api/homes/:id/bills/:billId   — route backend/routes/home.js:4585
//
// Backend gaps surfaced in the parity audit: there is no DELETE handler
// for bills (the page uses `PUT { status: 'cancelled' }` as a soft
// delete) and no POST handler for `:billId/splits`, so split editing is
// read-only until a backend follow-up ships.

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  AlertCircle,
  Calendar,
  Check,
  Clock,
  Plus,
  Receipt,
  Trash2,
} from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import type { HomeBill } from '@pantopus/api/endpoints/homeProfile';
import { colors } from '@pantopus/theme';
import ListOfRowsShell from '@/components/list-of-rows/ListOfRowsShell';
import type {
  ListOfRowsState,
  RowModel,
  RowTrailing,
  StatusChipVariant,
} from '@/components/list-of-rows/types';
import ModalShell from '@/components/ui/ModalShell';
import { toast } from '@/components/ui/toast-store';
import { confirmStore } from '@/components/ui/confirm-store';

type BillTab = 'upcoming' | 'paid' | 'all';

interface BillRowDisplay {
  rowId: string;
  payee: string;
  subtitle: string;
  amount: string;
  chipText: string;
  chipVariant: StatusChipVariant;
  chipIcon: typeof Clock;
}

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

type ChipStatus = 'paid' | 'overdue' | 'scheduled' | 'due';

function deriveChipStatus(bill: HomeBill, now: Date): ChipStatus {
  if (bill.status === 'paid') return 'paid';
  if (bill.status === 'scheduled') return 'scheduled';
  if (bill.due_date) {
    const due = new Date(bill.due_date);
    if (!Number.isNaN(due.getTime()) && due < now) return 'overdue';
  }
  return 'due';
}

function projectBill(bill: HomeBill, now: Date): BillRowDisplay {
  const chipStatus = deriveChipStatus(bill, now);
  const payee = bill.provider_name || bill.title || bill.bill_type || 'Bill';
  const amountSource = bill.amount_cents != null
    ? Number(bill.amount_cents) / 100
    : bill.amount;
  const amount = formatCurrency(amountSource);
  const dueShort = formatDateShort(bill.due_date);
  const paidShort = formatDateShort(bill.paid_at);

  let subtitle = '';
  let chipText = '';
  let chipVariant: StatusChipVariant = 'warning';
  let chipIcon = Clock;

  switch (chipStatus) {
    case 'paid':
      subtitle = paidShort ? `Paid ${paidShort}` : 'Paid';
      chipText = 'Paid';
      chipVariant = 'success';
      chipIcon = Check;
      break;
    case 'overdue':
      subtitle = dueShort ? `Due ${dueShort}` : 'Overdue';
      chipText = 'Overdue';
      chipVariant = 'error';
      chipIcon = AlertCircle;
      break;
    case 'scheduled':
      subtitle = dueShort ? `Auto-pay ${dueShort}` : 'Auto-pay';
      chipText = 'Scheduled';
      chipVariant = 'personal';
      chipIcon = Calendar;
      break;
    case 'due':
    default:
      subtitle = dueShort || 'No due date';
      chipText = dueShort ? `Due ${dueShort}` : 'Due';
      chipVariant = 'warning';
      chipIcon = Clock;
      break;
  }

  return {
    rowId: bill.id,
    payee,
    subtitle,
    amount,
    chipText,
    chipVariant,
    chipIcon,
  };
}

function passesTab(bill: HomeBill, tab: BillTab, now: Date): boolean {
  const chip = deriveChipStatus(bill, now);
  switch (tab) {
    case 'upcoming':
      return chip === 'due' || chip === 'overdue' || chip === 'scheduled';
    case 'paid':
      return chip === 'paid';
    case 'all':
      return bill.status !== 'cancelled';
  }
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

  // Re-key `now` whenever bills change so chip derivation matches the latest
  // payload without making the memo re-run on every render.
  const now = useMemo(() => new Date(), [bills]);

  const counts = useMemo(() => {
    const upcoming = bills.filter((b) => passesTab(b, 'upcoming', now)).length;
    const paid = bills.filter((b) => passesTab(b, 'paid', now)).length;
    const all = bills.filter((b) => b.status !== 'cancelled').length;
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
      description: `${activeBill.provider_name || activeBill.bill_type || 'This bill'} will be cancelled.`,
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
          headline: 'No bills yet',
          subcopy:
            'Add a bill to track due dates, schedule payments, and split with household members.',
          ctaTitle: 'Add a bill',
          onCta: openAdd,
        },
      };
    }
    const rows: RowModel[] = active.map((bill) => {
      const projected = projectBill(bill, now);
      const trailing: RowTrailing = {
        kind: 'amountWithChip',
        amount: projected.amount,
        chipText: projected.chipText,
        chipVariant: projected.chipVariant,
        chipIcon: projected.chipIcon,
      };
      const row: RowModel = {
        id: projected.rowId,
        title: projected.payee,
        subtitle: projected.subtitle,
        template: 'statusChip',
        leading: {
          kind: 'typeIcon',
          icon: Receipt,
          background: colors.primary[50],
          foreground: colors.primary[600],
        },
        trailing,
        onTap: () => setActiveBill(bill),
      };
      return row;
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
          topBarAction={{
            icon: Plus,
            accessibilityLabel: 'Add a bill',
            onClick: openAdd,
          }}
          fab={{
            icon: Plus,
            accessibilityLabel: 'Add a bill',
            variant: { kind: 'secondaryCreate' },
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
        <ModalShell
          open={!!activeBill}
          onClose={() => !savingBill && setActiveBill(null)}
          icon={Receipt}
          title={activeBill.provider_name || activeBill.bill_type || 'Bill'}
          subtitle={formatCurrency(
            activeBill.amount_cents != null
              ? Number(activeBill.amount_cents) / 100
              : activeBill.amount,
          )}
          cancelLabel="Close"
          onCancel={() => !savingBill && setActiveBill(null)}
          cancelDisabled={savingBill}
          submitLabel={activeBill.status === 'paid' ? 'Already paid' : 'Mark paid'}
          onSubmit={handleMarkPaid}
          submitDisabled={savingBill || activeBill.status === 'paid'}
          submitting={savingBill}
          submitIcon={Check}
        >
          <BillDetailBody bill={activeBill} onDelete={handleDelete} saving={savingBill} />
        </ModalShell>
      )}
    </>
  );
}

function BillDetailBody({
  bill,
  onDelete,
  saving,
}: {
  bill: HomeBill;
  onDelete: () => void;
  saving: boolean;
}) {
  const dueShort = formatDateShort(bill.due_date);
  const paidShort = formatDateShort(bill.paid_at);
  return (
    <div className="space-y-3 text-sm text-app-text">
      <DetailRow label="Status" value={bill.status} />
      {bill.due_date && <DetailRow label="Due date" value={dueShort} />}
      {bill.paid_at && <DetailRow label="Paid on" value={paidShort} />}
      {bill.currency && bill.currency !== 'USD' && (
        <DetailRow label="Currency" value={bill.currency} />
      )}
      <button
        type="button"
        onClick={onDelete}
        disabled={saving}
        className="inline-flex items-center gap-1.5 text-xs font-semibold text-error hover:underline disabled:opacity-50"
        data-testid="billDetail_delete"
      >
        <Trash2 className="w-3.5 h-3.5" /> Remove bill
      </button>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3 py-1">
      <span className="text-xs uppercase tracking-wider text-app-text-secondary">{label}</span>
      <span className="font-medium text-app-text capitalize">{value}</span>
    </div>
  );
}

export default function BillsPage() {
  return (
    <Suspense>
      <BillsContent />
    </Suspense>
  );
}
