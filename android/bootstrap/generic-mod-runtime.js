'use strict';
// Generic content mods for build 5364: mount the selected variant's ARC
// resources, overlay its ARZ on the loaded database and register its
// Text\modstrings.txt tags. The stock world is kept; custom worlds need a
// dedicated adapter. Runs once at character selection. No hot unload.

// Mod text files are usually Windows-1252; some are UTF-16LE or UTF-8.
const tqCp1252 = [0x20ac,0x81,0x201a,0x192,0x201e,0x2026,0x2020,0x2021,0x2c6,0x2030,0x160,0x2039,0x152,0x8d,0x17d,0x8f,
  0x90,0x2018,0x2019,0x201c,0x201d,0x2022,0x2013,0x2014,0x2dc,0x2122,0x161,0x203a,0x153,0x9d,0x17e,0x178];
function tqDecodeModText(buffer) {
  const bytes = new Uint8Array(buffer);
  if (bytes.length > 4 * 1024 * 1024) throw Error('Mod text exceeds 4 MiB');
  if (bytes.length >= 2 && bytes[0] === 0xff && bytes[1] === 0xfe) {
    let s = '';
    for (let i = 2; i + 1 < bytes.length; i += 2) s += String.fromCharCode(bytes[i] | bytes[i + 1] << 8);
    return s;
  }
  let start = bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf ? 3 : 0;
  // Strict UTF-8 first; any invalid sequence means a legacy single-byte file.
  let s = '', i = start, utf8 = true;
  while (i < bytes.length) {
    const b = bytes[i];
    if (b < 0x80) { s += String.fromCharCode(b); i++; continue; }
    const n = b >= 0xf0 && b < 0xf5 ? 3 : b >= 0xe0 ? 2 : b >= 0xc2 && b < 0xe0 ? 1 : -1;
    if (n < 0 || i + n >= bytes.length) { utf8 = false; break; }
    let c = b & (0x3f >> n);
    for (let k = 1; k <= n; k++) {
      const x = bytes[i + k];
      if (x === undefined || (x & 0xc0) !== 0x80) { utf8 = false; break; }
      c = c << 6 | x & 0x3f;
    }
    if (!utf8) break;
    s += String.fromCodePoint(c); i += n + 1;
  }
  if (utf8) return s;
  s = '';
  for (const b of bytes) s += String.fromCharCode(b >= 0x80 && b < 0xa0 ? tqCp1252[b - 0x80] : b);
  return s;
}

// Returns tag rows; later duplicates win like the PC loader's map insertion.
function tqParseModText(text) {
  const rows = new Map();
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.replace(/^\s+/, '');
    if (line.startsWith('//')) continue;
    const equals = line.indexOf('=');
    if (equals <= 0 || equals > 256) continue;
    const key = line.slice(0, equals).trim();
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) continue;
    if (line.length > 8192) throw Error('Mod text line exceeds 8192 characters');
    rows.set(key, key + '=' + line.slice(equals + 1).replace(/\s+$/, ''));
  }
  return Array.from(rows, ([key, line]) => ({key, line}));
}

// ARZ v3 header and record table: count plus evenly spread sample names.
function tqArzSummary(buffer, limit) {
  const view = new DataView(buffer), size = buffer.byteLength;
  if (size < 24 || view.getUint16(0, true) !== 4 || view.getUint16(2, true) !== 3) throw Error('Unsupported ARZ header');
  const recordStart = view.getUint32(4, true), recordSize = view.getUint32(8, true), count = view.getUint32(12, true);
  const stringStart = view.getUint32(16, true), stringSize = view.getUint32(20, true);
  if (recordStart + recordSize > size || stringStart + stringSize > size || count < 1 || count > 4000000) throw Error('Damaged ARZ tables');
  const strings = []; let p = stringStart + 4;
  const stringCount = view.getUint32(stringStart, true);
  if (stringCount > 8000000) throw Error('Damaged ARZ strings');
  for (let i = 0; i < stringCount; i++) {
    const n = view.getUint32(p, true); p += 4;
    if (p + n > stringStart + stringSize) throw Error('Damaged ARZ strings');
    strings.push(p); strings.push(n); p += n;
  }
  const want = new Set();
  const step = Math.max(1, Math.floor(count / limit));
  for (let i = 0; i < count && want.size < limit; i += step) want.add(i);
  const names = []; p = recordStart;
  const bytes = new Uint8Array(buffer);
  for (let i = 0; i < count; i++) {
    if (p + 8 > recordStart + recordSize) throw Error('Damaged ARZ records');
    const index = view.getUint32(p, true), typeLength = view.getUint32(p + 4, true);
    p += 8 + typeLength + 16;
    if (!want.has(i)) continue;
    if (index * 2 >= strings.length) throw Error('Damaged ARZ record name');
    let name = '';
    const at = strings[index * 2], n = strings[index * 2 + 1];
    for (let k = 0; k < n; k++) name += String.fromCharCode(bytes[at + k]);
    names.push(name);
  }
  return {count, names};
}

function tqActivateGenericMod(engine, archive, player, plan, gate, onReport) {
  const report = {schema: 1, kind: 'content', status: 'preflight', mounts: [], text: null};
  const save = () => onReport(report);
  const held = []; globalThis.__tqGenericHeld = held;
  if (!player.isNull() || archive.isNull()) throw Error('Requires menu without player');
  const F = (n, r, a) => new NativeFunction(gate.address(engine, n), r, a);
  const assign = F('_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6assignEPKc', 'pointer', ['pointer', 'pointer']);
  const str = s => { const p = Memory.alloc(24), t = Memory.allocUtf8String(s); p.writeByteArray(new Uint8Array(24)); assign(p, t); held.push(p, t); return p; };
  const index = F('_ZN4GAME15DatabaseArchive24GetRecordIndexFromStringERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE', 'int', ['pointer', 'pointer']);

  const data = File.readAllBytes(plan.database);
  const summary = tqArzSummary(data, 16);
  report.records = summary.count;

  if (plan.resources.length) {
    const fs = F('_ZN4GAME6Engine13GetFileSystemEv', 'pointer', ['pointer'])(engine.getExportByName('_ZN4GAME7gEngineE').readPointer());
    if (fs.isNull()) throw Error('No FileSystem');
    // Group 0 is searched before the stock archives; the mod must be its only user.
    if (fs.add(16).readPointer().sub(fs.add(8).readPointer()).toInt32() !== 0) throw Error('Resource group 0 occupied');
    const add = F('_ZN4GAME10FileSystem16AddSourceArchiveERKNS0_22AddSourceArchiveParamsE', 'int', ['pointer', 'pointer']);
    for (const item of plan.resources) {
      const options = Memory.alloc(0x30); held.push(options);
      options.add(8).writePointer(str(item.path));
      const result = add(fs, options);
      report.mounts.push({name: item.path.slice(plan.root.length), result}); save();
      if (result !== 1) throw Error('Could not open mod resource ' + item.path.slice(plan.root.length));
    }
    report.text = tqRegisterModText(engine, fs, gate, held);
  }

  report.status = 'loading-database'; save();
  const buffer = Memory.alloc(data.byteLength); buffer.writeByteArray(data); held.push(buffer);
  F('_ZN4GAME15DatabaseArchive11DeserializeEPKvjb', 'void', ['pointer', 'pointer', 'uint', 'bool'])(archive, buffer, data.byteLength, 0);
  const missing = summary.names.filter(name => index(archive, str(name)) < 0);
  report.sampled = summary.names.length; report.missing = missing.length;
  if (missing.length) throw Error('Mod records not found after loading: ' + missing[0]);
  report.status = 'content-loaded'; save();
  return report;
}

function tqRegisterModText(engine, fs, gate, held) {
  const F = (n, r, a) => new NativeFunction(gate.address(engine, n), r, a);
  const open = F('_ZN4GAME10FileSystem8OpenFileEPKcNS_8FileModeE', 'pointer', ['pointer', 'pointer', 'int']);
  const close = F('_ZN4GAME10FileSystem9CloseFileERPNS_4FileE', 'void', ['pointer', 'pointer']);
  const length = F('_ZNK4GAME11FileArchive9GetLengthEv', 'uint', ['pointer']);
  const lock = F('_ZN4GAME11FileArchive4LockEv', 'pointer', ['pointer']);
  const unlock = F('_ZN4GAME11FileArchive6UnlockEv', 'void', ['pointer']);
  let file = ptr(0), used = null;
  for (const name of ['Text\\modstrings.txt', 'Text\\modStrings.txt', 'Text/modstrings.txt']) {
    file = open(fs, Memory.allocUtf8String(name), 0);
    if (!file.isNull()) { used = name; break; }
  }
  if (file.isNull()) return {status: 'none'};
  let text;
  try {
    const n = length(file);
    if (n > 4 * 1024 * 1024) throw Error('Mod text exceeds 4 MiB');
    const p = lock(file);
    if (p.isNull()) throw Error('Mod text lock failed');
    try { text = tqDecodeModText(p.readByteArray(n)); } finally { unlock(file); }
  } finally { const pp = Memory.alloc(8); pp.writePointer(file); close(fs, pp); }
  const rows = tqParseModText(text);
  tqAddModTextRows(engine, gate, held, rows);
  return {status: 'registered', file: used, tags: rows.length};
}

function tqAddModTextRows(engine, gate, held, rows) {
  const F = (n, r, a) => new NativeFunction(gate.address(engine, n), r, a);
  const loc = engine.getExportByName('_ZN4GAME19LocalizationManager10m_InstanceE').readPointer();
  if (loc.isNull()) throw Error('Missing localization');
  const assignWide = F('_ZNSt6__ndk112basic_stringIwNS_11char_traitsIwEENS_9allocatorIwEEE6assignEPKw', 'pointer', ['pointer', 'pointer']);
  const destroyWide = F('_ZNSt6__ndk112basic_stringIwNS_11char_traitsIwEENS_9allocatorIwEEED2Ev', 'void', ['pointer']);
  const addWide = F('_ZN4GAME19LocalizationManager11AddTagToMapERNSt6__ndk112basic_stringIwNS1_11char_traitsIwEENS1_9allocatorIwEEEEPKcb', 'void', ['pointer', 'pointer', 'pointer', 'bool']);
  const has = F('_ZN4GAME19LocalizationManager15HasLocalizationEPKc', 'bool', ['pointer', 'pointer']);
  const source = Memory.allocUtf8String('Text/modstrings.txt'); held.push(source);
  const w = Memory.alloc(24);
  for (const row of rows) {
    const units = Array.from(row.line, c => c.codePointAt(0));
    const wide = Memory.alloc((units.length + 1) * 4);
    units.forEach((c, i) => wide.add(i * 4).writeU32(c));
    w.writeByteArray(new Uint8Array(24));
    assignWide(w, wide);
    try { addWide(loc, w, source, 1); } finally { destroyWide(w); }
  }
  const registered = rows.length ? has(loc, Memory.allocUtf8String(rows[0].key)) : true;
  if (!registered) throw Error('Mod text not registered');
}

// Reads Text.arc/modstrings.txt of one mod directly: in the shared file system the first mod's file hides the others.
function tqReadModTextFromArc(engine, gate, path) {
  const F = (n, r, a) => new NativeFunction(gate.address(engine, n), r, a);
  const ctor = F('_ZN4GAME7ArchiveC2Ev', 'void', ['pointer']), dtor = F('_ZN4GAME7ArchiveD2Ev', 'void', ['pointer']);
  const open = F('_ZN4GAME7Archive4OpenEPKcNS0_8FileModeEb', 'int', ['pointer', 'pointer', 'int', 'bool']);
  const find = F('_ZNK4GAME7Archive8FindFileEPKc', 'int', ['pointer', 'pointer']);
  const entry = F('_ZNK4GAME7Archive12GetFileEntryEi', 'pointer', ['pointer', 'int']);
  const extract = F('_ZNK4GAME7Archive19ExtractFileToMemoryEiPhj', 'int', ['pointer', 'int', 'pointer', 'uint']);
  const arc = Memory.alloc(0x200);
  ctor(arc);
  try {
    if (!open(arc, Memory.allocUtf8String(path), 1, 0)) throw Error('Could not open mod text archive');
    for (const name of ['modstrings.txt', 'modStrings.txt', 'ModStrings.txt']) {
      const i = find(arc, Memory.allocUtf8String(name));
      if (i < 0) continue;
      const size = entry(arc, i).add(12).readU32();
      if (size > 4 * 1024 * 1024) throw Error('Mod text exceeds 4 MiB');
      const buffer = Memory.alloc(Math.max(1, size));
      if (size && !extract(arc, i, buffer, size)) throw Error('Could not read mod text');
      return tqDecodeModText(buffer.readByteArray(size));
    }
    return null;
  } finally { dtor(arc); }
}

// Several content mods. The first mod in the list wins: its archives are mounted first (the first
// source wins a lookup), its database is applied last and its text tags are registered last.
function tqActivateGenericMods(engine, archive, player, plans, gate, onReport) {
  const report = {schema: 1, kind: 'combination', status: 'preflight', mods: plans.map(p => ({name: p.name, mounts: []}))};
  const save = () => onReport(report);
  const held = []; globalThis.__tqGenericHeld = held;
  if (!player.isNull() || archive.isNull()) throw Error('Requires menu without player');
  if (plans.length < 2 || plans.length > 8) throw Error('A combination needs 2 to 8 mods');
  const F = (n, r, a) => new NativeFunction(gate.address(engine, n), r, a);
  const assign = F('_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6assignEPKc', 'pointer', ['pointer', 'pointer']);
  const str = s => { const p = Memory.alloc(24), t = Memory.allocUtf8String(s); p.writeByteArray(new Uint8Array(24)); assign(p, t); held.push(p, t); return p; };
  const index = F('_ZN4GAME15DatabaseArchive24GetRecordIndexFromStringERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE', 'int', ['pointer', 'pointer']);
  const data = plans.map(p => File.readAllBytes(p.database));
  const summaries = data.map(d => tqArzSummary(d, 16));
  summaries.forEach((s, i) => { report.mods[i].records = s.count; });

  const fs = F('_ZN4GAME6Engine13GetFileSystemEv', 'pointer', ['pointer'])(engine.getExportByName('_ZN4GAME7gEngineE').readPointer());
  if (fs.isNull()) throw Error('No FileSystem');
  if (fs.add(16).readPointer().sub(fs.add(8).readPointer()).toInt32() !== 0) throw Error('Resource group 0 occupied');
  const add = F('_ZN4GAME10FileSystem16AddSourceArchiveERKNS0_22AddSourceArchiveParamsE', 'int', ['pointer', 'pointer']);
  plans.forEach((plan, i) => {
    for (const item of plan.resources) {
      const options = Memory.alloc(0x30); held.push(options);
      options.add(8).writePointer(str(item.path));
      const result = add(fs, options);
      report.mods[i].mounts.push({name: item.path.slice(plan.root.length), result}); save();
      if (result !== 1) throw Error('Could not open mod resource ' + item.path.slice(plan.root.length));
    }
  });
  for (let i = plans.length - 1; i >= 0; i--) {
    const textArc = plans[i].resources.find(r => r.path.slice(r.path.lastIndexOf('/') + 1).toLowerCase() === 'text.arc');
    const text = textArc ? tqReadModTextFromArc(engine, gate, textArc.path) : null;
    if (text === null) { report.mods[i].text = {status: 'none'}; continue; }
    const rows = tqParseModText(text);
    tqAddModTextRows(engine, gate, held, rows);
    report.mods[i].text = {status: 'registered', tags: rows.length}; save();
  }

  report.status = 'loading-database'; save();
  const deserialize = F('_ZN4GAME15DatabaseArchive11DeserializeEPKvjb', 'void', ['pointer', 'pointer', 'uint', 'bool']);
  for (let i = plans.length - 1; i >= 0; i--) {
    const buffer = Memory.alloc(data[i].byteLength); buffer.writeByteArray(data[i]); held.push(buffer);
    deserialize(archive, buffer, data[i].byteLength, 0);
  }
  // Every mod's records must still be there after the others were applied.
  summaries.forEach((s, i) => {
    const missing = s.names.filter(name => index(archive, str(name)) < 0);
    report.mods[i].sampled = s.names.length; report.mods[i].missing = missing.length;
    if (missing.length) throw Error('Mod records not found after loading: ' + missing[0]);
  });
  report.status = 'content-loaded'; save();
  return report;
}
