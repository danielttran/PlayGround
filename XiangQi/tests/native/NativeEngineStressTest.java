package org.xiangqi.study;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Repeated real-engine search/cancel/restart, isolated from any installed phone. */
public final class NativeEngineStressTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class TestContext extends Context {
        final Path files,binaries,network;
        TestContext(Path files,Path binaries,Path network)throws IOException{this.files=files;this.binaries=binaries;this.network=network;Files.createDirectories(files);}
        public ApplicationInfo getApplicationInfo(){ApplicationInfo info=new ApplicationInfo();info.nativeLibraryDir=binaries.toString();return info;}
        public File getFilesDir(){return files.toFile();}
        public AssetManager getAssets(){return new AssetManager(){public InputStream open(String name)throws IOException{return Files.newInputStream(network);}};}
    }
    private static int readerThreads(){int count=0;for(Thread thread:Thread.getAllStackTraces().keySet())if(thread.isAlive()&&thread.getName().equals("Pikafish-output"))count++;return count;}
    private static boolean childAlive(Path binary){return ProcessHandle.allProcesses().anyMatch(process->process.info().command().orElse("").equalsIgnoreCase(binary.toString()));}
    private static void released(Path binary,int baseline)throws InterruptedException{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while((childAlive(binary)||readerThreads()>baseline)&&System.nanoTime()<until)Thread.sleep(20);
        check(!childAlive(binary),"A cancelled task-owned Pikafish process survived");
        check(readerThreads()<=baseline,"A cancelled native output reader survived");
    }
    private static void legal(Position root,Engine.Result result){
        check(result!=null&&result.depth>0,"No completed progress iteration");
        check(root.legalMoves().contains(result.bestMove),"Progress best move is illegal");
        check(!result.lines.isEmpty()&&result.lines.size()<=5,"Invalid MultiPV count");
        for(Engine.Line line:result.lines){Position cursor=root;check(!line.pv.isEmpty(),"Unnamed root alternative");for(String move:line.pv)cursor=cursor.play(move);}
    }
    public static void main(String[]args)throws Exception{
        Path engine=Paths.get(args[0]).toAbsolutePath(),network=Paths.get(args[1]).toAbsolutePath(),work=Paths.get(args[2]).toAbsolutePath();
        Path binaries=work.resolve("stress-native");Files.createDirectories(binaries);Path binary=binaries.resolve("libpikafish.so");Files.copy(engine,binary,StandardCopyOption.REPLACE_EXISTING);
        TestContext context=new TestContext(work.resolve("stress-files"),binaries,network);android.os.Build.VERSION.SDK_INT=31;
        List<String> moves=Arrays.asList("b2e2","b7e7","g3g4","g6g5","h0g2");
        Position later=Position.initial();for(String move:moves)later=later.play(move);
        int readers=readerThreads();
        for(int cycle=0;cycle<20;cycle++){
            final Position root=cycle%2==0?Position.initial():later;
            final List<String> history=cycle%2==0?Collections.emptyList():moves;
            CountDownLatch started=new CountDownLatch(1);AtomicReference<Engine.Result> sample=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread worker=new Thread(()->{
                try{PikafishEngine.analyze(context,root,"",history,new PikafishEngine.Options(128,0,2,64,5),value->{if(value.depth>0){sample.set(value);started.countDown();}});}
                catch(Throwable error){failure.set(error);}
            },"native-stress-search-"+cycle);
            worker.start();
            try{
                check(started.await(10,TimeUnit.SECONDS),"Real Pikafish did not start searching, cycle "+cycle+": "+failure.get());
                legal(root,sample.get());
            }finally{worker.interrupt();worker.join(3000);}
            check(!worker.isAlive(),"Cancellation did not release the search thread");
            check(failure.get() instanceof InterruptedException,"Continuous search did not unwind by cancellation: "+failure.get());
            released(binary,readers);
            // A completed fresh search proves the next session can initialize,
            // read the same verified network and return a legal continuation.
            if(cycle%5==4){
                Engine.Result fresh=PikafishEngine.analyze(context,root,"",history,new PikafishEngine.Options(3,0,2,64,3),null);
                legal(root,fresh);check(fresh.depth==3&&fresh.lines.size()==3,"Fresh search after repeated cancellation was incomplete");released(binary,readers);
            }
        }
        System.out.println("PASS: "+checks+" assertions; 20 real Windows Pikafish five-line continuous-search cancellations, both turns/history positions, no surviving task-owned process/output reader, and four successful three-line restarts. Android ARM64 runtime is not exercised.");
    }
}
