package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.SoundTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sounds of the MCProtocolLib 1.19.4 release. A sound at a position and a sound following an entity each carry a
 * sound that the library has already resolved, a built-in sound from its registry ID or a sound the server named, and
 * the seed of its variation.
 */
public final class SoundPackets extends SoundTranslation {
	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundSoundPacket sound)
			return ClientboundSound.builder()
					.sound(key(sound.getSound().getName()))
					.source(source(sound.getCategory()))
					.position(new Vec3d(sound.getX(), sound.getY(), sound.getZ()))
					.volume(sound.getVolume())
					.pitch(sound.getPitch())
					.seed(sound.getSeed())
					.build();
		if (!(packet instanceof ClientboundSoundEntityPacket sound)) return null;

		return ClientboundSoundEntity.builder()
				.sound(key(sound.getSound().getName()))
				.source(source(sound.getCategory()))
				.entityId(sound.getEntityId())
				.volume(sound.getVolume())
				.pitch(sound.getPitch())
				.seed(sound.getSeed())
				.build();
	}
}
