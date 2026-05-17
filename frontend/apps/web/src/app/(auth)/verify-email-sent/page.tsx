'use client';

import { Suspense, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import * as api from '@pantopus/api';
import PantopusBadge from '@/components/PantopusBadge';
import { extractApiError } from '@/lib/auth-utils';

const RESEND_COOLDOWN_SECONDS = 30;

function VerifyEmailSentPageContent() {
  const searchParams = useSearchParams();
  const emailParam = searchParams.get('email') || '';
  const email = useMemo(() => emailParam.trim().toLowerCase(), [emailParam]);

  const [status, setStatus] = useState('');
  const [resending, setResending] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const id = setInterval(() => {
      setCooldown((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(id);
  }, [cooldown]);

  const onResend = async () => {
    if (!email || cooldown > 0 || resending) return;
    setResending(true);
    setStatus('');
    try {
      const res = await api.auth.resendVerification(email);
      setStatus(res?.message || 'If that email exists, a verification email has been sent.');
      setCooldown(RESEND_COOLDOWN_SECONDS);
    } catch (err: unknown) {
      setStatus(extractApiError(err, 'Could not resend verification email.'));
    } finally {
      setResending(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-blue-50 to-white dark:from-gray-950 dark:to-gray-950 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <PantopusBadge />
        </div>
        <div className="mt-6 flex justify-center">
          <div className="w-32 h-32 rounded-full bg-primary-50 flex items-center justify-center">
            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="#0ea5e9" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <path d="M22 17a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-6a4 4 0 0 1 4-4h12a4 4 0 0 1 4 4z"/>
              <polyline points="15 9 18 9 18 11"/>
              <path d="M6 11V7"/>
            </svg>
          </div>
        </div>
        <h1 className="mt-4 text-center text-3xl font-bold text-app-text dark:text-white">Verify your email</h1>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-app-surface/90 backdrop-blur py-8 px-4 shadow-lg shadow-black/5 dark:shadow-black/30 rounded-2xl border border-app-border-subtle sm:px-10">
          <p className="text-sm text-app-text-strong text-center">
            We sent a verification link to{' '}
            <span className="font-semibold">{email || 'your email address'}</span>.
            Click it to unlock all features.
          </p>

          {status ? (
            <div className="mt-4 rounded-lg bg-blue-50 border border-blue-200 px-4 py-3 text-sm text-blue-700 dark:bg-blue-950 dark:border-blue-900 dark:text-blue-200">
              {status}
            </div>
          ) : null}

          <div className="mt-6 space-y-3">
            <a
              href="mailto:"
              data-testid="verifyEmailOpenMailButton"
              className="w-full inline-flex justify-center py-2.5 px-4 rounded-lg shadow-sm text-sm font-semibold text-white bg-primary-600 hover:bg-primary-700"
            >
              Open mail app
            </a>
            <button
              type="button"
              onClick={onResend}
              disabled={!email || cooldown > 0 || resending}
              data-testid="verifyEmailResendButton"
              className="w-full py-2 text-sm font-medium text-primary-700 dark:text-primary-300 hover:opacity-90 disabled:opacity-60"
            >
              {resending ? 'Sending verification...' : cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend email'}
            </button>
            <Link
              href={`/register${email ? `?email=${encodeURIComponent(email)}` : ''}`}
              className="w-full inline-flex justify-center py-2 text-sm font-medium text-primary-700 dark:text-primary-300 hover:opacity-90"
            >
              Use a different email
            </Link>
            <Link
              href="/login"
              className="w-full inline-flex justify-center py-2.5 px-4 rounded-lg border border-app-border text-sm font-semibold text-app-text hover:bg-app-hover dark:hover:bg-gray-800"
            >
              Back to Sign in
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function VerifyEmailSentPage() {
  return (
    <Suspense>
      <VerifyEmailSentPageContent />
    </Suspense>
  );
}
