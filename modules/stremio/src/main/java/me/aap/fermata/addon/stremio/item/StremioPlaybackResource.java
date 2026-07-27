package me.aap.fermata.addon.stremio.item;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.subtitle.SubtitleDescriptor;
import me.aap.fermata.addon.stremio.subtitle.SubtitleFormat;
import me.aap.fermata.addon.stremio.subtitle.StremioSubtitleSession;
import me.aap.fermata.media.engine.SubtitleTrackFile;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.io.AsyncInputStream;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.generic.GenericFileSystem;

/** Synthetic media resource exposing lazy Stremio subtitle sidecars to existing engines. */
final class StremioPlaybackResource implements VirtualResource {
	private final Rid rid;
	private final SubtitleFolder parent;

	StremioPlaybackResource(PlaybackDescriptor descriptor, StremioItemGateway gateway,
			StreamAggregationRequest request) {
		String location = descriptor.targetValue();
		if (location == null) {
			location = "http://127.0.0.1/stremio-pending/" + descriptor.descriptorId();
		}
		rid = Rid.create(location);
		parent = new SubtitleFolder(descriptor, request, gateway);
	}

	void updateDescriptor(PlaybackDescriptor descriptor) {
		parent.updateDescriptor(descriptor);
	}

	void activateSubtitles() {
		parent.session.activate(parent.request.identity().videoKey());
	}

	void cancelSubtitles() {
		parent.session.cancel();
	}

	@NonNull
	@Override
	public VirtualFileSystem getVirtualFileSystem() {
		return GenericFileSystem.getInstance();
	}

	@NonNull
	@Override
	public String getName() {
		return "media.mp4";
	}

	@NonNull
	@Override
	public Rid getRid() {
		return rid;
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualFolder> getParent() {
		return completed(parent);
	}

	private static final class SubtitleFolder implements VirtualFolder {
		private final StreamAggregationRequest request;
		private volatile PlaybackDescriptor descriptor;
		private final StremioItemGateway gateway;
		private final Rid rid;
		private final StremioSubtitleSession session;
		private volatile FutureSupplier<List<VirtualResource>> children;

		private SubtitleFolder(PlaybackDescriptor descriptor, StreamAggregationRequest request,
				StremioItemGateway gateway) {
			this.descriptor = descriptor;
			this.request = request;
			this.gateway = gateway;
			rid = Rid.create("stremio-subtitles:" + request.identity().videoKey());
			session = new StremioSubtitleSession(request.identity().videoKey());
		}

		private synchronized void updateDescriptor(PlaybackDescriptor descriptor) {
			if (this.descriptor == descriptor) return;
			this.descriptor = descriptor;
			FutureSupplier<List<VirtualResource>> previous = children;
			children = null;
			if ((previous != null) && !previous.isDone()) previous.cancel();
		}

		@Override
		public synchronized FutureSupplier<List<VirtualResource>> getChildren() {
			if (children != null) return children;
			PlaybackDescriptor playback = descriptor;
			FutureSupplier<List<VirtualResource>> loaded = session.discover(() ->
					gateway.subtitles(playback, request.identities(), request.type(),
							request.videoId())).map(result -> {
				List<VirtualResource> files = new ArrayList<>();
				int index = 0;
				for (SubtitleDescriptor descriptor : result.subtitles()) {
					if (!isEngineSupported(descriptor) || !descriptor.isPlayable(Instant.now())) {
						continue;
					}
					files.add(new SubtitleFile(this, descriptor, gateway, index++));
				}
				return List.copyOf(files);
			});
			children = loaded;
			// Provider bootstrap may race the first player request. Do not permanently
			// cache an empty/failed snapshot; the next menu open must be able to retry.
			loaded.onCompletion((files, error) -> {
				if ((descriptor != playback) || (error != null) ||
						((files != null) && files.isEmpty())) {
					synchronized (SubtitleFolder.this) {
						if (children == loaded) children = null;
					}
				}
			});
			return loaded;
		}

		@NonNull
		@Override
		public VirtualFileSystem getVirtualFileSystem() {
			return GenericFileSystem.getInstance();
		}

		@NonNull
		@Override
		public String getName() {
			return "stremio-subtitles";
		}

		@NonNull
		@Override
		public Rid getRid() {
			return rid;
		}
	}

	private static final class SubtitleFile implements SubtitleTrackFile {
		private final SubtitleFolder parent;
		private final SubtitleDescriptor descriptor;
		private final StremioItemGateway gateway;
		private final String name;
		private final Rid rid;
		private final long trackId;

		private SubtitleFile(SubtitleFolder parent, SubtitleDescriptor descriptor,
				StremioItemGateway gateway, int index) {
			this.parent = parent;
			this.descriptor = descriptor;
			this.gateway = gateway;
			name = "media." + language(descriptor) + '-' + index + extension(descriptor.format());
			rid = Rid.create("stremio-subtitle:" + descriptor.identity() + ':' + index);
			trackId = stableTrackId(descriptor.identity());
		}

		@NonNull
		@Override
		public VirtualFileSystem getVirtualFileSystem() {
			return GenericFileSystem.getInstance();
		}

		@NonNull
		@Override
		public String getName() {
			return name;
		}

		@NonNull
		@Override
		public Rid getRid() {
			return rid;
		}

		@Override
		public long getSubtitleTrackId() {
			return trackId;
		}

		@Override
		public String getSubtitleLanguage() {
			return descriptor.language().tag();
		}

		@Override
		public String getSubtitleDescription() {
			return descriptor.providerLabel();
		}

		@NonNull
		@Override
		public FutureSupplier<VirtualFolder> getParent() {
			return completed(parent);
		}

		@Override
		public FutureSupplier<Long> getLength() {
			return completed(Math.max(0L, descriptor.declaredSizeBytes()));
		}

		@Override
		public AsyncInputStream getInputStream(long offset) throws IOException {
			if (offset != 0L) throw new IOException("Stremio subtitle seeking is unsupported");
			return AsyncInputStream.from(loadCurrentDescriptor().map(bytes ->
					AsyncInputStream.from(new ByteArrayInputStream(bytes))));
		}

		private FutureSupplier<byte[]> loadCurrentDescriptor() {
			return parent.session.load(() -> loadCurrentDescriptorOwned());
		}

		private FutureSupplier<byte[]> loadCurrentDescriptorOwned() {
			if (descriptor.isPlayable(Instant.now())) return gateway.loadSubtitle(descriptor);
			return gateway.subtitles(parent.descriptor, parent.request.identities(),
					parent.request.type(), parent.request.videoId()).then(result -> {
				for (SubtitleDescriptor refreshed : result.subtitles()) {
					if (descriptor.identity().equals(refreshed.identity()) &&
							refreshed.isPlayable(Instant.now())) {
						return gateway.loadSubtitle(refreshed);
					}
				}
				return me.aap.utils.async.Completed.failed(
						new IOException("Stremio subtitle is no longer available"));
			});
		}
	}

	private static boolean isEngineSupported(SubtitleDescriptor descriptor) {
		return descriptor.format().isSupported() ||
				descriptor.format().isEngineReadable(descriptor.location());
	}

	private static String extension(SubtitleFormat format) {
		return (format == SubtitleFormat.WEBVTT) ? ".vtt" : ".srt";
	}

	private static String language(SubtitleDescriptor descriptor) {
		String language = descriptor.language().tag().toLowerCase(Locale.ROOT);
		StringBuilder safe = new StringBuilder(language.length());
		for (int i = 0; i < language.length(); i++) {
			char c = language.charAt(i);
			if (Character.isLetterOrDigit(c) || (c == '-')) safe.append(c);
		}
		return safe.isEmpty() ? "und" : safe.toString();
	}

	static long stableTrackId(String identity) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
					.digest(identity.getBytes(StandardCharsets.UTF_8));
			return 0x4000_0000_0000_0000L |
					(ByteBuffer.wrap(hash).getLong() & 0x3fff_ffff_ffff_ffffL);
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}
}
