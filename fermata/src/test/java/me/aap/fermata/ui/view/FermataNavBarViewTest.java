package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FermataNavBarViewTest {
	@Test
	public void childClipReservesOnlyVisibleHintGutters() {
		assertEquals(new FermataNavBarView.ChildClip(0, 358),
				FermataNavBarView.childClip(0, 400, 142, 42));
		assertEquals(new FermataNavBarView.ChildClip(92, 408),
				FermataNavBarView.childClip(50, 400, 142, 42));
		assertEquals(new FermataNavBarView.ChildClip(184, 542),
				FermataNavBarView.childClip(142, 400, 142, 42));
	}
}
