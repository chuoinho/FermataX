package me.aap.fermata.diagnostics.export;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class DiagnosticTripExportRaceTest {
    @Test public void disappearingTripDoesNotAbortOtherReportEntries() throws Exception {
        Path dir = Files.createTempDirectory("export-trip-race");
        Path gone = dir.resolve("gone.csv");
        Path kept = dir.resolve("kept.csv");
        Files.write(gone, "#fermatax-trip-v2\nE,1,start\n".getBytes(UTF_8));
        Files.write(kept, "#fermatax-trip-v2\nE,2,start\n".getBytes(UTF_8));
        File disappearing = new File(gone.toString()) {
            int reads;
            @Override public Path toPath() {
                // First call validates the candidate; second call reads the selected file.
                if (++reads == 2) {
                    try { Files.delete(gone); } catch (java.io.IOException e) { throw new AssertionError(e); }
                }
                return super.toPath();
            }
        };
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public File getExternalFilesDir(String type) {
                return new File(dir.toString()) {
                    @Override public File[] listFiles(FilenameFilter filter) {
                        return new File[]{disappearing, kept.toFile()};
                    }
                };
            }
        };
        File report = DiagnosticReportExporter.create(context, null);
        try (ZipFile zip = new ZipFile(report, UTF_8)) {
            assertNotNull(zip.getEntry("manifest.json"));
            assertNull(zip.getEntry("trip/gone.csv"));
            assertNotNull(zip.getEntry("trip/kept.csv"));
        }
    }
}
