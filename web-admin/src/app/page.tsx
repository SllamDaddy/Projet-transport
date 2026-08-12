'use client';

import React, { useEffect, useState } from 'react';
import Sidebar from '@/components/Sidebar';
import { supabase } from '@/lib/supabase';

interface Rapport {
  id: string;
  service_nom: string;
  date: string;
  recettes_especes: number;
  cree_le: string;
  conducteur?: {
    nom: string;
  };
}

export default function DashboardPage() {
  const [stats, setStats] = useState({
    driversCount: 0,
    tarifsCount: 0,
    recettesTotales: 0,
  });
  const [recentRapports, setRecentRapports] = useState<Rapport[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        setLoading(true);

        // 1. Fetch Conducteurs count
        const { count: driversCount } = await supabase
          .from('conducteurs')
          .select('*', { count: 'exact', head: true });

        // 2. Fetch Tarifs count
        const { count: tarifsCount } = await supabase
          .from('tarifs')
          .select('*', { count: 'exact', head: true });

        // 3. Fetch Rapports (and calculate cash revenue & link to drivers)
        const { data: rapportsData } = await supabase
          .from('rapports_services')
          .select('id, service_nom, date, recettes_especes, cree_le, conducteur:conducteur_id(nom)')
          .order('cree_le', { ascending: false })
          .limit(5);

        // Calculate sum of cash revenues
        const { data: sumData } = await supabase
          .from('rapports_services')
          .select('recettes_especes');

        const totalCash = sumData?.reduce((acc, curr) => acc + Number(curr.recettes_especes || 0), 0) ?? 0;

        setStats({
          driversCount: driversCount ?? 0,
          tarifsCount: tarifsCount ?? 0,
          recettesTotales: totalCash,
        });

        // Safe cast for relations
        const formattedRapports = (rapportsData ?? []).map((r: any) => ({
          id: r.id,
          service_nom: r.service_nom,
          date: r.date,
          recettes_especes: Number(r.recettes_especes || 0),
          cree_le: r.cree_le,
          conducteur: Array.isArray(r.conducteur) ? r.conducteur[0] : r.conducteur,
        })) as Rapport[];

        setRecentRapports(formattedRapports);
      } catch (error) {
        console.error('Error fetching dashboard data:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchDashboardData();
  }, []);

  return (
    <div className="flex h-screen bg-dark-bg font-sans overflow-hidden">
      {/* Barre latérale de navigation */}
      <Sidebar />

      {/* Zone de contenu principal */}
      <main className="flex-1 flex flex-col overflow-y-auto px-8 py-8">
        {/* Header de page */}
        <div className="mb-8">
          <h2 className="text-2xl font-bold text-dark-on-surface">Tableau de bord</h2>
          <p className="text-sm text-dark-on-surface-variant">
            Vue d'ensemble de l'exploitation en temps réel
          </p>
        </div>

        {loading ? (
          <div className="flex flex-1 items-center justify-center">
            <svg
              className="h-8 w-8 animate-spin text-blue-500"
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
          </div>
        ) : (
          <div className="space-y-8">
            {/* Chiffres clés */}
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
              {/* Recettes */}
              <div className="bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md relative overflow-hidden">
                <div className="absolute right-0 bottom-0 translate-y-3 translate-x-3 text-blue-500/10 pointer-events-none">
                  <svg className="h-32 w-32" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                </div>
                <p className="text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                  Recettes totales (Espèces)
                </p>
                <p className="mt-2 text-3xl font-extrabold text-blue-200">
                  {stats.recettesTotales.toFixed(2)} €
                </p>
              </div>

              {/* Conducteurs */}
              <div className="bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md relative overflow-hidden">
                <div className="absolute right-0 bottom-0 translate-y-3 translate-x-3 text-blue-500/10 pointer-events-none">
                  <svg className="h-32 w-32" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                  </svg>
                </div>
                <p className="text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                  Conducteurs enregistrés
                </p>
                <p className="mt-2 text-3xl font-extrabold text-dark-on-surface">
                  {stats.driversCount}
                </p>
              </div>

              {/* Tarifs actifs */}
              <div className="bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md relative overflow-hidden">
                <div className="absolute right-0 bottom-0 translate-y-3 translate-x-3 text-blue-500/10 pointer-events-none">
                  <svg className="h-32 w-32" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
                  </svg>
                </div>
                <p className="text-xs font-semibold text-dark-on-surface-variant uppercase tracking-wider">
                  Tickets & Tarifs configurés
                </p>
                <p className="mt-2 text-3xl font-extrabold text-dark-on-surface">
                  {stats.tarifsCount}
                </p>
              </div>
            </div>

            {/* Rapports récents */}
            <div className="bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md">
              <h3 className="text-lg font-bold text-dark-on-surface mb-4">
                Derniers rapports de fin de service
              </h3>
              {recentRapports.length === 0 ? (
                <div className="text-center py-8 text-sm text-dark-on-surface-variant">
                  Aucun rapport de service envoyé pour le moment.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b border-dark-outline text-dark-on-surface-variant font-semibold">
                        <th className="pb-3">Date</th>
                        <th className="pb-3">Conducteur</th>
                        <th className="pb-3">Service</th>
                        <th className="pb-3 text-right">Recettes Espèces</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-dark-outline/40">
                      {recentRapports.map((rapport) => (
                        <tr key={rapport.id} className="text-dark-on-surface hover:bg-dark-surface-variant/20 transition-colors">
                          <td className="py-3.5">
                            {new Date(rapport.date).toLocaleDateString('fr-FR')}
                          </td>
                          <td className="py-3.5 font-medium">
                            {rapport.conducteur?.nom ?? 'Inconnu'}
                          </td>
                          <td className="py-3.5">
                            {rapport.service_nom}
                          </td>
                          <td className="py-3.5 text-right font-semibold text-blue-200">
                            {rapport.recettes_especes.toFixed(2)} €
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
