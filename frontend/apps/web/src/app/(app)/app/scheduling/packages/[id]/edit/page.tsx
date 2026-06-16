// W15 · G9 — Create / Edit Package route. `id === "new"` creates; any other id
// edits. Thin client wrapper around the stream-owned PackageEditor.

"use client";

import { useParams } from "next/navigation";
import { PackageEditor } from "@/components/scheduling/packages";

export default function PackageEditPage() {
  const params = useParams<{ id: string }>();
  const raw = params?.id;
  const id = Array.isArray(raw) ? raw[0] : (raw ?? "new");
  return <PackageEditor id={id} />;
}
