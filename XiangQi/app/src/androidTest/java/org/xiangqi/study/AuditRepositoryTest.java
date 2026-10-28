package org.xiangqi.study;

import android.content.Context;
import android.content.ContextWrapper;
import android.test.InstrumentationTestCase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public void testQuotedClosingBracesRemainJsonAndSchemaErrorsDoNotFallback()throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);int initial=repo.load().size();
        JSONObject game=new JSONObject().put("event","}and notes").put("moves",new JSONArray().put("b2e2").put("b9c7")).put("result","*");
        String json=new JSONObject().put("games",new JSONArray().put(game)).toString();
        assertEquals(1,repo.importText(json,"Quoted JSON",false));assertEquals("}and notes",repo.all().get(initial).event);
        try{repo.importText(new JSONObject().put("notes","}and notes").toString(),"Invalid JSON schema",false);fail("Valid JSON with no games must not become PGN");}catch(org.json.JSONException expected){}
        assertEquals(1,repo.importedCollections().size());
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
    public void testNamedImportsStaySeparateFromBundledGamesAndEachOther() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);List<GameRepository.Game> bundled=repo.load();int initial=bundled.size();
        GameRepository.Game source=bundled.get(0);String text=GameRepository.export(source,source.moves,false);
        assertEquals("A user may keep their own copy of a bundled game",1,repo.importText(text,"My human study",false));
        assertEquals("Repeated moves in another named import are a separate user collection",1,repo.importText(text,"Engine comparison",true));
        GameRepository reopened=new GameRepository(context);List<GameRepository.Game> games=reopened.load();
        assertEquals(initial+2,games.size());List<GameRepository.ImportedCollection> groups=reopened.importedCollections();
        assertEquals(2,groups.size());assertEquals("My human study",groups.get(0).name);assertFalse(groups.get(0).engineGame);
        assertEquals("Engine comparison",groups.get(1).name);assertTrue(groups.get(1).engineGame);
        assertEquals(1,groups.get(0).gameCount);assertEquals(1,groups.get(1).gameCount);
        assertFalse(groups.get(0).id.equals(groups.get(1).id));
        Set<String> ids=new HashSet<>();for(GameRepository.Game game:games)assertTrue("Study-state IDs must stay unique",ids.add(game.id));
        for(int i=0;i<initial;i++)assertFalse("Imports never modify bundled provenance",games.get(i).imported);
        GameRepository.Game human=games.get(initial),engine=games.get(initial+1);
        assertTrue(human.imported);assertFalse(human.engineGame);assertEquals(groups.get(0).id,human.importCollectionId);
        assertTrue(engine.imported);assertTrue(engine.engineGame);assertEquals(groups.get(1).id,engine.importCollectionId);
        assertEquals(source.moves,human.moves);assertEquals(source.moves,engine.moves);
        String humanId=human.id,engineId=engine.id;
        reopened.load();assertEquals(humanId,reopened.all().get(initial).id);assertEquals(engineId,reopened.all().get(initial+1).id);
    }
    public void testOneBatchDeduplicatesButSameNameCreatesDistinctCollections() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);int initial=repo.load().size();
        String text=pgn("Batch duplicate","1. b2e2 b9c7 2. a0a1");
        assertEquals(1,repo.importText(text+"\n"+text,"Same name",false));
        assertEquals(1,repo.importText(text,"Same name",false));
        GameRepository reopened=new GameRepository(context);assertEquals(initial+2,reopened.load().size());
        List<GameRepository.ImportedCollection> groups=reopened.importedCollections();assertEquals(2,groups.size());
        assertEquals(groups.get(0).name,groups.get(1).name);assertFalse(groups.get(0).id.equals(groups.get(1).id));
        groups.clear();assertEquals("Callers cannot mutate the repository's collection list",2,reopened.importedCollections().size());
    }
    public void testFlatImportsMigrateWithoutLosingSavedGameIdsOrMetadata() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);int initial=repo.load().size();
        assertEquals(1,repo.importText(pgn("Existing phone import","1. b2e2 b9c7 2. a0a1")));
        GameRepository.Game old=repo.all().get(initial);String oldId=old.id;
        JSONObject oldJson=old.json();oldJson.remove("engineGame");oldJson.remove("importCollectionId");
        try(FileOutputStream out=new FileOutputStream(new File(directory,"imported-games.json"))){out.write(new JSONArray().put(oldJson).toString().getBytes(StandardCharsets.UTF_8));}
        GameRepository migrated=new GameRepository(context);assertEquals(initial+1,migrated.load().size());
        assertEquals(oldId,migrated.all().get(initial).id);assertEquals("Existing phone import",migrated.all().get(initial).event);
        assertEquals("Imported games",migrated.importedCollections().get(0).name);assertEquals(1,migrated.importedCollections().get(0).gameCount);
        assertEquals(1,migrated.importText(pgn("New collection","1. h2e2 h9g7 2. i0i1"),"My engines",true));
        GameRepository reloaded=new GameRepository(context);assertEquals(initial+2,reloaded.load().size());
        assertEquals(oldId,reloaded.all().get(initial).id);assertEquals(2,reloaded.importedCollections().size());
        assertFalse(reloaded.all().get(initial).engineGame);assertTrue(reloaded.all().get(initial+1).engineGame);
        try(FileInputStream in=new FileInputStream(new File(directory,"imported-games.json"))){assertEquals(2,new JSONObject(GameRepository.read(in)).getInt("schema"));}
    }
    public void testConcurrentNamedImportsKeepBothBatchesEvenWithIdenticalMoves() throws Exception {
        Context context=storage();GameRepository first=new GameRepository(context),second=new GameRepository(context);int initial=first.load().size();second.load();
        String text=pgn("Shared move record","1. b2e2 b9c7 2. a0a1");
        assertEquals(1,first.importText(text,"First Activity",false));assertEquals(1,second.importText(text,"Recreated Activity",true));
        GameRepository loaded=new GameRepository(context);assertEquals(initial+2,loaded.load().size());
        List<GameRepository.ImportedCollection> groups=loaded.importedCollections();assertEquals(2,groups.size());
        assertEquals("First Activity",groups.get(0).name);assertEquals("Recreated Activity",groups.get(1).name);
    }
    public void testInvalidNamedImportDoesNotCreateCollectionOrChangeCommittedFile() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);int initial=repo.load().size();
        String valid=pgn("Valid record","1. b2e2 b9c7 2. a0a1");assertEquals(1,repo.importText(valid,"Saved collection",false));
        File saved=new File(directory,"imported-games.json");String before;try(FileInputStream in=new FileInputStream(saved)){before=GameRepository.read(in);}
        String invalid=pgn("Illegal record","1. a0a9");
        try{repo.importText(valid+"\n"+invalid,"Rejected batch",true);fail("The complete batch must validate before adding a collection");}catch(IllegalArgumentException expected){}
        for(String name:new String[]{"   ","Line\nBreak",new String(new char[81]).replace('\0','x')}) {
            try{repo.importText(valid,name,true);fail("Invalid collection name accepted");}catch(IllegalArgumentException expected){}
        }
        try(FileInputStream in=new FileInputStream(saved)){assertEquals(before,GameRepository.read(in));}
        GameRepository reopened=new GameRepository(context);assertEquals(initial+1,reopened.load().size());assertEquals(1,reopened.importedCollections().size());
    }
    public void testUntrustedImportCannotChooseIdsOrEscapeItsAssignedCollection() throws Exception {
        Context context=storage();GameRepository repo=new GameRepository(context);List<GameRepository.Game> original=repo.load();int initial=original.size();
        JSONObject source=original.get(0).json().put("id",original.get(0).id).put("importCollectionId","forged-group").put("engineGame",true);
        assertEquals(1,repo.importText(new JSONArray().put(source).toString(),"Chosen human collection",false));
        GameRepository reopened=new GameRepository(context);reopened.load();GameRepository.Game imported=reopened.all().get(initial);
        assertFalse(original.get(0).id.equals(imported.id));assertFalse(imported.engineGame);
        assertEquals(reopened.importedCollections().get(0).id,imported.importCollectionId);
        assertFalse("forged-group".equals(imported.importCollectionId));
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
