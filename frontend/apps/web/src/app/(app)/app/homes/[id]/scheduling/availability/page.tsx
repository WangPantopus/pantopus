"use client";

// F8 — My household availability settings (W10). Exposure-only boundary screen.

import { Suspense, useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { getAuthToken } from "@pantopus/api";
import HouseholdAvailabilityForm from "@/components/scheduling/home/HouseholdAvailabilityForm";

function AvailabilityContent() {
  const router = useRouter();
  const params = useParams();
  const homeId = (params?.id ?? "") as string;

  useEffect(() => {
    if (!getAuthToken()) router.push("/login");
  }, [router]);

  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <div className="mb-5 flex items-center gap-3">
        <button
          onClick={() => router.back()}
          className="rounded-lg p-1.5 transition hover:bg-app-surface-sunken"
          aria-label="Back"
        >
          <ArrowLeft className="h-5 w-5 text-app-text" />
        </button>
        <div>
          <div className="text-[11px] font-bold uppercase tracking-[0.08em] text-app-home">
            Household
          </div>
          <h1 className="text-xl font-bold text-app-text">My availability</h1>
        </div>
      </div>

      <HouseholdAvailabilityForm homeId={homeId} />
    </div>
  );
}

export default function HouseholdAvailabilityPage() {
  return (
    <Suspense>
      <AvailabilityContent />
    </Suspense>
  );
}
