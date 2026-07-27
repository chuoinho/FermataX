package me.aap.fermata.addon.stremio.subtitle;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SubtitleLanguageNormalizer {
	private static final Map<String, String> ALIASES = aliases();
	private static final Set<String> RTL = Set.of("ar", "fa", "he", "ps", "sd", "ug", "ur", "yi");

	private SubtitleLanguageNormalizer() {
	}

	public static SubtitleLanguage normalize(String value) {
		if (value == null) return SubtitleLanguage.UNKNOWN;
		String input = value.trim().replace('_', '-');
		if (input.isEmpty()) return SubtitleLanguage.UNKNOWN;

		String[] parts = input.split("-", -1);
		if (parts.length == 0) return SubtitleLanguage.UNKNOWN;
		String alias = ALIASES.get(parts[0].toLowerCase(Locale.ROOT));
		if (alias != null) parts[0] = alias;
		input = String.join("-", parts);

		Locale locale = Locale.forLanguageTag(input);
		String base = locale.getLanguage();
		String tag = locale.toLanguageTag();
		if (base.isEmpty() || "und".equalsIgnoreCase(tag)) return SubtitleLanguage.UNKNOWN;
		base = base.toLowerCase(Locale.ROOT);
		return new SubtitleLanguage(tag, base,
				RTL.contains(base) ? SubtitleLanguage.Direction.RTL : SubtitleLanguage.Direction.LTR);
	}

	private static Map<String, String> aliases() {
		var aliases = new HashMap<String, String>();
		put(aliases, "en", "eng");
		put(aliases, "es", "spa");
		put(aliases, "fr", "fre", "fra");
		put(aliases, "de", "ger", "deu");
		put(aliases, "it", "ita");
		put(aliases, "pt", "por");
		put(aliases, "ru", "rus");
		put(aliases, "uk", "ukr");
		put(aliases, "vi", "vie", "vietnamese");
		put(aliases, "zh", "chi", "zho", "cmn", "chinese");
		put(aliases, "ja", "jpn", "japanese");
		put(aliases, "ko", "kor", "korean");
		put(aliases, "ar", "ara", "arabic");
		put(aliases, "he", "heb", "iw", "hebrew");
		put(aliases, "fa", "fas", "per", "persian");
		put(aliases, "id", "ind", "in");
		put(aliases, "yi", "yid", "ji");
		put(aliases, "nl", "dut", "nld");
		put(aliases, "ro", "rum", "ron");
		put(aliases, "cs", "cze", "ces");
		put(aliases, "sk", "slo", "slk");
		put(aliases, "el", "gre", "ell");
		put(aliases, "eu", "baq", "eus");
		put(aliases, "ms", "may", "msa");
		put(aliases, "sq", "alb", "sqi");
		put(aliases, "hy", "arm", "hye");
		put(aliases, "my", "bur", "mya");
		put(aliases, "bo", "tib", "bod");
		put(aliases, "cy", "wel", "cym");
		return Map.copyOf(aliases);
	}

	private static void put(Map<String, String> aliases, String canonical, String... values) {
		aliases.put(canonical, canonical);
		for (String value : values) aliases.put(value, canonical);
	}
}
