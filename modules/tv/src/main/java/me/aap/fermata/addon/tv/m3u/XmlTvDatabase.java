package me.aap.fermata.addon.tv.m3u;

import static java.util.Collections.emptyList;
import static me.aap.fermata.addon.tv.m3u.TvM3uTrackItem.EPG_ID_NOT_FOUND;
import static me.aap.fermata.addon.tv.m3u.TvM3uTrackItem.EPG_ID_UNKNOWN;
import static me.aap.fermata.addon.tv.m3u.XmlTvChannelMatcher.normalizeName;
import static me.aap.fermata.addon.tv.m3u.XmlTvSchema.*;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.utils.log.Log;

final class XmlTvDatabase {
	private XmlTvDatabase() {
	}

	static int updateTrack(SQLiteDatabase database, TvM3uTrackItem track) {
		int id = track.getEpgId();
		String icon = track.getEpgChIcon();

		if (id == EPG_ID_UNKNOWN) {
			String tvgId = track.getTvgId();
			String tvgName = track.getTvgName();
			String name = normalizeName(track.getName());
			String nameSelection;
			String[] nameArgs;

			if (tvgName == null) {
				nameSelection = Q_SEL_NAME;
				nameArgs = new String[]{name};
			} else {
				nameSelection = Q_SEL_NAME_OR;
				nameArgs = new String[]{normalizeName(tvgName), name};
			}

			if (tvgId != null) {
				try (Cursor cursor = database.query(TABLE_CH, Q_COL_ID_ICON, Q_SEL_EPG_ID,
						new String[]{tvgId}, null, null, null)) {
					if (cursor.moveToFirst()) {
						id = cursor.getInt(0);
						icon = cursor.getString(1);
					}
				}

				if (id != EPG_ID_UNKNOWN) {
					try (Cursor cursor = database.query(TABLE_NAME_TO_ICON, Q_COL_ICON,
							nameSelection, nameArgs, null, null, null)) {
						if (cursor.moveToFirst()) icon = cursor.getString(0);
					}
				}
			}

			if (id == EPG_ID_UNKNOWN) {
				try (Cursor cursor = database.query(TABLE_NAME_TO_ID, Q_COL_CH_ID,
						nameSelection, nameArgs, null, null, null)) {
					if (cursor.moveToFirst()) id = cursor.getInt(0);
				}

				if (id != EPG_ID_UNKNOWN) {
					try (Cursor cursor = database.query(TABLE_NAME_TO_ICON, Q_COL_ICON,
							nameSelection, nameArgs, null, null, null)) {
						if (cursor.moveToFirst()) icon = cursor.getString(0);
					}

					if (icon == null) {
						try (Cursor cursor = database.query(TABLE_CH, Q_COL_ICON, Q_SEL_ID,
								new String[]{String.valueOf(id)}, null, null, null)) {
							if (cursor.moveToFirst()) icon = cursor.getString(0);
						}
					}
				}
			}
		}

		if (id == EPG_ID_UNKNOWN) {
			Log.d("Channel not found: ", track.getName());
			track.update(EPG_ID_NOT_FOUND, null, 0, 0, null, null, null, false);
			return EPG_ID_UNKNOWN;
		}

		String time = String.valueOf(System.currentTimeMillis());
		try (Cursor cursor = database.query(TABLE_PROG, Q_COL_EPG, Q_SEL_CH_ID_TIME,
				new String[]{String.valueOf(id), time, time}, null, null, null)) {
			if (cursor.moveToFirst()) {
				track.update(id, icon, cursor.getLong(0), cursor.getLong(1), cursor.getString(2),
						cursor.getString(3), cursor.getString(4), false);
			} else {
				track.update(id, icon, 0, 0, null, null, null, false);
			}
		}

		return id;
	}

	static List<TvM3uEpgItem> getEpg(SQLiteDatabase database, TvM3uTrackItem track) {
		int id = track.getEpgId();
		if (id == EPG_ID_UNKNOWN) {
			id = updateTrack(database, track);
			if (id == EPG_ID_UNKNOWN) return emptyList();
		}

		try (Cursor cursor = database.query(TABLE_PROG, Q_COL_EPG, Q_SEL_CH_ID,
				new String[]{String.valueOf(id)}, null, null, null)) {
			int count = cursor.getCount();
			if (count == 0) return emptyList();
			List<TvM3uEpgItem> epg = new ArrayList<>(count);
			while (cursor.moveToNext()) {
				epg.add(TvM3uEpgItem.create(track, cursor.getLong(0), cursor.getLong(1),
						cursor.getString(2), cursor.getString(3), cursor.getString(4)));
			}
			if (epg.isEmpty()) return emptyList();
			Collections.sort(epg);
			for (int i = 1, size = epg.size(); i < size; i++) {
				TvM3uEpgItem current = epg.get(i);
				TvM3uEpgItem previous = epg.get(i - 1);
				previous.setNext(current);
				current.setPrev(previous);
			}
			epg.get(0).setPrev(null);
			epg.get(epg.size() - 1).setNext(null);
			return epg;
		}
	}
}
