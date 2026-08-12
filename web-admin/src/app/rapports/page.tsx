'use client';

import React, { useEffect, useState } from 'react';
import Sidebar from '@/components/Sidebar';
import { supabase } from '@/lib/supabase';

interface Rapport {
  id: string;
  service_nom: string;
  date: string;
  recettes_especes: number;
  ventes_tickets_json: Record<string, number> | null;
  trajets_effectues_json: Array<{
    id?: string;
    title?: string;
    routeTitle?: string;
    time?: string;
    direction?: string;
  }> | null;
  cree_le: string;
  conducteur?: {
    nom: string;
  };
}

export default function RapportsPage() {
  const [rapports, setRapports] = useState<Rapport[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedRapportId, setExpandedRapportId] = useState<string | null>(null);

  useEffect(() => {
    fetchRapports();
  }, []);

  const fetchRapports = async () => {
    try {
      setLoading(true);
      const { data, error } = await supabase
        .from('rapports_services')
        .select('id, service_nom, date, recettes_especes, ventes_tickets_json, trajets_effectues_json, cree_le, conducteur:conducteur_id(nom)')
        .order('cree_le', { ascending: false });

      if (error) throw error;

      // Safe cast for relations
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const formatted = (data ?? []).map((r: any) => ({
        id: r.id,
        service_nom: r.service_nom,
        date: r.date,
        recettes_especes: Number(r.recettes_especes || 0),
        ventes_tickets_json: r.ventes_tickets_json,
        trajets_effectues_json: r.trajets_effectues_json,
        cree_le: r.cree_le,
        conducteur: Array.isArray(r.conducteur) ? r.conducteur[0] : r.conducteur,
      })) as Rapport[];

      setRapports(formatted);
    } catch (error) {
      console.error('Error fetching reports:', error);
    } finally {
      setLoading(false);
    }
  };

  const toggleExpand = (id: string) => {
    setExpandedRapportId(expandedRapportId === id ? null : id);
  };

  // Helper to format currency
  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
  };

  // Helper to format timestamp
  const formatDateTime = (timestampStr: string) => {
    try {
      const date = new Date(timestampStr);
      return date.toLocaleString('fr-FR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return timestampStr;
    }
  };

  return (
    <div className="flex h-screen bg-dark-bg font-sans overflow-hidden">
      <Sidebar />

      <main className="flex-1 flex flex-col overflow-y-auto px-8 py-8">
        <div className="mb-8">
          <h2 className="text-2xl font-bold text-dark-on-surface">Rapports de service</h2>
          <p className="text-sm text-dark-on-surface-variant">
            Consultez les feuilles de route de caisse, les recettes collectées et les trajets validés par les conducteurs.
          </p>
        </div>

        {loading ? (
          <div className="flex flex-1 items-center justify-center py-12">
            <svg className="h-8 w-8 animate-spin text-blue-500" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
            </svg>
          </div>
        ) : rapports.length === 0 ? (
          <div className="bg-dark-surface border border-dark-outline rounded-3xl p-12 text-center text-dark-on-surface-variant">
            {"Aucun rapport de service n'a été transmis pour le moment."}
          </div>
        ) : (
          <div className="space-y-4">
            {rapports.map((rapport) => {
              const isExpanded = expandedRapportId === rapport.id;
              return (
                <div
                  key={rapport.id}
                  className="bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md transition-all duration-300 hover:border-blue-500/20"
                >
                  <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                    <div className="flex items-start gap-4">
                      {/* Avatar initials of driver */}
                      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-blue-600/10 border border-blue-600/25 text-blue-300 text-base font-bold">
                        {rapport.conducteur?.nom.substring(0, 2).toUpperCase() ?? 'CO'}
                      </div>
                      <div>
                        <h4 className="font-bold text-dark-on-surface text-base">
                          {rapport.conducteur?.nom ?? 'Conducteur Inconnu'}
                        </h4>
                        <p className="text-xs text-dark-on-surface-variant mt-1 flex flex-wrap gap-x-4 gap-y-1">
                          <span>📅 Service du : <strong>{rapport.date}</strong></span>
                          <span>🚌 Ligne : <strong>{rapport.service_nom}</strong></span>
                          <span>⏰ Transmis le : {formatDateTime(rapport.cree_le)}</span>
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-6 self-end md:self-center">
                      <div className="text-right">
                        <span className="text-[10px] uppercase font-bold text-dark-on-surface-variant tracking-wider">
                          Recette Espèces
                        </span>
                        <p className="text-lg font-extrabold text-green-400">
                          {formatCurrency(rapport.recettes_especes)}
                        </p>
                      </div>

                      <button
                        onClick={() => toggleExpand(rapport.id)}
                        className="p-2.5 rounded-xl bg-blue-600/10 border border-blue-600/30 text-blue-300 hover:bg-blue-600/20 transition-all cursor-pointer"
                        title={isExpanded ? 'Masquer les détails' : 'Afficher les détails'}
                      >
                        <svg
                          className={`h-5 w-5 transform transition-transform duration-300 ${isExpanded ? 'rotate-180' : ''}`}
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                        </svg>
                      </button>
                    </div>
                  </div>

                  {/* Expanded detail section */}
                  {isExpanded && (
                    <div className="mt-6 pt-6 border-t border-dark-outline/40 grid grid-cols-1 md:grid-cols-2 gap-6 animate-fade-in">
                      {/* Ventes de tickets */}
                      <div className="bg-dark-bg/30 border border-dark-outline/20 rounded-2xl p-5">
                        <h5 className="text-sm font-bold text-dark-on-surface mb-3 flex items-center gap-2">
                          🎫 Tickets vendus
                        </h5>
                        {rapport.ventes_tickets_json && Object.keys(rapport.ventes_tickets_json).length > 0 ? (
                          <div className="space-y-2">
                            {Object.entries(rapport.ventes_tickets_json).map(([ticketName, count]) => (
                              <div key={ticketName} className="flex justify-between items-center text-xs">
                                <span className="text-dark-on-surface-variant font-medium">{ticketName}</span>
                                <span className="px-2.5 py-0.5 rounded-lg bg-blue-600/10 border border-blue-600/25 text-blue-300 font-bold">
                                  x{count}
                                </span>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <p className="text-xs text-dark-on-surface-variant italic">Aucun ticket vendu durant ce service.</p>
                        )}
                      </div>

                      {/* Trajets effectués */}
                      <div className="bg-dark-bg/30 border border-dark-outline/20 rounded-2xl p-5">
                        <h5 className="text-sm font-bold text-dark-on-surface mb-3 flex items-center gap-2">
                          🗺️ Trajets effectués
                        </h5>
                        {rapport.trajets_effectues_json && rapport.trajets_effectues_json.length > 0 ? (
                          <div className="space-y-3">
                            {rapport.trajets_effectues_json.map((trajet, idx) => (
                              <div key={idx} className="border-l-2 border-blue-600/40 pl-3 py-1">
                                <p className="text-xs font-bold text-dark-on-surface">
                                  {trajet.title || trajet.routeTitle || 'Trajet'}
                                </p>
                                <p className="text-[10px] text-dark-on-surface-variant mt-0.5 flex gap-2">
                                  {trajet.time && <span>🕒 {trajet.time}</span>}
                                  {trajet.direction && <span>方向 {trajet.direction}</span>}
                                </p>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <p className="text-xs text-dark-on-surface-variant italic">Aucun trajet enregistré durant ce service.</p>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </main>
    </div>
  );
}
