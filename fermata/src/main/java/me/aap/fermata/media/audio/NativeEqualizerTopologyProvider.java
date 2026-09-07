package me.aap.fermata.media.audio;

import androidx.annotation.Nullable;

/** Exposes a read-only topology already obtained from the current native Equalizer. */
interface NativeEqualizerTopologyProvider {
	@Nullable NativeEqualizerTopology getEqualizerTopology();
}
