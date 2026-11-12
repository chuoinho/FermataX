package me.aap.fermata.carfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class CarFinderPrefsPhotoTest {

	@Test
	public void photoInExternalStorageMovesToPrivateStorage() throws IOException {
		Context ctx = RuntimeEnvironment.getApplication();
		File oldDir = new File(ctx.getExternalFilesDir(null), "parking_photos");
		File current = write(new File(oldDir, "spot_2.jpg"));
		File stale = write(new File(oldDir, "spot_1.jpg"));
		CarParkingLocation loc = new CarParkingLocation(50.0, 30.0, 5f, 1000L);
		loc.setPhotoPath(current.getAbsolutePath());
		CarFinderPrefs.saveParkingLocation(ctx, loc);

		File moved = new File(CarFinderPrefs.getPhotoDir(ctx), "spot_2.jpg");
		assertEquals(moved.getAbsolutePath(), CarFinderPrefs.getParkingLocation(ctx).getPhotoPath());
		assertTrue(moved.isFile());
		assertFalse(current.exists());
		assertFalse(stale.exists());
		// persisted, so the next read does no file work
		assertEquals(moved.getAbsolutePath(), CarFinderPrefs.getParkingLocation(ctx).getPhotoPath());
	}

	@Test
	public void missingExternalPhotoIsDropped() {
		Context ctx = RuntimeEnvironment.getApplication();
		CarParkingLocation loc = new CarParkingLocation(50.0, 30.0, 5f, 1000L);
		loc.setPhotoPath(new File(ctx.getExternalFilesDir(null), "parking_photos/gone.jpg").getAbsolutePath());
		CarFinderPrefs.saveParkingLocation(ctx, loc);

		assertEquals("", CarFinderPrefs.getParkingLocation(ctx).getPhotoPath()); // "" means no photo
	}

	private static File write(File f) throws IOException {
		f.getParentFile().mkdirs();
		try (FileOutputStream out = new FileOutputStream(f)) {
			out.write(new byte[]{1, 2, 3});
		}
		return f;
	}
}
