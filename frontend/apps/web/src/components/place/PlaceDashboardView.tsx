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
import { Group, HeroCard, PlaceHeader, type PlaceSwitcherHome } from '@/components/archetypes/place';
import { derivePulse, renderSection } from './presentation';

export interface PlaceDashboardViewProps {
  intelligence: PlaceIntelligence;
  /** Resident initials for the avatar; falls back to a neutral glyph. */
  userInitials?: string;
  /** The resident's places — when 2+, the header opens the multi-home switcher. */
  switchHomes?: PlaceSwitcherHome[];
  /** The home currently shown (highlighted in the switcher). */
  activeHomeId?: string | null;
  /** Switch the active place — re-queries the contract for it. */
  onSwitchHome?: (id: string) => void;
  /** Claim or verify another address. */
  onAddPlace?: () => void;
}

export default function PlaceDashboardView({
  intelligence,
  userInitials,
  switchHomes,
  activeHomeId,
  onSwitchHome,
  onAddPlace,
}: PlaceDashboardViewProps) {
  const pulse = derivePulse(intelligence);
  const status =
    intelligence.tier === 'T4' ? 'verified' : intelligence.tier === 'T3' ? 'claimed' : 'none';

  return (
    <div className="flex flex-col">
      <PlaceHeader
        address={intelligence.place.label}
        status={status}
        initials={userInitials ?? ''}
        switchHomes={switchHomes}
        activeHomeId={activeHomeId}
        onSwitchHome={onSwitchHome}
        onAddPlace={onAddPlace}
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
        {intelligence.groups.map((group) => (
          <Group key={group.group} label={group.label}>
            {group.sections.map((section) => (
              <Fragment key={section.id}>{renderSection(section)}</Fragment>
            ))}
          </Group>
        ))}
      </div>
    </div>
  );
}
