package org.tqmods;
import java.io.*;

/** Bounded private copy: selection and import read the same bytes. */
public final class ImportSnapshot implements Closeable {
    private final File file;
    public final String name;
    private ImportSnapshot(File file, String name) { this.file=file; this.name=name; }
    public static ImportSnapshot create(File directory, InputStream input, String name) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create import cache");
        File file=File.createTempFile("mod-", ".input", directory);
        boolean ok=false;
        try {
            try(OutputStream out=new FileOutputStream(file)) {
                byte[] bytes=new byte[65536]; long total=0; int n;
                while((n=input.read(bytes))!=-1) {
                    total+=n;
                    if(total>512L*1024*1024) throw new IOException("Input exceeds 512 MiB limit");
                    out.write(bytes,0,n);
                }
            }
            ok=true; return new ImportSnapshot(file,name);
        } finally { if(!ok && file.exists() && !file.delete()) file.deleteOnExit(); }
    }
    public boolean is7z() throws IOException { return SevenZip.is7z(file); }
    /** Unpacks a 7z input into a ZIP snapshot next to it and removes this one. */
    public ImportSnapshot unpack7z(SevenZip.Filter wanted) throws IOException {
        File zip=File.createTempFile("mod-", ".input", file.getParentFile());
        boolean ok=false;
        try {
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(zip), 65536)) { SevenZip.toZip(file, out, wanted); }
            String base=name==null ? "mod" : name.replaceFirst("(?i)\\.(7z|zip)$", "");
            ok=true; close();
            return new ImportSnapshot(zip, base + ".zip");
        } finally { if(!ok && zip.exists() && !zip.delete()) zip.deleteOnExit(); }
    }
    public InputStream open() throws IOException { return new FileInputStream(file); }
    public void close() throws IOException {
        if(file.exists() && !file.delete()) throw new IOException("Cannot remove temporary import copy");
    }
}
