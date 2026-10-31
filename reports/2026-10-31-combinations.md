# Several mods at once (app 1.4, module 1.3)

Date: 2026-10-31. Device: Redmi Note 8, Android 16, game build 3.0.5364.

## What was added

- **App:** the button *Play several mods together* appears when at least two content mods are in the list. The player ticks 2 to 8 mods, most important first.
  - The app then reads the record names from each mod's database and counts the records two mods both change.
  - The player creates the set after seeing that count. A set is stored in `files/tqmods/combos/<uuid>/` (an ordered `members` list and a title), and a set with the same mods in the same order is reused.
  - The start request lists the members in order (`memberIds`, `members`). The set's own UUID names its character copy.
- **Module (`tqmods.sh launch`):** copies every member mod into the game's private folder instead of one. It validates each UUID, and the cleanup keeps every member.
- **Loader:**
  - `resolve-selection` resolves each member through the single-mod path and accepts only content mods. It refuses fewer than 2 or more than 8 mods, repeated mods and custom maps.
  - `tqActivateGenericMods` mounts the members' archives in order. The first source added wins a file lookup, so the first mod's resources win.
  - It reads each mod's text from its own `Text.arc` and registers the texts in reverse order.
  - It deserializes the databases in reverse order, so the first mod's records are applied last and win. It then checks sampled records of every mod.

## Rule: the mod ticked first wins

| Overlap | Winner | How |
| --- | --- | --- |
| Database record | first mod | applied last |
| Resource file (texture, sound, map piece) | first mod | mounted first |
| Text tag | first mod | registered last |

## Phone check: Masteries 1.03 + Halloween 2012

| Step | Result |
| --- | --- |
| Conflict dialog | 3 shared records; Masteries' version used |
| Loader status | `ready`, mods activated, content loaded |
| Masteries | 1,998 records, 0 sampled missing, 2,629 text tags, 6 mounts |
| Halloween 2012 | 2,727 records, 0 sampled missing, 1 text tag, 7 mounts |
| New character in the set's copy | mastery list shows *Chronosophy* from Masteries next to the stock masteries |
| Original saves afterwards | all 1,081 files byte-identical to the backup taken before the tests |

Not checked: Halloween's own items or events during play with the set. Resource overlaps are resolved by the rule above but are not yet counted in the dialog.

## Tests

- `android/tests/CombinationTest.js`: selection limits, mount order, database order and text registration.
- `android/tests/CombosTest.java`: set storage and reuse, ARZ record names, conflict counts and damaged ARZ files.
- `android/tests/ResolveSaveProfileTest.js`: a set has no single database but still needs its prepared character copy.
