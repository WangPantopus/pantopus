// ─────────────────────────────────────────────────────────────────────────────
// FooterSection — Site footer with links and copyright
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import Link from 'next/link';
import { LayoutDashboard } from 'lucide-react';
import { FOOTER_PRODUCT_LINKS, FOOTER_COMPANY_LINKS, FOOTER_LEGAL_LINKS } from './constants';

export default function FooterSection() {
  return (
    <footer
      className="
        py-16 border-t border-app-border-subtle
        bg-app-surface-raised dark:bg-gray-900
        text-app-text-secondary
      "
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid md:grid-cols-5 gap-8 mb-12">
          <div className="md:col-span-2">
            <div className="flex items-center gap-2.5 mb-3">
              <LayoutDashboard className="w-5 h-5 text-primary-600" />
              <span className="text-lg font-bold text-app-text dark:text-white">Pantopus</span>
            </div>
            <p className="text-sm text-app-text-secondary leading-relaxed max-w-xs">
              Verified people, real homes, real work — all in one place.
            </p>
          </div>
          <div>
            <h4 className="text-app-text dark:text-white font-semibold mb-4 text-sm">Product</h4>
            <ul className="space-y-2.5 text-sm">
              {FOOTER_PRODUCT_LINKS.map(({ label, href }) => (
                <li key={href}>
                  <a href={href} className="hover:text-app-text dark:hover:text-white transition">{label}</a>
                </li>
              ))}
            </ul>
          </div>
          <div>
            <h4 className="text-app-text dark:text-white font-semibold mb-4 text-sm">Company</h4>
            <ul className="space-y-2.5 text-sm">
              {FOOTER_COMPANY_LINKS.map(({ label, href }) => (
                <li key={href}>
                  <Link href={href} className="hover:text-app-text dark:hover:text-white transition">{label}</Link>
                </li>
              ))}
            </ul>
          </div>
          <div>
            <h4 className="text-app-text dark:text-white font-semibold mb-4 text-sm">Legal</h4>
            <ul className="space-y-2.5 text-sm">
              {FOOTER_LEGAL_LINKS.map(({ label, href }) => (
                <li key={href}>
                  <Link href={href} className="hover:text-app-text dark:hover:text-white transition">{label}</Link>
                </li>
              ))}
            </ul>
          </div>
        </div>
        <div className="border-t border-app-border-subtle pt-8 text-center text-sm text-app-text-muted">
          <p>© 2026 Pantopus. All rights reserved.</p>
        </div>
      </div>
    </footer>
  );
}
