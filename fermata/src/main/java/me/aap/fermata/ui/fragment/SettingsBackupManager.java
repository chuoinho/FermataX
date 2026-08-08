package me.aap.fermata.ui.fragment;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.backup.AndroidBackupStateStore;
import me.aap.fermata.backup.BackupCoordinator;
import me.aap.fermata.backup.BackupData;
import me.aap.fermata.backup.BackupException;
import me.aap.fermata.backup.LegacyBackupReader;
import me.aap.fermata.backup.PortableBackupCodec;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.util.Utils;
import me.aap.utils.app.App;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.vfs.local.LocalFileSystem;

/** One portable backup flow for normal configuration and addon-owned secure data. */
final class SettingsBackupManager {
	private static final int MAX_INPUT_BYTES = 32 * 1024 * 1024;
	private static final Pref<Supplier<String>> PASSWORD = Pref.s("backup_password");
	private static final Pref<Supplier<String>> CONFIRM_PASSWORD =
			Pref.s("backup_password_confirm");

	private SettingsBackupManager() {
	}

	static void backupAllData(MainActivityDelegate activity) {
		queryPassword(activity, true).onSuccess(password -> chooseBackupDestination(activity,
				password.toCharArray()));
	}

	static void restoreBackup(MainActivityDelegate activity) {
		chooseBackupFile(activity);
	}

	private static void chooseBackupDestination(MainActivityDelegate activity, char[] password) {
		String pattern = "FermataX_" + new SimpleDateFormat("yyyyMMdd_HHmm",
				Locale.US).format(new Date());
		Context context = activity.getContext();
		if (Utils.isSafSupported(activity)) {
			activity.startActivityForResult(() -> new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
					.onCompletion((data, error) -> {
						if (error != null || data == null || data.getData() == null) {
							clear(password);
							if (error != null) showFailure(context, error, true);
							return;
						}
						try {
							DocumentFile directory = DocumentFile.fromTreeUri(context, data.getData());
							if (directory == null) throw new IOException("Backup directory unavailable");
							String name = availableName(directory, pattern);
							DocumentFile file = directory.createFile("application/octet-stream", name);
							if (file == null) throw new IOException("Backup file unavailable");
							createBackup(activity, password, file.getUri(), name);
						} catch (Throwable failure) {
							clear(password);
							showFailure(context, failure, true);
						}
					});
			return;
		}

		if (!(activity.showFragment(me.aap.utils.R.id.file_picker) instanceof FilePickerFragment pick)) {
			clear(password);
			return;
		}
		pick.setMode((byte) (FilePickerFragment.FOLDER | FilePickerFragment.WRITABLE));
		pick.setFileSystem(LocalFileSystem.getInstance());
		pick.setFileConsumer(result -> {
			activity.showFragment(R.id.settings_fragment);
			if (result == null || result.getLocalFile() == null) {
				clear(password);
				return;
			}
			File directory = result.getLocalFile();
			String name = availableName(directory, pattern);
			createBackup(activity, password, new File(directory, name), name);
		});
	}

	private static void createBackup(MainActivityDelegate activity, char[] password, Uri uri,
			String fileName) {
		Context context = activity.getContext();
		App.get().execute(() -> {
			byte[] encoded = createBackupBytes(context, password);
			try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
				if (output == null) throw new IOException("Backup output unavailable");
				output.write(encoded);
			} finally {
				Arrays.fill(encoded, (byte) 0);
			}
			return fileName;
		}).main().onCompletion((name, error) -> {
			clear(password);
			if (error != null) showFailure(context, error, true);
			else UiUtils.showInfo(context, context.getString(R.string.backup_all_data_ok, name));
		});
	}

	private static void createBackup(MainActivityDelegate activity, char[] password, File file,
			String fileName) {
		Context context = activity.getContext();
		App.get().execute(() -> {
			byte[] encoded = createBackupBytes(context, password);
			try (OutputStream output = new FileOutputStream(file)) {
				output.write(encoded);
			} finally {
				Arrays.fill(encoded, (byte) 0);
			}
			return fileName;
		}).main().onCompletion((name, error) -> {
			clear(password);
			if (error != null) showFailure(context, error, true);
			else UiUtils.showInfo(context, context.getString(R.string.backup_all_data_ok, name));
		});
	}

	private static byte[] createBackupBytes(Context context, char[] password) throws Exception {
		BackupCoordinator coordinator = coordinator(context);
		BackupData data = coordinator.capture();
		return new PortableBackupCodec().encode(data, password, System.currentTimeMillis(),
				BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME);
	}

	private static void chooseBackupFile(MainActivityDelegate activity) {
		Context context = activity.getContext();
		if (Utils.isSafSupported(activity)) {
			activity.startActivityForResult(() -> new Intent(Intent.ACTION_OPEN_DOCUMENT)
						.setType("*/*").addCategory(Intent.CATEGORY_OPENABLE))
					.onCompletion((data, error) -> {
						if (error != null) {
							showFailure(context, error, false);
							return;
						}
						if (data == null || data.getData() == null) return;
						Uri uri = data.getData();
						App.get().execute(() -> {
							try (InputStream input = context.getContentResolver().openInputStream(uri)) {
								if (input == null) throw new IOException("Backup input unavailable");
								return readAll(input);
							}
						}).main().onCompletion((bytes, failure) -> {
							if (failure != null) showFailure(context, failure, false);
							else dispatchRestore(activity, bytes);
						});
					});
			return;
		}

		if (!(activity.showFragment(me.aap.utils.R.id.file_picker) instanceof FilePickerFragment pick))
			return;
		pick.setMode(FilePickerFragment.FILE);
		pick.setPattern(Pattern.compile(".+\\.(?:fxbackup|zip)", Pattern.CASE_INSENSITIVE));
		pick.setFileSystem(LocalFileSystem.getInstance());
		pick.setFileConsumer(result -> {
			activity.showFragment(R.id.settings_fragment);
			if (result == null || result.getLocalFile() == null) return;
			File selected = result.getLocalFile();
			App.get().execute(() -> {
				try (InputStream input = new FileInputStream(selected)) {
					return readAll(input);
				}
			}).main().onCompletion((bytes, failure) -> {
				if (failure != null) showFailure(context, failure, false);
				else dispatchRestore(activity, bytes);
			});
		});
	}

	private static void dispatchRestore(MainActivityDelegate activity, byte[] bytes) {
		if (PortableBackupCodec.hasMagic(bytes)) {
			queryPassword(activity, false).onSuccess(password ->
				restorePortable(activity, bytes, password.toCharArray()));
		} else {
			restoreLegacy(activity, bytes);
		}
	}

	private static void restorePortable(MainActivityDelegate activity, byte[] bytes,
			char[] password) {
		Context context = activity.getContext();
		App.get().execute(() -> {
			PortableBackupCodec.Decoded decoded = new PortableBackupCodec().decode(bytes, password);
			coordinator(context).restore(decoded.data());
			return 0;
		}).main().onCompletion((ignored, error) -> {
			clear(password);
			Arrays.fill(bytes, (byte) 0);
			if (error != null) showFailure(context, error, false);
			else showRestoreComplete(context, false);
		});
	}

	private static void restoreLegacy(MainActivityDelegate activity, byte[] bytes) {
		Context context = activity.getContext();
		App.get().execute(() -> {
			LegacyBackupReader.Result legacy = new LegacyBackupReader().read(
					new ByteArrayInputStream(bytes));
			AndroidBackupStateStore stateStore = new AndroidBackupStateStore(context);
			BackupData current = stateStore.snapshot();
			BackupData safeLegacy = new BackupData(legacy.data().preferences(),
					current.securePreferences(), java.util.Map.of());
			new BackupCoordinator(stateStore, java.util.List.of()).restore(safeLegacy);
			return legacy.skippedSecureStores();
		}).main().onCompletion((skipped, error) -> {
			Arrays.fill(bytes, (byte) 0);
			if (error != null) showFailure(context, error, false);
			else showRestoreComplete(context, skipped != null && skipped > 0);
		});
	}

	private static BackupCoordinator coordinator(Context context) throws BackupException {
		AddonManager addons = FermataApplication.get().getAddonManager();
		return new BackupCoordinator(new AndroidBackupStateStore(context),
				addons.getBackupContributors());
	}

	private static me.aap.utils.async.FutureSupplier<String> queryPassword(
			MainActivityDelegate activity, boolean confirmation) {
		Context context = activity.getContext();
		return UiUtils.queryPrefs(context, R.string.backup_password_title, (store, set) -> {
			set.addStringPref(options -> {
				options.store = store;
				options.pref = PASSWORD;
				options.title = R.string.backup_password;
				options.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD;
				options.removeBlank = true;
			});
			if (confirmation) set.addStringPref(options -> {
				options.store = store;
				options.pref = CONFIRM_PASSWORD;
				options.title = R.string.backup_password_confirm;
				options.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD;
				options.removeBlank = true;
			});
		}, store -> validPassword(store, confirmation)).map(store -> store.getStringPref(PASSWORD));
	}

	private static boolean validPassword(PreferenceStore store, boolean confirmation) {
		String password = store.getStringPref(PASSWORD);
		return (password != null) && (password.length() >= 8) &&
				(!confirmation || password.equals(store.getStringPref(CONFIRM_PASSWORD)));
	}

	private static void showRestoreComplete(Context context, boolean legacySecretsSkipped) {
		int message = legacySecretsSkipped ? R.string.restore_legacy_secure_skipped :
				R.string.restore_backup_ok;
		UiUtils.showInfo(context, message).thenRun(() -> {
			App.get().getHandler().postDelayed(() -> System.exit(0), 1000);
			if (context instanceof Activity activity) activity.finishAffinity();
			else System.exit(0);
		});
	}

	private static void showFailure(Context context, Throwable error, boolean export) {
		Throwable cause = unwrap(error);
		Log.e("Backup operation failed: ", cause.getClass().getSimpleName());
		int message;
		if (cause instanceof BackupException backup) {
			message = switch (backup.getCode()) {
				case AUTHENTICATION_FAILED -> R.string.restore_backup_auth_failed;
				case UNSUPPORTED_VERSION -> R.string.restore_backup_unsupported;
				case SECURE_STORAGE_UNAVAILABLE -> R.string.restore_backup_secure_unavailable;
				default -> export ? R.string.backup_all_data_failed :
						R.string.restore_backup_failed;
			};
		} else {
			message = export ? R.string.backup_all_data_failed : R.string.restore_backup_failed;
		}
		UiUtils.showAlert(context, message);
	}

	private static Throwable unwrap(Throwable error) {
		Throwable result = error;
		while ((result.getCause() != null) &&
				((result instanceof java.util.concurrent.ExecutionException) ||
						(result instanceof java.util.concurrent.CompletionException))) {
			result = result.getCause();
		}
		return result;
	}

	private static byte[] readAll(InputStream input) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		for (int read; (read = input.read(buffer)) >= 0; ) {
			if ((bytes.size() + read) > MAX_INPUT_BYTES) throw new IOException("Backup is too large");
			bytes.write(buffer, 0, read);
		}
		return bytes.toByteArray();
	}

	private static String availableName(DocumentFile directory, String pattern) {
		String name = pattern + PortableBackupCodec.EXTENSION;
		for (int i = 1; directory.findFile(name) != null; i++) {
			name = pattern + '_' + i + PortableBackupCodec.EXTENSION;
		}
		return name;
	}

	private static String availableName(File directory, String pattern) {
		String name = pattern + PortableBackupCodec.EXTENSION;
		for (int i = 1; new File(directory, name).exists(); i++) {
			name = pattern + '_' + i + PortableBackupCodec.EXTENSION;
		}
		return name;
	}

	private static void clear(char[] password) {
		if (password != null) Arrays.fill(password, '\0');
	}
}
