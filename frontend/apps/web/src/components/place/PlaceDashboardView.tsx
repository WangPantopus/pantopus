// ============================================================
// PlaceDashboardView — the presentational dashboard.
//
// Renders the trust header, the Today's Pulse hero, and the launch-set
// groups (data-driven from the PlaceIntelligence contract). Pure: it
// takes the already-fetched intelligence so it's trivial to preview and
// test. The container (PlaceDashboard) owns fetching + the page states.
// ============================================================

'use client';

import { Fragment } from 'react';
import type { PlaceIntelligence } from '@pantopus/types';
import { Group, HeroCard, PlaceHeader } from '@/components/archetypes/place';
import { derivePulse, renderSection } from './presentation';
import { GROUP_TO_SLUG } from './detail/sections';

export interface PlaceDashboardViewProps {
  intelligence: PlaceIntelligence;
  /** Resident initials for the avatar; falls back to a neutral glyph. */
  userInitials?: string;
  /** Tap-through to a group-detail page (W2.3). Cards only show the
   *  chevron when their group has a detail screen. */
  onOpenSection?: (slug: string) => void;
}

export default function PlaceDashboardView({ intelligence, userInitials, onOpenSection }: PlaceDashboardViewProps) {
  const pulse = derivePulse(intelligence);
  const status =
    intelligence.tier === 'T4' ? 'verified' : intelligence.tier === 'T3' ? 'claimed' : 'none';

  return (
    <div className="flex flex-col">
      <PlaceHeader
        address={intelligence.place.label}
        status={status}
        initials={userInitials ?? ''}
      />

      <div className="mt-4">
        <HeroCard
          variant={pulse.variant}
          title={pulse.title}
          chip={pulse.chip}
          mainIcon={pulse.mainIcon}
          nudge={pulse.nudge}
        />
      </div>

      <div className="mt-6">
        {intelligence.groups.map((group) => {
          const slug = GROUP_TO_SLUG[group.group];
          const onOpen = slug && onOpenSection ? () => onOpenSection(slug) : undefined;
          return (
            <Group key={group.group} label={group.label}>
              {group.sections.map((section) => (
                <Fragment key={section.id}>{renderSection(section, onOpen)}</Fragment>
              ))}
            </Group>
          );
        })}
      </div>
    </div>
  );
}
