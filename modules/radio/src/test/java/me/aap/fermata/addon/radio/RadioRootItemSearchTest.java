package me.aap.fermata.addon.radio;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RadioRootItemSearchTest {

	@Test
	public void searchReturnsLocalMatchesAndRemoteStationsMerged() throws Exception {
		Context context = RuntimeEnvironment.getApplication();
		RadioSourceStore store = new RadioSourceStore(context);
		store.save(RadioSource.create("BBC Radio 1", "http://stream.test/bbc1"));

		RadioBrowserStation remoteStation = new RadioBrowserStation("uuid-world", "BBC World Service",
				"http://stream.test/bbcworld", "", "", "", "", "United Kingdom", "GB",
				"English", "mp3", 128, 50, 500);

		RadioBrowserApi fakeApi = new RadioBrowserApi() {
			@Override
			FutureSupplier<List<RadioBrowserStation>> searchByName(String query, int offset, int limit) {
				assertEquals(50, limit);
				return completed(Collections.singletonList(remoteStation));
			}
		};

		RadioRootItem root = createRoot(context, fakeApi, store);
		List<? extends Item> results = root.search("BBC").get();

		assertNotNull(results);
		assertEquals(2, results.size());
		assertEquals("BBC Radio 1", results.get(0).getName());
		assertEquals("BBC World Service", results.get(1).getName());
	}

	@Test
	public void searchFallsBackToLocalMatchesWhenApiFails() throws Exception {
		Context context = RuntimeEnvironment.getApplication();
		RadioSourceStore store = new RadioSourceStore(context);
		store.save(RadioSource.create("BBC Radio 1", "http://stream.test/bbc1"));

		RadioBrowserApi failingApi = new RadioBrowserApi() {
			@Override
			FutureSupplier<List<RadioBrowserStation>> searchByName(String query, int offset, int limit) {
				return failed(new IOException("Simulated network timeout"));
			}
		};

		RadioRootItem root = createRoot(context, failingApi, store);
		List<? extends Item> results = root.search("BBC").get();

		assertNotNull(results);
		assertEquals(1, results.size());
		assertEquals("BBC Radio 1", results.get(0).getName());
	}

	@Test
	public void searchReturnsEmptyWhenNeitherLocalNorRemoteMatches() throws Exception {
		Context context = RuntimeEnvironment.getApplication();
		RadioSourceStore store = new RadioSourceStore(context);
		store.save(RadioSource.create("Rock FM", "http://stream.test/rock"));

		RadioBrowserApi emptyApi = new RadioBrowserApi() {
			@Override
			FutureSupplier<List<RadioBrowserStation>> searchByName(String query, int offset, int limit) {
				return completed(Collections.emptyList());
			}
		};

		RadioRootItem root = createRoot(context, emptyApi, store);
		List<? extends Item> results = root.search("Jazz").get();

		assertNotNull(results);
		assertTrue(results.isEmpty());
	}

	private static RadioRootItem createRoot(Context context, RadioBrowserApi api, RadioSourceStore store) {
		MediaLib lib = (MediaLib) Proxy.newProxyInstance(
				MediaLib.class.getClassLoader(),
				new Class<?>[]{MediaLib.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "getContext" -> context;
					case "getId" -> "Radio";
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					default -> null;
				});

		return new RadioRootItem(lib, api, store);
	}
}
