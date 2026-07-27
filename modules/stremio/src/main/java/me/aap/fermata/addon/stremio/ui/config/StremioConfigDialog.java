package me.aap.fermata.addon.stremio.ui.config;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.view.LayoutInflater;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.addon.stremio.R;
import me.aap.fermata.addon.stremio.ui.source.SourceUiGateway;
import me.aap.fermata.addon.stremio.ui.source.SourceUiOperation;
import me.aap.fermata.addon.stremio.ui.source.SourceUiResult;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.GenericDialogFragment;

/** Hosts one Stremio-only provider configuration session in Fermata's AA-safe dialog shell. */
public final class StremioConfigDialog {
	private static final long SAVE_TIMEOUT_MILLIS = 30_000L;
	private StremioConfigDialog() {
	}

	public static void show(MainActivityDelegate activity, String providerName, String sourceUuid,
			SourceUiGateway gateway, StremioConfigLaunch launch, Runnable finished) {
		Objects.requireNonNull(activity, "activity");
		Objects.requireNonNull(providerName, "providerName");
		Objects.requireNonNull(sourceUuid, "sourceUuid");
		Objects.requireNonNull(gateway, "gateway");
		Objects.requireNonNull(launch, "launch");
		Objects.requireNonNull(finished, "finished");
		activity.showFragment(me.aap.fermata.R.id.stremio_fragment);
		activity.post(() -> open(activity, providerName, sourceUuid, gateway, launch, finished));
	}

	private static void open(MainActivityDelegate activity, String providerName, String sourceUuid,
			SourceUiGateway gateway, StremioConfigLaunch launch, Runnable finished) {
		if (!StremioConfigWebIsolation.productionSupported()) {
			UiUtils.showAlert(activity.getContext(), R.string.stremio_config_webview_unsupported);
			return;
		}
		if (!(activity.showFragment(me.aap.utils.R.id.generic_dialog_fragment)
				instanceof GenericDialogFragment dialog)) return;
		View content = LayoutInflater.from(activity.getContext())
				.inflate(R.layout.stremio_config_web, null, false);
		StremioConfigWebView web = content.findViewById(R.id.stremio_config_web);
		View progress = content.findViewById(R.id.stremio_config_progress);
		AtomicReference<StremioConfigWebController> controller = new AtomicReference<>();
		AtomicReference<SourceUiOperation> operation = new AtomicReference<>();
		AtomicReference<Runnable> saveDeadline = new AtomicReference<>();
		AtomicBoolean terminal = new AtomicBoolean();

		Runnable close = () -> {
			Runnable deadline = saveDeadline.getAndSet(null);
			if (deadline != null) content.removeCallbacks(deadline);
			StremioConfigWebController current = controller.getAndSet(null);
			if (current != null) current.close();
			SourceUiOperation pending = operation.getAndSet(null);
			if (pending != null) pending.cancel();
		};
		Runnable complete = () -> {
			if (!terminal.compareAndSet(false, true)) return;
			close.run();
			activity.showFragment(me.aap.fermata.R.id.stremio_fragment);
			finished.run();
		};

		dialog.setTitle(activity.getString(R.string.stremio_config_title, providerName));
		dialog.setContentProvider(group -> {
			content.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT));
			group.addView(content);
			StremioConfigWebController session;
			try {
				session = launch.open(web, new StremioConfigCallback() {
				@Override
				public void onConfigured(StremioConfigResult result) {
					final SourceUiOperation update;
					try {
						update = Objects.requireNonNull(gateway.configure(sourceUuid, result),
								"configuration update");
					} catch (RuntimeException error) {
						progress.setVisibility(GONE);
						UiUtils.showAlert(activity.getContext(),
								R.string.stremio_config_save_failed);
						return;
					}
					SourceUiOperation previous = operation.getAndSet(update);
					if (previous != null) previous.cancel();
					progress.setVisibility(VISIBLE);
					Runnable deadline = () -> {
						if (!operation.compareAndSet(update, null)) return;
						saveDeadline.set(null);
						update.cancel();
						progress.setVisibility(GONE);
						UiUtils.showAlert(activity.getContext(),
								R.string.stremio_config_save_failed);
					};
					Runnable oldDeadline = saveDeadline.getAndSet(deadline);
					if (oldDeadline != null) content.removeCallbacks(oldDeadline);
					content.postDelayed(deadline, SAVE_TIMEOUT_MILLIS);
					update.completion().whenComplete((value, error) -> activity.post(() -> {
						if (operation.get() != update) return;
						operation.set(null);
						Runnable currentDeadline = saveDeadline.getAndSet(null);
						if (currentDeadline != null) content.removeCallbacks(currentDeadline);
						if ((error != null) || (value == null) ||
								(value.status() == SourceUiResult.Status.FAILED)) {
							progress.setVisibility(GONE);
							UiUtils.showAlert(activity.getContext(),
									R.string.stremio_config_save_failed);
							return;
						}
						complete.run();
					}));
				}

				@Override
				public void onLoadingChanged(boolean loading) {
					progress.setVisibility(loading ? VISIBLE : GONE);
				}

				@Override
				public void onFailure(Failure failure) {
					progress.setVisibility(GONE);
					UiUtils.showAlert(activity.getContext(), failure.messageResource());
				}
				});
			} catch (RuntimeException unsupported) {
				progress.setVisibility(GONE);
				UiUtils.showAlert(activity.getContext(), R.string.stremio_config_load_failed);
				return;
			}
			controller.set(session);
			session.start();
		});
		dialog.setDialogValidator(() -> false);
		dialog.setBackHandler(() -> {
			if (web.canGoBack()) {
				web.goBack();
				return true;
			}
			return false;
		});
		dialog.setDialogConsumer(ok -> complete.run());
	}
}
