# Masteries 1.03 on a new character

Date: 2026-10-31. Phone: Redmi Note 8, Android 16, game build 3.0.5364. App 1.2, module 1.0. Mod: Masteries 1.03, Rebalance Patch variant, from the author's 7z download.

Goal: check what a player sees when choosing a mastery from the mod on a new character. Everything ran in the mod's own character copy; the original characters were compared afterwards (1,081 files, identical by SHA-256).

## What was done

1. **Play** with Masteries in the app, **Start game**, **Create** a hero with **Advanced hero** (level 40, start of Ragnarök).
2. In the world: character window → skills tab → mastery choice.

## Results

| Check | Result |
| --- | --- |
| Mastery list | 11 masteries. The Dream slot shows the mod's **Chronosophy** with its name and the mod's English description ("Time itself is a powerful weapon…") |
| Chronosophy portrait after choosing | The mod's own picture (`Textures\chronopanel01.tex` from the mod's Textures.arc) |
| Skill tree layout | The mod's layout: 20 skill slots on the mod's tiers |
| Skill icons | **Mostly blank frames.** Only skills whose icon is a base-game file show an icon (for example Ternion at tier 40). The mod's own icons (`Textures\chrono_*.tex`) are not shown |
| Tab title | Base-game Polish name "Mistrzostwo snów" (the mod ships English text only, and the game's Polish text for that tag wins) |
| Loader | One injection, mod active (`modsActivated: true`), 54 engine functions verified |

## Why the icons are blank

A temporary diagnostic (not part of any release) logged the engine's file and texture requests after the mod was active:

- The portrait `Textures\chronopanel01.tex` is requested through `GraphicsEngine::LoadTexture` and opened from the mod's archive.
- For the skill icons, the engine makes **no request at all**: no `LoadTexture`, `OpenFile`, `DoesFileExist`, `GetFileSize`, `OpenFileStream` or `FindFullFilePath` call with a `chrono_*` name, while the mastery panel is opened and the mastery is chosen.

So the mod's icon files are present and readable, but the mobile skill panel does not ask for them. The likely cause is that skill icons are resolved earlier or through a cache that is filled before the mod is activated. This needs a separate investigation; it does not affect whether the skills work.

A first attempt hooked `FileSystem::OpenFile` before activation; the loader noticed the changed function bytes ("API bytes mismatch") and closed the game before anything was saved, as designed.

## Status

- Choosing a mod mastery on a new character: **works**, with the mod's name, description and portrait.
- Mod skill icons: **known issue** (blank frames).
- Skill effects in combat were not checked in this run.
