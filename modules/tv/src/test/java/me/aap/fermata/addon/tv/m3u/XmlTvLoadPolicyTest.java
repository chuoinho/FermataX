package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.addon.tv.m3u.TvM3uFile.EPG_FILE_AGE;
import static me.aap.fermata.addon.tv.m3u.XmlTvLoadPolicy.DownloadAction.PARSE_AFTER_DELAY;
import static me.aap.fermata.addon.tv.m3u.XmlTvLoadPolicy.DownloadAction.PARSE_NOW;
import static me.aap.fermata.addon.tv.m3u.XmlTvLoadPolicy.DownloadAction.USE_EXISTING;
import static me.aap.fermata.addon.tv.m3u.XmlTvLoadPolicy.FailureAction.CLOSE;
import static me.aap.fermata.addon.tv.m3u.XmlTvLoadPolicy.FailureAction.RETRY;
import static me.aap.fermata.addon.tv.m3u.XmlTvLoadPolicy.StartupAction.REFRESH_IN_BACKGROUND;
import static me.aap.fermata.addon.tv.m3u.XmlTvLoadPolicy.StartupAction.WAIT_FOR_INITIAL_LOAD;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XmlTvLoadPolicyTest {
	@Test
	public void startupReturnsExistingIndexAndWaitsForInitialDatabase() {
		assertEquals(REFRESH_IN_BACKGROUND, XmlTvLoadPolicy.resolveStartup(true));
		assertEquals(WAIT_FOR_INITIAL_LOAD, XmlTvLoadPolicy.resolveStartup(false));
	}

	@Test
	public void indexedDatabaseUsesExistingDataWhenDownloadIsUnchanged() {
		assertEquals(USE_EXISTING, XmlTvLoadPolicy.resolveDownload(true, 0L));
	}

	@Test
	public void indexedDatabaseDelaysDownloadedReplacement() {
		assertEquals(PARSE_AFTER_DELAY, XmlTvLoadPolicy.resolveDownload(true, 1L));
		assertEquals(30_000L, XmlTvLoadPolicy.REPLACEMENT_DELAY_MS);
	}

	@Test
	public void initialDatabaseWaitsForImmediateParse() {
		assertEquals(PARSE_NOW, XmlTvLoadPolicy.resolveDownload(false, 0L));
		assertEquals(PARSE_NOW, XmlTvLoadPolicy.resolveDownload(false, 1L));
	}

	@Test
	public void existingDatabaseRetriesButInitialFailureCloses() {
		assertEquals(RETRY, XmlTvLoadPolicy.resolveFailure(true));
		assertEquals(CLOSE, XmlTvLoadPolicy.resolveFailure(false));
		assertEquals(5 * 60_000L, XmlTvLoadPolicy.RETRY_DELAY_MS);
	}

	@Test
	public void validTimestampAndMaxAgeDetermineNextUpdate() {
		long now = 1_000_000L;
		assertEquals(1_120_000L, XmlTvLoadPolicy.nextUpdateTime(1_000_000L, 120, now));
	}

	@Test
	public void missingOrExpiredMetadataFallsBackToDefaultAgeFromNow() {
		long now = 1_000_000L;
		long fallback = now + EPG_FILE_AGE * 1000L;
		assertEquals(fallback, XmlTvLoadPolicy.nextUpdateTime(0L, 0, now));
		assertEquals(fallback, XmlTvLoadPolicy.nextUpdateTime(1L, 1, now));
	}
}
