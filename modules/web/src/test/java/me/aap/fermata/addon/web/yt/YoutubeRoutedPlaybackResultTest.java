package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;
import static me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.*;
import static me.aap.fermata.ui.policy.RuntimeHostMode.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import me.aap.fermata.auto.OpenOnCarMediaRouting.Admission;

public class YoutubeRoutedPlaybackResultTest {
	@Test public void unavailableCarNeverReportsYoutubeDispatchOrRunsExecutor() {
		assertEquals(NOT_READY, YoutubeFragment.dispatchPlayback(new Admission(AA_PROJECTION, () -> true),
				() -> false, () -> { throw new AssertionError("no fallback or preparation"); }));
	}
	@Test public void staleYoutubeRequestReturnsCancelledWithoutDispatch() {
		assertEquals(CANCELLED, YoutubeFragment.dispatchPlayback(new Admission(AA_PROJECTION, () -> false),
				() -> false, () -> { throw new AssertionError("stale request"); }));
	}
	@Test public void onlyActualYoutubeDispatchMayReportLoadDispatched() {
		Admission admission = new Admission(PHONE, () -> true);
		assertEquals(NOT_READY, YoutubeFragment.dispatchPlayback(admission, () -> false, () -> false));
		assertEquals(LOAD_DISPATCHED, YoutubeFragment.dispatchPlayback(admission, () -> false, () -> true));
		AtomicBoolean current = new AtomicBoolean(true);
		assertEquals(CANCELLED, YoutubeFragment.dispatchPlayback(new Admission(PHONE, current::get),
				() -> false, () -> { current.set(false); return true; }));
	}
	@Test public void alreadyHandledYoutubeRouteRemainsSuccessfulWithoutClaimingLoad() {
		Admission admission = new Admission(PHONE, () -> true);
		java.util.function.Supplier<me.aap.fermata.auto.AutomotiveNavigationController.OpenResult>
				handled = () -> OPENED;
		assertEquals(OPENED, YoutubeFragment.dispatchTypedPlayback(admission, () -> false, handled));
	}
}
