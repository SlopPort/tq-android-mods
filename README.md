# Titan Quest Mods for Android

**A mod loader for Titan Quest on Android (Magisk): add a PC mod from a file, tap Play, switch back to the original game with one tap.** Each mod gets its own copy of your characters, so your normal saves are not touched.

It is experimental and unofficial. It works on one game build (3.0.5364, 64-bit phones).

**[Download Titan Quest Mods 1.7](https://github.com/SlopPort/tq-android-mods/releases/latest)**: on the Releases page, scroll to **Assets** and download both files: the app (`titan-quest-mods-1.7.apk`) and the Magisk module (`tq-mods-magisk-1.5.zip`).

> **Requirement: a rooted phone (Magisk) and your own copy of the game.**
> The Android game cannot load mods by itself: its PC mod-loading code is switched off, and it reads no game content from folders you can reach without root ([evidence](reports/2026-10-31-data-only-mods.md)). Mods therefore need a small loader running inside the game. The only ways to put it there without root are to rebuild the game APK or wrap it in a container app, and both mean defeating the publisher's license protection. **We will not do that.** With Magisk, the loader is put into your game while it runs, so your purchased copy, its files and its signature stay as they are.
>
> We are sorry that this leaves out phones without root. A loader for everyone would need the developers to add mod support to the Android version.

### Install (once)

1. Install Titan Quest as usual.
2. Open **Magisk** → **Modules** → **Install from storage** → choose `tq-mods-magisk-1.5.zip`. When it finishes, tap **Reboot**.
3. Open your Downloads and tap `titan-quest-mods-1.7.apk`. If Android says installing from this source is blocked, tap **Settings**, switch on **Allow from this source**, go back and tap **Install**. If Play Protect warns about an unknown app, choose to install anyway.
4. Open **Titan Quest Mods** and tap **Allow root access**. A Magisk window (*Superuser Request*) appears with a countdown: tap **Grant** before it runs out. If you missed it, open Magisk → **Superuser**, switch on Titan Quest Mods and tap the button again.
5. Make a backup of your characters: **Settings and backups** → **Save a backup of my characters**.

**Perf and Traders are optional and not included.** This download contains only the mod loader. [TQ Android Perf](https://github.com/SlopPort/tq-android-perf) (graphics and speed) and [TQ Traders](https://github.com/SlopPort/tq-traders) (traders and respec) are separate Magisk modules that you install only if you want them. If you already use them, update them to Perf 2.4 and Traders 1.4. The game closes itself when two programs are put into it, so with Titan Quest Mods installed those modules do nothing on their own, and Titan Quest Mods starts them for you.

### Play

1. Download a mod for the PC game (a ZIP, 7z or RAR file) from its author's page.
2. Open **Titan Quest Mods**, tap **Add a mod from a file** and pick the file from Downloads. The new mod is chosen automatically.
3. Tap the green **Play: …** button. The first time, the app makes a separate copy of your characters for this mod.
4. Wait a few seconds in the game's main menu, then open the character list as usual.
5. To go back, tap **Original game (no mods)** and the green button. If the game is running, the app reminds you to save and restarts it for you.

**More enemies** (app 1.7 with module 1.5): in **Settings and backups**, tap **More enemies** to choose off, x1.5, x2 or x3. Every monster group then spawns that many times more members; it works with any mod and with the original game, and takes effect at the next game start. It changes no game files: the loader scales the game's own two spawn settings while the game runs. Measured: x2 doubled every group (for example 3–4 monsters became 7–9) ([report](reports/2026-11-01-more-enemies.md)).

After changing your choice, always start the game with the app's green button: the game's own icon starts whatever you played last. The app is in Polish on Polish phones and in English everywhere else. Step-by-step guide with all messages: [USAGE.md](USAGE.md).

### Which mods work?

| Kind of mod | Status |
| --- | --- |
| Items, skills, masteries, balance, loot (normal game world) | **Loaded automatically.** Tested with this release: Masteries 1.03. Tested earlier with the development build: LootPlus 1.3, Halloween 2012 |
| Custom maps with their own world | **Gladiators only (experimental).** PC maps carry no navigation data for the mobile engine; since module 1.2 the loader has the game build it for each area you enter (about half a second), so the hero can walk ([report](reports/2026-10-31-map-navigation.md)). Other maps: not yet |
| Several mods at once | **2 to 8 content mods together** (app 1.4 with module 1.3). The app shows which game records two mods both change; the mod ticked first wins. Tested: Masteries 1.03 + Halloween 2012 ([report](reports/2026-10-31-combinations.md)). Custom maps cannot be combined |
| 7z downloads | **Read directly** (LZMA, LZMA2, Deflate, uncompressed); password-protected 7z files are not |
| RAR downloads | Repack as ZIP on a computer; the app says so when you pick one |

"Loaded" means the mod's files were opened by the game and its data was found. It does not mean every part of a mod was played through. Evidence: [generic content mods](reports/2026-10-31-generic-content-mods.md), [LootPlus profile](reports/2026-10-28-lootplus-profile-background.md), [map roundtrip](reports/2026-10-28-integrated-profile-loader.md).

### Status

App 1.7 with module 1.5 adds the **More enemies** option. App 1.6 (module 1.4) reads RAR mods, lets you rename mods and add your own description, and lists files two mods in a set both ship. App 1.5 with module 1.4 can delete a mod's copy of your characters to start that mod over. App 1.4 with module 1.3 plays several mods together, each set with its own copy of your characters ([report](reports/2026-10-31-combinations.md)). Module 1.2 makes the Gladiators map walkable. App 1.3 shows a message over the game while a mod loads, because the game stands still for a few seconds then ([report](reports/2026-10-31-loading-message.md)); it needs module 1.1 for the exact moment and works with module 1.0. 1.2 keeps a removed mod's characters when the same mod is added again and shows how much space each mod uses. 1.1 reads 7z mods directly and recognises a mod that is already in the list ([7z report](reports/2026-10-31-7z-import.md)); 1.0.1 fixed restoring characters from a backup ([test report](reports/2026-10-31-magisk-route.md)). It was tested on one rooted Redmi Note 8 (Android 16) with game build 3.0.5364. Other phones and copies of the game were not tested; [problem reports are welcome](https://github.com/SlopPort/tq-android-mods/issues). This repository never distributes game APKs, game files or mods.

## Known issues

- Masteries from mods can be chosen, but most of their skill icons show as blank frames; the mod's own icon files are not requested by the mobile skill panel ([report](reports/2026-10-31-masteries-coverage.md)).
- With Perf enabled, labels in the in-game pause menu can be tiny ([investigation](reports/2026-10-29-pause-menu-investigation.md)).
- A few game values that the game reads before the character list may keep their normal values.
- Custom-world navigation: see the [single-level experiment](reports/2026-10-30-single-level-navigation.md).

## Developers

### How it works

- **App** ([android/src](android/src/org/tqmods)): imports ZIP/ARZ files through Android's file picker into its own storage, checks paths and sizes, records the choice and backs up or restores characters. It talks to the module through one root shell ([RootLink](android/src/org/tqmods/RootLink.java)).
- **Magisk module** ([magisk/module](magisk/module)): `tqmods.sh` copies the chosen mod into the game's private folder, makes the mod's copy of the characters once and starts the game. `service.sh` injects the loader with `frida-inject` into every game start about half a second after the process appears, together with Perf or Traders when they are installed. The game app itself is never changed.
- **Loader** ([profile-loader.js](android/bootstrap/profile-loader.js)): checks the fingerprints of every engine function it uses, redirects the save folder to the mod's copy, keeps cloud sync off for mod saves, and activates the mod at character selection: [generic-mod-runtime.js](android/bootstrap/generic-mod-runtime.js) mounts ARCs, overlays the ARZ and registers mod text.

If anything does not match, the game is closed before a save can be written and the app explains what happened in one sentence. The engine reads the save folder only when the character list opens, many seconds after the injection; if the list was somehow read before the loader was ready, the loader refuses instead of mixing characters.

An older development route embeds the same loader as a Frida Gadget in a locally rebuilt APK ([GameBootstrapActivity](android/bootstrap/GameBootstrapActivity.java)); it is not distributed.

### Build and test

Build the module with `python magisk/build.py` and the app with `python tools/build-import-test.py --manager --jdk ... --sdk ... --out ...` (sign it with your own key). For RAR import, first build the native reader with `python tools/build-rar-native.py --ndk <NDK> --libarchive <unpacked libarchive-3.7.7>`; the app build then packs `libtqrar.so`, and without it RAR files are refused with a message. Run the host tests with Node.js and a JDK:

```text
for f in android/tests/*.js; do node "$f"; done
python tools/check-publication.py
```

Build steps: [android/BUILD-TEST-APP.md](android/BUILD-TEST-APP.md). Research tools: [tools/README.md](tools/README.md). Open work: [TODO.md](TODO.md).

Third-party components: [THIRD-PARTY.md](THIRD-PARTY.md). Unofficial and not endorsed by the publisher. Supply your own lawfully obtained game and get mods from their authors. Purchase-check removal and DLC unlocking are outside this project's scope. See [publication boundaries](PUBLICATION.md).
