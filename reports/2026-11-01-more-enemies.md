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
