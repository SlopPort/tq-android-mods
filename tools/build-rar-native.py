"""Builds android/lib/arm64-v8a/libtqrar.so: the JNI wrapper plus libarchive's RAR readers.

Usage: python tools/build-rar-native.py --ndk <NDK dir> --libarchive <unpacked libarchive-3.7.7>
libarchive is BSD-2-Clause (see NOTICE-libarchive.txt); no unRAR code is used."""
import argparse, glob, os, pathlib, subprocess, sys
p = argparse.ArgumentParser(); p.add_argument('--ndk', required=True); p.add_argument('--libarchive', required=True)
a = p.parse_args()
here = pathlib.Path(__file__).resolve().parent.parent
la = pathlib.Path(a.libarchive)
host = 'windows-x86_64' if os.name == 'nt' else ('darwin-x86_64' if sys.platform == 'darwin' else 'linux-x86_64')
bin_ = pathlib.Path(a.ndk) / 'toolchains/llvm/prebuilt' / host / 'bin'
cc = str(bin_ / ('aarch64-linux-android28-clang' + ('.cmd' if os.name == 'nt' else '')))
ar = str(bin_ / ('llvm-ar' + ('.exe' if os.name == 'nt' else '')))
obj = here / 'build/rar-native'; obj.mkdir(parents=True, exist_ok=True)
flags = ['-O2', '-fPIC', '-ffunction-sections', '-fdata-sections', '-w',
         '-DPLATFORM_CONFIG_H="' + (la / 'contrib/android/config/android.h').as_posix() + '"',
         '-I', str(la / 'libarchive'), '-I', str(la / 'contrib/android/include')]
objects = []
for src in sorted(glob.glob(str(la / 'libarchive/*.c'))):
    name = os.path.basename(src)
    if 'windows' in name or name.startswith('test'): continue
    out = obj / (name + '.o')
    if subprocess.run([cc, *flags, '-c', src, '-o', str(out)]).returncode == 0: objects.append(str(out))
    else: print('skipped', name)
lib = obj / 'libarchive.a'
if lib.exists(): lib.unlink()
subprocess.run([ar, 'rcs', str(lib), *objects], check=True)
dest = here / 'android/lib/arm64-v8a'; dest.mkdir(parents=True, exist_ok=True)
subprocess.run([cc, *flags, '-shared', '-o', str(dest / 'libtqrar.so'), str(here / 'android/native/tqrar.c'), str(lib),
                '-Wl,--gc-sections', '-Wl,-s', '-Wl,--no-undefined', '-lz', '-llog'], check=True)
print('Built', dest / 'libtqrar.so', (dest / 'libtqrar.so').stat().st_size, 'bytes')
