package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.java.physics.geometry.Vector;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.java.physics.simulation.PlayerSimulation;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.protocol.java.api.type.player.PlayerCommandAction;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionReporterTest {
	private static final Position START = Position.builder().x(8.5).y(65.0).z(8.5).build();

	@Test
	void sendsThePositionOnlyWhenItMovedEnoughOrEveryTwentyTicks() {
		Fixture fixture = new Fixture("1.21.11", true);
		fixture.reporter.sent(START);

		fixture.player.place(new Vector(8.5 + 1.0E-4, 65.0, 8.5), Vector.ZERO, 0.0F, 0.0F);
		assertEquals(List.of(), fixture.report());

		fixture.player.place(new Vector(8.5 + 3.0E-4, 65.0, 8.5), Vector.ZERO, 0.0F, 0.0F);
		ServerboundPacket moved = ServerboundMovePlayerPos.builder().position(new Vec3d(8.5 + 3.0E-4, 65.0, 8.5)).build();
		assertEquals(List.of(moved), fixture.report());

		for (int tick = 0; tick < 19; tick++) assertEquals(List.of(), fixture.report());
		assertEquals(List.of(moved), fixture.report());
	}

	@Test
	void sendsTheRotationAloneOrTogetherWithThePosition() {
		Fixture fixture = new Fixture("1.21.11", true);
		fixture.reporter.sent(START);

		fixture.player.look(90.0F, 10.0F);
		assertEquals(List.of(ServerboundMovePlayerRot.builder().yaw(90.0F).pitch(10.0F).build()), fixture.report());

		fixture.player.place(new Vector(9.5, 65.0, 8.5), Vector.ZERO, 45.0F, 10.0F);
		assertEquals(List.of(ServerboundMovePlayerPosRot.builder().position(new Vec3d(9.5, 65.0, 8.5)).yaw(45.0F)
				.pitch(10.0F).build()), fixture.report());
	}

	@Test
	void reportsThePositionOfANewPlayerThatToldNothing() {
		Fixture fixture = new Fixture("1.21.11", true);

		assertEquals(List.of(ServerboundMovePlayerPos.builder().position(new Vec3d(8.5, 65.0, 8.5)).build()),
				fixture.report());
	}

	@Test
	void sendsTheGroundStateAloneWhenOnlyItChanged() {
		Fixture fixture = new Fixture("1.20.6", false);
		fixture.client.floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		fixture.reporter.sent(START);

		fixture.player.tick(MovementInput.NONE);
		fixture.player.tick(MovementInput.NONE);

		assertEquals(List.of(ServerboundMovePlayerStatusOnly.builder().onGround(true).build()), fixture.report());
		assertEquals(List.of(), fixture.report());
	}

	@Test
	void tellsSprintingAndSneakingThroughCommandsWhereTheReleaseCarriesNoHeldKeys() {
		Fixture fixture = new Fixture("1.20.6", false);
		fixture.client.floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		fixture.settle();

		fixture.player.tick(MovementInput.builder().forward(true).sprint(true).build());
		assertEquals(command(PlayerCommandAction.START_SPRINTING), fixture.report().getFirst());

		fixture.player.tick(MovementInput.builder().sneak(true).build());
		assertEquals(List.of(command(PlayerCommandAction.STOP_SPRINTING), command(PlayerCommandAction.PRESS_SHIFT_KEY)),
				fixture.report().subList(0, 2));
	}

	@Test
	void tellsTheHeldKeysWhereTheReleaseCarriesThemAndSneakingOnlyThroughThem() {
		Fixture fixture = new Fixture("1.21.11", true);
		fixture.client.floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		fixture.settle();

		fixture.player.tick(MovementInput.builder().sneak(true).build());
		List<ServerboundPacket> packets = fixture.report();

		assertEquals(ServerboundPlayerInput.builder().shift(true).build(), packets.getFirst());
		assertEquals(0, packets.stream().filter(ServerboundPlayerCommand.class::isInstance).count());
		fixture.player.tick(MovementInput.builder().sneak(true).build());
		assertEquals(0, fixture.report().stream().filter(ServerboundPlayerInput.class::isInstance).count());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void reportsHorizontalCollisionOnlyFrom1_21_2(@NotNull String version) {
		boolean modern = version.equals("1.21.11");
		Fixture fixture = new Fixture(version, modern);
		fixture.client.floor(0, 15, 64, 0, 15, "minecraft:stone").floor(0, 15, 65, 10, 15, "minecraft:stone")
				.floor(0, 15, 66, 10, 15, "minecraft:stone").send();
		fixture.player.place(new Vector(8.5, 65.0, 9.5), Vector.ZERO, 0.0F, 0.0F);
		boolean reported = false;
		for (int tick = 0; tick < 15; tick++) {
			fixture.player.tick(MovementInput.builder().forward(true).build());
			reported |= fixture.report().stream().anyMatch(PositionReporterTest::collides);
		}

		assertTrue(fixture.player.body().isHorizontalCollision());
		assertEquals(modern, reported);
	}

	private static boolean collides(@NotNull ServerboundPacket packet) {
		return packet instanceof ServerboundMovePlayerPos move && move.isHorizontalCollision()
				|| packet instanceof ServerboundMovePlayerStatusOnly status && status.isHorizontalCollision()
				|| packet instanceof ServerboundMovePlayerPosRot move && move.isHorizontalCollision();
	}

	private static @NotNull ServerboundPlayerCommand command(@NotNull PlayerCommandAction action) {
		return new ServerboundPlayerCommand(TestClient.ENTITY_ID, action);
	}

	/**
	 * A simulated player of one version and the reporter of its connection.
	 */
	private static final class Fixture {
		private final TestClient client;
		private final PlayerSimulation player;
		private final PositionReporter reporter;

		private Fixture(@NotNull String version, boolean inputPackets) {
			this.client = new TestClient(version).join().load(0, 0);
			PlayerState state = new PlayerState(client.version);
			state.login(ClientboundLogin.builder().entityId(TestClient.ENTITY_ID).dimension("minecraft:overworld")
					.dimensionType(client.dimension).build());
			this.player = client.simulation(state, 8.5, 65.0, 8.5);
			this.reporter = new PositionReporter(new MovementRules(client.version), state, inputPackets);
		}

		private @NotNull List<ServerboundPacket> report() {
			return reporter.report(player.body());
		}

		private void settle() {
			for (int tick = 0; tick < 5; tick++) player.tick(MovementInput.NONE);
			report();
		}
	}
}
