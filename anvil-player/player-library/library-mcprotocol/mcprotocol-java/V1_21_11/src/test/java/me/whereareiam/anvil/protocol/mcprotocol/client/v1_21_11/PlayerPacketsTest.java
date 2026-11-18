package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.edition.java.protocol.type.player.PlayerCommandAction;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPacketsTest {
	private final PlayerPackets packets = new PlayerPackets();

	@Test
	void encodesThePositionRotationAndBothFlagsAsOnePacket() {
		Object encoded = packets.encode(move(true, false));

		ServerboundMovePlayerPosRotPacket packet = assertInstanceOf(ServerboundMovePlayerPosRotPacket.class, encoded);
		assertTrue(packet.isOnGround());
		assertFalse(packet.isHorizontalCollision());
		assertEquals(1.5, packet.getX());
		assertEquals(64, packet.getY());
		assertEquals(-2.5, packet.getZ());
		assertEquals(90, packet.getYaw());
		assertEquals(-15, packet.getPitch());
	}

	@Test
	void encodesHorizontalCollision() {
		Object encoded = packets.encode(move(false, true));

		ServerboundMovePlayerPosRotPacket packet = assertInstanceOf(ServerboundMovePlayerPosRotPacket.class, encoded);
		assertFalse(packet.isOnGround());
		assertTrue(packet.isHorizontalCollision());
	}

	@Test
	void encodesThePositionAloneTheRotationAloneAndTheFlagsAloneWithHorizontalCollision() {
		ServerboundMovePlayerPosPacket position = assertInstanceOf(ServerboundMovePlayerPosPacket.class,
				packets.encode(ServerboundMovePlayerPos.builder().position(new Vec3d(1.5, 64, -2.5)).onGround(true)
						.horizontalCollision(true).build()));
		assertEquals(1.5, position.getX());
		assertEquals(64, position.getY());
		assertEquals(-2.5, position.getZ());
		assertTrue(position.isOnGround());
		assertTrue(position.isHorizontalCollision());

		ServerboundMovePlayerRotPacket rotation = assertInstanceOf(ServerboundMovePlayerRotPacket.class,
				packets.encode(ServerboundMovePlayerRot.builder().yaw(90).pitch(-15).onGround(false)
						.horizontalCollision(true).build()));
		assertEquals(90, rotation.getYaw());
		assertEquals(-15, rotation.getPitch());
		assertFalse(rotation.isOnGround());
		assertTrue(rotation.isHorizontalCollision());

		ServerboundMovePlayerStatusOnlyPacket status = assertInstanceOf(ServerboundMovePlayerStatusOnlyPacket.class,
				packets.encode(ServerboundMovePlayerStatusOnly.builder().onGround(true).horizontalCollision(false)
						.build()));
		assertTrue(status.isOnGround());
		assertFalse(status.isHorizontalCollision());
	}

	@Test
	void encodesTheKeysTheClientHoldsAndTheEndOfItsTick() {
		ServerboundPlayerInputPacket input = assertInstanceOf(ServerboundPlayerInputPacket.class,
				packets.encode(ServerboundPlayerInput.builder().forward(true).left(true).jump(true).shift(true)
						.build()));
		assertTrue(input.isForward());
		assertFalse(input.isBackward());
		assertTrue(input.isLeft());
		assertFalse(input.isRight());
		assertTrue(input.isJump());
		assertTrue(input.isShift());
		assertFalse(input.isSprint());

		assertSame(ServerboundClientTickEndPacket.INSTANCE, packets.encode(new ServerboundClientTickEnd()));
	}

	@Test
	void encodesSprintingAndRefusesSneakingWhichTheReleaseTellsOnlyThroughTheKeys() {
		ServerboundPlayerCommandPacket start = assertInstanceOf(ServerboundPlayerCommandPacket.class,
				packets.encode(new ServerboundPlayerCommand(new EntityId(42), PlayerCommandAction.START_SPRINTING)));
		assertEquals(42, start.getEntityId());
		assertEquals(PlayerState.START_SPRINTING, start.getState());
		assertEquals(PlayerState.STOP_SPRINTING, ((ServerboundPlayerCommandPacket) packets.encode(
				new ServerboundPlayerCommand(new EntityId(42), PlayerCommandAction.STOP_SPRINTING))).getState());

		ProtocolUnavailableException refused = assertThrows(ProtocolUnavailableException.class,
				() -> packets.encode(new ServerboundPlayerCommand(new EntityId(42), PlayerCommandAction.PRESS_SHIFT_KEY)));
		assertTrue(refused.getMessage().contains("PRESS_SHIFT_KEY"));
		assertThrows(ProtocolUnavailableException.class,
				() -> packets.encode(new ServerboundPlayerCommand(new EntityId(42), PlayerCommandAction.RELEASE_SHIFT_KEY)));
	}

	@Test
	void decodesThePlayersPlacementWithItsVelocityAndEveryRelativeElement() {
		ClientboundPlayerPositionPacket position = new ClientboundPlayerPositionPacket(1, Vector3d.from(10, 64, -10),
				Vector3d.from(0.25, -0.5, 0), 90, 5, List.of(PositionElement.X, PositionElement.X_ROT,
				PositionElement.DELTA_Y, PositionElement.ROTATE_DELTA));

		assertEquals(ClientboundPlayerPosition.builder().position(new Vec3d(10, 64, -10)).yaw(90).pitch(5)
				.deltaMovement(new Vec3d(0.25, -0.5, 0))
				.relatives(Set.of(Relative.X, Relative.PITCH, Relative.DELTA_Y, Relative.ROTATE_DELTA)).build(),
				packets.decode(position));
	}

	@Test
	void decodesThePlayersAbilities() {
		assertEquals(ClientboundPlayerAbilities.builder().invulnerable(true).flying(false).mayFly(true)
						.instabuild(true).flyingSpeed(0.05f).walkingSpeed(0.1f).build(),
				packets.decode(new ClientboundPlayerAbilitiesPacket(true, true, false, true, 0.05f, 0.1f)));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesThePlayersPlacementAbilitiesMovementCommandsKeysAndTickEnd() {
		assertEquals(Set.of(
				ClientboundPlayerAbilities.class,
				ClientboundPlayerPosition.class,
				ServerboundClientTickEnd.class,
				ServerboundMovePlayerPos.class,
				ServerboundMovePlayerPosRot.class,
				ServerboundMovePlayerRot.class,
				ServerboundMovePlayerStatusOnly.class,
				ServerboundPlayerAbilities.class,
				ServerboundPlayerCommand.class,
				ServerboundPlayerInput.class
		), packets.packets());
	}

	@Test
	void encodesTheFlightThePlayerStartsOrStopsAsTheOnlyAbilityItTells() {
		ServerboundPlayerAbilitiesPacket flying = assertInstanceOf(ServerboundPlayerAbilitiesPacket.class,
				packets.encode(new ServerboundPlayerAbilities(true)));
		ServerboundPlayerAbilitiesPacket landed = assertInstanceOf(ServerboundPlayerAbilitiesPacket.class,
				packets.encode(new ServerboundPlayerAbilities(false)));

		assertTrue(flying.isFlying());
		assertFalse(landed.isFlying());
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
