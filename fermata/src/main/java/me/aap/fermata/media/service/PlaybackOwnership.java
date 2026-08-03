package me.aap.fermata.media.service;

import androidx.annotation.Nullable;

/**
 * Tracks playback identity independently from transport-state commands.
 *
 * <p>Identity comparisons deliberately use reference equality. Playback items are canonicalized
 * before entering this class, so an equal-but-different wrapper must never acquire ownership.</p>
 */
final class PlaybackOwnership {
	private long itemGeneration;
	private long stateRevision;
	@Nullable
	private Token committed;
	@Nullable
	private Token pending;

	Token begin(Object addonIdentity, Object itemIdentity, @Nullable Object engine) {
		stateRevision++;
		return pending = new Token(++itemGeneration, addonIdentity, itemIdentity, engine);
	}

	Token adopt(Object addonIdentity, Object itemIdentity, Object engine) {
		stateRevision++;
		Token token = new Token(++itemGeneration, addonIdentity, itemIdentity, engine);
		pending = null;
		committed = token;
		return token;
	}

	@Nullable
	Token bindEngine(long generation, Object itemIdentity, Object engine) {
		Token token = pending;
		if (!matchesRequest(token, generation, itemIdentity)) return null;
		return pending = token.withEngine(engine);
	}

	@Nullable
	Token bindEngine(@Nullable Token expectedPending, Object engine) {
		if ((expectedPending == null) || (pending != expectedPending)) return null;
		return pending = expectedPending.withEngine(engine);
	}

	boolean replaceEngine(Object expected, Object replacement) {
		Token token = active();
		if ((token == null) || (token.engineIdentity() != expected)) return false;
		Token replaced = token.withEngine(replacement);
		if (token == pending) pending = replaced;
		else committed = replaced;
		return true;
	}

	boolean commit(Object engine, Object itemIdentity) {
		Token token = pending;
		if (!matches(token, engine, itemIdentity)) return false;
		committed = token;
		pending = null;
		return true;
	}

	boolean rollback(long generation, Object itemIdentity) {
		if (!matchesRequest(pending, generation, itemIdentity)) return false;
		pending = null;
		stateRevision++;
		return true;
	}

	@Nullable
	RollbackResult rollback(@Nullable Token expectedPending) {
		if ((expectedPending == null) || (pending != expectedPending)) return null;
		pending = null;
		stateRevision++;
		Token restoredOwner = active();
		long restoredRevision = (restoredOwner == null) ? itemGeneration :
				restoredOwner.generation();
		return new RollbackResult(restoredOwner, restoredRevision);
	}

	void release() {
		if ((pending == null) && (committed == null)) {
			stateRevision++;
			return;
		}
		pending = null;
		committed = null;
		itemGeneration++;
		stateRevision++;
	}

	long reviseState() {
		return ++stateRevision;
	}

	long getItemGeneration() {
		return itemGeneration;
	}

	long getStateRevision() {
		return stateRevision;
	}

	@Nullable
	Token getActive() {
		return active();
	}

	@Nullable
	Token getPending() {
		return pending;
	}

	@Nullable
	Token getCommitted() {
		return committed;
	}

	boolean hasPending() {
		return pending != null;
	}

	boolean isRequestCurrent(long generation, Object itemIdentity) {
		return matchesRequest(pending, generation, itemIdentity);
	}

	boolean owns(Object engine, Object itemIdentity) {
		return matches(active(), engine, itemIdentity);
	}

	boolean ownsCommitted(Object engine, Object itemIdentity) {
		return matches(committed, engine, itemIdentity);
	}

	long committedGeneration(Object engine, Object itemIdentity) {
		Token token = committed;
		return matches(token, engine, itemIdentity) ? token.generation() : -1L;
	}

	boolean referencesEngine(@Nullable Object engine) {
		if (engine == null) return false;
		Token pending = this.pending;
		Token committed = this.committed;
		return ((pending != null) && (pending.engineIdentity() == engine)) ||
				((committed != null) && (committed.engineIdentity() == engine));
	}

	StateToken captureState() {
		return new StateToken(active(), stateRevision);
	}

	boolean owns(StateToken token) {
		return (token != null) && (token.revision() == stateRevision) &&
				sameToken(token.owner(), active());
	}

	private static boolean matches(@Nullable Token token, Object engine, Object itemIdentity) {
		return (token != null) && (token.engineIdentity() == engine) &&
				(token.itemIdentity() == itemIdentity);
	}

	private static boolean matchesRequest(@Nullable Token token, long generation,
			Object itemIdentity) {
		return (token != null) && (token.generation() == generation) &&
				(token.itemIdentity() == itemIdentity);
	}

	private static boolean sameToken(@Nullable Token first, @Nullable Token second) {
		return (first == second) || ((first != null) && (second != null) &&
				(first.generation() == second.generation()) &&
				(first.addonIdentity() == second.addonIdentity()) &&
				(first.itemIdentity() == second.itemIdentity()) &&
				(first.engineIdentity() == second.engineIdentity()));
	}

	@Nullable
	private Token active() {
		return (pending != null) ? pending : committed;
	}

	record Token(long generation, Object addonIdentity, Object itemIdentity,
			@Nullable Object engineIdentity) {
		Token withEngine(Object engine) {
			return new Token(generation, addonIdentity, itemIdentity, engine);
		}
	}

	record StateToken(@Nullable Token owner, long revision) {
	}

	record RollbackResult(@Nullable Token restoredOwner, long restoredRevision) {
	}
}
