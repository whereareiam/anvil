package me.whereareiam.anvil.protocol.java.api.packet.sound;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.sound.SoundPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
import org.jetbrains.annotations.NotNull;

/**
 * A sound the server names to play at a position, a packet of releases before Minecraft 1.19.3; later releases name
 * such sounds in {@link ClientboundSound}.
 */
@Value
@Builder
public class ClientboundCustomSound implements ClientboundPacket {
	/**
	 * The sound's namespaced key, such as {@code example:alert}; a name sent without a namespace is in the
	 * {@code minecraft} namespace.
	 */
	@NotNull String sound;
	/**
	 * The volume slider the sound plays through.
	 */
	@NotNull SoundSource source;
	/**
	 * Where the sound plays.
	 */
	@NotNull SoundPosition position;
	/**
	 * The sound's volume, where {@code 1} is full volume and higher values carry further.
	 */
	float volume;
	/**
	 * The sound's pitch, from {@code 0.5} to {@code 2}, where {@code 1} is unchanged.
	 */
	float pitch;
}
