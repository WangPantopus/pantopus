import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import {
  buildShareMetadata,
  displayNameForUser,
  fetchPublicUser,
  summarizeText,
} from '@/lib/publicShare';
import { buildUserProfileShareUrl } from '@pantopus/utils';
import PublicProfileClient from './PublicProfileClient';

export async function generateMetadata({
  params,
}: {
  params: Promise<{ username: string }>;
}): Promise<Metadata> {
  const { username } = await params;
  const result = await fetchPublicUser(username);
  const profile = result.data;

  if (!profile) {
    return {
      title: 'Profile Not Found | Pantopus',
      description: 'This Pantopus profile could not be found.',
    };
  }

  const fullName = displayNameForUser(profile, profile.username || 'Pantopus member');
  const bioSource = typeof profile.bio === 'string' ? profile.bio : '';

  return buildShareMetadata({
    title: fullName,
    description: summarizeText(
      bioSource,
      160,
      `${fullName} on Pantopus. Connect, message, and see their work.`,
    ),
    path: `/${username}`,
    appArgument: buildUserProfileShareUrl(username),
    images: profile.profile_picture_url ? [profile.profile_picture_url] : null,
  });
}

export default async function PublicProfilePage({
  params,
}: {
  params: Promise<{ username: string }>;
}) {
  const { username } = await params;
  const result = await fetchPublicUser(username);

  if (!result.data && result.status === 404) {
    notFound();
  }

  return (
    <PublicProfileClient username={username} initialProfile={result.data} />
  );
}
