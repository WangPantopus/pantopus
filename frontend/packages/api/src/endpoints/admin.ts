// ============================================================
// ADMIN ENDPOINTS — Platform admin operations
// ============================================================

import { get, post } from '../client';

export interface AdminClaim {
  id: string;
  home_id: string;
  claimant_user_id: string;
  claim_type: string;
  state: string;
  method: string;
  risk_score: number;
  created_at: string;
  updated_at: string;
  home: {
    id: string;
    address: string;
    city: string;
    state: string;
    zipcode: string;
    name: string;
  } | null;
  claimant: {
    id: string;
    username: string;
    name: string;
    email: string;
    created_at: string;
    profile_picture_url: string | null;
  } | null;
  evidence_count: number;
}

export interface ClaimEvidence {
  id: string;
  evidence_type: string;
  provider: string;
  status: string;
  storage_ref: string | null;
  file_url: string | null;
  file_name: string | null;
  file_size: number | null;
  mime_type: string | null;
  created_at: string;
}

export interface ClaimDetail {
  claim: any;
  home: any;
  claimant: any;
  evidence: ClaimEvidence[];
}

export type AdminClaimBucket = 'pending' | 'approved' | 'rejected';

export interface AdminClaimsResponse {
  claims: AdminClaim[];
  total: number;
  /** Age (in seconds) of the oldest claim in the current bucket — only
   *  populated when `bucket=pending`; null otherwise. Powers the queue
   *  banner above the Pending tab. */
  oldest_age_seconds: number | null;
}

export interface AdminClaimCounts {
  pending: number;
  approved: number;
  rejected: number;
}

/**
 * Legacy shape kept for backward compatibility — equivalent to
 * `getClaimsByBucket('pending')` without the `oldest_age_seconds` field.
 */
export async function getPendingClaims(): Promise<{ claims: AdminClaim[]; total: number }> {
  return get('/api/admin/pending-claims');
}

export async function getClaimsByBucket(
  bucket: AdminClaimBucket,
  opts: { limit?: number; offset?: number } = {},
): Promise<AdminClaimsResponse> {
  const params = new URLSearchParams({ bucket });
  if (opts.limit != null) params.set('limit', String(opts.limit));
  if (opts.offset != null) params.set('offset', String(opts.offset));
  return get(`/api/admin/claims?${params.toString()}`);
}

export async function getClaimCounts(): Promise<AdminClaimCounts> {
  return get('/api/admin/claims/counts');
}

export async function getClaimDetail(claimId: string): Promise<ClaimDetail> {
  return get(`/api/admin/claims/${claimId}`);
}

export async function reviewClaim(
  claimId: string,
  data: { action: 'approve' | 'reject' | 'request_more_info'; note?: string }
): Promise<{ message: string }> {
  return post(`/api/admin/claims/${claimId}/review`, data);
}
