"use client";

// E9 — Booking Search & Filter (the filter sheet). Edits the live filter model
// in place: status / event-type / date facets + a free-text query that searches
// invitee name (q maps to GET /bookings?q=). The CTA reflects the current result
// count and just closes the sheet (results update live behind it). Empty result
// disables the CTA with a "no matches" note.

import { Search, SearchX, X } from "lucide-react";
import BottomSheet from "@/components/ui/BottomSheet";
import { FilterChip, SectionOverline } from "./ui";
import {
  DATE_FACETS,
  STATUS_FACETS,
  type BookingFilters,
  countActiveFilters,
} from "./filters";

interface EventTypeOption {
  id: string;
  name: string;
}

export default function BookingSearchFilter({
  open,
  onClose,
  filters,
  onChange,
  eventTypes,
  resultCount,
  loading,
  onClear,
}: {
  open: boolean;
  onClose: () => void;
  filters: BookingFilters;
  onChange: (next: BookingFilters) => void;
  eventTypes: EventTypeOption[];
  resultCount: number | null;
  loading: boolean;
  onClear: () => void;
}) {
  if (!open) return null;

  const activeCount = countActiveFilters(filters);
  const hasResults = (resultCount ?? 0) > 0;
  const noMatches = resultCount === 0 && activeCount > 0;

  const set = (patch: Partial<BookingFilters>) =>
    onChange({ ...filters, ...patch });

  const toggleStatus = (id: BookingFilters["status"]) =>
    set({ status: filters.status === id ? "all" : id });
  const toggleDate = (id: BookingFilters["date"]) =>
    set({ date: filters.date === id ? "all" : id });
  const toggleEventType = (id: string) =>
    set({ eventTypeId: filters.eventTypeId === id ? null : id });

  const activeChips: Array<{ label: string; onRemove: () => void }> = [];
  if (filters.status !== "all") {
    const s = STATUS_FACETS.find((f) => f.id === filters.status);
    if (s)
      activeChips.push({
        label: s.label,
        onRemove: () => set({ status: "all" }),
      });
  }
  if (filters.eventTypeId) {
    const et = eventTypes.find((e) => e.id === filters.eventTypeId);
    activeChips.push({
      label: et?.name ?? "Event type",
      onRemove: () => set({ eventTypeId: null }),
    });
  }
  if (filters.date !== "all") {
    const d = DATE_FACETS.find((f) => f.id === filters.date);
    if (d)
      activeChips.push({
        label: d.label,
        onRemove: () => set({ date: "all" }),
      });
  }

  const ctaLabel = loading
    ? "Updating…"
    : resultCount === null
      ? "Show bookings"
      : noMatches
        ? "No matches"
        : `Show ${resultCount} booking${resultCount === 1 ? "" : "s"}`;

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      footer={
        <button
          type="button"
          onClick={onClose}
          disabled={!hasResults && activeCount > 0}
          className="w-full rounded-lg bg-primary-600 px-4 py-3 text-sm font-semibold text-white transition hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-app-surface-sunken disabled:text-app-text-muted"
        >
          {ctaLabel}
        </button>
      }
    >
      <div className="px-1">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-base font-bold text-app-text">Filter bookings</h3>
          <button
            type="button"
            onClick={onClear}
            disabled={activeCount === 0}
            className="text-[13px] font-semibold text-primary-600 disabled:text-app-text-muted"
          >
            Clear all
          </button>
        </div>

        {/* Search */}
        <div className="mb-4 flex items-center gap-2 rounded-lg border border-app-border bg-app-surface-sunken px-3">
          <Search className="h-4 w-4 text-app-text-muted" aria-hidden />
          <input
            value={filters.q}
            onChange={(e) => set({ q: e.target.value })}
            placeholder="Search invitee name"
            aria-label="Search invitee name"
            className="w-full bg-transparent py-2.5 text-sm text-app-text placeholder:text-app-text-muted focus:outline-none"
          />
          {filters.q && (
            <button
              type="button"
              onClick={() => set({ q: "" })}
              aria-label="Clear search"
              className="text-app-text-muted hover:text-app-text"
            >
              <X className="h-4 w-4" aria-hidden />
            </button>
          )}
        </div>

        {/* Active summary */}
        {activeChips.length > 0 && (
          <div className="mb-4">
            <SectionOverline className="mb-2">Active filters</SectionOverline>
            <div className="flex flex-wrap gap-2">
              {activeChips.map((c) => (
                <button
                  key={c.label}
                  type="button"
                  onClick={c.onRemove}
                  className="inline-flex h-7 items-center gap-1.5 rounded-full bg-app-info-bg px-3 text-[11px] font-semibold text-app-info"
                >
                  {c.label}
                  <X className="h-3 w-3" aria-hidden />
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Status */}
        <div className="mb-4">
          <SectionOverline className="mb-2">Status</SectionOverline>
          <div className="flex flex-wrap gap-2">
            {STATUS_FACETS.map((f) => (
              <FilterChip
                key={f.id}
                label={f.label}
                tone={f.tone}
                active={filters.status === f.id}
                onClick={() => toggleStatus(f.id)}
              />
            ))}
          </div>
        </div>

        {/* Event type */}
        {eventTypes.length > 0 && (
          <div className="mb-4">
            <SectionOverline className="mb-2">Event type</SectionOverline>
            <div className="flex flex-wrap gap-2">
              {eventTypes.map((et) => (
                <FilterChip
                  key={et.id}
                  label={et.name}
                  active={filters.eventTypeId === et.id}
                  onClick={() => toggleEventType(et.id)}
                />
              ))}
            </div>
          </div>
        )}

        {/* Date range */}
        <div className="mb-2">
          <SectionOverline className="mb-2">Date range</SectionOverline>
          <div className="flex flex-wrap gap-2">
            {DATE_FACETS.map((f) => (
              <FilterChip
                key={f.id}
                label={f.label}
                active={filters.date === f.id}
                onClick={() => toggleDate(f.id)}
              />
            ))}
          </div>
          {filters.date === "custom" && (
            <div className="mt-3 grid grid-cols-2 gap-2">
              <label className="block">
                <span className="mb-1 block text-[11px] font-semibold uppercase text-app-text-muted">
                  From
                </span>
                <input
                  type="date"
                  value={filters.from ?? ""}
                  onChange={(e) => set({ from: e.target.value || null })}
                  className="w-full rounded-lg border border-app-border bg-app-surface-sunken px-3 py-2 text-sm text-app-text focus:outline-none focus:ring-2 focus:ring-primary-500/40"
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-[11px] font-semibold uppercase text-app-text-muted">
                  To
                </span>
                <input
                  type="date"
                  value={filters.to ?? ""}
                  onChange={(e) => set({ to: e.target.value || null })}
                  className="w-full rounded-lg border border-app-border bg-app-surface-sunken px-3 py-2 text-sm text-app-text focus:outline-none focus:ring-2 focus:ring-primary-500/40"
                />
              </label>
            </div>
          )}
        </div>

        {noMatches && (
          <div className="mt-4 flex flex-col items-center gap-2 rounded-xl border border-dashed border-app-border bg-app-surface px-4 py-6 text-center">
            <SearchX className="h-6 w-6 text-app-text-muted" aria-hidden />
            <p className="text-sm font-semibold text-app-text">
              No bookings match these filters
            </p>
            <button
              type="button"
              onClick={onClear}
              className="text-xs font-semibold text-primary-600"
            >
              Clear all
            </button>
          </div>
        )}
      </div>
    </BottomSheet>
  );
}
