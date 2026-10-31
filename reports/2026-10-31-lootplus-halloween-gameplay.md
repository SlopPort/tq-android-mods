# LootPlus drops and Halloween gameplay (2026-10-31)

Device: Redmi Note 8 (rooted), module 1.4 release loader, app 1.5. A temporary test
script (never shipped) spawned monsters next to the hero, killed them with the hero
credited as the attacker, and counted the Item objects in the world before and after.
Nothing was saved; every run ended with a force-stop.

## LootPlus 1.3

Imported through the app ("Dodaj mod z pliku"); recognised as the tested build.
The loader report showed the loot-table overrides live in memory (`overrides-verified`).

Test monster: `as_serpentoid_caster_32`. LootPlus raises its head, forearm and leg
equip chance from 20% to 50%; weapons are unchanged. 60 kills per run, same hero and
spot:

| Items from 60 kills | Original game | LootPlus |
|---|---:|---:|
| All | 119 | 191 |
| Helm | 12 | 30 |
| Greaves | 11 | 33 |
| Armband | 11 | 26 |
| Torso | 20 | 35 |
| Staff (not changed by the mod) | 60 | 60 |

The extra drops are in exactly the slots the mod changes, and the untouched weapon
slot matches, so LootPlus drops work on the phone.

A satyr (`bm_peltast_06`) gave 49 items from 30 kills in both runs, which is expected:
LootPlus ships that record unchanged.

## Halloween2012

The world loads and plays at 30 FPS, and combat works. All 7 resource archives mount
and all 2,727 records load (16 sampled, 0 missing). The mod mostly swaps NPC and
monster meshes between regions and tints the hero's night light; nothing visibly
changes in the Summer Palace area, whose creatures are not in the mod.
