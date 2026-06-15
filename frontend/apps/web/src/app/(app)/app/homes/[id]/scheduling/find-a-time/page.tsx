"use client";

// F4 → F5 — Household "Find a time". One route, three steps:
//   setup     → FindATimeSetup (title, who's needed, mode, duration, window)
//   computing → overlaying everyone's personal availability
//   results   → SuggestedSlots (ranked common-free times) → Book it (hands to the
//               W10 add-event flow) or Send proposal (creates a poll → F6)
// Owner context is home (the route's /app/homes/:id segment resolves it), so all
// reads/writes route through the /api/homes/:id/scheduling alias.

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, BarChart3, Home, Loader2, Users } from "lucide-react";
import * as api from "@pantopus/api";
import { getAuthToken } from "@pantopus/api";
import { APP_WEB_URL } from "@pantopus/utils";
import type { BookingSlot } from "@pantopus/types";
import ErrorState from "@/components/ui/ErrorState";
import { toast } from "@/components/ui/toast-store";
import { useSchedulingOwner } from "@/components/scheduling/SchedulingOwnerProvider";
import { decodeError } from "@/components/scheduling/decodeError";
import TimezoneSelector, {
  detectTimezone,
  zoneLabel,
} from "@/components/scheduling/TimezoneSelector";
import ShareLink from "@/components/scheduling/ShareLink";
import FindATimeSetup, {
  type FindATimeConfig,
} from "@/components/scheduling/home/find-a-time/FindATimeSetup";
import SuggestedSlots from "@/components/scheduling/home/find-a-time/SuggestedSlots";
import {
  readMembers,
  shortName,
  type MemberView,
} from "@/components/scheduling/home/find-a-time/members";
import {
  rangeLabel,
  todayKey,
  addDaysKey,
} from "@/components/scheduling/home/find-a-time/format";

type Step = "setup" | "computing" | "results" | "sent";

export default function FindATimePage() {
  const router = useRouter();
  const owner = useSchedulingOwner();
  const params = useParams<{ id: string }>();
  const homeId = params?.id ?? "";

  const [tz, setTz] = useState<string>(() => detectTimezone());
  const [tzOpen, setTzOpen] = useState(false);

  const [members, setMembers] = useState<MemberView[]>([]);
  const [membersLoading, setMembersLoading] = useState(true);
  const [membersError, setMembersError] = useState<string | null>(null);

  const [step, setStep] = useState<Step>("setup");
  const [config, setConfig] = useState<FindATimeConfig | null>(null);
  const [slots, setSlots] = useState<BookingSlot[]>([]);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [sentPollId, setSentPollId] = useState<string | null>(null);

  const loadMembers = useCallback(async () => {
    if (!getAuthToken()) {
      router.push("/login");
      return;
    }
    setMembersLoading(true);
    setMembersError(null);
    try {
      const res = await api.homeIam.getHomeMembers(homeId);
      setMembers(readMembers(res.members ?? []));
    } catch (err) {
      setMembersError(
        decodeError(err).message || "Couldn’t load your household.",
      );
    } finally {
      setMembersLoading(false);
    }
  }, [homeId, router]);

  useEffect(() => {
    loadMembers();
  }, [loadMembers]);

  const defaultFrom = useMemo(() => todayKey(tz), [tz]);
  const defaultTo = useMemo(() => addDaysKey(todayKey(tz), 6), [tz]);

  // The members we actually overlay: collective needs everyone required free;
  // round-robin includes optionals too (anyone can cover).
  const queryIds = useMemo(() => {
    if (!config) return [];
    return config.mode === "round_robin"
      ? [...config.requiredIds, ...config.optionalIds]
      : config.requiredIds;
  }, [config]);

  const shownMembers = useMemo(
    () => members.filter((m) => queryIds.includes(m.userId)),
    [members, queryIds],
  );

  const runFind = useCallback(
    async (cfg: FindATimeConfig) => {
      setConfig(cfg);
      setStep("computing");
      setFetchError(null);
      const ids =
        cfg.mode === "round_robin"
          ? [...cfg.requiredIds, ...cfg.optionalIds]
          : cfg.requiredIds;
      try {
        const res = await api.scheduling.getFindATime(
          {
            member_ids: ids,
            mode: cfg.mode,
            duration_min: cfg.durationMin,
            slot_interval_min: cfg.durationMin,
            from: cfg.fromKey,
            to: cfg.toKey,
            timezone: tz,
          },
          owner,
        );
        setSlots(res.slots ?? []);
        setStep("results");
      } catch (err) {
        setFetchError(decodeError(err).message);
        setStep("results");
      }
    },
    [owner, tz],
  );

  // Re-run when the tz changes while viewing results so labels stay correct.
  const changeTz = (next: string) => {
    setTz(next);
    if (config && step === "results") {
      runFind(config);
    }
  };

  const book = (slot: BookingSlot) => {
    const params = new URLSearchParams({
      start: slot.start,
      end: slot.end,
      title: config?.title ?? "",
    });
    // The household add-event flow (W10) creates the actual calendar event.
    router.push(
      `/app/homes/${homeId}/scheduling/events/new?${params.toString()}`,
    );
  };

  const sendProposal = async () => {
    if (!config || slots.length === 0) return;
    setSending(true);
    try {
      const { poll } = await api.scheduling.createPoll(
        {
          title: config.title,
          duration_min: config.durationMin,
          options: slots
            .slice(0, 20)
            .map((s) => ({ start: s.start, end: s.end })),
        },
        owner,
      );
      setSentPollId(poll.id);
      setStep("sent");
    } catch (err) {
      toast.error(decodeError(err).message || "Couldn’t send the proposal.");
    } finally {
      setSending(false);
    }
  };

  const windowLabel = config
    ? rangeLabel(config.fromKey, config.toKey)
    : "this week";
  const pollUrl = sentPollId ? `${APP_WEB_URL}/poll/${sentPollId}` : "";

  return (
    <div className="mx-auto max-w-2xl pb-10">
      <header className="mb-5">
        <button
          type="button"
          onClick={() =>
            step === "setup" || step === "sent"
              ? router.push(`/app/homes/${homeId}/calendar`)
              : setStep("setup")
          }
          className="mb-3 inline-flex items-center gap-1.5 text-sm font-medium text-app-text-secondary hover:text-app-text"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          {step === "setup" || step === "sent" ? "Calendar" : "Back to setup"}
        </button>
        <p className="text-xs font-bold uppercase tracking-wider text-app-home">
          Calendarly · Household
        </p>
        <h1 className="mt-0.5 text-2xl font-bold text-app-text">Find a time</h1>
        <p className="mt-1 max-w-md text-sm text-app-text-secondary">
          Overlay everyone&apos;s personal availability to find a time that
          works — without touching anyone&apos;s calendar.
        </p>
      </header>

      {step === "setup" && (
        <FindATimeSetup
          members={members}
          membersLoading={membersLoading}
          membersError={membersError}
          onRetryMembers={loadMembers}
          submitting={false}
          defaultFrom={defaultFrom}
          defaultTo={defaultTo}
          onSubmit={runFind}
        />
      )}

      {step === "computing" && (
        <div className="flex flex-col items-center justify-center rounded-2xl border border-app-border bg-app-surface px-6 py-16 text-center">
          <Loader2
            className="h-12 w-12 animate-spin text-app-home"
            aria-hidden
          />
          <h2 className="mt-5 text-lg font-bold text-app-text">
            Checking everyone&apos;s availability
          </h2>
          <p className="mt-1 text-sm text-app-text-secondary">
            Composing{" "}
            {shownMembers.length
              ? shownMembers.map((m) => shortName(m.name)).join(", ")
              : "the household"}
            &apos;s free time
          </p>
        </div>
      )}

      {step === "results" &&
        (fetchError ? (
          <ErrorState
            message={fetchError}
            onRetry={() => config && runFind(config)}
          />
        ) : (
          <SuggestedSlots
            slots={slots}
            members={shownMembers}
            requiredIds={config?.requiredIds ?? []}
            mode={config?.mode ?? "collective"}
            durationMin={config?.durationMin ?? 30}
            peopleCount={queryIds.length}
            windowLabel={windowLabel}
            tz={zoneLabel(tz)}
            onChangeTz={() => setTzOpen(true)}
            onEdit={() => setStep("setup")}
            onBook={book}
            onSendProposal={sendProposal}
            sending={sending}
          />
        ))}

      {step === "sent" && (
        <div className="flex flex-col items-center rounded-2xl border border-app-border bg-app-surface px-6 py-12 text-center">
          <span className="mb-5 flex h-20 w-20 items-center justify-center rounded-full bg-app-home-bg text-app-home">
            <Users className="h-9 w-9" aria-hidden />
          </span>
          <h2 className="text-xl font-bold text-app-text">
            Proposal sent to {queryIds.length}{" "}
            {queryIds.length === 1 ? "person" : "people"}
          </h2>
          <p className="mt-2 max-w-xs text-sm leading-relaxed text-app-text-secondary">
            Share the link so members can mark which times work. The most-picked
            time wins.
          </p>
          {pollUrl && (
            <div className="mt-5 w-full max-w-sm text-left">
              <ShareLink
                url={pollUrl}
                label="Poll link"
                shareTitle={config?.title}
                pillar="home"
              />
            </div>
          )}
          <div className="mt-5 flex w-full max-w-sm flex-col gap-2.5">
            {sentPollId && (
              <a
                href={`/poll/${sentPollId}`}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center justify-center gap-2 rounded-xl bg-app-home px-4 py-2.5 text-sm font-bold text-white hover:opacity-90"
              >
                <BarChart3 className="h-4 w-4" aria-hidden /> Open poll
              </a>
            )}
            <button
              type="button"
              onClick={() => router.push(`/app/homes/${homeId}/calendar`)}
              className="flex items-center justify-center gap-2 rounded-xl border border-app-border bg-app-surface px-4 py-2.5 text-sm font-bold text-app-text-secondary hover:bg-app-hover"
            >
              <Home className="h-4 w-4" aria-hidden /> Back to calendar
            </button>
          </div>
        </div>
      )}

      <TimezoneSelector
        open={tzOpen}
        onClose={() => setTzOpen(false)}
        value={tz}
        onSelect={changeTz}
        pillar="home"
      />
    </div>
  );
}
