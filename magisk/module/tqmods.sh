#!/system/bin/sh
# Root helper for the Titan Quest Mods app (run through su). Prints key=value lines; error=... on failure.
# It copies the chosen mod into the game's private folder, makes the mod's copy of the characters once,
# starts or stops the game and stages backups. It never changes the game app itself.
MODDIR=${0%/*}
PKG=com.hg.titanquestultimate
DATA=/data/data/$PKG
TQ=$DATA/files/tqmods
LIVE=/storage/emulated/0/Android/data/$PKG/files/Preferences/SaveData
PERF=/data/adb/modules/tq_autopatch
umask 077
# Root granted to an app runs in that app's mount namespace, where Android hides other apps' data.
# Continue in the global namespace (init's), where the game's folders are visible.
if [ ! -d "$DATA" ] && [ -z "$TQMODS_GLOBAL" ] && command -v nsenter >/dev/null; then
 export TQMODS_GLOBAL=1; exec nsenter -t 1 -m -- /system/bin/sh "$0" "$@"
fi
fail() { echo "error=$*"; exit 1; }
uuid_ok() { echo "$1" | grep -Eq '^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$'; }
running() { pidof $PKG >/dev/null; }
# The app passes its own private folder; accept only that shape.
app_dir() {
 case "$1" in /data/user/0/*/*|/data/data/*/*) ;; *) fail "unexpected folder";; esac
 case "$1" in *..*) fail "unexpected folder";; esac
 [ -d "$1" ] && [ ! -L "$1" ] || fail "folder missing"
}
own_like() { chown -R "$(stat -c %u "$2"):$(stat -c %g "$2")" "$1" && chcon -R "$(stat -c %C "$2")" "$1"; }
stop_game() {
 am force-stop $PKG
 i=0; while running && [ $i -lt 25 ]; do sleep 0.2; i=$((i+1)); done
 running && fail "game did not close"
}
[ -d "$DATA" ] || { [ "$1" = status ] || fail "Titan Quest is not installed"; }
case "$1" in
status)
 echo "version=$(sed -n 's/^version=//p' "$MODDIR/module.prop")"
 [ -d "$DATA" ] && echo "game=yes" || echo "game=no"
 echo "pid=$(pidof $PKG)"
 if [ -f "$PERF/service.sh" ] && [ ! -f "$PERF/disable" ] && [ ! -f "$PERF/remove" ]; then
  [ -f "$PERF/paused" ] && echo "perf=off" || echo "perf=on"
 else echo "perf=absent"; fi
 [ -f "$MODDIR/disable" ] && echo "blocked=The Magisk module is switched off"
 [ -f "$MODDIR/blocked" ] && echo "blocked=$(cat "$MODDIR/blocked")"
 echo "report=$(tr -d '\n' < "$DATA/files/bootstrap-status.json" 2>/dev/null)"
 # Mods and sets that have their own copy of the characters.
 p=''; for d in "$TQ"/save-profiles/*; do n=${d##*/}; uuid_ok "$n" && p="$p $n"; done
 echo "profiles=${p# }"
 ;;
deleteprofile)
 # deleteprofile <id>: delete a mod's copy of the characters; the next start makes a fresh copy.
 uuid_ok "$2" || fail "bad mod id"
 running && fail "game is running"
 [ -d "$TQ/save-profiles/$2" ] || fail "no characters copy for this mod"
 rm -rf "$TQ/save-profiles/$2" || fail "cannot delete characters copy"
 echo "ok=1"
 ;;
launch)
 # launch <app files> [restart]: request-game.json in the app folder holds the choice with game paths.
 app_dir "$2"
 if running; then [ "$3" = restart ] || fail "game is running"; stop_game; fi
 req="$2/tqmods/request-game.json"
 mkdir -p "$TQ/library" "$TQ/save-profiles"
 id=''; members=''
 [ -f "$req" ] && id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$req")
 # A combination lists its mods as "memberIds":"id,id"; the combination id only names the characters copy.
 [ -f "$req" ] && members=$(sed -n 's/.*"memberIds":"\([^"]*\)".*/\1/p' "$req" | tr ',' ' ')
 if [ -n "$id" ]; then
  uuid_ok "$id" || fail "bad mod id"
  for m in ${members:-$id}; do
   uuid_ok "$m" || fail "bad mod id"
   src="$2/tqmods/library/$m"
   [ -d "$src" ] || fail "mod files missing"
   if [ ! -d "$TQ/library/$m" ]; then
    rm -rf "$TQ/library/.copy-$m"
    cp -r "$src" "$TQ/library/.copy-$m" && mv "$TQ/library/.copy-$m" "$TQ/library/$m" || fail "cannot copy the mod"
   fi
  done
  if [ ! -d "$TQ/save-profiles/$id" ]; then
   # First start of this mod: an independent copy of the characters. Originals are only read.
   stage="$TQ/save-profiles/.creating-$id-$$"
   mkdir -p "$stage/SaveData" || fail "cannot prepare characters"
   if [ -d "$LIVE" ]; then cp -r "$LIVE/." "$stage/SaveData/" || fail "cannot copy characters"; fi
   n=$(find "$stage/SaveData" -type f | wc -l)
   printf '#Independent copy made by tq_mods\nschema=1\nid=%s\nfiles=%s\nruntimeBound=false\n' "$id" "$n" > "$stage/profile.properties"
   mv "$stage" "$TQ/save-profiles/$id" || fail "cannot save characters copy"
   echo "profileCreated=$n"
  fi
  cp "$req" "$TQ/.request.tmp" && mv "$TQ/.request.tmp" "$TQ/request.json" || fail "cannot save choice"
 else
  rm -f "$TQ/request.json"
 fi
 # Keep only the chosen mods' files in the game folder; the app keeps every imported mod.
 for d in "$TQ"/library/*; do
  n=${d##*/}; uuid_ok "$n" || continue
  case " $id $members " in *" $n "*) ;; *) rm -rf "$d";; esac
 done
 own_like "$TQ" "$DATA/files"
 rm -f "$DATA/files/bootstrap-status.json"
 # The game's own start screen (skip activities of a locally embedded test manager).
 act=$(cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $PKG \
  | grep "^ *$PKG/" | grep -v "/org\.tqmods\." | head -n 1 | tr -d ' ')
 # Optional override, one line "package/activity" (for test installations with an extra start screen).
 [ -f "$MODDIR/activity" ] && act=$(head -n 1 "$MODDIR/activity")
 case "$act" in */*) ;; *) fail "cannot find the game's start screen";; esac
 am start -n "$act" >/dev/null 2>&1 || fail "cannot start the game"
 echo "ok=1"
 ;;
stop)
 stop_game; echo "ok=1" ;;
perf)
 [ -f "$PERF/service.sh" ] || fail "TQ Android Perf is not installed"
 case "$2" in on) rm -f "$PERF/paused";; off) touch "$PERF/paused";; *) fail "on or off";; esac
 echo "ok=1" ;;
backup)
 # backup <app folder>/<staging>: copy the original characters for the app to pack.
 app_dir "${2%/*}"
 running && fail "game is running"
 [ -d "$LIVE" ] || fail "no characters found"
 rm -rf "$2"; mkdir -p "$2/SaveData" && cp -r "$LIVE/." "$2/SaveData/" || fail "cannot copy characters"
 own_like "$2" "${2%/*}"
 echo "files=$(find "$2/SaveData" -type f | wc -l)"
 ;;
restore)
 # restore <app folder>/<staging>: the app has unpacked and checked a backup into <staging>/SaveData.
 app_dir "${2%/*}"
 running && fail "game is running"
 [ -d "$2/SaveData" ] || fail "nothing to restore"
 prev="${LIVE%/*}/.SaveData-before-restore-$(date +%Y%m%d-%H%M%S)"
 if [ -d "$LIVE" ]; then mv "$LIVE" "$prev" || fail "cannot keep current characters"; fi
 if ! cp -r "$2/SaveData" "$LIVE"; then
  rm -rf "$LIVE"; [ -d "$prev" ] && mv "$prev" "$LIVE"; fail "restore failed; current characters kept"
 fi
 rm -rf "$2"
 echo "kept=${prev##*/}"
 ;;
*) fail "unknown command" ;;
esac
