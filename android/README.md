# Manager app source

The classes in `src/org/tqmods` build two ways: as the released **Titan Quest Mods** app (`tools/build-import-test.py --manager`, package `org.tqmods.manager`), which reaches the game through the `tq_mods` Magisk module, and embedded in a locally rebuilt game APK, the older development route. Everything below this paragraph documents that embedded route and its history; it is not how players install the project.



Status (2026-10-31): the embedded manager imports a mod, creates its save copy on first start and loads unknown content mods (ARZ + ARC + mod text) on the stock world; see ../reports/2026-10-31-generic-content-mods.md. The same manager also builds as the separate app for the Magisk route (../reports/2026-10-31-magisk-route.md). Custom worlds other than the Gladiators adapter and several mods at once are not done. Sections below are kept as the development history.


`ModManagerActivity` is intended to be embedded in a locally patched, user-supplied game APK. It opens Android's document picker and copies selected ZIP/ARZ content into that application's private `files/tqmods/library` directory. It is designed to operate without root, ADB, broad storage permission, a companion file manager, or a network listener at runtime. A separate app would have a different UID and cannot provide the missing engine integration.



The current build registers a non-exported manager activity and an exported launcher alias in a separate task. An in-game entry point remains future work. Standalone XMAX x2 import and persistence passed device tests, but XMAX activation is unsupported. Embedded LootPlus activation has separate evidence. Do not expose game-private files through an unrestricted provider. No game APK, signing key, or proprietary binary is distributed.



See the [manager workflow report](../reports/2026-10-27-manager-workflow.md) for the tested artifact and process-level evidence. Source capability does not imply device verification.



## Implemented



- Document picker, background import, readable imported names and explicit unverified status.



- UUID staging and commit on the same filesystem; failed imports are removed.



- ZIP path containment, path depth, entry count, expanded size, case-insensitive duplicate and extension checks.



- One ARZ per imported variant; bundles with separate database folders show a variant picker. ARC, MAP and text companion files allowed. Recognizes the 04 00 03 00 ARZ prefix from the actual LootPlus specimen. This is not full archive or engine validation.



- 512 MiB expanded limit and 4,096 entries. These intentionally exclude some large maps; limits are prototype constraints.



## Required next steps



1. Extend the verified local signing, launch and bootstrap experiment to a supported store specimen and stock-phone setup. Re-signing changes app identity for updates and may affect licensing or asset delivery. Preserve save/APK backups and do not bypass entitlement checks.



2. Extend the tested script-mode Frida Gadget bootstrap, keeping its no-listener design. Startup must return before waiting for engine initialization: blocking Gadget init while waiting for the engine can deadlock startup.



3. Integrate the paused Perf/Traders features into the single embedded runtime. Never run the existing root injector alongside embedded Gadget.



4. Extend the existing fingerprint-gated 5364 loader only after validating each additional specimen. The current loader checks for an absent player before database mutation. The rooted-phone test used embedded Gadget without root injection.



5. Add and test a mod-specific character policy. Restart-to-apply and vanilla sentinel restoration are verified; save isolation is not implemented. Import alone must never report activation.



6. Validate ARC mounting and custom maps separately. Database overrides do not establish map compatibility.



## Tests



Compile `src/org/tqmods/*.java` with Android API 36 android.jar. `tests/SevenZipTest.java` converts the 7z fixtures in `tests/fixtures/7z` (synthetic content packed with 7-Zip in several modes) and compares every file by SHA-256; it also checks refusals (encrypted, BZip2), damaged and truncated input, import of the result and duplicate detection. Extra .7z paths on the command line are converted and timed. `tools/make-7z-fixtures.py` regenerates the fixtures with 7-Zip; the publication guard accepts only archives listed in `fixtures/7z/SHA256SUMS`. `tests/ModStoreTest.java` runs on a host JDK; optionally pass an author-downloaded LootPlus ARZ path. Checks valid standalone/wrapped imports, real specimen, path traversal, duplicate case variants, multiple ARZ files, disallowed native-code payloads, bad headers and failed-import rollback. Embedded LootPlus activation has device evidence; storage-provider failures and low-space behavior still need device tests.



References: [Android document picker](https://developer.android.com/training/data-storage/shared/documents-files), [Android storage restrictions](https://developer.android.com/about/versions/11/privacy/storage), [Frida Gadget](https://frida.re/docs/gadget/).



### Resource inventory (source update)



Selection now records the selected variant's ARC paths and sizes alongside its

database. This is an inventory for future guarded activation, not permission to

mount archives. The current embedded resolver does not consume this list. The

source compiles and synthetic inventory tests pass; this update has not replaced

the game APK on the test phone. No multi-mod or custom-map UI is enabled by it.



The ASCII mod-text parser is likewise a tested building block, not yet connected

to the installed loader. The separate Gladiators text experiment is described in

the localization report. Non-ASCII input is explicitly unsupported by this parser.



### Custom-map preflight



The bootstrap source can recognize the recorded Gladiators specimen and check its

database plus seven required ARCs before producing an inactive map plan. The

manager has a separate message for this state. No custom-map database, resources,

text or world is activated by this path. Map startup and character isolation are

still pending integration. Old installed APKs do not acquire this UI automatically.



Profile matching uses sizes and FNV-1a fingerprints to detect accidental specimen

changes, not cryptographic authenticity or malware safety. Unknown profiles,

missing/changed required archives and invalid profile paths are refused. Extra

files are not mounted or treated as verified by this check.



### Separate save copies (source update)



The manager source can prepare a complete SaveData copy for a selected library

entry. An existing copy is never replaced. The copy includes a checksummed

snapshot and consumes additional storage. This action requires closing the game.

It does not yet make the game use the copied saves: runtime profile routing is

unfinished. The storage test passed on host and Android synthetic data; the new

manager action is compiled but not installed/UI-tested.

