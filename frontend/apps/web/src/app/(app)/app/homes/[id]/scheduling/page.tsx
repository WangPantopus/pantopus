"use client";

// F15 — Permission-gated scheduler home (W10). Members with calendar.edit see a
// hub of scheduling surfaces; members without it get the calendar in read-only
// render-mode (handled inside PermissionGate / HomeAgenda).

import { Suspense, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import * as api from "@pantopus/api";
import { getAuthToken } from "@pantopus/api";
import { HomePermissionsProvider } from "@/components/home/useHomePermissions";
import PermissionGate from "@/components/scheduling/home/PermissionGate";

function SchedulerContent() {
  const router = useRouter();
  const params = useParams();
  const homeId = (params?.id ?? "") as string;
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);

  useEffect(() => {
    if (!getAuthToken()) router.push("/login");
  }, [router]);

  useEffect(() => {
    api.users
      .getMyProfile()
      .then((u) => setCurrentUserId((u as { id?: string })?.id ?? null))
      .catch(() => {});
  }, []);

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
          <h1 className="text-xl font-bold text-app-text">Scheduler</h1>
        </div>
      </div>

      <PermissionGate homeId={homeId} currentUserId={currentUserId} />
    </div>
  );
}

export default function SchedulerPage() {
  const homeId = (useParams()?.id ?? "") as string;
  return (
    <Suspense>
      <HomePermissionsProvider homeId={homeId}>
        <SchedulerContent />
      </HomePermissionsProvider>
    </Suspense>
  );
}
