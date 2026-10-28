package org.xiangqi.study;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import org.xiangqi.study.core.Engine;
import org.xiangqi.study.core.Position;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Runs the actual production Android adapter on the JVM with narrow Context stubs. */
public final class NativeAdapterAuditTest {
    private static int checks;
    private static Path fixtureRoot, network;
    private static void check(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
    private static final class TestContext extends Context {
        final Path files, binaries; final byte[] badAsset; int assetOpens;
        TestContext(Path files,Path binaries,byte[] badAsset) throws Exception {this.files=files;this.binaries=binaries;this.badAsset=badAsset;Files.createDirectories(files);}
        public ApplicationInfo getApplicationInfo(){ApplicationInfo info=new ApplicationInfo();info.nativeLibraryDir=binaries.toString();return info;}
        public File getFilesDir(){return files.toFile();}
        public AssetManager getAssets(){return new AssetManager(){public InputStream open(String name)throws IOException{assetOpens++;return badAsset==null?Files.newInputStream(network):new ByteArrayInputStream(badAsset);}};}
        Path installed(){return files.resolve("pikafish-"+PikafishRelease.VERSION+"/pikafish.nnue");}
        void mode(String mode)throws Exception{
            Path dir=installed().getParent();Files.createDirectories(dir);Files.writeString(dir.resolve("mode.txt"),mode);
            for(String name:new String[]{"started.txt","commands.txt"}){
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                while(true)try{Files.deleteIfExists(dir.resolve(name));break;}
                catch(FileSystemException busy){if(System.nanoTime()>=until)throw busy;Thread.sleep(25);}
            }
        }
    }
    private static String hash(Path path)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(path)){byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}
    private static void expectFailure(TestContext context,String message)throws Exception{
        try{PikafishEngine.analyze(context,Position.initial(),"",Collections.emptyList(),8,50);throw new AssertionError("Expected failure: "+message);}
        catch(IllegalStateException|IOException e){check(e.getMessage().contains(message),"Wrong error: "+e);}
    }
    public static void main(String[]args)throws Exception{
        Path real=Paths.get(args[0]).toAbsolutePath(),fake=Paths.get(args[1]).toAbsolutePath();network=Paths.get(args[2]).toAbsolutePath();fixtureRoot=Paths.get(args[3]).toAbsolutePath();
        Path realDir=fixtureRoot.resolve("real-native"),fakeDir=fixtureRoot.resolve("fake-native");Files.createDirectories(realDir);Files.createDirectories(fakeDir);
        Files.copy(real,realDir.resolve("libpikafish.so"),StandardCopyOption.REPLACE_EXISTING);Files.copy(fake,fakeDir.resolve("libpikafish.so"),StandardCopyOption.REPLACE_EXISTING);
        TestContext actual=new TestContext(fixtureRoot.resolve("real-files"),realDir,null);
        Files.deleteIfExists(actual.installed());
        android.os.Build.VERSION.SDK_INT=28;check(!PikafishEngine.available(actual),"API28 must not launch an API29 native executable");
        android.os.Build.VERSION.SDK_INT=29;check(PikafishEngine.available(actual),"Supported API29 native executable unavailable");android.os.Build.VERSION.SDK_INT=31;
        List<String> history=Arrays.asList("b2e2","b7e7","g3g4");Position position=Position.initial();for(String move:history)position=position.play(move);
        for(String start:new String[]{"",null,Position.START_FEN}){
            Engine.Result result=PikafishEngine.analyze(actual,position,start,history,8,1500);
            check(position.legalMoves().contains(result.bestMove),"Actual native returned illegal move");check(result.depth>0&&!result.pv.isEmpty(),"Actual native analysis missing");
        }
        check(actual.assetOpens==1,"Verified NNUE must be reused without reopening asset");
        check(hash(actual.installed()).equals(hash(network)),"Initial NNUE copy changed");
        TestContext fixture=new TestContext(actual.files,fakeDir,null);fixture.mode("valid");
        Engine.Result valid=PikafishEngine.analyze(fixture,Position.initial(),"",Collections.emptyList(),8,50);
        check(valid.bestMove.equals("b0c2")&&valid.score==20,"Valid deterministic protocol result");
        fixture.mode("draw");Engine.Result drawn=PikafishEngine.analyze(fixture,Position.initial(),"",Collections.emptyList(),8,50);
        check(drawn.depth==1&&drawn.score==0&&drawn.pv.equals(Collections.singletonList("b0c2")),"A root draw with an empty PV must still complete with a legal best move");
        fixture.mode("stream");List<Engine.Result> progress=new ArrayList<>();
        PikafishEngine.Options configured=new PikafishEngine.Options(40,0,2,128);
        PikafishEngine.analyze(fixture,Position.initial(),"",Collections.emptyList(),configured,progress::add);
        check(progress.size()==2&&progress.get(0).depth==1&&progress.get(1).depth==2,"Exact depth progress was missing or bound score leaked");
        check(progress.get(0).bestMove.equals("b0c2")&&progress.get(1).bestMove.equals("h0g2"),"Live best-move updates did not change");
        check(progress.get(0).score==20&&progress.get(1).score==30,"Progress snapshots were later mutated");
        List<String> commands=Files.readAllLines(fixture.installed().resolveSibling("commands.txt"));
        check(commands.contains("setoption name Threads value "+configured.threads)&&commands.contains("setoption name Hash value 128"),"Configured engine resources were ignored");
        check(commands.contains("go depth 40")&&commands.stream().noneMatch(s->s.startsWith("go ")&&s.contains("movetime")),"Untimed depth search was given an arbitrary time limit");
        PikafishEngine.Options clamp=new PikafishEngine.Options(999,Long.MAX_VALUE,999,Integer.MAX_VALUE);
        check(clamp.maxDepth==128&&clamp.millis==3600000&&clamp.threads<=Runtime.getRuntime().availableProcessors()&&clamp.hashMb==512,"Unsafe settings not bounded");
        try(RandomAccessFile file=new RandomAccessFile(fixture.installed().toFile(),"rw")){file.setLength(3);}
        fixture.mode("valid");PikafishEngine.analyze(fixture,Position.initial(),"",Collections.emptyList(),8,50);
        check(fixture.assetOpens==1&&hash(fixture.installed()).equals(hash(network)),"Truncated NNUE was not repaired");
        long previous=fixture.installed().toFile().lastModified();
        try(RandomAccessFile file=new RandomAccessFile(fixture.installed().toFile(),"rw")){int value=file.read();file.seek(0);file.write(value^255);}
        check(fixture.installed().toFile().setLastModified(previous+2000),"Could not update corruption fixture timestamp");
        fixture.mode("valid");PikafishEngine.analyze(fixture,Position.initial(),"",Collections.emptyList(),8,50);
        check(fixture.assetOpens==2&&hash(fixture.installed()).equals(hash(network)),"Same-length corrupted NNUE was not repaired");
        for(String[] test:new String[][]{{"eof","exited"},{"illegal","invalid best move"},{"critical","CRITICAL ERROR"},{"incomplete","no completed analysis"},{"hang","timed out"}}){
            fixture.mode(test[0]);long started=System.nanoTime();expectFailure(fixture,test[1]);check(System.nanoTime()-started<TimeUnit.SECONDS.toNanos(10),"Unbounded failure handling: "+test[0]);
        }
        TestContext corrupt=new TestContext(fixtureRoot.resolve("bad-asset-files"),fakeDir,new byte[]{1,2,3});corrupt.mode("valid");expectFailure(corrupt,"integrity check");
        check(!Files.exists(corrupt.installed())&&!Files.exists(corrupt.installed().resolveSibling("pikafish.nnue.tmp")),"Invalid bundled asset left an installed or temporary network");
        fixture.mode("hang");AtomicReference<Throwable> interrupted=new AtomicReference<>();
        Thread search=new Thread(()->{try{PikafishEngine.analyze(fixture,Position.initial(),"",Collections.emptyList(),new PikafishEngine.Options(128,0,1,32),null);}catch(Throwable e){interrupted.set(e);}},"native-cancel-test");
        search.start();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!Files.exists(fixture.installed().resolveSibling("started.txt"))&&System.nanoTime()<until)Thread.sleep(10);
        search.interrupt();search.join(3000);check(!search.isAlive()&&interrupted.get() instanceof InterruptedException,"Cancellation failed to release adapter thread");
        long childUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);boolean child;
        do{child=ProcessHandle.allProcesses().anyMatch(p->p.info().command().orElse("").equalsIgnoreCase(fakeDir.resolve("libpikafish.so").toString()));if(child)Thread.sleep(20);}while(child&&System.nanoTime()<childUntil);
        check(!child,"Cancelled native process leaked");
        System.out.println("PASS: "+checks+" production native-adapter assertions; real Windows Pikafish blank/null/explicit histories, verified network reuse/repair, exact-depth streaming snapshots, changed best-move updates, configured Threads/Hash/depth/untimed options, EOF, critical/illegal/incomplete output, bounded finite timeout and continuous-search process cancellation. Android ABI execution is not exercised.");
    }
}
