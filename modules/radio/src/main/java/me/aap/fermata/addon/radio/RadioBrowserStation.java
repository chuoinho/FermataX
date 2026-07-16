package me.aap.fermata.addon.radio;

import java.net.URI;
import java.net.URISyntaxException;

class RadioBrowserStation {
	final String uuid;
	final String name;
	final String url;
	final String urlResolved;
	final String homepage;
	final String favicon;
	final String tags;
	final String country;
	final String countryCode;
	final String language;
	final String codec;
	final int bitrate;
	final int votes;
	final int clickCount;

	RadioBrowserStation(String uuid, String name, String url, String urlResolved, String homepage,
											String favicon, String tags, String country, String countryCode,
											String language, String codec, int bitrate, int votes, int clickCount) {
		this.uuid = clean(uuid);
		this.name = clean(name);
		this.url = clean(url);
		this.urlResolved = clean(urlResolved);
		this.homepage = clean(homepage);
		this.favicon = clean(favicon);
		this.tags = clean(tags);
		this.country = clean(country);
		this.countryCode = clean(countryCode);
		this.language = clean(language);
		this.codec = clean(codec);
		this.bitrate = bitrate;
		this.votes = votes;
		this.clickCount = clickCount;
	}

	String getName() {
		if (!name.isEmpty()) return name;
		String host = host(getStreamUrl());
		return host.isEmpty() ? "Radio station" : host;
	}

	boolean hasStreamUrl() {
		String stream = getStreamUrl();
		String lower = stream.toLowerCase();
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	String getStreamUrl() {
		return !urlResolved.isEmpty() ? urlResolved : url;
	}

	String getArtist() {
		if (!country.isEmpty()) return country;
		if (!language.isEmpty()) return language;
		return "";
	}

	String getSubtitle() {
		StringBuilder sb = new StringBuilder();
		append(sb, country);
		append(sb, language);
		if (!codec.isEmpty()) append(sb, bitrate > 0 ? codec + ' ' + bitrate + " kbps" : codec);
		else if (bitrate > 0) append(sb, bitrate + " kbps");
		return sb.toString();
	}

	private static void append(StringBuilder sb, String s) {
		if (s.isEmpty()) return;
		if (sb.length() != 0) sb.append(" - ");
		sb.append(s);
	}

	private static String host(String url) {
		try {
			String host = new URI(url).getHost();
			return (host == null) ? "" : host;
		} catch (URISyntaxException ex) {
			return "";
		}
	}

	private static String clean(String s) {
		return (s == null) ? "" : s.trim();
	}
}
