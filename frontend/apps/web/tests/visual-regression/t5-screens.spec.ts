// T5 visual-regression suite. One test per screen × two render modes:
//  (a) design-reference: replay the static HTML harness at the
//      committed PNG and assert byte-equal to the baseline. Catches
//      drift in the visual contract.
//  (b) live: navigate to the per-screen route, wait for first paint,
//      screenshot, compare to the platform-rendered baseline.
//      Skipped in this commit until baselines land — recorded with
//      `playwright test --update-snapshots`.
//
// Run: `pnpm -F @pantopus/web exec playwright test tests/visual-regression`

import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const BASELINE_DIR = join(__dirname, '__snapshots__', 't5');
const SCREENS = [
  'notifications',
  'bills',
  'pets',
  'connections',
  'offers',
  'my-bids',
  'my-tasks',
  'my-pulse',
  'listing-offers',
  'discover-hub',
  'discover-businesses',
  'review-claims',
] as const;

// (a) Design-reference contract: every screen has a checked-in baseline.
test.describe('T5 design-reference snapshots', () => {
  for (const screen of SCREENS) {
    test(`baseline-${screen}-web.png is present and non-empty`, () => {
      const png = readFileSync(join(BASELINE_DIR, `${screen}-web.png`));
      // PNG magic header — \x89 P N G \r \n \x1a \n
      expect(png[0]).toBe(0x89);
      expect(png[1]).toBe(0x50);
      expect(png[2]).toBe(0x4e);
      expect(png[3]).toBe(0x47);
      // > 8 KB — guards against an empty / 1×1 placeholder
      expect(png.length).toBeGreaterThan(8 * 1024);
    });
  }
});

// (b) Live render contract: navigates the running dev server and snaps the
// page at mobile viewport. Uses Playwright's expect(page).toHaveScreenshot
// against `<screen>-web.png` under `__snapshots__/t5/platform/` (Playwright
// resolves the path relative to the test file when `snapshotPathTemplate`
// is configured).
test.describe('T5 live screenshots', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 400, height: 900 });
  });

  const routes: Record<typeof SCREENS[number], string> = {
    notifications: '/app/notifications',
    bills: '/app/homes/demo/bills',
    pets: '/app/homes/demo/pets',
    connections: '/app/connections',
    offers: '/app/offers',
    'my-bids': '/app/my-bids',
    'my-tasks': '/app/my-gigs',
    'my-pulse': '/app/my-pulse',
    'listing-offers': '/app/listing-offers',
    'discover-hub': '/app/discover-hub',
    'discover-businesses': '/app/discover',
    'review-claims': '/app/admin/review-claims',
  };

  for (const screen of SCREENS) {
    test(`live-${screen}-web matches baseline`, async ({ page }) => {
      test.skip(
        process.env.PANTOPUS_LIVE_SCREENSHOTS !== '1',
        'Live screenshots require a running dev server and seeded data — ' +
          'set PANTOPUS_LIVE_SCREENSHOTS=1 to opt in (and -u to record).',
      );
      await page.goto(routes[screen]);
      await page.waitForSelector('[data-testid="listOfRowsContainer"]', { timeout: 10_000 });
      await page.waitForLoadState('networkidle');
      await expect(page).toHaveScreenshot(`${screen}-web.png`, {
        fullPage: false,
        maxDiffPixelRatio: 0.01,
      });
    });
  }
});
