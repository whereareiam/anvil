package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.MovementState;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMovementTest {
	@Test
	void answersEveryTeleportWithThePositionItLeftThePlayerAtWithoutPhysics() {
		Fixture fixture = new Fixture("1.18.2");
		fixture.protocol.receive(new ClientboundLogin(7, new Dimension("minecraft:overworld", -64, 384)));

		fixture.protocol.receive(teleport(new Vec3d(8.5, 65.0, 8.5), 90.0F, 10.0F, Set.of()));
		fixture.protocol.receive(teleport(new Vec3d(1.0, 0.0, -1.0), 5.0F, 100.0F, EnumSet.of(Relative.X, Relative.Y,
				Relative.Z, Relative.YAW, Relative.PITCH)));

		assertEquals(List.of(confirmation(8.5, 65.0, 8.5, 90.0F, 10.0F), confirmation(9.5, 65.0, 7.5, 95.0F, 110.0F)),
				fixture.protocol.sent);
		assertEquals(9.5, fixture.state().getPosition().getX());
	}

	@Test
	void leavesTheAnswerToTheAcceptanceFrom26_3() {
		Fixture fixture = new Fixture("26.3");
		fixture.protocol.receive(new ClientboundLogin(7, new Dimension("minecraft:overworld", -64, 384)));

		fixture.protocol.receive(teleport(new Vec3d(8.5, 65.0, 8.5), 90.0F, 10.0F, Set.of()));

		assertEquals(List.of(), fixture.protocol.sent);
		assertEquals(8.5, fixture.state().getPosition().getX());
	}

	@Test
	void clampsTheTeleportedPitchFrom1_21_2() {
		Fixture fixture = new Fixture("1.21.11");
		fixture.protocol.receive(new ClientboundLogin(7, new Dimension("minecraft:overworld", -64, 384)));

		fixture.protocol.receive(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 80.0F, Set.of()));
		fixture.protocol.receive(teleport(Vec3d.ZERO, 0.0F, 30.0F, EnumSet.of(Relative.X, Relative.Y, Relative.Z,
				Relative.YAW, Relative.PITCH)));

		assertEquals(confirmation(8.5, 65.0, 8.5, 0.0F, 90.0F), fixture.protocol.sent.getLast());
	}

	@Test
	void keepsTheVelocityAlongTheTeleportsRelativeComponentsAndSetsTheOthers() {
		Fixture fixture = new Fixture("1.21.11");
		fixture.protocol.receive(new ClientboundLogin(7, new Dimension("minecraft:overworld", -64, 384)));
		fixture.operations.execute(MovementChannel.PHYSICS, true);
		fixture.protocol.receive(teleport(new Vec3d(8.5, 120.0, 8.5), 0.0F, 0.0F, Set.of()));
		for (int tick = 0; tick < 5; tick++) fixture.movement.tick();
		double falling = fixture.state().getMotion().getY();

		fixture.protocol.receive(ClientboundPlayerPosition.builder()
				.position(Vec3d.ZERO)
				.deltaMovement(new Vec3d(0.5, 0.0, 0.0))
				.relatives(EnumSet.of(Relative.X, Relative.Y, Relative.Z, Relative.YAW, Relative.PITCH, Relative.DELTA_Y))
				.build());

		assertEquals(0.5, fixture.state().getMotion().getX());
		assertEquals(falling, fixture.state().getMotion().getY());
		assertEquals(0.0, fixture.state().getMotion().getZ());
		fixture.protocol.receive(teleport(new Vec3d(8.5, 120.0, 8.5), 0.0F, 0.0F, EnumSet.of(Relative.YAW)));
		assertEquals(0.0, fixture.state().getMotion().getY());
	}

	@Test
	void answersATeleportWithPhysicsOnAtOnceAndBeforeItsNextTick() {
		Fixture fixture = new Fixture("1.16.5");
		fixture.world.floor(0, 15, 64, 0, 15, "minecraft:stone");
		fixture.protocol.receive(new ClientboundLogin(7, new Dimension("minecraft:overworld", 0, 256)));
		fixture.operations.execute(MovementChannel.PHYSICS, true);
		fixture.protocol.receive(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));
		for (int tick = 0; tick < 3; tick++) fixture.movement.tick();
		fixture.protocol.sent.clear();

		fixture.protocol.receive(teleport(new Vec3d(4.5, 70.0, 4.5), 30.0F, 0.0F, Set.of()));

		assertEquals(List.of(confirmation(4.5, 70.0, 4.5, 30.0F, 0.0F)), fixture.protocol.sent);
	}

	@Test
	void refusesPhysicsForAReleaseWithoutBlockData() {
		Fixture fixture = new Fixture("26.2");

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> fixture.operations.execute(MovementChannel.PHYSICS, true));

		assertTrue(failure.getMessage().contains("No block data for Minecraft 26.2"), failure.getMessage());
		assertFalse(fixture.state().isPhysics());
		assertTrue(fixture.state().getPhysicsUnavailable().contains("No block data for Minecraft 26.2"));
	}

	@Test
	void fallsToTheGroundAndReportsItAsTheVanillaClientDoes() {
		Fixture fixture = new Fixture("1.21.11");
		fixture.world.floor(0, 15, 64, 0, 15, "minecraft:stone");
		fixture.operations.execute(MovementChannel.PHYSICS, true);
		assertTrue(fixture.clock.running);
		fixture.protocol.receive(new ClientboundLogin(7, new Dimension("minecraft:overworld", -64, 384)));
		fixture.protocol.receive(teleport(new Vec3d(8.5, 67.0, 8.5), 0.0F, 0.0F, Set.of()));
		fixture.protocol.sent.clear();

		for (int tick = 0; tick < 20; tick++) fixture.movement.tick();

		MovementState state = fixture.state();
		assertEquals(20, state.getTicks());
		assertEquals(65.0, state.getPosition().getY());
		assertTrue(state.getPosition().isOnGround());
		assertEquals(-0.0784000015258789, state.getMotion().getY());
		assertEquals(20, fixture.protocol.sent.stream().filter(ServerboundClientTickEnd.class::isInstance).count());
		assertTrue(fixture.protocol.sent.getFirst() instanceof ServerboundMovePlayerPos);
	}

	@Test
	void endsNoTickBefore1_21_2AndWalksWithTheHeldKeys() {
		Fixture fixture = new Fixture("1.20.6");
		fixture.world.floor(0, 15, 64, 0, 15, "minecraft:stone");
		fixture.protocol.receive(new ClientboundLogin(7, new Dimension("minecraft:overworld", -64, 384)));
		fixture.protocol.receive(teleport(new Vec3d(8.5, 65.0, 2.5), 0.0F, 0.0F, Set.of()));
		fixture.operations.execute(MovementChannel.PHYSICS, true);
		fixture.operations.execute(MovementChannel.INPUT, MovementInput.builder().forward(true).build());

		for (int tick = 0; tick < 20; tick++) fixture.movement.tick();

		assertTrue(fixture.state().getPosition().getZ() > 5.0, "the player walked to " + fixture.state().getPosition());
		assertEquals(0, fixture.protocol.sent.stream().filter(ServerboundClientTickEnd.class::isInstance).count());
	}

	@Test
	void doesNotMoveThePlayerInAnUnloadedChunk() {
		Fixture fixture = new Fixture("1.21.11");
		fixture.world.unload(0, 0);
		fixture.protocol.receive(new ClientboundLogin(7, new Dimension("minecraft:overworld", -64, 384)));
		fixture.protocol.receive(teleport(new Vec3d(8.5, 80.0, 8.5), 0.0F, 0.0F, Set.of()));
		fixture.operations.execute(MovementChannel.PHYSICS, true);
		fixture.protocol.sent.clear();

		for (int tick = 0; tick < 10; tick++) fixture.movement.tick();

		assertEquals(0, fixture.state().getTicks());
		assertEquals(80.0, fixture.state().getPosition().getY());
		assertEquals(10, fixture.protocol.sent.size());
		assertTrue(fixture.protocol.sent.stream().allMatch(ServerboundClientTickEnd.class::isInstance));
	}

	@Test
	void placesThePlayerWhereATestMovesItAndContinuesFromThere() {
		Fixture fixture = new Fixture("1.21.11");
		fixture.world.floor(0, 15, 64, 0, 15, "minecraft:stone");
		fixture.protocol.receive(new ClientboundLogin(7, new Dimension("minecraft:overworld", -64, 384)));
		fixture.protocol.receive(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));
		fixture.operations.execute(MovementChannel.PHYSICS, true);

		fixture.operations.execute(MovementChannel.MOVE, Position.builder().x(4.5).y(70.0).z(4.5).build());
		fixture.movement.tick();

		Position position = fixture.state().getPosition();
		assertEquals(4.5, position.getX());
		assertEquals(70.0 - 0.0, position.getY());
		fixture.movement.tick();
		assertTrue(fixture.state().getPosition().getY() < 70.0);
	}

	@Test
	void turnsTheViewAtOnceWithoutPhysicsAndWithTheNextTickWithIt() {
		Fixture fixture = new Fixture("1.21.11");
		fixture.world.floor(0, 15, 64, 0, 15, "minecraft:stone");
		fixture.protocol.receive(new ClientboundLogin(7, new Dimension("minecraft:overworld", -64, 384)));
		fixture.protocol.receive(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));
		fixture.protocol.sent.clear();

		fixture.operations.execute(MovementChannel.LOOK, new ViewRotation(45.0F, 5.0F));
		assertEquals(List.of(ServerboundMovePlayerRot.builder().yaw(45.0F).pitch(5.0F).build()), fixture.protocol.sent);
		assertEquals(new ViewRotation(45.0F, 5.0F), fixture.player.rotation);

		fixture.operations.execute(MovementChannel.PHYSICS, true);
		fixture.protocol.sent.clear();
		fixture.operations.execute(MovementChannel.LOOK, new ViewRotation(90.0F, 0.0F));
		assertEquals(List.of(), fixture.protocol.sent);
		fixture.movement.tick();
		assertTrue(fixture.protocol.sent.stream().anyMatch(packet -> packet instanceof ServerboundMovePlayerPosRot move
				&& move.getYaw() == 90.0F || packet instanceof ServerboundMovePlayerRot rotation && rotation.getYaw() == 90.0F));
	}

	@Test
	void forgetsThePositionWhenTheConnectionEndsAndKeepsPhysicsOn() {
		Fixture fixture = new Fixture("1.21.11");
		fixture.protocol.receive(new ClientboundLogin(7, new Dimension("minecraft:overworld", -64, 384)));
		fixture.protocol.receive(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));
		fixture.operations.execute(MovementChannel.PHYSICS, true);

		fixture.player.reconnect();

		assertNull(fixture.state().getPosition());
		assertTrue(fixture.state().isPhysics());
		fixture.operations.execute(MovementChannel.PHYSICS, false);
		assertFalse(fixture.clock.running);
	}

	private static @NotNull ClientboundPlayerPosition teleport(@NotNull Vec3d position, float yaw, float pitch, @NotNull Set<Relative> relatives) {
		return ClientboundPlayerPosition.builder()
				.position(position)
				.yaw(yaw)
				.pitch(pitch)
				.relatives(relatives)
				.build();
	}

	private static @NotNull ServerboundMovePlayerPosRot confirmation(double x, double y, double z, float yaw, float pitch) {
		return ServerboundMovePlayerPosRot.builder()
				.position(new Vec3d(x, y, z))
				.yaw(yaw)
				.pitch(pitch)
				.onGround(false)
				.horizontalCollision(false)
				.build();
	}

	/**
	 * A bound movement over a recorded protocol and a test world of one loaded column.
	 */
	private static final class Fixture {
		private final TestWorld world;
		private final RecordingProtocol protocol = new RecordingProtocol();
		private final RecordingPlayer player;
		private final RecordingOperations operations = new RecordingOperations();
		private final RecordingClock clock = new RecordingClock();
		private final ClientMovement movement;

		private Fixture(@NotNull String version) {
			this.world = new TestWorld(version.startsWith("26.") ? "26.1.2" : version).load(0, 0);
			this.player = new RecordingPlayer(protocol, world, MinecraftVersion.parse(version));
			this.movement = ClientMovement.bind(player, operations, clock);
		}

		private @NotNull MovementState state() {
			return (MovementState) operations.execute(MovementChannel.STATE, null);
		}
	}

	private static final class RecordingClock implements ClientMovement.Clock {
		private boolean running;

		@Override
		public @NotNull Subscription start(@NotNull Runnable tick) {
			running = true;
			return () -> running = false;
		}
	}

	/**
	 * Delivers the packets a test receives to the listeners and records the packets sent.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final List<ServerboundPacket> sent = new ArrayList<>();
		private final Map<Class<?>, List<Consumer<Object>>> listeners = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <P extends ClientboundPacket> @NotNull Subscription listen(@NotNull Class<P> type, @NotNull Consumer<? super P> listener) {
			Consumer<Object> each = packet -> ((Consumer<P>) listener).accept((P) packet);
			listeners.computeIfAbsent(type, ignored -> new ArrayList<>()).add(each);
			return () -> listeners.get(type).remove(each);
		}

		private void receive(@NotNull ClientboundPacket packet) {
			for (Consumer<Object> listener : List.copyOf(listeners.getOrDefault(packet.getClass(), List.of()))) listener.accept(packet);
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			sent.add(packet);
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(@NotNull Class<P> type, @NotNull Consumer<? super P> listener) {
			throw new AssertionError("Movement observes no sent packets");
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return true;
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), (Function<Object, Object>) handler);
		}

		private <Q> @Nullable Object execute(@NotNull OperationDescriptor<Q, ?> operation, @Nullable Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}

	/**
	 * A connected player of one release that speaks the recorded protocol and the test world.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final RecordingProtocol protocol;
		private final JavaWorld world;
		private final MinecraftVersion version;
		private final List<Supplier<Subscription>> connections = new ArrayList<>();
		private final List<Subscription> current = new ArrayList<>();
		private @Nullable ViewRotation rotation;

		private RecordingPlayer(@NotNull RecordingProtocol protocol, @NotNull JavaWorld world, @NotNull MinecraftVersion version) {
			this.protocol = protocol;
			this.world = world;
			this.version = version;
		}

		private void reconnect() {
			current.forEach(Subscription::close);
			current.clear();
			for (Supplier<Subscription> connection : connections) current.add(connection.get());
		}

		@Override
		public <P> @NotNull P protocol(@NotNull Class<P> type) {
			if (type == JavaProtocol.class) return type.cast(protocol);
			if (type == JavaWorld.class) return type.cast(world);

			return PlayerBindingContext.super.protocol(type);
		}

		@Override
		public @NotNull MinecraftVersion minecraftVersion() {
			return version;
		}

		@Override
		public @NotNull Subscription bindConnection(@NotNull Supplier<Subscription> listener) {
			connections.add(listener);
			current.add(listener.get());
			return () -> connections.remove(listener);
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
			throw new AssertionError("Movement does not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("Movement does not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("Movement does not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("Movement uses the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("Movement uses the protocol, not the native session");
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			throw new AssertionError("Movement follows connections, not native sessions");
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("Movement only records the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			this.rotation = rotation;
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			throw new AssertionError("Movement emits no events");
		}
	}
}
