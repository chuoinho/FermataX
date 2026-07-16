package me.aap.fermata.ui.fragment;

import me.aap.fermata.R;
import me.aap.fermata.action.Action;
import me.aap.fermata.action.Key;
import me.aap.utils.pref.PreferenceSet;

final class KeyBindingPrefsBuilder {
	private KeyBindingPrefsBuilder() {
	}

	static void add(PreferenceSet parent) {
		PreferenceSet bindings = parent.subSet(o -> o.title = R.string.key_bindings);
		var actions = Action.getAll();
		int[] actionNames = new int[actions.size()];
		int[] actionOrdinals = new int[actions.size()];
		for (Action action : actions) {
			actionNames[action.ordinal()] = action.getName();
			actionOrdinals[action.ordinal()] = action.ordinal();
		}

		for (Key key : Key.getAll()) {
			PreferenceSet keySet = bindings.subSet(o -> o.ctitle = key.name());
			keySet.addListPref(o -> {
				o.store = Key.getPrefs();
				o.pref = key.getActionPref();
				o.title = R.string.key_on_click;
				o.subtitle = R.string.string_format;
				o.values = actionNames;
				o.valuesMap = actionOrdinals;
				o.formatSubtitle = true;
			});
			keySet.addListPref(o -> {
				o.store = Key.getPrefs();
				o.pref = key.getLongActionPref();
				o.title = R.string.key_on_long_click;
				o.subtitle = R.string.string_format;
				o.values = actionNames;
				o.valuesMap = actionOrdinals;
				o.formatSubtitle = true;
			});
			keySet.addListPref(o -> {
				o.store = Key.getPrefs();
				o.pref = key.getDblActionPref();
				o.title = R.string.key_on_dbl_click;
				o.subtitle = R.string.string_format;
				o.values = actionNames;
				o.valuesMap = actionOrdinals;
				o.formatSubtitle = true;
			});
		}
	}
}
