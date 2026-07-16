package me.aap.fermata.addon.tv.m3u;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import me.aap.utils.log.Log;

final class XmlTvSchema {
	static final String TABLE_CH = "Channels";
	static final String TABLE_PROG = "Prog";
	static final String TABLE_NAME_TO_ID = "NameToId";
	static final String TABLE_NAME_TO_ICON = "NameToIcon";
	static final String IDX_PROG_CH = "ProgChIdx";
	static final String COL_ID = "Id";
	static final String COL_EPG_ID = "EpgId";
	static final String COL_NAME = "Name";
	static final String COL_ICON = "Icon";
	static final String COL_CH_ID = "ChId";
	static final String COL_START = "Start";
	static final String COL_STOP = "Stop";
	static final String COL_TITLE = "Title";
	static final String COL_DSC = "Dsc";
	static final String[] Q_COL_ID_ICON = new String[]{COL_ID, COL_ICON};
	static final String[] Q_COL_CH_ID = new String[]{COL_CH_ID};
	static final String[] Q_COL_ICON = new String[]{COL_ICON};
	static final String[] Q_COL_EPG = new String[]{COL_START, COL_STOP, COL_TITLE, COL_DSC, COL_ICON};
	static final String Q_SEL_ID = COL_ID + " = ?";
	static final String Q_SEL_EPG_ID = COL_EPG_ID + " = ?";
	static final String Q_SEL_NAME = COL_NAME + " = ?";
	static final String Q_SEL_NAME_OR = COL_NAME + " = ? or " + COL_NAME + " = ?";
	static final String Q_SEL_CH_ID = COL_CH_ID + " = ? ";
	static final String Q_SEL_CH_ID_TIME = COL_CH_ID + " = ? AND " +
			COL_START + " <= ? AND " + COL_STOP + " > ?";

	private XmlTvSchema() {
	}

	static void rebuild(SQLiteDatabase database) {
		database.execSQL("DROP INDEX IF EXISTS " + IDX_PROG_CH);
		database.execSQL("DROP TABLE IF EXISTS " + TABLE_CH);
		database.execSQL("DROP TABLE IF EXISTS " + TABLE_PROG);
		database.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_TO_ID);
		database.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_TO_ICON);

		database.execSQL("CREATE TABLE " + TABLE_CH + '(' +
				COL_ID + " INTEGER PRIMARY KEY, " +
				COL_EPG_ID + " VARCHAR UNIQUE, " +
				COL_ICON + " VARCHAR" +
				");");
		database.execSQL("CREATE TABLE " + TABLE_PROG + '(' +
				COL_CH_ID + " INTEGER, " +
				COL_START + " INTEGER, " +
				COL_STOP + " INTEGER, " +
				COL_TITLE + " VARCHAR, " +
				COL_DSC + " VARCHAR, " +
				COL_ICON + " VARCHAR" +
				");");
		database.execSQL("CREATE TABLE " + TABLE_NAME_TO_ID + '(' +
				COL_NAME + " VARCHAR PRIMARY KEY, " +
				COL_CH_ID + " INTEGER" +
				");");
		database.execSQL("CREATE TABLE " + TABLE_NAME_TO_ICON + '(' +
				COL_NAME + " VARCHAR PRIMARY KEY, " +
				COL_ICON + " VARCHAR NOT NULL" +
				");");
	}

	static boolean hasProgrammeIndex(SQLiteDatabase database) {
		try (Cursor cursor = database.rawQuery(
				"SELECT count(*) FROM sqlite_master WHERE type='index' AND name=?;",
				new String[]{IDX_PROG_CH})) {
			return cursor.moveToFirst() && (cursor.getInt(0) != 0);
		} catch (Throwable ex) {
			Log.d(ex, "Failed to get index");
			return false;
		}
	}
}
