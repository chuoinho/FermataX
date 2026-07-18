package me.aap.fermata.provider;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;
import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.content.ContentResolver.SCHEME_FILE;
import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.Cancellable;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpConnection;

/** Shares only normalized artwork files owned by this provider. */
public class FermataContentProvider extends ContentProvider {
	private static final String AUTHORITY = BuildConfig.APPLICATION_ID;
	private static final String IMAGE_PATH = "image";
	private static final String IMAGE_PREFIX = "content://" + AUTHORITY + '/' + IMAGE_PATH + '/';
	private static final int TOKEN_BYTES = 24;
	private static final int TOKEN_LENGTH = TOKEN_BYTES * 2;
	private static final int MAX_SOURCE_BYTES = 8 * 1024 * 1024;
	private static final int MAX_IMAGE_DIMENSION = 512;
	private static final int MAX_REDIRECTS = 5;
	private static final int MAX_CACHE_FILES = 4096;
	private static final int HTTP_TIMEOUT_MILLIS = 10_000;
	private static final int HTTP_TIMEOUT_SECONDS = HTTP_TIMEOUT_MILLIS / 1000;
	private static final char[] HEX = "0123456789abcdef".toCharArray();
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final ArtworkStore ARTWORK = new ArtworkStore();

	public static boolean isSupportedFileScheme(@Nullable String scheme) {
		if (scheme == null) return false;
		return switch (scheme) {
			case "http", "https", SCHEME_FILE, SCHEME_CONTENT, SCHEME_ANDROID_RESOURCE -> true;
			default -> FermataApplication.get().getVfsManager().isSupportedScheme(scheme);
		};
	}

	/** Materializes an untrusted source as a bounded PNG before making it cross-UID readable. */
	@NonNull
	public static FutureSupplier<Uri> shareImage(@NonNull Uri source) {
		UriRequest current = UriRequest.parse(source);
		if (current != null) {
			File file = ARTWORK.resolve(FermataApplication.get(), current.token);
			return (file == null) ? failed(new FileNotFoundException(source.toString())) :
					completed(source);
		}

		return App.get().execute(() -> {
			Context context = FermataApplication.get();
			Bitmap bitmap = loadNormalizedBitmap(context, source);
			if (bitmap == null) throw new IOException("Unsupported artwork: " + source);
			return ARTWORK.publish(context, source.toString(), bitmap);
		});
	}

	/** Provider URIs no longer disclose or recover their untrusted source URI. */
	@Nullable
	public static String getOrigUri(String ignored) {
		return null;
	}

	@Nullable
	@Override
	public String[] getStreamTypes(@NonNull Uri uri, @NonNull String mimeTypeFilter) {
		return (resolveImage(uri) == null) ? null : new String[]{"image/png"};
	}

	@Nullable
	@Override
	public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
			throws FileNotFoundException {
		if (!"r".equals(mode)) throw new FileNotFoundException("Provider is read-only");
		UriRequest request = UriRequest.parse(uri);
		Context context = getContext();
		if ((request == null) || (context == null)) {
			throw new FileNotFoundException("Unknown artwork token");
		}
		return ARTWORK.open(context, request.token);
	}

	@Override
	public boolean onCreate() {
		Context context = getContext();
		return (context == null) || ARTWORK.ensureDirectory(context);
	}

	@Nullable
	@Override
	public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
			@Nullable String[] selectionArgs, @Nullable String sortOrder) {
		UriRequest request = UriRequest.parse(uri);
		if ((request == null) || (resolveImage(uri) == null)) return null;
		MatrixCursor cursor = new MatrixCursor(new String[]{"_display_name", "mime_type"});
		cursor.addRow(new Object[]{request.token + ".png", "image/png"});
		return cursor;
	}

	@Nullable
	@Override
	public String getType(@NonNull Uri uri) {
		return (resolveImage(uri) == null) ? null : "image/png";
	}

	@Nullable
	@Override
	public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
		throw new UnsupportedOperationException("Provider is read-only");
	}

	@Override
	public int delete(@NonNull Uri uri, @Nullable String selection,
			@Nullable String[] selectionArgs) {
		throw new UnsupportedOperationException("Provider is read-only");
	}

	@Override
	public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
			@Nullable String[] selectionArgs) {
		throw new UnsupportedOperationException("Provider is read-only");
	}

	@Nullable
	private File resolveImage(Uri uri) {
		UriRequest request = UriRequest.parse(uri);
		Context context = getContext();
		return ((request == null) || (context == null)) ? null :
				ARTWORK.resolve(context, request.token);
	}

	@Nullable
	static UriRequest parseImageRequest(String expectedAuthority, @Nullable String scheme,
			@Nullable String authority, @Nullable String encodedPath, @Nullable String encodedQuery,
			@Nullable String encodedFragment) {
		if (!SCHEME_CONTENT.equals(scheme) || !expectedAuthority.equals(authority) ||
				(encodedQuery != null) || (encodedFragment != null) || (encodedPath == null)) return null;
		String prefix = '/' + IMAGE_PATH + '/';
		if (!encodedPath.startsWith(prefix)) return null;
		String token = encodedPath.substring(prefix.length());
		return isValidToken(token) ? new UriRequest(token) : null;
	}

	static boolean isValidToken(@Nullable String token) {
		if ((token == null) || (token.length() != TOKEN_LENGTH)) return false;
		for (int i = 0; i < token.length(); i++) {
			char c = token.charAt(i);
			if (((c < '0') || (c > '9')) && ((c < 'a') || (c > 'f'))) return false;
		}
		return true;
	}

	static boolean isSafeRemoteAddress(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() ||
				address.isLinkLocalAddress() || address.isSiteLocalAddress() ||
				address.isMulticastAddress()) return false;
		byte[] bytes = address.getAddress();

		if (address instanceof Inet4Address) {
			int a = bytes[0] & 0xff;
			int b = bytes[1] & 0xff;
			if ((a == 0) || (a >= 224) || ((a == 100) && ((b & 0xc0) == 0x40)) ||
					((a == 198) && ((b == 18) || (b == 19)))) return false;
			return !((a == 192) && (b == 0));
		}

		if (address instanceof Inet6Address) {
			int first = bytes[0] & 0xff;
			if ((first & 0xfe) == 0xfc) return false;
			if ((bytes[0] == 0x20) && (bytes[1] == 0x01) &&
					(bytes[2] == 0x0d) && ((bytes[3] & 0xff) == 0xb8)) return false;
			if (isIpv4Mapped(bytes)) {
				try {
					return isSafeRemoteAddress(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16)));
				} catch (IOException impossible) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean isIpv4Mapped(byte[] address) {
		if (address.length != 16) return false;
		for (int i = 0; i < 10; i++) if (address[i] != 0) return false;
		return (address[10] == (byte) 0xff) && (address[11] == (byte) 0xff);
	}

	private static Bitmap loadNormalizedBitmap(Context context, Uri source) throws IOException {
		String scheme = source.getScheme();
		if (scheme == null) throw new IOException("Artwork URI has no scheme");
		Bitmap bitmap;

		switch (scheme) {
			case "http", "https" -> bitmap = loadRemoteBitmap(source);
			case SCHEME_FILE -> {
				File file = allowedImageFile(context, source);
				try (InputStream input = new FileInputStream(file)) {
					bitmap = decodeBounded(input);
				}
			}
			case SCHEME_CONTENT, SCHEME_ANDROID_RESOURCE -> {
				if (SCHEME_CONTENT.equals(scheme) && AUTHORITY.equals(source.getAuthority())) {
					throw new IOException("Nested FermataX provider URI");
				}
				try (InputStream input = context.getContentResolver().openInputStream(source)) {
					if (input == null) throw new FileNotFoundException(source.toString());
					bitmap = decodeBounded(input);
				}
			}
			default -> {
				if (!FermataApplication.get().getVfsManager().isSupportedScheme(scheme)) {
					throw new IOException("Unsupported artwork scheme: " + scheme);
				}
				try {
					bitmap = FermataApplication.get().getBitmapCache()
							.getBitmap(context, source.toString(), true, true).get();
				} catch (Exception error) {
					throw new IOException("Failed to decode artwork", error);
				}
			}
		}

		if (bitmap == null) throw new IOException("Source is not a decodable image");
		return resize(bitmap, MAX_IMAGE_DIMENSION);
	}

	private static File allowedImageFile(Context context, Uri source) throws IOException {
		String path = source.getPath();
		if ((path == null) || path.isEmpty()) throw new FileNotFoundException(source.toString());
		File file = new File(path).getCanonicalFile();
		File data = context.getDataDir().getCanonicalFile();
		if (isWithin(file, data)) {
			File cache = context.getCacheDir().getCanonicalFile();
			File icons = new File(cache, "icons").getCanonicalFile();
			File images = new File(cache, "images").getCanonicalFile();
			File shared = ARTWORK.directory(context).getCanonicalFile();
			if (!isWithin(file, icons) && !isWithin(file, images) && !isWithin(file, shared)) {
				throw new IOException("Private app file is not shareable");
			}
		}
		if (!file.isFile()) throw new FileNotFoundException(source.toString());
		return file;
	}

	private static boolean isWithin(File file, File directory) {
		String child = file.getPath();
		String parent = directory.getPath();
		return child.equals(parent) || child.startsWith(parent + File.separator);
	}

	private static Bitmap loadRemoteBitmap(Uri source) throws IOException {
		URL current = new URL(source.toString());
		for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
			RemoteResponse response = fetchRemote(current);
			if (response.redirect != null) {
				current = new URL(current, response.redirect);
				continue;
			}
			return decodeBounded(response.body);
		}
		throw new IOException("Too many artwork redirects");
	}

	private static RemoteResponse fetchRemote(URL url) throws IOException {
		InetSocketAddress address = resolveRemoteAddress(url);
		Promise<RemoteResponse> result = new Promise<>();
		AtomicReference<HttpConnection> connection = new AtomicReference<>();
		HttpConnection.Opts options = new HttpConnection.Opts();
		options.url = url;
		options.address = address;
		options.keepAlive = false;
		options.maxRedirects = 0;
		options.maxReconnects = 0;
		options.connectTimeout = HTTP_TIMEOUT_MILLIS;
		options.readTimeout = HTTP_TIMEOUT_MILLIS;
		options.responseTimeout = HTTP_TIMEOUT_SECONDS;
		options.userAgent = "FermataX/Artwork";
		options.acceptEncoding = null;

		Cancellable request = HttpConnection.connect(options, (response, error) -> {
			if (error != null) {
				result.completeExceptionally(error);
				return completedVoid();
			}

			connection.set(response.getConnection());
			int status = response.getStatusCode();
			if ((status >= 300) && (status < 400)) {
				CharSequence location = response.getLocation();
				if (location == null) {
					result.completeExceptionally(new IOException("Artwork redirect has no location"));
				} else {
					result.complete(new RemoteResponse(location.toString(), null));
				}
				return response.skipPayload();
			}
			if ((status < 200) || (status >= 300)) {
				result.completeExceptionally(new IOException("Artwork HTTP " + status));
				return response.skipPayload();
			}

			return response.getPayload((payload, payloadError) -> {
				if (payloadError != null) {
					result.completeExceptionally(payloadError);
				} else {
					ByteBuffer data = payload.slice();
					byte[] bytes = new byte[data.remaining()];
					data.get(bytes);
					result.complete(new RemoteResponse(null, bytes));
				}
				return completedVoid();
			}, false, MAX_SOURCE_BYTES);
		});

		try {
			return result.get(HTTP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new IOException("Artwork download interrupted", error);
		} catch (ExecutionException | TimeoutException error) {
			throw new IOException("Artwork download failed", error);
		} finally {
			request.cancel();
			HttpConnection active = connection.get();
			if (active != null) active.close();
		}
	}

	private static InetSocketAddress resolveRemoteAddress(URL url) throws IOException {
		String protocol = url.getProtocol();
		if (!"http".equals(protocol) && !"https".equals(protocol)) {
			throw new IOException("Unsafe artwork redirect scheme");
		}
		if ((url.getUserInfo() != null) || (url.getHost() == null) || url.getHost().isBlank()) {
			throw new IOException("Invalid artwork URL");
		}
		InetAddress[] addresses = InetAddress.getAllByName(url.getHost());
		return selectRemoteAddress(url, addresses);
	}

	static InetSocketAddress selectRemoteAddress(URL url, InetAddress[] addresses) throws IOException {
		if (addresses.length == 0) throw new IOException("Artwork host did not resolve");
		for (InetAddress address : addresses) {
			if (!isSafeRemoteAddress(address)) {
				throw new IOException("Artwork URL resolves to a private address");
			}
		}
		int port = url.getPort();
		if (port == -1) port = "https".equals(url.getProtocol()) ? 443 : 80;
		return new InetSocketAddress(addresses[0], port);
	}

	private static final class RemoteResponse {
		final String redirect;
		final byte[] body;

		RemoteResponse(String redirect, byte[] body) {
			this.redirect = redirect;
			this.body = body;
		}
	}

	private static Bitmap decodeBounded(byte[] data) {
		BitmapFactory.Options bounds = new BitmapFactory.Options();
		bounds.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
		if ((bounds.outWidth <= 0) || (bounds.outHeight <= 0)) return null;
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_DIMENSION);
		return resize(BitmapFactory.decodeByteArray(data, 0, data.length, options),
				MAX_IMAGE_DIMENSION);
	}

	private static Bitmap decodeBounded(InputStream input) throws IOException {
		return decodeBounded(readBounded(input, MAX_SOURCE_BYTES));
	}

	static int sampleSize(int width, int height, int maxDimension) {
		int sample = 1;
		long decodeTarget = Math.max(1L, (long) maxDimension * 2L);
		while (((width / sample) > decodeTarget) || ((height / sample) > decodeTarget)) {
			if (sample > (Integer.MAX_VALUE / 2)) return Integer.MAX_VALUE;
			sample *= 2;
		}
		return sample;
	}

	private static byte[] readBounded(InputStream input, int limit) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 64 * 1024));
		byte[] buffer = new byte[8192];
		int total = 0;
		for (int read; (read = input.read(buffer)) != -1; ) {
			if (read == 0) continue;
			total += read;
			if (total > limit) throw new IOException("Artwork exceeds size limit");
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static Bitmap resize(@Nullable Bitmap bitmap, int maxDimension) {
		if (bitmap == null) return null;
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		if ((width <= maxDimension) && (height <= maxDimension)) return bitmap;
		float scale = Math.min((float) maxDimension / width, (float) maxDimension / height);
		return Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(width * scale)),
				Math.max(1, Math.round(height * scale)), true);
	}

	static final class UriRequest {
		final String token;

		private UriRequest(String token) {
			this.token = token;
		}

		@Nullable
		static UriRequest parse(Uri uri) {
			return parseImageRequest(AUTHORITY, uri.getScheme(), uri.getAuthority(),
					uri.getEncodedPath(), uri.getEncodedQuery(), uri.getEncodedFragment());
		}
	}

	private static final class ArtworkStore {
		private final Map<String, String> sources = new LinkedHashMap<>(16, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
				return size() > MAX_CACHE_FILES;
			}
		};

		boolean ensureDirectory(Context context) {
			File directory = directory(context);
			return directory.isDirectory() || directory.mkdirs();
		}

		File directory(Context context) {
			return new File(context.getCacheDir(), "shared-artwork");
		}

		synchronized Uri publish(Context context, String source, Bitmap bitmap) throws IOException {
			if (!ensureDirectory(context)) throw new IOException("Unable to create artwork cache");
			String existing = sources.get(source);
			File file = (existing == null) ? null : resolve(context, existing);
			if (file != null) return Uri.parse(IMAGE_PREFIX + existing);

			String token;
			do token = randomToken(); while (new File(directory(context), token + ".png").exists());
			File destination = new File(directory(context), token + ".png");
			File staging = File.createTempFile("artwork-", ".tmp", directory(context));
			try {
				try (FileOutputStream output = new FileOutputStream(staging)) {
					if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
						throw new IOException("Unable to encode artwork");
					}
					output.getFD().sync();
				}
				movePublished(staging, destination);
			} finally {
				//noinspection ResultOfMethodCallIgnored
				staging.delete();
			}
			sources.put(source, token);
			prune(context, destination);
			return Uri.parse(IMAGE_PREFIX + token);
		}

		@Nullable
		synchronized File resolve(Context context, String token) {
			if (!isValidToken(token)) return null;
			try {
				File directory = directory(context).getCanonicalFile();
				File file = new File(directory, token + ".png").getCanonicalFile();
				return isWithin(file, directory) && file.isFile() ? file : null;
			} catch (IOException error) {
				return null;
			}
		}

		synchronized ParcelFileDescriptor open(Context context, String token)
				throws FileNotFoundException {
			File image = resolve(context, token);
			if (image == null) throw new FileNotFoundException("Unknown artwork token");
			//noinspection ResultOfMethodCallIgnored
			image.setLastModified(System.currentTimeMillis());
			return ParcelFileDescriptor.open(image, MODE_READ_ONLY);
		}

		private void prune(Context context, File keep) {
			File[] files = directory(context).listFiles(file -> file.isFile() &&
					file.getName().endsWith(".png"));
			if ((files == null) || (files.length <= MAX_CACHE_FILES)) return;
			Arrays.sort(files, Comparator.comparingLong(File::lastModified));
			int excess = files.length - MAX_CACHE_FILES;
			for (File file : files) {
				if ((excess == 0) || file.equals(keep)) continue;
				if (file.delete()) {
					String name = file.getName();
					String token = name.substring(0, name.length() - 4);
					sources.values().removeIf(token::equals);
					excess--;
				}
			}
		}
	}

	private static void movePublished(File source, File destination) throws IOException {
		try {
			Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException error) {
			if (!source.renameTo(destination)) throw new IOException("Unable to publish artwork", error);
		}
	}

	private static String randomToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		char[] token = new char[TOKEN_LENGTH];
		for (int i = 0; i < bytes.length; i++) {
			int value = bytes[i] & 0xff;
			token[i * 2] = HEX[value >>> 4];
			token[i * 2 + 1] = HEX[value & 0x0f];
		}
		return new String(token);
	}
}
