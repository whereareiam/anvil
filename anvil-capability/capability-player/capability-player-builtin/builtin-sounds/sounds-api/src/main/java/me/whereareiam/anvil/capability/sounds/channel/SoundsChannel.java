package me.whereareiam.anvil.capability.sounds.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;

/**
 * The channel contract between the host sounds capability and its worker extensions: the capability ID both register
 * under and the event the worker emits.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SoundsChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.sounds";
	/**
	 * A sound the player's current connection was told to play.
	 */
	public static final EventDescriptor<HeardSound> HEARD = new EventDescriptor<>("sounds.heard", HeardSound.class);
}
