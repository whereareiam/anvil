package me.whereareiam.anvil.player.library.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerState;
import com.github.steveice10.mc.protocol.data.game.entity.player.PositionElement;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.edition.java.protocol.type.player.PlayerCommandAction;
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
	void decodesThePlayersPlacementKeepingTheVelocityAlongEachRelativeCoordinate() {
		ClientboundPlayerPositionPacket position = new ClientboundPlayerPositionPacket(10, 64, -10, 90, 5, 1, false,
				PositionElement.X, PositionElement.Z, PositionElement.PITCH);

		assertEquals(ClientboundPlayerPosition.builder().position(new Vec3d(10, 64, -10)).yaw(90).pitch(5)
				.deltaMovement(Vec3d.ZERO)
				.relatives(Set.of(Relative.X, Relative.DELTA_X, Relative.Z, Relative.DELTA_Z, Relative.PITCH)).build(),
				packets.decode(position));
	}

	@Test
	void decodesAnAbsolutePlacementAsStoppingTheVelocity() {
		ClientboundPlayerPositionPacket position = new ClientboundPlayerPositionPacket(10, 64, -10, 90, 5, 1, false);

		assertEquals(ClientboundPlayerPosition.builder().position(new Vec3d(10, 64, -10)).yaw(90).pitch(5)
				.relatives(Set.of()).build(), packets.decode(position));
	}

	@Test
	void decodesTheAbilitiesTheGameModeGivesThePlayer() {
		assertEquals(ClientboundPlayerAbilities.builder().invulnerable(true).flying(false).mayFly(true).instabuild(true)
						.flyingSpeed(0.05f).walkingSpeed(0.1f).build(),
				packets.decode(new ClientboundPlayerAbilitiesPacket(true, true, false, true, 0.05f, 0.1f)));
	}

	@Test
	void encodesThePositionTheRotationAndTheGroundStateAloneWithoutHorizontalCollision() {
		ServerboundMovePlayerPosPacket position = assertInstanceOf(ServerboundMovePlayerPosPacket.class,
				packets.encode(ServerboundMovePlayerPos.builder().position(new Vec3d(1, 2, 3)).onGround(true)
						.horizontalCollision(true).build()));
		ServerboundMovePlayerRotPacket rotation = assertInstanceOf(ServerboundMovePlayerRotPacket.class,
				packets.encode(ServerboundMovePlayerRot.builder().yaw(45).pitch(10).horizontalCollision(true).build()));
		ServerboundMovePlayerStatusOnlyPacket status = assertInstanceOf(ServerboundMovePlayerStatusOnlyPacket.class,
				packets.encode(ServerboundMovePlayerStatusOnly.builder().onGround(true).horizontalCollision(true).build()));

		assertEquals(List.of(1.0, 2.0, 3.0), List.of(position.getX(), position.getY(), position.getZ()));
		assertTrue(position.isOnGround());
		assertEquals(45, rotation.getYaw());
		assertEquals(10, rotation.getPitch());
		assertFalse(rotation.isOnGround());
		assertTrue(status.isOnGround());
	}

	@Test
	void encodesSprintingAndSneakingAsPlayerStatesOfThePlayersEntity() {
		ServerboundPlayerCommandPacket sprint = assertInstanceOf(ServerboundPlayerCommandPacket.class,
				packets.encode(new ServerboundPlayerCommand(new EntityId(7), PlayerCommandAction.START_SPRINTING)));

		assertEquals(7, sprint.getEntityId());
		assertEquals(PlayerState.START_SPRINTING, sprint.getState());
		assertEquals(PlayerState.STOP_SPRINTING, state(PlayerCommandAction.STOP_SPRINTING));
		assertEquals(PlayerState.START_SNEAKING, state(PlayerCommandAction.PRESS_SHIFT_KEY));
		assertEquals(PlayerState.STOP_SNEAKING, state(PlayerCommandAction.RELEASE_SHIFT_KEY));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesThePlayersPacketsButNoInputOrTickEnd() {
		assertEquals(Set.of(
				ClientboundPlayerAbilities.class,
				ClientboundPlayerPosition.class,
				ServerboundMovePlayerPos.class,
				ServerboundMovePlayerPosRot.class,
				ServerboundMovePlayerRot.class,
				ServerboundMovePlayerStatusOnly.class,
				ServerboundPlayerAbilities.class,
				ServerboundPlayerCommand.class
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

	private PlayerState state(PlayerCommandAction action) {
		return assertInstanceOf(ServerboundPlayerCommandPacket.class,
				packets.encode(new ServerboundPlayerCommand(new EntityId(7), action))).getState();
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
