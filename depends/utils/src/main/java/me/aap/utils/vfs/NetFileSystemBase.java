package me.aap.utils.vfs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.resource.Rid;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextBuilder;
import me.aap.utils.text.TextUtils;

import static java.nio.charset.StandardCharsets.UTF_16BE;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.text.TextUtils.appendHexString;
import static me.aap.utils.text.TextUtils.hexToBytes;

/**
 * @author Andrey Pavlenko
 */
public abstract class NetFileSystemBase implements VirtualFileSystem {
	/** Where passwords are kept. Without one installed they stay hex-encoded in the preference store. */
	public interface Secrets {
		@Nullable
		String get(String key);

		/** False when the value could not be stored. */
		boolean put(String key, String value);

		void remove(String key);
	}

	private static volatile Secrets secrets;
	private final Provider provider;
	private final PreferenceStore ps;
	private volatile List<VirtualFolder> roots;

	/** The app installs its encrypted store before any network file system is created. */
	public static void setSecrets(@Nullable Secrets s) {
		secrets = s;
	}

	protected NetFileSystemBase(Provider provider, PreferenceStore ps) {
		this.provider = provider;
		this.ps = ps;

		String[] pref = ps.getStringArrayPref(getRootsPref());

		if (pref.length == 0) {
			roots = Collections.emptyList();
			return;
		}

		ArrayList<VirtualFolder> roots = new ArrayList<>(pref.length);

		for (String p : pref) {
			try {
				VirtualFolder r = createRoot(p);
				if (r == null) Log.e("Invalid resource id: ", p);
				else roots.add(r);
			} catch (Throwable ex) {
				Log.e("Invalid resource id: ", p);
			}
		}

		roots.trimToSize();
		this.roots = !roots.isEmpty() ? roots : Collections.emptyList();
	}

	protected VirtualFolder createRoot(String rootId) {
		Rid rid = Rid.create(rootId);
		String scheme = rid.getScheme();
		String user = rid.getUserInfo();
		String host = rid.getHost();
		int port = rid.getPort();
		if (port == -1) port = getDefaultPort();
		String path = rid.getPath();
		String password;
		String keyFile;
		String keyPass;

		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			appendRid(scheme, tb, user, host, port);
			tb.append('#');
			int len = tb.length();

			tb.append('P');
			password = readSecret(tb.toString());
			tb.setLength(len);
			tb.append('F');
			keyFile = ps.getStringPref(Pref.s(tb.toString(), () -> null));
			tb.setLength(len);
			tb.append('K');
			keyPass = readSecret(tb.toString());
		}

		return createRoot(user, host, port, path, password, keyFile, keyPass);
	}

	@Nullable
	private String readSecret(String key) {
		Pref<Supplier<String>> legacy = Pref.s(key, () -> null);
		String hex = ps.getStringPref(legacy);
		Secrets s = secrets;
		if (s == null) return fromHex(hex);
		if (hex == null) return s.get(key);
		// saved before the encrypted store existed: move it there and drop the plain copy
		String value = fromHex(hex);
		if (s.put(key, value)) ps.removePref(legacy);
		return value;
	}

	private void writeSecret(PreferenceStore.Edit e, String key, @Nullable String value) {
		if (value == null) return;
		Pref<Supplier<String>> legacy = Pref.s(key, () -> null);
		Secrets s = secrets;
		if (s == null) e.setStringPref(legacy, toHex(value));
		else if (s.put(key, value)) e.removePref(legacy);
		else Log.e("Secure storage is unavailable: a network share password is kept only until restart");
	}

	protected abstract VirtualFolder createRoot(
			@Nullable String user, @NonNull String host, int port,
			@Nullable String path, @Nullable String password,
			@Nullable String keyFile, @Nullable String keyPass);

	protected abstract FutureSupplier<VirtualFolder> createConnectedRoot(
			@Nullable String user, @NonNull String host, int port,
			@Nullable String path, @Nullable String password,
			@Nullable String keyFile, @Nullable String keyPass);

	protected abstract FutureSupplier<VirtualResource> createResource(VirtualResource root, String path);

	protected abstract VirtualFile createFile(VirtualResource root, String path);

	protected abstract VirtualFolder createFolder(VirtualResource root, String path);

	protected abstract int getDefaultPort();

	protected abstract Pref<Supplier<String[]>> getRootsPref();

	@NonNull
	@Override
	public Provider getProvider() {
		return provider;
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualResource> getResource(Rid rid) {
		int[] cmp = new int[1];
		String path = rid.getPath();
		VirtualResource root = findRoot(rid, path, cmp);
		if (root == null) return completedNull();
		return (cmp[0] == 0) ? completed(root) : createResource(root, path);
	}

	@Override
	public FutureSupplier<VirtualFile> getFile(Rid rid) {
		int[] cmp = new int[1];
		String path = rid.getPath();
		VirtualResource root = findRoot(rid, path, cmp);
		if (root == null) return completedNull();
		return (cmp[0] == 0) ? completedNull() : completed(createFile(root, path));
	}

	@Override
	public FutureSupplier<VirtualFolder> getFolder(Rid rid) {
		int[] cmp = new int[1];
		String path = rid.getPath();
		VirtualResource root = findRoot(rid, path, cmp);
		if (root == null) return completedNull();
		return (cmp[0] == 0) ? completed((VirtualFolder) root) : completed(createFolder(root, path));
	}

	private VirtualResource findRoot(Rid rid, String path, int[] match) {
		if (!isSupported(rid)) return null;

		String user = rid.getUserInfo();
		String host = rid.getHost();
		int port = rid.getPort();
		if (port == -1) port = getDefaultPort();

		for (VirtualResource root : roots) {
			int cmp = compareRoot(root, user, host, port, path);
			if (cmp == -1) continue;
			match[0] = cmp;
			return root;
		}

		return null;
	}

	protected boolean isSupported(Rid rid) {
		return getProvider().getSupportedSchemes().contains(rid.getScheme());
	}

	protected int compareRoot(VirtualResource root, CharSequence user, CharSequence host, int port,
														CharSequence path) {
		Rid rootId = root.getRid();

		if (!TextUtils.equals(user, rootId.getUserInfo())) return -1;
		if (!TextUtils.equals(host, rootId.getHost())) return -1;

		int rootPort = rootId.getPort();
		if (rootPort == -1) rootPort = getDefaultPort();
		if (port != rootPort) return -1;

		CharSequence rootPath = rootId.getPath();
		if (!TextUtils.startsWith(path, rootPath)) return -1;

		int pathLen = path.length();
		int rootPathLen = rootPath.length();
		if (pathLen == rootPathLen) return 0;
		return (path.charAt(rootPathLen) == '/') ? 1 : -1;
	}

	@NonNull
	@Override
	public FutureSupplier<List<VirtualFolder>> getRoots() {
		return completed(roots);
	}

	public FutureSupplier<VirtualFolder> addRoot(@Nullable String user, @NonNull String host, int port,
																							 @Nullable String path, @Nullable String password,
																							 @Nullable String keyFile, @Nullable String keyPass) {
		return createConnectedRoot(user, host, port, path, password, keyFile, keyPass).then(root -> {
			Rid rid = root.getRid();

			synchronized (this) {
				List<VirtualFolder> roots = this.roots;

				for (VirtualFolder r : roots) {
					if (rid.equals(r.getRid())) return completed(r);
				}

				List<VirtualFolder> newRoots = new ArrayList<>(roots.size() + 1);
				newRoots.addAll(roots);
				newRoots.add(root);
				setRoots(newRoots);
				saveCredentials(rid.getScheme(), user, host, port, password, keyFile, keyPass);
				return completed(root);
			}
		});
	}

	public synchronized boolean removeRoot(VirtualFolder root) {
		List<VirtualFolder> roots = this.roots;
		if (roots.isEmpty()) return false;

		boolean removed = false;
		List<VirtualFolder> newRoots = new ArrayList<>(roots.size() - 1);
		Rid rid = root.getRid();

		for (VirtualFolder r : roots) {
			if (r.getRid().equals(rid)) removed = true;
			else newRoots.add(r);
		}

		if (!removed) return false;

		String scheme = rid.getScheme();
		String user = rid.getUserInfo();
		String host = rid.getHost();
		int port = rid.getPort();
		if (port == -1) port = getDefaultPort();
		Pref<Supplier<String>> password;
		Pref<Supplier<String>> keyFile;
		Pref<Supplier<String>> keyPass;

		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			appendRid(scheme, tb, user, host, port);
			tb.append('#');
			int len = tb.length();

			tb.append('P');
			password = Pref.s(tb.toString(), () -> null);
			tb.setLength(len);
			tb.append('F');
			keyFile = Pref.s(tb.toString(), () -> null);
			tb.setLength(len);
			tb.append('K');
			keyPass = Pref.s(tb.toString(), () -> null);
		}

		try (PreferenceStore.Edit e = getPreferenceStore().editPreferenceStore()) {
			e.removePref(password);
			e.removePref(keyFile);
			e.removePref(keyPass);
		}

		Secrets s = secrets;
		if (s != null) {
			s.remove(password.getName());
			s.remove(keyPass.getName());
		}

		setRoots(newRoots.isEmpty() ? Collections.emptyList() : newRoots);
		return true;
	}

	private void setRoots(List<VirtualFolder> roots) {
		this.roots = roots;
		String[] pref = new String[roots.size()];

		for (int i = 0, s = roots.size(); i < s; i++) {
			pref[i] = roots.get(i).getRid().toString();
		}

		getPreferenceStore().applyStringArrayPref(getRootsPref(), pref);
	}

	public PreferenceStore getPreferenceStore() {
		return ps;
	}

	protected void saveCredentials(@NonNull String scheme, @Nullable String user, @NonNull String host,
																 int port, @Nullable String password, @Nullable String keyFile,
																 @Nullable String keyPass) {
		try (SharedTextBuilder tb = SharedTextBuilder.get();
				 PreferenceStore.Edit e = getPreferenceStore().editPreferenceStore()) {
			appendRid(scheme, tb, user, host, port);
			tb.append('#');
			int len = tb.length();

			tb.append('P');
			writeSecret(e, tb.toString(), password);

			if (keyFile != null) {
				tb.setLength(len);
				tb.append('F');
				e.setStringPref(Pref.s(tb.toString(), () -> null), keyFile);
			}

			tb.setLength(len);
			tb.append('K');
			writeSecret(e, tb.toString(), keyPass);
		}
	}

	private void appendRid(@NonNull CharSequence scheme, TextBuilder tb, @Nullable String user,
												 @NonNull String host, int port) {
		tb.append(scheme).append("://");
		if (user != null) tb.append(user).append('@');
		if (host.indexOf(':') != -1) tb.append('[').append(host).append(']');
		else tb.append(host);
		if (port != getDefaultPort()) tb.append(':').append(port);
	}

	// Encoding only, not encryption: the format of passwords saved before Secrets
	private static String toHex(@NonNull String v) {
		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			appendHexString(tb, v.getBytes(UTF_16BE));
			return tb.toString();
		}
	}

	@Nullable
	private static String fromHex(@Nullable String v) {
		if (v == null) return v;
		return new String(hexToBytes(v), UTF_16BE);
	}
}
