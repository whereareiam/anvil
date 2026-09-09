package me.whereareiam.anvil.capability.protocol.api.player;

import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Player capability context with borrowed native-channel and external protocol SDK access.
 * The player owns the capability and its registered cleanup; the protocol backend owns its services.
 */
public interface ProtocolPlayerCapabilityContext extends PlayerCapabilityContext {
	/**
	 * Returns the typed channel required by a packet-backed capability.
	 *
	 * @return native operation channel
	 * @throws CapabilityException when the backend has no channel
	 */
	@NotNull CapabilityChannel channel();

	/**
	 * Finds an explicitly supplied external backend SDK service. Anvil family services
	 * are connected through assembly adapters and are not supplied through this hook.
	 *
	 * @param type external backend service type
	 * @param <T> service type
	 * @return matching service when available
	 */
	@NotNull <T> Optional<T> findService(@NotNull Class<T> type);

	/**
	 * Resolves an external backend SDK service required by this capability.
	 *
	 * @param type external backend service type
	 * @param <T> service type
	 * @return matching service
	 * @throws CapabilityException when the selected backend does not expose the service
	 */
	default @NotNull <T> T requireService(@NotNull Class<T> type) {
		return findService(type).orElseThrow(() -> new CapabilityException(
				"Player '" + playerName() + "' does not expose required service " + type.getName()
		));
	}
}
