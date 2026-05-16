'use client';

// T5.4.3 / P16 — Review claims (web only).
//
// Per `docs/mobile/pantopus-t5-notes.md` §1.8 the screen ships on web
// only — there is no admin role on mobile yet. The list shape mirrors
// `more-designed-pages/reviewclaims-frames.jsx`: three tabs (Pending /
// Approved / Rejected), a queue banner above the Pending list, and one
// row per claim ending with a primary "Review claim" footer button.
// Approve / Reject / Request-info happen in the detail panel that
// slides over after a row tap (matches the existing flow against
// `POST /api/admin/claims/:claimId/review`).

/* eslint-disable @next/next/no-img-element */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  CheckCheck,
  CheckCircle,
  Clock,
  FileText,
  Gavel,
  HelpCircle,
  Hourglass,
  Image as ImageIcon,
  MapPin,
  Paperclip,
  Sparkles,
  User,
  X,
  XCircle,
} from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import { formatTimeAgo } from '@pantopus/ui-utils';
import { toast } from '@/components/ui/toast-store';
import { confirmStore } from '@/components/ui/confirm-store';
import { queryKeys } from '@/lib/query-keys';
import ListOfRowsShell from '@/components/list-of-rows/ListOfRowsShell';
import type {
  ListOfRowsState,
  RowChip,
  RowFooterAction,
  RowModel,
  StatusChipVariant,
} from '@/components/list-of-rows/types';

type Bucket = api.admin.AdminClaimBucket;
type AdminClaim = api.admin.AdminClaim;
type EvidenceItem = api.admin.ClaimEvidence;
type ClaimDetail = api.admin.ClaimDetail;

const METHOD_LABELS: Record<string, string> = {
  doc_upload: 'Document Upload',
  escrow_agent: 'Escrow/Title Agent',
  property_data_match: 'ID Verification',
  invite: 'Invited',
  vouch: 'Vouched',
  landlord_portal: 'Landlord Portal',
};

const EVIDENCE_LABELS: Record<string, string> = {
  deed: 'Deed',
  closing_disclosure: 'Closing Disclosure',
  tax_bill: 'Tax Bill',
  utility_bill: 'Utility Bill',
  lease: 'Lease Agreement',
  escrow_attestation: 'Escrow Attestation',
  title_match: 'Title Match',
  idv: 'ID Verification',
};

// Avatar gradient palette — one per claimant, hashed by user id so the
// same person reads the same colour across loads.
const AVATAR_GRADIENTS: Array<{ start: string; end: string }> = [
  { start: '#0ea5e9', end: '#0369a1' },
  { start: '#dc2626', end: '#991b1b' },
  { start: '#f97316', end: '#c2410c' },
  { start: '#16a34a', end: '#15803d' },
  { start: '#7c3aed', end: '#5b21b6' },
  { start: '#0891b2', end: '#155e75' },
];

function hashIndex(seed: string, modulo: number): number {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) {
    hash = (hash * 31 + seed.charCodeAt(i)) | 0;
  }
  return Math.abs(hash) % modulo;
}

function gradientFor(seed: string) {
  return AVATAR_GRADIENTS[hashIndex(seed, AVATAR_GRADIENTS.length)];
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function formatAddress(claim: AdminClaim): string {
  if (!claim.home) return 'Unknown address';
  const parts = [claim.home.name || claim.home.address, claim.home.city, claim.home.state].filter(Boolean);
  return parts.join(', ') || 'Unknown address';
}

function formatOldestAge(seconds: number | null): string {
  if (seconds == null) return 'no claims';
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min`;
  if (seconds < 86_400) return `${Math.floor(seconds / 3600)}h`;
  return `${Math.floor(seconds / 86_400)}d`;
}

interface ChipStyle {
  text: string;
  icon: typeof Sparkles;
  variant: StatusChipVariant;
}

function statusChipFor(claim: AdminClaim, bucket: Bucket): ChipStyle {
  if (bucket === 'approved') {
    return { text: 'Approved', icon: CheckCircle, variant: 'success' };
  }
  if (bucket === 'rejected') {
    return { text: 'Rejected', icon: XCircle, variant: 'error' };
  }
  // Pending bucket — derive the triage chip from state + age.
  if (claim.state === 'disputed') {
    return { text: 'Conflict', icon: AlertTriangle, variant: 'error' };
  }
  if (claim.state === 'needs_more_info') {
    return { text: 'Awaiting docs', icon: Hourglass, variant: 'neutral' };
  }
  const ageMs = Date.now() - new Date(claim.created_at).getTime();
  const ageDays = Math.floor(ageMs / 86_400_000);
  if (ageDays >= 7) {
    return { text: `Aging · ${ageDays}d`, icon: Clock, variant: 'warning' };
  }
  return { text: 'New', icon: Sparkles, variant: 'personal' };
}

function evidenceChipFor(claim: AdminClaim): RowChip {
  const n = claim.evidence_count;
  return {
    text: `${n} doc${n === 1 ? '' : 's'}`,
    icon: Paperclip,
    tint: { kind: 'status', variant: 'neutral' },
  };
}

function makeRow(
  claim: AdminClaim,
  bucket: Bucket,
  onOpen: (claim: AdminClaim) => void,
): RowModel {
  const chip = statusChipFor(claim, bucket);
  const gradient = gradientFor(claim.claimant_user_id || claim.id);
  const claimantName = claim.claimant?.name || claim.claimant?.username || 'Unknown claimant';
  const address = formatAddress(claim);

  const footerAction: RowFooterAction = {
    title: 'Review claim',
    icon: ArrowRight,
    variant: 'primary',
    onClick: () => onOpen(claim),
  };

  return {
    id: claim.id,
    title: claimantName,
    subtitle: address,
    template: 'statusChip',
    leading: {
      kind: 'avatarWithBadge',
      name: claimantName,
      background: { kind: 'gradient', gradient },
      size: 'medium',
    },
    trailing: { kind: 'none' },
    onTap: () => onOpen(claim),
    chips: [
      { text: chip.text, icon: chip.icon, tint: { kind: 'status', variant: chip.variant } },
      evidenceChipFor(claim),
    ],
    timeMeta: formatTimeAgo(claim.created_at),
    footer: { actions: [footerAction] },
  };
}

export default function AdminReviewClaimsPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [bucket, setBucket] = useState<Bucket>('pending');

  // Detail overlay state (right side on desktop, full overlay on mobile).
  const [selectedClaim, setSelectedClaim] = useState<AdminClaim | null>(null);
  const [claimDetail, setClaimDetail] = useState<ClaimDetail | null>(null);
  const [comparison, setComparison] = useState<api.homeOwnership.OwnershipClaimComparison | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [reviewingAction, setReviewingAction] = useState<string | null>(null);
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [rejectNote, setRejectNote] = useState('');
  const [evidenceViewerUrl, setEvidenceViewerUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const countsQuery = useQuery({
    queryKey: queryKeys.adminClaimCounts(),
    queryFn: () => api.admin.getClaimCounts(),
    staleTime: 30_000,
  });

  const claimsQuery = useQuery({
    queryKey: queryKeys.adminClaims(bucket),
    queryFn: () => api.admin.getClaimsByBucket(bucket),
    staleTime: 30_000,
  });

  // 403 from either query means the signed-in user isn't an admin.
  useEffect(() => {
    const err = (claimsQuery.error ?? countsQuery.error) as { statusCode?: number; status?: number } | null;
    if (err && (err.statusCode === 403 || err.status === 403)) {
      toast.error('You do not have admin access.');
      router.back();
    }
  }, [claimsQuery.error, countsQuery.error, router]);

  const claims = useMemo<AdminClaim[]>(
    () => claimsQuery.data?.claims ?? [],
    [claimsQuery.data],
  );
  const oldestAgeSeconds = claimsQuery.data?.oldest_age_seconds ?? null;
  const counts = useMemo(
    () => countsQuery.data ?? { pending: 0, approved: 0, rejected: 0 },
    [countsQuery.data],
  );

  const openClaimDetail = useCallback(async (claim: AdminClaim) => {
    setSelectedClaim(claim);
    setDetailLoading(true);
    setClaimDetail(null);
    setComparison(null);
    try {
      const detail = await api.admin.getClaimDetail(claim.id);
      setClaimDetail(detail);
      try {
        const compare = await api.homeOwnership.getOwnershipClaimComparison(claim.home_id);
        setComparison(compare);
      } catch {
        setComparison(null);
      }
    } catch {
      toast.error('Failed to load claim details');
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const closeDetail = useCallback(() => {
    setSelectedClaim(null);
    setClaimDetail(null);
    setComparison(null);
    setShowRejectModal(false);
    setRejectNote('');
  }, []);

  const handleReview = useCallback(
    async (claimId: string, action: 'approve' | 'reject' | 'request_more_info', note?: string) => {
      setReviewingAction(action);
      try {
        await api.admin.reviewClaim(claimId, { action, note });
        toast.success(
          action === 'approve' ? 'Claim approved. User has been verified.'
            : action === 'reject' ? 'Claim rejected. User has been notified.'
              : 'More info requested. User has been notified.',
        );
        closeDetail();
        // Refresh the active list + the tab counts so the row leaves the
        // current bucket and the next bucket's badge updates.
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ['admin', 'claims'] }),
        ]);
      } catch (err: unknown) {
        const message = err && typeof err === 'object' && 'message' in err
          ? String((err as { message?: string }).message ?? 'Failed to review claim')
          : 'Failed to review claim';
        toast.error(message);
      } finally {
        setReviewingAction(null);
      }
    },
    [closeDetail, queryClient],
  );

  const handleApprove = useCallback(async (claimId: string) => {
    const ok = await confirmStore.open({
      title: 'Approve Claim',
      description: 'This will verify the user and grant them access. Are you sure?',
      confirmLabel: 'Approve',
    });
    if (ok) handleReview(claimId, 'approve');
  }, [handleReview]);

  const tabs = useMemo(
    () => [
      { id: 'pending', label: 'Pending', count: counts.pending },
      { id: 'approved', label: 'Approved', count: counts.approved },
      { id: 'rejected', label: 'Rejected', count: counts.rejected },
    ],
    [counts],
  );

  const state = useMemo<ListOfRowsState>(() => {
    if (claimsQuery.isPending) return { kind: 'loading' };
    if (claimsQuery.isError) {
      return {
        kind: 'error',
        message: claimsQuery.error?.message ?? "Couldn't load claims.",
      };
    }
    if (claims.length === 0) {
      if (bucket === 'pending') {
        return {
          kind: 'empty',
          config: {
            icon: CheckCheck,
            headline: 'No claims to review',
            subcopy:
              "You're all caught up. New ownership claims will appear here when neighbors submit address verification.",
            ctaTitle: 'View approved',
            onCta: () => setBucket('approved'),
          },
        };
      }
      return {
        kind: 'empty',
        config: {
          icon: bucket === 'approved' ? CheckCircle : XCircle,
          headline: bucket === 'approved' ? 'No approved claims yet' : 'No rejected claims',
          subcopy:
            bucket === 'approved'
              ? 'Approved ownership claims will appear here once the team works through the queue.'
              : 'Rejected claims will appear here. Rejecting a claim notifies the claimant.',
        },
      };
    }
    return {
      kind: 'loaded',
      sections: [
        {
          id: 'all',
          rows: claims.map((claim) => makeRow(claim, bucket, openClaimDetail)),
        },
      ],
    };
  }, [
    claimsQuery.isPending,
    claimsQuery.isError,
    claimsQuery.error,
    claims,
    bucket,
    openClaimDetail,
  ]);

  const banner = useMemo(() => {
    if (bucket !== 'pending' || claims.length === 0) return undefined;
    return {
      icon: Gavel,
      title: `${counts.pending} ${counts.pending === 1 ? 'claim' : 'claims'} awaiting review`,
      subtitle: `Oldest in queue: ${formatOldestAge(oldestAgeSeconds)}`,
    };
  }, [bucket, claims.length, counts.pending, oldestAgeSeconds]);

  return (
    <>
      <ListOfRowsShell
        title="Review claims"
        state={state}
        onRefresh={() => {
          claimsQuery.refetch();
          countsQuery.refetch();
        }}
        tabs={tabs}
        selectedTab={bucket}
        onTabChange={(id) => setBucket(id as Bucket)}
        banner={banner}
      />

      {/* Detail overlay — full-screen on mobile, anchored to the right on desktop. */}
      {selectedClaim && (
        <div
          className="fixed inset-0 z-40 flex bg-black/40"
          onClick={closeDetail}
          role="presentation"
        >
          <div
            className="ml-auto w-full max-w-xl h-full bg-app-surface flex flex-col shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-5 py-3 border-b border-app-border flex-shrink-0">
              <button
                onClick={closeDetail}
                className="lg:hidden p-1.5 hover:bg-app-hover rounded-lg"
                aria-label="Back"
              >
                <ArrowLeft className="w-5 h-5" />
              </button>
              <h2 className="text-base font-semibold text-app-text">Review Claim</h2>
              <button
                onClick={closeDetail}
                className="hidden lg:block p-1.5 hover:bg-app-hover rounded-lg"
                aria-label="Close"
              >
                <X className="w-5 h-5 text-app-text-muted" />
              </button>
            </div>

            {detailLoading ? (
              <div className="flex-1 flex items-center justify-center">
                <div className="animate-spin h-8 w-8 border-3 border-violet-600 border-t-transparent rounded-full" />
              </div>
            ) : claimDetail ? (
              <div className="flex-1 overflow-y-auto p-5 space-y-5">
                {/* Home */}
                <section>
                  <h3 className="text-xs font-bold text-app-text-secondary uppercase tracking-wider mb-2">Home</h3>
                  <div className="flex items-center gap-3 bg-app-surface border border-app-border rounded-xl p-4">
                    <MapPin className="w-5 h-5 text-violet-600 flex-shrink-0" />
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-app-text truncate">
                        {claimDetail.home?.name || claimDetail.home?.address}
                      </p>
                      <p className="text-xs text-app-text-secondary">
                        {[claimDetail.home?.address, claimDetail.home?.city, claimDetail.home?.state, claimDetail.home?.zipcode]
                          .filter(Boolean)
                          .join(', ')}
                      </p>
                    </div>
                  </div>
                </section>

                {/* Claimant */}
                <section>
                  <h3 className="text-xs font-bold text-app-text-secondary uppercase tracking-wider mb-2">Claimant</h3>
                  <div className="flex items-center gap-3 bg-app-surface border border-app-border rounded-xl p-4">
                    {claimDetail.claimant?.profile_picture_url ? (
                      <img src={claimDetail.claimant.profile_picture_url} alt="" className="w-10 h-10 rounded-full" />
                    ) : (
                      <div className="w-10 h-10 rounded-full bg-app-surface-sunken flex items-center justify-center">
                        <User className="w-5 h-5 text-app-text-muted" />
                      </div>
                    )}
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-app-text">
                        {claimDetail.claimant?.name || claimDetail.claimant?.username || 'Unknown'}
                      </p>
                      <p className="text-xs text-app-text-secondary">{claimDetail.claimant?.email}</p>
                      {claimDetail.claimant?.created_at && (
                        <p className="text-[11px] text-app-text-muted mt-0.5">
                          Account created: {formatDate(claimDetail.claimant.created_at)}
                        </p>
                      )}
                    </div>
                  </div>
                </section>

                {/* Claim details grid */}
                <section>
                  <h3 className="text-xs font-bold text-app-text-secondary uppercase tracking-wider mb-2">Claim Details</h3>
                  <div className="grid grid-cols-2 gap-2">
                    {[
                      {
                        label: 'Type',
                        value:
                          claimDetail.claim?.claim_type === 'owner' ? 'Ownership'
                            : claimDetail.claim?.claim_type === 'resident' ? 'Residency'
                              : claimDetail.claim?.claim_type,
                      },
                      { label: 'Method', value: METHOD_LABELS[claimDetail.claim?.method] || claimDetail.claim?.method },
                      {
                        label: 'Risk Score',
                        value: claimDetail.claim?.risk_score ?? 'N/A',
                        danger: (claimDetail.claim?.risk_score || 0) > 50,
                      },
                      { label: 'Submitted', value: formatDate(claimDetail.claim?.created_at) },
                    ].map((item) => (
                      <div key={item.label} className="bg-app-surface border border-app-border rounded-xl p-3">
                        <p className="text-[11px] text-app-text-muted font-semibold mb-1">{item.label}</p>
                        <p
                          className={`text-sm font-semibold ${
                            (item as { danger?: boolean }).danger ? 'text-red-600' : 'text-app-text'
                          }`}
                        >
                          {item.value}
                        </p>
                      </div>
                    ))}
                  </div>
                </section>

                {/* Household comparison */}
                {comparison && (
                  <section>
                    <h3 className="text-xs font-bold text-app-text-secondary uppercase tracking-wider mb-2">Household Comparison</h3>
                    <div className="space-y-3">
                      <div className="rounded-xl border border-app-border bg-app-surface p-4">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-[10px] font-semibold uppercase px-2 py-1 rounded bg-violet-100 text-violet-700">
                            {comparison.household_resolution_state.replace(/_/g, ' ')}
                          </span>
                          <span className="text-[10px] font-semibold uppercase px-2 py-1 rounded bg-slate-100 text-slate-700">
                            Security: {comparison.home.security_state.replace(/_/g, ' ')}
                          </span>
                          <span className="text-[10px] font-semibold uppercase px-2 py-1 rounded bg-amber-100 text-amber-700">
                            Incumbent: {comparison.incumbent.challenge_state.replace(/_/g, ' ')}
                          </span>
                        </div>
                        {comparison.incumbent.owners.length > 0 ? (
                          <div className="mt-3 space-y-2">
                            {comparison.incumbent.owners.map((owner) => (
                              <div key={owner.id} className="rounded-lg bg-app-surface-sunken px-3 py-2">
                                <p className="text-sm font-semibold text-app-text">
                                  {owner.user?.name || owner.user?.username || owner.subject_id}
                                </p>
                                <p className="text-xs text-app-text-secondary">
                                  {owner.owner_status} {owner.verification_tier ? `· ${owner.verification_tier}` : ''}
                                  {owner.is_primary_owner ? ' · primary' : ''}
                                </p>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <p className="mt-3 text-sm text-app-text-secondary">No verified incumbent owners.</p>
                        )}
                      </div>

                      <div className="space-y-3">
                        {comparison.claims.map((compareClaim) => (
                          <div
                            key={compareClaim.id}
                            className={`rounded-xl border p-4 ${
                              compareClaim.id === claimDetail.claim?.id
                                ? 'border-violet-500 bg-violet-50/40'
                                : 'border-app-border bg-app-surface'
                            }`}
                          >
                            <div className="flex items-start justify-between gap-3">
                              <div>
                                <p className="text-sm font-semibold text-app-text">
                                  {compareClaim.claimant?.name || compareClaim.claimant?.username || compareClaim.claimant_user_id}
                                </p>
                                <p className="text-xs text-app-text-secondary">
                                  {compareClaim.claim_type} · {compareClaim.method}
                                </p>
                              </div>
                              {compareClaim.id === claimDetail.claim?.id ? (
                                <span className="text-[10px] font-semibold uppercase px-2 py-1 rounded bg-violet-600 text-white">
                                  selected
                                </span>
                              ) : null}
                            </div>

                            <div className="mt-3 flex flex-wrap gap-2">
                              <span className="text-[10px] font-semibold uppercase px-2 py-1 rounded bg-amber-100 text-amber-700">
                                {(compareClaim.claim_phase_v2 || compareClaim.state || 'unknown').replace(/_/g, ' ')}
                              </span>
                              {compareClaim.routing_classification ? (
                                <span className="text-[10px] font-semibold uppercase px-2 py-1 rounded bg-blue-100 text-blue-700">
                                  {compareClaim.routing_classification.replace(/_/g, ' ')}
                                </span>
                              ) : null}
                              {compareClaim.claim_strength ? (
                                <span className="text-[10px] font-semibold uppercase px-2 py-1 rounded bg-slate-100 text-slate-700">
                                  {compareClaim.claim_strength.replace(/_/g, ' ')}
                                </span>
                              ) : null}
                              {compareClaim.challenge_state && compareClaim.challenge_state !== 'none' ? (
                                <span className="text-[10px] font-semibold uppercase px-2 py-1 rounded bg-red-100 text-red-700">
                                  {compareClaim.challenge_state.replace(/_/g, ' ')}
                                </span>
                              ) : null}
                            </div>

                            <div className="mt-3 space-y-2">
                              {compareClaim.evidence.length > 0 ? (
                                compareClaim.evidence.map((ev) => (
                                  <div key={ev.id} className="flex items-center justify-between rounded-lg bg-app-surface-sunken px-3 py-2">
                                    <div>
                                      <p className="text-xs font-semibold text-app-text">
                                        {EVIDENCE_LABELS[ev.evidence_type] || ev.evidence_type}
                                      </p>
                                      <p className="text-[11px] text-app-text-secondary">
                                        {ev.provider} · {ev.status}
                                        {ev.confidence_level ? ` · ${ev.confidence_level}` : ''}
                                      </p>
                                    </div>
                                  </div>
                                ))
                              ) : (
                                <p className="text-xs text-app-text-secondary">No evidence attached.</p>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </section>
                )}

                {/* Evidence */}
                <section>
                  <h3 className="text-xs font-bold text-app-text-secondary uppercase tracking-wider mb-2">
                    Evidence ({claimDetail.evidence?.length || 0})
                  </h3>
                  {!claimDetail.evidence || claimDetail.evidence.length === 0 ? (
                    <div className="flex items-center gap-2 p-3 bg-amber-50 border border-amber-200 rounded-xl">
                      <AlertCircle className="w-5 h-5 text-amber-600 flex-shrink-0" />
                      <p className="text-sm text-amber-800">No documents uploaded yet</p>
                    </div>
                  ) : (
                    <div className="space-y-2">
                      {claimDetail.evidence.map((ev: EvidenceItem) => (
                        <div
                          key={ev.id}
                          className="flex items-center gap-3 bg-app-surface border border-app-border rounded-xl p-3"
                        >
                          <div className="w-10 h-10 rounded-lg bg-violet-100 flex items-center justify-center flex-shrink-0">
                            {ev.mime_type?.startsWith('image/') ? (
                              <ImageIcon className="w-5 h-5 text-violet-600" />
                            ) : (
                              <FileText className="w-5 h-5 text-violet-600" />
                            )}
                          </div>
                          <div className="flex-1 min-w-0">
                            <p className="text-sm font-semibold text-app-text">
                              {EVIDENCE_LABELS[ev.evidence_type] || ev.evidence_type}
                            </p>
                            <p className="text-xs text-app-text-secondary truncate">{ev.file_name || 'Document'}</p>
                            {ev.file_size && (
                              <p className="text-[11px] text-app-text-muted">
                                {(ev.file_size / 1024).toFixed(0)} KB · {formatDate(ev.created_at)}
                              </p>
                            )}
                          </div>
                          {ev.file_url && (
                            <button
                              onClick={() => setEvidenceViewerUrl(ev.file_url!)}
                              className="flex items-center gap-1 px-2.5 py-1.5 bg-violet-50 text-violet-600 text-xs font-semibold rounded-lg hover:bg-violet-100 transition"
                            >
                              View
                            </button>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </section>

                {/* Actions — only on pending bucket. Approved/Rejected detail is read-only. */}
                {bucket === 'pending' && (
                  <section className="pt-4 border-t border-app-border space-y-3">
                    <button
                      onClick={() => handleApprove(claimDetail.claim.id)}
                      disabled={!!reviewingAction}
                      className="w-full flex items-center justify-center gap-2 py-3 bg-emerald-600 text-white font-bold rounded-xl hover:bg-emerald-700 disabled:opacity-50 transition"
                    >
                      {reviewingAction === 'approve' ? 'Approving...' : (
                        <>
                          <CheckCircle className="w-5 h-5" /> Approve
                        </>
                      )}
                    </button>

                    <div className="grid grid-cols-2 gap-2">
                      <button
                        onClick={() => setShowRejectModal(true)}
                        disabled={!!reviewingAction}
                        className="flex items-center justify-center gap-1.5 py-2.5 border-2 border-red-300 bg-red-50 text-red-700 font-semibold rounded-xl hover:bg-red-100 disabled:opacity-50 transition"
                      >
                        <XCircle className="w-4 h-4" /> Reject
                      </button>
                      <button
                        onClick={() => handleReview(claimDetail.claim.id, 'request_more_info', 'Please upload additional documents.')}
                        disabled={!!reviewingAction}
                        className="flex items-center justify-center gap-1.5 py-2.5 border-2 border-amber-300 bg-amber-50 text-amber-700 font-semibold rounded-xl hover:bg-amber-100 disabled:opacity-50 transition"
                      >
                        {reviewingAction === 'request_more_info' ? 'Sending...' : (
                          <>
                            <HelpCircle className="w-4 h-4" /> Request Info
                          </>
                        )}
                      </button>
                    </div>
                  </section>
                )}
              </div>
            ) : (
              <div className="flex-1 flex items-center justify-center text-app-text-secondary text-sm">
                Failed to load claim details
              </div>
            )}
          </div>
        </div>
      )}

      {/* Evidence viewer modal */}
      {evidenceViewerUrl && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center">
          <div
            className="w-full max-w-4xl mx-4 bg-app-surface rounded-xl shadow-2xl border border-app-border flex flex-col"
            style={{ height: '85vh' }}
          >
            <div className="flex items-center justify-between px-5 py-3 border-b border-app-border">
              <h3 className="text-sm font-semibold text-app-text">Document Viewer</h3>
              <button
                onClick={() => setEvidenceViewerUrl(null)}
                className="p-1 text-app-text-muted hover:text-app-text-secondary"
                aria-label="Close"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="flex-1">
              <iframe src={evidenceViewerUrl} className="w-full h-full border-0 rounded-b-xl" title="Evidence document" />
            </div>
          </div>
        </div>
      )}

      {/* Reject reason modal */}
      {showRejectModal && claimDetail && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center">
          <div className="w-full max-w-md mx-4 bg-app-surface rounded-xl shadow-2xl border border-app-border p-6">
            <h3 className="text-lg font-bold text-app-text mb-2">Reject Claim</h3>
            <p className="text-sm text-app-text-secondary mb-3">Optionally provide a reason for rejection:</p>
            <textarea
              value={rejectNote}
              onChange={(e) => setRejectNote(e.target.value)}
              rows={3}
              placeholder="e.g., Document doesn't match address..."
              className="w-full text-sm px-3 py-2 border border-app-border rounded-lg bg-app-surface text-app-text placeholder:text-app-text-muted focus:outline-none focus:ring-1 focus:ring-red-500 resize-none mb-4"
            />
            <button
              onClick={() => handleReview(claimDetail.claim.id, 'reject', rejectNote || undefined)}
              disabled={!!reviewingAction}
              className="w-full py-3 bg-red-600 text-white font-bold rounded-xl hover:bg-red-700 disabled:opacity-50 transition mb-2"
            >
              {reviewingAction === 'reject' ? 'Rejecting...' : 'Reject Claim'}
            </button>
            <button
              onClick={() => {
                setShowRejectModal(false);
                setRejectNote('');
              }}
              className="w-full py-2.5 text-app-text-secondary font-medium hover:bg-app-hover rounded-xl transition"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </>
  );
}
