# Early mod activation: investigation (2026-11-01)

Question: can mods be activated before the game reads its startup records, so that XMAX (game-engine record) and the Masteries skill icons (skill tables) work? Setup: app 1.6, module 1.4, game build 3.0.5364, Redmi Note 8. Temporary probes were appended to the loader for single runs and removed afterwards; nothing was saved in the game.

## Findings

1. **The loader cannot run before startup.** The module injects the loader about half a second after the game process appears. In a warm start the stock database already contained the game-engine record 6 ms after injection, so overlaying XMAX x2 that early still left 90 monsters. In a cold start the engine and database appeared 4.8 s after injection, but the engine settings were read through a path that inline hooks on `GameEngine::Initialize` and `GameEngine::LoadFromDatabase` never saw.
2. **Inline hooks on functions the loader checks are refused.** Hooking `DatabaseArchive::Deserialize` at injection made the loader's API byte check fail ("API bytes mismatch"), and the game was stopped as intended.
3. **The game caches loaded records.** `ProxyPool::RunPool` (monster group spawning) reads `spawnMinModifier`/`spawnMaxModifier` at spawn time through `GameEngine::GetDatabase()`, i.e. from a record table loaded once and kept in the object manager's table cache. Adding the mod's database later does not replace a table that is already cached.
4. **`GameEngine::Reload()` is not usable.** It deletes the engine's skill profiles and reads the record again; the game then crashed in the world (`SkillAttributeStore::AddToAccumulator`, a freed skill profile).
5. **A targeted refresh works mechanically.** Hiding only the cached `Records/XPack/Game/GameEngine.dbr` table (the old table is kept in memory, not freed) and calling `GameEngine::LoadFromDatabase` gave the engine a new table built from the mod's record, with no crash.
6. **But XMAX's record is not safe to apply.** x2 ships the PC version of the record, which lacks 65 mobile fields. After the refresh 46 engine values changed, many to zero, including all inventory bag sizes (480×200 → 0). Playing like that would break the inventory. The monster count did not double either (84 in both refreshed runs).

## What this means

- XMAX cannot work as published: its game-engine record must first be merged with the phone's own record (take the mobile record, change only the fields the mod changes). The loader would have to do that merge on the phone, since the game's record may not be redistributed.
- Mod skill icons likely need the same targeted approach: hide only the cached tables of records the mod overrides, then let the game load them again. The earlier icon attempt hid every hero and skill-tree table and broke saved characters' masteries.
- Both are new loader features with save-safety risks; neither is in a release.
