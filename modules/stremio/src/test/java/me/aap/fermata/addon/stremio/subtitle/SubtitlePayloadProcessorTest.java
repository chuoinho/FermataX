package me.aap.fermata.addon.stremio.subtitle;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class SubtitlePayloadProcessorTest {
	@Test
	public void convertsAssAndStripsOverrideTags() throws Exception {
		String ass = "[Script Info]\n[Events]\n" +
				"Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
				"Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,{\\i1}Hello\\NWorld\n";
		String result = new String(SubtitlePayloadProcessor.process(
				ass.getBytes(StandardCharsets.UTF_8), SubtitleFormat.ASS), StandardCharsets.UTF_8);
		assertTrue(result.contains("00:00:01,000 --> 00:00:02,500"));
		assertTrue(result.contains("Hello\nWorld"));
	}

	@Test
	public void convertsTtmlWithSecureParser() throws Exception {
		String ttml = "<?xml version=\"1.0\"?><tt xmlns=\"http://www.w3.org/ns/ttml\"><body>" +
				"<div><p begin=\"1.5s\" end=\"3s\">Hello</p></div></body></tt>";
		String result = new String(SubtitlePayloadProcessor.process(
				ttml.getBytes(StandardCharsets.UTF_8), SubtitleFormat.TTML), StandardCharsets.UTF_8);
		assertTrue(result.contains("00:00:01,500 --> 00:00:03,000"));
	}

	@Test
	public void sniffsExtensionlessVttAndRejectsCueLessPayload() throws Exception {
		String vtt = "WEBVTT\n\n00:01.000 --> 00:02.000\nHello\n";
		assertTrue(new String(SubtitlePayloadProcessor.process(
				vtt.getBytes(StandardCharsets.UTF_8), SubtitleFormat.UNKNOWN),
				StandardCharsets.UTF_8).startsWith("WEBVTT"));
		assertThrows(java.io.IOException.class, () -> SubtitlePayloadProcessor.process(
				"WEBVTT\n".getBytes(StandardCharsets.UTF_8), SubtitleFormat.WEBVTT));
	}
}
