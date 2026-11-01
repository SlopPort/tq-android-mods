# Player guide

You need a 64-bit phone with root (Magisk) and Titan Quest installed (game build 3.0.5364). You do not need a computer. The app is in Polish on Polish phones; Polish button names are given in brackets.

## Install (once)

1. Open the [Releases page](https://github.com/SlopPort/tq-android-mods/releases/latest), scroll to **Assets** and download `tq-mods-magisk-1.5.zip` and `titan-quest-mods-1.7.apk`.
2. Open **Magisk** → **Modules** → **Install from storage** → choose `tq-mods-magisk-1.5.zip`. When it finishes, tap **Reboot**. In Magisk the module is called **Titan Quest Mods loader**.
3. Open your Downloads and tap `titan-quest-mods-1.7.apk`. If Android says installing from this source is blocked, tap **Settings**, switch on **Allow from this source**, go back and tap **Install**. If Play Protect warns about an unknown app, choose to install anyway.
4. Open **Titan Quest Mods** and tap **Allow root access** (*Zezwól na dostęp roota*). A Magisk window (*Superuser Request*) appears with a countdown and two buttons: tap **Grant** (*Zezwól*) before the countdown runs out.

If the Magisk window closed by itself, Magisk remembers that as a refusal. Open **Magisk** → **Superuser**, switch on **Titan Quest Mods**, then tap **Allow root access** in the app again.

TQ Android Perf and TQ Traders are not needed and not included. Already using them? Update them to Perf 2.4 and Traders 1.4. Older versions would put a second program into the game, which makes it close, so the app shows "Mods are paused" until you update. With the new versions, Titan Quest Mods starts them for you.

## Before the first try

**Settings and backups** (*Ustawienia i kopie zapasowe*) → **Save a backup of my characters** (*Zapisz kopię zapasową postaci*). Save the file somewhere you can find again (for example Downloads) and copy it to a computer too.

## Play a mod

1. Download a mod for the PC game on your phone, from its author's page. Mods come as a ZIP or 7z file and land in **Downloads**.
2. Open **Titan Quest Mods** and tap **Add a mod from a file** (*Dodaj mod z pliku*). Pick the file. A 7z file is unpacked first; large mods take up to a minute.
3. If the ZIP has several versions of the mod, the app asks which one to add.
4. The mod appears in the list and is chosen (gold frame). Tap the green **Play: …** button (*Graj: …*).
5. The first time, the app copies your characters for this mod. This takes a few seconds and happens once per mod.
6. In the game, wait a few seconds in the main menu, then tap **Start game**. The mod loads now and the game stands still for a few seconds; a message at the bottom says "Loading the mod…" and then "Mod ready. Choose your character." Pick a character as usual.

   The first time you tap Play, Android asks whether Titan Quest Mods may send notifications. Allow it: without it Android also hides these messages over the game. You can change it later in Android settings → Apps → Titan Quest Mods → Notifications.

Your original characters are not used by mods. Progress made with a mod stays with that mod, even if you remove the mod and add the same file again later. Each mod in the list shows how much space it uses on the phone.

To start a mod over with a fresh copy of your characters, tap **Delete this mod's characters** (*Usuń postacie tego moda*) on its card and confirm. Progress made with that mod is deleted for good; your original characters and other mods are not touched. The game must be closed. The link appears only when the mod already has its own copy (module 1.4 or newer).

After changing your choice, start the game with the app's green button. The game's own icon starts whatever you played last.

## Play several mods together

When the list has at least two mods with a normal game world, the button **+ Play several mods together** (*+ Graj kilkoma modami naraz*) appears under them.

1. Tap it and tick 2 to 8 mods, **the most important first**. The order matters: when two mods change the same thing, the one ticked earlier wins.
2. Tap **Next** (*Dalej*). The app compares the mods and says which ones change the same game records and whose version is used, or that they do not overlap.
3. Tap **Create** (*Utwórz*). The set appears in the list with the label **Several mods** (*Kilka modów*) and is chosen. Tap the green **Play: …** button as for a single mod.

A set has its own copy of your characters, separate from the copies of its mods. Custom maps (such as Gladiators) cannot be put in a set. If you remove a mod that a set uses, the set says so and cannot be played until you add that mod again or remove the set. Removing a set does not remove its mods. Sets need module 1.3.

## Go back to the original game

Open **Titan Quest Mods**, tap **Original game (no mods)** (*Oryginalna gra (bez modów)*), then the green **Play: Original game** button.

If the game is still running, the green button says **Restart the game with: …** (*Uruchom grę ponownie z: …*). Save in the game first (game menu → Save), then confirm. The game closes and starts again with your choice.

## What the labels mean

| Label | Meaning |
| --- | --- |
| Tested (*Sprawdzony*) | This exact mod file has been checked on a phone |
| Experimental (*Eksperymentalny*) | The app will load it; not every feature of the mod has been checked |
| Custom map – not supported yet | The mod has its own world, which cannot be played on Android yet |
| Several mods (*Kilka modów*) | A set of mods played together, with its own copy of your characters |
| Custom map with its own characters | A recognised map (Gladiators). Your characters for it are a separate copy. When a character enters the map, the game stops for about half a second while the loader prepares the paths the hero walks on |

## Settings and backups

- **Faster graphics (Perf)**: switches the TQ Android Perf module on or off from the next game start. Greyed out when Perf is not installed.
- **Save a backup of my characters**: saves all your original characters into one ZIP file.
- **Restore characters from a backup**: replaces your original characters with a backup (see below).
- **Put this app on the Home screen**: adds a shortcut to Titan Quest Mods.
- **Close the game**: shown while the game runs. Save in the game first.

## When something goes wrong

The top of the app shows one sentence about what happened. When the loader stops the game, it does so before anything is saved, so your characters are safe.

| Message | What to do |
| --- | --- |
| The Titan Quest Mods loader module is not installed | Install `tq-mods-magisk-1.5.zip` in Magisk and reboot |
| Root access was refused | Open Magisk → Superuser, switch on Titan Quest Mods, tap **Allow root access** again |
| Titan Quest is not installed on this phone | Install the game, or update the Magisk module to the latest release |
| Mods are paused: update … | Update TQ Android Perf or TQ Traders, then reboot |
| The character list was opened before the mod loader was ready | Tap the green button again and wait a few seconds in the main menu before tapping **Start game** |
| The character was opened before the mod finished loading | Start again; on the character list, wait a moment before picking a character |
| The game did not reach the character list in time | Start again and open the character list within a few minutes |
| The separate character copy for this mod is missing | Start the game with the app's green button, not the game icon |
| This is a RAR archive | On a computer, unpack it and pack the folder again as ZIP |
| This 7z file cannot be read here | It is password-protected or uses a rare packing method: on a computer, unpack it and pack the folder again as ZIP |
| This mod is already in the list | Nothing to do: the existing entry is chosen and keeps its characters |
| Added again: … Your characters for it are back | Nothing to do: this mod was removed earlier and its characters are used again |
| This game version is not supported | The loader only knows game build 3.0.5364 |
| This mod could not be loaded on Android | The mod's files did not load; choose another mod |
| An unexpected error | Please [report it](https://github.com/SlopPort/tq-android-mods/issues) with the details line shown under the message |

## Restore characters from a backup

**Settings and backups** → **Restore characters from a backup** → choose the ZIP. The game must be closed (**Settings and backups → Close the game**). Your current characters are not deleted: the app moves them to a hidden folder next to the game's save folder. [Ask in Issues](https://github.com/SlopPort/tq-android-mods/issues) if you need them back.

## Uninstall

Remove **Titan Quest Mods loader** in Magisk → Modules and reboot, then uninstall the app. The mods' copies of your characters stay in the game's storage until you uninstall the game or clear its data. Your original characters are never changed by mods.

## Reporting a problem

[Open an issue](https://github.com/SlopPort/tq-android-mods/issues) with the game version, Android version, mod name and version, the link to the mod's page and the exact message. In Magisk → Modules, the **▶** (action) button on the Titan Quest Mods loader card shows what happened at the last game start. Do not upload game APKs, saves or mod files.
