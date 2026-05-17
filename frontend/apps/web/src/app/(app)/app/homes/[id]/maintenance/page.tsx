'use client';

// Maintenance — Homes pillar list screen (T6.3b / P10).
//
// Built on the shared `<ListOfRowsShell />` archetype. Mirrors the iOS
// `MaintenanceListView` + Android `MaintenanceListScreen`. Replaces the
// pre-T6 stub that piggy-backed on `HomeIssue` — the new screen owns its
// own backend table (`HomeMaintenanceLog`, extended in migration 151).
//
// Endpoints:
//   GET    /api/homes/:id/maintenance              — backend/routes/home.js
//   POST   /api/homes/:id/maintenance              — backend/routes/home.js
//   PUT    /api/homes/:id/maintenance/:taskId      — backend/routes/home.js
//   DELETE /api/homes/:id/maintenance/:taskId      — backend/routes/home.js

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  AlertCircle,
  Calendar,
  Check,
  Clock,
  Hammer,
  Plus,
  Trash2,
  Wrench,
  X,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import type { HomeMaintenanceTask } from '@pantopus/api/endpoints/homeProfile';
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
  categoryFromTask,
  maintenanceVisual,
  recurrenceLabel,
  type MaintenanceCategory,
} from './maintenance-palette';

type MaintTab = 'scheduled' | 'completed' | 'all';

/** Canonical 6-state chip status, derived from
 *  `HomeMaintenanceTask.status` + `due_date`. Discriminated union
 *  mirrors iOS `MaintenanceChipStatus`. */
type ChipStatus =
  | 'scheduled'
  | 'dueSoon'
  | 'overdue'
  | 'inProgress'
  | 'completed'
  | 'cancelled';

interface MaintRowProjection {
  rowId: string;
  title: string;
  subtitle: string;
  amount: string;
  chipText: string;
  chipVariant: StatusChipVariant;
  chipIcon: LucideIcon;
  status: ChipStatus;
  category: MaintenanceCategory;
  inlineChip?: RowChip;
  highlight?: RowHighlight;
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;
const SEVEN_DAYS = 7 * MS_PER_DAY;

function formatCurrency(raw: number | string | null | undefined): string {
  if (raw == null) return '$0';
  const num = typeof raw === 'string' ? Number.parseFloat(raw) : raw;
  if (!Number.isFinite(num)) return '$0';
  return num.toLocaleString('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
    minimumFractionDigits: 0,
  });
}

function formatCost(raw: number | string | null | undefined): string {
  if (raw == null) return '—';
  const num = typeof raw === 'string' ? Number.parseFloat(raw) : raw;
  if (!Number.isFinite(num)) return '—';
  if (num === 0) return 'DIY';
  return formatCurrency(num);
}

function taskCostNumber(task: HomeMaintenanceTask): number | null {
  if (task.cost == null) return null;
  const num = typeof task.cost === 'string' ? Number.parseFloat(task.cost) : task.cost;
  return Number.isFinite(num) ? Number(num) : null;
}

function formatDateShort(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

/**
 * Derive the chip status per the T6.3b contract:
 *   - `cancelled`  when status is "cancelled"
 *   - `completed`  when status is "completed"
 *   - `inProgress` when status is "in_progress"
 *   - `overdue`    when status="scheduled" + due_date in past
 *   - `dueSoon`    when status="scheduled" + due_date ≤ 7 days out
 *   - `scheduled`  otherwise
 */
function deriveChipStatus(task: HomeMaintenanceTask, now: Date): ChipStatus {
  if (task.status === 'cancelled') return 'cancelled';
  if (task.status === 'completed') return 'completed';
  if (task.status === 'in_progress') return 'inProgress';
  if (task.due_date) {
    const due = new Date(task.due_date);
    if (!Number.isNaN(due.getTime())) {
      if (due.getTime() < now.getTime()) return 'overdue';
      if (due.getTime() <= now.getTime() + SEVEN_DAYS) return 'dueSoon';
    }
  }
  return 'scheduled';
}

function vendorSubtitle(
  vendor: string | null,
  recurrence: string | null,
): string {
  const vendorPart = vendor && vendor.length > 0 ? vendor : 'Self-managed';
  return recurrence ? `${vendorPart} · ${recurrence}` : vendorPart;
}

/**
 * Pure projection of one task into a row's display fields. Exposed so
 * the detail modal header can reuse the chip / category derivation.
 */
function projectTask(task: HomeMaintenanceTask, now: Date): MaintRowProjection {
  const chip = deriveChipStatus(task, now);
  const category = categoryFromTask(task.task);
  const visual = maintenanceVisual(category);
  const title = task.task && task.task.length > 0 ? task.task : visual.label;
  const amount = formatCost(task.cost);
  const recurrenceText = recurrenceLabel(
    task.recurrence as Parameters<typeof recurrenceLabel>[0],
  );
  const subtitle = vendorSubtitle(task.vendor, recurrenceText);
  const dueShort = formatDateShort(task.due_date);

  const base = { rowId: task.id, title, subtitle, amount, category };

  switch (chip) {
    case 'completed':
      return {
        ...base,
        chipText: 'Completed',
        chipVariant: 'success',
        chipIcon: Check,
        status: chip,
        inlineChip: undefined,
        highlight: undefined,
      };
    case 'cancelled':
      return {
        ...base,
        chipText: 'Cancelled',
        chipVariant: 'neutral',
        chipIcon: X,
        status: chip,
        inlineChip: undefined,
        highlight: 'muted',
      };
    case 'overdue':
      return {
        ...base,
        chipText: 'Overdue',
        chipVariant: 'error',
        chipIcon: AlertCircle,
        status: chip,
        inlineChip: dueShort
          ? {
              text: `Was due ${dueShort}`,
              icon: Clock,
              tint: { kind: 'status', variant: 'error' },
            }
          : undefined,
        highlight: undefined,
      };
    case 'dueSoon':
      return {
        ...base,
        chipText: 'Due soon',
        chipVariant: 'warning',
        chipIcon: Clock,
        status: chip,
        inlineChip: dueShort
          ? {
              text: dueShort,
              icon: Calendar,
              tint: { kind: 'status', variant: 'warning' },
            }
          : undefined,
        highlight: undefined,
      };
    case 'inProgress':
      return {
        ...base,
        chipText: 'In progress',
        chipVariant: 'info',
        chipIcon: Hammer,
        status: chip,
        inlineChip: undefined,
        highlight: undefined,
      };
    case 'scheduled':
    default:
      return {
        ...base,
        chipText: 'Scheduled',
        chipVariant: 'info',
        chipIcon: Calendar,
        status: chip,
        inlineChip: dueShort
          ? {
              text: dueShort,
              icon: Calendar,
              tint: { kind: 'status', variant: 'info' },
            }
          : undefined,
        highlight: undefined,
      };
  }
}

function passesTab(task: HomeMaintenanceTask, tab: MaintTab, now: Date): boolean {
  const chip = deriveChipStatus(task, now);
  switch (tab) {
    case 'scheduled':
      return chip !== 'completed' && chip !== 'cancelled';
    case 'completed':
      return chip === 'completed';
    case 'all':
      return chip !== 'cancelled';
  }
}

interface MaintBannerSummary {
  overdueCount: number;
  ytdSpendLabel: string | null;
  scheduledSubtitle: string | null;
}

/**
 * Pure banner summary. Mirrors iOS `MaintenanceListViewModel.summarize`:
 * skips cancelled rows; counts overdue (status=scheduled + past due);
 * sums YTD spend across `completed` rows whose `updated_at` (fallback
 * `created_at`) falls in the current year; surfaces a "X scheduled ·
 * next-up Y" subtitle.
 */
function summarize(tasks: HomeMaintenanceTask[], now: Date): MaintBannerSummary {
  const yearStart = new Date(now.getFullYear(), 0, 1).getTime();
  let overdueCount = 0;
  let scheduledCount = 0;
  let ytdSpend = 0;
  let nextDue: { date: Date; task: HomeMaintenanceTask } | null = null;
  for (const task of tasks) {
    const chip = deriveChipStatus(task, now);
    switch (chip) {
      case 'cancelled':
        continue;
      case 'completed': {
        const performedAtIso = task.updated_at ?? task.created_at;
        if (!performedAtIso) continue;
        const performedAt = new Date(performedAtIso);
        if (Number.isNaN(performedAt.getTime())) continue;
        if (performedAt.getTime() >= yearStart) {
          const cost = taskCostNumber(task);
          if (cost != null) ytdSpend += cost;
        }
        continue;
      }
      case 'overdue': {
        overdueCount += 1;
        scheduledCount += 1;
        if (task.due_date) {
          const due = new Date(task.due_date);
          if (!Number.isNaN(due.getTime())) {
            if (!nextDue || due.getTime() < nextDue.date.getTime()) {
              nextDue = { date: due, task };
            }
          }
        }
        break;
      }
      case 'scheduled':
      case 'dueSoon':
      case 'inProgress': {
        scheduledCount += 1;
        if (task.due_date) {
          const due = new Date(task.due_date);
          if (!Number.isNaN(due.getTime()) && due.getTime() >= now.getTime()) {
            if (!nextDue || due.getTime() < nextDue.date.getTime()) {
              nextDue = { date: due, task };
            }
          }
        }
        break;
      }
    }
  }
  const ytdSpendLabel = ytdSpend > 0 ? formatCurrency(ytdSpend) : null;
  let scheduledSubtitle: string | null = null;
  if (scheduledCount > 0) {
    const prefix = scheduledCount === 1 ? '1 scheduled' : `${scheduledCount} scheduled`;
    if (nextDue) {
      const title = nextDue.task.task && nextDue.task.task.length > 0
        ? nextDue.task.task
        : 'next task';
      const days = Math.floor(
        (nextDue.date.getTime() - now.getTime()) / MS_PER_DAY,
      );
      let when: string;
      if (days < 0) when = 'overdue';
      else if (days === 0) when = 'today';
      else if (days === 1) when = 'tomorrow';
      else when = `in ${days} days`;
      scheduledSubtitle = `${prefix} · ${title} ${when}`;
    } else {
      scheduledSubtitle = prefix;
    }
  }
  return { overdueCount, ytdSpendLabel, scheduledSubtitle };
}

function bannerTitle(summary: MaintBannerSummary): string {
  return summary.scheduledSubtitle ?? 'Maintenance';
}

function bannerSubtitle(summary: MaintBannerSummary): string | undefined {
  if (summary.overdueCount > 0) {
    const label = summary.overdueCount === 1
      ? '1 overdue'
      : `${summary.overdueCount} overdue`;
    return summary.ytdSpendLabel
      ? `${label} · ${summary.ytdSpendLabel} spent YTD`
      : label;
  }
  return summary.ytdSpendLabel
    ? `${summary.ytdSpendLabel} spent YTD · all current`
    : 'All current';
}

function MaintenanceContent() {
  const router = useRouter();
  const { id: homeIdParam } = useParams<{ id: string }>();
  const homeId = homeIdParam;

  const [tasks, setTasks] = useState<HomeMaintenanceTask[]>([]);
  const [loadKind, setLoadKind] =
    useState<'loading' | 'loaded' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [tab, setTab] = useState<MaintTab>('scheduled');

  // Add task modal state
  const [showAdd, setShowAdd] = useState(false);
  const [creating, setCreating] = useState(false);
  const [newTask, setNewTask] = useState('');
  const [newVendor, setNewVendor] = useState('');
  const [newCost, setNewCost] = useState('');
  const [newDueDate, setNewDueDate] = useState('');

  // Detail modal state
  const [activeTask, setActiveTask] = useState<HomeMaintenanceTask | null>(null);
  const [savingTask, setSavingTask] = useState(false);

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const fetchTasks = useCallback(async () => {
    if (!homeId) return;
    setLoadKind('loading');
    try {
      const res = await api.homeProfile.getHomeMaintenance(homeId);
      setTasks(res?.tasks ?? []);
      setLoadKind('loaded');
    } catch (err) {
      setErrorMessage(
        err instanceof Error ? err.message : "Couldn't load your maintenance log.",
      );
      setLoadKind('error');
    }
  }, [homeId]);

  useEffect(() => {
    fetchTasks();
  }, [fetchTasks]);

  // Re-key `now` whenever tasks change so chip derivation matches the
  // latest payload without making the memo re-run on every render.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const now = useMemo(() => new Date(), [tasks]);

  const counts = useMemo(() => {
    let scheduled = 0;
    let completed = 0;
    let all = 0;
    for (const t of tasks) {
      const chip = deriveChipStatus(t, now);
      if (chip === 'cancelled') continue;
      all += 1;
      if (chip === 'completed') completed += 1;
      else scheduled += 1;
    }
    return { scheduled, completed, all };
  }, [tasks, now]);

  const resetAddForm = useCallback(() => {
    setNewTask('');
    setNewVendor('');
    setNewCost('');
    setNewDueDate('');
  }, []);

  const openAdd = useCallback(() => {
    resetAddForm();
    setShowAdd(true);
  }, [resetAddForm]);

  const handleCreate = useCallback(async () => {
    if (!homeId) return;
    const task = newTask.trim();
    if (!task) {
      toast.error('Add a task name.');
      return;
    }
    const cost = newCost ? Number.parseFloat(newCost) : null;
    if (newCost && (!Number.isFinite(cost) || (cost ?? 0) < 0)) {
      toast.error('Enter a valid cost.');
      return;
    }
    setCreating(true);
    try {
      await api.homeProfile.createHomeMaintenance(homeId, {
        task,
        vendor: newVendor.trim() || null,
        cost,
        due_date: newDueDate || null,
        status: 'scheduled',
      });
      toast.success('Maintenance task added');
      setShowAdd(false);
      resetAddForm();
      await fetchTasks();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to add task');
    } finally {
      setCreating(false);
    }
  }, [homeId, newTask, newVendor, newCost, newDueDate, resetAddForm, fetchTasks]);

  const handleMarkComplete = useCallback(async () => {
    if (!homeId || !activeTask) return;
    setSavingTask(true);
    try {
      await api.homeProfile.updateHomeMaintenance(homeId, activeTask.id, {
        status: 'completed',
      });
      toast.success('Marked complete');
      setActiveTask(null);
      await fetchTasks();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to update task');
    } finally {
      setSavingTask(false);
    }
  }, [homeId, activeTask, fetchTasks]);

  const handleDelete = useCallback(async () => {
    if (!homeId || !activeTask) return;
    const yes = await confirmStore.open({
      title: 'Delete maintenance task?',
      description: `${activeTask.task || 'This task'} will be removed.`,
      confirmLabel: 'Delete',
      variant: 'destructive',
    });
    if (!yes) return;
    setSavingTask(true);
    try {
      await api.homeProfile.deleteHomeMaintenance(homeId, activeTask.id);
      toast.success('Task removed');
      setActiveTask(null);
      await fetchTasks();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to remove task');
    } finally {
      setSavingTask(false);
    }
  }, [homeId, activeTask, fetchTasks]);

  const banner: BannerConfig | undefined = useMemo(() => {
    if (loadKind !== 'loaded' || tab !== 'scheduled') return undefined;
    const summary = summarize(tasks, now);
    if (
      summary.overdueCount === 0 &&
      !summary.ytdSpendLabel &&
      !summary.scheduledSubtitle
    ) {
      return undefined;
    }
    return {
      icon: Hammer,
      title: bannerTitle(summary),
      subtitle: bannerSubtitle(summary),
      tint: 'home',
    };
  }, [loadKind, tab, tasks, now]);

  const state: ListOfRowsState = useMemo(() => {
    if (loadKind === 'loading') return { kind: 'loading' };
    if (loadKind === 'error') {
      return {
        kind: 'error',
        message: errorMessage || "Couldn't load your maintenance log.",
      };
    }
    const active = tasks.filter((t) => passesTab(t, tab, now));
    if (active.length === 0) {
      return {
        kind: 'empty',
        config: {
          icon: Hammer,
          headline: 'No maintenance logged yet',
          subcopy:
            'Track HVAC tune-ups, gutter cleans, filter swaps and inspections. ' +
            'Build a service history that protects warranties and resale value.',
          ctaTitle: 'Log maintenance',
          onCta: openAdd,
        },
      };
    }
    const rows: RowModel[] = active.map((task) => {
      const projected = projectTask(task, now);
      const visual = maintenanceVisual(projected.category);
      const trailing: RowTrailing = {
        kind: 'amountWithChip',
        amount: projected.amount,
        chipText: projected.chipText,
        chipVariant: projected.chipVariant,
        chipIcon: projected.chipIcon,
      };
      return {
        id: projected.rowId,
        title: projected.title,
        subtitle: projected.subtitle,
        template: 'statusChip',
        leading: {
          kind: 'typeIcon',
          icon: visual.icon,
          background: visual.background,
          foreground: visual.foreground,
        },
        trailing,
        onTap: () => setActiveTask(task),
        inlineChip: projected.inlineChip,
        highlight: projected.highlight,
      };
    });
    return {
      kind: 'loaded',
      sections: [{ id: 'maintenance', rows }],
      hasMore: false,
    };
  }, [tasks, tab, now, loadKind, errorMessage, openAdd]);

  return (
    <>
      <div data-testid="maintenanceList">
        <ListOfRowsShell
          title="Maintenance"
          state={state}
          onRefresh={fetchTasks}
          tabs={[
            { id: 'scheduled', label: 'Scheduled', count: counts.scheduled },
            { id: 'completed', label: 'Completed', count: counts.completed },
            { id: 'all', label: 'All', count: counts.all },
          ]}
          selectedTab={tab}
          onTabChange={(id) => setTab(id as MaintTab)}
          // T6.3b: top-bar action is undefined by design (mirrors Bills
          // T6.0a). The FAB owns the canonical "Log maintenance" action.
          topBarAction={undefined}
          banner={banner}
          fab={{
            icon: Plus,
            accessibilityLabel: 'Log maintenance',
            variant: { kind: 'canonicalCreate' },
            tint: 'home',
            onClick: openAdd,
          }}
        />
      </div>

      <ModalShell
        open={showAdd}
        onClose={() => !creating && setShowAdd(false)}
        icon={Wrench}
        title="Log maintenance"
        subtitle="Track a scheduled or completed maintenance task."
        cancelLabel="Cancel"
        onCancel={() => !creating && setShowAdd(false)}
        cancelDisabled={creating}
        submitLabel="Add task"
        onSubmit={handleCreate}
        submitDisabled={creating || !newTask.trim()}
        submitting={creating}
        submitIcon={Plus}
      >
        <div className="space-y-3">
          <label className="block">
            <span className="block text-xs font-medium text-app-text-secondary mb-1">
              Task
            </span>
            <input
              type="text"
              value={newTask}
              onChange={(e) => setNewTask(e.target.value)}
              placeholder="Fall HVAC tune-up"
              className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-primary-500"
              data-testid="addMaintenance_task"
            />
          </label>
          <label className="block">
            <span className="block text-xs font-medium text-app-text-secondary mb-1">
              Vendor (optional)
            </span>
            <input
              type="text"
              value={newVendor}
              onChange={(e) => setNewVendor(e.target.value)}
              placeholder="Riverside HVAC"
              className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-primary-500"
              data-testid="addMaintenance_vendor"
            />
          </label>
          <label className="block">
            <span className="block text-xs font-medium text-app-text-secondary mb-1">
              Cost (optional)
            </span>
            <input
              type="text"
              inputMode="decimal"
              value={newCost}
              onChange={(e) => setNewCost(e.target.value)}
              placeholder="$ 0"
              className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-primary-500"
              data-testid="addMaintenance_cost"
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
              data-testid="addMaintenance_dueDate"
            />
          </label>
        </div>
      </ModalShell>

      {activeTask && (
        <MaintenanceDetailModal
          task={activeTask}
          now={now}
          saving={savingTask}
          onClose={() => !savingTask && setActiveTask(null)}
          onMarkComplete={handleMarkComplete}
          onDelete={handleDelete}
        />
      )}
    </>
  );
}

/**
 * Detail modal — header (category-tinted icon + title + cost + chip),
 * a Category / Status / Vendor / Recurrence / Due / Updated grid,
 * "Mark complete" submit, and a destructive "Remove task" action.
 */
function MaintenanceDetailModal({
  task,
  now,
  saving,
  onClose,
  onMarkComplete,
  onDelete,
}: {
  task: HomeMaintenanceTask;
  now: Date;
  saving: boolean;
  onClose: () => void;
  onMarkComplete: () => void;
  onDelete: () => void;
}) {
  const projected = projectTask(task, now);
  const visual = maintenanceVisual(projected.category);
  const isCompleted = task.status === 'completed';
  const CategoryIcon = visual.icon;
  const ChipIcon = projected.chipIcon;
  return (
    <ModalShell
      open={true}
      onClose={() => !saving && onClose()}
      title="Maintenance"
      cancelLabel="Close"
      onCancel={() => !saving && onClose()}
      cancelDisabled={saving}
      submitLabel={isCompleted ? 'Already complete' : 'Mark complete'}
      onSubmit={onMarkComplete}
      submitDisabled={saving || isCompleted}
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
              <CategoryIcon
                className="w-6 h-6"
                style={{ color: visual.foreground }}
              />
            </div>
            <div className="flex-1 min-w-0">
              <h3 className="text-base font-semibold text-app-text leading-snug line-clamp-2">
                {projected.title}
              </h3>
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
          <DetailRow label="Status" value={capitalize(task.status.replace('_', ' '))} />
          <DetailDivider />
          <DetailRow
            label="Vendor"
            value={task.vendor && task.vendor.length > 0 ? task.vendor : 'Self-managed'}
          />
          {task.recurrence && task.recurrence !== 'one_time' && (
            <>
              <DetailDivider />
              <DetailRow
                label="Recurrence"
                value={capitalize(task.recurrence)}
              />
            </>
          )}
          {task.due_date && (
            <>
              <DetailDivider />
              <DetailRow label="Due" value={formatDateShort(task.due_date)} />
            </>
          )}
          {task.updated_at && (
            <>
              <DetailDivider />
              <DetailRow
                label="Updated"
                value={formatDateShort(task.updated_at)}
              />
            </>
          )}
        </div>

        <button
          type="button"
          onClick={onDelete}
          disabled={saving}
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-app-error hover:underline disabled:opacity-50"
          data-testid="maintenanceDetail_delete"
        >
          <Trash2 className="w-3.5 h-3.5" />
          Remove task
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

export default function MaintenancePage() {
  return (
    <Suspense>
      <MaintenanceContent />
    </Suspense>
  );
}
