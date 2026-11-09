package me.app.fermatax.auto.carapps.server;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps a single CarDisplayServer alive: stale instances are killed (idle exit: IdleExitPolicy). */
final class ServerLifecycle {
	private static final String MARKER = "carapps.server.Main";

	private ServerLifecycle() {
	}

	/**
	 * PIDs whose command line is a CarDisplayServer, other than {@code self} and its ancestors: over ADB the launching
	 * {@code sh -c "... app_process ... Main"} carries the same markers, and killing it hangs the server up.
	 */
	static List<Integer> stalePids(Map<Integer, String> cmdlines, Map<Integer, Integer> parents, int self) {
		Set<Integer> keep = new HashSet<>();
		for (Integer pid = self; pid != null && pid > 0 && keep.add(pid); pid = parents.get(pid)) {
			// walks self, parent, grandparent...; keep.add stops a cycle in a torn /proc read
		}
		List<Integer> res = new ArrayList<>();
		for (Map.Entry<Integer, String> e : cmdlines.entrySet()) {
			if (!keep.contains(e.getKey()) && e.getValue() != null && e.getValue().contains(MARKER)
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

	/** PPid of every process, read from /proc/PID/status. */
	static Map<Integer, Integer> readParents() {
		Map<Integer, Integer> res = new HashMap<>();
		File[] dirs = new File("/proc").listFiles();
		if (dirs == null) return res;
		for (File d : dirs) {
			try {
				int pid = Integer.parseInt(d.getName());
				for (String line : Files.readAllLines(new File(d, "status").toPath())) {
					if (line.startsWith("PPid:")) res.put(pid, Integer.parseInt(line.substring(5).trim()));
				}
			} catch (Exception ignored) {
				// not a pid dir, or the process exited meanwhile
			}
		}
		return res;
	}

	static void killStale(int self) {
		for (int pid : stalePids(readCmdlines(), readParents(), self)) android.os.Process.killProcess(pid);
	}
}
