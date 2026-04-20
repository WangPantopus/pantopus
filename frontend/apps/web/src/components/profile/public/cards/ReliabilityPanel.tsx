interface ReliabilityPanelProps {
  profile: Record<string, unknown>;
  reliabilityLabel: string;
  reliabilityScore: number | null;
}

function BreakdownRow({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="flex items-center justify-between border-b border-app pb-2 last:border-0 last:pb-0">
      <span className="text-app-secondary">{label}</span>
      <span className="font-medium text-app">{value}</span>
    </div>
  );
}

export default function ReliabilityPanel({ profile, reliabilityLabel, reliabilityScore }: ReliabilityPanelProps) {
  return (
    <div className="bg-surface rounded-xl border border-app p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold text-app">Reliability</h3>
          <p className="text-sm text-app-secondary">{reliabilityLabel}</p>
        </div>
        {reliabilityScore != null && (
          <span
            className="text-xs px-2 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200"
            title="Calculated from no-shows, late cancels, disputes, and on-time rate over the last 90 days."
          >
            Score available
          </span>
        )}
      </div>

      <div className="mt-4 space-y-2.5 text-sm">
        <BreakdownRow label="No-shows" value={profile.no_show_count ?? '—'} />
        <BreakdownRow label="Late cancels" value={profile.late_cancel_count ?? '—'} />
        <BreakdownRow label="On-time rate" value={profile.on_time_rate != null ? `${profile.on_time_rate}%` : '—'} />
        <BreakdownRow label="Disputes" value={profile.dispute_count ?? '—'} />
      </div>
    </div>
  );
}
