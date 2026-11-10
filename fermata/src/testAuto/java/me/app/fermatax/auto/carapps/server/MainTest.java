package me.app.fermatax.auto.carapps.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainTest {

	/** The shape of ContentProviderHolder: a public ProviderInfo field. */
	public static final class Holder {
		public ProviderInfo info;

		Holder(int uid) {
			if (uid < 0) return;
			info = new ProviderInfo();
			info.applicationInfo = new ApplicationInfo();
			info.applicationInfo.uid = uid;
		}
	}

	/**
	 * A reinstall (same version) gives the app a new uid; the old server's caller check would refuse every call of the
	 * new app. Answering its ping, such a server must exit instead, so the client's wait ends in one normal start.
	 */
	@Test
	public void aServerOfAReinstalledAppKnowsItIsStale() {
		assertTrue(Main.isOtherApp(10512, new Holder(10600)));
		assertFalse(Main.isOtherApp(10512, new Holder(10512)));
		assertFalse("unknown: hand over as before", Main.isOtherApp(10512, new Holder(-1)));
		assertFalse(Main.isOtherApp(10512, new Object()));
	}
}
