package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ShellAssociationTest {
	private static final String MAC = "02:00:00:00:FE:01";

	@Test
	public void findsAndroid17Association() {
		String list = "Max ID: 2\n"
				+ "Association{mId=1, mUserId=0, mPackageName='com.google.android.projection.gearhead', mDeviceMacAddress=null, mDisplayName='Android Auto'}\n\n"
				+ "Association{mId=2, mUserId=0, mPackageName='com.android.shell', mDeviceMacAddress=02:00:00:00:fe:01, mDisplayName='streaming'}\n";
		assertEquals(Integer.valueOf(2), ShellAssociation.findId(list, MAC));
	}

	@Test
	public void android17RequiresOurUserPackageAndAddress() {
		String association = "Association{mId=7, mUserId=0, mPackageName='com.android.shell', mDeviceMacAddress=02:00:00:00:fe:01, mDisplayName='streaming'}";
		assertNull(ShellAssociation.findId(association.replace("mUserId=0", "mUserId=10"), MAC));
		assertNull(ShellAssociation.findId(association.replace("com.android.shell", "com.example"), MAC));
		assertNull(ShellAssociation.findId(association.replace("fe:01", "fe:99"), MAC));
		assertNull(ShellAssociation.findId(association.replace("mId=7", "mId=2147483648"), MAC));
	}

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
