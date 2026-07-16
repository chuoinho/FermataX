package me.aap.fermata.addon.audiobook.remote;

import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;

public record AudiobookSourceSnapshot(AudiobookSource source, List<AudiobookBook> books) {
}
