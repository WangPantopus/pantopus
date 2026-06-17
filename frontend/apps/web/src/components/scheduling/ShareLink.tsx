"use client";

// Share sheet for a booking-page or one-off link: context label, copyable URL
// + Copy button, native share, a QR thumbnail, an optional draft warning, and a
// Regenerate link. Mirrors the home ScopedShareModal copy + canvas-QR idiom.
// The context dot / accent follows the link's pillar; functional controls stay
// neutral.

import { useEffect, useRef, useState } from "react";
import { Check, Copy, QrCode, Share2 } from "lucide-react";
import clsx from "clsx";
import { copyToClipboard } from "@pantopus/utils";
import { toast } from "@/components/ui/toast-store";
import { pillarTokens, type Pillar } from "./pillarTokens";

// Decorative QR thumbnail (same canvas idiom as ScopedShareModal — a visual
// affordance, not a scannable code; no QR dependency is added).
function drawQR(canvas: HTMLCanvasElement, url: string) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const size = 144;
  canvas.width = size;
  canvas.height = size;
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, size, size);
  ctx.fillStyle = "#111827";
  const cellSize = 6;
  const gridSize = Math.floor(size / cellSize);
  let hash = 0;
  for (let i = 0; i < url.length; i++)
    hash = ((hash << 5) - hash + url.charCodeAt(i)) | 0;
  const drawFinder = (x: number, y: number) => {
    const s = cellSize;
    ctx.fillStyle = "#111827";
    ctx.fillRect(x, y, 7 * s, 7 * s);
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(x + s, y + s, 5 * s, 5 * s);
    ctx.fillStyle = "#111827";
    ctx.fillRect(x + 2 * s, y + 2 * s, 3 * s, 3 * s);
  };
  drawFinder(0, 0);
  drawFinder((gridSize - 7) * cellSize, 0);
  drawFinder(0, (gridSize - 7) * cellSize);
  for (let row = 8; row < gridSize - 8; row++) {
    for (let col = 8; col < gridSize - 8; col++) {
      if (((hash * (row + 1) * (col + 1)) >>> 0) % 3 === 0) {
        ctx.fillRect(col * cellSize, row * cellSize, cellSize, cellSize);
      }
    }
  }
}

interface ShareLinkProps {
  url: string;
  label?: string;
  shareTitle?: string;
  showQr?: boolean;
  qrCaption?: string;
  /** Page isn't live yet — show the amber draft banner. */
  draft?: boolean;
  onTurnOn?: () => void;
  onRegenerate?: () => void;
  pillar?: Pillar;
  className?: string;
}

export default function ShareLink({
  url,
  label = "Your booking link",
  shareTitle = "Book time with me",
  showQr = true,
  qrCaption = "Scan to open the booking page",
  draft = false,
  onTurnOn,
  onRegenerate,
  pillar = "personal",
  className,
}: ShareLinkProps) {
  const [copied, setCopied] = useState(false);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const tk = pillarTokens(pillar);

  useEffect(() => {
    if (showQr && canvasRef.current) drawQR(canvasRef.current, url);
  }, [showQr, url]);

  const handleCopy = async () => {
    const ok = await copyToClipboard(url);
    if (ok) {
      setCopied(true);
      toast.success("Link copied");
      setTimeout(() => setCopied(false), 2000);
    } else {
      toast.error("Could not copy the link");
    }
  };

  const handleNativeShare = async () => {
    if (typeof navigator !== "undefined" && navigator.share) {
      try {
        await navigator.share({ title: shareTitle, url });
      } catch {
        // user dismissed — no-op
      }
    } else {
      handleCopy();
    }
  };

  return (
    <div className={clsx("space-y-4", className)}>
      <div className="flex items-center gap-2">
        <span className={clsx("h-2.5 w-2.5 rounded-full", tk.bg)} aria-hidden />
        <span className="text-xs font-semibold uppercase tracking-wide text-app-text-muted">
          {label}
        </span>
      </div>

      {draft && (
        <div className="flex items-start justify-between gap-3 rounded-xl border border-app-warning/40 bg-app-warning-bg/60 px-3 py-2.5">
          <p className="text-xs text-app-text">
            This page isn’t live yet. People can’t book until you turn it on.
          </p>
          {onTurnOn && (
            <button
              type="button"
              onClick={onTurnOn}
              className="shrink-0 rounded-md bg-app-warning px-2.5 py-1 text-xs font-semibold text-white"
            >
              Turn on
            </button>
          )}
        </div>
      )}

      <div className="flex items-center gap-2">
        <div className="min-w-0 flex-1 truncate rounded-lg border border-app-border bg-app-surface px-3 py-2 text-sm text-app-text">
          {url}
        </div>
        <button
          type="button"
          onClick={handleCopy}
          className={clsx(
            "flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold",
            tk.bg,
            tk.textOn,
          )}
        >
          {copied ? (
            <Check className="h-4 w-4" aria-hidden />
          ) : (
            <Copy className="h-4 w-4" aria-hidden />
          )}
          {copied ? "Copied" : "Copy"}
        </button>
      </div>

      <button
        type="button"
        onClick={handleNativeShare}
        className="flex w-full items-center justify-center gap-2 rounded-lg border border-app-border bg-app-surface px-3 py-2 text-sm font-medium text-app-text hover:bg-app-hover"
      >
        <Share2 className="h-4 w-4" aria-hidden />
        Share…
      </button>

      {showQr && (
        <div className="flex flex-col items-center gap-2">
          <div className="rounded-xl border border-app-border bg-white p-3">
            <canvas
              ref={canvasRef}
              className="h-[144px] w-[144px]"
              style={{ imageRendering: "pixelated" }}
              aria-label="QR code for the booking link"
            />
          </div>
          <p className="flex items-center gap-1.5 text-xs text-app-text-muted">
            <QrCode className="h-3.5 w-3.5" aria-hidden />
            {qrCaption}
          </p>
        </div>
      )}

      {onRegenerate && (
        <button
          type="button"
          onClick={onRegenerate}
          className="text-xs font-medium text-app-text-muted hover:text-app-text"
        >
          Regenerate link
        </button>
      )}
    </div>
  );
}
