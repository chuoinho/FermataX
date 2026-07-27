package me.aap.fermata.addon.stremio.data;

import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.List;

final class StremioDatabase {
	private StremioDatabase() {
	}

	static SQLiteDatabase open(File file) {
		return open(file, StremioSchema.CURRENT_VERSION, StremioSchema.migrations());
	}

	static SQLiteDatabase open(File file, int targetVersion,
			List<StremioSchema.Migration> migrations) {
		File parent = file.getParentFile();
		if ((parent != null) && !parent.isDirectory() && !parent.mkdirs()) {
			throw new IllegalStateException("Failed to create Stremio database directory");
		}

		SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(file, null);
		try {
			database.setForeignKeyConstraintsEnabled(true);
			StremioSchema.migrate(database, targetVersion, migrations);
			return database;
		} catch (Throwable error) {
			database.close();
			throw error;
		}
	}
}
