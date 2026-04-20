'use client';

import Image from 'next/image';
import { useRouter } from 'next/navigation';
import { Search, User, Briefcase, ShoppingBag, Store } from 'lucide-react';
import type { UnifiedResult } from './discoverTypes';

const TYPE_STYLE: Record<string, { icon: typeof User; color: string; label: string }> = {
  person:   { icon: User,        color: 'text-blue-600 bg-blue-50',     label: 'Person' },
  business: { icon: Store,       color: 'text-emerald-600 bg-emerald-50', label: 'Business' },
  task:     { icon: Briefcase,   color: 'text-orange-600 bg-orange-50', label: 'Task' },
  listing:  { icon: ShoppingBag, color: 'text-purple-600 bg-purple-50', label: 'Listing' },
};

export function UnifiedResultCard({ item }: { item: UnifiedResult }) {
  const router = useRouter();
  const style = TYPE_STYLE[item.type];
  const Icon = style?.icon ?? Search;

  return (
    <button
      onClick={() => router.push(item.href)}
      className="w-full text-left flex items-center gap-3 p-3 bg-surface border border-app rounded-xl hover:bg-surface-raised transition"
    >
      {item.imageUrl ? (
        <Image src={item.imageUrl} alt="" className="w-10 h-10 rounded-lg object-cover flex-shrink-0" width={40} height={40} sizes="40px" quality={75} />
      ) : (
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 ${style?.color ?? 'text-app-muted bg-surface-muted'}`}>
          <Icon className="w-5 h-5" />
        </div>
      )}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-app truncate">{item.title}</p>
        {item.subtitle && <p className="text-xs text-app-muted truncate">{item.subtitle}</p>}
      </div>
      {item.meta && (
        <span className="text-xs font-semibold text-app-secondary flex-shrink-0">{item.meta}</span>
      )}
      <span className={`text-[10px] font-bold uppercase px-1.5 py-0.5 rounded ${style?.color ?? 'text-app-muted bg-surface-muted'}`}>
        {style?.label ?? item.type}
      </span>
    </button>
  );
}

export function UnifiedResultSkeleton() {
  return (
    <div className="flex items-center gap-3 p-3 bg-surface border border-app rounded-xl animate-pulse">
      <div className="w-10 h-10 rounded-lg bg-surface-muted flex-shrink-0" />
      <div className="flex-1 space-y-2">
        <div className="h-3.5 bg-surface-muted rounded w-2/3" />
        <div className="h-2.5 bg-surface-muted rounded w-1/3" />
      </div>
    </div>
  );
}

export default UnifiedResultCard;
