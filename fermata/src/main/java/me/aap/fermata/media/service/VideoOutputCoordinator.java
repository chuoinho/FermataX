package me.aap.fermata.media.service;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.ui.view.VideoView;

/**
 * Owns the single decoder-output target selected from the currently available video surfaces.
 *
 * <p>Registering or removing a non-winning target is deliberately inert. Rebinding a decoder can
 * detach a native VLC vout, so it is valid only when the selected target or bound engine changes.
 * The generation lets asynchronous surface work prove that it still belongs to the same target.</p>
 */
public final class VideoOutputCoordinator {
	private final List<Registration> targets = new ArrayList<>(2);
	@Nullable
	private VideoView selected;
	@Nullable
	private MediaEngine boundEngine;
	@Nullable
	private MediaEngine requestedEngine;
	private boolean requestedVideo;
	@Nullable
	private MediaEngine sourceEngine;
	private long generation;
	private long sourceOutputGeneration = -1L;
	private long sourceGeneration;

	void add(VideoView view, int priority) {
		for (Registration target : targets) {
			if (target.view == view) return;
		}
		targets.add(new Registration(view, priority, targets.size()));
		reconcile();
	}

	void remove(VideoView view) {
		boolean removed = targets.removeIf(target -> target.view == view);
		if (!removed) return;
		reconcile();
	}

	void bind(@Nullable MediaEngine engine, boolean video) {
		requestedEngine = engine;
		requestedVideo = video;
		reconcile();
	}

	/** Returns whether a surface callback can still manipulate the requested decoder output. */
	public boolean isBound(MediaEngine engine) {
		return requestedVideo && (requestedEngine == engine) && (boundEngine == engine);
	}

	/** Starts a source lease before its candidate is physically attached to the selected output. */
	public SourceLease beginSource(MediaEngine engine) {
		sourceEngine = engine;
		sourceOutputGeneration = generation;
		return new SourceLease(generation, ++sourceGeneration);
	}

	void clear() {
		requestedEngine = null;
		requestedVideo = false;
		detachBound();
	}

	/**
	 * Detaches the exact candidate only if it still owns this coordinator. This lets an aborted
	 * playback transaction clean up its Surface without being able to tear down a newer winner.
	 */
	void clearIfBound(@Nullable MediaEngine engine) {
		if ((engine == null) || ((requestedEngine != engine) && (boundEngine != engine))) return;
		clear();
	}

	private void detachBound() {
		invalidateSource();
		detachBoundEngine();
	}

	/** Clears the bound slot before native detach so a synchronous callback cannot see stale state. */
	private void detachBoundEngine() {
		MediaEngine engine = boundEngine;
		boundEngine = null;
		if (engine != null) engine.setVideoView(null);
	}

	@Nullable
	VideoView getSelected() {
		return selected;
	}

	public boolean isSelected(VideoView view) {
		return selected == view;
	}

	public long generation() {
		return generation;
	}

	public boolean isCurrent(MediaEngine engine, SourceLease source) {
		return (boundEngine == engine) && (sourceEngine == engine) &&
				(generation == source.outputGeneration()) &&
				(sourceOutputGeneration == source.outputGeneration()) &&
				(sourceGeneration == source.sourceGeneration());
	}

	/** True while a staged source still belongs to the currently selected output generation. */
	public boolean isSourceCurrent(MediaEngine engine, SourceLease source) {
		return (sourceEngine == engine) && (generation == source.outputGeneration()) &&
				(sourceOutputGeneration == source.outputGeneration()) &&
				(sourceGeneration == source.sourceGeneration());
	}

	private void reconcile() {
		VideoView next = select();
		boolean targetChanged = selected != next;
		if (targetChanged) {
			invalidateSource();
			selected = next;
			generation++;
		}

		MediaEngine engine = requestedEngine;
		if ((engine == null) || !requestedVideo || (next == null)) {
			detachBound();
			return;
		}
		if (targetChanged || (boundEngine != engine)) {
			if ((boundEngine != null) && (boundEngine != engine)) {
				if (sourceEngine == boundEngine) invalidateSource();
				detachBoundEngine();
			}
			if (targetChanged) stageSelectedSource(engine);
			boundEngine = engine;
			try {
				engine.setVideoView(next);
			} catch (RuntimeException | LinkageError error) {
				boundEngine = null;
				invalidateSource();
				throw error;
			}
			if (sourceEngine != engine) invalidateSource();
		}
	}

	/** Gives a newly selected output the same first-frame authority before its decoder is attached. */
	private void stageSelectedSource(MediaEngine engine) {
		VideoView view = selected;
		if (view != null) view.setVideoSourceLease(beginSource(engine));
	}

	private void invalidateSource() {
		sourceEngine = null;
		sourceOutputGeneration = -1L;
		sourceGeneration++;
	}

	@Nullable
	private VideoView select() {
		Registration selected = null;
		for (Registration target : targets) {
			if ((selected == null) || target.precedes(selected)) selected = target;
		}
		return (selected == null) ? null : selected.view;
	}

	private record Registration(VideoView view, int priority, int order) {
		boolean precedes(Registration other) {
			return (priority < other.priority) || ((priority == other.priority) && (order < other.order));
		}
	}

	/** Immutable proof that a first-frame callback still belongs to this output/source pairing. */
	public record SourceLease(long outputGeneration, long sourceGeneration) {
	}
}
