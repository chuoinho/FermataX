package me.aap.fermata.ui.glass.theme;

import android.content.Context;

import me.aap.fermata.ui.activity.MainActivityPrefs;

/**
 * Immutable color tokens for the Glassmorphism Theme, supporting Day/Night adaptation
 * controlled strictly by user's manual appearance preference (G02/G03/G04).
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
	private final int actionText;
	private final int iconColor;
	private final int inputBg;
	private final int inputText;
	private final int inputHint;
	private final int inputBorder;
	private final int divider;
	private final int disabledText;
	private final int errorText;
	private final int surfaceBg;
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
			this.textSecondary = 0xFF94A3B8;
			this.textTertiary = 0xFF64748B;
			this.actionText = 0xFF00E5FF;
			this.iconColor = 0xFF00E5FF;
			this.inputBg = 0x26181C1E;
			this.inputText = 0xFFFFFFFF;
			this.inputHint = 0x66FFFFFF;
			this.inputBorder = 0x20FFFFFF;
			this.divider = 0x1AFFFFFF;
			this.disabledText = 0x4DFFFFFF;
			this.errorText = 0xFFFF5252;
			this.surfaceBg = 0xFF0A0F1D;
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
			this.textSecondary = 0xFF475569;
			this.textTertiary = 0xFF64748B;
			this.actionText = 0xFF007A87;
			this.iconColor = 0xFF007A87;
			this.inputBg = 0xFFFFFFFF;
			this.inputText = 0xFF15181A;
			this.inputHint = 0x6615181A;
			this.inputBorder = 0x20000000;
			this.divider = 0x1A000000;
			this.disabledText = 0x4D15181A;
			this.errorText = 0xFFD32F2F;
			this.surfaceBg = 0xFFEBEAE8;
			this.nenA = 0xFFEBEAE8;
			this.nenB = 0xFFF2F1EF;
			this.nenC = 0xFFE0DFDC;
		}
	}

	public static boolean isNight(Context context) {
		try {
			return MainActivityPrefs.get().isGlassNightMode();
		} catch (Throwable e) {
			return true;
		}
	}

	public static GlassThemeColors get(Context context) {
		return new GlassThemeColors(isNight(context));
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

	public int getActionText() {
		return actionText;
	}

	public int getIconColor() {
		return iconColor;
	}

	public int getInputBg() {
		return inputBg;
	}

	public int getInputText() {
		return inputText;
	}

	public int getInputHint() {
		return inputHint;
	}

	public int getInputBorder() {
		return inputBorder;
	}

	public int getDivider() {
		return divider;
	}

	public int getDisabledText() {
		return disabledText;
	}

	public int getErrorText() {
		return errorText;
	}

	public int getSurfaceBg() {
		return surfaceBg;
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
