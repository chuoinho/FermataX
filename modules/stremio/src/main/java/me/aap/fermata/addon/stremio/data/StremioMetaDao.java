package me.aap.fermata.addon.stremio.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.addon.stremio.security.StremioDurableTextPolicy;

final class StremioMetaDao {
	void put(SQLiteDatabase database, StremioMetaRecord meta) {
		assertUntainted(meta);
		database.beginTransaction();
		try {
			assertIdentity(database, meta);
			ContentValues values = StremioDataRows.metaValues(meta);
			int updated = database.update("stremio_meta", values, "meta_key=?",
					new String[]{meta.metaKey()});
			if (updated == 0) database.insertOrThrow("stremio_meta", null, values);
			StremioProgressFavoriteDao.pruneDurableRows(database,
					StremioRepository.MAX_META_ROWS, StremioRepository.MAX_VIDEO_ROWS,
					StremioRepository.MAX_PROGRESS_ROWS);
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	void putOwned(SQLiteDatabase database, StremioMetaRecord meta,
			StremioMetaProviderRecord provider) {
		assertUntainted(meta);
		StremioDurableTextPolicy.requireUntainted("metadata provider",
				provider.sourceUuid(), provider.providerMetaId());
		database.beginTransaction();
		try {
			assertIdentity(database, meta);
			boolean existing = exists(database, meta.metaKey());
			if (!existing) {
				database.insertOrThrow("stremio_meta", null, StremioDataRows.metaValues(meta));
			}
			putProviderRow(database, provider);
			if (existing && isPreferredProvider(database, provider)) {
				database.update("stremio_meta", StremioDataRows.metaValues(meta), "meta_key=?",
						new String[]{meta.metaKey()});
			}
			StremioProgressFavoriteDao.pruneDurableRows(database,
					StremioRepository.MAX_META_ROWS, StremioRepository.MAX_VIDEO_ROWS,
					StremioRepository.MAX_PROGRESS_ROWS);
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	StremioMetaRecord get(SQLiteDatabase database, String metaKey) {
		try (Cursor cursor = database.query("stremio_meta", null, "meta_key=?",
				new String[]{metaKey}, null, null, null)) {
			return cursor.moveToFirst() ? StremioDataRows.readMeta(cursor) : null;
		}
	}

	void putProvider(SQLiteDatabase database, StremioMetaProviderRecord provider) {
		StremioDurableTextPolicy.requireUntainted("metadata provider",
				provider.sourceUuid(), provider.providerMetaId());
		putProviderRow(database, provider);
	}

	List<StremioMetaProviderRecord> getProviders(SQLiteDatabase database, String metaKey) {
		List<StremioMetaProviderRecord> providers = new ArrayList<>();
		try (Cursor cursor = database.query("stremio_meta_provider", null, "meta_key=?",
				new String[]{metaKey}, null, null, "priority ASC, updated_ms DESC")) {
			while (cursor.moveToNext()) {
				providers.add(new StremioMetaProviderRecord(
						StremioDataRows.string(cursor, "meta_key"),
						StremioDataRows.string(cursor, "source_uuid"),
						StremioDataRows.string(cursor, "provider_meta_id"),
						cursor.getInt(cursor.getColumnIndexOrThrow("priority")),
						cursor.getLong(cursor.getColumnIndexOrThrow("updated_ms"))));
			}
		}
		return List.copyOf(providers);
	}

	Map<String, StremioMetaRecord> readMetadata(SQLiteDatabase database,
			Collection<String> keys) {
		Map<String, StremioMetaRecord> result = new LinkedHashMap<>();
		StremioDataRows.queryBatches(database, "stremio_meta", "meta_key", keys, cursor -> {
			StremioMetaRecord meta = StremioDataRows.readMeta(cursor);
			result.put(meta.metaKey(), meta);
		});
		return result;
	}

	Map<String, List<StremioMetaProviderRecord>> readProviders(SQLiteDatabase database,
			Collection<String> keys) {
		Map<String, List<StremioMetaProviderRecord>> result = new LinkedHashMap<>();
		StremioDataRows.queryBatches(database, "stremio_meta_provider", "meta_key", keys,
				"meta_key ASC,priority ASC,updated_ms DESC,source_uuid ASC", cursor -> {
					StremioMetaProviderRecord provider = new StremioMetaProviderRecord(
							StremioDataRows.string(cursor, "meta_key"),
							StremioDataRows.string(cursor, "source_uuid"),
							StremioDataRows.string(cursor, "provider_meta_id"),
							StremioDataRows.integer(cursor, "priority"),
							StremioDataRows.longValue(cursor, "updated_ms"));
					result.computeIfAbsent(provider.metaKey(), ignored -> new ArrayList<>())
							.add(provider);
				});
		return result;
	}

	private static void putProviderRow(SQLiteDatabase database,
			StremioMetaProviderRecord provider) {
		ContentValues values = StremioDataRows.metaProviderValues(provider);
		int updated = database.update("stremio_meta_provider", values,
				"meta_key=? AND source_uuid=?",
				new String[]{provider.metaKey(), provider.sourceUuid()});
		if (updated == 0) database.insertOrThrow("stremio_meta_provider", null, values);
	}

	private static boolean exists(SQLiteDatabase database, String metaKey) {
		try (Cursor cursor = database.query("stremio_meta", new String[]{"meta_key"},
				"meta_key=?", new String[]{metaKey}, null, null, null)) {
			return cursor.moveToFirst();
		}
	}

	private static boolean isPreferredProvider(SQLiteDatabase database,
			StremioMetaProviderRecord candidate) {
		try (Cursor cursor = database.rawQuery(
				"SELECT provider.source_uuid FROM stremio_meta_provider provider " +
						"JOIN stremio_addon source ON source.source_uuid=provider.source_uuid " +
						"WHERE provider.meta_key=? AND source.enabled=1 " +
						"ORDER BY provider.priority ASC,source.position ASC," +
						"provider.source_uuid ASC LIMIT 1",
				new String[]{candidate.metaKey()})) {
			return cursor.moveToFirst() && candidate.sourceUuid().equals(cursor.getString(0));
		}
	}

	private static void assertIdentity(SQLiteDatabase database, StremioMetaRecord meta) {
		try (Cursor cursor = database.query("stremio_meta",
				new String[]{"identity_scope", "type", "provider_meta_id", "canonical_identity"},
				"meta_key=?", new String[]{meta.metaKey()}, null, null, null)) {
			if (!cursor.moveToFirst()) return;
			String existingScope = cursor.getString(0);
			// Early Stremio builds accidentally wrote the opaque content key as the scope.
			// Allow only that exact legacy shape to be rebound to its verified source UUID.
			boolean legacyScope = existingScope.equals(meta.metaKey()) &&
					meta.metaKey().startsWith("stremio:content:");
			if ((!meta.identityScope().equals(existingScope) && !legacyScope) ||
					!meta.type().equals(cursor.getString(1)) ||
					!meta.providerMetaId().equals(cursor.getString(2)) ||
					!equal(meta.canonicalIdentity(), cursor.isNull(3) ? null : cursor.getString(3))) {
				throw new IllegalStateException("Stremio meta key collision: " + meta.metaKey());
			}
		}
	}

	private static void assertUntainted(StremioMetaRecord meta) {
		StremioDurableTextPolicy.requireUntainted("metadata",
				meta.identityScope(), meta.type(), meta.providerMetaId(), meta.canonicalIdentity(),
				meta.name(), meta.description(), meta.posterUrl(), meta.backgroundUrl(), meta.logoUrl(),
				meta.releaseInfo(), meta.genresJson());
	}

	private static boolean equal(String first, String second) {
		return (first == null) ? (second == null) : first.equals(second);
	}
}
