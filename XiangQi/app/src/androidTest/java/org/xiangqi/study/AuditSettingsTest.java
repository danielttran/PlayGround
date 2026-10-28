package org.xiangqi.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.Map;

/** Tests spoiler-safe defaults, persisted appearance, and real Settings controls. */
public final class AuditSettingsTest extends InstrumentationTestCase {
    public void testAppearanceDefaultsAndRoundTrip() {
        Context target=getInstrumentation().getTargetContext();String name="appearance-audit-"+System.nanoTime();
        Context storage=new ContextWrapper(target){
            @Override public SharedPreferences getSharedPreferences(String ignored,int mode){return target.getSharedPreferences(name,mode);}
        };
        try {
            StudySettings defaults=StudySettings.load(storage);assertTrue(defaults.hideResults);
            assertEquals("moss",defaults.boardStyle);assertEquals("traditional",defaults.glyphStyle);assertEquals("cream",defaults.discStyle);
            assertEquals("none",defaults.coordinateStyle);StudySettings.save(storage,"ink","simplified","jade","numeric",false);
            StudySettings saved=StudySettings.load(storage);assertFalse(saved.hideResults);
            assertEquals("ink",saved.boardStyle);assertEquals("simplified",saved.glyphStyle);assertEquals("jade",saved.discStyle);
            assertEquals("numeric",saved.coordinateStyle);
            StudySettings.save(storage,"unknown board","icons","flat",true);
            StudySettings repaired=StudySettings.load(storage);assertEquals("moss",repaired.boardStyle);
            assertEquals("traditional",repaired.glyphStyle);assertEquals("cream",repaired.discStyle);
            assertEquals("Legacy appearance save must preserve the coordinate choice","numeric",repaired.coordinateStyle);
            StudySettings.save(storage,"moss","traditional","cream","invalid",true);assertEquals("none",StudySettings.load(storage).coordinateStyle);
        } finally {target.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit();}
    }
    public void testSettingsHideCardSpoilersAndPersistChineseStyleChoices() throws Exception {
        Context target=getInstrumentation().getTargetContext();SharedPreferences preferences=target.getSharedPreferences(StudySettings.PREFERENCES,Context.MODE_PRIVATE);
        Map<String,?> previous=preferences.getAll();StudySettings.save(target,"moss","traditional","cream","none",true);
        Intent intent=new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity=getInstrumentation().startActivitySync(intent);
        try {
            waitFor(activity,"Settings");
            getInstrumentation().runOnMainSync(()->{View back=findText(activity.getWindow().getDecorView(),"‹  Library");if(back!=null)back.performClick();});
            waitFor(activity,"Xiangqi Study");
            getInstrumentation().runOnMainSync(()->{
                assertNull("Hidden results must not appear on library cards",findText(activity.getWindow().getDecorView(),"0–1"));
                assertFalse("Card accessibility must not reveal results",hasResultDescription(activity.getWindow().getDecorView()));
                findText(activity.getWindow().getDecorView(),"Settings").performClick();
            });
            final AlertDialog[] dialog={null};Field field=MainActivity.class.getDeclaredField("settingsDialog");field.setAccessible(true);
            getInstrumentation().runOnMainSync(()->{try{dialog[0]=(AlertDialog)field.get(activity);}catch(Exception e){throw new RuntimeException(e);}});
            assertNotNull(dialog[0]);
            getInstrumentation().runOnMainSync(()->{
                CheckBox hide=(CheckBox)findText(dialog[0].getWindow().getDecorView(),"Hide game results");assertTrue(hide.isChecked());hide.performClick();
                assertFalse(StudySettings.load(target).hideResults);
                assertNotNull("Results are shown only after user disables spoiler hiding",findText(activity.getWindow().getDecorView(),"0–1"));
                assertTrue(hasResultDescription(activity.getWindow().getDecorView()));hide.performClick();
                ((Spinner)findDescription(dialog[0].getWindow().getDecorView(),"Board style")).setSelection(2);
                ((Spinner)findDescription(dialog[0].getWindow().getDecorView(),"Chinese piece characters")).setSelection(1);
                ((Spinner)findDescription(dialog[0].getWindow().getDecorView(),"Piece finish")).setSelection(2);
                ((Spinner)findDescription(dialog[0].getWindow().getDecorView(),"Board coordinates")).setSelection(2);
            });
            getInstrumentation().waitForIdleSync();
            StudySettings saved=StudySettings.load(target);assertTrue(saved.hideResults);assertEquals("ink",saved.boardStyle);
            assertEquals("simplified",saved.glyphStyle);assertEquals("jade",saved.discStyle);
            assertEquals("numeric",saved.coordinateStyle);
            getInstrumentation().runOnMainSync(()->{
                dialog[0].dismiss();
                assertNull(findText(activity.getWindow().getDecorView(),"0–1"));assertFalse(hasResultDescription(activity.getWindow().getDecorView()));
                View player=findText(activity.getWindow().getDecorView(),"Yin Sheng");assertNotNull(player);((View)player.getParent().getParent()).performClick();
                assertNull("Replay heading must hide the result too",findTextContaining(activity.getWindow().getDecorView(),"0–1"));
                assertNotNull(findText(activity.getWindow().getDecorView(),"Settings"));
            });
            // Visibility preferences never rewrite source results used by export.
            GameRepository.Game game=new GameRepository(target).load().get(0);assertEquals("0-1",game.result);
            assertTrue(GameRepository.export(game,game.moves,false).contains("[Result \"0-1\"]"));
        } finally {
            getInstrumentation().runOnMainSync(activity::finish);getInstrumentation().waitForIdleSync();
            SharedPreferences.Editor restore=preferences.edit().clear();
            for(Map.Entry<String,?> entry:previous.entrySet()){
                Object value=entry.getValue();if(value instanceof Boolean)restore.putBoolean(entry.getKey(),(Boolean)value);
                else if(value instanceof String)restore.putString(entry.getKey(),(String)value);
            }
            restore.commit();
        }
    }
    private void waitFor(Activity activity,String prefix) throws Exception {
        long deadline=SystemClock.uptimeMillis()+10000;boolean[] found={false};
        while(SystemClock.uptimeMillis()<deadline){getInstrumentation().runOnMainSync(()->found[0]=findText(activity.getWindow().getDecorView(),prefix)!=null);if(found[0])return;Thread.sleep(50);}
        fail("UI did not display "+prefix);
    }
    private static View findText(View view,String prefix){if(view instanceof TextView&&((TextView)view).getText().toString().startsWith(prefix))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),prefix);if(found!=null)return found;}}return null;}
    private static View findTextContaining(View view,String value){if(view instanceof TextView&&((TextView)view).getText().toString().contains(value))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findTextContaining(group.getChildAt(i),value);if(found!=null)return found;}}return null;}
    private static View findDescription(View view,String value){if(value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),value);if(found!=null)return found;}}return null;}
    private static boolean hasResultDescription(View view){CharSequence description=view.getContentDescription();if(description!=null&&description.toString().contains(", result "))return true;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(hasResultDescription(group.getChildAt(i)))return true;}return false;}
}
