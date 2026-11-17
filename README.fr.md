# MrCuisineConnectCookomix

> **Fork avec les recettes Cookomix.** Copie de
> [flexbox/MrCuisineVibeConnect](https://github.com/flexbox/MrCuisineVibeConnect) dont l'appli Toque
> ajoute les recettes de [cookomix.com](https://www.cookomix.com) compatibles avec le Monsieur
> Cuisine Connect, en plus des recettes Lidl. Toque se met à jour depuis les versions de **ce**
> dépôt (même signature que l'original : la mise à jour se fait par-dessus sans désinstaller).
>
> **Comment ça marche.** Cookomix n'a pas d'API publique (son API mobile exige une clé privée).
> Toque lit donc les pages publiques du site, depuis le robot lui-même : la liste des recettes
> vient des sitemaps, et chaque page contient le pas-à-pas Cookomix avec des réglages structurés
> (durée, °C, vitesse, sens inverse, pétrin, Varoma). Les recettes sont écrites pour le
> Thermomix ; chaque réglage est converti pour le MCC
> ([`Cookomix.kt`](core/src/main/kotlin/app/toque/core/recipes/Cookomix.kt)) :
>
> | Thermomix | Monsieur Cuisine Connect |
> |---|---|
> | vitesse cuillère (codée 0 par Cookomix, 40 tr/min) ou 0,5 | vitesse 1, la plus lente du MCC (les recettes Lidl mijotent aussi en vitesse 1 inverse) |
> | vitesse décimale (3,5) | arrondie au-dessus (4) |
> | température hors table (98 °C) | palier inférieur du MCC (95 °C), recette « adaptée » |
> | Varoma (120 °C au plus sur le Thermomix) | 120 °C en manuel, même vitesse : le programme Vapeur du MCC arrête la lame et s'arrête à 60 min |
> | « Rissoler » 120 °C vitesse 1 | 120 °C en manuel, vitesse 1 en continu (le programme Saisir du MCC ne tourne que par à-coups) |
> | vitesse > 3 en chauffe ou en sens inverse | vitesse 3, recette « adaptée » |
> | fouet (papillon) au-delà de la vitesse 4 | vitesse 4, recette « adaptée » |
> | pétrin | programme Pétrir, découpé en tranches de 2 min |
> | plus de 130 °C, mode inconnu | recette **incompatible**, non importée |
>
> Sur 371 recettes du site tirées au hasard : 304 compatibles telles quelles, 67 adaptées,
> 0 incompatible. Les limites du MCC appliquées viennent de sa notice (SKMC 1200) : 37 à 130 °C par
> paliers de 5, vitesse 1 à 3 en chauffe et en sens inverse, 1 à 4 avec le fouet, 99 min jusqu'à la
> vitesse 6 et 15 min au-delà. Les recettes adaptées portent le mot-clé « Adaptée MCC » et expliquent dans
> leurs notes ce qui a changé ; le texte de l'étape rappelle le réglage Thermomix d'origine.
> Comme pour les recettes Lidl, chaque réglage importé est à confirmer avant d'être lancé.
>
> *Réglages › Recettes Cookomix › Synchroniser Cookomix* : environ une heure la première fois
> (une page par seconde), puis seules les recettes modifiées sont retéléchargées. Le **compte
> Cookomix** (email ou pseudo + mot de passe, facultatif) sert à importer les recettes du carnet
> dans les favoris ; seul un jeton de connexion est enregistré, jamais le mot de passe.
> Si le site est en panne ou change de format, la synchro s'arrête avec un code d'erreur
> (CKX-01 à CKX-10, décrits dans [docs/COOKOMIX-ERREURS.md](docs/COOKOMIX-ERREURS.md)) sans
> supprimer aucune recette déjà enregistrée.
> Les recettes restent la propriété de Cookomix et de leurs auteurs : elles sont téléchargées pour
> un usage personnel par chaque robot et ne sont pas redistribuées par ce dépôt.

## Projet d'origine : MrCuisineVibeConnect

🇬🇧 **[English version](README.md)**

> **Version macOS.** Ceci est une copie de [Wuki60/MrCuisineVibeConnect](https://github.com/Wuki60/MrCuisineVibeConnect)
> dont les outils d'installation tournent sur un **Mac** : des scripts shell au lieu de PowerShell,
> et [mtkclient](https://github.com/bkerler/mtkclient) au lieu de SP Flash Tool et de son pilote
> Windows. L'appli Toque est inchangée et se met toujours à jour depuis les versions du dépôt
> d'origine. L'installation depuis macOS a été faite de bout en bout sur un vrai robot en
> novembre 2026 ; voir [AGENTS.md](AGENTS.md).

**Toque**, une interface moderne et rapide pour le robot **Monsieur Cuisine Connect** (SKMC 1200),
qui remplace l'appli d'origine sur la tablette du robot tout en gardant **les recettes Lidl**.

> Projet indépendant, non affilié à Lidl, SilverCrest ni Hoyer. Le pilotage du moteur repose
> sur une analyse publique du protocole ; il tourne au quotidien sur un robot (SKMC 1200, Android 6)
> depuis octobre 2026 et sur un second depuis novembre 2026, mais n'a pas été essayé sur d'autres.
> À utiliser à vos risques, en suivant la procédure de premier essai.

## Installer

**Commencez ici : [docs/INSTALLATION.fr.md](docs/INSTALLATION.fr.md)**, un guide pas à pas qui part
de zéro, pour Mac. Sous Windows, suivez le
[guide du dépôt d'origine](https://github.com/Wuki60/MrCuisineVibeConnect/blob/main/docs/INSTALLATION.fr.md).

En bref : il faut un Mac, un câble **USB-A mâle ↔ USB-A mâle**, un petit tournevis Torx et 1 à 2
heures. Vous préparez le Mac (Homebrew, puis une copie de ce dépôt), puis soit vous laissez
[Claude Code](https://code.claude.com/docs/en/quickstart) faire l'installation en suivant
[AGENTS.md](AGENTS.md), soit vous lancez vous-même [`tools/setup/toque-setup.sh`](tools/setup/toque-setup.sh),
une étape à la fois. Il sauvegarde d'abord le robot, le roote avec Magisk, installe Toque et active
ADB par Wi-Fi. Vous ne vous occupez que de la trappe, du câble, de l'interrupteur et d'un appui sur
l'écran du robot.

## Ce que fait Toque

- **Interface sombre**, pensée pour l'écran 7" et le bouton rotatif du robot, fluide sur sa
  tablette d'origine (1 Go de RAM, Android 6) : pas de bibliothèque lourde, images
  sous-échantillonnées, vues recyclées.
- **Recettes Cookomix** (ce fork) : les recettes de cookomix.com que le MCC peut faire, chaque
  réglage Thermomix converti pour le MCC et contrôlé par les mêmes sécurités (voir le haut de page).
- **Recettes Lidl, directement depuis monsieur-cuisine.com**. Toque télécharge les recettes
  officielles du Monsieur Cuisine connect (environ 1 700 en français, avec les réglages machine de
  chaque étape) par l'API publique du site, chaque nuit ou sur demande : aucun compte, aucun
  serveur à gérer, plus besoin de l'appli d'origine. Requêtes espacées, et seules les recettes
  nouvelles ou modifiées sont retéléchargées. On peut toujours importer à la place celles de
  l'appli d'origine.
- **Auteur, notes et avis** : chaque recette affiche qui l'a publiée et sa note sur le site, et la
  fiche charge les commentaires des utilisateurs (avec les réponses de Monsieur Cuisine).
- **Recherche qui marche** : insensible aux accents, aux pluriels et aux fautes de frappe
  (« bechamle », « risoto »). Recherche par ingrédients (« poulet, curry »), filtres, favoris.
- **Cuisine pas à pas** : chaque étape se lance d'un appui, l'étape suivante s'affiche toute
  seule, pesée intégrée, reprise après coupure. Les réglages qui se suivent sans geste à faire
  entre eux (rinçage, mixages en plusieurs vitesses…) partent ensemble d'un seul appui : la lame
  passe directement d'une vitesse et d'une température à l'autre, sans s'arrêter.
- **Cuisson manuelle**, programmes **Pétrir / Vapeur / Rissoler**, **Turbo**, **balance**.
- **Hors ligne** : tout fonctionne sans réseau (plus de « serveur injoignable » au démarrage).
- **Heure toujours juste** : le fuseau horaire est trouvé d'après la connexion Internet et l'heure
  vient du réseau (le robot n'a pas de pile d'horloge) ; réglable à la main dans Réglages › Date et heure.
- **Pensé pour un robot sans boutons Android** : Toque s'ouvre au démarrage, et un bouton
  **« Retour à Toque »** reste affiché par-dessus l'appli d'origine quand on l'ouvre.
- **Mises à jour depuis GitHub** : Réglages › Mises à jour vérifie les
  [versions publiées](https://github.com/Wuki60/MrCuisineVibeConnect/releases) de ce dépôt et installe
  la nouvelle (sans rien demander sur un robot rooté, puis Toque redémarre). Refusé pendant un programme.

## Sécurité : ce qui change par rapport à l'appli d'origine

Toutes les limites d'origine sont reprises (vitesse 3 maximum en chauffe et en sens inverse,
durées maximales, conditions du Turbo…). Plusieurs défauts relevés par l'analyse du protocole
sont corrigés :

| Défaut de l'appli d'origine | Toque |
|---|---|
| Le démarrage était demandé avant de vérifier le couvercle | Couvercle, erreurs et fraîcheur des mesures sont vérifiés **avant** tout démarrage |
| Aucune détection d'une base moteur muette | Plus de réponse pendant 1,5 s : tout s'arrête et une alerte s'affiche |
| Erreurs d'écriture série ignorées | Arrêt et alerte |
| Un STOP suivi d'un START pouvait ne jamais émettre d'arrêt | Tout redémarrage ou changement de sens attend un échantillon confirmant **moteur arrêté** |
| Lecture série sans resynchronisation | Trames fragmentées, collées ou corrompues gérées |
| La lame tournait parfois en vapeur | Vapeur à vitesse 0 |

Autres ajouts : confirmation (et montée progressive) avant de mixer un contenu à 60 °C ou plus,
limite de vitesse avec le fouet, alertes verrouillées jusqu'à acquittement, rien ne redémarre
tout seul après une pause ou une alerte. À froid, la lame va directement à la vitesse demandée. Le pilotage tourne dans **son propre processus** : un plantage de l'interface ne coupe
pas une cuisson, qui se termine normalement.

## Structure

```
core/   Logique pure Kotlin, testée sans Android
        protocol/  trames série (15 octets émis, 27 reçus, 4800 bauds)
        machine/   règles de sécurité, contrôleur, boucle 100 ms, liaisons (série root, TCP, démo)
        recipes/   modèle, import de la base d'origine, recherche
app/    Appli Android (minSdk 23), sans AndroidX
        machine/   service de pilotage (processus « :machine ») et IPC
        recipes/   catalogue local, synchro Lidl
        ui/        écrans
tools/  setup/toque-setup.sh (installation complète depuis un Mac, pilotable par un agent IA), robot/
        (scripts lancés sur le robot : root, écriture depuis TWRP, restauration), scripts
        d'installation, de restauration et de publication (.sh pour macOS, les .ps1 d'origine pour
        Windows), twin.py (jumeau numérique pour tester)
docs/   INSTALLATION.md (EN), INSTALLATION.fr.md (FR)
AGENTS.md  procédure d'installation pour un agent IA
```

## Compiler

Ouvrir le dossier dans **Android Studio**, attendre la synchronisation Gradle, puis
**Build › Build App Bundle(s) / APK(s) › Build APK(s)**. L'APK est dans
`app/build/outputs/apk/debug/`.

En ligne de commande : `./gradlew assembleDebug` (et `./gradlew :core:test` pour les tests). Gradle
demande le JDK 17 ou 21 ; avec un JDK par défaut plus récent, la compilation s'arrête avec un simple
numéro de version comme erreur. Le JDK d'Android Studio convient :
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## Tester sans le robot

1. **Mode Démo** (par défaut) : installer l'APK sur n'importe quel Android (ou l'émulateur
   d'Android Studio, en paysage). La base moteur est simulée ; Réglages › Outils › Diagnostic
   permet d'ouvrir le couvercle ou de provoquer une erreur pour voir les sécurités.
   Réglages › Recettes Lidl › Synchroniser télécharge les recettes sur n'importe quel appareil.
2. **Jumeau numérique** : le jumeau du [Monsieur Cuisine Developer SDK](https://github.com/antrea/monsieur-cuisine-developer-sdk)
   reproduit le protocole réel indépendamment de Toque.
   `python3 tools/twin.py --sdk <dossier du SDK>` puis, dans Toque, Réglages › Machine ›
   Jumeau (émulateur : hôte `10.0.2.2` ; appareil réel : `adb reverse tcp:8765 tcp:8765` et
   hôte `127.0.0.1`). `python3 tools/twin.py --send "lid 1"` ouvre le couvercle, etc.
3. **Recettes Cookomix sans robot** : en mode Démo, *Réglages › Recettes Cookomix › Synchroniser
   Cookomix* télécharge et convertit les recettes sur n'importe quel Android ; on peut ouvrir une
   recette et dérouler la cuisson guidée contre la base simulée (ou le jumeau).
4. **Tests automatiques** (sur le PC, sans Android) : `./gradlew :core:test`. Ils couvrent la
   conversion Thermomix → MCC (page d'essai inventée, fouet, turbo, demi-vitesses, pétrissage,
   Varoma), les pages illisibles et les coupe-circuits (codes CKX), et les traductions. Avec le
   jumeau lancé (point 2), deux tests de plus s'exécutent au lieu d'être ignorés :
   `TwinIntegrationTest` (protocole) et `CookomixTwinTest`, qui envoie chaque réglage converti
   d'une recette Cookomix au jumeau, vérifie qu'il arrive tel quel à la base, et que les réglages
   hors limites du MCC (vitesse en chauffe, sens inverse, fouet, Turbo avec le fouet ou à chaud)
   sont refusés avant de l'atteindre.

## Ajouter une langue

L'appli est écrite en français ; chaque autre langue est un fichier dans
[`app/src/main/assets/lang/`](app/src/main/assets/lang) (`en.json` pour l'anglais).

1. Copier `en.json` en `<code>.json` (par exemple `de.json`), mettre `"_name"` (par exemple `"Deutsch"`)
   et traduire les valeurs. Les clés sont les textes français ; garder les `%s` / `%d`.
   Les textes manquants restent en français.
2. Recompiler l'appli, ou, sans recompiler, copier le fichier dans `Android/data/app.toque/files/lang/`
   sur l'appareil puis relancer Toque.

La langue apparaît alors dans Réglages › Langue · Language. `./gradlew :core:test` vérifie chaque
fichier de langue.

## Publier une nouvelle version

1. Augmenter `versionCode` et `versionName` dans `app/build.gradle.kts`.
2. Committer et pousser sur `main`.

Le [workflow de publication](.github/workflows/release.yml) lance les tests, compile l'APK et publie la
version `vX.Y.Z` avec. Les robots la trouvent ensuite dans Réglages › Mises à jour.

Toutes les compilations sont signées avec la clé du projet (`app/signature/`, publique exprès) : un APK
compilé sur n'importe quel PC ou par GitHub s'installe par-dessus la version précédente. N'installez que
les APK publiés sur ce dépôt.

Sans GitHub Actions : `./gradlew assembleRelease`, puis
`bash tools/publish-release.sh --apk app/build/outputs/apk/release/app-release.apk [--repo propriétaire/nom]`
crée la version `vX.Y.Z` et y joint l'APK (il faut `gh` ; jeton GitHub : `GH_TOKEN`, `gh auth login`,
ou les identifiants déjà enregistrés pour git ; `--dry-run` montre seulement ce qu'il ferait).

## Installer sur le robot

**[docs/INSTALLATION.fr.md](docs/INSTALLATION.fr.md)** (voir [Installer](#installer)) : préparation
du Mac, root, installation avec Claude Code ou à la main, premier essai à vide, synchronisation des
recettes, retour à l'appli d'origine. Depuis Windows, suivre le guide du dépôt
d'origine.

## Crédits

- Projet d'origine : [Wuki60/MrCuisineVibeConnect](https://github.com/Wuki60/MrCuisineVibeConnect)
  (Toque) et sa version macOS [flexbox/MrCuisineVibeConnect](https://github.com/flexbox/MrCuisineVibeConnect),
  dont ce dépôt est un fork : toute l'application, le pilotage et l'installation viennent d'eux ;
  ce fork ajoute les recettes Cookomix.
- Recettes Cookomix : [cookomix.com](https://www.cookomix.com) et les membres qui y publient leurs
  recettes. Projet indépendant, non affilié à Cookomix. Les recettes restent leur propriété : ce
  dépôt n'en contient aucune, chaque robot les lit sur le site pour l'usage personnel de son
  propriétaire, avec l'auteur, la note et le lien vers la page d'origine dans chaque recette.
- Protocole : [Monsieur Cuisine Developer SDK](https://github.com/antrea/monsieur-cuisine-developer-sdk) (MIT).
- Accès au robot : [Monsieur-Cuisine-Connect-Hack](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack).
- Mode téléchargement MediaTek sous macOS : [mtkclient](https://github.com/bkerler/mtkclient) (GPLv3, téléchargé par `prepare`, non inclus ; `prepare` applique les corrections pour le pilote série de macOS de `tools/setup/mtkclient-macos-serial.patch`).

Licence MIT. Ce dépôt ne contient aucune recette, image ni code de Lidl ou de Cookomix : chaque
robot télécharge lui-même les recettes sur monsieur-cuisine.com et cookomix.com, pour l'usage
personnel de son propriétaire.
