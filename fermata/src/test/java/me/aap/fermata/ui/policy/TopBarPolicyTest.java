package me.aap.fermata.ui.policy;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TopBarPolicyTest {
	@Test
	public void backFollowsRouteOnEveryHost() {
		assertEquals(GONE, TopBarPolicy.resolve(RuntimeHostMode.PHONE,
				true, 1, 0, "Dashboard", "", "").backVisibility());
		assertEquals(GONE, TopBarPolicy.resolve(RuntimeHostMode.AA_PROJECTION,
				true, 1, 0, "Dashboard", "", "").backVisibility());

		assertEquals(VISIBLE, TopBarPolicy.resolve(RuntimeHostMode.PHONE,
				false, 2, 2, "TV", "VTV3", "").backVisibility());
		assertEquals(VISIBLE, TopBarPolicy.resolve(RuntimeHostMode.AA_PROJECTION,
				false, 2, 2, "TV", "VTV3", "").backVisibility());
		assertEquals(VISIBLE, TopBarPolicy.resolve(RuntimeHostMode.MIRROR,
				false, 2, 2, "TV", "VTV3", "").backVisibility());
	}

	@Test
	public void playbackOwnerTitleSemanticsAreHostIndependent() {
		TopBarPolicy.State phone = TopBarPolicy.resolve(RuntimeHostMode.PHONE,
				false, 42, 42, "Local channels", "VTV3", "Preparing");
		TopBarPolicy.State auto = TopBarPolicy.resolve(RuntimeHostMode.AA_PROJECTION,
				false, 42, 42, "Local channels", "VTV3", "Preparing");
		assertEquals("VTV3 | Preparing", phone.title());
		assertEquals(phone.title(), auto.title());
	}

	@Test
	public void unrelatedPlaybackCannotReplaceRouteTitle() {
		TopBarPolicy.State state = TopBarPolicy.resolve(RuntimeHostMode.PHONE,
				false, 42, 99, "TV", "Other playback", "Preparing");
		assertEquals("TV", state.title());
	}
}
