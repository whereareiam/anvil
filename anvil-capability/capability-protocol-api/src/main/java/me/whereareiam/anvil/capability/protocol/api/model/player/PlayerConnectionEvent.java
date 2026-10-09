package me.whereareiam.anvil.capability.protocol.api.model.player;

import me.whereareiam.anvil.api.type.DisconnectCause;
import lombok.Value;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports a native player's connection transition, including an optional disconnect reason.
 */
@Value
public class PlayerConnectionEvent {
	/**
	 * Connection state events emitted by the protocol library after login or disconnect.
	 */
	public static final @NotNull EventDescriptor<PlayerConnectionEvent> CHANGED = new EventDescriptor<>("player.connection", PlayerConnectionEvent.class);
	/**
	 * Permanent destruction event emitted after the protocol library releases its resources.
	 */
	public static final @NotNull EventDescriptor<Void> DESTROYED = new EventDescriptor<>("player.destroyed", Void.class);

	/**
	 * Whether the native player is connected after this transition.
	 */
	boolean connected;
	/**
	 * Optional explanation supplied for a disconnect.
	 */
	@Nullable String reason;
	/**
	 * What ended the connection; null while connected and for libraries that do not report it.
	 */
	@Nullable DisconnectCause cause;
}
