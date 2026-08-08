package me.aap.fermata.whisper;

import java.nio.ByteBuffer;
import java.util.Objects;

/** Input validation kept in Java so malformed PCM never reaches the JNI boundary. */
final class WhisperAudioInput {
	private static final int MAX_CHANNELS = 8;
	private static final int MAX_FRAME_RATE = 192_000;

	private WhisperAudioInput() {
	}

	static ByteBuffer prepare(ByteBuffer source, int chunkLen, int bytesPerSample, int channels,
			int frameRate) {
		Objects.requireNonNull(source, "audio buffer");
		if (!source.isDirect()) throw new IllegalArgumentException("Whisper requires a direct PCM buffer");
		if (chunkLen <= 0) throw new IllegalArgumentException("Chunk length must be positive");
		if (bytesPerSample < 1 || bytesPerSample > 4) {
			throw new IllegalArgumentException("Unsupported PCM bytes per sample: " + bytesPerSample);
		}
		if (channels <= 0 || channels > MAX_CHANNELS) {
			throw new IllegalArgumentException("Unsupported PCM channel count: " + channels);
		}
		if (frameRate <= 0 || frameRate > MAX_FRAME_RATE) {
			throw new IllegalArgumentException("Unsupported PCM frame rate: " + frameRate);
		}
		if ((source.remaining() % (bytesPerSample * channels)) != 0) {
			throw new IllegalArgumentException("PCM buffer ends in a partial frame");
		}
		return ((source.position() == 0) && (source.limit() == source.capacity())) ? source : source.slice();
	}

	static boolean applyNativeConsumed(ByteBuffer source, int available, int consumed) {
		long count = Math.abs((long) consumed);
		if (count > available) {
			throw new IllegalStateException("Whisper native resampler reported an invalid consumed byte count");
		}
		source.position(source.position() + (int) count);
		return consumed <= 0;
	}
}
