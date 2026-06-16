"use client";

// W16 · H8 — Message Template Library. The list of reusable templates: a channel
// icon tile, name, a body snippet, an active chip + toggle, opening the H5 editor.
// Backed by GET /message-templates, PUT /message-templates/:id (toggle active).
// Personal sky pillar.

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import clsx from "clsx";
import { MessageSquarePlus, Plus } from "lucide-react";
import * as api from "@pantopus/api";
import type { MessageTemplate } from "@pantopus/types";
import { useSchedulingOwner } from "@/components/scheduling/SchedulingOwnerProvider";
import {
  pillarForOwner,
  pillarTokens,
} from "@/components/scheduling/pillarTokens";
import { decodeError } from "@/components/scheduling/decodeError";
import { toast } from "@/components/ui/toast-store";
import { ShimmerBlock } from "@/components/ui/Shimmer";
import ErrorState from "@/components/ui/ErrorState";
import { Card, Chip, IconTile, Overline, Toggle } from "./kit";
import { channelMeta } from "./templateMeta";

function snippet(body: string): string {
  const oneLine = body.replace(/\s+/g, " ").trim();
  return oneLine.length > 64 ? `${oneLine.slice(0, 64)}…` : oneLine;
}

export default function TemplateLibrary() {
  const router = useRouter();
  const owner = useSchedulingOwner();
  const pillar = pillarForOwner(owner.ownerType);

  const [phase, setPhase] = useState<"loading" | "error" | "ready">("loading");
  const [templates, setTemplates] = useState<MessageTemplate[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setPhase("loading");
    try {
      const { templates: list } =
        await api.scheduling.listMessageTemplates(owner);
      setTemplates(list ?? []);
      setPhase("ready");
    } catch {
      setPhase("error");
    }
  }, [owner]);

  useEffect(() => {
    void load();
  }, [load]);

  const toggleActive = async (t: MessageTemplate) => {
    const next = !t.is_active;
    setBusyId(t.id);
    setTemplates((list) =>
      list.map((x) => (x.id === t.id ? { ...x, is_active: next } : x)),
    );
    try {
      await api.scheduling.updateMessageTemplate(
        t.id,
        { is_active: next },
        owner,
      );
    } catch (err) {
      setTemplates((list) =>
        list.map((x) => (x.id === t.id ? { ...x, is_active: !next } : x)),
      );
      toast.error(decodeError(err).message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <header className="mb-4 flex items-start justify-between gap-4">
        <div className="min-w-0">
          <Overline pillar={pillar} className="mb-1.5">
            Reminders &amp; automations
          </Overline>
          <h1 className="text-xl font-bold text-app-text">Templates</h1>
          <p className="mt-0.5 text-sm text-app-text-secondary">
            Reusable message copy for workflows and manual sends.
          </p>
        </div>
        <button
          type="button"
          onClick={() => router.push("/app/scheduling/templates/new")}
          className="inline-flex shrink-0 items-center gap-1.5 rounded-lg bg-primary-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-primary-700"
        >
          <Plus className="h-4 w-4" strokeWidth={2.4} aria-hidden />
          New
        </button>
      </header>

      {phase === "loading" && (
        <Card>
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex items-center gap-3 px-3.5 py-3.5">
              <ShimmerBlock className="h-[34px] w-[34px] rounded-[9px]" />
              <div className="flex-1 space-y-1.5">
                <ShimmerBlock className="h-3 w-1/3 rounded" />
                <ShimmerBlock className="h-2.5 w-2/3 rounded" />
              </div>
              <ShimmerBlock className="h-[28px] w-[46px] rounded-full" />
            </div>
          ))}
        </Card>
      )}

      {phase === "error" && (
        <ErrorState
          message="Couldn't load templates."
          onRetry={() => void load()}
        />
      )}

      {phase === "ready" && templates.length === 0 && (
        <EmptyTemplates
          pillar={pillar}
          onAdd={() => router.push("/app/scheduling/templates/new")}
        />
      )}

      {phase === "ready" && templates.length > 0 && (
        <Card>
          {templates.map((t, i) => {
            const meta = channelMeta(t.channel);
            return (
              <div
                key={t.id}
                className={clsx(
                  "flex items-center gap-3 px-3.5 py-3",
                  i < templates.length - 1 &&
                    "border-b border-app-border-subtle",
                )}
              >
                <button
                  type="button"
                  onClick={() =>
                    router.push(`/app/scheduling/templates/${t.id}`)
                  }
                  className="flex min-w-0 flex-1 items-center gap-3 text-left"
                >
                  <IconTile icon={meta.icon} pillar={pillar} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate text-[13.5px] font-semibold text-app-text">
                        {t.name}
                      </span>
                      <Chip tone={t.is_active ? "success" : "neutral"}>
                        {t.is_active ? "Active" : "Off"}
                      </Chip>
                    </div>
                    <div className="mt-0.5 truncate text-[11.5px] text-app-text-secondary">
                      <span className="font-medium text-app-text-muted">
                        {meta.label}
                      </span>
                      {t.body ? ` · ${snippet(t.body)}` : ""}
                    </div>
                  </div>
                </button>
                <Toggle
                  on={t.is_active}
                  pillar={pillar}
                  disabled={busyId === t.id}
                  onChange={() => toggleActive(t)}
                  label={`${t.name} active`}
                />
              </div>
            );
          })}
        </Card>
      )}
    </div>
  );
}

function EmptyTemplates({
  pillar,
  onAdd,
}: {
  pillar: ReturnType<typeof pillarForOwner>;
  onAdd: () => void;
}) {
  const tk = pillarTokens(pillar);
  return (
    <div className="flex flex-col items-center gap-2.5 rounded-xl border border-app-border bg-app-surface px-6 py-12 text-center">
      <span
        className={clsx(
          "flex h-14 w-14 items-center justify-center rounded-full",
          tk.bgSoft,
          tk.text,
        )}
      >
        <MessageSquarePlus className="h-6 w-6" strokeWidth={1.8} aria-hidden />
      </span>
      <h2 className="text-[15px] font-bold text-app-text">No templates yet</h2>
      <p className="max-w-xs text-[12.5px] leading-5 text-app-text-secondary">
        Save the messages you reuse — thank-you notes, reminders, review
        requests — and drop them into workflows.
      </p>
      <button
        type="button"
        onClick={onAdd}
        className="mt-1 inline-flex items-center gap-1.5 rounded-lg border border-app-border-strong bg-app-surface px-4 py-2 text-[13px] font-bold text-app-text transition hover:bg-app-hover"
      >
        <Plus className={clsx("h-3.5 w-3.5", tk.text)} aria-hidden />
        New template
      </button>
    </div>
  );
}
