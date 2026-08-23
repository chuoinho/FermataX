package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.vfs.m3u.M3uFile.URL;
import static me.aap.utils.text.TextUtils.isNullOrBlank;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
class KnownProviders {
	private static final String TVQQ_RAW =
			"https://raw.githubusercontent.com/chuoinho/IPTV/refs/heads/master/123.m3u";
	private static final String TVQQ_CDN =
			"https://cdn.jsdelivr.net/gh/chuoinho/IPTV@master/123.m3u";

	static void configure(PreferenceStore ps) {
		String playlistUrl = ps.getStringPref(URL);
		if (isNullOrBlank(playlistUrl)) return;
		String host = Uri.parse(playlistUrl).getHost();
		if (host == null) return;
		int i1 = host.lastIndexOf('.');

		if (i1 != -1) {
			int i2 = host.lastIndexOf('.', i1 - 1);
			host = (i2 == -1) ? host.substring(0, i1) : host.substring(i2 + 1, i1);
		} else {
			return;
		}

		switch (host) {
			case "edem":
			case "iedem":
			case "edemtv":
			case "ilooktv":
			case "ilook-tv":
				configure(ps, "http://epg.it999.ru/epg2.xml.gz", TvM3uFile.CATCHUP_TYPE_SHIFT);
		}
	}

	static List<String> fetchCandidates(String playlistUrl) {
		String normalized = (playlistUrl == null) ? "" : playlistUrl.trim();
		if (!("http://bit.ly/tvqq".equalsIgnoreCase(normalized) ||
				"https://bit.ly/tvqq".equalsIgnoreCase(normalized) ||
				TVQQ_RAW.equalsIgnoreCase(normalized) || TVQQ_CDN.equalsIgnoreCase(normalized)))
			return List.of(normalized);
		List<String> result = new ArrayList<>(3);
		result.add(TVQQ_RAW);
		if (!normalized.equalsIgnoreCase(TVQQ_RAW) && !normalized.equalsIgnoreCase(TVQQ_CDN))
			result.add(normalized);
		result.add(TVQQ_CDN);
		return List.copyOf(result);
	}

	static void configure(PreferenceStore ps, String epg, int catchup) {
		configure(ps, epg, catchup, null);
	}

	static void configure(PreferenceStore ps, String epg, int catchup, String q) {
		try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
			if (isNullOrBlank(ps.getStringPref(TvM3uFile.EPG_URL))) {
				e.setStringPref(TvM3uFile.EPG_URL, epg);
			}
			if (ps.getIntPref(TvM3uFile.CATCHUP_TYPE) == TvM3uFile.CATCHUP_TYPE_AUTO) {
				e.setIntPref(TvM3uFile.CATCHUP_TYPE, catchup);
			}
			if ((q != null) && isNullOrBlank(ps.getStringPref(TvM3uFile.CATCHUP_QUERY))) {
				e.setStringPref(TvM3uFile.CATCHUP_QUERY, epg);
			}
		}
	}
}
