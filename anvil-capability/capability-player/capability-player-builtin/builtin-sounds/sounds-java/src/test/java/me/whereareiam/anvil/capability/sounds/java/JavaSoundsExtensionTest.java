package me.whereareiam.anvil.capability.sounds.java;

import me.whereareiam.anvil.capability.sounds.channel.SoundsChannel;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.model.SoundPosition;
import me.whereareiam.anvil.capability.sounds.type.SoundCategory;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaSoundsExtensionTest {
	private static final Vec3d POSITION = new Vec3d(1.5D, 64D, -2D);

	private final JavaSoundsExtension extension = new JavaSoundsExtension();

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
		RecordingOperations operations = new RecordingOperations();

		extension.bind(new RecordingPlayer(new RecordingProtocol()), operations);

		assertEquals(List.of(), operations.registered);
	}

	@Test
	void emitsEverySoundThePlayerIsToldToPlayByItsKey() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(ClientboundSound.builder()
				.sound("minecraft:entity.player.levelup")
				.source(SoundSource.PLAYERS)
				.position(POSITION)
				.volume(1F)
				.pitch(2F)
				.seed(7L)
				.build());
		protocol.deliver(ClientboundSoundEntity.builder()
				.sound("minecraft:block.note_block.pling")
				.source(SoundSource.BLOCKS)
				.entityId(42)
				.volume(0.5F)
				.pitch(1F)
				.build());
		protocol.deliver(ClientboundSound.builder()
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
		), player.emitted);
	}

	@Test
	void reportsEverySourceAsTheCategoryOfTheSameName() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		for (SoundSource source : SoundSource.values()) {
			protocol.deliver(ClientboundSoundEntity.builder()
					.sound("minecraft:ui.toast.in")
					.source(source)
					.entityId(42)
					.build());
		}

		assertEquals(Arrays.stream(SoundSource.values()).map(Enum::name).toList(),
				player.emitted.stream().map(sound -> sound.getCategory().name()).toList());
	}

	@Test
	void listensToTheSoundsAtAPositionAndFollowingAnEntity() {
		RecordingProtocol protocol = new RecordingProtocol();
		extension.bind(new RecordingPlayer(protocol), new RecordingOperations());

		assertEquals(Set.of(ClientboundSound.class, ClientboundSoundEntity.class), protocol.listeners.keySet());
	}

	@Test
	void stopsListeningWhenClosed() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		WorkerBinding binding = extension.bind(player, new RecordingOperations());

		binding.close();
		protocol.deliver(ClientboundSoundEntity.builder()
				.sound("minecraft:after.close")
				.source(SoundSource.MASTER)
				.build());

		assertEquals(Map.of(), protocol.listeners);
		assertEquals(List.of(), player.emitted);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingPlayer player = new RecordingPlayer(null);

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(player, new RecordingOperations()));
	}

	/**
	 * Hands delivered packets to the listener of their class.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();

		@Override
		public <P extends ClientboundPacket> @NotNull Subscription listen(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			listeners.put(type, packet -> listener.accept(type.cast(packet)));
			return () -> listeners.remove(type);
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			throw new AssertionError("Sounds send no packets");
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new AssertionError("The sounds binding observes no sent packets");
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return true;
		}

		private void deliver(ClientboundPacket packet) {
			Consumer<Object> listener = listeners.get(packet.getClass());
			if (listener != null) listener.accept(packet);
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final List<String> registered = new ArrayList<>();

		@Override
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			registered.add(operation.getId());
		}
	}

	/**
	 * A player that speaks the given protocol, or none, and records what the binding emits.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable JavaProtocol protocol;
		private final List<HeardSound> emitted = new ArrayList<>();

		private RecordingPlayer(@Nullable JavaProtocol protocol) {
			this.protocol = protocol;
		}

		@Override
		public <P> @NotNull P protocol(@NotNull Class<P> type) {
			if (protocol == null) return PlayerBindingContext.super.protocol(type);

			return type.cast(protocol);
		}

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull UUID uniqueId() {
			return UUID.nameUUIDFromBytes("Alice".getBytes());
		}

		@Override
		public void connect() {
			throw new AssertionError("Sounds do not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("Sounds do not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("Sounds do not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("Sounds use the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("Sounds use the protocol, not the native session");
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			throw new AssertionError("Sounds use the protocol, not the native session");
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("Sounds do not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("Sounds do not change the view rotation");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			assertSame(SoundsChannel.HEARD, event);
			emitted.add((HeardSound) payload);
		}
	}
}
