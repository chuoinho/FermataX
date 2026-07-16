package me.aap.fermata.addon.audiobook.catalog;

import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;

public record LibriVoxImport(AudiobookSource source, AudiobookBook book,
		List<AudiobookChapter> chapters) {
}
