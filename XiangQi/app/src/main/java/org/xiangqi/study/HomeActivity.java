package org.xiangqi.study;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.xiangqi.study.core.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

/** Play / Database / Options. Recorded source games keep the established study viewer. */
public final class HomeActivity extends Activity {
    public static final String REVIEW_ID="personalReviewId",SOURCE_ID="sourceGameId",FROM_HOME="fromHome",START_FEN="playStartFen",SOURCE_TITLE="playSourceTitle",OPEN_IMPORT="openImport";
    private static final int BG=0xff171f19,SURFACE=0xff263128,LINE=0xff39473b,LIGHT=0xfff0f0e5,MUTED=0xffb4c2b1,ACCENT=0xffbed5a7,INK=0xff203322;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->new Thread(r,"xiangqi-personal-storage"));
    private final ExecutorService work=Executors.newSingleThreadExecutor(r->new Thread(r,"xiangqi-computer")),searcher=Executors.newSingleThreadExecutor(r->new Thread(r,"xiangqi-database-search"));
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;private ComputerGameStore store;private GameRepository repository;private LibrarySearchIndex sourceIndex;
    private LinearLayout root,page,recordPanel;private BoardView playBoard;private TextView recordText,topPlayer,bottomPlayer;private Button undoButton,hintButton;
    private ComputerGameStore.SavedGame current;private final List<Position> positions=new ArrayList<>();
    private List<GameRepository.Game> sources=new ArrayList<>();private List<ComputerGameStore.SavedGame> personal=new ArrayList<>();
    private Future<?> task,searchTask;private int generation,searchGeneration;private boolean foreground,destroyed,ready,playing,setup,computing,hinting,flip,returnToSetup,resultScreen;
    private long importVersion;
    private boolean databaseScrollReset;
    private String screen="play",collection="personal",query="",order="newest",importId="",side="Red",startFen=Position.START_FEN,sourceTitle="",hintMove="",deletedId="";
    private ListView gameList;private TextView empty;private Rows adapter;private AlertDialog dialog,colorDialog;
    private final Runnable filterRequest=this::filterGames;
    private final Runnable reviewRefresh=new Runnable(){public void run(){if(foreground&&"database".equals(screen)&&"personal".equals(collection)){filterGames();reviewCompleted();handler.postDelayed(this,2500);}}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("home",MODE_PRIVATE);store=ComputerGameStore.get(this);repository=new GameRepository(this);
        SharedPreferences appearance=getSharedPreferences(StudySettings.PREFERENCES,MODE_PRIVATE);if(!appearance.contains("board")&&!appearance.contains("boardColor"))StudySettings.selectBoardPreset(this,"warm-maple");
        screen=prefs.getString("screen","play");collection=prefs.getString("collection","personal");importId=prefs.getString("importId","");loadScope();
        if(state!=null){playing=state.getBoolean("playing");setup=state.getBoolean("setup");side=state.getString("side","Red");startFen=state.getString("startFen",Position.START_FEN);sourceTitle=state.getString("sourceTitle","");}
        if(getIntent().hasExtra(START_FEN)){startFen=getIntent().getStringExtra(START_FEN);sourceTitle=getIntent().getStringExtra(SOURCE_TITLE);if(sourceTitle==null)sourceTitle="";Position.fromFen(startFen);screen="play";setup=true;}
        String requested=getIntent().getStringExtra("homeScreen");if(requested!=null)screen=requested;
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);root=col();root.setFocusableInTouchMode(true);root.setBackgroundColor(BG);root.setPadding(dp(12),dp(8),dp(12),dp(5));setContentView(root);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());root.setPadding(dp(12)+b.left,dp(8)+b.top,dp(12)+b.right,dp(5)+b.bottom);return i;});}
        root.addView(text("Opening Xiangqi…",18,MUTED));final String restore=state==null?prefs.getString("activeGame",""):state.getString("activeGame","");
        IO.execute(()->{try{
            List<GameRepository.Game> loaded=repository.load();LibrarySearchIndex index=new LibrarySearchIndex(loaded);List<ComputerGameStore.SavedGame> saved=store.list();
            ComputerGameStore.SavedGame active=restore.isEmpty()?null:store.load(restore);
            runOnUiThread(()->{if(destroyed)return;sources=loaded;sourceIndex=index;personal=saved;current=active;pickResume();flip=state!=null?state.getBoolean("flip",current!=null&&!current.humanRed):current!=null&&!current.humanRed;importVersion=importVersion();ready=true;showScreen(screen);});
        }catch(Exception e){runOnUiThread(()->{if(!destroyed)failure("Could not open database",e);});}});
    }
    private void pickResume(){if(current!=null&&!current.finished())return;current=null;for(ComputerGameStore.SavedGame g:personal)if(!g.finished()){current=g;break;}}
    private void refreshPersonal(){IO.execute(()->{try{List<ComputerGameStore.SavedGame> data=store.list();runOnUiThread(()->{if(destroyed)return;personal=data;if(!playing&&!resultScreen){pickResume();if(ready&&"play".equals(screen)&&!setup)showScreen("play");}if("database".equals(screen))filterGames();});}catch(Exception e){report(e);}});}
    private long importVersion(){return new java.io.File(getFilesDir(),"imported-games.json").lastModified();}
    private void refreshSources(){IO.execute(()->{try{List<GameRepository.Game> data=repository.load();LibrarySearchIndex index=new LibrarySearchIndex(data);runOnUiThread(()->{if(destroyed)return;sources=data;sourceIndex=index;importVersion=importVersion();collection=prefs.getString("collection",collection);importId=prefs.getString("importId",importId);loadScope();if("database".equals(screen))showScreen("database");});}catch(Exception e){report(e);}});}
    private void showScreen(String name){
        if(!ready)return;rememberDatabaseScroll();cancelTask();ComputerReview.pause();handler.removeCallbacks(reviewRefresh);screen=name;prefs.edit().putString("screen",name).apply();
        android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(root.getWindowToken(),0);
        root.removeAllViews();playBoard=null;page=col();root.addView(header(),new LinearLayout.LayoutParams(-1,dp(62)));root.addView(page,new LinearLayout.LayoutParams(-1,0,1));root.addView(navigation(),new LinearLayout.LayoutParams(-1,dp(64)));
        if("database".equals(name)){buildDatabase();handler.postDelayed(reviewRefresh,2500);reviewCompleted();}
        else if("options".equals(name))buildOptions();else if(setup)buildSetup();else if(resultScreen&&current!=null&&current.finished())buildResult();else if(playing&&current!=null&&!current.finished()){rebuild();buildPlaying();resumeComputer();}else buildPlayHub();
        root.requestFocus();
    }
    private View header(){LinearLayout row=row();TextView logo=text("弈",27,ACCENT);logo.setGravity(Gravity.CENTER);logo.setBackground(shape(SURFACE,LINE,12));row.addView(logo,new LinearLayout.LayoutParams(dp(39),dp(39)));
        LinearLayout brand=col();brand.setPadding(dp(10),0,0,0);TextView title=text("Xiangqi",19,LIGHT);title.setTypeface(null,Typeface.BOLD);brand.addView(title);brand.addView(text("PLAY  ·  STUDY  ·  IMPROVE",10,MUTED));row.addView(brand,new LinearLayout.LayoutParams(0,-2,1));return row;}
    private View navigation(){LinearLayout nav=row();nav.setBackgroundColor(BG);for(String item:new String[]{"play","database","options"}){Button b=button(item.equals("play")?"Play":item.equals("database")?"Database":"Options",()->{returnToSetup=false;showScreen(item);},false);b.setTextSize(11);b.setSelected(item.equals(screen));b.setBackground(shape(item.equals(screen)?SURFACE:BG,0,14));b.setTextColor(item.equals(screen)?ACCENT:MUTED);b.setCompoundDrawablesWithIntrinsicBounds(null,new AppIcon(item,item.equals(screen)?ACCENT:MUTED,dp(21)),null,null);b.setCompoundDrawablePadding(dp(4));b.setContentDescription(item.equals("play")?"Play tab":item.equals("database")?"Database tab":"Options tab");nav.addView(b,new LinearLayout.LayoutParams(0,dp(58),1));}return nav;}
    private void buildPlayHub(){
        ScrollView scroll=new ScrollView(this);LinearLayout body=col();scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        TextView heading=text("Your next\nmove awaits.",35,LIGHT);heading.setTypeface(null,Typeface.BOLD);pad(heading,2,22,0,12);body.addView(heading);TextView intro=text("Play at your pace. Review the better moves afterward.",14,MUTED);pad(intro,2,0,20,24);body.addView(intro);
        LinearLayout start=panel();TextView name=text("Play "+engineName(),20,LIGHT);start.addView(name);TextView level=text("Level "+level()+" · "+ComputerDifficulty.label(level())+" · Offline",13,MUTED);pad(level,0,7,0,15);start.addView(level);
        start.addView(button("New game  →",()->{startFen=Position.START_FEN;sourceTitle="";setup=true;showScreen("play");},true));body.addView(start);
        if(current!=null&&!current.finished()){
            section(body,"YOUR SAVED GAME");LinearLayout resume=row();BoardView mini=board();mini.setPosition(current.position(),"",false);mini.setFlipped(!current.humanRed);resume.addView(mini,new LinearLayout.LayoutParams(dp(126),-2));
            LinearLayout info=col();pad(info,14,0,0,0);info.addView(text("You vs "+engineName(),16,LIGHT));TextView meta=text((current.humanRed?"Red":"Black")+" · Level "+current.level+"\nMove "+((current.moves.size()+1)/2),13,MUTED);pad(meta,0,6,0,9);info.addView(meta);info.addView(button("Resume",()->resume(current.id),false));resume.addView(info,new LinearLayout.LayoutParams(0,-2,1));body.addView(resume);
        }
    }
    private void buildSetup(){
        ScrollView scroll=new ScrollView(this);LinearLayout body=col();scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        heading(body,"New game");body.addView(text("Choose your side",19,LIGHT));LinearLayout choices=row();for(String value:new String[]{"Red","Black","Random"})choices.addView(button(value,()->{side=value;showScreen("play");},side.equals(value)),new LinearLayout.LayoutParams(0,dp(50),1));body.addView(choices);
        section(body,"COMPUTER DIFFICULTY");LinearLayout difficulty=row();difficulty.addView(text("Level "+level()+" · "+ComputerDifficulty.label(level()),17,LIGHT),new LinearLayout.LayoutParams(0,-2,1));difficulty.addView(button("Change",()->{returnToSetup=true;showScreen("options");},false));body.addView(difficulty);
        section(body,"STARTING POSITION");body.addView(text(sourceTitle.isEmpty()?"Standard starting position":sourceTitle,14,MUTED));
        TextView summary=text("Untimed · Post-game review at maximum strength",13,MUTED);pad(summary,0,20,0,20);body.addView(summary);
        body.addView(button("Start game",this::startGame,true));body.addView(button("Cancel",()->{setup=false;showScreen("play");},false));
    }
    private void startGame(){
        cancelTask();ComputerGameStore.SavedGame saved=new ComputerGameStore.SavedGame();saved.level=level();saved.humanRed="Random".equals(side)?new Random().nextBoolean():"Red".equals(side);saved.startFen=startFen;saved.source=sourceTitle;
        current=saved;playing=true;setup=false;resultScreen=false;flip=!saved.humanRed;prefs.edit().putString("activeGame",saved.id).apply();rebuild();persist(this::reviewWhileThinking);showScreen("play");checkEnd();
    }
    private void resume(String id){cancelTask();final int token=generation;IO.execute(()->{try{ComputerGameStore.SavedGame saved=store.load(id);runOnUiThread(()->{if(destroyed||token!=generation||saved==null)return;current=saved;if(saved.finished()){openReview(saved.id);return;}playing=true;setup=false;resultScreen=false;flip=!saved.humanRed;prefs.edit().putString("activeGame",id).apply();showScreen("play");});}catch(Exception e){report(e);}});}
    private void rebuild(){positions.clear();Position p=Position.fromFen(current.startFen);positions.add(p);for(String move:current.moves){p=p.play(move);positions.add(p);}}
    private Position position(){return positions.get(positions.size()-1);}
    private void buildPlaying(){
        LinearLayout top=row();Button back=button("‹",()->{playing=false;showScreen("play");},false);back.setContentDescription("Back to Play");top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(42)));TextView title=text("You vs "+engineName(),16,LIGHT);title.setGravity(Gravity.CENTER);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button menu=button("⋮",()->{},false);menu.setContentDescription("Playing game menu");menu.setOnClickListener(v->{PopupMenu p=new PopupMenu(this,menu);p.getMenu().add("Flip board").setOnMenuItemClickListener(i->{flip=!flip;renderPosition();return true;});p.show();});top.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(42)));page.addView(top);
        PlayBoardLayout stack=new PlayBoardLayout();page.addView(stack,new LinearLayout.LayoutParams(-1,0,1));
        topPlayer=text("",13,LIGHT);pad(topPlayer,4,7,4,7);stack.addView(topPlayer);playBoard=board();playBoard.setContentDescription("Playing Xiangqi board");playBoard.setMoveListener(this::humanMove);stack.addView(playBoard,new LinearLayout.LayoutParams(-1,-2));
        bottomPlayer=text("",13,LIGHT);pad(bottomPlayer,4,7,4,7);stack.addView(bottomPlayer);
        LinearLayout actions=row();undoButton=action("Undo","undo",this::undoMove);actions.addView(undoButton,new LinearLayout.LayoutParams(0,dp(49),1));actions.addView(action("Surrender","surrender",this::surrender),new LinearLayout.LayoutParams(0,dp(49),1));hintButton=action("Hint","hint",this::hint);hintButton.setContentDescription("Hint: maximum strength, 2-second search");actions.addView(hintButton,new LinearLayout.LayoutParams(0,dp(49),1));stack.addView(actions);
        recordPanel=col();section(recordPanel,"MOVES · RED / BLACK");ScrollView records=new ScrollView(this);recordText=text("",12,MUTED);recordText.setContentDescription("Playing move record");records.addView(recordText);recordPanel.addView(records,new LinearLayout.LayoutParams(-1,0,1));stack.addView(recordPanel,new LinearLayout.LayoutParams(-1,0,1));renderPosition();
    }
    private final class PlayBoardLayout extends LinearLayout {
        PlayBoardLayout(){super(HomeActivity.this);setOrientation(VERTICAL);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            if(playBoard!=null&&playBoard.getParent()==this){int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec),fixed=dp(49+64+88);int boardWidth=Math.max(0,Math.min(width,(int)((height-fixed)/1.10f)));LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)playBoard.getLayoutParams();p.width=boardWidth;p.gravity=Gravity.CENTER_HORIZONTAL;}
            super.onMeasure(widthSpec,heightSpec);
        }
    }
    private void renderPosition(){
        if(playBoard==null||current==null||!playing||positions.isEmpty())return;Position p=position();
        playBoard.setFlipped(flip);playBoard.setPosition(p,current.moves.isEmpty()?"":current.moves.get(current.moves.size()-1),p.redToMove==current.humanRed&&!computing&&!hinting);
        playBoard.setHint(hintMove);boolean topRed=flip;topPlayer.setText((topRed?"RED  ·  ":"BLACK  ·  ")+(topRed==current.humanRed?"You":engineName()+"  ·  Level "+current.level));bottomPlayer.setText((topRed?"BLACK  ·  ":"RED  ·  ")+(topRed!=current.humanRed?"You":engineName()+"  ·  Level "+current.level));
        topPlayer.setTextColor(topRed?0xfff0a69c:LIGHT);bottomPlayer.setTextColor(topRed?LIGHT:0xfff0a69c);undoButton.setEnabled(lastHumanMove()>=0);hintButton.setEnabled(!computing&&!hinting&&p.redToMove==current.humanRed);hintButton.setText(hinting?"Thinking…":"Hint");
        StringBuilder moves=new StringBuilder();for(int i=0;i<current.moves.size();i+=2){moves.append(i/2+1).append(".  ").append(pretty(current.moves.get(i)));if(i+1<current.moves.size())moves.append("       ").append(pretty(current.moves.get(i+1)));moves.append('\n');}recordText.setText(moves.toString());
    }
    private int lastHumanMove(){if(current==null)return -1;boolean starts=Position.fromFen(current.startFen).redToMove;for(int i=current.moves.size()-1;i>=0;i--)if((i%2==0?starts:!starts)==current.humanRed)return i;return -1;}
    private void humanMove(String move){if(current==null||!playing||computing||hinting||position().redToMove!=current.humanRed)return;try{position().play(move);append(move);}catch(IllegalArgumentException ignored){/* Board selection can outlive a cancelled gesture. */}}
    private void append(String move){Position next=position().play(move);current.moves.add(move);positions.add(next);current.updated=System.currentTimeMillis();hintMove="";persist(this::reviewWhileThinking);renderPosition();if(!checkEnd())resumeComputer();}
    private void undoMove(){
        int index=lastHumanMove();if(index<0||current.finished())return;cancelTask();ComputerReview.pause();while(current.moves.size()>index)current.moves.remove(current.moves.size()-1);current.hints.removeIf(p->p>=index);current.revision++;current.updated=System.currentTimeMillis();hintMove="";rebuild();persist(this::reviewWhileThinking);renderPosition();
    }
    private void surrender(){if(current==null||current.finished())return;dialog=new AlertDialog.Builder(this).setTitle("Surrender this game?").setMessage("The game will be saved in My Games for review.").setNegativeButton("Keep playing",null).setPositiveButton("Surrender",(d,w)->finishGame(current.humanRed?"0-1":"1-0","Surrender")).show();}
    private boolean checkEnd(){
        Position p=position();if(p.legalMoves().isEmpty()){finishGame(p.redToMove?"0-1":"1-0",p.isInCheck(p.redToMove)?"Checkmate":"No legal moves");return true;}
        String key=key(p);List<Integer> repeats=new ArrayList<>();for(int i=0;i<positions.size();i++)if(key(positions.get(i)).equals(key))repeats.add(i);
        if(repeats.size()>=3){int from=repeats.get(repeats.size()-3);boolean redChecks=true,blackChecks=true;for(int i=from+1;i<positions.size();i++){Position after=positions.get(i);if(after.redToMove)blackChecks&=after.isInCheck(true);else redChecks&=after.isInCheck(false);}if(redChecks!=blackChecks)finishGame(redChecks?"0-1":"1-0","Perpetual check");else finishGame("1/2-1/2","Threefold repetition (casual play)");return true;}
        if(current.moves.size()>=2000){finishGame("1/2-1/2","Move limit");return true;}return false;
    }
    private static String key(Position p){String[] f=p.toFen().split(" ");return f[0]+" "+f[1];}
    private void finishGame(String result,String reason){cancelTask();ComputerReview.pause();current.result=result;current.reason=reason;current.updated=System.currentTimeMillis();playing=false;resultScreen=true;persist(()->{if(foreground)ComputerReview.start(this,current.id);});buildResult();refreshPersonal();}
    private void buildResult(){page.removeAllViews();playBoard=null;heading(page,"Game finished");page.addView(text(current.result.equals("1/2-1/2")?"Draw":current.result.equals("1-0")?"Red wins":"Black wins",28,LIGHT));TextView reason=text(current.reason,15,MUTED);pad(reason,0,8,0,25);page.addView(reason);page.addView(button("My Games",()->{collection="personal";loadScope();query="";order="newest";databaseScrollReset=true;resultScreen=false;showScreen("database");},true));page.addView(button("New game",()->{setup=true;startFen=Position.START_FEN;sourceTitle="";showScreen("play");},false));}
    private void resumeComputer(){
        if(!foreground||!playing||current==null||current.finished()||!"play".equals(screen)||computing||hinting)return;
        if(position().redToMove==current.humanRed){reviewWhileThinking();return;}
        ComputerReview.pause();cancelTask();computing=true;renderPosition();final int token=generation;final ComputerGameStore.SavedGame saved=current.copy();final Position p=position().copy();
        task=work.submit(()->{try{
            Engine.Result result;
            if(PikafishEngine.available(this)){PikafishEngine.Options max=EngineSettings.load(this);result=PikafishEngine.analyze(this,p,saved.startFen,saved.moves,new PikafishEngine.Options(ComputerDifficulty.depth(saved.level),ComputerDifficulty.millis(saved.level),max.threads,max.hashMb,saved.level==100?1:5,ComputerDifficulty.nodes(saved.level)),null);}
            else result=Engine.analyze(p,Math.min(8,ComputerDifficulty.depth(saved.level)),ComputerDifficulty.millis(saved.level),Math.min(5,saved.level==100?1:3));
            String move=ComputerDifficulty.choose(result,p,saved.level,saved.id.hashCode()*31L+saved.moves.size());if(!p.legalMoves().contains(move))throw new IllegalStateException("Computer returned an illegal move");
            runOnUiThread(()->{if(!valid(token,saved))return;task=null;computing=false;append(move);});
        }catch(Exception e){runOnUiThread(()->{if(valid(token,saved)){task=null;computing=false;renderPosition();engineFailure(e);}});}});
    }
    private void hint(){
        if(current==null||computing||hinting||!playing||position().redToMove!=current.humanRed)return;ComputerReview.pause();cancelTask();hinting=true;hintMove="";renderPosition();final int token=generation;final ComputerGameStore.SavedGame saved=current.copy();final Position p=position().copy();
        task=work.submit(()->{try{
            Engine.Result result=PikafishEngine.available(this)?PikafishEngine.analyze(this,p,saved.startFen,saved.moves,EngineSettings.timed(this,2000,1),null):Engine.analyze(p,64,2000);
            if(!p.legalMoves().contains(result.bestMove))throw new IllegalStateException("No legal hint found");
            runOnUiThread(()->{if(!valid(token,saved))return;task=null;hinting=false;hintMove=result.bestMove;current.hints.add(current.moves.size());current.updated=System.currentTimeMillis();persist(this::reviewWhileThinking);renderPosition();});
        }catch(Exception e){runOnUiThread(()->{if(valid(token,saved)){task=null;hinting=false;renderPosition();failure("Hint unavailable",e);}});}});
    }
    private boolean valid(int token,ComputerGameStore.SavedGame saved){return !destroyed&&foreground&&playing&&"play".equals(screen)&&generation==token&&current!=null&&current.id.equals(saved.id)&&current.revision==saved.revision&&current.moves.size()==saved.moves.size();}
    private void engineFailure(Exception e){dialog=new AlertDialog.Builder(this).setTitle("Computer paused").setMessage(e.getMessage()+"\nYour game can be resumed.").setNegativeButton("Back to Play",(d,w)->{playing=false;showScreen("play");}).setPositiveButton("Retry",(d,w)->resumeComputer()).show();}
    private void reviewWhileThinking(){if(foreground&&playing&&!computing&&!hinting&&current!=null&&!current.finished()&&"play".equals(screen)&&position().redToMove==current.humanRed)ComputerReview.start(this,current.id);}
    private void reviewCompleted(){if(!foreground)return;final List<ComputerGameStore.SavedGame> snapshot=new ArrayList<>(personal);IO.execute(()->{for(ComputerGameStore.SavedGame g:snapshot)if(g.finished()&&store.analyzed(g.id)<g.moves.size()+1){runOnUiThread(()->{if(!destroyed&&foreground&&"database".equals(screen))ComputerReview.start(this,g.id);});break;}});}
    private void persist(Runnable after){final ComputerGameStore.SavedGame snapshot=current.copy();IO.execute(()->{try{store.save(snapshot);if(after!=null)runOnUiThread(()->{if(!destroyed&&current!=null&&current.id.equals(snapshot.id)&&current.revision==snapshot.revision)after.run();});}catch(Exception e){runOnUiThread(()->{if(!destroyed){cancelTask();failure("Could not save game",e);}});}});}
    private void cancelTask(){generation++;if(task!=null){task.cancel(true);task=null;}computing=false;hinting=false;hintMove="";}

    private static final class Entry {GameRepository.Game source;ComputerGameStore.SavedGame saved;String status;Entry(GameRepository.Game g){source=g;}Entry(ComputerGameStore.SavedGame g,String s){saved=g;status=s;}}
    private void buildDatabase(){
        LinearLayout title=row();title.addView(text("Database",28,LIGHT),new LinearLayout.LayoutParams(0,-2,1));page.addView(title);
        LinearLayout tools=row();String last=getSharedPreferences("study",MODE_PRIVATE).getString("lastGame","");boolean returnable=false,personalReview=false;
        for(GameRepository.Game g:sources)if(g.id.equals(last)){returnable=true;break;}for(ComputerGameStore.SavedGame g:personal)if(g.id.equals(last)&&g.finished()){returnable=true;personalReview=true;break;}
        if(returnable){final boolean own=personalReview;Button back=button("Return to current game",()->{if(own)openReview(last);else openSourceGame(last);},false);back.setTextSize(12);tools.addView(back,new LinearLayout.LayoutParams(0,dp(44),1));}
        Button imports=action("Import games","import",()->startActivity(new Intent(this,MainActivity.class).putExtra(FROM_HOME,true).putExtra(OPEN_IMPORT,true)));imports.setTextSize(12);tools.addView(imports,new LinearLayout.LayoutParams(0,dp(44),1));page.addView(tools);
        LinearLayout tabs=row();String[] ids={"personal","human","engine","imports"},names={"My Games","Human","Engine","Imports"};
        for(int i=0;i<ids.length;i++){String id=ids[i];Button b=button(names[i],()->{saveScope();collection=id;loadScope();showScreen("database");},collection.equals(id));b.setTextSize(11);b.setSelected(collection.equals(id));b.setPadding(dp(2),0,dp(2),0);b.setContentDescription(names[i]+" collection");tabs.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));}page.addView(tabs);
        if("imports".equals(collection)){List<GameRepository.ImportedCollection> groups=repository.importedCollections();if(!groups.isEmpty()){String[] labels=new String[groups.size()];int selected=0;for(int i=0;i<groups.size();i++){labels[i]=groups.get(i).name+" · "+groups.get(i).gameCount;if(groups.get(i).id.equals(importId))selected=i;}if(importId.isEmpty())importId=groups.get(0).id;Spinner picker=spinner(labels,selected);picker.setContentDescription("Imported collection");page.addView(picker);picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){String value=groups.get(pos).id;if(!value.equals(importId)){saveScope();importId=value;loadScope();showScreen("database");}}public void onNothingSelected(AdapterView<?> p){}});}}
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setTextColor(LIGHT);search.setHintTextColor(MUTED);search.setHint("Search players or events");search.setContentDescription("Database search");search.setBackground(shape(SURFACE,LINE,12));pad(search,12,6,12,6);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(46));sp.setMargins(0,dp(12),0,dp(6));page.addView(search,sp);search.setText(query);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int b,int c){query=s.toString();databaseScrollReset=true;handler.removeCallbacks(filterRequest);handler.postDelayed(filterRequest,150);}});
        LinearLayout sort=row();sort.addView(text("personal".equals(collection)?"Your played games":"Game collection",12,MUTED),new LinearLayout.LayoutParams(0,-2,1));Spinner picker=spinner(new String[]{"Newest first","Oldest first","Collection order"},"oldest".equals(order)?1:"collection".equals(order)?2:0);picker.setContentDescription("Database sort order");sort.addView(picker,new LinearLayout.LayoutParams(dp(155),dp(40)));page.addView(sort);picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int i,long id){String value=new String[]{"newest","oldest","collection"}[i];if(!value.equals(order)){order=value;databaseScrollReset=true;filterGames();}}public void onNothingSelected(AdapterView<?> p){}});
        empty=text("Loading games…",14,MUTED);pad(empty,4,15,4,15);page.addView(empty);gameList=new ListView(this);gameList.setTag("scroll:"+collection+":"+importId);gameList.setItemsCanFocus(true);gameList.setContentDescription("Database games");gameList.setDivider(new android.graphics.drawable.ColorDrawable(LINE));gameList.setDividerHeight(dp(1));adapter=new Rows();gameList.setAdapter(adapter);page.addView(gameList,new LinearLayout.LayoutParams(-1,0,1));
        if(!deletedId.isEmpty()){LinearLayout undo=row();undo.addView(text("Game deleted",13,MUTED),new LinearLayout.LayoutParams(0,-2,1));undo.addView(button("Undo",()->{String id=deletedId;deletedId="";IO.execute(()->{store.setDeleted(id,false);refreshPersonal();runOnUiThread(()->{if(!destroyed)showScreen("database");});});},false));page.addView(undo);}
        filterGames();
    }
    private void filterGames(){
        if(!ready||destroyed||!foreground||!"database".equals(screen)||adapter==null)return;saveScope();if(searchTask!=null)searchTask.cancel(true);final int token=++searchGeneration;final String tab=collection,needle=query,ordering=order,group=importId;final Rows target=adapter;final ListView list=gameList;
        String scroll=String.valueOf(list.getTag());int position=list.getChildCount()>0?list.getFirstVisiblePosition():prefs.getInt(scroll,0);View first=list.getChildAt(0);int offset=first==null?prefs.getInt(scroll+":top",0):first.getTop();final int restorePosition=databaseScrollReset?0:position,restoreTop=databaseScrollReset?0:offset;
        searchTask=searcher.submit(()->{try{
            List<Entry> rows=new ArrayList<>();if("personal".equals(tab)){
                List<ComputerGameStore.SavedGame> saved=store.list();if(!"collection".equals(ordering))saved.sort((a,b)->"oldest".equals(ordering)?Long.compare(a.created,b.created):Long.compare(b.created,a.created));
                String folded=PlayerNames.fold(needle).trim();for(ComputerGameStore.SavedGame g:saved){String searchable="You Pikafish computer game Level "+g.level+" "+date(g.created)+" "+g.source;if(!PlayerNames.fold(searchable).contains(folded))continue;int n=g.finished()?store.analyzed(g.id):0;rows.add(new Entry(g,g.finished()?(n>=g.moves.size()+1?"Review ready":"Review preparing · "+Math.min(n,g.moves.size()+1)+"/"+(g.moves.size()+1)):"Resume game"));}
            }else{String scope="imports".equals(tab)?"import:"+group:tab;LibrarySearchIndex.Result result=sourceIndex.search(scope,ordering,needle);for(GameRepository.Game g:result.matches)rows.add(new Entry(g));}
            runOnUiThread(()->{if(destroyed||!foreground||token!=searchGeneration||adapter!=target||!"database".equals(screen))return;target.items=rows;target.notifyDataSetChanged();databaseScrollReset=false;empty.setText("imports".equals(tab)?"No games here. Import a named collection to get started.":"No matching games.");empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);list.post(()->{if(!destroyed&&adapter==target&&token==searchGeneration)list.setSelectionFromTop(Math.min(restorePosition,Math.max(0,rows.size()-1)),restoreTop);});});
        }catch(CancellationException ignored){}catch(Exception e){report(e);}});
    }
    private final class Rows extends BaseAdapter {
        List<Entry> items=Collections.emptyList();public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return p;}
        public View getView(int pos,View convert,ViewGroup parent){Entry item=items.get(pos);LinearLayout row=row();pad(row,0,12,0,12);LinearLayout info=col();info.setFocusable(true);String red,black,event,meta,status;
            if(item.saved!=null){ComputerGameStore.SavedGame g=item.saved;red=g.humanRed?"You":engineName()+" · Level "+g.level;black=g.humanRed?engineName()+" · Level "+g.level:"You";event=date(g.created)+" · "+g.moves.size()+" half-moves";meta=g.finished()&&!StudySettings.load(HomeActivity.this).hideResults?g.result.replace("1/2-1/2","½–½")+" · "+g.reason:"";status=item.status;}
            else{GameRepository.Game g=item.source;red=PlayerNames.display(g.red);black=PlayerNames.display(g.black);event=g.event+(g.round.isEmpty()?"":" · "+g.round);meta=g.date+(StudySettings.load(HomeActivity.this).hideResults?"":" · "+g.result.replace("1/2-1/2","½–½"));status="Open game";}
            info.addView(player("RED",red));info.addView(player("BLACK",black));TextView eventView=text(event,12,MUTED);pad(eventView,0,8,0,0);info.addView(eventView);if(!meta.isEmpty())info.addView(text(meta,12,MUTED));TextView action=text(status+"  →",12,ACCENT);pad(action,0,6,0,0);info.addView(action);info.setContentDescription("Red: "+red+". Black: "+black+". "+event+". "+meta+". "+status);info.setOnClickListener(v->{if(item.saved!=null){if(item.saved.finished())openReview(item.saved.id);else resume(item.saved.id);}else openSourceGame(item.source.id);});row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            if(item.saved!=null){Button delete=button("",()->delete(item.saved.id),false);delete.setCompoundDrawablesWithIntrinsicBounds(new AppIcon("delete",MUTED,dp(19)),null,null,null);delete.setContentDescription("Delete played game");delete.setPadding(dp(12),0,dp(10),0);delete.setBackgroundColor(Color.TRANSPARENT);row.addView(delete,new LinearLayout.LayoutParams(dp(46),dp(48)));}return row;
        }
    }
    private void delete(String id){ComputerReview.pause();IO.execute(()->{store.setDeleted(id,true);runOnUiThread(()->{if(destroyed)return;deletedId=id;if(current!=null&&current.id.equals(id)){cancelTask();playing=false;resultScreen=false;current=null;}refreshPersonal();showScreen("database");});});}
    private void openReview(String id){ComputerReview.pause();startActivity(new Intent(this,MainActivity.class).putExtra(FROM_HOME,true).putExtra(REVIEW_ID,id));}
    private void openSourceGame(String id){ComputerReview.pause();startActivity(new Intent(this,MainActivity.class).putExtra(FROM_HOME,true).putExtra(SOURCE_ID,id));}
    private void saveScope(){prefs.edit().putString("collection",collection).putString("importId",importId).putString("search:"+collection+":"+importId,query).putString("order:"+collection+":"+importId,order).apply();}
    private void rememberDatabaseScroll(){if(!"database".equals(screen)||gameList==null||gameList.getParent()!=page||gameList.getChildCount()==0)return;String key=String.valueOf(gameList.getTag());prefs.edit().putInt(key,gameList.getFirstVisiblePosition()).putInt(key+":top",gameList.getChildAt(0).getTop()).apply();}
    private void loadScope(){query=prefs.getString("search:"+collection+":"+importId,"");order=prefs.getString("order:"+collection+":"+importId,"newest");}

    private void buildOptions(){
        ScrollView scroll=new ScrollView(this);LinearLayout body=col();scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,-1));heading(body,"Options");section(body,"COMPUTER DIFFICULTY");
        LinearLayout label=row();label.addView(text("Playing level",15,LIGHT),new LinearLayout.LayoutParams(0,-2,1));Button value=button(Integer.toString(level()),()->{},false);value.setContentDescription("Enter difficulty level");label.addView(value,new LinearLayout.LayoutParams(dp(65),dp(44)));body.addView(label);
        TextView category=text(ComputerDifficulty.label(level())+" · Applies to new games",12,MUTED);SeekBar slider=new SeekBar(this);slider.setMax(99);slider.setProgress(level()-1);slider.setContentDescription("Computer difficulty level 1 to 100");
        LinearLayout controls=row();Button minus=button("−",()->slider.setProgress(Math.max(0,slider.getProgress()-1)),false),plus=button("+",()->slider.setProgress(Math.min(99,slider.getProgress()+1)),false);minus.setContentDescription("Decrease difficulty");plus.setContentDescription("Increase difficulty");controls.addView(minus,new LinearLayout.LayoutParams(dp(44),dp(44)));controls.addView(slider,new LinearLayout.LayoutParams(0,dp(44),1));controls.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(44)));body.addView(controls);body.addView(category);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){prefs.edit().putInt("level",n+1).apply();value.setText(Integer.toString(n+1));category.setText(ComputerDifficulty.label(n+1)+" · Applies to new games");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        value.setOnClickListener(v->{EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(Integer.toString(level()));input.setSelectAllOnFocus(true);AlertDialog shown=new AlertDialog.Builder(this).setTitle("Level 1–100").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Set",null).create();dialog=shown;shown.setOnShowListener(d->shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{int n=Integer.parseInt(input.getText().toString());if(n<1||n>100)throw new NumberFormatException();slider.setProgress(n-1);shown.dismiss();}catch(NumberFormatException e){input.setError("Enter a level from 1 to 100");}}));shown.show();});
        if(returnToSetup)body.addView(button("Continue new game",()->showScreen("play"),true));
        section(body,"GAME REVIEW");body.addView(text("Maximum-strength "+engineName(),16,LIGHT));body.addView(text("Analysis strength is managed automatically.",12,MUTED));
        section(body,"BOARD & PIECES");BoardView preview=board();preview.setPosition(Position.initial(),"",false);LinearLayout.LayoutParams previewSize=new LinearLayout.LayoutParams(dp(190),-2);previewSize.gravity=Gravity.CENTER_HORIZONTAL;body.addView(preview,previewSize);StudySettings saved=StudySettings.load(this);
        Runnable appearance=()->{StudySettings s=StudySettings.load(this);apply(preview,s);};String[] boards=AppearancePresets.boardIds(),discs=AppearancePresets.discIds();
        choice(body,"Board",AppearancePresets.boardLabels(),index(boards,saved.boardStyle),n->{StudySettings.selectBoardPreset(this,boards[n]);appearance.run();});
        choice(body,"Pieces",AppearancePresets.discLabels(),index(discs,saved.discStyle),n->{StudySettings.selectDiscPreset(this,discs[n]);appearance.run();});
        choice(body,"Coordinates",new String[]{"Off","ICCS · a–i / 0–9","Numeric · 0–8 / 0–9"},index(new String[]{"none","iccs","numeric"},saved.coordinateStyle),n->{StudySettings s=StudySettings.load(this);StudySettings.save(this,s.boardStyle,s.glyphStyle,s.discStyle,new String[]{"none","iccs","numeric"}[n],s.hideResults);appearance.run();});
        choice(body,"Chinese characters",new String[]{"Traditional","Simplified"},"simplified".equals(saved.glyphStyle)?1:0,n->{StudySettings s=StudySettings.load(this);StudySettings.save(this,s.boardStyle,n==0?"traditional":"simplified",s.discStyle,s.coordinateStyle,s.hideResults);appearance.run();});
        LinearLayout colors=row();colors.addView(button("Board color",()->color("Board color","boardColor",preview.getBoardColor(),appearance),false),new LinearLayout.LayoutParams(0,-2,1));colors.addView(button("Red color",()->color("Red piece background","redBackground",preview.getPieceBackground(true),appearance),false),new LinearLayout.LayoutParams(0,-2,1));colors.addView(button("Black color",()->color("Black piece background","blackBackground",preview.getPieceBackground(false),appearance),false),new LinearLayout.LayoutParams(0,-2,1));body.addView(colors);
        body.addView(button("Reset custom colors",()->{StudySettings.resetCustomColors(this);appearance.run();},false));
        int[] animation={0,100,200,300,500,800};String[] labels={"Off","100 ms","200 ms","300 ms","500 ms","800 ms"};int selected=2;for(int i=0;i<animation.length;i++)if(StudySettings.moveAnimationMillis(this)==animation[i])selected=i;choice(body,"Move animation",labels,selected,n->StudySettings.setMoveAnimationMillis(this,animation[n]));
        long[] intervals={500,1000,2000,3000,5000,10000};long delay=getSharedPreferences("study",MODE_PRIVATE).getLong("replayDelayMillis",1000);int delayIndex=1;for(int i=0;i<intervals.length;i++)if(intervals[i]==delay)delayIndex=i;choice(body,"Replay interval",new String[]{"0.5 seconds","1 second","2 seconds","3 seconds","5 seconds","10 seconds"},delayIndex,n->getSharedPreferences("study",MODE_PRIVATE).edit().putLong("replayDelayMillis",intervals[n]).apply());
        CheckBox hide=new CheckBox(this);hide.setText("Hide game results");hide.setTextColor(LIGHT);hide.setChecked(saved.hideResults);hide.setOnCheckedChangeListener((b,checked)->{StudySettings s=StudySettings.load(this);StudySettings.save(this,s.boardStyle,s.glyphStyle,s.discStyle,s.coordinateStyle,checked);});body.addView(hide);
        int lines=EngineSettings.load(this).lines;choice(body,"Analysis arrows",new String[]{"1 line per side","2 lines per side","3 lines per side","4 lines per side","5 lines per side"},lines-1,n->getSharedPreferences("engine-settings",MODE_PRIVATE).edit().putInt("lines",n+1).apply());
    }
    private void color(String title,String surface,int initial,Runnable changed){colorDialog=ColorPickerDialog.show(this,title,initial,color->{StudySettings.setCustomColor(this,surface,color);changed.run();});}
    private void choice(LinearLayout parent,String label,String[] values,int selected,IntConsumer changed){final int[] n={selected};LinearLayout row=row();Button prev=button("‹",()->{},false),next=button("›",()->{},false);prev.setContentDescription("Previous "+label);next.setContentDescription("Next "+label);LinearLayout names=col();TextView caption=text(label,11,MUTED),value=text(values[n[0]],14,LIGHT);caption.setGravity(Gravity.CENTER);value.setGravity(Gravity.CENTER);names.addView(caption);names.addView(value);prev.setOnClickListener(v->{n[0]=Math.floorMod(n[0]-1,values.length);value.setText(values[n[0]]);changed.accept(n[0]);});next.setOnClickListener(v->{n[0]=(n[0]+1)%values.length;value.setText(values[n[0]]);changed.accept(n[0]);});row.addView(prev,new LinearLayout.LayoutParams(dp(44),dp(48)));row.addView(names,new LinearLayout.LayoutParams(0,-2,1));row.addView(next,new LinearLayout.LayoutParams(dp(44),dp(48)));pad(row,0,4,0,4);parent.addView(row);}
    private BoardView board(){BoardView b=new BoardView(this);apply(b,StudySettings.load(this));return b;}
    private void apply(BoardView b,StudySettings s){b.setAppearance(s.boardStyle,s.glyphStyle,s.discStyle);b.setCustomColors(s.boardColor,s.redBackground,s.blackBackground);b.setCoordinates(s.coordinateStyle);b.setMoveAnimationMillis(StudySettings.moveAnimationMillis(this));}
    private int level(){return ComputerDifficulty.clamp(prefs.getInt("level",40));}
    private String engineName(){return PikafishEngine.available(this)?"Pikafish":"Built-in engine";}
    private void heading(LinearLayout parent,String title){TextView v=text(title,28,LIGHT);pad(v,2,10,0,18);parent.addView(v);}
    private void section(LinearLayout parent,String label){TextView v=text(label,11,MUTED);v.setLetterSpacing(.09f);pad(v,2,20,0,10);parent.addView(v);}
    private LinearLayout player(String side,String name){LinearLayout row=row();TextView label=text(side,10,"RED".equals(side)?0xfff2a392:MUTED);row.addView(label,new LinearLayout.LayoutParams(dp(43),-2));TextView player=text(name,14,LIGHT);player.setMaxLines(2);row.addView(player,new LinearLayout.LayoutParams(0,-2,1));return row;}
    private Button action(String label,String icon,Runnable run){Button b=button(label,run,false);b.setTextSize(12);b.setCompoundDrawablesWithIntrinsicBounds(new AppIcon(icon,LIGHT,dp(17)),null,null,null);b.setCompoundDrawablePadding(dp(5));b.setPadding(dp(10),dp(5),dp(8),dp(5));return b;}
    private Spinner spinner(String[] labels,int selected){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels){@Override public View getView(int p,View convert,ViewGroup parent){TextView v=(TextView)super.getView(p,convert,parent);v.setTextColor(MUTED);v.setTextSize(12);return v;}};s.setAdapter(a);s.setSelection(selected);return s;}
    private LinearLayout panel(){LinearLayout p=col();pad(p,20,18,20,18);p.setBackground(shape(SURFACE,LINE,18));return p;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTextSize(size);return v;}
    private Button button(String label,Runnable run,boolean selected){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(14);b.setTextColor(selected?INK:LIGHT);b.setBackground(shape(selected?ACCENT:SURFACE,0,13));b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setOnClickListener(v->run.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(2),dp(4),dp(2),dp(4));b.setLayoutParams(p);return b;}
    private GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private void pad(View v,int l,int t,int r,int b){v.setPadding(dp(l),dp(t),dp(r),dp(b));}private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static int index(String[] values,String value){for(int i=0;i<values.length;i++)if(values[i].equals(value))return i;return 0;}
    private static String pretty(String move){return move.substring(0,2)+"–"+move.substring(2);}private static String date(long time){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(time));}
    private void report(Exception e){runOnUiThread(()->{if(!destroyed)failure("Could not load games",e);});}
    private void failure(String title,Exception e){if(dialog!=null)dialog.dismiss();dialog=new AlertDialog.Builder(this).setTitle(title).setMessage(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()).setPositiveButton("OK",null).show();}
    @Override protected void onStart(){super.onStart();foreground=true;if(ready){refreshPersonal();if(importVersion()!=importVersion)refreshSources();if(playing&&"play".equals(screen)){renderPosition();resumeComputer();}if("database".equals(screen))showScreen("database");}}
    @Override protected void onStop(){rememberDatabaseScroll();foreground=false;cancelTask();ComputerReview.pause();searchGeneration++;if(searchTask!=null)searchTask.cancel(true);handler.removeCallbacksAndMessages(null);super.onStop();}
    @Override protected void onDestroy(){destroyed=true;cancelTask();work.shutdownNow();searcher.shutdownNow();handler.removeCallbacksAndMessages(null);if(dialog!=null)dialog.dismiss();if(colorDialog!=null)colorDialog.dismiss();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("playing",playing);out.putBoolean("setup",setup);out.putBoolean("flip",flip);out.putString("side",side);out.putString("startFen",startFen);out.putString("sourceTitle",sourceTitle);out.putString("activeGame",current==null?"":current.id);}
    @Override public void onBackPressed(){if(playing&&"play".equals(screen)){playing=false;showScreen("play");}else if(setup){setup=false;showScreen("play");}else if(!"play".equals(screen))showScreen("play");else super.onBackPressed();}
}
