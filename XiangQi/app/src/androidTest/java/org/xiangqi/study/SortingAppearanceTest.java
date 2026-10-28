package org.xiangqi.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.CheckBox;
import org.xiangqi.study.core.PlayerNames;
import org.xiangqi.study.core.Pgn;
import org.xiangqi.study.core.AppearancePresets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real date-sort, color-entry and appearance persistence controls. */
public final class SortingAppearanceTest extends InstrumentationTestCase {
    private Activity activity;
    private SharedPreferences study,appearance;
    private Map<String,?> oldStudy,oldAppearance;
    @Override protected void setUp() throws Exception {
        super.setUp();Context context=getInstrumentation().getTargetContext();
        study=context.getSharedPreferences("study",0);appearance=context.getSharedPreferences(StudySettings.PREFERENCES,0);
        oldStudy=study.getAll();oldAppearance=appearance.getAll();study.edit().clear().commit();appearance.edit().clear().commit();
    }
    @Override protected void tearDown() throws Exception {
        if(activity!=null&&!activity.isDestroyed())main(activity::finish);
        getInstrumentation().waitForIdleSync();
        long deadline=SystemClock.uptimeMillis()+5000;
        while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<deadline)Thread.sleep(50);
        restore(study,oldStudy);restore(appearance,oldAppearance);super.tearDown();
    }
    public void testIndependentCustomColorsAndPresetMigration() {
        Context target=getInstrumentation().getTargetContext();String name="custom-color-audit-"+System.nanoTime();
        Context storage=new ContextWrapper(target){@Override public SharedPreferences getSharedPreferences(String ignored,int mode){return target.getSharedPreferences(name,mode);}};
        try {
            StudySettings initial=StudySettings.load(storage);assertNull(initial.boardColor);assertNull(initial.redBackground);assertNull(initial.blackBackground);
            StudySettings.save(storage,"bamboo","traditional","obsidian","none",true);
            StudySettings.setCustomColor(storage,"boardColor",0x00123456);StudySettings.setCustomColor(storage,"redBackground",0x00784512);
            StudySettings.setCustomColor(storage,"blackBackground",0x80e8e6dc);
            StudySettings.save(storage,"bamboo","simplified","obsidian","numeric",false);
            StudySettings saved=StudySettings.load(storage);
            assertEquals(Integer.valueOf(0xff123456),saved.boardColor);assertEquals(Integer.valueOf(0xff784512),saved.redBackground);
            assertEquals(Integer.valueOf(0xffe8e6dc),saved.blackBackground);assertEquals("bamboo",saved.boardStyle);assertEquals("obsidian",saved.discStyle);
            assertEquals("numeric",saved.coordinateStyle);assertFalse(saved.hideResults);
            StudySettings.setCustomColor(storage,"redBackground",null);saved=StudySettings.load(storage);
            assertNull(saved.redBackground);assertEquals(Integer.valueOf(0xff123456),saved.boardColor);assertEquals(Integer.valueOf(0xffe8e6dc),saved.blackBackground);
            StudySettings.resetCustomColors(storage);saved=StudySettings.load(storage);
            assertNull(saved.boardColor);assertNull(saved.redBackground);assertNull(saved.blackBackground);
            assertEquals("bamboo",saved.boardStyle);assertEquals("obsidian",saved.discStyle);assertEquals("numeric",saved.coordinateStyle);
            boolean rejected=false;try{StudySettings.setCustomColor(storage,"unrelated",0);}catch(IllegalArgumentException expected){rejected=true;}assertTrue(rejected);
        } finally {target.getSharedPreferences(name,0).edit().clear().commit();}
    }
    public void testDateSortControlSearchAndPersistence() throws Exception {
        launch();
        main(()->{
            try {
                field("games").set(activity,Arrays.asList(game("Unknown","1982.??.??","Archive A"),game("Latest","2025-09-27","Archive B"),
                    game("Earliest","1982.10.04","Historical Audit Collection"),game("Latest tie","2025.09.27","Archive B")));
                Method render=MainActivity.class.getDeclaredMethod("renderCards");render.setAccessible(true);render.invoke(activity);
                assertEquals("Latest",firstRed());
                Spinner sort=(Spinner)findDescription(root(),"Game sort order");assertEquals(1,sort.getSelectedItemPosition());sort.setSelection(2);
            }catch(Exception e){throw new RuntimeException(e);}
        });
        getInstrumentation().waitForIdleSync();main(()->assertEquals("Earliest",firstRed()));assertEquals("oldest",study.getString("librarySort",""));
        main(()->((EditText)findType(root(),EditText.class)).setText("Historical Audit Collection"));getInstrumentation().waitForIdleSync();
        main(()->{assertEquals(1,cards().getChildCount());assertEquals("Earliest",firstRed());((EditText)findType(root(),EditText.class)).setText("");});
        getInstrumentation().waitForIdleSync();
        main(()->((Spinner)findDescription(root(),"Game sort order")).setSelection(0));getInstrumentation().waitForIdleSync();main(()->assertEquals("Unknown",firstRed()));
        main(()->((Spinner)findDescription(root(),"Game sort order")).setSelection(2));getInstrumentation().waitForIdleSync();
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        main(activity::recreate);Activity recreated=monitor.waitForActivityWithTimeout(10000);getInstrumentation().removeMonitor(monitor);assertNotNull(recreated);activity=recreated;awaitLibrary();
        main(()->assertEquals("Sort selection must survive recreation",2,((Spinner)findDescription(root(),"Game sort order")).getSelectedItemPosition()));
    }
    public void testLibraryPaginationStaysBoundedAndSearchAndSortResetIt() throws Exception {
        launch();List<GameRepository.Game> fixtures=new ArrayList<>();for(int i=0;i<85;i++)fixtures.add(game("Page fixture "+i,"2025-09-27","Pagination audit"));
        main(()->{try{field("games").set(activity,fixtures);Method render=MainActivity.class.getDeclaredMethod("renderCards");render.setAccessible(true);render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();main(()->{assertEquals(40,cards().getChildCount());assertEquals("Page fixture 0",firstRed());assertFalse(findText(root(),"Previous 40 games").isEnabled());findText(root(),"Next 40 games").performClick();});
        getInstrumentation().waitForIdleSync();main(()->{assertEquals(40,cards().getChildCount());assertEquals("Page fixture 40",firstRed());findText(root(),"Next 40 games").performClick();});
        getInstrumentation().waitForIdleSync();main(()->{assertEquals("Final page contains only remaining matches",5,cards().getChildCount());assertEquals("Page fixture 80",firstRed());assertFalse(findText(root(),"Next 40 games").isEnabled());findText(root(),"Previous 40 games").performClick();});
        getInstrumentation().waitForIdleSync();main(()->{assertEquals(40,cards().getChildCount());assertEquals("Page fixture 40",firstRed());((EditText)findType(root(),EditText.class)).setText("Page fixture 82");});
        getInstrumentation().waitForIdleSync();main(()->{assertEquals("Search scans records outside the visible page",1,cards().getChildCount());assertEquals("Page fixture 82",firstRed());assertNull(findText(root(),"Next 40 games"));((EditText)findType(root(),EditText.class)).setText("");});
        getInstrumentation().waitForIdleSync();main(()->{assertEquals("Clearing search returns to the first page","Page fixture 0",firstRed());findText(root(),"Next 40 games").performClick();});
        getInstrumentation().waitForIdleSync();main(()->{assertEquals("Page fixture 40",firstRed());((Spinner)findDescription(root(),"Game sort order")).setSelection(2);});
        getInstrumentation().waitForIdleSync();main(()->{assertEquals("Sorting returns to the first page","Page fixture 0",firstRed());assertEquals(40,cards().getChildCount());});
    }
    public void testSettingsColorPickerCommitsAndResetsIndependently() throws Exception {
        launch();main(()->findText(root(),"Settings").performClick());getInstrumentation().waitForIdleSync();
        AlertDialog settings=dialog("settingsDialog");assertNotNull(settings);
        main(()->{
            Spinner boards=(Spinner)findDescription(settings.getWindow().getDecorView(),"Board style");
            Spinner glyphs=(Spinner)findDescription(settings.getWindow().getDecorView(),"Chinese piece characters");
            Spinner discs=(Spinner)findDescription(settings.getWindow().getDecorView(),"Piece finish");
            assertEquals(AppearancePresets.boardIds().length,boards.getCount());assertEquals(2,glyphs.getCount());assertEquals(AppearancePresets.discIds().length,discs.getCount());boards.setSelection(3);glyphs.setSelection(1);discs.setSelection(5);
        });getInstrumentation().waitForIdleSync();
        assertEquals("rosewood",StudySettings.load(activity).boardStyle);assertEquals("simplified",StudySettings.load(activity).glyphStyle);assertEquals("obsidian",StudySettings.load(activity).discStyle);
        openColor(settings,"Board color");AlertDialog picker=dialog("colorDialog");
        main(()->{
            EditText hex=(EditText)findDescription(picker.getWindow().getDecorView(),"Hex color");hex.setText("#12");
            assertFalse(picker.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());assertNull(StudySettings.load(activity).boardColor);
            hex.setText("#12345678");assertFalse("Unsupported alpha values must not be truncated into valid RGB",picker.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
            hex.setText("#123456");assertTrue(picker.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
            assertNull("Previewing must not commit a color",StudySettings.load(activity).boardColor);picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });getInstrumentation().waitForIdleSync();assertEquals(Integer.valueOf(0xff123456),StudySettings.load(activity).boardColor);
        applyColor(settings,"Red piece background","#F4E2CC");applyColor(settings,"Black piece background","#102030");
        main(()->{
            StudySettings current=StudySettings.load(activity);assertEquals(Integer.valueOf(0xff123456),current.boardColor);
            assertEquals(Integer.valueOf(0xfff4e2cc),current.redBackground);assertEquals(Integer.valueOf(0xff102030),current.blackBackground);
            BoardView preview=(BoardView)findType(settings.getWindow().getDecorView(),BoardView.class);assertEquals(0xff123456,preview.getBoardColor());
            assertEquals(0xfff4e2cc,preview.getPieceBackground(true));assertEquals(0xff102030,preview.getPieceBackground(false));
            ((Spinner)findDescription(settings.getWindow().getDecorView(),"Board style")).setSelection(4);
        });getInstrumentation().waitForIdleSync();assertNull("Latest board preset replaces the custom board color",StudySettings.load(activity).boardColor);
        openColor(settings,"Red piece background");AlertDialog redPicker=dialog("colorDialog");main(()->redPicker.getButton(AlertDialog.BUTTON_NEUTRAL).performClick());getInstrumentation().waitForIdleSync();
        assertNull(StudySettings.load(activity).redBackground);assertEquals(Integer.valueOf(0xff102030),StudySettings.load(activity).blackBackground);
        openColor(settings,"Board color");AlertDialog cancel=dialog("colorDialog");main(()->{((EditText)findDescription(cancel.getWindow().getDecorView(),"Hex color")).setText("#ABCDEF");cancel.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});
        assertNull("Cancelling the picker must keep the latest board preset",StudySettings.load(activity).boardColor);
        main(()->findText(settings.getWindow().getDecorView(),"Reset custom colors").performClick());getInstrumentation().waitForIdleSync();
        StudySettings reset=StudySettings.load(activity);assertNull(reset.boardColor);assertNull(reset.redBackground);assertNull(reset.blackBackground);assertEquals("slate",reset.boardStyle);
    }
    public void testLatestPresetAndCustomColorSelectionsWinOnlyTheirOwnSurfaces() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        StudySettings.save(context,"wood","traditional","jade","none",true);
        StudySettings.setCustomColor(context,"boardColor",0xff123456);
        StudySettings.setCustomColor(context,"redBackground",0xfff4e2cc);
        StudySettings.setCustomColor(context,"blackBackground",0xff102030);
        launch();main(()->findText(root(),"Settings").performClick());getInstrumentation().waitForIdleSync();
        AlertDialog settings=dialog("settingsDialog");
        assertColors(0xff123456,0xfff4e2cc,0xff102030);
        main(()->{BoardView preview=(BoardView)findType(settings.getWindow().getDecorView(),BoardView.class);assertEquals(0xff123456,preview.getBoardColor());assertEquals(0xfff4e2cc,preview.getPieceBackground(true));assertEquals(0xff102030,preview.getPieceBackground(false));});
        choosePreset(settings,"Board style",1); // Reselect the already selected Wood preset.
        assertColors(null,0xfff4e2cc,0xff102030);assertEquals("wood",StudySettings.load(context).boardStyle);
        main(()->{BoardView expected=new BoardView(activity);expected.setAppearance("wood","traditional","jade");assertEquals(expected.getBoardColor(),((BoardView)findType(settings.getWindow().getDecorView(),BoardView.class)).getBoardColor());});
        applyColor(settings,"Board color","#345678");assertColors(0xff345678,0xfff4e2cc,0xff102030);
        main(()->{
            ((Spinner)findDescription(settings.getWindow().getDecorView(),"Chinese piece characters")).setSelection(1);
            ((Spinner)findDescription(settings.getWindow().getDecorView(),"Board coordinates")).setSelection(2);
            ((CheckBox)findDescription(settings.getWindow().getDecorView(),"Hide game results, including accessibility announcements")).setChecked(false);
        });getInstrumentation().waitForIdleSync();
        assertColors(0xff345678,0xfff4e2cc,0xff102030);
        choosePreset(settings,"Piece finish",2); // Same Jade preset must replace both custom discs.
        assertColors(0xff345678,null,null);assertEquals("jade",StudySettings.load(context).discStyle);
        applyColor(settings,"Red piece background","#884422");assertColors(0xff345678,0xff884422,null);
        choosePreset(settings,"Piece finish",5);assertColors(0xff345678,null,null);
        choosePreset(settings,"Board style",4);assertColors(null,null,null);
        applyColor(settings,"Black piece background","#445566");assertColors(null,null,0xff445566);
        main(()->settings.getButton(AlertDialog.BUTTON_POSITIVE).performClick());getInstrumentation().waitForIdleSync();
        main(()->findText(root(),"Settings").performClick());getInstrumentation().waitForIdleSync();
        AlertDialog reopened=dialog("settingsDialog");assertColors(null,null,0xff445566);
        main(()->((Spinner)findDescription(reopened.getWindow().getDecorView(),"Piece finish")).performClick());getInstrumentation().waitForIdleSync();
        AlertDialog oldPreset=dialog("presetDialog");assertTrue(oldPreset.isShowing());
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        main(activity::recreate);Activity replacement=monitor.waitForActivityWithTimeout(10000);getInstrumentation().removeMonitor(monitor);assertNotNull(replacement);activity=replacement;awaitLibrary();
        assertFalse("Recreation must dismiss the preset popup",oldPreset.isShowing());assertColors(null,null,0xff445566);
        main(()->findText(root(),"Settings").performClick());getInstrumentation().waitForIdleSync();
        AlertDialog restored=dialog("settingsDialog");assertColors(null,null,0xff445566);
        choosePreset(restored,"Piece finish",5);assertColors(null,null,null);
    }
    public void testSettingsSaveAndExplicitPresetApisRespectColorPrecedence() {
        Context context=getInstrumentation().getTargetContext();
        StudySettings.setCustomColor(context,"boardColor",0xff112233);
        StudySettings.setCustomColor(context,"redBackground",0xff445566);
        StudySettings.setCustomColor(context,"blackBackground",0xff778899);
        StudySettings.save(context,"wood","traditional","cream","numeric",true);
        assertColors(null,0xff445566,0xff778899);
        StudySettings.setCustomColor(context,"boardColor",0xff112233);
        StudySettings.save(context,"wood","simplified","jade","numeric",false);
        assertColors(0xff112233,null,null);
        StudySettings.setCustomColor(context,"redBackground",0xff445566);StudySettings.setCustomColor(context,"blackBackground",0xff778899);
        StudySettings.selectBoardPreset(context,"wood");assertColors(null,0xff445566,0xff778899);
        StudySettings.setCustomColor(context,"boardColor",0xff112233);
        StudySettings.selectDiscPreset(context,"jade");assertColors(0xff112233,null,null);
    }
    public void testLongPlayerNamesDoNotMoveBoardOrPositionSliderBetweenTurns() throws Exception {
        launch();GameRepository.Game game=game("Nguyễn Thành Bảo · Vietnamese master — international classical championship record","2025-09-27","");
        game.black="Andy Wo";game.moves.addAll(Arrays.asList("b2e2","b9c7","a0a1","a9a8","h2g2","h9g7"));
        main(()->{try{field("games").set(activity,Arrays.asList(game));Method open=MainActivity.class.getDeclaredMethod("openGame",GameRepository.Game.class,boolean.class);open.setAccessible(true);open.invoke(activity,game,false);}catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();
        main(()->{View seek=findType(root(),SeekBar.class);assertNotNull(seek);seek.requestRectangleOnScreen(new Rect(0,0,seek.getWidth(),seek.getHeight()),true);});getInstrumentation().waitForIdleSync();
        Rect[] bounds={null,null};main(()->{bounds[0]=screenBounds(findType(root(),BoardView.class));bounds[1]=screenBounds(findType(root(),SeekBar.class));assertTrue(bounds[0].width()>0);assertTrue(bounds[1].height()>0);});
        for(int ply:new int[]{0,1,2,3,6,5,4,1,0}) {
            main(()->{try{Method navigate=MainActivity.class.getDeclaredMethod("navigate",int.class);navigate.setAccessible(true);navigate.invoke(activity,ply);}catch(Exception e){throw new RuntimeException(e);}});getInstrumentation().waitForIdleSync();
            main(()->{
                assertEquals("Board bounds must remain fixed at half-move "+ply,bounds[0],screenBounds(findType(root(),BoardView.class)));
                assertEquals("Position slider bounds must remain fixed at half-move "+ply,bounds[1],screenBounds(findType(root(),SeekBar.class)));
                assertFalse("Side to move is a board dot, without a wrapping turn label",containsTurnText(root()));
                String description=findType(root(),BoardView.class).getContentDescription().toString();assertTrue(description.contains((ply%2==0?"Red":"Black")+" to move."));
                assertTrue(description.contains("Red: "+game.red));assertTrue(description.contains("Black: "+game.black));
            });
        }
    }
    public void testTwoThousandPlyMoveRecordKeepsCompleteHistoryWithBoundedViews() throws Exception {
        launch();GameRepository.Game game=game("Long record Red","2025-09-27","");game.black="Long record Black";
        for(int cycle=0;cycle<500;cycle++)game.moves.addAll(Arrays.asList("b0c2","b9c7","c2b0","c7b9"));
        main(()->{try{field("games").set(activity,Arrays.asList(game));Method open=MainActivity.class.getDeclaredMethod("openGame",GameRepository.Game.class,boolean.class);open.setAccessible(true);open.invoke(activity,game,false);}catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();
        main(()->{View seek=findType(root(),SeekBar.class);assertNotNull(seek);seek.requestRectangleOnScreen(new Rect(0,0,seek.getWidth(),seek.getHeight()),true);});getInstrumentation().waitForIdleSync();
        Rect[] bounds={null,null};main(()->{bounds[0]=screenBounds(findType(root(),BoardView.class));bounds[1]=screenBounds(findType(root(),SeekBar.class));});
        for(int ply:new int[]{0,1,1000,2000,999}) {
            main(()->{
                try {
                    Method navigate=MainActivity.class.getDeclaredMethod("navigate",int.class);navigate.setAccessible(true);navigate.invoke(activity,ply);
                    ViewGroup records=(ViewGroup)field("movesView").get(activity);int rows=0;
                    for(int i=0;i<records.getChildCount();i++)if(records.getChildAt(i) instanceof LinearLayout)rows++;
                    assertEquals("Move record renders a fixed40-row window",40,rows);assertTrue(records.getChildCount()<=41);
                    assertEquals("Full navigation range is retained",2000,((SeekBar)findType(root(),SeekBar.class)).getMax());
                    assertEquals(ply,((SeekBar)findType(root(),SeekBar.class)).getProgress());
                    assertEquals("Working history is never truncated",2000,((List<?>)field("workingMoves").get(activity)).size());
                    if(ply>0)assertNotNull("Selected move must remain in the current window",findDescription(root(),"After half-move "+ply+", "+game.moves.get(ply-1)));
                }catch(Exception e){throw new RuntimeException(e);}
            });getInstrumentation().waitForIdleSync();
            main(()->{assertEquals(bounds[0],screenBounds(findType(root(),BoardView.class)));assertEquals(bounds[1],screenBounds(findType(root(),SeekBar.class)));});
        }
        assertEquals("Source history is unchanged",2000,game.moves.size());
        assertEquals("Export keeps every original half-move",2000,Pgn.parse(GameRepository.export(game,game.moves,false)).get(0).moves.size());
    }
    public void testEveryBundledChinesePlayerHasReadableNamesAndSearchKeepsSourceNames() throws Exception {
        List<GameRepository.Game> games=new GameRepository(getInstrumentation().getTargetContext()).load();int checked=0;
        for(GameRepository.Game game:games) {
            if(game.imported)continue;
            for(String original:Arrays.asList(game.red,game.black)) {
            if(original.codePoints().noneMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))continue;
            assertTrue("Missing bundled name mapping: "+original,PlayerNames.mapped(original));
            assertTrue("Display must start with a readable name: "+original,PlayerNames.display(original).matches("[A-Za-z].*"));
                assertFalse(PlayerNames.source(original).isEmpty());checked++;
            }
        }
        assertTrue("Bundled source names must be checked",checked>0);
        GameRepository.Game source=game("谭氏垂容","2025-09-27","");source.black="吴宗翰";
        assertEquals("Dam Thi Thuy Dung · 谭氏垂容",PlayerNames.display(source.red));
        String exported=GameRepository.export(source,source.moves,false);
        assertTrue("Export must preserve original Red metadata",exported.contains("[Red \"谭氏垂容\"]"));
        assertTrue("Export must preserve original Black metadata",exported.contains("[Black \"吴宗翰\"]"));
        assertEquals("谭氏垂容",source.red);assertEquals("吴宗翰",source.black);
        launch();int[] count={0};
        main(()->((EditText)findType(root(),EditText.class)).setText("Wong Hok Him"));getInstrumentation().waitForIdleSync();
        main(()->{assertNotNull(findText(root(),"Wong Hok Him · 黄学谦"));count[0]=cards().getChildCount();assertTrue(count[0]>0);});
        main(()->((EditText)findType(root(),EditText.class)).setText("黄学谦"));getInstrumentation().waitForIdleSync();main(()->assertEquals(count[0],cards().getChildCount()));
        main(()->((EditText)findType(root(),EditText.class)).setText("Huang Xueqian"));getInstrumentation().waitForIdleSync();main(()->assertEquals(count[0],cards().getChildCount()));
        main(()->((EditText)findType(root(),EditText.class)).setText("Đàm Thị Thùy Dung"));getInstrumentation().waitForIdleSync();
        main(()->assertNotNull("Native accent spelling must find the readable player",findText(root(),"Dam Thi Thuy Dung · 谭氏垂容")));
    }
    private void applyColor(AlertDialog settings,String button,String value)throws Exception{openColor(settings,button);AlertDialog picker=dialog("colorDialog");main(()->{((EditText)findDescription(picker.getWindow().getDecorView(),"Hex color")).setText(value);picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});getInstrumentation().waitForIdleSync();}
    private void choosePreset(AlertDialog settings,String control,int position){
        main(()->((Spinner)findDescription(settings.getWindow().getDecorView(),control)).performClick());getInstrumentation().waitForIdleSync();
        AlertDialog preset=dialog("presetDialog");assertNotNull("Preset choices must open",preset);
        main(()->assertTrue(preset.getListView().performItemClick(null,position,preset.getListView().getAdapter().getItemId(position))));getInstrumentation().waitForIdleSync();
    }
    private void assertColors(Integer board,Integer red,Integer black){StudySettings current=StudySettings.load(getInstrumentation().getTargetContext());assertEquals("Board override",board,current.boardColor);assertEquals("Red background override",red,current.redBackground);assertEquals("Black background override",black,current.blackBackground);}
    private void openColor(AlertDialog settings,String text){main(()->{View view=findText(settings.getWindow().getDecorView(),text);assertNotNull(view);view.performClick();});getInstrumentation().waitForIdleSync();}
    private void launch()throws Exception{activity=getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));awaitLibrary();}
    private void awaitLibrary()throws Exception{long until=SystemClock.uptimeMillis()+15000;boolean[] ready={false};while(SystemClock.uptimeMillis()<until){main(()->ready[0]=findDescription(root(),"Game sort order")!=null);if(ready[0])return;Thread.sleep(50);}fail("Library did not load");}
    private AlertDialog dialog(String name){AlertDialog[] result={null};main(()->{try{result[0]=(AlertDialog)field(name).get(activity);}catch(Exception e){throw new RuntimeException(e);}});return result[0];}
    private static Field field(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private LinearLayout cards(){try{return (LinearLayout)field("cards").get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private String firstRed(){return ((TextView)((ViewGroup)((ViewGroup)cards().getChildAt(0)).getChildAt(0)).getChildAt(1)).getText().toString();}
    private static GameRepository.Game game(String red,String date,String collection){GameRepository.Game game=new GameRepository.Game();game.id="sort-"+red;game.red=red;game.black="Black player";game.date=date;game.collection=collection;game.event="Date sorting audit";game.round="";game.result="*";game.startFen="";game.sourceUrl="";return game;}
    private void main(Runnable work){getInstrumentation().runOnMainSync(work);}
    private View root(){return activity.getWindow().getDecorView();}
    private static View findText(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;return findChildren(view,child->findText(child,text));}
    private static View findDescription(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;return findChildren(view,child->findDescription(child,text));}
    private static View findType(View view,Class<?> type){if(type.isInstance(view))return view;return findChildren(view,child->findType(child,type));}
    private static Rect screenBounds(View view){assertNotNull(view);int[] location=new int[2];view.getLocationOnScreen(location);return new Rect(location[0],location[1],location[0]+view.getWidth(),location[1]+view.getHeight());}
    private static boolean containsTurnText(View view){if(view instanceof TextView&&((TextView)view).getText().toString().contains(" to move"))return true;if(view instanceof ViewGroup){ViewGroup children=(ViewGroup)view;for(int i=0;i<children.getChildCount();i++)if(containsTurnText(children.getChildAt(i)))return true;}return false;}
    private interface Search {View find(View child);}
    private static View findChildren(View view,Search search){if(view instanceof ViewGroup){ViewGroup children=(ViewGroup)view;for(int i=0;i<children.getChildCount();i++){View found=search.find(children.getChildAt(i));if(found!=null)return found;}}return null;}
    private static void restore(SharedPreferences preferences,Map<String,?> saved){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;edit.putStringSet(key,set);}}edit.commit();}
}
