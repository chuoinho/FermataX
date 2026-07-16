package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.text.TextUtils.isNullOrBlank;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author Andrey Pavlenko
 */
public class XtreamEpgProgram {
	private static final String[] TIME_PATTERNS = {
			"yyyy-MM-dd HH:mm:ss",
			"yyyy-MM-dd'T'HH:mm:ss",
			"yyyy-MM-dd'T'HH:mm:ss'Z'"
	};
	private final long startTime;
	private final long endTime;
	private final String title;
	private final String description;
	private final String icon;
	private final boolean archive;

	public XtreamEpgProgram(long startTime, long endTime, String title, String description,
													@Nullable String icon, boolean archive) {
		this.startTime = startTime;
		this.endTime = endTime;
		this.title = isNullOrBlank(title) ? "EPG" : title;
		this.description = description;
		this.icon = icon;
		this.archive = archive;
	}

	public long getStartTime() {
		return startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	public String getTitle() {
		return title;
	}

	@Nullable
	public String getDescription() {
		return description;
	}

	@Nullable
	public String getIcon() {
		return icon;
	}

	public boolean hasArchive() {
		return archive;
	}

	public boolean isValid() {
		return (startTime > 0) && (endTime > startTime);
	}

	public static long parseTime(@Nullable String value) {
		if (isNullOrBlank(value)) return 0;
		String v = value.trim();

		try {
			double epoch = Double.parseDouble(v);
			if (epoch > 100000000000L) return (long) epoch;
			if (epoch > 0) return (long) (epoch * 1000L);
		} catch (NumberFormatException ignore) {
		}

		for (String pattern : TIME_PATTERNS) {
			try {
				SimpleDateFormat df = new SimpleDateFormat(pattern, Locale.US);
				df.setTimeZone(TimeZone.getTimeZone("UTC"));
				Date date = df.parse(v);
				if (date != null) return date.getTime();
			} catch (ParseException ignore) {
			}
		}

		return 0;
	}

	@Nullable
	public static String decodeText(@Nullable String value) {
		if (isNullOrBlank(value)) return value;
		String v = value.trim();

		try {
			byte[] bytes = Base64.getDecoder().decode(v);
			String decoded = new String(bytes, StandardCharsets.UTF_8).trim();
			return (decoded.isEmpty() || !isReadable(decoded)) ? value : decoded;
		} catch (RuntimeException ignore) {
			return value;
		}
	}

	private static boolean isReadable(String value) {
		for (int i = 0, len = value.length(); i < len; i++) {
			char c = value.charAt(i);
			if ((c == '\uFFFD') || (Character.isISOControl(c) && !Character.isWhitespace(c))) {
				return false;
			}
		}

		return true;
	}
}
