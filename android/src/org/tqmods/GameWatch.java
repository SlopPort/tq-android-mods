package org.tqmods;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.Map;

/** While the game starts with a mod: tells the player, over the game, when the mod loads (the game stands
 *  still for a few seconds then) and when it is ready. Reads the loader status through the module helper. */
public final class GameWatch extends Service {
    private static final String CHANNEL = "mod-loading";
    private static final int NOTE = 1;
    private static final long POLL_MS = 500, NO_GAME_MS = 60_000, LIMIT_MS = 15 * 60_000;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile Thread worker;
    private String name;

    /** Called on the Play tap, while the app is still in front (Android refuses later starts). */
    static void start(Context c, String name) {
        Intent i = new Intent(c, GameWatch.class).putExtra("name", name);
        try { c.startForegroundService(i); } catch (RuntimeException refused) { /* Only the message is lost. */ }
    }
    static void stop(Context c) { c.stopService(new Intent(c, GameWatch.class)); }

    public IBinder onBind(Intent i) { return null; }

    public int onStartCommand(Intent intent, int flags, int id) {
        name = intent == null ? null : intent.getStringExtra("name");
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, Texts.watchChannel(), NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null); channel.enableVibration(false);
        nm.createNotificationChannel(channel);
        Notification first = note(Texts.starting(), true);
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE, first, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTE, first);
        Thread old = worker;
        if (old != null) old.interrupt();
        Thread t = new Thread(this::watch, "tqmods-watch");
        worker = t; t.start();
        return START_NOT_STICKY;
    }

    public void onDestroy() { Thread t = worker; worker = null; if (t != null) t.interrupt(); super.onDestroy(); }

    private Notification note(String text, boolean ongoing) { return build(text).setOngoing(ongoing).build(); }
    private Notification.Builder build(String text) {
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.graphics.drawable.Icon.createWithResource(this, getApplicationInfo().icon))
            .setContentTitle(name == null ? Texts.title() : name).setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_STATUS);
    }

    private void watch() {
        long began = System.currentTimeMillis();
        String seenPid = null, shown = null;
        try {
            while (worker == Thread.currentThread() && System.currentTimeMillis() - began < LIMIT_MS) {
                Thread.sleep(POLL_MS);
                Map<String, String> s;
                try { s = RootLink.run("status"); } catch (Exception broken) { continue; }
                String pid = s.get("pid") == null ? "" : s.get("pid").trim();
                if (pid.isEmpty() || pid.contains(" ")) {
                    if (seenPid != null || System.currentTimeMillis() - began > NO_GAME_MS) break; // Closed, or never started.
                    continue;
                }
                seenPid = pid;
                JSONObject r;
                try { r = new JSONObject(s.get("report")); } catch (Exception none) { continue; }
                if (!pid.equals(String.valueOf(r.optInt("pid")))) continue; // Status of an earlier start.
                String status = r.optString("status");
                if (status.equals("refused")) { finish(Texts.stateFailed(Texts.explain(r.optString("error"))), true); return; }
                if (r.optBoolean("modsActivated") || status.equals("ready")) { finish(Texts.watchReady(), false); return; }
                String state = activating(r) ? Texts.watchLoading() : Texts.watchMenu();
                if (!state.equals(shown)) {
                    shown = state;
                    update(state, true);
                    if (activating(r)) toast(state);
                }
            }
        } catch (InterruptedException stopped) { return; }
        ui.post(() -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); });
    }

    /** Module 1.1 says so; module 1.0 only shows the mod's own progress entry. */
    private static boolean activating(JSONObject r) {
        if (r.optString("status").equals("activating")) return true;
        JSONObject menu = r.optJSONObject("menuProbe");
        return menu != null && (menu.has("content") || menu.has("map") || menu.has("database"));
    }

    private void update(String text, boolean ongoing) {
        getSystemService(NotificationManager.class).notify(NOTE, note(text, ongoing));
    }
    private void toast(String text) { ui.post(() -> Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show()); }
    /** Leaves the last message as a normal notification that disappears on its own. */
    private void finish(String text, boolean problem) {
        toast(text);
        ui.post(() -> {
            stopForeground(STOP_FOREGROUND_REMOVE);
            getSystemService(NotificationManager.class).notify(NOTE,
                build(text).setTimeoutAfter(problem ? 10 * 60_000 : 15_000).setAutoCancel(true).build());
            stopSelf();
        });
    }
}
