package org.xiangqi.study;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;
import org.xiangqi.study.core.GameDateSort;
import org.xiangqi.study.core.PlayerNames;
import org.xiangqi.study.core.AppearancePresets;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Dependency-free native Android library, replay board and local analysis workspace. */
public final class MainActivity extends Activity {
    private static final int BG=0xff1e2520, SURFACE=0xff2d372f, LIGHT=0xffe7e7d5, MUTED=0xffb8c8b8,
        ACCENT=0xffb3cfa6, INK=0xff20342c, LINE=0xff465647;
    private static final int IMPORT=101, EXPORT=102;
    private final Handler handler=new Handler(Looper.getMainLooper());
    // File operations outlive configuration changes and stay ordered: a recreated
    // Activity loads after an import already queued by its predecessor.
    private static final ExecutorService FILE_IO=Executors.newSingleThreadExecutor();
    private final ExecutorService io=FILE_IO, engine=Executors.newSingleThreadExecutor();
    private final ExecutorService libraryWorker=Executors.newSingleThreadExecutor(task->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run();},"xiangqi-library-search"));
    private Future<?> librarySearchFuture;
    private Runnable librarySearchDispatch;
    private int librarySearchGeneration,pendingGameDirection;
    private boolean librarySearchPending,libraryScrollResetPending;
    private List<GameRepository.Game> requestedLibrarySource,preparedLibrarySource,workerLibrarySource;
    private String requestedLibraryScope="",requestedLibrarySort="",requestedLibraryQuery="",preparedLibraryScope="",preparedLibrarySort="",preparedLibraryQuery="";
    private LibrarySearchIndex workerLibraryIndex;
    private LibrarySearchIndex.Result preparedLibrary;
    private Future<?> analysisFuture,playFuture;
    private int analysisGeneration,playGeneration;
    private SharedPreferences prefs;
    private GameRepository repository;
    private List<GameRepository.Game> games=new ArrayList<>();
    private LinearLayout root,content,analysisBox,replayBoardRow,replayContextRow,gameTitle;
    private ListView libraryList,movesView;
    private MoveRecordAdapter movesAdapter;
    private Spinner librarySortPicker,libraryImportPicker;
    private EditText librarySearch;
    private LibraryAdapter libraryAdapter;
    private List<GameRepository.Game> libraryMatches=new ArrayList<>();
    private TextView libraryEmpty;
    private boolean libraryHideResults;
    private TextView status,positionLabel,analysisText,variationLabel,eventLabel;
    private Button autoplayButton,exploreButton,previousGameButton,nextGameButton;
    private BoardView board;
    private EvaluationBarView evaluationBar;
    private SeekBar replayProgress;
    private ScrollView analysisScroll;
    private GameRepository.Game game;
    private final List<Position> positions=new ArrayList<>();
    private List<String> workingMoves=new ArrayList<>();
    private int ply,libraryFirstPosition,libraryTop;
    private boolean exploring,flipped,autoplay,destroyed,library=true,loaded;
    private boolean vsEngine,humanRed,engineThinking,analysisEnabled,foreground,seeking;
    private String search="",pendingExport,librarySort="newest",libraryTab="human",selectedImportId="";
    private String pendingImportName="Imported games";
    private boolean pendingImportEngine;
    private Bundle restoredImportDraft;
    private int importDialogRoute;
    private Uri importDialogUri;
    private EditText importNameInput,pasteInput;
    private RadioButton importEngineInput;
    private String pasteDraftToken="",pasteSessionId="";
    private Engine.Result lastResult;
    private AlertDialog settingsDialog,engineSettingsDialog,colorDialog,presetDialog,playSideDialog,goToDialog,importCollectionDialog,pasteDialog,replaySpeedDialog,gameInformationDialog,errorDialog;
    private float swipeStartX,swipeStartY;
    private boolean swipeTracking,swipeConsumed,swipeBlocked;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs=getSharedPreferences("study",MODE_PRIVATE); repository=new GameRepository(this);
        restoreLibrarySelection();
        if(state!=null){pendingImportName=state.getString("pendingImportName","Imported games");pendingImportEngine=state.getBoolean("pendingImportEngine",false);restoredImportDraft=state.getBundle("importDraft");}
        flipped=prefs.getBoolean("flipped",false);
        analysisEnabled=prefs.getBoolean("analysisEnabled",false);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setPadding(dp(12),dp(8),dp(12),dp(8));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                root.setPadding(dp(12)+bars.left,dp(8)+bars.top,dp(12)+bars.right,dp(8)+bars.bottom);
            }
            return insets;
        });
        setContentView(root);
        status=text("Loading the offline library…",15,MUTED); root.addView(status);
        final Bundle draftToRestore=restoredImportDraft;
        io.execute(()->{
            try {
                List<GameRepository.Game> loadedGames=repository.load();
                String draftText="",draftError="";
                if(draftToRestore!=null&&"paste".equals(draftToRestore.getString("kind")))try{draftText=ImportDraftStore.read(this,draftToRestore.getString("token",""));}catch(Exception error){draftError=getString(R.string.import_draft_restore_error);}
                final String savedDraftText=draftText,savedDraftError=draftError;
                runOnUiThread(()->{
                    if(destroyed)return; games=loadedGames; loaded=true;restoreLibrarySelection();validateImportSelection();
                    boolean importedReady=!prefs.getString("newImportId","").isEmpty();prefs.edit().remove("newImportId").apply();String id=prefs.getString("lastGame",""); GameRepository.Game resume=find(id);
                    if(!importedReady&&!prefs.getBoolean("library",true)&&resume!=null) openGame(resume,true); else showLibrary();
                    if(!handleIntent(getIntent())){
                        if(importedReady){restoredImportDraft=null;discardImportDraft(draftToRestore);}
                        else restoreImportDraft(draftToRestore,savedDraftText,savedDraftError);
                    }
                });
            } catch(Exception e) { runOnUiThread(()->{ if(!destroyed) { root.removeAllViews(); root.addView(text("The library could not be loaded.",20,LIGHT)); root.addView(text(message(e),14,MUTED)); root.addView(button("Retry",()->recreate(),true)); } }); }
        });
    }

    private void showLibrary() {
        cancelLibrarySearch();
        seeking=false;replayProgress=null;
        stopAutoplay(); cancelGameSearch(true); cancelAnalysis(); saveState(); library=true; prefs.edit().putBoolean("library",true).apply();
        root.removeAllViews(); content=column();
        libraryList=new ListView(this);libraryList.setContentDescription("Game library");libraryList.setBackgroundColor(BG);
        libraryList.setItemsCanFocus(true);
        libraryList.setDivider(new android.graphics.drawable.ColorDrawable(BG));libraryList.setDividerHeight(dp(9));libraryList.setHeaderDividersEnabled(false);
        libraryList.addHeaderView(content,null,true);root.addView(libraryList,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout heading=row();TextView title=text("Xiangqi Study",28,LIGHT);title.setTypeface(null,Typeface.BOLD);
        heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=button("Settings",this::settings,false);heading.addView(settings,new LinearLayout.LayoutParams(dp(94),dp(48)));content.addView(heading);
        TextView subtitle=text("A world of games. A board to understand them.",14,MUTED); pad(subtitle,0,4,0,18); content.addView(subtitle);
        GameRepository.Game current=currentGame();
        if(current!=null)content.addView(button("Return to current game · swipe left\nRed: "+PlayerNames.display(current.red)+"\nBlack: "+PlayerNames.display(current.black),this::returnToCurrentGame,true));
        LinearLayout tools=row(); tools.addView(weight(button("Import games",this::importMenu,true),1));
        tools.addView(weight(button("About & sources",this::about,false),1)); content.addView(tools);
        LinearLayout tabs=row();
        String[] tabIds={"human","engine","imports"},tabLabels={getString(R.string.library_human_games),getString(R.string.library_engine_games),getString(R.string.library_my_imports)};
        for(int i=0;i<tabIds.length;i++){
            final String tabId=tabIds[i];Button tab=button(tabLabels[i],()->selectLibrary(tabId,selectedImportId),tabId.equals(libraryTab));
            tab.setTextSize(12);tab.setSelected(tabId.equals(libraryTab));tab.setContentDescription(tabLabels[i]+" tab");tabs.addView(weight(tab,1));
        }
        content.addView(tabs);
        if("imports".equals(libraryTab))buildImportSelector();
        EditText field=new EditText(this);librarySearch=field; field.setSingleLine(true); field.setTextColor(LIGHT); field.setHintTextColor(MUTED);
        field.setTextSize(15); field.setHint("Search player, event, round or collection");field.setContentDescription("Library search"); field.setBackground(shape(SURFACE,LINE,12)); pad(field,14,6,14,6);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(52)); fp.setMargins(0,dp(14),0,dp(10)); content.addView(field,fp);
        LinearLayout sortRow=row();TextView sortLabel=text("Sort games",13,MUTED);sortRow.addView(sortLabel,new LinearLayout.LayoutParams(dp(88),-2));
        Spinner sort=new Spinner(this);librarySortPicker=sort;sort.setContentDescription("Game sort order");
        ArrayAdapter<String> sortAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Collection order","Newest first","Oldest first"}){
            @Override public View getView(int position,View convert,ViewGroup parent){TextView view=(TextView)super.getView(position,convert,parent);view.setTextColor(LIGHT);return view;}
        };
        sort.setAdapter(sortAdapter);sort.setSelection(index(librarySort,"collection","newest","oldest"));
        sortRow.addView(sort,new LinearLayout.LayoutParams(0,dp(48),1));content.addView(sortRow);
        sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(!library||sort!=librarySortPicker)return;String value=new String[]{"collection","newest","oldest"}[position];if(!value.equals(librarySort)){librarySort=value;resetLibraryScroll();saveLibraryScope();renderCards();}}public void onNothingSelected(AdapterView<?> parent){}});
        status=text("",12,MUTED); pad(status,2,0,2,10); content.addView(status);
        libraryEmpty=text("No matching games. Try a player’s surname, or import a game pack.",16,MUTED);pad(libraryEmpty,4,24,4,24);libraryEmpty.setVisibility(View.GONE);content.addView(libraryEmpty);
        libraryMatches=Collections.emptyList();libraryAdapter=new LibraryAdapter();libraryList.setAdapter(libraryAdapter);field.setText(search);field.setSelection(field.length());
        field.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int count,int after){} public void onTextChanged(CharSequence s,int st,int before,int count){if(!library||field!=librarySearch)return; search=s.toString();resetLibraryScroll();requestLibrarySearch(120); } public void afterTextChanged(Editable e){} });
        renderCards();
    }

    private void renderCards() {
        if(libraryList==null||libraryAdapter==null||!library)return;
        libraryHideResults=StudySettings.load(this).hideResults;
        // Rebind visible rows immediately for spoiler-setting changes; the
        // adapter handles all other rows lazily as the user scrolls to them.
        for(int i=0;i<libraryList.getChildCount();i++)if(libraryList.getChildAt(i) instanceof GameCard){GameCard card=(GameCard)libraryList.getChildAt(i);if(card.item!=null)card.bind(card.item);}
        if(preparedLibraryCurrent()){if(librarySearchPending)cancelLibrarySearch();rememberLibraryScrollIfPopulated();applyLibraryResult(preparedLibrary,libraryFirstPosition,libraryTop,librarySearchGeneration);}
        else requestLibrarySearch(0);
    }
    private boolean preparedLibraryCurrent(){return preparedLibrary!=null&&preparedLibrarySource==games&&preparedLibraryScope.equals(libraryScope())&&preparedLibrarySort.equals(librarySort)&&preparedLibraryQuery.equals(search);}
    private void rememberLibraryScrollIfPopulated(){if(libraryList!=null&&libraryList.getCount()>libraryList.getHeaderViewsCount())rememberLibraryScroll();}
    private void requestLibrarySearch(long delay){
        if(destroyed||!loaded||!foreground)return;
        final List<GameRepository.Game> source=games;final String scope=libraryScope(),sort=librarySort,query=search;
        if(librarySearchPending&&requestedLibrarySource==source&&requestedLibraryScope.equals(scope)&&requestedLibrarySort.equals(sort)&&requestedLibraryQuery.equals(query))return;
        cancelLibrarySearchWork();final int generation=++librarySearchGeneration;librarySearchPending=true;
        requestedLibrarySource=source;requestedLibraryScope=scope;requestedLibrarySort=sort;requestedLibraryQuery=query;
        final ListView list=library?libraryList:null;final LibraryAdapter adapter=library?libraryAdapter:null;
        if(preparedLibraryCurrent())rememberLibraryScrollIfPopulated();final int position=libraryFirstPosition,top=libraryTop;
        final List<GameRepository.Game> snapshot=new ArrayList<>(source);
        librarySearchDispatch=()->{
            librarySearchDispatch=null;if(destroyed||generation!=librarySearchGeneration)return;saveLibraryScope();
            librarySearchFuture=libraryWorker.submit(()->{
                try{
                    if(workerLibrarySource!=source){LibrarySearchIndex index=new LibrarySearchIndex(snapshot);if(Thread.currentThread().isInterrupted())return;workerLibraryIndex=index;workerLibrarySource=source;}
                    LibrarySearchIndex.Result result=workerLibraryIndex.search(scope,sort,query);
                    runOnUiThread(()->{
                        if(destroyed||generation!=librarySearchGeneration||source!=games||!scope.equals(libraryScope())||!sort.equals(librarySort)||!query.equals(search))return;
                        librarySearchFuture=null;librarySearchPending=false;preparedLibrary=result;preparedLibrarySource=source;preparedLibraryScope=scope;preparedLibrarySort=sort;preparedLibraryQuery=query;
                        if(library&&list==libraryList&&adapter==libraryAdapter)applyLibraryResult(result,position,top,generation);
                        if(!library){refreshGameNavigation();int direction=pendingGameDirection;pendingGameDirection=0;if(direction!=0)navigateGame(direction);}
                    });
                }catch(CancellationException ignored){/* Superseded requests never publish partial results. */}
            });
        };
        handler.postDelayed(librarySearchDispatch,delay);
    }
    private void applyLibraryResult(LibrarySearchIndex.Result result,int position,int top,int generation){
        if(!library||libraryList==null||libraryAdapter==null)return;libraryMatches=result.matches;int total=result.ordered.size();
        status.setText(getResources().getQuantityString(R.plurals.library_count,total,result.matches.size(),total));
        libraryEmpty.setText(total==0?("imports".equals(libraryTab)?R.string.library_empty_imports:"engine".equals(libraryTab)?R.string.library_empty_engines:R.string.library_empty_humans):R.string.library_no_matches);
        libraryEmpty.setVisibility(result.matches.isEmpty()?View.VISIBLE:View.GONE);libraryAdapter.notifyDataSetChanged();
        final ListView list=libraryList;list.post(()->{if(!destroyed&&library&&generation==librarySearchGeneration&&list==libraryList){list.setSelectionFromTop(Math.min(position,Math.max(0,list.getCount()-1)),top);list.postOnAnimation(()->{if(!destroyed&&library&&generation==librarySearchGeneration&&list==libraryList)libraryScrollResetPending=false;});}});
    }
    private void cancelLibrarySearchWork(){if(librarySearchDispatch!=null){handler.removeCallbacks(librarySearchDispatch);librarySearchDispatch=null;}if(librarySearchFuture!=null){librarySearchFuture.cancel(true);librarySearchFuture=null;}}
    private void cancelLibrarySearch(){librarySearchGeneration++;librarySearchPending=false;pendingGameDirection=0;cancelLibrarySearchWork();}
    private void resetLibraryScroll(){libraryFirstPosition=0;libraryTop=0;libraryScrollResetPending=true;if(libraryList!=null&&libraryList.getFirstVisiblePosition()>0)libraryList.setSelection(0);}
    private void rememberLibraryScroll(){if(library&&libraryList!=null&&!libraryScrollResetPending){libraryFirstPosition=libraryList.getFirstVisiblePosition();View first=libraryList.getChildAt(0);libraryTop=first==null?0:first.getTop();}}
    private String libraryScope(){return "imports".equals(libraryTab)?"import:"+selectedImportId:libraryTab;}
    private void saveLibraryScope(){
        if(prefs==null)return;String key="libraryScope:"+libraryScope()+":";
        SharedPreferences.Editor edit=prefs.edit().putString("libraryTab",libraryTab).putString("selectedImportId",selectedImportId)
            .putString(key+"search",search).putString(key+"sort",librarySort).putInt(key+"position",libraryFirstPosition).putInt(key+"top",libraryTop);
        if("human".equals(libraryTab))edit.putString("librarySort",librarySort);edit.apply();
    }
    private void restoreLibrarySelection(){
        libraryTab=prefs.getString("libraryTab","human");if(!Arrays.asList("human","engine","imports").contains(libraryTab))libraryTab="human";
        selectedImportId=prefs.getString("selectedImportId","");String completed=prefs.getString("newImportId","");
        if(!completed.isEmpty()){libraryTab="imports";selectedImportId=completed;}loadLibraryScope();
    }
    private void loadLibraryScope(){
        libraryScrollResetPending=false;
        String key="libraryScope:"+libraryScope()+":";search=prefs.getString(key+"search","");
        librarySort=GameDateSort.order(prefs.getString(key+"sort","human".equals(libraryTab)?prefs.getString("librarySort","newest"):"newest"));
        libraryFirstPosition=prefs.getInt(key+"position",0);libraryTop=prefs.getInt(key+"top",0);
    }
    private void validateImportSelection(){
        List<GameRepository.ImportedCollection> collections=repository.importedCollections();
        for(GameRepository.ImportedCollection collection:collections)if(collection.id.equals(selectedImportId))return;
        selectedImportId=collections.isEmpty()?"":collections.get(0).id;if("imports".equals(libraryTab))loadLibraryScope();
    }
    private void selectLibrary(String tab,String importId){
        if(tab.equals(libraryTab)&&(!"imports".equals(tab)||importId.equals(selectedImportId)))return;
        rememberLibraryScroll();saveLibraryScope();libraryTab=tab;selectedImportId=importId;validateImportSelection();loadLibraryScope();
        libraryList=null;libraryAdapter=null;showLibrary();saveLibraryScope();
    }
    private boolean inLibraryScope(GameRepository.Game item){
        return "imports".equals(libraryTab)?item.imported&&selectedImportId.equals(item.importCollectionId):!item.imported&&item.engineGame=="engine".equals(libraryTab);
    }
    private List<GameRepository.Game> scopedGames(){List<GameRepository.Game> entries=new ArrayList<>();for(GameRepository.Game item:games)if(inLibraryScope(item))entries.add(item);return entries;}
    private void buildImportSelector(){
        List<GameRepository.ImportedCollection> collections=repository.importedCollections();
        if(collections.isEmpty()){TextView empty=text("No imports yet · each import becomes its own collection",13,MUTED);pad(empty,4,8,4,4);content.addView(empty);return;}
        LinearLayout selector=row();TextView label=text("Collection",13,MUTED);selector.addView(label,new LinearLayout.LayoutParams(dp(82),-2));
        Spinner picker=new Spinner(this);libraryImportPicker=picker;picker.setContentDescription("Imported collection");List<String> labels=new ArrayList<>();int selected=0;
        for(int i=0;i<collections.size();i++){GameRepository.ImportedCollection item=collections.get(i);labels.add(item.name+" · "+(item.engineGame?"Engine":"Human")+" · "+item.gameCount);if(item.id.equals(selectedImportId))selected=i;}
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels){
            @Override public View getView(int position,View convert,ViewGroup parent){TextView view=(TextView)super.getView(position,convert,parent);view.setTextColor(LIGHT);view.setSingleLine(true);view.setEllipsize(TextUtils.TruncateAt.END);return view;}
        };picker.setAdapter(adapter);picker.setSelection(selected);selector.addView(picker,new LinearLayout.LayoutParams(0,dp(52),1));content.addView(selector);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(!library||picker!=libraryImportPicker||!"imports".equals(libraryTab))return;String value=collections.get(position).id;if(!value.equals(selectedImportId))selectLibrary("imports",value);}public void onNothingSelected(AdapterView<?> parent){}});
    }
    private final class LibraryAdapter extends BaseAdapter {
        @Override public int getCount(){return libraryMatches.size();}
        @Override public GameRepository.Game getItem(int position){return libraryMatches.get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View convert,ViewGroup parent){GameCard card=convert instanceof GameCard?(GameCard)convert:new GameCard();card.bind(getItem(position));return card;}
    }
    /** Recycled row: player TextView → side row → game card stays accessible. */
    private final class GameCard extends LinearLayout {
        private GameRepository.Game item;
        private final TextView redName,blackName,score,event,round,collection,clock,details,action;
        private final LinearLayout result;
        GameCard(){
            super(MainActivity.this);setOrientation(LinearLayout.VERTICAL);setBackground(shape(SURFACE,LINE,15));pad(this,14,14,14,12);setLayoutParams(new AbsListView.LayoutParams(-1,-2));
            LinearLayout redRow=playerRow("RED","",16),blackRow=playerRow("BLACK","",16);addView(redRow);addView(blackRow);redName=(TextView)redRow.getChildAt(1);blackName=(TextView)blackRow.getChildAt(1);
            result=row();result.addView(text("Result",12,MUTED),new LinearLayout.LayoutParams(0,-2,1));score=text("",14,MUTED);result.addView(score);addView(result);
            event=text("",14,MUTED);pad(event,0,10,0,0);addView(event);round=text("",13,MUTED);addView(round);collection=text("",12,ACCENT);addView(collection);clock=text("",12,ACCENT);addView(clock);
            details=text("",12,MUTED);pad(details,0,14,0,6);addView(details);action=text("",13,ACCENT);action.setGravity(Gravity.END);addView(action);
            setFocusable(true);setOnClickListener(v->{if(item!=null){rememberLibraryScroll();openGame(item,true);}});
        }
        void bind(GameRepository.Game game){
            item=game;redName.setText(PlayerNames.display(game.red));blackName.setText(PlayerNames.display(game.black));score.setText(libraryHideResults?"":displayResult(game.result));result.setVisibility(libraryHideResults?View.GONE:View.VISIBLE);
            event.setText(game.event);round.setText(game.round);round.setVisibility(game.round.isEmpty()?View.GONE:View.VISIBLE);collection.setText(game.collection);collection.setVisibility(game.collection.isEmpty()?View.GONE:View.VISIBLE);
            clock.setText(timeControl(game.timeControl));clock.setVisibility(game.timeControl.isEmpty()?View.GONE:View.VISIBLE);
            details.setText(getResources().getQuantityString(R.plurals.library_game_details,game.moves.size(),game.date.isEmpty()?"Undated":game.date,game.moves.size(),game.imported?" · Imported · "+(game.engineGame?"Engine":"Human"):game.engineGame?" · Engine game":""));
            boolean variation=prefs.getBoolean("exploring:"+game.id,false);int resume=variation?prefs.getInt("variationPly:"+game.id,0):Math.min(game.moves.size(),prefs.getInt("ply:"+game.id,0));
            action.setText(variation?getString(R.string.library_resume_variation,(resume+1)/2):resume>0?getString(R.string.library_resume_move,(resume+1)/2):getString(R.string.library_open_game));
            setContentDescription("Red: "+PlayerNames.display(game.red)+". Black: "+PlayerNames.display(game.black)+". "+game.event+", "+game.round+(libraryHideResults?"":", result "+displayResult(game.result))+". "+(game.engineGame?"Engine game. ":"Human game. ")+"Open game.");
        }
    }

    private void openGame(GameRepository.Game selected,boolean restore) {
        cancelLibrarySearch();
        stopAutoplay(); cancelGameSearch(true); cancelAnalysis(); saveState();
        if(!inLibraryScope(selected)){
            libraryTab=selected.imported?"imports":selected.engineGame?"engine":"human";
            if(selected.imported)selectedImportId=selected.importCollectionId;
            loadLibraryScope();saveLibraryScope();libraryList=null;libraryAdapter=null;
        }
        game=selected; library=false;
        workingMoves=new ArrayList<>(game.moves); exploring=false; ply=restore?prefs.getInt("ply:"+game.id,0):0;
        if(restore && prefs.getBoolean("exploring:"+game.id,false)) try {
            JSONArray array=new JSONArray(prefs.getString("variationMoves:"+game.id,"[]"));
            List<String> saved=new ArrayList<>(); for(int i=0;i<array.length();i++)saved.add(array.getString(i));
            workingMoves=saved; exploring=true; ply=prefs.getInt("variationPly:"+game.id,0);
        } catch(Exception ignored) { /* An interrupted preference write leaves the original record intact. */ }
        try { rebuildPositions(); }
        catch(RuntimeException e) { exploring=false; workingMoves=new ArrayList<>(game.moves); try{rebuildPositions();}catch(RuntimeException bad){showLibrary();error("This game contains an invalid move",message(bad));return;} }
        ply=Math.max(0,Math.min(ply,workingMoves.size())); buildReplay(); updateReplay(); saveState(); resumeAnalysis();
    }

    private void rebuildPositions() {
        positions.clear(); Position position=game.initial(); positions.add(position);
        for(String move:workingMoves) { position=position.play(move); positions.add(position); }
    }

    private void buildReplay() {
        seeking=false;replayProgress=null;
        root.removeAllViews();content=new ReplayLayout();root.addView(content,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout nav=row();Button back=button("‹  Library",this::showLibrary,false);back.setTextSize(12);back.setSingleLine(true);back.setMinWidth(0);back.setMinimumWidth(0);back.setPadding(dp(3),0,dp(3),0);back.setContentDescription("Library");nav.addView(back,new LinearLayout.LayoutParams(dp(66),dp(48)));
        gameTitle=column();pad(gameTitle,6,0,6,0);
        for(boolean redSide:new boolean[]{true,false}){TextView player=text((redSide?"Red · ":"Black · ")+PlayerNames.display(redSide?game.red:game.black),13,redSide?0xfff0a69c:LIGHT);player.setSingleLine(true);player.setEllipsize(TextUtils.TruncateAt.END);gameTitle.addView(player,new LinearLayout.LayoutParams(-1,-2));}
        gameTitle.setContentDescription("Red: "+PlayerNames.display(game.red)+". Black: "+PlayerNames.display(game.black)+". "+game.event+". "+game.round+". Open game information.");gameTitle.setFocusable(true);gameTitle.setOnClickListener(v->gameInformation());nav.addView(gameTitle,new LinearLayout.LayoutParams(0,-2,1));
        Button menu=button("⋮",()->{},false);menu.setTextSize(25);menu.setContentDescription("Game menu");menu.setOnClickListener(v->gameMenu(menu));nav.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(48)));int headerHeight=Math.max(dp(48),Math.round(((TextView)gameTitle.getChildAt(0)).getPaint().getFontSpacing())*2);content.addView(nav,new LinearLayout.LayoutParams(-1,headerHeight));
        positionLabel=null;eventLabel=null;
        board=new BoardView(this);StudySettings appearance=StudySettings.load(this);board.setAppearance(appearance.boardStyle,appearance.glyphStyle,appearance.discStyle);
        board.setCoordinates(appearance.coordinateStyle);
        board.setCustomColors(appearance.boardColor,appearance.redBackground,appearance.blackBackground);
        board.setFlipped(flipped); board.setMoveListener(this::makeMove);
        LinearLayout boardArea=row();replayBoardRow=boardArea;evaluationBar=new EvaluationBarView(this);evaluationBar.setFlipped(flipped);
        LinearLayout.LayoutParams barSize=new LinearLayout.LayoutParams(dp(16),-1);barSize.setMargins(0,0,dp(4),0);boardArea.addView(evaluationBar,barSize);
        boardArea.addView(board,new LinearLayout.LayoutParams(0,-2,1));content.addView(boardArea,new LinearLayout.LayoutParams(-1,-2));
        replayProgress=new SeekBar(this);replayProgress.setContentDescription("Game progression");replayProgress.setPadding(dp(4),0,dp(4),0);
        replayProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar slider,int progress,boolean fromUser){if(fromUser&&activeReplaySlider(slider))navigate(progress);}
            public void onStartTrackingTouch(SeekBar slider){if(!activeReplaySlider(slider))return;seeking=true;stopAutoplay();cancelGameSearch(true);cancelAnalysis();updateReplay();}
            public void onStopTrackingTouch(SeekBar slider){if(!activeReplaySlider(slider))return;boolean wasSeeking=seeking;seeking=false;if(wasSeeking)resumeAnalysis();}
        });
        content.addView(replayProgress,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout controls=row();previousGameButton=skipGameButton(true);previousGameButton.setContentDescription("Previous game");controls.addView(weight(previousGameButton,1));
        nextGameButton=skipGameButton(false);nextGameButton.setContentDescription("Next game");controls.addView(weight(nextGameButton,1));
        autoplayButton=button("▶",this::toggleAutoplay,true); autoplayButton.setContentDescription("Play replay"); controls.addView(weight(autoplayButton,1));
        Button previousMove=button("‹",()->navigate(ply-1),false);previousMove.setContentDescription("Previous move");controls.addView(weight(previousMove,1));
        Button nextMove=button("›",()->navigate(ply+1),false);nextMove.setContentDescription("Next move");controls.addView(weight(nextMove,1));content.addView(controls,new LinearLayout.LayoutParams(-1,dp(48)));
        for(int i=0;i<controls.getChildCount();i++){LinearLayout.LayoutParams spacing=(LinearLayout.LayoutParams)controls.getChildAt(i).getLayoutParams();spacing.topMargin=0;spacing.bottomMargin=0;}
        replayContextRow=row();variationLabel=text("",12,ACCENT);variationLabel.setMaxLines(2);variationLabel.setEllipsize(TextUtils.TruncateAt.END);replayContextRow.addView(variationLabel,new LinearLayout.LayoutParams(0,-2,1));
        exploreButton=button("Return to recorded game",()->{if(vsEngine)stopEngineGame();else toggleExplore();},false);exploreButton.setTextSize(11);exploreButton.setSingleLine(true);exploreButton.setPadding(dp(4),0,dp(4),0);exploreButton.setMinHeight(dp(32));exploreButton.setMinimumHeight(dp(32));replayContextRow.addView(exploreButton,new LinearLayout.LayoutParams(dp(146),dp(32)));content.addView(replayContextRow,new LinearLayout.LayoutParams(-1,dp(36)));
        analysisBox=column();analysisBox.setBackground(shape(SURFACE,0,12));pad(analysisBox,10,6,10,6);analysisText=text(analysisIntro(),12,MUTED);analysisScroll=new ScrollView(this);analysisScroll.setContentDescription("Analysis details");analysisScroll.addView(analysisText,new ScrollView.LayoutParams(-1,-2));
        analysisBox.addView(analysisScroll,new LinearLayout.LayoutParams(-1,0,1));content.addView(analysisBox,new LinearLayout.LayoutParams(-1,dp(92)));analysisBox.setVisibility(analysisEnabled&&!vsEngine?View.VISIBLE:View.GONE);
        TextView movesTitle=text("MOVE RECORD",12,MUTED);pad(movesTitle,3,4,3,4);content.addView(movesTitle);
        movesView=new ListView(this);movesView.setContentDescription("Move record");movesView.setItemsCanFocus(true);movesView.setDividerHeight(0);movesView.setBackgroundColor(BG);movesAdapter=new MoveRecordAdapter();movesView.setAdapter(movesAdapter);content.addView(movesView,new LinearLayout.LayoutParams(-1,0,1));
    }

    /** Fixed replay stack; only the move list scrolls. Short displays cap the board proportionally. */
    private final class ReplayLayout extends LinearLayout {
        ReplayLayout(){super(MainActivity.this);setOrientation(VERTICAL);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight(),height=MeasureSpec.getSize(heightSpec)-getPaddingTop()-getPaddingBottom();
            if(replayBoardRow!=null&&replayBoardRow.getParent()==this&&MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED){
                int fixed=0;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child==replayBoardRow||child==movesView||child.getVisibility()==GONE)continue;measureChildWithMargins(child,widthSpec,0,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),0);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)child.getLayoutParams();fixed+=child.getMeasuredHeight()+lp.topMargin+lp.bottomMargin;}
                int rail=evaluationBar.getVisibility()==VISIBLE?dp(20):0,boardHeight=Math.max(0,height-fixed-dp(56));
                LinearLayout.LayoutParams size=(LinearLayout.LayoutParams)replayBoardRow.getLayoutParams();size.width=Math.max(rail,Math.min(width,(int)(boardHeight/1.10f)+rail));size.gravity=Gravity.CENTER_HORIZONTAL;
            }
            super.onMeasure(widthSpec,heightSpec);
        }
    }

    private void gameInformation(){String info="Red: "+PlayerNames.display(game.red)+"\nBlack: "+PlayerNames.display(game.black)+"\n\n"+replayEvent()+(game.date.isEmpty()?"":"\n"+game.date);AlertDialog.Builder details=dialog("Game information").setMessage(info).setPositiveButton("Close",null);Uri source=Uri.parse(game.sourceUrl==null?"":game.sourceUrl);if("https".equalsIgnoreCase(source.getScheme())||"http".equalsIgnoreCase(source.getScheme()))details.setNeutralButton("View source",(d,w)->openSource(game.sourceUrl));gameInformationDialog=details.create();gameInformationDialog.setOnDismissListener(d->gameInformationDialog=null);gameInformationDialog.show();}
    private Button skipGameButton(boolean previous){Button control=button("",()->navigateGame(previous?-1:1),false);control.setForeground(new SkipGameIcon(previous));control.setForegroundGravity(Gravity.CENTER);return control;}
    private final class SkipGameIcon extends Drawable {
        private final boolean previous;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path triangle=new Path();private int alpha=255;
        SkipGameIcon(boolean previous){this.previous=previous;}
        @Override public void draw(Canvas canvas){boolean enabled=false;for(int state:getState())if(state==android.R.attr.state_enabled)enabled=true;paint.setColor(LIGHT);paint.setAlpha(enabled?alpha:Math.round(alpha*.4f));int saved=canvas.save();Rect bounds=getBounds();canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width()/24f,bounds.height()/24f);if(!previous){canvas.translate(24,0);canvas.scale(-1,1);}canvas.drawRect(4,4,6,20,paint);triangle.reset();triangle.moveTo(8,12);triangle.lineTo(20,4);triangle.lineTo(20,20);triangle.close();canvas.drawPath(triangle,paint);canvas.restoreToCount(saved);}
        @Override public boolean isStateful(){return true;}
        @Override public int getIntrinsicWidth(){return dp(23);}
        @Override public int getIntrinsicHeight(){return dp(23);}
        @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
        @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    private void updateReplay() {
        if(library||board==null)return;
        if(replayProgress!=null){replayProgress.setMax(workingMoves.size());replayProgress.setKeyProgressIncrement(1);replayProgress.setProgress(ply);replayProgress.setEnabled(!workingMoves.isEmpty());}
        Position p=positions.get(ply); board.setPosition(p,ply==0?"":workingMoves.get(ply-1),!vsEngine||p.redToMove==humanRed&&!engineThinking);
        if(!exploring&&!vsEngine)board.setContentDescription("Xiangqi board. "+(p.redToMove?"Red":"Black")+" to move. Recorded game. Tap a piece, then a legal destination to try a variation.");
        board.setAnalysisLines(analysisEnabled&&!vsEngine?lastResult:null,p.redToMove);
        evaluationBar.setEvaluation(analysisEnabled&&!vsEngine,lastResult==null?null:lastResult.score);
        board.setContentDescription(board.getContentDescription()+" Red: "+PlayerNames.display(game.red)+". Black: "+PlayerNames.display(game.black)+".");
        analysisBox.setVisibility(analysisEnabled&&!vsEngine?View.VISIBLE:View.GONE);
        exploreButton.setText(vsEngine?"Stop playing":"Return to recorded game");
        exploreButton.setVisibility(exploring||vsEngine?View.VISIBLE:View.GONE);
        replayContextRow.setVisibility(exploring||vsEngine?View.VISIBLE:View.GONE);variationLabel.setVisibility(exploring||vsEngine?View.VISIBLE:View.GONE);
        variationLabel.setText(vsEngine?"Playing as "+(humanRed?"Red":"Black")+" · "+(engineThinking?"engine thinking…":p.legalMoves().isEmpty()?"game over":"your move") :exploring?"Exploring a variation · Legal moves…":"");
        variationLabel.setOnClickListener(exploring?v->legalMoves():null);
        variationLabel.setContentDescription(exploring?"Explore mode. Open legal moves list.":variationLabel.getText());
        refreshGameNavigation();
        renderMoves(); saveState();
    }

    private void renderMoves() {
        if(movesView==null||movesAdapter==null)return;movesAdapter.notifyDataSetChanged();int current=ply==0?0:(ply-1+(positions.get(0).redToMove?0:1))/2;
        int visible=current-movesView.getFirstVisiblePosition();View selected=visible>=0&&visible<movesView.getChildCount()?movesView.getChildAt(visible):null;
        if(selected==null||selected.getTop()<0||selected.getBottom()>movesView.getHeight())movesView.setSelectionFromTop(current,0);
    }
    private final class MoveRecordAdapter extends BaseAdapter {
        @Override public int getCount(){return (workingMoves.size()+(positions.get(0).redToMove?0:1)+1)/2;}
        @Override public Object getItem(int position){return position;}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View convert,ViewGroup parent){MoveRecordRow row=convert instanceof MoveRecordRow?(MoveRecordRow)convert:new MoveRecordRow();row.bind(position);return row;}
    }
    private final class MoveRecordRow extends LinearLayout {
        private final TextView number,red,black;
        MoveRecordRow(){super(MainActivity.this);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setLayoutParams(new AbsListView.LayoutParams(-1,dp(40)));number=text("",12,MUTED);number.setGravity(Gravity.CENTER_VERTICAL);addView(number,new LinearLayout.LayoutParams(dp(30),-1));red=cell();black=cell();addView(red,new LinearLayout.LayoutParams(0,-1,1));addView(black,new LinearLayout.LayoutParams(0,-1,1));}
        private TextView cell(){TextView label=text("",14,LIGHT);label.setGravity(Gravity.CENTER_VERTICAL);label.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);pad(label,10,0,4,0);return label;}
        void bind(int row){number.setText(getString(R.string.move_record_number,row+1));bindCell(red,row*2-(positions.get(0).redToMove?0:1));bindCell(black,row*2+1-(positions.get(0).redToMove?0:1));}
        private void bindCell(TextView label,int index){boolean valid=index>=0&&index<workingMoves.size();label.setText(valid?pretty(workingMoves.get(index)):"");label.setFocusable(valid);label.setContentDescription(valid?"After half-move "+(index+1)+", "+workingMoves.get(index):"");label.setOnClickListener(valid?v->navigate(index+1):null);label.setBackground(valid&&ply==index+1?shape(SURFACE,0,8):null);}
    }

    private void navigate(int target) { stopAutoplay();cancelGameSearch(true); moveTo(target); }
    private boolean activeReplaySlider(SeekBar slider){return slider==replayProgress&&!destroyed&&!library&&foreground;}
    private void moveTo(int target) { cancelAnalysis(); ply=Math.max(0,Math.min(target,workingMoves.size())); updateReplay(); resumeAnalysis(); }
    private void toggleAutoplay() {
        boolean wasPlaying=vsEngine;cancelGameSearch(true);updateReplay();if(wasPlaying)resumeAnalysis();
        if(autoplay){stopAutoplay();return;} if(ply>=workingMoves.size())moveTo(0);
        autoplay=true; autoplayButton.setText("Ⅱ"); autoplayButton.setContentDescription("Pause replay"); handler.postDelayed(playTick,replayDelay());
    }
    private final Runnable playTick=new Runnable(){public void run(){if(!autoplay||library)return;if(ply>=workingMoves.size()){stopAutoplay();return;}moveTo(ply+1);if(ply>=workingMoves.size())stopAutoplay();else if(autoplay)handler.postDelayed(this,replayDelay());}};
    private void stopAutoplay() { autoplay=false; handler.removeCallbacks(playTick); if(autoplayButton!=null){autoplayButton.setText("▶");autoplayButton.setContentDescription("Play replay");} }
    private long replayDelay(){long value=prefs.getLong("replayDelayMillis",1000);return value>=500&&value<=60000?value:1000;}
    private String replayDelayLabel(){return getString(R.string.replay_delay_label,java.text.NumberFormat.getNumberInstance().format(replayDelay()/1000.0));}
    private void replaySpeed(Runnable changed){
        LinearLayout body=column();pad(body,20,8,20,8);body.addView(text("Seconds between replay moves (0.5–60). This controls Play replay; engine thinking time is set separately.",14,MUTED));
        EditText seconds=new EditText(this);seconds.setSingleLine(true);seconds.setTextColor(LIGHT);
        // Offer a numeric keyboard without letting DigitsKeyListener silently
        // strip a pasted locale separator before our numeric validation.
        seconds.setRawInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        seconds.setContentDescription("Seconds between replay moves");seconds.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});seconds.setText(String.valueOf(replayDelay()/1000.0));seconds.selectAll();body.addView(seconds);
        replaySpeedDialog=dialog("Replay speed").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        final AlertDialog shown=replaySpeedDialog;shown.setOnDismissListener(d->replaySpeedDialog=null);shown.show();shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                String raw=seconds.getText().toString().trim();StringBuilder normalized=new StringBuilder();
                for(int i=0;i<raw.length();i++){char c=raw.charAt(i);int digit=Character.digit(c,10);normalized.append(digit>=0?(char)('0'+digit):c==','||c=='\u066b'?'.':c);}
                double value=Double.parseDouble(normalized.toString());if(!Double.isFinite(value)||value<0.5||value>60)throw new NumberFormatException();
                prefs.edit().putLong("replayDelayMillis",Math.round(value*1000)).apply();
                if(autoplay){handler.removeCallbacks(playTick);handler.postDelayed(playTick,replayDelay());}
                if(changed!=null)changed.run();shown.dismiss();
            }catch(NumberFormatException invalid){seconds.setError("Enter seconds from 0.5 to 60");}
        });
    }

    private void toggleExplore() {
        stopAutoplay(); cancelGameSearch(true); cancelAnalysis();
        if(exploring) {
            int target=Math.min(ply,game.moves.size()); exploring=false; workingMoves=new ArrayList<>(game.moves); rebuildPositions(); ply=target;
            prefs.edit().remove("variationMoves:"+game.id).remove("variationPly:"+game.id).putBoolean("exploring:"+game.id,false).apply();
        } else exploring=true;
        updateReplay();resumeAnalysis();
    }
    private void makeMove(String move) {
        if(vsEngine&&(engineThinking||positions.get(ply).redToMove!=humanRed))return;
        stopAutoplay(); cancelAnalysis();
        try {
            Position next=positions.get(ply).play(move); exploring=true;
            workingMoves=new ArrayList<>(workingMoves.subList(0,ply)); workingMoves.add(move);
            while(positions.size()>ply+1)positions.remove(positions.size()-1); positions.add(next); ply++; updateReplay();if(vsEngine)requestEngineMove();else resumeAnalysis();
        } catch(RuntimeException e){error("Move could not be played",message(e));resumeAnalysis();}
    }
    private void legalMoves() {
        if(!exploring||vsEngine&&(engineThinking||positions.get(ply).redToMove!=humanRed))return; List<String> moves=positions.get(ply).legalMoves();
        if(moves.isEmpty()){toast("No legal moves in this position.");return;}
        String[] labels=new String[moves.size()];for(int i=0;i<labels.length;i++)labels[i]=pretty(moves.get(i));
        dialog("Choose a legal move").setItems(labels,(d,n)->makeMove(moves.get(n))).setNegativeButton("Cancel",null).show();
    }

    private List<GameRepository.Game> navigationGames() {
        if(!preparedLibraryCurrent()){requestLibrarySearch(0);return Collections.emptyList();}
        return gameIndex(preparedLibrary.matches)>=0?preparedLibrary.matches:preparedLibrary.ordered;
    }
    private void refreshGameNavigation(){if(library||previousGameButton==null||nextGameButton==null)return;List<GameRepository.Game> ordered=navigationGames();int current=gameIndex(ordered);previousGameButton.setEnabled(current>0);nextGameButton.setEnabled(current>=0&&current<ordered.size()-1);}
    private int gameIndex(List<GameRepository.Game> entries){for(int i=0;i<entries.size();i++)if(game!=null&&entries.get(i).id.equals(game.id))return i;return -1;}
    private void navigateGame(int direction){if(!preparedLibraryCurrent()){pendingGameDirection=direction;requestLibrarySearch(0);return;}List<GameRepository.Game> entries=navigationGames();int index=gameIndex(entries)+direction;if(index>=0&&index<entries.size())openGame(entries.get(index),true);}
    private void gameMenu(View anchor) {
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,1,0,"Flip board");menu.getMenu().add(0,2,1,analysisEnabled?getString(R.string.stop_analysis):getString(R.string.analyze)).setCheckable(true).setChecked(analysisEnabled);
        menu.getMenu().add(0,3,2,"Play against engine");menu.getMenu().add(1,6,3,"Go to move…");menu.getMenu().add(1,4,4,"Export game");menu.getMenu().add(1,5,5,"Settings");
        menu.setOnMenuItemClickListener(item->{
            switch(item.getItemId()){
                case 1:flipped=!flipped;board.setFlipped(flipped);evaluationBar.setFlipped(flipped);saveState();break;
                case 2:if(analysisEnabled)stopSearch();else analyze();break;
                case 3:chooseEngineSide();break;
                case 4:exportGame();break;
                case 5:settings();break;
                case 6:goToMove();break;
                default:return false;
            }return true;
        });menu.show();
    }
    private void goToMove(){
        stopAutoplay();
        LinearLayout body=column();pad(body,20,8,20,8);body.addView(text(getString(R.string.position_help,workingMoves.size()),14,LIGHT));
        EditText number=new EditText(this);number.setSingleLine(true);number.setTextColor(LIGHT);number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        number.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});number.setContentDescription("Half-move number");
        number.setHint(getString(R.string.position_hint,workingMoves.size()));number.setText(String.valueOf(ply));number.selectAll();body.addView(number);
        goToDialog=dialog("Go to position").setView(body).setPositiveButton("Go",null).setNegativeButton("Cancel",null).create();
        goToDialog.setOnDismissListener(d->goToDialog=null);goToDialog.show();
        goToDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{int target=Integer.parseInt(number.getText().toString().trim());if(target<0||target>workingMoves.size())throw new NumberFormatException();navigate(target);goToDialog.dismiss();}
            catch(NumberFormatException invalid){number.setError(getString(R.string.position_invalid,workingMoves.size()));}
        });
    }
    private void chooseEngineSide(){
        playSideDialog=dialog("Play against engine").setMessage("Start from the current position. Choose your side.\n\nThe computer uses the time-per-position setting, or 3 seconds per move when analysis is untimed. Your recorded game is preserved.")
            .setPositiveButton("Play as Red",(d,w)->startEngineGame(true)).setNegativeButton("Play as Black",(d,w)->startEngineGame(false)).setNeutralButton("Cancel",null).create();
        playSideDialog.setOnDismissListener(d->playSideDialog=null);playSideDialog.show();
    }
    private void startEngineGame(boolean red){
        stopAutoplay();cancelGameSearch(true);cancelAnalysis();
        if(positions.get(ply).legalMoves().isEmpty()){updateReplay();toast("This position has no legal moves.");resumeAnalysis();return;}
        humanRed=red;vsEngine=true;exploring=true;
        workingMoves=new ArrayList<>(workingMoves.subList(0,ply));while(positions.size()>ply+1)positions.remove(positions.size()-1);
        analysisText.setText(getString(R.string.engine_play_intro,PikafishEngine.available(this)?"Pikafish":"Built-in engine",red?"Red":"Black"));
        updateReplay();requestEngineMove();
    }
    private void stopEngineGame(){cancelGameSearch(true);updateReplay();analysisText.setText(analysisIntro());resumeAnalysis();}
    private void cancelGameSearch(boolean finish){
        playGeneration++;if(playFuture!=null){playFuture.cancel(true);playFuture=null;}engineThinking=false;if(finish)vsEngine=false;
    }
    private void requestEngineMove(){
        if(!vsEngine||library)return;
        final Position snapshot=positions.get(ply).copy();
        if(snapshot.legalMoves().isEmpty()){
            engineThinking=false;updateReplay();analysisText.setText(getString(R.string.engine_play_terminal,snapshot.redToMove?"Red":"Black",snapshot.redToMove?"Black":"Red"));return;
        }
        if(snapshot.redToMove==humanRed){engineThinking=false;updateReplay();return;}
        cancelGameSearch(false);engineThinking=true;updateReplay();final int generation=playGeneration,rootPly=ply;
        final List<String> history=new ArrayList<>(workingMoves);final String startFen=game.startFen;
        final boolean nativeEngine=PikafishEngine.available(this);PikafishEngine.Options saved=EngineSettings.load(this);
        analysisText.setText(getString(R.string.engine_play_thinking,nativeEngine?"Pikafish":"Built-in engine",humanRed?"Red":"Black"));
        final PikafishEngine.Options options=new PikafishEngine.Options(saved.maxDepth,saved.millis>0?saved.millis:3000,saved.threads,saved.hashMb);
        playFuture=engine.submit(()->{
            try{
                Engine.Result result=nativeEngine?PikafishEngine.analyze(this,snapshot,startFen,history,options,null):Engine.analyze(snapshot,Math.min(64,options.maxDepth),options.millis,null);
                if(!snapshot.legalMoves().contains(result.bestMove))throw new IllegalStateException("Engine returned no legal reply");
                runOnUiThread(()->{
                    if(destroyed||library||!vsEngine||generation!=playGeneration||ply!=rootPly)return;
                    playFuture=null;engineThinking=false;Position next=positions.get(ply).play(result.bestMove);workingMoves.add(result.bestMove);positions.add(next);ply++;
                    updateReplay();analysisText.setText(getString(R.string.engine_play_reply,nativeEngine?"Pikafish":"Built-in engine",pretty(result.bestMove),next.legalMoves().isEmpty()?getString(R.string.engine_play_terminal,next.redToMove?"Red":"Black",next.redToMove?"Black":"Red"):getString(R.string.engine_play_your_move)));
                });
            }catch(Exception failure){runOnUiThread(()->{if(!destroyed&&!library&&vsEngine&&generation==playGeneration){playFuture=null;engineThinking=false;vsEngine=false;updateReplay();error("Engine could not reply",getString(R.string.engine_play_error,message(failure)));resumeAnalysis();}});}
        });
    }

    private void analyze() {
        analysisEnabled=true;prefs.edit().putBoolean("analysisEnabled",true).apply();
        stopAutoplay();cancelGameSearch(true);cancelAnalysis();updateReplay();resumeAnalysis();
    }
    /** The user's toggle survives a position's time/depth limit and temporary pauses. */
    private void resumeAnalysis() {
        if(!analysisEnabled||!foreground||destroyed||library||vsEngine||seeking||analysisFuture!=null||positions.isEmpty()||board==null)return;
        final int generation=++analysisGeneration;
        final Position snapshot=positions.get(ply).copy();
        final List<String> history=new ArrayList<>(workingMoves.subList(0,ply));
        final String startFen=game.startFen;
        final boolean nativeEngine=PikafishEngine.available(this);
        final String engineName=nativeEngine?"Pikafish":"Built-in analysis";
        final PikafishEngine.Options options=EngineSettings.load(this);
        analysisBox.setVisibility(View.VISIBLE);analysisText.setText(getString(R.string.analysis_searching,engineName));
        final Engine.ProgressListener progress=result->runOnUiThread(()->{
            if(!destroyed&&generation==analysisGeneration&&!library)renderAnalysis(result,engineName,nativeEngine,true);
        });
        analysisFuture=engine.submit(()->{
            try {
                Engine.Result result=nativeEngine?PikafishEngine.analyze(this,snapshot,startFen,history,options,progress)
                    :Engine.analyze(snapshot,options.maxDepth,options.millis,options.lines,progress);
                runOnUiThread(()->{
                    if(destroyed||generation!=analysisGeneration||library)return;
                    analysisFuture=null;renderAnalysis(result,engineName,nativeEngine,false);
                });
            } catch(Exception e){runOnUiThread(()->{if(!destroyed&&generation==analysisGeneration&&!library){analysisFuture=null;analysisText.setText(getString(R.string.analysis_error,engineName,message(e)));}});}
        });
    }
    private void renderAnalysis(Engine.Result result,String engineName,boolean nativeEngine,boolean searching) {
        lastResult=result;
        Position current=positions.get(ply);List<String> legal=current.legalMoves();
        evaluationBar.setEvaluation(analysisEnabled&&!vsEngine,result.score);
        if(legal.isEmpty()){
            analysisText.setText(getString(R.string.analysis_terminal,engineName,current.redToMove?"Red":"Black",current.redToMove?"Black":"Red",
                current.isInCheck(current.redToMove)?"Checkmate.":"Stalemate is a loss in Xiangqi."));
            board.setHint("");while(analysisBox.getChildCount()>1)analysisBox.removeViewAt(1);return;
        }
        StringBuilder pv=new StringBuilder();
        if(result.lines.isEmpty()){for(int i=0;i<Math.min(result.pv.size(),8);i++){if(i>0)pv.append("  ");pv.append(pretty(result.pv.get(i)));}}
        else for(Engine.Line line:result.lines){
            if(pv.length()>0)pv.append('\n');pv.append(getString(R.string.analysis_line,line.rank,analysisScore(line.score)));
            for(int i=0;i<Math.min(line.pv.size(),8);i++)pv.append(i==0?" ":"  ").append(pretty(line.pv.get(i)));
        }
        analysisText.setText(getString(R.string.analysis_result,engineName,analysisScore(result.score),result.depth,java.text.NumberFormat.getIntegerInstance().format(result.nodes),pv.toString()));
        board.setAnalysisLines(analysisEnabled&&!vsEngine?result:null,current.redToMove);
        while(analysisBox.getChildCount()>1)analysisBox.removeViewAt(1);
        if(result.bestMove!=null&&!result.bestMove.isEmpty()&&legal.contains(result.bestMove)){
            Button explore=button("Explore suggested move",()->makeMove(result.bestMove),false);explore.setTextSize(12);explore.setPadding(dp(4),0,dp(4),0);explore.setMinHeight(dp(32));explore.setMinimumHeight(dp(32));analysisBox.addView(explore,new LinearLayout.LayoutParams(-1,dp(32)));
        }
    }
    private String analysisScore(int score){return Math.abs(score)>=29901?getString(R.string.analysis_winning,score>0?"Red":"Black"):String.format(Locale.getDefault(),"%+.2f",score/100.0);}
    private void cancelAnalysis() {
        analysisGeneration++; if(analysisFuture!=null){analysisFuture.cancel(true);analysisFuture=null;}
        lastResult=null;
        if(board!=null)board.setHint("");
        if(evaluationBar!=null)evaluationBar.setEvaluation(analysisEnabled&&!vsEngine,null);
        if(analysisText!=null)analysisText.setText(analysisIntro());
        if(analysisScroll!=null)analysisScroll.scrollTo(0,0);
        if(analysisBox!=null)analysisBox.setVisibility(analysisEnabled&&!vsEngine&&!library?View.VISIBLE:View.GONE);
        if(analysisBox!=null)while(analysisBox.getChildCount()>1)analysisBox.removeViewAt(1);
    }

    /** Only an explicit toggle disables analysis; navigation cancels an old position's work. */
    private void stopSearch() {
        analysisEnabled=false;prefs.edit().putBoolean("analysisEnabled",false).apply();
        analysisGeneration++;if(analysisFuture!=null){analysisFuture.cancel(true);analysisFuture=null;}
        if(board!=null)board.setHint("");if(evaluationBar!=null)evaluationBar.setEvaluation(false,null);
        if(analysisBox!=null)analysisBox.setVisibility(View.GONE);
        if(analysisText!=null)analysisText.setText(lastResult==null?analysisIntro():getString(R.string.analysis_off_estimate,analysisText.getText()));
    }

    private String replayEvent() {
        return (game.imported?"My imports · ":"")+(game.engineGame?"Engine game":"Human game")+"\n"+game.event+" · "+game.round+(StudySettings.load(this).hideResults?"":" · "+displayResult(game.result))
            +(game.timeControl.isEmpty()?"":"\n"+timeControl(game.timeControl));
    }
    private void applyAppearance() {
        StudySettings s=StudySettings.load(this);
        if(board!=null){board.setAppearance(s.boardStyle,s.glyphStyle,s.discStyle);board.setCoordinates(s.coordinateStyle);board.setCustomColors(s.boardColor,s.redBackground,s.blackBackground);}
        if(library)renderCards();else if(eventLabel!=null)eventLabel.setText(replayEvent());
    }
    private void settings() {
        StudySettings saved=StudySettings.load(this);
        LinearLayout body=column();pad(body,18,8,18,12);
        CheckBox hide=new CheckBox(this);hide.setText(R.string.hide_game_results);hide.setTextColor(LIGHT);hide.setChecked(saved.hideResults);
        hide.setContentDescription("Hide game results, including accessibility announcements");body.addView(hide);
        TextView spoilerNote=text("Only recorded game results are hidden. Engine evaluations stay visible; exported PGN preserves the original result.",12,MUTED);pad(spoilerNote,0,0,0,10);body.addView(spoilerNote);
        Button speed=button(getString(R.string.replay_speed_label,replayDelayLabel()),()->{},false);speed.setContentDescription("Replay speed");speed.setOnClickListener(v->replaySpeed(()->speed.setText(getString(R.string.replay_speed_label,replayDelayLabel()))));body.addView(speed);
        String[] boardIds=AppearancePresets.boardIds(),discIds=AppearancePresets.discIds();
        PresetSpinner boards=(PresetSpinner)settingChoice(body,"Board style",AppearancePresets.boardLabels(),index(saved.boardStyle,boardIds));
        Spinner glyphs=settingChoice(body,"Chinese piece characters",new String[]{"Traditional Chinese","Simplified Chinese"},index(saved.glyphStyle,"traditional","simplified"));
        PresetSpinner discs=(PresetSpinner)settingChoice(body,"Piece finish",AppearancePresets.discLabels(),index(saved.discStyle,discIds));
        Spinner coordinates=settingChoice(body,"Board coordinates",new String[]{"Off","ICCS · a–i / 0–9","Numeric · 0–8 / 0–9"},index(saved.coordinateStyle,"none","iccs","numeric"));
        TextView previewLabel=text("Live board preview",13,ACCENT);pad(previewLabel,0,12,0,5);body.addView(previewLabel);
        BoardView preview=new BoardView(this);preview.setPosition(library||positions.isEmpty()?Position.initial():positions.get(ply).copy(),"",false);
        preview.setAppearance(saved.boardStyle,saved.glyphStyle,saved.discStyle);preview.setCoordinates(saved.coordinateStyle);preview.setCustomColors(saved.boardColor,saved.redBackground,saved.blackBackground);body.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        Runnable refreshColors=()->{StudySettings s=StudySettings.load(this);preview.setCustomColors(s.boardColor,s.redBackground,s.blackBackground);if(!library)applyAppearance();};
        LinearLayout colors=column();body.addView(colors,body.indexOfChild(previewLabel));
        colors.addView(button("Board color",()->customColor("Board color","boardColor",preview.getBoardColor(),refreshColors),false));
        colors.addView(button("Red piece background",()->customColor("Red piece background","redBackground",preview.getPieceBackground(true),refreshColors),false));
        colors.addView(button("Black piece background",()->customColor("Black piece background","blackBackground",preview.getPieceBackground(false),refreshColors),false));
        colors.addView(button("Reset custom colors",()->{StudySettings.resetCustomColors(this);refreshColors.run();},false));
        Runnable changed=()->{
            boolean wasHidden=StudySettings.load(this).hideResults;
            StudySettings.save(this,boardIds[boards.getSelectedItemPosition()],
                new String[]{"traditional","simplified"}[glyphs.getSelectedItemPosition()],
                discIds[discs.getSelectedItemPosition()],new String[]{"none","iccs","numeric"}[coordinates.getSelectedItemPosition()],hide.isChecked());
            StudySettings s=StudySettings.load(this);preview.setAppearance(s.boardStyle,s.glyphStyle,s.discStyle);preview.setCoordinates(s.coordinateStyle);preview.setCustomColors(s.boardColor,s.redBackground,s.blackBackground);
            if(!library||wasHidden!=s.hideResults)applyAppearance();
        };
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){changed.run();}public void onNothingSelected(AdapterView<?> parent){}};
        boards.onPresetSelected(position->{StudySettings.selectBoardPreset(this,boardIds[position]);changed.run();});
        discs.onPresetSelected(position->{StudySettings.selectDiscPreset(this,discIds[position]);changed.run();});
        glyphs.setOnItemSelectedListener(listener);coordinates.setOnItemSelectedListener(listener);hide.setOnCheckedChangeListener((button,checked)->changed.run());
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        settingsDialog=dialog("Study settings").setView(scroll).setPositiveButton("Done",null)
            .setNeutralButton("Engine settings",(d,w)->{
                engineSettingsDialog=EngineSettings.show(this,()->{
                cancelAnalysis();resumeAnalysis();
                });engineSettingsDialog.setOnDismissListener(closed->engineSettingsDialog=null);
            }).create();
        settingsDialog.setOnDismissListener(d->{if(presetDialog!=null)presetDialog.dismiss();settingsDialog=null;});settingsDialog.show();
    }
    private void customColor(String title,String surface,int initial,Runnable changed) {
        colorDialog=ColorPickerDialog.show(this,title,initial,color->{StudySettings.setCustomColor(this,surface,color);changed.run();});
        colorDialog.setOnDismissListener(dialog->colorDialog=null);
    }
    private Spinner settingChoice(LinearLayout body,String label,String[] choices,int selected) {
        TextView title=text(label,13,ACCENT);pad(title,0,8,0,0);body.addView(title);
        Spinner spinner=("Board style".equals(label)||"Piece finish".equals(label))?new PresetSpinner(label,choices):new Spinner(this);spinner.setContentDescription(label);
        spinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,choices){
            @Override public View getView(int position,View convert,ViewGroup parent){TextView view=(TextView)super.getView(position,convert,parent);view.setTextColor(LIGHT);return view;}
        });
        spinner.setSelection(selected);body.addView(spinner,new LinearLayout.LayoutParams(-1,dp(48)));return spinner;
    }
    /** A real user choice must fire even when a custom color covers that same preset. */
    private final class PresetSpinner extends Spinner {
        private final String title;private final String[] labels;
        private int observed;private java.util.function.IntConsumer chosen;
        PresetSpinner(String title,String[] labels){super(MainActivity.this,Spinner.MODE_DIALOG);this.title=title;this.labels=labels;}
        void onPresetSelected(java.util.function.IntConsumer listener){
            chosen=listener;observed=getSelectedItemPosition();
            setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                    if(position!=observed){observed=position;chosen.accept(position);}
                }
                public void onNothingSelected(AdapterView<?> parent){}
            });
        }
        @android.annotation.SuppressLint("ClickableViewAccessibility") // Spinner.super would open a second native selector; this override handles accessibility clicks itself.
        @Override public boolean performClick(){
            if(!isEnabled())return false;
            playSoundEffect(SoundEffectConstants.CLICK);sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
            if(presetDialog!=null)presetDialog.dismiss();
            presetDialog=dialog(title).setSingleChoiceItems(labels,getSelectedItemPosition(),(selection,position)->{
                // Record before setSelection's deferred callback to avoid applying twice.
                observed=position;setSelection(position);if(chosen!=null)chosen.accept(position);selection.dismiss();
            }).setNegativeButton("Cancel",null).create();
            presetDialog.setOnDismissListener(closed->presetDialog=null);presetDialog.show();return true;
        }
    }
    private static int index(String selected,String... choices){for(int i=0;i<choices.length;i++)if(choices[i].equals(selected))return i;return 0;}

    private GameRepository.Game currentGame() {
        if(game!=null){GameRepository.Game current=find(game.id);if(current!=null)return current;}
        return prefs==null?null:find(prefs.getString("lastGame",""));
    }
    private void returnToCurrentGame() {GameRepository.Game current=currentGame();if(current!=null)openGame(current,true);}
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(!library&&board!=null){Rect bounds=new Rect();if(board.getGlobalVisibleRect(bounds)&&bounds.contains((int)event.getRawX(),(int)event.getRawY()))stopAutoplay();}
            swipeStartX=event.getX();swipeStartY=event.getY();swipeConsumed=false;
            swipeBlocked=!loaded||horizontalControlAt(root,event.getRawX(),event.getRawY());swipeTracking=!swipeBlocked;
        } else if(event.getPointerCount()>1||action==MotionEvent.ACTION_POINTER_DOWN){
            if(!swipeConsumed)cancelChildTouch(event);
            swipeBlocked=true;swipeTracking=false;swipeConsumed=true;
        }
        if(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){
            float dx=event.getX()-swipeStartX,dy=event.getY()-swipeStartY;
            boolean direction=library?dx<0&&currentGame()!=null:dx>0;
            boolean qualifies=swipeTracking&&!swipeBlocked&&Math.abs(dx)>dp(80)&&Math.abs(dx)>Math.abs(dy)*1.6f&&direction;
            if(qualifies&&!swipeConsumed){cancelChildTouch(event);swipeConsumed=true;}
            if(action==MotionEvent.ACTION_UP){
                boolean consumed=swipeConsumed;swipeTracking=false;swipeConsumed=false;
                if(qualifies){if(library)returnToCurrentGame();else showLibrary();}
                if(consumed)return true;
            }else if(swipeConsumed)return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){boolean consumed=swipeConsumed;swipeTracking=false;swipeConsumed=false;if(consumed)return true;}
        if(swipeConsumed)return true;
        return super.dispatchTouchEvent(event);
    }
    private void cancelChildTouch(MotionEvent event){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();}
    private static boolean horizontalControlAt(View view,float x,float y){
        if(view==null)return false;Rect bounds=new Rect();if(!view.getGlobalVisibleRect(bounds)||!bounds.contains((int)x,(int)y))return false;
        if(view instanceof SeekBar||view instanceof EditText||view instanceof Spinner)return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=group.getChildCount()-1;i>=0;i--)if(horizontalControlAt(group.getChildAt(i),x,y))return true;}
        return false;
    }

    private void importMenu() { dialog("Import games").setItems(new String[]{"Choose a PGN or JSON file","Paste ICCS PGN / JSON","Import format help"},(d,n)->{if(n<2)importCollection(n,null);else importHelp();}).setNegativeButton("Cancel",null).show(); }
    private void importCollection(int route,Uri uri){
        importDialogRoute=route;importDialogUri=uri;
        LinearLayout body=column();pad(body,20,6,20,8);
        body.addView(text("Each import is saved as a separate collection in My imports.",14,MUTED));
        EditText name=new EditText(this);name.setSingleLine(true);name.setTextColor(LIGHT);name.setHintTextColor(MUTED);name.setHint("Collection name");name.setContentDescription("Import collection name");
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});body.addView(name,new LinearLayout.LayoutParams(-1,dp(56)));
        RadioGroup type=new RadioGroup(this);type.setOrientation(RadioGroup.HORIZONTAL);type.setContentDescription("Import game type");
        RadioButton human=new RadioButton(this);human.setId(View.generateViewId());human.setText(R.string.library_human_games);human.setTextColor(LIGHT);human.setContentDescription("Import human games");
        RadioButton engines=new RadioButton(this);engines.setId(View.generateViewId());engines.setText(R.string.library_engine_games);engines.setTextColor(LIGHT);engines.setContentDescription("Import engine games");
        importNameInput=name;importEngineInput=engines;
        type.addView(human,new RadioGroup.LayoutParams(0,dp(52),1));type.addView(engines,new RadioGroup.LayoutParams(0,dp(52),1));type.check(human.getId());body.addView(type);
        importCollectionDialog=dialog("New import collection").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Continue",null).create();
        importCollectionDialog.setOnDismissListener(d->{if(importCollectionDialog==d){importCollectionDialog=null;importNameInput=null;importEngineInput=null;}});importCollectionDialog.show();
        final AlertDialog shown=importCollectionDialog;shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("Name this collection");return;}
            pendingImportName=value;pendingImportEngine=engines.isChecked();shown.dismiss();
            if(route==0)chooseFile();else if(route==1)paste();else importUri(uri);
        });
    }
    private void chooseFile() { Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); try{startActivityForResult(intent,IMPORT);}catch(ActivityNotFoundException e){error("File picker unavailable","Use Paste to import a game on this device.");} }
    private void paste() {
        pasteSessionId=ImportDraftStore.newToken();
        EditText input=new EditText(this); input.setTextColor(LIGHT); input.setHintTextColor(MUTED); input.setHint("[Red \"Player\"]\n[Black \"Opponent\"]\n\n1. b2e2 b9c7 *"); input.setMinLines(7); input.setMaxLines(12); input.setGravity(Gravity.TOP); input.setTextSize(14); input.setTypeface(Typeface.MONOSPACE); pad(input,18,12,18,12);
        input.setContentDescription("Game record text");
        pasteInput=input;
        AlertDialog d=dialog("Paste into "+pendingImportName).setView(input).setNegativeButton("Cancel",null).setPositiveButton("Import",null).create();pasteDialog=d;d.setOnDismissListener(v->{if(pasteDialog==d){pasteDialog=null;pasteInput=null;if(!destroyed){clearPasteDraft();pasteSessionId="";}}});
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String value=input.getText().toString();if(value.getBytes(StandardCharsets.UTF_8).length>GameRepository.MAX_BYTES){input.setError("Text exceeds 5 MB");return;}importText(value,d);}));d.show();
    }
    private void importText(String text) { importText(text,null); }
    private void importText(String text,AlertDialog editing) {
        final String name=pendingImportName;final boolean engineGames=pendingImportEngine;
        final String session=editing!=null&&editing==pasteDialog?pasteSessionId:"";
        if(editing!=null){editing.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);editing.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);editing.setCancelable(false);if(pasteInput!=null)pasteInput.setEnabled(false);}
        toast("Checking all imported moves…");io.execute(()->{
            try{
                int added=repository.importText(text,name,engineGames);
                if(editing!=null)runOnUiThread(()->{if(!destroyed&&pasteDialog==editing)editing.dismiss();});
                completeImport(added,session);
            }catch(Exception e){runOnUiThread(()->{
                if(destroyed)return;
                if(editing!=null&&pasteDialog==editing&&editing.isShowing()){
                    editing.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);editing.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);editing.setCancelable(true);
                    if(pasteInput!=null){pasteInput.setEnabled(true);pasteInput.setError(message(e));}
                }else error("Import was not saved",message(e));
            });}
        });
    }
    private void importUri(Uri uri) {
        toast("Reading and checking game file…"); final ContentResolver resolver=getApplicationContext().getContentResolver();final String name=pendingImportName;final boolean engineGames=pendingImportEngine;
        io.execute(()->{try(InputStream in=resolver.openInputStream(uri)){
            if(in==null)throw new IOException("The selected file could not be opened.");
            int added=repository.importText(GameRepository.read(in),name,engineGames);completeImport(added);
        }catch(Exception e){runOnUiThread(()->{if(!destroyed)error("Import was not saved",message(e));});}});
    }
    private void completeImport(int added){completeImport(added,"");}
    @android.annotation.SuppressLint("ApplySharedPref") // FILE_IO only: persist completion before a replacement Activity's queued load, including after process death.
    private void completeImport(int added,String pasteSession){
        List<GameRepository.Game> updated=repository.all();List<GameRepository.ImportedCollection> collections=repository.importedCollections();
        String importedId=collections.get(collections.size()-1).id;
        // A replacement Activity queues its load after this task and reads the
        // destination even when the originating Activity has been destroyed.
        SharedPreferences.Editor completed=prefs.edit().putString("libraryTab","imports").putString("selectedImportId",importedId).putString("newImportId",importedId).putBoolean("library",true);
        if(!pasteSession.isEmpty())completed.putString("completedPasteSession",pasteSession);
        completed.commit();
        runOnUiThread(()->{if(destroyed)return;games=updated;selectLibrary("imports",importedId);prefs.edit().remove("newImportId").apply();toast("Imported "+added+" game"+(added==1?"":"s")+" into a separate collection. Saved on this device.");});
    }
    private void importHelp() { dialog("Supported imports").setMessage("Name your collection and choose Human games or Engine games. Each import appears separately under My imports and never changes the built-in Human or Engine collections.\n\nImport .pgn text using ICCS coordinates (a0–i9), or a normalized JSON pack. Example:\n\n[Red \"Player\"]\n[Black \"Opponent\"]\n[Result \"*\"]\n\n1. b2e2 b9c7 *\n\nComments and side variations in PGN are ignored; the main line is imported. Custom positions use a FEN tag. Every move must be legal, or the whole import is rejected. Duplicate move records within this import are skipped. The same game can be kept in different collections.\n\nFile limit: 5 MB. Binary XQF / CBR / CBL, Chinese move notation and WXF move notation need conversion to ICCS PGN before importing.").setPositiveButton("Got it",null).show(); }
    private void exportGame() {
        pendingExport=GameRepository.export(game,new ArrayList<>(workingMoves),exploring);
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/x-chess-pgn");intent.putExtra(Intent.EXTRA_TITLE,"xiangqi-"+game.id.replaceAll("[^A-Za-z0-9_-]","_")+(exploring?"-variation":"")+".pgn");
        try{startActivityForResult(intent,EXPORT);}catch(ActivityNotFoundException e){error("File picker unavailable","An Android document provider is required to export files.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==IMPORT)importUri(data.getData());
        else if(request==EXPORT&&pendingExport!=null){final String value=pendingExport;final Uri uri=data.getData();io.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("The selected destination could not be opened.");out.write(value.getBytes(StandardCharsets.UTF_8));runOnUiThread(()->{if(!destroyed)toast("Exported ICCS PGN.");});}catch(Exception e){runOnUiThread(()->{if(!destroyed)error("Could not export game",message(e));});}});}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(loaded)handleIntent(intent);}
    private boolean handleIntent(Intent intent){
        if(intent==null)return false;
        try {
            Uri uri=intent.getData();
            if(Intent.ACTION_SEND.equals(intent.getAction())){
                Parcelable stream=intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if(stream!=null&&!(stream instanceof Uri))throw new IllegalArgumentException("The shared file is not a document URI.");
                uri=(Uri)stream;
            }
            if(uri!=null&&(Intent.ACTION_VIEW.equals(intent.getAction())||Intent.ACTION_SEND.equals(intent.getAction()))){restoredImportDraft=null;importCollection(2,uri);setIntent(new Intent(this,MainActivity.class));return true;}
        }catch(BadParcelableException|IllegalArgumentException e){restoredImportDraft=null;setIntent(new Intent(this,MainActivity.class));error("Could not open shared game",message(e));return true;}
        return false;
    }

    private void restoreImportDraft(Bundle draft,String text,String error){
        if(draft==null||restoredImportDraft!=draft)return;restoredImportDraft=null;
        String session=draft.getString("sessionId","");
        if(!session.isEmpty()&&session.equals(prefs.getString("completedPasteSession",""))){discardImportDraft(draft);return;}
        if("setup".equals(draft.getString("kind"))){
            Uri uri=draft.getParcelable("uri");importCollection(draft.getInt("route",1),uri);
            importNameInput.setText(draft.getString("name",""));importEngineInput.setChecked(draft.getBoolean("engine",false));
        }else if("paste".equals(draft.getString("kind"))){
            pasteDraftToken=draft.getString("token","");paste();if(!session.isEmpty())pasteSessionId=session;pasteInput.setText(text);
            pasteInput.setSelection(Math.max(0,Math.min(text.length(),draft.getInt("cursor",text.length()))));
            if(!error.isEmpty())pasteInput.setError(error);
        }
    }
    private Bundle saveImportDraft(){
        Bundle draft=new Bundle();
        if(importCollectionDialog!=null&&importCollectionDialog.isShowing()&&importNameInput!=null){
            draft.putString("kind","setup");draft.putInt("route",importDialogRoute);draft.putParcelable("uri",importDialogUri);
            draft.putString("name",importNameInput.getText().toString());draft.putBoolean("engine",importEngineInput.isChecked());
        }else if(pasteDialog!=null&&pasteDialog.isShowing()&&pasteInput!=null){
            final String previousToken=pasteDraftToken;
            pasteDraftToken=ImportDraftStore.newToken();
            final String token=pasteDraftToken,text=pasteInput.getText().toString();
            draft.putString("kind","paste");draft.putString("token",token);draft.putString("sessionId",pasteSessionId);draft.putInt("cursor",pasteInput.getSelectionStart());
            // FILE_IO writes before a replacement Activity's queued load. Keep
            // multi-megabyte game text out of Android's saved-state transaction.
            io.execute(()->{try{ImportDraftStore.write(this,token,text);if(!previousToken.isEmpty())ImportDraftStore.delete(this,previousToken);}catch(Exception ignored){/* A failed snapshot has its own token, so restoration reports the failure instead of silently showing older text. */}});
        }else return restoredImportDraft;
        return draft;
    }
    private void clearPasteDraft(){String token=pasteDraftToken;pasteDraftToken="";if(!token.isEmpty())io.execute(()->{try{ImportDraftStore.delete(this,token);}catch(Exception ignored){/* Cache cleanup must not interrupt an import or cancellation. */}});}
    private void discardImportDraft(Bundle draft){if(draft!=null&&"paste".equals(draft.getString("kind"))){String token=draft.getString("token","");io.execute(()->{try{ImportDraftStore.delete(this,token);}catch(Exception ignored){/* An obsolete cache snapshot does not affect the committed collection. */}});}}

    private void about() {
        dialog("Xiangqi Study").setMessage("An offline game library, replay board and analysis workspace. Your imported games and study positions stay on this device.\n\nThe library includes the World Xiangqi Federation’s official 2025 World Championship records and selected historical master collections. Each game links back to its source. Collection labels identify the added packs; source and time-control evidence are documented in the project.\n\n"+(PikafishEngine.available(this)?"Pikafish is bundled for this device and runs offline. See the project’s engine provenance and GPL-3.0 source notices.":"This device uses the lightweight built-in classical engine. It is useful for exploring moves but cannot promise expert accuracy.")+" The replay board does not adjudicate repetition / perpetual-chase tournament rules.\n\nImport ICCS PGN or JSON. Use Export to back up a game or your current variation; uninstalling the app removes private imports and saved positions.")
            .setPositiveButton("Official WXF archive",(d,w)->openSource("https://www.wxf-xiangqi.org/index.php?Itemid=313&id=218&lang=en&option=com_content&view=article"))
            .setNegativeButton("Close",null).setNeutralButton("Engine notices",(d,w)->engineNotices()).show();
    }
    private void engineNotices(){dialog("Pikafish notices").setMessage("Pikafish "+PikafishRelease.VERSION+"\nA free UCI Xiangqi engine derived from Stockfish. Copyright the Pikafish and Stockfish contributors.\n\nEngine code: GNU General Public License version 3. The exact upstream release source and license are available from the links below and documented with this project.\n\nThe bundled pikafish.nnue weights have a separate upstream NNUE license. Commercial use requires the upstream rights holder’s permission. This build is intended for personal study.\n\nThe built-in fallback engine and replay board are part of Xiangqi Study; the engine name shown on the board identifies the engine actually used.")
        .setPositiveButton("Upstream source",(d,w)->openSource(PikafishRelease.SOURCE_URL))
        .setNeutralButton("GPL license",(d,w)->openSource(PikafishRelease.LICENSE_URL))
        .setNegativeButton("Close",null).show();}
    private void openSource(String url) {
        if(url==null||url.isEmpty()){toast("No source link is recorded for this imported game.");return;}
        Uri uri=Uri.parse(url);if(!"https".equalsIgnoreCase(uri.getScheme())&&!"http".equalsIgnoreCase(uri.getScheme())){error("Source information",url);return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){error("Source link",url);}
    }
    private GameRepository.Game find(String id){for(GameRepository.Game g:games)if(g.id.equals(id))return g;return null;}
    private String analysisIntro(){String name=PikafishEngine.available(this)?"Pikafish":"Built-in analysis";return name+" · offline analysis\n"+(analysisEnabled?"Analysis is on and follows the viewed position. Open Game menu → Stop analysis to switch it off.":"Open Game menu → Start analysis for an evaluation and suggested continuation.")+"\n+ favors Red · − favors Black. All analysis runs on your device.";}
    private static String timeControl(String value){
        if("3600+30".equals(value)||"60 分钟+30 秒".equals(value)||"60分钟+30秒".equals(value))return "Classical · 60 min + 30 sec";
        if(value.matches("[0-9]{1,6}\\+[0-9]{1,5}")){String[] parts=value.split("\\+");int seconds=Integer.parseInt(parts[0]);return "Time control · "+(seconds%60==0?seconds/60+" min":seconds+" sec")+" + "+Integer.parseInt(parts[1])+" sec";}
        return value;
    }
    private void saveState() {
        rememberLibraryScroll();if(prefs==null)return;saveLibraryScope(); SharedPreferences.Editor edit=prefs.edit().putBoolean("library",library).putBoolean("flipped",flipped);
        if(game!=null){edit.putString("lastGame",game.id);if(!exploring)edit.putInt("ply:"+game.id,ply);edit.putBoolean("exploring:"+game.id,exploring);if(exploring){edit.putString("variationMoves:"+game.id,new JSONArray(workingMoves).toString()).putInt("variationPly:"+game.id,ply);}}
        edit.apply();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);saveState();out.putString("pendingExport",pendingExport);out.putString("pendingImportName",pendingImportName);out.putBoolean("pendingImportEngine",pendingImportEngine);out.putBundle("importDraft",saveImportDraft());}
    @Override protected void onRestoreInstanceState(Bundle in){super.onRestoreInstanceState(in);pendingExport=in.getString("pendingExport");}
    @Override protected void onStart(){super.onStart();foreground=true;if(loaded){if(library)renderCards();else refreshGameNavigation();}resumeAnalysis();}
    @Override protected void onStop(){super.onStop();foreground=false;cancelLibrarySearch();seeking=false;boolean wasPlaying=vsEngine;stopAutoplay();cancelGameSearch(true);cancelAnalysis();if(wasPlaying&&!library&&board!=null)updateReplay();saveState();}
    @Override protected void onDestroy(){destroyed=true;cancelGameSearch(true);cancelLibrarySearch();libraryWorker.shutdownNow();handler.removeCallbacksAndMessages(null);engine.shutdownNow();if(errorDialog!=null)errorDialog.dismiss();if(gameInformationDialog!=null)gameInformationDialog.dismiss();if(replaySpeedDialog!=null)replaySpeedDialog.dismiss();if(importCollectionDialog!=null)importCollectionDialog.dismiss();if(pasteDialog!=null)pasteDialog.dismiss();if(goToDialog!=null)goToDialog.dismiss();if(playSideDialog!=null)playSideDialog.dismiss();if(colorDialog!=null)colorDialog.dismiss();if(presetDialog!=null)presetDialog.dismiss();if(settingsDialog!=null)settingsDialog.dismiss();if(engineSettingsDialog!=null)engineSettingsDialog.dismiss();super.onDestroy();}
    @Override public void onBackPressed(){if(!library)showLibrary();else super.onBackPressed();}

    private AlertDialog.Builder dialog(String title){return new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert).setTitle(title);}
    private void error(String title,String message){if(errorDialog!=null)errorDialog.dismiss();AlertDialog shown=dialog(title).setMessage(message).setPositiveButton("OK",null).create();errorDialog=shown;shown.setOnDismissListener(d->{if(errorDialog==shown)errorDialog=null;});shown.show();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private static String message(Exception e){String value=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();return value.length()>2000?value.substring(0,2000)+"…":value;}
    private static String displayResult(String result){return "1/2-1/2".equals(result)?"½–½":result.replace('-','–');}
    private static String pretty(String move){return move.length()==4?move.substring(0,2)+"–"+move.substring(2):move;}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    private LinearLayout playerRow(String side,String name,int size){
        LinearLayout line=row();pad(line,0,2,0,2);TextView label=text(side,11,"RED".equals(side)?0xfff1a294:0xffbfd5cf);
        label.setTypeface(null,Typeface.BOLD);line.addView(label,new LinearLayout.LayoutParams(dp(49),-2));
        TextView player=text(PlayerNames.display(name),size,LIGHT);player.setTypeface(null,Typeface.BOLD);line.addView(player,new LinearLayout.LayoutParams(0,-2,1));
        return line;
    }
    private GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private Button button(String label,Runnable action,boolean primary){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(14);b.setTextColor(primary?INK:LIGHT);b.setBackground(shape(primary?ACCENT:SURFACE,0,12));b.setMinimumHeight(dp(48));b.setMinHeight(dp(48));b.setPadding(dp(10),dp(6),dp(10),dp(6));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(2),dp(3),dp(2),dp(3));b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;}
    private View weight(View v,float value){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),value);p.setMargins(dp(2),dp(3),dp(2),dp(3));v.setLayoutParams(p);return v;}
    private void pad(View view,int l,int t,int r,int b){view.setPadding(dp(l),dp(t),dp(r),dp(b));}
}
