package me.whereareiam.anvil.capability.vitals.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.vitals.model.Death;
import me.whereareiam.anvil.capability.vitals.model.VitalStatus;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * The channel contract between the host vitals capability and its worker extensions: the capability ID both register
 * under, the operation the host requests and the events the worker emits.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VitalsChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.vitals";
	/**
	 * Asks the server to respawn the player on its current connection.
	 */
	public static final OperationDescriptor<Void, Void> RESPAWN = new OperationDescriptor<>(
			"vitals.respawn", Void.class, Void.class
	);
	/**
	 * The player's health, food or saturation changed, or a new connection started with the initial values.
	 */
	public static final EventDescriptor<VitalStatus> STATUS = new EventDescriptor<>("vitals.status", VitalStatus.class);
	/**
	 * The server told the player that it died.
	 */
	public static final EventDescriptor<Death> DIED = new EventDescriptor<>("vitals.died", Death.class);
}
