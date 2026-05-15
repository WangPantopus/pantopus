'use client';

import SharedEmptyState from '@/components/ui/EmptyState';
import type { EmptyConfig } from './types';

interface Props {
  config: EmptyConfig;
}

/**
 * Thin wrapper around the existing app-wide `EmptyState` so callers can
 * stay on the `ListOfRowsShell` API. The shared component's props are:
 *   { icon, title, description, actionLabel, onAction }.
 */
export default function EmptyState({ config }: Props) {
  return (
    <SharedEmptyState
      icon={config.icon}
      title={config.headline}
      description={config.subcopy}
      actionLabel={config.ctaTitle}
      onAction={config.onCta}
    />
  );
}
