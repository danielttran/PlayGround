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
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
                Spinner sort=(Spinner)findDescription(root(),"Game sort order");assertEquals(1,sort.getSelectedItemPosition());
            }catch(Exception e){throw new RuntimeException(e);}
        });
        getInstrumentation().waitForIdleSync();main(()->{assertEquals("Latest",firstRed());((Spinner)findDescription(root(),"Game sort order")).setSelection(2);});
        getInstrumentation().waitForIdleSync();main(()->assertEquals("Earliest",firstRed()));assertEquals("oldest",study.getString("librarySort",""));
        main(()->((EditText)findType(root(),EditText.class)).setText("Historical Audit Collection"));getInstrumentation().waitForIdleSync();
        main(()->{assertEquals(1,gameCount());assertEquals("Earliest",firstRed());((EditText)findType(root(),EditText.class)).setText("");});
        getInstrumentation().waitForIdleSync();
        main(()->((Spinner)findDescription(root(),"Game sort order")).setSelection(0));getInstrumentation().waitForIdleSync();main(()->assertEquals("Unknown",firstRed()));
        main(()->((Spinner)findDescription(root(),"Game sort order")).setSelection(2));getInstrumentation().waitForIdleSync();
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        main(activity::recreate);Activity recreated=monitor.waitForActivityWithTimeout(10000);getInstrumentation().removeMonitor(monitor);assertNotNull(recreated);activity=recreated;awaitLibrary();
        main(()->assertEquals("Sort selection must survive recreation",2,((Spinner)findDescription(root(),"Game sort order")).getSelectedItemPosition()));
    }
    public void testLibrarySearchKeepsFocusDuringRealCharacterTyping()throws Exception{
        launch();awaitLoadedLibrary();final EditText[] input={null};final Rect[] bounds={null};
        main(()->{
            assertEquals("The regression uses the complete real preload",398,gameCount());
            input[0]=(EditText)findType(root(),EditText.class);assertNotNull(input[0]);
            assertEquals("",input[0].getText().toString());
            input[0].requestRectangleOnScreen(new Rect(0,0,input[0].getWidth(),input[0].getHeight()),true);
        });
        getInstrumentation().waitForIdleSync();main(()->bounds[0]=screenBounds(input[0]));
        assertTrue("The search field must have a real visible touch target",bounds[0].width()>0&&bounds[0].height()>0);
        long down=SystemClock.uptimeMillis();sendPointer(down,MotionEvent.ACTION_DOWN,bounds[0].exactCenterX(),bounds[0].exactCenterY());
        sendPointer(down,MotionEvent.ACTION_UP,bounds[0].exactCenterX(),bounds[0].exactCenterY());awaitSearchLayout(input[0]);
        main(()->assertTrue("A real tap must focus the visible search field",input[0].hasFocus()));
        String query="Trenh";
        for(int index=0;index<query.length();index++){
            getInstrumentation().sendStringSync(query.substring(index,index+1));awaitSearchLayout(input[0]);final String expected=query.substring(0,index+1);
            main(()->{
                assertEquals("Each real key must remain after filtering and layout",expected,input[0].getText().toString());
                assertTrue("Filtering must not take focus away from the search field",input[0].hasFocus());
            });
        }
        main(()->assertEquals("All seven Trenh A Sang records are found",7,gameCount()));
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DEL);awaitSearchLayout(input[0]);
        main(()->{assertEquals("Tren",input[0].getText().toString());assertTrue(input[0].hasFocus());assertEquals(7,gameCount());});
        getInstrumentation().sendStringSync("h");awaitSearchLayout(input[0]);
        main(()->{assertEquals("Trenh",input[0].getText().toString());assertTrue(input[0].hasFocus());assertEquals(7,gameCount());});
        for(int index=0;index<query.length();index++){getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DEL);awaitSearchLayout(input[0]);}
        main(()->{assertEquals("",input[0].getText().toString());assertTrue("Clearing also retains input focus",input[0].hasFocus());assertEquals(398,gameCount());});
    }
    public void testContinuousLibraryIncludesEveryGameAndLazilyRecyclesRows() throws Exception {
        launch();main(()->{try{int source=0;for(Object value:(List<?>)field("games").get(activity)){GameRepository.Game game=(GameRepository.Game)value;if(!game.imported&&!game.engineGame)source++;}assertTrue(source>=337);assertEquals("Every built-in human game is in the Human adapter",source,gameCount());}catch(Exception e){throw new RuntimeException(e);}});
        List<GameRepository.Game> fixtures=new ArrayList<>();for(int i=0;i<85;i++)fixtures.add(game("Continuous fixture "+i,"2025-09-27","Continuous scroll audit"));
        replaceLibrary(fixtures);getInstrumentation().waitForIdleSync();
        main(()->{assertEquals(85,gameCount());assertEquals("Continuous fixture 0",firstRed());assertNull(findText(root(),"Previous 40 games"));assertNull(findText(root(),"Next 40 games"));assertTrue("Only on-screen rows are materialized",libraryList().getChildCount()<20);libraryList().setSelection(libraryList().getHeaderViewsCount()+84);});
        getInstrumentation().waitForIdleSync();main(()->{assertNotNull("Scrolling reaches a game beyond the old page limit",findText(root(),"Continuous fixture 84"));assertTrue(libraryList().getChildCount()<20);libraryList().setSelection(0);});
        getInstrumentation().waitForIdleSync();main(()->((EditText)findType(root(),EditText.class)).setText("Continuous fixture 82"));getInstrumentation().waitForIdleSync();
        main(()->{assertEquals("Search covers rows that were never bound",1,gameCount());assertEquals("Continuous fixture 82",firstRed());((EditText)findType(root(),EditText.class)).setText("");});
        getInstrumentation().waitForIdleSync();main(()->{assertEquals("Clearing search returns to the beginning","Continuous fixture 0",firstRed());assertEquals(85,gameCount());((Spinner)findDescription(root(),"Game sort order")).setSelection(2);});
        getInstrumentation().waitForIdleSync();main(()->assertEquals("Continuous fixture 0",firstRed()));
        List<GameRepository.Game> large=new ArrayList<>();for(int i=0;i<3000;i++)large.add(game("Large fixture "+i,"2025-09-27","Large imported-library audit"));
        long began=SystemClock.uptimeMillis();replaceLibrary(large);getInstrumentation().waitForIdleSync();
        assertTrue("A large library must bind promptly without inflating all its cards",SystemClock.uptimeMillis()-began<5000);
        main(()->{assertEquals(3000,gameCount());assertTrue("Large libraries still materialize only visible rows",libraryList().getChildCount()<20);libraryList().setSelection(libraryList().getHeaderViewsCount()+2999);});
        getInstrumentation().waitForIdleSync();main(()->assertNotNull("The last of 3,000 games remains scrollable",findText(root(),"Large fixture 2999")));
    }
    public void testDeepLibraryScrollSurvivesGameReturnSwipeAndRecreation() throws Exception {
        launch();
        final int[] scroll=new int[2];final String[] current=new String[2];
        main(()->{assertTrue("A deep library must extend beyond the old page boundary",gameCount()>=337);ListView list=libraryList();list.setSelectionFromTop(list.getHeaderViewsCount()+64,-Math.round(23*activity.getResources().getDisplayMetrics().density));});
        getInstrumentation().waitForIdleSync();
        main(()->{
            ListView list=libraryList();scroll[0]=list.getFirstVisiblePosition();scroll[1]=list.getChildAt(0).getTop();current[0]=firstRed();
            assertTrue(scroll[0]>40);assertTrue("Restore must preserve a partly scrolled row",scroll[1]<0);
            assertNull("The header is detached deep in the virtual list",findText(root(),"Xiangqi Study"));
            View card=list.getChildAt(1);assertTrue(card.getContentDescription().toString().startsWith("Red: "));assertTrue(card.performClick());
            try{current[1]=((GameRepository.Game)field("game").get(activity)).id;}catch(Exception e){throw new RuntimeException(e);}
        });
        getInstrumentation().waitForIdleSync();main(()->{assertNotNull(findType(root(),BoardView.class));assertTrue(findText(root(),"‹  Library").performClick());});
        awaitLoadedLibrary();assertLibraryScroll(scroll,current[0]);
        // A real pointer swipe starts on a deep, recycled card with no header.
        swipeLeftFromLibrary();
        main(()->{try{assertFalse(field("library").getBoolean(activity));assertEquals("Swipe returns to the exact current game",current[1],((GameRepository.Game)field("game").get(activity)).id);assertNotNull(findType(root(),BoardView.class));assertTrue(findText(root(),"‹  Library").performClick());}catch(Exception e){throw new RuntimeException(e);}});
        awaitLoadedLibrary();assertLibraryScroll(scroll,current[0]);
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        main(activity::recreate);Activity recreated=monitor.waitForActivityWithTimeout(10000);getInstrumentation().removeMonitor(monitor);assertNotNull(recreated);activity=recreated;
        awaitLoadedLibrary();assertLibraryScroll(scroll,current[0]);
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
    public void testLongPlayerNamesDoNotMoveBoardOrPositionLabelBetweenTurns() throws Exception {
        launch();GameRepository.Game game=game("Nguyễn Thành Bảo · Vietnamese master — international classical championship record","2025-09-27","");
        game.black="Andy Wo";game.moves.addAll(Arrays.asList("b2e2","b9c7","a0a1","a9a8","h2g2","h9g7"));
        main(()->{try{field("games").set(activity,Arrays.asList(game));Method open=MainActivity.class.getDeclaredMethod("openGame",GameRepository.Game.class,boolean.class);open.setAccessible(true);open.invoke(activity,game,false);}catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();
        main(()->{try{View label=(View)field("positionLabel").get(activity);label.requestRectangleOnScreen(new Rect(0,0,label.getWidth(),label.getHeight()),true);}catch(Exception error){throw new RuntimeException(error);}});getInstrumentation().waitForIdleSync();
        Rect[] bounds={null,null};main(()->{try{bounds[0]=screenBounds(findType(root(),BoardView.class));bounds[1]=screenBounds((View)field("positionLabel").get(activity));assertTrue(bounds[0].width()>0);assertTrue(bounds[1].height()>0);assertNull(findType(root(),SeekBar.class));}catch(Exception error){throw new RuntimeException(error);}});
        for(int ply:new int[]{0,1,2,3,6,5,4,1,0}) {
            main(()->{try{Method navigate=MainActivity.class.getDeclaredMethod("navigate",int.class);navigate.setAccessible(true);navigate.invoke(activity,ply);}catch(Exception e){throw new RuntimeException(e);}});getInstrumentation().waitForIdleSync();
            main(()->{
                assertEquals("Board bounds must remain fixed at half-move "+ply,bounds[0],screenBounds(findType(root(),BoardView.class)));
                try{assertEquals("Position label bounds must remain fixed at half-move "+ply,bounds[1],screenBounds((View)field("positionLabel").get(activity)));}catch(Exception error){throw new RuntimeException(error);}
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
        main(()->{try{View label=(View)field("positionLabel").get(activity);label.requestRectangleOnScreen(new Rect(0,0,label.getWidth(),label.getHeight()),true);}catch(Exception error){throw new RuntimeException(error);}});getInstrumentation().waitForIdleSync();
        Rect[] bounds={null,null};main(()->{try{bounds[0]=screenBounds(findType(root(),BoardView.class));bounds[1]=screenBounds((View)field("positionLabel").get(activity));}catch(Exception error){throw new RuntimeException(error);}});
        for(int ply:new int[]{0,1,1000,2000,999}) {
            main(()->{
                try {
                    Method navigate=MainActivity.class.getDeclaredMethod("navigate",int.class);navigate.setAccessible(true);navigate.invoke(activity,ply);
                    ViewGroup records=(ViewGroup)field("movesView").get(activity);int rows=0;
                    for(int i=0;i<records.getChildCount();i++)if(records.getChildAt(i) instanceof LinearLayout)rows++;
                    assertEquals("Move record renders a fixed40-row window",40,rows);assertTrue(records.getChildCount()<=41);
                    assertEquals("Full position range is retained",2001,((List<?>)field("positions").get(activity)).size());
                    assertEquals(ply,field("ply").getInt(activity));
                    assertEquals("Working history is never truncated",2000,((List<?>)field("workingMoves").get(activity)).size());
                    if(ply>0)assertNotNull("Selected move must remain in the current window",findDescription(root(),"After half-move "+ply+", "+game.moves.get(ply-1)));
                }catch(Exception e){throw new RuntimeException(e);}
            });getInstrumentation().waitForIdleSync();
            main(()->{try{assertEquals(bounds[0],screenBounds(findType(root(),BoardView.class)));assertEquals(bounds[1],screenBounds((View)field("positionLabel").get(activity)));}catch(Exception error){throw new RuntimeException(error);}});
        }
        UiTestActions.goToMove(getInstrumentation(),activity,2000);
        main(()->{try{assertEquals("Numeric navigation reaches the complete record's last half-move",2000,field("ply").getInt(activity));}catch(Exception error){throw new RuntimeException(error);}});
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
        main(()->{assertNotNull(findText(root(),"Wong Hok Him · 黄学谦"));count[0]=gameCount();assertTrue(count[0]>0);});
        main(()->((EditText)findType(root(),EditText.class)).setText("黄学谦"));getInstrumentation().waitForIdleSync();main(()->assertEquals(count[0],gameCount()));
        main(()->((EditText)findType(root(),EditText.class)).setText("Huang Xueqian"));getInstrumentation().waitForIdleSync();main(()->assertEquals(count[0],gameCount()));
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
    private void awaitSearchLayout(View input)throws Exception{CountDownLatch frame=new CountDownLatch(1);main(()->input.postOnAnimation(()->input.postOnAnimation(frame::countDown)));assertTrue("Search layout must settle",frame.await(5,TimeUnit.SECONDS));getInstrumentation().waitForIdleSync();}
    private void awaitLibrary()throws Exception{long until=SystemClock.uptimeMillis()+15000;boolean[] ready={false};while(SystemClock.uptimeMillis()<until){main(()->ready[0]=findDescription(root(),"Game sort order")!=null);if(ready[0])return;Thread.sleep(50);}fail("Library did not load");}
    private void awaitLoadedLibrary()throws Exception{long until=SystemClock.uptimeMillis()+15000;boolean[] ready={false};while(SystemClock.uptimeMillis()<until){getInstrumentation().waitForIdleSync();main(()->{try{ready[0]=field("loaded").getBoolean(activity)&&field("library").getBoolean(activity)&&libraryList()!=null&&libraryList().getChildCount()>0;}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;Thread.sleep(50);}fail("Scrolled library did not load");}
    private void assertLibraryScroll(int[] expected,String red){main(()->{assertEquals("First visible library position must survive",expected[0],libraryList().getFirstVisiblePosition());assertEquals("Partial-row pixel offset must survive",expected[1],libraryList().getChildAt(0).getTop());assertEquals("The same game remains at the top",red,firstRed());assertNull(findText(root(),"Xiangqi Study"));});}
    private void swipeLeftFromLibrary(){final int[] size=new int[2];main(()->{size[0]=root().getWidth();size[1]=root().getHeight();});long down=SystemClock.uptimeMillis();float y=size[1]*.65f;sendPointer(down,MotionEvent.ACTION_DOWN,size[0]*.85f,y);for(int step=1;step<=10;step++){SystemClock.sleep(20);sendPointer(down,MotionEvent.ACTION_MOVE,size[0]*(.85f-.7f*step/10f),y);}sendPointer(down,MotionEvent.ACTION_UP,size[0]*.15f,y);getInstrumentation().waitForIdleSync();}
    private void sendPointer(long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try{getInstrumentation().sendPointerSync(event);}finally{event.recycle();}}
    private AlertDialog dialog(String name){AlertDialog[] result={null};main(()->{try{result[0]=(AlertDialog)field(name).get(activity);}catch(Exception e){throw new RuntimeException(e);}});return result[0];}
    private static Field field(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private ListView libraryList(){try{return (ListView)field("libraryList").get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private int gameCount(){return libraryList().getAdapter().getCount()-libraryList().getHeaderViewsCount();}
    private String firstRed(){ListView list=libraryList();for(int i=0;i<list.getChildCount();i++){View card=list.getChildAt(i);if(card.getContentDescription()!=null&&card.getContentDescription().toString().startsWith("Red: "))return ((TextView)((ViewGroup)((ViewGroup)card).getChildAt(0)).getChildAt(1)).getText().toString();}fail("No visible game card");return "";}
    private void replaceLibrary(List<GameRepository.Game> games){main(()->{try{field("games").set(activity,games);Method reset=MainActivity.class.getDeclaredMethod("resetLibraryScroll");reset.setAccessible(true);reset.invoke(activity);Method render=MainActivity.class.getDeclaredMethod("renderCards");render.setAccessible(true);render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});}
    private static GameRepository.Game game(String red,String date,String collection){GameRepository.Game game=new GameRepository.Game();game.id="sort-"+red;game.red=red;game.black="Black player";game.date=date;game.collection=collection;game.event="Date sorting audit";game.round="";game.result="*";game.startFen="";game.sourceUrl="";return game;}
    private void main(Runnable work){UiTestActions.runOnMain(getInstrumentation(),work);}
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
