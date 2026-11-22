package me.whereareiam.anvil.edition.java.protocol.worker.position;

import me.whereareiam.anvil.edition.java.protocol.LocalPlayer;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundAcceptTeleportation;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.model.player.ViewRotation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static me.whereareiam.anvil.edition.java.protocol.worker.position.JoinedPlayers.ENTITY_ID;
import static me.whereareiam.anvil.edition.java.protocol.worker.position.JoinedPlayers.OVERWORLD_TYPE;
import static me.whereareiam.anvil.edition.java.protocol.worker.position.JoinedPlayers.joined;
import static me.whereareiam.anvil.edition.java.protocol.worker.position.JoinedPlayers.teleport;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientPositionTeleportTest {
	private static final Set<Relative> ALL_RELATIVE = EnumSet.of(Relative.X, Relative.Y, Relative.Z, Relative.YAW,
			Relative.PITCH);

	@Test
	void acceptsEveryTeleportWithThePositionAndViewItLeftThePlayerAtAndConfirmsItBefore26_3() {
		TestJavaPlayer player = joined("1.18.2");

		player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 90.0F, 10.0F, Set.of()));
		player.deliver(teleport(new Vec3d(1.0, 0.0, -1.0), 5.0F, 100.0F, ALL_RELATIVE));

		assertEquals(List.of(acceptance(8.5, 65.0, 8.5, 90.0F, 10.0F), confirmation(8.5, 65.0, 8.5, 90.0F, 10.0F),
				acceptance(9.5, 65.0, 7.5, 95.0F, 110.0F), confirmation(9.5, 65.0, 7.5, 95.0F, 110.0F)), player.sent());
		assertEquals(Optional.of(new Vec3d(9.5, 65.0, 7.5)), player.protocol().player().position());
		assertEquals(new ViewRotation(95.0F, 110.0F), player.viewRotation());
	}

	@Test
	void acceptsATeleportWithThePlacementAloneFrom26_3() {
		TestJavaPlayer player = joined("26.3");

		player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 90.0F, 10.0F, Set.of()));

		assertEquals(List.of(acceptance(8.5, 65.0, 8.5, 90.0F, 10.0F)), player.sent());
	}

	@Test
	void clampsTheTeleportedPitchFrom1_21_2() {
		for (String version : List.of("1.21.1", "1.21.11")) {
			TestJavaPlayer player = joined(version);

			player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 0.0F, 80.0F, Set.of()));
			player.deliver(teleport(Vec3d.ZERO, 0.0F, 30.0F, ALL_RELATIVE));

			float pitch = version.equals("1.21.1") ? 110.0F : 90.0F;
			assertEquals(confirmation(8.5, 65.0, 8.5, 0.0F, pitch), player.sent().getLast(), version);
		}
	}

	@Test
	void takesTheVelocityTheServerGivesItsOwnEntityAndNoOther() {
		TestJavaPlayer player = joined("1.21.11");

		player.deliver(new ClientboundSetEntityMotion(new EntityId(ENTITY_ID.getValue() + 1), new Vec3d(1.0, 1.0, 1.0)));
		player.deliver(new ClientboundSetEntityMotion(ENTITY_ID, new Vec3d(0.25, 0.5, -0.125)));

		assertEquals(new Vec3d(0.25, 0.5, -0.125), player.protocol().player().velocity());
	}

	@Test
	void keepsTheVelocityAlongTheTeleportsRelativeComponentsAndSetsTheOthers() {
		TestJavaPlayer player = joined("1.21.11");
		player.deliver(teleport(new Vec3d(8.5, 120.0, 8.5), 0.0F, 0.0F, Set.of()));
		player.deliver(new ClientboundSetEntityMotion(ENTITY_ID, new Vec3d(0.25, -0.5, 0.125)));

		player.deliver(ClientboundPlayerPosition.builder()
				.position(Vec3d.ZERO)
				.deltaMovement(new Vec3d(0.5, 0.0, 0.0))
				.relatives(EnumSet.of(Relative.X, Relative.Y, Relative.Z, Relative.YAW, Relative.PITCH, Relative.DELTA_Y))
				.build());

		assertEquals(new Vec3d(0.5, -0.5, 0.0), player.protocol().player().velocity());
		player.deliver(teleport(new Vec3d(8.5, 120.0, 8.5), 0.0F, 0.0F, EnumSet.of(Relative.YAW)));
		assertEquals(Vec3d.ZERO, player.protocol().player().velocity());
	}

	@Test
	void makesANewPlayerOnARespawnAndAClosedConnection() {
		TestJavaPlayer player = joined("1.21.11");
		LocalPlayer client = player.protocol().player();
		player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 45.0F, 0.0F, Set.of()));

		player.deliver(ClientboundRespawn.builder().dimension("minecraft:overworld").dimensionType(OVERWORLD_TYPE).build());

		assertEquals(Optional.empty(), client.position());
		assertEquals(new ViewRotation(0, 0), client.rotation());
		assertEquals(Optional.of(ENTITY_ID), client.entityId());
		player.deliver(teleport(new Vec3d(8.5, 65.0, 8.5), 45.0F, 0.0F, Set.of()));
		player.disconnect();
		assertEquals(Optional.empty(), client.position());
		assertEquals(Optional.empty(), client.entityId());
	}

	private static @NotNull ServerboundAcceptTeleportation acceptance(double x, double y, double z, float yaw, float pitch) {
		return ServerboundAcceptTeleportation.builder().position(new Vec3d(x, y, z)).yaw(yaw).pitch(pitch).build();
	}

	private static @NotNull ServerboundMovePlayerPosRot confirmation(double x, double y, double z, float yaw, float pitch) {
		return ServerboundMovePlayerPosRot.builder().position(new Vec3d(x, y, z)).yaw(yaw).pitch(pitch).build();
	}
}
