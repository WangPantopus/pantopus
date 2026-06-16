// W15 · G13 — Invoice Detail route (owner). Thin client wrapper around the
// stream-owned InvoiceDetail component.

"use client";

import { useParams } from "next/navigation";
import { InvoiceDetail } from "@/components/scheduling/packages";

export default function InvoiceDetailPage() {
  const params = useParams<{ id: string }>();
  const raw = params?.id;
  const id = Array.isArray(raw) ? raw[0] : (raw ?? "");
  return <InvoiceDetail id={id} />;
}
