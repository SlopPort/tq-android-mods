import org.tqmods.ModStore;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public final class ModStoreTest {
    static byte[] arz() { byte[] b=new byte[24]; b[0]=4; b[2]=3; return b; }
    static byte[] zip(String... names) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(bytes)) {
            for(String name:names) { z.putNextEntry(new ZipEntry(name)); z.write(arz()); z.closeEntry(); }
        }
        return bytes.toByteArray();
    }
    static void reject(ModStore store, byte[] data, String name) throws Exception {
        int before=store.installed().length;
        try { store.importFile(new ByteArrayInputStream(data),name); throw new AssertionError("Accepted invalid import: "+name); }
        catch(IOException expected) {}
        if(store.installed().length!=before) throw new AssertionError("Partial import became visible");
    }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("tqmods-test-").toFile();
        ModStore store=new ModStore(root);
        store.importFile(new ByteArrayInputStream(arz()),"Example.arz");
        store.importFile(new ByteArrayInputStream(zip("example/database/mod.arz","example/resources/items.arc","example/resources/Maps/main.map")),"Example.zip");
        reject(store,zip("../escape.arz"),"bad.zip");
        reject(store,zip("..\\escape.arz"),"bad.zip");
        reject(store,zip("/escape.arz"),"bad.zip");
        reject(store,zip("C:/escape.arz"),"bad.zip");
        reject(store,zip("database/mod.arz","DATABASE/MOD.ARZ"),"bad.zip");
        reject(store,zip("a.arz","b.arz"),"bad.zip");
        reject(store,zip("payload.so","a.arz"),"bad.zip");
        reject(store,new byte[24],"bad.arz");
        reject(store,new byte[0],"bad.zip");
        if(args.length>0) try(InputStream real=new FileInputStream(args[0])) { store.importFile(real,"LootPlus.arz"); }
        for(File f:root.listFiles()) if(f.getName().startsWith(".")) throw new AssertionError("Staging leak");
        File mod=store.installed()[0];
        ModStore.describe(mod,"  My mod\t ","More loot\nfor testing");
        if(!ModStore.title(mod).equals("My mod")||!ModStore.note(mod).equals("More loot\nfor testing")) throw new AssertionError("Rename/describe");
        ModStore.describe(mod,"My mod","");
        if(!ModStore.note(mod).isEmpty()||new File(mod,".note").exists()) throw new AssertionError("Empty description kept");
        try{ModStore.describe(mod," ","x");throw new AssertionError("Empty name accepted");}catch(IOException expected){}
        try{ModStore.describe(mod,"x",new String(new char[ModStore.MAX_NOTE+1]).replace('\0','a'));throw new AssertionError("Long note accepted");}catch(IOException expected){}
        for(File f:mod.listFiles()) if(f.getName().endsWith(".new")) throw new AssertionError("Temp note left");
        System.out.println("PASS: imports, real specimen (if supplied), traversal, duplicates, multiple databases, code payload and header rejection; rollback; rename and describe.");
    }
}
