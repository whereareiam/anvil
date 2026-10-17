package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.java.api.model.sound.SoundPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Sounds of the MCProtocolLib 1.21 release, the first one in the {@code org.geysermc.mcprotocollib} packages. A
 * sound at a position and a sound following an entity each carry a sound that the library has already resolved: a
 * built-in sound from its registry ID or a sound the server named.
 */
final class SoundPackets implements PacketTranslation<Packet> {
	private static final String DEFAULT_NAMESPACE = "minecraft:";

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundSound.class, ClientboundSoundEntity.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundSoundPacket sound)
			return ClientboundSound.builder()
					.sound(key(sound.getSound().getName()))
					.source(source(sound.getCategory()))
					.position(new SoundPosition(sound.getX(), sound.getY(), sound.getZ()))
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
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	private static String key(String name) {
		return name.indexOf(':') < 0 ? DEFAULT_NAMESPACE + name : name;
	}

	/**
	 * Maps the library's category by name, the library naming most of them in the singular; the user interface
	 * category of later releases is not a constant of this release's enum.
	 */
	private static SoundSource source(Enum<?> category) {
		return switch (category.name()) {
			case "RECORD" -> SoundSource.RECORDS;
			case "BLOCK" -> SoundSource.BLOCKS;
			case "PLAYER" -> SoundSource.PLAYERS;
			default -> SoundSource.valueOf(category.name());
		};
	}
}
