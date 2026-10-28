package org.xiangqi.study;

import android.app.*;
import android.content.*;
import android.graphics.Color;
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
    private final ExecutorService io=Executors.newSingleThreadExecutor(), engine=Executors.newSingleThreadExecutor();
    private Future<?> analysisFuture;
    private int analysisGeneration;
    private SharedPreferences prefs;
    private GameRepository repository;
    private List<GameRepository.Game> games=new ArrayList<>();
    private LinearLayout root,content,cards,movesView,analysisBox;
    private TextView status,positionLabel,turnLabel,analysisText,variationLabel;
    private Button autoplayButton,analyzeButton,exploreButton;
    private SeekBar seek;
    private BoardView board;
    private GameRepository.Game game;
    private final List<Position> positions=new ArrayList<>();
    private List<String> workingMoves=new ArrayList<>();
    private int ply;
    private boolean exploring,flipped,autoplay,destroyed,library=true,loaded;
    private String search="",pendingExport;
    private Engine.Result lastResult;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs=getSharedPreferences("study",MODE_PRIVATE); repository=new GameRepository(this);
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
        stopAutoplay(); cancelAnalysis(); saveState(); library=true; prefs.edit().putBoolean("library",true).apply();
        root.removeAllViews(); content=column();
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        TextView title=text("Xiangqi Study",28,LIGHT); title.setTypeface(null,Typeface.BOLD); content.addView(title);
        TextView subtitle=text("A world of games. A board to understand them.",14,MUTED); pad(subtitle,0,4,0,18); content.addView(subtitle);
        LinearLayout tools=row(); tools.addView(weight(button("Import games",this::importMenu,true),1));
        tools.addView(weight(button("About & sources",this::about,false),1)); content.addView(tools);
        EditText field=new EditText(this); field.setSingleLine(true); field.setTextColor(LIGHT); field.setHintTextColor(MUTED);
        field.setTextSize(15); field.setHint("Search player, event or round"); field.setBackground(shape(SURFACE,LINE,12)); pad(field,14,6,14,6);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(52)); fp.setMargins(0,dp(14),0,dp(10)); content.addView(field,fp);
        status=text("",12,MUTED); pad(status,2,0,2,10); content.addView(status);
        cards=column(); content.addView(cards); field.setText(search); field.setSelection(field.length());
        field.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int count,int after){} public void onTextChanged(CharSequence s,int st,int before,int count){ search=s.toString(); renderCards(); } public void afterTextChanged(Editable e){} });
        renderCards();
    }

    private void renderCards() {
        if(cards==null||!library)return; cards.removeAllViews(); int count=0;
        String needle=search.toLowerCase(Locale.ROOT).trim();
        for(GameRepository.Game item:games) {
            if(!needle.isEmpty() && !(item.title()+" "+item.event+" "+item.round).toLowerCase(Locale.ROOT).contains(needle))continue;
            count++;
            LinearLayout card=column(); card.setBackground(shape(SURFACE,LINE,15)); pad(card,14,14,14,12);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,0,0,dp(9)); cards.addView(card,cp);
            LinearLayout top=row(); TextView players=text(item.title(),16,LIGHT); players.setTypeface(null,Typeface.BOLD);
            top.addView(players,new LinearLayout.LayoutParams(0,-2,1)); TextView score=text(displayResult(item.result),14,MUTED); pad(score,8,0,0,0); top.addView(score); card.addView(top);
            TextView event=text(item.event,14,MUTED); pad(event,0,10,0,0); card.addView(event);
            if(!item.round.isEmpty()) card.addView(text(item.round,13,MUTED));
            if(!item.timeControl.isEmpty())card.addView(text(timeControl(item.timeControl),12,ACCENT));
            TextView details=text((item.date.isEmpty()?"Undated":item.date)+" · "+item.moves.size()+" half-moves"+(item.imported?" · Imported":""),12,MUTED); pad(details,0,14,0,6); card.addView(details);
            boolean hasVariation=prefs.getBoolean("exploring:"+item.id,false);
            int resume=hasVariation?prefs.getInt("variationPly:"+item.id,0):Math.min(item.moves.size(),prefs.getInt("ply:"+item.id,0));
            TextView action=text(hasVariation?"Resume variation · move "+((resume+1)/2)+"  →":resume>0?"Resume at move "+((resume+1)/2)+"  →":"Open game  →",13,ACCENT); action.setGravity(Gravity.END); card.addView(action);
            card.setOnClickListener(v->openGame(item,true)); card.setFocusable(true);
            card.setContentDescription(item.title()+", "+item.event+", "+item.round+", result "+displayResult(item.result)+". Open game.");
        }
        status.setText(getResources().getQuantityString(R.plurals.library_count,games.size(),count,games.size()));
        if(count==0) { TextView empty=text("No matching games. Try a player’s surname, or import a game pack.",16,MUTED); pad(empty,4,24,4,24); cards.addView(empty); }
    }

    private void openGame(GameRepository.Game selected,boolean restore) {
        stopAutoplay(); cancelAnalysis(); saveState(); game=selected; library=false;
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
        nav.addView(weight(button("Export",this::exportGame,false),1)); content.addView(nav);
        TextView title=text(game.title(),20,LIGHT); title.setTypeface(null,Typeface.BOLD); pad(title,2,14,2,4); content.addView(title);
        TextView event=text(game.event+" · "+game.round+" · "+displayResult(game.result)+(game.timeControl.isEmpty()?"":"\n"+timeControl(game.timeControl)),13,MUTED); pad(event,2,0,2,12); content.addView(event);
        board=new BoardView(this); board.setFlipped(flipped); board.setMoveListener(this::makeMove); content.addView(board,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout labels=row(); positionLabel=text("",13,LIGHT); turnLabel=text("",13,ACCENT); turnLabel.setGravity(Gravity.END);
        labels.addView(positionLabel,new LinearLayout.LayoutParams(0,-2,1)); labels.addView(turnLabel,new LinearLayout.LayoutParams(0,-2,1)); pad(labels,3,14,3,2); content.addView(labels);
        seek=new SeekBar(this); seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT)); seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT)); seek.setContentDescription("Game position, in half-moves");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){if(user)navigate(p);} public void onStartTrackingTouch(SeekBar b){stopAutoplay();} public void onStopTrackingTouch(SeekBar b){} }); content.addView(seek,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout controls=row(); controls.addView(weight(button("|‹",()->navigate(0),false),1)); controls.addView(weight(button("‹",()->navigate(ply-1),false),1));
        autoplayButton=button("▶",this::toggleAutoplay,true); controls.addView(weight(autoplayButton,1));
        controls.addView(weight(button("›",()->navigate(ply+1),false),1)); controls.addView(weight(button("›|",()->navigate(workingMoves.size()),false),1)); content.addView(controls);
        ((View)controls.getChildAt(0)).setContentDescription("First position"); ((View)controls.getChildAt(1)).setContentDescription("Previous move"); ((View)controls.getChildAt(3)).setContentDescription("Next move"); ((View)controls.getChildAt(4)).setContentDescription("Last position");
        LinearLayout tools=row(); analyzeButton=button("Analyze",()->{if(analysisFuture!=null)cancelAnalysis();else analyze();},true); tools.addView(weight(analyzeButton,1));
        tools.addView(weight(button("Flip board",()->{flipped=!flipped;board.setFlipped(flipped);saveState();},false),1)); pad(tools,0,10,0,0); content.addView(tools);
        exploreButton=button("Play from here",this::toggleExplore,false); content.addView(exploreButton);
        variationLabel=text("",13,ACCENT); pad(variationLabel,3,6,3,4); content.addView(variationLabel);
        analysisBox=column(); analysisBox.setBackground(shape(SURFACE,0,12)); pad(analysisBox,14,12,14,12);
        analysisText=text(analysisIntro(),13,MUTED);
        analysisBox.addView(analysisText); content.addView(analysisBox);
        TextView movesTitle=text("MOVE RECORD",12,MUTED); pad(movesTitle,3,20,3,8); content.addView(movesTitle);
        movesView=column(); content.addView(movesView);
        Button source=button(game.imported?"Game source / information":"View official game source",()->openSource(game.sourceUrl),false); content.addView(source);
        TextView note=text("ICCS coordinates · a0 is Red’s lower-left corner. Source results are recorded results; analysis variations do not change them.",12,MUTED); pad(note,3,8,3,18); content.addView(note);
    }

    private void updateReplay() {
        if(library||board==null)return;
        Position p=positions.get(ply); board.setPosition(p,ply==0?"":workingMoves.get(ply-1),exploring);
        positionLabel.setText(ply==0?"Starting position":"After "+((ply+1)/2)+" · "+(positions.get(ply-1).redToMove?"Red":"Black"));
        boolean check=p.isInCheck(p.redToMove); boolean terminal=p.legalMoves().isEmpty();
        turnLabel.setText(terminal?(p.redToMove?"Red":"Black")+" has no legal move":(p.redToMove?"Red":"Black")+" to move"+(check?" · check":""));
        seek.setMax(workingMoves.size()); seek.setProgress(ply);
        exploreButton.setText(exploring?"Return to recorded game":"Play from here");
        variationLabel.setText(exploring?"Exploring a variation · tap a piece, then a highlighted square.\nUse Legal moves for accessible move entry.":"Recorded game · "+ply+" / "+workingMoves.size()+" half-moves");
        variationLabel.setOnClickListener(exploring?v->legalMoves():null);
        variationLabel.setContentDescription(exploring?"Explore mode. Open legal moves list.":variationLabel.getText());
        renderMoves(); saveState();
    }

    private void renderMoves() {
        movesView.removeAllViews(); boolean startsRed=game.initial().redToMove;
        if(exploring) movesView.addView(button("Legal moves…",this::legalMoves,false));
        int slots=workingMoves.size()+(startsRed?0:1);
        for(int i=0;i<slots;i+=2) {
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

    private void navigate(int target) { stopAutoplay(); moveTo(target); }
    private void moveTo(int target) { cancelAnalysis(); ply=Math.max(0,Math.min(target,workingMoves.size())); updateReplay(); }
    private void toggleAutoplay() {
        if(autoplay){stopAutoplay();return;} if(ply>=workingMoves.size())moveTo(0);
        autoplay=true; autoplayButton.setText("Ⅱ"); autoplayButton.setContentDescription("Pause replay"); handler.postDelayed(playTick,1000);
    }
    private final Runnable playTick=new Runnable(){public void run(){if(!autoplay||library)return;if(ply>=workingMoves.size()){stopAutoplay();return;}moveTo(ply+1);if(autoplay)handler.postDelayed(this,1000);}};
    private void stopAutoplay() { autoplay=false; handler.removeCallbacks(playTick); if(autoplayButton!=null){autoplayButton.setText("▶");autoplayButton.setContentDescription("Play replay");} }

    private void toggleExplore() {
        stopAutoplay(); cancelAnalysis();
        if(exploring) {
            int target=Math.min(ply,game.moves.size()); exploring=false; workingMoves=new ArrayList<>(game.moves); rebuildPositions(); ply=target;
            prefs.edit().remove("variationMoves:"+game.id).remove("variationPly:"+game.id).putBoolean("exploring:"+game.id,false).apply();
        } else exploring=true;
        updateReplay();
    }
    private void makeMove(String move) {
        stopAutoplay(); cancelAnalysis();
        try {
            Position next=positions.get(ply).play(move); exploring=true;
            workingMoves=new ArrayList<>(workingMoves.subList(0,ply)); workingMoves.add(move);
            while(positions.size()>ply+1)positions.remove(positions.size()-1); positions.add(next); ply++; updateReplay();
        } catch(RuntimeException e){error("Move could not be played",message(e));}
    }
    private void legalMoves() {
        if(!exploring)return; List<String> moves=positions.get(ply).legalMoves();
        if(moves.isEmpty()){toast("No legal moves in this position.");return;}
        String[] labels=new String[moves.size()];for(int i=0;i<labels.length;i++)labels[i]=pretty(moves.get(i));
        dialog("Choose a legal move").setItems(labels,(d,n)->makeMove(moves.get(n))).setNegativeButton("Cancel",null).show();
    }

    private void analyze() {
        stopAutoplay(); cancelAnalysis(); final int generation=analysisGeneration;
        final Position snapshot=positions.get(ply).copy(); analyzeButton.setText(R.string.stop_analysis);
        final List<String> history=new ArrayList<>(workingMoves.subList(0,ply));
        final String startFen=game.startFen;
        final boolean nativeEngine=PikafishEngine.available(this);
        final String engineName=nativeEngine?"Pikafish":"Built-in analysis";
        analysisText.setText(getString(R.string.analysis_searching,engineName));
        analysisFuture=engine.submit(()->{
            try {
                Engine.Result result=nativeEngine?PikafishEngine.analyze(this,snapshot,startFen,history,30,3000):Engine.analyze(snapshot,6,3000);
                runOnUiThread(()->{
                    if(destroyed||generation!=analysisGeneration||library)return;
                    analysisFuture=null; analyzeButton.setText(R.string.analyze); lastResult=result;
                    String score=String.format(Locale.getDefault(),"%+.2f",result.score/100.0);
                    StringBuilder pv=new StringBuilder();for(int i=0;i<Math.min(result.pv.size(),8);i++){if(i>0)pv.append("  ");pv.append(pretty(result.pv.get(i)));}
                    analysisText.setText(getString(R.string.analysis_result,engineName,
                        Math.abs(result.score)>=29000?getString(R.string.analysis_winning,result.score>0?"Red":"Black"):score,
                        result.depth,java.text.NumberFormat.getIntegerInstance().format(result.nodes),
                        result.bestMove==null||result.bestMove.isEmpty()?getString(R.string.analysis_no_move):getString(R.string.analysis_suggested,pretty(result.bestMove)),
                        pv.toString(),getString(nativeEngine?R.string.analysis_native_limit:R.string.analysis_builtin_limit)));
                    board.setHint(result.bestMove);
                    while(analysisBox.getChildCount()>1)analysisBox.removeViewAt(1);
                    if(result.bestMove!=null&&!result.bestMove.isEmpty()&&snapshot.legalMoves().contains(result.bestMove))analysisBox.addView(button("Explore suggested move",()->makeMove(result.bestMove),false));
                });
            } catch(Exception e){runOnUiThread(()->{if(!destroyed&&generation==analysisGeneration&&!library){analysisFuture=null;analyzeButton.setText(R.string.analyze);analysisText.setText(getString(R.string.analysis_error,engineName,message(e)));}});}
        });
    }
    private void cancelAnalysis() {
        analysisGeneration++; if(analysisFuture!=null){analysisFuture.cancel(true);analysisFuture=null;}
        lastResult=null;
        if(analyzeButton!=null)analyzeButton.setText(R.string.analyze);
        if(board!=null)board.setHint("");
        if(analysisText!=null)analysisText.setText(analysisIntro());
        if(analysisBox!=null)while(analysisBox.getChildCount()>1)analysisBox.removeViewAt(1);
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
        toast("Reading game file…");io.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("The selected file could not be opened.");String text=GameRepository.read(in);runOnUiThread(()->{if(!destroyed)importText(text);});}catch(Exception e){runOnUiThread(()->{if(!destroyed)error("Could not read game file",message(e));});}});
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
    private void handleIntent(Intent intent){if(intent==null)return;Uri uri=intent.getData();if(Intent.ACTION_SEND.equals(intent.getAction()))uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(uri!=null&&(Intent.ACTION_VIEW.equals(intent.getAction())||Intent.ACTION_SEND.equals(intent.getAction()))){importUri(uri);setIntent(new Intent(this,MainActivity.class));}}

    private void about() {
        dialog("Xiangqi Study").setMessage("An offline game library, replay board and analysis workspace. Your imported games and study positions stay on this device.\n\nThe starter games are downloaded from the World Xiangqi Federation’s official 2025 World Championship game records. Each game links back to its source. The downloader and attribution notes are included in the project.\n\n"+(PikafishEngine.available(this)?"Pikafish is bundled for this device and runs offline. See the project’s engine provenance and GPL-3.0 source notices.":"This device uses the lightweight built-in classical engine. It is useful for exploring moves but cannot promise expert accuracy.")+" The replay board does not adjudicate repetition / perpetual-chase tournament rules.\n\nImport ICCS PGN or JSON. Use Export to back up a game or your current variation; uninstalling the app removes private imports and saved positions.")
            .setPositiveButton("Official WXF archive",(d,w)->openSource("https://www.wxf-xiangqi.org/index.php?Itemid=313&id=218&lang=en&option=com_content&view=article"))
            .setNegativeButton("Close",null).setNeutralButton("Engine notices",(d,w)->engineNotices()).show();
    }
    private void engineNotices(){dialog("Pikafish notices").setMessage("Pikafish 2026-09-06\nA free UCI Xiangqi engine derived from Stockfish. Copyright the Pikafish and Stockfish contributors.\n\nEngine code: GNU General Public License version 3. The exact upstream release source and license are available from the links below and documented with this project.\n\nThe bundled pikafish.nnue weights have a separate upstream NNUE license. Commercial use requires the upstream rights holder’s permission. This build is intended for personal study.\n\nThe built-in fallback engine and replay board are part of Xiangqi Study; the engine name shown on the board identifies the engine actually used.")
        .setPositiveButton("Upstream source",(d,w)->openSource("https://github.com/official-pikafish/Pikafish/tree/Pikafish-2026-09-06"))
        .setNeutralButton("GPL license",(d,w)->openSource("https://github.com/official-pikafish/Pikafish/blob/Pikafish-2026-09-06/Copying.txt"))
        .setNegativeButton("Close",null).show();}
    private void openSource(String url) {
        if(url==null||url.isEmpty()){toast("No source link is recorded for this imported game.");return;}
        Uri uri=Uri.parse(url);if(!"https".equalsIgnoreCase(uri.getScheme())&&!"http".equalsIgnoreCase(uri.getScheme())){error("Source information",url);return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){error("Source link",url);}
    }
    private GameRepository.Game find(String id){for(GameRepository.Game g:games)if(g.id.equals(id))return g;return null;}
    private String analysisIntro(){return PikafishEngine.available(this)?"Pikafish · offline analysis\nTap Analyze for an evaluation and suggested continuation. All analysis runs on your device.":"Built-in analysis · offline\nTap Analyze for a suggested move and evaluation. This lightweight engine is for study; its evaluation is not grandmaster strength.";}
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
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);saveState();out.putString("pendingExport",pendingExport);}
    @Override protected void onRestoreInstanceState(Bundle in){super.onRestoreInstanceState(in);pendingExport=in.getString("pendingExport");}
    @Override protected void onStop(){super.onStop();stopAutoplay();cancelAnalysis();saveState();}
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);engine.shutdownNow();io.shutdown();super.onDestroy();}
    @Override public void onBackPressed(){if(!library)showLibrary();else super.onBackPressed();}

    private AlertDialog.Builder dialog(String title){return new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert).setTitle(title);}
    private void error(String title,String message){dialog(title).setMessage(message).setPositiveButton("OK",null).show();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private static String message(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private static String displayResult(String result){return "1/2-1/2".equals(result)?"½–½":result.replace('-','–');}
    private static String pretty(String move){return move.length()==4?move.substring(0,2)+"–"+move.substring(2):move;}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    private GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private Button button(String label,Runnable action,boolean primary){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(14);b.setTextColor(primary?INK:LIGHT);b.setBackground(shape(primary?ACCENT:SURFACE,0,12));b.setMinimumHeight(dp(48));b.setMinHeight(dp(48));b.setPadding(dp(10),dp(6),dp(10),dp(6));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(2),dp(3),dp(2),dp(3));b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;}
    private View weight(View v,float value){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),value);p.setMargins(dp(2),dp(3),dp(2),dp(3));v.setLayoutParams(p);return v;}
    private void pad(View view,int l,int t,int r,int b){view.setPadding(dp(l),dp(t),dp(r),dp(b));}
}
