// W15 · G12 — Invoices List route (owner). Thin client wrapper around the
// stream-owned InvoiceList component.

"use client";

import { InvoiceList } from "@/components/scheduling/packages";

export default function InvoicesPage() {
  return <InvoiceList />;
}
