'use client';

import React, { useEffect, useMemo, useState } from 'react';
import Sidebar from '@/components/Sidebar';
import { supabase } from '@/lib/supabase';

interface Evenement {
  id: string;
  type_evenement: 'VENTE' | 'SCAN_CARNET' | 'SCAN_ABONNEMENT';
  type_ticket: string;
  prix_cents: number;
  station_id: string | null;
  station_nom: string | null;
  ligne_id: string | null;
  ligne_nom: string | null;
  agent_id: string | null;
  horodatage: string;
}

interface Conducteur {
  agent_id: string;
  nom: string;
}

// Couleurs catégorielles (mode sombre, validées CVD — cf. skill dataviz) : ordre fixe,
// jamais recyclé, un titre = une couleur sur toute la page.
const TICKET_TYPES = [
  { value: 'PLEIN_TARIF', label: 'Plein tarif', color: '#3987e5' },
  { value: 'CARNET', label: 'Carnet 10 trajets', color: '#d95926' },
  { value: 'ABONNEMENT_MENSUEL', label: 'Abonnement mensuel', color: '#199e70' },
  { value: 'CONTREMARQUE', label: 'Contremarque', color: '#c98500' },
] as const;

const ticketMeta = (type: string) =>
  TICKET_TYPES.find((t) => t.value === type) ?? { value: type, label: type, color: '#6b7280' };

const formatEuros = (cents: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(cents / 100);

const toISODate = (d: Date) => d.toISOString().slice(0, 10);

const formatDateTime = (iso: string) =>
  new Date(iso).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

export default function StatistiquesPage() {
  const [evenements, setEvenements] = useState<Evenement[]>([]);
  const [conducteurs, setConducteurs] = useState<Conducteur[]>([]);
  const [loading, setLoading] = useState(true);

  const today = useMemo(() => new Date(), []);
  const firstOfMonth = useMemo(() => new Date(today.getFullYear(), today.getMonth(), 1), [today]);

  const [dateDebut, setDateDebut] = useState(toISODate(firstOfMonth));
  const [dateFin, setDateFin] = useState(toISODate(today));
  const [agentFiltre, setAgentFiltre] = useState('');
  const [ligneFiltre, setLigneFiltre] = useState('');

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [{ data: evData, error: evErr }, { data: condData }] = await Promise.all([
        supabase
          .from('evenements_billettique')
          .select('*')
          .order('horodatage', { ascending: false })
          .limit(5000),
        supabase.from('conducteurs').select('agent_id, nom'),
      ]);
      if (evErr) throw evErr;
      setEvenements((evData ?? []) as Evenement[]);
      setConducteurs((condData ?? []) as Conducteur[]);
    } catch (error) {
      console.error('Error fetching stats:', error);
    } finally {
      setLoading(false);
    }
  };

  const nomAgent = (agentId: string | null) =>
    conducteurs.find((c) => c.agent_id === agentId)?.nom ?? agentId ?? 'Agent inconnu';

  const agentsDisponibles = useMemo(() => {
    const ids = Array.from(new Set(evenements.map((e) => e.agent_id).filter(Boolean))) as string[];
    return ids.map((id) => ({ id, nom: nomAgent(id) })).sort((a, b) => a.nom.localeCompare(b.nom));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [evenements, conducteurs]);

  const lignesDisponibles = useMemo(() => {
    return Array.from(new Set(evenements.map((e) => e.ligne_nom).filter(Boolean))) as string[];
  }, [evenements]);

  const applyQuickRange = (mode: 'today' | 'week' | 'month') => {
    const now = new Date();
    if (mode === 'today') {
      setDateDebut(toISODate(now));
      setDateFin(toISODate(now));
    } else if (mode === 'week') {
      const weekAgo = new Date(now);
      weekAgo.setDate(now.getDate() - 6);
      setDateDebut(toISODate(weekAgo));
      setDateFin(toISODate(now));
    } else {
      setDateDebut(toISODate(new Date(now.getFullYear(), now.getMonth(), 1)));
      setDateFin(toISODate(now));
    }
  };

  const filtered = useMemo(() => {
    const start = new Date(`${dateDebut}T00:00:00`);
    const end = new Date(`${dateFin}T23:59:59`);
    return evenements.filter((e) => {
      const t = new Date(e.horodatage);
      if (t < start || t > end) return false;
      if (agentFiltre && e.agent_id !== agentFiltre) return false;
      if (ligneFiltre && e.ligne_nom !== ligneFiltre) return false;
      return true;
    });
  }, [evenements, dateDebut, dateFin, agentFiltre, ligneFiltre]);

  const totalCourses = filtered.length;
  const recetteCents = filtered
    .filter((e) => e.type_evenement === 'VENTE')
    .reduce((sum, e) => sum + e.prix_cents, 0);

  const parType = useMemo(() => {
    const map = new Map<string, number>();
    filtered.forEach((e) => map.set(e.type_ticket, (map.get(e.type_ticket) ?? 0) + 1));
    return TICKET_TYPES.map((t) => ({ ...t, count: map.get(t.value) ?? 0 })).sort((a, b) => b.count - a.count);
  }, [filtered]);

  const titrePlusUtilise = parType[0];

  const parArret = useMemo(() => {
    const map = new Map<string, Record<string, number>>();
    filtered.forEach((e) => {
      const key = e.station_nom ?? 'Arrêt non localisé';
      if (!map.has(key)) map.set(key, {});
      const row = map.get(key)!;
      row[e.type_ticket] = (row[e.type_ticket] ?? 0) + 1;
    });
    return Array.from(map.entries())
      .map(([station, counts]) => ({
        station,
        counts,
        total: Object.values(counts).reduce((a, b) => a + b, 0),
      }))
      .sort((a, b) => b.total - a.total);
  }, [filtered]);

  const arretLePlusFrequente = parArret[0];
  const maxTotalArret = parArret[0]?.total ?? 1;

  const evenementsRecents = filtered.slice(0, 60);

  return (
    <div className="flex h-screen bg-dark-bg font-sans overflow-hidden">
      <Sidebar />

      <main className="flex-1 flex flex-col overflow-y-auto px-8 py-8">
        <div className="mb-8">
          <h2 className="text-2xl font-bold text-dark-on-surface">Statistiques</h2>
          <p className="text-sm text-dark-on-surface-variant">
            Ventes et montées validées (carnets, abonnements) — filtrez par période, agent ou ligne.
          </p>
        </div>

        {/* ── Filtres ── */}
        <div className="bg-dark-surface border border-dark-outline rounded-3xl p-5 mb-6 flex flex-wrap items-end gap-4">
          <div>
            <label className="block text-[10px] font-semibold text-dark-on-surface-variant uppercase tracking-wider mb-1">
              Du
            </label>
            <input
              type="date"
              value={dateDebut}
              onChange={(e) => setDateDebut(e.target.value)}
              className="rounded-xl border border-dark-outline bg-dark-bg/60 px-3 py-2 text-dark-on-surface text-sm focus:border-blue-500 focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-[10px] font-semibold text-dark-on-surface-variant uppercase tracking-wider mb-1">
              Au
            </label>
            <input
              type="date"
              value={dateFin}
              onChange={(e) => setDateFin(e.target.value)}
              className="rounded-xl border border-dark-outline bg-dark-bg/60 px-3 py-2 text-dark-on-surface text-sm focus:border-blue-500 focus:outline-none"
            />
          </div>

          <div className="flex gap-1.5">
            {(['today', 'week', 'month'] as const).map((mode) => (
              <button
                key={mode}
                onClick={() => applyQuickRange(mode)}
                className="px-3 py-2 rounded-xl text-xs font-semibold bg-dark-bg/60 border border-dark-outline text-dark-on-surface-variant hover:border-blue-500/50 hover:text-dark-on-surface transition-all cursor-pointer"
              >
                {mode === 'today' ? "Aujourd'hui" : mode === 'week' ? '7 derniers jours' : 'Ce mois-ci'}
              </button>
            ))}
          </div>

          <div className="min-w-[180px]">
            <label className="block text-[10px] font-semibold text-dark-on-surface-variant uppercase tracking-wider mb-1">
              Agent
            </label>
            <select
              value={agentFiltre}
              onChange={(e) => setAgentFiltre(e.target.value)}
              className="w-full rounded-xl border border-dark-outline bg-dark-bg/60 px-3 py-2 text-dark-on-surface text-sm focus:border-blue-500 focus:outline-none"
            >
              <option value="">Tous les agents</option>
              {agentsDisponibles.map((a) => (
                <option key={a.id} value={a.id}>{a.nom}</option>
              ))}
            </select>
          </div>

          <div className="min-w-[180px]">
            <label className="block text-[10px] font-semibold text-dark-on-surface-variant uppercase tracking-wider mb-1">
              Ligne
            </label>
            <select
              value={ligneFiltre}
              onChange={(e) => setLigneFiltre(e.target.value)}
              className="w-full rounded-xl border border-dark-outline bg-dark-bg/60 px-3 py-2 text-dark-on-surface text-sm focus:border-blue-500 focus:outline-none"
            >
              <option value="">Toutes les lignes</option>
              {lignesDisponibles.map((l) => (
                <option key={l} value={l}>{l}</option>
              ))}
            </select>
          </div>
        </div>

        {loading ? (
          <div className="flex flex-1 items-center justify-center py-12">
            <svg className="h-8 w-8 animate-spin text-blue-500" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
            </svg>
          </div>
        ) : (
          <>
            {/* ── Stat tiles ── */}
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-5 mb-6">
              <StatTile label="Courses (ventes + montées)" value={totalCourses.toString()} />
              <StatTile label="Recette (ventes)" value={formatEuros(recetteCents)} accent="text-green-400" />
              <StatTile
                label="Titre le plus utilisé"
                value={titrePlusUtilise && titrePlusUtilise.count > 0 ? titrePlusUtilise.label : '—'}
                sub={titrePlusUtilise && titrePlusUtilise.count > 0 ? `${titrePlusUtilise.count} montée(s)` : undefined}
                dotColor={titrePlusUtilise?.count ? titrePlusUtilise.color : undefined}
              />
              <StatTile
                label="Arrêt le plus fréquenté"
                value={arretLePlusFrequente?.station ?? '—'}
                sub={arretLePlusFrequente ? `${arretLePlusFrequente.total} montée(s)` : undefined}
              />
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-12 gap-6">
              {/* ── Répartition par type de titre ── */}
              <div className="xl:col-span-4 bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md h-fit">
                <h3 className="text-sm font-bold text-dark-on-surface mb-1">Répartition par titre</h3>
                <p className="text-xs text-dark-on-surface-variant mb-5">Ventes et montées validées, période filtrée</p>

                <div className="space-y-4">
                  {parType.map((t) => {
                    const max = parType[0]?.count || 1;
                    const pct = Math.round((t.count / max) * 100);
                    return (
                      <div key={t.value}>
                        <div className="flex items-center justify-between text-xs mb-1.5">
                          <span className="flex items-center gap-2 font-medium text-dark-on-surface">
                            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: t.color }} />
                            {t.label}
                          </span>
                          <span className="font-bold text-dark-on-surface">{t.count}</span>
                        </div>
                        <div className="h-2 w-full rounded-full bg-dark-bg/60 overflow-hidden">
                          <div
                            className="h-full rounded-full transition-all"
                            style={{ width: `${t.count > 0 ? Math.max(pct, 3) : 0}%`, backgroundColor: t.color }}
                          />
                        </div>
                      </div>
                    );
                  })}
                  {totalCourses === 0 && (
                    <p className="text-xs text-dark-on-surface-variant italic">Aucune donnée sur cette période.</p>
                  )}
                </div>
              </div>

              {/* ── Fréquentation par arrêt ── */}
              <div className="xl:col-span-8 bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md">
                <h3 className="text-sm font-bold text-dark-on-surface mb-1">Fréquentation par arrêt</h3>
                <p className="text-xs text-dark-on-surface-variant mb-4">
                  Montées par arrêt, détaillées par titre — utile pour repérer les arrêts les plus fréquentés.
                </p>

                {/* Légende */}
                <div className="flex flex-wrap gap-x-4 gap-y-1.5 mb-4 pb-4 border-b border-dark-outline/30">
                  {TICKET_TYPES.map((t) => (
                    <span key={t.value} className="flex items-center gap-1.5 text-[11px] text-dark-on-surface-variant font-medium">
                      <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: t.color }} />
                      {t.label}
                    </span>
                  ))}
                </div>

                {parArret.length === 0 ? (
                  <p className="text-xs text-dark-on-surface-variant italic">Aucune montée enregistrée sur cette période.</p>
                ) : (
                  <div className="space-y-3 max-h-[520px] overflow-y-auto pr-1">
                    {parArret.map((row) => (
                      <div key={row.station} className="border border-dark-outline/30 bg-dark-bg/25 rounded-2xl p-4">
                        <div className="flex items-center justify-between mb-2">
                          <span className="text-sm font-bold text-dark-on-surface truncate max-w-[60%]">{row.station}</span>
                          <span className="text-xs font-bold text-dark-on-surface-variant">{row.total} montée{row.total > 1 ? 's' : ''}</span>
                        </div>
                        {/* Barre empilée par titre */}
                        <div className="flex h-3 w-full rounded-full overflow-hidden bg-dark-bg/60" style={{ gap: '2px' }}>
                          {TICKET_TYPES.map((t) => {
                            const count = row.counts[t.value] ?? 0;
                            if (count === 0) return null;
                            const widthPct = (count / maxTotalArret) * 100;
                            return (
                              <div
                                key={t.value}
                                title={`${t.label} : ${count}`}
                                style={{ width: `${widthPct}%`, backgroundColor: t.color, minWidth: '4px' }}
                                className="rounded-full"
                              />
                            );
                          })}
                        </div>
                        {/* Détail chiffré */}
                        <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2.5">
                          {TICKET_TYPES.filter((t) => (row.counts[t.value] ?? 0) > 0).map((t) => (
                            <span key={t.value} className="text-[11px] text-dark-on-surface-variant">
                              <span className="font-bold text-dark-on-surface">{row.counts[t.value]}</span> {t.label.toLowerCase()}
                            </span>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* ── Détail des événements ── */}
            <div className="bg-dark-surface border border-dark-outline rounded-3xl p-6 shadow-md mt-6">
              <h3 className="text-sm font-bold text-dark-on-surface mb-1">Détail des courses</h3>
              <p className="text-xs text-dark-on-surface-variant mb-4">
                {evenementsRecents.length} événement{evenementsRecents.length > 1 ? 's' : ''} le{evenementsRecents.length > 1 ? 's' : ''} plus récent{evenementsRecents.length > 1 ? 's' : ''} sur la période filtrée{totalCourses > evenementsRecents.length ? ` (${totalCourses} au total)` : ''}.
              </p>

              {evenementsRecents.length === 0 ? (
                <p className="text-xs text-dark-on-surface-variant italic">Aucun événement sur cette période.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="text-left text-dark-on-surface-variant uppercase text-[10px] tracking-wider border-b border-dark-outline/40">
                        <th className="py-2 pr-4 font-semibold">Date</th>
                        <th className="py-2 pr-4 font-semibold">Agent</th>
                        <th className="py-2 pr-4 font-semibold">Ligne</th>
                        <th className="py-2 pr-4 font-semibold">Arrêt</th>
                        <th className="py-2 pr-4 font-semibold">Titre</th>
                        <th className="py-2 pr-4 font-semibold text-right">Montant</th>
                      </tr>
                    </thead>
                    <tbody>
                      {evenementsRecents.map((e) => {
                        const meta = ticketMeta(e.type_ticket);
                        return (
                          <tr key={e.id} className="border-b border-dark-outline/15 hover:bg-dark-bg/30">
                            <td className="py-2 pr-4 text-dark-on-surface-variant whitespace-nowrap">{formatDateTime(e.horodatage)}</td>
                            <td className="py-2 pr-4 text-dark-on-surface">{nomAgent(e.agent_id)}</td>
                            <td className="py-2 pr-4 text-dark-on-surface-variant">{e.ligne_nom ?? '—'}</td>
                            <td className="py-2 pr-4 text-dark-on-surface-variant">{e.station_nom ?? '—'}</td>
                            <td className="py-2 pr-4">
                              <span className="inline-flex items-center gap-1.5 font-medium text-dark-on-surface">
                                <span className="inline-block h-2 w-2 rounded-full" style={{ backgroundColor: meta.color }} />
                                {meta.label}
                              </span>
                            </td>
                            <td className="py-2 pr-4 text-right font-semibold text-dark-on-surface">
                              {e.type_evenement === 'VENTE' ? formatEuros(e.prix_cents) : '—'}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </>
        )}
      </main>
    </div>
  );
}

function StatTile({
  label, value, sub, accent, dotColor,
}: { label: string; value: string; sub?: string; accent?: string; dotColor?: string }) {
  return (
    <div className="bg-dark-surface border border-dark-outline rounded-3xl p-5">
      <p className="text-[10px] uppercase font-bold text-dark-on-surface-variant tracking-wider mb-2">{label}</p>
      <p className={`text-xl font-extrabold truncate flex items-center gap-2 ${accent ?? 'text-dark-on-surface'}`}>
        {dotColor && <span className="inline-block h-2.5 w-2.5 rounded-full shrink-0" style={{ backgroundColor: dotColor }} />}
        {value}
      </p>
      {sub && <p className="text-[11px] text-dark-on-surface-variant mt-1">{sub}</p>}
    </div>
  );
}
