package me.aap.fermata.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class BackupCoordinatorTest {
	@Test
	public void successfulRestoreReplacesBaseAndAddonState() throws Exception {
		FakeStateStore base = new FakeStateStore(base("old"));
		FakeContributor addon = new FakeContributor("addon", bytes("old-addon"));
		BackupCoordinator coordinator = new BackupCoordinator(base, List.of(addon));

		coordinator.restore(withSection(base("new"), "addon", bytes("new-addon")));

		assertEquals("new", value(base.current));
		assertArrayEquals(bytes("new-addon"), addon.current);
	}

	@Test
	public void validationFailureDoesNotMutateLiveState() throws Exception {
		FakeStateStore base = new FakeStateStore(base("old"));
		FakeContributor addon = new FakeContributor("addon", bytes("old-addon"));
		addon.rejectValidation = true;
		BackupCoordinator coordinator = new BackupCoordinator(base, List.of(addon));

		BackupException failure = assertThrows(BackupException.class, () ->
				coordinator.restore(withSection(base("new"), "addon", bytes("new-addon"))));

		assertEquals(BackupException.Code.INVALID_FORMAT, failure.getCode());
		assertEquals("old", value(base.current));
		assertArrayEquals(bytes("old-addon"), addon.current);
		assertEquals(0, base.replaceCount);
	}

	@Test
	public void contributorFailureRollsBackBaseAndPreviouslyAppliedAddonState() throws Exception {
		FakeStateStore base = new FakeStateStore(base("old"));
		FakeContributor first = new FakeContributor("first", bytes("old-first"));
		FakeContributor second = new FakeContributor("second", bytes("old-second"));
		second.failValue = "new-second";
		BackupCoordinator coordinator = new BackupCoordinator(base, List.of(first, second));
		BackupData replacement = base("new").withSections(Map.of(
				"first", section("first", "new-first"),
				"second", section("second", "new-second")));

		BackupException failure = assertThrows(BackupException.class,
				() -> coordinator.restore(replacement));

		assertEquals(BackupException.Code.RESTORE_FAILED, failure.getCode());
		assertEquals("old", value(base.current));
		assertArrayEquals(bytes("old-first"), first.current);
		assertArrayEquals(bytes("old-second"), second.current);
		assertEquals(2, base.replaceCount);
	}

	@Test
	public void missingRequiredContributorFailsBeforeMutation() throws Exception {
		FakeStateStore base = new FakeStateStore(base("old"));
		BackupCoordinator coordinator = new BackupCoordinator(base, List.of());

		BackupException failure = assertThrows(BackupException.class, () ->
				coordinator.restore(withSection(base("new"), "missing", bytes("data"))));

		assertEquals(BackupException.Code.INCOMPLETE_BACKUP, failure.getCode());
		assertEquals("old", value(base.current));
		assertEquals(0, base.replaceCount);
	}

	@Test
	public void oneEncryptedFileRestoresBaseSecretsAndMultipleAddonSections() throws Exception {
		BackupData sourceData = new BackupData(
				Map.of("fermata", Map.of("theme", "dark")),
				Map.of("xtream_credentials", Map.of(
						"username#2", "secret-user-issue5",
						"password#2", "secret-password-issue5")), Map.of());
		FakeContributor tv = new FakeContributor("tv.sources", bytes("m3u+two-xtream"));
		FakeContributor stremio = new FakeContributor("stremio.sources", bytes("one-provider"));
		BackupData captured = new BackupCoordinator(new FakeStateStore(sourceData),
				List.of(tv, stremio)).capture();
		PortableBackupCodec codec = new PortableBackupCodec(new java.security.SecureRandom(), 100_000);
		char[] password = "portable-password".toCharArray();
		byte[] file = codec.encode(captured, password, 1, 301, "2.0.1");

		FakeStateStore destination = new FakeStateStore(base("destination-old"));
		FakeContributor destinationTv = new FakeContributor("tv.sources", bytes("old-tv"));
		FakeContributor destinationStremio = new FakeContributor(
				"stremio.sources", bytes("old-stremio"));
		new BackupCoordinator(destination, List.of(destinationTv, destinationStremio)).restore(
				codec.decode(file, password).data());

		assertEquals(sourceData.preferences(), destination.current.preferences());
		assertEquals(sourceData.securePreferences(), destination.current.securePreferences());
		assertArrayEquals(bytes("m3u+two-xtream"), destinationTv.current);
		assertArrayEquals(bytes("one-provider"), destinationStremio.current);
	}

	private static BackupData base(String value) {
		return new BackupData(Map.of("fermata", Map.of("value", value)), Map.of(), Map.of());
	}

	private static BackupData withSection(BackupData data, String id, byte[] value) {
		return data.withSections(Map.of(id, new BackupData.Section(id, 1, value)));
	}

	private static BackupData.Section section(String id, String value) {
		return new BackupData.Section(id, 1, bytes(value));
	}

	private static String value(BackupData data) {
		return (String) data.preferences().get("fermata").get("value");
	}

	private static byte[] bytes(String value) {
		return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	private static final class FakeStateStore implements BackupStateStore {
		BackupData current;
		int replaceCount;

		FakeStateStore(BackupData current) {
			this.current = current;
		}

		@Override
		public BackupData snapshot() {
			return current;
		}

		@Override
		public void validate(BackupData data) {
		}

		@Override
		public void replace(BackupData data) {
			current = new BackupData(data.preferences(), data.securePreferences(), Map.of());
			replaceCount++;
		}
	}

	private static final class FakeContributor implements BackupContributor {
		final String id;
		byte[] current;
		boolean rejectValidation;
		String failValue;

		FakeContributor(String id, byte[] current) {
			this.id = id;
			this.current = current.clone();
		}

		@Override
		public String getBackupId() {
			return id;
		}

		@Override
		public int getBackupVersion() {
			return 1;
		}

		@Override
		public byte[] exportBackup() {
			return current.clone();
		}

		@Override
		public void validateRestore(int version, byte[] data) {
			if (rejectValidation) throw new IllegalArgumentException("rejected");
		}

		@Override
		public void restoreBackup(int version, byte[] data) {
			String value = new String(data, java.nio.charset.StandardCharsets.UTF_8);
			if (value.equals(failValue)) throw new IllegalStateException("restore failed");
			current = data.clone();
		}
	}
}
