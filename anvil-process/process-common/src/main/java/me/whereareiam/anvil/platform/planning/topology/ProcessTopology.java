package me.whereareiam.anvil.platform.planning.topology;

import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the undirected process topology used for forwarding negotiation. */
public final class ProcessTopology {
	private final Map<String, MinecraftProcess> processes;
	private final Map<String, Set<String>> neighbors;

	private ProcessTopology(@NotNull AnvilScenario scenario) {
		Map<String, MinecraftProcess> processes = new LinkedHashMap<>();
		scenario.getServers().forEach(server -> processes.put(server.getName(), server));
		scenario.getProxies().forEach(proxy -> processes.put(proxy.getName(), proxy));
		this.processes = Collections.unmodifiableMap(processes);

		Map<String, Set<String>> neighbors = new LinkedHashMap<>();
		processes.keySet().forEach(name -> neighbors.put(name, new LinkedHashSet<>()));
		for (MinecraftProxy proxy : scenario.getProxies()) {
			for (String server : proxy.getServers()) {
				neighbors.get(proxy.getName()).add(server);
				neighbors.get(server).add(proxy.getName());
			}
		}

		Map<String, Set<String>> immutable = new LinkedHashMap<>();
		neighbors.forEach((name, connected) -> immutable.put(name, ordered(connected)));
		this.neighbors = Collections.unmodifiableMap(immutable);
	}

	public static @NotNull ProcessTopology from(@NotNull AnvilScenario scenario) {
		return new ProcessTopology(scenario);
	}

	@NotNull MinecraftProcess process(@NotNull String name) {
		return processes.get(name);
	}

	@NotNull List<Component> components() {
		List<Component> result = new ArrayList<>();
		Set<String> assigned = new LinkedHashSet<>();
		for (MinecraftProxy proxy : processes.values().stream()
				.filter(MinecraftProxy.class::isInstance).map(MinecraftProxy.class::cast).toList()) {
			if (assigned.contains(proxy.getName())) continue;

			Set<String> connected = connected(proxy.getName());
			result.add(new Component(ordered(connected), proxy));
			assigned.addAll(connected);
		}

		for (String name : processes.keySet())
			if (!assigned.contains(name)) result.add(new Component(Set.of(name), null));

		return List.copyOf(result);
	}

	private Set<String> connected(@NotNull String start) {
		Set<String> result = new LinkedHashSet<>();
		ArrayDeque<String> pending = new ArrayDeque<>();
		pending.add(start);
		while (!pending.isEmpty()) {
			String name = pending.removeFirst();
			if (!result.add(name)) continue;
			pending.addAll(neighbors.get(name));
		}

		return result;
	}

	private static @NotNull Set<String> ordered(@NotNull Set<String> values) {
		return Collections.unmodifiableSet(new LinkedHashSet<>(values));
	}

	record Component(@NotNull Set<String> processes, @Nullable MinecraftProxy entry) { }
}
