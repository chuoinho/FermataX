package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.R;

public class PhoneBottomMenuControllerTest {
	@Test
	public void renderSelectsPhoneRootWithoutUsingAddonSelection() {
		assertEquals(R.id.dashboard_fragment,
				PhoneBottomMenuController.resolveSelectedRoot(R.id.dashboard_fragment));
		assertEquals(VISIBLE, PhoneBottomMenuController.resolveVisibility(true));
	}

	@Test
	public void renderHidesMenuWithoutChangingItsRootSelection() {
		assertEquals(R.id.settings_fragment,
				PhoneBottomMenuController.resolveSelectedRoot(R.id.settings_fragment));
		assertEquals(GONE, PhoneBottomMenuController.resolveVisibility(false));
	}
}
