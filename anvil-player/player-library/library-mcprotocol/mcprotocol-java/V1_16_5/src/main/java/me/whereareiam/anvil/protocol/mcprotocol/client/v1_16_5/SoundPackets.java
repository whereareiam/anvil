package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.MagicValues;
import com.github.steveice10.mc.protocol.data.UnmappedValueException;
import com.github.steveice10.mc.protocol.data.game.world.sound.BuiltinSound;
import com.github.steveice10.mc.protocol.data.game.world.sound.CustomSound;
import com.github.steveice10.mc.protocol.data.game.world.sound.Sound;
import com.github.steveice10.mc.protocol.data.game.world.sound.SoundCategory;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerEntitySoundEffectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerPlayBuiltinSoundPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerPlaySoundPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
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
 * Sounds of the MCProtocolLib releases from 1.16.5 until 1.17.1, which have three sound packets: a built-in sound at a
 * position, a named sound at a position, both reported as a sound at a position, and a built-in sound following an
 * entity by registry ID, which the translation resolves through the library's own mapping. None of them carries a
 * seed.
 */
public final class SoundPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundSound.class, ClientboundSoundEntity.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ServerPlayBuiltinSoundPacket sound)
			return ClientboundSound.builder()
					.sound(key(sound.getSound().getName()))
					.source(source(sound.getCategory()))
					.position(new Vec3d(sound.getX(), sound.getY(), sound.getZ()))
					.volume(sound.getVolume())
					.pitch(sound.getPitch())
					.build();
		if (packet instanceof ServerPlaySoundPacket sound)
			return ClientboundSound.builder()
					.sound(key(name(sound.getSound())))
					.source(source(sound.getCategory()))
					.position(new Vec3d(sound.getX(), sound.getY(), sound.getZ()))
					.volume(sound.getVolume())
					.pitch(sound.getPitch())
					.build();
		if (!(packet instanceof ServerEntitySoundEffectPacket sound)) return null;

		return ClientboundSoundEntity.builder()
				.sound(builtinKey(sound.getSoundId()))
				.source(source(sound.getSoundCategory()))
				.entityId(sound.getEntityId())
				.volume(sound.getVolume())
				.pitch(sound.getPitch())
				.build();
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Resolves a registry ID through the library's mapping of built-in sounds, keeping an ID it does not map as its
	 * number.
	 */
	private static String builtinKey(int id) {
		try {
			return key(MagicValues.<BuiltinSound>key(BuiltinSound.class, id).getName());
		} catch (UnmappedValueException unmapped) {
			return Integer.toString(id);
		}
	}

	/**
	 * Names a sound of the named-sound packet, which this release's sound interface leaves to its two kinds.
	 */
	private static String name(Sound sound) {
		return sound instanceof BuiltinSound builtin ? builtin.getName() : ((CustomSound) sound).getName();
	}

	/**
	 * Returns the namespaced key of a sound the library names, whose namespace is {@code minecraft} when it names
	 * none.
	 */
	private static String key(String name) {
		return Key.key(name).asString();
	}

	private static SoundSource source(SoundCategory category) {
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
