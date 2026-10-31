package org.tqmods;

import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;

/** One feature choice, frozen into the next startup request. */
public final class RuntimeFeatures {
    private RuntimeFeatures() {}
    public static synchronized boolean perf(File files) throws Exception {
        AtomicFile file = new AtomicFile(new File(files, "tqmods/features.json"));
        try {
            byte[] data = file.readFully();
            if (data.length > 4096) throw new IOException("Feature settings exceed limit");
            JSONObject value = new JSONObject(new String(data, "UTF-8"));
            if (value.getInt("schema") != 1 || !(value.get("perf") instanceof Boolean))
                throw new IOException("Invalid feature settings");
            return value.getBoolean("perf");
        } catch (FileNotFoundException absent) { return false; }
    }
    public static synchronized void setPerf(File files, boolean enabled) throws Exception {
        File directory = new File(files, "tqmods");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot save feature settings");
        JSONObject value = new JSONObject(); value.put("schema", 1); value.put("perf", enabled);
        AtomicFile file = new AtomicFile(new File(directory, "features.json"));
        FileOutputStream out = null;
        try { out=file.startWrite(); out.write(value.toString().getBytes("UTF-8")); file.finishWrite(out); }
        catch (Exception error) { if(out!=null) file.failWrite(out); throw error; }
    }
}
