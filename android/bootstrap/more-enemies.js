'use strict';
// "More enemies": ProxyPool::RunPool(int) reads spawnMinModifier and spawnMaxModifier (percent, used only when > 0)
// from the engine record on every pool run. We scale both values right after they are read; no game data changes.
// Read sites (game 3.0.5364): +0x9c `ldr s1,[x19,#0x10]` and +0x160 `ldr s1,[x19,#0x18]`; there s9 = min, s0 = max.
function tqStartEnemies(engine, percent) {
  if (percent === undefined || percent === 100) return {requested: false, status: 'off'};
  if (![150, 200, 300].includes(percent)) throw Error('Unsupported enemies factor');
  const factor = percent / 100;
  const run = engine.getExportByName('_ZN4GAME9ProxyPool7RunPoolEi');
  const sites = [[0x9c, 0xbd401261], [0x160, 0xbd401a61]];
  // Check both sites before hooking either, so an unknown game build is left untouched.
  for (const [off, word] of sites) if (run.add(off).readU32() !== word) return {requested: true, status: 'unsupported-build'};
  const scale = v => (v > 0 ? v : 100) * factor;
  for (const [off] of sites) Interceptor.attach(run.add(off), function () {
    const c = this.context; c.s9 = scale(c.s9); c.s0 = scale(c.s0);
  });
  return {requested: true, status: 'on', factor};
}
