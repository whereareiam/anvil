package me.whereareiam.anvil.capability.sounds.java;

import me.whereareiam.anvil.capability.sounds.channel.SoundsChannel;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.model.SoundPosition;
import me.whereareiam.anvil.capability.sounds.type.SoundCategory;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.sound.ClientboundSound;
import me.whereareiam.anvil.edition.java.protocol.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.edition.java.protocol.type.world.SoundSource;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.worker.OperationRegistry;
import me.whereareiam.anvil.player.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;


/**
 * Installs the built-in sounds capability for players that speak the Java Edition protocol: it emits every sound the
 * player is told to play, at a position or following an entity, by its namespaced key. Sounds register no operations.
 */
public final class JavaSoundsExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return SoundsChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);

		Subscription heard = Subscription.all(
				protocol.listen(ClientboundSound.class, sound -> heard(player, HeardSound.builder()
						.key(sound.sound())
						.category(category(sound.source()))
						.volume(sound.volume())
						.pitch(sound.pitch())
						.position(position(sound.position())))),
				protocol.listen(ClientboundSoundEntity.class, sound -> heard(player, HeardSound.builder()
						.key(sound.sound())
						.category(category(sound.source()))
						.volume(sound.volume())
						.pitch(sound.pitch())
						.entityId(sound.entityId().value())))
		);

		return heard::close;
	}

	private static void heard(PlayerBindingContext<Object> player, HeardSound.HeardSoundBuilder sound) {
		player.emit(SoundsChannel.HEARD, sound.build());
	}

	/**
	 * Copies a protocol position into the capability's own position.
	 */
	private static SoundPosition position(Vec3d position) {
		return new SoundPosition(position.x(), position.y(), position.z());
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
