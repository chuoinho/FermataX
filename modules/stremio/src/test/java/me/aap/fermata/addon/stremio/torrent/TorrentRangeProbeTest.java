package me.aap.fermata.addon.stremio.torrent;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class TorrentRangeProbeTest {
	@Test
	public void selectsDeterministicHeadAndTailPiecesWithoutDuplicates() {
		TorrentRangeProbe.Plan plan = TorrentRangeProbe.plan(
				0L, 10L * 1024L * 1024L, 1024 * 1024, 10,
				1024L * 1024L, 512L * 1024L);
		assertArrayEquals(new int[]{0, 9}, plan.pieces());
		assertEquals(2L * 1024L * 1024L, plan.targetBytes());
	}

	@Test
	public void smallFileUsesOneSharedPiece() {
		TorrentRangeProbe.Plan plan = TorrentRangeProbe.plan(
				128L, 256L, 1024, 1, 1024L, 512L);
		assertArrayEquals(new int[]{0}, plan.pieces());
		assertEquals(1024L, plan.targetBytes());
	}

	@Test
	public void rejectsInvalidGeometry() {
		assertThrows(IllegalArgumentException.class,
				() -> TorrentRangeProbe.plan(0L, 0L, 1024, 1, 1L, 1L));
		assertThrows(IllegalArgumentException.class,
				() -> TorrentRangeProbe.plan(0L, 1L, 0, 1, 1L, 1L));
	}
}
