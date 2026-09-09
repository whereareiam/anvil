package me.whereareiam.anvil.capability.session;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;

/**
 * Typed session operations implemented by native capability bindings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SessionOperations {
	/**
	 * Executes the connect operation with a registered request schema.
	 */
	public static final ChannelOperation<Void, Void> CONNECT = new ChannelOperation<>("session.connect", Void.class, Void.class);
	/**
	 * Executes the disconnect operation with a registered request schema.
	 */
	public static final ChannelOperation<Void, Void> DISCONNECT = new ChannelOperation<>("session.disconnect", Void.class, Void.class);
	/**
	 * Executes the rejoin operation with a registered request schema.
	 */
	public static final ChannelOperation<Void, Void> REJOIN = new ChannelOperation<>("session.rejoin", Void.class, Void.class);
}
