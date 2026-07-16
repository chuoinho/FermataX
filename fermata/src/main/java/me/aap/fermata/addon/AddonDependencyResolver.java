package me.aap.fermata.addon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class AddonDependencyResolver {
	private final AddonRegistry registry;

	AddonDependencyResolver(AddonRegistry registry) {
		this.registry = registry;
	}

	List<AddonInfo> resolveInstallOrder(AddonInfo root) {
		return resolveInstallOrder(root, false);
	}

	List<AddonInfo> resolveInstallOrder(AddonInfo root, boolean ignoreUnregisteredDependencies) {
		List<AddonInfo> result = new ArrayList<>();
		resolve(root, new HashMap<>(), new ArrayList<>(), result, ignoreUnregisteredDependencies);
		return result;
	}

	boolean isRequiredBy(AddonInfo dependency, Iterable<AddonInfo> candidates,
									 Predicate<AddonInfo> retained) {
		for (AddonInfo candidate : candidates) {
			if ((candidate != dependency) && retained.test(candidate) &&
					dependsOn(candidate, dependency, new HashSet<>())) return true;
		}
		return false;
	}

	private boolean dependsOn(AddonInfo candidate, AddonInfo dependency, Set<AddonInfo> visited) {
		if (!visited.add(candidate)) return false;
		for (String name : candidate.depends) {
			AddonInfo direct = registry.get(name);
			// Some legacy dependency entries name a module without an AddonInfo.
			if (direct == null) continue;
			if ((direct == dependency) || dependsOn(direct, dependency, visited)) return true;
		}
		return false;
	}

	private void resolve(AddonInfo info, Map<AddonInfo, Visit> visits, List<AddonInfo> path,
							 List<AddonInfo> result, boolean ignoreUnregisteredDependencies) {
		Visit visit = visits.get(info);
		if (visit == Visit.DONE) return;
		if (visit == Visit.ACTIVE) throw cycle(info, path);

		visits.put(info, Visit.ACTIVE);
		path.add(info);
		for (String dependency : info.depends) {
			AddonInfo dependencyInfo = registry.get(dependency);
			if ((dependencyInfo == null) && ignoreUnregisteredDependencies) continue;
			resolve((dependencyInfo != null) ? dependencyInfo : registry.require(dependency),
					visits, path, result, ignoreUnregisteredDependencies);
		}
		path.remove(path.size() - 1);
		visits.put(info, Visit.DONE);
		result.add(info);
	}

	private IllegalStateException cycle(AddonInfo repeated, List<AddonInfo> path) {
		StringBuilder message = new StringBuilder("Addon dependency cycle: ");
		int start = path.indexOf(repeated);
		for (int i = Math.max(0, start); i < path.size(); i++) {
			if (i > Math.max(0, start)) message.append(" -> ");
			message.append(path.get(i).className);
		}
		return new IllegalStateException(message.append(" -> ").append(repeated.className).toString());
	}

	private enum Visit {
		ACTIVE,
		DONE
	}
}
