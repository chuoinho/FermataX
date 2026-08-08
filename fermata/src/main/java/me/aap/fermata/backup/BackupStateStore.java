package me.aap.fermata.backup;

/** Mutable logical state controlled by the base application during backup and restore. */
public interface BackupStateStore {
	BackupData snapshot() throws BackupException;

	void validate(BackupData data) throws BackupException;

	void replace(BackupData data) throws BackupException;
}
