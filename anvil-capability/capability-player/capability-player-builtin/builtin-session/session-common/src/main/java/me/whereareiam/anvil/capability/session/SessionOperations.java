package me.whereareiam.anvil.capability.session;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * Typed session operations shared by the host session and the worker's session binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SessionOperations {
	/**
	 * Starts the native login sequence.
	 */
	public static final OperationDescriptor<Void, Void> CONNECT = new OperationDescriptor<>(
			"session.connect", Void.class, Void.class
	);
	/**
	 * Disconnects the current native session.
	 */
	public static final OperationDescriptor<Void, Void> DISCONNECT = new OperationDescriptor<>(
			"session.disconnect", Void.class, Void.class
	);
	/**
	 * Replaces the native session and starts login again.
	 */
	public static final OperationDescriptor<Void, Void> REJOIN = new OperationDescriptor<>(
			"session.rejoin", Void.class, Void.class
	);
}
