package org.xiangqi.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Collection isolation and replay speed through real Android controls. */
public final class CollectionUiTest extends InstrumentationTestCase {
    private Activity activity;
    private SharedPreferences preferences;
    private Map<String,?> original;
    private File directory;
    private GameRepository repository;

    @Override protected void setUp()throws Exception{
        super.setUp();Context target=getInstrumentation().getTargetContext();preferences=target.getSharedPreferences("study",0);
        original=preferences.getAll();preferences.edit().clear().commit();
        activity=getInstrumentation().startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->get("loaded").equals(Boolean.TRUE)&&findDescription(root(),"Human games tab")!=null,15000,"Library did not load");
        directory=new File(target.getCacheDir(),"collection-ui-"+System.nanoTime());assertTrue(directory.mkdirs());
        Context storage=new ContextWrapper(target){@Override public Context getApplicationContext(){return this;}@Override public File getFilesDir(){return directory;}};
        repository=new GameRepository(storage);repository.load();
        main(()->{set("repository",repository);set("games",repository.all());call("showLibrary");});
        getInstrumentation().waitForIdleSync();
    }
    @Override protected void tearDown()throws Exception{
        if(activity!=null&&!activity.isDestroyed())main(activity::finish);getInstrumentation().waitForIdleSync();
        Field fileIo=MainActivity.class.getDeclaredField("FILE_IO");fileIo.setAccessible(true);((ExecutorService)fileIo.get(null)).submit(()->{}).get(15,TimeUnit.SECONDS);
        long until=SystemClock.uptimeMillis()+5000;while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<until)Thread.sleep(25);
        restore(preferences,original);if(directory!=null){File[] files=directory.listFiles();if(files!=null)for(File file:files)assertTrue(file.delete());assertTrue(directory.delete());}super.tearDown();
    }

    public void testTabsKeepBuiltInsAndNamedImportsSeparate()throws Exception{
        int humans=0,engines=0;for(GameRepository.Game game:repository.all())if(game.engineGame)engines++;else humans++;
        final int humanCount=humans,engineCount=engines;
        repository.importText(record("Private Human","2021-01-01","1. b2e2 b9c7 2. a0a1"),"My human study",false);
        repository.importText(record("Private Engine","2022-01-01","1. h2e2 h9g7 2. i0i1"),"My engine study",true);
        main(()->{set("games",repository.all());call("showLibrary");assertEquals(humanCount,matches().size());assertAll(false,false);});
        clickDescription("Engine games tab");main(()->{assertEquals(engineCount,matches().size());assertAll(false,true);});
        clickDescription("My imports tab");main(()->{
            Spinner picker=(Spinner)findDescription(root(),"Imported collection");assertNotNull(picker);assertEquals(2,picker.getCount());
            assertTrue(String.valueOf(picker.getItemAtPosition(0)).contains("My human study · Human"));assertTrue(String.valueOf(picker.getItemAtPosition(1)).contains("My engine study · Engine"));
            assertEquals(1,matches().size());assertEquals("Private Human",matches().get(0).red);picker.setSelection(1);
        });
        await(()->matches().size()==1&&matches().get(0).red.equals("Private Engine"),5000,"Import picker did not switch collections");
        main(()->{assertAll(true,true);open(matches().get(0));});getInstrumentation().waitForIdleSync();
        main(()->{assertNotNull(findText(root(),"My imports · Engine game"));call("showLibrary");assertEquals("Private Engine",matches().get(0).red);});
        clickDescription("Human games tab");main(()->assertEquals("Imports never enter built-in Human",humanCount,matches().size()));
    }

    public void testEachImportRetainsItsOwnSearchSortAndNavigation()throws Exception{
        String first=record("Alpha newest","2025-01-01","1. b2e2 b9c7 2. a0a1")+"\n"+record("Alpha oldest","2020-01-01","1. h2e2 h9g7 2. i0i1");
        repository.importText(first,"Alpha collection",false);repository.importText(first,"Beta collection",true);
        final String alpha=repository.importedCollections().get(0).id,beta=repository.importedCollections().get(1).id;
        main(()->{set("games",repository.all());call("selectLibrary",new Class<?>[]{String.class,String.class},"imports",alpha);});getInstrumentation().waitForIdleSync();
        main(()->{assertEquals(2,matches().size());((Spinner)findDescription(root(),"Game sort order")).setSelection(2);});getInstrumentation().waitForIdleSync();
        main(()->{assertEquals("Alpha oldest",matches().get(0).red);search().setText("oldest");});getInstrumentation().waitForIdleSync();
        main(()->{assertEquals(1,matches().size());((Spinner)findDescription(root(),"Imported collection")).setSelection(1);});getInstrumentation().waitForIdleSync();
        main(()->{assertEquals(beta,get("selectedImportId"));assertEquals("",search().getText().toString());assertEquals(1,((Spinner)findDescription(root(),"Game sort order")).getSelectedItemPosition());assertEquals(2,matches().size());
            search().setText("newest");});getInstrumentation().waitForIdleSync();
        main(()->((Spinner)findDescription(root(),"Imported collection")).setSelection(0));getInstrumentation().waitForIdleSync();
        main(()->{
            assertEquals(alpha,get("selectedImportId"));assertEquals("oldest",search().getText().toString());assertEquals(2,((Spinner)findDescription(root(),"Game sort order")).getSelectedItemPosition());
            assertEquals(1,matches().size());open(matches().get(0));@SuppressWarnings("unchecked") List<GameRepository.Game> navigation=(List<GameRepository.Game>)call("navigationGames");
            assertEquals(1,navigation.size());assertEquals(alpha,navigation.get(0).importCollectionId);
        });
        main(()->{call("navigate",new Class<?>[]{int.class},1);call("showLibrary");call("selectLibrary",new Class<?>[]{String.class,String.class},"engine","");});getInstrumentation().waitForIdleSync();
        main(()->{View resume=findText(root(),"Return to current game");assertNotNull(resume);resume.performClick();});getInstrumentation().waitForIdleSync();
        main(()->{
            assertEquals("imports",get("libraryTab"));assertEquals(alpha,get("selectedImportId"));assertEquals("oldest",get("search"));assertEquals("oldest",get("librarySort"));
            assertEquals(1,((Integer)get("ply")).intValue());@SuppressWarnings("unchecked") List<GameRepository.Game> navigation=(List<GameRepository.Game>)call("navigationGames");assertEquals(1,navigation.size());assertEquals(alpha,navigation.get(0).importCollectionId);
        });
    }

    public void testPasteImportAsksForNameAndTypeAndCreatesANewPickerEntry()throws Exception{
        main(()->call("importCollection",new Class<?>[]{int.class,android.net.Uri.class},1,null));getInstrumentation().waitForIdleSync();
        main(()->{
            AlertDialog dialog=(AlertDialog)get("importCollectionDialog");assertNotNull(dialog);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue("A blank collection name cannot continue",dialog.isShowing());
            ((EditText)findDescription(dialog.getWindow().getDecorView(),"Import collection name")).setText("Weekend engine matches");
            ((RadioButton)findDescription(dialog.getWindow().getDecorView(),"Import engine games")).setChecked(true);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });getInstrumentation().waitForIdleSync();
        main(()->{AlertDialog dialog=(AlertDialog)get("pasteDialog");assertNotNull(dialog);((EditText)findDescription(dialog.getWindow().getDecorView(),"Game record text")).setText(record("Pasted Engine","2025-05-04","1. b2e2 b9c7 2. a0a1"));dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
        await(()->"imports".equals(get("libraryTab"))&&matches().size()==1&&matches().get(0).red.equals("Pasted Engine"),12000,"Completed import did not open its named collection");
        main(()->{assertEquals(1,repository.importedCollections().size());GameRepository.ImportedCollection imported=repository.importedCollections().get(0);assertEquals("Weekend engine matches",imported.name);assertTrue(imported.engineGame);assertAll(true,true);});
        clickDescription("Human games tab");main(()->{for(GameRepository.Game game:matches())assertFalse(game.imported);});
        clickDescription("Engine games tab");main(()->{for(GameRepository.Game game:matches())assertFalse(game.imported);});
    }

    public void testReplaySpeedValidationReschedulesAutoplayAndPersists()throws Exception{
        main(()->{open(matches().get(0));call("navigate",new Class<?>[]{int.class},0);preferences.edit().putLong("replayDelayMillis",5000).commit();call("toggleAutoplay");});
        Thread.sleep(350);main(()->assertEquals("The five-second delay must not use the old one-second loop",0,((Integer)get("ply")).intValue()));
        main(()->call("replaySpeed",new Class<?>[]{Runnable.class},(Object)null));getInstrumentation().waitForIdleSync();
        main(()->{
            AlertDialog dialog=(AlertDialog)get("replaySpeedDialog");EditText field=(EditText)findDescription(dialog.getWindow().getDecorView(),"Seconds between replay moves");
            field.setText("0.1");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(dialog.isShowing());assertEquals(5000,preferences.getLong("replayDelayMillis",0));
            field.setText("0.5");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals(500,preferences.getLong("replayDelayMillis",0));
        });
        await(()->((Integer)get("ply"))>0,2500,"A changed delay must reschedule the pending five-second tick");
        final int[] paused={0};main(()->{call("stopAutoplay");paused[0]=(Integer)get("ply");});Thread.sleep(850);main(()->assertEquals("Pause must cancel the faster pending tick",paused[0],((Integer)get("ply")).intValue()));
        // Recreate against the real bundled repository; no test import is written
        // into app-private user data, and the saved speed is independent of it.
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);main(activity::recreate);
        Activity replacement=monitor.waitForActivityWithTimeout(10000);getInstrumentation().removeMonitor(monitor);assertNotNull(replacement);activity=replacement;
        await(()->get("loaded").equals(Boolean.TRUE),15000,"Recreated Activity did not load");main(()->{assertEquals(500,((Long)call("replayDelay")).longValue());assertFalse((Boolean)get("autoplay"));call("settings");});
        getInstrumentation().waitForIdleSync();main(()->{AlertDialog settings=(AlertDialog)get("settingsDialog");assertNotNull(findDescription(settings.getWindow().getDecorView(),"Replay speed"));});
    }

    private void assertAll(boolean imported,boolean engine){for(GameRepository.Game game:matches()){assertEquals(imported,game.imported);assertEquals(engine,game.engineGame);}}
    private void open(GameRepository.Game game){call("openGame",new Class<?>[]{GameRepository.Game.class,boolean.class},game,false);}
    private EditText search(){return (EditText)findDescription(root(),"Library search");}
    @SuppressWarnings("unchecked") private List<GameRepository.Game> matches(){return (List<GameRepository.Game>)get("libraryMatches");}
    private View root(){return activity.getWindow().getDecorView();}
    private void main(Runnable task){UiTestActions.runOnMain(getInstrumentation(),task);}
    private void clickDescription(String description){main(()->{View view=findDescription(root(),description);assertNotNull(description,view);view.performClick();});getInstrumentation().waitForIdleSync();}
    private void await(BooleanSupplier condition,long timeout,String message)throws Exception{long until=SystemClock.uptimeMillis()+timeout;boolean[] passed={false};while(SystemClock.uptimeMillis()<until){main(()->passed[0]=condition.getAsBoolean());if(passed[0])return;Thread.sleep(40);}fail(message);}
    private Object get(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private void set(String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}catch(Exception error){throw new RuntimeException(error);}}
    private Object call(String name){return call(name,new Class<?>[0]);}
    private Object call(String name,Class<?>[] types,Object... values){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,values);}catch(Exception error){throw new RuntimeException(error);}}
    private static String record(String red,String date,String moves){return "[Event \"Collection UI fixture\"]\n[Red \""+red+"\"]\n[Black \"Other\"]\n[Date \""+date+"\"]\n[Result \"*\"]\n\n"+moves+" *\n";}
    private static View findDescription(View view,String description){if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View match=findDescription(group.getChildAt(i),description);if(match!=null)return match;}}return null;}
    private static View findText(View view,String text){if(view instanceof TextView&&((TextView)view).getText().toString().startsWith(text))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View match=findText(group.getChildAt(i),text);if(match!=null)return match;}}return null;}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){String key=item.getKey();Object value=item.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;edit.putStringSet(key,set);}}edit.commit();}
}
