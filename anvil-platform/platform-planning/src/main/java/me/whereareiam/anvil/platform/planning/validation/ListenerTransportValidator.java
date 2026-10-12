package me.whereareiam.anvil.platform.planning.validation;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.type.network.ListenerTransport;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Refuses a proxy whose backend servers listen on another game transport than the proxy itself.
 * A proxy forwards each player connection to a backend over the transport it accepted it on, so a TCP proxy
 * cannot reach a UDP server, and the other way round.
 */
public final class ListenerTransportValidator {
	/**
	 * Validates every proxy of the declarations against its backend servers.
	 *
	 * @param processes declarations validated by {@link ProcessDeclarationValidator}, by process name
	 * @param providers platform providers by platform identifier
	 * @throws ScenarioValidationException when a proxy and one of its backends use different transports
	 */
	public void validate(
			@NotNull Map<String, MinecraftProcess> processes,
			@NotNull Map<String, PlatformProvider> providers
	) {
		for (MinecraftProcess process : processes.values()) {
			if (!(process instanceof MinecraftProxy proxy)) continue;

			ListenerTransport transport = providers.get(proxy.getPlatform()).gameTransport();
			for (String name : proxy.getServers()) {
				MinecraftProcess server = processes.get(name);
				ListenerTransport backend = providers.get(server.getPlatform()).gameTransport();
				if (backend == transport) continue;

				throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' accepts players over " + transport
						+ " but its server '" + name + "' (platform '" + server.getPlatform() + "') listens on " + backend
						+ "; a proxy reaches only servers that listen on the same transport");
			}
		}
	}
}
