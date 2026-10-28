package org.xiangqi.study;

import android.content.Context;
import android.content.ContextWrapper;
import android.test.InstrumentationTestCase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Regression checks for persistence/import defects discovered by the full audit. */
public final class AuditRepositoryTest extends InstrumentationTestCase {
    private File directory;
    private Context storage() {
        Context target=getInstrumentation().getTargetContext();
        directory=new File(target.getCacheDir(),"repository-audit-"+System.nanoTime());
        assertTrue(directory.mkdirs());
        return new ContextWrapper(target) {
            @Override public Context getApplicationContext(){return this;}
            @Override public File getFilesDir(){return directory;}
        };
    }
    @Override protected void tearDown() throws Exception {
        if(directory!=null){File[] files=directory.listFiles();if(files!=null)for(File file:files)assertTrue(file.delete());assertTrue(directory.delete());}
        super.tearDown();
    }
    private static String pgn(String name,String moves) {
        return "[Event \""+name+"\"]\n[Red \"Audit Red\"]\n[Black \"Audit Black\"]\n[Result \"*\"]\n\n"+moves+" *\n";
    }
    public void testStaleRepositoryCannotOverwriteNewerImportedGames() throws Exception {
        Context context=storage(); GameRepository first=new GameRepository(context),second=new GameRepository(context);
        int initial=first.load().size();second.load();
        String a=pgn("First Activity", "1. b2e2 b9c7 2. a0a1");
        String b=pgn("Recreated Activity", "1. h2e2 h9g7 2. i0i1");
        assertEquals(1,first.importText(a));assertEquals(1,second.importText(b));
        assertEquals(initial+2,new GameRepository(context).load().size());
        assertEquals("Duplicate imports must see another instance's committed data",0,second.importText(a));
        assertEquals(initial+2,new GameRepository(context).load().size());
    }
    public void testTrailingJsonContentRejectsWholeImport() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);int initial=repo.load().size();
        JSONObject game=new JSONObject().put("red","Trailing content fixture").put("result","*")
            .put("moves",new JSONArray(new String[]{"b2e2","b9c7","a0a1"}));
        String text=new JSONArray().put(game).toString();
        try{repo.importText(text+"\nnot part of JSON");fail("A malformed file must not import a valid prefix");}
        catch(IllegalArgumentException expected){}
        assertEquals(initial,new GameRepository(context).load().size());
        assertEquals(1,repo.importText(text+" \r\n\t"));
    }
    public void testPgnCannotSaveGameThatJsonReloadWouldReject() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);int initial=repo.load().size();
        StringBuilder text=new StringBuilder("[Event \"Overlong but legal fixture\"]\n[FEN \"4k4/9/9/9/4p4/9/9/9/9/4K4 w - - 0 1\"]\n[Result \"*\"]\n\n");
        for(int i=0;i<501;i++)text.append("e0d0 e9f9 d0e0 f9e9 ");text.append('*');
        try{repo.importText(text.toString());fail("PGN and persisted JSON must enforce the same 2,000-ply limit");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("2,000"));}
        assertEquals(initial,new GameRepository(context).load().size());
    }
    public void testPublicImportEnforcesByteLimitBeforeSaving() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);int initial=repo.load().size();
        char[] oversized=new char[GameRepository.MAX_BYTES+1];Arrays.fill(oversized,'x');
        try{repo.importText(new String(oversized));fail("The repository must enforce the limit independently of the picker/paste UI");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("5 MB"));}
        assertEquals(initial,new GameRepository(context).load().size());
    }
    public void testInterruptedAtomicWriteRestoresCommittedLibrary() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);int initial=repo.load().size();
        assertEquals(1,repo.importText(pgn("Saved before interruption","1. b2e2 b9c7 2. a0a1")));
        File base=new File(directory,"imported-games.json"),backup=new File(base.getPath()+".bak");
        assertTrue(base.renameTo(backup));
        try(FileOutputStream out=new FileOutputStream(base)){out.write("interrupted write".getBytes(StandardCharsets.UTF_8));}
        assertEquals(initial+1,new GameRepository(context).load().size());assertFalse(backup.exists());
    }
    public void testOversizedMetadataCannotReachLibraryViews() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);int initial=repo.load().size();
        char[] oversized=new char[1025];Arrays.fill(oversized,'x');
        String longEvent=new String(oversized);
        JSONObject game=new JSONObject().put("event",longEvent).put("result","*").put("moves",new JSONArray(new String[]{"b2e2","b9c7","a0a1"}));
        for(String text:new String[]{new JSONArray().put(game).toString(),pgn(longEvent,"1. b2e2 b9c7 2. a0a1")}) {
            try{repo.importText(text);fail("Excessively long metadata must reject the whole import");}
            catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("metadata limit"));}
        }
        assertEquals(initial,new GameRepository(context).load().size());
    }
    public void testDeepJsonRejectsBeforeRecursiveParserAndPreservesLibrary() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);int initial=repo.load().size();
        for(int depth:new int[]{3000,10000}) {
            String text="{\"games\":"+nestedArrays(depth)+"}";
            assertTrue(text.length()<GameRepository.MAX_BYTES);
            try{repo.importText(text);fail("Deep JSON must reject before Android's recursive parser");}
            catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("nesting"));}
            assertEquals(initial,new GameRepository(context).load().size());
        }
        // Android accepts unquoted literals; an embedded quote must not hide nesting.
        for(String quote:new String[]{"'","\""}) {
            String text="{ignored:can"+quote+"ary,games:"+nestedArrays(10000)+"}";
            try{repo.importText(text);fail("A quote inside an unquoted literal must not bypass the nesting bound");}
            catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("nesting"));}
        }
        assertEquals(initial,new GameRepository(context).load().size());
    }
    public void testJsonDepthIgnoresQuotedBracketsAndAllowsBoundary() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);int initial=repo.load().size();
        JSONObject game=new JSONObject().put("red","Quoted JSON depth fixture").put("result","*")
            .put("notes","Escaped quote: \" and escaped slash: \\ "+nestedArrays(3000))
            .put("moves",new JSONArray(new String[]{"b2e2","b9c7","a0a1"}));
        assertEquals(1,repo.importText(new JSONObject().put("games",new JSONArray().put(game)).toString()));
        // Three schema containers plus 29 arrays reaches exactly the permitted depth.
        String boundary="{\"games\":[{"+"\"red\":\"Boundary\",\"result\":\"*\",\"moves\":[\"h2e2\",\"h9g7\",\"i0i1\"],\"notes\":"+nestedArrays(29)+"}]}";
        assertEquals(1,repo.importText(boundary));assertEquals(initial+2,new GameRepository(context).load().size());
        try{repo.importText(boundary.replace(nestedArrays(29),nestedArrays(30)));fail("The 33rd level must reject");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("nesting"));}
        assertEquals(initial+2,new GameRepository(context).load().size());
    }
    private static String nestedArrays(int depth){StringBuilder text=new StringBuilder();for(int i=0;i<depth;i++)text.append('[');text.append('0');for(int i=0;i<depth;i++)text.append(']');return text.toString();}
}
