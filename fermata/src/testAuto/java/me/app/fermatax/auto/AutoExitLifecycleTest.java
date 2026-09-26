package me.app.fermatax.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.android.gms.car.CarActivityHost;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AutoExitLifecycleTest {

	@Test
	public void mainCarActivityFinishInvokesHostFinish() {
		MainCarActivity activity = new MainCarActivity();
		AtomicInteger finishCount = new AtomicInteger();
		CarActivityHost host = (CarActivityHost) Proxy.newProxyInstance(
				getClass().getClassLoader(),
				new Class<?>[]{CarActivityHost.class},
				(proxy, method, args) -> {
					if ("finish".equals(method.getName())) {
						finishCount.incrementAndGet();
					}
					return null;
				});

		activity.attach(host);
		assertNotNull(activity.getCarActivityHost());

		activity.finish();
		assertEquals(1, finishCount.get());

		// Second finish should be ignored due to finishing guard
		activity.finish();
		assertEquals(1, finishCount.get());
	}

	@Test
	public void mirrorActivityFinishInvokesHostFinish() {
		MirrorActivity activity = new MirrorActivity();
		AtomicInteger finishCount = new AtomicInteger();
		CarActivityHost host = (CarActivityHost) Proxy.newProxyInstance(
				getClass().getClassLoader(),
				new Class<?>[]{CarActivityHost.class},
				(proxy, method, args) -> {
					if ("finish".equals(method.getName())) {
						finishCount.incrementAndGet();
					}
					return null;
				});

		activity.attach(host);
		assertNotNull(activity.getCarActivityHost());

		activity.finish();
		assertEquals(1, finishCount.get());

		// Second finish should be ignored due to finishing guard
		activity.finish();
		assertEquals(1, finishCount.get());
	}
}
