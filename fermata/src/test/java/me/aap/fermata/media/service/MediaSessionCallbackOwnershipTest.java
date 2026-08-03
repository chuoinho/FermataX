package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;

import org.junit.Test;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.lib.PlayableItemWrapper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

public class MediaSessionCallbackOwnershipTest {
	@Test
	public void oldEngineCallbacksAreRejectedWhileAnotherItemIsPending() {
		assertFalse(MediaSessionCallback.acceptsCallbackOwnership(true, true, false));
		assertTrue(MediaSessionCallback.acceptsCallbackOwnership(true, true, true));
	}

	@Test
	public void inactiveEngineCallbacksAreAlwaysRejected() {
		assertFalse(MediaSessionCallback.acceptsCallbackOwnership(false, false, false));
		assertFalse(MediaSessionCallback.acceptsCallbackOwnership(false, true, true));
	}

	@Test
	public void currentEngineIsAcceptedWithoutPendingTransition() {
		assertTrue(MediaSessionCallback.acceptsCallbackOwnership(true, false, false));
	}

	@Test
	public void differentVideoClearsPreviousDecoderFrame() {
		Object current = new Object();
		assertTrue(MediaSessionCallback.shouldClearPlaybackSurfaces(
				true, current, new Object()));
		assertTrue(MediaSessionCallback.shouldClearPlaybackSurfaces(
				true, null, new Object()));
		assertFalse(MediaSessionCallback.shouldClearPlaybackSurfaces(
				true, current, current));
		assertFalse(MediaSessionCallback.shouldClearPlaybackSurfaces(
				false, current, new Object()));
	}

	@Test
	public void engineReplacementRejectsSelfAndSupersededEngine() {
		MediaEngine expected = engine("expected");
		MediaEngine replacement = engine("replacement");
		MediaEngine newer = engine("newer");
		ReplacementCase[] cases = {
				new ReplacementCase("same replacement", expected, expected, true),
				new ReplacementCase("newer engine already selected", newer, replacement, true),
				new ReplacementCase("valid current engine", expected, replacement, false)
		};

		for (ReplacementCase test : cases) {
			assertEquals(test.name, test.rejected,
					MediaSessionCallback.rejectsEngineReplacement(
							test.current, expected, test.replacement));
		}
	}

	@Test
	public void pendingHandoffRequiresExpectedEngineAndPendingTarget() {
		MediaEngine expected = engine("expected");
		MediaEngine other = engine("other");
		PendingHandoffCase[] cases = {
				new PendingHandoffCase("late engine", other, true, false),
				new PendingHandoffCase("target no longer pending", expected, false, false),
				new PendingHandoffCase("current pending target", expected, true, true)
		};

		for (PendingHandoffCase test : cases) {
			assertEquals(test.name, test.accepted,
					MediaSessionCallback.acceptsPendingEngineHandoff(
							test.current, expected, test.pending));
		}
	}

	@Test
	public void pendingEngineHandoffRejectsInvalidLeaseOrUnexpectedEngine() {
		MediaEngine expected = engine("expected");
		MediaEngine replacement = engine("replacement");
		MediaEngine third = engine("third");
		HandoffCase[] cases = {
				new HandoffCase("uncaptured token", -1L, 7L, expected, true),
				new HandoffCase("superseded request", 6L, 7L, expected, true),
				new HandoffCase("third engine won", 7L, 7L, third, true),
				new HandoffCase("deferred engine still current", 7L, 7L, expected, false),
				new HandoffCase("replacement already current", 7L, 7L, replacement, false)
		};

		for (HandoffCase test : cases) {
			assertEquals(test.name, test.rejected,
					MediaSessionCallback.rejectsHandoff(test.requestRevision, test.liveRevision,
							test.current, expected, replacement));
		}
	}

	@Test
	public void playbackRequestRequiresLiveRevisionGenerationAndItemIdentity() {
		MediaEngine engine = engine("engine");
		PlayableItem ownerItem = item("owner", false);
		PlayableItem otherItem = item("other", false);
		PlayableItem transport = item("transport", true);
		PlaybackOwnership.Token owner =
				new PlaybackOwnership.Token(7L, new Object(), ownerItem, engine);
		PlaybackOwnership.Token wrongGeneration =
				new PlaybackOwnership.Token(6L, new Object(), ownerItem, engine);
		RequestCase[] cases = {
				new RequestCase("stale revision", 6L, 7L, owner, ownerItem, false),
				new RequestCase("missing owner", 7L, 7L, null, ownerItem, false),
				new RequestCase("token generation mismatch", 7L, 7L,
						wrongGeneration, ownerItem, false),
				new RequestCase("different regular item", 7L, 7L, owner, otherItem, false),
				new RequestCase("current regular item", 7L, 7L, owner, ownerItem, true),
				new RequestCase("transport command keeps active owner", 7L, 7L,
						owner, transport, true)
		};

		for (RequestCase test : cases) {
			assertEquals(test.name, test.accepted,
					MediaSessionCallback.isPlaybackRequestCurrent(test.requestRevision,
							test.liveRevision, test.owner, test.item));
		}
	}

	@Test
	public void externalPlaybackOwnershipBranchMatchesHandoffState() {
		BranchCase[] cases = {
				new BranchCase("different pending engine is handled by switch", false, true, true, false,
						MediaSessionCallback.ExternalPlaybackOwnershipBranch.SWITCH_ENGINE),
				new BranchCase("pending target completes", true, true, true, false,
						MediaSessionCallback.ExternalPlaybackOwnershipBranch.PENDING_TARGET_COMPLETION),
				new BranchCase("repeated signal keeps owner", true, false, true, true,
						MediaSessionCallback.ExternalPlaybackOwnershipBranch.ALREADY_OWNED),
				new BranchCase("unowned external source is adopted", true, false, true, false,
						MediaSessionCallback.ExternalPlaybackOwnershipBranch.ADOPT_NEW),
				new BranchCase("source-less engine changes no owner", true, false, false, false,
						MediaSessionCallback.ExternalPlaybackOwnershipBranch.NO_SOURCE)
		};

		for (BranchCase test : cases) {
			assertEquals(test.name, test.expected,
					MediaSessionCallback.selectExternalPlaybackOwnershipBranch(test.sameEngine,
							test.pendingTarget, test.sourcePresent, test.alreadyOwns));
		}
	}

	@Test
	public void tokenBackedAuthorityRequiresSourceAndActiveToken() {
		BooleanCase[] cases = {
				new BooleanCase(false, false, false),
				new BooleanCase(false, true, false),
				new BooleanCase(true, false, false),
				new BooleanCase(true, true, true)
		};

		for (BooleanCase test : cases) {
			assertEquals(test.expected, MediaSessionCallback.usesTokenBackedAuthority(
					test.first, test.second));
		}
	}

	@Test
	public void expectedSourceUsesCanonicalIdentity() {
		PlayableItem canonical = item("canonical", false);
		PlayableItem other = item("other", false);
		PlayableItem firstWrapper = new PlayableItemWrapper(canonical);
		PlayableItem secondWrapper = new PlayableItemWrapper(canonical);

		assertTrue(MediaSessionCallback.matchesExpectedSource(canonical, canonical));
		assertTrue(MediaSessionCallback.matchesExpectedSource(firstWrapper, secondWrapper));
		assertFalse(MediaSessionCallback.matchesExpectedSource(null, canonical));
		assertFalse(MediaSessionCallback.matchesExpectedSource(canonical, null));
		assertFalse(MediaSessionCallback.matchesExpectedSource(null, null));
		assertFalse(MediaSessionCallback.matchesExpectedSource(canonical, other));
	}

	@Test
	public void engineStateRequiresLiveStateTokenAndOwnedSource() {
		BooleanCase[] cases = {
				new BooleanCase(false, false, false),
				new BooleanCase(false, true, false),
				new BooleanCase(true, false, false),
				new BooleanCase(true, true, true)
		};

		for (BooleanCase test : cases) {
			assertEquals(test.expected, MediaSessionCallback.acceptsEngineState(
					test.first, test.second));
		}
	}

	@Test
	public void unifiedAsyncContinuationsMatchEngineStateDecision() {
		for (boolean stateTokenValid : new boolean[]{false, true}) {
			for (boolean sourceOwned : new boolean[]{false, true}) {
				boolean previousStartedDecision = sourceOwned && stateTokenValid;
				boolean previousMetadataDecision = sourceOwned && stateTokenValid;
				boolean unified = MediaSessionCallback.acceptsEngineState(
						stateTokenValid, sourceOwned);
				assertEquals(previousStartedDecision, unified);
				assertEquals(previousMetadataDecision, unified);
			}
		}
	}

	@Test
	public void gatekeeperPredicatesMatchTheExtractedWrapperDecisions() {
		for (boolean sourcePresent : new boolean[]{false, true}) {
			for (boolean activeTokenPresent : new boolean[]{false, true}) {
				boolean previousBranchChoice = sourcePresent && activeTokenPresent;
				assertEquals(previousBranchChoice,
						MediaSessionCallback.usesTokenBackedAuthority(
								sourcePresent, activeTokenPresent));
			}
		}

		PlayableItem canonical = item("canonical", false);
		PlayableItem current = new PlayableItemWrapper(canonical);
		PlayableItem expected = new PlayableItemWrapper(canonical);
		boolean previousFinalStep = (current != null) && (expected != null) &&
				(PlayableItemResolver.unwrap(current) == PlayableItemResolver.unwrap(expected));
		assertEquals(previousFinalStep,
				MediaSessionCallback.matchesExpectedSource(current, expected));
	}

	@Test
	public void queueActionCoversSameItemSameParentDifferentParentAndNoCurrent() {
		QueueCase[] cases = {
				new QueueCase(false, false, false,
						PlaybackPreparedItemDecisions.QueueAction.REFRESH_QUEUE),
				new QueueCase(true, true, false,
						PlaybackPreparedItemDecisions.QueueAction.SEEK_SAME_ITEM),
				new QueueCase(true, false, true,
						PlaybackPreparedItemDecisions.QueueAction.KEEP_QUEUE),
				new QueueCase(true, false, false,
						PlaybackPreparedItemDecisions.QueueAction.REFRESH_QUEUE)
		};
		for (QueueCase test : cases) {
			assertEquals(test.expected, PlaybackPreparedItemDecisions.queueAction(
					test.currentPresent, test.sameItem, test.sameParent));
		}
	}

	@Test
	public void outgoingSourceSelectsDirectCurrentOnlyForValidatedDirectBranch() {
		for (boolean currentPresent : new boolean[]{false, true}) {
			for (boolean directCurrent : new boolean[]{false, true}) {
				PlaybackPreparedItemDecisions.OutgoingSource expected =
						currentPresent && directCurrent ?
								PlaybackPreparedItemDecisions.OutgoingSource.DIRECT_CURRENT :
								PlaybackPreparedItemDecisions.OutgoingSource.PREVIOUS_SNAPSHOT;
				assertEquals(expected, PlaybackPreparedItemDecisions.outgoingSource(
						currentPresent, directCurrent));
			}
		}
	}

	@Test
	public void outgoingPublicationSelectsCanonicalMetadataAndPreservesStateFields() {
		PlayableItem item = item("outgoing", false);
		PlayableItem other = item("other", false);
		MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
				.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "outgoing").build();
		PlaybackStateCompat state = new PlaybackStateCompat.Builder()
				.setState(PlaybackStateCompat.STATE_PLAYING, 9L, 1.25f).build();
		PlaybackSnapshot snapshot = new PlaybackSnapshot(1L, item, state, metadata);
		PlaybackSnapshot wrongSnapshot = new PlaybackSnapshot(2L, other, state, null);

		PlaybackPreparedItemDecisions.OutgoingPublication selected =
				PlaybackPreparedItemDecisions.outgoingPublication(item, 42L, null, snapshot, state);
		PlaybackPreparedItemDecisions.OutgoingPublication wrong =
				PlaybackPreparedItemDecisions.outgoingPublication(item, 42L, null, wrongSnapshot, state);

		assertSame(metadata, selected.metadata());
		assertEquals(42L, selected.state().getPosition());
		assertEquals(state.getState(), selected.state().getState());
		assertEquals(state.getPlaybackSpeed(), selected.state().getPlaybackSpeed(), 0F);
		assertEquals(null, wrong.metadata());
	}

	@Test
	public void transitionPresentationSeparatesTransportAndRegularTarget() {
		PlayableItem target = item("target", false);
		PlayableItem transport = item("transport", true);
		PlayableItem current = item("current", false);
		MediaMetadataCompat metadata = new MediaMetadataCompat.Builder().build();
		PlaybackStateCompat previous = new PlaybackStateCompat.Builder()
				.setState(PlaybackStateCompat.STATE_PLAYING, 17L, 1.5f).build();

		PlaybackPreparedItemDecisions.TransitionPublication regular =
				PlaybackPreparedItemDecisions.transitionPublication(target, current,
						PlaybackStateCompat.STATE_CONNECTING, 4L, previous, metadata);
		PlaybackPreparedItemDecisions.TransitionPublication command =
				PlaybackPreparedItemDecisions.transitionPublication(transport, current,
						PlaybackStateCompat.STATE_SKIPPING_TO_NEXT, 4L, previous, metadata);

		assertSame(target, regular.item());
		assertEquals(4L, regular.position());
		assertEquals(null, regular.metadata());
		assertFalse(regular.transportCommand());
		assertSame(current, command.item());
		assertEquals(17L, command.position());
		assertSame(metadata, command.metadata());
		assertTrue(command.transportCommand());
	}

	@Test
	public void targetScopedQueueAndPreparationMetadataPredicatesCoverCanonicalAndStaleCases() {
		PlayableItem canonical = item("queue", false);
		PlayableItem wrapper = new PlayableItemWrapper(canonical);
		PlayableItem other = item("other", false);
		assertTrue(PlaybackPreparedItemDecisions.shouldPublishQueue(true, wrapper, canonical));
		assertFalse(PlaybackPreparedItemDecisions.shouldPublishQueue(false, wrapper, canonical));
		assertFalse(PlaybackPreparedItemDecisions.shouldPublishQueue(true, null, canonical));
		assertFalse(PlaybackPreparedItemDecisions.shouldPublishQueue(true, other, canonical));

		MediaMetadataCompat loaded = new MediaMetadataCompat.Builder().build();
		MediaMetadataCompat built = PlaybackPreparedItemDecisions.buildPreparationMetadata(
				loaded, "Title", "Progress");
		assertTrue(built != null);
		MediaMetadataCompat withoutProgress = PlaybackPreparedItemDecisions.buildPreparationMetadata(
				loaded, "Title", "");
		assertTrue(withoutProgress != null);
	}

	private static MediaEngine engine(String name) {
		return (MediaEngine) Proxy.newProxyInstance(MediaEngine.class.getClassLoader(),
				new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> switch (method.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> name;
					default -> defaultValue(method.getReturnType());
				});
	}

	private static PlayableItem item(String name, boolean transportCommand) {
		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "isPlaybackTransportCommand" -> transportCommand;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "getId", "getName", "toString" -> name;
					default -> defaultValue(method.getReturnType());
				});
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

	private record ReplacementCase(String name, MediaEngine current, MediaEngine replacement,
			boolean rejected) {}

	private record PendingHandoffCase(String name, MediaEngine current, boolean pending,
			boolean accepted) {}

	private record HandoffCase(String name, long requestRevision, long liveRevision,
			MediaEngine current, boolean rejected) {}

	private record RequestCase(String name, long requestRevision, long liveRevision,
			PlaybackOwnership.Token owner, PlayableItem item, boolean accepted) {}

	private record BranchCase(String name, boolean sameEngine, boolean pendingTarget,
			boolean sourcePresent, boolean alreadyOwns,
			MediaSessionCallback.ExternalPlaybackOwnershipBranch expected) {}

	private record BooleanCase(boolean first, boolean second, boolean expected) {}

	private record QueueCase(boolean currentPresent, boolean sameItem, boolean sameParent,
			PlaybackPreparedItemDecisions.QueueAction expected) {}
}
