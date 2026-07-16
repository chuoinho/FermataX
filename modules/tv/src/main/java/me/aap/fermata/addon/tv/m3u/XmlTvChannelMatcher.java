package me.aap.fermata.addon.tv.m3u;

import static java.lang.Character.NON_SPACING_MARK;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.computeIfAbsent;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

final class XmlTvChannelMatcher {
	private XmlTvChannelMatcher() {
	}

	static FutureSupplier<Void> collect(BrowsableItem item,
																		 Map<String, List<TvM3uTrackItem>> byId,
																		 Map<String, List<TvM3uTrackItem>> byName,
																		 BooleanSupplier closed) {
		if (closed.getAsBoolean()) return completedVoid();
		return item.getUnsortedChildren().then(children -> Async.forEach(child -> {
			if (closed.getAsBoolean()) return completedVoid();
			if (child instanceof TvM3uTrackItem track) {
				String id = track.getTvgId();
				if (id != null) computeIfAbsent(byId, id, key -> new ArrayList<>(1)).add(track);
				id = track.getTvgName();
				if (id != null) {
					computeIfAbsent(byName, normalizeName(id), key -> new ArrayList<>(1)).add(track);
				}
				computeIfAbsent(byName, normalizeName(track.getName()), key -> new ArrayList<>(1))
						.add(track);
				return completedVoid();
			}
			return collect((BrowsableItem) child, byId, byName, closed);
		}, children));
	}

	static String normalizeName(String name) {
		name = Normalizer.normalize(name, Normalizer.Form.NFD);
		try (SharedTextBuilder builder = SharedTextBuilder.get()) {
			for (int i = 0, size = name.length(); i < size; i++) {
				char value = name.charAt(i);
				if (Character.getType(value) != NON_SPACING_MARK) {
					builder.append(Character.toLowerCase(value));
				}
			}
			return builder.toString();
		}
	}
}
