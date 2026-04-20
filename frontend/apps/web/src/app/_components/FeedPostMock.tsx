// ─────────────────────────────────────────────────────────────────────────────
// FeedPostMock — Mock feed post used in the Pulse section
// Server component (no 'use client')
// ─────────────────────────────────────────────────────────────────────────────

export interface FeedPostMockProps {
  intent: string;
  intentColor: string;
  user: string;
  time: string;
  content: string;
  replies: number;
}

export default function FeedPostMock({
  intent,
  intentColor,
  user,
  time,
  content,
  replies,
}: FeedPostMockProps) {
  return (
    <div className="p-4 hover:bg-app-hover dark:hover:bg-gray-800/50 transition">
      <div className="flex items-start gap-3">
        <div className="w-8 h-8 rounded-full bg-app-surface-sunken flex items-center justify-center text-xs font-bold text-app-text-secondary flex-shrink-0">
          {user[0]}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1.5">
            <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${intentColor}`}>{intent}</span>
            <span className="text-xs font-medium text-app-text-strong">{user}</span>
            <span className="text-xs text-app-text-muted">{time}</span>
          </div>
          <p className="text-sm text-app-text-strong leading-relaxed">{content}</p>
          <p className="text-xs text-app-text-muted mt-2">{replies} replies</p>
        </div>
      </div>
    </div>
  );
}
