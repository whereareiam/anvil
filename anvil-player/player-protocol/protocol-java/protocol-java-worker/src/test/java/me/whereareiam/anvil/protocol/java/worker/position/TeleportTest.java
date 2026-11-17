package me.whereareiam.anvil.protocol.java.worker.position;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.type.entity.Relative;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportTest {
	private static final Set<LocalPlayerBehaviour> MODERN = LocalPlayerBehaviour.of(MinecraftVersion.parse("1.21.11"));
	private static final Set<LocalPlayerBehaviour> LEGACY = LocalPlayerBehaviour.of(MinecraftVersion.parse("1.20.6"));
	private static final Vec3d FROM = new Vec3d(10, 64, -5);
	private static final ViewRotation VIEW = new ViewRotation(30, 20);
	private static final Vec3d MOVING = new Vec3d(0.2, -0.1, 0.3);

	@Test
	void placesTheClientAbsolutelyAndStopsItsVelocity() {
		Teleport.Resolved resolved = Teleport.resolve(FROM, VIEW, MOVING, teleport(new Vec3d(1, 70, 2), 90, 10,
				Set.of()), MODERN);

		assertEquals(new Vec3d(1, 70, 2), resolved.position());
		assertEquals(new ViewRotation(90, 10), resolved.view());
		assertEquals(Vec3d.ZERO, resolved.velocity());
	}

	@Test
	void addsEachRelativeCoordinateAndAngleToWhereTheClientIs() {
		Teleport.Resolved resolved = Teleport.resolve(FROM, VIEW, MOVING, teleport(new Vec3d(1, 0, -1), 10, -5,
				Set.of(Relative.X, Relative.Z, Relative.YAW, Relative.PITCH)), MODERN);

		assertEquals(new Vec3d(11, 0, -6), resolved.position());
		assertEquals(new ViewRotation(40, 15), resolved.view());
	}

	@Test
	void keepsTheVelocityAlongEachRelativeDeltaAndAddsTheSentOne() {
		ClientboundPlayerPosition packet = ClientboundPlayerPosition.builder()
				.position(Vec3d.ZERO)
				.deltaMovement(new Vec3d(0.5, 0, 0))
				.relatives(Set.of(Relative.X, Relative.Y, Relative.Z, Relative.DELTA_X, Relative.DELTA_Y))
				.build();

		Teleport.Resolved resolved = Teleport.resolve(FROM, VIEW, MOVING, packet, MODERN);

		assertEquals(new Vec3d(0.7, -0.1, 0), resolved.velocity());
	}

	@Test
	void turnsTheKeptVelocityByTheChangeOfTheViewWhenTheTeleportSaysSo() {
		ClientboundPlayerPosition packet = ClientboundPlayerPosition.builder()
				.position(Vec3d.ZERO)
				.yaw(-60)
				.relatives(Set.of(Relative.X, Relative.Y, Relative.Z, Relative.PITCH, Relative.DELTA_X, Relative.DELTA_Y,
						Relative.DELTA_Z, Relative.ROTATE_DELTA))
				.build();

		Vec3d velocity = Teleport.resolve(FROM, new ViewRotation(30, 0), new Vec3d(1, 0, 0), packet, MODERN).velocity();

		assertEquals(0.0, velocity.getX(), 1.0E-6);
		assertEquals(1.0, Math.abs(velocity.getZ()), 1.0E-6);
	}

	@Test
	void clampsThePitchOnlyFromMinecraft1_21_2() {
		ClientboundPlayerPosition packet = teleport(Vec3d.ZERO, 0, 80, Set.of(Relative.PITCH));

		assertEquals(90, Teleport.resolve(FROM, VIEW, MOVING, packet, MODERN).view().getPitch());
		assertEquals(100, Teleport.resolve(FROM, VIEW, MOVING, packet, LEGACY).view().getPitch());
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
