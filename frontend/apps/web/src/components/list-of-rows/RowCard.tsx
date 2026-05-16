'use client';

// Pantopus — `<RowCard />` renders one `RowModel` covering every T5 row
// shape (A through G in the design brief). Token-only: all colours come
// from `@pantopus/theme` via Tailwind utility classes (`bg-app-surface`,
// `text-primary-600`, `border-app-border`, etc.).

import { ChevronRight, MoreHorizontal, Check as CheckIcon } from 'lucide-react';
import type {
  BidderTone,
  RowChip,
  RowFooterAction,
  RowLeading,
  RowModel,
  RowTrailing,
  StatusChipVariant,
} from './types';

interface RowCardProps {
  row: RowModel;
  /**
   * `standalone` renders the row as a free-standing card (default).
   * `grouped` strips background + border so the parent's section card
   * can paint them (Discover hub `SectionStyle.card`).
   */
  context?: 'standalone' | 'grouped';
  /** Set to true when this is the last row of a `card` section — controls
   *  bottom hairline divider. */
  isLastInGroup?: boolean;
}

export default function RowCard({ row, context = 'standalone', isLastInGroup = false }: RowCardProps) {
  const grouped = context === 'grouped';
  const cardClasses = grouped
    ? `flex flex-col gap-2 px-4 py-3 ${isLastInGroup ? '' : 'border-b border-app-border-subtle'}`
    : [
        'flex flex-col gap-2 rounded-xl px-4 py-3 border',
        row.highlight === 'unread'
          ? 'bg-primary-25 border-app-personal-bg'
          : row.highlight === 'leading'
            ? 'bg-app-surface border-app-warning'
            : 'bg-app-surface border-app-border',
        row.highlight === 'archived' || row.highlight === 'muted' ? 'opacity-[0.78]' : '',
      ]
        .filter(Boolean)
        .join(' ');

  const handleCardClick = () => {
    if (row.onTap) row.onTap();
  };

  return (
    <div
      className={cardClasses}
      onClick={handleCardClick}
      role={row.onTap ? 'button' : undefined}
      tabIndex={row.onTap ? 0 : undefined}
      onKeyDown={(e) => {
        if ((e.key === 'Enter' || e.key === ' ') && row.onTap) {
          e.preventDefault();
          row.onTap();
        }
      }}
    >
      {row.highlight === 'leading' && <LeadingBadge />}
      <div className="flex items-start gap-3">
        <Leading leading={row.leading ?? { kind: 'none' }} />
        <ContentColumn row={row} />
        <Trailing
          trailing={row.trailing ?? { kind: 'none' }}
          onSecondary={row.onSecondary}
          rowTitle={row.title}
        />
      </div>
      {row.note && <NoteBlock text={row.note} />}
      {row.footer && <FooterStack footer={row.footer} />}
    </div>
  );
}

// ─── Content column ────────────────────────────────────────────

function ContentColumn({ row }: { row: RowModel }) {
  return (
    <div className="flex-1 min-w-0">
      <div className="flex items-center gap-1.5">
        <span
          className={`flex-1 min-w-0 text-sm leading-snug tracking-[-0.01em] line-clamp-2 ${
            row.highlight === 'unread' ? 'font-bold text-app-text' : 'font-semibold text-app-text'
          }`}
        >
          {row.title}
        </span>
        {row.inlineChip && <ChipPill chip={row.inlineChip} />}
        {row.highlight === 'unread' && (
          <span
            aria-hidden="true"
            className="inline-block w-2 h-2 rounded-full bg-primary-600 shrink-0"
          />
        )}
      </div>
      {row.subtitle && (
        <div className="mt-0.5 text-xs text-app-text-secondary line-clamp-2">{row.subtitle}</div>
      )}
      {row.body && (
        <div className="mt-1 text-xs text-app-text-secondary line-clamp-2">{row.body}</div>
      )}
      {row.chips && row.chips.length > 0 && (
        <ChipRow chips={row.chips} timeMeta={row.timeMeta} metaTail={row.metaTail} />
      )}
    </div>
  );
}

// ─── Leading ───────────────────────────────────────────────────

function Leading({ leading }: { leading: RowLeading }) {
  switch (leading.kind) {
    case 'none':
      return null;
    case 'icon': {
      const Icon = leading.icon;
      return (
        <div className="w-10 h-10 rounded-lg bg-app-surface-sunken flex items-center justify-center shrink-0">
          <Icon className="w-5 h-5" style={{ color: leading.tint ?? 'rgb(2 132 199)' }} />
        </div>
      );
    }
    case 'avatar': {
      // Plain initials disk — avatar-with-identity-ring is a P-screen
      // detail; shell renders the simpler fallback shape.
      const initials = initialsFor(leading.name);
      return (
        <div className="w-10 h-10 rounded-full bg-app-personal-bg text-app-personal flex items-center justify-center text-sm font-semibold shrink-0">
          {initials}
        </div>
      );
    }
    case 'typeIcon': {
      const Icon = leading.icon;
      return (
        <div
          className="w-10 h-10 rounded-lg flex items-center justify-center shrink-0"
          style={{ background: leading.background }}
        >
          <Icon className="w-[19px] h-[19px]" style={{ color: leading.foreground }} />
        </div>
      );
    }
    case 'categoryGradientIcon': {
      const Icon = leading.icon;
      return (
        <div
          className="w-10 h-10 rounded-lg flex items-center justify-center shrink-0"
          style={{
            background: `linear-gradient(135deg, ${leading.gradient.start}, ${leading.gradient.end})`,
          }}
        >
          <Icon className="w-5 h-5 text-white" />
        </div>
      );
    }
    case 'avatarWithBadge': {
      const sizePx = leading.size === 'small' ? 36 : leading.size === 'medium' ? 40 : 44;
      const initials = initialsFor(leading.name);
      const bgStyle =
        leading.background.kind === 'solid'
          ? { background: leading.background.color }
          : {
              background: `linear-gradient(135deg, ${leading.background.gradient.start}, ${leading.background.gradient.end})`,
            };
      return (
        <div className="relative shrink-0" style={{ width: sizePx, height: sizePx }}>
          <div
            className="rounded-full text-white font-bold flex items-center justify-center"
            style={{
              width: sizePx,
              height: sizePx,
              fontSize: Math.round(sizePx * 0.32),
              ...bgStyle,
            }}
          >
            {initials}
          </div>
          {leading.verified && (
            <span
              className="absolute -right-0.5 -bottom-0.5 w-4 h-4 rounded-full bg-app-home border-2 border-app-surface flex items-center justify-center"
              aria-label="Verified"
            >
              <CheckIcon className="w-[9px] h-[9px] text-white" strokeWidth={4} />
            </span>
          )}
        </div>
      );
    }
    case 'thumbnail': {
      const sizePx = leading.size === 'medium' ? 56 : 64;
      const Icon =
        leading.image.kind === 'icon' ? leading.image.icon : leading.image.fallback;
      const gradient = leading.image.gradient;
      const overlayUrl = leading.image.kind === 'url' ? leading.image.url : null;
      return (
        <div
          className="rounded-lg flex items-center justify-center shrink-0 overflow-hidden relative"
          style={{
            width: sizePx,
            height: sizePx,
            background: `linear-gradient(135deg, ${gradient.start}, ${gradient.end})`,
          }}
        >
          {overlayUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={overlayUrl}
              alt=""
              className="absolute inset-0 w-full h-full object-cover"
            />
          ) : (
            <Icon
              className="text-white"
              style={{ width: sizePx * 0.42, height: sizePx * 0.42 }}
            />
          )}
        </div>
      );
    }
    case 'bidderStack': {
      const overflow = leading.overflow ?? 0;
      return (
        <div className="flex items-center shrink-0">
          {leading.bidders.map((b, i) => (
            <span
              key={b.id}
              className={`w-[22px] h-[22px] rounded-full border-2 border-app-surface flex items-center justify-center text-[8px] font-semibold ${
                i === 0 ? '' : '-ml-2'
              } ${toneClasses(b.tone)}`}
            >
              {b.initials.slice(0, 2).toUpperCase()}
            </span>
          ))}
          {overflow > 0 && (
            <span
              className={`w-[22px] h-[22px] rounded-full border-2 border-app-surface flex items-center justify-center text-[9px] font-bold bg-app-surface-sunken text-app-text-strong ${
                leading.bidders.length === 0 ? '' : '-ml-2'
              }`}
            >
              +{overflow}
            </span>
          )}
        </div>
      );
    }
  }
}

function toneClasses(tone: BidderTone): string {
  switch (tone) {
    case 'sky':
      return 'bg-primary-200 text-primary-800';
    case 'teal':
      return 'bg-app-success-light text-app-success';
    case 'amber':
      return 'bg-app-warning-light text-app-warning';
    case 'rose':
      return 'bg-app-error-light text-app-error';
    case 'violet':
      return 'bg-app-business-bg text-app-business';
    case 'slate':
      return 'bg-app-surface-sunken text-app-text-strong';
  }
}

function initialsFor(name: string): string {
  const parts = name.split(/\s+/).slice(0, 2);
  return parts
    .map((p) => p.charAt(0))
    .join('')
    .toUpperCase();
}

// ─── Trailing ──────────────────────────────────────────────────

function Trailing({
  trailing,
  onSecondary,
  rowTitle,
}: {
  trailing: RowTrailing;
  onSecondary?: () => void;
  rowTitle: string;
}) {
  switch (trailing.kind) {
    case 'none':
      return null;
    case 'statusChip':
      return (
        <ChipPill
          chip={{
            text: trailing.text,
            icon: trailing.icon,
            tint: { kind: 'status', variant: trailing.variant },
          }}
        />
      );
    case 'chevron':
      return <ChevronRight className="w-[18px] h-[18px] text-app-text-secondary shrink-0" />;
    case 'kebab':
      if (!onSecondary) return null;
      return (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onSecondary();
          }}
          className="w-11 h-11 -my-1 flex items-center justify-center text-app-text-secondary hover:text-app-text rounded-lg shrink-0"
          aria-label={`More actions for ${rowTitle}`}
        >
          <MoreHorizontal className="w-5 h-5" />
        </button>
      );
    case 'amountWithChip':
      return (
        <div className="flex flex-col items-end gap-1 shrink-0">
          <span className="text-sm font-bold text-app-text tracking-tight">
            {trailing.amount}
          </span>
          <ChipPill
            chip={{
              text: trailing.chipText,
              icon: trailing.chipIcon,
              tint: { kind: 'status', variant: trailing.chipVariant },
            }}
          />
        </div>
      );
    case 'circularAction': {
      const Icon = trailing.icon;
      return (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            trailing.onClick();
          }}
          aria-label={trailing.accessibilityLabel}
          className="w-[38px] h-[38px] rounded-full flex items-center justify-center shrink-0"
          style={{
            background: trailing.background ?? 'rgb(240 249 255)',
            color: trailing.foreground ?? 'rgb(2 132 199)',
          }}
        >
          <Icon className="w-[17px] h-[17px]" />
        </button>
      );
    }
    case 'verticalActions':
      return (
        <div className="flex flex-col gap-1 shrink-0 w-24">
          <CompactInlineButton action={trailing.primary} />
          <CompactInlineButton action={trailing.secondary} />
        </div>
      );
    case 'priceStack':
      return (
        <div className="flex flex-col items-end gap-0.5 shrink-0">
          <span className="text-sm font-bold text-app-text tracking-tight">
            {trailing.amount}
          </span>
          {trailing.sublabel && (
            <span className="text-[10px] text-app-text-muted">{trailing.sublabel}</span>
          )}
        </div>
      );
  }
}

function CompactInlineButton({
  action,
}: {
  action: { label: string; variant: 'primary' | 'ghost' | 'destructive'; onClick: () => void };
}) {
  const palette = compactPalette(action.variant);
  const height = action.variant === 'ghost' ? 28 : 30;
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        action.onClick();
      }}
      className={`px-3 rounded-lg text-xs font-semibold transition ${palette}`}
      style={{ height }}
    >
      {action.label}
    </button>
  );
}

function compactPalette(variant: 'primary' | 'ghost' | 'destructive'): string {
  switch (variant) {
    case 'primary':
      return 'bg-primary-600 text-white hover:bg-primary-700';
    case 'ghost':
      return 'bg-app-surface text-app-text-strong border border-app-border hover:bg-app-hover';
    case 'destructive':
      return 'bg-app-surface text-app-error border border-app-border hover:bg-app-error-bg';
  }
}

// ─── Chip row ──────────────────────────────────────────────────

function ChipRow({
  chips,
  timeMeta,
  metaTail,
}: {
  chips: RowChip[];
  timeMeta?: string;
  metaTail?: string;
}) {
  return (
    <div className="mt-1 flex items-center gap-1 flex-wrap">
      {chips.map((chip, i) => (
        <ChipPill key={i} chip={chip} />
      ))}
      {metaTail && (
        <span className="text-[10.5px] text-app-text-muted truncate">{metaTail}</span>
      )}
      <span className="flex-1" />
      {timeMeta && <span className="text-[10.5px] text-app-text-muted">{timeMeta}</span>}
    </div>
  );
}

function ChipPill({ chip }: { chip: RowChip }) {
  const palette = chipTintClasses(chip.tint);
  const Icon = chip.icon;
  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-[3px] rounded-full text-[10px] font-semibold whitespace-nowrap ${palette.className}`}
      style={palette.style}
    >
      {Icon && <Icon className="w-2.5 h-2.5" />}
      {chip.text}
    </span>
  );
}

function chipTintClasses(tint: RowChip['tint']): {
  className: string;
  style?: React.CSSProperties;
} {
  if (tint.kind === 'status') return { className: statusChipClasses(tint.variant) };
  return {
    className: '',
    style: { background: tint.background, color: tint.foreground },
  };
}

function statusChipClasses(variant: StatusChipVariant): string {
  switch (variant) {
    case 'success':
      return 'bg-app-success-bg text-app-success';
    case 'warning':
      return 'bg-app-warning-bg text-app-warning';
    case 'error':
      return 'bg-app-error-bg text-app-error';
    case 'info':
      return 'bg-app-info-bg text-app-info';
    case 'personal':
      return 'bg-app-personal-bg text-app-personal';
    case 'home':
      return 'bg-app-home-bg text-app-home';
    case 'business':
      return 'bg-app-business-bg text-app-business';
    case 'neutral':
      return 'bg-app-surface-sunken text-app-text-secondary';
  }
}

// ─── Note + footer + leading badge ─────────────────────────────

function NoteBlock({ text }: { text: string }) {
  return (
    <div className="ml-13 pl-2 py-2 text-xs italic text-app-text-strong bg-app-surface-sunken rounded-md border-l-2 border-app-border max-w-full">
      “{text}”
    </div>
  );
}

function FooterStack({ footer }: { footer: { actions: RowFooterAction[] } }) {
  return (
    <div className="pt-3 mt-1 border-t border-app-border flex items-center gap-1.5">
      {footer.actions.map((action, i) => (
        <CompactFooterButton key={i} action={action} />
      ))}
    </div>
  );
}

function CompactFooterButton({ action }: { action: RowFooterAction }) {
  const palette = compactPalette(action.variant ?? 'primary');
  const Icon = action.icon;
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        action.onClick();
      }}
      className={`flex-1 h-[34px] px-3 rounded-md text-xs font-semibold inline-flex items-center justify-center gap-1.5 ${palette}`}
      style={action.flex && action.flex !== 1 ? { flex: action.flex } : undefined}
    >
      {Icon && <Icon className="w-3.5 h-3.5" />}
      {action.title}
    </button>
  );
}

function LeadingBadge() {
  return (
    <span className="self-start inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-app-warning text-white text-[9.5px] font-bold tracking-wide">
      LEADING
    </span>
  );
}
