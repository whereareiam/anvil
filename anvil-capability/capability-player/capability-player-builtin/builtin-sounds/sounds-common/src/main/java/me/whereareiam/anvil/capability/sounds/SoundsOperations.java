package me.whereareiam.anvil.capability.sounds;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;

/**
 * The heard-sound event shared by the host sounds capability and the worker's sounds binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SoundsOperations {
	/**
	 * A sound the player's current native session was told to play.
	 */
	public static final EventDescriptor<HeardSound> HEARD = new EventDescriptor<>("sounds.heard", HeardSound.class);
}
