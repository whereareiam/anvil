package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.channel.MovementState;
import me.whereareiam.anvil.capability.movement.channel.PhysicsChange;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.edition.java.protocol.type.player.PlayerCommandAction;
import me.whereareiam.anvil.player.api.model.player.ViewRotation;
import me.whereareiam.anvil.player.worker.TestOperations;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMovementTest {
	private static final Set<Relative> ALL_RELATIVE = EnumSet.of(Relative.X, Relative.Y, Relative.Z, Relative.YAW,
			Relative.PITCH);

	private TestOperations operations = new TestOperations();
	private ClientMovement movement;

	@AfterEach
	void close() {
		if (movement != null) movement.close();
	}

	@Test
	void answersEveryTeleportWithThePositionItLeftThePlayerAtWithoutPhysics() {
		TestClient client = bind(new TestClient("1.18.2")).join();

		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 90.0F, 10.0F, Set.of()));
		client.player.deliver(teleport(new Vec3d(1.0, 0.0, -1.0), 5.0F, 100.0F, ALL_RELATIVE));

		assertEquals(List.of(confirmation(8.5, 65.0, 8.5, 90.0F, 10.0F), confirmation(9.5, 65.0, 7.5, 95.0F, 110.0F)),
				client.player.sent());
		assertEquals(9.5, state().getPosition().getX());
		assertEquals(new ViewRotation(95.0F, 110.0F), client.player.viewRotation());
	}

	@Test
	void leavesTheAnswerToTheAcceptanceFrom26_3() {
		TestClient client = bind(new TestClient("26.3")).join();

		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 90.0F, 10.0F, Set.of()));

		assertEquals(List.of(), client.player.sent());
		assertEquals(8.5, state().getPosition().getX());
	}

	@Test
	void clampsTheTeleportedPitchFrom1_21_2() {
		for (String version : List.of("1.21.1", "1.21.11")) {
			TestClient client = bind(new TestClient(version)).join();

			client.player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 80.0F, Set.of()));
			client.player.deliver(teleport(Vec3d.ZERO, 0.0F, 30.0F, ALL_RELATIVE));

			float pitch = version.equals("1.21.1") ? 110.0F : 90.0F;
			assertEquals(confirmation(8.5, 65.0, 8.5, 0.0F, pitch), client.player.sent().getLast(), version);
			movement.close();
		}
	}

	@Test
	void keepsTheVelocityAlongTheTeleportsRelativeComponentsAndSetsTheOthers() {
		TestClient client = physics(new TestClient("1.21.11").load(0, 0));
		client.player.deliver(teleport(new Vec3d(8.5, 120.0, 8.5), 0.0F, 0.0F, Set.of()));
		ticks(5);
		double falling = state().getMotion().getY();

		client.player.deliver(ClientboundPlayerPosition.builder()
				.position(Vec3d.ZERO)
				.deltaMovement(new Vec3d(0.5, 0.0, 0.0))
				.relatives(EnumSet.of(Relative.X, Relative.Y, Relative.Z, Relative.YAW, Relative.PITCH, Relative.DELTA_Y))
				.build());

		assertEquals(0.5, state().getMotion().getX());
		assertEquals(falling, state().getMotion().getY());
		assertEquals(0.0, state().getMotion().getZ());
		client.player.deliver(teleport(new Vec3d(8.5, 120.0, 8.5), 0.0F, 0.0F, EnumSet.of(Relative.YAW)));
		assertEquals(0.0, state().getMotion().getY());
	}

	@Test
	void takesTheVelocityTheServerGivesItsOwnEntityAndNoOther() {
		TestClient client = physics(new TestClient("1.21.11").load(0, 0));
		client.player.deliver(teleport(new Vec3d(8.5, 120.0, 8.5), 0.0F, 0.0F, Set.of()));
		ticks(1);

		client.player.deliver(new ClientboundSetEntityMotion(new EntityId(TestClient.ENTITY_ID.getValue() + 1), new Vec3d(1.0, 1.0, 1.0)));
		client.player.deliver(new ClientboundSetEntityMotion(TestClient.ENTITY_ID, new Vec3d(0.25, 0.5, -0.125)));

		assertEquals(0.25, state().getMotion().getX());
		assertEquals(0.5, state().getMotion().getY());
		assertEquals(-0.125, state().getMotion().getZ());
	}

	@Test
	void tellsWhyAReleaseWithoutBlockDataCannotSimulateAndStaysOff() {
		bind(new TestClient("26.2")).join();

		String reason = operations.call(MovementChannel.PHYSICS, true).getRefusal();

		assertTrue(reason != null && reason.contains("776"), reason);
		assertFalse(state().isPhysics());
		assertEquals(new PhysicsChange(null), operations.call(MovementChannel.PHYSICS, false));
	}

	@Test
	void fallsToTheGroundAndReportsItAsTheVanillaClientDoes() {
		TestClient client = physics(new TestClient("1.21.11").load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone"));
		client.player.deliver(teleport(new Vec3d(8.5, 67.0, 8.5), 0.0F, 0.0F, Set.of()));
		int sent = client.player.sent().size();

		ticks(20);

		MovementState state = state();
		assertEquals(20, state.getTicks());
		assertEquals(65.0, state.getPosition().getY());
		assertTrue(state.getPosition().isOnGround());
		assertEquals(-0.0784000015258789, state.getMotion().getY());
		List<?> ticked = client.player.sent().subList(sent, client.player.sent().size());
		assertEquals(20, ticked.stream().filter(ServerboundClientTickEnd.class::isInstance).count());
		assertTrue(ticked.getFirst() instanceof ServerboundMovePlayerPos, ticked.getFirst().toString());
	}

	@Test
	void endsNoTickBefore1_21_2AndWalksWithTheHeldKeys() {
		TestClient client = physics(new TestClient("1.20.6").load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone"));
		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 2.5), 0.0F, 0.0F, Set.of()));
		operations.call(MovementChannel.INPUT, MovementInput.builder().forward(true).build());

		ticks(20);

		assertTrue(state().getPosition().getZ() > 5.0, "the player walked to " + state().getPosition());
		assertEquals(List.of(), client.player.sent(ServerboundClientTickEnd.class));
	}

	@Test
	void doesNotMoveThePlayerInAnUnloadedChunk() {
		TestClient client = physics(new TestClient("1.21.11"));
		client.player.deliver(teleport(new Vec3d(8.5, 80.0, 8.5), 0.0F, 0.0F, Set.of()));
		int sent = client.player.sent().size();

		ticks(10);

		assertEquals(0, state().getTicks());
		assertEquals(80.0, state().getPosition().getY());
		assertEquals(10, client.player.sent().size() - sent);
		assertEquals(10, client.player.sent(ServerboundClientTickEnd.class).size());
	}

	@Test
	void placesThePlayerWhereATestMovesItAndContinuesFromThere() {
		TestClient client = physics(new TestClient("1.21.11").load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone"));
		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));

		operations.call(MovementChannel.MOVE, Position.builder().x(4.5).y(70.0).z(4.5).build());
		assertEquals(70.0, state().getPosition().getY());
		ticks(2);

		assertEquals(4.5, state().getPosition().getX());
		assertTrue(state().getPosition().getY() < 70.0);
	}

	@Test
	void turnsTheViewAtOnceWithoutPhysicsAndWithTheNextTickWithIt() {
		TestClient client = bind(new TestClient("1.21.11").load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone")).join();
		client.send().player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));
		int sent = client.player.sent().size();

		operations.call(MovementChannel.LOOK, new ViewRotation(45.0F, 5.0F));
		assertEquals(List.of(ServerboundMovePlayerRot.builder().yaw(45.0F).pitch(5.0F).build()),
				client.player.sent().subList(sent, client.player.sent().size()));
		assertEquals(new ViewRotation(45.0F, 5.0F), client.player.viewRotation());

		operations.call(MovementChannel.PHYSICS, true);
		sent = client.player.sent().size();
		operations.call(MovementChannel.LOOK, new ViewRotation(90.0F, 0.0F));
		assertEquals(sent, client.player.sent().size());
		ticks(1);
		assertTrue(client.player.sent(ServerboundMovePlayerRot.class).stream().anyMatch(rotation -> rotation.getYaw() == 90.0F)
				|| client.player.sent(ServerboundMovePlayerPosRot.class).stream().anyMatch(move -> move.getYaw() == 90.0F));
	}

	@Test
	void forgetsThePositionWhenTheConnectionEndsAndKeepsPhysicsOn() {
		TestClient client = physics(new TestClient("1.21.11"));
		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));

		client.player.rejoin();

		assertNull(state().getPosition());
		assertTrue(state().isPhysics());
	}

	@Test
	void runsThePhysicsOfAPlayerWhoseEntityIdIsNegative() {
		TestClient client = bind(new TestClient("1.21.11").load(0, 0));
		client.player.connect();
		client.player.deliver(ClientboundLogin.builder()
				.entityId(new EntityId(-39858984)).level("minecraft:overworld").dimension("minecraft:overworld")
				.dimensionType(client.dimension).build());
		client.send();
		operations.call(MovementChannel.PHYSICS, true);
		client.player.deliver(teleport(new Vec3d(8.5, 120.0, 8.5), 0.0F, 0.0F, Set.of()));

		ticks(1);

		assertEquals(1, state().getTicks());
		assertTrue(state().getMotion().getY() < 0.0);
	}

	@Test
	void answersATeleportWithPhysicsOnAtOnceAndBeforeItsNextTick() {
		TestClient client = physics(new TestClient("1.16.5").load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone"));
		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));
		ticks(3);
		int sent = client.player.sent().size();

		client.player.deliver(teleport(new Vec3d(4.5, 70.0, 4.5), 30.0F, 0.0F, Set.of()));

		assertEquals(List.of(confirmation(4.5, 70.0, 4.5, 30.0F, 0.0F)),
				client.player.sent().subList(sent, client.player.sent().size()));
	}

	@Test
	void holdsNoKeysAndSendsNoMovementWhileDeadUntilItRespawns() {
		TestClient client = physics(new TestClient("1.21.11").load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone"));
		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 2.5), 0.0F, 0.0F, Set.of()));
		operations.call(MovementChannel.INPUT, MovementInput.builder().forward(true).build());
		client.player.deliver(new ClientboundSetHealth(0.0F, 20, 5.0F));
		int sent = client.player.sent().size();

		ticks(10);

		assertEquals(2.5, state().getPosition().getZ());
		List<?> ticked = client.player.sent().subList(sent, client.player.sent().size());
		assertTrue(ticked.stream().allMatch(ServerboundClientTickEnd.class::isInstance), ticked.toString());

		client.player.deliver(ClientboundRespawn.builder().dimension("minecraft:overworld").dimensionType(client.dimension)
				.build());
		assertNull(state().getPosition());
		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 2.5), 0.0F, 0.0F, Set.of()));
		ticks(10);
		assertTrue(state().getPosition().getZ() > 2.5, "the respawned player walks again");
	}

	@Test
	void tellsSneakingThroughTheHeldKeysOnlyWhereTheReleaseCarriesThem() {
		for (boolean carried : List.of(true, false)) {
			TestClient client = physics(new TestClient("1.21.11", type -> carried || type != ServerboundPlayerInput.class)
					.load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone"));
			client.player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));
			operations.call(MovementChannel.INPUT, MovementInput.builder().sneak(true).build());

			ticks(3);

			boolean command = client.player.sent(ServerboundPlayerCommand.class).stream()
					.anyMatch(sent -> sent.getAction() == PlayerCommandAction.PRESS_SHIFT_KEY);
			assertEquals(!carried, command);
			assertEquals(carried, !client.player.sent(ServerboundPlayerInput.class).isEmpty());
			movement.close();
		}
	}

	@Test
	void tellsTheServerTheFlightADoubleJumpStartsBeforeTheMovementOfThatTick() {
		TestClient client = physics(new TestClient("1.21.11").load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone"));
		client.player.deliver(ClientboundPlayerAbilities.builder().mayFly(true).flyingSpeed(0.05F).walkingSpeed(0.1F)
				.build());
		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));
		ticks(3);
		MovementInput jump = MovementInput.builder().jump(true).build();
		operations.call(MovementChannel.INPUT, jump);
		ticks(1);
		operations.call(MovementChannel.INPUT, MovementInput.NONE);
		ticks(1);
		operations.call(MovementChannel.INPUT, jump);
		int sent = client.player.sent().size();

		ticks(1);

		List<?> ticked = client.player.sent().subList(sent, client.player.sent().size());
		assertEquals(new ServerboundPlayerAbilities(true), ticked.getFirst(), ticked.toString());
		assertTrue(ticked.stream().anyMatch(ServerboundMovePlayerPos.class::isInstance), ticked.toString());
		assertEquals(1, client.player.sent(ServerboundPlayerAbilities.class).size());
	}

	@Test
	void tellsWhyAReleaseWithoutTheAbilitiesPacketCannotSimulate() {
		bind(new TestClient("1.21.11", type -> type != ServerboundPlayerAbilities.class)).join();

		String reason = operations.call(MovementChannel.PHYSICS, true).getRefusal();

		assertTrue(reason != null && reason.contains(ServerboundPlayerAbilities.class.getSimpleName()), reason);
		assertFalse(state().isPhysics());
	}

	@Test
	void ticksTwentyTimesASecondOnItsOwnLoopUntilTurnedOff() {
		TestClient client = new TestClient("1.21.11").load(0, 0);
		movement = ClientMovement.bind(client.player, operations);
		client.join().send();
		operations.call(MovementChannel.PHYSICS, true);
		client.player.deliver(teleport(new Vec3d(8.5, 200.0, 8.5), 0.0F, 0.0F, Set.of()));

		long started = System.nanoTime();
		awaitState(state -> state.getTicks() >= 10);
		assertTrue(System.nanoTime() - started >= Duration.ofMillis(400).toNanos(), "ten ticks take half a second");
		operations.call(MovementChannel.PHYSICS, false);
		long ticks = state().getTicks();

		sleep(150);
		assertEquals(ticks, state().getTicks());
		assertFalse(state().isPhysics());
		assertTrue(state().getPosition().getY() < 200.0);
	}

	@Test
	void turnsPhysicsOffWithTheReasonWhenATickFailsAndSaysSoOnce() {
		TestClient client = new TestClient("1.21.11").load(0, 0);
		client.state(8, 64, 8, client.registry.stateCount());
		movement = ClientMovement.bind(client.player, operations);
		client.join().send();
		PrintStream standardError = System.err;
		ByteArrayOutputStream reported = new ByteArrayOutputStream();
		System.setErr(new PrintStream(reported, true, StandardCharsets.UTF_8));
		try {
			operations.call(MovementChannel.PHYSICS, true);
			client.player.deliver(teleport(new Vec3d(8.5, 66.0, 8.5), 0.0F, 0.0F, Set.of()));

			awaitState(state -> state.getFailure() != null);
			sleep(150);
		} finally {
			System.setErr(standardError);
		}

		assertFalse(state().isPhysics());
		assertTrue(state().getFailure().contains("has no block state"), state().getFailure());
		String lines = reported.toString(StandardCharsets.UTF_8);
		assertEquals(1, lines.lines().count(), lines);
		assertTrue(lines.contains("'Alice'"), lines);
		assertEquals(new PhysicsChange(null), operations.call(MovementChannel.PHYSICS, true));
		assertNull(state().getFailure());
	}

	private void awaitState(@NotNull Predicate<MovementState> reached) {
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (!reached.test(state())) {
			assertTrue(System.nanoTime() < deadline, "the state was not reached: " + state());
			sleep(5);
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private @NotNull TestClient bind(@NotNull TestClient client) {
		operations = new TestOperations();
		movement = ClientMovement.bind(client.player, operations, Duration.ofDays(1));
		return client;
	}

	private @NotNull TestClient physics(@NotNull TestClient client) {
		bind(client).join().send();
		assertEquals(new PhysicsChange(null), operations.call(MovementChannel.PHYSICS, true));
		return client;
	}

	private void ticks(int count) {
		for (int tick = 0; tick < count; tick++) movement.tick();
	}

	private @NotNull MovementState state() {
		return operations.call(MovementChannel.STATE, null);
	}

	static @NotNull ClientboundPlayerPosition teleport(@NotNull Vec3d position, float yaw, float pitch, @NotNull Set<Relative> relatives) {
		return ClientboundPlayerPosition.builder().position(position).yaw(yaw).pitch(pitch).relatives(relatives).build();
	}

	private static @NotNull ServerboundMovePlayerPosRot confirmation(double x, double y, double z, float yaw, float pitch) {
		return ServerboundMovePlayerPosRot.builder().position(new Vec3d(x, y, z)).yaw(yaw).pitch(pitch).build();
	}
}
