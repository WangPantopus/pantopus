// W15 · G8 — Packages List route. Thin client wrapper; the screen, data, and
// states live in the stream-owned PackageList component.

"use client";

import { PackageList } from "@/components/scheduling/packages";

export default function PackagesPage() {
  return <PackageList />;
}
