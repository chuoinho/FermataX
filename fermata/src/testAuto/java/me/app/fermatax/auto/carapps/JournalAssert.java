package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertTrue;

/** Installs an in-memory Phone Apps journal for a test and checks the events it got. */
public final class JournalAssert {
	private JournalAssert() {
	}

	public static PhoneAppsJournal install() {
		PhoneAppsJournal j = new PhoneAppsJournal(null, Runnable::run, System::currentTimeMillis, () -> 0L);
		PhoneAppsJournal.setForTest(j);
		return j;
	}

	public static void uninstall() {
		PhoneAppsJournal.setForTest(null);
	}

	/** Some line is this event with all these key=value attributes. */
	public static void has(PhoneAppsJournal j, String event, String... attrs) {
		for (String line : j.lines()) {
			if (!line.contains(" " + event + " ") && !line.endsWith(" " + event)) continue;
			boolean all = true;
			for (String a : attrs) all &= (line + " ").contains(" " + a + " ");
			if (all) return;
		}
		assertTrue(event + " " + String.join(" ", attrs) + " in\n" + String.join("\n", j.lines()), false);
	}
}
