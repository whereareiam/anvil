package me.whereareiam.anvil.launcher.assembly;

import me.whereareiam.anvil.launcher.adapter.player.CapabilityPlayerComposerProvider;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposerProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Selects the installed composition provider and hands the scenario's libraries to the built-in composer.
 */
public final class PlayerComposition {
	/**
	 * Discovers composition in provider order, preserving standalone protocol composer implementations.
	 * The built-in composer validates the capability graph of every listed library before players exist
	 * and the graph of any other library when its first player is composed.
	 *
	 * @param libraries libraries the scenario's players use unless they declare their own
	 * @return composer bound to this scenario
	 */
	public static @NotNull ProtocolPlayerComposer create(@NotNull Collection<String> libraries) {
		var provider = new ProviderDiscovery().required(ProtocolPlayerComposerProvider.class, "protocol-player composer");

		return create(libraries, provider);
	}

	static @NotNull ProtocolPlayerComposer create(
			@NotNull Collection<String> libraries,
			@NotNull ProtocolPlayerComposerProvider provider
	) {
		if (provider instanceof CapabilityPlayerComposerProvider capabilities)
			return capabilities.create(libraries);

		return provider.create();
	}
}
