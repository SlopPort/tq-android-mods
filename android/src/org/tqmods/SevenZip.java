package org.tqmods;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * Minimal 7z reader: converts a .7z archive into a ZIP for the normal import checks.
 * Supports Copy, LZMA, LZMA2 and Deflate, solid and non-solid archives, compressed headers.
 * Encrypted archives and other methods (BCJ2, PPMd, BZip2) are refused with a plain message.
 * Files the importer would skip anyway are not unpacked.
 */
public final class SevenZip {
    private SevenZip() {}
    static final byte[] MAGIC = {'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    private static final long MAX_OUTPUT = 512L * 1024 * 1024;
    private static final int MAX_FILES = 4096;

    public static final class Unsupported extends IOException { Unsupported(String m) { super(m); } }

    public static boolean is7z(File file) throws IOException {
        byte[] head = new byte[6];
        try (InputStream in = new FileInputStream(file)) {
            int n = 0, r;
            while (n < 6 && (r = in.read(head, n, 6 - n)) > 0) n += r;
            return n == 6 && Arrays.equals(head, MAGIC);
        }
    }

    /** Which entries to unpack; others are written empty so the importer still sees their names. */
    public interface Filter { boolean wanted(String path); }

    private static final class Coder { byte[] id; long numIn, numOut; byte[] props; }
    private static final class Folder {
        Coder[] coders; long[][] bindPairs; long[] packed; long[] unpackSizes;
        long unpackSize() {
            long outs = 0; for (Coder c : coders) outs += c.numOut;
            for (int i = 0; i < outs; i++) {
                boolean bound = false; for (long[] p : bindPairs) if (p[1] == i) bound = true;
                if (!bound) return unpackSizes[i];
            }
            throw new IllegalStateException();
        }
    }
    private static final class Streams {
        long packPos; long[] packSizes = new long[0]; Folder[] folders = new Folder[0];
        int[] subCount; long[] subSizes; long[] subCrc; boolean[] subCrcDefined; long[] folderCrc; boolean[] folderCrcDefined;
    }
    private static final class Entry { String name; boolean hasStream = true, dir; long size; }

    /** Writes the archive's files into a ZIP stream. Returns the number of files. */
    public static int toZip(File archive, OutputStream out, Filter filter) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(archive, "r")) {
            byte[] start = new byte[32];
            raf.readFully(start);
            for (int i = 0; i < 6; i++) if (start[i] != MAGIC[i]) throw new IOException("Not a 7z archive");
            Buf sh = new Buf(start, 12, 32);
            long nextOffset = sh.u64(), nextSize = sh.u64(), nextCrc = sh.u32();
            CRC32 shCrc = new CRC32(); shCrc.update(start, 12, 20);
            if (shCrc.getValue() != (((start[8] & 0xffL)) | ((start[9] & 0xffL) << 8) | ((start[10] & 0xffL) << 16) | ((start[11] & 0xffL) << 24)))
                throw new IOException("7z archive is damaged (start header)");
            if (nextSize <= 0 || nextSize > 64L * 1024 * 1024 || 32 + nextOffset + nextSize > raf.length())
                throw new IOException("7z archive is damaged or incomplete");
            byte[] header = new byte[(int) nextSize];
            raf.seek(32 + nextOffset); raf.readFully(header);
            CRC32 hc = new CRC32(); hc.update(header);
            if (hc.getValue() != nextCrc) throw new IOException("7z archive is damaged (header)");
            Buf h = new Buf(header, 0, header.length);
            int id = h.u8();
            if (id == 0x17) { // Encoded header: the real header is itself packed.
                Streams s = readStreams(h);
                if (s.folders.length == 0) throw new IOException("7z archive is damaged (header)");
                ByteArrayOutputStream decoded = new ByteArrayOutputStream();
                decodeFolder(raf, s, 0, packStart(s, 0), new Sink() {
                    public void write(byte[] b, int off, int len) throws IOException {
                        if (decoded.size() + len > 64 * 1024 * 1024) throw new IOException("7z header too large");
                        decoded.write(b, off, len);
                    }
                }, s.folders[0].unpackSize());
                byte[] real = decoded.toByteArray();
                if (s.folderCrcDefined != null && s.folderCrcDefined[0]) {
                    CRC32 c = new CRC32(); c.update(real);
                    if (c.getValue() != s.folderCrc[0]) throw new IOException("7z archive is damaged (header)");
                }
                h = new Buf(real, 0, real.length);
                id = h.u8();
            }
            if (id != 0x01) throw new IOException("7z archive is damaged (header)");
            Streams main = new Streams();
            Entry[] entries = new Entry[0];
            for (int t; (t = h.u8()) != 0; ) {
                if (t == 0x02) skipArchiveProperties(h);
                else if (t == 0x03) readStreams(h); // Additional streams: unused.
                else if (t == 0x04) main = readStreams(h);
                else if (t == 0x05) entries = readFiles(h);
                else throw new IOException("7z archive is damaged (header)");
            }
            return extract(raf, main, entries, out, filter);
        } catch (IndexOutOfBoundsException | IllegalStateException | NegativeArraySizeException e) {
            throw new IOException("7z archive is damaged");
        }
    }

    private static int extract(RandomAccessFile raf, Streams s, Entry[] entries, OutputStream out, Filter filter) throws IOException {
        // Map files with data onto folders and their substreams, in order.
        int folders = s.folders.length;
        int[] counts = s.subCount != null ? s.subCount : filled(folders, 1);
        int totalSubs = 0; for (int c : counts) totalSubs += c;
        long[] sizes = s.subSizes;
        if (sizes == null) { sizes = new long[folders]; for (int i = 0; i < folders; i++) sizes[i] = s.folders[i].unpackSize(); }
        int withData = 0; for (Entry e : entries) if (e.hasStream) withData++;
        if (withData != totalSubs) throw new IOException("7z archive is damaged (file list)");
        long[] crc = new long[totalSubs]; boolean[] crcDef = new boolean[totalSubs];
        if (s.subCrcDefined != null) { crc = s.subCrc; crcDef = s.subCrcDefined; }
        else if (s.folderCrcDefined != null) { // One file per folder: the folder CRC is the file CRC.
            int k = 0; for (int f = 0; f < folders; f++) for (int j = 0; j < counts[f]; j++, k++)
                if (counts[f] == 1) { crc[k] = s.folderCrc[f]; crcDef[k] = s.folderCrcDefined[f]; }
        }
        ZipOutputStream zip = new ZipOutputStream(out);
        zip.setLevel(Deflater.BEST_SPEED);
        long[] total = {0};
        int files = 0, sub = 0, entry = 0;
        // Files without data (empty files, folders) come in name order with the others; write them first.
        for (Entry e : entries) if (!e.hasStream && !e.dir) { zip.putNextEntry(new ZipEntry(safeName(e.name))); zip.closeEntry(); files++; }
        long packOffset = packStart(s, 0);
        int packIndex = 0;
        for (int f = 0; f < folders; f++) {
            Folder folder = s.folders[f];
            List<Entry> part = new ArrayList<>();
            List<Integer> partSub = new ArrayList<>();
            for (int j = 0; j < counts[f]; j++) {
                while (!entries[entry].hasStream) entry++;
                Entry e = entries[entry++];
                e.size = sizes[sub];
                part.add(e); partSub.add(sub++);
            }
            boolean any = false;
            for (Entry e : part) if (filter.wanted(e.name)) any = true;
            if (any) {
                Splitter split = new Splitter(zip, part, partSub, crc, crcDef, filter, total);
                decodeFolder(raf, s, f, packOffset, split, folder.unpackSize());
                split.finish();
            } else {
                for (Entry e : part) { zip.putNextEntry(new ZipEntry(safeName(e.name))); zip.closeEntry(); }
            }
            files += part.size();
            for (int p = 0; p < folder.packed.length; p++) packOffset += s.packSizes[packIndex++];
        }
        zip.finish();
        return files;
    }

    private static String safeName(String name) { return name.replace('\\', '/'); }
    private static int[] filled(int n, int v) { int[] a = new int[n]; Arrays.fill(a, v); return a; }
    private static long packStart(Streams s, int folder) {
        long pos = 32 + s.packPos; int index = 0;
        for (int f = 0; f < folder; f++) for (int p = 0; p < s.folders[f].packed.length; p++) pos += s.packSizes[index++];
        return pos;
    }

    /** Routes one folder's output into consecutive ZIP entries and checks each file's CRC. */
    private static final class Splitter implements Sink {
        final ZipOutputStream zip; final List<Entry> part; final List<Integer> subs; final long[] crc; final boolean[] crcDef;
        final Filter filter; final long[] total; int index = -1; long left; boolean keep; final CRC32 check = new CRC32();
        Splitter(ZipOutputStream zip, List<Entry> part, List<Integer> subs, long[] crc, boolean[] crcDef, Filter filter, long[] total) throws IOException {
            this.zip = zip; this.part = part; this.subs = subs; this.crc = crc; this.crcDef = crcDef; this.filter = filter; this.total = total;
            next();
        }
        private void next() throws IOException {
            if (index >= 0) close();
            while (++index < part.size()) {
                Entry e = part.get(index);
                zip.putNextEntry(new ZipEntry(safeName(e.name)));
                keep = filter.wanted(e.name); left = e.size; check.reset();
                if (keep && (total[0] += e.size) > MAX_OUTPUT) throw new IOException("Unpacked mod exceeds 512 MiB limit");
                if (left > 0) return;
                close();
            }
        }
        private void close() throws IOException {
            int s = subs.get(index);
            if (keep && crcDef[s] && check.getValue() != crc[s]) throw new IOException("7z archive is damaged (CRC)");
            zip.closeEntry();
        }
        public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (index >= part.size()) throw new IOException("7z archive is damaged (sizes)");
                int n = (int) Math.min(len, left);
                if (keep) { zip.write(b, off, n); check.update(b, off, n); }
                off += n; len -= n; left -= n;
                if (left == 0) next();
            }
        }
        void finish() throws IOException { if (index < part.size()) throw new IOException("7z archive is damaged (sizes)"); }
    }

    // ---- Header parsing ----

    private static final class Buf {
        final byte[] b; int p; final int end;
        Buf(byte[] b, int p, int end) { this.b = b; this.p = p; this.end = end; }
        int u8() { if (p >= end) throw new IndexOutOfBoundsException(); return b[p++] & 0xff; }
        long u32() { long v = 0; for (int i = 0; i < 4; i++) v |= (long) u8() << (8 * i); return v; }
        long u64() { long v = 0; for (int i = 0; i < 8; i++) v |= (long) u8() << (8 * i); return v; }
        long num() {
            int first = u8(), mask = 0x80; long v = 0;
            for (int i = 0; i < 8; i++) {
                if ((first & mask) == 0) return v | ((long) (first & (mask - 1)) << (8 * i));
                v |= (long) u8() << (8 * i); mask >>>= 1;
            }
            return v;
        }
        int count(int max) { long n = num(); if (n < 0 || n > max) throw new IndexOutOfBoundsException(); return (int) n; }
        void skip(long n) { if (n < 0 || n > end - p) throw new IndexOutOfBoundsException(); p += (int) n; }
        boolean[] bits(int n) {
            boolean[] v = new boolean[n]; int mask = 0, cur = 0;
            for (int i = 0; i < n; i++) { if (mask == 0) { cur = u8(); mask = 0x80; } v[i] = (cur & mask) != 0; mask >>>= 1; }
            return v;
        }
        boolean[] defined(int n) { int all = u8(); if (all != 0) { boolean[] v = new boolean[n]; Arrays.fill(v, true); return v; } return bits(n); }
    }

    private static void skipArchiveProperties(Buf h) { for (int t; (t = h.u8()) != 0; ) h.skip(h.num()); }

    private static Streams readStreams(Buf h) throws IOException {
        Streams s = new Streams();
        for (int t; (t = h.u8()) != 0; ) {
            if (t == 0x06) { // PackInfo
                s.packPos = h.num();
                int n = h.count(1 << 20);
                s.packSizes = new long[n];
                for (int k; (k = h.u8()) != 0; ) {
                    if (k == 0x09) for (int i = 0; i < n; i++) s.packSizes[i] = h.num();
                    else if (k == 0x0A) { boolean[] d = h.defined(n); for (boolean x : d) if (x) h.u32(); }
                    else throw new IOException("7z archive is damaged (pack info)");
                }
            } else if (t == 0x07) { // UnpackInfo
                if (h.u8() != 0x0B) throw new IOException("7z archive is damaged (folders)");
                int n = h.count(1 << 20);
                if (h.u8() != 0) throw new IOException("7z archive is damaged (folders)");
                s.folders = new Folder[n];
                for (int i = 0; i < n; i++) s.folders[i] = readFolder(h);
                if (h.u8() != 0x0C) throw new IOException("7z archive is damaged (folders)");
                for (Folder f : s.folders) {
                    long outs = 0; for (Coder c : f.coders) outs += c.numOut;
                    f.unpackSizes = new long[(int) outs];
                    for (int i = 0; i < outs; i++) f.unpackSizes[i] = h.num();
                }
                for (int k; (k = h.u8()) != 0; ) {
                    if (k == 0x0A) {
                        s.folderCrcDefined = h.defined(n); s.folderCrc = new long[n];
                        for (int i = 0; i < n; i++) if (s.folderCrcDefined[i]) s.folderCrc[i] = h.u32();
                    } else throw new IOException("7z archive is damaged (folders)");
                }
            } else if (t == 0x08) { // SubStreamsInfo
                int n = s.folders.length;
                s.subCount = filled(n, 1);
                int k = h.u8();
                if (k == 0x0D) { for (int i = 0; i < n; i++) s.subCount[i] = h.count(MAX_FILES); k = h.u8(); }
                int total = 0; for (int c : s.subCount) total += c;
                if (total > MAX_FILES) throw new IOException("Too many files in 7z archive");
                s.subSizes = new long[total];
                int idx = 0;
                for (int i = 0; i < n; i++) {
                    if (s.subCount[i] == 0) continue;
                    long sum = 0;
                    if (k == 0x09) for (int j = 1; j < s.subCount[i]; j++) { long v = h.num(); s.subSizes[idx++] = v; sum += v; }
                    long rest = s.folders[i].unpackSize() - sum;
                    if (rest < 0) throw new IOException("7z archive is damaged (sizes)");
                    s.subSizes[idx++] = rest;
                }
                if (k == 0x09) k = h.u8();
                // Files whose CRC is not already given by a single-file folder.
                int unknown = 0;
                for (int i = 0; i < n; i++) if (s.subCount[i] != 1 || s.folderCrcDefined == null || !s.folderCrcDefined[i]) unknown += s.subCount[i];
                s.subCrc = new long[total]; s.subCrcDefined = new boolean[total];
                while (k != 0) {
                    if (k == 0x0A) {
                        boolean[] d = h.defined(unknown); long[] v = new long[unknown];
                        for (int i = 0; i < unknown; i++) if (d[i]) v[i] = h.u32();
                        int a = 0, b = 0;
                        for (int i = 0; i < n; i++) {
                            if (s.subCount[i] == 1 && s.folderCrcDefined != null && s.folderCrcDefined[i]) {
                                s.subCrcDefined[a] = true; s.subCrc[a++] = s.folderCrc[i];
                            } else for (int j = 0; j < s.subCount[i]; j++, a++, b++) { s.subCrcDefined[a] = d[b]; s.subCrc[a] = v[b]; }
                        }
                    } else h.skip(h.num());
                    k = h.u8();
                }
                if (s.folderCrcDefined != null) { // Single-file folders: the folder CRC is the file CRC.
                    for (int i = 0, a = 0; i < n; i++) for (int j = 0; j < s.subCount[i]; j++, a++)
                        if (s.subCount[i] == 1 && s.folderCrcDefined[i]) { s.subCrcDefined[a] = true; s.subCrc[a] = s.folderCrc[i]; }
                }
            } else throw new IOException("7z archive is damaged (streams)");
        }
        return s;
    }

    private static Folder readFolder(Buf h) throws IOException {
        Folder f = new Folder();
        int n = h.count(64);
        f.coders = new Coder[n];
        long ins = 0, outs = 0;
        for (int i = 0; i < n; i++) {
            int flag = h.u8();
            if ((flag & 0x80) != 0) throw new Unsupported("7z alternative coders");
            Coder c = new Coder();
            c.id = new byte[flag & 0x0F];
            for (int j = 0; j < c.id.length; j++) c.id[j] = (byte) h.u8();
            if ((flag & 0x10) != 0) { c.numIn = h.count(64); c.numOut = h.count(64); } else { c.numIn = 1; c.numOut = 1; }
            if ((flag & 0x20) != 0) { int len = h.count(1 << 16); c.props = new byte[len]; for (int j = 0; j < len; j++) c.props[j] = (byte) h.u8(); }
            ins += c.numIn; outs += c.numOut; f.coders[i] = c;
        }
        f.bindPairs = new long[(int) (outs - 1)][];
        for (int i = 0; i < outs - 1; i++) f.bindPairs[i] = new long[]{h.num(), h.num()};
        long packed = ins - (outs - 1);
        f.packed = new long[(int) packed];
        if (packed == 1) {
            for (int i = 0; i < ins; i++) { boolean bound = false; for (long[] p : f.bindPairs) if (p[0] == i) bound = true; if (!bound) { f.packed[0] = i; break; } }
        } else for (int i = 0; i < packed; i++) f.packed[i] = h.num();
        return f;
    }

    private static Entry[] readFiles(Buf h) throws IOException {
        int n = h.count(MAX_FILES);
        Entry[] e = new Entry[n];
        for (int i = 0; i < n; i++) e[i] = new Entry();
        boolean[] empty = new boolean[n], emptyFile = null;
        for (int t; (t = h.u8()) != 0; ) {
            long size = h.num();
            int endPos = h.p + (int) size;
            if (size < 0 || size > h.end - h.p) throw new IOException("7z archive is damaged (file list)");
            if (t == 0x0E) empty = h.bits(n);
            else if (t == 0x0F) { int c = 0; for (boolean x : empty) if (x) c++; emptyFile = h.bits(c); }
            else if (t == 0x11) {
                if (h.u8() != 0) throw new Unsupported("7z external names");
                for (int i = 0; i < n; i++) {
                    int s = h.p; while (h.b[h.p] != 0 || h.b[h.p + 1] != 0) h.p += 2;
                    if (h.p > endPos) throw new IOException("7z archive is damaged (names)");
                    e[i].name = new String(h.b, s, h.p - s, StandardCharsets.UTF_16LE); h.p += 2;
                }
            }
            h.p = endPos;
        }
        for (int i = 0, k = 0; i < n; i++) {
            if (e[i].name == null) throw new IOException("7z archive is damaged (names)");
            if (empty[i]) { e[i].hasStream = false; e[i].dir = emptyFile == null || !emptyFile[k]; k++; }
        }
        return e;
    }

    // ---- Decoding ----

    interface Sink { void write(byte[] b, int off, int len) throws IOException; }

    private static void decodeFolder(RandomAccessFile raf, Streams s, int index, long packOffset, Sink sink, long size) throws IOException {
        Folder f = s.folders[index];
        if (f.coders.length != 1 || f.packed.length != 1) throw new Unsupported("7z filters (for example BCJ2)");
        Coder c = f.coders[0];
        int packIndex = 0; for (int i = 0; i < index; i++) packIndex += s.folders[i].packed.length;
        long packSize = s.packSizes[packIndex];
        InputStream in = new BufferedInputStream(new RafStream(raf, packOffset, packSize), 1 << 16);
        String id = hex(c.id);
        switch (id) {
            case "00": copy(in, sink, size); break;
            case "030101": new Lzma(sink, size).decodeLzma(in, c.props); break;
            case "21": new Lzma(sink, size).decodeLzma2(in, c.props); break;
            case "040108": copy(new InflaterInputStream(in, new Inflater(true), 1 << 16), sink, size); break;
            case "06f10701": throw new Unsupported("password-protected 7z");
            default: throw new Unsupported("7z compression method " + id);
        }
    }

    private static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x & 0xff)); return s.toString(); }

    private static void copy(InputStream in, Sink sink, long size) throws IOException {
        byte[] buf = new byte[65536];
        while (size > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, size));
            if (n < 0) throw new IOException("7z archive is damaged or incomplete");
            sink.write(buf, 0, n); size -= n;
        }
    }

    private static final class RafStream extends InputStream {
        final RandomAccessFile raf; long pos, left;
        RafStream(RandomAccessFile raf, long pos, long left) { this.raf = raf; this.pos = pos; this.left = left; }
        public int read() throws IOException { byte[] b = new byte[1]; return read(b, 0, 1) < 0 ? -1 : b[0] & 0xff; }
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) return -1;
            raf.seek(pos);
            int n = raf.read(b, off, (int) Math.min(len, left));
            if (n > 0) { pos += n; left -= n; }
            return n;
        }
    }

    /** LZMA / LZMA2 decoder written from the LZMA specification. */
    static final class Lzma {
        private final Sink sink; private long left;
        private byte[] dict; private int dictPos, dictFlushed; private boolean dictFull; private long written;
        private int lc, lp, pb, dictSize;
        private InputStream in; private int range, code;
        private short[] isMatch, isRep, isRepG0, isRepG1, isRepG2, isRep0Long, literal, posSlot, posSpecial, align;
        private Len len, repLen;
        private int state, rep0, rep1, rep2, rep3;

        Lzma(Sink sink, long size) { this.sink = sink; this.left = size; }

        void decodeLzma(InputStream in, byte[] props) throws IOException {
            if (props == null || props.length < 5) throw new IOException("7z archive is damaged (LZMA)");
            setProps(props[0] & 0xff);
            long d = (props[1] & 0xffL) | (props[2] & 0xffL) << 8 | (props[3] & 0xffL) << 16 | (props[4] & 0xffL) << 24;
            allocate(d);
            this.in = in; resetState(); initRange();
            decode(left, true);
            flush();
            if (left != 0) throw new IOException("7z archive is damaged (LZMA size)");
        }

        void decodeLzma2(InputStream in, byte[] props) throws IOException {
            if (props == null || props.length < 1 || (props[0] & 0xff) > 40) throw new IOException("7z archive is damaged (LZMA2)");
            int p = props[0] & 0xff;
            long d = p == 40 ? 0xFFFFFFFFL : (2L | (p & 1)) << (p / 2 + 11);
            allocate(d);
            this.in = in;
            boolean needDictReset = true, needProps = true;
            while (true) {
                int c = read();
                if (c == 0) break;
                if (c == 1 || c == 2) {
                    if (c == 1) { resetDict(); needDictReset = false; }
                    else if (needDictReset) throw new IOException("7z archive is damaged (LZMA2)");
                    int n = ((read() << 8) | read()) + 1;
                    for (int i = 0; i < n; i++) put((byte) read());
                    continue;
                }
                if (c < 0x80) throw new IOException("7z archive is damaged (LZMA2)");
                int unpacked = ((c & 0x1F) << 16) + ((read() << 8) | read()) + 1;
                int packed = ((read() << 8) | read()) + 1;
                int mode = (c >>> 5) & 3;
                if (mode == 3) { resetDict(); needDictReset = false; }
                else if (needDictReset) throw new IOException("7z archive is damaged (LZMA2)");
                if (mode >= 2) { setProps(read()); needProps = false; }
                else if (needProps) throw new IOException("7z archive is damaged (LZMA2)");
                if (mode >= 1) resetState();
                initRange();
                decode(unpacked, false);
            }
            flush();
            if (left != 0) throw new IOException("7z archive is damaged (LZMA2 size)");
        }

        private int read() throws IOException { int b = in.read(); if (b < 0) throw new IOException("7z archive is damaged or incomplete"); return b; }

        private void setProps(int b) throws IOException {
            if (b >= 9 * 5 * 5) throw new IOException("7z archive is damaged (LZMA properties)");
            lc = b % 9; b /= 9; lp = b % 5; pb = b / 5;
            literal = probs(0x300 << (lc + lp));
        }

        private void allocate(long d) throws IOException {
            long need = Math.max(Math.min(d, left), 4096);
            if (need > 256L * 1024 * 1024) throw new IOException("7z dictionary too large for this phone");
            dictSize = (int) need;
            try { dict = new byte[dictSize]; } catch (OutOfMemoryError e) { throw new IOException("Not enough memory to unpack this 7z archive"); }
            dictPos = dictFlushed = 0; dictFull = false;
        }

        private static short[] probs(int n) { short[] p = new short[n]; Arrays.fill(p, (short) 1024); return p; }

        private void resetState() {
            isMatch = probs(12 << 4); isRep = probs(12); isRepG0 = probs(12); isRepG1 = probs(12); isRepG2 = probs(12);
            isRep0Long = probs(12 << 4); posSlot = probs(4 << 6); posSpecial = probs(115); align = probs(16);
            if (literal == null) literal = probs(0x300 << (lc + lp)); else Arrays.fill(literal, (short) 1024);
            len = new Len(); repLen = new Len();
            state = 0; rep0 = rep1 = rep2 = rep3 = 0;
        }

        private void initRange() throws IOException {
            if (read() != 0) throw new IOException("7z archive is damaged (LZMA stream)");
            code = 0; range = 0xFFFFFFFF;
            for (int i = 0; i < 4; i++) code = (code << 8) | read();
            if (code == range) throw new IOException("7z archive is damaged (LZMA stream)");
        }

        private int bit(short[] p, int i) throws IOException {
            int prob = p[i];
            int bound = (range >>> 11) * prob;
            int b;
            if (Integer.compareUnsigned(code, bound) < 0) { range = bound; p[i] = (short) (prob + ((2048 - prob) >>> 5)); b = 0; }
            else { range -= bound; code -= bound; p[i] = (short) (prob - (prob >>> 5)); b = 1; }
            if (Integer.compareUnsigned(range, 1 << 24) < 0) { range <<= 8; code = (code << 8) | read(); }
            return b;
        }
        private int direct(int n) throws IOException {
            int res = 0;
            for (int i = 0; i < n; i++) {
                range >>>= 1;
                int t = Integer.compareUnsigned(code, range) >= 0 ? 1 : 0;
                if (t == 1) code -= range;
                res = (res << 1) | t;
                if (Integer.compareUnsigned(range, 1 << 24) < 0) { range <<= 8; code = (code << 8) | read(); }
            }
            return res;
        }
        private int tree(short[] p, int base, int bits) throws IOException {
            int m = 1; for (int i = 0; i < bits; i++) m = (m << 1) | bit(p, base + m); return m - (1 << bits);
        }
        private int reverse(short[] p, int base, int bits) throws IOException {
            int m = 1, sym = 0;
            for (int i = 0; i < bits; i++) { int b = bit(p, base + m); m = (m << 1) | b; sym |= b << i; }
            return sym;
        }

        private final class Len {
            final short[] choice = probs(2), low = probs(16 << 3), mid = probs(16 << 3), high = probs(256);
            int decode(int posState) throws IOException {
                if (bit(choice, 0) == 0) return tree(low, posState << 3, 3);
                if (bit(choice, 1) == 0) return 8 + tree(mid, posState << 3, 3);
                return 16 + tree(high, 0, 8);
            }
        }

        // Dictionary: a ring buffer; output goes to the sink as it fills.
        private void resetDict() throws IOException { flush(); dictPos = dictFlushed = 0; dictFull = false; written = 0; }
        private void put(byte b) throws IOException {
            if (left <= 0) throw new IOException("7z archive is damaged (too much data)");
            dict[dictPos++] = b; left--; written++;
            if (dictPos == dictSize) { flush(); dictPos = 0; dictFlushed = 0; dictFull = true; }
        }
        private byte get(int dist) { int i = dictPos - dist - 1; if (i < 0) i += dictSize; return dict[i]; }
        private void flush() throws IOException {
            if (dictPos > dictFlushed) { sink.write(dict, dictFlushed, dictPos - dictFlushed); dictFlushed = dictPos; }
        }

        private void decode(long limit, boolean allowEndMarker) throws IOException {
            int pbMask = (1 << pb) - 1, lpMask = (1 << lp) - 1;
            long end = written + limit;
            while (written < end) {
                int posState = (int) written & pbMask;
                if (bit(isMatch, (state << 4) + posState) == 0) {
                    int prev = written > 0 || dictFull || dictPos > 0 ? get(0) & 0xff : 0;
                    int base = 0x300 * ((((int) written & lpMask) << lc) + (prev >>> (8 - lc)));
                    int sym = 1;
                    if (state >= 7) {
                        int match = get(rep0) & 0xff;
                        do {
                            int mb = (match >>> 7) & 1; match <<= 1;
                            int b = bit(literal, base + ((1 + mb) << 8) + sym);
                            sym = (sym << 1) | b;
                            if (mb != b) break;
                        } while (sym < 0x100);
                    }
                    while (sym < 0x100) sym = (sym << 1) | bit(literal, base + sym);
                    put((byte) sym);
                    state = state < 4 ? 0 : state < 10 ? state - 3 : state - 6;
                    continue;
                }
                int length;
                if (bit(isRep, state) != 0) {
                    if (written == 0 && !dictFull && dictPos == 0) throw new IOException("7z archive is damaged (LZMA data)");
                    if (bit(isRepG0, state) == 0) {
                        if (bit(isRep0Long, (state << 4) + posState) == 0) {
                            state = state < 7 ? 9 : 11; put(get(rep0)); continue;
                        }
                    } else {
                        int dist;
                        if (bit(isRepG1, state) == 0) dist = rep1;
                        else { if (bit(isRepG2, state) == 0) dist = rep2; else { dist = rep3; rep3 = rep2; } rep2 = rep1; }
                        rep1 = rep0; rep0 = dist;
                    }
                    length = repLen.decode(posState);
                    state = state < 7 ? 8 : 11;
                } else {
                    rep3 = rep2; rep2 = rep1; rep1 = rep0;
                    length = len.decode(posState);
                    state = state < 7 ? 7 : 10;
                    rep0 = distance(length);
                    if (rep0 == -1) {
                        if (allowEndMarker) return;
                        throw new IOException("7z archive is damaged (LZMA end)");
                    }
                    long avail = dictFull ? dictSize : dictPos;
                    if (Integer.toUnsignedLong(rep0) >= avail) throw new IOException("7z archive is damaged (LZMA distance)");
                }
                length += 2;
                if (length > end - written) throw new IOException("7z archive is damaged (LZMA length)");
                for (int i = 0; i < length; i++) put(get(rep0));
            }
        }

        private int distance(int length) throws IOException {
            int lenState = Math.min(length, 3);
            int slot = tree(posSlot, lenState << 6, 6);
            if (slot < 4) return slot;
            int direct = (slot >>> 1) - 1;
            int dist = (2 | (slot & 1)) << direct;
            if (slot < 14) return dist + reverse(posSpecial, dist - slot, direct);
            dist += direct(direct - 4) << 4;
            return dist + reverse(align, 0, 4);
        }
    }
}
