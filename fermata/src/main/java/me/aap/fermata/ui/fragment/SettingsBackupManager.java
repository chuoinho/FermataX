package me.aap.fermata.ui.fragment;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static java.nio.charset.StandardCharsets.UTF_8;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.util.Utils;
import me.aap.utils.app.App;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PrefUtils;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.vfs.local.LocalFileSystem;

final class SettingsBackupManager {
	private SettingsBackupManager() {
	}

	static void exportPrefs(MainActivityDelegate activity) {
		SimpleDateFormat fmt = new SimpleDateFormat("ddMMyy", Locale.getDefault());
		String pattern = "Fermata_prefs_" + fmt.format(new Date());

		if (Utils.isSafSupported(activity)) {
			activity.startActivityForResult(() -> new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
					.onCompletion((data, error) -> {
						Context context = activity.getContext();

						if (error != null) {
							UiUtils.showAlert(context,
									context.getString(R.string.export_prefs_failed, error));
							return;
						}

						if (data == null) return;
						Uri uri = data.getData();
						if (uri == null) return;

						try {
							DocumentFile dir = DocumentFile.fromTreeUri(context, uri);
							if (dir == null) return;

							File prefsDir = PrefUtils.getSharedPrefsFile(context, "fermata").getParentFile();
							File[] files = (prefsDir == null) ? null :
									prefsDir.listFiles(file -> !file.getName().equals("image-cache.xml"));

							if ((files == null) || (files.length == 0)) {
								UiUtils.showAlert(context, R.string.prefs_not_found);
								return;
							}

							String name = pattern + ".zip";
							for (int i = 1; dir.findFile(name) != null; i++) name = pattern + '_' + i + ".zip";
							DocumentFile file = dir.createFile("application/zip", name);

							if (file == null) {
								UiUtils.showAlert(context,
										context.getString(R.string.export_prefs_failed, "Failed to create file"));
							} else {
								try (OutputStream output =
											 context.getContentResolver().openOutputStream(file.getUri())) {
									exportPrefs(activity, output, name);
								}
							}
						} catch (Exception ex) {
							Log.e(ex, "Failed to export preferences");
							UiUtils.showAlert(context, context.getString(R.string.export_prefs_failed, ex));
						}
					});
		} else {
			if (!(activity.showFragment(me.aap.utils.R.id.file_picker) instanceof FilePickerFragment pick))
				return;
			pick.setMode((byte) (FilePickerFragment.FOLDER | FilePickerFragment.WRITABLE));
			pick.setFileSystem(LocalFileSystem.getInstance());
			pick.setFileConsumer(result -> {
				activity.showFragment(R.id.settings_fragment);
				if (result == null) return;
				File dir = result.getLocalFile();
				if (dir == null) return;
				try {
					String name = pattern + ".zip";
					File file = new File(dir, name);
					for (int i = 1; file.exists(); i++) {
						name = pattern + '_' + i + ".zip";
						file = new File(dir, name);
					}
					try (OutputStream output = new FileOutputStream(file)) {
						exportPrefs(activity, output, name);
					}
				} catch (Exception ex) {
					Context context = activity.getContext();
					Log.e(ex, "Failed to export preferences");
					UiUtils.showAlert(context, context.getString(R.string.export_prefs_failed, ex));
				}
			});
		}
	}

	private static void exportPrefs(MainActivityDelegate activity, OutputStream output,
										String fileName) throws IOException {
		Context context = activity.getContext();
		File prefsDir = PrefUtils.getSharedPrefsFile(context, "fermata").getParentFile();
		File[] files = (prefsDir == null) ? null :
				prefsDir.listFiles(file -> !file.getName().equals("image-cache" + ".xml"));

		if ((files == null) || (files.length == 0)) {
			UiUtils.showAlert(context, R.string.prefs_not_found);
			return;
		}

		List<String> names = new ArrayList<>(files.length);
		for (File prefsFile : files) {
			String name = prefsFile.getName();
			if (name.endsWith(".xml")) names.add(name.substring(0, name.length() - 4));
		}

		try (ZipOutputStream zip = (SDK_INT >= N) ? new ZipOutputStream(output, UTF_8) :
				new ZipOutputStream(output)) {
			PrefUtils.exportSharedPrefs(context, names, zip);
			UiUtils.showInfo(context, context.getString(R.string.export_prefs_ok, fileName));
		}
	}

	static void importPrefs(MainActivityDelegate activity) {
		if (Utils.isSafSupported(activity)) {
			activity.startActivityForResult(
							() -> new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip"))
					.onCompletion((data, error) -> {
						Context context = activity.getContext();

						if (error != null) {
							UiUtils.showAlert(context,
									context.getString(R.string.import_prefs_failed, error));
							return;
						}

						if (data == null) return;
						Uri uri = data.getData();
						if (uri == null) return;

						try {
							DocumentFile file = DocumentFile.fromSingleUri(context, uri);
							if (file == null) return;

							try (InputStream input =
										 context.getContentResolver().openInputStream(file.getUri())) {
								importPrefs(activity, input);
							}
						} catch (Exception ex) {
							Log.e(ex, "Failed to import preferences");
							UiUtils.showAlert(context, context.getString(R.string.import_prefs_failed, ex));
						}
					});
		} else {
			if (!(activity.showFragment(me.aap.utils.R.id.file_picker) instanceof FilePickerFragment pick))
				return;
			pick.setMode(FilePickerFragment.FILE);
			pick.setPattern(Pattern.compile(".+\\.zip"));
			pick.setFileSystem(LocalFileSystem.getInstance());
			pick.setFileConsumer(result -> {
				activity.showFragment(R.id.settings_fragment);
				if (result == null) return;
				File file = result.getLocalFile();
				if (file == null) return;
				try (InputStream input = new FileInputStream(file)) {
					importPrefs(activity, input);
				} catch (Exception ex) {
					Context context = activity.getContext();
					Log.e(ex, "Failed to import preferences");
					UiUtils.showAlert(context, context.getString(R.string.import_prefs_failed, ex));
				}
			});
		}
	}

	private static void importPrefs(MainActivityDelegate activity, InputStream input)
			throws IOException {
		Context context = activity.getContext();

		try (ZipInputStream zip = (SDK_INT >= N) ? new ZipInputStream(input, UTF_8) :
				new ZipInputStream(input)) {
			PrefUtils.importSharedPrefs(context, zip);
		}

		UiUtils.showInfo(context, context.getString(R.string.import_prefs_ok)).thenRun(() -> {
			App.get().getHandler().postDelayed(() -> System.exit(0), 1000);
			if (context instanceof Activity) ((Activity) context).finishAffinity();
			else System.exit(0);
		});
	}

	static void openLog(MainActivityDelegate activity) {
		try {
			Intent intent = new Intent(Intent.ACTION_SEND);
			FermataApplication app = FermataApplication.get();
			File file = app.getLogFile();
			Uri uri = (SDK_INT >= Build.VERSION_CODES.N) ?
					FileProvider.getUriForFile(app, app.getPackageName() + ".FileProvider", file) :
					Uri.fromFile(file);
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_STREAM, uri);
			activity.startActivity(Intent.createChooser(intent, app.getString(R.string.open_log)));
		} catch (Exception error) {
			Log.e(error);
			showAlert(activity.getContext(), error.getLocalizedMessage());
		}
	}
}
