package org.xiangqi.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;

/** Three focused checks for the compact fixed-board replay layout. */
public final class ReplayLayoutAuditTest extends InstrumentationTestCase {
    private Activity activity;private SharedPreferences study,engine;private Map<String,?> oldStudy,oldEngine;
    @Override protected void setUp()throws Exception{
        super.setUp();Context context=getInstrumentation().getTargetContext();study=context.getSharedPreferences("study",0);engine=context.getSharedPreferences("engine-settings",0);oldStudy=study.getAll();oldEngine=engine.getAll();study.edit().clear().commit();engine.edit().putInt("depth",1).putLong("millis",500).putInt("threads",1).putInt("hashMb",32).putInt("lines",1).commit();
        activity=getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));await(()->Boolean.TRUE.equals(get("loaded")),15000,"Library did not load");
        main(()->{GameRepository.Game game=games().get(0);call("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},game,false);});getInstrumentation().waitForIdleSync();
    }
    @Override protected void tearDown()throws Exception{
        if(activity!=null&&!activity.isDestroyed())main(activity::finish);getInstrumentation().waitForIdleSync();Field io=MainActivity.class.getDeclaredField("FILE_IO");io.setAccessible(true);((ExecutorService)io.get(null)).submit(()->{}).get(15,TimeUnit.SECONDS);long until=SystemClock.uptimeMillis()+5000;while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<until)Thread.sleep(25);restore(study,oldStudy);restore(engine,oldEngine);super.tearDown();
    }
    public void testCompactHeaderAndFullLazyRecordScrollWithoutMovingBoard()throws Exception{
        main(()->{
            ViewGroup content=(ViewGroup)get("content");View board=(View)get("board"),boardRow=(View)board.getParent();assertEquals(1,content.indexOfChild(boardRow));assertEquals(content.getWidth(),board.getWidth());assertNull(get("positionLabel"));assertEquals(View.GONE,((View)get("variationLabel")).getVisibility());assertEquals(View.GONE,((View)get("analysisBox")).getVisibility());
            View title=(View)get("gameTitle");String description=title.getContentDescription().toString();assertTrue(description.contains("Red:"));assertTrue(description.contains("Black:"));assertNotNull(findDescription(content,"Library"));assertNotNull(findDescription(content,"Game menu"));
            GameRepository.Game game=new GameRepository.Game();game.id="lazy-layout-audit";game.red="Record Red";game.black="Record Black";game.event="Long move record";game.round="";game.date="2026-10-28";game.sourceUrl="";game.startFen="";game.result="*";for(int i=0;i<500;i++)game.moves.addAll(Arrays.asList("b0c2","b9c7","c2b0","c7b9"));set("games",new ArrayList<>(Collections.singletonList(game)));call("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},game,false);
        });getInstrumentation().waitForIdleSync();final Rect[] before={null};main(()->{assertEquals(1000,list().getCount());assertTrue("Only visible move rows are inflated",list().getChildCount()<20);before[0]=bounds((View)get("board"));list().setSelection(900);});
        await(()->list().getFirstVisiblePosition()>=850,5000,"The complete move record did not scroll to its later rows");main(()->{assertEquals(before[0],bounds((View)get("board")));assertTrue(list().getChildCount()<20);assertFalse((Boolean)get("library"));});
    }
    public void testAnalysisPanelIsConditionalBoundedAndKeepsAllLinesScrollable()throws Exception{
        main(()->call("analyze"));getInstrumentation().waitForIdleSync();final Rect[] board={null};final int[] height={0};main(()->{assertEquals(View.VISIBLE,((View)get("analysisBox")).getVisibility());board[0]=bounds((View)get("board"));height[0]=((View)get("analysisBox")).getHeight();call("cancelAnalysis");
            Position position=positions().get((Integer)get("ply"));List<Engine.Line> lines=new ArrayList<>();List<String> legal=position.legalMoves();for(int i=0;i<5;i++){String first=legal.get(i);List<String> reply=position.play(first).legalMoves();lines.add(new Engine.Line(i+1,100-i,8,Arrays.asList(first,reply.get(0))));}Engine.Result result=new Engine.Result();result.lines=lines;result.pv=lines.get(0).pv;result.bestMove=result.pv.get(0);result.score=100;result.depth=8;result.nodes=5000;call("renderAnalysis",new Class<?>[]{Engine.Result.class,String.class,boolean.class,boolean.class},result,"Built-in analysis",false,false);
        });getInstrumentation().waitForIdleSync();main(()->{assertEquals(height[0],((View)get("analysisBox")).getHeight());assertEquals(board[0],bounds((View)get("board")));assertTrue(((TextView)get("analysisText")).getText().toString().contains("Line 5"));((ScrollView)get("analysisScroll")).fullScroll(View.FOCUS_DOWN);});getInstrumentation().waitForIdleSync();
        try{await(()->((ScrollView)get("analysisScroll")).getScrollY()>0,3000,"All PV text must remain scrollable inside the fixed panel");}
        catch(junit.framework.AssertionFailedError failure){final String[] geometry={""};main(()->{ScrollView scroll=(ScrollView)get("analysisScroll");TextView text=(TextView)get("analysisText");geometry[0]=" [textHeight="+text.getHeight()+", viewportHeight="+scroll.getHeight()+", textLines="+text.getLineCount()+", scrollY="+scroll.getScrollY()+"]";});fail(failure.getMessage()+geometry[0]);}
        main(()->{call("stopSearch");assertEquals(View.GONE,((View)get("analysisBox")).getVisibility());});getInstrumentation().waitForIdleSync();main(()->assertEquals(((View)get("content")).getWidth(),((View)get("board")).getWidth()));
    }
    public void testResponsiveBoundsSliderAndGameIconsKeepNavigationUsable()throws Exception{
        main(()->{
            View content=(View)get("content");for(int[] size:new int[][]{{393,740},{280,560},{740,330}}){int width=dp(size[0]),height=dp(size[1]);content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));content.layout(0,0,width,height);View board=(View)get("board");SeekBar slider=(SeekBar)get("replayProgress");assertTrue(board.getHeight()>0);assertTrue(slider.getTop()>=((View)board.getParent()).getBottom());assertTrue(list().getHeight()>=dp(40));assertTrue(list().getBottom()<=height);if(size[0]==393)assertEquals(width,board.getWidth());}
            Button next=(Button)findDescription(content,"Next game"),previous=(Button)findDescription(content,"Previous game");assertNotNull(next.getForeground());assertNotNull(previous.getForeground());assertEquals("",next.getText().toString());int count=moves().size();assertEquals(count,((SeekBar)get("replayProgress")).getMax());call("navigate",new Class<?>[]{int.class},Math.min(3,count));assertEquals(Math.min(3,count),((SeekBar)get("replayProgress")).getProgress());
            ((View)get("gameTitle")).performClick();AlertDialog info=(AlertDialog)get("gameInformationDialog");assertNotNull(info);String text=((TextView)info.findViewById(android.R.id.message)).getText().toString();assertTrue(text.contains("Red:"));assertTrue(text.contains("Black:"));assertFalse("Hidden results remain hidden in game information",text.contains("0–1"));info.dismiss();
        });
    }
    private int dp(int value){return Math.round(value*getInstrumentation().getTargetContext().getResources().getDisplayMetrics().density);}
    private Rect bounds(View view){Rect bounds=new Rect();view.getGlobalVisibleRect(bounds);return bounds;}
    private ListView list(){return (ListView)get("movesView");}
    @SuppressWarnings("unchecked") private List<GameRepository.Game> games(){return (List<GameRepository.Game>)get("games");}
    @SuppressWarnings("unchecked") private List<String> moves(){return (List<String>)get("workingMoves");}
    @SuppressWarnings("unchecked") private List<Position> positions(){return (List<Position>)get("positions");}
    private void main(Runnable action){UiTestActions.runOnMain(getInstrumentation(),action);}
    private void await(BooleanSupplier condition,long timeout,String message)throws Exception{long until=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};while(SystemClock.uptimeMillis()<until){main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(40);}fail(message);}
    private Object get(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private void set(String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}catch(Exception error){throw new RuntimeException(error);}}
    private Object call(String name){return call(name,new Class<?>[0]);}
    private Object call(String name,Class<?>[] types,Object... values){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,values);}catch(Exception error){throw new RuntimeException(error);}}
    private static View findDescription(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object value=item.getValue();String key=item.getKey();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;edit.putStringSet(key,set);}}edit.commit();}
}
