'use client';

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  ArrowLeft,
  Plus,
  ClipboardList,
  CalendarDays,
  CheckCircle,
  MessageCircle,
  Lock,
  Clock,
  Trash2,
  X,
  type LucideIcon,
} from 'lucide-react';
import * as api from '@pantopus/api';
import type { PollOption, PollSummary } from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import { toast } from '@/components/ui/toast-store';
import { confirmStore } from '@/components/ui/confirm-store';

// ─── Kind palette ────────────────────────────────────────────────
// Mirrors `polls-frames.jsx:50-55` and the mobile `PollKindPalette`.
// Hex literals are the documented exception for designated palette files.

type PollKind = 'decision' | 'schedule' | 'yesno' | 'open';

const KIND_PALETTE: Record<
  PollKind,
  { label: string; icon: LucideIcon; bg: string; fg: string }
> = {
  decision: { label: 'Decision', icon: ClipboardList, bg: '#ede9fe', fg: '#6d28d9' },
  schedule: { label: 'Schedule', icon: CalendarDays, bg: '#dbeafe', fg: '#1d4ed8' },
  yesno: { label: 'Yes/No', icon: CheckCircle, bg: '#dcfce7', fg: '#15803d' },
  open: { label: 'Open', icon: MessageCircle, bg: '#e2e8f0', fg: '#334155' },
};

const SCHEDULE_KEYWORDS = [
  'when ', 'what day', 'what date', 'which day', 'which date',
  'weekend', 'schedule', 'saturday', 'sunday', 'monday', 'tuesday',
  'wednesday', 'thursday', 'friday', ' date ', ' date?', 'date?',
];

function kindFor(poll: PollSummary): PollKind {
  const normalised = (poll.poll_type || '').toLowerCase().replace(/-/g, '_');
  if (normalised === 'yes_no' || normalised === 'yesno') return 'yesno';
  const titleLower = ` ${(poll.title || '').toLowerCase()} `;
  if (SCHEDULE_KEYWORDS.some((kw) => titleLower.includes(kw))) return 'schedule';
  if (normalised === 'multiple_choice') return 'open';
  return 'decision';
}

// ─── Option normalisation ────────────────────────────────────────
// Options arrive as either strings or objects. Normalise to `{ id, label }`
// so the rest of the component speaks one shape.

interface NormalisedOption {
  id: string;
  label: string;
}

function normaliseOption(option: PollOption): NormalisedOption {
  if (typeof option === 'string') return { id: option, label: option };
  const id = option.id || option.key || option.label || option.text || '';
  const label = option.label || option.text || option.id || option.key || '';
  return { id, label };
}

// ─── Chip status ─────────────────────────────────────────────────

type PollChipStatus = 'active' | 'closing' | 'closed';

function chipStatus(poll: PollSummary, now: Date): PollChipStatus {
  const normalised = (poll.status || '').toLowerCase();
  if (normalised === 'closed' || normalised === 'canceled' || normalised === 'cancelled') {
    return 'closed';
  }
  if (poll.closes_at) {
    const closesAt = new Date(poll.closes_at);
    if (!Number.isNaN(closesAt.getTime())) {
      if (closesAt < now) return 'closed';
      const oneDayOut = new Date(now.getTime() + 24 * 60 * 60 * 1000);
      if (closesAt <= oneDayOut) return 'closing';
    }
  }
  return 'active';
}

function timeMetaText(poll: PollSummary, status: PollChipStatus, now: Date): string | null {
  if (!poll.closes_at) return null;
  const closes = new Date(poll.closes_at);
  if (Number.isNaN(closes.getTime())) return null;
  const monthDay = closes.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  if (status === 'closed') return `Closed ${monthDay}`;
  if (status === 'closing') {
    const seconds = (closes.getTime() - now.getTime()) / 1000;
    if (seconds <= 0) return 'Closes today';
    const hours = Math.floor(seconds / 3600);
    if (hours <= 0) {
      const minutes = Math.max(1, Math.floor(seconds / 60));
      return `Closes in ${minutes} min`;
    }
    return `Closes in ${hours} hr`;
  }
  return `Closes ${monthDay}`;
}

// ─── Leading-option summary ──────────────────────────────────────

function leadingOption(poll: PollSummary, options: NormalisedOption[]): { label: string; votes: number } | null {
  if (!poll.option_counts || options.length === 0) return null;
  let topLabel: string | null = null;
  let topVotes = 0;
  for (const option of options) {
    const votes = poll.option_counts[option.id] ?? poll.option_counts[option.label] ?? 0;
    if (votes > topVotes) {
      topVotes = votes;
      topLabel = option.label;
    }
  }
  if (topVotes === 0 || !topLabel) return null;
  return { label: topLabel, votes: topVotes };
}

// ─── Page ────────────────────────────────────────────────────────

type Tab = 'active' | 'closed';

function PollsContent() {
  const router = useRouter();
  const { id: homeId } = useParams<{ id: string }>();

  const [polls, setPolls] = useState<PollSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('active');
  const [showCreate, setShowCreate] = useState(false);
  const [votingPoll, setVotingPoll] = useState<string | null>(null);
  const [now, setNow] = useState(() => new Date());

  // Create-poll form state.
  const [newTitle, setNewTitle] = useState('');
  const [newOptions, setNewOptions] = useState(['', '']);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  // Refresh "now" once a minute so the closing-soon meta animates without
  // needing a re-fetch.
  useEffect(() => {
    const tick = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(tick);
  }, []);

  const fetchPolls = useCallback(async () => {
    if (!homeId) return;
    setError(null);
    try {
      const res = await api.homeProfile.getHomePolls(homeId);
      setPolls(res?.polls ?? []);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to load polls';
      setError(message);
    }
  }, [homeId]);

  useEffect(() => {
    setLoading(true);
    void fetchPolls().finally(() => setLoading(false));
  }, [fetchPolls]);

  const buckets = useMemo(() => {
    let active = 0;
    let closed = 0;
    const enriched = polls.map((poll) => {
      const status = chipStatus(poll, now);
      if (status === 'closed') closed += 1;
      else active += 1;
      return { poll, status };
    });
    return {
      enriched,
      counts: { active, closed },
    };
  }, [polls, now]);

  const visible = buckets.enriched.filter(({ status }) =>
    tab === 'active' ? status !== 'closed' : status === 'closed',
  );
  const awaiting = buckets.enriched.filter(
    ({ poll, status }) => status !== 'closed' && (!poll.my_vote || poll.my_vote.length === 0),
  ).length;

  const updateOption = (idx: number, val: string) =>
    setNewOptions(newOptions.map((opt, i) => (i === idx ? val : opt)));
  const addOption = () => {
    if (newOptions.length < 6) setNewOptions([...newOptions, '']);
  };
  const removeOption = (idx: number) => {
    if (newOptions.length > 2) setNewOptions(newOptions.filter((_, i) => i !== idx));
  };

  const handleCreate = useCallback(async () => {
    const title = newTitle.trim();
    const opts = newOptions.map((opt) => opt.trim()).filter(Boolean);
    if (!title || opts.length < 2) {
      toast.warning('Need a question and at least 2 options');
      return;
    }
    setCreating(true);
    try {
      await api.homeProfile.createHomePoll(homeId!, {
        title,
        options: opts.map((label) => ({ label })),
      });
      setNewTitle('');
      setNewOptions(['', '']);
      setShowCreate(false);
      toast.success('Poll created');
      await fetchPolls();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to create poll';
      toast.error(message);
    } finally {
      setCreating(false);
    }
  }, [homeId, newTitle, newOptions, fetchPolls]);

  const handleVote = useCallback(
    async (poll: PollSummary, optionId: string) => {
      setVotingPoll(poll.id);
      // Optimistic patch.
      setPolls((current) =>
        current.map((p) => {
          if (p.id !== poll.id) return p;
          const counts = { ...(p.option_counts || {}) };
          const previous = p.my_vote?.[0];
          if (previous) {
            counts[previous] = Math.max(0, (counts[previous] || 1) - 1);
            if (counts[previous] === 0) delete counts[previous];
          }
          counts[optionId] = (counts[optionId] || 0) + 1;
          return {
            ...p,
            vote_count: previous ? p.vote_count : p.vote_count + 1,
            option_counts: counts,
            my_vote: [optionId],
          };
        }),
      );
      try {
        await api.homeProfile.voteOnPoll(homeId!, poll.id, { selected_options: [optionId] });
        await fetchPolls();
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Failed to vote';
        toast.error(message);
        await fetchPolls();
      } finally {
        setVotingPoll(null);
      }
    },
    [homeId, fetchPolls],
  );

  const closePoll = useCallback(
    async (pollId: string) => {
      const yes = await confirmStore.open({
        title: 'Close poll',
        description: 'Close this poll to new votes?',
        confirmLabel: 'Close',
        variant: 'primary',
      });
      if (!yes) return;
      try {
        await api.homeProfile.updateHomePoll(homeId!, pollId, { status: 'closed' });
        toast.success('Poll closed');
        await fetchPolls();
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Failed to close poll';
        toast.error(message);
      }
    },
    [homeId, fetchPolls],
  );

  const removePoll = useCallback(
    async (pollId: string) => {
      const yes = await confirmStore.open({
        title: 'Delete poll',
        description: 'Are you sure? Closed polls remain in the history tab.',
        confirmLabel: 'Delete',
        variant: 'destructive',
      });
      if (!yes) return;
      try {
        // Backend has no DELETE for polls today; soft-close instead.
        await api.homeProfile.updateHomePoll(homeId!, pollId, { status: 'canceled' });
        await fetchPolls();
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Failed to delete poll';
        toast.error(message);
      }
    },
    [homeId, fetchPolls],
  );

  const TABS: { key: Tab; label: string; count: number }[] = [
    { key: 'active', label: 'Active', count: buckets.counts.active },
    { key: 'closed', label: 'Closed', count: buckets.counts.closed },
  ];

  if (loading) {
    return (
      <div
        className="flex items-center justify-center min-h-[50vh]"
        data-testid="pollsList_loading"
      >
        <div className="animate-spin h-8 w-8 border-3 border-emerald-600 border-t-transparent rounded-full" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-10 text-center" data-testid="pollsList_error">
        <p className="text-base font-semibold text-app-text mb-2">Couldn&apos;t load polls</p>
        <p className="text-sm text-app-text-secondary mb-6">{error}</p>
        <button
          onClick={() => {
            setLoading(true);
            void fetchPolls().finally(() => setLoading(false));
          }}
          className="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm font-semibold hover:bg-emerald-700 transition"
        >
          Try again
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-6" data-testid="pollsList">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={() => router.back()}
            className="p-1.5 hover:bg-app-hover rounded-lg transition"
            aria-label="Back"
          >
            <ArrowLeft className="w-5 h-5 text-app-text" />
          </button>
          <h1 className="text-xl font-bold text-app-text">Polls</h1>
        </div>
        <button
          onClick={() => setShowCreate(!showCreate)}
          className="flex items-center gap-1.5 px-3 py-2 bg-emerald-600 text-white text-sm font-semibold rounded-lg hover:bg-emerald-700 transition"
          data-testid="pollsList_startPoll"
        >
          <Plus className="w-4 h-4" /> Start a poll
        </button>
      </div>

      {/* Awaiting-vote banner */}
      {buckets.counts.active > 0 && tab === 'active' && (
        <div
          className="flex items-center gap-3 p-3 mb-4 bg-emerald-50 border border-emerald-200 rounded-xl"
          data-testid="pollsList_banner"
        >
          <div className="w-9 h-9 rounded-lg bg-white border border-emerald-200 flex items-center justify-center text-emerald-600">
            <CheckCircle className="w-4 h-4" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-app-text">
              {awaiting === 0
                ? `You're caught up on votes`
                : awaiting === 1
                  ? `1 poll needs your vote`
                  : `${awaiting} polls need your vote`}
            </p>
            <p className="text-xs text-app-text-secondary mt-0.5">
              {buckets.counts.active === 1
                ? '1 active in this household'
                : `${buckets.counts.active} active in this household`}
            </p>
          </div>
        </div>
      )}

      {showCreate && (
        <div className="bg-app-surface border border-app-border rounded-xl p-4 mb-4 space-y-3">
          <input
            type="text"
            value={newTitle}
            onChange={(e) => setNewTitle(e.target.value)}
            placeholder="What should we decide?"
            className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-emerald-400"
            data-testid="createPoll_title"
          />
          <p className="text-xs font-semibold text-app-text-strong">Options</p>
          {newOptions.map((opt, idx) => (
            <div key={idx} className="flex items-center gap-2">
              <input
                type="text"
                value={opt}
                onChange={(e) => updateOption(idx, e.target.value)}
                placeholder={`Option ${idx + 1}`}
                className="flex-1 px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-emerald-400"
              />
              {newOptions.length > 2 && (
                <button
                  type="button"
                  onClick={() => removeOption(idx)}
                  className="p-1 text-app-text-muted hover:text-red-500"
                  aria-label="Remove option"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>
          ))}
          {newOptions.length < 6 && (
            <button
              type="button"
              onClick={addOption}
              className="text-sm font-medium text-emerald-600 hover:text-emerald-700"
            >
              + Add option
            </button>
          )}
          <button
            onClick={handleCreate}
            disabled={creating}
            className="w-full py-2.5 bg-emerald-600 text-white rounded-lg font-semibold text-sm hover:bg-emerald-700 disabled:opacity-50 transition"
            data-testid="createPoll_submit"
          >
            {creating ? 'Creating…' : 'Create poll'}
          </button>
        </div>
      )}

      <div className="flex border-b border-app-border mb-4">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-4 py-2.5 text-sm font-medium transition ${
              tab === t.key
                ? 'text-emerald-700 border-b-2 border-emerald-600'
                : 'text-app-text-secondary hover:text-app-text'
            }`}
            data-testid={`pollsList_tab_${t.key}`}
          >
            {t.label} ({t.count})
          </button>
        ))}
      </div>

      {visible.length === 0 ? (
        <div className="text-center py-16">
          <CheckCircle className="w-10 h-10 mx-auto text-app-text-muted mb-3" />
          <p className="text-base font-semibold text-app-text mb-1">
            {tab === 'active' ? 'No active polls' : 'No closed polls yet'}
          </p>
          <p className="text-sm text-app-text-secondary mb-6 max-w-sm mx-auto">
            {tab === 'active'
              ? 'Ask the household. Paint colours, weekend plans, whether to replace the dishwasher — get a quick read instead of a long thread.'
              : 'Closed polls show up here once a vote wraps up or a member closes it manually.'}
          </p>
          {tab === 'active' && (
            <button
              onClick={() => setShowCreate(true)}
              className="inline-flex items-center gap-2 px-4 py-2.5 bg-emerald-600 text-white rounded-lg text-sm font-semibold hover:bg-emerald-700 transition"
            >
              <Plus className="w-4 h-4" /> Start a poll
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {visible.map(({ poll, status }) => {
            const options = (poll.options || []).map(normaliseOption);
            const totalVotes = options.reduce(
              (sum, opt) => sum + (poll.option_counts?.[opt.id] ?? poll.option_counts?.[opt.label] ?? 0),
              0,
            );
            const kind = kindFor(poll);
            const palette = KIND_PALETTE[kind];
            const KindIcon = palette.icon;
            const StatusIcon =
              status === 'active' ? CheckCircle : status === 'closing' ? Clock : Lock;
            const statusBadgeClass =
              status === 'active'
                ? 'bg-emerald-100 text-emerald-700'
                : status === 'closing'
                  ? 'bg-amber-100 text-amber-700'
                  : 'bg-slate-100 text-slate-600';
            const statusLabel =
              status === 'active' ? 'Active' : status === 'closing' ? 'Closes soon' : 'Closed';
            const meta = timeMetaText(poll, status, now);
            const leading = leadingOption(poll, options);
            const isActive = status !== 'closed';
            const isVoting = votingPoll === poll.id;
            const myVote = poll.my_vote?.[0] ?? null;
            const topVotes = leading?.votes ?? 0;

            return (
              <div
                key={poll.id}
                className={`bg-app-surface border border-app-border rounded-xl p-4 ${
                  status === 'closed' ? 'opacity-75' : ''
                }`}
                data-testid={`pollsList_row_${poll.id}`}
              >
                <div className="flex items-start gap-3 mb-3">
                  <div
                    className="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0"
                    style={{ background: palette.bg }}
                  >
                    <KindIcon className="w-5 h-5" style={{ color: palette.fg }} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p
                      className="text-[10px] font-semibold uppercase tracking-wide"
                      style={{ color: palette.fg }}
                    >
                      {palette.label}
                    </p>
                    <p className="text-sm font-semibold text-app-text mt-0.5">{poll.title}</p>
                    <p className="text-xs text-app-text-secondary mt-1">
                      {poll.vote_count} {poll.vote_count === 1 ? 'vote' : 'votes'} ·{' '}
                      {options.length} options
                    </p>
                  </div>
                  <div className="flex flex-col items-end gap-1.5 flex-shrink-0">
                    <span
                      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${statusBadgeClass}`}
                    >
                      <StatusIcon className="w-3 h-3" /> {statusLabel}
                    </span>
                    {meta && (
                      <span className="text-[10.5px] text-app-text-secondary">{meta}</span>
                    )}
                  </div>
                </div>

                <div className="space-y-1.5 mt-3">
                  {options.map((option) => {
                    const votes =
                      poll.option_counts?.[option.id] ?? poll.option_counts?.[option.label] ?? 0;
                    const pct = totalVotes > 0 ? Math.round((votes / totalVotes) * 100) : 0;
                    const isMyVote = myVote === option.id || myVote === option.label;
                    const isWinner = !isActive && votes === topVotes && topVotes > 0;
                    const buttonDisabled = !isActive || isVoting;
                    return (
                      <button
                        key={option.id}
                        type="button"
                        onClick={() => (isActive && !isVoting ? handleVote(poll, option.id) : undefined)}
                        disabled={buttonDisabled}
                        className={`relative w-full text-left rounded-lg px-3 py-2 border transition ${
                          isMyVote
                            ? 'border-emerald-500 bg-emerald-50'
                            : 'border-app-border hover:border-app-border-strong'
                        } ${!isActive ? 'cursor-default' : 'cursor-pointer'}`}
                        data-testid={`poll_${poll.id}_option_${option.id}`}
                      >
                        <div className="flex items-center justify-between relative z-10">
                          <span
                            className={`text-sm ${
                              isWinner ? 'font-bold text-emerald-700' : 'text-app-text-strong'
                            }`}
                          >
                            {option.label}
                          </span>
                          <span className="text-xs font-semibold text-app-text-secondary ml-2">
                            {pct}%
                          </span>
                        </div>
                        <div className="h-1 bg-app-surface-sunken rounded-full mt-1 relative z-10">
                          <div
                            className={`h-1 rounded-full transition-all ${
                              isWinner
                                ? 'bg-emerald-600'
                                : isMyVote
                                  ? 'bg-emerald-500'
                                  : 'bg-emerald-200'
                            }`}
                            style={{ width: `${pct}%` }}
                          />
                        </div>
                        <p className="text-[10.5px] text-app-text-muted mt-1 relative z-10">
                          {votes} {votes === 1 ? 'vote' : 'votes'}
                          {isMyVote ? ' · your vote' : ''}
                          {isWinner ? ' · winner' : ''}
                        </p>
                        {isMyVote && (
                          <CheckCircle className="absolute top-2 right-2 w-3.5 h-3.5 text-emerald-500" />
                        )}
                      </button>
                    );
                  })}
                </div>

                {leading && (
                  <p className="text-[11px] text-app-text-secondary mt-3">
                    {status === 'closed' ? 'Winner' : 'Leading'}:{' '}
                    <span className="font-semibold text-app-text">{leading.label}</span> ·{' '}
                    {leading.votes} {leading.votes === 1 ? 'vote' : 'votes'}
                  </p>
                )}

                {isActive && (
                  <div className="flex items-center gap-4 mt-3 pt-3 border-t border-app-border-subtle">
                    <button
                      onClick={() => closePoll(poll.id)}
                      className="flex items-center gap-1 text-xs font-medium text-amber-600 hover:text-amber-700"
                    >
                      <Lock className="w-3.5 h-3.5" /> Close
                    </button>
                    <button
                      onClick={() => removePoll(poll.id)}
                      className="flex items-center gap-1 text-xs font-medium text-red-500 hover:text-red-700"
                    >
                      <Trash2 className="w-3.5 h-3.5" /> Delete
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function PollsPage() {
  return (
    <Suspense>
      <PollsContent />
    </Suspense>
  );
}
