package me.aap.fermata.ui.voice;

import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small deterministic grammar for common car commands. It deliberately does not use an LLM
 * and returns null when the utterance is ambiguous or outside the supported grammar.
 */
public final class VoiceIntentParser {
	private static final Map<String, String> ADDON_ALIASES = new LinkedHashMap<>();

	static {
		alias("youtube", "youtube", "you tube", "yt");
		alias("tv", "tv", "television", "truyền hình", "truyen hinh");
		alias("radio", "radio", "đài", "dai");
		alias("podcast", "podcast", "podcasts");
		alias("audiobook", "audiobook", "audiobooks", "sách nói", "sach noi");
		alias("web", "web", "browser", "trình duyệt", "trinh duyet");
		alias("chatgpt", "chatgpt", "chat gpt", "gpt", "assistant", "trợ lý", "tro ly");
	}

	private VoiceIntentParser() {
	}

	private static void alias(String canonical, String... values) {
		for (String value : values) ADDON_ALIASES.put(value, canonical);
	}

	@Nullable
	public static VoiceIntent parse(@Nullable String phrase, @Nullable Locale locale) {
		String text = normalize(phrase);
		if (text.isEmpty()) return null;

		if (isAny(text, "play", "phát", "phat", "resume", "tiếp tục", "tiep tuc"))
			return VoiceIntent.playback(VoiceIntent.PlaybackAction.PLAY);
		if (isAny(text, "pause", "tạm dừng", "tam dung"))
			return VoiceIntent.playback(VoiceIntent.PlaybackAction.PAUSE);
		if (isAny(text, "stop", "dừng", "dung"))
			return VoiceIntent.playback(VoiceIntent.PlaybackAction.STOP);
		if (isAny(text, "what is playing", "now playing", "đang phát gì", "dang phat gi"))
			return VoiceIntent.playback(VoiceIntent.PlaybackAction.OPEN_CURRENT);
		if (isAny(text, "play favorites", "play my favorites", "phát mục yêu thích",
				"phat muc yeu thich"))
			return VoiceIntent.playback(VoiceIntent.PlaybackAction.PLAY_FAVORITES);

		int selection = parseSelectionIndex(text);
		if (selection >= 0) return VoiceIntent.selection(selection);

		VoiceIntent.SearchAction action = parseSearchAction(text);
		if (action == null) return null;
		String remainder = removeAction(text, action);
		if (remainder.isEmpty()) return null;

		String addon = null;
		for (Map.Entry<String, String> e : ADDON_ALIASES.entrySet()) {
			String alias = e.getKey();
			if (remainder.equals(alias) || remainder.startsWith(alias + " ")) {
				addon = e.getValue();
				remainder = remainder.substring(alias.length()).trim();
				break;
			}
		}

		remainder = removeContentQualifier(remainder);
		return remainder.isEmpty() ? null : VoiceIntent.search(action, addon, remainder);
	}

	/** Converts a MediaSession search query into the same deterministic in-app grammar. */
	@Nullable
	public static String mediaSearchCommand(@Nullable String query, @Nullable Locale locale) {
		if ((query == null) || query.isBlank()) return null;
		String command = query.trim();
		if (parse(command, locale) != null) return command;
		command = "play " + command;
		return (parse(command, locale) == null) ? null : command;
	}

	public static int parseSelectionIndex(@Nullable String phrase) {
		String text = normalize(phrase);
		if (text.isEmpty()) return -1;
		if (isAny(text, "1", "one", "first", "number one", "result one", "số một",
				"so mot", "kết quả một", "ket qua mot"))
			return 0;
		if (isAny(text, "2", "two", "second", "number two", "result two", "số hai",
				"so hai", "kết quả hai", "ket qua hai"))
			return 1;
		if (isAny(text, "3", "three", "third", "number three", "result three", "số ba",
				"so ba", "kết quả ba", "ket qua ba"))
			return 2;
		return -1;
	}

	private static VoiceIntent.SearchAction parseSearchAction(String text) {
		if (startsWithAny(text, "play ", "phát ", "phat "))
			return VoiceIntent.SearchAction.PLAY;
		if (startsWithAny(text, "find ", "search ", "tìm ", "tim ", "tìm kiếm ", "tim kiem "))
			return VoiceIntent.SearchAction.FIND;
		if (startsWithAny(text, "open ", "mở ", "mo "))
			return VoiceIntent.SearchAction.OPEN;
		return null;
	}

	private static String removeAction(String text, VoiceIntent.SearchAction action) {
		String[] prefixes = switch (action) {
			case PLAY -> new String[]{"play ", "phát ", "phat "};
			case FIND -> new String[]{"find ", "search ", "tìm kiếm ", "tim kiem ", "tìm ", "tim "};
			case OPEN -> new String[]{"open ", "mở ", "mo "};
		};
		for (String prefix : prefixes) if (text.startsWith(prefix)) return text.substring(prefix.length()).trim();
		return text;
	}

	private static String removeContentQualifier(String text) {
		String[] qualifiers = {"video ", "channel ", "kênh ", "kenh ", "bài ", "bai ",
				"episode ", "tập ", "tap ", "book ", "sách ", "sach "};
		for (String qualifier : qualifiers) if (text.startsWith(qualifier)) return text.substring(qualifier.length()).trim();
		return text;
	}

	private static boolean startsWithAny(String text, String... prefixes) {
		for (String prefix : prefixes) if (text.startsWith(prefix)) return true;
		return false;
	}

	private static boolean isAny(String text, String... values) {
		for (String value : values) if (text.equals(value)) return true;
		return false;
	}

	private static String normalize(String phrase) {
		if (phrase == null) return "";
		String text = Normalizer.normalize(phrase, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
		text = text.replaceAll("[,.!?;:]+", " ").replaceAll("\\s+", " ");
		return text;
	}
}
