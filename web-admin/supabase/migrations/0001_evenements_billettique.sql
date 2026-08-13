-- Table des événements de billettique (ventes + scans de titres) remontés par
-- l'app Android, utilisée par la page Statistiques du web-admin pour analyser
-- les courses par jour/mois/agent/ligne et la fréquentation par arrêt.
--
-- À exécuter manuellement dans l'éditeur SQL de Supabase (aucun accès direct
-- à la base depuis cet environnement) : Dashboard > SQL Editor > coller ce
-- fichier > Run.

create table if not exists public.evenements_billettique (
  id text primary key,
  type_evenement text not null check (type_evenement in ('VENTE', 'SCAN_CARNET', 'SCAN_ABONNEMENT')),
  type_ticket text not null,
  prix_cents integer not null default 0,
  station_id text,
  station_nom text,
  ligne_id text,
  ligne_nom text,
  agent_id text,
  horodatage timestamptz not null,
  cree_le timestamptz not null default now()
);

create index if not exists evenements_billettique_horodatage_idx on public.evenements_billettique (horodatage);
create index if not exists evenements_billettique_station_idx on public.evenements_billettique (station_nom);
create index if not exists evenements_billettique_agent_idx on public.evenements_billettique (agent_id);
create index if not exists evenements_billettique_ligne_idx on public.evenements_billettique (ligne_nom);

alter table public.evenements_billettique enable row level security;

-- Cohérent avec le reste du projet (lignes, tarifs, conducteurs) : l'app
-- conducteur et le web-admin utilisent tous deux la clé publishable pour
-- lire/écrire, donc politiques permissives ici aussi.
create policy "evenements_billettique_select" on public.evenements_billettique
  for select using (true);

create policy "evenements_billettique_insert" on public.evenements_billettique
  for insert with check (true);
