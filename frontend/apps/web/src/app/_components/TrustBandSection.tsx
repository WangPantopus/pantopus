// ─────────────────────────────────────────────────────────────────────────────
// TrustBandSection — §3 Anti-anonymous-internet trust statement
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

export default function TrustBandSection() {
  return (
    <section className="bg-app-surface-raised/60 border-y border-app-border-subtle py-10">
      <div className="max-w-3xl mx-auto px-4 text-center">
        <p className="text-base md:text-lg text-app-text-secondary dark:text-app-text-muted leading-relaxed">
          On most platforms, you have no idea who you&apos;re dealing with.{' '}
          <span className="font-semibold text-app-text dark:text-white">
            On Pantopus, every person is verified to a real address — so trust isn&apos;t hoped for, it&apos;s built in.
          </span>{' '}
          Post with purpose. Transact with confidence. Know the person behind the message is real.
        </p>
      </div>
    </section>
  );
}
