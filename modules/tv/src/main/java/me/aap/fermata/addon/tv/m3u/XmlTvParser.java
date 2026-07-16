package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.addon.tv.m3u.XmlTvChannelMatcher.normalizeName;
import static me.aap.fermata.addon.tv.m3u.XmlTvSchema.COL_CH_ID;
import static me.aap.fermata.addon.tv.m3u.XmlTvSchema.IDX_PROG_CH;
import static me.aap.fermata.addon.tv.m3u.XmlTvSchema.TABLE_CH;
import static me.aap.fermata.addon.tv.m3u.XmlTvSchema.TABLE_NAME_TO_ICON;
import static me.aap.fermata.addon.tv.m3u.XmlTvSchema.TABLE_NAME_TO_ID;
import static me.aap.fermata.addon.tv.m3u.XmlTvSchema.TABLE_PROG;
import static me.aap.utils.collection.CollectionUtils.compute;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import me.aap.utils.log.Log;

final class XmlTvParser {
	void parse(InputStream in, SQLiteDatabase database,
					 Map<String, List<TvM3uTrackItem>> byId,
					 Map<String, List<TvM3uTrackItem>> byName, float epgShift)
			throws ParserConfigurationException, SAXException, IOException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		SAXParser parser = factory.newSAXParser();
		XmlTvSchema.rebuild(database);
		database.beginTransaction();
		try {
			parser.parse(in, new Handler(database, byId, byName, epgShift));
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	private static final class Handler extends DefaultHandler {
		private final SimpleDateFormat timeFormat =
				new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault());
		private final long currentTime = System.currentTimeMillis();
		private final SQLiteDatabase database;
		private final Map<String, List<TvM3uTrackItem>> byId;
		private final Map<String, List<TvM3uTrackItem>> byName;
		private final long epgShift;
		private final Map<String, ChannelInfo> channels;
		private final Map<String, InfoIcon> channelNames;
		private final String localLanguage = Locale.getDefault().getLanguage();
		private final SQLiteStatement channelStatement;
		private final SQLiteStatement programmeStatement;
		private final SQLiteStatement nameToIdStatement;
		private final SQLiteStatement nameToIconStatement;
		private final Set<String> names = new HashSet<>();
		private final StringBuilder text = new StringBuilder(1024);
		private String epgId;
		private String icon;
		private String start;
		private String stop;
		private String title;
		private String alternateTitle;
		private String description;
		private String alternateDescription;
		private Tag tag = Tag.IGNORE;
		private int counter;

		Handler(SQLiteDatabase database, Map<String, List<TvM3uTrackItem>> byId,
					Map<String, List<TvM3uTrackItem>> byName, float epgShift) {
			channelStatement = database.compileStatement(
					"INSERT INTO " + TABLE_CH + " VALUES(?, ?, ?)");
			programmeStatement = database.compileStatement(
					"INSERT INTO " + TABLE_PROG + " VALUES(?, ?, ?, ?, ?, ?)");
			nameToIdStatement = database.compileStatement(
					"INSERT INTO " + TABLE_NAME_TO_ID + " VALUES(?, ?)");
			nameToIconStatement = database.compileStatement(
					"INSERT INTO " + TABLE_NAME_TO_ICON + " VALUES(?, ?)");
			this.database = database;
			this.byId = byId;
			this.byName = byName;
			this.epgShift = (long) (60 * 60000 * epgShift);
			int capacity = byId.size() + byName.size();
			channels = new HashMap<>(capacity);
			channelNames = new HashMap<>(capacity);
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attrs) {
			switch (localName) {
				case "channel":
					tag = Tag.IGNORE;
					epgId = attrs.getValue("id");
					break;
				case "display-name":
					tag = Tag.DISPLAY_NAME;
					break;
				case "icon":
					tag = Tag.IGNORE;
					icon = attrs.getValue("src");
					break;
				case "programme":
					tag = Tag.IGNORE;
					epgId = attrs.getValue("channel");
					start = attrs.getValue("start");
					stop = attrs.getValue("stop");
					break;
				case "title":
					tag = localLanguage.equals(attrs.getValue("lang")) ? Tag.TITLE : Tag.TITLE_ALT;
					break;
				case "desc":
					tag = localLanguage.equals(attrs.getValue("lang")) ? Tag.DESC : Tag.DESC_ALT;
					break;
			}
		}

		@Override
		public void characters(char[] characters, int start, int length) {
			switch (tag) {
				case DISPLAY_NAME:
				case TITLE:
				case TITLE_ALT:
				case DESC:
				case DESC_ALT:
					text.append(characters, start, length);
					break;
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			switch (localName) {
				case "channel":
					addChannel();
					return;
				case "programme":
					addProgramme();
					return;
			}

			switch (tag) {
				case DISPLAY_NAME:
					names.add(normalizeName(text.toString().trim()));
					break;
				case TITLE:
					title = text.toString().trim();
					break;
				case TITLE_ALT:
					alternateTitle = text.toString().trim();
					break;
				case DESC:
					description = text.toString().trim();
					break;
				case DESC_ALT:
					alternateDescription = text.toString().trim();
					break;
			}

			tag = Tag.IGNORE;
			text.setLength(0);
		}

		@Override
		public void endDocument() {
			Log.d("Inserting ", channels.size(), " channels");
			for (Map.Entry<String, ChannelInfo> entry : channels.entrySet()) {
				try {
					ChannelInfo info = entry.getValue();
					channelStatement.clearBindings();
					channelStatement.bindLong(1, info.id);
					bindString(channelStatement, 2, entry.getKey());
					bindString(channelStatement, 3, info.icon);
					channelStatement.execute();
				} catch (Exception ex) {
					Log.e(ex, "Failed to insert channel: ", entry.getKey());
				}
			}

			Log.d("Inserting ", channelNames.size(), " channel names");
			for (Map.Entry<String, InfoIcon> entry : channelNames.entrySet()) {
				try {
					InfoIcon value = entry.getValue();
					nameToIdStatement.clearBindings();
					nameToIdStatement.bindString(1, entry.getKey());
					nameToIdStatement.bindLong(2, value.info.id);
					nameToIdStatement.execute();

					if ((value.icon != null) && !value.icon.equals(value.info.icon)) {
						nameToIconStatement.clearBindings();
						nameToIconStatement.bindString(1, entry.getKey());
						nameToIconStatement.bindString(2, value.icon);
						nameToIconStatement.execute();
					}
				} catch (Exception ex) {
					Log.e(ex, "Failed to insert channel name: ", entry.getKey());
				}
			}

			Log.i("Creating XMLTV index");
			database.execSQL("CREATE INDEX " + IDX_PROG_CH + " ON " + TABLE_PROG +
					'(' + COL_CH_ID + ");");
		}

		private void addChannel() {
			if (!isEmpty(epgId)) {
				List<TvM3uTrackItem> tracks = byId.get(epgId);
				ChannelInfo info = (tracks != null) ? createChannel(tracks) : null;

				for (String name : names) {
					tracks = byName.get(name);
					if (tracks == null) continue;
					if (info == null) info = createChannel(tracks);
					else info.addTracks(tracks);
					ChannelInfo channel = info;
					compute(channelNames, name, (key, value) -> {
						if (value == null) return new InfoIcon(channel, icon);
						if (value.icon == null) value.icon = icon;
						return value;
					});

					for (TvM3uTrackItem track : tracks) {
						if (!Objects.equals(icon, track.getEpgChIcon())) {
							track.update(track.getEpgId(), icon, track.getEpgStart(), track.getEpgStop(),
									track.getEpgTitle(), track.getEpgDesc(), track.getEpgProgIcon(), true);
						}
					}
				}
			}

			epgId = icon = null;
			names.clear();
		}

		private ChannelInfo createChannel(List<TvM3uTrackItem> tracks) {
			ChannelInfo info = compute(channels, epgId, (key, value) -> {
				if (value == null) return new ChannelInfo(counter++, icon);
				if (value.icon == null) value.icon = icon;
				return value;
			});
			assert info != null;
			info.addTracks(tracks);
			return info;
		}

		private void addProgramme() {
			ChannelInfo info;
			if (!isEmpty(epgId) && ((info = channels.get(epgId)) != null)) {
				long start = toTime(this.start);
				long stop = toTime(this.stop);
				String programmeTitle = (title != null) ? title : alternateTitle;
				String programmeDescription =
						(description != null) ? description : alternateDescription;

				try {
					programmeStatement.clearBindings();
					programmeStatement.bindLong(1, info.id);
					programmeStatement.bindLong(2, start);
					programmeStatement.bindLong(3, stop);
					bindString(programmeStatement, 4, programmeTitle);
					bindString(programmeStatement, 5, programmeDescription);
					bindString(programmeStatement, 6, icon);
					programmeStatement.execute();
				} catch (Exception ex) {
					Log.e(ex, "Failed to insert programme: ", epgId);
				}

				if ((start <= currentTime) && (stop > currentTime)) {
					for (TvM3uTrackItem track : info.tracks) {
						track.update(info.id, track.getEpgChIcon(), start, stop, programmeTitle,
								programmeDescription, icon, true);
					}
				}
			}

			epgId = start = stop = icon = title = alternateTitle = description =
					alternateDescription = null;
		}

		private void bindString(SQLiteStatement statement, int index, String value) {
			if (value == null) statement.bindNull(index);
			else statement.bindString(index, value);
		}

		private long toTime(String value) {
			if (value == null) return 0;
			try {
				Date date = timeFormat.parse(value);
				return (date != null) ? (date.getTime() + epgShift) : 0;
			} catch (ParseException ex) {
				Log.e(ex, "Failed to parse time: ", value);
				return 0;
			}
		}

		private boolean isEmpty(String value) {
			return (value == null) || value.isEmpty();
		}

		private enum Tag {
			IGNORE, DISPLAY_NAME, TITLE, TITLE_ALT, DESC, DESC_ALT
		}

		private static final class ChannelInfo {
			final Set<TvM3uTrackItem> tracks = new HashSet<>();
			final int id;
			String icon;

			ChannelInfo(int id, String icon) {
				this.id = id;
				this.icon = icon;
			}

			void addTracks(List<TvM3uTrackItem> tracks) {
				this.tracks.addAll(tracks);
				for (TvM3uTrackItem track : tracks) {
					if (track.getEpgId() < 0) {
						track.update(id, icon, track.getEpgStart(), track.getEpgStop(),
								track.getEpgTitle(), track.getEpgDesc(), track.getEpgProgIcon(), true);
					}
				}
			}
		}

		private static final class InfoIcon {
			final ChannelInfo info;
			String icon;

			InfoIcon(ChannelInfo info, String icon) {
				this.info = info;
				this.icon = icon;
			}
		}
	}
}
