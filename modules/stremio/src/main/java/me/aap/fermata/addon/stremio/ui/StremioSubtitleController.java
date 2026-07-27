package me.aap.fermata.addon.stremio.ui;

import android.content.Context;
import android.view.View;

import java.util.List;
import java.util.Locale;

import me.aap.fermata.addon.stremio.R;
import me.aap.fermata.addon.stremio.StremioAddon;
import me.aap.fermata.addon.stremio.item.StremioSubtitleSelectionStore;
import me.aap.fermata.addon.stremio.presentation.StremioPresentationGateway;
import me.aap.fermata.addon.stremio.subtitle.SubtitleDescriptor;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.ContentSubtitleSelectionItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;

/** Builds and applies the existing content-level subtitle picker. */
public final class StremioSubtitleController {
	public void show(Context context, MainActivityDelegate activity,
			StremioPresentationGateway gateway, String stableKey) {
		if (gateway == null) return;
		StremioPresentationGateway.SubtitleTarget target = gateway.subtitleTarget(stableKey);
		if (target == null) {
			UiUtils.showAlert(context, R.string.stremio_subtitles_unavailable);
			return;
		}
		OverlayMenu menu = activity.findViewById(me.aap.fermata.R.id.control_menu);
		if (menu == null) return;
		int automaticId = View.generateViewId();
		int offId = View.generateViewId();
		menu.showFuture(builder -> {
			builder.setTitle(R.string.stremio_presentation_subtitles);
			StremioSubtitleSelectionStore.Selection selected =
					StremioSubtitleSelectionStore.get(target.videoKey());
			builder.setSelectionHandler(item -> {
				if (item.getItemId() == automaticId) {
					StremioSubtitleSelectionStore.useDefault(target.videoKey());
				} else if (item.getItemId() == offId) {
					StremioSubtitleSelectionStore.disable(target.videoKey());
				} else {
					SubtitleDescriptor descriptor = item.getData();
					if (descriptor == null) return true;
					StremioSubtitleSelectionStore.select(target.videoKey(), descriptor);
				}
				applyCurrentSelection(activity, target.videoKey());
				menu.hide();
				return true;
			});
			builder.addItem(automaticId, me.aap.fermata.R.drawable.subtitles,
					context.getString(R.string.stremio_subtitles_automatic,
							configuredLanguageLabel()))
					.setChecked(selected == null, selected == null);
			builder.addItem(offId, me.aap.utils.R.drawable.close,
					R.string.stremio_subtitles_off)
					.setChecked((selected != null) && selected.disabled(),
							(selected != null) && selected.disabled());
			OverlayMenuItem loading = builder.addItem(View.generateViewId(),
					me.aap.fermata.R.string.loading);
			if (loading instanceof View view) {
				view.setEnabled(false);
				view.setFocusable(false);
			}
			return gateway.subtitles(target).main(activity.getHandler()).map(result -> {
				loading.setVisible(false);
				int index = 0;
				for (SubtitleDescriptor descriptor : result.subtitles()) {
					boolean checked = (selected != null) && !selected.disabled() &&
							selected.identity().equals(descriptor.identity());
					builder.addItem(View.generateViewId(), me.aap.fermata.R.drawable.subtitles,
							title(descriptor)).setData(descriptor).setChecked(checked, checked);
					index++;
				}
				if (index == 0) {
					OverlayMenuItem empty = builder.addItem(View.generateViewId(),
							R.string.stremio_subtitles_empty);
					if (empty instanceof View view) {
						view.setEnabled(false);
						view.setFocusable(false);
					}
				}
				return (Void) null;
			}).ifFail(error -> {
				loading.setTitle(R.string.stremio_subtitles_load_failed);
				return (Void) null;
			});
		});
	}

	static String configuredLanguageLabel() {
		List<String> effective = StremioAddon.preferredSubtitleLanguages();
		String tag = effective.isEmpty() ? "en" : effective.get(0);
		Locale locale = Locale.forLanguageTag(tag);
		String label = locale.getDisplayLanguage(Locale.forLanguageTag(tag));
		return label.isBlank() ? tag : label;
	}

	static String title(SubtitleDescriptor descriptor) {
		Locale language = Locale.forLanguageTag(descriptor.language().tag());
		String label = language.getDisplayLanguage(language);
		if (label.isBlank()) label = descriptor.languageLabel();
		if (descriptor.providerLabel().isBlank()) return label;
		return label + " | " + descriptor.providerLabel();
	}

	private static void applyCurrentSelection(MainActivityDelegate activity, String videoKey) {
		MediaEngine engine = activity.getMediaSessionCallback().getEngine();
		if (engine == null) return;
		PlayableItem source = engine.getSource();
		if (!(source instanceof ContentSubtitleSelectionItem selection) ||
				!videoKey.equals(selection.getSubtitleSelectionKey())) return;
		engine.selectSubtitleStream().main(activity.getHandler()).onFailure(error ->
				UiUtils.showAlert(activity.getContext(), R.string.stremio_subtitles_load_failed));
	}
}
