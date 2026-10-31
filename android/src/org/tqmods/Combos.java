package org.tqmods;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Several content mods played together. A combination only lists its mods (first one wins
 *  conflicts) and gets its own copy of the characters; the mods stay in the library. */
public final class Combos {
    public static final int MIN = 2, MAX = 8;
    private final File root;
    public Combos(File root) throws IOException {
        this.root = root;
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create combinations folder");
    }

    public File dir(String id) { return new File(root, id); }
    public boolean has(String id) { return id != null && new File(dir(id), "members").isFile(); }
    public File[] all() {
        File[] found = root.listFiles(f -> new File(f, "members").isFile());
        return found == null ? new File[0] : found;
    }
    public List<String> members(String id) throws IOException {
        List<String> ids = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(dir(id), "members")), StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; )
                if (line.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) ids.add(line);
        }
        return ids;
    }

    /** Returns the existing combination with the same mods in the same order, or a new one. */
    public synchronized String create(List<String> ids, String title) throws IOException {
        if (ids.size() < MIN || ids.size() > MAX || new HashSet<>(ids).size() != ids.size()) throw new IOException("Choose 2 to 8 different mods");
        for (File existing : all()) if (members(existing.getName()).equals(ids)) return existing.getName();
        String id = UUID.randomUUID().toString();
        File stage = new File(root, ".new-" + id);
        if (!stage.mkdirs()) throw new IOException("Cannot create combination");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(new File(stage, "members")), StandardCharsets.UTF_8)) {
            for (String m : ids) out.write(m + "\n");
        }
        try (Writer out = new OutputStreamWriter(new FileOutputStream(new File(stage, ".title")), StandardCharsets.UTF_8)) { out.write(title); }
        if (!stage.renameTo(dir(id))) throw new IOException("Cannot save combination");
        return id;
    }
    public synchronized void remove(String id) throws IOException {
        File d = dir(id);
        File[] files = d.listFiles();
        if (files != null) for (File f : files) if (!f.delete()) throw new IOException("Cannot remove combination");
        if (d.exists() && !d.delete()) throw new IOException("Cannot remove combination");
    }

    /** Record names of an ARZ v3 database, lower case with forward slashes. */
    public static Set<String> records(File arz) throws IOException {
        if (arz.length() > 512L * 1024 * 1024) throw new IOException("Database too large");
        byte[] all = java.nio.file.Files.readAllBytes(arz.toPath());
        ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        try {
            if (b.getShort(0) != 4 || b.getShort(2) != 3) throw new IOException("Unsupported ARZ header");
            int recordStart = b.getInt(4), count = b.getInt(12), stringStart = b.getInt(16);
            if (count < 0 || count > 4_000_000) throw new IOException("Damaged ARZ");
            int strings = b.getInt(stringStart);
            if (strings < 0 || strings > 8_000_000) throw new IOException("Damaged ARZ");
            int[] at = new int[strings], len = new int[strings];
            int p = stringStart + 4;
            for (int i = 0; i < strings; i++) { len[i] = b.getInt(p); at[i] = p + 4; p += 4 + len[i]; }
            Set<String> names = new HashSet<>();
            p = recordStart;
            for (int i = 0; i < count; i++) {
                int index = b.getInt(p), type = b.getInt(p + 4);
                if (index < 0 || index >= strings || type < 0) throw new IOException("Damaged ARZ");
                names.add(new String(all, at[index], len[index], StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT).replace('\\', '/'));
                p += 8 + type + 16;
            }
            return names;
        } catch (IndexOutOfBoundsException | NegativeArraySizeException damaged) { throw new IOException("Damaged ARZ"); }
    }

    /** Files the mod's archives put into the game, as "archive/inner path" in lower case. Each mod's
     *  Text/modstrings.txt is left out: the loader reads it per mod, so it never hides another one. */
    public static Set<String> resourceFiles(File[] arcs) throws IOException {
        Set<String> files = new HashSet<>();
        for (File arc : arcs) {
            String name = arc.getName().toLowerCase(Locale.ROOT);
            String archive = name.endsWith(".arc") ? name.substring(0, name.length() - 4) : name;
            for (String inner : ModStore.arcEntries(arc).keySet())
                if (!(archive.equals("text") && inner.equals("modstrings.txt"))) files.add(archive + "/" + inner);
        }
        return files;
    }

    /** One line per pair of mods that change the same records (or files): {earlier, later, count}. */
    public static List<int[]> conflicts(List<Set<String>> records) {
        List<int[]> rows = new ArrayList<>();
        for (int i = 0; i < records.size(); i++)
            for (int j = i + 1; j < records.size(); j++) {
                int n = 0;
                Set<String> a = records.get(i), b = records.get(j);
                for (String r : a.size() < b.size() ? a : b) if ((a.size() < b.size() ? b : a).contains(r)) n++;
                if (n > 0) rows.add(new int[] {i, j, n});
            }
        return rows;
    }
}
