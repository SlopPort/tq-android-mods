# Masteries mod: blank skill icons (2026-11-01)

Test: Masteries v103 copy, test hero at the mastery panel, an extra mastery picked, game never saved (force-stopped).

- With a temporary probe that hides the 10 cached record tables the mod overrides (hero records excluded),
  the panel still shows every skill slot as an empty frame; only the mastery emblem and two tier-40 icons draw.
- The same happens for a stock mastery picked from the mod's list, so it is not limited to the new (Chronosophy) textures.
- During the panel visit the game never called `GameEngine::LoadSkillResource` or `SkillProfile::GetUpBitmapName`
  for the mod's skills, and the only mod texture requested was the panel background.
- So the mobile panel does not build its icons from the refreshed records; stale caches are not the cause.
  Next idea (not tried): find which panel object fills the slot bitmaps and where it reads the skill list.
- The phone was restored to the release loader (sha256 2d6302cf...a6).
