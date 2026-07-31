package me.aap.fermata.addon.stremio.ui;

import static android.view.View.GONE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import java.util.function.BiConsumer;

import me.aap.fermata.addon.stremio.R;
import me.aap.fermata.addon.stremio.runtime.StremioRuntimeGraph;
import me.aap.fermata.addon.stremio.ui.source.SourceUiConsent;
import me.aap.fermata.addon.stremio.ui.source.SourceUiController.EditorRequest;
import me.aap.fermata.addon.stremio.ui.source.SourceUiDraft;
import me.aap.fermata.addon.stremio.ui.source.SourceUiResult;
import me.aap.fermata.addon.stremio.ui.source.StremioSourceEditorDialog;
import me.aap.fermata.addon.stremio.ui.source.StremioSourceUiView;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.GenericDialogFragment;

/** Coordinates the existing source list/editor surfaces without owning their lifecycle. */
public final class StremioSourceController {
	public void show(StremioRuntimeGraph graph, MainActivityDelegate activity, int fragmentId,
			String title, Runnable refresh, BiConsumer<SourceUiResult, Throwable> completion) {
		if (graph == null) return;
		if (!(activity.showFragment(me.aap.utils.R.id.generic_dialog_fragment)
				instanceof GenericDialogFragment dialog)) return;
		StremioSourceUiView panel = new StremioSourceUiView(activity.getContext());
		boolean automotive = activity.getRuntimeHostMode().usesAutomotivePresentation();
		dialog.setTitle(title);
		dialog.setCloseButtonVisible(!automotive);
		dialog.setContentProvider(group -> {
			panel.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT));
			group.addView(panel);
			panel.bind(graph.sources(), activity, completion);
		});
		panel.setAutomotiveChrome(automotive);
		dialog.setDialogValidator(() -> false);
		dialog.setBackHandler(() -> false);
		dialog.setDialogConsumer(ok -> {
			panel.unbind();
			activity.showFragment(fragmentId);
			refresh.run();
		});
		if (automotive) {
			View close = activity.getToolBar().findViewById(me.aap.utils.R.id.file_picker_close);
			if (close != null) close.setVisibility(GONE);
		}
	}

	public void add(StremioRuntimeGraph graph, MainActivityDelegate activity,
			BiConsumer<SourceUiResult, Throwable> completion) {
		if (graph == null) return;
		EditorRequest request = new EditorRequest(null,
				new SourceUiDraft("", "", SourceUiConsent.STRICT));
		StremioSourceEditorDialog.show(activity, request, draft -> {
			var operation = graph.sources().add(draft);
			operation.completion().whenComplete((result, error) ->
					activity.post(() -> completion.accept(result, error)));
		});
	}

	public void finish(MainActivityDelegate activity, int fragmentId, Runnable refresh,
			SourceUiResult result, Throwable error) {
		activity.showFragment(fragmentId);
		refresh.run();
		if (error != null) {
			UiUtils.showAlert(activity.getContext(), R.string.stremio_source_error_unknown);
		} else if ((result != null) && (result.status() == SourceUiResult.Status.FAILED)) {
			UiUtils.showAlert(activity.getContext(), StremioSourceUiView.errorText(
					activity.getContext(), result.error()).toString());
		}
	}
}
