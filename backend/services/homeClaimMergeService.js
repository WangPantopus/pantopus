const supabaseAdmin = require('../config/supabaseAdmin');
const logger = require('../utils/logger');
const { mapLegacyRole, writeAuditLog } = require('../utils/homePermissions');
const homeClaimCompatService = require('./homeClaimCompatService');
const homeClaimRoutingService = require('./homeClaimRoutingService');

function buildHttpError(status, message, code) {
  const error = new Error(message);
  error.status = status;
  if (code) {
    error.code = code;
  }
  return error;
}

async function getLatestUserHomeClaim(homeId, userId) {
  const { data: claims, error } = await supabaseAdmin
    .from('HomeOwnershipClaim')
    .select('id, state, claim_phase_v2, claimant_user_id, created_at')
    .eq('home_id', homeId)
    .eq('claimant_user_id', userId)
    .order('created_at', { ascending: false })
    .limit(1);

  if (error) throw error;
  return claims?.[0] || null;
}

async function claimHasVerifiedIdentity(claim) {
  if (claim.identity_status === 'verified') {
    return true;
  }

  const { data: evidenceRows, error } = await supabaseAdmin
    .from('HomeVerificationEvidence')
    .select('id, evidence_type, status')
    .eq('claim_id', claim.id)
    .eq('evidence_type', 'idv');

  if (error) throw error;

  return (evidenceRows || []).some((evidence) => evidence.status === 'verified');
}

async function acceptClaimMerge({
  homeId,
  claimId,
  userId,
  invite,
}) {
  if (!invite || invite.home_id !== homeId || invite.proposed_preset_key !== `claim_merge:${claimId}`) {
    throw buildHttpError(404, 'No eligible household invitation was found for this claim');
  }

  const { data: claim, error: claimError } = await supabaseAdmin
    .from('HomeOwnershipClaim')
    .select('id, home_id, claimant_user_id, state, claim_phase_v2, terminal_reason, challenge_state, routing_classification, identity_status, merged_into_claim_id')
    .eq('id', claimId)
    .eq('home_id', homeId)
    .maybeSingle();

  if (claimError) throw claimError;
  if (!claim) {
    throw buildHttpError(404, 'Claim not found');
  }

  if (claim.claimant_user_id !== userId) {
    throw buildHttpError(403, 'Not authorized');
  }

  if (!homeClaimRoutingService.isClaimActiveRecord(claim)) {
    throw buildHttpError(400, 'Claim is not eligible for merge acceptance');
  }

  const hasVerifiedIdentity = await claimHasVerifiedIdentity(claim);
  if (!hasVerifiedIdentity) {
    throw buildHttpError(
      409,
      'Identity confirmation is required before this claim can be merged into the household',
      'IDENTITY_CONFIRMATION_REQUIRED',
    );
  }

  const occupancyAttachService = require('./occupancyAttachService');
  const attachResult = await occupancyAttachService.attach({
    homeId,
    userId,
    method: 'owner_bootstrap',
    claimType: 'member',
    roleOverride: invite.proposed_role_base || mapLegacyRole(invite.proposed_role || 'member'),
    actorId: userId,
    metadata: { source: 'claim_merge_accept', invite_id: invite.id, claim_id: claimId },
  });

  if (!attachResult.success && attachResult.status !== 'already_attached') {
    logger.error('Failed to attach claimant during merge acceptance', {
      error: attachResult.error,
      homeId,
      claimId,
      inviteId: invite.id,
    });
    throw buildHttpError(500, 'Failed to accept household merge');
  }

  const mergedIntoClaim = await getLatestUserHomeClaim(homeId, invite.invited_by);

  const { error: claimUpdateError } = await supabaseAdmin
    .from('HomeOwnershipClaim')
    .update({
      state: 'approved',
      claim_phase_v2: 'merged_into_household',
      terminal_reason: 'merged_via_invite',
      challenge_state: 'none',
      routing_classification: 'merge_candidate',
      merged_into_claim_id: mergedIntoClaim?.id || null,
      updated_at: new Date().toISOString(),
    })
    .eq('id', claimId);

  if (claimUpdateError) throw claimUpdateError;

  await supabaseAdmin
    .from('HomeInvite')
    .update({ status: 'accepted' })
    .eq('id', invite.id);

  const homeResolutionState = await homeClaimCompatService.recalculateHouseholdResolutionState(homeId);

  const notificationService = require('./notificationService');
  try {
    const [{ data: claimant }, { data: home }] = await Promise.all([
      supabaseAdmin.from('User').select('name, username, first_name').eq('id', userId).maybeSingle(),
      supabaseAdmin.from('Home').select('name, address').eq('id', homeId).maybeSingle(),
    ]);

    await notificationService.notifyHomeInviteAccepted({
      inviterUserId: invite.invited_by,
      accepterName: claimant?.name || claimant?.first_name || claimant?.username || 'Someone',
      homeName: home?.name || home?.address || 'A home',
      homeId,
    });
  } catch (notificationError) {
    logger.warn('Failed to notify inviter about accepted merge', {
      error: notificationError.message,
      homeId,
      claimId,
      inviteId: invite.id,
    });
  }

  await writeAuditLog(homeId, userId, 'OWNERSHIP_CLAIM_MERGED', 'HomeOwnershipClaim', claimId, {
    invite_id: invite.id,
    merged_into_claim_id: mergedIntoClaim?.id || null,
  });

  return {
    claimId,
    mergedIntoClaimId: mergedIntoClaim?.id || null,
    homeResolutionState,
    occupancy: attachResult.occupancy || null,
  };
}

module.exports = {
  acceptClaimMerge,
};
