package me.whereareiam.anvil.engine.scenario.preflight;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.engine.scenario.preflight.ScenarioPreflight.ForwardingGroup;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Negotiates identity forwarding for connected process groups from provider capabilities.
 */
final class ForwardingPlanner {
	@NotNull List<ForwardingGroup> plan(
			@NotNull AnvilScenario scenario,
			@NotNull Map<String, PlatformProvider> providers
	) {
		Map<String, MinecraftProcess> processes = new LinkedHashMap<>();
		scenario.getServers().forEach(server -> processes.put(server.getName(), server));
		scenario.getProxies().forEach(proxy -> processes.put(proxy.getName(), proxy));
		Map<String, Set<String>> neighbors = new LinkedHashMap<>();
		processes.keySet().forEach(name -> neighbors.put(name, new LinkedHashSet<>()));
		for (MinecraftProxy proxy : scenario.getProxies())
			for (String server : proxy.getServers()) {
				neighbors.get(proxy.getName()).add(server);
				neighbors.get(server).add(proxy.getName());
			}

		List<ForwardingGroup> result = new ArrayList<>();
		Set<String> assigned = new LinkedHashSet<>();
		for (MinecraftProxy proxy : scenario.getProxies()) {
			if (assigned.contains(proxy.getName())) continue;

			Set<String> group = connected(proxy.getName(), neighbors);
			result.add(negotiate(proxy, group, processes, providers));
			assigned.addAll(group);
		}
		for (String name : processes.keySet())
			if (!assigned.contains(name)) result.add(new ForwardingGroup(Set.of(name), ForwardingMode.NONE, false));

		return List.copyOf(result);
	}

	private Set<String> connected(String start, Map<String, Set<String>> neighbors) {
		Set<String> result = new LinkedHashSet<>();
		ArrayDeque<String> pending = new ArrayDeque<>();
		pending.add(start);
		while (!pending.isEmpty()) {
			String name = pending.removeFirst();
			if (!result.add(name))
				continue;
			pending.addAll(neighbors.get(name));
		}

		return result;
	}

	private ForwardingGroup negotiate(
			MinecraftProxy entry,
			Set<String> group,
			Map<String, MinecraftProcess> processes,
			Map<String, PlatformProvider> providers
	) {
		var compatible = new ArrayList<>(providers.get(entry.getPlatform()).forwardingModes());
		for (String name : group)
			compatible.retainAll(providers.get(processes.get(name).getPlatform()).forwardingModes());
		if (compatible.isEmpty())
			throw new ScenarioValidationException("No common identity-forwarding mode for processes " + group);

		ForwardingMode mode = compatible.getFirst();
		if (mode != ForwardingMode.NONE)
			for (String name : group)
				if (processes.get(name) instanceof MinecraftServer server && server.isOnlineMode())
					throw new ScenarioValidationException("Forwarded server '" + name + "' must use offline game authentication");
		if (mode == ForwardingMode.MODERN)
			for (String name : group)
				if (processes.get(name) instanceof MinecraftProxy proxy && proxy.isOnlineMode() != entry.isOnlineMode())
					throw new ScenarioValidationException("Modern forwarding requires matching proxy authentication modes: " + group);

		return new ForwardingGroup(Set.copyOf(group), mode, entry.isOnlineMode());
	}

}
