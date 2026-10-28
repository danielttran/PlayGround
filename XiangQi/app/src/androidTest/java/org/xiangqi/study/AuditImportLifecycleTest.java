package org.xiangqi.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.os.Bundle;
import android.os.Parcel;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Uncommitted import drafts survive Activity recreation without touching user files. */
public final class AuditImportLifecycleTest extends InstrumentationTestCase {
    private static final String DRAFT="[Event \"Import lifecycle audit\"]\n[Red \"Draft Red\"]\n[Black \"Draft Black\"]\n[Result \"*\"]\n\n1. b2e2 b9c7 2. a0a1 *\n";
    private Activity activity;
    private SharedPreferences preferences;
    private Map<String,?> original;
    private File directory;
    private GameRepository repository;
    private final List<String> draftTokens=new ArrayList<>();

    @Override protected void setUp()throws Exception {
        super.setUp();Context target=getInstrumentation().getTargetContext();
        preferences=target.getSharedPreferences("study",0);original=preferences.getAll();preferences.edit().clear().commit();
        directory=new File(target.getCacheDir(),"import-lifecycle-"+System.nanoTime());assertTrue(directory.mkdirs());
        Context storage=new ContextWrapper(target){@Override public Context getApplicationContext(){return this;}@Override public File getFilesDir(){return directory;}};
        repository=new GameRepository(storage);repository.load();
        activity=getInstrumentation().startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->Boolean.TRUE.equals(get("loaded")),15000,"Library did not load");installIsolatedRepository();
    }
    @Override protected void tearDown()throws Exception {
        if(activity!=null&&!activity.isDestroyed())main(activity::finish);getInstrumentation().waitForIdleSync();
        Field fileIo=MainActivity.class.getDeclaredField("FILE_IO");fileIo.setAccessible(true);((ExecutorService)fileIo.get(null)).submit(()->{}).get(15,TimeUnit.SECONDS);
        long deadline=SystemClock.uptimeMillis()+5000;while(activity!=null&&!activity.isDestroyed()&&SystemClock.uptimeMillis()<deadline)Thread.sleep(25);
        restore(preferences,original);
        for(String token:draftTokens)ImportDraftStore.delete(getInstrumentation().getTargetContext(),token);
        if(directory!=null){File[] files=directory.listFiles();if(files!=null)for(File file:files)assertTrue(file.delete());assertTrue(directory.delete());}
        super.tearDown();
    }

    public void testNamedPasteDraftAndTypeSurviveRecreation()throws Exception {
        main(()->call("importCollection",new Class<?>[]{int.class,Uri.class},1,null));getInstrumentation().waitForIdleSync();
        main(()->{
            AlertDialog setup=dialog("importCollectionDialog");name(setup).setText("Weekend engine study");engine(setup).setChecked(true);setup.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });getInstrumentation().waitForIdleSync();
        main(()->text(dialog("pasteDialog")).setText(DRAFT));recreate();
        await(()->get("pasteDialog") instanceof AlertDialog,5000,"The pasted record draft disappeared during recreation");
        main(()->{
            AlertDialog paste=dialog("pasteDialog");assertTrue(paste.isShowing());assertEquals(DRAFT,text(paste).getText().toString());
            assertEquals("Weekend engine study",get("pendingImportName"));assertEquals(Boolean.TRUE,get("pendingImportEngine"));
            paste.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        assertEquals("A preserved draft is not committed until Import is pressed",0,repository.importedCollections().size());
        recreate();main(()->assertNull("Cancelling a restored draft must keep it closed after another recreation",get("pasteDialog")));
    }

    public void testSharedFileSetupSurvivesRecreationAndImportsExactlyOnce()throws Exception {
        File shared=new File(directory,"shared-game.pgn");try(FileOutputStream out=new FileOutputStream(shared)){out.write(DRAFT.getBytes(StandardCharsets.UTF_8));}
        main(()->call("handleIntent",new Class<?>[]{Intent.class},new Intent(Intent.ACTION_VIEW,Uri.fromFile(shared))));getInstrumentation().waitForIdleSync();
        main(()->{AlertDialog setup=dialog("importCollectionDialog");name(setup).setText("Shared engine archive");engine(setup).setChecked(true);});
        recreate();await(()->get("importCollectionDialog") instanceof AlertDialog,5000,"The shared file and its setup draft disappeared during recreation");
        installIsolatedRepository();
        main(()->{
            AlertDialog setup=dialog("importCollectionDialog");assertEquals("Shared engine archive",name(setup).getText().toString());assertTrue(engine(setup).isChecked());
            setup.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        await(()->repository.importedCollections().size()==1&&"imports".equals(get("libraryTab")),12000,"The restored shared file was not imported into its own collection");
        main(()->{
            GameRepository.ImportedCollection group=repository.importedCollections().get(0);assertEquals("Shared engine archive",group.name);assertTrue(group.engineGame);assertEquals(1,group.gameCount);
        });
        Field fileIo=MainActivity.class.getDeclaredField("FILE_IO");fileIo.setAccessible(true);((ExecutorService)fileIo.get(null)).submit(()->{}).get(10,TimeUnit.SECONDS);
        assertEquals("A recreated setup must not queue the same shared URI twice",1,repository.importedCollections().size());
    }

    public void testLargePasteDraftUsesSmallInstanceStateAndSurvivesRecreation()throws Exception {
        main(()->call("importCollection",new Class<?>[]{int.class,Uri.class},1,null));getInstrumentation().waitForIdleSync();
        main(()->{AlertDialog setup=dialog("importCollectionDialog");name(setup).setText("Large draft");setup.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});getInstrumentation().waitForIdleSync();
        char[] characters=new char[600000];java.util.Arrays.fill(characters,'棋');String large=new String(characters);
        main(()->text(dialog("pasteDialog")).setText(large));
        Bundle state=new Bundle();main(()->call("onSaveInstanceState",new Class<?>[]{Bundle.class},state));
        Parcel parcel=Parcel.obtain();try{parcel.writeBundle(state);assertTrue("Large draft text must not be serialized into the Binder instance-state parcel",parcel.dataSize()<256*1024);}finally{parcel.recycle();}
        recreate();await(()->get("pasteDialog") instanceof AlertDialog,5000,"The large pasted draft disappeared during recreation");
        main(()->{AlertDialog draft=dialog("pasteDialog");assertEquals(large,text(draft).getText().toString());draft.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});
        assertEquals(0,repository.importedCollections().size());
    }

    public void testPasteDraftSurvivesTwoRecreationsBeforeFileIoIsReady()throws Exception {
        main(()->call("importCollection",new Class<?>[]{int.class,Uri.class},1,null));getInstrumentation().waitForIdleSync();
        main(()->{AlertDialog setup=dialog("importCollectionDialog");name(setup).setText("Rapid rotation draft");engine(setup).setChecked(true);setup.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});getInstrumentation().waitForIdleSync();
        main(()->text(dialog("pasteDialog")).setText(DRAFT));
        Field fileIo=MainActivity.class.getDeclaredField("FILE_IO");fileIo.setAccessible(true);ExecutorService worker=(ExecutorService)fileIo.get(null);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        worker.submit(()->{entered.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(3,TimeUnit.SECONDS));
        try{
            recreateWithoutWaiting();main(()->assertEquals(Boolean.FALSE,get("loaded")));
            recreateWithoutWaiting();main(()->assertEquals(Boolean.FALSE,get("loaded")));
            release.countDown();await(()->get("pasteDialog") instanceof AlertDialog,15000,"Queued loads lost the draft after rapid repeated recreation");
            main(()->{assertEquals(DRAFT,text(dialog("pasteDialog")).getText().toString());assertEquals("Rapid rotation draft",get("pendingImportName"));assertEquals(Boolean.TRUE,get("pendingImportEngine"));dialog("pasteDialog").getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});
        }finally{release.countDown();worker.submit(()->{}).get(15,TimeUnit.SECONDS);}
        assertEquals(0,repository.importedCollections().size());
    }

    public void testRepeatedSnapshotsRestoreLatestTextAndCancelDeletesIt()throws Exception {
        openPaste("Edited draft");main(()->text(dialog("pasteDialog")).setText(DRAFT));recreate();
        await(()->get("pasteDialog") instanceof AlertDialog,5000,"First draft snapshot was not restored");
        String previous=(String)get("pasteDraftToken");draftTokens.add(previous);String latest=DRAFT+"{Latest draft edit}\n";
        main(()->text(dialog("pasteDialog")).setText(latest));recreate();
        await(()->get("pasteDialog") instanceof AlertDialog,5000,"Second draft snapshot was not restored");
        String current=(String)get("pasteDraftToken");draftTokens.add(current);assertFalse("Each saved edit must have a distinct snapshot token",previous.equals(current));
        main(()->{assertEquals(latest,text(dialog("pasteDialog")).getText().toString());dialog("pasteDialog").getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});
        await(()->get("pasteDialog")==null,5000,"Cancel did not dismiss the restored draft");awaitFileIo();assertMissingDraft(current);assertMissingDraft(previous);
        recreate();main(()->assertNull("Cancelled latest snapshot must not return",get("pasteDialog")));
    }

    public void testFailedNewSnapshotReportsErrorInsteadOfRestoringOldText()throws Exception {
        openPaste("Draft write failure");main(()->text(dialog("pasteDialog")).setText(DRAFT));recreate();
        await(()->get("pasteDialog") instanceof AlertDialog,5000,"First draft snapshot was not restored");
        String previous=(String)get("pasteDraftToken");draftTokens.add(previous);
        awaitFileIo();File cache=new File(getInstrumentation().getTargetContext().getCacheDir(),"import-drafts");
        int permissions=android.system.Os.stat(cache.getPath()).st_mode&0777;
        File probe=new File(cache,"write-probe-"+System.nanoTime()+".tmp");
        try{
            assertTrue("The failure fixture must remove directory write permissions",cache.setWritable(false,false));
            assertFalse(cache.canWrite());
            try{assertFalse("The app must not be able to create a draft in the read-only cache",probe.createNewFile());}catch(java.io.IOException expected){}
            String edited=DRAFT+"{A newer edit whose cache write fails}\n";main(()->text(dialog("pasteDialog")).setText(edited));recreate();
            await(()->get("pasteDialog") instanceof AlertDialog,10000,"Failed draft snapshot did not restore an explicit error state");
            String failed=(String)get("pasteDraftToken");draftTokens.add(failed);assertFalse(previous.equals(failed));
            main(()->{
                EditText input=text(dialog("pasteDialog"));assertEquals("A failed snapshot must never silently restore an older edit","",input.getText().toString());assertNotNull("Restoration failure must be visible",input.getError());
                dialog("pasteDialog").getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            });
            awaitFileIo();assertMissingDraft(failed);
            assertEquals("The previous successful cache survives a failed replacement for recovery",DRAFT,ImportDraftStore.read(getInstrumentation().getTargetContext(),previous));
        }finally{
            android.system.Os.chmod(cache.getPath(),permissions);if(probe.exists())assertTrue(probe.delete());awaitFileIo();
        }
    }

    public void testInvalidPastedGameRetainsEditableDraftAndCanBeCorrected()throws Exception {
        openPaste("Correction study",true);String invalid=DRAFT.replace("b2e2 b9c7 2. a0a1","a0a9");
        main(()->{AlertDialog paste=dialog("pasteDialog");text(paste).setText(invalid);paste.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
        await(()->get("pasteDialog") instanceof AlertDialog&&text(dialog("pasteDialog")).getError()!=null,12000,"Invalid pasted moves did not leave an editable error state");
        main(()->{
            AlertDialog paste=dialog("pasteDialog");assertTrue(paste.isShowing());assertEquals(invalid,text(paste).getText().toString());assertTrue(text(paste).isEnabled());
            assertTrue(paste.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());assertTrue(paste.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled());assertEquals("Correction study",get("pendingImportName"));assertEquals(Boolean.TRUE,get("pendingImportEngine"));
            assertEquals("An invalid pasted game must not create a collection",0,repository.importedCollections().size());
            text(paste).setText(DRAFT);paste.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        await(()->repository.importedCollections().size()==1&&get("pasteDialog")==null&&"imports".equals(get("libraryTab")),12000,"Correcting the retained draft did not import it");
        GameRepository.ImportedCollection group=repository.importedCollections().get(0);assertEquals("Correction study",group.name);assertTrue(group.engineGame);assertEquals(1,group.gameCount);
    }

    public void testCompletedQueuedPasteDoesNotReopenAfterRecreation()throws Exception {
        openPaste("Queued engine archive",true);main(()->text(dialog("pasteDialog")).setText(DRAFT));
        Field fileIo=MainActivity.class.getDeclaredField("FILE_IO");fileIo.setAccessible(true);ExecutorService worker=(ExecutorService)fileIo.get(null);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        worker.submit(()->{entered.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}});assertTrue(entered.await(3,TimeUnit.SECONDS));
        try{
            main(()->{
                AlertDialog paste=dialog("pasteDialog");paste.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertFalse("Checking a captured record must prevent edits that would then be discarded",text(paste).isEnabled());
                assertFalse(paste.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());assertFalse(paste.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled());
            });
            Activity originalActivity=activity;recreateWithoutWaiting();
            Field token=MainActivity.class.getDeclaredField("pasteDraftToken");token.setAccessible(true);String cached=(String)token.get(originalActivity);if(cached!=null&&!cached.isEmpty())draftTokens.add(cached);
            // The queued replacement load reads this repository field only once
            // the latch is released, keeping the completed import cache-isolated.
            main(()->set("repository",repository));release.countDown();
            await(()->Boolean.TRUE.equals(get("loaded"))&&"imports".equals(get("libraryTab"))&&repository.importedCollections().size()==1,15000,"Queued pasted import did not route to its collection after recreation");
            main(()->{assertNull("A successfully committed paste must not reopen as an obsolete editable draft",get("pasteDialog"));assertEquals("Queued engine archive",repository.importedCollections().get(0).name);assertTrue(repository.importedCollections().get(0).engineGame);});
            worker.submit(()->{}).get(15,TimeUnit.SECONDS);assertEquals(1,repository.importedCollections().size());
        }finally{release.countDown();worker.submit(()->{}).get(15,TimeUnit.SECONDS);}
    }

    public void testDraftStoreRoundTripByteLimitAndTokenBoundary()throws Exception {
        Context target=getInstrumentation().getTargetContext();String token=ImportDraftStore.newToken();draftTokens.add(token);
        String text="Chinese 棋譜\nQuoted \"record\"\r\n"+DRAFT;ImportDraftStore.write(target,token,text);assertEquals(text,ImportDraftStore.read(target,token));
        char[] tooManyBytes=new char[GameRepository.MAX_BYTES/3+1];java.util.Arrays.fill(tooManyBytes,'棋');
        try{ImportDraftStore.write(target,token,new String(tooManyBytes));fail("The UTF-8 byte limit must preserve the existing draft");}catch(IllegalArgumentException expected){}
        assertEquals(text,ImportDraftStore.read(target,token));
        for(String bad:new String[]{"../outside","../../imported-games","not-a-token",token+"/extra"}){
            try{ImportDraftStore.write(target,bad,DRAFT);fail("A draft token must not select a path");}catch(IllegalArgumentException expected){}
            try{ImportDraftStore.read(target,bad);fail("Invalid read token accepted");}catch(IllegalArgumentException expected){}
            try{ImportDraftStore.delete(target,bad);fail("Invalid delete token accepted");}catch(IllegalArgumentException expected){}
        }
        ImportDraftStore.delete(target,token);try{ImportDraftStore.read(target,token);fail("Cancel cleanup must remove the saved draft");}catch(java.io.FileNotFoundException expected){}
    }

    public void testPgnLeadingBraceCommentImportsWithoutChangingFormat()throws Exception {
        assertEquals("A leading PGN comment must not be mistaken for JSON",1,repository.importText("{Notes before the PGN headers}\n"+DRAFT,"Commented PGN",false));
        assertEquals(1,repository.importedCollections().size());
    }

    private void openPaste(String collection){openPaste(collection,false);}
    private void openPaste(String collection,boolean engineGames){main(()->call("importCollection",new Class<?>[]{int.class,Uri.class},1,null));getInstrumentation().waitForIdleSync();main(()->{AlertDialog setup=dialog("importCollectionDialog");name(setup).setText(collection);engine(setup).setChecked(engineGames);setup.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});getInstrumentation().waitForIdleSync();}
    private void awaitFileIo()throws Exception{getInstrumentation().waitForIdleSync();Field field=MainActivity.class.getDeclaredField("FILE_IO");field.setAccessible(true);ExecutorService worker=(ExecutorService)field.get(null);worker.submit(()->{}).get(15,TimeUnit.SECONDS);getInstrumentation().waitForIdleSync();worker.submit(()->{}).get(15,TimeUnit.SECONDS);}
    private void assertMissingDraft(String token)throws Exception{try{ImportDraftStore.read(getInstrumentation().getTargetContext(),token);fail("Draft cache file was not removed: "+token);}catch(java.io.FileNotFoundException expected){}}

    private void installIsolatedRepository(){main(()->{set("repository",repository);set("games",repository.all());});getInstrumentation().waitForIdleSync();}
    private AlertDialog dialog(String field){AlertDialog dialog=(AlertDialog)get(field);assertNotNull("Missing "+field,dialog);return dialog;}
    private EditText name(AlertDialog dialog){return (EditText)findDescription(dialog.getWindow().getDecorView(),"Import collection name");}
    private RadioButton engine(AlertDialog dialog){return (RadioButton)findDescription(dialog.getWindow().getDecorView(),"Import engine games");}
    private EditText text(AlertDialog dialog){return (EditText)findDescription(dialog.getWindow().getDecorView(),"Game record text");}
    private void recreate()throws Exception {
        recreateWithoutWaiting();
        await(()->Boolean.TRUE.equals(get("loaded")),15000,"Recreated library did not load");getInstrumentation().waitForIdleSync();
    }
    private void recreateWithoutWaiting()throws Exception {
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);main(activity::recreate);
        Activity replacement=monitor.waitForActivityWithTimeout(10000);getInstrumentation().removeMonitor(monitor);assertNotNull("Activity must recreate",replacement);activity=replacement;
    }
    private Object get(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private void set(String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}catch(Exception error){throw new RuntimeException(error);}}
    private Object call(String name,Class<?>[] types,Object... args){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);}catch(Exception error){throw new RuntimeException(error);}}
    private void main(Runnable work){UiTestActions.runOnMain(getInstrumentation(),work);}
    private void await(BooleanSupplier condition,long timeout,String message)throws Exception {long deadline=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};while(SystemClock.uptimeMillis()<deadline){main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(40);}fail(message);}
    private static View findDescription(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){String key=item.getKey();Object value=item.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> set=(Set<String>)value;edit.putStringSet(key,set);}}edit.commit();}
}
