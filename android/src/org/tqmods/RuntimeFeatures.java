package org.tqmods;

import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;

/** Feature choices, frozen into the next startup request. */
public final class RuntimeFeatures {
    private RuntimeFeatures() {}
    /** Allowed "more enemies" factors in percent; 100 = off. */
    public static final int[] ENEMIES = {100, 150, 200, 300};

    private static JSONObject read(File files) throws Exception {
        AtomicFile file = new AtomicFile(new File(files, "tqmods/features.json"));
        try {
            byte[] data = file.readFully();
            if (data.length > 4096) throw new IOException("Feature settings exceed limit");
            JSONObject value = new JSONObject(new String(data, "UTF-8"));
            if (value.getInt("schema") != 1) throw new IOException("Invalid feature settings");
            if (value.has("perf") && !(value.get("perf") instanceof Boolean)) throw new IOException("Invalid feature settings");
            if (value.has("enemies") && !allowed(value.optInt("enemies", -1))) throw new IOException("Invalid feature settings");
            return value;
        } catch (FileNotFoundException absent) { JSONObject v = new JSONObject(); v.put("schema", 1); return v; }
    }
    private static void write(File files, JSONObject value) throws Exception {
        File directory = new File(files, "tqmods");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot save feature settings");
        AtomicFile file = new AtomicFile(new File(directory, "features.json"));
        FileOutputStream out = null;
        try { out=file.startWrite(); out.write(value.toString().getBytes("UTF-8")); file.finishWrite(out); }
        catch (Exception error) { if(out!=null) file.failWrite(out); throw error; }
    }
    static boolean allowed(int percent) { for (int p : ENEMIES) if (p == percent) return true; return false; }

    public static synchronized boolean perf(File files) throws Exception { return read(files).optBoolean("perf", false); }
    public static synchronized void setPerf(File files, boolean enabled) throws Exception {
        JSONObject value = read(files); value.put("perf", enabled); write(files, value);
    }
    public static synchronized int enemies(File files) throws Exception { return read(files).optInt("enemies", 100); }
    public static synchronized void setEnemies(File files, int percent) throws Exception {
        if (!allowed(percent)) throw new IllegalArgumentException("Unsupported factor " + percent);
        JSONObject value = read(files); value.put("enemies", percent); write(files, value);
    }
}
