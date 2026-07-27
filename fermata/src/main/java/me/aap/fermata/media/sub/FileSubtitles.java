package me.aap.fermata.media.sub;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import me.aap.utils.io.Utf8LineReader;
import me.aap.utils.vfs.VirtualFile;

/**
 * @author Andrey Pavlenko
 */
	public class FileSubtitles {
		private static final Pattern FULL_TIME_PATTERN = Pattern.compile(
				"(\\d{2}):(\\d{2}):(\\d{2})[.,](\\d{3})\\s*-->\\s*"
						+ "(\\d{2}):(\\d{2}):(\\d{2})[.,](\\d{3}).*");
		private static final Pattern SHORT_TIME_PATTERN = Pattern.compile(
				"(\\d{1,3}):(\\d{2})[.,](\\d{3})\\s*-->\\s*"
						+ "(\\d{1,3}):(\\d{2})[.,](\\d{3}).*");

	public enum Type {
		SRT, VTT;

		private static final List<String> extensions;

		static {
			var values = values();
			var ext = new ArrayList<String>(values.length);
			for (var v : values) ext.add(v.fileExt);
			extensions = Collections.unmodifiableList(ext);
		}

		private static final Type[] values = values();
		private final String fileExt = '.' + name().toLowerCase();
	}

	public static List<String> getSupportedFileExtensions() {
		return Type.extensions;
	}

	public static boolean isSupported(String fileName) {
		return getType(fileName) != null;
	}

	@Nullable
	public static Type getType(String fileName) {
		for (var t : Type.values)
			if (fileName.endsWith(t.fileExt)) return t;
		return null;
	}

	public static SubGrid load(VirtualFile file) throws IOException {
		var t = getType(file.getName());
		if (t == null) throw new UnsupportedOperationException();
		return load(file.getInputStream().asInputStream());
	}

	public static SubGrid load(InputStream in) throws IOException {
		try (var r = new Utf8LineReader(in)) {
			var sb = new StringBuilder();
			var sgb = new SubGrid.Builder();
			var positions = SubGrid.Position.values();
			long time = -1;
			int dur = -1;

			for (; ; ) {
				int n = r.readLine(sb);

				if ((n == 0) || (n == -1)) {
					if (time != -1) {
						String text;
						var pos = SubGrid.Position.BOTTOM_CENTER;

						if ((sb.length() > 5) && (sb.charAt(0) == '{') && (sb.charAt(1) == '\\') &&
								(sb.charAt(2) == 'a') && (sb.charAt(3) == 'n') && (sb.charAt(5) == '}')) {
							int id = sb.charAt(4) - '0';
							if (id > 0 && id < 10) pos = positions[id - 1];
							text = sb.substring(6);
						} else {
							text = sb.toString();
						}
						sgb.add(text.trim(), time, dur, pos);
						time = -1;
					}

					if (n == -1) return sgb.build();
					sb.setLength(0);
				} else if (time == -1) {
					long[] cue = parseCue(sb);
					if (cue != null) {
						time = cue[0];
						dur = (int) (cue[1] - time);
					}
					sb.setLength(0);
				} else {
					sb.append('\n');
				}
			}
		}
	}

	private static long[] parseCue(CharSequence line) {
		var full = FULL_TIME_PATTERN.matcher(line);
		if (full.matches()) {
			return new long[] {
					toMillis(full.group(1), full.group(2), full.group(3), full.group(4)),
					toMillis(full.group(5), full.group(6), full.group(7), full.group(8))
			};
		}
		var shortTime = SHORT_TIME_PATTERN.matcher(line);
		if (!shortTime.matches()) return null;
		return new long[] {
				toMillis("00", shortTime.group(1), shortTime.group(2), shortTime.group(3)),
				toMillis("00", shortTime.group(4), shortTime.group(5), shortTime.group(6))
		};
	}

	private static long toMillis(String h, String m, String s, String f) {
		return Long.parseLong(h) * 60 * 60000 + Long.parseLong(m) * 60000 + Long.parseLong(s) * 1000 +
				Long.parseLong(f);
	}
}
