'use client';

import { useCallback, useEffect, useReducer } from 'react';
import { useRouter } from 'next/navigation';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';

// ── Types ──

interface HomeDataEntities {
  home: Record<string, unknown> | null;
  members: Record<string, unknown>[];
  tasks: Record<string, unknown>[];
  issues: Record<string, unknown>[];
  bills: Record<string, unknown>[];
  packages: Record<string, unknown>[];
  documents: Record<string, unknown>[];
  events: Record<string, unknown>[];
  secrets: Record<string, unknown>[];
  emergencies: Record<string, unknown>[];
  nearbyGigs: Record<string, unknown>[];
  homeGigs: Record<string, unknown>[];
  pets: Record<string, unknown>[];
  polls: Record<string, unknown>[];
}

interface HomeAccessState {
  permissions: string[];
  role_base: string | null;
  isOwner: boolean;
}

export interface UseHomeDataReturn extends HomeDataEntities {
  loading: boolean;
  error: string | null;
  currentUserId: string | null;
  myAccess: HomeAccessState;
  can: (perm: string) => boolean;
  refresh: () => Promise<void>;
  refreshEntity: (entity: keyof HomeDataEntities) => Promise<void>;
  // Setters needed by handlers that do optimistic updates
  setTasks: (updater: (prev: Record<string, unknown>[]) => Record<string, unknown>[]) => void;
  setIssues: (updater: (prev: Record<string, unknown>[]) => Record<string, unknown>[]) => void;
  setBills: (updater: (prev: Record<string, unknown>[]) => Record<string, unknown>[]) => void;
  setPackages: (updater: (prev: Record<string, unknown>[]) => Record<string, unknown>[]) => void;
  setMembers: (updater: (prev: Record<string, unknown>[]) => Record<string, unknown>[]) => void;
  setSecrets: (updater: (prev: Record<string, unknown>[]) => Record<string, unknown>[]) => void;
}

// ── Reducer ──

type State = HomeDataEntities & {
  loading: boolean;
  error: string | null;
  currentUserId: string | null;
  myAccess: HomeAccessState;
};

type Action =
  | { type: 'LOAD_START' }
  | { type: 'LOAD_ERROR'; error: string }
  | { type: 'LOAD_COMPLETE'; data: Partial<State> }
  | { type: 'SET_ENTITY'; entity: keyof HomeDataEntities; data: Record<string, unknown>[] }
  | { type: 'UPDATE_ENTITY'; entity: keyof HomeDataEntities; updater: (prev: Record<string, unknown>[]) => Record<string, unknown>[] }
  | { type: 'SET_ACCESS'; access: HomeAccessState }
  | { type: 'SET_CURRENT_USER'; userId: string | null };

const INITIAL_ENTITIES: HomeDataEntities = {
  home: null,
  members: [],
  tasks: [],
  issues: [],
  bills: [],
  packages: [],
  documents: [],
  events: [],
  secrets: [],
  emergencies: [],
  nearbyGigs: [],
  homeGigs: [],
  pets: [],
  polls: [],
};

const initialState: State = {
  ...INITIAL_ENTITIES,
  loading: true,
  error: null,
  currentUserId: null,
  myAccess: { permissions: [], role_base: null, isOwner: false },
};

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'LOAD_START':
      return { ...state, loading: true, error: null };
    case 'LOAD_ERROR':
      return { ...state, loading: false, error: action.error };
    case 'LOAD_COMPLETE':
      return { ...state, ...action.data, loading: false, error: null };
    case 'SET_ENTITY':
      return { ...state, [action.entity]: action.data };
    case 'UPDATE_ENTITY':
      return { ...state, [action.entity]: action.updater(state[action.entity] as Record<string, unknown>[]) };
    case 'SET_ACCESS':
      return { ...state, myAccess: action.access };
    case 'SET_CURRENT_USER':
      return { ...state, currentUserId: action.userId };
    default:
      return state;
  }
}

// ── Entity fetch map ──

const ENTITY_FETCHERS: Record<
  keyof HomeDataEntities,
  (homeId: string) => Promise<{ key: string; data: Record<string, unknown> | Record<string, unknown>[] }>
> = {
  home: async (homeId) => {
    const res = await api.homes.getHome(homeId);
    return { key: 'home', data: (res as Record<string, unknown>).home as Record<string, unknown> };
  },
  members: async (homeId) => {
    const res = await api.homes.getHomeOccupants(homeId);
    const active = (res as Record<string, unknown>).occupants as Record<string, unknown>[] || [];
    const pending = (res as Record<string, unknown>).pendingInvites as Record<string, unknown>[] || [];
    return { key: 'members', data: [...active, ...pending] };
  },
  tasks: async (homeId) => {
    const res = await api.homeProfile.getHomeTasks(homeId);
    return { key: 'tasks', data: (res as Record<string, unknown>).tasks as Record<string, unknown>[] || [] };
  },
  issues: async (homeId) => {
    const res = await api.homeProfile.getHomeIssues(homeId);
    return { key: 'issues', data: (res as Record<string, unknown>).issues as Record<string, unknown>[] || [] };
  },
  bills: async (homeId) => {
    const res = await api.homeProfile.getHomeBills(homeId);
    return { key: 'bills', data: (res as Record<string, unknown>).bills as Record<string, unknown>[] || [] };
  },
  packages: async (homeId) => {
    const res = await api.homeProfile.getHomePackages(homeId);
    return { key: 'packages', data: (res as Record<string, unknown>).packages as Record<string, unknown>[] || [] };
  },
  documents: async (homeId) => {
    const res = await api.homeProfile.getHomeDocuments(homeId);
    return { key: 'documents', data: (res as Record<string, unknown>).documents as Record<string, unknown>[] || [] };
  },
  events: async (_homeId) => {
    // Events may come from tasks/bills or a dedicated endpoint — we handle both in aggregate
    return { key: 'events', data: [] };
  },
  secrets: async (homeId) => {
    const res = await api.homeProfile.getHomeAccessSecrets(homeId);
    return { key: 'secrets', data: (res as Record<string, unknown>).secrets as Record<string, unknown>[] || [] };
  },
  emergencies: async (homeId) => {
    const res = await api.homeProfile.getHomeEmergencies(homeId);
    return { key: 'emergencies', data: (res as Record<string, unknown>).emergencies as Record<string, unknown>[] || [] };
  },
  nearbyGigs: async (homeId) => {
    const res = await api.homeProfile.getNearbyGigs(homeId, { limit: 10 });
    return { key: 'nearbyGigs', data: (res as Record<string, unknown>).gigs as Record<string, unknown>[] || [] };
  },
  homeGigs: async (homeId) => {
    const res = await api.homeProfile.getHomeGigs(homeId, { limit: 20 });
    return { key: 'homeGigs', data: (res as Record<string, unknown>).gigs as Record<string, unknown>[] || [] };
  },
  pets: async (homeId) => {
    const res = await api.homeProfile.getHomePets(homeId);
    return { key: 'pets', data: (res as Record<string, unknown>).pets as Record<string, unknown>[] || [] };
  },
  polls: async (homeId) => {
    const res = await api.homeProfile.getHomePolls(homeId);
    return { key: 'polls', data: (res as Record<string, unknown>).polls as Record<string, unknown>[] || [] };
  },
};

// ── Hook ──

export function useHomeData(homeId: string): UseHomeDataReturn {
  const router = useRouter();
  const [state, dispatch] = useReducer(reducer, initialState);

  const loadDashboard = useCallback(async () => {
    dispatch({ type: 'LOAD_START' });
    try {
      const token = getAuthToken();
      if (!token) {
        router.push('/login');
        return;
      }

      // Load current user
      let userId: string | null = null;
      try {
        const userData = await api.users.getMyProfile() as Record<string, unknown>;
        const u = userData?.user ?? userData;
        userId = u?.id || null;
        dispatch({ type: 'SET_CURRENT_USER', userId });
      } catch {
        // Non-critical
      }

      // Load IAM permissions
      let access: HomeAccessState = { permissions: [], role_base: null, isOwner: false };
      try {
        const accessRes = await api.homeIam.getMyHomeAccess(homeId);
        access = {
          permissions: accessRes.permissions || [],
          role_base: accessRes.role_base || null,
          isOwner: accessRes.isOwner || false,
        };
      } catch {
        // IAM endpoint not deployed yet — gracefully default
      }

      // Try the aggregate endpoint first, fall back to individual calls
      const result: Partial<State> = { myAccess: access };
      try {
        const dash = await api.homeProfile.getHomeDashboard(homeId) as Record<string, unknown>;
        result.home = dash.home;
        result.members = dash.members || [];
        result.tasks = dash.tasks || [];
        result.issues = dash.issues || [];
        result.bills = dash.bills || [];
        result.packages = dash.packages || [];
        result.documents = dash.documents || [];
        result.events = dash.events || [];

        if (dash.nearbyGigs || dash.nearby_gigs) result.nearbyGigs = dash.nearbyGigs || dash.nearby_gigs || [];
        if (dash.homeGigs || dash.home_gigs) result.homeGigs = dash.homeGigs || dash.home_gigs || [];

        if (dash.myAccess) {
          const dm = dash.myAccess as {
            permissions?: string[];
            role_base?: string | null;
            isOwner?: boolean;
          };
          const mergedRole = dm.role_base ?? access.role_base ?? null;
          // Dashboard getUserAccess must align with GET /api/homes/:id/me; if either says owner-like, keep it.
          const ownerLike =
            !!dm.isOwner ||
            mergedRole === 'owner' ||
            access.isOwner ||
            access.role_base === 'owner';
          result.myAccess = {
            permissions: dm.permissions || access.permissions || [],
            role_base: mergedRole,
            isOwner: ownerLike,
          };
        }
      } catch {
        // Fallback: load individually
        const [homeRes, membersRes] = await Promise.allSettled([
          api.homes.getHome(homeId),
          api.homes.getHomeOccupants(homeId),
        ]);

        if (homeRes.status === 'fulfilled') result.home = (homeRes.value as Record<string, unknown>).home as Record<string, unknown>;
        if (membersRes.status === 'fulfilled') {
          const membersData = membersRes.value as Record<string, unknown>;
          result.members = [...((membersData.occupants as Record<string, unknown>[]) || []), ...((membersData.pendingInvites as Record<string, unknown>[]) || [])];
        }

        const [tasksRes, issuesRes, billsRes, pkgRes, docsRes] = await Promise.allSettled([
          api.homeProfile.getHomeTasks(homeId),
          api.homeProfile.getHomeIssues(homeId),
          api.homeProfile.getHomeBills(homeId),
          api.homeProfile.getHomePackages(homeId),
          api.homeProfile.getHomeDocuments(homeId),
        ]);

        if (tasksRes.status === 'fulfilled') result.tasks = (tasksRes.value as Record<string, unknown>).tasks as Record<string, unknown>[] || [];
        if (issuesRes.status === 'fulfilled') result.issues = (issuesRes.value as Record<string, unknown>).issues as Record<string, unknown>[] || [];
        if (billsRes.status === 'fulfilled') result.bills = (billsRes.value as Record<string, unknown>).bills as Record<string, unknown>[] || [];
        if (pkgRes.status === 'fulfilled') result.packages = (pkgRes.value as Record<string, unknown>).packages as Record<string, unknown>[] || [];
        if (docsRes.status === 'fulfilled') result.documents = (docsRes.value as Record<string, unknown>).documents as Record<string, unknown>[] || [];

        try {
          const [nearbyRes, homeGigsRes] = await Promise.allSettled([
            api.homeProfile.getNearbyGigs(homeId, { limit: 10 }),
            api.homeProfile.getHomeGigs(homeId, { limit: 20 }),
          ]);
          if (nearbyRes.status === 'fulfilled') result.nearbyGigs = (nearbyRes.value as Record<string, unknown>).gigs as Record<string, unknown>[] || [];
          if (homeGigsRes.status === 'fulfilled') result.homeGigs = (homeGigsRes.value as Record<string, unknown>).gigs as Record<string, unknown>[] || [];
        } catch {
          result.nearbyGigs = [];
          result.homeGigs = [];
        }
      }

      // Load sensitive data separately
      try {
        const [secretsRes, emergRes] = await Promise.allSettled([
          api.homeProfile.getHomeAccessSecrets(homeId),
          api.homeProfile.getHomeEmergencies(homeId),
        ]);
        if (secretsRes.status === 'fulfilled') result.secrets = (secretsRes.value as Record<string, unknown>).secrets as Record<string, unknown>[] || [];
        if (emergRes.status === 'fulfilled') result.emergencies = (emergRes.value as Record<string, unknown>).emergencies as Record<string, unknown>[] || [];
      } catch {
        // permission denied — fine
      }

      // Load pets and polls
      try {
        const [petsRes, pollsRes] = await Promise.allSettled([
          api.homeProfile.getHomePets(homeId),
          api.homeProfile.getHomePolls(homeId),
        ]);
        if (petsRes.status === 'fulfilled') result.pets = (petsRes.value as Record<string, unknown>).pets as Record<string, unknown>[] || [];
        if (pollsRes.status === 'fulfilled') result.polls = (pollsRes.value as Record<string, unknown>).polls as Record<string, unknown>[] || [];
      } catch {
        // endpoints not available yet
      }

      dispatch({ type: 'LOAD_COMPLETE', data: result });
    } catch (e: unknown) {
      dispatch({
        type: 'LOAD_ERROR',
        error: e instanceof Error ? e.message : 'Failed to load home dashboard',
      });
    }
  }, [homeId, router]);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  const refreshEntity = useCallback(
    async (entity: keyof HomeDataEntities) => {
      const fetcher = ENTITY_FETCHERS[entity];
      if (!fetcher) return;
      try {
        const { data } = await fetcher(homeId);
        dispatch({ type: 'SET_ENTITY', entity, data });
      } catch {
        // silently fail for individual refresh
      }
    },
    [homeId]
  );

  const can = useCallback(
    (perm: string): boolean => {
      if (state.myAccess.isOwner || state.home?.owner_id === state.currentUserId) return true;
      if (state.myAccess.permissions.length === 0 && !state.myAccess.role_base) return true;
      return state.myAccess.permissions.includes(perm);
    },
    [state.myAccess, state.home?.owner_id, state.currentUserId]
  );

  const makeEntityUpdater = useCallback(
    (entity: keyof HomeDataEntities) => (updater: (prev: Record<string, unknown>[]) => Record<string, unknown>[]) => {
      dispatch({ type: 'UPDATE_ENTITY', entity, updater });
    },
    []
  );

  return {
    // Entities
    home: state.home,
    members: state.members,
    tasks: state.tasks,
    issues: state.issues,
    bills: state.bills,
    packages: state.packages,
    documents: state.documents,
    events: state.events,
    secrets: state.secrets,
    emergencies: state.emergencies,
    nearbyGigs: state.nearbyGigs,
    homeGigs: state.homeGigs,
    pets: state.pets,
    polls: state.polls,
    // Meta
    loading: state.loading,
    error: state.error,
    currentUserId: state.currentUserId,
    myAccess: state.myAccess,
    can,
    refresh: loadDashboard,
    refreshEntity,
    // Optimistic updaters
    setTasks: makeEntityUpdater('tasks'),
    setIssues: makeEntityUpdater('issues'),
    setBills: makeEntityUpdater('bills'),
    setPackages: makeEntityUpdater('packages'),
    setMembers: makeEntityUpdater('members'),
    setSecrets: makeEntityUpdater('secrets'),
  };
}
