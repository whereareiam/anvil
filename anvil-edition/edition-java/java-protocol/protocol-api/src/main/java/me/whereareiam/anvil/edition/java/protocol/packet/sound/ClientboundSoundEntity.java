package me.whereareiam.anvil.edition.java.protocol.packet.sound;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.type.world.SoundSource;
import org.jetbrains.annotations.NotNull;

/**
 * A sound the server tells the player to play following an entity.
 */
@Value
@FieldNameConstants
@Builder
public class ClientboundSoundEntity implements ClientboundPacket {
	/**
	 * The sound's namespaced key, such as {@code minecraft:entity.player.levelup}, resolved from its registry ID when
	 * the server referred to a built-in sound. Releases before Minecraft 1.19.3 send only a registry ID, and one the
	 * loaded release does not know is kept as its decimal number.
	 */
	@NotNull String sound;
	/**
	 * The volume slider the sound plays through.
	 */
	@NotNull SoundSource source;
	/**
	 * The protocol ID of the entity the sound follows.
	 */
	@NotNull EntityId entityId;
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
