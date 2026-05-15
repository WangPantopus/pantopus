'use client';

import type { ListOfRowsTab } from './types';

interface Props {
  tabs: ListOfRowsTab[];
  selectedId: string;
  onSelect: (id: string) => void;
}

export default function TabStrip({ tabs, selectedId, onSelect }: Props) {
  return (
    <div
      role="tablist"
      className="flex border-b border-app-border bg-app-surface px-4 overflow-x-auto"
    >
      {tabs.map((tab) => {
        const active = tab.id === selectedId;
        return (
          <button
            key={tab.id}
            role="tab"
            aria-selected={active}
            onClick={() => onSelect(tab.id)}
            data-testid={`tab.${tab.id}`}
            className={`flex-1 min-w-[44px] py-3 px-1 text-sm transition-all duration-150 border-b-2 ${
              active
                ? 'border-primary-600 text-primary-600 font-semibold'
                : 'border-transparent text-app-text-secondary font-medium hover:text-app-text'
            }`}
          >
            <span className="inline-flex items-center gap-1 justify-center">
              {tab.label}
              {tab.count != null && (
                <span
                  className={`text-[10.5px] px-1.5 py-px rounded-full ${
                    active
                      ? 'bg-primary-50 text-primary-700'
                      : 'bg-app-surface-sunken text-app-text-secondary'
                  }`}
                >
                  {tab.count}
                </span>
              )}
            </span>
          </button>
        );
      })}
    </div>
  );
}
