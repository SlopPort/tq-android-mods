"""Builds the synthetic 7z test archives in android/tests/fixtures/7z with 7-Zip.

The content is generated here (no game or mod files). Writes expected.txt (SHA-256 of
every source file) and SHA256SUMS (the archives; the publication guard accepts only these).
Usage: python tools/make-7z-fixtures.py [path to 7z.exe or 7z]
"""
import hashlib, os, pathlib, random, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'android/tests/fixtures/7z'
SEVEN = sys.argv[1] if len(sys.argv) > 1 else shutil.which('7z') or r'C:\Program Files\7-Zip\7z.exe'
ARCHIVES = [
    ('lzma2-ultra.7z', ['MyMod', '-mx9']),
    ('lzma-nonsolid.7z', ['MyMod', '-m0=LZMA', '-ms=off']),
    ('copy-plainheader.7z', ['MyMod', '-mx0', '-mhc=off', '-xr!Items.arc']),
    ('deflate.7z', ['MyMod', '-m0=Deflate']),
    ('lzma2-fast-mt.7z', ['MyMod', '-mx1', '-mmt=4', '-m0=LZMA2:d=64k']),
    ('lzma2-random.7z', ['MyMod/database', '-m0=LZMA2', '-ms=off']),
    ('refuse-encrypted.7z', ['MyMod/Readme.txt', '-psecret', '-mhe=off']),
    ('refuse-bzip2.7z', ['MyMod/Readme.txt', '-m0=BZip2']),
]

def write(root, rel, data):
    path = root / 'MyMod' / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)

def main():
    r = random.Random(7)
    with tempfile.TemporaryDirectory() as tmp:
        src = pathlib.Path(tmp)
        write(src, 'database/MyMod.arz', bytes([4, 0, 3, 0]) + bytes(r.randrange(256) for _ in range(12000)))
        write(src, 'resources/Items.arc', b''.join(b'record value line\r\n' * 200 + b'block %d\r\n' % i for i in range(700)))
        write(src, 'Readme.txt', 'Zażółć gęślą jaźń\n'.encode('utf-8') * 50)
        write(src, 'docs/empty.txt', b'')
        write(src, 'Ünïcode name/Text.txt', b'x' * 5000)
        (src / 'MyMod/emptydir').mkdir()
        OUT.mkdir(parents=True, exist_ok=True)
        for name, args in ARCHIVES:
            target = OUT / name
            if target.exists(): target.unlink()
            subprocess.run([SEVEN, 'a', '-bso0', str(target)] + args, cwd=src, check=True)
        lines = []
        for path in sorted(p for p in src.rglob('*') if p.is_file()):
            rel = path.relative_to(src).as_posix()
            lines.append(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + rel)
        (OUT / 'expected.txt').write_text('\n'.join(sorted(lines, key=lambda l: l[66:])) + '\n', encoding='utf-8', newline='\n')
    sums = [hashlib.sha256((OUT / n).read_bytes()).hexdigest() + '  ' + n for n, _ in ARCHIVES]
    (OUT / 'SHA256SUMS').write_text('\n'.join(sums) + '\n', encoding='utf-8', newline='\n')
    print('Wrote', len(ARCHIVES), 'archives to', OUT)

if __name__ == '__main__':
    main()
