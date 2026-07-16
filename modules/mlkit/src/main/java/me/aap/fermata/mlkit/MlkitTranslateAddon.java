package me.aap.fermata.mlkit;

import android.util.Pair;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.common.MlKit;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.List;
import java.util.Locale;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.TranslateAddon;
import me.aap.fermata.addon.TranslateAddon.Translator;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.collection.CollectionUtils;

@Keep
@SuppressWarnings("unused")
public class MlkitTranslateAddon extends TranslateAddon {
	private static final AddonInfo info =
			FermataAddon.findAddonInfo(MlkitTranslateAddon.class.getName());
	private static volatile boolean mlkitInitialized;

	@Override
	public FutureSupplier<Translator> getTranslator(String srcLang, String targetLang) {
		Promise<Translator> promise = new Promise<>();
		com.google.mlkit.nl.translate.Translator translator;

		try {
			initMlkit();
			translator = Translation.getClient(new TranslatorOptions.Builder()
					.setSourceLanguage(srcLang).setTargetLanguage(targetLang)
					.setExecutor(App.get().getExecutor()).build());
		} catch (Throwable error) {
			promise.completeExceptionally(error);
			return promise;
		}

		translator.downloadModelIfNeeded()
				.addOnSuccessListener(new OnSuccessListener<Void>() {
					@Override
					public void onSuccess(Void unused) {
						promise.complete(new MlkitTranslator(translator));
					}
				})
				.addOnCanceledListener(new OnCanceledListener() {
					@Override
					public void onCanceled() {
						promise.cancel();
					}
				})
				.addOnFailureListener(new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception error) {
						promise.completeExceptionally(error);
					}
				});
		return promise;
	}

	private static void initMlkit() {
		if (mlkitInitialized) return;
		synchronized (MlkitTranslateAddon.class) {
			if (mlkitInitialized) return;
			try {
				MlKit.initialize(App.get());
			} catch (Throwable ignored) {
			}
			mlkitInitialized = true;
		}
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@Override
	public List<Pair<String, String>> getSupportedLanguages(@Nullable String srcLang) {
		var locale = Locale.getDefault(Locale.Category.DISPLAY);
		var langs = CollectionUtils.map(TranslateLanguage.getAllLanguages(),
				lang -> new Pair<>(lang, new Locale(lang).getDisplayLanguage(locale)));
		langs.sort((a, b) -> a.second.compareToIgnoreCase(b.second));
		return langs;
	}

	private static final class MlkitTranslator implements Translator {
		private final com.google.mlkit.nl.translate.Translator translator;

		private MlkitTranslator(com.google.mlkit.nl.translate.Translator translator) {
			this.translator = translator;
		}

		public FutureSupplier<String> translate(String text) {
			Promise<String> promise = new Promise<>();
			translator.translate(text)
					.addOnSuccessListener(new OnSuccessListener<String>() {
						@Override
						public void onSuccess(String translation) {
							promise.complete(translation);
						}
					})
					.addOnCanceledListener(new OnCanceledListener() {
						@Override
						public void onCanceled() {
							promise.cancel();
						}
					})
					.addOnFailureListener(new OnFailureListener() {
						@Override
						public void onFailure(@NonNull Exception error) {
							promise.completeExceptionally(error);
						}
					});
			return promise;
		}

		public boolean supportsBatch() {
			return true;
		}
	}
}
