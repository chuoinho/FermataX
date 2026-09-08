package me.aap.fermata.ui.view;

import java.util.Locale;

/** Display-only conversions for the legacy integer effect strengths. */
final class AudioEffectsDisplayUnits {
	private AudioEffectsDisplayUnits() {
	}

	static String formatBassStrength(int raw) {
		return format(raw / 10D, 1) + "%";
	}

	static int parseBassStrength(String displayed) {
		return parse(displayed, 0D, 100D, 10D);
	}

	static String bassInput(int raw) {
		return format(raw / 10D, 1);
	}

	static String formatLoudnessGain(int raw) {
		return format(raw / 100D, 2) + " dB";
	}

	static int parseLoudnessGain(String displayed) {
		return parse(displayed, 0D, 10D, 100D);
	}

	static String loudnessInput(int raw) {
		return format(raw / 100D, 2);
	}

	private static int parse(String displayed, double min, double max, double scale) {
		double value = Double.parseDouble(displayed.trim().replace(',', '.'));
		if (!Double.isFinite(value) || (value < min) || (value > max)) {
			throw new NumberFormatException(displayed);
		}
		return (int) Math.round(value * scale);
	}

	private static String format(double value, int decimalPlaces) {
		String text = String.format(Locale.US, "%." + decimalPlaces + "f", value);
		while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
		if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
		return text;
	}
}
