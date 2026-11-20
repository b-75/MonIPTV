# Mon IPTV

Appli IPTV personnelle pour Android TV (Freebox Pop / Mini 4K, Fire TV, Google TV…).

## Fonctions
- **TV en direct** : catégories, liste des chaînes avec logo et programme en cours, zapping avec les flèches haut/bas ou CH+/CH-, saisie directe du numéro de chaîne
- **Favoris** : appui long sur OK (ou touche Menu) sur une chaîne
- **Guide des programmes** (XMLTV) : ce qui passe en ce moment, la suite et le programme de la journée
- **Films** et **Séries** avec affiches, saisons/épisodes et reprise de lecture là où tu t'étais arrêté
- Sources acceptées : identifiants **Xtream Codes** ou lien **M3U** (+ guide XMLTV facultatif)

Tes identifiants restent uniquement sur ta box (et sont exclus des sauvegardes Google) : ils ne sont jamais dans ce dépôt.

## Sécurité
L'appli est signée avec une clé privée stockée dans les **Secrets** GitHub du dépôt
(`KEYSTORE_BASE64` et `KEYSTORE_PASSWORD`, dans Settings → Secrets and variables → Actions).
Garde une copie de cette clé en lieu sûr : sans elle, impossible de publier une mise à jour
installable par-dessus l'appli existante.

## Installer sur la Freebox
1. Sur la Freebox, installe l'appli **Downloader** (by AFTVnews) depuis le Play Store.
2. Autorise-la à installer des applis : Paramètres → Applications → Accès spécifiques → Installation d'applis inconnues → Downloader → Autoriser.
3. Dans Downloader, tape l'adresse :
   `https://github.com/TON-PSEUDO/MonIPTV/releases/latest/download/MonIPTV.apk`
4. Installe, puis ouvre **Mon IPTV** depuis l'accueil de la Freebox.

## Mettre à jour
Chaque modification envoyée sur GitHub recompile l'appli automatiquement (onglet **Actions**).
Refais simplement l'étape 3 : la nouvelle version s'installe par-dessus l'ancienne, favoris conservés.

## Personnaliser
- Couleurs : `app/src/main/res/values/colors.xml`
- Nom de l'appli : `app/src/main/res/values/strings.xml`
- Icônes des tuiles d'accueil : `MainActivity.kt` (bloc PERSONNALISATION)
- Icône / bannière : `app/src/main/res/mipmap-*/ic_launcher.png` et `drawable-xhdpi/banner.png`
