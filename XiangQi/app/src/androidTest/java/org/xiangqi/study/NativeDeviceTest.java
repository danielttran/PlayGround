package org.xiangqi.study;

import android.content.Context;
import android.os.Build;
import android.test.InstrumentationTestCase;
import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;
import java.util.*;
import java.util.concurrent.*;

/** Executes the production ARM64 binary on a compatible device, without changing study data. */
public final class NativeDeviceTest extends InstrumentationTestCase {
    private boolean nativeReady(){
        Context context=getInstrumentation().getTargetContext();
        boolean arm=Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");
        if(arm&&Build.VERSION.SDK_INT>=PikafishRelease.MIN_ANDROID_API)assertTrue("ARM64 delivery must contain Pikafish",PikafishEngine.available(context));
        if(!PikafishEngine.available(context))android.util.Log.i("XiangqiNativeAudit","NOT_RUN: emulator has no ARM64 payload");
        return PikafishEngine.available(context);
    }
    public void testNativeAnalysisAcrossEventsAndCustomPositions()throws Exception{
        if(!nativeReady())return;
        Context context=getInstrumentation().getTargetContext();
        List<GameRepository.Game> games=new GameRepository(context).load();Set<String> events=new HashSet<>();int checked=0;
        for(GameRepository.Game game:games){
            if(!events.add(game.event))continue;
            int target=game.moves.size()/2;Position position=game.initial();List<String> history=new ArrayList<>();
            for(int i=0;i<target;i++){history.add(game.moves.get(i));position=position.play(game.moves.get(i));}
            check(position,game.startFen,history);checked++;
        }
        check(Position.initial(),"",Collections.emptyList());checked++;
        check(Position.initial(),null,Collections.emptyList());checked++;
        boolean historicalDrawChecked=false;
        for(GameRepository.Game game:games)if(game.id.equals("historical-wxf-442-g5")){
            assertTrue("Draw regression must retain all 333 recorded plies",game.moves.size()>=333);
            Position longGame=game.initial();for(String move:game.moves)longGame=longGame.play(move);
            assertEquals("Full historical repetition/Rule60 context must retain the native draw",0,check(longGame,game.startFen,game.moves).score);
            historicalDrawChecked=true;checked++;
        }
        assertTrue("The historical draw regression must be bundled",historicalDrawChecked);
        Position variation=Position.initial().play("b2e2").play("b9c7").play("a0a1");
        check(variation,"",Arrays.asList("b2e2","b9c7","a0a1"));checked++;
        for(String fen:new String[]{
            "4k4/9/9/9/4p4/9/9/9/4r4/4K4 w - - 0 1",
            "4k4/9/9/9/4p4/9/9/9/9/4K4 b - - 0 1",
            "4k4/9/9/9/4P4/9/9/9/9/4K4 w - - 0 1",
            Position.START_FEN.replace("0 1","120 2147483647")}){
            check(Position.fromFen(fen),fen,Collections.emptyList());checked++;
        }
        android.util.Log.i("XiangqiNativeAudit","PASS actual Android "+Build.VERSION.RELEASE+" ARM64 Pikafish "+PikafishRelease.VERSION+": "+checked+" recorded/custom/check/variation positions");
    }
    private Engine.Result check(Position position,String start,List<String> history)throws Exception{
        final int[] updates={0};List<String> legal=position.legalMoves();
        Engine.Result result=PikafishEngine.analyze(getInstrumentation().getTargetContext(),position,start,history,
            new PikafishEngine.Options(3,400,1,32),progress->{
                updates[0]++;if(!legal.isEmpty()){assertTrue("Streamed best move must be legal",legal.contains(progress.bestMove));assertFalse("Streamed best move needs a legal PV",progress.pv.isEmpty());assertEquals(progress.bestMove,progress.pv.get(0));}
            });
        if(!legal.isEmpty()){
            assertTrue("Final best move must be legal",legal.contains(result.bestMove));assertTrue(result.depth>0);
            assertFalse("Final best move must appear in the PV",result.pv.isEmpty());assertEquals(result.bestMove,result.pv.get(0));
            // An adjudicated root draw can report an exact score with no PV,
            // even with visited nodes. The final legal bestmove still completes.
            if(updates[0]==0)assertEquals("Only an exact draw may finish without streamed PV updates",0,result.score);
        }
        return result;
    }
    public void testNativeContinuousSearchCancelsOnPhone()throws Exception{
        if(!nativeReady())return;
        ExecutorService worker=Executors.newSingleThreadExecutor();CountDownLatch update=new CountDownLatch(1);
        Future<?> running=worker.submit(()->{
            try{PikafishEngine.analyze(getInstrumentation().getTargetContext(),Position.initial(),"",Collections.emptyList(),new PikafishEngine.Options(128,0,1,32),result->update.countDown());}
            catch(InterruptedException expected){Thread.currentThread().interrupt();}
            catch(Exception failure){throw new RuntimeException(failure);}
        });
        try{
            assertTrue("Untimed native analysis must publish",update.await(25,TimeUnit.SECONDS));
            assertFalse("Untimed depth-128 search should still run",running.isDone());running.cancel(true);
        }finally{running.cancel(true);worker.shutdownNow();assertTrue("Native cancellation must release the worker",worker.awaitTermination(8,TimeUnit.SECONDS));}
    }
}
