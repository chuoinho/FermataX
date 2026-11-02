package me.app.fermatax.auto.carapps.server;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keeps a single CarDisplayServer alive: stale instances are killed (idle exit: IdleExitPolicy). */
final class ServerLifecycle {
	private static final String MARKER = "carapps.server.Main";

	private ServerLifecycle() {
	}

	/** PIDs (other than {@code self}) whose command line is a CarDisplayServer. */
	static List<Integer> stalePids(Map<Integer, String> cmdlines, int self) {
		List<Integer> res = new ArrayList<>();
		for (Map.Entry<Integer, String> e : cmdlines.entrySet()) {
			if (e.getKey() != self && e.getValue() != null && e.getValue().contains(MARKER)
					&& e.getValue().contains("app_process")) {
				res.add(e.getKey());
			}
		}
		return res;
	}

	static Map<Integer, String> readCmdlines() {
		Map<Integer, String> res = new HashMap<>();
		File[] dirs = new File("/proc").listFiles();
		if (dirs == null) return res;
		for (File d : dirs) {
			try {
				int pid = Integer.parseInt(d.getName());
				res.put(pid, new String(Files.readAllBytes(new File(d, "cmdline").toPath())).replace('\0', ' '));
			} catch (Exception ignored) {
				// not a pid dir, or the process exited meanwhile
			}
		}
		return res;
	}

	static void killStale(int self) {
		for (int pid : stalePids(readCmdlines(), self)) android.os.Process.killProcess(pid);
	}
}
