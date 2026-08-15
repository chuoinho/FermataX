package me.aap.fermata.ui.policy;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TopBarPolicyTest {
	@Test
	public void phoneBackFollowsRouteNotPlaybackOwnership() {
		assertEquals(GONE, TopBarPolicy.resolve(RuntimeHostMode.PHONE,
				true, false, true, false, false,
				1, 0, "Dashboard", "", "").backVisibility());
		assertEquals(VISIBLE, TopBarPolicy.resolve(RuntimeHostMode.PHONE,
				false, true, false, false, true,
				2, 2, "TV", "VTV3", "").backVisibility());
		assertEquals(VISIBLE, TopBarPolicy.resolve(RuntimeHostMode.PHONE,
				true, false, false, true, false,
				2, 0, "TV", "", "").backVisibility());
	}

	@Test
	public void automotiveKeepsSinglePlaybackBackOwner() {
		assertEquals(GONE, TopBarPolicy.resolve(RuntimeHostMode.AA_PROJECTION,
				false, true, false, false, true,
				2, 2, "TV", "VTV3", "").backVisibility());
		assertEquals(GONE, TopBarPolicy.resolve(RuntimeHostMode.AA_PROJECTION,
				true, false, false, true, false,
				2, 0, "TV", "", "").backVisibility());
		assertEquals(VISIBLE, TopBarPolicy.resolve(RuntimeHostMode.AA_PROJECTION,
				true, false, false, false, false,
				2, 0, "TV", "", "").backVisibility());
	}

	@Test
	public void playbackOwnerTitleSemanticsAreHostIndependent() {
		TopBarPolicy.State phone = TopBarPolicy.resolve(RuntimeHostMode.PHONE,
				false, true, false, false, false,
				42, 42, "Local channels", "VTV3", "Preparing");
		TopBarPolicy.State auto = TopBarPolicy.resolve(RuntimeHostMode.AA_PROJECTION,
				false, true, false, false, false,
				42, 42, "Local channels", "VTV3", "Preparing");
		assertEquals("VTV3 | Preparing", phone.title());
		assertEquals(phone.title(), auto.title());
	}

	@Test
	public void unrelatedPlaybackCannotReplaceRouteTitle() {
		TopBarPolicy.State state = TopBarPolicy.resolve(RuntimeHostMode.PHONE,
				true, false, false, true, false,
				42, 99, "TV", "Other playback", "Preparing");
		assertEquals("TV", state.title());
	}
}
