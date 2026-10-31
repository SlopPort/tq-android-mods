# Remaining work

Updated: 2026-10-31. Goal: a mod loader that a non-technical player can use: add a mod file, tap Play, switch back with one tap.

## Roadmap

1. **RAR import** (7z is done in 1.1). The official unRAR source may not be used to build a RAR-compatible archiver, and clean-room decoders need a licence review first.
2. **Content-mod coverage.** New-character mastery selection with Masteries works; its skill icons are blank because the mobile skill panel never requests mod icon files ([report](reports/2026-10-31-masteries-coverage.md)): find where skill icons are resolved. Still to check: LootPlus drops after the generic path, Halloween gameplay, and records the engine caches before character selection.
3. **Custom worlds.** Gladiators is walkable since module 1.2 (navigation built per area, [report](reports/2026-10-31-map-navigation.md)). Next: generalize the Gladiators adapter (map path, sentinel records, text tags) to other maps; this needs more map files from their authors to test with. Check enemies and quests inside a map once a map with them is available.
4. **Several mods at once:** done for content mods (1.4). Still open: report overlapping resource files (textures, sounds) as well as records, and combinations that include a custom map.
5. **Perf pause-menu labels** regression.
6. Rename/describe mods in the list.

Done on 2026-10-31 (1.5, module 1.4): delete a mod's or set's character copy from its card (module `deleteprofile`, refused while the game runs; `status` lists existing copies). Checked on the phone: only the chosen copy deleted, a fresh copy made on the next start, originals unchanged.

Done on 2026-10-31 (1.4, module 1.3): 2 to 8 content mods played together; the first-ticked mod wins conflicts, the app counts shared records before creating a set, each set has its own character copy. Checked on the phone with Masteries + Halloween 2012.

Done on 2026-10-31 (module 1.2): Gladiators navigation is built automatically when a character enters an area; walking, save, restart and reload checked on the phone.

Done on 2026-10-31 (1.3, module 1.1): a message over the game while a mod loads and when it is ready (notification and toast from the app; the loader reports an `activating` state).

Done on 2026-10-31 (1.2): a removed mod added again gets its characters back; size shown per mod.

Done on 2026-10-31 (1.1): 7z import on the phone, duplicate-import detection, list keeps its place after actions ([report](reports/2026-10-31-7z-import.md)). 1.0.1: restore fix.

Done on 2026-10-31: Magisk module and separate app, release 1.0 (reports/2026-10-31-magisk-route.md); generic content mods without an allowlist, any character in a mod's copy, activation on a fast LoadGame, automatic first-start save copy, one-button restart, simplified bilingual manager, skipping loose PC extras on import, mod removal. See reports/2026-10-31-generic-content-mods.md.

# Earlier checklist (2026-10-27)



Updated: 2026-10-27. Primary objective: convenient mod import, selection and activation inside Titan Quest Android without requiring root for normal use.



## Confirmed baseline



- [x] Standalone importer launched on the test phone without using root.

- [x] Android document picker -> original XMAX ZIP -> variant selection -> x2 import.

- [x] Imported entry persists after force-stop and relaunch.

- [x] Host tests verify six XMAX variants and LootPlus import; selected-variant files match originals and exclude sibling files.

- [x] Synthetic variant and malformed-archive regression tests pass.

- [x] Standalone importer build script completes locally.

- [x] Local unsigned game APK contains the initial manager activity. This is packaging evidence only; it predates later importer changes.



## 1. Resume device bootstrap testing



- [x] Restore availability of the normal automatic approval path. Last install attempt was not executed because approval review hit a usage limit. Do not bypass that gate.

- [x] Rebuild the bootstrap test APK with the latest PID-check diagnostic; the existing signed APK predates that fix.

- [x] Verify signature and alignment, then update only org.tqmods.importtest using the existing local test key.

- [x] Launch the bootstrap diagnostic. Require a report from the current PID, advancing ticks and no startup hang.

- [x] Confirm hooksInstalled=false and modsActivated=false. enginePresent=false is expected in this standalone harness.

- [x] Repeat after process restart and record outcomes. This does not prove embedded-game compatibility.



## 2. Integrate into the game locally



- [ ] Inventory game version, signature, original install source, current save locations and enabled root modules.

- [x] Make and verify a fresh backup of characters, settings, module state and installed APKs before replacing the game. Earlier backups must remain available.

- [ ] Rebuild with the latest importer and bootstrap. Preserve game content and entitlement checks. Do not substitute an unlocked build as evidence of legitimate-store compatibility.

- [ ] Review bootstrap dependency licenses and record versions/hashes. Keep proprietary APKs and signing keys local.

- [ ] Sign locally and plan the different-signature install/data-restore transition. Do not assume an in-place update will work.

- [x] Ensure only one hook owner: never run the old root injector concurrently with embedded Gadget.

- [ ] Verify game launch, assets, character selection, manager access and exit/restart before applying any mod.

- [ ] Prove the tested runtime path does not use root. A rooted test phone alone is insufficient evidence of compatibility with every stock phone.



## 3. Implement mod activation on 5364



- [x] Validate the eight 5364 API fingerprints and three bounded record layouts used by the current LootPlus experiment; menu-thread execution observed. This does not validate every engine API.

- [ ] Store selected variant/database/resource paths explicitly; distinguish imported, selected, active and failed states.

- [x] Current loader checks for an absent character before mutation; mocked refusal tests pass. Device mid-session refusal still needs a separate test.

- [x] Add restart-to-apply and vanilla mode; manager fresh-start cycle and three-field vanilla restoration verified. Automatic recovery from partial mutation remains future work.

- [ ] Define save separation and character policy. Use a disposable character during development.

- [ ] Verify stock -> mod -> fresh stock values for known database fields, plus delayed reads and save/reload.

- [x] Laboratory ARC mounting and Gladiators world-entry/resumption checks passed; see the October 27 reports.

- [ ] Integrate guarded map selection, identity propagation and resource mounting into the manager.

- [ ] Keep multi-mod merging out of the initial release unless conflict handling is implemented and tested.

- [x] Audit all 21 database pairs among LootPlus and six XMAX variants; publish collision matrix and a synthetic-tested comparison tool. This is static evidence only.

- [ ] Prepare and independently test a mobile-preserving x2 adaptation before trying LootPlus plus x2. Verify sequential-loading semantics and retained values before adding multi-selection UI.



## 4. Device mod validation



- [x] LootPlus activation and database sentinels verified on 5364.

- [ ] Verify observable LootPlus gameplay effects.

- [ ] XMAX x2: test changed enemy spawning with a disposable character, followed by stock restoration.

- [ ] Test remaining XMAX variants individually once x2 works; record performance and failures without claiming universal compatibility.

- [x] Evaluate Gladiators in a local world-entry/save-resumption prototype.

- [ ] Validate Gladiators gameplay and additional maps obtained from authors; do not redistribute third-party files without permission.

- [x] Expand local inventory with Halloween 2012, Masteries base/rebalance and Gladiators; compare all 55 pairs across eleven databases. Import normalized Halloween and Gladiators on device without activating them.

- [ ] Complete manager integration and gameplay tests before claiming map/resource-dependent mods playable. Sixth Tablet downloaded specimen fails archive validation and needs replacement.

- [ ] Compile and test the hash-bound Masteries/LootPlus field-resolution proposal; it is currently a plan, not an applied compatibility patch.

- [ ] Record game/mod versions, specimen hashes, device/Android version, activation evidence, gameplay results and regressions.



## 5. Importer and user experience



- [x] Export and restore a checksummed character backup through the embedded manager; device readback matches all 1,081 files and retains previous saves. User confirmed character list after restore.

- [x] Reject a mod ZIP supplied as a backup without changing current saves; block backup while engine is loaded.

- [ ] Test Android interruption recovery, restored-character gameplay and install latest activity-lifecycle fixes (currently compiled source only).



- [x] Test document-picker cancellation and close-dialog cancellation on device; selection and running PID preserved.

- [ ] Test rotation/activity recreation, process death, unavailable document provider and low-storage handling on device.

- [x] Current source stages a bounded private input snapshot before selection; variant retries reuse it. Host tests cover repeat reads and cleanup; updated UI remains device-untested.

- [ ] Test malformed/truncated archives and total-size/entry limits on device.

- [ ] Decide how to handle shared resources outside variant folders; currently they are excluded. Do not silently claim full package support.

- [ ] Add clear variant/status labels, removal and disk-space information.

- [ ] Add a game-menu entry and a usable return/start-game flow after engine integration.

- [ ] Update the plain-language guide only for features actually available, with screenshots from the final interface.



## 6. Publication



- [ ] Have the text-audit agent review new public English documentation before the next push.

- [ ] Run publication guard, synthetic tests and diff/history checks. Review copyright provenance and dependency notices manually.

- [ ] Publish local unpushed source/docs/test changes; never include game APKs, libraries, archives, saves or keys.

- [ ] Keep runtime limitations explicit: no completed rootless game loader yet.

- [ ] Enable the Actions workflow through an authorized credential with workflow scope; currently only a template is published.

- [ ] Choose/document an appropriate license for original code after provenance/dependency review; do not imply rights over third-party materials.



## 7. Deferred migration tasks



- [ ] Audio-cache playback/combat regression test was deferred by the user; activation was observed but audio quality/performance remain unverified.

- [ ] Before deleting old phone-side patch remnants, confirm the replacement works on restart and all needed settings/features were preserved.

- [ ] Retain old APKs and backups on the PC as requested. Do not remove unrelated phone content.



## Completion criteria



Latest resource milestone: isolated device reads of LootPlus, Gladiators and

Halloween map ARCs passed individually and with all three sources registered

in one temporary FileSystem. See reports/2026-10-27-isolated-resource-mounts.md.



Update: uniquely prefixed ARC registration in the engine-owned FileSystem and

restart removal passed; see reports/2026-10-27-global-resource-roundtrip.md.

Gladiators first world entry and a save succeeded on ModProbe. A scoped map-identity correction now permits world-entry resumption in two separate processes, including after a new save. The separate World::Load redirect remains required.

All 1,081 saves were restored and hash-verified. See

reports/2026-10-27-gladiators-world-entry.md and reports/2026-10-27-map-save-enumeration.md. Product integration, full saved-state validation, movement,

text registration and stock-name override coverage remain open.



Follow-up: isolated ARC collision tests established group 0 before group 1 and

first-added wins within tested group 0. A delayed stock observer measured group 0

empty and group 1 with 163 sources. For that earlier observation,

see reports/2026-10-27-stock-resource-groups.md for the early-observer failure and

successful delayed measurement.



A user with a supported, lawfully obtained game copy can follow a documented setup, import an author-supplied mod through Android's picker, choose a variant, start the game with confirmed activation, and return to vanilla without root or routine ADB use. Supported mod/version combinations have reproducible device evidence, saves have a tested recovery path, and public releases contain only material we may distribute. Until then, label releases as prototypes.



Localization follow-up: the wide-string workaround displayed the Gladiators

location name on device; the narrow loader produced an empty value despite tag

presence. See reports/2026-10-27-gladiators-localization.md. ASCII parser tests

pass, but manager wiring, other encodings and remaining tag consumers are open.



Map integration follow-up: source now prepares a fingerprint-checked inactive

Gladiators resource plan. Device preflight passed for seven ARCs without loading

a player; see reports/2026-10-27-map-preflight.md. Manager UI text is compiled,

not installed. Save isolation and native plan consumption remain unfinished.



Save-profile follow-up: independent snapshot-backed storage and the manager action

are implemented in source. Host/Android synthetic write-separation tests pass.

See reports/2026-10-27-save-profiles.md. Runtime save-root routing is not implemented;

this must not be presented as gameplay/save isolation yet.

