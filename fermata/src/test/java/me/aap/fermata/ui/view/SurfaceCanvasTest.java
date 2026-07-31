package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import android.view.SurfaceHolder;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class SurfaceCanvasTest {
	@Test
	public void nullCanvasIsNeverUnlocked() {
		AtomicInteger lockCalls = new AtomicInteger();
		AtomicInteger unlockCalls = new AtomicInteger();
		SurfaceHolder holder = (SurfaceHolder) Proxy.newProxyInstance(
				SurfaceHolder.class.getClassLoader(), new Class<?>[]{SurfaceHolder.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "lockCanvas" -> {
						lockCalls.incrementAndGet();
						yield null;
					}
					case "unlockCanvasAndPost" -> {
						unlockCalls.incrementAndGet();
						yield null;
					}
					default -> null;
				});

		assertFalse(SurfaceCanvas.draw(holder, canvas -> fail("Canvas must be absent")));
		assertEquals(1, lockCalls.get());
		assertEquals(0, unlockCalls.get());
	}

	@Test
	public void lockFailureIsContained() {
		SurfaceHolder holder = (SurfaceHolder) Proxy.newProxyInstance(
				SurfaceHolder.class.getClassLoader(), new Class<?>[]{SurfaceHolder.class},
				(proxy, method, args) -> {
					if ("lockCanvas".equals(method.getName()))
						throw new IllegalArgumentException("surface owned by decoder");
					return null;
				});

		assertFalse(SurfaceCanvas.draw(holder, canvas -> fail("Canvas must be absent")));
	}
}
