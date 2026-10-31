package org.xiangqi.study;
import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.test.InstrumentationTestCase;
import android.view.*;
import android.widget.*;
import org.xiangqi.study.core.AppearancePresets;
import java.lang.reflect.*;
import java.util.*;

public final class AppearanceArrowTest extends InstrumentationTestCase {
    private Activity activity;private SharedPreferences appearance,study;private Map<String,?> oldAppearance,oldStudy;
    protected void setUp()throws Exception{
        super.setUp();Context c=getInstrumentation().getTargetContext();appearance=c.getSharedPreferences("appearance",0);study=c.getSharedPreferences("study",0);oldAppearance=appearance.getAll();oldStudy=study.getAll();study.edit().clear().commit();appearance.edit().clear().putString("board","moss").putString("disc","cream").putInt("boardColor",0xff123456).putInt("redBackground",0xff654321).commit();
        activity=getInstrumentation().startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        long end=android.os.SystemClock.uptimeMillis()+15000;while(android.os.SystemClock.uptimeMillis()<end){boolean[] loaded={false};main(()->loaded[0]=(Boolean)get("loaded"));if(loaded[0])break;Thread.sleep(40);}UiTestActions.awaitLibrarySearch(getInstrumentation(),activity);main(()->call("settings"));getInstrumentation().waitForIdleSync();
    }
    protected void tearDown()throws Exception{if(activity!=null)main(activity::finish);getInstrumentation().waitForIdleSync();restore(appearance,oldAppearance);restore(study,oldStudy);super.tearDown();}
    public void testArrowsCycleLiveAndKeepThePreviewVisible()throws Exception{
        main(()->{
            assertNull(find(dialogRoot(),Spinner.class));BoardView preview=(BoardView)find(dialogRoot(),BoardView.class);assertEquals(0xff123456,preview.getBoardColor());assertEquals(Integer.valueOf(0xff123456),StudySettings.load(activity).boardColor);
            String[] ids=AppearancePresets.boardIds();int original=Arrays.asList(ids).indexOf("moss");
            for(int i=1;i<=ids.length;i++){press("Next Board style");StudySettings s=StudySettings.load(activity);assertEquals(ids[(original+i)%ids.length],s.boardStyle);assertEquals(AppearancePresets.board(s.boardStyle).background,preview.getBoardColor());assertEquals(Integer.valueOf(0xff654321),s.redBackground);}
            press("Previous Board style");assertEquals(ids[Math.floorMod(original-1,ids.length)],StudySettings.load(activity).boardStyle);
            press("Next Piece finish");StudySettings s=StudySettings.load(activity);assertNull(s.redBackground);assertEquals(AppearancePresets.disc(s.discStyle).redBackground,preview.getPieceBackground(true));
            for(int i=1;i<AppearancePresets.discIds().length;i++)press("Next Piece finish");assertEquals("cream",StudySettings.load(activity).discStyle);press("Previous Piece finish");
            press("Next Board coordinates");assertEquals("iccs",field(preview,"coordinateStyle"));press("Previous Board coordinates");assertEquals("none",field(preview,"coordinateStyle"));press("Previous Board coordinates");assertEquals("numeric",field(preview,"coordinateStyle"));
            press("Next Chinese piece characters");assertEquals(true,field(preview,"simplified"));press("Next Chinese piece characters");assertEquals(false,field(preview,"simplified"));
        });getInstrumentation().waitForIdleSync();
        final Rect[] before={null};main(()->{BoardView p=(BoardView)find(dialogRoot(),BoardView.class);before[0]=bounds(p);View coordinates=findDescription(dialogRoot(),"Next Board coordinates");assertTrue(bounds(coordinates).height()>0);((ScrollView)find(dialogRoot(),ScrollView.class)).fullScroll(View.FOCUS_DOWN);});getInstrumentation().waitForIdleSync();main(()->assertEquals(before[0],bounds(find(dialogRoot(),BoardView.class))));
    }
    public void testReopeningKeepsChoicesAndDoesNotEraseCustomColors()throws Exception{
        main(()->{press("Next Board style");press("Next Piece finish");press("Next Board coordinates");StudySettings.setCustomColor(activity,"boardColor",0xff234567);((AlertDialog)get("settingsDialog")).dismiss();call("settings");});getInstrumentation().waitForIdleSync();main(()->{assertEquals(Integer.valueOf(0xff234567),StudySettings.load(activity).boardColor);assertEquals(0xff234567,((BoardView)find(dialogRoot(),BoardView.class)).getBoardColor());assertEquals("iccs",field(find(dialogRoot(),BoardView.class),"coordinateStyle"));});
    }
    private void press(String description){View v=findDescription(dialogRoot(),description);assertNotNull(description,v);assertTrue(v.performClick());}
    private Rect bounds(View v){Rect r=new Rect();v.getGlobalVisibleRect(r);return r;}
    private View dialogRoot(){return ((AlertDialog)get("settingsDialog")).getWindow().getDecorView();}
    private Object get(String name){return field(activity,name);}
    private Object field(Object value,String name){try{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(Exception e){throw new RuntimeException(e);}}
    private void call(String name){try{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private void main(Runnable r){UiTestActions.runOnMain(getInstrumentation(),r);}
    private View find(View v,Class<?> type){if(type.isInstance(v))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=find(g.getChildAt(i),type);if(x!=null)return x;}}return null;}
    private View findDescription(View v,String text){if(text.equals(v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=findDescription(g.getChildAt(i),text);if(x!=null)return x;}}return null;}
    private void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:values.entrySet()){Object v=x.getValue();if(v instanceof String)e.putString(x.getKey(),(String)v);else if(v instanceof Integer)e.putInt(x.getKey(),(Integer)v);else if(v instanceof Boolean)e.putBoolean(x.getKey(),(Boolean)v);else if(v instanceof Long)e.putLong(x.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(x.getKey(),(Float)v);else if(v instanceof Set)e.putStringSet(x.getKey(),(Set<String>)v);}e.commit();}
}
