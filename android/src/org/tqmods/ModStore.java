package org.tqmods;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** App-private import storage. Import success does not imply engine compatibility. */
public final class ModStore {
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ENTRIES = 4096;
    private final File root;
    public ModStore(File root) throws IOException {
        this.root = root.getCanonicalFile();
        if (!this.root.isDirectory() && !this.root.mkdirs()) throw new IOException("Cannot create mod storage");
    }
    public static final class UnsupportedArchive extends IOException {
        UnsupportedArchive() { super("RAR archive"); }
    }
    /** Files a mod import keeps; loose PC install extras (textures, readmes, images) are not used here. */
    public static boolean keeps(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".arz") || lower.endsWith(".arc") || lower.endsWith(".map") || lower.endsWith(".txt") || lower.endsWith(".md");
    }
    public static final class VariantRequired extends IOException {
        public final String[] variants;
        VariantRequired(String[] variants) { super("Choose a mod variant"); this.variants = variants; }
    }
    public File importFile(InputStream source, String displayName) throws IOException {
        return importFile(source, displayName, null);
    }
    public synchronized File importFile(InputStream source, String displayName, String variant) throws IOException {
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".zip") && !name.endsWith(".arz")) throw new IOException("Choose a ZIP or ARZ file");
        // A RAR (or a 7z renamed to .zip) cannot be read as ZIP; report that plainly.
        source = new BufferedInputStream(source, 1 << 16);
        source.mark(8); byte[] magic = new byte[4]; int got = source.read(magic); source.reset();
        if (got == 4 && ((magic[0] == '7' && magic[1] == 'z' && (magic[2] & 0xff) == 0xbc)
                || (magic[0] == 'R' && magic[1] == 'a' && magic[2] == 'r' && magic[3] == '!')))
            throw new UnsupportedArchive();
        String id = UUID.randomUUID().toString();
        File stage = new File(root, ".import-" + id);
        if (!stage.mkdir()) throw new IOException("Cannot stage import");
        boolean committed = false;
        try {
            long[] budget = {0};
            if (name.endsWith(".arz")) {
                copy(source, new File(stage, "database/mod.arz"), budget);
            } else {
                Set<String> seen = new HashSet<>();
                int count = 0;
                try (ZipInputStream zip = new ZipInputStream(source)) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (++count > MAX_ENTRIES) throw new IOException("Too many archive entries");
                        String path = entry.getName().replace('\\', '/');
                        if (path.length() > 1024 || path.split("/", -1).length > 32)
                            throw new IOException("Archive path is too deep or long");
                        if (path.startsWith("/") || path.indexOf(':') >= 0 || path.indexOf('\0') >= 0)
                            throw new IOException("Unsafe archive path");
                        for (String part : path.split("/", -1)) {
                            if (part.equals("..") || part.equals(".")) throw new IOException("Unsafe archive path");
                        }
                        File dest = new File(stage, path).getCanonicalFile();
                        if (!dest.getPath().startsWith(stage.getCanonicalPath() + File.separator))
                            throw new IOException("Archive path escapes import directory");
                        if (!seen.add(dest.getPath().toLowerCase(Locale.ROOT))) throw new IOException("Duplicate archive path");
                        if (entry.isDirectory()) continue;
                        String lower = path.toLowerCase(Locale.ROOT);
                        if (isProgram(lower)) throw new IOException("Unsupported file in archive: " + path);
                        if (!keeps(lower)) {
                            zip.closeEntry(); continue;
                        }
                        copy(zip, dest, budget);
                        zip.closeEntry();
                    }
                }
            }
            List<File> databases = new ArrayList<>();
            collect(stage, databases);
            if (databases.isEmpty()) throw new IOException("No ARZ database found");
            if (databases.size() > 1) {
                TreeMap<String, File> choices = new TreeMap<>();
                for (File database : databases) {
                    File dbDir = database.getParentFile(), variantDir = dbDir.getParentFile();
                    if (!dbDir.getName().equalsIgnoreCase("database") || variantDir.equals(stage))
                        throw new IOException("Ambiguous multi-database layout");
                    String key = stage.toPath().relativize(variantDir.toPath()).toString().replace('\\', '/');
                    if (choices.put(key, variantDir) != null) throw new IOException("Multiple databases in one variant");
                }
                for (String a : choices.keySet()) for (String b : choices.keySet())
                    if (!a.equals(b) && b.startsWith(a + "/")) throw new IOException("Nested variants are ambiguous");
                if (variant == null) throw new VariantRequired(choices.keySet().toArray(new String[0]));
                File selected = choices.get(variant);
                if (selected == null) throw new IOException("Selected variant is no longer present");
                // Keep only the selected subtree; never merge sibling variant resources.
                keepSelected(stage, selected);
                databases.clear(); collect(stage, databases);
                if (databases.size() != 1) throw new IOException("Invalid selected variant");
            } else if (variant != null) {
                throw new IOException("Archive changed since variant selection");
            }
            // Preliminary format check only; the engine must validate records separately.
            try (InputStream in = new FileInputStream(databases.get(0))) {
                if (databases.get(0).length() < 24 || in.read() != 4 || in.read() != 0 || in.read() != 3 || in.read() != 0)
                    throw new IOException("Unsupported ARZ header");
            }
            try (Writer title = new OutputStreamWriter(new FileOutputStream(new File(stage, ".title")), "UTF-8")) {
                title.write(friendlyName(displayName, variant));
            }
            File dest = new File(root, id);
            if (!stage.renameTo(dest)) throw new IOException("Cannot commit import");
            committed = true;
            return dest;
        } finally {
            if (!committed) removeStage(stage);
        }
    }
    private static boolean isProgram(String lower) {
        for (String ext : new String[]{".so", ".dex", ".apk", ".jar", ".exe", ".dll", ".bat", ".cmd", ".sh", ".js", ".py", ".ps1", ".vbs"})
            if (lower.endsWith(ext)) return true;
        return false;
    }
    static String friendlyName(String displayName, String variant) {
        String base = displayName == null ? "Mod" : displayName.replaceAll("[\\p{Cntrl}]", " ");
        base = base.replaceAll("(?i)\\.(zip|arz)$", "").replace('_', ' ').trim();
        if (base.isEmpty()) base = "Mod";
        if (variant == null) return base;
        String last = variant.substring(variant.lastIndexOf('/') + 1).replace('_', ' ').trim();
        return last.equalsIgnoreCase(base) ? base : base + " (" + last + ")";
    }
    /** Another entry with exactly the same files, or null. Re-adding a mod must not split its character copy. */
    public synchronized File duplicateOf(File added) throws IOException {
        String mine = null;
        for (File other : installed()) {
            if (other.getCanonicalFile().equals(added.getCanonicalFile())) continue;
            if (mine == null) mine = fingerprint(added);
            if (mine.equals(fingerprint(other))) return other;
        }
        return null;
    }
    static String fingerprint(File entry) throws IOException {
        TreeMap<String, File> files = new TreeMap<>();
        list(entry.getCanonicalFile(), entry.getCanonicalFile(), files);
        try {
            java.security.MessageDigest all = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (Map.Entry<String, File> e : files.entrySet()) {
                all.update(e.getKey().getBytes("UTF-8")); all.update((byte) 0);
                try (InputStream in = new FileInputStream(e.getValue())) { for (int n; (n = in.read(buffer)) > 0; ) all.update(buffer, 0, n); }
                all.update((byte) 0);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : all.digest()) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    private static void list(File dir, File entry, TreeMap<String, File> found) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) throw new IOException("Cannot inspect mod");
        for (File f : files) {
            if (f.isDirectory()) list(f, entry, found);
            else if (!(dir.equals(entry) && f.getName().startsWith("."))) // .title, .kind: the app's own notes
                found.put(entry.toPath().relativize(f.toPath()).toString().replace('\\', '/').toLowerCase(Locale.ROOT), f);
        }
    }
    /** Removes one imported library entry and nothing outside it. */
    public synchronized void remove(File directory) throws IOException {
        File entry = directory.getCanonicalFile();
        if (!entry.getParentFile().equals(root) || entry.getName().startsWith(".") || !entry.isDirectory())
            throw new IOException("Not an installed library entry");
        File trash = new File(root, ".remove-" + entry.getName());
        if (!entry.renameTo(trash)) throw new IOException("Cannot remove mod");
        removeStage(trash);
    }
    /** True when Maps.arc carries a real world map, which needs a dedicated adapter. */
    public boolean customWorld(File directory) throws IOException {
        for (File arc : resources(directory)) {
            if (!arc.getName().equalsIgnoreCase("Maps.arc")) continue;
            for (long size : arcMapSizes(arc)) if (size > 4096) return true;
        }
        return false;
    }
    static List<Long> arcMapSizes(File arc) throws IOException {
        List<Long> sizes = new ArrayList<>();
        try (RandomAccessFile file = new RandomAccessFile(arc, "r")) {
            long length = file.length();
            if (length < 28 || length > Integer.MAX_VALUE) throw new IOException("Damaged ARC file " + arc.getName());
            byte[] head = new byte[28]; file.readFully(head);
            java.nio.ByteBuffer h = java.nio.ByteBuffer.wrap(head).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if (head[0] != 'A' || head[1] != 'R' || head[2] != 'C') throw new IOException("Damaged ARC file " + arc.getName());
            int files = h.getInt(8), parts = h.getInt(12), toc = h.getInt(24);
            long records = length - 44L * files, names = toc + 12L * parts;
            if (files < 0 || files > 100000 || parts < 0 || toc < 0 || records < names) throw new IOException("Damaged ARC file " + arc.getName());
            byte[] table = new byte[44 * files]; file.seek(records); file.readFully(table);
            java.nio.ByteBuffer t = java.nio.ByteBuffer.wrap(table).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < files; i++) {
                long size = t.getInt(44 * i + 12) & 0xffffffffL;
                int nameLength = t.getInt(44 * i + 36), nameOffset = t.getInt(44 * i + 40);
                if (nameLength < 0 || nameLength > 1024 || nameOffset < 0 || names + nameOffset + nameLength > records)
                    throw new IOException("Damaged ARC file " + arc.getName());
                byte[] name = new byte[nameLength]; file.seek(names + nameOffset); file.readFully(name);
                if (new String(name, "ISO-8859-1").toLowerCase(Locale.ROOT).endsWith(".map")) sizes.add(size);
            }
        }
        return sizes;
    }
    private static void keepSelected(File dir, File selected) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) throw new IOException("Cannot inspect variants");
        for (File child : children) {
            if (child.equals(selected)) continue;
            if (selected.toPath().startsWith(child.toPath())) keepSelected(child, selected);
            else removeStage(child);
        }
    }
    private static void copy(InputStream in, File dest, long[] budget) throws IOException {
        File parent = dest.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create import directory");
        try (OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) {
                budget[0] += n;
                if (budget[0] > MAX_BYTES) throw new IOException("Mod exceeds 512 MiB import limit");
                out.write(buffer, 0, n);
            }
        }
    }
    private static void collect(File dir, List<File> found) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) throw new IOException("Cannot inspect import");
        for (File f : files) {
            if (f.isDirectory()) collect(f, found);
            else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".arz")) found.add(f);
        }
    }
    private static void removeStage(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) removeStage(child);
        if (file.exists() && !file.delete()) throw new IOException("Cannot clean failed import");
    }
    public File[] installed() {
        File[] files = root.listFiles(f -> f.isDirectory() && !f.getName().startsWith("."));
        return files == null ? new File[0] : files;
    }
    /** Resolve one committed library entry; never accept arbitrary external paths. */
    public File database(File directory) throws IOException {
        File entry = directory.getCanonicalFile();
        if (!entry.getParentFile().equals(root.getCanonicalFile()) || entry.getName().startsWith(".") || !entry.isDirectory())
            throw new IOException("Not an installed library entry");
        List<File> databases = new ArrayList<>(); collect(entry, databases);
        if (databases.size() != 1) throw new IOException("Expected one imported database");
        File database = databases.get(0).getCanonicalFile();
        if (!database.toPath().startsWith(entry.toPath())) throw new IOException("Database escapes library entry");
        return database;
    }
    /** Inventory only: resource discovery never proves engine compatibility. */
    public File[] resources(File directory) throws IOException {
        File db = database(directory), entry = directory.getCanonicalFile();
        File variant = db.getParentFile().getName().equalsIgnoreCase("database")
            ? db.getParentFile().getParentFile() : db.getParentFile();
        TreeMap<String, File> found = new TreeMap<>();
        collectResources(variant, entry, found, new HashSet<String>(), 0);
        return found.values().toArray(new File[0]);
    }
    private static void collectResources(File dir, File entry, TreeMap<String, File> found,
                                         Set<String> visited, int depth) throws IOException {
        File canonical = dir.getCanonicalFile();
        if (!canonical.toPath().startsWith(entry.toPath()) || depth > 32
                || !visited.add(canonical.getPath()) || visited.size() > 4096)
            throw new IOException("Unsafe resource directory");
        File[] files = canonical.listFiles();
        if (files == null) throw new IOException("Cannot inspect resources");
        for (File file : files) {
            File resolved = file.getCanonicalFile();
            if (!resolved.toPath().startsWith(entry.toPath())) throw new IOException("Resource escapes entry");
            if (file.isDirectory()) collectResources(file, entry, found, visited, depth + 1);
            else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".arc")) {
                String key = entry.toPath().relativize(resolved.toPath()).toString().toLowerCase(Locale.ROOT);
                if (found.put(key, resolved) != null || found.size() > 4096)
                    throw new IOException("Ambiguous resource inventory");
            }
        }
    }
    public static String title(File dir) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(dir, ".title")), "UTF-8"))) {
            String text = reader.readLine();
            return text == null ? dir.getName() : friendlyName(text, null);
        } catch (IOException e) { return dir.getName(); }
    }
}
