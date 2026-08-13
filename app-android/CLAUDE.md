# Instructions Claude Code — Projet Transport

## Projet
Application Android native — annonces TTS pour trajets TER (Girouette Bus).
Stack : Kotlin, Jetpack Compose, Hilt, Room, OsmDroid, TTS, Bluetooth SCO.
Statut : MVP complet (Login, Home, Tracking, Caisse, Settings).
L'app est **en lecture seule** pour les lignes/arrêts/annonces vocales : toute la gestion
(création, modification, suppression) se fait exclusivement depuis le web-admin. L'app se
synchronise avec Supabase au démarrage (`HomeViewModel.init`) et via le bouton ↻ sur Home
(`RouteRepository.fetchRoutesFromSupabase()`).

## Branche Git
- Toujours travailler et committer sur **`master`**
- Ne jamais créer de branches séparées sans autorisation explicite
- Si une branche `claude/*` est créée automatiquement, la merger dans `master` immédiatement

## Règles techniques
- Build : `./gradlew assembleDebug` avant tout commit
- Ne jamais suggérer `kapt` — utiliser uniquement `ksp()`
- Annotations `@Composable` obligatoires sur toutes les fonctions appelant des Composables
- `collectAsStateWithLifecycle()` pour les Flows dans Compose (pas `collectAsState()`)

## Vérifications temporelles automatiques (à chaque requête)
- **Dimanche > 12h00** → vérifier Coran (3p/j) + sport dans Todoist pour les 14 prochains jours ; demander paiements hors budget
- **Après 08h00** + tâches non validées → demander si à reporter ou déjà fait
- **Après le 19 du mois** + pas de bilan financier → demander si paiements OK (sinon créer tâche Todoist)

## Heure locale (France)
- **Été** (dernier dimanche mars → dernier dimanche octobre) → **UTC+2** (CEST)
- **Hiver** (reste de l'année) → **UTC+1** (CET)
- Ne jamais afficher une heure UTC brute.

## Cohérence planning (sommeil / énergie)
- Avant de valider un créneau de deep work : vérifier heure de coucher + calculer le sommeil réel
- Si sommeil < 6h + service bus le lendemain → signaler l'incohérence, ne pas ajouter sans validation
- Ne jamais prendre un planning "sur papier" sans croiser les contraintes réelles

## Propagation des mises à jour
- Toute modification dans un fichier d'instructions d'agent doit être propagée simultanément à tous les autres fichiers agents du monorepo
- Fichiers concernés : voir `/CLAUDE.md` racine pour la liste complète
