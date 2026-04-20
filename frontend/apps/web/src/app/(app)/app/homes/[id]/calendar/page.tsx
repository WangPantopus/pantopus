'use client';

import { Suspense, useCallback, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { ArrowLeft, Plus } from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import { toast } from '@/components/ui/toast-store';
import CalendarCard from '@/components/home/cards/CalendarCard';

function CalendarContent() {
  const router = useRouter();
  const { id: homeId } = useParams<{ id: string }>();

  const [tasks, setTasks] = useState<Record<string, unknown>[]>([]);
  const [bills, setBills] = useState<Record<string, unknown>[]>([]);
  const [events, setEvents] = useState<Record<string, unknown>[]>([]);
  const [packages, setPackages] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);

  // Add event form
  const [showAddEvent, setShowAddEvent] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newDate, setNewDate] = useState('');
  const [addingEvent, setAddingEvent] = useState(false);

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const fetchData = useCallback(async () => {
    if (!homeId) return;
    const [tasksRes, billsRes, eventsRes, pkgRes] = await Promise.allSettled([
      api.homeProfile.getHomeTasks(homeId),
      api.homeProfile.getHomeBills(homeId),
      api.homeProfile.getHomeEvents(homeId),
      api.homeProfile.getHomePackages(homeId),
    ]);

    if (tasksRes.status === 'fulfilled')
      setTasks(((tasksRes.value as any)?.tasks || []) as Record<string, unknown>[]);
    if (billsRes.status === 'fulfilled')
      setBills(((billsRes.value as any)?.bills || []) as Record<string, unknown>[]);
    if (eventsRes.status === 'fulfilled')
      setEvents(((eventsRes.value as any)?.events || []) as Record<string, unknown>[]);
    if (pkgRes.status === 'fulfilled')
      setPackages(((pkgRes.value as any)?.packages || []) as Record<string, unknown>[]);
  }, [homeId]);

  useEffect(() => {
    setLoading(true);
    fetchData().finally(() => setLoading(false));
  }, [fetchData]);

  const handleAddEvent = useCallback(async () => {
    if (!newTitle.trim() || !newDate || !homeId) return;
    setAddingEvent(true);
    try {
      await api.homeProfile.createHomeEvent(homeId, {
        event_type: 'general',
        title: newTitle.trim(),
        start_at: new Date(newDate).toISOString(),
      });
      setNewTitle('');
      setNewDate('');
      setShowAddEvent(false);
      toast.success('Event added');
      await fetchData();
    } catch (err: any) {
      toast.error(err?.message || 'Failed to add event');
    } finally {
      setAddingEvent(false);
    }
  }, [homeId, newTitle, newDate, fetchData]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin h-8 w-8 border-3 border-emerald-600 border-t-transparent rounded-full" />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-4 py-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={() => router.back()}
            className="p-1.5 hover:bg-app-hover rounded-lg transition"
          >
            <ArrowLeft className="w-5 h-5 text-app-text" />
          </button>
          <h1 className="text-xl font-bold text-app-text">Household Calendar</h1>
        </div>
        <button
          onClick={() => setShowAddEvent(!showAddEvent)}
          className="flex items-center gap-1.5 px-3 py-2 bg-emerald-600 text-white text-sm font-semibold rounded-lg hover:bg-emerald-700 transition"
        >
          <Plus className="w-4 h-4" /> Add Event
        </button>
      </div>

      {/* Add event form */}
      {showAddEvent && (
        <div className="bg-app-surface border border-app-border rounded-xl p-4 mb-4 space-y-3">
          <input
            type="text"
            value={newTitle}
            onChange={(e) => setNewTitle(e.target.value)}
            placeholder="Event title"
            className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-emerald-400"
          />
          <input
            type="datetime-local"
            value={newDate}
            onChange={(e) => setNewDate(e.target.value)}
            className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface focus:outline-none focus:ring-2 focus:ring-emerald-400"
          />
          <button
            onClick={handleAddEvent}
            disabled={addingEvent || !newTitle.trim() || !newDate}
            className="w-full py-2.5 bg-emerald-600 text-white rounded-lg font-semibold text-sm hover:bg-emerald-700 disabled:opacity-50 transition"
          >
            {addingEvent ? 'Adding...' : 'Add Event'}
          </button>
        </div>
      )}

      {/* Calendar views (agenda/week/month) */}
      <CalendarCard
        tasks={tasks}
        bills={bills}
        events={events}
        packages={packages}
        onBack={() => router.push(`/app/homes/${homeId}/dashboard`)}
      />
    </div>
  );
}

export default function CalendarPage() {
  return (
    <Suspense>
      <CalendarContent />
    </Suspense>
  );
}
