package me.whereareiam.anvil.protocol.api.player;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Composes the public simulated-player facade around a backend-owned protocol player.
 *
 * <p>The protocol backend remains responsible for native transport. A composer may add
 * dependency-discovered capabilities without making the backend depend on their implementation.</p>
 */
public interface ProtocolPlayerComposer {
	/**
	 * Composes one backend player.
	 *
	 * @param player backend-owned protocol player
	 * @param services scenario-scoped services visible to the composer
	 * @param onDestroyed callback invoked after permanent destruction
	 * @return public simulated player facade
	 */
	@NotNull SimulatedPlayer compose(
			@NotNull ProtocolPlayer player,
			@NotNull Collection<?> services,
			@NotNull Consumer<SimulatedPlayer> onDestroyed
	);

	/**
	 * Discovers the installed player composer.
	 *
	 * @param protocolId resolved protocol-provider ID
	 * @return selected player composer
	 * @throws IllegalStateException when no composer is installed
	 */
	static @NotNull ProtocolPlayerComposer discover(@NotNull String protocolId) {
		ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
		ProtocolPlayerComposerProvider provider = contextLoader == null
				? null
				: ServiceLoader.load(ProtocolPlayerComposerProvider.class, contextLoader)
						.findFirst().orElse(null);
		if (provider != null)
			return provider.create(protocolId);
		return ServiceLoader.load(
				ProtocolPlayerComposerProvider.class,
				ProtocolPlayerComposer.class.getClassLoader()
			)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No Anvil protocol-player composer is installed"))
				.create(protocolId);
	}
}
