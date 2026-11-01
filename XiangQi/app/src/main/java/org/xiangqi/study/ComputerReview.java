package org.xiangqi.study;

import android.content.Context;
import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;
import java.util.*;
import java.util.concurrent.*;

/** A single preemptible review worker. It never sends hints or scores to a playing screen. */
public final class ComputerReview {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"xiangqi-hidden-review");t.setDaemon(true);return t;});
    private static Future<?> future;private static String active="";private static int generation;private static long requests;
    private ComputerReview(){}
    public static synchronized void pause(){generation++;active="";if(future!=null){future.cancel(true);future=null;}}
    public static synchronized void start(Context context,String id){
        requests++;if(id.equals(active)&&future!=null&&!future.isDone())return;pause();int token=generation;long requested=requests;active=id;Context app=context.getApplicationContext();
        future=WORK.submit(()->{
            try{
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                ComputerGameStore store=ComputerGameStore.get(app);
                for(;;){
                    if(!current(token))return;ComputerGameStore.SavedGame saved=store.load(id);if(saved==null)return;
                    if(store.analyzed(id)>=saved.moves.size()+1)return;
                    Position position=Position.fromFen(saved.startFen);List<String> history=new ArrayList<>();boolean worked=false;
                    for(int ply=0;ply<=saved.moves.size();ply++){
                        if(!current(token))return;
                        if(store.analysis(id,ply)==null){
                            Engine.Result result=PikafishEngine.available(app)
                                ?PikafishEngine.analyze(app,position,saved.startFen,history,EngineSettings.timed(app,2000,1),null)
                                :Engine.analyze(position,64,2000);
                            if(!current(token)||!store.putAnalysis(id,saved.revision,ply,result.score,result.bestMove,String.join(" ",result.pv),result.depth))return;
                            worked=true;
                        }
                        if(ply<saved.moves.size()){String move=saved.moves.get(ply);position=position.play(move);history.add(move);}
                    }
                    ComputerGameStore.SavedGame latest=store.load(id);
                    if(latest==null||latest.revision!=saved.revision||latest.moves.size()==saved.moves.size())return;
                }
            }catch(InterruptedException ignored){Thread.currentThread().interrupt();}
            catch(Exception error){android.util.Log.w("ComputerReview","Review will retry when the game is reopened",error);}
            finally{synchronized(ComputerReview.class){if(generation==token){future=null;active="";if(requests!=requested)start(app,id);}}}
        });
    }
    private static synchronized boolean current(int token){return token==generation&&!Thread.currentThread().isInterrupted();}
}
