package org.xiangqi.study;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.view.accessibility.AccessibilityNodeInfo;
import org.xiangqi.study.core.Pgn;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exercises real assets and storage plus essential replay interactions on an Android device. */
public final class StudySmokeTest extends InstrumentationTestCase {
    private File privateTestDirectory;
    private Context storageContext() {
        Context target=getInstrumentation().getTargetContext();
        File cache=target.getCacheDir();
        assertNotNull("Target app cache is unavailable; finish Android setup and unlock the test user before running instrumentation",cache);
        privateTestDirectory=new File(cache,"repository-smoke-"+System.nanoTime());
        assertTrue("Cannot create isolated test directory: "+privateTestDirectory,
            privateTestDirectory.mkdirs()||privateTestDirectory.isDirectory());
        return new ContextWrapper(target) {
            @Override public Context getApplicationContext(){return this;}
            @Override public File getFilesDir(){return privateTestDirectory;}
        };
    }
    @Override protected void tearDown() throws Exception {
        if(privateTestDirectory!=null&&privateTestDirectory.exists()){File[] files=privateTestDirectory.listFiles();if(files!=null)for(File file:files)assertTrue(file.delete());assertTrue(privateTestDirectory.delete());}
        super.tearDown();
    }

    public void testBundledLibraryAndAtomicImportRoundTrip() throws Exception {
        Context storage=storageContext(); GameRepository repo=new GameRepository(storage);
        List<GameRepository.Game> initial=repo.load(); assertTrue("Official starter games must be present",initial.size()>0);
        GameRepository.Game finalGame=null;
        for(GameRepository.Game g:initial) if(g.red.contains("Yin Sheng")&&g.black.contains("Lai Ly Huynh"))finalGame=g;
        assertNotNull("The 2025 men's final must be preloaded",finalGame);
        assertEquals("0-1",finalGame.result); assertTrue(finalGame.moves.size()>100);
        assertTrue(finalGame.sourceUrl.startsWith("https://www.wxf-xiangqi.org/"));
        finalGame.initial().play(finalGame.moves.get(0));

        String fixture="[Event \"Android import fixture\"]\n[Red \"Fixture Red\"]\n[Black \"Fixture Black\"]\n[TimeControl \"3600+30\"]\n[Result \"*\"]\n\n1. b2e2 b9c7 2. h0g2 *\n";
        assertEquals(1,repo.importText(fixture)); assertEquals(0,repo.importText(fixture));
        int savedCount=repo.all().size(); assertEquals(initial.size()+1,savedCount);
        String badBatch="[Red \"First valid candidate\"]\n[Result \"*\"]\n1. b2e2 b9c7 2. a0a1 *\n\n[Red \"Illegal second candidate\"]\n[Result \"*\"]\n1. a0a9 *\n";
        try { repo.importText(badBatch); fail("An illegal second game must reject the whole import"); }
        catch(IllegalArgumentException expected) { /* Atomic validation is the behavior under test. */ }
        assertEquals(savedCount,repo.all().size());
        GameRepository reopened=new GameRepository(storage); assertEquals(savedCount,reopened.load().size());
        GameRepository.Game imported=reopened.all().get(savedCount-1);
        assertTrue(imported.imported); assertEquals("3600+30",imported.timeControl);
        String exported=GameRepository.export(imported,imported.moves,false);
        List<Pgn.Game> parsed=Pgn.parse(exported); assertEquals(1,parsed.size()); assertEquals(imported.moves,parsed.get(0).moves);
        assertEquals(imported.timeControl,parsed.get(0).timeControl);
        assertEquals("*",Pgn.parse(GameRepository.export(imported,imported.moves,true)).get(0).result);
    }

    public void testReplayNavigationAndPersistentAnalysis() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        SharedPreferences study=context.getSharedPreferences("study",0),engine=context.getSharedPreferences("engine-settings",0);
        Map<String,?> oldStudy=study.getAll(),oldEngine=engine.getAll();study.edit().clear().commit();
        engine.edit().putInt("depth",3).putLong("millis",1500).putInt("threads",1).putInt("hashMb",32).putInt("lines",1).commit();
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity=getInstrumentation().startActivitySync(intent);
        try {
            long readyStarted=android.os.SystemClock.uptimeMillis(),readyUntil=readyStarted+8000;final boolean[] ready={false};
            while(!ready[0]&&android.os.SystemClock.uptimeMillis()<readyUntil){UiTestActions.runOnMain(getInstrumentation(),()->ready[0]=findText(activity.getWindow().getDecorView(),"Xiangqi Study")!=null||findText(activity.getWindow().getDecorView(),"‹  Library")!=null);if(!ready[0])Thread.sleep(100);}
            long startupMillis=android.os.SystemClock.uptimeMillis()-readyStarted;
            if(!ready[0]){
                final String[] diagnostic={""};
                UiTestActions.runOnMain(getInstrumentation(),()->{StringBuilder visible=new StringBuilder();collectText(activity.getWindow().getDecorView(),visible);diagnostic[0]="Finishing="+activity.isFinishing()+", destroyed="+activity.isDestroyed()+"; text: "+visible;});
                fail("Activity must load the library or saved replay; elapsed "+startupMillis+" ms. "+diagnostic[0]);
            }
            android.util.Log.i("XiangqiSmoke","Replay activity ready after "+startupMillis+" ms");
            UiTestActions.runOnMain(getInstrumentation(),()->{
                View library=findText(activity.getWindow().getDecorView(),"‹  Library");
                if(library!=null)library.performClick();
            });
            waitFor(activity,"Yin Sheng",8000);
            UiTestActions.runOnMain(getInstrumentation(),()->{
                View player=findText(activity.getWindow().getDecorView(),"Yin Sheng");assertNotNull(player);
                ((View)player.getParent().getParent()).performClick();
            });
            waitFor(activity,"MOVE RECORD",8000);
            UiTestActions.goToMove(getInstrumentation(),activity,0);
            getInstrumentation().waitForIdleSync();
            UiTestActions.runOnMain(getInstrumentation(),()->findDescription(activity.getWindow().getDecorView(),"Next move").performClick());
            getInstrumentation().waitForIdleSync();
            UiTestActions.runOnMain(getInstrumentation(),()->{
                assertTrue(findBoard(activity.getWindow().getDecorView()).getContentDescription().toString().contains("Black to move."));
            });
            UiTestActions.openGameMenu(getInstrumentation(),activity);
            clickPopup("Start analysis");
            UiTestActions.runOnMain(getInstrumentation(),()->{
                findDescription(activity.getWindow().getDecorView(),"Next move").performClick();
            });
            getInstrumentation().waitForIdleSync();
            UiTestActions.runOnMain(getInstrumentation(),()->{
                assertNotNull(findDescription(activity.getWindow().getDecorView(),"Game menu"));
                try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("analysisEnabled");field.setAccessible(true);assertTrue("Analysis follows the selected position",field.getBoolean(activity));}catch(Exception e){throw new RuntimeException(e);}
                assertTrue(findBoard(activity.getWindow().getDecorView()).getContentDescription().toString().contains("Red to move."));
            });
            UiTestActions.openGameMenu(getInstrumentation(),activity);
            clickPopup("Stop analysis");
            UiTestActions.runOnMain(getInstrumentation(),()->{
                try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("analysisFuture");field.setAccessible(true);assertNull("Explicit stop cancels the current search",field.get(activity));}catch(Exception e){throw new RuntimeException(e);}
            });
        } finally {
            UiTestActions.runOnMain(getInstrumentation(),activity::finish);getInstrumentation().waitForIdleSync();
            long deadline=android.os.SystemClock.uptimeMillis()+5000;
            while(!activity.isDestroyed()&&android.os.SystemClock.uptimeMillis()<deadline)Thread.sleep(25);
            restore(study,oldStudy);restore(engine,oldEngine);
        }
    }

    public void testJsonMoveNormalizationAndInvalidPositionRejection() throws Exception {
        GameRepository repo=new GameRepository(storageContext()); int initial=repo.load().size();
        JSONObject game=new JSONObject().put("red","JSON fixture").put("black","Normalization")
            .put("result","*").put("timeControl","3600+30")
            .put("moves",new JSONArray(new String[]{" B2-E2 ","B9–C7","h0-g2","h9g7"}));
        String pack=new JSONArray().put(game).toString();assertEquals(1,repo.importText(pack));
        GameRepository.Game saved=repo.all().get(initial);assertEquals("b2e2",saved.moves.get(0));assertEquals("b9c7",saved.moves.get(1));
        assertEquals(0,repo.importText(new JSONArray().put(saved.json()).toString()));
        assertEquals(saved.moves,Pgn.parse(GameRepository.export(saved,saved.moves,false)).get(0).moves);
        JSONObject missingKings=new JSONObject(game.toString()).put("startFen","9/9/9/9/9/9/9/9/9/9 w - - 0 1");
        try{repo.importText(new JSONArray().put(missingKings).toString());fail("A position without kings must not be saved");}catch(IllegalArgumentException expected){}
        JSONObject badRank=new JSONObject(game.toString()).put("startFen","10/9/9/9/9/9/9/9/9/4K4 w - - 0 1");
        try{repo.importText(new JSONArray().put(badRank).toString());fail("Malformed FEN rank must not be saved");}catch(IllegalArgumentException expected){}
        assertEquals(initial+1,repo.all().size());
    }

    public void testImportRejectsImpossibleGeometryButAllowsCheck() throws Exception {
        GameRepository repo=new GameRepository(storageContext());int initial=repo.load().size();
        String[][] invalid={
            {"4k4/9/9/9/9/9/9/9/9/K8 b - - 0 1","e9e8"},
            {"4k4/9/9/9/4p4/9/9/9/4r4/4K4 b - - 0 1","e9d9"},
            {"4k4/9/9/9/4p4/9/9/9/3A5/4K4 w - - 0 1","e0f0"},
            {"4k4/9/9/9/4p4/9/9/2B6/9/4K4 w - - 0 1","e0f0"},
            {"4k4/9/9/9/4p4/9/9/9/9/RR2K3R w - - 0 1","a0a1"}
        };
        for(String[] fixture:invalid){
            JSONObject game=new JSONObject().put("red","Invalid setup").put("result","*").put("startFen",fixture[0]).put("moves",new JSONArray(new String[]{fixture[1]}));
            try{repo.importText(new JSONArray().put(game).toString());fail("Impossible starting position must be rejected: "+fixture[0]);}catch(IllegalArgumentException expected){}
            assertEquals(initial,repo.all().size());
        }
        JSONObject legalCheck=new JSONObject().put("red","Legal check escape").put("result","*")
            .put("startFen","4k4/9/9/9/4p4/9/9/9/4r4/4K4 w - - 0 1").put("moves",new JSONArray(new String[]{"e0d0"}));
        assertEquals("Side-to-move may legally start in check",1,repo.importText(new JSONArray().put(legalCheck).toString()));
        assertEquals(initial+1,repo.all().size());
    }

    private void waitFor(Activity a,String text,long timeout)throws Exception{waitFor(a,text,timeout,false);}
    private void waitFor(Activity a,String text,long timeout,boolean optional)throws Exception {
        long until=System.currentTimeMillis()+timeout; final boolean[] found={false};
        while(System.currentTimeMillis()<until){UiTestActions.runOnMain(getInstrumentation(),()->found[0]=findText(a.getWindow().getDecorView(),text)!=null);if(found[0])return;Thread.sleep(100);}
        if(!optional)fail("UI did not display: "+text);
    }
    private static View findText(View view,String text){if(view instanceof TextView&&((TextView)view).getText().toString().startsWith(text))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static View findDescription(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static View findBoard(View view){if(view instanceof BoardView)return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findBoard(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){Object value=entry.getValue();String key=entry.getKey();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;edit.putStringSet(key,set);}}edit.commit();}
    private void clickPopup(String text)throws Exception{assertTrue("Missing game menu item "+text,UiTestActions.clickPopup(getInstrumentation(),text));}
    private static AccessibilityNodeInfo popupNode(AccessibilityNodeInfo node,String text){if(node==null)return null;if(text.equalsIgnoreCase(String.valueOf(node.getText())))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=popupNode(node.getChild(i),text);if(found!=null)return found;}return null;}
    private static void collectText(View view,StringBuilder output){
        if(output.length()>=2000)return;
        if(view instanceof TextView){String value=((TextView)view).getText().toString();if(!value.isEmpty()){if(output.length()>0)output.append(" | ");int remaining=Math.max(0,2000-output.length());output.append(value,0,Math.min(value.length(),remaining));}}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount()&&output.length()<2000;i++)collectText(group.getChildAt(i),output);}
    }
}
