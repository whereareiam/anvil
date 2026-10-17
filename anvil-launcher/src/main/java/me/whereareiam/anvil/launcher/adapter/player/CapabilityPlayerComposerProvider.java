package me.whereareiam.anvil.launcher.adapter.player;

import me.whereareiam.anvil.capability.player.LibraryCapabilityRuntimes;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposerProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Composes independently owned player capabilities for one scenario, with one capability runtime per protocol
 * library.
 */
public final class CapabilityPlayerComposerProvider implements ProtocolPlayerComposerProvider {
	@Override
	public @NotNull String id() {
		return "capabilities";
	}

	/**
	 * Creates capability composition for standalone protocol consumers. Each library's capability runtime is
	 * created when its first player is composed.
	 *
	 * @return composer without validated libraries
	 */
	@Override
	public @NotNull ProtocolPlayerComposer create() {
		return create(List.of());
	}

	/**
	 * Creates scenario composition and validates the capability graph of each listed library before
	 * any player exists. Other libraries' runtimes are created when their first player is composed.
	 *
	 * @param libraries protocol libraries whose capability graphs are validated immediately
	 * @return composer for the scenario's players
	 */
	public @NotNull ProtocolPlayerComposer create(@NotNull Collection<String> libraries) {
		var runtimes = new LibraryCapabilityRuntimes(libraries);

		return (player, connection, metadata, onDestroyed) -> runtimes.of(player.libraryId())
				.compose(new ProtocolPlayerAdapter(player, metadata), connection, onDestroyed);
	}
}
