package me.aap.utils.pref;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class PrefUtils {

	public static File getSharedPrefsFile(Context ctx, String name) {
		File file = null;

		try {
			if (VERSION.SDK_INT >= 24) {
				try {
					Method m = ctx.getClass().getMethod("getSharedPreferencesPath", String.class);
					file = (File) m.invoke(ctx, new Object[]{name});
				} catch (Throwable ex) {
					Log.w(ex, "getSharedPreferencesPath failed for ", name);
				}
			}

			if (file == null) {
				Method m = ctx.getClass().getMethod("getSharedPrefsFile", String.class);
				file = (File) m.invoke(ctx, new Object[]{name});
			}
		} catch (Throwable ex) {
			Log.w(ex, "getSharedPrefsFile failed for ", name);
			file = new File(ctx.getFilesDir(), "../shared_prefs/" + name + ".xml").getAbsoluteFile();
		}

		return file;
	}

	@SuppressWarnings("unchecked")
	public static void copySharedPrefs(SharedPreferences from, SharedPreferences to) {
		SharedPreferences.Editor ed = to.edit();
		for (Map.Entry<String, ?> entry : from.getAll().entrySet()) {
			String k = entry.getKey();
			Object v = entry.getValue();
			if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
			else if (v instanceof Float) ed.putFloat(k, (Float) v);
			else if (v instanceof Integer) ed.putInt(k, (Integer) v);
			else if (v instanceof Long) ed.putLong(k, (Long) v);
			else if (v instanceof String) ed.putString(k, (String) v);
			else if (v instanceof Set) ed.putStringSet(k, (Set<String>) v);
			else Log.w("Unsupported preference ", k, " of type ", v.getClass());
		}
		ed.apply();
	}
}
