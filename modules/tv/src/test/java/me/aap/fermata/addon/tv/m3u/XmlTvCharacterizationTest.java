package me.aap.fermata.addon.tv.m3u;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class XmlTvCharacterizationTest {
	private SQLiteDatabase database;

	@Before
	public void createDatabase() {
		database = SQLiteDatabase.create(null);
	}

	@After
	public void closeDatabase() {
		database.close();
	}

	@Test
	public void schemaRebuildCreatesTablesBeforeProgrammeIndex() throws Exception {
		XmlTvSchema.rebuild(database);

		assertEquals(1, schemaObjectCount("table", "Channels"));
		assertEquals(1, schemaObjectCount("table", "Prog"));
		assertEquals(1, schemaObjectCount("table", "NameToId"));
		assertEquals(1, schemaObjectCount("table", "NameToIcon"));
		assertFalse(XmlTvSchema.hasProgrammeIndex(database));

		database.execSQL("CREATE INDEX ProgChIdx ON Prog(ChId)");
		assertTrue(XmlTvSchema.hasProgrammeIndex(database));
		XmlTvSchema.rebuild(database);
		assertFalse(XmlTvSchema.hasProgrammeIndex(database));
	}

	@Test
	public void channelNameMatchingIsCaseAndAccentInsensitive() {
		assertEquals("đai truyen hinh", XmlTvChannelMatcher.normalizeName("ĐÀI Truyền Hình"));
		assertEquals("cafe tv", XmlTvChannelMatcher.normalizeName("Café TV"));
	}

	@Test
	public void parserIgnoresChannelsOutsidePlaylistAndCompletesIndex() throws Exception {
		Map<String, List<TvM3uTrackItem>> byId = new HashMap<>();
		Map<String, List<TvM3uTrackItem>> byName = new HashMap<>();
		String xml = """
				<tv xmlns="urn:xmltv">
				  <channel id="outside"><display-name>Outside</display-name></channel>
				  <programme channel="outside" start="20300101000000 +0000"
				      stop="20300101010000 +0000"><title>Ignored</title></programme>
				</tv>
				""";

		new XmlTvParser().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
				database, byId, byName, 0F);

		assertEquals(0, rowCount("Channels"));
		assertEquals(0, rowCount("Prog"));
		assertTrue(XmlTvSchema.hasProgrammeIndex(database));
	}

	private int schemaObjectCount(String type, String name) {
		try (Cursor cursor = database.rawQuery(
				"SELECT count(*) FROM sqlite_master WHERE type=? AND name=?",
				new String[]{type, name})) {
			assertTrue(cursor.moveToFirst());
			return cursor.getInt(0);
		}
	}

	private int rowCount(String table) {
		try (Cursor cursor = database.rawQuery("SELECT count(*) FROM " + table, null)) {
			assertTrue(cursor.moveToFirst());
			return cursor.getInt(0);
		}
	}

}
