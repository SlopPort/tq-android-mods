# Third-party components

- **Frida** (`frida-inject`): https://frida.re, licensed under the wxWindows Library Licence 3.1. The Magisk module zip bundles the unmodified `frida-inject` 17.22.1 binary for android-arm64 from the official release (`magisk/build.py` downloads it).
- **libarchive** 3.7.7 (https://libarchive.org, release tarball sha256 `4cc540a3e9a1eebdefa1045d2e4184831100667e6d7d5b315bb1cbc951f8ddff`): its own RAR4/RAR5 readers, unmodified, compiled into the app's `libtqrar.so` (arm64-v8a) by `tools/build-rar-native.py`. BSD 2-Clause; full text in [licenses/libarchive-COPYING.txt](licenses/libarchive-COPYING.txt). No unRAR source is used.
- **Magisk module installer** (`magisk/module/META-INF/com/google/android/update-binary`): the standard module installer script from the Magisk documentation.
- **Titan Quest** is a trademark of THQ Nordic. This is an unofficial, non-commercial fan project, not affiliated with or endorsed by THQ Nordic or HandyGames. It contains no game files, game code or assets; the loader works in memory on the player's own installed copy.
- Mods belong to their authors. The app imports files the player supplies; nothing here redistributes them.
