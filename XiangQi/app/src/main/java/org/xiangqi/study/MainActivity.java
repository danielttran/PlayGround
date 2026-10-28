package org.xiangqi.study;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
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
    private static final int IMPORT=101, EXPORT=102, LIBRARY_PAGE_SIZE=40, MOVE_RECORD_ROWS=40;
    private final Handler handler=new Handler(Looper.getMainLooper());
    // File operations outlive configuration changes and stay ordered: a recreated
    // Activity loads after an import already queued by its predecessor.
    private static final ExecutorService FILE_IO=Executors.newSingleThreadExecutor();
    private final ExecutorService io=FILE_IO, engine=Executors.newSingleThreadExecutor();
    private Future<?> analysisFuture,playFuture;
    private int analysisGeneration,playGeneration;
    private SharedPreferences prefs;
    private GameRepository repository;
    private List<GameRepository.Game> games=new ArrayList<>();
    private LinearLayout root,content,cards,libraryPager,movesView,analysisBox;
    private TextView status,positionLabel,analysisText,variationLabel,eventLabel;
    private Button autoplayButton,exploreButton,previousGameButton,nextGameButton;
    private SeekBar seek;
    private BoardView board;
    private GameRepository.Game game;
    private final List<Position> positions=new ArrayList<>();
    private List<String> workingMoves=new ArrayList<>();
    private int ply,libraryPage;
    private boolean exploring,flipped,autoplay,destroyed,library=true,loaded;
    private boolean vsEngine,humanRed,engineThinking;
    private String search="",pendingExport,librarySort="newest";
    private Engine.Result lastResult;
    private AlertDialog settingsDialog,engineSettingsDialog,colorDialog,presetDialog,playSideDialog;
    private float swipeStartX,swipeStartY;
    private boolean swipeTracking,swipeConsumed,swipeBlocked;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs=getSharedPreferences("study",MODE_PRIVATE); repository=new GameRepository(this);
        librarySort=GameDateSort.order(prefs.getString("librarySort","newest"));
        if(state!=null){search=state.getString("search", "");libraryPage=state.getInt("libraryPage",0);}
        flipped=prefs.getBoolean("flipped",false);
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
        io.execute(()->{
            try {
                List<GameRepository.Game> loadedGames=repository.load();
                runOnUiThread(()->{
                    if(destroyed)return; games=loadedGames; loaded=true;
                    String id=prefs.getString("lastGame",""); GameRepository.Game resume=find(id);
                    if(!prefs.getBoolean("library",true)&&resume!=null) openGame(resume,true); else showLibrary();
                    handleIntent(getIntent());
                });
            } catch(Exception e) { runOnUiThread(()->{ if(!destroyed) { root.removeAllViews(); root.addView(text("The library could not be loaded.",20,LIGHT)); root.addView(text(message(e),14,MUTED)); root.addView(button("Retry",()->recreate(),true)); } }); }
        });
    }

    private void showLibrary() {
        stopAutoplay(); cancelGameSearch(true); cancelAnalysis(); saveState(); library=true; prefs.edit().putBoolean("library",true).apply();
        root.removeAllViews(); content=column();
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout heading=row();TextView title=text("Xiangqi Study",28,LIGHT);title.setTypeface(null,Typeface.BOLD);
        heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=button("Settings",this::settings,false);heading.addView(settings,new LinearLayout.LayoutParams(dp(94),dp(48)));content.addView(heading);
        TextView subtitle=text("A world of games. A board to understand them.",14,MUTED); pad(subtitle,0,4,0,18); content.addView(subtitle);
        GameRepository.Game current=currentGame();
        if(current!=null)content.addView(button("Return to current game · swipe left\nRed: "+PlayerNames.display(current.red)+"\nBlack: "+PlayerNames.display(current.black),this::returnToCurrentGame,true));
        LinearLayout tools=row(); tools.addView(weight(button("Import games",this::importMenu,true),1));
        tools.addView(weight(button("About & sources",this::about,false),1)); content.addView(tools);
        EditText field=new EditText(this); field.setSingleLine(true); field.setTextColor(LIGHT); field.setHintTextColor(MUTED);
        field.setTextSize(15); field.setHint("Search player, event, round or collection"); field.setBackground(shape(SURFACE,LINE,12)); pad(field,14,6,14,6);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(52)); fp.setMargins(0,dp(14),0,dp(10)); content.addView(field,fp);
        LinearLayout sortRow=row();TextView sortLabel=text("Sort games",13,MUTED);sortRow.addView(sortLabel,new LinearLayout.LayoutParams(dp(88),-2));
        Spinner sort=new Spinner(this);sort.setContentDescription("Game sort order");
        ArrayAdapter<String> sortAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Collection order","Newest first","Oldest first"}){
            @Override public View getView(int position,View convert,ViewGroup parent){TextView view=(TextView)super.getView(position,convert,parent);view.setTextColor(LIGHT);return view;}
        };
        sort.setAdapter(sortAdapter);sort.setSelection(index(librarySort,"collection","newest","oldest"));
        sortRow.addView(sort,new LinearLayout.LayoutParams(0,dp(48),1));content.addView(sortRow);
        sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){String value=new String[]{"collection","newest","oldest"}[position];if(!value.equals(librarySort)){librarySort=value;libraryPage=0;prefs.edit().putString("librarySort",value).apply();renderCards();}}public void onNothingSelected(AdapterView<?> parent){}});
        status=text("",12,MUTED); pad(status,2,0,2,10); content.addView(status);
        cards=column(); content.addView(cards);libraryPager=row();content.addView(libraryPager);field.setText(search); field.setSelection(field.length());
        field.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int count,int after){} public void onTextChanged(CharSequence s,int st,int before,int count){ search=s.toString();libraryPage=0;renderCards(); } public void afterTextChanged(Editable e){} });
        renderCards();
    }

    private void renderCards() {
        if(cards==null||!library)return; cards.removeAllViews();
        boolean hideResults=StudySettings.load(this).hideResults;
        String needle=PlayerNames.fold(search).trim();
        List<GameRepository.Game> matches=new ArrayList<>();
        for(GameRepository.Game item:GameDateSort.sorted(games,g->g.date,librarySort)) {
            if(!needle.isEmpty() && !PlayerNames.fold(PlayerNames.searchText(item.red)+" "+PlayerNames.searchText(item.black)+" "+item.event+" "+item.round+" "+item.collection).contains(needle))continue;
            matches.add(item);
        }
        int count=matches.size();libraryPage=Math.max(0,Math.min(libraryPage,Math.max(0,(count-1)/LIBRARY_PAGE_SIZE)));
        int first=libraryPage*LIBRARY_PAGE_SIZE,last=Math.min(count,first+LIBRARY_PAGE_SIZE);
        // Bound native view inflation on the UI thread even for large imports.
        // Filtering and chronological sorting still cover the whole library.
        for(GameRepository.Game item:matches.subList(first,last)) {
            LinearLayout card=column(); card.setBackground(shape(SURFACE,LINE,15)); pad(card,14,14,14,12);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,0,0,dp(9)); cards.addView(card,cp);
            card.addView(playerRow("RED",item.red,16));card.addView(playerRow("BLACK",item.black,16));
            if(!hideResults){LinearLayout result=row();TextView caption=text("Result",12,MUTED);result.addView(caption,new LinearLayout.LayoutParams(0,-2,1));TextView score=text(displayResult(item.result),14,MUTED);result.addView(score);card.addView(result);}
            TextView event=text(item.event,14,MUTED); pad(event,0,10,0,0); card.addView(event);
            if(!item.round.isEmpty()) card.addView(text(item.round,13,MUTED));
            if(!item.collection.isEmpty())card.addView(text(item.collection,12,ACCENT));
            if(!item.timeControl.isEmpty())card.addView(text(timeControl(item.timeControl),12,ACCENT));
            TextView details=text((item.date.isEmpty()?"Undated":item.date)+" · "+item.moves.size()+" half-moves"+(item.imported?" · Imported":""),12,MUTED); pad(details,0,14,0,6); card.addView(details);
            boolean hasVariation=prefs.getBoolean("exploring:"+item.id,false);
            int resume=hasVariation?prefs.getInt("variationPly:"+item.id,0):Math.min(item.moves.size(),prefs.getInt("ply:"+item.id,0));
            TextView action=text(hasVariation?"Resume variation · move "+((resume+1)/2)+"  →":resume>0?"Resume at move "+((resume+1)/2)+"  →":"Open game  →",13,ACCENT); action.setGravity(Gravity.END); card.addView(action);
            card.setOnClickListener(v->openGame(item,true)); card.setFocusable(true);
            card.setContentDescription("Red: "+PlayerNames.display(item.red)+". Black: "+PlayerNames.display(item.black)+". "+item.event+", "+item.round+(hideResults?"":", result "+displayResult(item.result))+". Open game.");
        }
        String total=getResources().getQuantityString(R.plurals.library_count,games.size(),count,games.size());
        status.setText(count==0?total:getString(R.string.library_page_status,total,first+1,last));
        libraryPager.removeAllViews();
        if(count>LIBRARY_PAGE_SIZE){
            Button previous=button("Previous 40 games",()->changeLibraryPage(-1),false);previous.setEnabled(libraryPage>0);
            Button next=button("Next 40 games",()->changeLibraryPage(1),true);next.setEnabled(last<count);
            libraryPager.addView(weight(previous,1));libraryPager.addView(weight(next,1));
        }
        if(count==0) { TextView empty=text("No matching games. Try a player’s surname, or import a game pack.",16,MUTED); pad(empty,4,24,4,24); cards.addView(empty); }
    }
    private void changeLibraryPage(int direction){libraryPage+=direction;renderCards();if(content.getParent() instanceof ScrollView)((ScrollView)content.getParent()).scrollTo(0,0);}

    private void openGame(GameRepository.Game selected,boolean restore) {
        stopAutoplay(); cancelGameSearch(true); cancelAnalysis(); saveState(); game=selected; library=false;
        workingMoves=new ArrayList<>(game.moves); exploring=false; ply=restore?prefs.getInt("ply:"+game.id,0):0;
        if(restore && prefs.getBoolean("exploring:"+game.id,false)) try {
            JSONArray array=new JSONArray(prefs.getString("variationMoves:"+game.id,"[]"));
            List<String> saved=new ArrayList<>(); for(int i=0;i<array.length();i++)saved.add(array.getString(i));
            workingMoves=saved; exploring=true; ply=prefs.getInt("variationPly:"+game.id,0);
        } catch(Exception ignored) { /* An interrupted preference write leaves the original record intact. */ }
        try { rebuildPositions(); }
        catch(RuntimeException e) { exploring=false; workingMoves=new ArrayList<>(game.moves); try{rebuildPositions();}catch(RuntimeException bad){showLibrary();error("This game contains an invalid move",message(bad));return;} }
        ply=Math.max(0,Math.min(ply,workingMoves.size())); buildReplay(); updateReplay(); saveState();
    }

    private void rebuildPositions() {
        positions.clear(); Position position=game.initial(); positions.add(position);
        for(String move:workingMoves) { position=position.play(move); positions.add(position); }
    }

    private void buildReplay() {
        root.removeAllViews(); content=column(); ScrollView scroll=new ScrollView(this); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout nav=row(); nav.addView(weight(button("‹  Library",this::showLibrary,false),1));
        Button menu=button("⋮",()->{},false);menu.setTextSize(25);menu.setContentDescription("Game menu");menu.setOnClickListener(v->gameMenu(menu));nav.addView(menu,new LinearLayout.LayoutParams(dp(54),dp(48)));content.addView(nav);
        LinearLayout redPlayer=playerRow("RED",game.red,18);pad(redPlayer,2,14,2,3);content.addView(redPlayer);
        LinearLayout blackPlayer=playerRow("BLACK",game.black,18);pad(blackPlayer,2,0,2,6);content.addView(blackPlayer);
        eventLabel=text(replayEvent(),13,MUTED);pad(eventLabel,2,0,2,12);content.addView(eventLabel);
        board=new BoardView(this);StudySettings appearance=StudySettings.load(this);board.setAppearance(appearance.boardStyle,appearance.glyphStyle,appearance.discStyle);
        board.setCoordinates(appearance.coordinateStyle);
        board.setCustomColors(appearance.boardColor,appearance.redBackground,appearance.blackBackground);
        board.setFlipped(flipped); board.setMoveListener(this::makeMove); content.addView(board,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout labels=row();positionLabel=text("",13,LIGHT);positionLabel.setSingleLine(true);positionLabel.setEllipsize(TextUtils.TruncateAt.END);
        int labelHeight=Math.max(dp(26),Math.round(positionLabel.getPaint().getFontSpacing())+dp(4));
        labels.addView(positionLabel,new LinearLayout.LayoutParams(-1,labelHeight));pad(labels,3,14,3,2);content.addView(labels);
        seek=new SeekBar(this); seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT)); seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT)); seek.setContentDescription("Game position, in half-moves");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){if(user)navigate(p);} public void onStartTrackingTouch(SeekBar b){stopAutoplay();} public void onStopTrackingTouch(SeekBar b){} }); content.addView(seek,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout controls=row();previousGameButton=button("‹ Game",()->navigateGame(-1),false);previousGameButton.setTextSize(12);previousGameButton.setContentDescription("Previous game");controls.addView(weight(previousGameButton,1));
        nextGameButton=button("Game ›",()->navigateGame(1),false);nextGameButton.setTextSize(12);nextGameButton.setContentDescription("Next game");controls.addView(weight(nextGameButton,1));
        autoplayButton=button("▶",this::toggleAutoplay,true); autoplayButton.setContentDescription("Play replay"); controls.addView(weight(autoplayButton,1));
        Button previousMove=button("‹",()->navigate(ply-1),false);previousMove.setContentDescription("Previous move");controls.addView(weight(previousMove,1));
        Button nextMove=button("›",()->navigate(ply+1),false);nextMove.setContentDescription("Next move");controls.addView(weight(nextMove,1));content.addView(controls);
        exploreButton=button("Play from here",()->{if(vsEngine)stopEngineGame();else toggleExplore();},false); content.addView(exploreButton);
        variationLabel=text("",13,ACCENT); pad(variationLabel,3,6,3,4); content.addView(variationLabel);
        analysisBox=column(); analysisBox.setBackground(shape(SURFACE,0,12)); pad(analysisBox,14,12,14,12);
        analysisText=text(analysisIntro(),13,MUTED);
        analysisBox.addView(analysisText); content.addView(analysisBox);
        TextView movesTitle=text("MOVE RECORD",12,MUTED); pad(movesTitle,3,20,3,8); content.addView(movesTitle);
        movesView=column(); content.addView(movesView);
        Button source=button(game.imported?"Game source / information":"View game source",()->openSource(game.sourceUrl),false); content.addView(source);
        TextView note=text("ICCS coordinates · a0 is Red’s lower-left corner. Source results are recorded results; analysis variations do not change them.",12,MUTED); pad(note,3,8,3,18); content.addView(note);
    }

    private void updateReplay() {
        if(library||board==null)return;
        Position p=positions.get(ply); board.setPosition(p,ply==0?"":workingMoves.get(ply-1),exploring&&(!vsEngine||p.redToMove==humanRed&&!engineThinking));
        board.setContentDescription(board.getContentDescription()+" Red: "+PlayerNames.display(game.red)+". Black: "+PlayerNames.display(game.black)+".");
        positionLabel.setText(ply==0?"Starting position":"After "+((ply+1)/2)+" · "+(positions.get(ply-1).redToMove?"Red":"Black"));
        seek.setMax(workingMoves.size()); seek.setProgress(ply);
        exploreButton.setText(vsEngine?"Stop playing":exploring?"Return to recorded game":"Play from here");
        variationLabel.setText(vsEngine?"Playing as "+(humanRed?"Red":"Black")+" · "+(engineThinking?"engine thinking…":p.legalMoves().isEmpty()?"game over":"your move")
            :exploring?"Exploring a variation · tap a piece, then a highlighted square.\nUse Legal moves for accessible move entry.":"Recorded game · "+ply+" / "+workingMoves.size()+" half-moves");
        variationLabel.setOnClickListener(exploring?v->legalMoves():null);
        variationLabel.setContentDescription(exploring?"Explore mode. Open legal moves list.":variationLabel.getText());
        List<GameRepository.Game> ordered=navigationGames();int current=gameIndex(ordered);previousGameButton.setEnabled(current>0);nextGameButton.setEnabled(current>=0&&current<ordered.size()-1);
        renderMoves(); saveState();
    }

    private void renderMoves() {
        movesView.removeAllViews(); boolean startsRed=game.initial().redToMove;
        if(exploring&&(!vsEngine||!engineThinking&&positions.get(ply).redToMove==humanRed)) movesView.addView(button("Legal moves…",this::legalMoves,false));
        int slots=workingMoves.size()+(startsRed?0:1);
        int totalRows=(slots+1)/2,currentRow=ply==0?0:(ply-1+(startsRed?0:1))/2;
        int firstRow=Math.max(0,Math.min(currentRow-MOVE_RECORD_ROWS/2,Math.max(0,totalRows-MOVE_RECORD_ROWS)));
        int lastRow=Math.min(totalRows,firstRow+MOVE_RECORD_ROWS);
        if(totalRows>MOVE_RECORD_ROWS){TextView window=text(getString(R.string.move_record_window,firstRow+1,lastRow,totalRows),12,MUTED);pad(window,3,0,3,8);movesView.addView(window);}
        for(int rowIndex=firstRow;rowIndex<lastRow;rowIndex++) {
            int i=rowIndex*2;
            LinearLayout moveRow=row(); TextView number=text((i/2+1)+".",12,MUTED); number.setGravity(Gravity.CENTER_VERTICAL); moveRow.addView(number,new LinearLayout.LayoutParams(dp(30),dp(46)));
            for(int k=0;k<2;k++) {
                final int index=i+k-(startsRed?0:1);
                TextView label=text(index>=0&&index<workingMoves.size()?pretty(workingMoves.get(index)):"",15,LIGHT);
                label.setGravity(Gravity.CENTER_VERTICAL); pad(label,12,0,6,0); label.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);
                if(index>=0&&index<workingMoves.size()) { label.setOnClickListener(v->navigate(index+1)); label.setFocusable(true); label.setContentDescription("After half-move "+(index+1)+", "+workingMoves.get(index)); if(ply==index+1)label.setBackground(shape(SURFACE,0,8)); }
                moveRow.addView(label,new LinearLayout.LayoutParams(0,dp(46),1));
            }
            movesView.addView(moveRow);
        }
    }

    private void navigate(int target) { stopAutoplay();cancelGameSearch(true); moveTo(target); }
    private void moveTo(int target) { cancelAnalysis(); ply=Math.max(0,Math.min(target,workingMoves.size())); updateReplay(); }
    private void toggleAutoplay() {
        cancelGameSearch(true);updateReplay();
        if(autoplay){stopAutoplay();return;} if(ply>=workingMoves.size())moveTo(0);
        autoplay=true; autoplayButton.setText("Ⅱ"); autoplayButton.setContentDescription("Pause replay"); handler.postDelayed(playTick,1000);
    }
    private final Runnable playTick=new Runnable(){public void run(){if(!autoplay||library)return;if(ply>=workingMoves.size()){stopAutoplay();return;}moveTo(ply+1);if(autoplay)handler.postDelayed(this,1000);}};
    private void stopAutoplay() { autoplay=false; handler.removeCallbacks(playTick); if(autoplayButton!=null){autoplayButton.setText("▶");autoplayButton.setContentDescription("Play replay");} }

    private void toggleExplore() {
        stopAutoplay(); cancelGameSearch(true); cancelAnalysis();
        if(exploring) {
            int target=Math.min(ply,game.moves.size()); exploring=false; workingMoves=new ArrayList<>(game.moves); rebuildPositions(); ply=target;
            prefs.edit().remove("variationMoves:"+game.id).remove("variationPly:"+game.id).putBoolean("exploring:"+game.id,false).apply();
        } else exploring=true;
        updateReplay();
    }
    private void makeMove(String move) {
        if(vsEngine&&(engineThinking||positions.get(ply).redToMove!=humanRed))return;
        stopAutoplay(); cancelAnalysis();
        try {
            Position next=positions.get(ply).play(move); exploring=true;
            workingMoves=new ArrayList<>(workingMoves.subList(0,ply)); workingMoves.add(move);
            while(positions.size()>ply+1)positions.remove(positions.size()-1); positions.add(next); ply++; updateReplay();if(vsEngine)requestEngineMove();
        } catch(RuntimeException e){error("Move could not be played",message(e));}
    }
    private void legalMoves() {
        if(!exploring||vsEngine&&(engineThinking||positions.get(ply).redToMove!=humanRed))return; List<String> moves=positions.get(ply).legalMoves();
        if(moves.isEmpty()){toast("No legal moves in this position.");return;}
        String[] labels=new String[moves.size()];for(int i=0;i<labels.length;i++)labels[i]=pretty(moves.get(i));
        dialog("Choose a legal move").setItems(labels,(d,n)->makeMove(moves.get(n))).setNegativeButton("Cancel",null).show();
    }

    private List<GameRepository.Game> navigationGames() {
        List<GameRepository.Game> ordered=GameDateSort.sorted(games,g->g.date,librarySort),matched=new ArrayList<>();
        String needle=PlayerNames.fold(search).trim();
        for(GameRepository.Game item:ordered)if(needle.isEmpty()||PlayerNames.fold(PlayerNames.searchText(item.red)+" "+PlayerNames.searchText(item.black)+" "+item.event+" "+item.round+" "+item.collection).contains(needle))matched.add(item);
        return gameIndex(matched)>=0?matched:ordered;
    }
    private int gameIndex(List<GameRepository.Game> entries){for(int i=0;i<entries.size();i++)if(game!=null&&entries.get(i).id.equals(game.id))return i;return -1;}
    private void navigateGame(int direction){List<GameRepository.Game> entries=navigationGames();int index=gameIndex(entries)+direction;if(index>=0&&index<entries.size())openGame(entries.get(index),true);}
    private void gameMenu(View anchor) {
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,1,0,"Flip board");menu.getMenu().add(0,2,1,analysisFuture==null?getString(R.string.analyze):getString(R.string.stop_analysis));
        menu.getMenu().add(0,3,2,"Play against engine");menu.getMenu().add(1,4,3,"Export game");menu.getMenu().add(1,5,4,"Settings");
        menu.setOnMenuItemClickListener(item->{
            switch(item.getItemId()){
                case 1:flipped=!flipped;board.setFlipped(flipped);saveState();break;
                case 2:if(analysisFuture==null)analyze();else stopSearch();break;
                case 3:chooseEngineSide();break;
                case 4:exportGame();break;
                case 5:settings();break;
                default:return false;
            }return true;
        });menu.show();
    }
    private void chooseEngineSide(){
        playSideDialog=dialog("Play against engine").setMessage("Start from the current position. Choose your side.\n\nThe computer uses the time-per-position setting, or 3 seconds per move when analysis is untimed. Your recorded game is preserved.")
            .setPositiveButton("Play as Red",(d,w)->startEngineGame(true)).setNegativeButton("Play as Black",(d,w)->startEngineGame(false)).setNeutralButton("Cancel",null).create();
        playSideDialog.setOnDismissListener(d->playSideDialog=null);playSideDialog.show();
    }
    private void startEngineGame(boolean red){
        stopAutoplay();cancelGameSearch(true);cancelAnalysis();
        if(positions.get(ply).legalMoves().isEmpty()){updateReplay();toast("This position has no legal moves.");return;}
        humanRed=red;vsEngine=true;exploring=true;
        workingMoves=new ArrayList<>(workingMoves.subList(0,ply));while(positions.size()>ply+1)positions.remove(positions.size()-1);
        analysisText.setText(getString(R.string.engine_play_intro,PikafishEngine.available(this)?"Pikafish":"Built-in engine",red?"Red":"Black"));
        updateReplay();requestEngineMove();
    }
    private void stopEngineGame(){cancelGameSearch(true);updateReplay();analysisText.setText(analysisIntro());}
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
            }catch(Exception error){runOnUiThread(()->{if(!destroyed&&!library&&vsEngine&&generation==playGeneration){playFuture=null;engineThinking=false;vsEngine=false;updateReplay();analysisText.setText(getString(R.string.engine_play_error,message(error)));}});}
        });
    }

    private void analyze() {
        stopAutoplay();cancelGameSearch(true); cancelAnalysis();updateReplay(); final int generation=analysisGeneration;
        final Position snapshot=positions.get(ply).copy();
        final List<String> history=new ArrayList<>(workingMoves.subList(0,ply));
        final String startFen=game.startFen;
        final boolean nativeEngine=PikafishEngine.available(this);
        final String engineName=nativeEngine?"Pikafish":"Built-in analysis";
        final PikafishEngine.Options options=EngineSettings.load(this);
        analysisText.setText(getString(R.string.analysis_searching,engineName));
        final Engine.ProgressListener progress=result->runOnUiThread(()->{
            if(!destroyed&&generation==analysisGeneration&&!library)renderAnalysis(result,engineName,nativeEngine,true);
        });
        analysisFuture=engine.submit(()->{
            try {
                Engine.Result result=nativeEngine?PikafishEngine.analyze(this,snapshot,startFen,history,options,progress)
                    :Engine.analyze(snapshot,options.maxDepth,options.millis,progress);
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
        if(legal.isEmpty()){
            analysisText.setText(getString(R.string.analysis_terminal,engineName,current.redToMove?"Red":"Black",current.redToMove?"Black":"Red",
                current.isInCheck(current.redToMove)?"Checkmate.":"Stalemate is a loss in Xiangqi."));
            board.setHint("");while(analysisBox.getChildCount()>1)analysisBox.removeViewAt(1);return;
        }
        String score=String.format(Locale.getDefault(),"%+.2f",result.score/100.0);
        StringBuilder pv=new StringBuilder();for(int i=0;i<Math.min(result.pv.size(),8);i++){if(i>0)pv.append("  ");pv.append(pretty(result.pv.get(i)));}
        analysisText.setText(getString(R.string.analysis_result,engineName,
            Math.abs(result.score)>=29901?getString(R.string.analysis_winning,result.score>0?"Red":"Black"):score,
            result.depth,java.text.NumberFormat.getIntegerInstance().format(result.nodes),
            result.bestMove==null||result.bestMove.isEmpty()?getString(R.string.analysis_no_move):getString(R.string.analysis_suggested,pretty(result.bestMove)),
            pv.toString(),getString(nativeEngine?R.string.analysis_native_limit:R.string.analysis_builtin_limit)));
        board.setHint(result.bestMove);
        while(analysisBox.getChildCount()>1)analysisBox.removeViewAt(1);
        if(result.bestMove!=null&&!result.bestMove.isEmpty()&&legal.contains(result.bestMove))
            analysisBox.addView(button("Explore suggested move",()->makeMove(result.bestMove),false));
    }
    private void cancelAnalysis() {
        analysisGeneration++; if(analysisFuture!=null){analysisFuture.cancel(true);analysisFuture=null;}
        lastResult=null;
        if(board!=null)board.setHint("");
        if(analysisText!=null)analysisText.setText(analysisIntro());
        if(analysisBox!=null)while(analysisBox.getChildCount()>1)analysisBox.removeViewAt(1);
    }

    /** Stop retains the best completed estimate; navigation clears it separately. */
    private void stopSearch() {
        analysisGeneration++;if(analysisFuture!=null){analysisFuture.cancel(true);analysisFuture=null;}
        if(lastResult==null&&analysisText!=null)analysisText.setText(analysisIntro());
    }

    private String replayEvent() {
        return game.event+" · "+game.round+(StudySettings.load(this).hideResults?"":" · "+displayResult(game.result))
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
                boolean restart=analysisFuture!=null;cancelAnalysis();if(restart&&!library&&!destroyed)analyze();
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

    private void importMenu() { dialog("Import games").setItems(new String[]{"Choose a PGN or JSON file","Paste ICCS PGN / JSON","Import format help"},(d,n)->{if(n==0)chooseFile();else if(n==1)paste();else importHelp();}).setNegativeButton("Cancel",null).show(); }
    private void chooseFile() { Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); try{startActivityForResult(intent,IMPORT);}catch(ActivityNotFoundException e){error("File picker unavailable","Use Paste to import a game on this device.");} }
    private void paste() {
        EditText input=new EditText(this); input.setTextColor(LIGHT); input.setHintTextColor(MUTED); input.setHint("[Red \"Player\"]\n[Black \"Opponent\"]\n\n1. b2e2 b9c7 *"); input.setMinLines(7); input.setMaxLines(12); input.setGravity(Gravity.TOP); input.setTextSize(14); input.setTypeface(Typeface.MONOSPACE); pad(input,18,12,18,12);
        AlertDialog d=dialog("Paste a game record").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Import",null).create(); d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String value=input.getText().toString();if(value.getBytes(StandardCharsets.UTF_8).length>GameRepository.MAX_BYTES){input.setError("Text exceeds 5 MB");return;}d.dismiss();importText(value);}));d.show();
    }
    private void importText(String text) {
        toast("Checking all imported moves…"); io.execute(()->{try{int added=repository.importText(text);List<GameRepository.Game> updated=repository.all();runOnUiThread(()->{if(destroyed)return;games=updated;showLibrary();toast(added==0?"These games are already in your library.":"Imported "+added+" game"+(added==1?"":"s")+". Saved on this device.");});}catch(Exception e){runOnUiThread(()->{if(!destroyed)error("Import was not saved",message(e));});}});
    }
    private void importUri(Uri uri) {
        toast("Reading and checking game file…"); final ContentResolver resolver=getApplicationContext().getContentResolver();
        io.execute(()->{try(InputStream in=resolver.openInputStream(uri)){
            if(in==null)throw new IOException("The selected file could not be opened.");
            int added=repository.importText(GameRepository.read(in));List<GameRepository.Game> updated=repository.all();
            runOnUiThread(()->{if(destroyed)return;games=updated;showLibrary();toast(added==0?"These games are already in your library.":"Imported "+added+" game"+(added==1?"":"s")+". Saved on this device.");});
        }catch(Exception e){runOnUiThread(()->{if(!destroyed)error("Import was not saved",message(e));});}});
    }
    private void importHelp() { dialog("Supported imports").setMessage("Import .pgn text using ICCS coordinates (a0–i9), or a normalized JSON pack. Example:\n\n[Red \"Player\"]\n[Black \"Opponent\"]\n[Result \"*\"]\n\n1. b2e2 b9c7 *\n\nComments and side variations in PGN are ignored; the main line is imported. Custom positions use a FEN tag. Every move must be legal, or the whole import is rejected. Duplicate move records are skipped.\n\nFile limit: 5 MB. Binary XQF / CBR / CBL, Chinese move notation and WXF move notation need conversion to ICCS PGN before importing.").setPositiveButton("Got it",null).show(); }
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
    private void handleIntent(Intent intent){
        if(intent==null)return;
        try {
            Uri uri=intent.getData();
            if(Intent.ACTION_SEND.equals(intent.getAction())){
                Parcelable stream=intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if(stream!=null&&!(stream instanceof Uri))throw new IllegalArgumentException("The shared file is not a document URI.");
                uri=(Uri)stream;
            }
            if(uri!=null&&(Intent.ACTION_VIEW.equals(intent.getAction())||Intent.ACTION_SEND.equals(intent.getAction()))){importUri(uri);setIntent(new Intent(this,MainActivity.class));}
        }catch(BadParcelableException|IllegalArgumentException e){setIntent(new Intent(this,MainActivity.class));error("Could not open shared game",message(e));}
    }

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
    private String analysisIntro(){return PikafishEngine.available(this)?"Pikafish · offline analysis\nOpen Game menu → Start analysis for an evaluation and suggested continuation. All analysis runs on your device.":"Built-in analysis · offline\nOpen Game menu → Start analysis for a suggested move and evaluation. This lightweight engine is for study; its evaluation is not grandmaster strength.";}
    private static String timeControl(String value){
        if("3600+30".equals(value)||"60 分钟+30 秒".equals(value)||"60分钟+30秒".equals(value))return "Classical · 60 min + 30 sec";
        if(value.matches("[0-9]{1,6}\\+[0-9]{1,5}")){String[] parts=value.split("\\+");int seconds=Integer.parseInt(parts[0]);return "Time control · "+(seconds%60==0?seconds/60+" min":seconds+" sec")+" + "+Integer.parseInt(parts[1])+" sec";}
        return value;
    }
    private void saveState() {
        if(prefs==null)return; SharedPreferences.Editor edit=prefs.edit().putBoolean("library",library).putBoolean("flipped",flipped);
        if(game!=null){edit.putString("lastGame",game.id);if(!exploring)edit.putInt("ply:"+game.id,ply);edit.putBoolean("exploring:"+game.id,exploring);if(exploring){edit.putString("variationMoves:"+game.id,new JSONArray(workingMoves).toString()).putInt("variationPly:"+game.id,ply);}}
        edit.apply();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);saveState();out.putString("pendingExport",pendingExport);out.putString("search",search);out.putInt("libraryPage",libraryPage);}
    @Override protected void onRestoreInstanceState(Bundle in){super.onRestoreInstanceState(in);pendingExport=in.getString("pendingExport");}
    @Override protected void onStop(){super.onStop();boolean wasPlaying=vsEngine;stopAutoplay();cancelGameSearch(true);cancelAnalysis();if(wasPlaying&&!library&&board!=null)updateReplay();saveState();}
    @Override protected void onDestroy(){destroyed=true;cancelGameSearch(true);handler.removeCallbacksAndMessages(null);engine.shutdownNow();if(playSideDialog!=null)playSideDialog.dismiss();if(colorDialog!=null)colorDialog.dismiss();if(presetDialog!=null)presetDialog.dismiss();if(settingsDialog!=null)settingsDialog.dismiss();if(engineSettingsDialog!=null)engineSettingsDialog.dismiss();super.onDestroy();}
    @Override public void onBackPressed(){if(!library)showLibrary();else super.onBackPressed();}

    private AlertDialog.Builder dialog(String title){return new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert).setTitle(title);}
    private void error(String title,String message){dialog(title).setMessage(message).setPositiveButton("OK",null).show();}
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
