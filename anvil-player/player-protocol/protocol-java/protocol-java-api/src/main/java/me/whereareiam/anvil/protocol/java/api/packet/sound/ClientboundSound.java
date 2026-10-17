package me.whereareiam.anvil.protocol.java.api.packet.sound;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.sound.SoundPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
import org.jetbrains.annotations.NotNull;

/**
 * A sound the server tells the player to play at a position. Releases before Minecraft 1.19.3 send only built-in
 * sounds by their registry ID in it and name other sounds in {@link ClientboundCustomSound}.
 */
@Value
@Builder
public class ClientboundSound implements ClientboundPacket {
	/**
	 * The sound's namespaced key, such as {@code minecraft:entity.player.levelup}, resolved from its registry ID when
	 * the server referred to a built-in sound.
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
	/**
	 * The seed that picks among the sound's variants; releases before Minecraft 1.19 do not send it and report 0.
	 */
	long seed;
}
