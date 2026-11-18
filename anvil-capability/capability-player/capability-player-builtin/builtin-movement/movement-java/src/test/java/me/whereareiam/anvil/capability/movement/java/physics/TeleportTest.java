package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportTest {
	private static final MovementRules MODERN = new MovementRules(MinecraftVersion.parse("1.21.11"));
	private static final MovementRules LEGACY = new MovementRules(MinecraftVersion.parse("1.20.6"));
	private static final Position FROM = Position.builder().x(10).y(64).z(-5).yaw(30).pitch(20).onGround(true).build();
	private static final Vector MOVING = new Vector(0.2, -0.1, 0.3);

	@Test
	void placesTheClientAbsolutelyAndStopsItsVelocity() {
		Teleport.Resolved resolved = Teleport.resolve(FROM, MOVING, teleport(new Vec3d(1, 70, 2), 90, 10, Set.of()),
				MODERN);

		assertEquals(Position.builder().x(1).y(70).z(2).yaw(90).pitch(10).onGround(true).build(), resolved.at());
		assertEquals(new Vector(0, 0, 0), resolved.velocity());
	}

	@Test
	void addsEachRelativeCoordinateAndAngleToWhereTheClientIs() {
		Teleport.Resolved resolved = Teleport.resolve(FROM, MOVING, teleport(new Vec3d(1, 0, -1), 10, -5,
				Set.of(Relative.X, Relative.Z, Relative.YAW, Relative.PITCH)), MODERN);

		assertEquals(Position.builder().x(11).y(0).z(-6).yaw(40).pitch(15).onGround(true).build(), resolved.at());
	}

	@Test
	void keepsTheVelocityAlongEachRelativeDeltaAndAddsTheSentOne() {
		ClientboundPlayerPosition packet = ClientboundPlayerPosition.builder()
				.position(new Vec3d(0, 0, 0))
				.deltaMovement(new Vec3d(0.5, 0, 0))
				.relatives(Set.of(Relative.X, Relative.Y, Relative.Z, Relative.DELTA_X, Relative.DELTA_Y))
				.build();

		Teleport.Resolved resolved = Teleport.resolve(FROM, MOVING, packet, MODERN);

		assertEquals(new Vector(0.7, -0.1, 0), resolved.velocity());
	}

	@Test
	void clampsThePitchOnlyFromMinecraft1212() {
		ClientboundPlayerPosition packet = teleport(new Vec3d(0, 0, 0), 0, 80, Set.of(Relative.PITCH));

		assertEquals(90, Teleport.resolve(FROM, MOVING, packet, MODERN).at().getPitch());
		assertEquals(100, Teleport.resolve(FROM, MOVING, packet, LEGACY).at().getPitch());
	}

	private static ClientboundPlayerPosition teleport(Vec3d position, float yaw, float pitch, Set<Relative> relatives) {
		return ClientboundPlayerPosition.builder()
				.position(position)
				.yaw(yaw)
				.pitch(pitch)
				.relatives(relatives)
				.build();
	}
}
