import org.tqmods.Combos;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Combinations: storage, reuse, record names from ARZ v3 and conflict counts.
 *  Usage: java CombosTest [two or more real ARZ files to print their shared records] */
public final class CombosTest {
    static File arz(File dir, String name, String... records) throws IOException {
        ByteArrayOutputStream strings = new ByteArrayOutputStream(), table = new ByteArrayOutputStream();
        strings.write(le(records.length));
        for (String r : records) { byte[] b = r.getBytes(StandardCharsets.ISO_8859_1); strings.write(le(b.length)); strings.write(b); }
        for (int i = 0; i < records.length; i++) { table.write(le(i)); table.write(le(3)); table.write("cls".getBytes()); table.write(new byte[16]); }
        ByteBuffer head = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        head.putShort((short) 4).putShort((short) 3).putInt(24).putInt(table.size()).putInt(records.length).putInt(24 + table.size()).putInt(strings.size());
        File f = new File(dir, name);
        try (OutputStream out = new FileOutputStream(f)) { out.write(head.array()); table.writeTo(out); strings.writeTo(out); }
        return f;
    }
    static byte[] le(int v) { return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array(); }

    public static void main(String[] args) throws Exception {
        File tmp = Files.createTempDirectory("tqmods-combo-").toFile();
        Set<String> a = Combos.records(arz(tmp, "a.arz", "Records\\Item\\A.dbr", "records\\shared\\x.dbr", "records\\shared\\y.dbr"));
        Set<String> b = Combos.records(arz(tmp, "b.arz", "records/shared/X.dbr", "records\\b.dbr"));
        Set<String> c = Combos.records(arz(tmp, "c.arz", "records\\c.dbr"));
        if (!a.contains("records/item/a.dbr")) throw new AssertionError("Names not normalised: " + a);
        List<int[]> rows = Combos.conflicts(Arrays.asList(a, b, c));
        if (rows.size() != 1 || rows.get(0)[0] != 0 || rows.get(0)[1] != 1 || rows.get(0)[2] != 1) throw new AssertionError("Conflict count");
        File bad = new File(tmp, "bad.arz"); Files.write(bad.toPath(), new byte[] {1, 2, 3, 4});
        try { Combos.records(bad); throw new AssertionError("Accepted bad ARZ"); } catch (IOException expected) { }
        byte[] cut = Arrays.copyOf(Files.readAllBytes(new File(tmp, "a.arz").toPath()), 40);
        File damaged = new File(tmp, "cut.arz"); Files.write(damaged.toPath(), cut);
        try { Combos.records(damaged); throw new AssertionError("Accepted damaged ARZ"); } catch (IOException expected) { }

        Combos store = new Combos(new File(tmp, "combos"));
        String m1 = UUID.randomUUID().toString(), m2 = UUID.randomUUID().toString();
        String id = store.create(Arrays.asList(m1, m2), "One + Two");
        if (!store.has(id) || !store.members(id).equals(Arrays.asList(m1, m2))) throw new AssertionError("Members");
        if (!store.create(Arrays.asList(m1, m2), "again").equals(id)) throw new AssertionError("Same set not reused");
        if (store.create(Arrays.asList(m2, m1), "Two + One").equals(id)) throw new AssertionError("Order ignored");
        try { store.create(Arrays.asList(m1), "x"); throw new AssertionError("One mod accepted"); } catch (IOException expected) { }
        try { store.create(Arrays.asList(m1, m1), "x"); throw new AssertionError("Repeat accepted"); } catch (IOException expected) { }
        store.remove(id);
        if (store.has(id) || store.all().length != 1) throw new AssertionError("Remove");

        if (args.length >= 2) {
            List<Set<String>> real = new ArrayList<>();
            for (String p : args) real.add(Combos.records(new File(p)));
            for (int[] r : Combos.conflicts(real)) System.out.println(args[r[0]] + " + " + args[r[1]] + ": " + r[2] + " shared records");
        }
        System.out.println("PASS: combination storage and reuse, ARZ record names, conflict counts, damaged ARZ refused");
    }
}
