package me.aap.fermata.diagnostics.android;

import static org.junit.Assert.*;
import android.app.Application;
import java.util.List;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.pref.DiagnosticsPreferences;
import me.aap.utils.log.Log;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class LegacyLogRetentionTest {
    @Test public void detailedErrorsAlsoKeepRateLimitedEssentialEvents() throws Exception {
        AndroidDiagnosticsRuntime.resetForTesting();
        Application context = RuntimeEnvironment.getApplication();
        DiagnosticsPreferences prefs = new DiagnosticsPreferences(context);
        prefs.setDetailedEnabled(true, System.currentTimeMillis());
        AndroidDiagnosticsRuntime runtime = AndroidDiagnosticsRuntime.install(context);
        try {
            runtime.awaitIdleForTesting();
            assertTrue(runtime.isDetailedEnabled());
            FermataApplication app = new FermataApplication();
            StackTraceElement caller = new StackTraceElement("Example", "run", "Example.java", 1);
            for (int i = 0; i < 8; i++) app.onLogFailure(Log.Level.ERROR, null, caller);
            List<String> events = runtime.getRecorder().snapshotBreadcrumbs();
            assertEquals(events.toString(), 5, events.stream().filter(e -> e.contains("\"category\":\"legacy_log\"")
                    && e.contains("\"scope\":\"essential\"")).count());
            assertTrue(events.stream().anyMatch(e -> e.contains("\"category\":\"log\"")
                    && e.contains("\"scope\":\"detailed\"")));
        } finally { AndroidDiagnosticsRuntime.resetForTesting(); }
    }
}
