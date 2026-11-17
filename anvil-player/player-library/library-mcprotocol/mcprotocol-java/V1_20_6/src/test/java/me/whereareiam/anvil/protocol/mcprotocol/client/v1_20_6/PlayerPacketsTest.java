package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.protocol.java.api.type.entity.Relative;
import me.whereareiam.anvil.protocol.java.api.type.player.PlayerCommandAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPacketsTest {
	private final PlayerPackets packets = new PlayerPackets();

	@Test
	void encodesThePositionAndRotationAsOnePacketWithoutHorizontalCollision() {
		Object encoded = packets.encode(move(false, true));

		ServerboundMovePlayerPosRotPacket packet = assertInstanceOf(ServerboundMovePlayerPosRotPacket.class, encoded);
		assertFalse(packet.isOnGround());
		assertEquals(1.5, packet.getX());
		assertEquals(64, packet.getY());
		assertEquals(-2.5, packet.getZ());
		assertEquals(90, packet.getYaw());
		assertEquals(-15, packet.getPitch());
	}

	@Test
	void encodesTheGroundState() {
		Object encoded = packets.encode(move(true, false));

		assertTrue(assertInstanceOf(ServerboundMovePlayerPosRotPacket.class, encoded).isOnGround());
	}

	@Test
	void encodesThePositionTheRotationAndTheGroundStateAloneWithoutHorizontalCollision() {
		ServerboundMovePlayerPosPacket position = assertInstanceOf(ServerboundMovePlayerPosPacket.class,
				packets.encode(ServerboundMovePlayerPos.builder().position(new Vec3d(1, 2, 3)).onGround(true)
						.horizontalCollision(true).build()));
		ServerboundMovePlayerRotPacket rotation = assertInstanceOf(ServerboundMovePlayerRotPacket.class,
				packets.encode(ServerboundMovePlayerRot.builder().yaw(45).pitch(10).horizontalCollision(true).build()));
		ServerboundMovePlayerStatusOnlyPacket status = assertInstanceOf(ServerboundMovePlayerStatusOnlyPacket.class,
				packets.encode(ServerboundMovePlayerStatusOnly.builder().onGround(true).horizontalCollision(true)
						.build()));

		assertEquals(List.of(1.0, 2.0, 3.0), List.of(position.getX(), position.getY(), position.getZ()));
		assertTrue(position.isOnGround());
		assertEquals(45, rotation.getYaw());
		assertEquals(10, rotation.getPitch());
		assertFalse(rotation.isOnGround());
		assertTrue(status.isOnGround());
	}

	@Test
	void encodesSprintingAndSneakingAsPlayerStates() {
		ServerboundPlayerCommandPacket sprint = command(PlayerCommandAction.START_SPRINTING);

		assertEquals(7, sprint.getEntityId());
		assertEquals(PlayerState.START_SPRINTING, sprint.getState());
		assertEquals(PlayerState.STOP_SPRINTING, command(PlayerCommandAction.STOP_SPRINTING).getState());
		assertEquals(PlayerState.START_SNEAKING, command(PlayerCommandAction.PRESS_SHIFT_KEY).getState());
		assertEquals(PlayerState.STOP_SNEAKING, command(PlayerCommandAction.RELEASE_SHIFT_KEY).getState());
	}

	@Test
	void decodesThePlayersPlacementKeepingTheVelocityAlongEachRelativeCoordinate() {
		ClientboundPlayerPositionPacket position = new ClientboundPlayerPositionPacket(10, 64, -10, 90, 5, 1,
				PositionElement.X, PositionElement.Z, PositionElement.PITCH);

		assertEquals(ClientboundPlayerPosition.builder().position(new Vec3d(10, 64, -10)).yaw(90).pitch(5)
				.deltaMovement(Vec3d.ZERO)
				.relatives(Set.of(Relative.X, Relative.DELTA_X, Relative.Z, Relative.DELTA_Z, Relative.PITCH))
				.build(), packets.decode(position));
	}

	@Test
	void decodesAnAbsolutePlacementStoppingTheVelocity() {
		ClientboundPlayerPositionPacket position = new ClientboundPlayerPositionPacket(10, 64, -10, 90, 5, 1);

		assertEquals(ClientboundPlayerPosition.builder().position(new Vec3d(10, 64, -10)).yaw(90).pitch(5)
				.relatives(Set.of()).build(), packets.decode(position));
	}

	@Test
	void decodesTheAbilitiesTheServerGrants() {
		assertEquals(ClientboundPlayerAbilities.builder().invulnerable(true).mayFly(true).instabuild(true)
						.flyingSpeed(0.05F).walkingSpeed(0.1F).build(),
				packets.decode(new ClientboundPlayerAbilitiesPacket(true, true, false, true, 0.05F, 0.1F)));
		assertEquals(ClientboundPlayerAbilities.builder().flying(true).mayFly(true).flyingSpeed(0.1F)
						.walkingSpeed(0.2F).build(),
				packets.decode(new ClientboundPlayerAbilitiesPacket(false, true, true, false, 0.1F, 0.2F)));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesThePlayerPacketsOfTheReleaseButItsInputAndTickEnd() {
		assertEquals(Set.of(
				ClientboundPlayerAbilities.class,
				ClientboundPlayerPosition.class,
				ServerboundMovePlayerPos.class,
				ServerboundMovePlayerPosRot.class,
				ServerboundMovePlayerRot.class,
				ServerboundMovePlayerStatusOnly.class,
				ServerboundPlayerCommand.class
		), packets.packets());
	}

	private ServerboundPlayerCommandPacket command(PlayerCommandAction action) {
		return assertInstanceOf(ServerboundPlayerCommandPacket.class,
				packets.encode(new ServerboundPlayerCommand(new EntityId(7), action)));
	}

	private static ServerboundMovePlayerPosRot move(boolean onGround, boolean horizontalCollision) {
		return ServerboundMovePlayerPosRot.builder()
				.position(new Vec3d(1.5, 64, -2.5))
				.yaw(90)
				.pitch(-15)
				.onGround(onGround)
				.horizontalCollision(horizontalCollision)
				.build();
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
