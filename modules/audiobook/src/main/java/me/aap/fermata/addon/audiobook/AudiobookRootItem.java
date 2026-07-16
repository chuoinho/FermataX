package me.aap.fermata.addon.audiobook;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.audiobook.data.AudiobookRepository;
import me.aap.fermata.addon.audiobook.download.AudiobookDownloadManager;
import me.aap.fermata.addon.audiobook.catalog.LibriVoxBook;
import me.aap.fermata.addon.audiobook.catalog.LibriVoxCatalogClient;
import me.aap.fermata.addon.audiobook.catalog.LibriVoxImport;
import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;
import me.aap.fermata.addon.audiobook.remote.AudiobookSourceSnapshot;
import me.aap.fermata.addon.audiobook.remote.AudiobookshelfBookDetails;
import me.aap.fermata.addon.audiobook.remote.AudiobookshelfClient;
import me.aap.fermata.addon.audiobook.remote.OpdsCatalogClient;
import me.aap.fermata.addon.audiobook.remote.OpdsCatalogSnapshot;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.vfs.VirtualResource;

public final class AudiobookRootItem extends ExtRoot implements AudiobookItem {
	static final String ID = "Audiobook";
	static final String SCHEME = "audiobook";
	private static final String SECTION_PREFIX = SCHEME + ":section:";
	private static final String SOURCE_PREFIX = SCHEME + ":source:";
	private static final String BOOK_PREFIX = SCHEME + ":book:";
	private static final String CHAPTER_PREFIX = SCHEME + ":chapter:";
	private static final String CATALOG_ACTION_PREFIX = SCHEME + ":catalog-action:";
	private static final String CATALOG_SEARCH_PREFIX = SCHEME + ":catalog-search:";
	private static final String CATALOG_BOOK_PREFIX = SCHEME + ":catalog-book:";
	private static final String CATALOG_IMPORT_PREFIX = SCHEME + ":catalog-import:";
	private final EnumMap<AudiobookSection, AudiobookSectionItem> sections =
			new EnumMap<>(AudiobookSection.class);
	private final EnumMap<AudiobookCatalogAction, AudiobookCatalogActionItem> catalogActions =
			new EnumMap<>(AudiobookCatalogAction.class);
	private final AudiobookRepository repository;
	private final AudiobookDownloadManager downloads;
	private final AudiobookshelfClient audiobookshelf;
	private final OpdsCatalogClient opds;
	private final LibriVoxCatalogClient catalog = new LibriVoxCatalogClient();

	AudiobookRootItem(DefaultMediaLib lib, AudiobookRepository repository,
			AudiobookDownloadManager downloads, AudiobookshelfClient audiobookshelf,
			OpdsCatalogClient opds) {
		super(ID, lib, AddonCapability.AUDIOBOOK);
		this.repository = repository;
		this.downloads = downloads;
		this.audiobookshelf = audiobookshelf;
		this.opds = opds;
		for (AudiobookSection section : AudiobookSection.values()) {
			sections.put(section, new AudiobookSectionItem(this, section));
		}
		AudiobookSectionItem discover = sections.get(AudiobookSection.DISCOVER);
		for (AudiobookCatalogAction action : AudiobookCatalogAction.values()) {
			catalogActions.put(action, new AudiobookCatalogActionItem(this, discover, action));
		}
	}

	@Nullable
	FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		if (scheme == null) return ID.equals(id) ? completed(this) : null;
		if (!SCHEME.equals(scheme)) return null;
		for (AudiobookSectionItem item : sections.values()) {
			if (item.getId().equals(id)) return completed(item);
		}
		for (AudiobookCatalogActionItem item : catalogActions.values()) {
			if (item.getId().equals(id)) return completed(item);
		}
		if (id.startsWith(SOURCE_PREFIX)) {
			String sourceId = id.substring(SOURCE_PREFIX.length());
			return repository.getSource(sourceId).map(source -> (source == null) ? null :
					new AudiobookSourceItem(this, sections.get(AudiobookSection.SOURCES), source));
		}
		if (id.startsWith(BOOK_PREFIX)) {
			String bookId = id.substring(BOOK_PREFIX.length());
			return repository.getBook(bookId).map(book -> (book == null) ? null :
					new AudiobookBookItem(this, sections.get(AudiobookSection.LIBRARY), book));
		}
		if (id.startsWith(CHAPTER_PREFIX)) {
			String[] keys = id.substring(CHAPTER_PREFIX.length()).split(":", 2);
			if (keys.length != 2) return completedNull();
			return repository.getBook(keys[0]).then(book -> {
				if (book == null) return completedNull();
				AudiobookBookItem parent = new AudiobookBookItem(this,
						sections.get(AudiobookSection.LIBRARY), book);
				return resolveBook(book).then(resolved -> {
					List<AudiobookChapter> chapters = resolved.chapters;
					for (int index = 0; index < chapters.size(); index++) {
						AudiobookChapter chapter = chapters.get(index);
						if (!chapter.getId().equals(keys[1])) continue;
						String next = (index + 1 < chapters.size()) ? chapters.get(index + 1).getId() : null;
						return createChapter(parent, resolved.book, chapter, next);
					}
					return completedNull();
				});
			});
		}
		return completedNull();
	}

	FutureSupplier<List<Item>> listSection(AudiobookSectionItem section) {
		return switch (section.getSection()) {
			case CONTINUE -> repository.listContinue(50).map(books -> createBooks(section, books));
			case LIBRARY -> repository.listBooks().map(books -> createBooks(section, books));
			case DOWNLOADS -> repository.listDownloaded(100).map(books -> createBooks(section, books));
			case SOURCES -> repository.listSources().map(sources -> createSources(section, sources));
			case DISCOVER -> completed(new ArrayList<>(catalogActions.values()));
		};
	}

	FutureSupplier<List<Item>> listCatalog(BrowsableItem parent, String query,
			LibriVoxCatalogClient.Sort sort) {
		return catalog.search(query, sort, 25).map(books -> {
			List<Item> result = new ArrayList<>(books.size());
			for (LibriVoxBook book : books) {
				result.add(new AudiobookCatalogBookItem(this, parent, book));
			}
			return result;
		});
	}

	AudiobookCatalogFolder createSearchFolder(String query) {
		return new AudiobookCatalogFolder(this, sections.get(AudiobookSection.DISCOVER), query);
	}

	FutureSupplier<AudiobookBook> importBook(LibriVoxBook book) {
		return catalog.load(book.identifier()).then(this::saveImport);
	}

	FutureSupplier<Void> download(AudiobookBook book) {
		return downloads.download(book);
	}

	FutureSupplier<Void> deleteDownload(AudiobookBook book) {
		return downloads.delete(book);
	}

	private FutureSupplier<AudiobookBook> saveImport(LibriVoxImport imported) {
		return repository.saveCatalogBook(imported.source(), imported.book(), imported.chapters());
	}

	AudiobookBookItem createLibraryBook(AudiobookBook book) {
		return new AudiobookBookItem(this, sections.get(AudiobookSection.LIBRARY), book);
	}

	FutureSupplier<List<Item>> listSourceBooks(AudiobookSourceItem parent) {
		return repository.listBooksBySource(parent.getSource().getId()).map(books ->
				createBooks(parent, books));
	}

	FutureSupplier<List<Item>> listChapters(AudiobookBookItem parent) {
		return resolveBook(parent.getBook()).then(resolved ->
				createChapters(parent, resolved.book, resolved.chapters, 0, new ArrayList<>()));
	}

	private FutureSupplier<BookChapters> resolveBook(AudiobookBook book) {
		return repository.listChapters(book.getId()).then(chapters -> {
			if (book.getSourceId() == null) {
				return completed(new BookChapters(book, chapters));
			}
			return repository.getSource(book.getSourceId()).then(source -> {
				if ((source == null) || (source.getType() != AudiobookSourceType.AUDIOBOOKSHELF) ||
						(book.getRemoteId() == null)) {
					return completed(new BookChapters(book, chapters));
				}
				return audiobookshelf.loadBook(source, book).then(details ->
						repository.saveRemoteBook(source, details.book(), details.chapters(),
								details.remoteProgressUpdatedMs()).map(saved ->
									new BookChapters(saved, details.chapters())),
					error -> completed(new BookChapters(book, chapters)));
			});
		});
	}

	private FutureSupplier<List<Item>> createChapters(AudiobookBookItem parent, AudiobookBook book,
			List<AudiobookChapter> chapters, int index, List<Item> result) {
		if (index >= chapters.size()) return completed(result);
		AudiobookChapter chapter = chapters.get(index);
		String next = (index + 1 < chapters.size()) ? chapters.get(index + 1).getId() : null;
		return createChapter(parent, book, chapter, next).then(item -> {
			if (item != null) result.add(item);
			return createChapters(parent, book, chapters, index + 1, result);
		});
	}

	private FutureSupplier<AudiobookChapterItem> createChapter(AudiobookBookItem parent,
			AudiobookBook book, AudiobookChapter chapter, String nextChapterId) {
		String location = chapter.isDownloaded() ? chapter.getLocalPath() : chapter.getMediaUrl();
		return requestHeaders(book).then(headers -> getLib().getVfsManager().getResource(location)
				.map(resource -> (resource == null) ? null : new AudiobookChapterItem(parent, book,
						chapter, resource, repository, this, headers, nextChapterId)));
	}

	private FutureSupplier<Map<String, String>> requestHeaders(AudiobookBook book) {
		if (book.getSourceId() == null) return completed(Map.of());
		return repository.getSource(book.getSourceId()).map(source -> {
			if (source == null) return Map.<String, String>of();
			return switch (source.getType()) {
				case AUDIOBOOKSHELF -> audiobookshelf.requestHeaders(source);
				case OPDS -> opds.requestHeaders(source);
				default -> Map.of();
			};
		});
	}

	private List<Item> createBooks(BrowsableItem parent, List<AudiobookBook> books) {
		List<Item> result = new ArrayList<>(books.size());
		for (AudiobookBook book : books) result.add(new AudiobookBookItem(this, parent, book));
		return result;
	}

	private List<Item> createSources(BrowsableItem parent, List<AudiobookSource> sources) {
		List<Item> result = new ArrayList<>(sources.size());
		for (AudiobookSource source : sources) {
			result.add(new AudiobookSourceItem(this, parent, source));
		}
		return result;
	}

	static String sectionId(AudiobookSection section) {
		return SECTION_PREFIX + section.id;
	}

	static String sourceId(String sourceId) {
		return SOURCE_PREFIX + sourceId;
	}

	static String bookId(String bookId) {
		return BOOK_PREFIX + bookId;
	}

	static String chapterId(String bookId, String chapterId) {
		return CHAPTER_PREFIX + bookId + ':' + chapterId;
	}

	static String catalogActionId(AudiobookCatalogAction action) {
		return CATALOG_ACTION_PREFIX + action.id;
	}

	static String catalogSearchId(String id) {
		return CATALOG_SEARCH_PREFIX + id;
	}

	static String catalogBookId(String id) {
		return CATALOG_BOOK_PREFIX + id;
	}

	static String catalogImportId(String id) {
		return CATALOG_IMPORT_PREFIX + id;
	}

	FutureSupplier<AudiobookBook> addLocalFolder(me.aap.utils.vfs.VirtualFolder folder) {
		return repository.addLocalFolder(folder);
	}

	FutureSupplier<AudiobookSource> addAudiobookshelf(String endpoint, String username,
			String password) {
		return audiobookshelf.connect(endpoint, username, password)
				.then(repository::saveSourceSnapshot);
	}

	FutureSupplier<AudiobookSource> addOpds(String endpoint, String username, String password,
			String bearerToken) {
		return opds.connect(endpoint, username, password, bearerToken).then(this::saveOpds);
	}

	private FutureSupplier<AudiobookSource> saveOpds(OpdsCatalogSnapshot snapshot) {
		return repository.saveSourceSnapshot(new AudiobookSourceSnapshot(snapshot.source(), List.of()))
				.then(ignored -> saveOpds(snapshot, 0));
	}

	private FutureSupplier<AudiobookSource> saveOpds(OpdsCatalogSnapshot snapshot, int index) {
		if (index >= snapshot.entries().size()) return completed(snapshot.source());
		OpdsCatalogSnapshot.Entry entry = snapshot.entries().get(index);
		return repository.saveCatalogBook(snapshot.source(), entry.book(), entry.chapters())
				.then(ignored -> saveOpds(snapshot, index + 1));
	}

	FutureSupplier<?> refresh(AudiobookSource source) {
		if (source.getType() == AudiobookSourceType.AUDIOBOOKSHELF) {
			return audiobookshelf.refresh(source).then(repository::saveSourceSnapshot);
		}
		if (source.getType() == AudiobookSourceType.OPDS) {
			return opds.refresh(source).then(this::saveOpds);
		}
		return repository.refreshSource(source.getId());
	}

	FutureSupplier<Boolean> delete(AudiobookSource source) {
		return repository.deleteSource(source.getId()).map(deleted -> {
			if (deleted && (source.getType() == AudiobookSourceType.AUDIOBOOKSHELF)) {
				audiobookshelf.removeCredentials(source);
			} else if (deleted && (source.getType() == AudiobookSourceType.OPDS)) {
				opds.removeCredentials(source);
			}
			return deleted;
		});
	}

	void syncProgress(AudiobookBook book, AudiobookChapter chapter, long position,
			boolean finished) {
		if (book.getSourceId() == null) return;
		repository.getSource(book.getSourceId()).onFailure(error ->
				Log.e(error, "Failed to resolve Audiobook source for progress sync"))
				.onSuccess(source -> {
			if ((source != null) && (source.getType() == AudiobookSourceType.AUDIOBOOKSHELF)) {
				audiobookshelf.updateProgress(source, book, chapter, position, finished)
						.onFailure(error -> Log.e(error,
								"Failed to sync Audiobookshelf playback progress"));
			}
		});
	}

	boolean isChildItemId(String id) {
		return ID.equals(id) || id.startsWith(SCHEME + ':');
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(
				me.aap.fermata.R.string.addon_name_audiobook));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return (DefaultMediaLib) super.getLib();
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public boolean getTitleSeqNumPref() {
		return false;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return completed(new ArrayList<>(sections.values()));
	}

	private record BookChapters(AudiobookBook book, List<AudiobookChapter> chapters) {
	}
}
