package me.app.fermatax.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class CarTextInputSessionTest {
	@Test
	public void submitCompletesExactlyOnce() {
		CarTextInputSession session = new CarTextInputSession();
		AtomicInteger completions = new AtomicInteger();
		AtomicReference<String> value = new AtomicReference<>();
		session.getResult().onSuccess(result -> {
			value.set(result);
			completions.incrementAndGet();
		});

		assertTrue(session.submit("planet money"));
		assertFalse(session.submit("stale"));
		assertFalse(session.cancel());
		assertEquals("planet money", value.get());
		assertEquals(1, completions.get());
		assertTrue(session.isDone());
	}

	@Test
	public void cancelCompletesExactlyOnceWithoutSuccess() {
		CarTextInputSession session = new CarTextInputSession();
		AtomicInteger successes = new AtomicInteger();
		AtomicInteger cancellations = new AtomicInteger();
		session.getResult().onSuccess(result -> successes.incrementAndGet());
		session.getResult().onCancel(cancellations::incrementAndGet);

		assertTrue(session.cancel());
		assertFalse(session.cancel());
		assertFalse(session.submit("late"));
		assertEquals(0, successes.get());
		assertEquals(1, cancellations.get());
		assertTrue(session.isDone());
	}
}
