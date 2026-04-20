'use client';

import { Suspense, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { getAuthToken } from '@pantopus/api';
import LandlordVerificationFlow from '@/components/home/LandlordVerificationFlow';

function VerifyLandlordContent() {
  const router = useRouter();
  const { id: homeId } = useParams<{ id: string }>();

  useEffect(() => { if (!getAuthToken()) router.push('/login'); }, [router]);

  if (!homeId) return null;

  return (
    <div className="max-w-2xl mx-auto px-4 py-6">
      <LandlordVerificationFlow
        homeId={homeId}
        onApproved={() => router.push(`/app/homes/${homeId}/dashboard`)}
        onBack={() => router.back()}
      />
    </div>
  );
}

export default function VerifyLandlordPage() { return <Suspense><VerifyLandlordContent /></Suspense>; }
