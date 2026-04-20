// ─────────────────────────────────────────────────────────────────────────────
// FinalCTASection — Identity callback + final CTA with QR codes
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import Link from 'next/link';
import Image from 'next/image';

export default function FinalCTASection() {
  return (
    <section
      className="
        py-24 relative overflow-hidden
        bg-gradient-to-br from-primary-50 via-white to-primary-100
        dark:from-primary-800 dark:via-primary-700 dark:to-primary-600
      "
    >
      {/* Soft accent blobs — lighter in light mode, original in dark mode */}
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute bottom-0 right-0 w-96 h-96 bg-primary-200/40 dark:bg-glass/5 rounded-full blur-3xl" />
        <div className="absolute top-0 left-0 w-64 h-64 bg-primary-200/40 dark:bg-glass/5 rounded-full blur-3xl" />
      </div>

      <div className="relative max-w-4xl mx-auto text-center px-4">
        {/* Identity callback */}
        <p className="text-2xl md:text-3xl font-bold text-primary-900 dark:text-white mb-4 max-w-3xl mx-auto leading-snug">
          In an AI-saturated world, being a verified, skilled, trusted human is a competitive advantage.
        </p>
        <p className="text-lg text-primary-800/80 dark:text-primary-100 mb-12 max-w-xl mx-auto">
          Pantopus helps you own that advantage — with your identity, your reputation, and your income.
        </p>

        {/* Visual separator */}
        <div className="w-16 h-px bg-primary-300 dark:bg-white/20 mx-auto mb-12" />

        {/* CTA block */}
        <h2 className="text-4xl md:text-5xl font-bold mb-4 leading-tight text-primary-900 dark:text-white">
          Ready to get things done?
        </h2>
        <p className="text-xl text-primary-800/80 dark:text-primary-100 mb-10 max-w-xl mx-auto">
          Connect with verified people. Hire, sell, organize, earn — all in one place.
        </p>

        <div className="flex flex-col sm:flex-row justify-center gap-4">
          {/* Primary CTA: solid primary in light mode (pops on the soft tint), white-on-blue in dark mode */}
          <Link
            href="/register"
            className="
              inline-flex items-center justify-center gap-2 px-8 py-3.5 rounded-xl text-base font-bold transition shadow-lg
              bg-primary-600 text-white hover:bg-primary-700
              dark:bg-app-surface dark:text-primary-700 dark:hover:bg-app-hover
            "
          >
            Download the app
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
          {/* Secondary CTA: outlined */}
          <Link
            href="/register"
            className="
              inline-flex items-center justify-center gap-2 px-8 py-3.5 rounded-xl text-base font-semibold transition
              bg-white/60 border border-primary-300 text-primary-700 hover:bg-white
              dark:bg-glass/10 dark:border-white/20 dark:text-white dark:hover:bg-glass/20
            "
          >
            Or create your account on web
          </Link>
        </div>

        {/* QR codes — keep white tile so QRs stay scannable, but add a subtle border in light mode */}
        <div className="mt-10 flex flex-wrap justify-between gap-6 max-w-md mx-auto">
          <div className="flex flex-col items-center gap-2">
            <div className="w-44 h-44 bg-white rounded-xl p-2 border border-primary-200/60 dark:border-transparent shadow-sm">
              <Image
                src="/landing/qr-ios.png"
                alt="QR code for iOS App Store"
                width={160}
                height={160}
                className="w-full h-full"
              />
            </div>
            <span className="text-xs text-primary-700 dark:text-primary-200">iOS</span>
          </div>
          <div className="flex flex-col items-center gap-2">
            <div className="w-44 h-44 bg-white rounded-xl p-2 border border-primary-200/60 dark:border-transparent shadow-sm">
              <Image
                src="/landing/qr-android.png"
                alt="QR code for Google Play Store"
                width={160}
                height={160}
                className="w-full h-full"
              />
            </div>
            <span className="text-xs text-primary-700 dark:text-primary-200">Android</span>
          </div>
        </div>
        <p className="mt-3 text-xs text-primary-700/70 dark:text-primary-200/70">Scan to download</p>

        <p className="mt-8 text-sm text-primary-700/70 dark:text-primary-200/70">
          Founding communities get early access and a direct feedback line to the team.
        </p>
      </div>
    </section>
  );
}
