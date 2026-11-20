# Installer Toque sur le Monsieur Cuisine Connect depuis un Mac

🇬🇧 **[English version](INSTALLATION.md)**

Ce guide part de zéro : pas besoin de savoir programmer. Il faut un Mac, un câble, un tournevis et
1 à 2 heures (surtout de l'attente). **Lisez-le une fois en entier avant de commencer.**

Il installe **Toque avec les recettes Cookomix**, depuis ce dépôt
([aguiran/MrCuisineConnectCookomix](https://github.com/aguiran/MrCuisineConnectCookomix)) : le script
télécharge Toque depuis ses [releases](https://github.com/aguiran/MrCuisineConnectCookomix/releases/latest),
et les mises à jour viennent ensuite de là.

Sur un PC Windows, ce guide ne s'applique pas : suivez celui du
[dépôt d'origine](https://github.com/Wuki60/MrCuisineVibeConnect/blob/main/docs/INSTALLATION.fr.md).
Il installe le Toque d'origine, **sans Cookomix**, qui se met à jour depuis le dépôt d'origine.
À la fin, installez par-dessus le Toque de ce dépôt (même signature, rien n'est perdu) :
téléchargez `Toque-X.Y.Z.apk` de la [dernière release](https://github.com/aguiran/MrCuisineConnectCookomix/releases/latest)
puis `adb install -r Toque-X.Y.Z.apk`. Les mises à jour suivantes viendront de ce dépôt.

## Sommaire

1. [Avant de commencer](#1-avant-de-commencer) : le robot, le matériel, les risques, les 3 règles
2. [Préparer le Mac](#2-préparer-le-mac-une-seule-fois) (une seule fois, 15 minutes)
3. [Installer avec Claude](#3a-installer-avec-claude-recommandé) (recommandé) **ou**
   [installer vous-même](#3b-installer-vous-même-étape-par-étape), étape par étape
4. [Après l'installation](#4-après-linstallation) : premier essai, recettes, Wi-Fi
5. [En cas de problème](#5-en-cas-de-problème) · [Revenir en arrière](#6-revenir-en-arrière) ·
   [Détails techniques](#7-détails-techniques)

## 1. Avant de commencer

### Votre robot

- **Monsieur Cuisine Connect**, référence **SKMC 1200** (elle est sur l'étiquette du robot).
- Le **Monsieur Cuisine Smart** (SKMS 1200) ou un autre modèle : **arrêtez-vous ici**, ce guide ne
  s'applique pas.

### Le matériel

| | À avoir | Remarque |
|---|---|---|
| ☐ | Un **Mac** (Apple silicon ou Intel), connecté à Internet | Le mot de passe de session du Mac vous sera demandé |
| ☐ | Un câble de données **USB-A mâle ↔ USB-A mâle** | Les deux bouts sont de grandes prises USB rectangulaires. Un câble USB-C ↔ USB-A **ne marche pas** |
| ☐ | Si le Mac n'a que des ports USB-C : un **adaptateur USB-C → USB-A** (femelle) ou un hub | Le câble A-A se branche dedans |
| ☐ | Un **petit tournevis Torx** | Pour la trappe de maintenance sous le robot |
| ☐ | Le **mot de passe du Wi-Fi** de la maison | Pour la fin |
| ☐ | Le robot **près du Mac**, branché sur le secteur, **bol vide** | Il reste branché au secteur du début à la fin |

### Les risques

- La garantie du robot saute.
- Une erreur peut rendre la tablette du robot inutilisable. Le script vérifie tout avant d'écrire
  quoi que ce soit, et la **sauvegarde** faite au début est votre voie de retour : ne la sautez
  jamais, gardez-en une copie.
- Toque tourne au quotidien sur deux robots : un installé depuis Windows (octobre 2026), un depuis
  un Mac (novembre 2026) (le Toque d'origine, sans Cookomix, installé par les projets d'origine).
  C'est peu : à vos risques.
- **La version avec Cookomix n'a pas encore tourné sur un robot** (seulement sur un téléphone
  Android en mode Démo). Elle ne change rien au pilotage du moteur ni aux règles de sécurité de
  Toque : les réglages des recettes Cookomix sont convertis pour le MCC, puis contrôlés par les
  mêmes sécurités que les recettes Lidl, et, par défaut, chacun est à confirmer avant d'être lancé
  (*Réglages › Sécurité › Faire confirmer les réglages des recettes*, à laisser activée). Vérifiez
  quand même chaque réglage les premières fois.

### Les 3 règles à retenir

Sous le robot, derrière la trappe, il y a deux ports USB :

<img src="https://raw.githubusercontent.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/master/informations/usbports.jpg" alt="Les deux ports USB sous la trappe : « Power PCB USB » à gauche, « Android USB » à droite" width="360">

1. **Toujours le port de droite, marqué « Android USB ».** Jamais celui marqué « Power PCB USB » :
   il peut détruire la carte du robot.
2. **Allumer le robot câble débranché**, puis brancher le câble une fois l'écran allumé. Allumé
   avec le câble branché, il reste bloqué sur un écran de batterie : l'éteindre, débrancher, rallumer,
   rebrancher. **Seule exception** : les étapes `backup` et `flash-twrp`, où il faut au contraire
   brancher le câble robot éteint, puis l'allumer quand le Mac le demande.
3. **Ne jamais éteindre le robot ni débrancher le câble pendant qu'une étape tourne.** Attendez la
   ligne `OK: ... done`.

L'interrupteur du robot est à l'arrière.

## 2. Préparer le Mac (une seule fois)

Tout se fait dans le **Terminal** : ouvrez-le avec `Cmd + Espace`, tapez `Terminal`, `Entrée`.
Pour chaque commande ci-dessous : copiez-la, collez-la dans le Terminal (`Cmd + V`), `Entrée`, et
attendez qu'elle se termine (le curseur revient).

1. **Installer Homebrew** (l'outil qui installe le reste) :
   ```
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```
   Il demande le mot de passe du Mac : tapez-le, **rien ne s'affiche pendant la frappe, c'est
   normal**, puis `Entrée`. Il installe aussi les outils d'Apple (dont git) : comptez 5 à 15 minutes.
   À la fin, s'il affiche **Next steps** avec des commandes à lancer, copiez-les et lancez-les,
   puis **fermez le Terminal et rouvrez-le**.
2. **Installer Python et libusb** :
   ```
   brew install python libusb
   ```
3. **Télécharger ce dépôt** dans le dossier `MrCuisineConnectCookomix` de votre dossier personnel :
   ```
   git clone https://github.com/aguiran/MrCuisineConnectCookomix.git ~/MrCuisineConnectCookomix
   ```
4. **Aller dans ce dossier** :
   ```
   cd ~/MrCuisineConnectCookomix
   ```
   Chaque fois que vous rouvrez le Terminal, retapez cette commande avant les autres.

Choisissez ensuite : **3a** (Claude fait les commandes à votre place) ou **3b** (vous les tapez).
Dans les deux cas, le robot ne change qu'aux étapes qui le disent.

## 3a. Installer avec Claude (recommandé)

[Claude Code](https://code.claude.com/docs/en/quickstart) est un assistant IA qui tape les
commandes dans le Terminal, lit les résultats et vous dit quoi faire de vos mains. Il faut un
abonnement Claude payant (Pro ou plus) : l'offre gratuite ne suffit pas.

1. Installez-le :
   ```
   curl -fsSL https://claude.ai/install.sh | bash
   ```
   puis fermez et rouvrez le Terminal. `claude --version` doit afficher un numéro de version.
2. Lancez-le dans le dossier du dépôt :
   ```
   cd ~/MrCuisineConnectCookomix
   claude
   ```
   La première fois, il ouvre le navigateur pour vous connecter à votre compte Claude.
3. Collez ce message :
   > Installe Toque sur mon Monsieur Cuisine Connect en suivant AGENTS.md. Parle-moi en français.

Ensuite :

- Claude vous explique les risques et vous demande un **oui** avant de commencer.
- Avant chaque commande, Claude Code vous demande la permission : lisez, puis acceptez.
- Avant chaque étape qui écrit sur le robot, il vous dit ce qu'elle fait et attend votre **oui**.
- Quand il faut agir (trappe, câble, interrupteur, un appui sur l'écran du robot), il vous le dit :
  faites-le, puis répondez-lui « c'est fait ».

Gardez ce guide ouvert à côté : la partie 3b vous montre ce qui vous attend à chaque étape.

## 3b. Installer vous-même, étape par étape

Un seul script fait tout : `tools/setup/toque-setup.sh`. Chaque étape se lance avec
`bash tools/setup/toque-setup.sh <étape>`, depuis le dossier du dépôt. Ce qu'il affiche :

- `OK:` c'est fait et vérifié ;
- `HUMAN:` une action pour vous (câble, interrupteur, écran du robot). Faites-la ; si l'étape s'est
  arrêtée, relancez la même commande ;
- `STOP:` problème, **rien d'irréversible n'a été fait**. La ligne dit quoi faire ; voir aussi
  [En cas de problème](#5-en-cas-de-problème).

Perdu ? Cette commande dit ce qui est fait et quelle est l'étape suivante :

```
bash tools/setup/toque-setup.sh status
```

Chaque étape peut être relancée sans risque.

### Étape 1 · Télécharger les outils — `prepare`

Le robot n'est pas concerné.

```
bash tools/setup/toque-setup.sh prepare
```

Il télécharge dans le dossier `~/Toque` : adb, Magisk, TWRP, le dernier Toque et mtkclient, et
vérifie chaque fichier. S'il affiche `MISSING:`, installez ce qu'il dit puis relancez-le.

✅ À la fin : `OK: prepare done. Next: backup`

### Étape 2 · Sauvegarder le robot — `backup`

Le robot est seulement lu, rien n'est écrit.

1. **Éteignez** le robot (interrupteur à l'arrière).
2. Retirez le bol et dévissez la **trappe de maintenance** sous le robot.
3. Branchez le câble A-A dans le port de droite **« Android USB »** et dans le Mac.
4. Lancez :
   ```
   bash tools/setup/toque-setup.sh backup
   ```
5. Quand le Terminal affiche *Waiting for PreLoader VCOM* (puis *Handshake failed, retrying...*
   toutes les 10 secondes, c'est normal), **allumez le robot**, une seule fois. L'écran du robot
   reste noir : c'est normal aussi.
6. Si le Mac demande d'**autoriser l'accessoire** à se connecter : cliquez sur **Autoriser**, puis
   éteignez et rallumez le robot pendant que l'étape attend.

✅ À la fin : `OK: backup done. Next: flash-twrp`

Le dossier `~/Toque/backup` contient maintenant vos deux sauvegardes. **Copiez-le sur une clé USB
ou dans le cloud** : c'est votre seule voie de retour. Puis **éteignez le robot** (écran noir :
il est resté en mode téléchargement).

### Étape 3 · Mettre TWRP sur le robot — `flash-twrp`

Écrit sur le robot : TWRP, un système de maintenance, remplace temporairement le démarrage normal.

1. Robot **éteint**, câble **branché**.
2. Lancez :
   ```
   bash tools/setup/toque-setup.sh flash-twrp
   ```
3. Comme pour la sauvegarde : allumez le robot quand le Terminal attend l'appareil.

✅ À la fin : `OK: flash-twrp done`

4. **Éteignez** le robot, **débranchez** le câble, **rallumez** : l'écran de **TWRP** apparaît.
   **Rebranchez** le câble.

### Étape 4 · Préparer le root — `root`

Robot sur l'écran de TWRP, câble branché.

```
bash tools/setup/toque-setup.sh root
```

Il prépare, à partir de votre sauvegarde, la version « rootée » du démarrage du robot (avec
[Magisk](https://github.com/topjohnwu/Magisk)), et active la connexion avec le Mac dans Android.

✅ À la fin : `OK: Magisk boot image ready ... Next: flash-root`

### Étape 5 · Écrire le root — `flash-root`

Écrit sur le robot. Toujours sur TWRP, câble branché.

```
bash tools/setup/toque-setup.sh flash-root
```

**Ne touchez pas au robot** jusqu'à la fin. Le script vérifie d'abord le contenu du robot (et
n'écrit rien s'il ne reconnaît pas ce qu'il trouve), écrit, puis relit pour vérifier.

✅ À la fin : `OK: flash-root done. Next: android`

Puis **éteignez** le robot, **débranchez** le câble, **rallumez** : l'appli Monsieur Cuisine
habituelle démarre. **Rebranchez** le câble.

### Étape 6 · Vérifier le root — `android`

```
bash tools/setup/toque-setup.sh android
```

- Si le robot demande d'**autoriser le débogage USB** : cochez *Toujours autoriser*, appuyez sur
  **OK**, relancez l'étape.
- Quand Magisk demande l'accès superutilisateur pour *Shell* : appuyez sur **Autoriser** sur
  l'écran du robot **dans les 10 secondes**. Raté ? Relancez l'étape.

✅ À la fin : `OK: Root works (uid 0). Next: install`

### Étape 7 · Installer Toque — `install`

```
bash tools/setup/toque-setup.sh install
```

✅ À la fin : `OK: Toque installed` et Toque s'ouvre sur le robot. Passez au premier essai
([partie 4](#4-après-linstallation)), puis revenez à l'étape 8.

### Étape 8 · Se passer du câble — `wifi`

Le robot doit être connecté au Wi-Fi de la maison. S'il ne l'est pas : dans Toque, touchez
**5 fois rapidement l'heure** en haut à droite (paramètres Android), rejoignez le Wi-Fi, revenez
avec le bouton **« Retour à Toque »**.

```
bash tools/setup/toque-setup.sh wifi
```

✅ À la fin : `OK: ADB over Wi-Fi: <adresse IP>:5555`. Débranchez le câble et **revissez la trappe**.
C'est fini.

## 4. Après l'installation

### Premier essai — bol VIDE

1. Dans Toque : **Réglages › Machine › Machine réelle › Appliquer**. Toque coupe le service moteur
   de l'appli d'origine (réversible) et ouvre le port série.
2. La pastille en haut à droite doit passer à **« Prête »**. Sinon : Réglages › Outils ›
   Diagnostic de la base moteur (trames reçues ?) et Journal.
3. Bol vide, couvercle fermé, **main près de l'interrupteur** :
   - Cuisson manuelle : 10 s, vitesse 2 → le moteur tourne puis s'arrête seul.
   - Ouvrir le couvercle pendant un essai → arrêt immédiat, « Pause : couvercle ouvert ».
   - 30 s à 37 °C, vitesse 1 → la température affichée monte.
   - Balance : poser un objet de poids connu, Tare.
   - Diagnostic de la base moteur : retirer le bol et regarder « Bol détecté ». S'il change bien, vous pouvez
     activer Réglages › Sécurité › « Exiger la détection du bol ».
4. Au moindre comportement anormal : **interrupteur**, puis revenez à l'appli d'origine
   ([partie 6](#6-revenir-en-arrière)).

### Les recettes Lidl

**Réglages › Recettes Lidl › Synchroniser** (robot en Wi-Fi). Toque télécharge les recettes
officielles sur monsieur-cuisine.com, sans compte : environ 15 minutes la première fois, ensuite
seulement les nouvelles, chaque nuit à 3 h 30. Elles sont dans la langue de l'interface.

Autre source : **« Importer de l'appli d'origine »** copie les recettes déjà présentes dans l'appli
d'origine (elles remplacent celles du site). Si elle n'en a pas encore, **« Ouvrir l'appli
d'origine »** la laisse se synchroniser avec les serveurs Lidl ; revenez avec le bouton **« Retour à Toque »**. Si cet import échoue, le fichier
`Android/data/app.toque/files/diagnostic-recettes.txt` décrit la base d'origine.

### Les recettes Cookomix

**Réglages › Recettes Cookomix › Synchroniser Cookomix** (robot en Wi-Fi). Toque lit les recettes
de cookomix.com, écrites pour le Thermomix, et garde celles que le MCC peut faire, avec leurs
réglages convertis : une heure ou plus la première fois (une page toutes les 1 à 2 secondes, photos
comprises), ensuite seulement les nouvelles ou modifiées. Les recettes apparaissent pendant la
synchro, avec leur photo. Si Toque s'arrête en cours (coupure, mise à jour), la synchro reprend
toute seule au démarrage suivant (3 fois de suite au plus). Les recettes portent le mot-clé « Cookomix » ; celles dont un
réglage a dû être adapté portent aussi « Adaptée MCC » et le disent dans leurs notes. Par défaut,
comme pour les recettes Lidl, chaque réglage est à confirmer avant d'être lancé (option
*Réglages › Sécurité › Faire confirmer les réglages des recettes*, à laisser activée).

Dans l'écran **Recettes**, les onglets **Tout / Lidl / Cookomix** séparent les deux sources (leurs
catégories ne sont pas les mêmes) et **« Catégories… »** ouvre la liste de toutes les catégories.

Options de la section : **Inclure les recettes adaptées** (activée par défaut ; désactivée, les
recettes adaptées sont retirées à la synchro suivante, sauf celles en favori) et **Synchro Cookomix
chaque nuit** (désactivée par défaut).

Aucun compte Cookomix n'est nécessaire : mettez les recettes qui vous plaisent dans les favoris de
Toque (étoile sur une recette).

Si la synchro affiche un code **CKX-01** à **CKX-10** (site en panne, format changé…), les recettes
déjà enregistrées restent disponibles : voir [COOKOMIX-ERREURS.md](COOKOMIX-ERREURS.md).

### Au quotidien

- Toque s'ouvre tout seul au démarrage du robot.
- Le robot n'a pas de bouton Accueil ni Retour : quand une autre appli est ouverte (l'appli
  d'origine, les paramètres Android), le bouton **« Retour à Toque »** reste affiché par-dessus
  (on peut le déplacer).
- Paramètres Android : 5 appuis rapides sur l'heure en haut à droite.
- Mises à jour : **Réglages › Mises à jour** installe les nouvelles versions de ce dépôt.
- Depuis le Mac, par Wi-Fi : `~/Toque/platform-tools/adb connect <adresse IP du robot>:5555`, puis
  `~/Toque/platform-tools/adb shell am start -n app.toque/.ui.MainActivity` ouvre Toque.

## 5. En cas de problème

| Ce que vous voyez | Pourquoi | Que faire |
|---|---|---|
| Le robot affiche une **batterie** au lieu de démarrer | Allumé avec le câble branché | Éteindre, débrancher, allumer, rebrancher une fois l'écran allumé |
| `backup` / `flash-twrp` attendent sans fin (*Waiting for device*) | Mauvais câble, mauvais port, port USB-C sans adaptateur, ou robot déjà allumé | Câble A-A, port « Android USB », adaptateur USB-C → USB-A. Robot éteint, relancer l'étape, allumer quand c'est demandé |
| *Handshake failed, retrying...* toutes les 10 s | Normal : le Mac attend le robot | Allumer le robot, une seule fois |
| *Sync error* ou *Failed to upload da* | Le robot était déjà en mode téléchargement, ou `prepare` pas fait | `prepare`, éteindre le robot, relancer l'étape |
| Écran du robot noir après `backup` ou `flash-twrp` | Il reste en mode téléchargement | L'éteindre |
| Le Mac demande d'**autoriser l'accessoire** | Sécurité des Mac Apple silicon | **Autoriser**, puis éteindre et rallumer le robot |
| `MISSING: Python` ou `MISSING: libusb` | Paquets manquants | `brew install python libusb`. `brew` introuvable : refaire les *Next steps* de Homebrew ([partie 2](#2-préparer-le-mac-une-seule-fois)) |
| Le Mac ne voit pas le robot dans TWRP | Câble branché avant que TWRP soit affiché | Débrancher, rebrancher |
| Écran de batterie entre `root` et `flash-root` | Le robot a redémarré câble branché | Éteindre, débrancher, rallumer : TWRP revient. Rebrancher, continuer |
| `flash-root` : *write failed* ou *read-back differs* | Erreur d'écriture | **Ne pas éteindre le robot** (TWRP tourne toujours). Relancer `flash-root` |
| `flash-root` : *holds something unexpected* | Contenu inattendu sur le robot | Rien n'a été écrit. Ne pas continuer : `status`, et demander de l'aide |
| `android` : l'appui sur **Autoriser** a été raté | La fenêtre de Magisk se ferme après 10 s | Relancer `android` et appuyer vite. Refusé pour de bon : appli Magisk › Superutilisateur › Shell › autoriser |
| Toque affiche « Base absente » | Busybox de Magisk manquante | Relancer `install` |
| Messages « Toque s'est vu refuser les droits », ou fenêtre Magisk pour Toque à chaque démarrage | Sur Android 6, Magisk 31.0 oublie les autorisations des applis à chaque démarrage | Relancer `install` : il met en place un script de démarrage qui rétablit l'autorisation de Toque |
| Bloqué dans l'appli d'origine | Normal, pas de bouton Retour sur ce robot | Bouton **« Retour à Toque »** |

Toujours bloqué ? Envoyez le fichier `~/Toque/setup-log.txt` (et `~/Toque/mtk-<étape>-out.txt`
pour `backup` ou `flash-twrp`) à qui vous aide, ou ouvrez une
[issue](https://github.com/aguiran/MrCuisineConnectCookomix/issues).

### Si le Mac ne joint jamais le robot en mode téléchargement

Rien n'est écrit tant que le Mac ne joint pas le robot. Dans l'ordre :

1. Juste après avoir allumé le robot, `system_profiler SPUSBDataType` doit afficher un
   **MT65xx Preloader** (identifiant fabricant `0x0e8d`). Sinon, c'est le câble, le port ou
   l'adaptateur.
2. Si le robot est vu mais que l'étape s'arrête sur *Sync error* : `status` doit afficher
   *mtkclient: v2.1.4.1 + macOS serial fixes* (sinon lancer `prepare`). Éteindre le robot et relancer.
   `--libusb` essaie une autre méthode de connexion (elle n'a jamais marché sur le robot essayé).
3. Sinon, faire **seulement** `backup` et `flash-twrp` sur un PC Windows avec le
   [guide d'origine](https://github.com/Wuki60/MrCuisineVibeConnect/blob/main/docs/INSTALLATION.fr.md)
   (SP Flash Tool), copier les deux sauvegardes dans `~/Toque/backup/` sur le Mac, lancer `backup`
   (il ne fait alors que les vérifier) et continuer avec `root` sur le Mac : tout le reste passe
   par adb.

## 6. Revenir en arrière

- **Revenir à l'appli d'origine** (en gardant le root) : dans Toque, Réglages › Outils ›
  « Revenir à l'appli d'origine », ou depuis le Mac `bash tools/restore-robot.sh --uninstall`.
  Toque ne modifie ni le système ni l'appli d'origine : il désactive seulement son service moteur,
  ce que ces deux méthodes annulent.
- **Retirer aussi le root et TWRP** (après la ligne ci-dessus) :
  `bash tools/setup/toque-setup.sh unroot` remet vos sauvegardes `boot-original.img` et
  `recovery-original.img` sur le robot, depuis TWRP. Si le robot ne démarre plus du tout :
  `unroot --download-mode` (robot éteint, câble branché, comme pour `backup`).

## 7. Détails techniques

Pour les curieux, ou pour tout faire sans le script. Les fichiers sont dans `~/Toque` (dossier
de travail, modifiable avec `--work`), le journal dans `~/Toque/setup-log.txt`.

**Ce qui est téléchargé** (et vérifié par SHA-256) : adb (Google), l'APK officiel de
[Magisk](https://github.com/topjohnwu/Magisk/releases) v31.0, la recovery TWRP `hacks/recovery.img`
et le fichier de partition `l706_dfbh_v_695scatter.txt` du
[guide de référence](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack), le dernier
APK de Toque, et [mtkclient](https://github.com/bkerler/mtkclient) v2.1.4.1. mtkclient remplace
SP Flash Tool et son pilote Windows pour les deux opérations qui demandent le mode téléchargement
MediaTek du robot (la sauvegarde et la première écriture de TWRP). Il a fallu trois corrections à
mtkclient pour le pilote série de macOS :
[tools/setup/mtkclient-macos-serial.patch](../tools/setup/mtkclient-macos-serial.patch), appliqué
par `prepare`.

**`backup`** lit `boot` (début `0x1d20000`, longueur `0x1000000`, région `EMMC_USER`) et
`recovery` (début `0x2d20000`, longueur `0x1000000`) dans `~/Toque/backup/boot-original.img` et
`recovery-original.img` (16 Mo chacun), et vérifie que ce sont des images de démarrage Android.
À la main, depuis `~/Toque/mtkclient/run` :

```
DYLD_FALLBACK_LIBRARY_PATH="$(brew --prefix)/lib" ../venv/bin/python ../src/mtk.py --serialport DETECT multi "ro 0x1d20000 0x1000000 boot-original.img --parttype user;ro 0x2d20000 0x1000000 recovery-original.img --parttype user"
```

(le chemin de libusb est nécessaire sur Apple silicon ; `--serialport` ne marche qu'avec les
corrections qu'applique `prepare`).

**`flash-twrp`** écrit TWRP (`hacks/recovery.img`) dans `boot` uniquement, et le relit pour vérifier.

**`root`**, depuis TWRP, revient à :

```
adb push ~/Toque/downloads/Magisk-v31.0.apk /tmp/magisk.apk
adb push ~/Toque/backup/boot-original.img /tmp/boot.img
adb push ~/.android/adbkey.pub /tmp/adbkey.pub
adb push tools/robot/twrp-root.sh /tmp/
adb shell sh /tmp/twrp-root.sh
adb pull /tmp/magisk/new-boot.img ~/Toque/boot-magisk.img
```

[`twrp-root.sh`](../tools/robot/twrp-root.sh) patche votre `boot` d'origine avec Magisk
(`boot-magisk.img`), active ADB dans Android (`/system/build.prop`), supprime
`/system/recovery-from-boot.p` (sinon Android remet le recovery d'origine par-dessus TWRP) et
autorise la clé ADB de votre Mac (aucune confirmation à faire sur l'écran du robot).

**`flash-root`** écrit TWRP dans `recovery` et `boot-magisk.img` dans `boot` depuis TWRP, avec
[`twrp-flash.sh`](../tools/robot/twrp-flash.sh) : il vérifie d'abord que `recovery` contient votre
recovery d'origine (ou TWRP) et `boot` contient TWRP (ou le boot Magisk), n'écrit rien sinon, puis
relit chaque partition.

**`android`** installe l'appli Magisk et vérifie le root : à la main,
`adb install ~/Toque/downloads/Magisk-v31.0.apk` puis `adb shell su -c id`, qui doit afficher
`uid=0`.

**`install`** lance [`tools/install-robot.sh`](../tools/install-robot.sh) (par Wi-Fi :
`--ip <adresse IP du robot>` ; un autre APK : `--apk <fichier>`). Il autorise aussi Toque dans
Magisk (aucune fenêtre sur le robot : celle de Magisk se ferme seule au bout de 10 s puis refuse
définitivement) et met en place la **busybox** de Magisk (`/data/adb/magisk/busybox`), dont Toque a
besoin pour le port série. Sur Android 6, Magisk 31.0 oublie les autorisations des applis à chaque
démarrage (il ne suit pas le lien `/data/user/0`) : `install` ajoute donc un script de démarrage,
`/data/adb/post-fs-data.d/toque-root-grant.sh`, qui rétablit l'autorisation de Toque et rien
d'autre. Son dernier résultat est dans `/data/adb/toque-root-grant.log`.

**`wifi`** revient à :

```
adb shell su -c "setprop persist.adb.tcp.port 5555"
adb reboot
```

À faire uniquement sur le réseau de la maison : n'importe quel appareil connecté dessus pourrait
alors accéder au robot.
