package me.aap.fermata.ui.glass.theme;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Immutable color tokens for the Glassmorphism Theme, supporting Day/Night adaptation
 * based on automotive headlight/sensor signals. Adapted from CoDienMau.
 */
public final class GlassThemeColors {
	private final boolean night;
	private final int cardBg;
	private final int cardBg2;
	private final int cardBorder;
	private final int chipBg;
	private final int chipBorder;
	private final int textPrimary;
	private final int textSecondary;
	private final int textTertiary;
	private final int nenA;
	private final int nenB;
	private final int nenC;

	public static final int ACCENT_CYAN = 0xFF00E5FF;
	public static final int ACCENT_BLUE = 0xFF2979FF;
	public static final int ACCENT_GREEN = 0xFF00E676;
	public static final int ACCENT_RED = 0xFFFF1744;
	public static final int ACCENT_YELLOW = 0xFFFFD600;

	private GlassThemeColors(boolean night) {
		this.night = night;
		if (night) {
			this.cardBg = 0x1A181C1E;
			this.cardBg2 = 0x26181C1E;
			this.cardBorder = 0x18FFFFFF;
			this.chipBg = 0x24FFFFFF;
			this.chipBorder = 0x20FFFFFF;
			this.textPrimary = 0xFFFFFFFF;
			this.textSecondary = 0x99FFFFFF;
			this.textTertiary = 0x66FFFFFF;
			this.nenA = 0xFF15181A;
			this.nenB = 0xFF0E1113;
			this.nenC = 0xFF0A0C0E;
		} else {
			this.cardBg = 0xE6F2F1EF;
			this.cardBg2 = 0xFAF2F1EF;
			this.cardBorder = 0x14000000;
			this.chipBg = 0x14000000;
			this.chipBorder = 0x10000000;
			this.textPrimary = 0xFF15181A;
			this.textSecondary = 0x9915181A;
			this.textTertiary = 0x6615181A;
			this.nenA = 0xFFEBEAE8;
			this.nenB = 0xFFF2F1EF;
			this.nenC = 0xFFE0DFDC;
		}
	}

	public static GlassThemeColors get(Context context) {
		boolean isNight = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
		return new GlassThemeColors(isNight);
	}

	public static GlassThemeColors get(Context context, boolean isNight) {
		return new GlassThemeColors(isNight);
	}

	public boolean isNight() {
		return night;
	}

	public int getCardBg() {
		return cardBg;
	}

	public int getCardBg2() {
		return cardBg2;
	}

	public int getCardBorder() {
		return cardBorder;
	}

	public int getChipBg() {
		return chipBg;
	}

	public int getChipBorder() {
		return chipBorder;
	}

	public int getTextPrimary() {
		return textPrimary;
	}

	public int getTextSecondary() {
		return textSecondary;
	}

	public int getTextTertiary() {
		return textTertiary;
	}

	public int getNenA() {
		return nenA;
	}

	public int getNenB() {
		return nenB;
	}

	public int getNenC() {
		return nenC;
	}
}
