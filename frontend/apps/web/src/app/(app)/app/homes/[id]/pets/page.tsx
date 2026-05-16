'use client';

// Pantopus — T5.2.1 Pets screen on the shared `<ListOfRowsShell />`.
// Single source of truth = the shell; this page only owns the data
// fetch (via `@pantopus/api`) + the row projection. Each pet renders
// as shape **E** (64dp rounded-square thumbnail leading + inline
// species chip + breed subtitle + notes preview + kebab trailing).
// Mirrors the iOS PetsListView and Android PetsListScreen exactly.

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { PawPrint, Plus } from 'lucide-react';
import * as api from '@pantopus/api';
import { getAuthToken } from '@pantopus/api';
import ListOfRowsShell from '@/components/list-of-rows/ListOfRowsShell';
import type { ListOfRowsState, RowModel } from '@/components/list-of-rows/types';
import { toast } from '@/components/ui/toast-store';
import { confirmStore } from '@/components/ui/confirm-store';
import {
  SPECIES_LABEL,
  parseSpecies,
  swatchFor,
  type PetSpeciesWire,
} from './species-palette';
import AddPetWizard from './AddPetWizard';

interface PetRecord {
  id: string;
  home_id?: string;
  name: string;
  species?: string | null;
  breed?: string | null;
  notes?: string | null;
  photo_url?: string | null;
  vet_name?: string | null;
  vet_phone?: string | null;
  vet_address?: string | null;
  vaccine_notes?: string | null;
  feeding_schedule?: string | null;
  medications?: string | null;
  microchip_id?: string | null;
  age_years?: number | null;
  weight_lbs?: number | null;
}

function PetsContent() {
  const router = useRouter();
  const { id: homeId } = useParams<{ id: string }>();

  const [pets, setPets] = useState<PetRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [editingPet, setEditingPet] = useState<PetRecord | null>(null);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    if (!getAuthToken()) router.push('/login');
  }, [router]);

  const fetchPets = useCallback(async () => {
    if (!homeId) return;
    setErrorMessage(null);
    try {
      const res = await api.homeProfile.getHomePets(homeId);
      setPets(((res as { pets?: PetRecord[] } | undefined)?.pets) || []);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load pets';
      setErrorMessage(message);
      toast.error('Failed to load pets');
    }
  }, [homeId]);

  useEffect(() => {
    setLoading(true);
    fetchPets().finally(() => setLoading(false));
  }, [fetchPets]);

  const handleDelete = useCallback(
    async (pet: PetRecord) => {
      const yes = await confirmStore.open({
        title: 'Remove pet?',
        description: `${pet.name} will be removed from this home.`,
        confirmLabel: 'Remove',
        variant: 'destructive',
      });
      if (!yes || !homeId) return;
      const previous = pets;
      setPets((rows) => rows.filter((p) => p.id !== pet.id));
      try {
        await api.homeProfile.deleteHomePet(homeId, pet.id);
        toast.success('Pet removed');
      } catch {
        setPets(previous);
        toast.error('Failed to remove pet');
      }
    },
    [homeId, pets],
  );

  const rows = useMemo<RowModel[]>(() => {
    return pets.map((pet) => {
      const species = parseSpecies(pet.species);
      const swatch = swatchFor(species);
      return {
        id: pet.id,
        title: pet.name,
        subtitle: pet.breed?.trim() || undefined,
        template: 'avatarKebab',
        leading: pet.photo_url
          ? {
              kind: 'thumbnail',
              size: 'large',
              image: {
                kind: 'url',
                url: pet.photo_url,
                fallback: swatch.icon,
                gradient: swatch.iconBackground,
              },
            }
          : {
              kind: 'thumbnail',
              size: 'large',
              image: {
                kind: 'icon',
                icon: swatch.icon,
                gradient: swatch.iconBackground,
              },
            },
        trailing: { kind: 'kebab' },
        onTap: () => setEditingPet(pet),
        onSecondary: () => void handleDelete(pet),
        body: pet.notes?.trim() || undefined,
        inlineChip: {
          text: SPECIES_LABEL[species],
          tint: {
            kind: 'custom',
            background: swatch.chipBackground,
            foreground: swatch.chipForeground,
          },
        },
      } satisfies RowModel;
    });
  }, [pets, handleDelete]);

  const state: ListOfRowsState = useMemo(() => {
    if (loading) return { kind: 'loading' };
    if (errorMessage) return { kind: 'error', message: errorMessage };
    if (rows.length === 0) {
      return {
        kind: 'empty',
        config: {
          icon: PawPrint,
          headline: 'No pets yet',
          subcopy:
            'Add your pets so household members and pet-sitters have the info they need.',
          ctaTitle: 'Add a pet',
          onCta: () => setAdding(true),
        },
      };
    }
    return {
      kind: 'loaded',
      sections: [{ id: 'pets', rows }],
      hasMore: false,
    };
  }, [loading, errorMessage, rows]);

  if (!homeId) return null;

  return (
    <>
      <ListOfRowsShell
        title="Pets"
        state={state}
        onRefresh={() => {
          void fetchPets();
        }}
        fab={{
          icon: Plus,
          accessibilityLabel: 'Add a pet',
          variant: { kind: 'secondaryCreate' },
          onClick: () => setAdding(true),
        }}
      />
      {(adding || editingPet) && (
        <AddPetWizard
          homeId={homeId}
          existing={editingPet}
          onClose={(result) => {
            const wasEditing = editingPet !== null;
            setAdding(false);
            setEditingPet(null);
            if (!result) return;
            if (wasEditing) {
              setPets((rows) => rows.map((p) => (p.id === result.id ? result : p)));
              toast.success('Pet updated');
            } else {
              setPets((rows) => [result, ...rows]);
              toast.success('Pet added');
            }
          }}
        />
      )}
    </>
  );
}

export default function PetsPage() {
  return (
    <Suspense>
      <PetsContent />
    </Suspense>
  );
}
