// ============================================================
// Place — Identity detail (C9). Verification status + a generator for
// a verified residency letter: edit the purpose, see a live product-UI
// preview, and deliver it by Download PDF (browser print) or Mail a
// copy through your mailbox (reuses the compose/send endpoint, home
// self-delivery). Portable ID sits below as "coming soon".
//
// Identity has no launch-set contract section, so this reads the
// verified tier from the intelligence + the resident's name/home.
// ============================================================

'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import * as api from '@pantopus/api';
import type { PlaceIntelligence } from '@pantopus/types';
import { BadgeCheck, Check, FileText, ScanFace, Mailbox, Download, ChevronRight, LayoutDashboard } from 'lucide-react';
import Chip from '@/components/archetypes/primitives/Chip';
import { LockedCard, DetailHeader, DetailSectionLabel, SourceNote, InfoNote } from '@/components/archetypes/place';
import { toast } from '@/components/ui/toast-store';
import { detailAddress } from './sections';

function issueDate(): string {
  return new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' });
}

interface LetterFacts {
  name: string;
  line1: string;
  cityStateZip: string;
  purpose: string;
}

function letterPurpose(purpose: string): string {
  const p = purpose.trim();
  return p || 'General verification of residency';
}

function letterPlainText(f: LetterFacts): string {
  const who = f.name ? f.name : 'the resident named on this account';
  return [
    issueDate(),
    '',
    'To whom it may concern,',
    '',
    `This letter confirms that ${who} is a verified resident at the address below, confirmed through Pantopus address verification.`,
    '',
    'Verified address:',
    f.line1,
    f.cityStateZip,
    '',
    `Issued for: ${letterPurpose(f.purpose)}`,
    '',
    'Address verified through Pantopus.',
  ].join('\n');
}

// ── Verification status — the green badge ───────────────────
function VerifiedStatus({ name, address }: { name: string; address: string }) {
  return (
    <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-[18px]">
      <div className="flex items-center gap-3.5">
        <span className="w-14 h-14 rounded-2xl bg-app-home-bg border border-app-success-light flex items-center justify-center shrink-0">
          <BadgeCheck size={30} strokeWidth={2} className="text-app-home" />
        </span>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[18px] font-bold -tracking-[0.015em] text-app-text">Verified resident</span>
            <Chip label="Active" variant="success" icon={Check} />
          </div>
          <div className="text-[13.5px] text-app-text-secondary mt-0.5 truncate">{[name, address].filter(Boolean).join(' · ')}</div>
        </div>
      </div>
      <div className="text-[13px] text-app-text-strong leading-5 mt-[15px] pt-[15px] border-t border-app-border-subtle">
        Your address is verified through Pantopus. You can generate a residency letter from it below.
      </div>
    </div>
  );
}

// ── Letter preview — product-UI, sans-serif (not ceremonial) ──
function LetterPreview({ facts }: { facts: LetterFacts }) {
  return (
    <div className="bg-app-surface border border-app-border rounded-[14px] shadow-md overflow-hidden">
      <div className="px-5 py-4 border-b border-app-border-subtle flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <span className="w-[26px] h-[26px] rounded-[7px] bg-primary-600 flex items-center justify-center">
            <LayoutDashboard size={15} strokeWidth={2.25} className="text-white" />
          </span>
          <span className="text-[15px] font-bold text-app-text -tracking-[0.01em]">Pantopus</span>
        </div>
        <span className="text-[10.5px] font-semibold tracking-[0.06em] uppercase text-app-text-muted">Verified residency</span>
      </div>
      <div className="px-5 pt-[18px] pb-5">
        <div className="text-[11px] text-app-text-muted">{issueDate()}</div>
        <div className="text-[15px] font-bold text-app-text mt-3 -tracking-[0.01em]">To whom it may concern,</div>
        <div className="text-[13.5px] text-app-text-strong leading-[21px] mt-2">
          This letter confirms that <span className="font-bold text-app-text">{facts.name || 'the resident named on this account'}</span> is a verified resident at the address below, confirmed through Pantopus address verification.
        </div>
        <div className="mt-3.5 px-3.5 py-3 bg-app-surface-muted border border-app-border-subtle rounded-[11px]">
          <div className="text-[11px] font-semibold tracking-[0.04em] uppercase text-app-text-muted mb-1">Verified address</div>
          <div className="text-[14.5px] font-semibold text-app-text leading-5">{facts.line1}<br />{facts.cityStateZip}</div>
        </div>
        <div className="mt-3 text-[13.5px] text-app-text-strong leading-[21px]">
          <span className="text-app-text-muted">Issued for: </span>
          <span className="font-semibold text-app-text">{letterPurpose(facts.purpose)}</span>
        </div>
        <div className="flex items-center gap-3 mt-[18px] pt-3.5 border-t border-dashed border-app-border-strong">
          <span className="w-[38px] h-[38px] rounded-[9px] bg-app-home-bg border border-app-success-light flex items-center justify-center shrink-0">
            <BadgeCheck size={20} strokeWidth={2} className="text-app-home" />
          </span>
          <div className="text-[12.5px] font-bold text-app-success">Address verified through Pantopus</div>
        </div>
      </div>
    </div>
  );
}

function printLetter(facts: LetterFacts): boolean {
  if (typeof window === 'undefined') return false;
  const win = window.open('', '_blank', 'width=720,height=900');
  if (!win) return false;
  const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>Verified residency letter</title>
<style>
  *{box-sizing:border-box} body{font-family:ui-sans-serif,system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;color:#111827;margin:0;padding:48px;}
  .sheet{max-width:640px;margin:0 auto;border:1px solid #e5e7eb;border-radius:14px;overflow:hidden}
  .head{display:flex;justify-content:space-between;align-items:center;padding:18px 24px;border-bottom:1px solid #f1f3f5}
  .brand{font-weight:700;font-size:16px} .tag{font-size:11px;letter-spacing:.06em;text-transform:uppercase;color:#9ca3af;font-weight:600}
  .body{padding:24px} .date{font-size:12px;color:#9ca3af} .greet{font-weight:700;margin-top:14px}
  p{font-size:14px;line-height:22px;color:#374151} .addr{margin:16px 0;padding:14px 16px;background:#f8fafb;border:1px solid #eef0f2;border-radius:11px}
  .addr .l{font-size:11px;letter-spacing:.04em;text-transform:uppercase;color:#9ca3af;font-weight:600;margin-bottom:4px}
  .addr .v{font-size:15px;font-weight:600;color:#111827} .foot{margin-top:20px;padding-top:16px;border-top:1px dashed #e2e5e9;font-size:13px;font-weight:700;color:#15803d}
</style></head><body><div class="sheet">
  <div class="head"><span class="brand">Pantopus</span><span class="tag">Verified residency</span></div>
  <div class="body">
    <div class="date">${esc(issueDate())}</div>
    <div class="greet">To whom it may concern,</div>
    <p>This letter confirms that <b>${esc(facts.name || 'the resident named on this account')}</b> is a verified resident at the address below, confirmed through Pantopus address verification.</p>
    <div class="addr"><div class="l">Verified address</div><div class="v">${esc(facts.line1)}<br>${esc(facts.cityStateZip)}</div></div>
    <p><span style="color:#9ca3af">Issued for:</span> <b>${esc(letterPurpose(facts.purpose))}</b></p>
    <div class="foot">Address verified through Pantopus</div>
  </div>
</div>
<script>window.onload=function(){setTimeout(function(){window.print();},250);};</script>
</body></html>`);
  win.document.close();
  return true;
}

function ResidencyLetterLeaf({ facts, homeId, address, onBack }: { facts: Omit<LetterFacts, 'purpose'>; homeId: string | null; address: string; onBack: () => void }) {
  const [purpose, setPurpose] = useState('');
  const [sending, setSending] = useState(false);
  const fullFacts: LetterFacts = { ...facts, purpose };

  const onDownload = () => {
    const ok = printLetter(fullFacts);
    if (ok) toast.success('Opened your letter to print or save as PDF.');
    else toast.error('Your browser blocked the print window. Allow pop-ups and try again.');
  };

  const onMail = async () => {
    if (!homeId) {
      toast.error('We couldn’t find your mailbox for this place.');
      return;
    }
    setSending(true);
    try {
      await api.mailCompose.sendComposedMail({
        destination: { deliveryTargetType: 'home', homeId, attnLabel: 'Current Resident', visibility: 'home_members' },
        envelope: { type: 'letter', subject: 'Verified residency letter' },
        object: {
          format: 'mailjson_v1',
          mimeType: 'application/json',
          title: 'Verified residency letter',
          content: letterPlainText(fullFacts),
          payload: { bodyFormat: 'plain_text' },
        },
        tracking: { source: 'place_identity_residency_letter_web' },
      });
      toast.success('A copy is on its way through your mailbox.');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'We couldn’t mail your letter. Try again.');
    } finally {
      setSending(false);
    }
  };

  return (
    <>
      <DetailHeader title="Residency letter" address={address} onBack={onBack} />
      <div className="px-4 sm:px-5 pt-1 pb-16">
        <DetailSectionLabel>Purpose</DetailSectionLabel>
        <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-4">
          <label htmlFor="letter-purpose" className="block text-[12.5px] font-semibold text-app-text-secondary mb-1.5">What is this letter for?</label>
          <input
            id="letter-purpose"
            value={purpose}
            onChange={(e) => setPurpose(e.target.value)}
            placeholder="e.g. New library card application"
            className="w-full h-[46px] px-3.5 text-[15px] text-app-text bg-app-surface border-[1.5px] border-app-border rounded-[10px] outline-none transition focus:border-primary-600 focus:ring-4 focus:ring-primary-600/10 placeholder:text-app-text-muted"
          />
          <div className="text-[12px] text-app-text-muted mt-1.5 leading-[17px]">Appears on the letter as the stated purpose. Leave blank for general verification.</div>
        </div>

        <DetailSectionLabel>Preview</DetailSectionLabel>
        <LetterPreview facts={fullFacts} />

        <div className="flex gap-2.5 mt-3.5">
          <button
            type="button"
            onClick={onMail}
            disabled={sending}
            className="flex-1 h-12 rounded-xl border-[1.5px] border-app-border bg-app-surface text-app-text text-[15px] font-semibold flex items-center justify-center gap-2 hover:bg-app-hover transition disabled:opacity-60"
          >
            <Mailbox size={18} strokeWidth={2} className="text-app-text-strong" /> {sending ? 'Sending…' : 'Mail a copy'}
          </button>
          <button
            type="button"
            onClick={onDownload}
            className="flex-1 h-12 rounded-xl bg-primary-600 text-white text-[15px] font-semibold flex items-center justify-center gap-2 shadow-[0_6px_16px_rgba(2,132,199,0.22)] hover:bg-primary-700 transition"
          >
            <Download size={18} strokeWidth={2.25} /> Download PDF
          </button>
        </div>
        <InfoNote>
          The letter draws only on what you&apos;ve already verified. &ldquo;Download PDF&rdquo; opens a printable copy; &ldquo;Mail a copy&rdquo; delivers one to your mailbox.
        </InfoNote>
      </div>
    </>
  );
}

export default function IdentityDetail({ intelligence, homeId, residentName }: { intelligence: PlaceIntelligence; homeId: string | null; residentName: string }) {
  const router = useRouter();
  const [letterOpen, setLetterOpen] = useState(false);
  const verified = intelligence.tier === 'T4';
  const address = detailAddress(intelligence.place);
  const place = intelligence.place;
  const cityStateZip = [place.city, place.state].filter(Boolean).join(', ') + (place.postal_code ? ` ${place.postal_code}` : '');

  if (letterOpen && verified) {
    return (
      <ResidencyLetterLeaf
        facts={{ name: residentName, line1: place.line1 || place.label, cityStateZip }}
        homeId={homeId}
        address={address}
        onBack={() => setLetterOpen(false)}
      />
    );
  }

  return (
    <>
      <DetailHeader title="Identity" address={address} />
      <div className="px-4 sm:px-5 pt-1 pb-16">
        <DetailSectionLabel>Verification</DetailSectionLabel>
        {verified ? (
          <>
            <VerifiedStatus name={residentName} address={place.line1 || place.label} />
            <SourceNote name="Address verification · Pantopus" asOf="active" />

            <DetailSectionLabel>Residency letter</DetailSectionLabel>
            <button
              type="button"
              onClick={() => setLetterOpen(true)}
              className="w-full flex items-center gap-3.5 bg-app-surface border border-app-border rounded-2xl shadow-sm p-4 text-left hover:bg-app-hover transition"
            >
              <span className="w-11 h-11 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
                <FileText size={22} strokeWidth={2} className="text-primary-600" />
              </span>
              <div className="flex-1 min-w-0">
                <div className="text-[15.5px] font-semibold text-app-text -tracking-[0.01em]">Generate a verified residency letter</div>
                <div className="text-[12.5px] text-app-text-muted mt-0.5">Proof of address you can download or mail</div>
              </div>
              <ChevronRight size={18} strokeWidth={2.25} className="shrink-0 text-app-text-muted" />
            </button>
            <InfoNote>
              A residency letter states your verified address for a purpose you choose — landlords, schools, libraries. It draws only on what you&apos;ve already verified.
            </InfoNote>
          </>
        ) : (
          <LockedCard
            icon={BadgeCheck}
            title="Verify your address"
            reason="Verify your address to get your badge and generate a residency letter."
            cta="Verify address"
            onCta={() => homeId && router.push(`/app/homes/${homeId}/verify-postcard`)}
          />
        )}

        <DetailSectionLabel>Portable ID</DetailSectionLabel>
        <div className="bg-app-surface border border-app-border rounded-2xl shadow-sm p-4 flex items-center gap-3.5">
          <span className="w-11 h-11 rounded-xl bg-app-surface-sunken flex items-center justify-center shrink-0">
            <ScanFace size={22} strokeWidth={2} className="text-app-text-muted" />
          </span>
          <div className="flex-1 min-w-0">
            <div className="text-[15.5px] font-semibold text-app-text -tracking-[0.01em]">Portable ID</div>
            <div className="text-[12.5px] text-app-text-muted mt-0.5">Carry your verified status to other apps</div>
          </div>
          <Chip label="Coming soon" variant="neutral" />
        </div>
      </div>
    </>
  );
}
