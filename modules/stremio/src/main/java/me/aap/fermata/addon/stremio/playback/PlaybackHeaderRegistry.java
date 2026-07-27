package me.aap.fermata.addon.stremio.playback;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;
import me.aap.fermata.media.net.PlaybackHeaderResolver;
import me.aap.fermata.media.net.PlaybackRequestProfile.HeaderReference;
import me.aap.fermata.media.net.PlaybackRequestValidationException;

/** Stores request headers outside descriptors and returns an opaque, expiring lookup reference. */
@FunctionalInterface
public interface PlaybackHeaderRegistry {
	HeaderReference register(
			String providerSourceUuid, String descriptorId, Map<String, String> headers,
			long expiresAtEpochMillis);

	/** Runtime-owned in-memory store. Header values never enter descriptors or persistence. */
	final class HeaderStore implements PlaybackHeaderRegistry,
			PlaybackHeaderResolver, AutoCloseable {
		static final int MAX_ENTRIES = 256;
		private static final int MAX_HEADERS_PER_ENTRY = 32;
		private static final int MAX_HEADER_CHARS_PER_ENTRY = 16 * 1024;
		private final LinkedHashMap<String, HeaderEntry> entries =
				new LinkedHashMap<>(16, 0.75f, true);
		private final LongSupplier clock;
		private Map<String, SourceOwnership> sourceOwnerships;
		private long sourceRevision = -1L;
		private boolean closed;

		public HeaderStore() {
			this(System::currentTimeMillis);
		}

		public HeaderStore(LongSupplier clock) {
			this.clock = Objects.requireNonNull(clock, "clock");
		}

		@Override
		public synchronized HeaderReference register(String providerSourceUuid,
				String descriptorId, Map<String, String> values, long expiresAtEpochMillis) {
			if (closed) throw new IllegalStateException("Playback header store is closed");
			providerSourceUuid = requireText(providerSourceUuid, "providerSourceUuid");
			descriptorId = requireText(descriptorId, "descriptorId");
			Objects.requireNonNull(values, "values");
			SourceOwnership ownership = null;
			if (sourceOwnerships != null) {
				ownership = sourceOwnerships.get(providerSourceUuid);
				if ((ownership == null) || !ownership.enabled) {
					throw new IllegalStateException("Playback header provider is unavailable");
				}
			}
			long now = clock.getAsLong();
			if (expiresAtEpochMillis <= now) {
				throw new IllegalArgumentException("Playback headers must expire in the future");
			}
			if (values.size() > MAX_HEADERS_PER_ENTRY) {
				throw new IllegalArgumentException("Too many playback headers");
			}
			int chars = 0;
			for (Map.Entry<String, String> value : values.entrySet()) {
				chars = Math.addExact(chars, value.getKey().length());
				chars = Math.addExact(chars, value.getValue().length());
				if (chars > MAX_HEADER_CHARS_PER_ENTRY) {
					throw new IllegalArgumentException("Playback headers are too large");
				}
			}
			entries.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
			String id = UUID.randomUUID().toString();
			entries.put(id, new HeaderEntry(providerSourceUuid, descriptorId,
					ownership, Map.copyOf(values), expiresAtEpochMillis));
			while (entries.size() > MAX_ENTRIES) {
				entries.remove(entries.entrySet().iterator().next().getKey());
			}
			return HeaderReference.of(id);
		}

		@Override
		public synchronized Map<String, String> resolve(HeaderReference reference)
				throws PlaybackRequestValidationException {
			if (closed) {
				throw new PlaybackRequestValidationException("Playback header store is closed");
			}
			if (reference == null) {
				throw new PlaybackRequestValidationException("Playback header reference is missing");
			}

			String id = reference.getOpaqueId();
			HeaderEntry entry = entries.get(id);
			if (entry == null) {
				throw new PlaybackRequestValidationException(
						"Playback header reference is unavailable");
			}
			if (clock.getAsLong() >= entry.expiresAt) {
				entries.remove(id);
				throw new PlaybackRequestValidationException(
						"Playback header reference has expired");
			}
			return entry.values;
		}

		/** Revokes headers when their owning source is unavailable or changes transport policy. */
		public synchronized void reconcileSources(StremioSourceSnapshot snapshot) {
			Objects.requireNonNull(snapshot, "snapshot");
			if (closed || (snapshot.revision() <= sourceRevision)) return;

			Map<String, SourceOwnership> current = new HashMap<>();
			for (StremioSourceRecord source : snapshot.sources()) {
				current.put(source.sourceUuid(), SourceOwnership.of(source));
			}
			Map<String, SourceOwnership> previous = sourceOwnerships;
			entries.entrySet().removeIf(stored -> {
				HeaderEntry entry = stored.getValue();
				SourceOwnership ownership = current.get(entry.providerSourceUuid);
				if ((ownership == null) || !ownership.enabled) return true;
				if (entry.ownership != null) return !entry.ownership.equals(ownership);
				SourceOwnership prior = (previous == null) ? null :
						previous.get(entry.providerSourceUuid);
				return (prior != null) && !prior.equals(ownership);
			});
			sourceOwnerships = Map.copyOf(current);
			sourceRevision = snapshot.revision();
		}

		@Override
		public synchronized void close() {
			closed = true;
			entries.clear();
			sourceOwnerships = null;
		}

		synchronized int size() {
			return entries.size();
		}

		private static String requireText(String value, String field) {
			Objects.requireNonNull(value, field);
			if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
			return value;
		}

		private record HeaderEntry(
				String providerSourceUuid,
				String descriptorId,
				SourceOwnership ownership,
				Map<String, String> values,
				long expiresAt) {
		}

		private record SourceOwnership(
				String transportFingerprint,
				String secretRef,
				boolean enabled,
				boolean allowCleartext,
				boolean allowLan) {
			private static SourceOwnership of(StremioSourceRecord source) {
				return new SourceOwnership(source.transportFingerprint(), source.secretRef(),
						source.enabled(), source.allowCleartext(), source.allowLan());
			}
		}
	}
}
