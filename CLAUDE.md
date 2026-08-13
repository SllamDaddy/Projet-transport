# Instructions Claude Code — Projet Transport Monorepo

Ce dépôt contient l'écosystème du Projet Transport :
- `app-android/` : Application mobile Android (Kotlin/Compose) pour le conducteur (TTS & Caisse). **Lecture seule** pour les lignes/arrêts/annonces vocales — synchronisée via Supabase, aucune création/édition locale.
- `web-admin/` : Portail d'administration web (Next.js/Tailwind), **source de vérité unique** pour la gestion des conducteurs, tarifs, rapports, lignes, arrêts et annonces vocales (TTS).

## Branche Git
- Toujours travailler et committer sur **`master`**
- Ne jamais créer de branches séparées sans autorisation explicite

## Commandes utiles

### Application Android (app-android/)
- Compiler l'application : `cd app-android && ./gradlew.bat :app:assembleDebug`
- Nettoyer le build : `cd app-android && ./gradlew.bat clean`

### Portail Web Admin (web-admin/)
- Lancer le serveur de développement : `cd web-admin && npm run dev`
- Compiler l'application Next.js : `cd web-admin && npm run build`
- Exécuter le linter : `cd web-admin && npm run lint`

## Heure locale (France)
- **Été** (dernier dimanche mars → dernier dimanche octobre) → **UTC+2** (CEST)
- **Hiver** (reste de l'année) → **UTC+1** (CET)
- Ne jamais afficher une heure UTC brute.
