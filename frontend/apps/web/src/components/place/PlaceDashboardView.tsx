// ============================================================
// PlaceDashboardView — the presentational dashboard.
//
// Renders the trust header, the Today's Pulse hero, and the launch-set
// groups (data-driven from the PlaceIntelligence contract). Pure: it
// takes the already-fetched intelligence so it's trivial to preview and
// test. The container (PlaceDashboard) owns fetching + the page states.
// ============================================================

'use client';

import { Fragment, useState } from 'react';
import type { PlaceIntelligence } from '@pantopus/types';
import { Group, HeroCard, PlaceHeader } from '@/components/archetypes/place';
import { derivePulse, renderSection } from './presentation';
import { IdentityGroup, LockedIdentityGroup, VerifyBanner } from './PlaceIdentitySection';
import VerifyPromptSheet from './VerifyPromptSheet';

export interface PlaceDashboardViewProps {
  intelligence: PlaceIntelligence;
  /** The claimed home; needed to route the verification flow. */
  homeId: string;
  /** Resident initials for the avatar; falls back to a neutral glyph. */
  userInitials?: string;
}

export default function PlaceDashboardView({ intelligence, homeId, userInitials }: PlaceDashboardViewProps) {
  const [verifyOpen, setVerifyOpen] = useState(false);
  const pulse = derivePulse(intelligence);
  const tier = intelligence.tier;
  const status = tier === 'T4' ? 'verified' : tier === 'T3' ? 'claimed' : 'none';

  // Identity / Band-D isn't in the contract yet (launch set is Band A), so
  // we derive the verify entry (T3) and the available identity rows (T4)
  // from the resolved tier. Skip the client identity group if a later wave
  // starts serving one.
  const showVerifyEntry = tier === 'T3';
  const hasServerIdentity = intelligence.groups.some((g) => g.group === 'identity');
  const showIdentity = tier === 'T4' && !hasServerIdentity;

  return (
    <div className="flex flex-col">
      <PlaceHeader
        address={intelligence.place.label}
        status={status}
        initials={userInitials ?? ''}
      />

      {showVerifyEntry ? (
        <div className="mt-4">
          <VerifyBanner onClick={() => setVerifyOpen(true)} />
        </div>
      ) : null}

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

        {showVerifyEntry ? <LockedIdentityGroup onVerify={() => setVerifyOpen(true)} /> : null}
        {showIdentity ? <IdentityGroup /> : null}
      </div>

      {showVerifyEntry ? (
        <VerifyPromptSheet
          open={verifyOpen}
          onClose={() => setVerifyOpen(false)}
          homeId={homeId}
          address={intelligence.place.label}
        />
      ) : null}
    </div>
  );
}
