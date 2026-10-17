package me.whereareiam.anvil.protocol.api.player;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Service-provider contract for the public player composition layer.
 */
public interface ProtocolPlayerComposerProvider {
	/**
	 * Returns the stable composer identifier.
	 *
	 * @return composer identifier
	 */
	@NotNull String id();

	/**
	 * Creates a composer for players of every installed protocol library. The composer selects
	 * library-specific behavior for each player from {@link ProtocolPlayer#libraryId()}.
	 *
	 * @return player composer
	 */
	@NotNull ProtocolPlayerComposer create();

	/**
	 * Creates a composer for a scenario whose players select the given libraries unless they declare their own.
	 * A composer may prepare or validate what it needs for those libraries before any player exists; players of
	 * other libraries are still composed when they are created.
	 *
	 * @param libraries protocol libraries the scenario's players use by default
	 * @return player composer
	 */
	default @NotNull ProtocolPlayerComposer create(@NotNull Collection<String> libraries) {
		return create();
	}
}
