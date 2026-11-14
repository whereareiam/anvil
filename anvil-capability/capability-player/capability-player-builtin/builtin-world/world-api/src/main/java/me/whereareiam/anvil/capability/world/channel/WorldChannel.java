package me.whereareiam.anvil.capability.world.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.world.model.DimensionChange;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;

/**
 * The channel contract between the host world capability and its worker extensions: the capability ID both register
 * under and the events the worker emits.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorldChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.world";
	/**
	 * The player's current connection joined a level or respawned.
	 */
	public static final EventDescriptor<DimensionChange> DIMENSION = new EventDescriptor<>(
			"world.dimension", DimensionChange.class
	);
	/**
	 * The player's current connection ended while it was in a level, with the key of that level.
	 */
	public static final EventDescriptor<String> LEFT = new EventDescriptor<>("world.left", String.class);
}
