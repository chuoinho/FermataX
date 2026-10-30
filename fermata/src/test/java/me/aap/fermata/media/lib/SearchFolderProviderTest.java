package me.aap.fermata.media.lib;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;

@RunWith(RobolectricTestRunner.class)
public class SearchFolderProviderTest {

	@Test
	public void searchProviderReturnsRemoteWhenNoLocalResultsWithoutNpe() throws Exception {
		MediaLib lib = createFakeMediaLib();
		PlayableItem remoteItem = createPlayableItem("Remote Station 1");
		AtomicInteger childrenCallCount = new AtomicInteger();

		BrowsableItem root = createFakeSearchProviderRoot(lib, childrenCallCount,
				query -> futureItems(Collections.singletonList(remoteItem)));

		SearchFolder folder = SearchFolder.search("test", items -> root).get();

		assertNotNull("SearchFolder should not be null", folder);
		List<PlayableItem> found = folder.getItemsFound();
		assertNotNull(found);
		assertEquals(1, found.size());
		assertEquals("Remote Station 1", found.get(0).getName());
	}

	@Test
	public void searchProviderRootDoesNotTraverseChildren() throws Exception {
		MediaLib lib = createFakeMediaLib();
		AtomicInteger childrenCallCount = new AtomicInteger();

		BrowsableItem root = createFakeSearchProviderRoot(lib, childrenCallCount,
				query -> futureItems(Collections.emptyList()));

		SearchFolder folder = SearchFolder.search("test", items -> root).get();

		assertNotNull(folder);
		assertEquals("SearchProvider root must not have its children traversed",
				0, childrenCallCount.get());
	}

	@Test
	public void searchProviderHandlesNullOrEmptyResultsSafely() throws Exception {
		MediaLib lib = createFakeMediaLib();
		AtomicInteger childrenCallCount = new AtomicInteger();

		BrowsableItem root = createFakeSearchProviderRoot(lib, childrenCallCount,
				query -> completedNull());

		SearchFolder folder = SearchFolder.search("test", items -> root).get();

		assertNotNull(folder);
		assertTrue(folder.getItemsFound().isEmpty());
	}

	@SuppressWarnings("unchecked")
	private static FutureSupplier<List<? extends Item>> futureItems(List<? extends Item> items) {
		return (FutureSupplier<List<? extends Item>>) (FutureSupplier<?>) completed(items);
	}

	private static PlayableItem createPlayableItem(String name) {
		MetadataBuilder meta = new MetadataBuilder();
		meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, name);
		MediaMetadataCompat mmc = meta.build();
		MediaDescriptionCompat mdc = new MediaDescriptionCompat.Builder()
				.setTitle(name)
				.build();

		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getName" -> name;
					case "getMediaMetadata" -> completed(mmc);
					case "getMediaDescription" -> completed(mdc);
					default -> null;
				});
	}

	private static BrowsableItem createFakeSearchProviderRoot(
			MediaLib lib, AtomicInteger childrenCallCount,
			Function<String, FutureSupplier<List<? extends Item>>> searchFunc) {
		return (BrowsableItem) Proxy.newProxyInstance(
				SearchFolderProviderTest.class.getClassLoader(),
				new Class<?>[]{BrowsableItem.class, SearchFolder.SearchProvider.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "getId" -> "FakeRoot";
					case "getName" -> "FakeRoot";
					case "getLib" -> lib;
					case "getRoot" -> proxy;
					case "getParent" -> null;
					case "search" -> searchFunc.apply((String) args[0]);
					case "getChildren", "getUnsortedChildren" -> {
						childrenCallCount.incrementAndGet();
						yield completed(emptyList());
					}
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					default -> null;
				}
		);
	}

	@SuppressWarnings("unchecked")
	private static MediaLib createFakeMediaLib() {
		Map<String, Item> cache = new HashMap<>();
		Context context = RuntimeEnvironment.getApplication();

		return (MediaLib) Proxy.newProxyInstance(
				SearchFolderProviderTest.class.getClassLoader(),
				new Class<?>[]{MediaLib.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "getCachedItem" -> cache.get(args[0]);
					case "getOrCreateCachedItem" -> {
						String id = (String) args[0];
						me.aap.utils.function.Function<String, Item> creator =
								(me.aap.utils.function.Function<String, Item>) args[1];
						yield cache.computeIfAbsent(id, creator::apply);
					}
					case "getContext" -> context;
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					default -> null;
				}
		);
	}
}
