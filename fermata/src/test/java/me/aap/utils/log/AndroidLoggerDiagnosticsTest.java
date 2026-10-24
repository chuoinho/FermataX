package me.aap.utils.log;

import static org.junit.Assert.*;
import me.aap.utils.app.App;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = AndroidLoggerDiagnosticsTest.CallerApp.class)
public class AndroidLoggerDiagnosticsTest {
    public static class CallerApp extends App {
        boolean detailed;
        StackTraceElement caller;
        public boolean isLogFailureCallerEnabled() { return detailed; }
        @Override public void onLogFailure(Log.Level level, Throwable error, StackTraceElement caller) {
            this.caller = caller;
        }
    }
    private static class CountingLogger extends AndroidLogger {
        int walks;
        @Override protected StackTraceElement callerFrame(int depth) {
            walks++;
            return new StackTraceElement("Example", "caller", "Example.java", 1);
        }
    }
    @Test public void warningAndErrorSkipCallerWalkUntilDetailedIsEnabled() {
        CallerApp app = (CallerApp) RuntimeEnvironment.getApplication();
        CountingLogger logger = new CountingLogger();
        logger.logWarn(new StringBuilder());
        logger.logWarn(new StringBuilder(), new IllegalStateException());
        logger.logError(new StringBuilder());
        logger.logError(new StringBuilder(), new IllegalStateException());
        assertEquals(0, logger.walks);
        assertNull(app.caller);
        app.detailed = true;
        logger.logWarn(new StringBuilder());
        logger.logError(new StringBuilder(), new IllegalStateException());
        assertEquals(2, logger.walks);
        assertEquals("caller", app.caller.getMethodName());
    }
}
