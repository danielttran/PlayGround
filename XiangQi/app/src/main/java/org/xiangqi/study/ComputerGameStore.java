package org.xiangqi.study;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xiangqi.study.core.Position;
import java.text.SimpleDateFormat;
import java.util.*;

/** Personal games and hidden position analyses. Source collections are never modified. */
public final class ComputerGameStore extends SQLiteOpenHelper {
    private static ComputerGameStore instance;
    public static synchronized ComputerGameStore get(Context context){
        if(instance==null)instance=new ComputerGameStore(context.getApplicationContext());return instance;
    }
    private ComputerGameStore(Context context){super(context,"computer-games.db",null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE games(id TEXT PRIMARY KEY,payload TEXT NOT NULL,updated INTEGER NOT NULL,done INTEGER NOT NULL,revision INTEGER NOT NULL,deleted INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE analyses(game_id TEXT NOT NULL,ply INTEGER NOT NULL,score INTEGER NOT NULL,best TEXT NOT NULL,pv TEXT NOT NULL,depth INTEGER NOT NULL,PRIMARY KEY(game_id,ply),FOREIGN KEY(game_id) REFERENCES games(id) ON DELETE CASCADE)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Unsupported personal-game database version");}
    public static final class SavedGame {
        public String id="played-"+UUID.randomUUID(),startFen=Position.START_FEN,result="*",reason="",source="";
        public int level=40,revision;
        public boolean humanRed=true;
        public long created=System.currentTimeMillis(),updated=created;
        public final List<String> moves=new ArrayList<>();
        public final Set<Integer> hints=new HashSet<>();
        public boolean finished(){return !"*".equals(result);}
        public Position position(){Position p=Position.fromFen(startFen);for(String move:moves)p=p.play(move);return p;}
        public SavedGame copy(){try{return read(json());}catch(Exception e){throw new IllegalStateException(e);}}
        public JSONObject json() throws Exception {
            return new JSONObject().put("id",id).put("startFen",startFen).put("result",result).put("reason",reason).put("source",source)
                .put("level",level).put("revision",revision).put("humanRed",humanRed).put("created",created).put("updated",updated)
                .put("moves",new JSONArray(moves)).put("hints",new JSONArray(hints));
        }
        static SavedGame read(JSONObject j) throws Exception {
            SavedGame g=new SavedGame();g.id=j.getString("id");g.startFen=j.getString("startFen");g.result=j.getString("result");
            g.reason=j.optString("reason","");g.source=j.optString("source","");g.level=ComputerDifficulty.clamp(j.getInt("level"));
            g.revision=j.getInt("revision");g.humanRed=j.getBoolean("humanRed");g.created=j.getLong("created");g.updated=j.getLong("updated");
            JSONArray moves=j.getJSONArray("moves");if(moves.length()>2000)throw new IllegalArgumentException("Personal game is too long");
            for(int i=0;i<moves.length();i++)g.moves.add(moves.getString(i));
            JSONArray hints=j.optJSONArray("hints");if(hints!=null)for(int i=0;i<hints.length();i++)g.hints.add(hints.getInt(i));return g;
        }
    }
    public static final class Analysis {
        public int score,depth;public String best,pv;
    }
    public synchronized void save(SavedGame game) throws Exception {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            try(Cursor c=db.rawQuery("SELECT revision,updated,deleted FROM games WHERE id=?",new String[]{game.id})){
                if(c.moveToFirst()&&(c.getInt(2)!=0||c.getInt(0)>game.revision||c.getInt(0)==game.revision&&c.getLong(1)>game.updated)){db.setTransactionSuccessful();return;}
            }
            ContentValues values=new ContentValues();values.put("id",game.id);values.put("payload",game.json().toString());values.put("updated",game.updated);
            values.put("done",game.finished()?1:0);values.put("revision",game.revision);
            if(db.update("games",values,"id=?",new String[]{game.id})==0)db.insertOrThrow("games",null,values);
            db.delete("analyses","game_id=? AND ply>?",new String[]{game.id,Integer.toString(game.moves.size())});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized SavedGame load(String id) throws Exception {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM games WHERE id=? AND deleted=0",new String[]{id})){
            return c.moveToFirst()?SavedGame.read(new JSONObject(c.getString(0))):null;
        }
    }
    public synchronized List<SavedGame> list() throws Exception {
        List<SavedGame> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM games WHERE deleted=0 ORDER BY updated DESC",null)){
            while(c.moveToNext())result.add(SavedGame.read(new JSONObject(c.getString(0))));
        }return result;
    }
    public synchronized void setDeleted(String id,boolean deleted){ContentValues v=new ContentValues();v.put("deleted",deleted?1:0);getWritableDatabase().update("games",v,"id=?",new String[]{id});}
    public synchronized boolean putAnalysis(String id,int revision,int ply,int score,String best,String pv,int depth) throws Exception {
        SavedGame g=load(id);if(g==null||g.revision!=revision||ply>g.moves.size())return false;
        ContentValues v=new ContentValues();v.put("game_id",id);v.put("ply",ply);v.put("score",score);v.put("best",best==null?"":best);v.put("pv",pv);v.put("depth",depth);
        getWritableDatabase().insertWithOnConflict("analyses",null,v,SQLiteDatabase.CONFLICT_REPLACE);return true;
    }
    public synchronized Analysis analysis(String id,int ply){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT score,best,pv,depth FROM analyses WHERE game_id=? AND ply=?",new String[]{id,Integer.toString(ply)})){
            if(!c.moveToFirst())return null;Analysis a=new Analysis();a.score=c.getInt(0);a.best=c.getString(1);a.pv=c.getString(2);a.depth=c.getInt(3);return a;
        }
    }
    public synchronized int analyzed(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM analyses WHERE game_id=?",new String[]{id})){c.moveToFirst();return c.getInt(0);}}
    public synchronized GameRepository.Game review(String id) throws Exception {
        SavedGame saved=load(id);if(saved==null||!saved.finished())throw new IllegalArgumentException("Finish this game before opening its review");
        GameRepository.Game g=new GameRepository.Game();g.personalGame=true;g.id=saved.id;g.event="Computer game · Level "+saved.level;g.round="";
        g.red=saved.humanRed?"You":"Pikafish · Level "+saved.level;g.black=saved.humanRed?"Pikafish · Level "+saved.level:"You";
        g.date=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(saved.created));g.result=saved.result;g.startFen=saved.startFen;
        g.sourceUrl="";g.timeControl="Untimed";g.collection="My Games";g.moves.addAll(saved.moves);g.metadataNote=saved.reason+(saved.source.isEmpty()?"":"\nStarted from: "+saved.source);
        g.summary=analyzed(id)>=saved.moves.size()+1?"Pikafish review · maximum strength":"Pikafish review preparing · reopen to refresh";
        for(int ply=0;ply<=saved.moves.size();ply++){
            Analysis before=analysis(id,ply);if(before!=null&&!before.best.isEmpty())g.moveBestMoves.put(ply,before.best);
            if(ply==0)continue;Analysis previous=analysis(id,ply-1);if(previous==null||before==null)continue;
            boolean redMover=Position.fromFen(saved.startFen).redToMove==((ply&1)==1);
            int loss=(previous.score-before.score)*(redMover?1:-1);String actual=saved.moves.get(ply-1);
            if(actual.equals(previous.best))loss=0;
            String label=actual.equals(previous.best)?"Best move":loss>=250?"Blunder":loss>=120?"Mistake":loss>=60?"Inaccuracy":"Good move";
            String glyph=loss>=250?"??":loss>=120?"?":loss>=60?"?!":"";
            String comment=label+" · "+score(previous.score)+" → "+score(before.score)+(previous.best.isEmpty()?"":" · Best: "+previous.best)
                +(previous.pv.isEmpty()?"":"\nLine: "+previous.pv)+(saved.hints.contains(ply-1)?"\nHint used before this move.":"");
            g.moveComments.put(ply,comment);g.moveGlyphs.put(ply,glyph);
        }return g;
    }
    private static String score(int score){return Math.abs(score)>=29000?(score>0?"Red winning":"Black winning"):String.format(Locale.ROOT,"%+.2f",score/100.0);}
}
