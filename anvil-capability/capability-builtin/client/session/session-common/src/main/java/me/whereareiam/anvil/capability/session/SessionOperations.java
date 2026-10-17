package me.whereareiam.anvil.capability.session;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;

/**
 * Typed session operations shared by the host session and the worker's session binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SessionOperations {
	/**
	 * Starts the native login sequence.
	 */
	public static final ChannelOperation<Void, Void> CONNECT = new ChannelOperation<>("session.connect", Void.class, Void.class);
	/**
	 * Disconnects the current native session.
	 */
	public static final ChannelOperation<Void, Void> DISCONNECT = new ChannelOperation<>("session.disconnect", Void.class, Void.class);
	/**
	 * Replaces the native session and starts login again.
	 */
	public static final ChannelOperation<Void, Void> REJOIN = new ChannelOperation<>("session.rejoin", Void.class, Void.class);
}
