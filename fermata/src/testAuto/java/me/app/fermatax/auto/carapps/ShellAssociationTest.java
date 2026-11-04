package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ShellAssociationTest {
	private static final String MAC = "02:00:00:00:FE:01";

	@Test
	public void findsOurAssociationInTheList() {
		String list = "Max ID: 3\n"
				+ "Association ID | Package Name | Mac Address\n"
				+ "2 | com.google.android.projection.gearhead | null\n"
				+ "3 | com.android.shell | 02:00:00:00:fe:01\n";
		assertEquals(Integer.valueOf(3), ShellAssociation.findId(list, MAC));
	}

	@Test
	public void noAssociationOfOursMeansOneMustBeCreated() {
		String list = "Max ID: 2\n"
				+ "Association ID | Package Name | Mac Address\n"
				+ "2 | com.google.android.projection.gearhead | null\n";
		assertNull(ShellAssociation.findId(list, MAC));
	}

	@Test
	public void anotherShellAssociationIsNotOurs() {
		assertNull(ShellAssociation.findId("7 | com.android.shell | 02:00:00:00:fe:99\n", MAC));
	}

	@Test
	public void anotherPackageWithOurAddressIsNotOurs() {
		assertNull(ShellAssociation.findId("7 | com.example | 02:00:00:00:fe:01\n", MAC));
	}

	@Test
	public void unexpectedOutputIsNotAnAssociation() {
		assertNull(ShellAssociation.findId("x | com.android.shell | 02:00:00:00:fe:01\nError\n", MAC));
	}
}
