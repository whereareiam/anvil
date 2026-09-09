package me.whereareiam.anvil.platform.planning.topology;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Negotiates one forwarding mode for each connected process component. */
public final class ForwardingNegotiator {
	public @NotNull ForwardingPlan negotiate(
			@NotNull ProcessTopology topology,
			@NotNull Map<String, PlatformProvider> providers
	) {
		List<ForwardingPlan.Group> groups = new ArrayList<>();
		for (ProcessTopology.Component component : topology.components()) {
			if (component.entry() == null) {
				groups.add(new ForwardingPlan.Group(component.processes(), ForwardingMode.NONE, false));
				continue;
			}

			groups.add(negotiate(component, topology, providers));
		}

		return new ForwardingPlan(groups);
	}

	private ForwardingPlan.Group negotiate(
			@NotNull ProcessTopology.Component component,
			@NotNull ProcessTopology topology,
			@NotNull Map<String, PlatformProvider> providers
	) {
		MinecraftProxy entry = component.entry();
		var compatible = new ArrayList<>(provider(entry, providers).forwardingModes());
		for (String name : component.processes())
			compatible.retainAll(provider(topology.process(name), providers).forwardingModes());

		if (compatible.isEmpty()) {
			throw new ScenarioValidationException("No common identity-forwarding mode for processes " + component.processes());
		}

		ForwardingMode mode = compatible.getFirst();
		if (mode != ForwardingMode.NONE)
			for (String name : component.processes())
				if (topology.process(name) instanceof MinecraftServer server && server.isOnlineMode())
					throw new ScenarioValidationException("Forwarded server '" + name + "' must use offline game authentication");

		if (mode == ForwardingMode.MODERN)
			for (String name : component.processes())
				if (topology.process(name) instanceof MinecraftProxy proxy && proxy.isOnlineMode() != entry.isOnlineMode())
					throw new ScenarioValidationException("Modern forwarding requires matching proxy authentication modes: " + component.processes());

		return new ForwardingPlan.Group(component.processes(), mode, entry.isOnlineMode());
	}

	private PlatformProvider provider(@NotNull MinecraftProcess process, @NotNull Map<String, PlatformProvider> providers) {
		return providers.get(process.getPlatform());
	}
}
