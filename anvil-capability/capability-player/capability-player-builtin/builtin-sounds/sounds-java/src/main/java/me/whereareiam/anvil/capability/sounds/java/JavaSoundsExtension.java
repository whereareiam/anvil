package me.whereareiam.anvil.capability.sounds.java;

import me.whereareiam.anvil.capability.sounds.channel.SoundsChannel;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.model.SoundPosition;
import me.whereareiam.anvil.capability.sounds.type.SoundCategory;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundCustomSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Installs the built-in sounds capability for players that speak the Java Edition protocol: it emits every sound the
 * player is told to play, at a position or following an entity, by its namespaced key. A release without the named
 * sound packet simply names such sounds in the positioned sound packet. Sounds register no operations.
 */
public final class JavaSoundsExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return SoundsChannel.ID;
	}

	@Override
	public @NotNull Optional<String> libraryId() {
		return Optional.empty();
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);

		List<Subscription> heard = new ArrayList<>();
		heard.add(protocol.listen(ClientboundSound.class, sound -> heard(player, HeardSound.builder()
				.key(sound.getSound())
				.category(category(sound.getSource()))
				.volume(sound.getVolume())
				.pitch(sound.getPitch())
				.position(position(sound.getPosition())))));
		heard.add(protocol.listen(ClientboundSoundEntity.class, sound -> heard(player, HeardSound.builder()
				.key(sound.getSound())
				.category(category(sound.getSource()))
				.volume(sound.getVolume())
				.pitch(sound.getPitch())
				.entityId(sound.getEntityId()))));
		if (protocol.supports(ClientboundCustomSound.class))
			heard.add(protocol.listen(ClientboundCustomSound.class, sound -> heard(player, HeardSound.builder()
					.key(sound.getSound())
					.category(category(sound.getSource()))
					.volume(sound.getVolume())
					.pitch(sound.getPitch())
					.position(position(sound.getPosition())))));

		return () -> heard.forEach(Subscription::close);
	}

	private static void heard(PlayerBindingContext<Object> player, HeardSound.HeardSoundBuilder sound) {
		player.emit(SoundsChannel.HEARD, sound.build());
	}

	/**
	 * Copies a protocol position into the capability's own position, which shares its name.
	 */
	private static SoundPosition position(me.whereareiam.anvil.protocol.java.api.model.sound.SoundPosition position) {
		return new SoundPosition(position.getX(), position.getY(), position.getZ());
	}

	private static SoundCategory category(SoundSource source) {
		return switch (source) {
			case MASTER -> SoundCategory.MASTER;
			case MUSIC -> SoundCategory.MUSIC;
			case RECORDS -> SoundCategory.RECORDS;
			case WEATHER -> SoundCategory.WEATHER;
			case BLOCKS -> SoundCategory.BLOCKS;
			case HOSTILE -> SoundCategory.HOSTILE;
			case NEUTRAL -> SoundCategory.NEUTRAL;
			case PLAYERS -> SoundCategory.PLAYERS;
			case AMBIENT -> SoundCategory.AMBIENT;
			case VOICE -> SoundCategory.VOICE;
			case UI -> SoundCategory.UI;
		};
	}
}
