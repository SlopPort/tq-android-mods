"""Build an unsigned standalone importer APK from project source only.

No game inputs, network downloads, root access or Gadget dependency.
Sign locally before installing. Output directory must not already exist.
"""
import argparse
import pathlib
import subprocess
import zipfile
import shutil

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--jdk', required=True, type=pathlib.Path)
p.add_argument('--sdk', required=True, type=pathlib.Path)
p.add_argument('--build-tools', default='35.0.0')
p.add_argument('--platform', default='android-36')
p.add_argument('--out', required=True, type=pathlib.Path)
p.add_argument('--with-bootstrap', action='store_true', help='Include original bootstrap activity classes for local embedding; no native library is bundled')
p.add_argument('--manager', action='store_true', help='Build the distributable Titan Quest Mods app (package org.tqmods.manager) for the Magisk module')
a = p.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
suffix = '.exe' if __import__('os').name == 'nt' else ''
javac = a.jdk / 'bin' / ('javac' + suffix)
java = a.jdk / 'bin' / ('java' + suffix)
bt = a.sdk / 'build-tools' / a.build_tools
android = a.sdk / 'platforms' / a.platform / 'android.jar'
for file in (javac, java, android, bt / 'lib/d8.jar', bt / ('aapt' + suffix), bt / ('zipalign' + suffix)):
    if not file.is_file():
        p.error('Missing tool/input: ' + str(file))
a.out.mkdir(parents=True, exist_ok=False)
classes, dex = a.out / 'classes', a.out / 'dex'
classes.mkdir(); dex.mkdir()
# Use a workspace copy: some Windows JDK ZIP providers cannot canonicalize the SDK path.
shutil.copyfile(android, a.out / 'android.jar')
android = a.out / 'android.jar'
manifest = a.out / 'AndroidManifest.xml'
package, label, version, code = (('org.tqmods.manager', 'Titan Quest Mods', '1.1', 3) if a.manager
                                 else ('org.tqmods.importtest', 'TQ Mods Import Test', '0.1', 1))
icon = ' android:icon="@drawable/icon"' if a.manager else ''
manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
 package="{package}" android:versionCode="{code}" android:versionName="{version}">
 <uses-sdk android:minSdkVersion="28" android:targetSdkVersion="36"/>
 <queries><package android:name="com.hg.titanquestultimate"/></queries>
 <application android:label="{label}"{icon} android:allowBackup="false"
 android:theme="@android:style/Theme.Material.NoActionBar">
 <activity android:name="org.tqmods.ModManagerActivity" android:exported="true">
 <intent-filter><action android:name="android.intent.action.MAIN"/>
 <category android:name="android.intent.category.LAUNCHER"/></intent-filter>
 </activity></application></manifest>''', encoding='utf-8')
def run(*cmd):
    result = subprocess.run([str(x) for x in cmd], check=True, capture_output=True, text=True)
    if result.stdout: print(result.stdout, end='')
    if result.stderr: print(result.stderr, end='')
    if 'An exception has occurred in the compiler' in result.stderr:
        raise RuntimeError('Compiler reported an internal failure despite its exit status')
sources = sorted((root / 'android/src/org/tqmods').glob('*.java'))
if a.with_bootstrap:
    sources += sorted((root / 'android/bootstrap').glob('*.java'))
run(javac, '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', android,
    '-d', classes, *sources)
jar = a.out / 'classes.jar'
with zipfile.ZipFile(jar, 'w') as z:
    for file in classes.rglob('*.class'):
        z.write(file, file.relative_to(classes).as_posix())
run(java, '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '28',
    '--lib', android, '--output', dex, jar)
unsigned = a.out / 'unsigned.apk'
res = ['-S', root / 'android/manager/res'] if a.manager else []
run(bt / ('aapt' + suffix), 'package', '-f', '-M', manifest, *res, '-I', android, '-F', unsigned)
with zipfile.ZipFile(unsigned, 'a') as z:
    for file in dex.glob('*.dex'):
        z.write(file, file.name)
aligned = a.out / ('titan-quest-mods-unsigned.apk' if a.manager else 'import-test-unsigned.apk')
run(bt / ('zipalign' + suffix), '-f', '4', unsigned, aligned)
run(bt / ('zipalign' + suffix), '-c', '4', aligned)
print('Unsigned standalone test APK:', aligned)
print('Not a game loader. Sign locally; do not reuse a production signing key for experiments.')
