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

		setupAppLanguage(activity);
		setupVoiceLanguage(activity);
		navPosition.check(activity.getPrefs().getNavBarPosPref(activity) == NavBarView.POSITION_RIGHT
				? R.id.initial_setup_nav_right : R.id.initial_setup_nav_left);
		voiceEnabled.setChecked(activity.getPrefs().getVoiceControlEnabledPref());
		voiceEnabled.setOnCheckedChangeListener((button, checked) -> updateVoiceControls(checked));
		microphoneTest.setOnClickListener(v -> testMicrophone());
		view.findViewById(R.id.initial_setup_continue).setOnClickListener(v -> applyAndContinue());
		updateVoiceControls(voiceEnabled.isChecked());
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
				android.R.layout.simple_spinner_item, values);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
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
		voiceLanguage.setEnabled(enabled);
		microphoneTest.setEnabled(enabled);
		if (!enabled) microphoneStatus.setText(R.string.initial_setup_test_disabled);
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
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
			return config.getLocales().get(0);
		return config.locale;
	}

	private Locale selectedAppLocale() {
		return MainActivityPrefs.Lang.get(appLanguage.getSelectedItemPosition()).locale;
	}

	private void applyAndContinue() {
		MainActivityDelegate activity = getActivityDelegate();
		MainActivityPrefs prefs = activity.getPrefs();
		int nav = (navPosition.getCheckedRadioButtonId() == R.id.initial_setup_nav_right)
				? NavBarView.POSITION_RIGHT : NavBarView.POSITION_LEFT;
		int locale = appLanguage.getSelectedItemPosition();
		boolean voice = voiceEnabled.isChecked();
		String voiceLocale = selectedVoiceLocale(activity);
		boolean autoVoice = voiceLanguage.getSelectedItemPosition() == VOICE_AUTO;
		boolean recreate = !prefs.getLocalePref().equals(MainActivityPrefs.Lang.get(locale).locale) ||
					(prefs.getNavBarPosPref(activity) != nav);

		try (var edit = prefs.editPreferenceStore()) {
			edit.setIntPref(NAV_BAR_POS, nav);
			if (BuildConfig.AUTO) edit.setIntPref(NAV_BAR_POS_AA, nav);
			edit.setIntPref(LOCALE, locale);
			edit.setBooleanPref(VOICE_CONTROl_ENABLED, voice);
			edit.setStringPref(VOICE_CONTROL_LANG, voiceLocale);
			edit.setBooleanPref(VOICE_CONTROL_AUTO_LANG, autoVoice);
			edit.setBooleanPref(INITIAL_SETUP_COMPLETED, true);
			edit.setIntPref(INITIAL_SETUP_VERSION, INITIAL_SETUP_CURRENT_VERSION);
		}

		if (recreate) activity.recreate();
		else activity.showDashboard();
	}
}
