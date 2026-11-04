package me.app.fermatax.auto.carapps.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.ActivityManager;
import android.app.TaskInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HiddenTest {

	public static class FakeEmptyTaskManager {
	}

	private static ActivityManager.RunningTaskInfo task(int id, int displayId) throws Exception {
		ActivityManager.RunningTaskInfo t = new ActivityManager.RunningTaskInfo();
		t.taskId = id;
		TaskInfo.class.getField("displayId").setInt(t, displayId);
		return t;
	}

	/** Android 10-11 IActivityTaskManager.getTasks(maxNum): every display, most recent first. */
	public static class FakeOneArgTasks {
		public List<ActivityManager.RunningTaskInfo> getTasks(int maxNum) throws Exception {
			return Arrays.asList(task(1, 0), task(2, 5));
		}
	}

	/** Early android13-release: getTasks(maxNum, filterOnlyVisibleRecents, keepIntentExtra). */
	public static class FakeThreeArgTasks {
		public List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, boolean visible, boolean extras) throws Exception {
			return Arrays.asList(task(1, 0), task(3, 5));
		}
	}

	/** Android 12+: the display is a parameter and the system filters. */
	public static class FakeFourArgTasks {
		public List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, boolean visible, boolean extras, int displayId) throws Exception {
			return Arrays.asList(task(7, displayId));
		}
	}

	@Test
	public void topTaskWorksWithEveryGetTasksSignature() throws Exception {
		Method one = Hidden.findGetTasksMethod(FakeOneArgTasks.class);
		Method three = Hidden.findGetTasksMethod(FakeThreeArgTasks.class);
		Method four = Hidden.findGetTasksMethod(FakeFourArgTasks.class);
		assertEquals(1, one.getParameterCount());
		assertEquals(3, three.getParameterCount());
		assertEquals(4, four.getParameterCount());
		assertEquals(2, Hidden.topTask(new FakeOneArgTasks(), one, 5).taskId);
		assertEquals(3, Hidden.topTask(new FakeThreeArgTasks(), three, 5).taskId);
		assertEquals(7, Hidden.topTask(new FakeFourArgTasks(), four, 5).taskId);
	}

	@Test
	public void topTaskIsNullWhenTheCarDisplayHasNoTask() throws Exception {
		assertNull(Hidden.topTask(new FakeOneArgTasks(), Hidden.findGetTasksMethod(FakeOneArgTasks.class), 9));
	}

	@Test
	public void noGetTasksMethodMeansNoTaskListing() {
		assertNull(Hidden.findGetTasksMethod(FakeEmptyTaskManager.class));
	}

	@Test
	public void android13TopTaskSkipsTheAppOnThePhone() throws Exception {
		assertEquals(2, Hidden.topOnDisplay(Arrays.asList(task(1, 0), task(2, 5), task(3, 5)), 5).taskId);
	}

	@Test
	public void android13HasNoTopTaskWhenTheCarDisplayIsEmpty() throws Exception {
		assertNull(Hidden.topOnDisplay(Arrays.asList(task(1, 0), task(2, 0)), 5));
	}

	public static class FakeTaskManagerWithRemove {
		public List<ActivityManager.RunningTaskInfo> getTasks(int maxNum) {
			return java.util.Collections.emptyList();
		}
		public boolean removeTask(int taskId) {
			return true;
		}
	}

	@Test
	public void taskSupportRequiresBothGetTasksAndRemoveTask() {
		Method getTasks = Hidden.findGetTasksMethod(FakeTaskManagerWithRemove.class);
		org.junit.Assert.assertNotNull(getTasks);
		try {
			Method remove = FakeTaskManagerWithRemove.class.getMethod("removeTask", int.class);
			org.junit.Assert.assertNotNull(remove);
		} catch (NoSuchMethodException e) {
			org.junit.Assert.fail("Should have removeTask");
		}
		org.junit.Assert.assertNull(Hidden.findGetTasksMethod(FakeEmptyTaskManager.class));
	}
}
