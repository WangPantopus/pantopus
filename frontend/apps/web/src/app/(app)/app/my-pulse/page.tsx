'use client';

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation } from '@tanstack/react-query';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import type { Post } from '@pantopus/types';
import { PostCard, PostDetailPanel } from '@/components/feed';
import ReportModal from '@/components/ui/ReportModal';
import { toast } from '@/components/ui/toast-store';
import { Newspaper } from 'lucide-react';

export default function MyPulsePage() {
  const router = useRouter();
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [userId, setUserId] = useState<string | null>(null);
  const [detailPostId, setDetailPostId] = useState<string | null>(null);
  const [reportPostId, setReportPostId] = useState<string | null>(null);
  const [likingIds, setLikingIds] = useState<Set<string>>(new Set());

  // ── Fetch user ───────────────────────────────────────────
  useEffect(() => {
    (async () => {
      const token = getAuthToken();
      if (!token) { router.push('/login'); return; }
      try {
        const u = await api.users.getMyProfile();
        setUserId(u.id);
      } catch {
        router.push('/login');
      }
    })();
  }, [router]);

  // ── Fetch posts ──────────────────────────────────────────
  const loadPosts = useCallback(async () => {
    if (!userId) return;
    setLoading(true);
    try {
      const result = await api.posts.getUserPosts(userId, { limit: 50 });
      setPosts((result as any)?.posts || []);
    } catch (err) {
      console.error('Failed to load my posts:', err);
      setPosts([]);
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => { loadPosts(); }, [loadPosts]);

  // ── Post actions ─────────────────────────────────────────
  const likeMutation = useMutation({
    mutationFn: (postId: string) => api.posts.toggleLike(postId),
    onMutate: (postId) => {
      setLikingIds((prev) => new Set(prev).add(postId));
    },
    onSuccess: (res, postId) => {
      setPosts((prev) =>
        prev.map((p) =>
          p.id === postId
            ? { ...p, liked_by_user: res.liked, like_count: res.likeCount }
            : p,
        ),
      );
    },
    onError: () => {
      toast.error('Failed to like post');
    },
    onSettled: (_data, _err, postId) => {
      setLikingIds((prev) => {
        const next = new Set(prev);
        next.delete(postId);
        return next;
      });
    },
  });

  const handleLike = (postId: string) => {
    likeMutation.mutate(postId);
  };

  const handleDelete = async (postId: string) => {
    try {
      await api.posts.deletePost(postId);
      setPosts((prev) => prev.filter((p) => p.id !== postId));
      toast.success('Post deleted');
    } catch {
      toast.error('Failed to delete post');
    }
  };

  const saveMutation = useMutation({
    mutationFn: (postId: string) => api.posts.toggleSave(postId),
    onSuccess: (res, postId) => {
      setPosts((prev) =>
        prev.map((p) =>
          p.id === postId ? { ...p, userHasSaved: res.saved } : p,
        ),
      );
    },
    onError: () => {
      toast.error('Failed to save post');
    },
  });

  const handleSave = (postId: string) => {
    saveMutation.mutate(postId);
  };

  return (
    <div className="min-h-[calc(100vh-64px)]">
      <main className="max-w-3xl mx-auto px-4 sm:px-6 py-6">
        {/* ── Header ──────────────────────────────────────────── */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
          <div>
            <h1 className="text-2xl font-bold text-app-text">My Pulse</h1>
            <p className="text-sm text-app-text-secondary mt-0.5">
              {posts.length} post{posts.length !== 1 ? 's' : ''}
            </p>
          </div>
          <button
            onClick={() => router.push('/app/feed')}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 font-medium text-sm"
          >
            Go to Pulse
          </button>
        </div>

        {/* ── Content ─────────────────────────────────────────── */}
        {loading ? (
          <div className="text-center py-16">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto" />
            <p className="mt-4 text-app-text-secondary">Loading your posts...</p>
          </div>
        ) : posts.length === 0 ? (
          <div className="text-center py-16 bg-app-surface rounded-xl border border-app-border">
            <Newspaper className="w-12 h-12 text-app-text-muted mx-auto mb-4" />
            <h3 className="text-lg font-semibold text-app-text mb-2">
              You haven&apos;t posted anything yet
            </h3>
            <p className="text-app-text-secondary mb-6">
              Share updates, questions, or recommendations with your community!
            </p>
            <button
              onClick={() => router.push('/app/feed')}
              className="px-6 py-2.5 bg-primary-600 text-white rounded-lg hover:bg-primary-700 font-medium"
            >
              Go to Pulse
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {posts.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                onLike={handleLike}
                onComment={(id) => setDetailPostId(id)}
                onOpenDetail={(id) => setDetailPostId(id)}
                onSave={handleSave}
                onDelete={handleDelete}
                onReport={(id) => setReportPostId(id)}
                currentUserId={userId || undefined}
                isLiking={likingIds.has(post.id)}
              />
            ))}
          </div>
        )}
      </main>

      {/* ── Post detail panel ───────────────────────────────── */}
      <PostDetailPanel
        postId={detailPostId}
        open={!!detailPostId}
        onClose={() => setDetailPostId(null)}
        currentUserId={userId || undefined}
      />

      {/* ── Report modal ────────────────────────────────────── */}
      <ReportModal
        open={!!reportPostId}
        onClose={() => setReportPostId(null)}
        onSubmit={async (reason, details) => {
          if (!reportPostId) return;
          await api.posts.reportPost(reportPostId, {
            reason: reason as 'spam' | 'harassment' | 'inappropriate' | 'misinformation' | 'other',
            details,
          });
          toast.success('Report submitted');
          setReportPostId(null);
        }}
        entityType="post"
      />
    </div>
  );
}
