"""Build the installable Magisk zip: module scripts + loader.js (built from android/bootstrap) + frida-inject.

  python magisk/build.py                      # downloads frida-inject from the official Frida GitHub release
  python magisk/build.py --injector path/to/frida-inject-17.22.1-android-arm64

Tested with Frida 17.22.1. The output contains no game code or data.
"""
import argparse, json, lzma, pathlib, urllib.request, zipfile

FRIDA = '17.22.1'
URL = f'https://github.com/frida/frida/releases/download/{FRIDA}/frida-inject-{FRIDA}-android-arm64.xz'
HERE = pathlib.Path(__file__).resolve().parent
BOOT = HERE.parent / 'android' / 'bootstrap'
MODULE = HERE / 'module'
FILES = ['module.prop', 'customize.sh', 'service.sh', 'tqmods.sh', 'action.sh', 'skip_mount',
         'META-INF/com/google/android/update-binary', 'META-INF/com/google/android/updater-script']
# Order matters: helpers first, the orchestrator (which sets rpc.exports.init) last.
SCRIPTS = ['resolve-selection.js', 'lab-database-probe.js', 'plan-save-path.js', 'resolve-save-profile.js',
           'magisk-request.js', 'preflight-native-api.js', 'save-profile-runtime.js', 'local-only-cloud.js',
           'observe-engine-startup.js', 'gladiators-map-runtime.js', 'map-navigation-runtime.js', 'generic-mod-runtime.js', 'more-enemies.js', 'profile-loader.js']
GAME = '/data/user/0/com.hg.titanquestultimate/files'
ALIAS = '/data/data/com.hg.titanquestultimate/files'


def loader():
    params = {
        'mode': 'magisk',
        'report': GAME + '/bootstrap-status.json',
        'selectionRequest': GAME + '/tqmods/request.json',
        'libraryRoot': [GAME + '/tqmods/library', ALIAS + '/tqmods/library'],
        'profilesRoot': [GAME + '/tqmods/save-profiles', ALIAS + '/tqmods/save-profiles'],
        'liveSaveRoot': '/storage/emulated/0/Android/data/com.hg.titanquestultimate/files/Preferences/SaveData/',
    }
    for key, name in [('expectedApi', 'api-5364.json'), ('saveApi', 'save-profile-api-5364.json'),
                      ('cloudApi', 'cloud-policy-api-5364.json'), ('mapApi', 'map-api-5364.json'), ('navApi', 'nav-api-5364.json'),
                      ('gladiators', 'gladiators-runtime-5364.json')]:
        params[key] = json.loads((BOOT / name).read_text(encoding='utf-8'))
    params['profiles'] = (json.loads((BOOT / 'profiles-5364.json').read_text(encoding='utf-8'))
                          + json.loads((BOOT / 'map-profiles-5364.json').read_text(encoding='utf-8')))
    head = ('// Titan Quest Mods loader (Magisk). Built from android/bootstrap; contains no game code.\n'
            'const TQ_STATIC = ' + json.dumps(params, separators=(',', ':')) + ';\n'
            '// Scripts hosted in the same injection (Perf, add-ons) register a start function here.\n'
            'globalThis.tqStartPerf = function (stage) {\n'
            '  return Promise.all((globalThis.__tqHosted || []).map(function (start) { return start(stage); }));\n'
            '};\n')
    body = '\n'.join((BOOT / n).read_text(encoding='utf-8') for n in SCRIPTS)
    tail = ('\nconst tqLoaderInit = rpc.exports.init;\n'
            'rpc.exports.init = function (stage, p) {\n'
            '  return tqLoaderInit(stage, Object.assign({}, TQ_STATIC, p || {}, {mode: \'magisk\'}));\n'
            '};\n')
    return head + '(function(){\n' + body + tail + '})();\n'


ap = argparse.ArgumentParser()
ap.add_argument('--injector', help='existing frida-inject binary for android-arm64')
ap.add_argument('--out', default=str(HERE.parent / 'dist' / 'tq-mods-magisk-1.5.zip'))
a = ap.parse_args()
inj = pathlib.Path(a.injector).read_bytes() if a.injector else lzma.decompress(urllib.request.urlopen(URL).read())
out = pathlib.Path(a.out)
out.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    for f in FILES:
        data = (MODULE / f).read_bytes()
        if f != 'skip_mount':  # Every module file is text; Android needs LF line endings.
            data = data.replace(b'\r\n', b'\n')
        z.writestr(f, data)
    z.writestr('loader.js', loader())
    z.writestr('injector', inj)
print('built', out)
