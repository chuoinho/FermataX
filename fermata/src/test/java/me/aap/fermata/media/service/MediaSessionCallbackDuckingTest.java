package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib;

@RunWith(RobolectricTestRunner.class)
public class MediaSessionCallbackDuckingTest {

	@Test
	public void duckSwitchesOwnershipAcrossEnginesAndRestoresOnGain() throws Exception {
		AtomicInteger aDuck = new AtomicInteger();
		AtomicInteger aUnduck = new AtomicInteger();
		AtomicInteger bDuck = new AtomicInteger();
		AtomicInteger bUnduck = new AtomicInteger();

		MediaEngine engineA = mockEngine("EngineA", aDuck, aUnduck);
		MediaEngine engineB = mockEngine("EngineB", bDuck, bUnduck);

		MediaSessionCallback callback = allocate(MediaSessionCallback.class);
		set(callback, "lib", mockMediaLib());
		set(callback, "engine", engineA);

		// 1. Audio focus loss transient can duck -> duck
		callback.duck();
		assertTrue("Callback should be ducked", callback.isDucked());
		assertSame(engineA, callback.getDuckedEngine());
		assertEquals("Engine A should be ducked", 1, aDuck.get());
		assertEquals("Engine A should not be unducked yet", 0, aUnduck.get());

		// 2. Engine change: A -> B while still in ducked state
		Method syncMethod = MediaSessionCallback.class.getDeclaredMethod("syncDuckingOnEngineChange", MediaEngine.class);
		syncMethod.setAccessible(true);
		set(callback, "engine", engineB);
		syncMethod.invoke(callback, engineB);

		assertTrue("Callback must stay ducked during engine change", callback.isDucked());
		assertSame("Ducked engine must track replacement B", engineB, callback.getDuckedEngine());
		assertEquals("Engine A must be unducked upon replacement", 1, aUnduck.get());
		assertEquals("Engine B must be immediately ducked to prevent volume blast", 1, bDuck.get());

		// 3. Audio focus gain -> unduck
		callback.unduck();
		assertFalse("Ducked state must be cleared after focus gain", callback.isDucked());
		assertNull("Ducked engine reference must be cleared", callback.getDuckedEngine());
		assertEquals("Engine B must be unducked on focus gain", 1, bUnduck.get());
	}

	@Test
	public void duckWhileEngineNullAppliesToEngineWhenStarted() throws Exception {
		AtomicInteger bDuck = new AtomicInteger();
		AtomicInteger bUnduck = new AtomicInteger();
		MediaEngine engineB = mockEngine("EngineB", bDuck, bUnduck);

		MediaSessionCallback callback = allocate(MediaSessionCallback.class);
		set(callback, "lib", mockMediaLib());

		// 1. Duck occurs while no active engine
		callback.duck();
		assertTrue(callback.isDucked());
		assertNull(callback.getDuckedEngine());

		// 2. Engine B arrives
		Method syncMethod = MediaSessionCallback.class.getDeclaredMethod("syncDuckingOnEngineChange", MediaEngine.class);
		syncMethod.setAccessible(true);
		set(callback, "engine", engineB);
		syncMethod.invoke(callback, engineB);

		assertSame("Engine B must be adopted as ducked engine", engineB, callback.getDuckedEngine());
		assertEquals("Engine B must be ducked", 1, bDuck.get());

		// 3. Unduck restores Engine B
		callback.unduck();
		assertFalse(callback.isDucked());
		assertEquals("Engine B must be unducked", 1, bUnduck.get());
	}

	private static MediaLib mockMediaLib() {
		return (MediaLib) Proxy.newProxyInstance(MediaLib.class.getClassLoader(),
				new Class<?>[]{MediaLib.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getContext" -> RuntimeEnvironment.getApplication();
					default -> defaultValue(method.getReturnType());
				});
	}

	private static MediaEngine mockEngine(String name, AtomicInteger duckCount, AtomicInteger unduckCount) {
		return (MediaEngine) Proxy.newProxyInstance(MediaEngine.class.getClassLoader(),
				new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> switch (method.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> name;
					case "duck" -> {
						duckCount.incrementAndGet();
						yield null;
					}
					case "unduck" -> {
						unduckCount.incrementAndGet();
						yield null;
					}
					default -> defaultValue(method.getReturnType());
				});
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		if (type == double.class) return 0D;
		if (type == char.class) return '\0';
		return null;
	}

	private static <T> T allocate(Class<T> type) throws Exception {
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		Field field = unsafeType.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		Object unsafe = field.get(null);
		return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
	}

	private static void set(Object target, String name, Object value) throws Exception {
		Field field = MediaSessionCallback.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
