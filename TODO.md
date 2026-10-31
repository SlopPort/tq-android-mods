# Remaining work

Updated: 2026-10-31. Goal: a mod loader that a non-technical player can use: add a mod file, tap Play, switch back with one tap.

## Roadmap

1. ~~**RAR import**~~ Done 2026-10-31: libarchive 3.7.7 RAR4/RAR5 readers (BSD-2-Clause, no unRAR code) in `libtqrar.so` for arm64-v8a, built by `tools/build-rar-native.py`; the app rewrites the RAR as ZIP like a 7z. Checked on the phone with RAR5 packs of Halloween 2012 and Gladiators (both decoded to the same files as the installed copies) and a password-protected RAR (refused with a clear message). Not yet checked: RAR4 (current WinRAR cannot create it) and multi-part RARs (not supported).
2. **Content-mod coverage.** New-character mastery selection with Masteries works; its skill icons are blank because the mobile skill panel never requests mod icon files ([report](reports/2026-10-31-masteries-coverage.md)): find where skill icons are resolved. Still to check: LootPlus drops after the generic path, Halloween gameplay, and records the engine caches before character selection. (2026-11-01: cache refresh does not help; see reports/2026-11-01-masteries-icons.md)
3. **Custom worlds.** Gladiators is walkable since module 1.2 (navigation built per area, [report](reports/2026-10-31-map-navigation.md)). Next: generalize the Gladiators adapter (map path, sentinel records, text tags) to other maps; this needs more map files from their authors to test with. Check enemies and quests inside a map once a map with them is available. Assessed 2026-10-31, what is Gladiators-specific in `android/bootstrap/gladiators-map-runtime.js` and `gladiators-runtime-5364.json`: ARC and database size/FNV pins, the map path `maps/gladiators.map` with its size and hash, sentinel records checked after loading, the probe tag `tagCave_01`, and the save-identity fix that swaps `Levels/World/World01.map` for the mod's map. A generic adapter can take the map path from the `.map` entries in the mod's `Maps.arc`, read tags from its `Text/modstrings.txt`, and skip the pins for unknown maps (shown as "experimental" in the app, as content mods are). The blocker is test material: no second custom map exists among the local specimens (only Gladiators ships a `.map`), so generalized code could not be checked on the phone.
4. **Several mods at once:** done for content mods (1.4); the set dialog also lists files two mods both ship inside same-named archives (first-ticked mod's file is used). Still open: combinations that include a custom map.
5. **Perf pause-menu labels:** not reproduced with the Magisk route (2026-10-31: tq_mods + LootPlus, overlay button visible, all pause-menu labels readable). The [2026-10-29 report](reports/2026-10-29-pause-menu-investigation.md) covered the old repacked-APK setup.
6. ~~Rename/describe mods in the list.~~ Done: "Zmień nazwę i opis" on each card (name up to 60, description up to 300 characters, atomic write); tested on the phone.

Done on 2026-10-31 (1.5, module 1.4): delete a mod's or set's character copy from its card (module `deleteprofile`, refused while the game runs; `status` lists existing copies). Checked on the phone: only the chosen copy deleted, a fresh copy made on the next start, originals unchanged.

Done on 2026-10-31 (1.4, module 1.3): 2 to 8 content mods played together; the first-ticked mod wins conflicts, the app counts shared records before creating a set, each set has its own character copy. Checked on the phone with Masteries + Halloween 2012.

Done on 2026-10-31 (module 1.2): Gladiators navigation is built automatically when a character enters an area; walking, save, restart and reload checked on the phone.

Done on 2026-10-31 (1.3, module 1.1): a message over the game while a mod loads and when it is ready (notification and toast from the app; the loader reports an `activating` state).

Done on 2026-10-31 (1.2): a removed mod added again gets its characters back; size shown per mod.

Done on 2026-10-31 (1.1): 7z import on the phone, duplicate-import detection, list keeps its place after actions ([report](reports/2026-10-31-7z-import.md)). 1.0.1: restore fix.

Done on 2026-10-31: Magisk module and separate app, release 1.0 (reports/2026-10-31-magisk-route.md); generic content mods without an allowlist, any character in a mod's copy, activation on a fast LoadGame, automatic first-start save copy, one-button restart, simplified bilingual manager, skipping loose PC extras on import, mod removal. See reports/2026-10-31-generic-content-mods.md.

# Still open from the 2026-10-27 checklist

The old checklist was written for the repacked-APK and rootless-Gadget routes, which the Magisk route (1.0) replaced; its full text is in the git history. Reviewed 2026-10-31; what still applies:

- **XMAX variants:** x2 tested 2026-10-31: no effect on the phone (90 monsters with and without it), because the game reads its game-engine record at startup, before mods are activated ([report](reports/2026-10-31-xmax-x2.md)). The other variants change the same record, so they need early activation first. (2026-11-01: own runtime override works, effect still unmeasured; see reports/2026-11-01-more-enemies.md)
- **Early activation:** investigated 2026-11-01 ([report](reports/2026-11-01-early-activation.md)). Activating before startup is not possible (the loader is injected after the engine reads its records). A targeted refresh after activation works without a crash: hide only the cached tables of records the mod overrides, then re-read (`GameEngine::LoadFromDatabase` for the engine record; `GameEngine::Reload` crashes). Next: (a) a field merge for PC records that lack mobile fields, needed before XMAX can be applied (x2 as published zeroes the inventory bag sizes); (b) try the targeted refresh for Masteries skill tables to fix the icons, checking saved characters keep their masteries. (2026-11-01: cache refresh does not help; see reports/2026-11-01-masteries-icons.md) (2026-11-01: own runtime override works, effect still unmeasured; see reports/2026-11-01-more-enemies.md)
- **Masteries + LootPlus field-resolution proposal:** still a plan, not compiled or tested.
- **Audio cache:** playback and combat regression test, deferred by the user.
- **Robustness on the phone:**
  - Done 2026-10-31 (app 1.5 + RAR build): damaged ZIP, 7z and RAR files are refused with a message, including a corrupted RAR whose damaged file was stored uncompressed (this needed a local fix in libarchive's RAR5 reader, see `tools/build-rar-native.py`).
  - Done 2026-10-31: the app killed while the file picker is open; the import finishes after Android restarts it. This found and fixed a race where the new choice was lost (a read of `request.json` during its write).
  - Open: low storage during import (needs a phone that can be filled safely), and screen rotation during an import (the test phone keeps the app in landscape).
- **Shared files outside variant folders:** the importer still leaves out loose files next to the variant folders; decide whether any mod needs them.
- **Publication:**
  - Choose a licence for the original code after a dependency review (Frida: wxWindows 3.1; libarchive: BSD-2-Clause).
  - Enable the GitHub Actions workflow (needs a token with workflow scope; only a template is published).
  - Keep the text-audit, publication-guard and history checks before every push.
