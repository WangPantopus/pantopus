"use client";

// Searchable timezone bottom sheet. Opens from the slot picker's tz chip.
// Pinned "Detected" device-zone row + a Common list; each row shows the GMT
// offset and current local time. Selected checkmark uses the pillar accent.
// Changing the zone re-times the slots beneath (caller refetches on onSelect).

import { useMemo, useState } from "react";
import { Check, RotateCcw, Search, SearchX } from "lucide-react";
import clsx from "clsx";
import BottomSheet from "@/components/ui/BottomSheet";
import { pillarTokens, type Pillar } from "./pillarTokens";

const COMMON_ZONES = [
  "Pacific/Honolulu",
  "America/Anchorage",
  "America/Los_Angeles",
  "America/Denver",
  "America/Phoenix",
  "America/Chicago",
  "America/New_York",
  "America/Toronto",
  "America/Sao_Paulo",
  "Europe/London",
  "Europe/Dublin",
  "Europe/Paris",
  "Europe/Berlin",
  "Europe/Madrid",
  "Europe/Athens",
  "Europe/Moscow",
  "Africa/Johannesburg",
  "Asia/Dubai",
  "Asia/Kolkata",
  "Asia/Bangkok",
  "Asia/Shanghai",
  "Asia/Singapore",
  "Asia/Hong_Kong",
  "Asia/Tokyo",
  "Australia/Perth",
  "Australia/Sydney",
  "Pacific/Auckland",
  "UTC",
];

export function detectTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  } catch {
    return "UTC";
  }
}

export function zoneLabel(tz: string): string {
  const tail = tz.split("/").pop() || tz;
  return tail.replace(/_/g, " ");
}

function offsetLabel(tz: string): string {
  try {
    const parts = new Intl.DateTimeFormat("en-US", {
      timeZone: tz,
      timeZoneName: "shortOffset",
    }).formatToParts(new Date());
    return parts.find((p) => p.type === "timeZoneName")?.value || "";
  } catch {
    return "";
  }
}

function localTime(tz: string): string {
  try {
    return new Intl.DateTimeFormat("en-US", {
      timeZone: tz,
      hour: "numeric",
      minute: "2-digit",
    }).format(new Date());
  } catch {
    return "";
  }
}

interface TimezoneSelectorProps {
  open: boolean;
  onClose: () => void;
  value?: string;
  onSelect: (tz: string) => void;
  pillar?: Pillar;
}

export default function TimezoneSelector({
  open,
  onClose,
  value,
  onSelect,
  pillar = "personal",
}: TimezoneSelectorProps) {
  const [query, setQuery] = useState("");
  const detected = useMemo(detectTimezone, []);
  const tk = pillarTokens(pillar);

  const zones = useMemo(
    () => Array.from(new Set([detected, ...COMMON_ZONES])),
    [detected],
  );
  const q = query.trim().toLowerCase();
  const filtered = q
    ? zones.filter(
        (z) =>
          z.toLowerCase().includes(q) || zoneLabel(z).toLowerCase().includes(q),
      )
    : zones;

  const handlePick = (tz: string) => {
    onSelect(tz);
    onClose();
  };

  return (
    <BottomSheet open={open} onClose={onClose} title="Time zone">
      <div className="space-y-3">
        <div className="relative">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-app-text-muted"
            aria-hidden
          />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search a city or zone"
            aria-label="Search time zones"
            className="w-full rounded-lg border border-app-border bg-app-surface py-2 pl-9 pr-3 text-sm text-app-text placeholder:text-app-text-muted focus:border-app-personal focus:outline-none focus:ring-1 focus:ring-app-personal"
          />
        </div>

        {value && value !== detected && (
          <button
            type="button"
            onClick={() => handlePick(detected)}
            className="flex items-center gap-2 text-xs font-medium text-app-personal hover:underline"
          >
            <RotateCcw className="h-3.5 w-3.5" aria-hidden />
            Reset to detected ({zoneLabel(detected)})
          </button>
        )}

        {filtered.length === 0 ? (
          <div className="flex flex-col items-center gap-2 rounded-xl border border-app-border bg-app-surface py-10 text-center">
            <SearchX className="h-6 w-6 text-app-text-muted" aria-hidden />
            <p className="text-sm font-medium text-app-text">
              No time zones match “{query}”
            </p>
            <p className="text-xs text-app-text-muted">Try a city name.</p>
          </div>
        ) : (
          <ul className="max-h-[50vh] overflow-y-auto rounded-xl border border-app-border divide-y divide-app-border-subtle">
            {filtered.map((tz) => {
              const selected = tz === value;
              const isDetected = tz === detected;
              return (
                <li key={tz}>
                  <button
                    type="button"
                    onClick={() => handlePick(tz)}
                    aria-pressed={selected}
                    className="flex w-full items-center justify-between gap-3 bg-app-surface px-3 py-3 text-left hover:bg-app-hover"
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium text-app-text">
                        {zoneLabel(tz)}
                        {isDetected && (
                          <span className="ml-2 text-xs font-normal text-app-text-muted">
                            Detected
                          </span>
                        )}
                      </span>
                      <span className="block truncate text-xs text-app-text-muted">
                        {[offsetLabel(tz), localTime(tz)]
                          .filter(Boolean)
                          .join(" · ")}
                      </span>
                    </span>
                    {selected && (
                      <Check
                        className={clsx("h-4 w-4 shrink-0", tk.text)}
                        aria-hidden
                      />
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </BottomSheet>
  );
}
