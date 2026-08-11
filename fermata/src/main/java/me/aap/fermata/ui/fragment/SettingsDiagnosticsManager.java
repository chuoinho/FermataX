package me.aap.fermata.ui.fragment;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Collections;
import java.util.function.BooleanSupplier;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.action.HardwareInputTestSession;
import me.aap.fermata.diagnostics.DiagnosticEvent;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.DiagnosticScope;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.fermata.diagnostics.export.DiagnosticReportExporter;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.app.App;
import me.aap.utils.ui.UiUtils;

final class SettingsDiagnosticsManager {
	private SettingsDiagnosticsManager() {
	}

	static void exportReport(MainActivityDelegate activity, BooleanSupplier uiActive) {
		if (activity.isCarActivityNotMirror()) {
			UiUtils.showInfo(activity.getContext(), R.string.diagnostics_phone_only);
			return;
		}
		FermataApplication app = FermataApplication.get();
		AndroidDiagnosticsRuntime runtime = app.getDiagnostics();
		App.get().execute(() -> DiagnosticReportExporter.create(app, runtime.getRecorder()))
				.main(activity.getHandler()).onCompletion((report, error) -> {
					if (error != null) {
						recordFailure(runtime, "report_create_failed", error);
						if (canUseUi(activity, uiActive)) showAlert(activity.getContext(),
								activity.getString(R.string.diagnostic_report_failed,
										error.getClass().getSimpleName()));
						return;
					}
					if (canUseUi(activity, uiActive)) showExportActions(activity, report, uiActive);
				});
	}

	static void clear(MainActivityDelegate activity, BooleanSupplier uiActive) {
		Context context = activity.getContext();
		new MaterialAlertDialogBuilder(context)
				.setMessage(context.getString(R.string.delete_confirm,
						context.getString(R.string.diagnostics)))
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					AndroidDiagnosticsRuntime runtime = FermataApplication.get().getDiagnostics();
					App.get().execute(() -> {
						boolean cleared = runtime.clear(3000L);
						DiagnosticReportExporter.clearCachedReports(FermataApplication.get());
						return cleared;
					}).main(activity.getHandler())
							.onCompletion((cleared, error) -> {
								if ((error != null) || !Boolean.TRUE.equals(cleared)) {
									Throwable failure = (error != null) ? error :
											new IllegalStateException("clear_failed");
									recordFailure(runtime, "diagnostic_clear_failed", failure);
									if (canUseUi(activity, uiActive)) showAlert(context,
											context.getString(R.string.diagnostic_report_failed,
													failure.getClass().getSimpleName()));
								} else {
									if (canUseUi(activity, uiActive)) UiUtils.showInfo(context,
											R.string.diagnostic_data_cleared);
								}
							});
				})
				.show();
	}

	static void testVehicleKeys(MainActivityDelegate activity, BooleanSupplier uiActive) {
		if (!canUseUi(activity, uiActive)) return;
		AndroidDiagnosticsRuntime runtime = FermataApplication.get().getDiagnostics();
		HardwareInputTestSession.Result result = HardwareInputTestSession.toggle();
		if (result.isStarted()) {
			runtime.setDetailedEnabled(true);
			runtime.record(DiagnosticEvent.builder("hardware_input", "input_test_started")
					.scope(DiagnosticScope.DETAILED)
					.priority(DiagnosticPriority.STATE)
					.build());
			UiUtils.showInfo(activity.getContext(), R.string.vehicle_key_test_started);
			return;
		}
		runtime.record(DiagnosticEvent.builder("hardware_input", "input_test_completed")
				.scope(DiagnosticScope.DETAILED)
				.priority(DiagnosticPriority.STATE)
				.build());
		String summary = result.getSummary();
		UiUtils.showInfo(activity.getContext(), summary.isEmpty() ?
				activity.getString(R.string.vehicle_key_test_no_events) :
				activity.getString(R.string.vehicle_key_test_result, summary));
	}

	private static void showExportActions(MainActivityDelegate activity, File report,
			BooleanSupplier uiActive) {
		Context context = activity.getContext();
		CharSequence[] actions = {
				context.getText(R.string.share_diagnostic_report),
				context.getText(R.string.save_diagnostic_report)
		};
		new MaterialAlertDialogBuilder(context)
				.setTitle(R.string.export_diagnostic_report)
				.setItems(actions, (dialog, which) -> {
					if (!canUseUi(activity, uiActive)) return;
					if (which == 0) share(activity, report, uiActive);
					else save(activity, report, uiActive);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private static void share(MainActivityDelegate activity, File report,
			BooleanSupplier uiActive) {
		if (!canUseUi(activity, uiActive)) return;
		try {
			Context context = activity.getContext();
			Uri uri = FileProvider.getUriForFile(context,
					context.getPackageName() + ".DiagnosticsFileProvider", report);
			Intent send = new Intent(Intent.ACTION_SEND)
					.setType("application/zip")
					.putExtra(Intent.EXTRA_STREAM, uri)
					.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
			send.setClipData(ClipData.newRawUri("FermataX diagnostics", uri));
			activity.startActivity(Intent.createChooser(send,
					context.getText(R.string.share_diagnostic_report)));
		} catch (RuntimeException error) {
			recordFailure(FermataApplication.get().getDiagnostics(), "report_share_failed", error);
			if (canUseUi(activity, uiActive)) showAlert(activity.getContext(), activity.getString(
					R.string.diagnostic_report_failed, error.getClass().getSimpleName()));
		}
	}

	private static void save(MainActivityDelegate activity, File report,
			BooleanSupplier uiActive) {
		if (!canUseUi(activity, uiActive)) return;
		if (DiagnosticReportExporter.supportsDirectDownloads()) {
			App.get().execute(() -> DiagnosticReportExporter.saveToDownloads(
					FermataApplication.get(), report)).main(activity.getHandler())
					.onCompletion((name, error) -> onSaved(activity, uiActive, name, error));
			return;
		}

		Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
				.setType("application/zip")
				.putExtra(Intent.EXTRA_TITLE, report.getName());
		activity.startActivityForResult(() -> create).onCompletion((data, error) -> {
			if (!canUseUi(activity, uiActive)) return;
			if (error != null) {
				onSaved(activity, uiActive, null, error);
				return;
			}
			Uri destination = (data == null) ? null : data.getData();
			if (destination == null) return;
			App.get().execute(() -> {
				DiagnosticReportExporter.copyToUri(FermataApplication.get(), report, destination);
				return report.getName();
			}).main(activity.getHandler()).onCompletion((name, failure) ->
					onSaved(activity, uiActive, name, failure));
		});
	}

	private static void onSaved(MainActivityDelegate activity, BooleanSupplier uiActive,
			String name, Throwable error) {
		if (error != null) {
			recordFailure(FermataApplication.get().getDiagnostics(), "report_save_failed", error);
			if (canUseUi(activity, uiActive)) showAlert(activity.getContext(), activity.getString(
					R.string.diagnostic_report_failed, error.getClass().getSimpleName()));
		} else {
			if (canUseUi(activity, uiActive)) UiUtils.showInfo(activity.getContext(),
					activity.getString(R.string.diagnostic_report_saved, name));
		}
	}

	private static boolean canUseUi(MainActivityDelegate activity, BooleanSupplier uiActive) {
		return isUiCallbackAllowed(uiActive, activity.getAppActivity().isDestroyed(),
				activity.getAppActivity().isFinishing());
	}

	static boolean isUiCallbackAllowed(BooleanSupplier uiActive, boolean destroyed,
			boolean finishing) {
		if (destroyed || finishing || (uiActive == null)) return false;
		try {
			return uiActive.getAsBoolean();
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static void recordFailure(AndroidDiagnosticsRuntime runtime, String name,
			Throwable error) {
		runtime.record(DiagnosticEvent.builder("diagnostics", name)
				.scope(DiagnosticScope.ESSENTIAL)
				.priority(DiagnosticPriority.ERROR)
				.attributes(Collections.emptyMap())
				.error(error)
				.build());
	}
}
