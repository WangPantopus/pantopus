// Types and constants specific to the Discover page

export type ViewMode = 'list' | 'map';
export type SearchScope = 'all' | 'people' | 'businesses' | 'tasks' | 'listings';

export const PAGE_SIZE = 20;

export const SCOPE_TABS: { key: SearchScope; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'people', label: 'People' },
  { key: 'businesses', label: 'Businesses' },
  { key: 'tasks', label: 'Tasks' },
  { key: 'listings', label: 'Listings' },
];

export interface UnifiedResult {
  id: string;
  type: 'person' | 'business' | 'task' | 'listing';
  title: string;
  subtitle?: string;
  meta?: string;
  imageUrl?: string | null;
  href: string;
}
