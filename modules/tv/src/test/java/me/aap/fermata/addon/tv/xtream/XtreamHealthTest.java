package me.aap.fermata.addon.tv.xtream;

import org.junit.Assert;
import org.junit.Test;

public class XtreamHealthTest extends Assert {

	@Test
	public void accountFailureReportsAuthentication() {
		XtreamHealth health = new XtreamHealth(account());
		health.setStatus(new XtreamStatus(false, null, null));

		assertTrue(health.accountFailure().getMessage().contains("authentication failed"));
	}

	@Test
	public void accountFailureReportsExpired() {
		XtreamHealth health = new XtreamHealth(account());
		health.setStatus(new XtreamStatus(true, "Active", null, 1, 0, 1));

		assertTrue(health.accountFailure().getMessage().contains("expired"));
	}

	@Test
	public void accountFailureReportsExpiredBeforeAuthentication() {
		XtreamHealth health = new XtreamHealth(account());
		health.setStatus(new XtreamStatus(false, "Expired", null, 0, 0, 1));

		assertTrue(health.accountFailure().getMessage().contains("expired"));
	}

	@Test
	public void accountFailureReportsNoFreeConnectionSlot() {
		XtreamHealth health = new XtreamHealth(account());
		health.setStatus(new XtreamStatus(true, "Active", null, 0, 2, 2));

		assertTrue(health.accountFailure().getMessage().contains("no free connection slots"));
	}

	@Test
	public void statusTracksExpiryAndConnectionSlots() {
		XtreamStatus status = new XtreamStatus(true, "Active", null, 4102444800L, 1, 3);

		assertTrue(status.isAuthenticated());
		assertTrue(status.isActive());
		assertFalse(status.isExpired());
		assertTrue(status.hasFreeConnectionSlot());
		assertEquals(4102444800L, status.getExpiryTime());
		assertEquals(1, status.getActiveConnections());
		assertEquals(3, status.getMaxConnections());
	}

	@Test
	public void streamFailureReportsDeadStream() {
		XtreamHealth health = new XtreamHealth(account());
		health.setTestedStream(new XtreamChannel(42, "News", null, null, false, 0, null));

		assertTrue(health.streamFailure(404, "Not Found").getMessage().contains("dead"));
	}

	private static XtreamAccount account() {
		return new XtreamAccount(0, "Test", 0, "example.com", 80, "user", "pass",
				0, null, 0);
	}
}
