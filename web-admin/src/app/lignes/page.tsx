'use client';

import React, { useEffect, useState } from 'react';
import dynamic from 'next/dynamic';
import Sidebar from '@/components/Sidebar';
import { supabase } from '@/lib/supabase';

// Load Map component dynamically (client-side only) to avoid SSR window errors
const Map = dynamic(() => import('@/components/Map'), {
  ssr: false,
  loading: () => (
    <div className="flex h-full w-full items-center justify-center bg-dark-surface border border-dark-outline rounded-3xl min-h-[350px]">
      <div className="flex flex-col items-center gap-2">
        <svg className="h-8 w-8 animate-spin text-blue-500" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
        </svg>
        <span className="text-xs text-dark-on-surface-variant font-medium">Chargement de la carte...</span>
      </div>
    </div>
  )
});

interface Station {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  approachRadius: number;
  arrivalRadius: number;
}

interface Ligne {
  id: string;
  nom: string;
  stations: Station[];
  actif: boolean;
  cree_le: string;
}

export default function LignesPage() {
  const [lignes, setLignes] = useState<Ligne[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // Form states
  const [nom, setNom] = useState('');
  const [stations, setStations] = useState<Omit<Station, 'id'>[]>([
    { name: '', latitude: 0, longitude: 0, approachRadius: 300, arrivalRadius: 80 }
  ]);
  const [editingLigne, setEditingLigne] = useState<Ligne | null>(null);

  useEffect(() => {
    fetchLignes();
  }, []);

  const fetchLignes = async () => {
    try {
      setLoading(true);
      const { data } = await supabase
        .from('lignes')
        .select('*')
        .order('cree_le', { ascending: false });

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const formatted = (data ?? []).map((l: any) => ({
        ...l,
        stations: Array.isArray(l.stations) ? l.stations : []
      }));

      setLignes(formatted);
    } catch (error) {
      console.error('Error fetching lines:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleAddStationField = () => {
    setStations([
      ...stations,
      { name: '', latitude: 0, longitude: 0, approachRadius: 300, arrivalRadius: 80 }
    ]);
  };

  const handleRemoveStationField = (index: number) => {
    setStations(stations.filter((_, i) => i !== index));
  };

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handleStationChange = (index: number, field: keyof Omit<Station, 'id'>, value: any) => {
    const updated = [...stations];
    updated[index] = {
      ...updated[index],
      [field]: field === 'name' ? value : Number(value)
    };
    setStations(updated);
  };

  // Click on the map to add or update a marker coordinates
  const handleMapClick = (lat: number, lng: number) => {
    const lastStation = stations[stations.length - 1];
    if (lastStation && lastStation.name === '' && lastStation.latitude === 0 && lastStation.longitude === 0) {
      // If the last station field is empty, populate it
      const updated = [...stations];
      updated[stations.length - 1] = {
        ...lastStation,
        name: `Arrêt ${stations.length}`,
        latitude: Number(lat.toFixed(6)),
        longitude: Number(lng.toFixed(6))
      };
      setStations(updated);
    } else {
      // Append a new station
      setStations([
        ...stations,
        {
          name: `Arrêt ${stations.length + 1}`,
          latitude: Number(lat.toFixed(6)),
          longitude: Number(lng.toFixed(6)),
          approachRadius: 300,
          arrivalRadius: 80
        }
      ]);
    }
  };

  const handleMarkerDrag = (index: number, lat: number, lng: number) => {
    const updated = [...stations];
    updated[index] = {
      ...updated[index],
      latitude: Number(lat.toFixed(6)),
      longitude: Number(lng.toFixed(6))
    };
    setStations(updated);
  };

  const handleToggleActif = async (id: string, currentStatus: boolean) => {
    try {
      const { error } = await supabase
        .from('lignes')
        .update({ actif: !currentStatus })
        .eq('id', id);

      if (error) throw error;
      setLignes(lignes.map(l => l.id === id ? { ...l, actif: !currentStatus } : l));
    } catch (error) {
      console.error('Error toggling active status:', error);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Êtes-vous sûr de vouloir supprimer cette ligne de bus ?')) return;

    try {
      const { error } = await supabase
        .from('lignes')
        .delete()
        .eq('id', id);

      if (error) throw error;
      setLignes(lignes.filter(l => l.id !== id));
    } catch (error) {
      console.error('Error deleting line:', error);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!nom.trim()) return;

    setSubmitting(true);
    try {
      const stationsWithIds: Station[] = stations.map((s, index) => ({
        ...s,
        id: `station_${Date.now()}_${index}`
      }));

      if (editingLigne) {
        const { error } = await supabase
          .from('lignes')
          .update({
            nom,
            stations: stationsWithIds
          })
          .eq('id', editingLigne.id);

        if (error) throw error;
      } else {
        const { error } = await supabase
          .from('lignes')
          .insert({
            nom,
            stations: stationsWithIds,
            actif: true
          });

        if (error) throw error;
      }

      setNom('');
      setStations([{ name: '', latitude: 0, longitude: 0, approachRadius: 300, arrivalRadius: 80 }]);
      setEditingLigne(null);
      fetchLignes();
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));
      console.error('Error saving line:', err);
      alert(err.message || 'Une erreur est survenue lors de la sauvegarde.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleEditClick = (ligne: Ligne) => {
    setEditingLigne(ligne);
    setNom(ligne.nom);
    setStations(
      ligne.stations.map((s) => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const { id, ...rest } = s;
        return rest;
      })
    );
  };

  return (
    <div className="flex h-screen bg-dark-bg font-sans overflow-hidden">
      <Sidebar />

      <main className="flex-1 flex flex-col overflow-y-auto px-8 py-8">
        <div className="mb-8">
          <h2 className="text-2xl font-bold text-dark-on-surface">Lignes de bus</h2>
          <p className="text-sm text-dark-on-surface-variant">
            {"Placez les arrêts et visualisez les rayons de détection directement sur la carte"}
          </p>
        </div>

        {/* Layout en 3 colonnes pour grand écran */}
        <div className="grid grid-cols-1 xl:grid-cols-12 gap-8 flex-1">
          {/* Colonne 1: Liste des lignes */}
          <div className="xl:col-span-4 bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md h-fit">
            <h3 className="text-lg font-bold text-dark-on-surface mb-6">Lignes configurées</h3>
            
            {loading ? (
              <div className="flex justify-center py-12">
                <svg className="h-8 w-8 animate-spin text-blue-500" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
              </div>
            ) : lignes.length === 0 ? (
              <div className="text-center py-12 text-sm text-dark-on-surface-variant">
                Aucune ligne de bus configurée.
              </div>
            ) : (
              <div className="space-y-4 max-h-[calc(100vh-280px)] overflow-y-auto pr-1">
                {lignes.map((ligne) => (
                  <div key={ligne.id} className="border border-dark-outline/40 bg-dark-bg/25 rounded-2xl p-4 flex flex-col justify-between gap-4">
                    <div>
                      <div className="flex items-center justify-between">
                        <h4 className="font-bold text-dark-on-surface text-sm truncate max-w-[150px]">{ligne.nom}</h4>
                        <span className={`px-2 py-0.5 rounded-full text-[9px] font-semibold tracking-wider uppercase ${
                          ligne.actif 
                            ? 'bg-success-green/15 text-green-400 border border-success-green/30' 
                            : 'bg-slate-800 text-slate-400 border border-slate-700'
                        }`}>
                          {ligne.actif ? 'Active' : 'Inactive'}
                        </span>
                      </div>
                      <p className="text-[11px] text-dark-on-surface-variant mt-1 font-medium">
                        {ligne.stations.length} arrêt(s) configuré(s).
                      </p>
                    </div>

                    <div className="flex items-center justify-end gap-2">
                      <button
                        onClick={() => handleToggleActif(ligne.id, ligne.actif)}
                        className={`p-2 rounded-xl border transition-all cursor-pointer ${
                          ligne.actif
                            ? 'bg-success-green/10 border-success-green/30 text-green-400 hover:bg-success-green/20'
                            : 'bg-slate-800 border-slate-700 text-slate-400 hover:bg-slate-700'
                        }`}
                        title={ligne.actif ? 'Désactiver' : 'Activer'}
                      >
                        <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5.636 18.364a9 9 0 010-12.728m12.728 0a9 9 0 010 12.728m-9.9-2.829a5 5 0 010-7.07m7.07 0a5 5 0 010 7.07M13 12a1 1 0 11-2 0 1 1 0 012 0z" />
                        </svg>
                      </button>
                      <button
                        onClick={() => handleEditClick(ligne)}
                        className="p-2 rounded-xl bg-blue-600/10 border border-blue-600/30 text-blue-300 hover:bg-blue-600/20 transition-all cursor-pointer"
                        title="Modifier"
                      >
                        <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-2.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                        </svg>
                      </button>
                      <button
                        onClick={() => handleDelete(ligne.id)}
                        className="p-2 rounded-xl bg-danger-red-dark/10 border border-danger-red-dark/30 text-danger-red-dark hover:bg-danger-red-dark/20 transition-all cursor-pointer"
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

          {/* Colonne 2: Formulaire */}
          <div className="xl:col-span-4 bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md h-fit">
            <h3 className="text-lg font-bold text-dark-on-surface mb-6">
              {editingLigne ? 'Modifier la ligne' : 'Ajouter une ligne'}
            </h3>

            <form onSubmit={handleSubmit} className="space-y-6">
              <div>
                <label className="block text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                  Nom de la ligne
                </label>
                <input
                  type="text"
                  required
                  value={nom}
                  onChange={(e) => setNom(e.target.value)}
                  className="mt-1 block w-full rounded-xl border border-dark-outline bg-dark-bg/60 px-4 py-2.5 text-dark-on-surface placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-sm"
                  placeholder="Ex: Lille - Arras"
                />
              </div>

              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <label className="block text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                    Points d’arrêt
                  </label>
                  <button
                    type="button"
                    onClick={handleAddStationField}
                    className="text-xs font-bold text-blue-400 hover:text-blue-300 flex items-center gap-1 cursor-pointer"
                  >
                    + Ajouter
                  </button>
                </div>

                <div className="space-y-4 max-h-[calc(100vh-420px)] overflow-y-auto pr-1">
                  {stations.map((station, index) => (
                    <div key={index} className="border border-dark-outline/30 bg-dark-bg/40 rounded-xl p-4 space-y-3 relative">
                      <button
                        type="button"
                        onClick={() => handleRemoveStationField(index)}
                        className="absolute top-2 right-2 text-slate-500 hover:text-danger-red-dark cursor-pointer"
                      >
                        <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                      </button>

                      <div>
                        <input
                          type="text"
                          required
                          value={station.name}
                          onChange={(e) => handleStationChange(index, 'name', e.target.value)}
                          className="block w-full rounded-lg border border-dark-outline bg-dark-surface px-3 py-1.5 text-dark-on-surface placeholder-slate-500 focus:border-blue-500 focus:outline-none text-xs"
                          placeholder={`Nom de l’arrêt ${index + 1}`}
                        />
                      </div>

                      <div className="grid grid-cols-2 gap-2">
                        <div>
                          <label className="block text-[9px] font-semibold text-dark-on-surface-variant uppercase">Lat</label>
                          <input
                            type="number"
                            step="any"
                            required
                            value={station.latitude}
                            onChange={(e) => handleStationChange(index, 'latitude', e.target.value)}
                            className="mt-0.5 block w-full rounded-lg border border-dark-outline bg-dark-surface px-3 py-1.5 text-dark-on-surface focus:border-blue-500 focus:outline-none text-xs"
                          />
                        </div>
                        <div>
                          <label className="block text-[9px] font-semibold text-dark-on-surface-variant uppercase">Lng</label>
                          <input
                            type="number"
                            step="any"
                            required
                            value={station.longitude}
                            onChange={(e) => handleStationChange(index, 'longitude', e.target.value)}
                            className="mt-0.5 block w-full rounded-lg border border-dark-outline bg-dark-surface px-3 py-1.5 text-dark-on-surface focus:border-blue-500 focus:outline-none text-xs"
                          />
                        </div>
                      </div>

                      <div className="grid grid-cols-2 gap-2">
                        <div>
                          <label className="block text-[9px] font-semibold text-dark-on-surface-variant uppercase">Approche (m)</label>
                          <input
                            type="number"
                            required
                            value={station.approachRadius}
                            onChange={(e) => handleStationChange(index, 'approachRadius', e.target.value)}
                            className="mt-0.5 block w-full rounded-lg border border-dark-outline bg-dark-surface px-3 py-1.5 text-dark-on-surface focus:border-blue-500 focus:outline-none text-xs"
                          />
                        </div>
                        <div>
                          <label className="block text-[9px] font-semibold text-dark-on-surface-variant uppercase">Arrêt (m)</label>
                          <input
                            type="number"
                            required
                            value={station.arrivalRadius}
                            onChange={(e) => handleStationChange(index, 'arrivalRadius', e.target.value)}
                            className="mt-0.5 block w-full rounded-lg border border-dark-outline bg-dark-surface px-3 py-1.5 text-dark-on-surface focus:border-blue-500 focus:outline-none text-xs"
                          />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="flex gap-2 pt-2">
                {editingLigne && (
                  <button
                    type="button"
                    onClick={() => {
                      setEditingLigne(null);
                      setNom('');
                      setStations([{ name: '', latitude: 0, longitude: 0, approachRadius: 300, arrivalRadius: 80 }]);
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
                  {submitting ? 'Sauvegarde...' : editingLigne ? 'Mettre à jour' : 'Créer la ligne'}
                </button>
              </div>
            </form>
          </div>

          {/* Colonne 3: Carte Interactive */}
          <div className="xl:col-span-4 bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md h-[400px] xl:h-[calc(100vh-220px)] flex flex-col">
            <h3 className="text-lg font-bold text-dark-on-surface mb-3 flex items-center justify-between">
              <span>Carte interactive</span>
              <span className="text-[10px] bg-blue-600/20 text-blue-300 border border-blue-600/40 px-2 py-0.5 rounded-full font-medium uppercase tracking-wider">
                Clic = Placer arrêt
              </span>
            </h3>
            <div className="flex-1 w-full rounded-2xl overflow-hidden min-h-[250px]">
              <Map
                stations={stations as Station[]}
                onMapClick={handleMapClick}
                onMarkerDrag={handleMarkerDrag}
              />
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
