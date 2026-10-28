package org.xiangqi.study;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Search is real worker work: a blocked worker must not block typing or publish stale results. */
public final class LibrarySearchConcurrencyTest extends InstrumentationTestCase {
    private Activity activity;private SharedPreferences study;private Map<String,?> oldStudy;
    private final List<CountDownLatch> releases=new ArrayList<>();

    @Override protected void setUp()throws Exception{
        super.setUp();Context context=getInstrumentation().getTargetContext();study=context.getSharedPreferences("study",0);oldStudy=study.getAll();study.edit().clear().commit();
        activity=getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->Boolean.TRUE.equals(get("loaded")),15000,"Library did not load");ready();
    }
    @Override protected void tearDown()throws Exception{
        for(CountDownLatch release:releases)release.countDown();if(activity!=null&&!activity.isDestroyed())main(activity::finish);getInstrumentation().waitForIdleSync();
        Field io=MainActivity.class.getDeclaredField("FILE_IO");io.setAccessible(true);((ExecutorService)io.get(null)).submit(()->{}).get(15,TimeUnit.SECONDS);
        long until=SystemClock.uptimeMillis()+5000;while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<until)Thread.sleep(25);restore(study,oldStudy);super.tearDown();
    }

    public void testBlockedWorkerKeepsTypingResponsiveAndOnlyLatestQueryPublishes()throws Exception{
        CountDownLatch release=blockWorker();final EditText[] input={null};final Object[] previous={null};List<GameRepository.Game> corpus=new ArrayList<>();
        for(int i=0;i<4999;i++)corpus.add(game("large-"+i,"Corpus player "+i,"2023-11-20",false));corpus.add(game("needle","Latest needle","2023-11-24",false));
        main(()->{
            previous[0]=get("libraryMatches");set("games",corpus);call("renderCards");input[0]=search();input[0].requestFocus();
            input[0].setText("Corpus");input[0].setText("No matching previous query");input[0].setText("Latest needle");input[0].setSelection(6);
        });
        CountDownLatch frame=new CountDownLatch(1);main(()->input[0].postOnAnimation(frame::countDown));assertTrue("A frame must run while the search worker is blocked",frame.await(3,TimeUnit.SECONDS));
        main(()->{assertSame(input[0],search());assertEquals("Latest needle",input[0].getText().toString());assertEquals(6,input[0].getSelectionStart());assertTrue(input[0].hasFocus());assertSame("Filtering cannot run synchronously on the UI thread",previous[0],get("libraryMatches"));assertTrue((Boolean)get("librarySearchPending"));});
        release.countDown();ready();main(()->{assertEquals(1,matches().size());assertEquals("needle",matches().get(0).id);assertEquals("Latest needle",search().getText().toString());assertEquals(6,search().getSelectionStart());assertTrue(search().hasFocus());assertTrue(((ListView)get("libraryList")).getChildCount()<20);});
    }

    public void testSupersededHumanSearchCannotReplaceEngineTabAndSortedNavigation()throws Exception{
        List<GameRepository.Game> corpus=Arrays.asList(game("human","Alpha human","2025-01-01",false),game("human-old","Alpha older","2020-01-01",false),game("engine-new","Engine new","2025-01-01",true),game("engine-old","Engine old","2020-01-01",true));
        main(()->{set("games",new ArrayList<>(corpus));call("renderCards");});ready();CountDownLatch release=blockWorker();
        main(()->((Spinner)get("librarySortPicker")).setSelection(2));getInstrumentation().waitForIdleSync();
        main(()->assertTrue((Boolean)get("librarySearchPending")));
        main(()->((Spinner)get("librarySortPicker")).setSelection(1));getInstrumentation().waitForIdleSync();
        main(()->assertFalse("Returning to the cached newest order must cancel the obsolete oldest request",(Boolean)get("librarySearchPending")));
        main(()->((Spinner)get("librarySortPicker")).setSelection(2));getInstrumentation().waitForIdleSync();
        main(()->assertTrue("The same oldest order must be requestable again after cancellation",(Boolean)get("librarySearchPending")));
        release.countDown();ready();main(()->{assertEquals("oldest",get("librarySort"));assertEquals("human-old",matches().get(0).id);assertFalse((Boolean)get("librarySearchPending"));});release=blockWorker();
        main(()->{search().setText("Alpha");call("selectLibrary",new Class<?>[]{String.class,String.class},"engine","");search().setText("Engine");((Spinner)get("librarySortPicker")).setSelection(2);});getInstrumentation().waitForIdleSync();
        release.countDown();ready();main(()->{assertEquals("engine",get("libraryTab"));assertEquals("oldest",get("librarySort"));assertEquals(2,matches().size());assertEquals("engine-old",matches().get(0).id);assertEquals("engine-new",matches().get(1).id);for(GameRepository.Game game:matches())assertTrue(game.engineGame);call("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},matches().get(0),false);});ready();
        main(()->call("navigateGame",new Class<?>[]{int.class},1));ready();main(()->assertEquals("engine-new",((GameRepository.Game)get("game")).id));
    }

    public void testLeavingLibraryAndDestroyingActivityRejectLateSearchCallbacks()throws Exception{
        GameRepository.Game alpha=game("alpha","Alpha record","2023-11-20",false),beta=game("beta","Beta record","2023-11-21",false);
        main(()->{set("games",new ArrayList<>(Arrays.asList(alpha,beta)));call("renderCards");});ready();CountDownLatch release=blockWorker();final View[] board={null};
        main(()->{search().setText("Beta");call("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},beta,false);board[0]=(View)get("board");});release.countDown();ready();
        main(()->{assertFalse((Boolean)get("library"));assertSame("A late library query must not replace or reset the replay board",board[0],get("board"));assertEquals("beta",((GameRepository.Game)get("game")).id);call("showLibrary");});ready();
        main(()->{assertEquals(1,matches().size());assertEquals("beta",matches().get(0).id);});CountDownLatch last=blockWorker();ExecutorService worker=worker();
        main(()->{search().setText("Alpha");activity.finish();});last.countDown();await(activity::isDestroyed,5000,"Activity did not finish");assertTrue("Destroyed activity shuts down its search executor",worker.isShutdown());assertTrue("No activity-owned search thread survives destruction",worker.awaitTermination(5,TimeUnit.SECONDS));
    }

    private CountDownLatch blockWorker()throws Exception{CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);releases.add(release);worker().submit(()->{entered.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException cancelled){Thread.currentThread().interrupt();}});assertTrue("Search worker fixture must start",entered.await(3,TimeUnit.SECONDS));return release;}
    private ExecutorService worker(){return (ExecutorService)get("libraryWorker");}
    private void ready()throws Exception{UiTestActions.awaitLibrarySearch(getInstrumentation(),activity);}
    private EditText search(){return (EditText)get("librarySearch");}
    @SuppressWarnings("unchecked")private List<GameRepository.Game> matches(){return (List<GameRepository.Game>)get("libraryMatches");}
    private void main(Runnable task){UiTestActions.runOnMain(getInstrumentation(),task);}
    private void await(BooleanSupplier check,long timeout,String message)throws Exception{long deadline=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};while(SystemClock.uptimeMillis()<deadline){main(()->ready[0]=check.getAsBoolean());if(ready[0])return;Thread.sleep(25);}fail(message);}
    private Object get(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private void set(String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}catch(Exception error){throw new RuntimeException(error);}}
    private Object call(String name){return call(name,new Class<?>[0]);}
    private Object call(String name,Class<?>[] types,Object... args){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);}catch(Exception error){throw new RuntimeException(error);}}
    private static GameRepository.Game game(String id,String red,String date,boolean engine){GameRepository.Game game=new GameRepository.Game();game.id=id;game.red=red;game.black="Opponent";game.date=date;game.engineGame=engine;game.event="Search concurrency";game.round="";game.sourceUrl="";game.startFen="";game.result="*";game.moves.addAll(Arrays.asList("b2e2","b9c7","a0a1"));return game;}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Set){@SuppressWarnings("unchecked")Set<String> set=(Set<String>)value;edit.putStringSet(key,set);}}edit.commit();}
}
