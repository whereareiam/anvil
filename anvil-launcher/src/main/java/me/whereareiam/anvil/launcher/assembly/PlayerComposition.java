package me.whereareiam.anvil.launcher.assembly;

import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposerProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Selects the installed composition provider and hands it the scenario's libraries.
 */
public final class PlayerComposition {
	/**
	 * Discovers composition in provider order and creates the first provider's composer for the scenario's
	 * libraries. The built-in composer validates the capability graph of every listed library before players exist
	 * and the graph of any other library when its first player is composed.
	 *
	 * @param libraries libraries the scenario's players use unless they declare their own
	 * @return composer bound to this scenario
	 */
	public static @NotNull ProtocolPlayerComposer create(@NotNull Collection<String> libraries) {
		var provider = new ProviderDiscovery().required(ProtocolPlayerComposerProvider.class, "protocol-player composer");

		return provider.create(libraries);
	}
}
