'use client';

import { useState, useEffect, useRef, useCallback } from 'react';

interface RadiusSuggestion {
  suggestedRadius: number;
  reason: string;
  direction: 'expand' | 'narrow';
}

const RADIUS_OPTIONS = [1, 3, 10, 25] as const;

/**
 * Given the current radius and result count, suggest a different radius.
 *
 * Thresholds (tuned for neighborhood-scale content):
 *   - 0 results at any radius → suggest next larger radius
 *   - 1-2 results at ≥3mi → suggest next larger radius
 *   - 50+ results at ≥3mi → suggest next smaller radius
 *   - Otherwise → no suggestion
 */
function computeSuggestion(
  currentRadius: number,
  resultCount: number,
): RadiusSuggestion | null {
  const currentIdx = RADIUS_OPTIONS.indexOf(currentRadius as any);
  if (currentIdx === -1) return null;

  // Too few results → suggest expanding
  if (resultCount === 0 && currentIdx < RADIUS_OPTIONS.length - 1) {
    const next = RADIUS_OPTIONS[currentIdx + 1];
    return {
      suggestedRadius: next,
      reason: `No results within ${currentRadius} mi. Try ${next} mi?`,
      direction: 'expand',
    };
  }

  if (resultCount <= 2 && currentRadius < 25 && currentIdx < RADIUS_OPTIONS.length - 1) {
    const next = RADIUS_OPTIONS[currentIdx + 1];
    return {
      suggestedRadius: next,
      reason: `Only ${resultCount} result${resultCount === 1 ? '' : 's'} nearby. Expand to ${next} mi?`,
      direction: 'expand',
    };
  }

  // Too many results → suggest narrowing
  if (resultCount >= 50 && currentRadius > 1 && currentIdx > 0) {
    const next = RADIUS_OPTIONS[currentIdx - 1];
    return {
      suggestedRadius: next,
      reason: `Lots of activity here! Focus to ${next} mi?`,
      direction: 'narrow',
    };
  }

  return null;
}

/**
 * Content-density-based radius suggestion hook.
 *
 * On web, the caller provides the current radius and an optional setter
 * (since web doesn't have a global LocationContext like mobile).
 *
 * Usage:
 *   const [radius, setRadius] = useState(10);
 *   const { suggestion, dismiss, applySuggestion } = useRadiusSuggestion(resultCount, radius, setRadius);
 */
export function useRadiusSuggestion(
  resultCount: number,
  currentRadius: number = 10,
  setRadius?: (r: number) => void,
) {
  const [suggestion, setSuggestion] = useState<RadiusSuggestion | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const prevRadiusRef = useRef<number | null>(null);

  // Reset dismissed state when radius changes (user manually changed it)
  useEffect(() => {
    if (prevRadiusRef.current !== null && prevRadiusRef.current !== currentRadius) {
      setDismissed(false);
    }
    prevRadiusRef.current = currentRadius;
  }, [currentRadius]);

  // Compute suggestion when result count changes
  useEffect(() => {
    if (dismissed) {
      setSuggestion(null);
      return;
    }

    const s = computeSuggestion(currentRadius, resultCount);
    setSuggestion(s);
  }, [resultCount, currentRadius, dismissed]);

  const dismiss = useCallback(() => {
    setDismissed(true);
    setSuggestion(null);
  }, []);

  const applySuggestion = useCallback(() => {
    if (suggestion) {
      setRadius?.(suggestion.suggestedRadius);
      setSuggestion(null);
      setDismissed(true);
    }
  }, [suggestion, setRadius]);

  return {
    suggestion: dismissed ? null : suggestion,
    dismiss,
    applySuggestion,
  };
}
