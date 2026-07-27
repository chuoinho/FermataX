package me.aap.fermata.addon.stremio.ui;

/** Pure responsive sizing policy for Stremio poster surfaces. */
public final class StremioLayoutPolicy {
	static final int MIN_COLUMNS = 1;
	static final int DEFAULT_COLUMNS = 2;
	static final int MAX_COLUMNS = 16;
	static final int MIN_POSTER_WIDTH_DP = 118;
	static final int POSTER_GAP_DP = 8;
	static final int DEFAULT_SHELF_POSTER_WIDTH_DP = 128;
	static final int MIN_SHELF_POSTER_WIDTH_DP = 112;
	static final int MAX_SHELF_POSTER_WIDTH_DP = 152;

	private StremioLayoutPolicy() {
	}

	public static int posterColumns(int availableWidthDp) {
		if (availableWidthDp <= 0) return DEFAULT_COLUMNS;
		int columns = (availableWidthDp + POSTER_GAP_DP) /
				(MIN_POSTER_WIDTH_DP + POSTER_GAP_DP);
		return Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, columns));
	}

	/** Keeps Home shelves readable while exposing enough of the next card to signal scrolling. */
	public static int shelfPosterWidthDp(int availableWidthDp) {
		if (availableWidthDp <= 0) return DEFAULT_SHELF_POSTER_WIDTH_DP;
		int visible = (availableWidthDp < 400) ? 2 :
				(availableWidthDp < 640) ? 3 : (availableWidthDp < 900) ? 4 : 5;
		int width = (availableWidthDp - ((visible + 1) * POSTER_GAP_DP)) / visible;
		return Math.max(MIN_SHELF_POSTER_WIDTH_DP,
				Math.min(MAX_SHELF_POSTER_WIDTH_DP, width));
	}

	/** Poster image (2:3) plus a centered two-line title and item spacing. */
	public static int shelfHeightDp(int posterWidthDp) {
		int width = (posterWidthDp <= 0) ? DEFAULT_SHELF_POSTER_WIDTH_DP : posterWidthDp;
		int imageWidth = Math.max(width - POSTER_GAP_DP, 1);
		return Math.round(imageWidth * 1.5f) + 60;
	}

	public static int detailsPosterWidthDp(int availableWidthDp) {
		if ((availableWidthDp > 0) && (availableWidthDp < 360)) return 88;
		if (availableWidthDp < 640) return 104;
		if (availableWidthDp < 900) return 116;
		return 128;
	}

	public static int detailsBackdropHeightDp(int availableWidthDp) {
		if ((availableWidthDp > 0) && (availableWidthDp < 360)) return 160;
		if (availableWidthDp < 640) return 176;
		if (availableWidthDp < 900) return 192;
		return 208;
	}
}
