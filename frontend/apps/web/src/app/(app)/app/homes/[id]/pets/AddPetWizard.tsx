'use client';

// Pantopus — Add / Edit Pet form, presented as a modal from the
// `<ListOfRowsShell />` FAB or a row tap. Keeps the existing single-page
// edit/delete UX from the legacy pets screen — the mobile sides use a
// 3-step wizard archetype, but web has the real estate to render the
// full form on one page, so we stay with the simpler shape and let
// `@pantopus/api` do the work.

import { useEffect, useMemo, useState } from 'react';
import { X } from 'lucide-react';
import * as api from '@pantopus/api';
import { SPECIES_LABEL, swatchFor, type PetSpeciesWire } from './species-palette';

interface PetPayload {
  id: string;
  home_id?: string;
  name: string;
  species?: string | null;
  breed?: string | null;
  notes?: string | null;
  photo_url?: string | null;
}

const SPECIES_ORDER: PetSpeciesWire[] = [
  'dog',
  'cat',
  'bird',
  'fish',
  'reptile',
  'rabbit',
  'hamster',
  'other',
];

interface AddPetWizardProps {
  homeId: string;
  existing: PetPayload | null;
  onClose: (result: PetPayload | null) => void;
}

export default function AddPetWizard({ homeId, existing, onClose }: AddPetWizardProps) {
  const editing = existing !== null;
  const [name, setName] = useState(existing?.name ?? '');
  const [species, setSpecies] = useState<PetSpeciesWire>(
    (existing?.species as PetSpeciesWire | undefined) ?? 'dog',
  );
  const [breed, setBreed] = useState(existing?.breed ?? '');
  const [photoUrl, setPhotoUrl] = useState(existing?.photo_url ?? '');
  const [notes, setNotes] = useState(existing?.notes ?? '');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const valid = name.trim().length > 0;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose(null);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const handleSubmit = async () => {
    if (!valid || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const trimmedBreed = breed.trim();
      const trimmedPhoto = photoUrl.trim();
      const trimmedNotes = notes.trim();
      const payload = {
        name: name.trim(),
        species,
        breed: trimmedBreed || undefined,
        photo_url: trimmedPhoto || undefined,
        notes: trimmedNotes || undefined,
      };
      let res: { pet?: PetPayload } | undefined;
      if (editing && existing) {
        res = (await api.homeProfile.updateHomePet(
          homeId,
          existing.id,
          payload,
        )) as { pet?: PetPayload } | undefined;
      } else {
        res = (await api.homeProfile.createHomePet(homeId, payload)) as
          | { pet?: PetPayload }
          | undefined;
      }
      if (res?.pet) {
        onClose(res.pet);
      } else {
        onClose(null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Couldn’t save the pet. Try again.');
      setSubmitting(false);
    }
  };

  const previewSwatch = useMemo(() => swatchFor(species), [species]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/40"
      role="dialog"
      aria-modal="true"
      aria-label={editing ? 'Edit pet' : 'Add a pet'}
      data-testid="addPetWizard"
      onClick={() => onClose(null)}
    >
      <div
        className="w-full sm:max-w-md bg-app-surface rounded-t-2xl sm:rounded-2xl shadow-xl max-h-[92vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-app-border">
          <h2 className="text-base font-semibold text-app-text">
            {editing ? 'Edit pet' : 'Add a pet'}
          </h2>
          <button
            type="button"
            className="w-9 h-9 inline-flex items-center justify-center rounded-md text-app-text hover:bg-app-hover"
            aria-label="Close"
            onClick={() => onClose(null)}
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="px-4 py-4 space-y-4">
          <div className="flex items-center gap-3">
            <div
              className="w-16 h-16 rounded-lg flex items-center justify-center text-white shrink-0"
              style={{
                background: `linear-gradient(135deg, ${previewSwatch.iconBackground.start}, ${previewSwatch.iconBackground.end})`,
              }}
            >
              <previewSwatch.icon className="w-7 h-7" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-app-text">
                {name.trim() || 'New pet'}
              </p>
              <p className="text-xs text-app-text-secondary">
                {SPECIES_LABEL[species]}
                {breed.trim() ? ` · ${breed.trim()}` : ''}
              </p>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-app-text-strong mb-1.5">
              Species
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5">
              {SPECIES_ORDER.map((s) => {
                const active = s === species;
                const swatch = swatchFor(s);
                return (
                  <button
                    key={s}
                    type="button"
                    onClick={() => setSpecies(s)}
                    aria-pressed={active}
                    data-testid={`addPet_species_${s}`}
                    className={`flex items-center gap-1.5 px-2.5 py-2 rounded-lg border text-xs font-semibold transition ${
                      active
                        ? 'border-primary-500 bg-primary-50 text-primary-700'
                        : 'border-app-border text-app-text-secondary hover:bg-app-hover'
                    }`}
                  >
                    <span
                      className="w-5 h-5 rounded-md flex items-center justify-center text-white"
                      style={{
                        background: `linear-gradient(135deg, ${swatch.iconBackground.start}, ${swatch.iconBackground.end})`,
                      }}
                    >
                      <swatch.icon className="w-3 h-3" />
                    </span>
                    {SPECIES_LABEL[s]}
                  </button>
                );
              })}
            </div>
          </div>

          <Field
            label="Name"
            value={name}
            onChange={setName}
            placeholder="Mango"
            testId="addPet_name"
            required
          />
          <Field
            label="Breed (optional)"
            value={breed}
            onChange={setBreed}
            placeholder="Golden Retriever"
            testId="addPet_breed"
          />
          <Field
            label="Photo URL (optional)"
            value={photoUrl}
            onChange={setPhotoUrl}
            placeholder="https://…/mango.jpg"
            testId="addPet_photoUrl"
          />
          <div>
            <label className="block text-xs font-semibold text-app-text-strong mb-1.5">
              Notes
            </label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={3}
              placeholder="Allergies, medication, sitter notes…"
              data-testid="addPet_notes"
              className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-primary-400 resize-none"
            />
          </div>

          {error && (
            <div className="text-xs text-app-error bg-app-error-bg border border-app-error-light rounded-md px-3 py-2">
              {error}
            </div>
          )}
        </div>

        <div className="px-4 py-3 border-t border-app-border flex justify-end gap-2">
          <button
            type="button"
            onClick={() => onClose(null)}
            disabled={submitting}
            className="px-4 h-10 rounded-lg text-sm font-semibold text-app-text-strong hover:bg-app-hover disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => void handleSubmit()}
            disabled={!valid || submitting}
            data-testid="addPet_submit"
            className="px-4 h-10 rounded-lg bg-primary-600 text-white text-sm font-semibold hover:bg-primary-700 disabled:opacity-50"
          >
            {submitting ? 'Saving…' : editing ? 'Save changes' : 'Add pet'}
          </button>
        </div>
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  placeholder,
  testId,
  required = false,
}: {
  label: string;
  value: string;
  onChange: (next: string) => void;
  placeholder?: string;
  testId?: string;
  required?: boolean;
}) {
  return (
    <div>
      <label className="block text-xs font-semibold text-app-text-strong mb-1.5">
        {label}
        {required && <span className="text-app-error"> *</span>}
      </label>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        data-testid={testId}
        className="w-full px-3 py-2 border border-app-border rounded-lg text-sm text-app-text bg-app-surface placeholder:text-app-text-muted focus:outline-none focus:ring-2 focus:ring-primary-400"
      />
    </div>
  );
}
