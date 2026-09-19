package me.app.fermatax.auto;

import static org.junit.Assert.assertEquals;
import static me.aap.utils.async.Completed.completed;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.fermata.auto.AutomotiveNavigationController.OpenResult;
import me.aap.fermata.auto.OpenOnCarKind;
import me.aap.fermata.auto.OpenOnCarRequest;
import me.aap.fermata.auto.OpenOnCarToken;

public class MainCarActivityOpenOnCarReceiverTest {
	@Test
	public void webUrlStopsAtReceiverBeforeAddonContinuation() {
		AtomicInteger addonContinuations = new AtomicInteger();
		OpenOnCarRequest request = new OpenOnCarRequest(OpenOnCarKind.WEB_URL, 71,
				"https://example.test", new OpenOnCarToken(1, 1, 1, 1, 1));

		OpenResult result = MainCarActivity.dispatchPhoneRequest(request, () -> true, id -> {
			addonContinuations.incrementAndGet();
			return completed(OpenResult.OPENED);
		}).peek();

		assertEquals(OpenResult.NOT_READY, result);
		assertEquals(0, addonContinuations.get());
	}
}
