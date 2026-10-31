package org.tqmods;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** RAR4/RAR5 input through libarchive (libtqrar.so), rewritten as ZIP for the importer like a 7z. */
public final class Rar {
    private Rar() {}
    private static final byte[] MAGIC = {'R', 'a', 'r', '!', 0x1a, 0x07};
    private static final long MAX_TOTAL = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 50_000;
    private static Boolean loaded;
    public static final class Locked extends IOException { Locked() { super("RAR archive is password-protected"); } }

    static native long open(String path) throws IOException;
    static native String next(long handle) throws IOException;
    static native int read(long handle, byte[] buffer, int offset, int length) throws IOException;
    static native void close(long handle);

    public static boolean isRar(File file) throws IOException {
        byte[] head = new byte[6];
        try (InputStream in = new FileInputStream(file)) {
            int n = 0, r;
            while (n < 6 && (r = in.read(head, n, 6 - n)) > 0) n += r;
            return n == 6 && Arrays.equals(head, MAGIC);
        }
    }
    /** False on devices without the native library (only arm64-v8a is built). */
    public static synchronized boolean available() {
        if (loaded == null) {
            try { System.loadLibrary("tqrar"); loaded = true; } catch (UnsatisfiedLinkError e) { loaded = false; }
        }
        return loaded;
    }

    /** Writes the archive's files into a ZIP stream; unwanted files are written empty. Returns the number of files. */
    public static int toZip(File archive, OutputStream out, SevenZip.Filter filter) throws IOException {
        if (!available()) throw new ModStore.UnsupportedArchive();
        long handle = open(archive.getAbsolutePath());
        try {
            ZipOutputStream zip = new ZipOutputStream(out);
            Set<String> seen = new HashSet<>();
            byte[] buffer = new byte[65536];
            long total = 0;
            int files = 0;
            for (String name; (name = next(handle)) != null; ) {
                if (++files > MAX_ENTRIES) throw new IOException("RAR archive has too many entries");
                String path = name.replace('\\', '/');
                while (path.startsWith("/")) path = path.substring(1);
                if (path.isEmpty() || path.endsWith("/")) { files--; continue; }
                if (path.matches("(.*/)?\\.\\.(/.*)?") || path.contains(":")) throw new IOException("Unsafe path in archive: " + path);
                if (!seen.add(path.toLowerCase(Locale.ROOT))) throw new IOException("Repeated file in archive: " + path);
                zip.putNextEntry(new ZipEntry(path));
                if (filter == null || filter.wanted(path))
                    for (int n; (n = read(handle, buffer, 0, buffer.length)) > 0; ) {
                        total += n;
                        if (total > MAX_TOTAL) throw new IOException("RAR contents exceed 2 GiB");
                        zip.write(buffer, 0, n);
                    }
                zip.closeEntry();
            }
            zip.finish();
            return files;
        } catch (IOException e) {
            if ("RAR archive is password-protected".equals(e.getMessage())) throw new Locked();
            throw e;
        } finally { close(handle); }
    }
}
