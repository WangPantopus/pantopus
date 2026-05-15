'use client';

// Skeleton mirror of the loaded row geometry. Never a spinner.
export default function LoadingRows() {
  return (
    <div className="px-4 py-4 space-y-3 bg-app-bg">
      {Array.from({ length: 6 }).map((_, i) => (
        <div
          key={i}
          className="bg-app-surface rounded-xl p-3 flex items-center gap-3"
        >
          <div className="w-10 h-10 rounded-full bg-gradient-to-r from-app-surface-sunken via-app-surface to-app-surface-sunken bg-[length:200%_100%] animate-shimmer motion-reduce:animate-pulse" />
          <div className="flex-1 space-y-2">
            <div className="h-3.5 w-1/2 rounded bg-gradient-to-r from-app-surface-sunken via-app-surface to-app-surface-sunken bg-[length:200%_100%] animate-shimmer motion-reduce:animate-pulse" />
            <div className="h-3 w-1/3 rounded bg-gradient-to-r from-app-surface-sunken via-app-surface to-app-surface-sunken bg-[length:200%_100%] animate-shimmer motion-reduce:animate-pulse" />
          </div>
        </div>
      ))}
    </div>
  );
}
