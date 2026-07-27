package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeAdControllerTest {
	@Test
	public void preRollSupportsAnAdPodAndResumesContentOnce() {
		YoutubeAdControllerFixture f = new YoutubeAdControllerFixture();
		f.begin("video-1");

		f.record(f.controller.onAdPodStarted(f.generation,
				new YoutubeAdController.AdPod("pre-1", YoutubeAdController.BreakType.PRE_ROLL, 2)));
		f.record(f.controller.onAdStarted(f.generation, "pre-1", "ad-1"));
		f.record(f.controller.onAdCompleted(f.generation, "pre-1", "ad-1"));
		f.record(f.controller.onAdStarted(f.generation, "pre-1", "ad-2"));
		f.record(f.controller.onAdCompleted(f.generation, "pre-1", "ad-2"));
		YoutubeAdController.Transition completed = f.controller.onAdPodCompleted(f.generation, "pre-1");
		f.record(completed);

		assertTrue(completed.accepted());
		assertEquals(YoutubeAdController.State.CONTENT, completed.state());
		assertEquals(1L, f.count(YoutubeAdController.EffectType.ENTER_AD_POD));
		assertEquals(1L, f.count(YoutubeAdController.EffectType.RESUME_CONTENT));
	}

	@Test
	public void midRollRequiresContentAndAllowsMultipleAds() {
		YoutubeAdControllerFixture f = new YoutubeAdControllerFixture();
		f.begin("video-1");
		assertTrue(f.controller.onContentStarted(f.generation).accepted());

		YoutubeAdController.Transition preBeforeContent =
				new YoutubeAdController().onAdPodStarted(1L,
						YoutubeAdController.AdPod.midRoll("mid-1"));
		assertFalse(preBeforeContent.accepted());

		f.record(f.controller.onAdPodStarted(f.generation, YoutubeAdController.AdPod.midRoll("mid-1")));
		f.record(f.controller.onAdStarted(f.generation, "mid-1", "ad-1"));
		f.record(f.controller.onAdCompleted(f.generation, "mid-1", "ad-1"));
		f.record(f.controller.onAdStarted(f.generation, "mid-1", "ad-2"));
		f.record(f.controller.onAdCompleted(f.generation, "mid-1", "ad-2"));
		YoutubeAdController.Transition completed = f.controller.onAdPodCompleted(f.generation, "mid-1");

		assertTrue(completed.accepted());
		assertEquals(YoutubeAdController.State.CONTENT, completed.state());
		assertEquals(2L, f.count(YoutubeAdController.EffectType.START_AD));
	}

	@Test
	public void duplicateSignalsAreIdempotent() {
		YoutubeAdControllerFixture f = new YoutubeAdControllerFixture();
		f.begin("video-1");
		YoutubeAdController.AdPod pod = YoutubeAdController.AdPod.preRoll("pre-1");

		f.record(f.controller.onAdPodStarted(f.generation, pod));
		YoutubeAdController.Transition duplicatePod = f.controller.onAdPodStarted(f.generation, pod);
		f.record(f.controller.onAdStarted(f.generation, "pre-1", "ad-1"));
		YoutubeAdController.Transition duplicateAd = f.controller.onAdStarted(f.generation, "pre-1", "ad-1");
		f.record(f.controller.onAdCompleted(f.generation, "pre-1", "ad-1"));
		YoutubeAdController.Transition duplicateComplete =
				f.controller.onAdCompleted(f.generation, "pre-1", "ad-1");

		assertTrue(duplicatePod.isNoOp());
		assertTrue(duplicateAd.isNoOp());
		assertTrue(duplicateComplete.isNoOp());
		assertEquals(1L, f.count(YoutubeAdController.EffectType.ENTER_AD_POD));
		assertEquals(1L, f.count(YoutubeAdController.EffectType.START_AD));
		assertEquals(1L, f.count(YoutubeAdController.EffectType.COMPLETE_AD));
	}

	@Test
	public void staleGenerationAndUncertainSignalsAreNoOps() {
		YoutubeAdControllerFixture f = new YoutubeAdControllerFixture();
		f.begin("video-1");
		long oldGeneration = f.generation;
		f.begin("video-2");

		assertTrue(f.controller.onAdPodStarted(oldGeneration,
				YoutubeAdController.AdPod.preRoll("stale")).isNoOp());
		assertTrue(f.controller.onAdPodStarted(f.generation,
				new YoutubeAdController.AdPod("", YoutubeAdController.BreakType.PRE_ROLL, -1)).isNoOp());
		assertTrue(f.controller.onAdStarted(f.generation, "missing-pod", "ad-1").isNoOp());
		assertTrue(f.controller.onAdPodCompleted(f.generation, "missing-pod").isNoOp());
		assertEquals(2L, f.generation);
		assertEquals(2L, f.count(YoutubeAdController.EffectType.PLAYBACK_STARTED));
	}

	@Test
	public void retryWaitsForCooldownAndDoesNotReplayError() {
		YoutubeAdControllerFixture f = new YoutubeAdControllerFixture();
		f.begin("video-1");
		f.record(f.controller.onAdPodStarted(f.generation, YoutubeAdController.AdPod.preRoll("pre-1")));
		f.record(f.controller.onAdStarted(f.generation, "pre-1", "ad-1"));

		YoutubeAdController.Transition failed =
				f.controller.onAdError(f.generation, "pre-1", "ad-1", true, 1_000L);
		f.record(failed);
		assertEquals(YoutubeAdController.State.COOLDOWN, failed.state());
		assertTrue(f.controller.onClock(f.generation, 1_099L).isNoOp());
		assertTrue(f.controller.onAdError(f.generation, "pre-1", "ad-1", true, 1_099L).isNoOp());

		YoutubeAdController.Transition retry = f.controller.onClock(f.generation, 1_100L);
		f.record(retry);
		assertEquals(YoutubeAdController.State.PRE_ROLL, retry.state());
		assertEquals(1L, f.count(YoutubeAdController.EffectType.RETRY_AD_POD));
	}

	@Test
	public void exhaustedRetryFailsOpenToContent() {
		YoutubeAdControllerFixture f = new YoutubeAdControllerFixture();
		f.begin("video-1");
		f.record(f.controller.onAdPodStarted(f.generation, YoutubeAdController.AdPod.preRoll("pre-1")));

		for (int attempt = 0; attempt < 2; attempt++) {
			f.record(f.controller.onAdStarted(f.generation, "pre-1", "ad-" + attempt));
			f.record(f.controller.onAdError(f.generation, "pre-1", "ad-" + attempt, true,
					attempt * 200L));
			f.record(f.controller.onClock(f.generation, attempt * 200L + 100L));
		}

		YoutubeAdController.Transition exhausted =
				f.controller.onAdStarted(f.generation, "pre-1", "ad-final");
		f.record(exhausted);
		YoutubeAdController.Transition resumed = f.controller.onAdError(
				f.generation, "pre-1", "ad-final", true, 500L);
		f.record(resumed);

		assertEquals(YoutubeAdController.State.CONTENT, resumed.state());
		assertEquals(2L, f.count(YoutubeAdController.EffectType.RETRY_AD_POD));
		assertEquals(1L, f.count(YoutubeAdController.EffectType.RESUME_CONTENT));
	}

	@Test
	public void nonRetryableFailureResumesAndEndInvalidatesCallbacks() {
		YoutubeAdControllerFixture f = new YoutubeAdControllerFixture();
		f.begin("video-1");
		f.record(f.controller.onAdPodStarted(f.generation, YoutubeAdController.AdPod.midRoll("mid-1")));
		f.record(f.controller.onAdStarted(f.generation, "mid-1", "ad-1"));

		YoutubeAdController.Transition resumed = f.controller.onAdError(
				f.generation, "mid-1", "ad-1", false, 10L);
		assertEquals(YoutubeAdController.State.CONTENT, resumed.state());
		assertTrue(f.controller.endPlayback(f.generation).accepted());
		assertTrue(f.controller.onContentStarted(f.generation).isNoOp());
		assertTrue(f.controller.onAdPodStarted(f.generation,
				YoutubeAdController.AdPod.midRoll("late")).isNoOp());
	}

	@Test
	public void adEndingDuringCooldownCancelsRetryAndResumesContent() {
		YoutubeAdControllerFixture f = new YoutubeAdControllerFixture();
		f.begin("video-1");
		f.record(f.controller.onAdPodStarted(f.generation,
				YoutubeAdController.AdPod.preRoll("pre-1")));
		f.record(f.controller.onAdStarted(f.generation, "pre-1", "ad-1"));
		f.record(f.controller.onAdError(f.generation, "pre-1", "ad-1", true, 1_000L));

		YoutubeAdController.Transition completed =
				f.controller.onAdPodCompleted(f.generation, "pre-1");

		assertTrue(completed.accepted());
		assertEquals(YoutubeAdController.State.CONTENT, completed.state());
		assertTrue(f.controller.onClock(f.generation, 2_000L).isNoOp());
	}

	@Test
	public void contentSignalAlsoRecoversAStaleCooldown() {
		YoutubeAdControllerFixture f = new YoutubeAdControllerFixture();
		f.begin("video-1");
		f.record(f.controller.onAdPodStarted(f.generation,
				YoutubeAdController.AdPod.preRoll("pre-1")));
		f.record(f.controller.onAdStarted(f.generation, "pre-1", "ad-1"));
		f.record(f.controller.onAdError(f.generation, "pre-1", "ad-1", true, 1_000L));

		YoutubeAdController.Transition content = f.controller.onContentStarted(f.generation);

		assertTrue(content.accepted());
		assertEquals(YoutubeAdController.State.CONTENT, content.state());
	}
}
