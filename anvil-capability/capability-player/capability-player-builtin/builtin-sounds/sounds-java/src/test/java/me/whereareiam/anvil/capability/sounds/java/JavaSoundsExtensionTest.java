package me.whereareiam.anvil.capability.sounds.java;

import me.whereareiam.anvil.capability.sounds.channel.SoundsChannel;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.model.SoundPosition;
import me.whereareiam.anvil.capability.sounds.type.SoundCategory;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.sound.ClientboundSound;
import me.whereareiam.anvil.edition.java.protocol.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.edition.java.protocol.type.world.SoundSource;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
import me.whereareiam.anvil.player.worker.TestOperations;
import me.whereareiam.anvil.player.worker.TestWorkerPlayer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSoundsExtensionTest {
	private static final Vec3d POSITION = new Vec3d(1.5D, 64D, -2D);

	private final JavaSoundsExtension extension = new JavaSoundsExtension();
	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();

	@Test
	void isDiscoveredAsTheJavaEditionSoundsExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaSoundsExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.sounds", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void registersNoOperations() {
		extension.bind(player, operations);

		assertEquals(List.of(), operations.registered());
	}

	@Test
	void emitsEverySoundThePlayerIsToldToPlayByItsKey() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(ClientboundSound.builder()
				.sound("minecraft:entity.player.levelup")
				.source(SoundSource.PLAYERS)
				.position(POSITION)
				.volume(1F)
				.pitch(2F)
				.seed(7L)
				.build());
		player.deliver(ClientboundSoundEntity.builder()
				.sound("minecraft:block.note_block.pling")
				.source(SoundSource.BLOCKS)
				.entityId(new EntityId(42))
				.volume(0.5F)
				.pitch(1F)
				.build());
		player.deliver(ClientboundSound.builder()
				.sound("example:alert")
				.source(SoundSource.RECORDS)
				.position(POSITION)
				.volume(1F)
				.pitch(1F)
				.build());

		assertEquals(List.of(
				HeardSound.builder()
						.key("minecraft:entity.player.levelup")
						.category(SoundCategory.PLAYERS)
						.volume(1F)
						.pitch(2F)
						.position(new SoundPosition(1.5D, 64D, -2D))
						.build(),
				HeardSound.builder()
						.key("minecraft:block.note_block.pling")
						.category(SoundCategory.BLOCKS)
						.volume(0.5F)
						.pitch(1F)
						.entityId(42)
						.build(),
				HeardSound.builder()
						.key("example:alert")
						.category(SoundCategory.RECORDS)
						.volume(1F)
						.pitch(1F)
						.position(new SoundPosition(1.5D, 64D, -2D))
						.build()
		), player.emitted(SoundsChannel.HEARD));
		assertEquals(List.of("sounds.heard", "sounds.heard", "sounds.heard"), player.emittedEvents());
	}

	@Test
	void reportsEverySourceAsTheCategoryOfTheSameName() {
		extension.bind(player, operations);
		player.connect();

		for (SoundSource source : SoundSource.values()) {
			player.deliver(ClientboundSoundEntity.builder()
					.sound("minecraft:ui.toast.in")
					.source(source)
					.entityId(new EntityId(42))
					.build());
		}

		assertEquals(Arrays.stream(SoundSource.values()).map(Enum::name).toList(),
				player.emitted(SoundsChannel.HEARD).stream().map(sound -> sound.getCategory().name()).toList());
	}

	@Test
	void listensToTheSoundsAtAPositionAndFollowingAnEntity() {
		extension.bind(player, operations);

		assertTrue(player.protocol().wants(ClientboundSound.class));
		assertTrue(player.protocol().wants(ClientboundSoundEntity.class));
	}

	@Test
	void stopsListeningWhenClosed() {
		WorkerBinding binding = extension.bind(player, operations);

		binding.close();
		player.connect();
		player.deliver(ClientboundSoundEntity.builder()
				.sound("minecraft:after.close")
				.source(SoundSource.MASTER)
				.entityId(new EntityId(1))
				.build());

		assertFalse(player.protocol().wants(ClientboundSound.class));
		assertFalse(player.protocol().wants(ClientboundSoundEntity.class));
		assertEquals(List.of(), player.emittedEvents());
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
	}
}
