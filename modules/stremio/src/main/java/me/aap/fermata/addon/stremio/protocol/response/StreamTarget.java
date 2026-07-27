package me.aap.fermata.addon.stremio.protocol.response;

public sealed interface StreamTarget permits DirectStreamTarget, YoutubeStreamTarget,
		ExternalStreamTarget, InfoHashStreamTarget, NzbStreamTarget,
		ArchiveStreamTarget, UnsupportedStreamTarget {
}
