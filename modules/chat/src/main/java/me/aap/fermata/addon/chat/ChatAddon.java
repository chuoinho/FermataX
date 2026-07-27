package me.aap.fermata.addon.chat;

import static me.aap.fermata.util.Utils.openUrl;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

import java.util.Locale;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataFragmentAddon;
import me.aap.fermata.addon.VoiceSearchAddon;
import me.aap.utils.app.App;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class ChatAddon implements FermataFragmentAddon, VoiceSearchAddon {
	private static final AddonInfo info = FermataAddon.findAddonInfo(ChatAddon.class.getName());
	private static final Pref<Supplier<String>> OPENAI_KEY = Pref.s("OPENAI_KEY", "");
	private static final int DEFAULT_MODEL = 0;
	private static final String[] MODELS =
			new String[]{"gpt-5.4-mini", "gpt-5.5", "gpt-5.4", "gpt-5.4-nano", "gpt-5.3-chat-latest"};
	private static final int CUSTOM_MODEL = MODELS.length;
	private static final int CURRENT_MODEL_LIST_VERSION = 2;
	private static final Pref<IntSupplier> MODEL = Pref.i("MODEL", DEFAULT_MODEL);
	private static final Pref<IntSupplier> MODEL_LIST_VERSION = Pref.i("MODEL_LIST_VERSION", 0);
	private static final Pref<Supplier<String>> MODEL_OTHER = Pref.s("MODEL_OTHER", "");
	private static final Pref<Supplier<String>> CHAT_LANG =
			Pref.s("CHAT_LANG", () -> Locale.getDefault().toLanguageTag());

	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.chat_addon;
	}

	@NonNull
	@Override
	public String getVoiceTarget() {
		return "chatgpt";
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new ChatFragment();
	}

	public String getOpenaiKey() {
		return FermataApplication.get().getPreferenceStore().getStringPref(OPENAI_KEY);
	}

	public String getModel() {
		var ps = FermataApplication.get().getPreferenceStore();
		migrateModelPreference(ps);
		return resolveModel(ps.getIntPref(MODEL), ps.getStringPref(MODEL_OTHER));
	}

	public String getGetChatLang() {
		return FermataApplication.get().getPreferenceStore().getStringPref(CHAT_LANG);
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore store, PreferenceSet set,
																 ChangeableCondition visibility) {
		migrateModelPreference(store);
		set.addStringPref(o -> {
			String keyUrl = "https://platform.openai.com/api-keys";
			String sub = App.get().getString(R.string.openai_key_sub, keyUrl);
			o.store = store;
			o.pref = OPENAI_KEY;
			o.title = R.string.openai_key;
			o.csubtitle = HtmlCompat.fromHtml(sub, HtmlCompat.FROM_HTML_MODE_COMPACT);
			o.clickListener = v -> openUrl(v.getContext(), keyUrl);
		});
		set.addListPref(o -> {
			o.store = store;
			o.pref = MODEL;
			o.title = R.string.openai_model;
			o.stringValues = createModelOptions(ctx);
			o.subtitle = me.aap.fermata.R.string.string_format;
			o.formatSubtitle = true;
		});
		set.addStringPref(o -> {
			o.store = store;
			o.pref = MODEL_OTHER;
			o.title = R.string.openai_model_other;
			o.stringHint = "gpt-5.5";
		});
		set.addTtsLocalePref(o -> {
			o.store = store;
			o.pref = CHAT_LANG;
			o.title = me.aap.fermata.R.string.lang;
			o.subtitle = me.aap.fermata.R.string.string_format;
			o.formatSubtitle = true;
		});
	}

	private void migrateModelPreference(PreferenceStore store) {
		int version = store.getIntPref(MODEL_LIST_VERSION);
		if (version >= CURRENT_MODEL_LIST_VERSION) return;
		if ((version < 1) && store.hasPref(MODEL, false)) {
			store.applyIntPref(MODEL, DEFAULT_MODEL);
		}
		if ((version < 2) && !store.getStringPref(MODEL_OTHER).trim().isEmpty()) {
			store.applyIntPref(MODEL, CUSTOM_MODEL);
		}
		store.applyIntPref(false, MODEL_LIST_VERSION, CURRENT_MODEL_LIST_VERSION);
	}

	static String resolveModel(int model, String customModel) {
		String custom = (customModel == null) ? "" : customModel.trim();
		if ((model == CUSTOM_MODEL) && !custom.isEmpty()) return custom;
		return (model >= 0) && (model < MODELS.length) ? MODELS[model] :
				MODELS[DEFAULT_MODEL];
	}

	private static String[] createModelOptions(Context ctx) {
		String[] options = MODELS.clone();
		String[] result = new String[options.length + 1];
		System.arraycopy(options, 0, result, 0, options.length);
		result[CUSTOM_MODEL] = ctx.getString(R.string.openai_model_custom);
		return result;
	}
}
