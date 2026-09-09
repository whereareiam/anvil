package me.whereareiam.anvil.capability.protocol.api.player.worker;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Installs capability-owned behavior against an explicitly selected native backend SDK.
 * The generic type is the actual SDK context, not an Anvil packet abstraction.
 *
 * @param <B> external SDK context type
 */
public interface WorkerExtension<B> {
	/**
	 * Returns the capability ID also declared by the host-side provider.
	 *
	 * @return stable capability ID
	 */
	@NotNull String id();

	/**
	 * Returns the protocol backend this extension targets.
	 *
	 * @return backend identifier
	 */
	@NotNull String backendId();

	/**
	 * Returns the exact native context type required by this binding.
	 *
	 * @return external SDK class
	 */
	@NotNull Class<B> backendType();

	/**
	 * Determines compatibility with an exact Minecraft protocol version.
	 *
	 * @param protocolNumber exact protocol number
	 * @return whether this extension supports the protocol
	 */
	default boolean supports(int protocolNumber) {
		return true;
	}

	/**
	 * Creates isolated state and handlers for one player before that player is exposed.
	 * Operations may be registered only during the player-binding phase. Their IDs must be
	 * namespaced and unique across that player's bindings. The supplied registry enforces these
	 * restrictions while the transport bridge owns schema conversion and response validation.
	 *
	 * @param player native lifecycle, SDK access, and event delivery for this player's binding
	 * @param operations typed operation registration for this player
	 * @return owned binding closed on player destruction or rollback after a later binding fails
	 */
	@NotNull WorkerBinding bind(@NotNull PlayerBindingContext<B> player, @NotNull OperationRegistry operations);
}
