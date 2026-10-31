# Walking in the Gladiators map (module 1.2)

Date: 2026-10-31. Phone: Redmi Note 8, Android 16, game build 3.0.5364. App 1.3, module 1.2 (development copy of the loader). Map: Gladiators, imported from a ZIP through the app.

## Background

PC maps do not contain the navigation data (Recast meshes) that the mobile engine uses for movement. The Gladiators world loaded, but the hero could not walk ([investigation](2026-10-29-navigation-format-investigation.md)). A lab test showed that the engine's own `Level::CreatePathMesh` builds the missing data for one level ([lab test](2026-10-30-single-level-navigation.md)).

## What module 1.2 does

`map-navigation-runtime.js` runs only when a recognised map is active. Each frame of the in-game HUD it checks the player's level, and builds that level's navigation once all of the following hold:

- the call runs on the engine's main thread (full thread handle and the engine's own id agree);
- no level is loading, and the level is loaded, post-loaded and belongs to the player's region;
- the level has a navigation object that is still empty (stock levels already have data and are never touched);
- the same level has been stable for 30 frames.

Each level is built at most once per game start. A build that leaves the mesh empty is not retried. At most 64 builds per game start. All 13 engine functions are checked by size and fingerprint before the game starts (`nav-api-5364.json`); on a mismatch the loader refuses and nothing is changed.

## Device results

| Check | Result |
| --- | --- |
| Import of Gladiators through the app, recognised as a supported map | Pass |
| New character MapTest enters "Room of gladiators" | Pass |
| Navigation built automatically | 1 level, 412 ms, initialised |
| Walking with the on-screen joystick: right, up, left to the wall, down into the next part of the room | Pass; camera and scenery follow, walls block |
| Save in the game, close the game, start again, load MapTest | Pass; navigation built again (404 ms), walking works |
| Original characters after the test | 1,081 files identical to the earlier backup |
| Unit test (`MapNavigationTest.js`): settle, once per level, stock levels, loading, wrong thread, failed build, limit | Pass |

## Not checked

- Enemies or other creatures moving on the built navigation: the explored part of the room held only a statue.
- Other custom maps: the adapter recognises only this Gladiators file.
