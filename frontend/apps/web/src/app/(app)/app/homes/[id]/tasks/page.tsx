'use client';

// Household tasks — Homes pillar per-home chore list (T6.3c / P11).
//
// Web mirror of iOS `HouseholdTasksListView` + Android
// `HouseholdTasksListScreen`. Built on the shared `<ListOfRowsShell />`
// archetype with home-pillar identity tones (green tab underline,
// home-tinted summary banner, home-tinted secondaryCreate FAB).
//
// DISTINCT from "My tasks" — `me.gigs` (the posted-to-neighbours gig
// list). This is the per-home chore list: who's vacuuming, taking out
// the trash, walking the dog.
//
// Backend deviation: the task brief specified `template_id != null` for
// the Recurring filter, but the live `HomeTask` schema
// (`backend/database/schema.sql:6833`) has no `template_id` column —
// recurrence is captured in the `recurrence_rule` RRULE text field. The
// Recurring filter therefore uses `recurrence_rule != null`, which is
// the canonical signal today. Mirrored on iOS / Android.
//
// Endpoints:
//   GET    /api/homes/:id/tasks              — backend/routes/home.js:4170
//   POST   /api/homes/:id/tasks              — backend/routes/home.js:4238
//   PUT    /api/homes/:id/tasks/:taskId      — backend/routes/home.js:4308
//   DELETE /api/homes/:id/tasks/:taskId      — backend/routes/home.js:4354

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  AlertCircle,
  Check,
  CheckCircle2,
  Circle,
  Clock,
  ListChecks,
  Plus,
  Repeat,
  X,
  type LucideIcon,
} from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
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
import {
  categoryFromTitle,
  taskCategoryVisual,
  type HouseholdTaskCategory,
} from './task-category-palette';

type TaskTab = 'active' | 'done' | 'recurring';

interface HomeTask {
  id: string;
  home_id: string;
  task_type: string;
  title: string;
  description?: string | null;
  assigned_to?: string | null;
  due_at?: string | null;
  recurrence_rule?: string | null;
  status: string;
  priority?: string | null;
  completed_at?: string | null;
  created_by?: string | null;
  created_at?: string | null;
  updated_at?: string | null;
}

interface HouseholdTaskRowProjection {
  rowId: string;
  title: string;
  subtitle: string;
  chipText?: string;
  chipVariant?: StatusChipVariant;
  chipIcon?: LucideIcon;
  recurrenceChip?: string;
  category: HouseholdTaskCategory;
  isAssigned: boolean;
  assigneeLabel?: string;
  highlight?: RowHighlight;
}

interface BannerSummary {
  dueTodayCount: number;
  overdueCount: number;
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;
const THIRTY_DAYS = 30 * MS_PER_DAY;

// ─── Pure helpers (mirror iOS/Android) ─────────────────────────────

function passes(task: HomeTask, tab: TaskTab, now: Date): boolean {
  switch (tab) {
    case 'active':
      return task.status === 'open' || task.status === 'in_progress';
    case 'done': {
      if (task.status !== 'done') return false;
      const iso = task.completed_at ?? task.updated_at;
      if (!iso) return true;
      const date = new Date(iso);
      if (Number.isNaN(date.getTime())) return true;
      return now.getTime() - date.getTime() <= THIRTY_DAYS;
    }
    case 'recurring':
      return (task.recurrence_rule ?? '').trim().length > 0;
  }
}

function startOfDay(date: Date): Date {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d;
}

function daysBetween(a: Date, b: Date): number {
  return Math.round((startOfDay(b).getTime() - startOfDay(a).getTime()) / MS_PER_DAY);
}

function formatWeekday(date: Date): string {
  return date.toLocaleDateString('en-US', { weekday: 'short' });
}

function formatDateShort(date: Date): string {
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function humanRelativeTime(iso: string | null | undefined, now: Date): string | undefined {
  if (!iso) return undefined;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return undefined;
  const delta = (now.getTime() - date.getTime()) / 1000;
  if (delta < 60) return 'just now';
  if (delta < 3600) return `${Math.floor(delta / 60)}m ago`;
  if (delta < 24 * 3600) return `${Math.floor(delta / 3600)}h ago`;
  if (delta < 48 * 3600) return 'yesterday';
  return formatDateShort(date);
}

function humanRecurrence(rule: string | null | undefined): string | undefined {
  const raw = (rule ?? '').trim();
  if (!raw) return undefined;
  const lower = raw.toLowerCase();
  if (lower.includes('freq=daily')) return 'Daily';
  if (lower.includes('freq=weekly')) {
    const byday = parseByDay(lower);
    return byday ? `Weekly · ${byday}` : 'Weekly';
  }
  if (lower.includes('freq=monthly')) return 'Monthly';
  if (lower.includes('freq=yearly')) return 'Yearly';
  return raw;
}

function parseByDay(rrule: string): string | undefined {
  const i = rrule.indexOf('byday=');
  if (i < 0) return undefined;
  const tail = rrule.substring(i + 'byday='.length);
  const token = tail.split(';', 1)[0];
  const map: Record<string, string> = {
    mo: 'Mon', tu: 'Tue', we: 'Wed', th: 'Thu', fr: 'Fri', sa: 'Sat', su: 'Sun',
  };
  const pieces = token
    .split(',')
    .map((p) => map[p.trim().toLowerCase()])
    .filter(Boolean);
  return pieces.length ? pieces.join(', ') : undefined;
}

/** Best-effort fingerprint for an unjoined assignee uuid. Backend
 *  returns just an id today; surface a short identifier so the row
 *  stays distinguishable but the UI doesn't lie about who's assigned. */
function assigneeDisplay(id: string | null | undefined): string | undefined {
  if (!id) return undefined;
  return `Member ${id.substring(0, 4).toUpperCase()}`;
}

interface DueChipPayload {
  text?: string;
  variant?: StatusChipVariant;
  icon?: LucideIcon;
  subtitleLine?: string;
}

function dueChip(iso: string | null | undefined, now: Date): DueChipPayload {
  if (!iso) return {};
  const due = new Date(iso);
  if (Number.isNaN(due.getTime())) return {};
  const days = daysBetween(now, due);
  if (days < 0) {
    const lateBy = -days;
    const label = lateBy === 1 ? '1 day late' : `${lateBy} days late`;
    return { text: label, variant: 'error', icon: AlertCircle, subtitleLine: label };
  }
  if (days === 0) return { text: 'Today', variant: 'warning', icon: Clock, subtitleLine: 'Due today' };
  if (days === 1) return { text: 'Tomorrow', variant: 'warning', icon: Clock, subtitleLine: 'Due tomorrow' };
  if (days <= 7) {
    const label = formatWeekday(due);
    return { text: label, variant: 'neutral', subtitleLine: `Due ${label}` };
  }
  const label = formatDateShort(due);
  return { text: label, variant: 'neutral', subtitleLine: `Due ${label}` };
}

function project(task: HomeTask, now: Date): HouseholdTaskRowProjection {
  const category = categoryFromTitle(task.title, task.task_type);
  const assigneeLabel = assigneeDisplay(task.assigned_to);
  const isAssigned = Boolean(assigneeLabel);
  const recurrenceChip = humanRecurrence(task.recurrence_rule);
  if (task.status === 'done') {
    const doneTime = humanRelativeTime(task.completed_at ?? task.updated_at, now);
    const by = assigneeLabel ?? 'Someone';
    return {
      rowId: task.id,
      title: task.title,
      subtitle: doneTime ? `Done by ${by} · ${doneTime}` : `Done by ${by}`,
      recurrenceChip,
      category,
      isAssigned,
      assigneeLabel,
      highlight: 'muted',
    };
  }
  if (task.status === 'canceled') {
    return {
      rowId: task.id,
      title: task.title,
      subtitle: 'Canceled',
      chipText: 'Canceled',
      chipVariant: 'neutral',
      chipIcon: X,
      recurrenceChip,
      category,
      isAssigned,
      assigneeLabel,
      highlight: 'muted',
    };
  }
  const due = dueChip(task.due_at, now);
  const assigneeLine = assigneeLabel ? `Assigned to ${assigneeLabel}` : 'Unassigned';
  const subtitle = due.subtitleLine ? `${assigneeLine} · ${due.subtitleLine}` : assigneeLine;
  return {
    rowId: task.id,
    title: task.title,
    subtitle,
    chipText: due.text,
    chipVariant: due.variant,
    chipIcon: due.icon,
    recurrenceChip,
    category,
    isAssigned,
    assigneeLabel,
  };
}

function summarize(tasks: HomeTask[], now: Date): BannerSummary {
  let dueToday = 0;
  let overdue = 0;
  for (const task of tasks) {
    if (task.status !== 'open' && task.status !== 'in_progress') continue;
    if (!task.due_at) continue;
    const due = new Date(task.due_at);
    if (Number.isNaN(due.getTime())) continue;
    const days = daysBetween(now, due);
    if (days < 0) overdue += 1;
    else if (days === 0) dueToday += 1;
  }
  return { dueTodayCount: dueToday, overdueCount: overdue };
}

function summaryHasContent(summary: BannerSummary): boolean {
  return summary.dueTodayCount > 0 || summary.overdueCount > 0;
}

function bannerTitle(summary: BannerSummary): string {
  if (summary.dueTodayCount === 0 && summary.overdueCount > 0) {
    return summary.overdueCount === 1
      ? '1 task overdue'
      : `${summary.overdueCount} tasks overdue`;
  }
  return summary.dueTodayCount === 1
    ? '1 task due today'
    : `${summary.dueTodayCount} tasks due today`;
}

function bannerSubtitle(summary: BannerSummary): string | undefined {
  if (summary.overdueCount > 0) {
    return summary.overdueCount === 1
      ? '1 overdue · finish or reassign'
      : `${summary.overdueCount} overdue · finish or reassign`;
  }
  return "You're on track for the week";
}

// ─── Page ──────────────────────────────────────────────────────────

function TasksContent() {
  const router = useRouter();
  const { id: homeId } = useParams<{ id: string }>();

  const [tasks, setTasks] = useState<HomeTask[]>([]);
  const [loadKind, setLoadKind] = useState<'loading' | 'loaded' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [tab, setTab] = useState<TaskTab>('active');

  // Add task modal state
  const [showAdd, setShowAdd] = useState(false);
  const [creating, setCreating] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [newPriority, setNewPriority] = useState<'low' | 'medium' | 'high'>('medium');

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const fetchTasks = useCallback(async () => {
    if (!homeId) return;
    setLoadKind('loading');
    try {
      const res = await api.homeProfile.getHomeTasks(homeId);
      setTasks(((res as { tasks?: HomeTask[] })?.tasks ?? []) as HomeTask[]);
      setLoadKind('loaded');
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : "Couldn't load your tasks.");
      setLoadKind('error');
    }
  }, [homeId]);

  useEffect(() => {
    fetchTasks();
  }, [fetchTasks]);

  // Re-key `now` whenever tasks change so chip derivation matches the
  // latest payload without making the memo re-run on every render.
  const now = useMemo(() => new Date(), [tasks]);

  const counts = useMemo(() => {
    let active = 0;
    let done = 0;
    let recurring = 0;
    for (const task of tasks) {
      if (passes(task, 'active', now)) active += 1;
      if (passes(task, 'done', now)) done += 1;
      if (passes(task, 'recurring', now)) recurring += 1;
    }
    return { active, done, recurring };
  }, [tasks, now]);

  const resetAddForm = useCallback(() => {
    setNewTitle('');
    setNewDescription('');
    setNewPriority('medium');
  }, []);

  const openAdd = useCallback(() => {
    resetAddForm();
    setShowAdd(true);
  }, [resetAddForm]);

  const handleCreate = useCallback(async () => {
    if (!homeId) return;
    const title = newTitle.trim();
    if (!title) {
      toast.error('Add a task title.');
      return;
    }
    setCreating(true);
    try {
      await api.homeProfile.createHomeTask(homeId, {
        task_type: 'chore',
        title,
        description: newDescription.trim() || undefined,
        priority: newPriority,
      });
      setShowAdd(false);
      resetAddForm();
      toast.success('Task added');
      await fetchTasks();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to add task');
    } finally {
      setCreating(false);
    }
  }, [homeId, newTitle, newDescription, newPriority, fetchTasks, resetAddForm]);

  /** Optimistic toggle — flip the row locally, fire the PUT, roll back
   *  on failure. */
  const toggleDone = useCallback(
    async (taskId: string) => {
      if (!homeId) return;
      const original = tasks.find((t) => t.id === taskId);
      if (!original) return;
      const newStatus = original.status === 'done' ? 'open' : 'done';
      const optimisticCompletedAt = newStatus === 'done' ? new Date().toISOString() : null;
      setTasks((prev) =>
        prev.map((t) =>
          t.id === taskId
            ? { ...t, status: newStatus, completed_at: optimisticCompletedAt }
            : t,
        ),
      );
      try {
        // Backend auto-sets `completed_at` when `status` flips to
        // 'done' (home.js:4326), so we only forward the status change.
        await api.homeProfile.updateHomeTask(homeId, taskId, {
          status: newStatus,
        });
      } catch (err) {
        // Roll back.
        setTasks((prev) => prev.map((t) => (t.id === taskId ? original : t)));
        toast.error(err instanceof Error ? err.message : 'Failed to update task');
      }
    },
    [homeId, tasks],
  );

  const filteredTasks = useMemo(
    () => tasks.filter((t) => passes(t, tab, now)),
    [tasks, tab, now],
  );

  const banner: BannerConfig | undefined = useMemo(() => {
    if (loadKind !== 'loaded') return undefined;
    if (tab !== 'active') return undefined;
    const summary = summarize(tasks, now);
    if (!summaryHasContent(summary)) return undefined;
    return {
      icon: ListChecks,
      title: bannerTitle(summary),
      subtitle: bannerSubtitle(summary),
      tint: 'home',
    };
  }, [loadKind, tab, tasks, now]);

  const state: ListOfRowsState = useMemo(() => {
    if (loadKind === 'loading') return { kind: 'loading' };
    if (loadKind === 'error') {
      return { kind: 'error', message: errorMessage || "Couldn't load your tasks." };
    }
    if (filteredTasks.length === 0) {
      const empty = emptyConfigForTab(tab, openAdd);
      return { kind: 'empty', config: empty };
    }
    const rows: RowModel[] = filteredTasks.map((task) => {
      const projection = project(task, now);
      const visual = taskCategoryVisual(projection.category);
      const isDone = task.status === 'done';
      const trailing = trailingFor(task, tab, () => toggleDone(task.id));
      const chips = chipsLine(tab, projection);
      const inlineChip =
        tab === 'recurring' && projection.recurrenceChip
          ? ({
              text: projection.recurrenceChip,
              icon: Repeat,
              tint: { kind: 'custom', background: visual.background, foreground: visual.foreground },
            } satisfies RowChip)
          : undefined;
      return {
        id: projection.rowId,
        title: projection.title,
        subtitle: projection.subtitle,
        template: 'statusChip',
        leading:
          projection.isAssigned && projection.assigneeLabel
            ? {
                kind: 'avatar',
                name: projection.assigneeLabel,
                imageURL: null,
                identity: 'home',
                ringProgress: 1,
              }
            : {
                kind: 'typeIcon',
                icon: visual.icon,
                background: visual.background,
                foreground: visual.foreground,
              },
        trailing,
        chips,
        inlineChip,
        highlight: projection.highlight,
        onTap: () => {
          // Detail surface lands in a follow-up PR — keep the row tap
          // a no-op for now beyond the explicit checkbox toggle on
          // Active.
          if (tab === 'active' && !isDone) toggleDone(task.id);
        },
      } satisfies RowModel;
    });
    return { kind: 'loaded', sections: [{ id: 'tasks', rows }], hasMore: false };
  }, [loadKind, errorMessage, filteredTasks, tab, now, openAdd, toggleDone]);

  return (
    <>
      <div data-testid="householdTasksList">
        <ListOfRowsShell
          title="Tasks"
          state={state}
          onRefresh={fetchTasks}
          tabs={[
            { id: 'active', label: 'Active', count: counts.active },
            { id: 'done', label: 'Done', count: counts.done },
            { id: 'recurring', label: 'Recurring', count: counts.recurring },
          ]}
          selectedTab={tab}
          onTabChange={(id) => setTab(id as TaskTab)}
          topBarAction={undefined}
          banner={banner}
          fab={{
            icon: Plus,
            accessibilityLabel: 'Add a task',
            variant: { kind: 'secondaryCreate' },
            tint: 'home',
            onClick: openAdd,
          }}
        />
      </div>

      <ModalShell
        open={showAdd}
        onClose={() => !creating && setShowAdd(false)}
        icon={ListChecks}
        title="Add a task"
        subtitle="Track a one-off chore, or set up a recurring task later."
        cancelLabel="Cancel"
        onCancel={() => !creating && setShowAdd(false)}
        cancelDisabled={creating}
        submitLabel="Add task"
        onSubmit={handleCreate}
        submitDisabled={creating || !newTitle.trim()}
        submitting={creating}
        submitIcon={Plus}
      >
        <div className="space-y-3">
          <label className="block">
            <span className="block text-xs font-medium text-app-text-secondary mb-1">
              Title
            </span>
            <input
              type="text"
              value={newTitle}
              onChange={(e) => setNewTitle(e.target.value)}
              placeholder="Take out trash"
              className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-emerald-400"
              data-testid="addTask_title"
            />
          </label>
          <label className="block">
            <span className="block text-xs font-medium text-app-text-secondary mb-1">
              Notes (optional)
            </span>
            <textarea
              value={newDescription}
              onChange={(e) => setNewDescription(e.target.value)}
              placeholder="Wheelie bins out front by Tuesday morning"
              rows={2}
              className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-emerald-400 resize-none"
              data-testid="addTask_description"
            />
          </label>
          <div>
            <span className="block text-xs font-medium text-app-text-secondary mb-1">
              Priority
            </span>
            <div className="flex gap-2">
              {(['low', 'medium', 'high'] as const).map((p) => {
                const selected = newPriority === p;
                return (
                  <button
                    key={p}
                    type="button"
                    onClick={() => setNewPriority(p)}
                    className={`flex-1 py-2 rounded-lg border text-sm font-medium capitalize transition ${
                      selected
                        ? 'border-emerald-500 text-emerald-700 bg-emerald-50'
                        : 'border-app-border text-app-text-secondary'
                    }`}
                    data-testid={`addTask_priority_${p}`}
                  >
                    {p}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </ModalShell>
    </>
  );
}

function trailingFor(
  task: HomeTask,
  tab: TaskTab,
  onToggle: () => void,
): RowTrailing {
  if (tab === 'active') {
    const isDone = task.status === 'done';
    return {
      kind: 'circularAction',
      icon: isDone ? Check : Circle,
      accessibilityLabel: isDone ? 'Mark not done' : 'Mark done',
      background: isDone ? '#dcfce7' : '#ffffff',
      foreground: isDone ? '#16a34a' : '#9ca3af',
      onClick: onToggle,
    };
  }
  if (tab === 'done') {
    return { kind: 'statusChip', text: 'Done', variant: 'success', icon: Check };
  }
  // Recurring — kebab for edit. Edit affordance lands in a follow-up.
  return { kind: 'kebab' };
}

function chipsLine(
  tab: TaskTab,
  projection: HouseholdTaskRowProjection,
): RowChip[] | undefined {
  if (tab === 'recurring') return undefined;
  if (!projection.chipText || !projection.chipVariant) return undefined;
  return [
    {
      text: projection.chipText,
      icon: projection.chipIcon,
      tint: { kind: 'status', variant: projection.chipVariant },
    },
  ];
}

function emptyConfigForTab(
  tab: TaskTab,
  openAdd: () => void,
): { icon: LucideIcon; headline: string; subcopy: string; ctaTitle?: string; onCta?: () => void } {
  switch (tab) {
    case 'active':
      return {
        icon: ListChecks,
        headline: 'No tasks yet',
        subcopy:
          "Track who's doing what. Add a one-off chore, or set up the recurring stuff " +
          '(trash, dog walks, plants) once and let it spawn itself.',
        ctaTitle: 'Add a task',
        onCta: openAdd,
      };
    case 'done':
      return {
        icon: CheckCircle2,
        headline: 'Nothing done yet',
        subcopy: 'Finished chores from the last 30 days will show up here.',
        ctaTitle: 'Add a task',
        onCta: openAdd,
      };
    case 'recurring':
      return {
        icon: Repeat,
        headline: 'No recurring chores',
        subcopy:
          'Set up the weekly trash run, daily dog walks, or plant watering once and ' +
          "they'll spawn themselves.",
        ctaTitle: 'Add a recurring task',
        onCta: openAdd,
      };
  }
}

export default function TasksPage() {
  return (
    <Suspense>
      <TasksContent />
    </Suspense>
  );
}
