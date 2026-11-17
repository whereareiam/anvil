package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.channel.MovementState;
import me.whereareiam.anvil.capability.movement.channel.PhysicsChange;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerLoaded;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.type.entity.Relative;
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

import static me.whereareiam.anvil.capability.movement.java.physics.BoundMovement.LOADING_STARTED;
import static me.whereareiam.anvil.capability.movement.java.physics.BoundMovement.acceptance;
import static me.whereareiam.anvil.capability.movement.java.physics.BoundMovement.area;
import static me.whereareiam.anvil.capability.movement.java.physics.BoundMovement.confirmation;
import static me.whereareiam.anvil.capability.movement.java.physics.BoundMovement.teleport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMovementTest {
	private final BoundMovement movement = new BoundMovement();

	@AfterEach
	void close() {
		movement.close();
	}

	@Test
	void takesTheVelocityTheServerGivesItsOwnEntityAndNoOther() {
		TestClient client = movement.physics(new TestClient("1.21.11").load(0, 0));
		client.player.deliver(teleport(new Vec3d(8.5, 120.0, 8.5), 0.0F, 0.0F, Set.of()));
		movement.ticks(1);

		client.player.deliver(new ClientboundSetEntityMotion(new EntityId(TestClient.ENTITY_ID.getValue() + 1), new Vec3d(1.0, 1.0, 1.0)));
		client.player.deliver(new ClientboundSetEntityMotion(TestClient.ENTITY_ID, new Vec3d(0.25, 0.5, -0.125)));

		assertEquals(0.25, movement.state().getMotion().getX());
		assertEquals(0.5, movement.state().getMotion().getY());
		assertEquals(-0.125, movement.state().getMotion().getZ());
	}

	@Test
	void goesOnFromATeleportWithTheVelocityItKeptAndAnswersItAtOnce() {
		TestClient client = movement.physics(new TestClient("1.16.5").load(0, 0));
		client.player.deliver(teleport(new Vec3d(8.5, 120.0, 8.5), 0.0F, 0.0F, Set.of()));
		movement.ticks(5);
		double falling = movement.state().getMotion().getY();
		int sent = client.player.sent().size();

		client.player.deliver(teleport(new Vec3d(0.0, 0.0, 0.0), 30.0F, 0.0F,
				EnumSet.of(Relative.X, Relative.Y, Relative.Z, Relative.DELTA_Y)));

		assertEquals(List.of(acceptance(8.5, movement.state().getPosition().getY(), 8.5, 30.0F, 0.0F),
						confirmation(8.5, movement.state().getPosition().getY(), 8.5, 30.0F, 0.0F)),
				client.player.sent().subList(sent, client.player.sent().size()));
		assertEquals(falling, movement.state().getMotion().getY());
		movement.ticks(1);
		assertTrue(movement.state().getMotion().getY() < falling, "the player falls on from the teleport");
	}

	@Test
	void tellsWhyAReleaseWithoutBlockDataCannotSimulateAndStaysOff() {
		movement.bind(new TestClient("1.16.3", 753)).join();

		String reason = movement.call(MovementChannel.PHYSICS, true).getRefusal();

		assertTrue(reason != null && reason.contains("753"), reason);
		assertFalse(movement.state().isPhysics());
		assertEquals(new PhysicsChange(null), movement.call(MovementChannel.PHYSICS, false));
	}

	@Test
	void fallsToTheGroundAndReportsItAsTheVanillaClientDoes() {
		TestClient client = movement.physics(area(new TestClient("1.21.11")).floor(0, 15, 64, 0, 15, "minecraft:stone"));
		movement.enter(client, new Vec3d(8.5, 67.0, 8.5));
		int sent = client.player.sent().size();

		movement.ticks(20);

		MovementState state = movement.state();
		assertEquals(20, state.getTicks());
		assertEquals(65.0, state.getPosition().getY());
		assertTrue(state.getPosition().isOnGround());
		assertEquals(-0.0784000015258789, state.getMotion().getY());
		List<?> ticked = client.player.sent().subList(sent, client.player.sent().size());
		assertEquals(20, ticked.stream().filter(ServerboundClientTickEnd.class::isInstance).count());
		assertEquals(new ServerboundPlayerLoaded(), ticked.getFirst());
		assertTrue(ticked.get(1) instanceof ServerboundMovePlayerPos, ticked.get(1).toString());
	}

	@Test
	void placesThePlayerWhereATestMovesItAndContinuesFromThere() {
		TestClient client = movement.physics(area(new TestClient("1.21.11")).floor(0, 15, 64, 0, 15, "minecraft:stone"));
		movement.enter(client, new Vec3d(8.5, 65.0, 8.5));

		movement.call(MovementChannel.MOVE, Position.builder().x(4.5).y(70.0).z(4.5).build());
		assertEquals(70.0, movement.state().getPosition().getY());
		movement.ticks(2);

		assertEquals(4.5, movement.state().getPosition().getX());
		assertTrue(movement.state().getPosition().getY() < 70.0);
	}

	@Test
	void turnsTheViewAtOnceWithoutPhysicsAndWithTheNextTickWithIt() {
		TestClient client = movement.bind(area(new TestClient("1.21.11")).floor(0, 15, 64, 0, 15, "minecraft:stone")).join();
		client.send().player.deliver(LOADING_STARTED);
		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));
		movement.ticks(1);
		int sent = client.player.sent().size();

		movement.call(MovementChannel.LOOK, new ViewRotation(45.0F, 5.0F));
		assertEquals(List.of(ServerboundMovePlayerRot.builder().yaw(45.0F).pitch(5.0F).build()),
				client.player.sent().subList(sent, client.player.sent().size()));
		assertEquals(new ViewRotation(45.0F, 5.0F), client.player.viewRotation());

		movement.call(MovementChannel.PHYSICS, true);
		sent = client.player.sent().size();
		movement.call(MovementChannel.LOOK, new ViewRotation(90.0F, 0.0F));
		assertEquals(sent, client.player.sent().size());
		movement.ticks(1);
		assertTrue(client.player.sent(ServerboundMovePlayerRot.class).stream().anyMatch(rotation -> rotation.getYaw() == 90.0F)
				|| client.player.sent(ServerboundMovePlayerPosRot.class).stream().anyMatch(move -> move.getYaw() == 90.0F));
	}

	@Test
	void forgetsThePositionWhenTheConnectionEndsAndKeepsPhysicsOn() {
		TestClient client = movement.physics(new TestClient("1.21.11"));
		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 0.0F, Set.of()));

		client.player.rejoin();

		assertNull(movement.state().getPosition());
		assertTrue(movement.state().isPhysics());
	}

	@Test
	void runsThePhysicsOfAPlayerWhoseEntityIdIsNegative() {
		TestClient client = movement.bind(area(new TestClient("1.21.11")));
		client.player.connect();
		client.player.deliver(me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin.builder()
				.entityId(new EntityId(-39858984)).level("minecraft:overworld").dimension("minecraft:overworld")
				.dimensionType(client.dimension).build());
		client.send();
		movement.call(MovementChannel.PHYSICS, true);
		movement.enter(client, new Vec3d(8.5, 120.0, 8.5));

		movement.ticks(1);

		assertEquals(1, movement.state().getTicks());
		assertTrue(movement.state().getMotion().getY() < 0.0);
	}

	@Test
	void tellsWhyAReleaseWithoutTheAbilitiesPacketCannotSimulate() {
		movement.bind(new TestClient("1.21.11", type -> type != ServerboundPlayerAbilities.class)).join();

		String reason = movement.call(MovementChannel.PHYSICS, true).getRefusal();

		assertTrue(reason != null && reason.contains(ServerboundPlayerAbilities.class.getSimpleName()), reason);
		assertFalse(movement.state().isPhysics());
	}

	@Test
	void ticksTwentyTimesASecondWithTheClientUntilTurnedOff() {
		TestClient client = area(new TestClient("1.21.11"));
		movement.bind(client);
		client.join().send();
		movement.call(MovementChannel.PHYSICS, true);
		client.player.deliver(LOADING_STARTED);
		client.player.deliver(teleport(new Vec3d(8.5, 200.0, 8.5), 0.0F, 0.0F, Set.of()));

		long started = System.nanoTime();
		client.player.protocol().start();
		awaitState(state -> state.getTicks() >= 10);
		assertTrue(System.nanoTime() - started >= Duration.ofMillis(400).toNanos(), "ten ticks take half a second");
		movement.call(MovementChannel.PHYSICS, false);
		long ticks = movement.state().getTicks();

		sleep(150);
		assertEquals(ticks, movement.state().getTicks());
		assertFalse(movement.state().isPhysics());
		assertTrue(movement.state().getPosition().getY() < 200.0);
	}

	@Test
	void turnsPhysicsOffWithTheReasonWhenATickFailsAndSaysSoOnce() {
		TestClient client = area(new TestClient("1.21.11"));
		client.state(8, 64, 8, client.registry.stateCount());
		movement.bind(client);
		client.join().send();
		client.player.protocol().start();
		PrintStream standardError = System.err;
		ByteArrayOutputStream reported = new ByteArrayOutputStream();
		System.setErr(new PrintStream(reported, true, StandardCharsets.UTF_8));
		try {
			movement.call(MovementChannel.PHYSICS, true);
			client.player.deliver(LOADING_STARTED);
			client.player.deliver(teleport(new Vec3d(8.5, 66.0, 8.5), 0.0F, 0.0F, Set.of()));

			awaitState(state -> state.getFailure() != null);
			sleep(150);
		} finally {
			System.setErr(standardError);
		}

		assertFalse(movement.state().isPhysics());
		assertTrue(movement.state().getFailure().contains("has no block state"), movement.state().getFailure());
		String lines = reported.toString(StandardCharsets.UTF_8);
		assertEquals(1, lines.lines().count(), lines);
		assertTrue(lines.contains("'Alice'"), lines);
		assertEquals(new PhysicsChange(null), movement.call(MovementChannel.PHYSICS, true));
		assertNull(movement.state().getFailure());
	}

	private void awaitState(@NotNull Predicate<MovementState> reached) {
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (!reached.test(movement.state())) {
			assertTrue(System.nanoTime() < deadline, "the state was not reached: " + movement.state());
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
}
