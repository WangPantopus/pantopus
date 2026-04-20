import InfoLine from '../atoms/InfoLine';
import InfoRow from '../atoms/InfoRow';
import { homePlaceText, type ResidencyPayload } from '../ResidencyHomeBlock';

interface AboutCardProps {
  profile: Record<string, unknown>;
  residency?: ResidencyPayload | null;
}

export default function AboutCard({ profile, residency }: AboutCardProps) {
  const workType = profile.gigs_completed > 0 && profile.gigs_posted > 0
    ? 'Worker & Poster'
    : profile.gigs_completed > 0
      ? 'Worker'
      : profile.gigs_posted > 0
        ? 'Poster'
        : 'Member';

  return (
    <div className="bg-surface rounded-xl border border-app p-5">
      <h3 className="text-lg font-semibold text-app mb-3">About</h3>
      <p className="text-sm text-app-secondary leading-6">{profile.bio || 'No bio added yet.'}</p>
      <div className="mt-4 space-y-2 text-sm">
        <InfoLine label="Home" value={homePlaceText(residency ?? undefined)} />
        <InfoLine
          label="Member since"
          value={new Date(profile.created_at || profile.createdAt).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}
        />
        <InfoLine label="Work type" value={workType} />
        {profile.website && <InfoRow icon="🌐" label="Website" value={profile.website} link />}
      </div>
    </div>
  );
}
