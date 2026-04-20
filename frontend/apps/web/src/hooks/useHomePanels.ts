'use client';

import { useCallback, useState } from 'react';

export type PanelType = 'task' | 'issue' | 'bill' | 'package' | null;

export interface UseHomePanelsReturn {
  taskPanel: { open: boolean; task?: Record<string, unknown> };
  issuePanel: { open: boolean; issue?: Record<string, unknown> };
  billPanel: { open: boolean; bill?: Record<string, unknown> };
  packagePanel: { open: boolean; pkg?: Record<string, unknown> };
  inviteModal: boolean;
  expandedCard: string | null;
  // Panel openers
  openTaskPanel: (task?: Record<string, unknown>) => void;
  closeTaskPanel: () => void;
  openIssuePanel: (issue?: Record<string, unknown>) => void;
  closeIssuePanel: () => void;
  openBillPanel: (bill?: Record<string, unknown>) => void;
  closeBillPanel: () => void;
  openPackagePanel: (pkg?: Record<string, unknown>) => void;
  closePackagePanel: () => void;
  // Modal
  openInviteModal: () => void;
  closeInviteModal: () => void;
  // Card expansion
  setExpandedCard: (card: string | null) => void;
}

export function useHomePanels(): UseHomePanelsReturn {
  const [taskPanel, setTaskPanel] = useState<{ open: boolean; task?: Record<string, unknown> }>({ open: false });
  const [issuePanel, setIssuePanel] = useState<{ open: boolean; issue?: Record<string, unknown> }>({ open: false });
  const [billPanel, setBillPanel] = useState<{ open: boolean; bill?: Record<string, unknown> }>({ open: false });
  const [packagePanel, setPackagePanel] = useState<{ open: boolean; pkg?: Record<string, unknown> }>({ open: false });
  const [inviteModal, setInviteModal] = useState(false);
  const [expandedCard, setExpandedCard] = useState<string | null>(null);

  const openTaskPanel = useCallback((task?: Record<string, unknown>) => setTaskPanel({ open: true, task }), []);
  const closeTaskPanel = useCallback(() => setTaskPanel({ open: false }), []);
  const openIssuePanel = useCallback((issue?: Record<string, unknown>) => setIssuePanel({ open: true, issue }), []);
  const closeIssuePanel = useCallback(() => setIssuePanel({ open: false }), []);
  const openBillPanel = useCallback((bill?: Record<string, unknown>) => setBillPanel({ open: true, bill }), []);
  const closeBillPanel = useCallback(() => setBillPanel({ open: false }), []);
  const openPackagePanel = useCallback((pkg?: Record<string, unknown>) => setPackagePanel({ open: true, pkg }), []);
  const closePackagePanel = useCallback(() => setPackagePanel({ open: false }), []);

  const openInviteModal = useCallback(() => setInviteModal(true), []);
  const closeInviteModal = useCallback(() => setInviteModal(false), []);

  return {
    taskPanel,
    issuePanel,
    billPanel,
    packagePanel,
    inviteModal,
    expandedCard,
    openTaskPanel,
    closeTaskPanel,
    openIssuePanel,
    closeIssuePanel,
    openBillPanel,
    closeBillPanel,
    openPackagePanel,
    closePackagePanel,
    openInviteModal,
    closeInviteModal,
    setExpandedCard,
  };
}
