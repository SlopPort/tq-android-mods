# Message over the game while a mod loads

Date: 2026-10-31. Phone: Redmi Note 8, Android 16, game build 3.0.5364. App 1.3, module 1.1. Mod: Masteries 1.03.

## The problem

The mod activates inside the game's own frame when the character list is opened (`PlayMenu::Update`). Until it finishes, the game draws nothing: after **Start game** the main menu stands still. Measured: about 8 s on the first start after copying the mod, 3–6 s later. The loader itself is ready about 1 s after the game process starts, before the main menu is shown.

Because the game cannot draw during this time, the message has to come from outside the game.

## What 1.3 does

- On **Play** with a mod, the app starts a small foreground service (`GameWatch`) before the game starts, with a notification for the time the game runs. It reads the loader status every 0.5 s through the module helper.
- Main menu: notification "The mod loads when you open the character list. The game then stands still for about 10 seconds; please wait."
- Activation: toast and notification "Loading the mod… The game stands still for a few seconds. Please wait." Module 1.1 writes the status `activating` right before the mod starts loading; with module 1.0 the app recognises the mod's own progress entry.
- Ready: toast "Mod ready. Choose your character."; the notification disappears after 15 s.
- Refused: the reason in plain words, in a toast and a notification that stays for 10 minutes.
- The service stops when the game closes, after 60 s without a game process, or after 15 minutes.

Android 13+ hides toasts from an app whose notifications are off (log: "Suppressing toast … by user request"), so the first **Play** asks for notification permission and starts the game after the answer. A refusal is remembered and not asked again; the game still starts.

## Device results

| Check | Result |
| --- | --- |
| First Play: permission question, then the game starts on its own after **Allow** | Pass |
| Notification while in the main menu (channel importance high) | Pass |
| Toast "Ładuję mod… Gra stoi przez kilka sekund. Poczekaj." over the frozen main menu | Pass |
| Toast "Mod gotowy. Wybierz postać." over the character list | Pass |
| Without notification permission | Messages suppressed by Android (reason for asking first) |
| Loader tests (`ProfileLoaderTest`: `waiting-for-menu` → `activating` → `ready`; never `activating` for the original game) | Pass |
