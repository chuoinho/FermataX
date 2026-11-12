package me.aap.fermata.engine.vlc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Proxy;

import org.junit.Test;
import org.videolan.libvlc.interfaces.IMedia;

public class VlcTrackLookupTest {
	@Test
	public void findsTheLanguageByTrackIdNotListPosition() {
		// MP4 uses its 1-based track ids on both sides: neither matches the list position
		IMedia media = media(audio(2, "vie"), audio(3, "eng"), subtitle(4, "fre"));

		assertEquals("eng", VlcEngine.trackLanguage(media, 3, IMedia.AudioTrack.class));
		assertEquals("vie", VlcEngine.trackLanguage(media, 2, IMedia.AudioTrack.class));
		assertEquals("fre", VlcEngine.trackLanguage(media, 4, IMedia.SubtitleTrack.class));
	}

	@Test
	public void unknownIdOrWrongTypeHasNoLanguage() {
		// MPEG-TS: the player reports PIDs (257) while the media numbers its tracks 0, 1, 2
		IMedia media = media(audio(0, "vie"), audio(1, "eng"), subtitle(2, "fre"));

		assertNull(VlcEngine.trackLanguage(media, 257, IMedia.AudioTrack.class));
		assertNull(VlcEngine.trackLanguage(media, 2, IMedia.AudioTrack.class));
	}

	private static IMedia.Track audio(int id, String language) {
		return new IMedia.AudioTrack("mp4a", "mp4a", 0, id, 0, 0, 0, language, null, 2, 48000);
	}

	private static IMedia.Track subtitle(int id, String language) {
		return new IMedia.SubtitleTrack("tx3g", "tx3g", 0, id, 0, 0, 0, language, null, null);
	}

	private static IMedia media(IMedia.Track... tracks) {
		return (IMedia) Proxy.newProxyInstance(IMedia.class.getClassLoader(),
				new Class<?>[]{IMedia.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getTrackCount" -> tracks.length;
					case "getTrack" -> tracks[(Integer) args[0]];
					default -> throw new UnsupportedOperationException(method.getName());
				});
	}
}
