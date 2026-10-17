package me.whereareiam.anvil.capability.sounds.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.level.sound.BuiltinSound;
import com.github.steveice10.mc.protocol.data.game.level.sound.CustomSound;
import com.github.steveice10.mc.protocol.data.game.level.sound.Sound;
import com.github.steveice10.mc.protocol.data.game.level.sound.SoundCategory;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundCustomSoundPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.event.session.SessionListener;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.model.SoundPosition;
import me.whereareiam.anvil.capability.sounds.packet.SoundsPackets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Sound packets of the MCProtocolLib release for Minecraft 1.18.2, which has three of them: a built-in sound at a
 * position by registry ID, a named sound at a position, and a built-in sound following an entity by registry ID,
 * which this release leaves unresolved. Listeners follow packetlib 2.1.
 */
public final class McProtocolSoundsPackets implements SoundsPackets<Session> {
	private static final String DEFAULT_NAMESPACE = "minecraft:";

	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public @NotNull Runnable listen(@NotNull Session session, @NotNull Consumer<HeardSound> heard) {
		SessionListener listener = new SessionAdapter() {
			@Override
			public void packetReceived(Session received, Packet packet) {
				if (!session.isConnected()) return;

				HeardSound sound = sound(packet);
				if (sound != null) heard.accept(sound);
			}
		};
		session.addListener(listener);
		return () -> session.removeListener(listener);
	}

	private static @Nullable HeardSound sound(Packet packet) {
		if (packet instanceof ClientboundSoundPacket sound)
			return HeardSound.builder()
					.key(key(sound.getSound().getName()))
					.category(category(sound.getCategory()))
					.volume(sound.getVolume())
					.pitch(sound.getPitch())
					.position(new SoundPosition(sound.getX(), sound.getY(), sound.getZ()))
					.build();
		if (packet instanceof ClientboundCustomSoundPacket sound)
			return HeardSound.builder()
					.key(key(name(sound.getSound())))
					.category(category(sound.getCategory()))
					.volume(sound.getVolume())
					.pitch(sound.getPitch())
					.position(new SoundPosition(sound.getX(), sound.getY(), sound.getZ()))
					.build();
		if (packet instanceof ClientboundSoundEntityPacket sound)
			return HeardSound.builder()
					.key(builtinKey(sound.getSoundId()))
					.category(category(sound.getSoundCategory()))
					.volume(sound.getVolume())
					.pitch(sound.getPitch())
					.entityId(sound.getEntityId())
					.build();
		return null;
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

	private static String key(String name) {
		return name.indexOf(':') < 0 ? DEFAULT_NAMESPACE + name : name;
	}

	private static me.whereareiam.anvil.capability.sounds.type.SoundCategory category(SoundCategory category) {
		return switch (category) {
			case MASTER -> me.whereareiam.anvil.capability.sounds.type.SoundCategory.MASTER;
			case MUSIC -> me.whereareiam.anvil.capability.sounds.type.SoundCategory.MUSIC;
			case RECORD -> me.whereareiam.anvil.capability.sounds.type.SoundCategory.RECORDS;
			case WEATHER -> me.whereareiam.anvil.capability.sounds.type.SoundCategory.WEATHER;
			case BLOCK -> me.whereareiam.anvil.capability.sounds.type.SoundCategory.BLOCKS;
			case HOSTILE -> me.whereareiam.anvil.capability.sounds.type.SoundCategory.HOSTILE;
			case NEUTRAL -> me.whereareiam.anvil.capability.sounds.type.SoundCategory.NEUTRAL;
			case PLAYER -> me.whereareiam.anvil.capability.sounds.type.SoundCategory.PLAYERS;
			case AMBIENT -> me.whereareiam.anvil.capability.sounds.type.SoundCategory.AMBIENT;
			case VOICE -> me.whereareiam.anvil.capability.sounds.type.SoundCategory.VOICE;
		};
	}
}
