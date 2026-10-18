package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.level.sound.SoundCategory;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Sounds of the MCProtocolLib releases from 1.18.2 until 1.19.4, which report a sound at a position and a sound
 * following an entity and name the sound categories alike. Each release's {@code SoundPackets} reads its sound packets,
 * which Minecraft 1.19.3 reduced to one packet for each kind that carries the sound and a seed.
 */
public abstract class SoundTranslation implements PacketTranslation<Packet> {
	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundSound.class, ClientboundSoundEntity.class);
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Returns the namespaced key of a sound the library names, whose namespace is {@code minecraft} when it names
	 * none.
	 *
	 * @param name the sound's name
	 * @return the sound's key
	 */
	protected static @NotNull String key(@NotNull String name) {
		return Key.key(name).asString();
	}

	/**
	 * Maps the library's sound category, which it names mostly in the singular.
	 *
	 * @param category the library's category
	 * @return the volume slider the category names
	 */
	protected static @NotNull SoundSource source(@NotNull SoundCategory category) {
		return switch (category) {
			case MASTER -> SoundSource.MASTER;
			case MUSIC -> SoundSource.MUSIC;
			case RECORD -> SoundSource.RECORDS;
			case WEATHER -> SoundSource.WEATHER;
			case BLOCK -> SoundSource.BLOCKS;
			case HOSTILE -> SoundSource.HOSTILE;
			case NEUTRAL -> SoundSource.NEUTRAL;
			case PLAYER -> SoundSource.PLAYERS;
			case AMBIENT -> SoundSource.AMBIENT;
			case VOICE -> SoundSource.VOICE;
		};
	}
}
