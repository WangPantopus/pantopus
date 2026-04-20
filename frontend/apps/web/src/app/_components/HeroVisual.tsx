// ─────────────────────────────────────────────────────────────────────────────
// HeroVisual — Phone mockup with demo video overlay
// Client component (needs 'use client' for video interactivity)
// ─────────────────────────────────────────────────────────────────────────────
'use client';

import { useState } from 'react';

export default function HeroVisual() {
  const [showVideo, setShowVideo] = useState(false);

  return (
    <div className="mt-16 flex justify-center">
      <div className="relative rounded-2xl bg-gray-900 p-2 shadow-2xl w-full max-w-4xl aspect-video">
        <video
          src="/landing/demo-video.mp4"
          poster="/landing/demo-video-poster.jpg"
          controls={showVideo}
          autoPlay={showVideo}
          playsInline
          className="rounded-xl w-full h-full object-contain bg-black"
        />
        {!showVideo && (
          <button
            onClick={() => setShowVideo(true)}
            className="absolute inset-2 flex items-center justify-center bg-black/30 rounded-xl hover:bg-black/40 transition group"
            aria-label="Play demo video"
          >
            <div className="w-16 h-16 rounded-full bg-white/90 flex items-center justify-center shadow-lg group-hover:scale-110 transition-transform">
              <svg className="w-7 h-7 text-primary-700 ml-1" fill="currentColor" viewBox="0 0 24 24">
                <path d="M8 5v14l11-7z" />
              </svg>
            </div>
          </button>
        )}
      </div>
    </div>
  );
}
