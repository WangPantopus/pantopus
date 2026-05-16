/**
 * T5.2.1 — Pets V2 web page tests.
 *
 * Covers the `<ListOfRowsShell />` reskin:
 *   - empty state render + "Add a pet" CTA
 *   - populated rows show the species chip + breed subtitle + notes body
 *   - FAB opens the Add Pet wizard
 *   - successful submit prepends the new pet at the top of the list
 *   - kebab → confirm → delete optimistically removes the row
 */

import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';

// ── Mock next/navigation ────────────────────────────────────
const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn() }),
  useParams: () => ({ id: 'home_1' }),
}));

// ── Mock @pantopus/api ──────────────────────────────────────
const mockGetHomePets = jest.fn();
const mockCreateHomePet = jest.fn();
const mockUpdateHomePet = jest.fn();
const mockDeleteHomePet = jest.fn();

jest.mock('@pantopus/api', () => ({
  getAuthToken: () => 'fake-token',
  homeProfile: {
    getHomePets: (...args: unknown[]) => mockGetHomePets(...args),
    createHomePet: (...args: unknown[]) => mockCreateHomePet(...args),
    updateHomePet: (...args: unknown[]) => mockUpdateHomePet(...args),
    deleteHomePet: (...args: unknown[]) => mockDeleteHomePet(...args),
  },
}));

// ── Mock confirm/toast stores ────────────────────────────────
const mockConfirmOpen = jest.fn();
jest.mock('@/components/ui/confirm-store', () => ({
  confirmStore: {
    open: (...args: unknown[]) => mockConfirmOpen(...args),
  },
}));
jest.mock('@/components/ui/toast-store', () => ({
  toast: {
    success: jest.fn(),
    error: jest.fn(),
  },
}));

import PetsPage from '../../src/app/(app)/app/homes/[id]/pets/page';

const TWO_PETS = {
  pets: [
    {
      id: 'p1',
      home_id: 'home_1',
      name: 'Mango',
      species: 'dog',
      breed: 'Golden Retriever',
      notes: 'Allergic to chicken.',
    },
    {
      id: 'p2',
      home_id: 'home_1',
      name: 'Biscuit',
      species: 'cat',
      breed: 'Maine Coon',
      notes: 'Skittish around new people.',
    },
  ],
};

describe('PetsPage (T5.2.1)', () => {
  beforeEach(() => {
    mockGetHomePets.mockReset();
    mockCreateHomePet.mockReset();
    mockUpdateHomePet.mockReset();
    mockDeleteHomePet.mockReset();
    mockConfirmOpen.mockReset();
    mockPush.mockReset();
  });

  it('renders the empty state when the home has no pets', async () => {
    mockGetHomePets.mockResolvedValueOnce({ pets: [] });
    render(<PetsPage />);
    await waitFor(() => expect(mockGetHomePets).toHaveBeenCalledWith('home_1'));
    expect(await screen.findByText('No pets yet')).toBeInTheDocument();
    // Empty CTA + FAB both target Add Pet — at least one button labeled
    // either "Add a pet" or with that accessible label.
    const addCtas = screen.getAllByRole('button', { name: /add a pet/i });
    expect(addCtas.length).toBeGreaterThanOrEqual(1);
  });

  it('renders rows with species chip + breed + notes for each pet', async () => {
    mockGetHomePets.mockResolvedValueOnce(TWO_PETS);
    render(<PetsPage />);
    expect(await screen.findByText('Mango')).toBeInTheDocument();
    expect(screen.getByText('Biscuit')).toBeInTheDocument();
    expect(screen.getByText('Golden Retriever')).toBeInTheDocument();
    expect(screen.getByText('Maine Coon')).toBeInTheDocument();
    expect(screen.getByText('Allergic to chicken.')).toBeInTheDocument();
    expect(screen.getByText('Skittish around new people.')).toBeInTheDocument();
    // Species chips render inline beside the name.
    expect(screen.getByText('Dog')).toBeInTheDocument();
    expect(screen.getByText('Cat')).toBeInTheDocument();
  });

  it('opens the Add Pet wizard when the FAB is clicked', async () => {
    mockGetHomePets.mockResolvedValueOnce({ pets: [] });
    render(<PetsPage />);
    await waitFor(() => expect(mockGetHomePets).toHaveBeenCalled());
    // Both the empty CTA and the FAB carry the "Add a pet" label. Click
    // the FAB (the last one in the DOM).
    const buttons = screen.getAllByRole('button', { name: /add a pet/i });
    fireEvent.click(buttons[buttons.length - 1]);
    expect(await screen.findByTestId('addPetWizard')).toBeInTheDocument();
  });

  it('submits the Add Pet form and prepends the result', async () => {
    mockGetHomePets.mockResolvedValueOnce({ pets: [] });
    mockCreateHomePet.mockResolvedValueOnce({
      pet: {
        id: 'p_new',
        home_id: 'home_1',
        name: 'Pickle',
        species: 'bird',
        breed: 'Conure',
        notes: null,
      },
    });
    render(<PetsPage />);
    await waitFor(() => expect(mockGetHomePets).toHaveBeenCalled());
    const openButtons = screen.getAllByRole('button', { name: /add a pet/i });
    fireEvent.click(openButtons[openButtons.length - 1]);

    const dialog = await screen.findByTestId('addPetWizard');
    fireEvent.change(within(dialog).getByTestId('addPet_name'), {
      target: { value: 'Pickle' },
    });
    fireEvent.change(within(dialog).getByTestId('addPet_breed'), {
      target: { value: 'Conure' },
    });
    fireEvent.click(within(dialog).getByTestId('addPet_species_bird'));
    fireEvent.click(within(dialog).getByTestId('addPet_submit'));

    await waitFor(() => expect(mockCreateHomePet).toHaveBeenCalled());
    expect(mockCreateHomePet).toHaveBeenCalledWith(
      'home_1',
      expect.objectContaining({ name: 'Pickle', species: 'bird', breed: 'Conure' }),
    );
    expect(await screen.findByText('Pickle')).toBeInTheDocument();
  });

  it('deletes a row optimistically after confirmation', async () => {
    mockGetHomePets.mockResolvedValueOnce(TWO_PETS);
    mockConfirmOpen.mockResolvedValueOnce(true);
    mockDeleteHomePet.mockResolvedValueOnce({ message: 'Pet deleted' });
    render(<PetsPage />);
    expect(await screen.findByText('Mango')).toBeInTheDocument();
    // Kebab buttons carry the accessibility label "More actions for {title}".
    const kebab = screen.getByRole('button', { name: /more actions for mango/i });
    fireEvent.click(kebab);
    await waitFor(() => expect(mockConfirmOpen).toHaveBeenCalled());
    await waitFor(() => expect(mockDeleteHomePet).toHaveBeenCalledWith('home_1', 'p1'));
    await waitFor(() => expect(screen.queryByText('Mango')).not.toBeInTheDocument());
  });
});
