// T5 soak harness — 60-second scroll + filter + pull-to-refresh loop
// against each new list screen at mobile viewport. Samples
// `performance.memory.usedJSHeapSize` and DOM-node counts every 500 ms
// and writes a CSV per screen under `docs/soak-tests-t5/`.
//
// Pass criterion: heap delta ≤ 8 MB AND DOM-node delta ≤ 200 between
// the t=0 sample (just-loaded) and the t=60s sample.
//
// Run: `PANTOPUS_LIVE_SCREENSHOTS=1 pnpm -F @pantopus/web exec \
//        playwright test tests/visual-regression/t5-soak.spec.ts`

import { expect, test } from '@playwright/test';
import { writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

const DURATION_MS = 60_000;
const SAMPLE_MS = 500;
const SOAK_OUT = join(__dirname, '..', '..', '..', '..', '..', 'docs', 'soak-tests-t5');

const SCREENS = [
  { id: 'notifications', route: '/app/notifications', tabs: 2 },
  { id: 'bills', route: '/app/homes/demo/bills', tabs: 3 },
  { id: 'pets', route: '/app/homes/demo/pets', tabs: 0 },
  { id: 'connections', route: '/app/connections', tabs: 3 },
  { id: 'offers', route: '/app/offers', tabs: 2 },
  { id: 'my-bids', route: '/app/my-bids', tabs: 4 },
  { id: 'my-tasks', route: '/app/my-gigs', tabs: 4 },
  { id: 'my-pulse', route: '/app/my-pulse', tabs: 2 },
  { id: 'listing-offers', route: '/app/listing-offers', tabs: 0 },
  { id: 'discover-hub', route: '/app/discover-hub', tabs: 0 },
  { id: 'discover-businesses', route: '/app/discover', tabs: 0 },
  { id: 'review-claims', route: '/app/admin/review-claims', tabs: 3 },
];

type Sample = {
  t: number; // ms since soak start
  jsHeapMB: number;
  domNodes: number;
  scrollY: number;
};

test.describe('T5 soak — 60s scroll + filter + pull-to-refresh', () => {
  test.setTimeout(120_000);

  test.beforeAll(() => {
    mkdirSync(SOAK_OUT, { recursive: true });
  });

  for (const screen of SCREENS) {
    test(`soak ${screen.id}`, async ({ page, context }) => {
      test.skip(
        process.env.PANTOPUS_LIVE_SCREENSHOTS !== '1',
        'Soak run requires a running dev server with seeded data — set ' +
          'PANTOPUS_LIVE_SCREENSHOTS=1 to opt in.',
      );

      await page.setViewportSize({ width: 400, height: 900 });
      await page.goto(screen.route);
      await page
        .waitForSelector('[data-testid="listOfRowsContainer"]', { timeout: 10_000 })
        .catch(() => null);
      await page.waitForLoadState('networkidle');

      const samples: Sample[] = [];
      const client = await context.newCDPSession(page);
      const sample = async (t: number): Promise<Sample> => {
        const heap = await page.evaluate(() => {
          const m = (performance as unknown as { memory?: { usedJSHeapSize: number } }).memory;
          return m?.usedJSHeapSize ?? 0;
        });
        const { result } = await client.send('Runtime.evaluate', {
          expression: 'document.getElementsByTagName("*").length',
        });
        const domNodes = (result as { value: number }).value;
        const scrollY = await page.evaluate(() => window.scrollY || 0);
        return { t, jsHeapMB: Math.round(heap / 10485.76) / 100, domNodes, scrollY };
      };

      const t0 = Date.now();
      samples.push(await sample(0));

      // Drive the interaction loop for ~60s. Three behaviours interleave:
      //  1. scroll down/up a screenful
      //  2. tab/filter swap every 5s (if tabs exist)
      //  3. pull-to-refresh every 10s
      let tabIdx = 0;
      let tick = 0;
      while (Date.now() - t0 < DURATION_MS) {
        tick++;

        // Scroll down then up — one cycle.
        await page.mouse.wheel(0, 900);
        await page.waitForTimeout(SAMPLE_MS);
        samples.push(await sample(Date.now() - t0));
        await page.mouse.wheel(0, -900);
        await page.waitForTimeout(SAMPLE_MS);
        samples.push(await sample(Date.now() - t0));

        // Tab swap every 5s.
        if (tick % 5 === 0 && screen.tabs > 0) {
          const next = (tabIdx + 1) % screen.tabs;
          await page.evaluate((i: number) => {
            const buttons = document.querySelectorAll(
              '[data-testid="listOfRowsContainer"] [role="tab"]',
            );
            (buttons[i] as HTMLElement | undefined)?.click();
          }, next);
          tabIdx = next;
          await page.waitForTimeout(SAMPLE_MS);
          samples.push(await sample(Date.now() - t0));
        }

        // Pull-to-refresh every 10s.
        if (tick % 10 === 0) {
          await page.evaluate(() => window.scrollTo(0, 0));
          await page.evaluate(() => {
            const ev = new CustomEvent('pantopus-pull-to-refresh');
            window.dispatchEvent(ev);
          });
          await page.waitForTimeout(SAMPLE_MS);
          samples.push(await sample(Date.now() - t0));
        }
      }
      samples.push(await sample(Date.now() - t0));

      // Write CSV.
      const csv = ['t_ms,js_heap_mb,dom_nodes,scroll_y']
        .concat(samples.map(s => `${s.t},${s.jsHeapMB},${s.domNodes},${s.scrollY}`))
        .join('\n');
      writeFileSync(join(SOAK_OUT, `${screen.id}-web.csv`), csv + '\n');

      // Assert leak budget.
      const first = samples[0];
      const last = samples[samples.length - 1];
      const heapDelta = last.jsHeapMB - first.jsHeapMB;
      const domDelta = last.domNodes - first.domNodes;
      console.log(
        `soak ${screen.id} · heap +${heapDelta.toFixed(2)} MB · dom +${domDelta} nodes`,
      );
      expect(heapDelta).toBeLessThanOrEqual(8);
      expect(domDelta).toBeLessThanOrEqual(200);
    });
  }
});
