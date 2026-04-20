/**
 * Unified status colors used across the entire app.
 *
 * Gig and Bid statuses are sourced from @pantopus/ui-utils.
 * Home-task, Bill, and Issue statuses remain local (web-only).
 */

import {
  GIG_STATUS_STYLES as GIG_STATUS,
  BID_STATUS_STYLES as BID_STATUS,
  statusClasses,
  statusLabel,
} from '@pantopus/ui-utils';

// Re-export shared statuses with their legacy names
export { GIG_STATUS, BID_STATUS, statusClasses, statusLabel };

// ─── Home-task statuses (web-only) ──────────────────────────
export const TASK_STATUS: Record<string, { bg: string; text: string; label: string }> = {
  open:        { bg: 'bg-emerald-50', text: 'text-emerald-700', label: 'Open' },
  in_progress: { bg: 'bg-violet-50',  text: 'text-violet-700',  label: 'In Progress' },
  done:        { bg: 'bg-teal-50',    text: 'text-teal-700',    label: 'Done' },
  canceled:    { bg: 'bg-gray-100',   text: 'text-gray-500',    label: 'Canceled' },
};

// ─── Bill statuses (web-only) ───────────────────────────────
export const BILL_STATUS: Record<string, { bg: string; text: string; label: string }> = {
  due:      { bg: 'bg-blue-50',   text: 'text-blue-700',  label: 'Due' },
  paid:     { bg: 'bg-teal-50',   text: 'text-teal-700',  label: 'Paid' },
  overdue:  { bg: 'bg-red-50',    text: 'text-red-600',   label: 'Overdue' },
  canceled: { bg: 'bg-gray-100',  text: 'text-gray-500',  label: 'Canceled' },
};

// ─── Issue statuses (web-only) ──────────────────────────────
export const ISSUE_STATUS: Record<string, { bg: string; text: string; label: string }> = {
  open:        { bg: 'bg-emerald-50', text: 'text-emerald-700', label: 'Open' },
  scheduled:   { bg: 'bg-violet-50',  text: 'text-violet-700',  label: 'Scheduled' },
  in_progress: { bg: 'bg-amber-50',   text: 'text-amber-700',   label: 'In Progress' },
  resolved:    { bg: 'bg-teal-50',    text: 'text-teal-700',    label: 'Resolved' },
  canceled:    { bg: 'bg-gray-100',   text: 'text-gray-500',    label: 'Canceled' },
};
