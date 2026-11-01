package org.xiangqi.study;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.test.InstrumentationTestCase;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import org.xiangqi.study.core.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Theme changes must preserve the live game, variations and independent board colors. */
public final class ThemeUiTest extends InstrumentationTestCase {
    private HomeActivity home;private Application application;private Context context;
    private final Set<Activity> activities=new CopyOnWriteArraySet<>();
    private final Map<String,Map<String,?>> preferences=new HashMap<>();
    private ComputerGameStore store;private String fixture="";
    private final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
        public void onActivityCreated(Activity a,Bundle b){activities.add(a);}public void onActivityDestroyed(Activity a){activities.remove(a);}
        public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}
    };
    @Override protected void setUp()throws Exception{
        super.setUp();context=getInstrumentation().getTargetContext();application=(Application)context.getApplicationContext();
        for(String name:new String[]{"home","study",StudySettings.PREFERENCES})preferences.put(name,context.getSharedPreferences(name,0).getAll());
        context.getSharedPreferences("home",0).edit().clear().putString("screen","options").commit();
        AppTheme.setPalette(context,"cobalt");AppTheme.setMode(context,"dark");
        application.registerActivityLifecycleCallbacks(lifecycle);store=ComputerGameStore.get(context);
        android.view.accessibility.AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText("System UI isn't responding").isEmpty())for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Wait")){node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);Thread.sleep(300);break;}
        home=(HomeActivity)getInstrumentation().startActivitySync(new Intent(context,HomeActivity.class).putExtra("homeScreen","options").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));ready(home,"ready");
    }
    @Override protected void tearDown()throws Exception{
        ComputerReview.pause();long end=SystemClock.uptimeMillis()+8000;while(!activities.isEmpty()&&SystemClock.uptimeMillis()<end){ui(()->{for(Activity a:new ArrayList<>(activities))if(!a.isFinishing())a.finish();});Thread.sleep(50);}
        application.unregisterActivityLifecycleCallbacks(lifecycle);
        for(Class<?> type:new Class<?>[]{HomeActivity.class,MainActivity.class}){Field f=type.getDeclaredField(type==HomeActivity.class?"IO":"FILE_IO");f.setAccessible(true);((ExecutorService)f.get(null)).submit(()->{}).get(15,TimeUnit.SECONDS);}
        if(!fixture.isEmpty())store.getWritableDatabase().delete("games","id=?",new String[]{fixture});
        for(Map.Entry<String,Map<String,?>> entry:preferences.entrySet())restore(context.getSharedPreferences(entry.getKey(),0),entry.getValue());super.tearDown();
    }
    public void testAllPaletteModesNativeWidgetsAndBoardIndependence()throws Exception{
        StudySettings.selectBoardPreset(context,"warm-maple");StudySettings.selectDiscPreset(context,"obsidian");StudySettings.setCustomColor(context,"boardColor",0xff765432);StudySettings.setCustomColor(context,"redBackground",0xff9b2339);StudySettings.setCustomColor(context,"blackBackground",0xff18212c);
        for(String id:UiPalette.ids()){
            themeClick("App theme: "+UiPalette.get(id,false).label);
            for(String mode:new String[]{"light","dark"}){
                themeClick("Display mode: "+(mode.equals("light")?"Light":"Dark"));
                ui(()->{AppTheme theme=AppTheme.load(home);UiPalette colors=theme.colors;assertEquals(id,colors.id);assertEquals(mode.equals("dark"),colors.dark);assertEquals(colors.background,field(home,"BG"));assertEquals(colors.text,field(home,"LIGHT"));assertEquals(colors.accent,attribute(home,android.R.attr.colorAccent));
                    AlertDialog dialog=new AlertDialog.Builder(home).setTitle("Theme audit").setMessage("Readable native dialog").create();assertEquals(colors.accent,attribute(dialog.getContext(),android.R.attr.colorAccent));assertEquals(colors.text,attribute(dialog.getContext(),android.R.attr.textColorPrimary));assertEquals(colors.surface,attribute(dialog.getContext(),android.R.attr.colorBackground));
                    if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=home.getWindow().getInsetsController();assertNotNull(controller);assertEquals(!colors.dark,(controller.getSystemBarsAppearance()&WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)!=0);}
                    StudySettings board=StudySettings.load(home);assertEquals("warm-maple",board.boardStyle);assertEquals("obsidian",board.discStyle);assertEquals(Integer.valueOf(0xff765432),board.boardColor);assertEquals(Integer.valueOf(0xff9b2339),board.redBackground);assertEquals(Integer.valueOf(0xff18212c),board.blackBackground);
                });if(id.equals("cobalt"))capture(home,"theme-cobalt-"+mode);
            }
        }
        themeClick("Display mode: System");ui(()->{boolean night=(home.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;assertEquals(night,AppTheme.load(home).colors.dark);});
        for(int night:new int[]{Configuration.UI_MODE_NIGHT_YES,Configuration.UI_MODE_NIGHT_NO}){Configuration c=new Configuration(context.getResources().getConfiguration());c.uiMode=(c.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|night;assertEquals(night==Configuration.UI_MODE_NIGHT_YES,AppTheme.load(context.createConfigurationContext(c)).colors.dark);}
    }
    public void testLightColorPickerAndNewGameKeepChosenPalette()throws Exception{
        themeClick("Display mode: Light");ui(()->{UiPalette colors=AppTheme.load(home).colors;AlertDialog dialog=ColorPickerDialog.show(home,"Board color",0xff888888,value->{});try{assertEquals(colors.accent,attribute(dialog.getContext(),android.R.attr.colorAccent));EditText hex=findEdit(dialog.getWindow().getDecorView());assertNotNull(hex);assertEquals(colors.text,hex.getCurrentTextColor());assertEquals(Integer.valueOf(0xff888888),Integer.valueOf(android.graphics.Color.parseColor(hex.getText().toString())));}finally{dialog.dismiss();}
            invoke(home,"showScreen",new Class<?>[]{String.class},"play");assertEquals(colors.background,field(home,"BG"));assertNotNull(findDescription(home.getWindow().getDecorView(),"Play as Black"));});capture(home,"theme-play-light");
    }
    public void testLatestExplicitChoiceWinsOverPendingPreferenceChange()throws Exception{
        Instrumentation.ActivityMonitor mode=getInstrumentation().addMonitor(HomeActivity.class.getName(),null,false);
        ui(()->{AppTheme.setMode(context,"light");findDescription(home.getWindow().getDecorView(),"Display mode: Dark").performClick();});home=(HomeActivity)mode.waitForActivityWithTimeout(15000);getInstrumentation().removeMonitor(mode);assertNotNull(home);ready(home,"ready");assertEquals("dark",AppTheme.load(home).mode);
        Instrumentation.ActivityMonitor palette=getInstrumentation().addMonitor(HomeActivity.class.getName(),null,false);
        ui(()->{AppTheme.setPalette(context,"amber");findDescription(home.getWindow().getDecorView(),"App theme: Cobalt").performClick();});home=(HomeActivity)palette.waitForActivityWithTimeout(15000);getInstrumentation().removeMonitor(palette);assertNotNull(home);ready(home,"ready");assertEquals("cobalt",AppTheme.load(home).colors.id);
    }
    public void testPlayingGameAndFlippedBoardSurviveThemeRecreation()throws Exception{
        ComputerGameStore.SavedGame game=new ComputerGameStore.SavedGame();fixture=game.id="played-theme-audit-"+UUID.randomUUID();game.level=42;game.moves.addAll(Arrays.asList("b2e2","b9c7"));store.save(game);
        ui(()->{set(home,"current",game);set(home,"playing",true);set(home,"flip",true);invoke(home,"showScreen",new Class<?>[]{String.class},"options");});themeClick("Display mode: Light");themeClick("App theme: Amber");
        ui(()->{ComputerGameStore.SavedGame restored=(ComputerGameStore.SavedGame)field(home,"current");assertEquals(fixture,restored.id);assertEquals(42,restored.level);assertEquals(game.moves,restored.moves);assertEquals(Boolean.TRUE,field(home,"playing"));assertEquals(Boolean.TRUE,field(home,"flip"));invoke(home,"showScreen",new Class<?>[]{String.class},"play");assertEquals(Boolean.TRUE,field(field(home,"playBoard"),"flipped"));});
    }
    public void testViewerVariationAnalysisAndScrollSurviveThemeReturn()throws Exception{
        context.getSharedPreferences("study",0).edit().putBoolean("analysisEnabled",false).commit();GameRepository.Game game=new GameRepository(context).load().get(0);
        MainActivity viewer=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).putExtra(HomeActivity.FROM_HOME,true).putExtra(HomeActivity.SOURCE_ID,game.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ready(viewer,"loaded");
        final String[] fen={null};final int[] scroll={0};ui(()->{invoke(viewer,"navigate",new Class<?>[]{int.class},3);@SuppressWarnings("unchecked")List<Position> positions=(List<Position>)field(viewer,"positions");String alternative=positions.get(3).legalMoves().stream().filter(move->!move.equals(game.moves.get(3))).findFirst().get();invoke(viewer,"makeMove",new Class<?>[]{String.class},alternative);set(viewer,"flipped",true);context.getSharedPreferences("study",0).edit().putBoolean("analysisEnabled",true).apply();set(viewer,"analysisEnabled",true);@SuppressWarnings("unchecked")List<Position> changed=(List<Position>)field(viewer,"positions");fen[0]=changed.get((Integer)field(viewer,"ply")).toFen();invoke(viewer,"updateReplay");});ui(()->{ListView record=(ListView)field(viewer,"movesView");record.setSelection(record.getCount()-1);});ui(()->scroll[0]=((ListView)field(viewer,"movesView")).getFirstVisiblePosition());
        Instrumentation.ActivityMonitor options=getInstrumentation().addMonitor(HomeActivity.class.getName(),null,false);ui(()->invoke(viewer,"settings"));home=(HomeActivity)options.waitForActivityWithTimeout(15000);getInstrumentation().removeMonitor(options);assertNotNull(home);ready(home,"ready");themeClick("Display mode: Light");themeClick("App theme: Graphite");
        Instrumentation.ActivityMonitor resumed=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);ui(home::finish);MainActivity replacement=(MainActivity)resumed.waitForActivityWithTimeout(15000);getInstrumentation().removeMonitor(resumed);assertNotNull("Viewer must rebuild with the selected theme",replacement);ready(replacement,"loaded");
        ui(()->{assertEquals("graphite",AppTheme.load(replacement).colors.id);assertFalse(AppTheme.load(replacement).colors.dark);assertEquals(Boolean.TRUE,field(replacement,"exploring"));assertEquals(Boolean.TRUE,field(replacement,"flipped"));assertEquals(Boolean.TRUE,field(replacement,"analysisEnabled"));@SuppressWarnings("unchecked")List<Position> current=(List<Position>)field(replacement,"positions");assertEquals(fen[0],current.get((Integer)field(replacement,"ply")).toFen());assertEquals(scroll[0],((ListView)field(replacement,"movesView")).getFirstVisiblePosition());if(Build.VERSION.SDK_INT>=30)assertTrue((replacement.getWindow().getInsetsController().getSystemBarsAppearance()&WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)!=0);invoke(replacement,"stopSearch");});capture(replacement,"theme-review-light");
    }
    private void themeClick(String label)throws Exception{
        final boolean[] changed={false};Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(HomeActivity.class.getName(),null,false);
        ui(()->{View button=findDescription(home.getWindow().getDecorView(),label);assertNotNull(label,button);if(!button.isSelected()){changed[0]=true;button.performClick();}});
        if(changed[0]){HomeActivity next=(HomeActivity)monitor.waitForActivityWithTimeout(15000);assertNotNull("Theme did not recreate the screen",next);home=next;ready(home,"ready");}getInstrumentation().removeMonitor(monitor);
    }
    private void ready(Object target,String flag)throws Exception{await(()->Boolean.TRUE.equals(field(target,flag)),20000,"Screen did not load");ui(()->{});}
    private void capture(Activity activity,String name)throws Exception{ui(()->{});Thread.sleep(300);android.view.accessibility.AccessibilityNodeInfo active=getInstrumentation().getUiAutomation().getRootInActiveWindow();assertNotNull(active);assertEquals("org.xiangqi.study",String.valueOf(active.getPackageName()));android.graphics.Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),name+".png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}
    private static int attribute(Context c,int attr){TypedValue value=new TypedValue();if(!c.getTheme().resolveAttribute(attr,value,true))throw new AssertionError("Missing theme attribute "+attr);return value.data;}
    private static View findDescription(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findDescription(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private static EditText findEdit(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText found=findEdit(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object target,String name,Object value){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new AssertionError(e);}}
    private static void invoke(Object target,String name,Class<?>[] types,Object... values){try{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(target,values);}catch(Exception e){throw new AssertionError(e);}}
    private static void invoke(Object target,String name){invoke(target,name,new Class<?>[0]);}
    private void ui(Runnable run){java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();getInstrumentation().runOnMainSync(()->{try{run.run();}catch(Throwable e){error.set(e);}});getInstrumentation().waitForIdleSync();if(error.get()!=null)throw new AssertionError(error.get());}
    private static void await(BooleanSupplier test,long millis,String message)throws Exception{long end=SystemClock.uptimeMillis()+millis;while(SystemClock.uptimeMillis()<end){if(test.getAsBoolean())return;Thread.sleep(40);}throw new AssertionError(message);}
    @SuppressWarnings("unchecked") private static void restore(android.content.SharedPreferences p,Map<String,?> data){android.content.SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:data.entrySet()){Object value=item.getValue();if(value instanceof String)e.putString(item.getKey(),(String)value);else if(value instanceof Integer)e.putInt(item.getKey(),(Integer)value);else if(value instanceof Long)e.putLong(item.getKey(),(Long)value);else if(value instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)value);else if(value instanceof Float)e.putFloat(item.getKey(),(Float)value);else if(value instanceof Set)e.putStringSet(item.getKey(),new HashSet<>((Set<String>)value));}e.commit();}
}
