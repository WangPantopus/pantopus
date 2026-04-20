// ─────────────────────────────────────────────────────────────────────────────
// NavBar — Sticky top navigation for the landing page
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

import Link from 'next/link';
import { LayoutDashboard } from 'lucide-react';
import { NAV_LINKS } from './constants';

export default function NavBar() {
  return (
    <nav className="sticky top-0 z-50 bg-app-surface/90 backdrop-blur-md border-b border-app-border-subtle">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          {/* Logo */}
          <div className="flex items-center gap-2.5">
            <LayoutDashboard className="w-7 h-7 text-primary-600" />
            <div className="flex flex-col leading-none">
              <span className="text-xl font-bold tracking-tight text-primary-700 dark:text-primary-400">
                Pantopus
              </span>
              <span className="hidden sm:block text-[10px] font-medium text-app-text-muted dark:text-app-text-secondary tracking-wide uppercase">
                Verified People Platform
              </span>
            </div>
          </div>

          {/* Nav links (desktop) */}
          <div className="hidden md:flex items-center gap-6 text-sm">
            {NAV_LINKS.map(({ label, href }) => (
              <a
                key={href}
                href={href}
                className="text-app-text-secondary dark:text-app-text-muted hover:text-app-text dark:hover:text-white transition"
              >
                {label}
              </a>
            ))}
          </div>

          {/* Auth */}
          <div className="flex items-center gap-3">
            <Link
              href="/login"
              className="text-sm font-medium text-app-text-strong hover:text-primary-700 dark:hover:text-primary-300 px-3 py-2 transition"
            >
              Log in
            </Link>
            <Link
              href="/register"
              className="text-sm font-semibold bg-primary-600 text-white px-4 py-2 rounded-lg hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-primary-400/40 transition shadow-sm"
            >
              Get started free
            </Link>
          </div>
        </div>
      </div>
    </nav>
  );
}
