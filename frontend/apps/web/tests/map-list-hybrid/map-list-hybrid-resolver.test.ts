/**
 * T6.6a (P24) — pure unit tests for the web detent resolver.
 *
 * Mirrors the iOS `MapListHybridShellTests.swift` and the Android
 * `MapListHybridResolverTest.kt`. The detent contract (160 / 296 / 518
 * px) and velocity thresholds must stay in sync across all three
 * platforms so the same gesture lands at the same stop.
 */

import {
  MAP_LIST_HYBRID_DETENT_HEIGHTS,
  MAP_LIST_HYBRID_DETENT_ORDER,
  resolveMapListHybridDetent,
  type MapListHybridDetent,
} from '../../src/components/map-list-hybrid/types';

describe('resolveMapListHybridDetent', () => {
  describe('snap-to-nearest (no flick)', () => {
    it('snaps to collapsed when released near the collapsed height', () => {
      expect(resolveMapListHybridDetent('standard', 0, 170)).toBe('collapsed');
    });

    it('snaps to standard when released near the standard height', () => {
      expect(resolveMapListHybridDetent('collapsed', 0, 290)).toBe('standard');
    });

    it('snaps to expanded when released near the expanded height', () => {
      expect(resolveMapListHybridDetent('standard', 0, 500)).toBe('expanded');
    });
  });

  describe('flick-up nudge (velocity < -threshold)', () => {
    it('advances collapsed → standard', () => {
      expect(resolveMapListHybridDetent('collapsed', -800, 170)).toBe('standard');
    });

    it('advances standard → expanded', () => {
      expect(resolveMapListHybridDetent('standard', -1000, 320)).toBe('expanded');
    });

    it('keeps expanded as expanded', () => {
      expect(resolveMapListHybridDetent('expanded', -1500, 510)).toBe('expanded');
    });
  });

  describe('flick-down nudge (velocity > threshold)', () => {
    it('retreats expanded → standard', () => {
      expect(resolveMapListHybridDetent('expanded', 1000, 500)).toBe('standard');
    });

    it('retreats standard → collapsed', () => {
      expect(resolveMapListHybridDetent('standard', 1500, 280)).toBe('collapsed');
    });

    it('keeps collapsed as collapsed', () => {
      expect(resolveMapListHybridDetent('collapsed', 1500, 150)).toBe('collapsed');
    });
  });

  describe('threshold boundary', () => {
    it('does not nudge when |velocity| is within threshold', () => {
      // 500 px/s < 600 px/s threshold: snap-to-nearest wins.
      expect(resolveMapListHybridDetent('collapsed', 500, 250)).toBe('standard');
    });
  });

  describe('detent contract', () => {
    it('heights match the Q9 contract (160 / 296 / 518 px)', () => {
      expect(MAP_LIST_HYBRID_DETENT_HEIGHTS.collapsed).toBe(160);
      expect(MAP_LIST_HYBRID_DETENT_HEIGHTS.standard).toBe(296);
      expect(MAP_LIST_HYBRID_DETENT_HEIGHTS.expanded).toBe(518);
    });

    it('order is small → large', () => {
      const expected: MapListHybridDetent[] = ['collapsed', 'standard', 'expanded'];
      expect([...MAP_LIST_HYBRID_DETENT_ORDER]).toEqual(expected);
    });
  });
});
