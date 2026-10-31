package org.tqmods;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.*;

/** Two builds: embedded in a locally prepared game APK (shares the game's UID), or the standalone
 * "Titan Quest Mods" app that reaches the unmodified game through the tq_mods Magisk module. */
public final class ModManagerActivity extends Activity {
    static final int BG = 0xff15120e, CARD = 0xff241f18, CARD_ON = 0xff3a2f1c, GOLD = 0xffd9a441,
        TEXT = 0xfff2ebdd, MUTED = 0xffb5a991, GREEN = 0xff3e8e41, WARN = 0xffe0a030, ERROR = 0xffe06a50;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final int ASK_NOTIFICATIONS = 7;
    private boolean restartAfterAsking;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private ModStore store;
    private ImportSnapshot pending;
    private static boolean selectionPending, importPending;
    private boolean ownsOperation, showSettings;
    private String message; private int messageColor = MUTED;
    private LinearLayout root;
    private ScrollView scroll;
    private boolean embedded;
    private java.util.Map<String, String> link; // Last status from the Magisk module (standalone app only).
    private String linkProblem;
    private static final String ROOT_INTRO = "root-intro";
    private final Runnable waitForOperation = new Runnable() {
        public void run() {
            if (isDestroyed()) return;
            render();
            if (isBusy()) ui.postDelayed(this, 300);
        }
    };
    public static boolean isBusy() { return selectionPending || importPending; }

    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll = new ScrollView(this); scroll.setBackgroundColor(BG); scroll.setFillViewport(true);
        scroll.addView(root); setContentView(scroll);
        embedded = getPackageName().equals(RootLink.GAME);
        try { store = new ModStore(new File(getFilesDir(), "tqmods/library")); }
        catch (IOException e) { say(Texts.importFailed(e.getMessage()), ERROR); }
        render();
    }
    protected void onResume() { super.onResume(); waitForOperation.run(); refresh(); }
    protected void onPause() { ui.removeCallbacks(waitForOperation); super.onPause(); }

    // ---- State -------------------------------------------------------------------------------
    /** Standalone app: ask the Magisk module what the game is doing. */
    private void refresh() {
        if (embedded) return;
        // Magisk turns an unanswered prompt into a lasting refusal, so ask only after the player taps the button.
        if (!new File(getFilesDir(), "root-asked").exists()) { link = null; linkProblem = ROOT_INTRO; render(); return; }
        io.execute(() -> {
            java.util.Map<String, String> status = null; String problem = null;
            try {
                status = RootLink.run("status");
                if (status.containsKey("missing")) problem = Texts.noModule();
                else if ("no".equals(status.get("game"))) problem = Texts.noGame();
            } catch (RootLink.NoRoot none) { problem = Texts.noRoot(); }
            catch (IOException error) { problem = Texts.noModule() + " (" + error.getMessage() + ")"; }
            final java.util.Map<String, String> s = status; final String p = problem;
            runOnUiThread(() -> { link = s; linkProblem = p; if (!isDestroyed()) render(); });
        });
    }
    /** Runs a module command off the UI thread, then refreshes the status. */
    private void rootTask(String done, String... args) {
        background(() -> {
            try { RootLink.run(args); done(done, GREEN); }
            catch (Exception error) { done(Texts.startFailed(error.getMessage()), ERROR); }
            refresh();
        });
    }
    private String gamePid() {
        String pid = link == null ? null : link.get("pid");
        return pid == null || pid.trim().isEmpty() ? null : pid.trim();
    }
    private boolean engineLoaded() {
        if (!embedded) return gamePid() != null;
        try (BufferedReader maps = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = maps.readLine()) != null) if (line.contains("libtq.so")) return true;
        } catch (IOException ignored) { return true; } // Unknown: behave as if the game runs.
        return false;
    }
    private String selectedId() {
        try {
            JSONObject request = new JSONObject(new String(new AtomicFile(new File(getFilesDir(), "tqmods/request.json")).readFully(), "UTF-8"));
            return request.isNull("id") ? null : request.getString("id");
        } catch (Exception absent) { return null; }
    }
    private JSONObject report() {
        if (!embedded) {
            try { String r = link == null ? null : link.get("report"); return r == null || r.isEmpty() ? null : new JSONObject(r); }
            catch (Exception broken) { return null; }
        }
        try { return new JSONObject(new String(new AtomicFile(new File(getFilesDir(), "bootstrap-status.json")).readFully(), "UTF-8")); }
        catch (Exception absent) { return null; }
    }
    private File entry(String id) { return new File(getFilesDir(), "tqmods/library/" + id); }
    private String name(String id) { return id == null ? Texts.vanilla() : ModStore.title(entry(id)); }
    private static boolean same(String a, String b) { return a == null ? b == null : a.equals(b); }

    // ---- Layout ------------------------------------------------------------------------------
    private void render() {
        if (root == null) return;
        root.removeAllViews();
        boolean running = engineLoaded(), busy = isBusy() || store == null;
        // Without root or the module, mods can still be added; only playing needs the module.
        boolean noLink = !embedded && (link == null || linkProblem != null);
        String selected = selectedId();
        if (selected != null && !entry(selected).isDirectory()) selected = null;
        JSONObject report = report();
        boolean current = report != null && (embedded ? report.optInt("pid") == android.os.Process.myPid()
            : String.valueOf(report.optInt("pid")).equals(gamePid()));
        String active = null;
        if (current) {
            JSONObject menu = report.optJSONObject("menuProbe");
            JSONObject choice = menu == null ? null : menu.optJSONObject("selection");
            if (choice != null && !choice.isNull("id")) active = choice.optString("id", null);
        }

        text(Texts.title(), 26, GOLD, true);
        text(Texts.subtitle(), 15, MUTED, false).setPadding(0, dp(2), 0, dp(14));

        // What is happening now, in one sentence.
        if (!embedded && (ROOT_INTRO.equals(linkProblem) || Texts.noRoot().equals(linkProblem))) {
            banner(ROOT_INTRO.equals(linkProblem) ? Texts.rootIntro() : Texts.noRoot(), WARN, null);
            Button allow = button(Texts.allowRoot(), GOLD, BG, 18);
            allow.setOnClickListener(v -> {
                try { new File(getFilesDir(), "root-asked").createNewFile(); } catch (IOException ignored) { }
                linkProblem = null; refresh();
            });
            root.addView(allow, wide(dp(60), 0, dp(12)));
        } else if (!embedded && linkProblem != null) banner(linkProblem, ERROR, null);
        else if (!embedded && link != null && link.containsKey("blocked")) banner(Texts.blocked(link.get("blocked")), WARN, null);
        if (running && current) {
            String status = report.optString("status");
            if (status.equals("refused")) banner(Texts.stateFailed(Texts.explain(report.optString("error"))), ERROR, report.optString("error"));
            else if (active == null) banner(Texts.stateRunningVanilla(), GREEN, null);
            else if (report.optBoolean("modsActivated")) banner(Texts.stateRunning(name(active)), GREEN, null);
            else banner(Texts.stateWaiting(name(active)), WARN, null);
        } else if (!running && report != null && report.optString("status").equals("refused")) {
            banner(Texts.lastFailed(Texts.explain(report.optString("error"))), WARN, report.optString("error"));
        }
        if (message != null) banner(message, messageColor, null);

        // The one big button.
        String playLabel;
        if (busy || (noLink && link == null && linkProblem == null)) playLabel = Texts.working();
        else if (running && same(selected, active)) playLabel = Texts.returnToGame();
        else if (running) playLabel = Texts.restartWith(name(selected));
        else playLabel = Texts.play(name(selected));
        Button play = button(playLabel, GREEN, Color.WHITE, 20);
        play.setEnabled(!busy && !noLink); play.setAlpha(play.isEnabled() ? 1f : 0.45f);
        final String target = selected; final String now = active;
        play.setOnClickListener(v -> onPlay(target, now, running));
        root.addView(play, wide(dp(68), dp(4), dp(20)));

        text(Texts.chooseHeader(), 18, TEXT, true).setPadding(0, dp(4), 0, dp(8));
        modCard(null, Texts.vanilla(), Texts.vanillaHint(), null, selected == null, busy);
        File[] mods = store == null ? new File[0] : store.installed();
        java.util.Arrays.sort(mods, (a, b) -> ModStore.title(a).compareToIgnoreCase(ModStore.title(b)));
        for (File dir : mods) {
            String kind = ModKind.of(store, dir);
            String badge = kind.equals(ModKind.MAP) ? Texts.badgeMap()
                : kind.equals(ModKind.TESTED) || kind.equals(ModKind.MAP_SUPPORTED) ? Texts.badgeTested() : Texts.badgeExperimental();
            String hint = kind.equals(ModKind.MAP) ? Texts.hintMap() : kind.equals(ModKind.MAP_SUPPORTED) ? Texts.hintMapSupported() : Texts.hintContent();
            modCard(dir.getName(), ModStore.title(dir), hint + " " + Texts.size(ModStore.size(dir)), badge, dir.getName().equals(selected), busy);
        }

        Button add = button(Texts.addMod(), CARD, GOLD, 17);
        outline(add, GOLD); add.setEnabled(!busy);
        add.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
            startActivityForResult(intent, 100);
        });
        root.addView(add, wide(dp(56), dp(10), dp(6)));
        text(mods.length == 0 ? Texts.steps() : Texts.addHint(), 14, MUTED, false).setPadding(dp(4), dp(4), dp(4), dp(18));

        Button more = button((showSettings ? "▾  " : "▸  ") + Texts.more(), BG, TEXT, 16);
        more.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        more.setOnClickListener(v -> { showSettings = !showSettings; render(); });
        root.addView(more, wide(dp(52), 0, 0));
        if (showSettings) settings(running, busy || noLink);
    }

    private void modCard(String id, String title, String hint, String badge, boolean chosen, boolean busy) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable shape = new GradientDrawable(); shape.setCornerRadius(dp(14));
        shape.setColor(chosen ? CARD_ON : CARD); if (chosen) shape.setStroke(dp(2), GOLD);
        card.setBackground(shape);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(title, 18, TEXT, true); top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (chosen) top.addView(label(Texts.selected(), 15, GOLD, true));
        card.addView(top);
        if (badge != null) {
            boolean map = badge.equals(Texts.badgeMap());
            TextView b = label(badge, 13, map ? ERROR : badge.equals(Texts.badgeTested()) ? GREEN : WARN, true);
            b.setPadding(0, dp(4), 0, 0); card.addView(b);
        }
        TextView h = label(hint, 14, MUTED, false); h.setPadding(0, dp(4), 0, 0); card.addView(h);
        if (id != null) {
            TextView remove = label(Texts.remove(), 14, ERROR, false);
            remove.setPadding(0, dp(10), dp(12), dp(2));
            remove.setOnClickListener(v -> { if (!isBusy()) confirmRemove(id, title); });
            card.addView(remove, new LinearLayout.LayoutParams(-2, -2));
        }
        card.setClickable(true);
        card.setOnClickListener(v -> {
            if (busy) return;
            if (id != null && !ModKind.playable(ModKind.of(store, entry(id)))) { say(Texts.unsupportedMap(), WARN); render(); return; }
            select(id);
        });
        root.addView(card, wide(-2, 0, dp(10)));
    }

    private void settings(boolean running, boolean busy) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(8), dp(14), dp(12));
        GradientDrawable shape = new GradientDrawable(); shape.setCornerRadius(dp(14)); shape.setColor(CARD); box.setBackground(shape);
        Switch perf = new Switch(this); perf.setText(Texts.perf()); perf.setTextColor(TEXT);
        perf.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17); perf.setPadding(0, dp(8), 0, dp(4));
        String perfState = embedded ? null : link == null ? "absent" : link.get("perf");
        if (embedded) {
            try { perf.setChecked(RuntimeFeatures.perf(getFilesDir())); } catch (Exception error) { perf.setEnabled(false); }
            perf.setOnCheckedChangeListener((b, checked) -> {
                try { RuntimeFeatures.setPerf(getFilesDir(), checked); } catch (Exception error) { say(error.getMessage(), ERROR); render(); }
            });
        } else {
            // Perf is its own Magisk module; this switch only pauses or resumes it.
            perf.setChecked("on".equals(perfState)); perf.setEnabled(!busy && !"absent".equals(perfState));
            perf.setOnCheckedChangeListener((b, checked) -> rootTask(null, "perf", checked ? "on" : "off"));
        }
        box.addView(perf);
        TextView ph = label("absent".equals(perfState) ? Texts.perfMissing() : Texts.perfHint(), 13, MUTED, false); ph.setPadding(0, 0, 0, dp(10)); box.addView(ph);
        {
            settingButton(box, Texts.backup(), busy, v -> {
                if (!stopped()) return;
                Intent target = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                target.addCategory(Intent.CATEGORY_OPENABLE); target.setType("application/zip");
                target.putExtra(Intent.EXTRA_TITLE, "TitanQuest-characters-" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date()) + ".zip");
                startActivityForResult(target, 101);
            });
            settingButton(box, Texts.restore(), busy, v -> {
                if (!stopped()) return;
                new AlertDialog.Builder(this).setTitle(Texts.restoreTitle()).setMessage(Texts.restoreBody())
                    .setNegativeButton(Texts.cancel(), null)
                    .setPositiveButton(Texts.chooseBackup(), (d, w) -> {
                        Intent input = new Intent(Intent.ACTION_OPEN_DOCUMENT); input.addCategory(Intent.CATEGORY_OPENABLE);
                        input.setType("application/zip"); startActivityForResult(input, 102);
                    }).show();
            });
        }
        settingButton(box, Texts.shortcut(), false, v -> pinShortcut());
        if (running) settingButton(box, Texts.closeGame(), busy, v -> new AlertDialog.Builder(this)
            .setTitle(Texts.saveFirstTitle()).setMessage(Texts.saveFirstBody())
            .setNegativeButton(Texts.cancel(), null)
            .setPositiveButton(Texts.savedClose(), (d, w) -> {
                if (isBusy()) return;
                if (embedded) android.os.Process.killProcess(android.os.Process.myPid()); else rootTask(null, "stop");
            }).show());
        root.addView(box, wide(-2, dp(4), 0));
    }
    private void settingButton(LinearLayout box, String label, boolean busy, View.OnClickListener action) {
        Button b = button(label, CARD_ON, TEXT, 16); b.setEnabled(!busy); b.setOnClickListener(action);
        box.addView(b, wide(dp(52), dp(6), 0));
    }

    // ---- Small view helpers ------------------------------------------------------------------
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams wide(int height, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height); p.topMargin = top; p.bottomMargin = bottom; return p;
    }
    private TextView label(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextColor(color); v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); v.setLineSpacing(0, 1.15f); return v;
    }
    private TextView text(String s, int sp, int color, boolean bold) { TextView v = label(s, sp, color, bold); root.addView(v); return v; }
    private Button button(String s, int bg, int fg, int sp) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(fg);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable shape = new GradientDrawable(); shape.setCornerRadius(dp(14)); shape.setColor(bg); b.setBackground(shape);
        b.setPadding(dp(16), 0, dp(16), 0); return b;
    }
    private void outline(Button b, int color) { ((GradientDrawable) b.getBackground()).setStroke(dp(2), color); }
    private void banner(String s, int color, String detail) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable shape = new GradientDrawable(); shape.setCornerRadius(dp(12)); shape.setColor(CARD); shape.setStroke(dp(2), color);
        box.setBackground(shape);
        box.addView(label(s, 15, TEXT, false));
        if (detail != null && !detail.isEmpty()) { TextView d = label(Texts.details() + detail, 11, MUTED, false); d.setPadding(0, dp(6), 0, 0); box.addView(d); }
        root.addView(box, wide(-2, 0, dp(10)));
    }
    private void say(String s, int color) {
        message = s; messageColor = color;
        // Show a new message at the top; with no message keep the player's place in the list.
        if (s != null && scroll != null) scroll.post(() -> scroll.smoothScrollTo(0, 0));
    }

    // ---- Actions -----------------------------------------------------------------------------
    private void onPlay(String selected, String active, boolean running) {
        if (isBusy()) return;
        if (selected != null && !ModKind.playable(ModKind.of(store, entry(selected)))) { say(Texts.unsupportedMap(), WARN); render(); return; }
        if (!running) { startGame(false); return; }
        if (same(selected, active)) {
            Intent game = embedded ? new Intent() : getPackageManager().getLaunchIntentForPackage(RootLink.GAME);
            if (game == null) { say(Texts.noGame(), ERROR); render(); return; }
            if (embedded) game.setClassName(getPackageName(), "com.hg.titanquest.MainActivity");
            game.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(game); return;
        }
        new AlertDialog.Builder(this).setTitle(Texts.saveFirstTitle()).setMessage(Texts.restartBody(name(selected)))
            .setNegativeButton(Texts.cancel(), null)
            .setPositiveButton(Texts.savedRestart(), (d, w) -> { if (!isBusy()) startGame(true); }).show();
    }
    private void startGame(boolean restart) {
        if (!embedded) {
            // The module copies the mod into the game, makes the character copy once and starts the game.
            String id = selectedId();
            // Android hides the loading message (even toasts) without this permission; ask before the game covers the dialog.
            if (id != null && askNotifications()) { restartAfterAsking = restart; return; }
            if (id != null) GameWatch.start(this, name(id));
            background(() -> {
                try { RootLink.run(restart ? new String[] {"launch", getFilesDir().getPath(), "restart"} : new String[] {"launch", getFilesDir().getPath()}); done(null, GREEN); }
                catch (Exception error) { GameWatch.stop(this); done(Texts.startFailed(error.getMessage()), ERROR); }
                refresh();
            });
            return;
        }
        Intent game = new Intent(); game.setClassName(getPackageName(), "org.tqmods.GameBootstrapActivity");
        game.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        game.putExtra("restart", restart);
        try { startActivity(game); } catch (Exception error) { say(Texts.startFailed(error.getMessage()), ERROR); render(); return; }
        // The engine cannot be unloaded: end this process so Android starts the game afresh.
        if (restart) Runtime.getRuntime().exit(0);
    }
    /** Android 13+ asks once; returns true while the question is open (the game starts after the answer). */
    private boolean askNotifications() {
        if (android.os.Build.VERSION.SDK_INT < 33) return false;
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        File asked = new File(getFilesDir(), "notifications-asked");
        if (asked.exists()) return false;
        try { asked.createNewFile(); } catch (IOException ignored) { }
        requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, ASK_NOTIFICATIONS);
        return true;
    }
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == ASK_NOTIFICATIONS && !isBusy()) startGame(restartAfterAsking);
    }
    private void pinShortcut() {
        android.content.pm.ShortcutManager shortcuts = getSystemService(android.content.pm.ShortcutManager.class);
        if (shortcuts == null || !shortcuts.isRequestPinShortcutSupported()) return;
        Intent target = new Intent(Intent.ACTION_MAIN);
        target.setClassName(getPackageName(), embedded ? "org.tqmods.Launcher" : ModManagerActivity.class.getName());
        android.graphics.Bitmap icon = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.drawable.Drawable art = getApplicationInfo().loadIcon(getPackageManager());
        art.setBounds(0, 0, 96, 96); art.draw(new android.graphics.Canvas(icon));
        shortcuts.requestPinShortcut(new android.content.pm.ShortcutInfo.Builder(this, "tq-mod-manager")
            .setShortLabel("TQ Mods").setLongLabel(Texts.title())
            .setIcon(android.graphics.drawable.Icon.createWithBitmap(icon)).setIntent(target).build(), null);
    }
    private boolean stopped() {
        if (isBusy()) return false;
        if (engineLoaded()) { say(Texts.closeGameFirst(), WARN); render(); return false; }
        return true;
    }
    private void confirmRemove(String id, String title) {
        new AlertDialog.Builder(this).setTitle(Texts.removeTitle(title)).setMessage(Texts.removeBody())
            .setNegativeButton(Texts.cancel(), null)
            .setPositiveButton(Texts.remove(), (d, w) -> {
                boolean wasSelected = id.equals(selectedId());
                background(() -> {
                    try { store.retire(entry(id)); done(Texts.removed(), MUTED); } catch (IOException e) { done(e.getMessage(), ERROR); }
                    if (wasSelected) runOnUiThread(() -> select(null));
                });
            }).show();
    }

    private void select(String id) {
        if (isBusy() || store == null) return;
        selectionPending = true; render();
        // Persist intent only. The runtime report is the only activation evidence.
        io.execute(() -> {
            String error = null;
            try { writeSelection(id); } catch (Exception e) { error = e.getMessage(); }
            final String failure = error;
            runOnUiThread(() -> {
                selectionPending = false;
                if (failure != null) say(failure, ERROR); else message = null;
                if (!isDestroyed()) render();
            });
        });
    }
    private void writeSelection(String id) throws Exception {
        writeRequest(id, "tqmods/request.json", false);
        // The standalone app's files are copied into the game folder; the game reads paths there.
        if (!embedded) writeRequest(id, "tqmods/request-game.json", true);
    }
    private String path(File file, boolean inGame) {
        String p = file.getPath();
        if (!inGame) return p;
        // The library may be reached through /data/user/0 or its /data/data alias.
        String[] own = {getFilesDir().getPath(), "/data/data/" + getPackageName() + "/files", "/data/user/0/" + getPackageName() + "/files"};
        for (String prefix : own) if (p.startsWith(prefix + "/")) return RootLink.GAME_FILES + p.substring(prefix.length());
        throw new IllegalStateException("Mod outside app storage: " + p);
    }
    private void writeRequest(String id, String target, boolean inGame) throws Exception {
        JSONObject request = new JSONObject(); request.put("schema", 1);
        request.put("id", id == null ? JSONObject.NULL : id);
        File database = id == null ? null : store.database(entry(id));
        request.put("database", database == null ? JSONObject.NULL : path(database, inGame));
        request.put("size", database == null ? 0 : database.length());
        request.put("name", id == null ? JSONObject.NULL : ModStore.title(entry(id)));
        request.put("customWorld", id != null && store.customWorld(entry(id)));
        org.json.JSONArray resources = new org.json.JSONArray();
        if (id != null) for (File resource : store.resources(entry(id))) {
            JSONObject item = new JSONObject(); item.put("path", path(resource, inGame));
            item.put("size", resource.length()); resources.put(item);
        }
        request.put("resources", resources);
        AtomicFile file = new AtomicFile(new File(getFilesDir(), target));
        FileOutputStream stream = null;
        try {
            stream = file.startWrite(); stream.write(request.toString().getBytes("UTF-8"));
            file.finishWrite(stream); stream = null;
        } finally { if (stream != null) file.failWrite(stream); }
    }

    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == 101 && stopped()) exportBackup(data.getData());
        else if (request == 102 && stopped()) restoreBackup(data.getData());
        else if (request == 100) stageUri(data.getData());
    }
    private void background(Runnable task) {
        ownsOperation = true; importPending = true; message = null; render();
        io.execute(task);
    }
    private void done(String text, int color) {
        runOnUiThread(() -> { importPending = false; say(text, color); if (!isDestroyed()) render(); });
    }
    private void exportBackup(android.net.Uri target) {
        background(() -> {
            File snapshot = null, staging = null;
            try {
                File source;
                if (embedded) {
                    File external = getExternalFilesDir(null);
                    if (external == null) throw new IOException("Game storage unavailable");
                    source = new File(external, "Preferences/SaveData");
                } else {
                    staging = new File(getCacheDir(), "save-backup");
                    RootLink.run("backup", staging.getPath()); // Root copies the characters here for packing.
                    source = new File(staging, "SaveData");
                }
                snapshot = File.createTempFile("save-backup-", ".zip", getCacheDir());
                int count;
                try (OutputStream output = new FileOutputStream(snapshot)) { count = SaveBackup.write(source, output); }
                try (InputStream input = new FileInputStream(snapshot); OutputStream output = getContentResolver().openOutputStream(target, "wt")) {
                    if (output == null) throw new IOException("Cannot open backup destination");
                    byte[] buffer = new byte[65536]; int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
                done(Texts.backupDone(count), GREEN);
            } catch (Exception error) { done(Texts.backupFailed(error.getMessage()), ERROR); }
            finally { if (snapshot != null) snapshot.delete(); deleteTree(staging); }
        });
    }
    private void restoreBackup(android.net.Uri source) {
        background(() -> {
            File snapshot = null;
            try {
                snapshot = File.createTempFile("restore-", ".zip", getCacheDir());
                try (InputStream input = getContentResolver().openInputStream(source); OutputStream output = new FileOutputStream(snapshot)) {
                    if (input == null) throw new IOException("Cannot open backup");
                    byte[] buffer = new byte[65536]; long total = 0; int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count; if (total > 512L * 1024 * 1024) throw new IOException("Backup input too large");
                        output.write(buffer, 0, count);
                    }
                }
                if (embedded) {
                    File external = getExternalFilesDir(null);
                    if (external == null) throw new IOException("Game storage unavailable");
                    SaveRestore.restore(snapshot, new File(external, "Preferences/SaveData"));
                } else {
                    // Unpack and check every file here; root then swaps it in and keeps the current characters.
                    // Canonical path: SaveRestore refuses aliases such as /data/user/0 for /data/data.
                    File staging = new File(getCacheDir().getCanonicalFile(), "restore"); deleteTree(staging);
                    File saves = new File(staging, "SaveData");
                    if (!saves.mkdirs()) throw new IOException("Cannot prepare restore");
                    SaveRestore.restore(snapshot, saves);
                    RootLink.run("restore", staging.getPath());
                }
                done(Texts.restoreDone(), GREEN);
            } catch (Exception error) { done(Texts.restoreFailed(error.getMessage()), ERROR); }
            finally { if (snapshot != null) snapshot.delete(); }
        });
    }
    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = java.nio.file.Files.isSymbolicLink(file.toPath()) ? null : file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
    private void stageUri(final android.net.Uri uri) {
        background(() -> {
            try {
                String name = null;
                try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                }
                String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
                if (lower.endsWith(".rar")) { finishImport(Texts.archiveRar(), WARN, null); return; }
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new IOException("Cannot open selected file");
                    pending = ImportSnapshot.create(new File(getCacheDir(), "mod-inputs"), stream, name);
                }
                if (pending.is7z()) {
                    runOnUiThread(() -> { message = Texts.unpacking7z(); messageColor = MUTED; if (!isDestroyed()) render(); });
                    pending = pending.unpack7z(ModStore::keeps);
                }
                importPending(null);
            } catch (SevenZip.Unsupported e) { finishImport(Texts.unsupported7z(e.getMessage()), WARN, null);
            } catch (Exception e) { finishImport(Texts.importFailed(e.getMessage()), ERROR, null); }
        });
    }
    // Runs on the single IO executor, including each variant retry.
    private void importPending(final String variant) {
        try {
            File added;
            try (InputStream stream = pending.open()) { added = store.importFile(stream, pending.name, variant); }
            File known = store.duplicateOf(added);
            if (known != null) { store.remove(added); added = known; } // Keep the existing entry and its character copy.
            File back = known == null ? store.reclaim(added) : added; // Removed earlier: same id, same characters.
            boolean returned = back != added; added = back;
            boolean playable = ModKind.playable(ModKind.of(store, added));
            String title = ModStore.title(added);
            finishImport(known != null ? Texts.alreadyAdded(title) : returned ? Texts.addedBack(title)
                : playable ? Texts.importedChosen(title) : Texts.imported(title), GREEN, playable ? added.getName() : null);
        } catch (ModStore.UnsupportedArchive archive) {
            finishImport(Texts.archiveRar(), WARN, null);
        } catch (ModStore.VariantRequired choice) {
            String[] labels = new String[choice.variants.length];
            for (int i = 0; i < labels.length; i++) labels[i] = choice.variants[i].substring(choice.variants[i].lastIndexOf('/') + 1).replace('_', ' ');
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                new AlertDialog.Builder(this).setTitle(Texts.chooseVariant())
                    .setItems(labels, (d, which) -> io.execute(() -> importPending(choice.variants[which])))
                    .setNegativeButton(Texts.cancel(), (d, w) -> io.execute(() -> finishImport(null, MUTED, null)))
                    .setOnCancelListener(d -> io.execute(() -> finishImport(null, MUTED, null))).show();
            });
        } catch (Exception e) { finishImport(Texts.importFailed(e.getMessage()), ERROR, null); }
    }
    private void finishImport(String text, int color, String selectId) {
        if (pending != null) { try { pending.close(); } catch (IOException ignored) { } pending = null; }
        // A newly added playable mod becomes the choice: one less step for the player.
        if (selectId != null) try { writeSelection(selectId); } catch (Exception ignored) { }
        runOnUiThread(() -> { importPending = false; ownsOperation = false; say(text, color); if (!isDestroyed()) render(); });
    }
    protected void onDestroy() {
        if (ownsOperation) io.execute(() -> finishImport(null, MUTED, null));
        io.shutdown(); super.onDestroy();
    }
}
