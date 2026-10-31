# Magisk route: loader injected into the installed game

Date: 2026-10-31. Device: rooted Redmi Note 8, Android 16, Magisk, game build 3.0.5364. Release 1.0: Magisk module `tq_mods` and the separate app **Titan Quest Mods** (`org.tqmods.manager`).

## Design

- The game app is not changed. The module's `service.sh` watches for the game process and injects one script with `frida-inject` 17.22.1: the loader, plus TQ Android Perf with its add-ons (or TQ Traders alone) when they are installed. The game closes itself after a second injection, so Perf 2.4 and Traders 1.4 stand back while `tq_mods` is active. With older versions active, `tq_mods` does not inject and the app says what to update.
- The app has its own package and signing key and contains no game code. It imports mods into its own storage. When the player taps Play, it runs the module's `tqmods.sh` through one root shell. The helper copies the chosen mod into the game's private folder, makes the mod's character copy once (from the original saves, read only), writes the choice and starts the game.
- The loader runs in a new `magisk` mode. It reads the persistent choice, checks the character copy record (`profile.properties`), binds the request to its own process and attaches to the running engine.

## Timing

| Measurement | Result |
| --- | --- |
| Injection 0.25 s after the process appeared | Failed (`connection closed`): the process was still starting |
| Injection 0.4–2 s after the process appeared | Worked; `libtq.so` was already loaded every time |
| First call of the save-folder function after start | Only when **Start game** was tapped (about 28 s in the test), before the first character-list frame |

The engine therefore cannot be caught before it loads. The save folder is first read when the character list opens, so the loader installs its hooks on the running engine at once. As a guard, if the character list shows a frame and the save folder was never redirected, the loader closes the game before anything is written.

## Device results

| Test | Result |
| --- | --- |
| Install the three modules, reboot | `tq_mods`, Perf 2.4 (paused) and Traders 1.4 services running |
| App without root granted | Explains root and shows a button; importing works without root |
| Import Masteries 1.03 through the picker | Variant dialog; imported and chosen |
| Play (helper `launch`) with Masteries | Character copy made (1,081 files); one injection; Traders hosted |
| Loader report after attach | Late attach, 54 engine functions matched, cloud saves off, save redirect ready |
| Open character list | 6 ARCs mounted, 1,998 records loaded (16 samples found), 2,629 text tags; save folder redirected |
| Load a character | Character loaded from the mod's copy; in the game world |
| Switch to the original game | No mod, no save hooks, Traders hosted; the mod's files removed from the game folder |
| Original saves afterwards | 1,081 files compared by SHA-256 with the pre-test backup: 0 changed, 0 missing, 0 added |

Most root actions above were run with the same helper command the app uses, from ADB. After the phone owner granted root to the app, the app's own path was checked too:

| Test | Result |
| --- | --- |
| First status call from the app | "Titan Quest is not installed": root granted to an app runs in that app's mount namespace, where Android hides other apps' data. Fixed: the helper continues in the global namespace (`nsenter -t 1 -m`) |
| Status after the fix | Game found; Play enabled |
| Play in the app | Game started with Masteries; one injection; loader waiting for the menu |
| App while the game runs | "… will load when the game shows the character list" and **Return to the game** |

Settings in the app (fresh save backup taken first):

| Test | Result |
| --- | --- |
| Perf switch off, then on | Creates and removes Perf's `paused` file; left off as before the test |
| Close the game from the app | Game closed |
| Save a backup of my characters | ZIP saved to the phone; 1,081 files plus `SHA256SUMS.txt`, identical by SHA-256 to the pre-test backup; staging folder removed |
| Restore characters from a backup (1.0) | **Failed**: "Existing SaveData directory required". The app's cache path (`/data/user/0/…`) is an alias of `/data/data/…`, and the restore check refuses aliased paths. Nothing was changed |
| Restore (1.0.1, canonical staging path) | "Characters restored". Previous saves kept in `.SaveData-before-restore-<date>`; 1,081 restored files identical by SHA-256 to the backup; owner `1023:30185` and SELinux context `media_rw_data_file` as before; staging folder removed |
| Original game after restore | Character list shows the characters |

## Limits and notes

- The test phone runs a third-party repack of the game, whose launcher first shows an unpack screen. For the test, the module's optional `activity` file pointed the launch at the game's main activity. A Google Play copy has not been tested.
- Magisk turns an unanswered root prompt into a lasting refusal ("Always" is preselected). The app therefore asks for root only after the player taps a button, and explains how to switch it on in Magisk → Superuser.
- The game icon starts the last choice that was played. A new choice takes effect through **Play**.
- The game folder keeps only the chosen mod's files; the app keeps every imported mod. Character copies are never deleted.
- Content-mod limits are unchanged (see the [generic content mods report](2026-10-31-generic-content-mods.md)).
