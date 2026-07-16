package me.aap.fermata.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import me.aap.utils.async.Promise;

public class AddonRuntimeStateTest {
	@Test
	public void removingAddonPreservesUnrelatedAndSharedModuleEntries() {
		AddonInfo firstInfo = info("shared", "test.First", 1);
		AddonInfo secondInfo = info("shared", "test.Second", 2);
		AddonInfo uniqueInfo = info("unique", "test.Unique", 3);
		AddonRuntimeState state = new AddonRuntimeState(
				new AddonRegistry(new AddonInfo[]{firstInfo, secondInfo, uniqueInfo}));
		TestAddon first = new TestAddon(firstInfo);
		TestAddon second = new TestAddon(secondInfo);
		TestAddon unique = new TestAddon(uniqueInfo);
		state.add(first);
		state.add(second);
		state.add(unique);
		state.activate(firstInfo, first);
		state.activate(secondInfo, second);
		state.activate(uniqueInfo, unique);

		assertNull(state.get("shared"));
		assertSame(unique, state.get("unique"));
		assertSame(first, state.remove(firstInfo));
		assertNull(state.get(firstInfo.className));
		assertSame(second, state.get(secondInfo.className));
		assertSame(unique, state.get(uniqueInfo.className));
		assertEquals(List.of(second, unique), state.getAll());
	}

	@Test
	public void failedAndInstallingStateAreScopedToExactAddon() {
		AddonInfo first = info("first", "test.First", 1);
		AddonInfo second = info("second", "test.Second", 2);
		AddonRuntimeState state =
				new AddonRuntimeState(new AddonRegistry(new AddonInfo[]{first, second}));
		Promise<Void> task = new Promise<>();

		state.markFailed(first);
		assertTrue(state.setInstallingIfAbsent(second, task));

		assertTrue(state.isFailed(first));
		assertFalse(state.isFailed(second));
		assertTrue(state.isInstalling(second, task));
		assertFalse(state.isInstalling(first));
		state.removeInstalling(second, task);
		assertFalse(state.isInstalling(second));
	}

	@Test
	public void zeroIdsDoNotAliasOrRemoveAnotherAddon() {
		AddonInfo firstInfo = info("first", "test.First", 0);
		AddonInfo secondInfo = info("second", "test.Second", 0);
		AddonRuntimeState state =
				new AddonRuntimeState(new AddonRegistry(new AddonInfo[]{firstInfo, secondInfo}));
		TestAddon first = new TestAddon(firstInfo);
		TestAddon second = new TestAddon(secondInfo);

		state.add(first);
		state.add(second);
		state.activate(firstInfo, first);
		state.activate(secondInfo, second);

		assertNull(state.get(0));
		assertSame(first, state.remove(firstInfo));
		assertSame(second, state.get(secondInfo.className));
		assertEquals(List.of(second), state.getAll());
	}

	@Test
	public void declaredZeroIdsIgnoreSharedInheritedRuntimeId() {
		AddonInfo firstInfo = info("first", "test.First", 0);
		AddonInfo secondInfo = info("second", "test.Second", 0);
		AddonRuntimeState state =
				new AddonRuntimeState(new AddonRegistry(new AddonInfo[]{firstInfo, secondInfo}));
		FermataAddon first = new RuntimeIdAddon(firstInfo, 42);
		FermataAddon second = new RuntimeIdAddon(secondInfo, 42);

		state.add(first);
		state.add(second);
		state.activate(firstInfo, first);
		state.activate(secondInfo, second);

		assertNull(state.get(42));
		assertSame(first, state.get(firstInfo.className));
		assertSame(second, state.get(secondInfo.className));
		assertEquals(List.of(first, second), state.getAll());
	}

	@Test
	public void duplicateNonZeroIdCannotReplaceLoadedAddon() {
		AddonInfo firstInfo = info("first", "test.First", 1);
		AddonInfo secondInfo = info("second", "test.Second", 1);
		AddonRuntimeState state =
				new AddonRuntimeState(new AddonRegistry(new AddonInfo[]{firstInfo, secondInfo}));
		TestAddon first = new TestAddon(firstInfo);
		TestAddon second = new TestAddon(secondInfo);
		state.add(first);
		state.activate(firstInfo, first);

		assertThrows(IllegalStateException.class, () -> state.add(second));
		assertSame(first, state.get(1));
		assertSame(first, state.get(firstInfo.className));
		assertNull(state.get(secondInfo.className));
		assertEquals(List.of(first), state.getAll());
	}

	@Test
	public void installingTaskReplacementRequiresCurrentIdentity() {
		AddonInfo info = info("module", "test.Addon", 1);
		AddonRuntimeState state = new AddonRuntimeState(new AddonRegistry(new AddonInfo[]{info}));
		Promise<Void> first = new Promise<>();
		Promise<Void> stale = new Promise<>();
		Promise<Void> retry = new Promise<>();

		assertTrue(state.setInstallingIfAbsent(info, first));
		assertFalse(state.setInstallingIfAbsent(info, stale));
		assertFalse(state.replaceInstalling(info, stale, retry));
		assertSame(first, state.getInstalling(info));
		assertTrue(state.replaceInstalling(info, first, retry));
		assertSame(retry, state.getInstalling(info));
		state.removeInstalling(info, first);
		assertSame(retry, state.getInstalling(info));
	}

	private static AddonInfo info(String module, String className, int id) {
		return new AddonInfo(module, className, id, id, id, id,
				false, false, false, false, "");
	}

	private record TestAddon(AddonInfo info) implements FermataAddon {
		@Override
		public int getAddonId() {
			return info.addonId;
		}

		@Override
		public AddonInfo getInfo() {
			return info;
		}
	}

	private record RuntimeIdAddon(AddonInfo info, int runtimeId) implements FermataAddon {
		@Override
		public int getAddonId() {
			return runtimeId;
		}

		@Override
		public AddonInfo getInfo() {
			return info;
		}
	}
}
