// TEMPORARY experiment: "more enemies" without any mod data. ProxyPool::RunPool reads spawnMinModifier and
// spawnMaxModifier (percent, applied only when > 0) from the engine record on every pool run; we override the two
// values right after they are read. Factor comes from files/tqmods/enemies.txt (e.g. "2"); missing file = off.
(function () {
  const out = '/data/data/com.hg.titanquestultimate/files/enemies.log', lines = [];
  const flush = () => { try { const f = new File(out, 'w'); f.write(lines.join('\n') + '\n'); f.close(); } catch (e) {} };
  setInterval(flush, 1000);
  let factor = 0;
  try { factor = parseFloat(File.readAllText('/data/data/com.hg.titanquestultimate/files/tqmods/enemies.txt').trim()); } catch (e) {}
  if (!(factor > 0 && factor <= 4)) { lines.push('off'); return; }
  const m = Process.getModuleByName('libtq.so');
  const run = m.getExportByName('_ZN4GAME9ProxyPool7RunPoolEi');
  // Two read sites inside RunPool (normal and second pool block): s0 = max modifier, s9 = min modifier.
  const scale = v => v > 0 ? v * factor : 100 * factor;
  let n = 0;
  for (const off of [0x9c, 0x160]) {
    const at = run.add(off);
    if (at.readU32() !== 0xbd401261 && at.readU32() !== 0xbd401a61) { lines.push('unexpected code at +' + off.toString(16) + ' ' + at.readU32().toString(16)); continue; }
    Interceptor.attach(at, function () {
      const c = this.context, mn = c.s9, mx = c.s0;
      c.s9 = scale(mn); c.s0 = scale(mx);
      if (n++ < 30) lines.push('pool +' + off.toString(16) + ' min ' + mn + '->' + c.s9 + ' max ' + mx + '->' + c.s0);
    });
  }
  lines.push('enemies x' + factor + ' ready');
})();
