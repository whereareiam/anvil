package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.level.sound.BuiltinSound;
import com.github.steveice10.mc.protocol.data.game.level.sound.CustomSound;
import com.github.steveice10.mc.protocol.data.game.level.sound.Sound;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundCustomSoundPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sounds of the MCProtocolLib 1.18.2 release, which has three sound packets: a built-in sound at a position by
 * registry ID, a named sound at a position, both reported as a sound at a position, and a built-in sound following an
 * entity by registry ID, which this release leaves unresolved. None of them carries a seed.
 */
final class SoundPackets extends SoundTranslation {
	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundSoundPacket sound)
			return ClientboundSound.builder()
					.sound(key(sound.getSound().getName()))
					.source(source(sound.getCategory()))
					.position(new Vec3d(sound.getX(), sound.getY(), sound.getZ()))
					.volume(sound.getVolume())
					.pitch(sound.getPitch())
					.build();
		if (packet instanceof ClientboundCustomSoundPacket sound)
			return ClientboundSound.builder()
					.sound(key(name(sound.getSound())))
					.source(source(sound.getCategory()))
					.position(new Vec3d(sound.getX(), sound.getY(), sound.getZ()))
					.volume(sound.getVolume())
					.pitch(sound.getPitch())
					.build();
		if (!(packet instanceof ClientboundSoundEntityPacket sound)) return null;

		return ClientboundSoundEntity.builder()
				.sound(builtinKey(sound.getSoundId()))
				.source(source(sound.getSoundCategory()))
				.entityId(sound.getEntityId())
				.volume(sound.getVolume())
				.pitch(sound.getPitch())
				.build();
	}

	/**
	 * Resolves a registry ID through the release's sound registry, keeping an ID it does not know as its number.
	 */
	private static String builtinKey(int id) {
		if (id < 0 || id >= BuiltinSound.VALUES.length) return Integer.toString(id);

		return key(BuiltinSound.VALUES[id].getName());
	}

	/**
	 * Names a sound of the named-sound packet, which this release's sound interface leaves to its two kinds.
	 */
	private static String name(Sound sound) {
		return sound instanceof BuiltinSound builtin ? builtin.getName() : ((CustomSound) sound).getName();
	}
}
