package me.aap.fermata.ui.fragment;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static me.aap.fermata.ui.activity.MainActivityPrefs.INITIAL_SETUP_CURRENT_VERSION;
import static me.aap.fermata.ui.activity.MainActivityPrefs.INITIAL_SETUP_COMPLETED;
import static me.aap.fermata.ui.activity.MainActivityPrefs.INITIAL_SETUP_VERSION;
import static me.aap.fermata.ui.activity.MainActivityPrefs.LOCALE;
import static me.aap.fermata.ui.activity.MainActivityPrefs.NAV_BAR_POS;
import static me.aap.fermata.ui.activity.MainActivityPrefs.NAV_BAR_POS_AA;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_LANG;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_AUTO_LANG;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_ENABLED;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.function.Cancellable;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.NavBarView;

/** Phone-only first-run setup. It never initializes addons or changes source state. */
public class InitialSetupFragment extends me.aap.utils.ui.fragment.ActivityFragment {
	private static final int VOICE_SAME_AS_APP = 0;
	private static final int VOICE_SYSTEM = 1;
	private static final int VOICE_ENGLISH = 2;
	private static final int VOICE_VIETNAMESE = 3;
	private static final int VOICE_AUTO = 4;
	// Marks status text that keeps its semantic colour.
	private static final String STATE_TAG = "permission_state";
	private static final NavBarView.Mediator HIDDEN_NAV = new NavBarView.Mediator() {
		@Override
		public void enable(NavBarView view, me.aap.utils.ui.fragment.ActivityFragment fragment) {
			view.removeAllViews();
			view.setVisibility(GONE);
		}

		@Override
		public void disable(NavBarView view) {
			view.setVisibility(VISIBLE);
		}
	};
	private static final FloatingButton.Mediator HIDDEN_FLOATING = new FloatingButton.Mediator() {
		@Override
		public void enable(FloatingButton view, me.aap.utils.ui.fragment.ActivityFragment fragment) {
			view.setVisibility(GONE);
		}

		@Override
		public void disable(FloatingButton view) {
			view.setVisibility(VISIBLE);
		}
	};
	private Spinner appLanguage;
	private Spinner voiceLanguage;
	private RadioGroup navPosition;
	private SwitchCompat voiceEnabled;
	private Button microphoneTest;
	private TextView microphoneStatus;
	private LinearLayout permissions;
	private LinearLayout navigationPermissions;
	private View voiceDetails;
	private Cancellable speech;

	@Override
	public int getFragmentId() {
		return R.id.initial_setup_fragment;
	}

	@Override
	public MainActivityDelegate getActivityDelegate() {
		return (MainActivityDelegate) super.getActivityDelegate();
	}

	@Override
	public CharSequence getTitle() {
		return getString(R.string.initial_setup);
	}

	@Override
	public NavBarView.Mediator getNavBarMediator() {
		return HIDDEN_NAV;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return HIDDEN_FLOATING;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.initial_setup, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
		super.onViewCreated(view, state);
		MainActivityDelegate activity = getActivityDelegate();
		appLanguage = view.findViewById(R.id.initial_setup_app_language);
		voiceLanguage = view.findViewById(R.id.initial_setup_voice_language);
		navPosition = view.findViewById(R.id.initial_setup_nav_position);
		voiceEnabled = view.findViewById(R.id.initial_setup_voice_enabled);
		microphoneTest = view.findViewById(R.id.initial_setup_microphone_test);
		microphoneStatus = view.findViewById(R.id.initial_setup_microphone_status);
		permissions = view.findViewById(R.id.initial_setup_permissions);
		navigationPermissions = view.findViewById(R.id.initial_setup_navigation_permissions);
		voiceDetails = view.findViewById(R.id.initial_setup_voice_details);

		setupAppLanguage(activity);
		setupVoiceLanguage(activity);
		navPosition.check(activity.getPrefs().getNavBarPosPref(activity) == NavBarView.POSITION_RIGHT
				? R.id.initial_setup_nav_right : R.id.initial_setup_nav_left);
		voiceEnabled.setChecked(activity.getPrefs().getVoiceControlEnabledPref());
		voiceEnabled.setOnCheckedChangeListener((button, checked) -> {
			updateVoiceControls(checked);
			updatePermissions();
			if (checked && !isGranted(activity.getContext(), android.Manifest.permission.RECORD_AUDIO)) {
				requestVoiceMicrophone(activity);
			}
		});
		microphoneTest.setOnClickListener(v -> testMicrophone());
		view.findViewById(R.id.initial_setup_continue).setOnClickListener(v -> applyAndContinue());
		updateVoiceControls(voiceEnabled.isChecked());

		if (activity.isGlassTheme()) {
			View content = view.findViewById(R.id.initial_setup_nav_position).getParent() instanceof View ?
					(View) view.findViewById(R.id.initial_setup_nav_position).getParent() : view;
			content.setBackground(activity.getGlassCardDrawable(view.getContext(), 16f));
			android.content.res.ColorStateList cyanTint = android.content.res.ColorStateList.valueOf(0xFF00E5FF);
			android.content.res.ColorStateList trackTint = new android.content.res.ColorStateList(
					new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
					new int[]{0x6600E5FF, 0x40FFFFFF}
			);
			voiceEnabled.setThumbTintList(cyanTint);
			voiceEnabled.setTrackTintList(trackTint);
			boolean isNight = activity.isGlassNightMode();
			int actionColor = activity.getGlassActionColor();
			Button btnContinue = view.findViewById(R.id.initial_setup_continue);
			if (btnContinue != null) {
				btnContinue.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassPillDrawable(view.getContext(), 12f, isNight));
				btnContinue.setTextColor(actionColor);
			}
			if (microphoneTest != null) {
				microphoneTest.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassPillDrawable(view.getContext(), 12f, isNight));
				microphoneTest.setTextColor(actionColor);
			}
			view.findViewById(R.id.initial_setup_privacy).setBackground(activity.getGlassCardDrawable(view.getContext(), 12f));
		}
		updatePermissions();
	}

	@Override
	public void onResume() {
		super.onResume();
		updatePermissions();
	}

	/** One row per permission: what it is for, its state and, when missing, a button to grant it. */
	private void updatePermissions() {
		android.content.Context ctx = getContext();
		if ((permissions == null) || (ctx == null)) return;
		MainActivityDelegate activity = getActivityDelegate();
		updateVoiceControls(voiceEnabled.isChecked());
		permissions.removeAllViews();
		navigationPermissions.removeAllViews();
		addHeader(ctx, R.string.initial_setup_required);
		addRow(activity, R.string.initial_setup_perm_storage, R.string.initial_setup_perm_storage_sub,
				isStorageGranted(ctx), () -> requestStorage(activity));
		addHeader(ctx, R.string.initial_setup_optional);
		addLocationRow(activity);
		addRow(activity, R.string.initial_setup_perm_notifications, R.string.initial_setup_perm_notifications_sub,
				androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
				() -> requestNotifications(activity));
		addRow(activity, navigationPermissions, R.string.initial_setup_perm_nav, R.string.initial_setup_perm_nav_sub,
				me.aap.fermata.ui.control.PhoneReadiness.hasNotificationListenerCheck(ctx),
				() -> startSafely(activity, me.aap.fermata.ui.control.PhoneReadiness.createNotificationListenerSettingsIntent()));
		if (activity.isGlassTheme() && (getView() != null)) {
			applyGlassText(getView(), me.aap.fermata.ui.glass.theme.GlassThemeColors.get(ctx, activity.isGlassNightMode()));
		}
	}

	private void addLocationRow(MainActivityDelegate activity) {
		android.content.Context ctx = activity.getContext();
		var access = me.aap.fermata.ui.glass.speedo.TripModePolicy.access(
				me.aap.fermata.ui.glass.speedo.TripModePolicy.hasForegroundLocation(ctx),
				me.aap.fermata.ui.glass.speedo.TripModePolicy.hasBackgroundLocation(ctx));
		LinearLayout row = new LinearLayout(ctx);
		row.setGravity(android.view.Gravity.CENTER_VERTICAL);
		row.setMinimumHeight(dp(ctx, 56));
		row.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));
		android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
		shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
		shape.setColor(InterfacePrefsBuilder.locationAccessColor(access));
		View dot = new View(ctx);
		dot.setBackground(shape);
		LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(ctx, 10), dp(ctx, 10));
		dotLp.setMarginEnd(dp(ctx, 12));
		row.addView(dot, dotLp);

		LinearLayout texts = new LinearLayout(ctx);
		texts.setOrientation(LinearLayout.VERTICAL);
		TextView title = new TextView(ctx);
		title.setText(R.string.initial_setup_perm_location);
		title.setTypeface(null, android.graphics.Typeface.BOLD);
		TextView purpose = new TextView(ctx);
		purpose.setText(R.string.initial_setup_perm_location_sub);
		purpose.setTextAppearance(android.R.style.TextAppearance_Small);
		texts.addView(title);
		texts.addView(purpose);
		row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView status = new TextView(ctx);
		status.setText(InterfacePrefsBuilder.locationAccessState(access));
		status.setTextColor(InterfacePrefsBuilder.locationAccessColor(access));
		status.setTag(STATE_TAG);
		status.setPadding(dp(ctx, 10), 0, 0, 0);
		row.addView(status);
		if (access == me.aap.fermata.ui.glass.speedo.TripModePolicy.Access.NOT_GRANTED) {
			Button allow = new Button(ctx);
			allow.setText(R.string.initial_setup_allow);
			allow.setOnClickListener(v -> InterfacePrefsBuilder.manageLocationAccessFromInitialSetup(activity,
					this::updatePermissions));
			if (activity.isGlassTheme()) {
				allow.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassPillDrawable(
						ctx, 12f, activity.isGlassNightMode()));
				allow.setTextColor(activity.getGlassActionColor());
			}
			row.addView(allow);
		}
		boolean canManageBackground = BuildConfig.TRIP_BACKGROUND_SUPPORTED
				&& (access == me.aap.fermata.ui.glass.speedo.TripModePolicy.Access.WHILE_USING
				|| InterfacePrefsBuilder.offersPermissionDowngrade(ctx));
		row.setFocusable(access == me.aap.fermata.ui.glass.speedo.TripModePolicy.Access.NOT_GRANTED || canManageBackground);
		if (row.isFocusable()) {
			row.setOnClickListener(v -> InterfacePrefsBuilder.manageLocationAccessFromInitialSetup(activity,
					this::updatePermissions));
		}
		permissions.addView(row);
	}

	/** Headings use the theme's primary text colour, everything else its secondary colour. */
	private static void applyGlassText(View v, me.aap.fermata.ui.glass.theme.GlassThemeColors gc) {
		if (v instanceof android.widget.AdapterView) return; // spinner rows are styled by their own layouts
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) applyGlassText(g.getChildAt(i), gc);
		} else if (v instanceof android.widget.CompoundButton cb) {
			cb.setTextColor(gc.getTextPrimary());
		} else if ((v instanceof TextView tv) && !(v instanceof Button) && !STATE_TAG.equals(v.getTag())) {
			boolean heading = (tv.getTypeface() != null) && tv.getTypeface().isBold();
			tv.setTextColor(heading ? gc.getTextPrimary() : gc.getTextSecondary());
		}
	}

	private void addHeader(android.content.Context ctx, int text) {
		TextView tv = new TextView(ctx);
		tv.setText(text);
		tv.setTextAppearance(android.R.style.TextAppearance_Small);
		tv.setTypeface(null, android.graphics.Typeface.BOLD);
		tv.setAllCaps(true);
		tv.setPadding(0, dp(ctx, 16), 0, dp(ctx, 4));
		permissions.addView(tv);
	}

	private void addRow(MainActivityDelegate activity, int title, int purpose, boolean granted, Runnable request) {
		addRow(activity, permissions, title, purpose, granted, request);
	}

	private void addRow(MainActivityDelegate activity, LinearLayout destination, int title, int purpose,
						boolean granted, Runnable request) {
		android.content.Context ctx = destination.getContext();
		LinearLayout row = new LinearLayout(ctx);
		row.setGravity(android.view.Gravity.CENTER_VERTICAL);
		row.setMinimumHeight(dp(ctx, 56));
		row.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));

		View dot = new View(ctx);
		android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
		shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
		shape.setColor(granted ? 0xFF22C55E : 0xFFEF4444);
		dot.setBackground(shape);
		LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(ctx, 10), dp(ctx, 10));
		dotLp.setMarginEnd(dp(ctx, 12));
		row.addView(dot, dotLp);

		LinearLayout texts = new LinearLayout(ctx);
		texts.setOrientation(LinearLayout.VERTICAL);
		TextView tvTitle = new TextView(ctx);
		tvTitle.setText(title);
		tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
		TextView tvPurpose = new TextView(ctx);
		tvPurpose.setText(purpose);
		tvPurpose.setTextAppearance(android.R.style.TextAppearance_Small);
		texts.addView(tvTitle);
		texts.addView(tvPurpose);
		row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		if (granted) {
			// Text as well as colour, so the state does not depend on colour vision.
			TextView ok = new TextView(ctx);
			ok.setText(R.string.initial_setup_allowed);
			ok.setTextColor(0xFF22C55E);
			ok.setTag(STATE_TAG);
			ok.setPadding(dp(ctx, 12), 0, 0, 0);
			row.addView(ok);
		} else {
			Button allow = new Button(ctx);
			allow.setText(R.string.initial_setup_allow);
			allow.setOnClickListener(v -> request.run());
			if (activity.isGlassTheme()) {
				allow.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassPillDrawable(ctx, 12f, activity.isGlassNightMode()));
				allow.setTextColor(activity.getGlassActionColor());
			}
			row.addView(allow);
		}
		destination.addView(row);
	}

	private static int dp(android.content.Context ctx, int v) {
		return Math.round(v * ctx.getResources().getDisplayMetrics().density);
	}

	private static boolean isGranted(android.content.Context ctx, String permission) {
		return androidx.core.content.ContextCompat.checkSelfPermission(ctx, permission)
				== android.content.pm.PackageManager.PERMISSION_GRANTED;
	}

	private static boolean isStorageGranted(android.content.Context ctx) {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
			return android.os.Environment.isExternalStorageManager();
		}
		return isGranted(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE);
	}

	private void request(MainActivityDelegate activity, String... perms) {
		activity.getAppActivity().checkPermissions(perms).thenRun(this::updatePermissions);
	}

	private void requestVoiceMicrophone(MainActivityDelegate activity) {
		activity.getAppActivity().checkPermissions(android.Manifest.permission.RECORD_AUDIO).thenRun(() -> {
			if (!isGranted(activity.getContext(), android.Manifest.permission.RECORD_AUDIO)) {
				voiceEnabled.setChecked(false);
			}
			updatePermissions();
		});
	}

	private void requestStorage(MainActivityDelegate activity) {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
			startSafely(activity, new android.content.Intent(
					android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
					android.net.Uri.parse("package:" + activity.getContext().getPackageName())));
		} else {
			request(activity, android.Manifest.permission.READ_EXTERNAL_STORAGE);
		}
	}

	private void requestNotifications(MainActivityDelegate activity) {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
				!isGranted(activity.getContext(), android.Manifest.permission.POST_NOTIFICATIONS)) {
			request(activity, android.Manifest.permission.POST_NOTIFICATIONS);
		} else {
			// Granted at runtime but switched off by the user: only the system page can turn it back on.
			startSafely(activity, new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
					.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, activity.getContext().getPackageName()));
		}
	}

	private static void startSafely(MainActivityDelegate activity, android.content.Intent intent) {
		try {
			activity.getAppActivity().startActivity(intent);
		} catch (RuntimeException ignored) {
		}
	}

	@Override
	public void onDestroyView() {
		if (speech != null) speech.cancel();
		speech = null;
		super.onDestroyView();
	}

	private void setupAppLanguage(MainActivityDelegate activity) {
		List<String> labels = new ArrayList<>();
		for (MainActivityPrefs.Lang lang : MainActivityPrefs.Lang.getValues())
			labels.add(lang.locale.getDisplayName(lang.locale));
		appLanguage.setAdapter(createAdapter(labels));
		appLanguage.setSelection(findLanguage(activity.getPrefs().getLocalePref()));
	}

	private void setupVoiceLanguage(MainActivityDelegate activity) {
		List<String> labels = new ArrayList<>(List.of(
				getString(R.string.initial_setup_voice_same_app),
				getString(R.string.initial_setup_voice_system),
				getString(R.string.initial_setup_voice_english),
				getString(R.string.initial_setup_voice_vietnamese)));
		boolean autoSupported = android.os.Build.VERSION.SDK_INT >=
				android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
		if (autoSupported) labels.add(getString(R.string.initial_setup_voice_auto));
		voiceLanguage.setAdapter(createAdapter(labels));
		String current = activity.getPrefs().getVoiceControlLang(activity);
		Locale app = activity.getPrefs().getLocalePref();
		int selection = current.equals(app.toLanguageTag()) ? VOICE_SAME_AS_APP : VOICE_SYSTEM;
		if (current.startsWith("en")) selection = VOICE_ENGLISH;
		else if (current.startsWith("vi")) selection = VOICE_VIETNAMESE;
		if (autoSupported && activity.getPrefs().getVoiceControlAutoLangPref()) selection = VOICE_AUTO;
		voiceLanguage.setSelection(selection);
	}

	private ArrayAdapter<String> createAdapter(List<String> values) {
		ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
				R.layout.initial_setup_spinner_item, values);
		adapter.setDropDownViewResource(R.layout.initial_setup_spinner_dropdown_item);
		return adapter;
	}

	private int findLanguage(Locale locale) {
		List<MainActivityPrefs.Lang> values = MainActivityPrefs.Lang.getValues();
		for (int i = 0; i < values.size(); i++) {
			if (values.get(i).locale.getLanguage().equals(locale.getLanguage())) return i;
		}
		return MainActivityPrefs.Lang.EN.ordinal();
	}

	private void updateVoiceControls(boolean enabled) {
		boolean hasMicrophone = isGranted(requireContext(), android.Manifest.permission.RECORD_AUDIO);
		voiceDetails.setVisibility(enabled && hasMicrophone ? VISIBLE : GONE);
		voiceLanguage.setEnabled(enabled && hasMicrophone);
		microphoneTest.setEnabled(enabled && hasMicrophone);
		if (!enabled || !hasMicrophone) microphoneStatus.setText(R.string.initial_setup_test_disabled);
		else if (microphoneStatus.getText().equals(getString(R.string.initial_setup_test_disabled)))
			microphoneStatus.setText("");
	}

	private void testMicrophone() {
		MainActivityDelegate activity = getActivityDelegate();
		if (!voiceEnabled.isChecked()) return;
		microphoneStatus.setText(R.string.initial_setup_test_running);
		if (speech != null) speech.cancel();
		speech = activity.startSpeechRecognizer(selectedVoiceLocale(activity), false)
				.onSuccess(result -> activity.post(() -> {
					if (!isAdded()) return;
					String text = result.isEmpty() ? "" : result.get(0);
					microphoneStatus.setText(getString(R.string.initial_setup_test_success, text));
				}))
				.onFailure(err -> activity.post(() -> {
					if (isAdded()) microphoneStatus.setText(R.string.initial_setup_test_failed);
				}));
	}

	private String selectedVoiceLocale(MainActivityDelegate activity) {
		return switch (voiceLanguage.getSelectedItemPosition()) {
			case VOICE_ENGLISH -> Locale.ENGLISH.toLanguageTag();
			case VOICE_VIETNAMESE -> "vi-VN";
			case VOICE_SYSTEM -> systemLocale().toLanguageTag();
			default -> selectedAppLocale().toLanguageTag();
		};
	}

	@SuppressWarnings("deprecation")
	private static Locale systemLocale() {
		android.content.res.Configuration config =
				android.content.res.Resources.getSystem().getConfiguration();
		return config.getLocales().get(0);
	}

	private Locale selectedAppLocale() {
		return MainActivityPrefs.Lang.getValues().get(appLanguage.getSelectedItemPosition()).locale;
	}

	private void applyAndContinue() {
		MainActivityDelegate activity = getActivityDelegate();
		MainActivityPrefs prefs = activity.getPrefs();
		int nav = (navPosition.getCheckedRadioButtonId() == R.id.initial_setup_nav_right)
				? NavBarView.POSITION_RIGHT : NavBarView.POSITION_LEFT;
		MainActivityPrefs.Lang selectedLang = MainActivityPrefs.Lang.getValues().get(appLanguage.getSelectedItemPosition());
		int locale = selectedLang.ordinal();
		boolean voice = voiceEnabled.isChecked();
		String voiceLocale = selectedVoiceLocale(activity);
		boolean autoVoice = voiceLanguage.getSelectedItemPosition() == VOICE_AUTO;
		boolean navChanged = prefs.getNavBarPosPref(activity) != nav;
		boolean localeChanged = !prefs.getLocalePref().equals(selectedLang.locale);
		boolean presentationChanged = navChanged || localeChanged;

		try (var edit = prefs.editPreferenceStore()) {
			// MainActivityDelegate owns the single recreation caused by these presentation prefs.
			// Do not write them when unchanged: remove-default edits still broadcast a change.
			if (navChanged) {
				edit.setIntPref(NAV_BAR_POS, nav);
				if (BuildConfig.AUTO) edit.setIntPref(NAV_BAR_POS_AA, nav);
			}
			if (localeChanged) edit.setIntPref(LOCALE, locale);
			edit.setBooleanPref(VOICE_CONTROl_ENABLED, voice);
			edit.setStringPref(VOICE_CONTROL_LANG, voiceLocale);
			edit.setBooleanPref(VOICE_CONTROL_AUTO_LANG, autoVoice);
			edit.setBooleanPref(INITIAL_SETUP_COMPLETED, true);
			edit.setIntPref(INITIAL_SETUP_VERSION, INITIAL_SETUP_CURRENT_VERSION);
		}

		// A changed nav position/locale already schedules exactly one recreation via the
		// activity preference listener. Otherwise stay in the task and enter Dashboard now.
		if (!presentationChanged) activity.showDashboard();
	}
}
