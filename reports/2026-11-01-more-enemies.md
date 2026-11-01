# Own "more enemies" (2026-11-01)

No mod data and no third-party code. `ProxyPool::RunPool(int)` reads four engine-record floats on every pool run
through `GameEngine::GetDatabase()`: `spawnMin`, `spawnMax`, `spawnMinModifier`, `spawnMaxModifier`.
The modifiers are percentages, applied only when > 0 (stock phone value: 100).
The probe (`2026-11-01-enemies-probe.js`) hooks the two places in RunPool right after the values are read
(+0x9c and +0x160; it checks the instruction words first) and multiplies them by a factor from `files/tqmods/enemies.txt`.

Result on the phone (test hero, never saved; same start area, 3 counts each):

| run | pool runs seen | modifier | monsters |
|---|---|---|---|
| off | - | 100 | 90 |
| x2 | 4 | 100 -> 200 | 94 |

The override takes effect (log shows 100 -> 200 at load), unlike XMAX's record change, but only the pools that run
in the starting area are affected, so the start-area count barely moves. Most of the 90 monsters there are not
from pools that ran. A fair test needs walking into new areas (the benchmark walker) and comparing counts.
Next: measure along a walk; if useful, add it to the loader as an opt-in setting with a factor of 1-3.

## Per-pool measurement (same day, later)

The probe now also logs each pool's final size (`[pool+0x10]` min, `[pool+0x14]` max) after RunPool.
Same hero, same spot, saves restored from a backup after every run (the game autosaves on load):

| pool | x1 min/max | x2 min/max |
|---|---|---|
| 1 | 3/4 | 7/9 |
| 2 | 1/2 | 2/4 |
| 3 | 2/3 | 4/7 |
| 4 | 3/5 | 7/11 and 11/18 |
| 5 | 1/2 | 2/4 |
| monsters loaded | 119 | 141, 134 |

So the factor works as intended: every pool spawns about twice as many monsters. Walking tests were dropped:
adb swipes only nudge the on-screen joystick, and the test hero's position differs per save.
Test-run note: always back up the save profile first and restore it after each run.
