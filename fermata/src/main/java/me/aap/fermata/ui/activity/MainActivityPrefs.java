package me.aap.fermata.ui.activity;

import static me.aap.fermata.BuildConfig.AUTO;
import static android.view.KeyEvent.KEYCODE_UNKNOWN;

import android.content.pm.PackageInfo;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.view.NavBarView;

/**
 * @author Andrey Pavlenko
 */
public interface MainActivityPrefs
		extends SharedPreferenceStore, EventBroadcaster<PreferenceStore.Listener> {
	int THEME_DARK = 0;
	int THEME_LIGHT = 1;
	int THEME_SYSTEM = 2;
	int THEME_BLACK = 3;
	int THEME_STAR_WARS = 4;
	int THEME_PURPLE = 5;
	int THEME_CLASSIC = 6;
	int THEME_MATERIAL_YOU = 7;
	int THEME_GLASS = 9;
	int CLOCK_POS_NONE = 0;
	int CLOCK_POS_LEFT = 1;
	int CLOCK_POS_RIGHT = 2;
	int CLOCK_POS_CENTER = 3;

	Pref<Supplier<String>> PREF_GLASS_QUICK_SHORTCUTS = Pref.s("GLASS_QUICK_SHORTCUTS", "[]");
	Pref<IntSupplier> GLASS_SPEED_UNIT = Pref.i("GLASS_SPEED_UNIT", 0);
	Pref<IntSupplier> THEME_MAIN = Pref.i("THEME_MAIN", THEME_CLASSIC);
	Pref<BooleanSupplier> HIDE_BARS = Pref.b("HIDE_BARS", false);
	Pref<BooleanSupplier> FULLSCREEN = Pref.b("FULLSCREEN", false);
	Pref<BooleanSupplier> SHOW_PG_UP_DOWN = Pref.b("SHOW_PG_UP_DOWN", true);
	Pref<BooleanSupplier> USE_DPAD_CURSOR = AUTO ? Pref.b("USE_DPAD_CURSOR", true) : null;
	Pref<IntSupplier> NAV_BAR_POS = Pref.i("NAV_BAR_POS", NavBarView.POSITION_LEFT);
	Pref<DoubleSupplier> NAV_BAR_SIZE = Pref.f("NAV_BAR_SIZE", 1f);
	Pref<DoubleSupplier> TOOL_BAR_SIZE = Pref.f("TOOL_BAR_SIZE", 1f);
	Pref<DoubleSupplier> CONTROL_PANEL_SIZE = Pref.f("CONTROL_PANEL_SIZE", 1f);
	Pref<DoubleSupplier> TEXT_ICON_SIZE = Pref.f("TEXT_ICON_SIZE", 1f);
	Pref<BooleanSupplier> GRID_VIEW = Pref.b("GRID_VIEW", false);
	Pref<DoubleSupplier> P_SPLIT_PERCENT = Pref.f("P_SPLIT_PERCENT", 0.6f);
	Pref<DoubleSupplier> L_SPLIT_PERCENT = Pref.f("L_SPLIT_PERCENT", 0.4f);
	Pref<DoubleSupplier> P_SPLIT_PERCENT_SUB = Pref.f("P_SPLIT_PERCENT_SUB", 0.5f);
	Pref<DoubleSupplier> L_SPLIT_PERCENT_SUB = Pref.f("L_SPLIT_PERCENT_SUB", 0.5f);
	Pref<Supplier<String>> SHOW_ADDON_ON_START = Pref.s("SHOW_ADDON_ON_START", (String) null);
	Pref<BooleanSupplier> SYS_BARS_ON_VIDEO_TOUCH = Pref.b("SYS_BARS_ON_VIDEO_TOUCH", false);
	Pref<BooleanSupplier> LANDSCAPE_VIDEO = Pref.b("LANDSCAPE_VIDEO", false);
	Pref<BooleanSupplier> CHANGE_BRIGHTNESS = Pref.b("CHANGE_BRIGHTNESS", false);
	Pref<IntSupplier> BRIGHTNESS = Pref.i("BRIGHTNESS", 255);
	Pref<BooleanSupplier> VOICE_CONTROl_ENABLED = Pref.b("VOICE_CONTROl_ENABLED", true);
	Pref<BooleanSupplier> VOICE_CONTROl_FB = Pref.b("VOICE_CONTROl_FB", false);
	Pref<Supplier<String>> VOICE_CONTROL_SUBST = Pref.s("VOICE_CONTROL_SUBST", "");
	Pref<Supplier<String>> VOICE_CONTROL_LANG =
			Pref.s("VOICE_CONTROL_LANG", () -> Locale.getDefault().toLanguageTag());
	Pref<BooleanSupplier> VOICE_CONTROL_AUTO_LANG = Pref.b("VOICE_CONTROL_AUTO_LANG", false);
	Pref<IntSupplier> VOICE_TRIGGER_KEY_CODE =
			Pref.i("VOICE_TRIGGER_KEY_CODE", KEYCODE_UNKNOWN);
	Pref<BooleanSupplier> SMART_TOP_V2_ENABLED = Pref.b("smart_top/v2_enabled", true);
	Pref<BooleanSupplier> SMART_TOP_BACKGROUND_ENABLED =
			Pref.b("smart_top/background_enabled", true);
	Pref<Supplier<String>> SMART_TOP_RECENT_TITLE =
			Pref.s("smart_top/recent_title", (String) null);
	Pref<Supplier<String>> SMART_TOP_RECENT_SUBTITLE =
			Pref.s("smart_top/recent_subtitle", "");
	Pref<BooleanSupplier> SMART_TOP_RECENT_VIDEO =
			Pref.b("smart_top/recent_video", false);
	Pref<IntSupplier> CLOCK_POS = Pref.i("CLOCK_POS", CLOCK_POS_NONE);
	Pref<IntSupplier> LOCALE = Pref.i("LOCALE", Lang.EN.ordinal());
	int INITIAL_SETUP_CURRENT_VERSION = 1;
	Pref<BooleanSupplier> INITIAL_SETUP_COMPLETED =
			Pref.b("INITIAL_SETUP_COMPLETED", false);
	Pref<IntSupplier> INITIAL_SETUP_VERSION = Pref.i("INITIAL_SETUP_VERSION", 0);

	Pref<IntSupplier> THEME_AA = Pref.i("THEME_AA", THEME_GLASS);
	Pref<BooleanSupplier> HIDE_BARS_AA = AUTO ? Pref.b("HIDE_BARS_AA", false) : null;
	Pref<BooleanSupplier> FULLSCREEN_AA = AUTO ? Pref.b("FULLSCREEN_AA", false) : null;
	Pref<BooleanSupplier> SHOW_PG_UP_DOWN_AA = AUTO ? Pref.b("SHOW_PG_UP_DOWN_AA", true) : null;
	Pref<IntSupplier> NAV_BAR_POS_AA =
			AUTO ? Pref.i("NAV_BAR_POS_AA", NavBarView.POSITION_LEFT) : null;
	Pref<DoubleSupplier> NAV_BAR_SIZE_AA = AUTO ? Pref.f("NAV_BAR_SIZE_AA", 1.85f) : null;
	Pref<DoubleSupplier> TOOL_BAR_SIZE_AA = AUTO ? Pref.f("TOOL_BAR_SIZE_AA", 1f) : null;
	Pref<DoubleSupplier> CONTROL_PANEL_SIZE_AA = AUTO ? Pref.f("CONTROL_PANEL_SIZE_AA", 1f) : null;
	Pref<DoubleSupplier> TEXT_ICON_SIZE_AA = AUTO ? Pref.f("TEXT_ICON_SIZE_AA", 1f) : null;
	Pref<BooleanSupplier> GRID_VIEW_AA = AUTO ? Pref.b("GRID_VIEW_AA", false) : null;

	static MainActivityPrefs get() {
		return MainActivityPreferenceStore.INSTANCE;
	}

	static boolean hasThemePref(MainActivityDelegate a, List<Pref<?>> prefs) {
		return prefs.contains(THEME_MAIN) || (AUTO && prefs.contains(THEME_AA));
	}

	default int getThemePref(boolean auto) {
		if (AUTO && auto) {
			int aaTheme = getIntPref(THEME_AA);
			if (aaTheme == THEME_CLASSIC || aaTheme == THEME_MATERIAL_YOU || aaTheme == THEME_GLASS) {
				return aaTheme;
			}
			return THEME_GLASS;
		}
		int theme = getIntPref(THEME_MAIN);
		if (theme != THEME_CLASSIC && theme != THEME_MATERIAL_YOU && theme != THEME_GLASS) return THEME_CLASSIC;
		return theme;
	}

	default boolean isGlassTheme(boolean auto) {
		return auto ? (AUTO && getThemePref(true) == THEME_GLASS) : (getThemePref(false) == THEME_GLASS);
	}

	@Nullable
	default String getShowAddonOnStartPref() {
		return getStringPref(SHOW_ADDON_ON_START);
	}

	default void setShowAddonOnStartPref(@Nullable String className) {
		applyStringPref(SHOW_ADDON_ON_START, className);
	}

	static boolean hasFullscreenPref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(FULLSCREEN_AA);
		return prefs.contains(FULLSCREEN);
	}

	default boolean getFullscreenPref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getBooleanPref(FULLSCREEN_AA);
		return getBooleanPref(FULLSCREEN);
	}

	static boolean hasHideBarsPref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(HIDE_BARS_AA);
		return prefs.contains(HIDE_BARS);
	}

	default boolean getHideBarsPref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getBooleanPref(HIDE_BARS_AA);
		return getBooleanPref(HIDE_BARS);
	}

	default boolean getShowPgUpDownPref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getBooleanPref(SHOW_PG_UP_DOWN_AA);
		return getBooleanPref(SHOW_PG_UP_DOWN);
	}

	default boolean useDpadCursor(MainActivityDelegate a) {
		return AUTO && a.isCarActivity() && getBooleanPref(USE_DPAD_CURSOR);
	}

	static boolean hasNavBarPosPref(MainActivityDelegate a, List<Pref<?>> prefs) {
		return prefs.contains(NAV_BAR_POS) || (AUTO && prefs.contains(NAV_BAR_POS_AA));
	}

	default int getNavBarPosPref(MainActivityDelegate a) {
		Pref<IntSupplier> pref = (AUTO && a.isCarActivity()) ? NAV_BAR_POS_AA : NAV_BAR_POS;
		int pos = getIntPref(pref);
		int normalized = normalizeNavBarPosition(pos);
		if (normalized != pos) applyIntPref(pref, normalized);
		return normalized;
	}

	static int normalizeNavBarPosition(int position) {
		return (position == NavBarView.POSITION_RIGHT) ?
				NavBarView.POSITION_RIGHT : NavBarView.POSITION_LEFT;
	}

	static boolean hasNavBarSizePref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(NAV_BAR_SIZE_AA);
		return prefs.contains(NAV_BAR_SIZE);
	}

	default float getNavBarSizePref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) {
			if (hasPref(NAV_BAR_SIZE_AA, false)) return Math.max(1.75F, getFloatPref(NAV_BAR_SIZE_AA));
			int width = a.getContext().getResources().getConfiguration().screenWidthDp;
			if ((width > 0) && (width < 700)) return 1.75F;
			return (width >= 1000) ? 2F : 1.85F;
		}
		return getFloatPref(NAV_BAR_SIZE);
	}

	static boolean hasToolBarSizePref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(TOOL_BAR_SIZE_AA);
		return prefs.contains(TOOL_BAR_SIZE);
	}

	default float getToolBarSizePref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) {
			if (hasPref(TOOL_BAR_SIZE_AA, false)) return Math.max(1F, getFloatPref(TOOL_BAR_SIZE_AA));
			int width = a.getContext().getResources().getConfiguration().screenWidthDp;
			if ((width > 0) && (width < 700)) return 1.1F;
			return (width >= 1000) ? 1.3F : 1.2F;
		}
		return getFloatPref(TOOL_BAR_SIZE);
	}

	static boolean hasControlPanelSizePref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(CONTROL_PANEL_SIZE_AA);
		return prefs.contains(CONTROL_PANEL_SIZE);
	}

	default float getControlPanelSizePref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getFloatPref(CONTROL_PANEL_SIZE_AA);
		return getFloatPref(CONTROL_PANEL_SIZE);
	}

	static boolean hasTextIconSizePref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(TEXT_ICON_SIZE_AA);
		return prefs.contains(TEXT_ICON_SIZE);
	}

	default float getTextIconSizePref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getFloatPref(TEXT_ICON_SIZE_AA);
		return getFloatPref(TEXT_ICON_SIZE);
	}

	static boolean hasGridViewPref(MainActivityDelegate a, List<Pref<?>> prefs) {
		return prefs.contains(getGridViewPrefKey(a));
	}

	static Pref<BooleanSupplier> getGridViewPrefKey(MainActivityDelegate a) {
		return (AUTO && a.isCarActivity()) ? GRID_VIEW_AA : GRID_VIEW;
	}

	default boolean getGridViewPref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getBooleanPref(GRID_VIEW_AA);
		return getBooleanPref(GRID_VIEW);
	}

	default void setGridViewPref(MainActivityDelegate a, boolean value) {
		applyBooleanPref(getGridViewPrefKey(a), value);
	}

	default boolean getSysBarsOnVideoTouchPref() {
		return getBooleanPref(SYS_BARS_ON_VIDEO_TOUCH);
	}

	default boolean getLandscapeVideoPref() {
		return getBooleanPref(LANDSCAPE_VIDEO);
	}

	default boolean getChangeBrightnessPref() {
		return getBooleanPref(CHANGE_BRIGHTNESS);
	}

	default int getBrightnessPref() {
		return getIntPref(BRIGHTNESS);
	}

	default boolean getVoiceControlEnabledPref() {
		return getBooleanPref(VOICE_CONTROl_ENABLED);
	}

	default boolean getVoiceControlFBPref() {
		return getBooleanPref(VOICE_CONTROl_FB);
	}

	default String getVoiceControlLang(MainActivityDelegate a) {
		return a.getPrefs().getStringPref(VOICE_CONTROL_LANG);
	}

	default boolean getVoiceControlAutoLangPref() {
		return getBooleanPref(VOICE_CONTROL_AUTO_LANG);
	}

	default boolean isSmartTopV2Enabled() {
		return getBooleanPref(SMART_TOP_V2_ENABLED);
	}

	default boolean isSmartTopBackgroundEnabled() {
		return getBooleanPref(SMART_TOP_BACKGROUND_ENABLED);
	}

	default int getClockPosPref() {
		return getIntPref(CLOCK_POS);
	}

	default Locale getLocalePref() {
		return Lang.get(getIntPref(LOCALE)).locale;
	}

	/**
	 * Keeps first-run setup out of update and restore paths. The explicit marker wins;
	 * package install timestamps are only used when the marker is absent.
	 */
	static boolean shouldShowInitialSetup(long firstInstallTime, long lastUpdateTime,
												 boolean hasExistingPrefs, boolean setupCompleted) {
		if (setupCompleted || hasExistingPrefs) return false;
		return firstInstallTime == lastUpdateTime;
	}

	default boolean shouldShowInitialSetup(MainActivityDelegate activity) {
		return !getBooleanPref(INITIAL_SETUP_COMPLETED);
	}

	default void applyInitialSetupCompleted() {
		try (PreferenceStore.Edit e = editPreferenceStore()) {
			e.setBooleanPref(INITIAL_SETUP_COMPLETED, true);
			e.setIntPref(INITIAL_SETUP_VERSION, INITIAL_SETUP_CURRENT_VERSION);
		}
	}

	enum Lang {
		EN(Locale.ENGLISH),
		RU,
		IT(Locale.ITALIAN),
		TR,
		DE(Locale.GERMAN),
		PT,
		VI,
		PL,
		HR,
		JA,
		ZH_TW(Locale.TRADITIONAL_CHINESE),
		KO,
		FR(Locale.FRENCH),
    RO,
		AR,
		ES,
		KM,
    ;

		private static final List<Lang> values = List.of(values());
		private static final List<Lang> SUPPORTED_VALUES = List.of(EN, VI, RU, ES, PT, ZH_TW);
		private static final Map<String, Lang> nameToValue = new HashMap<>();
		public final Locale locale;

		static {
			for (var v : SUPPORTED_VALUES) {
				nameToValue.put(v.locale.getLanguage(), v);
			}
		}

		Lang() {
			locale = new Locale(name().toLowerCase());
		}

		Lang(Locale locale) {
			this.locale = locale;
		}

		public static List<Lang> getValues() {
			return SUPPORTED_VALUES;
		}

		public static Lang get(int pref) {
			if ((pref < 0) || (pref >= values.size())) return EN;
			Lang l = values.get(pref);
			return isSupported(l) ? l : EN;
		}

		public static boolean isSupported(Lang l) {
			return SUPPORTED_VALUES.contains(l);
		}

		public static Lang get(String name) {
			var value = nameToValue.get(name);
			return (value == null) ? EN : value;
		}
	}
}
