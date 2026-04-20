'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import { buildSupportTrainShareUrl } from '@pantopus/utils';
import {
  Calendar,
  Clock,
  Heart,
  MapPin,
  Share2,
  Settings,
  Users,
  ChefHat,
  ShoppingCart,
  Truck,
  AlertTriangle,
  CheckCircle2,
  MessageSquare,
  RefreshCw,
} from 'lucide-react';

// ============================================================
// SUPPORT TRAIN CAMPAIGN PAGE (Web)
// Three-column layout: Left info | Center tabs | Right admin
// ============================================================

type TabKey = 'needs' | 'details' | 'updates';

export default function SupportTrainDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();

  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabKey>('needs');

  const fetchData = useCallback(async () => {
    const token = getAuthToken();
    if (!token) {
      router.push('/login');
      return;
    }
    try {
      const result = await api.supportTrains.getSupportTrain(id);
      setData(result);
      setError(null);
    } catch (err: any) {
      setError(err?.message || 'Failed to load');
    }
  }, [id, router]);

  useEffect(() => {
    setLoading(true);
    fetchData().finally(() => setLoading(false));
  }, [fetchData]);

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-16 flex justify-center">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-600" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-16 text-center">
        <AlertTriangle className="w-12 h-12 text-app-text-muted mx-auto mb-4" />
        <p className="text-app-text-secondary mb-4">{error || 'Not found'}</p>
        <button
          onClick={fetchData}
          className="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700 transition"
        >
          Retry
        </button>
      </div>
    );
  }

  const slots = data.slots || [];
  const openSlots = slots.filter((s: any) => s.status === 'open');
  const otherSlots = slots.filter((s: any) => s.status !== 'open');
  const updates = data.updates || [];
  const viewerLevel = data.viewer_level;
  const isOrganizer = viewerLevel === 'organizer';

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* ── Left Column: Campaign Info ── */}
        <div className="lg:col-span-3 space-y-6">
          {/* Back */}
          <button
            onClick={() => router.push('/app/support-trains')}
            className="text-sm text-app-text-secondary hover:text-app-text transition flex items-center gap-1"
          >
            &larr; All Trains
          </button>

          {/* Title + Story */}
          <div>
            <h1 className="text-2xl font-bold text-app-text">{data.title}</h1>
            {data.recipient_summary && (
              <p className="text-sm text-app-text-secondary mt-1 italic">
                {data.recipient_summary}
              </p>
            )}
            {data.story && (
              <p className="text-sm text-app-text-secondary mt-3 leading-relaxed">{data.story}</p>
            )}
          </div>

          {/* Restriction chips */}
          <div className="flex flex-wrap gap-2">
            {(data.dietary_restrictions || []).map((r: string, i: number) => (
              <span
                key={i}
                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300"
              >
                <AlertTriangle className="w-3 h-3" />
                {r.replace(/_/g, ' ')}
              </span>
            ))}
            {(data.dietary_preferences || []).map((p: string, i: number) => (
              <span
                key={`p-${i}`}
                className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300"
              >
                {p.replace(/_/g, ' ')}
              </span>
            ))}
            {data.household_size && (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-200">
                <Users className="w-3 h-3" />
                Family of {data.household_size}
              </span>
            )}
            {data.contactless_preferred && (
              <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-200">
                Contactless
              </span>
            )}
            {data.preferred_dropoff_window?.start_time && (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-200">
                <Clock className="w-3 h-3" />
                {data.preferred_dropoff_window.start_time}
                {data.preferred_dropoff_window.end_time
                  ? ` - ${data.preferred_dropoff_window.end_time}`
                  : '+'}
              </span>
            )}
          </div>

          {/* Support modes */}
          <div className="space-y-1">
            <p className="text-xs font-semibold text-app-text-muted uppercase tracking-wider">
              Support types
            </p>
            <div className="flex flex-wrap gap-2">
              {data.support_modes?.home_cooked_meals && <ModeBadge icon={ChefHat} label="Meals" />}
              {data.support_modes?.takeout && <ModeBadge icon={Truck} label="Takeout" />}
              {data.support_modes?.groceries && <ModeBadge icon={ShoppingCart} label="Groceries" />}
              {data.support_modes?.gift_funds && <ModeBadge icon={Heart} label="Gift Funds" />}
            </div>
          </div>

          {/* CTA buttons */}
          <div className="space-y-2">
            <button
              onClick={() => setActiveTab('needs')}
              className="w-full flex items-center justify-center gap-2 px-4 py-3 bg-primary-600 text-white rounded-lg text-sm font-medium hover:bg-primary-700 transition"
            >
              Take a slot
            </button>
            <button
              onClick={() => {
                navigator.clipboard?.writeText(buildSupportTrainShareUrl(id));
              }}
              className="w-full flex items-center justify-center gap-2 px-4 py-2.5 border border-app-border rounded-lg text-sm text-app-text-secondary hover:bg-app-surface-sunken transition"
            >
              <Share2 className="w-4 h-4" />
              Copy link
            </button>
          </div>
        </div>

        {/* ── Center Column: Tabs ── */}
        <div className="lg:col-span-6">
          {/* Tab bar */}
          <div className="flex border-b border-app-border mb-6">
            {(['needs', 'details', 'updates'] as TabKey[]).map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`px-5 py-3 text-sm font-medium border-b-2 -mb-px transition ${
                  activeTab === tab
                    ? 'border-primary-600 text-primary-600'
                    : 'border-transparent text-app-text-secondary hover:text-app-text'
                }`}
              >
                {tab === 'needs'
                  ? `Needs (${openSlots.length})`
                  : tab.charAt(0).toUpperCase() + tab.slice(1)}
              </button>
            ))}
          </div>

          {/* Needs tab */}
          {activeTab === 'needs' && (
            <div className="space-y-3">
              {slots.length === 0 ? (
                <div className="text-center py-16">
                  <Calendar className="w-10 h-10 text-app-text-muted mx-auto mb-3" />
                  <p className="text-app-text-secondary">No slots yet</p>
                </div>
              ) : (
                <>
                  {openSlots.map((slot: any) => (
                    <SlotCard key={slot.id} slot={slot} isOpen />
                  ))}
                  {otherSlots.map((slot: any) => (
                    <SlotCard key={slot.id} slot={slot} isOpen={false} />
                  ))}
                </>
              )}
            </div>
          )}

          {/* Details tab */}
          {activeTab === 'details' && (
            <div className="space-y-6">
              <DetailSection label="Dietary restrictions">
                {(data.dietary_restrictions || []).length > 0 ? (
                  <div className="flex flex-wrap gap-2">
                    {data.dietary_restrictions.map((r: string, i: number) => (
                      <span
                        key={i}
                        className="px-2.5 py-1 rounded-full text-xs font-medium bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300 capitalize"
                      >
                        {r.replace(/_/g, ' ')}
                      </span>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-app-text-muted">None specified</p>
                )}
              </DetailSection>

              <DetailSection label="Household size">
                <p className="text-sm text-app-text">{data.household_size || 'Not specified'}</p>
              </DetailSection>

              <DetailSection label="Drop-off window">
                <p className="text-sm text-app-text">
                  {data.preferred_dropoff_window?.start_time
                    ? `${data.preferred_dropoff_window.start_time}${data.preferred_dropoff_window.end_time ? ' - ' + data.preferred_dropoff_window.end_time : '+'}`
                    : 'Not specified'}
                </p>
              </DetailSection>

              <DetailSection label="Contactless">
                <p className="text-sm text-app-text">{data.contactless_preferred ? 'Yes' : 'No'}</p>
              </DetailSection>

              {/* Address (privacy-gated) */}
              {viewerLevel !== 'viewer' && data.address && (
                <DetailSection label="Address">
                  <p className="text-sm text-app-text flex items-center gap-1">
                    <MapPin className="w-4 h-4 text-app-text-muted" />
                    {data.address.address}
                    {data.address.unit_number ? ` ${data.address.unit_number}` : ''},{' '}
                    {data.address.city}, {data.address.state} {data.address.zip_code}
                  </p>
                </DetailSection>
              )}
              {viewerLevel === 'viewer' && data.coarse_location && (
                <DetailSection label="Location">
                  <p className="text-sm text-app-text">
                    {data.coarse_location.city}, {data.coarse_location.state}{' '}
                    {data.coarse_location.zip_code}
                  </p>
                  <p className="text-xs text-app-text-muted italic mt-1">
                    Exact address will appear after you sign up.
                  </p>
                </DetailSection>
              )}

              {viewerLevel !== 'viewer' && data.delivery_instructions && (
                <DetailSection label="Delivery instructions">
                  <p className="text-sm text-app-text">{data.delivery_instructions}</p>
                </DetailSection>
              )}
            </div>
          )}

          {/* Updates tab */}
          {activeTab === 'updates' && (
            <div className="space-y-4">
              {updates.length === 0 ? (
                <div className="text-center py-16">
                  <MessageSquare className="w-10 h-10 text-app-text-muted mx-auto mb-3" />
                  <p className="text-app-text-secondary">No updates yet</p>
                </div>
              ) : (
                updates.map((u: any) => (
                  <div
                    key={u.id}
                    className="bg-app-surface border border-app-border rounded-xl p-4"
                  >
                    <div className="flex justify-between items-center mb-2">
                      <span className="text-sm font-semibold text-app-text">
                        {u.author?.name || 'Organizer'}
                      </span>
                      <span className="text-xs text-app-text-muted">
                        {formatTimeAgo(u.created_at)}
                      </span>
                    </div>
                    <p className="text-sm text-app-text leading-relaxed">{u.body}</p>
                  </div>
                ))
              )}
            </div>
          )}
        </div>

        {/* ── Right Column: Organizer Panel ── */}
        {isOrganizer && (
          <div className="lg:col-span-3 space-y-4">
            <div className="bg-app-surface border border-app-border rounded-xl p-5">
              <h3 className="text-sm font-semibold text-app-text mb-4">Quick Stats</h3>
              <div className="grid grid-cols-2 gap-3">
                <StatBox label="Total slots" value={slots.length} />
                <StatBox label="Open" value={openSlots.length} />
                <StatBox
                  label="Filled"
                  value={
                    slots.filter((s: any) => s.status === 'full' || s.status === 'completed').length
                  }
                />
                <StatBox
                  label="Helpers"
                  value={
                    new Set(slots.filter((s: any) => s.filled_count > 0).map((s: any) => s.id)).size
                  }
                />
              </div>
              <div className="mt-3">
                <span
                  className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium ${statusBadgeClasses(data.status)}`}
                >
                  {data.status}
                </span>
              </div>
            </div>

            <button
              onClick={() => router.push(`/app/support-trains/${id}/manage`)}
              className="w-full flex items-center justify-center gap-2 px-4 py-3 bg-app-surface border border-app-border rounded-xl text-sm font-medium text-app-text hover:bg-app-surface-sunken transition"
            >
              <Settings className="w-4 h-4" />
              Manage Train
            </button>

            <button
              onClick={() => router.push(`/app/support-trains/${id}/calendar`)}
              className="w-full flex items-center justify-center gap-2 px-4 py-3 bg-app-surface border border-app-border rounded-xl text-sm font-medium text-app-text hover:bg-app-surface-sunken transition"
            >
              <Calendar className="w-4 h-4" />
              Calendar View
            </button>

            {/* Organizers */}
            <div className="bg-app-surface border border-app-border rounded-xl p-5">
              <h3 className="text-sm font-semibold text-app-text mb-3">Organizers</h3>
              {(data.organizers || []).map((o: any) => (
                <div key={o.id} className="flex items-center gap-2 py-1.5">
                  <div className="w-6 h-6 rounded-full bg-primary-100 dark:bg-primary-900/30 flex items-center justify-center text-xs font-semibold text-primary-600">
                    {(o.user?.name || '?')[0]}
                  </div>
                  <span className="text-sm text-app-text flex-1 truncate">
                    {o.user?.name || 'Organizer'}
                  </span>
                  <span className="text-xs text-app-text-muted capitalize">{o.role}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ─── Sub-components ─────────────────────────────────────────────────────

function SlotCard({ slot, isOpen }: { slot: any; isOpen: boolean }) {
  const date = new Date(slot.slot_date + 'T00:00:00Z');
  const dateStr = date.toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  });
  const ModeIcon =
    slot.support_mode === 'meal'
      ? ChefHat
      : slot.support_mode === 'groceries'
        ? ShoppingCart
        : Truck;

  return (
    <div
      className={`flex items-center gap-4 p-4 rounded-xl border transition ${
        isOpen
          ? 'border-app-border bg-app-surface hover:border-primary-300 dark:hover:border-primary-700 cursor-pointer'
          : 'border-app-border/50 bg-app-surface-sunken opacity-60'
      }`}
    >
      <div
        className={`w-10 h-10 rounded-lg flex items-center justify-center ${isOpen ? 'bg-primary-50 dark:bg-primary-950/30' : 'bg-slate-100 dark:bg-slate-800'}`}
      >
        <ModeIcon className={`w-5 h-5 ${isOpen ? 'text-primary-600' : 'text-app-text-muted'}`} />
      </div>
      <div className="flex-1 min-w-0">
        <p className={`text-sm font-semibold ${isOpen ? 'text-app-text' : 'text-app-text-muted'}`}>
          {slot.slot_label} — {dateStr}
        </p>
        {slot.start_time && (
          <p className="text-xs text-app-text-secondary mt-0.5">
            {slot.start_time}
            {slot.end_time ? ` - ${slot.end_time}` : '+'}
          </p>
        )}
      </div>
      {isOpen ? (
        <span className="px-3 py-1.5 bg-primary-600 text-white text-xs font-medium rounded-lg">
          Sign up
        </span>
      ) : (
        <span className="text-xs text-app-text-muted capitalize">{slot.status}</span>
      )}
    </div>
  );
}

function DetailSection({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs font-semibold text-app-text-muted uppercase tracking-wider mb-2">
        {label}
      </p>
      {children}
    </div>
  );
}

function ModeBadge({ icon: Icon, label }: { icon: any; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-app-surface-sunken text-app-text-secondary">
      <Icon className="w-3.5 h-3.5" />
      {label}
    </span>
  );
}

function StatBox({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="bg-app-surface-sunken rounded-lg p-3 text-center">
      <p className="text-xl font-bold text-app-text">{value}</p>
      <p className="text-xs text-app-text-muted mt-0.5">{label}</p>
    </div>
  );
}

function statusBadgeClasses(status: string): string {
  switch (status) {
    case 'draft':
      return 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300';
    case 'published':
      return 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-200';
    case 'active':
      return 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-200';
    case 'paused':
      return 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-200';
    case 'completed':
      return 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400';
    default:
      return 'bg-slate-100 text-slate-600';
  }
}

function formatTimeAgo(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  if (diffMs < 60000) return 'just now';
  if (diffMs < 3600000) return `${Math.floor(diffMs / 60000)}m ago`;
  if (diffMs < 86400000) return `${Math.floor(diffMs / 3600000)}h ago`;
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}
