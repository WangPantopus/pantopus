'use client';

// T5.3.3 — My posts (web). Two-tab list (Active / Archived) on top of
// the shared `<ListOfRowsShell />` archetype, matching the mobile
// implementation. Active tab is wired to the existing
// `GET /api/posts/user/:userId` endpoint (backend filters
// `archived_at IS NULL`). Archive / unarchive are local-only optimistic
// mutations today — a follow-up backend PR will land
// `POST /api/posts/:id/archive` + `/unarchive` to power the Archived
// tab end-to-end. Delete is wired to the real `DELETE /api/posts/:id`.

import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useRouter } from 'next/navigation';
import {
  Archive,
  ArrowLeft,
  CheckCircle,
  Filter,
  HelpCircle,
  Megaphone,
  MessageCircle,
  MessageSquarePlus,
  PenLine,
  Pencil,
  RotateCcw,
  Search,
  ThumbsUp,
  Trash2,
  Calendar,
  Eye,
} from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import type { Post } from '@pantopus/types';
import {
  ListOfRowsShell,
  type ListOfRowsState,
  type RowChip,
  type RowEngagement,
  type RowEngagementItem,
  type RowModel,
  type RowSection,
} from '@/components/list-of-rows';
import { toast } from '@/components/ui/toast-store';

// ─── Intent palette ────────────────────────────────────────────

type Intent = 'ask' | 'recommend' | 'event' | 'lost' | 'announce';

const INTENT_PALETTE: Record<
  Intent,
  { label: string; icon: typeof HelpCircle; background: string; foreground: string }
> = {
  ask: { label: 'Ask', icon: HelpCircle, background: '#fef3c7', foreground: '#92400e' },
  recommend: {
    label: 'Recommend',
    icon: ThumbsUp,
    background: '#d1fae5',
    foreground: '#047857',
  },
  event: { label: 'Event', icon: Calendar, background: '#ede9fe', foreground: '#6d28d9' },
  lost: { label: 'Lost & Found', icon: Search, background: '#ffe4e6', foreground: '#be123c' },
  announce: { label: 'Announce', icon: Megaphone, background: '#e2e8f0', foreground: '#334155' },
};

function intentFromPostType(postType: string | null | undefined): Intent {
  switch (postType ?? '') {
    case 'ask_local':
    case 'ask':
      return 'ask';
    case 'recommendation':
    case 'recommend':
      return 'recommend';
    case 'event':
      return 'event';
    case 'lost_found':
      return 'lost';
    case 'local_update':
    case 'announcement':
    case 'heads_up':
    case 'neighborhood_win':
      return 'announce';
    default:
      return 'announce';
  }
}

// ─── Time meta ─────────────────────────────────────────────────

function relativeTime(iso: string, now: Date): string {
  const created = new Date(iso);
  if (Number.isNaN(created.getTime())) return '';
  const seconds = (now.getTime() - created.getTime()) / 1000;
  if (seconds < 60) return 'now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  if (seconds < 86_400) return `${Math.floor(seconds / 3600)}h`;
  const days = Math.floor(seconds / 86_400);
  if (days === 1) return 'Yesterday';
  if (days < 7) {
    return created.toLocaleDateString('en-US', { weekday: 'short' });
  }
  return created.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function timeMetaFor(post: Post, now: Date): string {
  const parts: string[] = [];
  const time = relativeTime(post.created_at, now);
  if (time) parts.push(time);
  if (post.location_name) parts.push(post.location_name);
  return parts.join(' · ');
}

// ─── Engagement items per intent ──────────────────────────────

function engagementItemsFor(post: Post, intent: Intent): RowEngagementItem[] {
  const replies: RowEngagementItem = {
    id: 'replies',
    icon: MessageCircle,
    label: `${post.comment_count ?? 0} ${post.comment_count === 1 ? 'reply' : 'replies'}`,
  };
  const likes: RowEngagementItem = {
    id: 'likes',
    icon: ThumbsUp,
    label: `${post.like_count ?? 0} ${post.like_count === 1 ? 'like' : 'likes'}`,
  };
  switch (intent) {
    case 'event':
      return [
        { id: 'going', icon: CheckCircle, label: `${post.like_count ?? 0} going` },
        replies,
      ];
    case 'recommend':
      return [
        { id: 'helpful', icon: ThumbsUp, label: `${post.like_count ?? 0} helpful` },
        replies,
      ];
    case 'lost':
      return [
        replies,
        { id: 'seen', icon: Eye, label: `${post.like_count ?? 0} seen` },
      ];
    case 'ask':
    case 'announce':
    default:
      return [replies, likes];
  }
}

// ─── Page ──────────────────────────────────────────────────────

type TabId = 'active' | 'archived';

export default function MyPulsePage() {
  const router = useRouter();
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedTab, setSelectedTab] = useState<TabId>('active');
  const [kebabPostId, setKebabPostId] = useState<string | null>(null);
  const [deletePostId, setDeletePostId] = useState<string | null>(null);
  // Local optimistic archive overrides. Keyed by post id → 'archived' /
  // 'active'. Wiped on refresh. Replaced with real wire archive_at when
  // the backend POST /:id/archive route ships.
  const [archiveOverrides, setArchiveOverrides] = useState<Record<string, 'archived' | 'active'>>(
    {},
  );
  const nowRef = useRef(new Date());

  // ── Auth bootstrap ────────────────────────────────────────────
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const token = getAuthToken();
      if (!token) {
        router.push('/login');
        return;
      }
      try {
        const me = await api.users.getMyProfile();
        if (cancelled) return;
        setLoading(true);
        try {
          const result = await api.posts.getUserPosts(me.id, { limit: 50 });
          if (cancelled) return;
          setPosts(((result as { posts?: Post[] }).posts) ?? []);
          setError(null);
        } catch (err) {
          if (!cancelled) setError((err as Error).message || 'Couldn’t load your posts.');
        } finally {
          if (!cancelled) setLoading(false);
        }
      } catch {
        if (!cancelled) router.push('/login');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [router]);

  const refresh = useCallback(async () => {
    setArchiveOverrides({});
    setLoading(true);
    try {
      const me = await api.users.getMyProfile();
      const result = await api.posts.getUserPosts(me.id, { limit: 50 });
      setPosts(((result as { posts?: Post[] }).posts) ?? []);
      setError(null);
    } catch (err) {
      setError((err as Error).message || 'Couldn’t load your posts.');
    } finally {
      setLoading(false);
    }
  }, []);

  // ── Tab/archive verdict ──────────────────────────────────────
  const isArchived = useCallback(
    (post: Post): boolean => {
      const override = archiveOverrides[post.id];
      if (override) return override === 'archived';
      return !!post.archived_at;
    },
    [archiveOverrides],
  );

  // ── Optimistic mutations ─────────────────────────────────────

  const handleArchive = useCallback((postId: string) => {
    setArchiveOverrides((prev) => ({ ...prev, [postId]: 'archived' }));
    setKebabPostId(null);
    // TODO(backend): wire POST /api/posts/:id/archive — on failure,
    // restore the previous override.
  }, []);

  const handleUnarchive = useCallback((postId: string) => {
    setArchiveOverrides((prev) => ({ ...prev, [postId]: 'active' }));
    setKebabPostId(null);
    // TODO(backend): wire POST /api/posts/:id/unarchive.
  }, []);

  const handleDelete = useCallback(async () => {
    if (!deletePostId) return;
    const id = deletePostId;
    setDeletePostId(null);
    const previousPosts = posts;
    const previousOverrides = archiveOverrides;
    setPosts((prev) => prev.filter((p) => p.id !== id));
    setArchiveOverrides((prev) => {
      const next = { ...prev };
      delete next[id];
      return next;
    });
    try {
      await api.posts.deletePost(id);
      toast.success('Post deleted');
    } catch {
      setPosts(previousPosts);
      setArchiveOverrides(previousOverrides);
      toast.error('Couldn’t delete the post');
    }
  }, [deletePostId, posts, archiveOverrides]);

  // ── Projections ──────────────────────────────────────────────

  const counts = useMemo(() => {
    let active = 0;
    let archived = 0;
    for (const p of posts) {
      if (isArchived(p)) archived += 1;
      else active += 1;
    }
    return { active, archived };
  }, [posts, isArchived]);

  const rows = useMemo<RowModel[]>(() => {
    const now = nowRef.current;
    return posts
      .filter((p) => (selectedTab === 'archived' ? isArchived(p) : !isArchived(p)))
      .map<RowModel>((post) => {
        const intent = intentFromPostType(post.post_type);
        const palette = INTENT_PALETTE[intent];
        const headerChips: RowChip[] = [
          {
            text: palette.label,
            icon: palette.icon,
            tint: isArchived(post)
              ? { kind: 'custom', background: '#f3f4f6', foreground: '#6b7280' }
              : { kind: 'custom', background: palette.background, foreground: palette.foreground },
          },
        ];
        if (isArchived(post)) {
          headerChips.push({
            text: 'ARCHIVED',
            icon: Archive,
            tint: { kind: 'custom', background: '#f3f4f6', foreground: '#6b7280' },
          });
        }
        const engagement: RowEngagement = {
          items: engagementItemsFor(post, intent),
          cta: isArchived(post)
            ? {
                label: 'Restore',
                icon: RotateCcw,
                accessibilityLabel: 'Restore post',
                onClick: () => handleUnarchive(post.id),
              }
            : {
                label: 'Edit',
                icon: Pencil,
                accessibilityLabel: 'Edit post',
                onClick: () => router.push(`/app/feed?edit=${post.id}`),
              },
        };
        return {
          id: post.id,
          title: '',
          template: 'statusChip',
          leading: { kind: 'none' },
          trailing: { kind: 'kebab' },
          onTap: () => router.push(`/app/feed?post=${post.id}`),
          onSecondary: () => setKebabPostId(post.id),
          body: post.content || post.title || '',
          bodyEmphasis: 'primary',
          headerChips,
          timeMeta: timeMetaFor(post, now),
          highlight: isArchived(post) ? 'archived' : undefined,
          engagement,
        };
      });
  }, [posts, selectedTab, isArchived, router, handleUnarchive]);

  // ── State machine ────────────────────────────────────────────

  const state: ListOfRowsState = useMemo(() => {
    if (loading) return { kind: 'loading' };
    if (error) return { kind: 'error', message: error };
    if (rows.length === 0) {
      if (selectedTab === 'archived') {
        return {
          kind: 'empty',
          config: {
            icon: Archive,
            headline: 'Nothing archived',
            subcopy:
              'Archived posts move out of the Pulse but stay on your profile. Use the kebab on any active post to archive it.',
          },
        };
      }
      return {
        kind: 'empty',
        config: {
          icon: MessageSquarePlus,
          headline: 'You haven’t posted yet',
          subcopy:
            'Ask a question, recommend a spot, or share a local heads-up. Your neighbors will see it on the Pulse.',
          ctaTitle: 'Write a post',
          onCta: () => router.push('/app/feed?compose=1'),
        },
      };
    }
    const section: RowSection = { id: selectedTab, rows };
    return { kind: 'loaded', sections: [section] };
  }, [loading, error, rows, selectedTab, router]);

  return (
    <div className="min-h-[calc(100vh-64px)] bg-app-bg" data-testid="my-posts">
      <div className="max-w-3xl mx-auto px-2 sm:px-4 py-4">
        <div className="flex items-center gap-2 mb-2">
          <button
            type="button"
            onClick={() => router.back()}
            className="w-9 h-9 flex items-center justify-center rounded-full text-app-text hover:bg-app-hover"
            aria-label="Back"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <h1 className="text-base font-semibold text-app-text">My posts</h1>
        </div>
        <ListOfRowsShell
          title=""
          state={state}
          onRefresh={refresh}
          tabs={[
            { id: 'active', label: 'Active', count: counts.active },
            { id: 'archived', label: 'Archived', count: counts.archived },
          ]}
          selectedTab={selectedTab}
          onTabChange={(id) => setSelectedTab(id as TabId)}
          topBarAction={{
            icon: Filter,
            accessibilityLabel: 'Filter posts',
            onClick: () => toast.success('Filters coming soon'),
          }}
          fab={{
            icon: PenLine,
            accessibilityLabel: 'Write a post',
            variant: { kind: 'secondaryCreate' },
            onClick: () => router.push('/app/feed?compose=1'),
          }}
        />
      </div>

      {/* ── Kebab dropdown ─────────────────────────────────────── */}
      {kebabPostId && (
        <KebabSheet
          isArchived={isArchived(posts.find((p) => p.id === kebabPostId) ?? ({} as Post))}
          onArchive={() => handleArchive(kebabPostId)}
          onRestore={() => handleUnarchive(kebabPostId)}
          onDelete={() => {
            setKebabPostId(null);
            setDeletePostId(kebabPostId);
          }}
          onCancel={() => setKebabPostId(null)}
        />
      )}

      {/* ── Delete confirmation ────────────────────────────────── */}
      {deletePostId && (
        <DeleteConfirm
          onCancel={() => setDeletePostId(null)}
          onConfirm={handleDelete}
        />
      )}
    </div>
  );
}

// ─── Kebab sheet ───────────────────────────────────────────────

interface KebabSheetProps {
  isArchived: boolean;
  onArchive: () => void;
  onRestore: () => void;
  onDelete: () => void;
  onCancel: () => void;
}

function KebabSheet({ isArchived, onArchive, onRestore, onDelete, onCancel }: KebabSheetProps) {
  return (
    <>
      <div
        role="presentation"
        className="fixed inset-0 z-40 bg-black/30"
        onClick={onCancel}
      />
      <div
        role="dialog"
        aria-label="Post options"
        className="fixed left-1/2 -translate-x-1/2 bottom-0 sm:bottom-1/2 sm:translate-y-1/2 z-50 w-full max-w-md bg-app-surface rounded-t-2xl sm:rounded-2xl shadow-xl border border-app-border p-4"
      >
        <div className="text-base font-semibold text-app-text mb-3">Post options</div>
        <div className="flex flex-col gap-1">
          {isArchived ? (
            <SheetAction
              label="Restore post"
              icon={RotateCcw}
              testId="kebab-restore"
              onClick={onRestore}
            />
          ) : (
            <SheetAction
              label="Archive post"
              icon={Archive}
              testId="kebab-archive"
              onClick={onArchive}
            />
          )}
          <SheetAction
            label="Delete post"
            icon={Trash2}
            testId="kebab-delete"
            destructive
            onClick={onDelete}
          />
        </div>
        <button
          type="button"
          onClick={onCancel}
          className="mt-3 w-full h-11 rounded-lg bg-app-surface-sunken text-app-text font-semibold text-sm"
          data-testid="kebab-cancel"
        >
          Cancel
        </button>
      </div>
    </>
  );
}

function SheetAction({
  label,
  icon: Icon,
  testId,
  destructive = false,
  onClick,
}: {
  label: string;
  icon: typeof HelpCircle;
  testId: string;
  destructive?: boolean;
  onClick: () => void;
}) {
  const color = destructive ? 'text-app-error' : 'text-app-text';
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-3 w-full h-12 px-3 rounded-lg text-sm font-medium hover:bg-app-hover ${color}`}
      data-testid={testId}
    >
      <Icon className="w-[18px] h-[18px]" />
      {label}
    </button>
  );
}

// ─── Delete confirmation ───────────────────────────────────────

function DeleteConfirm({ onCancel, onConfirm }: { onCancel: () => void; onConfirm: () => void }) {
  return (
    <>
      <div
        role="presentation"
        className="fixed inset-0 z-40 bg-black/30"
        onClick={onCancel}
      />
      <div
        role="dialog"
        aria-label="Delete this post?"
        className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 w-full max-w-md bg-app-surface rounded-2xl shadow-xl border border-app-border p-5"
      >
        <div className="text-lg font-semibold text-app-text mb-2">Delete this post?</div>
        <div className="text-sm text-app-text-secondary mb-4">
          This post will be permanently removed from your profile and the Pulse feed.
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 h-11 rounded-lg border border-app-border text-app-text font-semibold text-sm"
            data-testid="delete-cancel"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="flex-1 h-11 rounded-lg bg-app-error text-white font-semibold text-sm"
            data-testid="delete-confirm"
          >
            Delete
          </button>
        </div>
      </div>
    </>
  );
}
