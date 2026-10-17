package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Sounds of the MCProtocolLib releases from 1.21 on. A sound at a position and a sound following an entity each carry
 * a sound that the library has already resolved: a built-in sound from its registry ID or a sound the server named.
 * Each release's {@code SoundPackets} maps the release's sound categories.
 */
public abstract class SoundTranslation implements PacketTranslation<Packet> {
	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundSound.class, ClientboundSoundEntity.class);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
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

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Returns the namespaced key of a sound the library names, whose namespace is {@code minecraft} when it names
	 * none.
	 */
	private static String key(String name) {
		return Key.key(name).asString();
	}

	/**
	 * Maps the release's sound category.
	 *
	 * @param category the library's category
	 * @return the volume slider the category names
	 */
	protected abstract @NotNull SoundSource source(@NotNull SoundCategory category);
}
