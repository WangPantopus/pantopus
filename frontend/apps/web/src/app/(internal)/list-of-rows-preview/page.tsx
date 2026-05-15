'use client';

// Pantopus — `<ListOfRowsShell />` preview canvas.
//
// Designer sanity check: every new T5 row shape rendered side-by-side
// with the original A/B shapes so the designer can spot drift before
// any feature PR ships. Not linked from the navigation — accessed
// directly via `/list-of-rows-preview`.

import { useState } from 'react';
import {
  Bell,
  Hammer,
  Send,
  Heart,
  X as XIcon,
  Check,
  Star,
  Inbox,
  File as FileIcon,
  AlertCircle,
  Search,
  MapPin,
} from 'lucide-react';
import { ListOfRowsShell, type RowModel, type ListOfRowsState } from '@/components/list-of-rows';

export default function ListOfRowsPreviewPage() {
  const [tab, setTab] = useState('populated');

  const rows: RowModel[] = [
    // A. icon + title + subtitle + trailing status chip (existing)
    {
      id: 'a',
      template: 'statusChip',
      title: 'A — icon + status chip (existing)',
      subtitle: 'Original render path; unchanged.',
      leading: { kind: 'icon', icon: Bell, tint: 'rgb(2 132 199)' },
      trailing: { kind: 'statusChip', text: 'NEW', variant: 'info' },
    },
    // B. avatar + title + subtitle + trailing kebab (existing)
    {
      id: 'b',
      template: 'avatarKebab',
      title: 'B — avatar + kebab (existing)',
      subtitle: 'Original render path; identity-ringed avatar + 44pt kebab tap target.',
      leading: {
        kind: 'avatar',
        name: 'Avery Park',
        identity: 'personal',
        ringProgress: 0.7,
      },
      trailing: { kind: 'kebab' },
      onSecondary: () => {},
    },
    // C. category gradient icon + price stack + footer
    {
      id: 'c',
      template: 'statusChip',
      title: 'Mount 65″ TV above brick fireplace · drill anchors needed',
      subtitle: 'for Sarah Kowalski · Elm Park · 2d ago',
      leading: {
        kind: 'categoryGradientIcon',
        icon: Hammer,
        gradient: { start: '#60a5fa', end: '#1d4ed8' },
      },
      trailing: { kind: 'priceStack', amount: '$95', sublabel: 'budget $120' },
      chips: [{ text: 'Top bid', icon: Check, tint: { kind: 'status', variant: 'success' } }],
      metaTail: '· 3 others bid · 1d left to reply',
      footer: {
        actions: [
          { title: 'Withdraw', icon: XIcon, variant: 'destructive', onClick: () => {} },
          { title: 'Edit bid', icon: Check, variant: 'primary', onClick: () => {} },
        ],
      },
    },
    // D. type icon + body + unread highlight
    {
      id: 'd',
      template: 'statusChip',
      title: 'Maria Kovács replied to your gig',
      leading: {
        kind: 'typeIcon',
        icon: Send,
        background: 'rgb(219 234 254)',
        foreground: 'rgb(29 78 216)',
      },
      body: '"Sounds great — can we move it to Saturday morning instead of Friday?"',
      chips: [{ text: 'Reply', icon: Send, tint: { kind: 'status', variant: 'personal' } }],
      timeMeta: '12m',
      highlight: 'unread',
    },
    // E. 64pt thumbnail + inline chip + kebab
    {
      id: 'e',
      template: 'avatarKebab',
      title: 'Mango',
      subtitle: 'Golden Retriever · 3 yr',
      leading: {
        kind: 'thumbnail',
        image: {
          kind: 'icon',
          icon: Heart,
          gradient: { start: '#fed7aa', end: '#fb923c' },
        },
        size: 'large',
      },
      trailing: { kind: 'kebab' },
      inlineChip: {
        text: 'Dog',
        tint: { kind: 'custom', background: '#ffedd5', foreground: '#9a3412' },
      },
      onSecondary: () => {},
    },
    // F. avatar with verified badge + circular CTA
    {
      id: 'f',
      template: 'avatarKebab',
      title: 'Maria Kovács',
      subtitle: 'Elm Park · 0.2 mi',
      leading: {
        kind: 'avatarWithBadge',
        name: 'Maria Kovács',
        background: { kind: 'gradient', gradient: { start: '#0ea5e9', end: '#0369a1' } },
        size: 'large',
        verified: true,
      },
      trailing: {
        kind: 'circularAction',
        icon: Send,
        accessibilityLabel: 'Message Maria',
        onClick: () => {},
      },
      body: 'Last chat 2 days ago',
    },
    // F2. avatar pending — vertical actions
    {
      id: 'f2',
      template: 'avatarKebab',
      title: 'Sofia Romero',
      subtitle: 'Elm Park · 0.5 mi',
      leading: {
        kind: 'avatarWithBadge',
        name: 'Sofia Romero',
        background: { kind: 'gradient', gradient: { start: '#ec4899', end: '#be185d' } },
        size: 'large',
        verified: false,
      },
      trailing: {
        kind: 'verticalActions',
        primary: { label: 'Accept', variant: 'primary', onClick: () => {} },
        secondary: { label: 'Ignore', variant: 'ghost', onClick: () => {} },
      },
    },
    // G. receipt icon + amount with chip (Bills)
    {
      id: 'g',
      template: 'statusChip',
      title: 'ConEd Electric',
      subtitle: 'Oct 15',
      leading: {
        kind: 'typeIcon',
        icon: FileIcon,
        background: 'rgb(240 249 255)',
        foreground: 'rgb(2 132 199)',
      },
      trailing: {
        kind: 'amountWithChip',
        amount: '$142.80',
        chipText: 'Due Oct 15',
        chipVariant: 'warning',
        chipIcon: AlertCircle,
      },
    },
    // C2. bidder stack + status chip (My tasks)
    {
      id: 'c2',
      template: 'statusChip',
      title: 'Saturday move help, 2 hours, a few boxes + couch',
      leading: {
        kind: 'bidderStack',
        bidders: [
          { id: '1', initials: 'IB', tone: 'rose' },
          { id: '2', initials: 'LP', tone: 'sky' },
          { id: '3', initials: 'CR', tone: 'amber' },
        ],
        overflow: 4,
      },
      trailing: { kind: 'priceStack', amount: '$80/hr' },
      chips: [{ text: 'Reviewing bids', icon: Inbox, tint: { kind: 'status', variant: 'info' } }],
    },
    // Leading offer with note (Listing offers)
    {
      id: 'lead',
      template: 'avatarKebab',
      title: 'Anika Reyes',
      subtitle: 'Elm Park · 4.9 ★ · 12 trades',
      leading: {
        kind: 'avatarWithBadge',
        name: 'Anika Reyes',
        background: { kind: 'gradient', gradient: { start: '#a78bfa', end: '#6d28d9' } },
        size: 'medium',
      },
      trailing: { kind: 'priceStack', amount: '$240' },
      chips: [{ text: 'New', icon: Star, tint: { kind: 'status', variant: 'personal' } }],
      timeMeta: '12m',
      note: 'Love the dovetail joinery. Can pick up Saturday in my truck.',
      highlight: 'leading',
    },
    // Archived (My posts)
    {
      id: 'arch',
      template: 'statusChip',
      title: 'Found a black Lab mix near 19th and Tillamook',
      body: 'Friendly, no tag, faded blue collar. Holding her at my place until owner shows up.',
      leading: { kind: 'icon', icon: Heart, tint: 'rgb(127 29 29)' },
      chips: [{ text: 'Lost & Found', tint: { kind: 'custom', background: '#ffe4e6', foreground: '#be123c' } }],
      timeMeta: '4d',
      highlight: 'archived',
    },
  ];

  const loadedState: ListOfRowsState = {
    kind: 'loaded',
    sections: [
      {
        id: 'rows',
        header: 'Every variant',
        rows,
      },
    ],
    hasMore: false,
  };

  const loadingState: ListOfRowsState = { kind: 'loading' };
  const emptyState: ListOfRowsState = {
    kind: 'empty',
    config: {
      icon: Bell,
      headline: "You're all caught up",
      subcopy: 'No unread notifications.',
      ctaTitle: 'View all',
      onCta: () => setTab('populated'),
    },
  };
  const errorState: ListOfRowsState = {
    kind: 'error',
    message: 'The list failed to load. Tap "Try again" to retry.',
  };

  const state: ListOfRowsState =
    tab === 'loading'
      ? loadingState
      : tab === 'empty'
        ? emptyState
        : tab === 'error'
          ? errorState
          : loadedState;

  return (
    <div className="max-w-md mx-auto min-h-screen">
      <ListOfRowsShell
        title="ListOfRows preview"
        state={state}
        tabs={[
          { id: 'populated', label: 'Populated', count: rows.length },
          { id: 'loading', label: 'Loading' },
          { id: 'empty', label: 'Empty' },
          { id: 'error', label: 'Error' },
        ]}
        selectedTab={tab}
        onTabChange={setTab}
        topBarAction={{
          icon: Search,
          accessibilityLabel: 'Search',
          onClick: () => {},
        }}
        banner={{
          icon: MapPin,
          title: '9 new variants since yesterday',
          subtitle: '1 archetype upgrade closing in 0h',
        }}
        fab={{
          icon: Send,
          accessibilityLabel: 'Test FAB',
          variant: { kind: 'extendedNav', label: 'Test action' },
          onClick: () => {},
        }}
      />
    </div>
  );
}
