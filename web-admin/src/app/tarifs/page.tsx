'use client';

import React, { useEffect, useState } from 'react';
import Sidebar from '@/components/Sidebar';
import { supabase } from '@/lib/supabase';

interface Tarif {
  id: string;
  nom: string;
  prix: number;
  description: string;
  cree_le?: string;
}

export default function TarifsPage() {
  const [tarifs, setTarifs] = useState<Tarif[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // Form states
  const [nom, setNom] = useState('');
  const [prix, setPrix] = useState<number | ''>('');
  const [description, setDescription] = useState('');
  const [editingTarif, setEditingTarif] = useState<Tarif | null>(null);

  useEffect(() => {
    fetchTarifs();
  }, []);

  const fetchTarifs = async () => {
    try {
      setLoading(true);
      const { data, error } = await supabase
        .from('tarifs')
        .select('*')
        .order('prix', { ascending: true });

      if (error) throw error;
      setTarifs(data ?? []);
    } catch (error) {
      console.error('Error fetching tariffs:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!nom.trim() || prix === '') return;

    setSubmitting(true);
    try {
      if (editingTarif) {
        // Update existing tariff
        const { error } = await supabase
          .from('tarifs')
          .update({
            nom,
            prix: Number(prix),
            description
          })
          .eq('id', editingTarif.id);

        if (error) throw error;
      } else {
        // Create new tariff
        const { error } = await supabase
          .from('tarifs')
          .insert({
            nom,
            prix: Number(prix),
            description
          });

        if (error) throw error;
      }

      setNom('');
      setPrix('');
      setDescription('');
      setEditingTarif(null);
      fetchTarifs();
    } catch (error) {
      console.error('Error saving tariff:', error);
      alert('Une erreur est survenue lors de la sauvegarde.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleEditClick = (tarif: Tarif) => {
    setEditingTarif(tarif);
    setNom(tarif.nom);
    setPrix(tarif.prix);
    setDescription(tarif.description || '');
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Êtes-vous sûr de vouloir supprimer ce tarif ?')) return;

    try {
      const { error } = await supabase
        .from('tarifs')
        .delete()
        .eq('id', id);

      if (error) throw error;
      setTarifs(tarifs.filter(t => t.id !== id));
    } catch (error) {
      console.error('Error deleting tariff:', error);
    }
  };

  return (
    <div className="flex h-screen bg-dark-bg font-sans overflow-hidden">
      <Sidebar />

      <main className="flex-1 flex flex-col overflow-y-auto px-8 py-8">
        <div className="mb-8">
          <h2 className="text-2xl font-bold text-dark-on-surface">Tarifs des tickets</h2>
          <p className="text-sm text-dark-on-surface-variant">
            {"Gérez la liste de prix et de tickets d'impression disponibles pour les conducteurs"}
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 flex-1">
          {/* Liste des tarifs */}
          <div className="lg:col-span-8 bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md h-fit">
            <h3 className="text-lg font-bold text-dark-on-surface mb-6">Grille tarifaire active</h3>

            {loading ? (
              <div className="flex justify-center py-12">
                <svg className="h-8 w-8 animate-spin text-blue-500" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
              </div>
            ) : tarifs.length === 0 ? (
              <div className="text-center py-12 text-sm text-dark-on-surface-variant">
                Aucun ticket configuré dans la grille tarifaire.
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {tarifs.map((tarif) => (
                  <div
                    key={tarif.id}
                    className="border border-dark-outline/40 bg-dark-bg/25 rounded-2xl p-5 flex flex-col justify-between gap-4 hover:border-blue-500/40 transition-all duration-300"
                  >
                    <div>
                      <div className="flex items-start justify-between gap-2">
                        <h4 className="font-bold text-dark-on-surface text-base">{tarif.nom}</h4>
                        <span className="text-lg font-extrabold text-blue-400 bg-blue-600/10 border border-blue-600/30 px-3 py-1 rounded-xl">
                          {tarif.prix.toFixed(2)} €
                        </span>
                      </div>
                      <p className="text-xs text-dark-on-surface-variant mt-2 font-medium">
                        {tarif.description || 'Aucune description fournie.'}
                      </p>
                    </div>

                    <div className="flex items-center justify-end gap-2 border-t border-dark-outline/20 pt-3">
                      <button
                        onClick={() => handleEditClick(tarif)}
                        className="px-3 py-1.5 rounded-lg bg-blue-600/10 border border-blue-600/30 text-blue-300 hover:bg-blue-600/20 text-xs font-semibold transition-all cursor-pointer flex items-center gap-1"
                      >
                        <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-2.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                        </svg>
                        Modifier
                      </button>
                      <button
                        onClick={() => handleDelete(tarif.id)}
                        className="px-3 py-1.5 rounded-lg bg-danger-red-dark/10 border border-danger-red-dark/30 text-danger-red-dark hover:bg-danger-red-dark/20 text-xs font-semibold transition-all cursor-pointer flex items-center gap-1"
                      >
                        <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                        </svg>
                        Supprimer
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Formulaire de création / édition */}
          <div className="lg:col-span-4 bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md h-fit">
            <h3 className="text-lg font-bold text-dark-on-surface mb-6">
              {editingTarif ? 'Modifier le tarif' : 'Ajouter un tarif'}
            </h3>

            <form onSubmit={handleSubmit} className="space-y-6">
              <div>
                <label className="block text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                  Nom du ticket
                </label>
                <input
                  type="text"
                  required
                  value={nom}
                  onChange={(e) => setNom(e.target.value)}
                  className="mt-1 block w-full rounded-xl border border-dark-outline bg-dark-bg/60 px-4 py-2.5 text-dark-on-surface placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-sm"
                  placeholder="Ex: Ticket Unitaire"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                  Prix (€)
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  required
                  value={prix}
                  onChange={(e) => setPrix(e.target.value === '' ? '' : Number(e.target.value))}
                  className="mt-1 block w-full rounded-xl border border-dark-outline bg-dark-bg/60 px-4 py-2.5 text-dark-on-surface placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-sm"
                  placeholder="Ex: 1.50"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                  Description
                </label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="mt-1 block w-full rounded-xl border border-dark-outline bg-dark-bg/60 px-4 py-2.5 text-dark-on-surface placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-sm h-24 resize-none"
                  placeholder="Détails du ticket..."
                />
              </div>

              <div className="flex gap-2">
                {editingTarif && (
                  <button
                    type="button"
                    onClick={() => {
                      setEditingTarif(null);
                      setNom('');
                      setPrix('');
                      setDescription('');
                    }}
                    className="flex-1 rounded-xl bg-slate-800 border border-slate-700 py-2.5 text-sm font-semibold text-slate-300 hover:bg-slate-700 transition-all cursor-pointer"
                  >
                    Annuler
                  </button>
                )}
                <button
                  type="submit"
                  disabled={submitting}
                  className="flex-1 rounded-xl bg-blue-600 py-2.5 text-sm font-semibold text-white hover:bg-blue-500 transition-all shadow-[0_4px_12px_rgba(25,118,210,0.2)] cursor-pointer"
                >
                  {submitting ? 'Sauvegarde...' : editingTarif ? 'Mettre à jour' : 'Ajouter à la grille'}
                </button>
              </div>
            </form>
          </div>
        </div>
      </main>
    </div>
  );
}
