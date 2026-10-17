package me.whereareiam.anvil.capability.protocol.api.player;

import me.whereareiam.anvil.capability.api.player.CapabilityPlayer;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Protocol-backed player input supplying native channels and external SDK services to composition.
 * Outer assembly adapters supply this view without exposing another Anvil family's API.
 * The composed player retains the library-owned lifetime defined by {@link CapabilityPlayer}.
 */
public interface ProtocolCapabilityPlayer extends CapabilityPlayer {
	/**
	 * Returns the ID of the protocol library selected for this player, such as {@code mcprotocol}. Composition
	 * names it when the library's worker does not install a capability.
	 *
	 * @return protocol-library identifier
	 */
	@NotNull String libraryId();

	/**
	 * Returns the typed native channel when the selected library supports worker capabilities.
	 *
	 * @return optional native channel
	 */
	@NotNull Optional<CapabilityChannel> channel();

	/**
	 * Finds an external library's own stable service. Anvil family services must be wired
	 * through explicit assembly adapters, rather than passed through this extension hook.
	 *
	 * @param type external library service type
	 * @param <T> service type
	 * @return matching external service
	 */
	@NotNull <T> Optional<T> findService(@NotNull Class<T> type);
}
