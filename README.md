# MrCuisineConnectCookomix

> **Fork with Cookomix recipes.** A copy of
> [flexbox/MrCuisineVibeConnect](https://github.com/flexbox/MrCuisineVibeConnect) whose Toque app
> adds the [cookomix.com](https://www.cookomix.com) recipes that the Monsieur Cuisine Connect can
> make, alongside the Lidl recipes. Toque updates from **this** repository's releases (same
> signature as the original, so it installs over it). Cookomix has no public API, so Toque reads
> the site's public pages from the robot (sitemaps + each page's structured step-by-step) and
> converts every Thermomix setting for the MCC; see [README.fr.md](README.fr.md) for the
> conversion table. If the site is down or changes its format, the sync stops with a documented
> error code ([docs/COOKOMIX-ERREURS.md](docs/COOKOMIX-ERREURS.md), CKX-01 to CKX-10) and never
> deletes saved recipes. Recipes stay the property of Cookomix and their authors: each robot downloads
> them for personal use and this repository does not redistribute them.

## Original project: MrCuisineVibeConnect

🇫🇷 **[Version française](README.fr.md)**

> **macOS version.** This is a copy of [Wuki60/MrCuisineVibeConnect](https://github.com/Wuki60/MrCuisineVibeConnect)
> whose install tools run on a **Mac**: shell scripts instead of PowerShell, and
> [mtkclient](https://github.com/bkerler/mtkclient) instead of SP Flash Tool and its Windows driver.
> The macOS install was done end to end on a real robot in November 2026; see [AGENTS.md](AGENTS.md).
> (Upstream text. In **this** fork, Toque adds the Cookomix recipes and updates from this
> repository's releases.)

**Toque** is a fast, modern replacement interface for the Lidl **Monsieur Cuisine Connect**
kitchen robot (SKMC 1200). It runs on the robot's own tablet instead of the stock app and still
uses **the official Lidl recipes**.

> Independent project, not affiliated with Lidl, SilverCrest or Hoyer. Motor control is based on
> a public analysis of the serial protocol; it has been running daily on a robot (SKMC 1200,
> Android 6) since October 2026 and on a second one since November 2026, but has not been tried on
> others. Use at your own risk and
> follow the first-run procedure.
>
> The interface is available in **English** and **French** (*Settings › Langue · Language*), and
> other languages can be added with a single file (see below).

## Install

**Start here: [docs/INSTALLATION.md](docs/INSTALLATION.md)**, a step-by-step guide that starts from
zero, for a Mac. On Windows, there is only the
[original repository's guide](https://github.com/Wuki60/MrCuisineVibeConnect/blob/main/docs/INSTALLATION.md):
it installs the original Toque, **without Cookomix**, which updates from the original repository.
Once it is done, install this repository's Toque over it (same signature, nothing is lost):
download `Toque-X.Y.Z.apk` from the [latest release](https://github.com/aguiran/MrCuisineConnectCookomix/releases/latest)
and run `adb install -r Toque-X.Y.Z.apk`; later updates then come from here.

In short: you need a Mac, a **USB-A male ↔ USB-A male** cable, a small Torx screwdriver and 1 to 2
hours. You prepare the Mac (Homebrew, then a copy of this repository), then either let
[Claude Code](https://code.claude.com/docs/en/quickstart) do the install by following
[AGENTS.md](AGENTS.md), or run [`tools/setup/toque-setup.sh`](tools/setup/toque-setup.sh) yourself,
one step at a time. It backs up the robot first, roots it with Magisk, installs Toque and sets up
ADB over Wi-Fi. You only handle the cover, the cable, the power switch and a tap on the robot's
screen.

## Features

- **Dark interface** designed for the robot's 7" screen and rotary knob, and smooth on its
  original tablet (1 GB RAM, Android 6): no heavy libraries, downsampled images, recycled views.
- **Cookomix recipes** (this fork): the cookomix.com recipes the MCC can make, with each Thermomix
  setting converted for the MCC and checked by the same safety rules; see the top of this page.
- **Lidl recipes, straight from monsieur-cuisine.com.** Toque downloads the official Monsieur
  Cuisine connect recipes (about 1,700 in French, with each step's machine settings) from the
  site's public API, nightly or on demand: no account, no server to run, no need for the stock app.
  Requests are spaced out and only new or changed recipes are downloaded again. The recipes already
  in the stock app can still be imported instead.
- **Author, ratings and reviews**: each recipe shows who published it and its rating on the site,
  and the recipe page loads the users' comments (with Monsieur Cuisine's replies).
- **Search that works**: ignores accents, plurals and typos ("bechamle", "risoto").
  Search by ingredients (e.g. "chicken, curry"), filters, favorites.
- **Guided cooking**: each step starts with one tap, the next step shows up by itself, built-in
  weighing, resume after a power cut. Settings that follow each other with nothing to do in
  between (rinsing, blending at several speeds…) start together with a single tap: the blade goes
  straight from one speed and temperature to the next without stopping.
- **Manual cooking**, **Knead / Steam / Sauté** programs, **Turbo**, **scale**.
- **Offline**: everything works without a network (no more "server unreachable" at startup).
- **Always the right time**: the time zone comes from the Internet connection and the time from
  the network (the robot has no clock battery); can be set by hand in *Settings › Date and time*.
- **Made for a robot without Android buttons**: Toque opens at startup, and a **Back to Toque**
  button stays on top of the stock app when you open it.
- **Updates from GitHub**: *Settings › Updates* checks this repository's
  [Releases](https://github.com/aguiran/MrCuisineConnectCookomix/releases) and installs the new version
  (silently on a rooted robot, then Toque restarts). Refused while a program is running.

## Safety: what changes compared to the stock app

All original limits are kept (speed 3 max when heating and in reverse, maximum durations,
Turbo conditions…). Several flaws found while analysing the protocol are fixed:

| Stock app flaw | Toque |
|---|---|
| Start was requested before checking the lid | Lid, errors and data freshness are checked **before** any start |
| No detection of a silent motor board | No reply for 1.5 s: everything stops and an alert is shown |
| Serial write errors ignored | Stop and alert |
| A STOP followed by a START could never send the stop | Any restart or direction change waits for a sample confirming **motor stopped** |
| Serial reading without resynchronisation | Fragmented, merged or corrupted frames are handled |
| The blade sometimes turned in steam mode | Steam at speed 0 |

Also added: confirmation (and a gradual ramp-up) before blending contents at 60 °C or more, a
speed limit with the whisk, alerts that stay latched until acknowledged, nothing restarts on its
own after a pause or an alert. When cold, the blade goes straight to the requested speed. Motor control runs in **its own process**: if the interface crashes, a cooking run is not
cut off and finishes normally.

## Project layout

```
core/   Pure Kotlin logic, tested without Android
        protocol/  serial frames (15 bytes sent, 27 received, 4800 baud)
        machine/   safety rules, controller, 100 ms loop, links (root serial, TCP, demo)
        recipes/   model, stock database import, search
app/    Android app (minSdk 23), no AndroidX
        machine/   motor control service (":machine" process) and IPC
        recipes/   local catalogue, Lidl sync
        ui/        screens
tools/  setup/toque-setup.sh (full install from a Mac, can be driven by an AI agent), robot/
        (scripts run on the robot: root, flash from TWRP, restore), install, restore and release
        scripts (.sh for macOS, the original .ps1 for Windows), twin.py (digital twin for testing)
docs/   INSTALLATION.md, INSTALLATION.fr.md
AGENTS.md  install procedure for an AI agent
```

## Build

Open the folder in **Android Studio**, wait for the Gradle sync, then
**Build › Build App Bundle(s) / APK(s) › Build APK(s)**. The APK is in
`app/build/outputs/apk/debug/`.

From the command line: `./gradlew assembleDebug` (and `./gradlew :core:test` for the tests). Gradle
needs JDK 17 or 21; with a newer default JDK the build stops with just a version number as error.
Android Studio's JDK works:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## Try it without the robot

1. **Demo mode** (default): install the APK on any Android device (or the Android Studio
   emulator, in landscape). The motor board is simulated; *Settings › Tools › Motor base
   diagnostics* lets you open the lid or trigger an error to see the safety
   features. *Settings › Lidl recipes › Sync* downloads the recipes on any device too.
2. **Digital twin**: the twin from the [Monsieur Cuisine Developer SDK](https://github.com/antrea/monsieur-cuisine-developer-sdk)
   reproduces the real protocol independently of Toque.
   Run `python3 tools/twin.py --sdk <SDK folder>`, then in Toque choose *Settings › Machine ›
   Twin (PC)* (emulator: host `10.0.2.2`; real device: `adb reverse tcp:8765 tcp:8765` and host
   `127.0.0.1`). `python3 tools/twin.py --send "lid 1"` opens the lid, etc.
3. **Cookomix recipes without the robot**: in Demo mode, *Settings › Cookomix recipes › Sync
   Cookomix* downloads and converts the recipes on any Android device; open a recipe and run the
   guided cooking against the simulated base (or the twin).
4. **Automated tests** (on the PC, no Android): `./gradlew :core:test`. They cover the Thermomix →
   MCC conversion (made-up test page, whisk, turbo, half speeds, kneading, Varoma), unreadable pages
   and circuit breakers (CKX codes), and translations. With the twin running (point 2), two more
   tests run instead of being skipped: `TwinIntegrationTest` (protocol) and `CookomixTwinTest`,
   which sends every converted setting of a Cookomix recipe to the twin, checks it reaches the
   base unchanged, and that settings beyond the MCC's limits (heated speed, reverse, whisk, Turbo
   with the whisk or when hot) are refused before reaching it.

## Add a language

The app is written in French; each other language is one file in
[`app/src/main/assets/lang/`](app/src/main/assets/lang) (`en.json` for English).

1. Copy `en.json` to `<code>.json` (for example `de.json`), set `"_name"` (for example `"Deutsch"`)
   and translate the values. Keys are the French texts; keep the `%s` / `%d` placeholders.
   Missing texts stay in French.
2. Rebuild the app, or, without rebuilding, copy the file to `Android/data/app.toque/files/lang/`
   on the device and restart Toque.

The language then appears in *Settings › Langue · Language*. `./gradlew :core:test` checks
every language file.

## Publish a new version

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit and push to `main`.

The [release workflow](.github/workflows/release.yml) runs the tests, builds the APK and publishes
the `vX.Y.Z` release with it. Robots then find it in *Settings › Updates*.

All builds are signed with the project key in `app/signature/` (public on purpose), so an APK built
on any PC or by GitHub installs over the previous version. Only install APKs from this repository's
releases.

Without GitHub Actions: `./gradlew assembleRelease`, then
`bash tools/publish-release.sh --apk app/build/outputs/apk/release/app-release.apk [--repo owner/name]`
creates the `vX.Y.Z` release and attaches the APK (needs `gh`; GitHub token: `GH_TOKEN`, `gh auth login`,
or the credentials already stored for git; `--dry-run` only shows what it would do).

## Install on the robot

**[docs/INSTALLATION.md](docs/INSTALLATION.md)** (see [Install](#install)): preparing the Mac, root,
install with Claude Code or by hand, first run with an empty bowl, recipe sync, going back to the
stock app. From Windows, use the original repository's guide.

## Credits

- Original project: [Wuki60/MrCuisineVibeConnect](https://github.com/Wuki60/MrCuisineVibeConnect)
  (Toque) and its macOS version [flexbox/MrCuisineVibeConnect](https://github.com/flexbox/MrCuisineVibeConnect),
  which this repository forks: the whole app, motor control and install come from them; this fork
  adds the Cookomix recipes.
- Cookomix recipes: [cookomix.com](https://www.cookomix.com) and the members who publish their
  recipes there. Independent project, not affiliated with Cookomix. Recipes remain their
  property: this repository contains none, each robot reads them from the site for its owner's
  personal use, with the author, rating and a link to the original page in every recipe.
- Protocol: [Monsieur Cuisine Developer SDK](https://github.com/antrea/monsieur-cuisine-developer-sdk) (MIT).
- Robot access: [Monsieur-Cuisine-Connect-Hack](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack).
- MediaTek download mode on macOS: [mtkclient](https://github.com/bkerler/mtkclient) (GPLv3, downloaded by `prepare`, not included; `prepare` applies the macOS serial fixes in `tools/setup/mtkclient-macos-serial.patch`).

MIT License. This repository contains no Lidl or Cookomix recipes, images or code: each robot
downloads the recipes itself from monsieur-cuisine.com and cookomix.com, for its owner's personal
use.
