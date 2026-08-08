package me.aap.fermata.backup;

/** Dynamic-feature boundary for portable configuration that does not live in preferences. */
public interface BackupContributor {
	String getBackupId();

	int getBackupVersion();

	byte[] exportBackup() throws Exception;

	void validateRestore(int version, byte[] data) throws Exception;

	void restoreBackup(int version, byte[] data) throws Exception;

	default void verifyRestore(int version, byte[] data) throws Exception {
	}
}
