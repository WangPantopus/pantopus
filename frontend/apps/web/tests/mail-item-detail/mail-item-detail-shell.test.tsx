/**
 * T6.5a (P19) — Tests for <MailItemDetailShell />.
 *
 * Covers:
 *   - shell renders with every slot supplied,
 *   - shell renders with only required slots (top bar + hero),
 *   - nil optional payloads (aiElf, attachments) are skipped without
 *     rendering empty containers,
 *   - top bar overflow menu surfaces every item and dispatches onSelect.
 */

import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { Archive, Bell, Bookmark, Calendar, MapPin, Pencil, Send, Trash2 } from 'lucide-react';
import {
  MailItemDetailShell,
  type AIElfStripContent,
  type AttachmentsRowContent,
  type MailTopBarConfig,
} from '../../src/components/mail-item-detail';

function makeTopBar(overrides: Partial<MailTopBarConfig> = {}): MailTopBarConfig {
  return {
    eyebrow: 'Certified',
    trust: 'verified',
    onBack: () => {},
    overflowItems: [
      { id: 'forward', icon: Send, label: 'Forward', onSelect: () => {} },
      { id: 'archive', icon: Archive, label: 'Archive', onSelect: () => {} },
      { id: 'delete', icon: Trash2, label: 'Delete', isDestructive: true, onSelect: () => {} },
    ],
    ...overrides,
  };
}

function makeAIElf(): AIElfStripContent {
  return {
    summary: 'Your neighbor wants a setback variance. Hearing June 3.',
    bullets: [
      { id: '1', icon: MapPin, label: 'Affects 412 Elm St', text: 'next door' },
      { id: '2', icon: Calendar, label: 'Hearing Tue Jun 3', text: '6:00 PM' },
      { id: '3', icon: Pencil, label: 'Comments by May 30', text: 'optional' },
    ],
    trailingBadge: '2 min summary',
  };
}

function makeAttachments(): AttachmentsRowContent {
  return {
    items: [
      { id: 'a1', kind: 'pdf', name: 'Public notice ZA-2026-0188.pdf', meta: '2 pages · 84 KB' },
      { id: 'a2', kind: 'image', name: 'Site plan.jpg', meta: '1.2 MB' },
      { id: 'a3', kind: 'video', name: 'Reading.mp4', meta: '1m 22s' },
    ],
  };
}

describe('<MailItemDetailShell />', () => {
  it('renders every slot when all are supplied', () => {
    render(
      <MailItemDetailShell
        topBar={makeTopBar()}
        aiElf={makeAIElf()}
        attachments={makeAttachments()}
        hero={<div>Hero card</div>}
        keyFacts={<div>Key facts</div>}
        body={<div>Body</div>}
        sender={<div>Sender</div>}
        actions={<button type="button">Acknowledge</button>}
      />,
    );

    expect(screen.getByTestId('mailItemDetailShell')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_topBar')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_hero')).toHaveTextContent('Hero card');
    expect(screen.getByTestId('mailItemDetail_aiElf')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_keyFacts')).toHaveTextContent('Key facts');
    expect(screen.getByTestId('mailItemDetail_body')).toHaveTextContent('Body');
    expect(screen.getByTestId('mailItemDetail_attachments')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_sender')).toHaveTextContent('Sender');
    expect(screen.getByTestId('mailItemDetail_actions')).toHaveTextContent('Acknowledge');
  });

  it('renders with only the required slots (top bar + hero)', () => {
    render(
      <MailItemDetailShell
        topBar={{
          eyebrow: 'Booklet',
          trust: 'verified',
          onBack: () => {},
        }}
        hero={<div>Hero</div>}
      />,
    );

    expect(screen.getByTestId('mailItemDetailShell')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_topBar')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_hero')).toBeInTheDocument();

    // Optional slots / sections must NOT render when not supplied.
    expect(screen.queryByTestId('mailItemDetail_aiElf')).toBeNull();
    expect(screen.queryByTestId('mailItemDetail_keyFacts')).toBeNull();
    expect(screen.queryByTestId('mailItemDetail_body')).toBeNull();
    expect(screen.queryByTestId('mailItemDetail_attachments')).toBeNull();
    expect(screen.queryByTestId('mailItemDetail_sender')).toBeNull();
    expect(screen.queryByTestId('mailItemDetail_actions')).toBeNull();
  });

  it('skips nil optional payloads (aiElf, attachments) without rendering empty containers', () => {
    render(
      <MailItemDetailShell
        topBar={makeTopBar()}
        aiElf={null}
        attachments={null}
        hero={<div>Hero</div>}
        keyFacts={<div>Facts</div>}
        body={<div>Body</div>}
        sender={<div>Sender</div>}
        actions={<button type="button">Action</button>}
      />,
    );

    expect(screen.queryByTestId('mailItemDetail_aiElf')).toBeNull();
    expect(screen.queryByTestId('mailItemDetail_attachments')).toBeNull();
    // But the other generic slots still rendered.
    expect(screen.getByTestId('mailItemDetail_keyFacts')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_body')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_sender')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_actions')).toBeInTheDocument();
  });

  it('renders the eyebrow trust dot from config', () => {
    render(
      <MailItemDetailShell
        topBar={{ eyebrow: 'Notice', trust: 'warning', onBack: () => {} }}
        hero={<div>Hero</div>}
      />,
    );
    expect(screen.getByTestId('mailItemDetail_eyebrow')).toHaveTextContent('NOTICE');
  });

  it('renders the trailing action when supplied + reflects the active state', () => {
    const onSave = jest.fn();
    render(
      <MailItemDetailShell
        topBar={{
          eyebrow: 'Booklet',
          trust: 'verified',
          onBack: () => {},
          trailingAction: {
            icon: Bookmark,
            accessibilityLabel: 'Save to vault',
            isActive: true,
            onClick: onSave,
          },
        }}
        hero={<div>Hero</div>}
      />,
    );
    const btn = screen.getByTestId('mailItemDetail_trailingAction');
    expect(btn).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(btn);
    expect(onSave).toHaveBeenCalledTimes(1);
  });

  it('renders overflow menu items and dispatches onSelect', () => {
    const onForward = jest.fn();
    const onDelete = jest.fn();
    render(
      <MailItemDetailShell
        topBar={{
          eyebrow: 'Certified',
          trust: 'verified',
          onBack: () => {},
          overflowItems: [
            { id: 'forward', icon: Send, label: 'Forward', onSelect: onForward },
            { id: 'delete', icon: Trash2, label: 'Delete', isDestructive: true, onSelect: onDelete },
          ],
        }}
        hero={<div>Hero</div>}
      />,
    );
    // Open the <details>-based menu so JSDOM exposes the items.
    const menu = screen.getByTestId('mailItemDetail_overflow') as HTMLDetailsElement;
    menu.open = true;
    fireEvent.click(screen.getByTestId('mailItemDetail_overflowItem_forward'));
    fireEvent.click(screen.getByTestId('mailItemDetail_overflowItem_delete'));
    expect(onForward).toHaveBeenCalledTimes(1);
    expect(onDelete).toHaveBeenCalledTimes(1);
  });

  it('hides the back button when onBack is null', () => {
    render(
      <MailItemDetailShell
        topBar={{ eyebrow: null, trust: 'neutral', onBack: null }}
        hero={<div>Hero</div>}
      />,
    );
    expect(screen.queryByTestId('mailItemDetail_back')).toBeNull();
    // Eyebrow is also null — shouldn't render either.
    expect(screen.queryByTestId('mailItemDetail_eyebrow')).toBeNull();
  });

  it('AI elf strip renders headline + summary + bullets + trailing badge + redo handler', () => {
    const onRedo = jest.fn();
    render(
      <MailItemDetailShell
        topBar={makeTopBar()}
        aiElf={{
          headline: 'Custom headline',
          summary: 'Custom summary text.',
          bullets: [
            { id: 'b1', icon: Bell, label: 'First', text: 'one' },
          ],
          trailingBadge: '1 min',
          onRedo,
        }}
        hero={<div>Hero</div>}
      />,
    );
    expect(screen.getByText('Custom headline')).toBeInTheDocument();
    expect(screen.getByText('Custom summary text.')).toBeInTheDocument();
    expect(screen.getByText('First')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_aiElfBadge')).toHaveTextContent('1 min');
    fireEvent.click(screen.getByTestId('mailItemDetail_aiElfRedo'));
    expect(onRedo).toHaveBeenCalledTimes(1);
  });

  it('attachments row renders every item + tile labels per kind', () => {
    const onOpenPdf = jest.fn();
    render(
      <MailItemDetailShell
        topBar={makeTopBar()}
        attachments={{
          items: [
            { id: 'a1', kind: 'pdf', name: 'Notice.pdf', meta: '84 KB', onSelect: onOpenPdf },
            { id: 'a2', kind: 'image', name: 'Site.jpg' },
            { id: 'a3', kind: 'link', name: 'oaklandca.gov' },
          ],
        }}
        hero={<div>Hero</div>}
      />,
    );
    expect(screen.getByTestId('mailItemDetail_attachment_a1')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_attachment_a2')).toBeInTheDocument();
    expect(screen.getByTestId('mailItemDetail_attachment_a3')).toBeInTheDocument();
    // Tile labels — visual contract.
    expect(screen.getByText('PDF')).toBeInTheDocument();
    expect(screen.getByText('IMG')).toBeInTheDocument();
    expect(screen.getByText('URL')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('mailItemDetail_attachment_a1'));
    expect(onOpenPdf).toHaveBeenCalledTimes(1);
  });
});
