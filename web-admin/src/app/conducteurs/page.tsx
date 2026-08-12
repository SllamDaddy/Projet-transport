'use client';

import React, { useEffect, useState } from 'react';
import Sidebar from '@/components/Sidebar';
import { supabase } from '@/lib/supabase';

interface Conducteur {
  id: string;
  nom: string;
  email: string;
  agent_id?: string;
  actif: boolean;
  cree_le?: string;
}

export default function ConducteursPage() {
  const [conducteurs, setConducteurs] = useState<Conducteur[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const [nom, setNom] = useState('');
  const [email, setEmail] = useState('');
  const [agentId, setAgentId] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [editingConducteur, setEditingConducteur] = useState<Conducteur | null>(null);

  useEffect(() => {
    fetchConducteurs();
  }, []);

  const fetchConducteurs = async () => {
    try {
      setLoading(true);
      const { data, error } = await supabase
        .from('conducteurs')
        .select('*')
        .order('cree_le', { ascending: false });

      if (error) throw error;
      setConducteurs(data ?? []);
    } catch (error) {
      console.error('Error fetching drivers:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleToggleActif = async (id: string, currentStatus: boolean) => {
    try {
      const { error } = await supabase
        .from('conducteurs')
        .update({ actif: !currentStatus })
        .eq('id', id);

      if (error) throw error;
      setConducteurs(conducteurs.map(c => c.id === id ? { ...c, actif: !currentStatus } : c));
    } catch (error) {
      console.error('Error toggling driver status:', error);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!nom.trim() || !email.trim() || (!editingConducteur && !agentId.trim())) return;
    if (!editingConducteur && !motDePasse.trim()) {
      alert('Le mot de passe est requis pour la création.');
      return;
    }

    setSubmitting(true);
    try {
      if (editingConducteur) {
        // Update driver profile row
        const { error } = await supabase
          .from('conducteurs')
          .update({
            nom,
            email,
            agent_id: agentId
          })
          .eq('id', editingConducteur.id);

        if (error) throw error;
      } else {
        // Call backend API Route using the bearer token to authenticate
        const { data: { session } } = await supabase.auth.getSession();
        const response = await fetch('/api/conducteurs', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${session?.access_token || ''}`
          },
          body: JSON.stringify({
            nom,
            email,
            motDePasse,
            agentId
          })
        });

        const resData = await response.json();
        if (!response.ok) {
          throw new Error(resData.error || 'Erreur lors de la création du compte conducteur.');
        }
      }

      setNom('');
      setEmail('');
      setAgentId('');
      setMotDePasse('');
      setEditingConducteur(null);
      fetchConducteurs();
    } catch (error: unknown) {
      console.error('Error saving driver:', error);
      const errorMessage = error instanceof Error ? error.message : 'Une erreur est survenue lors de la sauvegarde.';
      alert(errorMessage);
    } finally {
      setSubmitting(false);
    }
  };

  const handleEditClick = (conducteur: Conducteur) => {
    setEditingConducteur(conducteur);
    setNom(conducteur.nom);
    setEmail(conducteur.email);
    setAgentId(conducteur.agent_id || '');
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Êtes-vous sûr de vouloir supprimer ce conducteur ?')) return;

    try {
      const { error } = await supabase
        .from('conducteurs')
        .delete()
        .eq('id', id);

      if (error) throw error;
      setConducteurs(conducteurs.filter(c => c.id !== id));
    } catch (error) {
      console.error('Error deleting driver:', error);
    }
  };

  return (
    <div className="flex h-screen bg-dark-bg font-sans overflow-hidden">
      <Sidebar />

      <main className="flex-1 flex flex-col overflow-y-auto px-8 py-8">
        <div className="mb-8">
          <h2 className="text-2xl font-bold text-dark-on-surface">Gestion des conducteurs</h2>
          <p className="text-sm text-dark-on-surface-variant">
            {"Enregistrez et activez les comptes des conducteurs autorisés à se connecter sur l'application mobile"}
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 flex-1">
          {/* Liste des conducteurs */}
          <div className="lg:col-span-8 bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md h-fit">
            <h3 className="text-lg font-bold text-dark-on-surface mb-6">Conducteurs enregistrés</h3>

            {loading ? (
              <div className="flex justify-center py-12">
                <svg className="h-8 w-8 animate-spin text-blue-500" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
              </div>
            ) : conducteurs.length === 0 ? (
              <div className="text-center py-12 text-sm text-dark-on-surface-variant">
                Aucun conducteur enregistré pour le moment.
              </div>
            ) : (
              <div className="space-y-4">
                {conducteurs.map((conducteur) => (
                  <div
                    key={conducteur.id}
                    className="border border-dark-outline/40 bg-dark-bg/25 rounded-2xl p-5 flex flex-col md:flex-row md:items-center justify-between gap-4 hover:border-blue-500/30 transition-all duration-300"
                  >
                    <div className="flex items-start gap-4">
                      {/* Avatar ou Initiale */}
                      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-blue-600/10 border border-blue-600/25 text-blue-300 text-base font-bold">
                        {conducteur.nom.substring(0, 2).toUpperCase()}
                      </div>
                      <div>
                        <div className="flex items-center gap-3">
                          <h4 className="font-bold text-dark-on-surface text-base">{conducteur.nom}</h4>
                          {conducteur.agent_id && (
                            <span className="bg-blue-600/20 text-blue-300 border border-blue-600/30 px-2 py-0.5 rounded-lg text-xs font-semibold">
                              ID Agent: {conducteur.agent_id}
                            </span>
                          )}
                          <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-semibold tracking-wider uppercase ${
                            conducteur.actif 
                              ? 'bg-success-green/15 text-green-400 border border-success-green/30' 
                              : 'bg-slate-800 text-slate-400 border border-slate-700'
                          }`}>
                            {conducteur.actif ? 'Autorisé' : 'Bloqué'}
                          </span>
                        </div>
                        <p className="text-xs text-dark-on-surface-variant mt-1 font-medium flex flex-col sm:flex-row sm:gap-4">
                          <span>📧 {conducteur.email}</span>
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 self-end md:self-center border-t md:border-t-0 border-dark-outline/20 pt-3 md:pt-0">
                      <button
                        onClick={() => handleToggleActif(conducteur.id, conducteur.actif)}
                        className={`px-3 py-1.5 rounded-lg border text-xs font-semibold transition-all cursor-pointer ${
                          conducteur.actif
                            ? 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700'
                            : 'bg-success-green/10 border-success-green/30 text-green-400 hover:bg-success-green/20'
                        }`}
                      >
                        {conducteur.actif ? 'Bloquer' : 'Autoriser'}
                      </button>
                      <button
                        onClick={() => handleEditClick(conducteur)}
                        className="p-2 rounded-lg bg-blue-600/10 border border-blue-600/30 text-blue-300 hover:bg-blue-600/20 transition-all cursor-pointer"
                        title="Modifier"
                      >
                        <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-2.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                        </svg>
                      </button>
                      <button
                        onClick={() => handleDelete(conducteur.id)}
                        className="p-2 rounded-lg bg-danger-red-dark/10 border border-danger-red-dark/30 text-danger-red-dark hover:bg-danger-red-dark/20 transition-all cursor-pointer"
                        title="Supprimer"
                      >
                        <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                        </svg>
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Formulaire */}
          <div className="lg:col-span-4 bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md h-fit animate-fade-in">
            <h3 className="text-lg font-bold text-dark-on-surface mb-6">
              {editingConducteur ? 'Modifier le conducteur' : 'Ajouter un conducteur'}
            </h3>

            <form onSubmit={handleSubmit} className="space-y-6">
              <div>
                <label className="block text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                  Nom Complet
                </label>
                <input
                  type="text"
                  required
                  value={nom}
                  onChange={(e) => setNom(e.target.value)}
                  className="mt-1 block w-full rounded-xl border border-dark-outline bg-dark-bg/60 px-4 py-2.5 text-dark-on-surface placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-sm"
                  placeholder="Ex: Jean Dupont"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                  ID Agent
                </label>
                <input
                  type="text"
                  required
                  value={agentId}
                  onChange={(e) => setAgentId(e.target.value)}
                  className="mt-1 block w-full rounded-xl border border-dark-outline bg-dark-bg/60 px-4 py-2.5 text-dark-on-surface placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-sm"
                  placeholder="Ex: A123"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                  Adresse Email
                </label>
                <input
                  type="email"
                  required
                  disabled={!!editingConducteur}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="mt-1 block w-full rounded-xl border border-dark-outline bg-dark-bg/60 px-4 py-2.5 text-dark-on-surface placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-sm disabled:opacity-50 disabled:cursor-not-allowed"
                  placeholder="Ex: jean.dupont@email.com"
                />
              </div>

              {/* Password field only shown for new drivers */}
              {!editingConducteur && (
                <div>
                  <label className="block text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                    Mot de passe initial
                  </label>
                  <input
                    type="password"
                    required
                    value={motDePasse}
                    onChange={(e) => setMotDePasse(e.target.value)}
                    className="mt-1 block w-full rounded-xl border border-dark-outline bg-dark-bg/60 px-4 py-2.5 text-dark-on-surface placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-sm"
                    placeholder="Min. 6 caractères"
                  />
                </div>
              )}



              <div className="flex gap-2">
                {editingConducteur && (
                  <button
                    type="button"
                    onClick={() => {
                      setEditingConducteur(null);
                      setNom('');
                      setEmail('');
                      setAgentId('');
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
                  {submitting ? 'Sauvegarde...' : editingConducteur ? 'Mettre à jour' : 'Enregistrer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      </main>
    </div>
  );
}
