package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceStore;

@RunWith(RobolectricTestRunner.class)
public class DynamicPreferenceVisibilityTest {
	@Test
	public void preferenceListsAllowRowsToChangeVisibility() {
		assertFalse(new PreferenceSet().createView(RuntimeEnvironment.getApplication(), false)
				.hasFixedSize());
	}

	@Test
	public void copiedVisibilityConditionsNotifyEveryDependentRow() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> equalizerEnabled =
				PreferenceStore.Pref.b("enabled", false);
		PreferenceStore.Pref<me.aap.utils.function.BooleanSupplier> profileEnabled =
				PreferenceStore.Pref.b("profile", true);
		var condition = PrefCondition.create(store, equalizerEnabled)
				.and(PrefCondition.create(store, profileEnabled));
		var first = condition.copy();
		var second = condition.copy();
		int[] notifications = new int[2];
		first.setListener(ignored -> notifications[0]++);
		second.setListener(ignored -> notifications[1]++);

		store.applyBooleanPref(equalizerEnabled, true);

		assertEquals(1, notifications[0]);
		assertEquals(1, notifications[1]);
	}
}
