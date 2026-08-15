package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.view.VideoView;

public class VideoOutputCoordinatorTest {
	@Test
	public void inactiveRegistrationDoesNotRebindTheDecoder() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		VideoView aa = view();
		VideoView phone = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();

		coordinator.bind(engine, true);
		coordinator.add(aa, 0);
		coordinator.add(phone, 1);

		assertSame(aa, coordinator.getSelected());
		assertEquals(List.of(aa), bindings);
		assertTrue(coordinator.isSelected(aa));
		assertTrue(coordinator.isBound(engine));
		assertEquals(1L, coordinator.generation());
	}

	@Test
	public void removingInactiveTargetDoesNotRebindTheDecoder() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		VideoView aa = view();
		VideoView phone = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		coordinator.bind(engine, true);
		coordinator.add(aa, 0);
		coordinator.add(phone, 1);

		coordinator.remove(phone);

		assertSame(aa, coordinator.getSelected());
		assertEquals(List.of(aa), bindings);
		assertEquals(1L, coordinator.generation());
	}

	@Test
	public void selectedTargetHandoffRebindsExactlyOnceAndAdvancesGeneration() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		VideoView phone = view();
		VideoView aa = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		coordinator.bind(engine, true);
		coordinator.add(phone, 1);

		coordinator.add(aa, 0);

		assertSame(aa, coordinator.getSelected());
		assertEquals(List.of(phone, aa), bindings);
		assertEquals(2L, coordinator.generation());
	}

	@Test
	public void initialAttachmentIsAlreadyBoundWhenTheEngineReceivesItsVideoView() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		List<Boolean> boundDuringAttachment = new ArrayList<>();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		MediaEngine[] engineRef = new MediaEngine[1];
		MediaEngine engine = engine(bindings, true,
				view -> boundDuringAttachment.add(coordinator.isBound(engineRef[0])));
		engineRef[0] = engine;

		coordinator.bind(engine, true);
		coordinator.add(view(), 0);

		assertEquals(1, boundDuringAttachment.size());
		assertTrue(boundDuringAttachment.get(0));
	}

	@Test
	public void selectedTargetHandoffStagesTheNewLeaseAndInvalidatesTheOldOne() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		VideoView phone = view();
		VideoView aa = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		coordinator.bind(engine, true);
		coordinator.add(phone, 1);
		VideoOutputCoordinator.SourceLease oldLease = videoSourceLease(phone);

		coordinator.add(aa, 0);

		VideoOutputCoordinator.SourceLease newLease = videoSourceLease(aa);
		assertNotNull(oldLease);
		assertNotNull(newLease);
		assertFalse(coordinator.isCurrent(engine, oldLease));
		assertTrue(coordinator.isCurrent(engine, newLease));
		assertEquals(List.of(phone, aa), bindings);
	}

	@Test
	public void removingSelectedTargetPromotesTheNextTarget() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		VideoView aa = view();
		VideoView phone = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		coordinator.bind(engine, true);
		coordinator.add(aa, 0);
		coordinator.add(phone, 1);

		coordinator.remove(aa);

		assertSame(phone, coordinator.getSelected());
		assertEquals(List.of(aa, phone), bindings);
		assertEquals(2L, coordinator.generation());
	}

	@Test
	public void recreatingTheOnlySurfaceDetachesThenRebindsTheDecoder() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		VideoView target = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		coordinator.bind(engine, true);
		coordinator.add(target, 0);

		coordinator.remove(target);
		coordinator.add(target, 0);

		assertEquals(Arrays.asList(target, null, target), bindings);
		assertSame(target, coordinator.getSelected());
		assertTrue(coordinator.isBound(engine));
		assertEquals(3L, coordinator.generation());
	}

	@Test
	public void noOutputDetachesOnlyTheBoundEngine() throws Exception {
		List<VideoView> firstBindings = new ArrayList<>();
		List<VideoView> secondBindings = new ArrayList<>();
		MediaEngine first = videoEngine(firstBindings);
		MediaEngine second = videoEngine(secondBindings);
		VideoView target = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		coordinator.bind(first, true);
		coordinator.add(target, 0);

		coordinator.bind(second, true);
		coordinator.remove(target);

		assertEquals(Arrays.asList(target, null), firstBindings);
		assertEquals(Arrays.asList(target, null), secondBindings);
		assertNull(coordinator.getSelected());
	}

	@Test
	public void sourceAndOutputGenerationRejectStaleFirstFrameReveal() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		VideoView phone = view();
		VideoView aa = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		coordinator.bind(engine, true);
		coordinator.add(phone, 1);
		VideoOutputCoordinator.SourceLease source = coordinator.beginSource(engine);

		assertTrue(coordinator.isCurrent(engine, source));
		coordinator.add(aa, 0);
		assertFalse(coordinator.isCurrent(engine, source));

		VideoOutputCoordinator.SourceLease currentSource = coordinator.beginSource(engine);
		assertTrue(coordinator.isCurrent(engine, currentSource));
		VideoOutputCoordinator.SourceLease replacementSource = coordinator.beginSource(engine);
		assertFalse(coordinator.isCurrent(engine, currentSource));
		assertTrue(coordinator.isCurrent(engine, replacementSource));
	}

	@Test
	public void stagedCandidateSourceSurvivesItsLaterOutputBinding() throws Exception {
		List<VideoView> oldBindings = new ArrayList<>();
		List<VideoView> candidateBindings = new ArrayList<>();
		MediaEngine old = videoEngine(oldBindings);
		MediaEngine candidate = videoEngine(candidateBindings);
		VideoView target = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		coordinator.bind(old, true);
		coordinator.add(target, 0);

		VideoOutputCoordinator.SourceLease candidateSource = coordinator.beginSource(candidate);
		coordinator.bind(candidate, true);

		assertEquals(Arrays.asList(target, null), oldBindings);
		assertEquals(List.of(target), candidateBindings);
		assertTrue(coordinator.isCurrent(candidate, candidateSource));
	}

	@Test
	public void stagedInitialSourceSurvivesItsFirstOutputBinding() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine candidate = videoEngine(bindings);
		VideoView target = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		coordinator.add(target, 0);

		VideoOutputCoordinator.SourceLease source = coordinator.beginSource(candidate);
		coordinator.bind(candidate, true);

		assertEquals(List.of(target), bindings);
		assertTrue(coordinator.isCurrent(candidate, source));
	}

	@Test
	public void audioBindingDetachesThePreviousVideoOutput() throws Exception {
		List<VideoView> videoBindings = new ArrayList<>();
		List<VideoView> audioBindings = new ArrayList<>();
		MediaEngine video = videoEngine(videoBindings);
		MediaEngine audio = audioEngine(audioBindings);
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		VideoView target = view();
		coordinator.bind(video, true);
		coordinator.add(target, 0);

		coordinator.bind(audio, false);

		assertEquals(Arrays.asList(target, null), videoBindings);
		assertTrue(audioBindings.isEmpty());
		assertFalse(coordinator.isBound(video));
		assertFalse(coordinator.isBound(audio));
	}

	@Test
	public void clearInvalidatesTheSourceBeforeDetachingTheOldOutput() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine video = videoEngine(bindings);
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		VideoView target = view();
		coordinator.bind(video, true);
		coordinator.add(target, 0);
		VideoOutputCoordinator.SourceLease source = coordinator.beginSource(video);

		coordinator.clear();

		assertEquals(Arrays.asList(target, null), bindings);
		assertFalse(coordinator.isCurrent(video, source));
	}

	@Test
	public void clearIfBoundOnlyDetachesTheMatchingCandidate() throws Exception {
		List<VideoView> oldBindings = new ArrayList<>();
		List<VideoView> candidateBindings = new ArrayList<>();
		MediaEngine old = videoEngine(oldBindings);
		MediaEngine candidate = videoEngine(candidateBindings);
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		VideoView target = view();
		coordinator.bind(old, true);
		coordinator.add(target, 0);

		coordinator.clearIfBound(candidate);

		assertEquals(List.of(target), oldBindings);
		assertTrue(coordinator.isBound(old));
		coordinator.clearIfBound(old);
		assertEquals(Arrays.asList(target, null), oldBindings);
		assertFalse(coordinator.isBound(old));
		assertTrue(candidateBindings.isEmpty());
	}

	@Test
	public void sourceLeaseCanStayValidBeforeItsDecoderAttachment() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		VideoView target = view();
		coordinator.add(target, 0);

		VideoOutputCoordinator.SourceLease source = coordinator.beginSource(engine);

		assertTrue(coordinator.isSourceCurrent(engine, source));
		assertFalse(coordinator.isCurrent(engine, source));
		coordinator.bind(engine, true);
		assertTrue(coordinator.isCurrent(engine, source));
	}

	@Test
	public void attachmentFailureDoesNotLeaveTheEngineMarkedAsBound() throws Exception {
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		MediaEngine failing = engine(new ArrayList<>(), true, view -> {
			throw new IllegalStateException("attach failed");
		});
		coordinator.add(view(), 0);

		try {
			coordinator.bind(failing, true);
		} catch (IllegalStateException expected) {
			// The native engine failure is propagated, but no stale output ownership remains.
		}

		assertFalse(coordinator.isBound(failing));
	}

	@Test
	public void candidateBindingRejectsAnOlderStagedSource() throws Exception {
		List<VideoView> oldBindings = new ArrayList<>();
		List<VideoView> candidateBindings = new ArrayList<>();
		MediaEngine old = videoEngine(oldBindings);
		MediaEngine candidate = videoEngine(candidateBindings);
		VideoView target = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		coordinator.bind(old, true);
		coordinator.add(target, 0);

		VideoOutputCoordinator.SourceLease oldSource = coordinator.beginSource(old);
		coordinator.bind(candidate, true);

		assertFalse(coordinator.isCurrent(old, oldSource));
		VideoOutputCoordinator.SourceLease candidateSource = coordinator.beginSource(candidate);
		assertTrue(coordinator.isCurrent(candidate, candidateSource));
	}

	@Test
	public void targetHandoffInvalidatesAStagedSource() throws Exception {
		List<VideoView> bindings = new ArrayList<>();
		MediaEngine engine = videoEngine(bindings);
		VideoView phone = view();
		VideoView aa = view();
		VideoOutputCoordinator coordinator = new VideoOutputCoordinator();
		coordinator.bind(engine, true);
		coordinator.add(phone, 1);
		VideoOutputCoordinator.SourceLease source = coordinator.beginSource(engine);

		coordinator.add(aa, 0);

		assertFalse(coordinator.isCurrent(engine, source));
	}

	private static MediaEngine videoEngine(List<VideoView> bindings) {
		return engine(bindings, true);
	}

	private static MediaEngine audioEngine(List<VideoView> bindings) {
		return engine(bindings, false);
	}

	private static MediaEngine engine(List<VideoView> bindings, boolean video) {
		return engine(bindings, video, view -> {
		});
	}

	private static MediaEngine engine(List<VideoView> bindings, boolean video,
			Consumer<VideoView> onVideoViewSet) {
		PlayableItem source = (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "isVideo" -> video;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> defaultValue(method.getReturnType());
				});
		return (MediaEngine) Proxy.newProxyInstance(MediaEngine.class.getClassLoader(),
				new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getSource" -> source;
					case "setVideoView" -> {
						VideoView view = (VideoView) args[0];
						bindings.add(view);
						onVideoViewSet.accept(view);
						yield null;
					}
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> defaultValue(method.getReturnType());
				});
	}

	private static VideoOutputCoordinator.SourceLease videoSourceLease(VideoView view) throws Exception {
		Field source = VideoView.class.getDeclaredField("visibleSource");
		source.setAccessible(true);
		return (VideoOutputCoordinator.SourceLease) source.get(view);
	}

	private static VideoView view() throws Exception {
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		Field field = unsafeType.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		Object unsafe = field.get(null);
		return (VideoView) unsafeType.getMethod("allocateInstance", Class.class)
				.invoke(unsafe, VideoView.class);
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		if (type == double.class) return 0D;
		if (type == char.class) return '\0';
		return null;
	}
}
