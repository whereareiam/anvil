package me.whereareiam.anvil.protocol.api.player;

import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import org.jetbrains.annotations.NotNull;

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
	 * @param observation player identity and route observation
	 * @param onDestroyed callback invoked after permanent destruction
	 * @return public simulated player facade
	 */
	@NotNull SimulatedPlayer compose(
			@NotNull ProtocolPlayer player,
			@NotNull PlayerObservation observation,
			@NotNull Consumer<SimulatedPlayer> onDestroyed
	);

}
