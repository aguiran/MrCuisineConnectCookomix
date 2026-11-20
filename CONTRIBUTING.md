# Contribuer : le workflow git du dépôt

`main` est toujours stable et publiable : c'est elle que les robots reçoivent (*Réglages › Mises à
jour*) et que le script d'installation télécharge. On ne pousse jamais directement dessus.

## Un changement

1. Partir de `main` à jour et créer une branche :
   ```
   git switch main && git pull
   git switch -c fix/molette-temperature
   ```
   Préfixes : `feat/` (fonction), `fix/` (correction), `docs/` (documentation), `chore/` (outils,
   CI, version).
2. Faire des commits clairs, en français, sur cette branche. Avant de pousser :
   ```
   ./gradlew :core:test :app:assembleDebug
   ```
   (JDK 17 ou 21.) Si le changement touche les scripts d'installation, lancer aussi
   `bash tools/setup/tests/root-permissions.sh` et `bash tools/setup/tests/root-boot-grant.sh` (la
   CI les lance dans tous les cas). Si le changement touche le pilotage ou la conversion Cookomix, lancer aussi
   `python3 tools/twin.py --sdk <dossier du SDK>` dans un autre terminal : `TwinIntegrationTest` et
   `CookomixTwinTest` tournent alors au lieu d'être ignorés.
3. Pousser la branche et ouvrir une pull request **vers `main` de ce dépôt** (et non vers le dépôt
   d'origine) :
   ```
   git push -u origin fix/molette-temperature
   gh pr create --repo aguiran/MrCuisineConnectCookomix --base main
   ```
4. La CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) lance les tests et compile l'appli.
   La PR ne se fusionne que si elle est verte. Fusion en **squash** (un commit par changement) ; la
   branche est supprimée automatiquement.

## Publier une version

Dans la PR (ou une PR `chore/version-X.Y.Z` dédiée) : augmenter `versionCode` et `versionName` dans
[`app/build.gradle.kts`](app/build.gradle.kts). À la fusion dans `main`, le workflow
[`release.yml`](.github/workflows/release.yml) lance les tests, compile l'APK signé et publie la
version `vX.Y.Z` avec l'APK ; les robots la trouvent dans *Réglages › Mises à jour*. Sans changement
de version, une fusion ne publie rien.

Tant qu'une version n'a pas tourné sur un vrai robot, ses notes de version le disent.

## Récupérer les nouveautés du projet d'origine

Le dépôt d'origine est le remote `upstream` (`flexbox/MrCuisineVibeConnect`) :
```
git fetch upstream
git switch -c chore/maj-upstream origin/main
git merge upstream/main
```
Résoudre les conflits (attention aux fichiers propres à ce fork : `Releases.kt`,
`tools/setup/toque-setup.*`, `tools/publish-release.*` pointent vers ce dépôt), tester, puis PR
comme d'habitude.

## Urgence

Le propriétaire du dépôt peut contourner la protection de `main` en cas d'urgence (par exemple si
la CI de GitHub est en panne). À éviter : tout passe par une PR.
