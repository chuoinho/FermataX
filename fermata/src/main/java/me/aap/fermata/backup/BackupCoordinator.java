package me.aap.fermata.backup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates, applies and rolls back one complete logical restore operation. */
public final class BackupCoordinator {
	private final BackupStateStore stateStore;
	private final Map<String, BackupContributor> contributors;

	public BackupCoordinator(BackupStateStore stateStore,
			Collection<? extends BackupContributor> contributors) throws BackupException {
		this.stateStore = stateStore;
		this.contributors = index(contributors);
	}

	public BackupData capture() throws Exception {
		Map<String, BackupData.Section> sections = exportSections();
		return stateStore.snapshot().withSections(sections);
	}

	public void restore(BackupData data) throws BackupException {
		validate(data);
		BackupData rollback;
		try {
			rollback = stateStore.snapshot().withSections(exportSections());
		} catch (BackupException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new BackupException(BackupException.Code.RESTORE_FAILED,
					"Unable to prepare restore rollback", ex);
		}

		try {
			stateStore.replace(data);
			restoreSections(data.sections());
			verifySections(data.sections());
		} catch (Throwable failure) {
			rollback(rollback, failure);
			throw new BackupException(BackupException.Code.RESTORE_FAILED,
					"Backup restore failed; previous configuration was restored", failure);
		}
	}

	private void validate(BackupData data) throws BackupException {
		stateStore.validate(data);
		for (Map.Entry<String, BackupData.Section> entry : data.sections().entrySet()) {
			BackupContributor contributor = contributors.get(entry.getKey());
			if (contributor == null) throw new BackupException(
					BackupException.Code.INCOMPLETE_BACKUP,
					"A required addon is unavailable for this backup");
			BackupData.Section section = entry.getValue();
			try {
				contributor.validateRestore(section.version(), section.data());
			} catch (Exception ex) {
				throw new BackupException(BackupException.Code.INVALID_FORMAT,
						"Invalid addon backup section", ex);
			}
		}
	}

	private Map<String, BackupData.Section> exportSections() throws Exception {
		Map<String, BackupData.Section> result = new LinkedHashMap<>();
		for (BackupContributor contributor : contributors.values()) {
			byte[] data = contributor.exportBackup();
			result.put(contributor.getBackupId(), new BackupData.Section(
					contributor.getBackupId(), contributor.getBackupVersion(), data));
		}
		return result;
	}

	private void restoreSections(Map<String, BackupData.Section> sections) throws Exception {
		for (Map.Entry<String, BackupData.Section> entry : sections.entrySet()) {
			BackupData.Section section = entry.getValue();
			contributors.get(entry.getKey()).restoreBackup(section.version(), section.data());
		}
	}

	private void verifySections(Map<String, BackupData.Section> sections) throws Exception {
		for (Map.Entry<String, BackupData.Section> entry : sections.entrySet()) {
			BackupData.Section section = entry.getValue();
			contributors.get(entry.getKey()).verifyRestore(section.version(), section.data());
		}
	}

	private void rollback(BackupData rollback, Throwable original) {
		List<Throwable> failures = new ArrayList<>();
		try {
			stateStore.replace(rollback);
		} catch (Throwable ex) {
			failures.add(ex);
		}
		try {
			restoreSections(rollback.sections());
			verifySections(rollback.sections());
		} catch (Throwable ex) {
			failures.add(ex);
		}
		for (Throwable failure : failures) original.addSuppressed(failure);
	}

	private static Map<String, BackupContributor> index(
			Collection<? extends BackupContributor> contributors) throws BackupException {
		Map<String, BackupContributor> result = new LinkedHashMap<>();
		for (BackupContributor contributor : contributors) {
			String id = contributor.getBackupId();
			if ((id == null) || id.isBlank() || (contributor.getBackupVersion() <= 0) ||
					(result.put(id, contributor) != null)) {
				throw new BackupException(BackupException.Code.INVALID_FORMAT,
						"Invalid backup contributor registration");
			}
		}
		return Map.copyOf(result);
	}
}
