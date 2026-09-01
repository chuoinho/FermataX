package me.aap.fermata.addon.tv;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AutomotiveShutdownParticipant;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.MediaLibAddon;
import me.aap.fermata.addon.VoiceSearchAddon;
import me.aap.fermata.backup.BackupContributor;
import me.aap.fermata.backup.BackupIO;
import me.aap.fermata.addon.tv.xtream.XtreamAccount;
import me.aap.fermata.addon.tv.stalker.StalkerAccount;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class TvAddon implements MediaLibAddon, VoiceSearchAddon, AutomotiveShutdownParticipant,
		BackupContributor {
	private static final String BACKUP_ID = "tv.sources";
	private static final int BACKUP_VERSION = 2;
	private static final int MAX_BACKUP_SOURCES = 10_000;
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(TvAddon.class.getName());
	private static TvRootItem root;
	private final TvSourceRefreshCoordinator refreshCoordinator =
			new TvSourceRefreshCoordinator();

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.tv_fragment;
	}

	@NonNull
	@Override
	public String getVoiceTarget() {
		return "tv";
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new TvFragment();
	}

	@Override
	public boolean isSupportedItem(Item i) {
		return (i instanceof TvItem);
	}

	public synchronized TvRootItem getRootItem(DefaultMediaLib lib) {
		if ((root == null) || (root.getLib() != lib)) {
			refreshCoordinator.reset();
			root = new TvRootItem(lib);
		}
		return root;
	}

	TvSourceRefreshCoordinator getRefreshCoordinator() {
		return refreshCoordinator;
	}

	@Override
	public void start() {
		refreshCoordinator.start();
	}

	@Override
	public void stop() {
		refreshCoordinator.stop();
	}

	@Override
	public void onAutomotiveShutdown() {
		stop();
	}

	@Override
	public void onAutomotiveSessionStarted() {
		start();
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme,
																								String id) {
		return getRootItem(lib).getItem(scheme, id);
	}

	@Override
	public String getBackupId() {
		return BACKUP_ID;
	}

	@Override
	public int getBackupVersion() {
		return BACKUP_VERSION;
	}

	@Override
	public byte[] exportBackup() throws Exception {
		TvSourceRepository repository = backupRepository();
		int[] ids = repository.getSourceIds();
		int counter = repository.getSourceCounter();
		for (int id : ids) counter = Math.max(counter, id);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeInt(counter);
			output.writeInt(ids.length);
			for (int id : ids) {
				output.writeInt(id);
				String type = repository.getSourceType(id);
				BackupIO.writeString(output, type);
				if (TvSourceItem.TYPE_XTREAM.equals(type)) {
					XtreamAccount account = XtreamAccount.load(repository.getStore(), id);
					if (account == null) throw new IllegalStateException(
							"Xtream source credentials are unavailable");
					writeXtream(output, account);
				} else if (TvSourceItem.TYPE_STALKER.equals(type)) {
					StalkerAccount account = StalkerAccount.load(repository.getStore(), id);
					if (account == null) throw new IllegalStateException(
							"Stalker source identity is unavailable");
					writeStalker(output, account);
				} else {
					BackupIO.writeNullableString(output, repository.getM3uId(id));
				}
			}
		}
		return bytes.toByteArray();
	}

	@Override
	public void validateRestore(int version, byte[] data) throws Exception {
		readSources(version, data);
	}

	@Override
	public void restoreBackup(int version, byte[] data) throws Exception {
		TvBackup sources = readSources(version, data);
		backupRepository().restoreSources(sources.counter, sources.sources);
	}

	@Override
	public void verifyRestore(int version, byte[] data) throws Exception {
		TvBackup expected = readSources(version, data);
		TvSourceRepository repository = backupRepository();
		int[] actualIds = repository.getSourceIds();
		if (actualIds.length != expected.sources.size()) throw incomplete();
		for (int i = 0; i < actualIds.length; i++) {
			SourceBackup source = expected.sources.get(i);
			if ((actualIds[i] != source.id) ||
					!repository.getSourceType(source.id).equals(source.type)) throw incomplete();
			if (source.account != null) {
				XtreamAccount actual = XtreamAccount.load(repository.getStore(), source.id);
				if ((actual == null) || !sameAccount(source.account, actual)) throw incomplete();
			} else if (source.stalkerAccount != null) {
				StalkerAccount actual = StalkerAccount.load(repository.getStore(), source.id);
				if ((actual == null) || !sameStalkerAccount(source.stalkerAccount, actual)) {
					throw incomplete();
				}
			} else if (!java.util.Objects.equals(source.m3uId,
					repository.getM3uId(source.id))) throw incomplete();
		}
		if ((repository.getSourceCounter() != expected.counter) ||
				repository.hasSource(repository.nextSourceId())) throw incomplete();
	}

	private static TvSourceRepository backupRepository() {
		SharedPreferenceStore store = SharedPreferenceStore.create(
				FermataApplication.get(), "medialib");
		return new TvSourceRepository(store);
	}

	private static void writeXtream(DataOutputStream output, XtreamAccount account)
			throws Exception {
		BackupIO.writeNullableString(output, account.getRawName());
		output.writeInt(account.getSchemeIndex());
		BackupIO.writeString(output, account.getHost());
		output.writeInt(account.getPort());
		BackupIO.writeString(output, account.getUsername());
		BackupIO.writeString(output, account.getPassword());
		output.writeInt(account.getOutputIndex());
		BackupIO.writeNullableString(output, account.getUserAgent());
		output.writeInt(account.getResponseTimeout());
	}

	private static void writeStalker(DataOutputStream output, StalkerAccount account)
			throws Exception {
		BackupIO.writeNullableString(output, account.getRawName());
		BackupIO.writeString(output, account.getPortal());
		BackupIO.writeString(output, account.getMac());
		BackupIO.writeNullableString(output, account.getSerial());
		BackupIO.writeNullableString(output, account.getDeviceId());
		BackupIO.writeNullableString(output, account.getRawUserAgent());
		output.writeInt(account.getResponseTimeout());
	}

	private static TvBackup readSources(int version, byte[] data) throws Exception {
		if ((version < 1) || (version > BACKUP_VERSION)) throw new IllegalArgumentException(
				"Unsupported TV backup version");
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
			int counter = input.readInt();
			int count = BackupIO.readCount(input, MAX_BACKUP_SOURCES, "TV sources");
			List<SourceBackup> sources = new ArrayList<>(count);
			Set<Integer> ids = new HashSet<>();
			int maximumId = 0;
			for (int i = 0; i < count; i++) {
				int id = input.readInt();
				if ((id <= 0) || !ids.add(id)) throw new IllegalArgumentException(
						"Invalid TV source ID");
				maximumId = Math.max(maximumId, id);
				String type = BackupIO.readString(input);
				if (TvSourceItem.TYPE_XTREAM.equals(type)) {
					String name = BackupIO.readNullableString(input);
					int scheme = input.readInt();
					String host = BackupIO.readString(input);
					int port = input.readInt();
					String username = BackupIO.readString(input);
					String password = BackupIO.readString(input);
					int output = input.readInt();
					String userAgent = BackupIO.readNullableString(input);
					int timeout = input.readInt();
					if ((scheme < 0) || (scheme >= XtreamAccount.SCHEMES.length) ||
							(output < 0) || (output >= XtreamAccount.OUTPUTS.length) ||
							(port < 0) || (port > 65_535) || (timeout < 0)) {
						throw new IllegalArgumentException("Invalid Xtream source options");
					}
					XtreamAccount account = new XtreamAccount(id,
							name, scheme, host, port, username, password, output, userAgent, timeout);
					if (!account.isComplete()) throw new IllegalArgumentException(
							"Incomplete Xtream source");
					sources.add(new SourceBackup(id, type, null, account, null));
				} else if (TvSourceItem.TYPE_STALKER.equals(type) && (version >= 2)) {
					String name = BackupIO.readNullableString(input);
					String portal = BackupIO.readString(input);
					String mac = BackupIO.readString(input);
					String serial = BackupIO.readNullableString(input);
					String deviceId = BackupIO.readNullableString(input);
					String userAgent = BackupIO.readNullableString(input);
					int timeout = input.readInt();
					StalkerAccount account = new StalkerAccount(id, name, portal, mac, serial,
							deviceId, userAgent, timeout);
					if ((timeout < 0) || !account.isComplete()) {
						throw new IllegalArgumentException("Invalid Stalker source options");
					}
					sources.add(new SourceBackup(id, type, null, null, account));
				} else if (TvSourceItem.TYPE_M3U.equals(type)) {
					String m3uId = BackupIO.readNullableString(input);
					if ((m3uId == null) || m3uId.isBlank()) throw new IllegalArgumentException(
							"Missing M3U source reference");
					sources.add(new SourceBackup(id, type, m3uId, null, null));
				} else {
					throw new IllegalArgumentException("Unknown TV source type");
				}
			}
			if ((counter < maximumId) || (input.read() != -1)) {
				throw new IllegalArgumentException("Invalid TV source counter");
			}
			return new TvBackup(counter, List.copyOf(sources));
		}
	}

	private static boolean sameAccount(XtreamAccount first, XtreamAccount second) {
		return java.util.Objects.equals(first.getRawName(), second.getRawName()) &&
				(first.getSchemeIndex() == second.getSchemeIndex()) &&
				first.getHost().equals(second.getHost()) && (first.getPort() == second.getPort()) &&
				first.getUsername().equals(second.getUsername()) &&
				first.getPassword().equals(second.getPassword()) &&
				(first.getOutputIndex() == second.getOutputIndex()) &&
				java.util.Objects.equals(first.getUserAgent(), second.getUserAgent()) &&
				(first.getResponseTimeout() == second.getResponseTimeout());
	}

	private static boolean sameStalkerAccount(StalkerAccount first, StalkerAccount second) {
		return java.util.Objects.equals(first.getRawName(), second.getRawName()) &&
				first.getPortal().equals(second.getPortal()) &&
				first.getMac().equals(second.getMac()) &&
				java.util.Objects.equals(first.getSerial(), second.getSerial()) &&
				java.util.Objects.equals(first.getDeviceId(), second.getDeviceId()) &&
				java.util.Objects.equals(first.getRawUserAgent(), second.getRawUserAgent()) &&
				(first.getResponseTimeout() == second.getResponseTimeout());
	}

	private static IllegalStateException incomplete() {
		return new IllegalStateException("TV sources did not restore completely");
	}

	private record TvBackup(int counter, List<SourceBackup> sources) {
	}

	static final class SourceBackup {
		final int id;
		final String type;
		final String m3uId;
		final XtreamAccount account;
		final StalkerAccount stalkerAccount;

		SourceBackup(int id, String type, String m3uId, XtreamAccount account,
				StalkerAccount stalkerAccount) {
			this.id = id;
			this.type = type;
			this.m3uId = m3uId;
			this.account = account;
			this.stalkerAccount = stalkerAccount;
		}
	}
}
