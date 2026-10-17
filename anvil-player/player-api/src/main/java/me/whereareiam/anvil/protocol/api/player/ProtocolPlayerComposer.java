package me.whereareiam.anvil.protocol.api.player;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Composes the public simulated-player facade around a library-owned protocol player.
 *
 * <p>The protocol library remains responsible for native transport. A composer may add
 * dependency-discovered capabilities without making the library depend on their implementation.</p>
 */
public interface ProtocolPlayerComposer {
	/**
	 * Composes one library-owned player.
	 *
	 * @param player library-owned protocol player
	 * @param connection how the player connects, with its target process always named, which capabilities that
	 *                   follow the player's connection, such as the Server capability, start from
	 * @param metadata optional presentation details supplied by the player declaration
	 * @param onDestroyed callback invoked after permanent destruction
	 * @return public simulated player facade
	 */
	@NotNull SimulatedPlayer compose(
			@NotNull ProtocolPlayer player,
			@NotNull PlayerConnection connection,
			@Nullable PresentationMetadata metadata,
			@NotNull Consumer<SimulatedPlayer> onDestroyed
	);

}
