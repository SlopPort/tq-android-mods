import org.tqmods.ModStore;
import org.tqmods.SevenZip;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Converts 7z fixtures made with 7-Zip (synthetic content) and checks every file by SHA-256.
 *  Usage: java SevenZipTest [fixtures/7z folder] [extra .7z files, for example a real mod download] */
public final class SevenZipTest {
    static String sha(byte[] b) throws Exception {
        StringBuilder s = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) s.append(String.format("%02x", x & 0xff));
        return s.toString();
    }
    static Map<String, byte[]> convert(File archive, SevenZip.Filter filter) throws Exception {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        SevenZip.toZip(archive, zip, filter);
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip.toByteArray()))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                if (files.put(e.getName(), in.readAllBytes()) != null) throw new AssertionError("Duplicate " + e.getName());
            }
        }
        return files;
    }
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "android/tests/fixtures/7z");
        Map<String, String> expected = new TreeMap<>();
        for (String line : Files.readAllLines(new File(dir, "expected.txt").toPath(), StandardCharsets.UTF_8))
            if (!line.isEmpty()) expected.put(line.substring(66), line.substring(0, 64));
        int checked = 0;
        File[] fixtures = dir.listFiles((d, n) -> n.endsWith(".7z"));
        Arrays.sort(fixtures);
        for (File f : fixtures) {
            if (!SevenZip.is7z(f)) throw new AssertionError("Not detected as 7z: " + f);
            if (f.getName().startsWith("refuse-")) {
                try { convert(f, p -> true); throw new AssertionError("Accepted " + f.getName()); }
                catch (SevenZip.Unsupported expectedRefusal) { }
                // Nothing wanted: the folder is skipped, so the unsupported method does not matter.
                Map<String, byte[]> names = convert(f, p -> false);
                if (!names.containsKey("MyMod/Readme.txt") || names.get("MyMod/Readme.txt").length != 0)
                    throw new AssertionError("Skipped entry missing in " + f.getName());
                continue;
            }
            Map<String, byte[]> files = convert(f, p -> true);
            int matched = 0;
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                String want = expected.get(e.getKey());
                if (want == null) throw new AssertionError(f.getName() + ": unexpected " + e.getKey());
                if (!want.equals(sha(e.getValue()))) throw new AssertionError(f.getName() + ": content differs " + e.getKey());
                matched++;
            }
            int wantCount = f.getName().startsWith("copy-") ? expected.size() - 1 : f.getName().equals("lzma2-random.7z") ? 1 : expected.size();
            if (matched != wantCount) throw new AssertionError(f.getName() + ": " + matched + " files, expected " + wantCount);
            // Filtered: unwanted files appear empty, wanted ones intact.
            Map<String, byte[]> some = convert(f, p -> p.endsWith(".arz"));
            for (Map.Entry<String, byte[]> e : some.entrySet())
                if (!e.getKey().endsWith(".arz") && e.getValue().length != 0) throw new AssertionError("Filter ignored " + e.getKey());
            checked++;
        }
        if (checked < 6) throw new AssertionError("Too few fixtures: " + checked);
        // Damaged archive: flip a byte in the packed data.
        byte[] good = Files.readAllBytes(new File(dir, "lzma2-ultra.7z").toPath());
        byte[] bad = good.clone(); bad[200] ^= 0x55;
        File damaged = File.createTempFile("damaged-", ".7z"); Files.write(damaged.toPath(), bad);
        try { convert(damaged, p -> true); throw new AssertionError("Accepted damaged archive"); } catch (IOException ok) { }
        File cut = File.createTempFile("cut-", ".7z"); Files.write(cut.toPath(), Arrays.copyOf(good, good.length / 2));
        try { convert(cut, p -> true); throw new AssertionError("Accepted truncated archive"); } catch (IOException ok) { }
        damaged.delete(); cut.delete();
        // End to end: the converted ZIP goes through the normal importer.
        File root = Files.createTempDirectory("tqmods-7z-").toFile();
        ModStore store = new ModStore(root);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        SevenZip.toZip(new File(dir, "lzma2-ultra.7z"), zip, p -> true);
        File added = store.importFile(new ByteArrayInputStream(zip.toByteArray()), "MyMod.zip");
        if (store.installed().length != 1 || !added.isDirectory())
            throw new AssertionError("Import of converted 7z failed");
        // Same mod from a different archive format is recognised as a duplicate; a different one is not.
        ByteArrayOutputStream other = new ByteArrayOutputStream();
        SevenZip.toZip(new File(dir, "lzma-nonsolid.7z"), other, p -> true);
        Files.write(new File(added, ".kind").toPath(), "content".getBytes(StandardCharsets.UTF_8)); // Written by the app later.
        File again = store.importFile(new ByteArrayInputStream(other.toByteArray()), "MyMod again.zip");
        if (!added.equals(store.duplicateOf(again))) throw new AssertionError("Duplicate not recognised");
        ByteArrayOutputStream changed = new ByteArrayOutputStream();
        SevenZip.toZip(new File(dir, "lzma2-random.7z"), changed, p -> true);
        File different = store.importFile(new ByteArrayInputStream(changed.toByteArray()), "Other.zip");
        if (store.duplicateOf(different) != null) throw new AssertionError("Different mod taken as duplicate");
        // Removed by the player, then added again: the old id (and so the character copy) comes back.
        store.remove(again);
        String oldId = added.getName();
        store.retire(added);
        File readded = store.reclaim(store.importFile(new ByteArrayInputStream(other.toByteArray()), "MyMod.zip"));
        if (!readded.getName().equals(oldId)) throw new AssertionError("Re-added mod got a new id");
        File fresh = store.importFile(new ByteArrayInputStream(zip.toByteArray()), "Copy.zip");
        if (store.reclaim(fresh) != fresh) throw new AssertionError("Id handed out twice");
        if (ModStore.size(readded) < 2_600_000) throw new AssertionError("Size too small");
        // Optional real archives passed on the command line: report time and file count.
        for (int i = 1; i < args.length; i++) {
            long t = System.nanoTime();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int n = SevenZip.toZip(new File(args[i]), out, p -> true);
            System.out.printf("%s: %d files, %d bytes zipped, %.1f s%n", args[i], n, out.size(), (System.nanoTime() - t) / 1e9);
        }
        System.out.println("PASS: " + checked + " 7z fixtures, refusals, damaged input and import");
    }
}
