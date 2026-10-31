"""Conservative publication hygiene; not a copyright or legal compliance verdict."""
import hashlib
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
DENIED = {'.apk', '.dex', '.so', '.arz', '.arc', '.map', '.obb', '.class',
          '.jar', '.jks', '.keystore', '.p12', '.pem', '.zip', '.tar', '.7z',
          '.rar', '.ogg', '.mp3', '.dds', '.tex'}

# Synthetic 7z test archives made by tools/make-7z-fixtures.py; only the listed hashes pass.
FIXTURES = pathlib.Path('android/tests/fixtures/7z')

def fixture_hashes():
    sums = ROOT / FIXTURES / 'SHA256SUMS'
    if not sums.is_file():
        return set()
    return {line.split()[0] for line in sums.read_text(encoding='utf-8').splitlines() if line.strip()}

def violations(path, data, fixtures=frozenset()):
    issues = []
    if (path.parent == FIXTURES and path.suffix.lower() == '.7z' and len(data) < 64 * 1024
            and hashlib.sha256(data).hexdigest() in fixtures):
        return issues
    if path.suffix.lower() in DENIED:
        issues.append('binary/archive/key extension')
    if any(p.lower() in {'savedata', 'local-inputs', 'local-output'} for p in path.parts):
        issues.append('private-data directory')
    if len(data) > 1024 * 1024:
        issues.append('over 1 MiB: manual review required')
    if b'\x00' in data:
        issues.append('binary content')
    if any(line.strip() in {b'-----BEGIN PRIVATE KEY-----', b'-----BEGIN RSA PRIVATE KEY-----'} for line in data.splitlines()):
        issues.append('private key content')
    return issues

def main():
    if '--self-test' in sys.argv:
        assert violations(pathlib.Path('game.apk'), b'example')
        assert violations(pathlib.Path('notes.txt'), b'\x00ELF')
        assert violations(pathlib.Path('SaveData/hero.txt'), b'save')
        assert not violations(pathlib.Path('src/Example.java'), b'class Example {}')
        assert violations(FIXTURES / 'mod.7z', b'7z-archive', set())
        assert not violations(FIXTURES / 'mod.7z', b'7z', {hashlib.sha256(b'7z').hexdigest()})
        assert violations(pathlib.Path('mods/mod.7z'), b'7z', {hashlib.sha256(b'7z').hexdigest()})
        print('PASS: publication guard self-test')
        return 0
    result = subprocess.run(['git', '-c', 'safe.directory=' + ROOT.as_posix(),
        '-C', str(ROOT), 'ls-files', '--cached', '--others', '--exclude-standard', '-z'],
        capture_output=True, check=True)
    failed = False
    fixtures = fixture_hashes()
    names = sorted(set(result.stdout.decode('utf-8').split('\0')) - {''})
    for name in names:
        path = ROOT / name
        if path.is_symlink():
            print('BLOCK:', name, 'symlink requires review'); failed = True; continue
        if not path.exists():
            continue
        if path.stat().st_size > 1024 * 1024:
            print('BLOCK:', name, 'over 1 MiB: manual review required'); failed = True; continue
        issues = violations(pathlib.Path(name), path.read_bytes(), fixtures)
        if issues:
            print('BLOCK:', name, '; '.join(issues)); failed = True
    print(('FAIL' if failed else 'PASS') + ': checked %d publication candidate paths; not a legal audit' % len(names))
    return int(failed)

if __name__ == '__main__':
    sys.exit(main())
