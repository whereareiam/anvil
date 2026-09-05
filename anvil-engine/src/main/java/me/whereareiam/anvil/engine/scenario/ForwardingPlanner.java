package me.whereareiam.anvil.engine.scenario;

import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.engine.AnvilException;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Negotiates identity forwarding for connected process groups from provider capabilities.
 */
public final class ForwardingPlanner {
	public @NotNull Map<String, ForwardingConfiguration> plan(
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

		Map<String, ForwardingConfiguration> result = new LinkedHashMap<>();
		for (MinecraftProxy proxy : scenario.getProxies()) {
			if (result.containsKey(proxy.getName()))
				continue;
			Set<String> group = connected(proxy.getName(), neighbors);
			ForwardingConfiguration forwarding = negotiate(proxy, group, processes, providers);
			group.forEach(name -> result.put(name, forwarding));
		}
		ForwardingConfiguration direct = ForwardingConfiguration.builder().build();
		processes.keySet().forEach(name -> result.putIfAbsent(name, direct));
		return Map.copyOf(result);
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

	private ForwardingConfiguration negotiate(
			MinecraftProxy entry,
			Set<String> group,
			Map<String, MinecraftProcess> processes,
			Map<String, PlatformProvider> providers
	) {
		var compatible = new ArrayList<>(providers.get(entry.getPlatform()).forwardingModes());
		for (String name : group)
			compatible.retainAll(providers.get(processes.get(name).getPlatform()).forwardingModes());
		if (compatible.isEmpty())
			throw new AnvilException("No common identity-forwarding mode for processes " + group);

		ForwardingMode mode = compatible.getFirst();
		if (mode != ForwardingMode.NONE)
			for (String name : group)
				if (processes.get(name) instanceof MinecraftServer server && server.isOnlineMode())
					throw new AnvilException("Forwarded server '" + name + "' must use offline game authentication");
		if (mode == ForwardingMode.MODERN)
			for (String name : group)
				if (processes.get(name) instanceof MinecraftProxy proxy && proxy.isOnlineMode() != entry.isOnlineMode())
					throw new AnvilException("Modern forwarding requires matching proxy authentication modes: " + group);
		return ForwardingConfiguration.builder().mode(mode).proxyOnlineMode(entry.isOnlineMode())
				.secret(mode == ForwardingMode.NONE ? null : secret()).build();
	}

	private String secret() {
		byte[] secret = new byte[32];
		new SecureRandom().nextBytes(secret);
		return HexFormat.of().formatHex(secret);
	}
}
