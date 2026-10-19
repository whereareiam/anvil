package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.protocol.java.api.type.PlayerCommandAction;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionReporterTest {
	@Test
	void sendsThePositionOnlyWhenItMovedEnoughOrEveryTwentyTicks() {
		Fixture fixture = new Fixture("1.21.11");
		PlayerSimulation player = fixture.player(8.5, 65.0, 8.5);
		PositionReporter reporter = new PositionReporter(fixture.rules, 7);
		reporter.sent(8.5, 65.0, 8.5, 0.0F, 0.0F, false);

		player.place(8.5 + 1.0E-4, 65.0, 8.5, Vector.ZERO, 0.0F, 0.0F);
		assertEquals(List.of(), reporter.report(player));

		player.place(8.5 + 3.0E-4, 65.0, 8.5, Vector.ZERO, 0.0F, 0.0F);
		assertEquals(List.of(ServerboundMovePlayerPos.builder().position(new Vec3d(8.5 + 3.0E-4, 65.0, 8.5)).build()),
				reporter.report(player));

		for (int tick = 0; tick < 19; tick++) assertEquals(List.of(), reporter.report(player));
		assertEquals(List.of(ServerboundMovePlayerPos.builder().position(new Vec3d(8.5 + 3.0E-4, 65.0, 8.5)).build()),
				reporter.report(player));
	}

	@Test
	void sendsTheRotationAloneOrTogetherWithThePosition() {
		Fixture fixture = new Fixture("1.21.11");
		PlayerSimulation player = fixture.player(8.5, 65.0, 8.5);
		PositionReporter reporter = new PositionReporter(fixture.rules, 7);
		reporter.sent(8.5, 65.0, 8.5, 0.0F, 0.0F, false);

		player.look(90.0F, 10.0F);
		assertEquals(List.of(ServerboundMovePlayerRot.builder().yaw(90.0F).pitch(10.0F).build()), reporter.report(player));

		player.place(9.5, 65.0, 8.5, Vector.ZERO, 45.0F, 10.0F);
		assertEquals(List.of(ServerboundMovePlayerPosRot.builder().position(new Vec3d(9.5, 65.0, 8.5)).yaw(45.0F).pitch(10.0F).build()),
				reporter.report(player));
	}

	@Test
	void sendsTheGroundStateAloneWhenOnlyItChanged() {
		Fixture fixture = new Fixture("1.20.6");
		fixture.world.floor(0, 15, 64, 0, 15, "minecraft:stone");
		PlayerSimulation player = fixture.player(8.5, 65.0, 8.5);
		PositionReporter reporter = new PositionReporter(fixture.rules, 7);
		reporter.sent(8.5, 65.0, 8.5, 0.0F, 0.0F, false);

		player.tick(MovementInput.NONE);
		player.tick(MovementInput.NONE);

		assertEquals(List.of(ServerboundMovePlayerStatusOnly.builder().onGround(true).build()), reporter.report(player));
		assertEquals(List.of(), reporter.report(player));
	}

	@Test
	void tellsSprintingAndSneakingThroughCommandsBefore1_21_2() {
		Fixture fixture = new Fixture("1.20.6");
		fixture.world.floor(0, 15, 64, 0, 15, "minecraft:stone");
		PlayerSimulation player = fixture.player(8.5, 65.0, 8.5);
		PositionReporter reporter = new PositionReporter(fixture.rules, 7);
		reporter.sent(8.5, 65.0, 8.5, 0.0F, 0.0F, true);
		for (int tick = 0; tick < 5; tick++) player.tick(MovementInput.NONE);
		reporter.report(player);

		player.tick(MovementInput.builder().forward(true).sprint(true).build());
		List<ServerboundPacket> sprinting = reporter.report(player);
		assertEquals(new ServerboundPlayerCommand(7, PlayerCommandAction.START_SPRINTING), sprinting.getFirst());

		player.tick(MovementInput.builder().sneak(true).build());
		List<ServerboundPacket> sneaking = reporter.report(player);
		assertEquals(List.of(new ServerboundPlayerCommand(7, PlayerCommandAction.STOP_SPRINTING),
				new ServerboundPlayerCommand(7, PlayerCommandAction.PRESS_SHIFT_KEY)), sneaking.subList(0, 2));
	}

	@Test
	void tellsTheHeldKeysFrom1_21_2AndSneakingOnlyThroughThem() {
		Fixture fixture = new Fixture("1.21.11");
		fixture.world.floor(0, 15, 64, 0, 15, "minecraft:stone");
		PlayerSimulation player = fixture.player(8.5, 65.0, 8.5);
		PositionReporter reporter = new PositionReporter(fixture.rules, 7);
		reporter.sent(8.5, 65.0, 8.5, 0.0F, 0.0F, true);
		for (int tick = 0; tick < 5; tick++) player.tick(MovementInput.NONE);
		reporter.report(player);

		player.tick(MovementInput.builder().sneak(true).build());
		List<ServerboundPacket> packets = reporter.report(player);

		assertEquals(ServerboundPlayerInput.builder().shift(true).build(), packets.getFirst());
		assertEquals(0, packets.stream().filter(ServerboundPlayerCommand.class::isInstance).count());
		player.tick(MovementInput.builder().sneak(true).build());
		assertEquals(0, reporter.report(player).stream().filter(ServerboundPlayerInput.class::isInstance).count());
	}

	@Test
	void reportsHorizontalCollisionOnlyFrom1_21_2() {
		for (String version : List.of("1.20.6", "1.21.11")) {
			Fixture fixture = new Fixture(version);
			fixture.world.floor(0, 15, 64, 0, 15, "minecraft:stone").floor(0, 15, 65, 10, 15, "minecraft:stone")
					.floor(0, 15, 66, 10, 15, "minecraft:stone");
			PlayerSimulation player = fixture.player(8.5, 65.0, 9.5);
			PositionReporter reporter = new PositionReporter(fixture.rules, 7);
			boolean reported = false;
			for (int tick = 0; tick < 15; tick++) {
				player.tick(MovementInput.builder().forward(true).build());
				reported |= reporter.report(player).stream().anyMatch(PositionReporterTest::collides);
			}

			assertTrue(player.horizontalCollision(), version);
			assertEquals(version.equals("1.21.11"), reported, version);
		}
	}

	private static boolean collides(@NotNull ServerboundPacket packet) {
		return packet instanceof ServerboundMovePlayerPos move && move.isHorizontalCollision()
				|| packet instanceof ServerboundMovePlayerStatusOnly status && status.isHorizontalCollision()
				|| packet instanceof ServerboundMovePlayerPosRot move && move.isHorizontalCollision();
	}

	/**
	 * A world of one loaded column and the rules of one version.
	 */
	private static final class Fixture {
		private final TestWorld world;
		private final MovementRules rules;

		private Fixture(@NotNull String version) {
			this.world = new TestWorld(version).load(0, 0);
			this.rules = new MovementRules(MinecraftVersion.parse(version));
		}

		private @NotNull PlayerSimulation player(double x, double y, double z) {
			return new PlayerSimulation(rules, new BlockView(world, world.registry()), x, y, z, 0.0F, 0.0F);
		}
	}
}
