package me.app.fermatax.auto.carapps;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Receives the CarDisplayServer Binder from the root server process via ContentProvider.call.
 */
public class ServerBinderProvider extends ContentProvider {

	public static final String METHOD_ATTACH = "attach";
	public static final String EXTRA_BINDER = "binder";
	public static final String KEY_OK = "ok";

	@Override
	public boolean onCreate() {
		return true;
	}

	@Nullable
	@Override
	public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
		int callingUid = Binder.getCallingUid();
		if (!CallerPolicy.providerAccepts(callingUid, android.os.Build.VERSION.SDK_INT)) {
			throw new SecurityException("Unauthorized caller UID: " + callingUid);
		}

		if (METHOD_ATTACH.equals(method) && extras != null) {
			IBinder binder = extras.getBinder(EXTRA_BINDER);
			if (binder != null) {
				CarDisplayClient.get().onServerBinderReceived(binder, callingUid);
				Bundle result = new Bundle();
				result.putBoolean(KEY_OK, true);
				return result;
			}
		}

		return null;
	}

	@Nullable
	@Override
	public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
	                    @Nullable String[] selectionArgs, @Nullable String sortOrder) {
		return null;
	}

	@Nullable
	@Override
	public String getType(@NonNull Uri uri) {
		return null;
	}

	@Nullable
	@Override
	public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
		return null;
	}

	@Override
	public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
		return 0;
	}

	@Override
	public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
	                  @Nullable String[] selectionArgs) {
		return 0;
	}
}
