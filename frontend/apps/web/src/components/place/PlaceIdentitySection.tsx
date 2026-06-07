// ============================================================
// Place — dashboard verify entry + identity group.
//
// The web mirror of the claimed (C1) and verified ProfileDashboard
// designs in docs/design/place:
//   • VerifyBanner       — the gentle sky nudge above the pulse (T3),
//                          opens the B1 prompt sheet.
//   • LockedIdentityGroup — the "Locked until you verify" Band-D cards
//                          (T3), each opening the B1 sheet.
//   • IdentityGroup       — the verified resident + residency-letter
//                          rows (T4), routing to the Identity center.
//
// The Place intelligence contract carries no identity sections yet
// (launch set is Band A), so these are derived from the resolved tier.
// Tokens only; home-green for the place, sky for the CTA.
// ============================================================

'use client';

import { useRouter } from 'next/navigation';
import {
  ShieldCheck,
  ChevronRight,
  ArrowRight,
  MessageCircle,
  BadgeCheck,
  Mailbox,
  FileText,
  Check,
} from 'lucide-react';
import { Group, LockedCard, SectionCard } from '@/components/archetypes/place';

// ── VerifyBanner — the gentle nudge above the pulse (T3) ────────
export function VerifyBanner({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full flex items-center gap-3 text-left rounded-2xl bg-primary-50 border border-primary-200 px-3.5 py-3 shadow-sm hover:bg-primary-100/60 transition-colors"
    >
      <span className="inline-flex items-center justify-center shrink-0 w-[38px] h-[38px] rounded-xl bg-primary-100 text-primary-600">
        <ShieldCheck size={20} strokeWidth={2} />
      </span>
      <span className="flex-1 min-w-0">
        <span className="block text-[14.5px] font-semibold text-primary-800 leading-5 -tracking-[0.01em]">
          Verify your address to message neighbors and get your badge.
        </span>
        <span className="inline-flex items-center gap-1 mt-1 text-[13.5px] font-semibold text-primary-600">
          Verify address
          <ArrowRight size={14} strokeWidth={2.5} />
        </span>
      </span>
      <ChevronRight size={18} strokeWidth={2.25} className="shrink-0 text-primary-400" />
    </button>
  );
}

// ── LockedIdentityGroup — Band-D shown locked to motivate B1 (T3) ──
export function LockedIdentityGroup({ onVerify }: { onVerify: () => void }) {
  return (
    <Group label="Locked until you verify">
      <LockedCard
        icon={MessageCircle}
        title="Neighbor messaging"
        reason="Verify your address to message neighbors."
        cta="Verify address"
        onCta={onVerify}
      />
      <LockedCard
        icon={BadgeCheck}
        title="Verified badge"
        reason="Verify your address to get your verified badge."
        cta="Verify address"
        onCta={onVerify}
      />
      <LockedCard
        icon={Mailbox}
        title="Your mailbox"
        reason="Verify your address for your mailbox — packages, civic notices, and permits."
        cta="Verify address"
        onCta={onVerify}
      />
    </Group>
  );
}

// ── IdentityGroup — the now-available Band-D rows (T4) ─────────
export function IdentityGroup() {
  const router = useRouter();
  const goIdentity = () => router.push('/app/identity');
  return (
    <Group label="Identity">
      <SectionCard
        icon={BadgeCheck}
        title="Identity"
        chip={{ label: 'Verified', variant: 'success', icon: Check }}
        inline
        onClick={goIdentity}
      />
      <SectionCard
        icon={FileText}
        title="Residency letter"
        action={{ label: 'Generate a residency letter', onClick: goIdentity }}
        onClick={goIdentity}
      />
    </Group>
  );
}
