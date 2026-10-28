package org.xiangqi.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Spinner;
import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;
import org.xiangqi.study.core.UciInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Raw UCI perspective passes through the production parser and actual analysis UI once. */
public final class EvaluationPerspectiveTest extends InstrumentationTestCase {
    private Activity activity;
    private SharedPreferences study,enginePreferences;
    private Map<String,?> savedStudy,savedEngine;

    @Override protected void setUp()throws Exception {
        super.setUp();Context context=getInstrumentation().getTargetContext();study=context.getSharedPreferences("study",0);
        savedStudy=study.getAll();study.edit().clear().commit();
        enginePreferences=context.getSharedPreferences("engine-settings",0);savedEngine=enginePreferences.getAll();
        enginePreferences.edit().putInt("depth",3).putLong("millis",1500).putInt("threads",1).putInt("hashMb",32).putInt("lines",1).commit();
        activity=getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        long until=SystemClock.uptimeMillis()+15000;boolean[] ready={false};
        while(!ready[0]&&SystemClock.uptimeMillis()<until){main(()->ready[0]=findText(root(),"Xiangqi Study")!=null);if(!ready[0])Thread.sleep(50);}
        assertTrue("Library must be ready before installing the isolated fixture",ready[0]);
    }
    @Override protected void tearDown()throws Exception {
        if(activity!=null&&!activity.isDestroyed())main(activity::finish);
        getInstrumentation().waitForIdleSync();long until=SystemClock.uptimeMillis()+5000;
        while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<until)Thread.sleep(25);
        restore(study,savedStudy);restore(enginePreferences,savedEngine);super.tearDown();
    }
    private static void restore(SharedPreferences preferences,Map<String,?> values){
        SharedPreferences.Editor restore=preferences.edit().clear();
        for(Map.Entry<String,?> entry:values.entrySet()){
            String key=entry.getKey();Object value=entry.getValue();
            if(value instanceof String)restore.putString(key,(String)value);else if(value instanceof Integer)restore.putInt(key,(Integer)value);
            else if(value instanceof Long)restore.putLong(key,(Long)value);else if(value instanceof Float)restore.putFloat(key,(Float)value);
            else if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> entries=(Set<String>)value;restore.putStringSet(key,entries);}
        }
        restore.commit();
    }

    public void testBlackToMoveScoresAreConvertedExactlyOnceAndFlipKeepsThem()throws Exception {
        open(false);
        Engine.Result blackFavored=show("cp 50");assertEquals(-50,blackFavored.score);assertDisplayedScore(-50);
        String before=displayed();UiTestActions.openGameMenu(getInstrumentation(),activity);
        assertTrue("Flip is chosen from the real game menu",UiTestActions.clickPopup(getInstrumentation(),"Flip board"));
        assertEquals("Board orientation must not change the evaluation perspective",before,displayed());
        main(()->{assertTrue((Boolean)get("flipped"));assertTrue(((BoardView)get("board")).getContentDescription().toString().contains("Black to move."));});
        Engine.Result redFavored=show("cp -50");assertEquals(50,redFavored.score);assertDisplayedScore(50);
    }

    public void testRedToMoveScoresAndBoundsRetainRedPerspective()throws Exception {
        open(true);
        Engine.Result positive=show("cp 50");assertEquals(50,positive.score);assertDisplayedScore(50);
        Engine.Result negative=show("cp -50");assertEquals(-50,negative.score);assertDisplayedScore(-50);
        main(()->{
            UciInfo.update(negative,current(),"info depth 99 score cp 900 lowerbound nodes 999 pv b2e2");
            render(negative);assertEquals("A search bound cannot overwrite the last exact score",-50,negative.score);
        });
        assertDisplayedScore(-50);
    }

    public void testMateWinnerLabelsFollowRedAndBlackRatherThanBoardOrientation()throws Exception {
        open(false);assertEquals(-29997,show("mate 3").score);assertTrue(displayed().contains("Black winning"));
        assertEquals(29997,show("mate -3").score);assertTrue(displayed().contains("Red winning"));
        open(true);assertEquals(29997,show("mate 3").score);assertTrue(displayed().contains("Red winning"));
        assertEquals(-29997,show("mate -3").score);assertTrue(displayed().contains("Black winning"));
    }

    public void testEvaluationRailTracksBothSidesAndExplicitStopClearsIt()throws Exception{
        open(true);show("cp 125");assertRail(true,125);
        int[] geometry=new int[4];main(()->{View board=(View)get("board");geometry[0]=board.getWidth();geometry[1]=board.getHeight();geometry[2]=board.getLeft();geometry[3]=board.getTop();});
        show("cp -75");assertRail(true,-75);
        UiTestActions.openGameMenu(getInstrumentation(),activity);
        assertTrue(UiTestActions.clickPopup(getInstrumentation(),"Stop analysis"));
        assertRail(false,null);assertTrue(displayed().startsWith("Analysis off · last estimate"));
        main(()->{
            View board=(View)get("board");assertEquals("Hiding the rail preserves board width",geometry[0],board.getWidth());assertEquals(geometry[1],board.getHeight());assertEquals(geometry[2],board.getLeft());assertEquals(geometry[3],board.getTop());
            assertEquals(Boolean.FALSE,get("analysisEnabled"));
            assertTrue("Stopping removes all continuation arrows",((List<?>)viewField(get("board"),"analysisArrows")).isEmpty());
            findDescription(root(),"Next move").performClick();
        });
        assertRail(false,null);
    }

    public void testAnalysisLineSettingPersistsAndRestartsEnabledAnalysis()throws Exception{
        open(true);
        // The other cases render synthetic scores with the toggle enabled but
        // no worker. This case exercises a real user start from a known off state.
        main(()->{
            invoke("stopSearch",new Class<?>[]{});
            assertEquals(Boolean.FALSE,get("analysisEnabled"));assertNull(get("analysisFuture"));
        });
        long focusedBy=SystemClock.uptimeMillis()+8000;boolean[] focused={false};
        while(SystemClock.uptimeMillis()<focusedBy){main(()->focused[0]=activity.hasWindowFocus());if(focused[0])break;Thread.sleep(50);}
        assertTrue("The live-analysis fixture must own the focused app window",focused[0]);
        UiTestActions.openGameMenu(getInstrumentation(),activity);
        assertTrue(UiTestActions.clickPopup(getInstrumentation(),"Start analysis"));awaitCompleted(1);
        int[] before={0};main(()->before[0]=(Integer)get("analysisGeneration"));
        UiTestActions.openGameMenu(getInstrumentation(),activity);assertTrue(UiTestActions.clickPopup(getInstrumentation(),"Settings"));
        assertTrue(UiTestActions.clickPopup(getInstrumentation(),"Engine settings"));
        main(()->{
            AlertDialog dialog=(AlertDialog)get("engineSettingsDialog");assertNotNull(dialog);View settings=dialog.getWindow().getDecorView();
            Spinner lines=(Spinner)findDescription(settings,"Analysis lines");assertNotNull(lines);assertEquals(5,lines.getCount());lines.setSelection(2);
            ((Spinner)findDescription(settings,"Maximum depth")).setSelection(0);
            ((Spinner)findDescription(settings,"Time per position")).setSelection(1);
            ((Spinner)findDescription(settings,"Engine threads")).setSelection(0);
            ((Spinner)findDescription(settings,"Hash memory")).setSelection(0);
        });
        getInstrumentation().waitForIdleSync();assertTrue(UiTestActions.clickPopup(getInstrumentation(),"Save"));
        assertEquals("The selected number of analysis lines is persisted",3,enginePreferences.getInt("lines",0));
        main(()->{assertEquals(Boolean.TRUE,get("analysisEnabled"));assertTrue("Saving settings restarts analysis for this position",((Integer)get("analysisGeneration"))>before[0]);});
        awaitCompleted(3);main(()->{
            Engine.Result result=(Engine.Result)get("lastResult");assertEquals(3,result.lines.size());
            assertEquals("Each selected continuation draws one root move and one reply",6,((List<?>)viewField(get("board"),"analysisArrows")).size());
        });
    }

    private void awaitCompleted(int lines)throws Exception{
        long deadline=SystemClock.uptimeMillis()+20000;boolean[] done={false};
        while(SystemClock.uptimeMillis()<deadline){main(()->{Engine.Result result=(Engine.Result)get("lastResult");done[0]=get("analysisFuture")==null&&result!=null&&result.depth>0&&result.lines.size()>=lines;});if(done[0])return;Thread.sleep(50);}
        fail("Finite analysis did not produce "+lines+" lines: "+displayed());
    }

    private void assertRail(boolean active,Integer score){main(()->{
        Object rail=get("evaluationBar");assertNotNull(rail);assertEquals(active,viewField(rail,"active"));assertEquals(score,viewField(rail,"score"));
        assertEquals("The hidden rail reserves its layout space",active?View.VISIBLE:View.INVISIBLE,((View)rail).getVisibility());
        String text=((View)rail).getContentDescription().toString();
        if(!active)assertTrue(text.contains("Analysis off"));
        else if(score!=null){assertTrue(text.contains("Red perspective "+String.format(Locale.getDefault(),"%+.2f",score/100.0)));assertTrue(text.contains(score>0?"Red advantage":score<0?"Black advantage":"Equal evaluation"));}
    });}
    private static Object viewField(Object view,String name){try{Field field=view.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(view);}catch(Exception error){throw new RuntimeException(error);}}

    private void open(boolean red)throws Exception {
        GameRepository.Game game=new GameRepository.Game();game.id="perspective-"+(red?"red":"black");game.red="Perspective Red";game.black="Perspective Black";
        game.event="Evaluation perspective audit";game.round="";game.date="2026-10-28";game.result="*";game.sourceUrl="";
        game.startFen=Position.START_FEN.replace(" w ",red?" w ":" b ");game.moves.add(red?"b2e2":"b7e7");
        main(()->{set("analysisEnabled",false);set("games",new ArrayList<>(Collections.singletonList(game)));invoke("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},game,false);});
        getInstrumentation().waitForIdleSync();
    }
    private Engine.Result show(String rawScore)throws Exception {
        Engine.Result result=new Engine.Result();
        main(()->{
            set("analysisEnabled",true);
            Position position=current();String best=position.redToMove?"b2e2":"b7e7";
            UciInfo.update(result,position,"info depth 8 score "+rawScore+" nodes 100 pv "+best);
            assertTrue("A parsed best move must be legal for the current side",position.legalMoves().contains(result.bestMove));
            render(result);
        });getInstrumentation().waitForIdleSync();return result;
    }
    private void render(Engine.Result result){invoke("renderAnalysis",new Class<?>[]{Engine.Result.class,String.class,boolean.class,boolean.class},result,"Pikafish",true,false);}
    private Position current(){@SuppressWarnings("unchecked") List<Position> positions=(List<Position>)get("positions");return positions.get((Integer)get("ply"));}
    private void assertDisplayedScore(int score)throws Exception {
        String text=displayed(),number=String.format(Locale.getDefault(),"%+.2f",score/100.0);
        assertTrue("Displayed centipawns must be Red-relative: "+text,text.contains("Red perspective "+number));
        assertTrue("A plain-language legend must identify the positive side",text.contains("+ favors Red"));
        assertTrue("A plain-language legend must identify the negative side",text.contains("− favors Black"));
        assertRail(true,score);
    }
    private String displayed(){String[] text={null};main(()->text[0]=((TextView)get("analysisText")).getText().toString());return text[0];}
    private void main(Runnable task){UiTestActions.runOnMain(getInstrumentation(),task);}
    private View root(){return activity.getWindow().getDecorView();}
    private Object get(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}catch(Exception e){throw new RuntimeException(e);}}
    private void invoke(String name,Class<?>[] types,Object... values){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(activity,values);}catch(Exception e){throw new RuntimeException(e);}}
    private static View findText(View view,String text){if(view instanceof TextView&&((TextView)view).getText().toString().startsWith(text))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static View findDescription(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
}
