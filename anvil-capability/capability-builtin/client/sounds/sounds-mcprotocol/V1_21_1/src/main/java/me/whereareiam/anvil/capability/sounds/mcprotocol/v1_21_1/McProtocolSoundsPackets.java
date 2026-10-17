package me.whereareiam.anvil.capability.sounds.mcprotocol.v1_21_1;

import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.model.SoundPosition;
import me.whereareiam.anvil.capability.sounds.packet.SoundsPackets;
import me.whereareiam.anvil.capability.sounds.type.SoundCategory;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Sound packets of MCProtocolLib releases from Minecraft 1.21.1, the first one in the
 * {@code org.geysermc.mcprotocollib} packages. A sound at a position and a sound following an entity each carry a
 * sound that the library has already resolved: a built-in sound from its registry ID or a sound the server named.
 * The packets keep their shape in every later release Anvil ships, so this segment serves 1.21.11 and 26.1.2 as
 * well; the user interface category those releases add is mapped by its name.
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
		if (packet instanceof ClientboundSoundEntityPacket sound)
			return HeardSound.builder()
					.key(key(sound.getSound().getName()))
					.category(category(sound.getCategory()))
					.volume(sound.getVolume())
					.pitch(sound.getPitch())
					.entityId(sound.getEntityId())
					.build();
		return null;
	}

	private static String key(String name) {
		return name.indexOf(':') < 0 ? DEFAULT_NAMESPACE + name : name;
	}

	/**
	 * Maps the library's category by name, the library naming most of them in the singular; the user interface
	 * category of later releases is not a constant of this release's enum.
	 */
	private static SoundCategory category(Enum<?> category) {
		return switch (category.name()) {
			case "RECORD" -> SoundCategory.RECORDS;
			case "BLOCK" -> SoundCategory.BLOCKS;
			case "PLAYER" -> SoundCategory.PLAYERS;
			default -> SoundCategory.valueOf(category.name());
		};
	}
}
