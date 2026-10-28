package org.xiangqi.study;

import android.test.InstrumentationTestCase;

/** A failed UI assertion must reach JUnit while the app's main thread survives. */
public final class UiTestActionsTest extends InstrumentationTestCase {
    public void testMainThreadAssertionIsRethrownOnInstrumentationThread(){
        AssertionError expected=new AssertionError("isolated harness failure");boolean caught=false;
        try{UiTestActions.runOnMain(getInstrumentation(),()->{throw expected;});}
        catch(AssertionError actual){assertSame(expected,actual);caught=true;}
        assertTrue("JUnit must receive the UI assertion",caught);
        final boolean[] responsive={false};UiTestActions.runOnMain(getInstrumentation(),()->responsive[0]=true);
        assertTrue("The app's main thread must still accept cleanup",responsive[0]);
    }
}
