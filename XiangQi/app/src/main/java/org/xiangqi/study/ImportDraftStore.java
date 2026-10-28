package org.xiangqi.study;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Keeps large editable imports out of Activity instance-state Binder parcels. */
public final class ImportDraftStore {
    private ImportDraftStore() {}
    public static String newToken(){return UUID.randomUUID().toString();}

    public static synchronized void write(Context context,String token,String text)throws Exception {
        File target=file(context,token);
        if(text==null)throw new IllegalArgumentException("Import draft text is missing.");
        if(text.length()>GameRepository.MAX_BYTES)throw new IllegalArgumentException("Import draft exceeds 5 MB.");
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>GameRepository.MAX_BYTES)throw new IllegalArgumentException("Import draft exceeds 5 MB.");
        File directory=target.getParentFile();
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Could not create the private import draft cache.");
        AtomicFile atomic=new AtomicFile(target);FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(bytes);atomic.finishWrite(out);}
        catch(Exception error){if(out!=null)atomic.failWrite(out);throw error;}
    }
    public static synchronized String read(Context context,String token)throws Exception {
        try(InputStream in=new AtomicFile(file(context,token)).openRead()){return GameRepository.read(in);}
    }
    public static synchronized void delete(Context context,String token)throws Exception {
        if(token==null||token.isEmpty())return;
        new AtomicFile(file(context,token)).delete();
    }
    private static File file(Context context,String token)throws IOException {
        if(token==null||!token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("Invalid import draft token.");
        File cache=context.getCacheDir();if(cache==null)throw new IOException("Private import draft cache is unavailable.");
        return new File(new File(cache,"import-drafts"),token+".txt");
    }
}
